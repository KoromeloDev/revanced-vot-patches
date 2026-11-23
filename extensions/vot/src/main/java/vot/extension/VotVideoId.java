package vot.extension;

/**
 * Захватил идентификатор текущего видео: во все строковые getter'ы
 * {@code PlayerResponseModelImpl} поставил хук {@code VotVideoId.accept(String)},
 * а потом беру только валидные 11-символьные идентификаторы
 * (мало какие из остальных).
 */
public final class VotVideoId {
    private static volatile String current;

    private VotVideoId() {
    }

    /** Вызывается патчем для каждого строкового getter'а PlayerResponseModelImpl. */
    public static void accept(String value) {
        if (!isVideoId(value)) {
            return;
        }
        boolean changed = !value.equals(current);
        current = value;
        if (changed) {
            VotController.onVideoChanged("https://www.youtube.com/watch?v=" + value);
        }
    }

    public static String current() {
        return current;
    }

    public static String url() {
        String id = current;
        return id == null ? null : "https://www.youtube.com/watch?v=" + id;
    }

    private static boolean isVideoId(String value) {
        if (value == null || value.length() != 11) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
