package vot.extension;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Настройки VOT. Ключи совпадают с ключами оригинального userscript
 * (см. storageKeys в voice-over-translation/src/types/storage.ts).
 */
public final class VotSettings {
    private static final String FILE = "vot_settings";
    public static final String DEFAULT_HOST = "https://api.browser.yandex.ru";

    private static SharedPreferences prefs;

    private VotSettings() {
    }

    public static synchronized void init(Context context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        }
    }

    // ---- generic, ключи как в оригинальном скрипте ----

    public static boolean getBool(String key, boolean fallback) {
        return prefs == null ? fallback : prefs.getBoolean(key, fallback);
    }

    public static void setBool(String key, boolean value) {
        if (prefs != null) prefs.edit().putBoolean(key, value).apply();
    }

    public static String getString(String key, String fallback) {
        return prefs == null ? fallback : prefs.getString(key, fallback);
    }

    public static void setString(String key, String value) {
        if (prefs != null) prefs.edit().putString(key, value).apply();
    }

    public static int getInt(String key, int fallback) {
        return prefs == null ? fallback : prefs.getInt(key, fallback);
    }

    public static void setInt(String key, int value) {
        if (prefs != null) prefs.edit().putInt(key, value).apply();
    }

    public static void reset() {
        if (prefs != null) prefs.edit().clear().apply();
    }

    // ---- часто используемые ----

    public static boolean enabled() {
        return getBool("enable", true);
    }

    public static boolean autoTranslate() {
        return getBool("autoTranslate", false);
    }

    public static String host() {
        return getString("proxyWorkerHost", DEFAULT_HOST);
    }

    public static String apiToken() {
        return getString("accountToken", "");
    }

    public static String targetLanguage() {
        return getString("responseLanguage", "ru");
    }

    public static String sourceLanguage() {
        return getString("sourceLanguage", "auto");
    }

    public static boolean livelyVoice() {
        return getBool("useLivelyVoice", false);
    }
}
