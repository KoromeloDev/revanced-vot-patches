package vot.extension;

/**
 * Заменил {@code AudioTrack.setVolume} в аудиосинках, чтобы приглушать
 * дорожку видео точно (на уровне AudioTrack), без системной громкости телефона.
 * После хука захватываю активный трек по {@code AudioTrack.play()}
 * и перемножаю его громкость на коэффициент «Громкости оригинала».
 */
public final class VotAudioSink {
    private static volatile android.media.AudioTrack track;
    private static volatile float baseVolume = 1f;
    private static volatile float factor = 1f;

    private VotAudioSink() {
    }

    /** Вызывается хуком вместо AudioTrack.play — трек точно активный. */
    public static void play(android.media.AudioTrack audioTrack) {
        if (audioTrack == null) {
            return;
        }
        if (audioTrack != track) {
            track = audioTrack;
            baseVolume = 1f;
        }
        if (baseVolume > 0.001f) {
            apply(baseVolume * factor);
            android.util.Log.i("VOT", "sink play base=" + baseVolume + " factor=" + factor);
        }
        try {
            audioTrack.play();
        } catch (Throwable ignored) {
            // уже играет
        }
    }

    /** Вызывается хуком вместо AudioTrack.setVolume. */
    public static int setVolume(android.media.AudioTrack audioTrack, float volume) {
        track = audioTrack;
        baseVolume = volume;
        int result = apply(volume * factor);
        android.util.Log.i("VOT", "sink vol=" + volume + " factor=" + factor + " result=" + result);
        return result;
    }

    /** factor: 1 — оригинал как есть, 0.2 — приглушить до 20%. */
    public static void duck(float newFactor) {
        factor = Math.max(0f, Math.min(1f, newFactor));
        android.media.AudioTrack current = track;
        if (current != null) {
            apply(baseVolume * factor);
        }
    }

    public static float factor() {
        return factor;
    }

    private static int apply(float value) {
        android.media.AudioTrack current = track;
        if (current == null) {
            return 0;
        }
        try {
            return current.setVolume(Math.max(0f, Math.min(1f, value)));
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
