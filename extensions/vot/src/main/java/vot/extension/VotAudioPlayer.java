package vot.extension;

import android.media.MediaPlayer;

/** Проигрывание переведённой дорожки поверх видео. */
public final class VotAudioPlayer {
    private MediaPlayer player;
    private volatile boolean playing;
    private int pendingSeekMs = -1;

    public synchronized void start(String audioUrl) {
        start(audioUrl, true, -1);
    }

    /** @param autoStart false — подготовить, но не запускать (видео на паузе). */
    public synchronized void start(String audioUrl, boolean autoStart) {
        start(audioUrl, autoStart, -1);
    }

    public synchronized void start(String audioUrl, boolean autoStart, int startPositionMs) {
        stop();
        pendingSeekMs = startPositionMs;
        try {
            player = new MediaPlayer();
            player.setDataSource(audioUrl);
            player.setOnPreparedListener(mp -> {
                if (pendingSeekMs > 0) {
                    try {
                        mp.seekTo(pendingSeekMs);
                    } catch (Exception ignored) {
                        // игнорируем
                    }
                }
                if (autoStart) {
                    mp.start();
                    playing = true;
                    VotController.onAudioStarted();
                }
            });
            player.prepareAsync();
        } catch (Exception ignored) {
            stop();
        }
    }

    public synchronized void setVolume(float volume) {
        if (player != null) {
            player.setVolume(volume, volume);
        }
    }

    public synchronized void pause() {
        if (player != null && playing) {
            try {
                player.pause();
            } catch (Exception ignored) {
                // уже остановлен
            }
            playing = false;
        }
    }

    public synchronized void resume() {
        if (player != null && !playing) {
            try {
                player.start();
                playing = true;
            } catch (Exception ignored) {
                // не готов
            }
        }
    }

    public synchronized void seekTo(int positionMs) {
        if (player != null) {
            try {
                player.seekTo(Math.max(0, positionMs));
            } catch (Exception ignored) {
                // не готов
            }
        }
    }

    public synchronized int getPosition() {
        if (player == null) {
            return -1;
        }
        try {
            return player.getCurrentPosition();
        } catch (Exception ignored) {
            return -1;
        }
    }

    public boolean isPlaying() {
        return playing;
    }

    public synchronized boolean hasTrack() {
        return player != null;
    }

    public synchronized void stop() {
        playing = false;
        if (player == null) {
            return;
        }
        try {
            player.stop();
        } catch (Exception ignored) {
            // уже остановлен
        }
        player.release();
        player = null;
    }
}
