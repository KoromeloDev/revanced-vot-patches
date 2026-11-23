package vot.extension;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

/** Управление переводом текущего видео. */
public final class VotController {
    private static final String TAG = "VOT";
    private static final int MAX_WAIT_SECONDS = 15 * 60;
    private static final int CACHE_SIZE = 5;
    private static final int SYNC_THRESHOLD_MS = 1500;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Кэш перевода: ссылка на видео -> URL аудио. */
    private static final Map<String, String> CACHE =
            new LinkedHashMap<String, String>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > CACHE_SIZE;
                }
            };

    private static Context appContext;
    private static boolean volumeObserverRegistered;
    private static VotApiClient api = new VotApiClient();
    private static final VotAudioPlayer player = new VotAudioPlayer();

    private static String currentUrl;
    private static volatile boolean running;
    private static volatile int lastPlaybackState = android.media.session.PlaybackState.STATE_NONE;
    private static volatile long lastPositionMs = 0;
    private static volatile boolean audioReady;

    private VotController() {
    }

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        VotSettings.init(context);
        api = new VotApiClient(VotSettings.host(), VotSettings.apiToken());

        if (!volumeObserverRegistered && appContext != null) {
            try {
                ContentResolver resolver = appContext.getContentResolver();
                resolver.registerContentObserver(Settings.System.CONTENT_URI, true,
                        new ContentObserver(MAIN) {
                            @Override
                            public void onChange(boolean selfChange) {
                                if (VotSettings.getBool("syncVolume", false)) {
                                    applyVolume();
                                }
                            }
                        });
                volumeObserverRegistered = true;
            } catch (Throwable ignored) {
                // нет доступа
            }
        }
    }

    /** Вызывается после фактического старта переведённой дорожки. */
    static void onAudioStarted() {
        applyDucking(true);
    }

    /**
     * @return 0 — видео не найдено, 1 — перевод включён, 2 — перевод выключен.
     */
    public static int translateCurrent() {
        String url = VotVideoId.url();
        if (url == null) {
            return 0;
        }
        return toggle(url) ? 1 : 2;
    }

    public static synchronized boolean toggle(String url) {
        if (!isYoutubeUrl(url) || !VotSettings.enabled()) {
            return false;
        }
        if (running && url.equals(currentUrl)) {
            stop();
            return false;
        }
        start(url);
        return true;
    }

    /** Сменилось видео: сбросить перевод прошлого и, если включено, начать новый. */
    public static void onVideoChanged(String url) {
        synchronized (VotController.class) {
            if (running) {
                stop();
            }
            audioReady = false;
            lastPositionMs = 0;
        }
        maybeAutoStart(url);
    }

    public static synchronized void maybeAutoStart(String url) {
        if (!VotSettings.autoTranslate() || !VotSettings.enabled()) {
            return;
        }
        if (running && url.equals(currentUrl)) {
            return;
        }
        start(url);
    }

    private static void start(String url) {
        currentUrl = url;
        running = true;
        audioReady = false;

        boolean autoStart = lastPlaybackState != android.media.session.PlaybackState.STATE_PAUSED;
        String cached = CACHE.get(url);
        if (cached != null) {
            audioReady = true;
            player.start(cached, autoStart, (int) Math.max(0, lastPositionMs));
            applyVolume();
            applyDucking(true);
            return;
        }

        new Thread(() -> translateLoop(url), "vot-translate").start();
    }

    private static void translateLoop(String url) {
        int waited = 0;
        try {
            while (running && url.equals(currentUrl)) {
                VotApiClient.Result result = api.translate(
                        url,
                        0,
                        VotSettings.sourceLanguage(),
                        VotSettings.targetLanguage(),
                        VotSettings.livelyVoice());

                if (result.ready) {
                    CACHE.put(url, result.audioUrl);
                    boolean autoStart =
                            lastPlaybackState != android.media.session.PlaybackState.STATE_PAUSED;
                    audioReady = true;
                    player.start(result.audioUrl, autoStart, (int) Math.max(0, lastPositionMs));
                    applyVolume();
                    applyDucking(true);
                    if (VotSettings.getBool("sendNotifyOnComplete", false)) {
                        toast("VOT: перевод готов");
                    }
                    return;
                }

                int delay = result.remainingSeconds > 0
                        ? Math.max(result.remainingSeconds, 3)
                        : 5;
                if (result.status == 0 && result.remainingSeconds <= 0) {
                    throw new IllegalStateException(
                            "Yandex отказал" + (result.message == null ? "" : ": " + result.message));
                }
                if (waited >= MAX_WAIT_SECONDS) {
                    throw new IllegalStateException("Перевод не успел подготовиться");
                }

                if (VotSettings.getBool("sendNotifyOnComplete", false)) {
                    toast("VOT: перевод готовится, ~" + delay + " c");
                }
                Thread.sleep(delay * 1000L);
                waited += delay;
            }
        } catch (Exception error) {
            Log.e(TAG, "Ошибка перевода", error);
            toast("VOT error: " + error.getMessage());
            stop();
        }
    }

    /**
     * Синхронизация с плеером YouTube: вызывается хуком при смене
     * PlaybackState (пауза/воспроизведение/перемотка).
     */
    public static void onPlaybackState(Object state) {
        if (!(state instanceof android.media.session.PlaybackState)) {
            return;
        }
        android.media.session.PlaybackState playback =
                (android.media.session.PlaybackState) state;
        int status = playback.getState();
        long position = playback.getPosition();
        // Запоминаем ВСЕГДА, даже если перевод ещё не запущен.
        lastPlaybackState = status;
        lastPositionMs = position;
        Log.i(TAG, "playback state=" + status + " pos=" + position);

        if (!running) {
            return;
        }

        if (status == android.media.session.PlaybackState.STATE_PLAYING) {
            player.resume();
            applyDucking(true);
            int current = player.getPosition();
            if (current >= 0 && Math.abs(current - position) > SYNC_THRESHOLD_MS) {
                player.seekTo((int) position);
            }
        } else if (status == android.media.session.PlaybackState.STATE_PAUSED
                || status == android.media.session.PlaybackState.STATE_STOPPED) {
            player.pause();
            // пересчитать приглушение (для адаптивного режима)
            applyDucking(true);
        }
    }

    public static boolean isRunning() {
        return running;
    }

    /** Идёт ожидание готовности перевода (аудио ещё не играет). */
    public static boolean isLoading() {
        return running && !audioReady;
    }

    public static void setOriginalVolume(int percent) {
        VotSettings.setInt("originalVolume", Math.max(0, Math.min(100, percent)));
        refreshDucking();
    }

    /** Применить/снять приглушение оригинала. */
    public static void refreshDucking() {
        applyDucking(running);
    }

    /** factor для дорожки видео: 1 — не трогать. */
    private static void applyDucking(boolean active) {
        float factor = 1f;
        boolean autoVolume = VotSettings.getBool("enabledAutoVolume", true);
        if (active && autoVolume) {
            boolean smart = VotSettings.getBool("enabledSmartDucking", true);
            if (!smart || player.isPlaying()) {
                int original = VotSettings.getInt("originalVolume", 100);
                factor = Math.max(0f, Math.min(1f, original / 100f));
            }
        }
        VotAudioSink.duck(factor);
        Log.i(TAG, "ducking factor=" + factor);
    }

    public static void setTranslationVolume(int percent) {
        VotSettings.setInt("defaultVolume", Math.max(0, Math.min(100, percent)));
        applyVolume();
    }

    /** Громкость перевода (0..1) из настроек; при syncVolume — как у медиапотока. */
    private static void applyVolume() {
        int volume;
        if (VotSettings.getBool("syncVolume", false)) {
            volume = streamVolumePercent();
        } else {
            volume = Math.max(0, Math.min(100, VotSettings.getInt("defaultVolume", 100)));
        }
        player.setVolume(volume / 100f);
    }

    private static int streamVolumePercent() {
        try {
            if (appContext == null) return 100;
            android.media.AudioManager audio =
                    (android.media.AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) return 100;
            int max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            int current = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
            return max > 0 ? Math.round(100f * current / max) : 100;
        } catch (Throwable ignored) {
            return 100;
        }
    }

    /** Обновить громкость перевода после смены настроек/системной громкости. */
    public static void refreshVolume() {
        applyVolume();
    }

    public static synchronized void stop() {
        running = false;
        audioReady = false;
        player.stop();
        applyDucking(false);
    }



    public static boolean isYoutubeUrl(String url) {
        return url != null
                && (url.contains("youtube.com/watch")
                || url.contains("youtu.be/")
                || url.contains("youtube.com/shorts/"));
    }

    static void toast(String text) {
        if (appContext == null) return;
        MAIN.post(() -> Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show());
    }
}
