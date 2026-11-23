package vot.extension;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Base64;
import android.view.Window;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Вход в Яндекс по OAuth (PKCE) через WebView — как в оригинальном VOT. */
public final class VotOAuth {
    private static final String CLIENT_ID = "1666fbe22f3749c581002a4f97b2592d";
    private static final String AUTH_URL = "https://oauth.yandex.ru/authorize";
    private static final String REDIRECT_URI = "https://oauth.yandex.ru/verification_code";
    private static final String TOKEN_URL = "https://oauth.yandex.ru/token";
    private static final String USER_INFO_URL = "https://login.yandex.ru/info?format=json";

    private VotOAuth() {
    }

    public static void login(Activity activity, Runnable onSuccess) {
        try {
            String verifier = randomVerifier();
            String challenge = base64Url(sha256(verifier));
            String state = randomState();

            String url = AUTH_URL
                    + "?client_id=" + enc(CLIENT_ID)
                    + "&response_type=code"
                    + "&redirect_uri=" + enc(REDIRECT_URI)
                    + "&state=" + enc(state)
                    + "&code_challenge=" + enc(challenge)
                    + "&code_challenge_method=S256"
                    + "&force_confirm=1"
                    + "&et=" + System.currentTimeMillis();

            Dialog dialog = new Dialog(activity);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            WebView web = new WebView(activity);
            web.getSettings().setJavaScriptEnabled(true);
            web.getSettings().setDomStorageEnabled(true);
            web.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return handle(activity, dialog, request.getUrl().toString(), state, verifier, onSuccess);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return handle(activity, dialog, url, state, verifier, onSuccess);
                }

                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    handle(activity, dialog, url, state, verifier, onSuccess);
                }
            });

            dialog.setContentView(web);
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            }
            dialog.show();
            web.loadUrl(url);
        } catch (Throwable error) {
            VotController.toast("VOT: не удалось открыть вход Яндекс");
        }
    }

    private static boolean handle(
            Activity activity, Dialog dialog, String url, String state, String verifier,
            Runnable onSuccess) {
        if (url == null || !url.startsWith(REDIRECT_URI)) {
            return false;
        }
        try {
            Uri parsed = Uri.parse(url);
            String code = parsed.getQueryParameter("code");
            String returnedState = parsed.getQueryParameter("state");
            if (code == null || code.isEmpty()) {
                dialog.dismiss();
                VotController.toast("VOT: Яндекс не вернул код");
                return true;
            }
            if (returnedState != null && !returnedState.equals(state)) {
                dialog.dismiss();
                VotController.toast("VOT: ошибка state");
                return true;
            }
            dialog.dismiss();

            new Thread(() -> {
                try {
                    String token = exchange(code, verifier);
                    String username = fetchUsername(token);
                    VotSettings.setString("accountToken", token);
                    VotSettings.setString("accountUsername", username == null ? "" : username);
                    VotController.init(activity);
                    activity.runOnUiThread(() -> {
                        VotController.toast("VOT: вход выполнен" + (username == null ? "" : ": " + username));
                        if (onSuccess != null) onSuccess.run();
                    });
                } catch (Throwable error) {
                    activity.runOnUiThread(() -> VotController.toast("VOT: вход не удался"));
                }
            }, "vot-oauth").start();
        } catch (Throwable ignored) {
            dialog.dismiss();
        }
        return true;
    }

    private static String exchange(String code, String verifier) throws Exception {
        String body = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&client_id=" + enc(CLIENT_ID)
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&code_verifier=" + enc(verifier);

        HttpURLConnection connection = (HttpURLConnection) new URL(TOKEN_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        String response = read(connection.getInputStream());
        JSONObject json = new JSONObject(response);
        String token = json.optString("access_token", "");
        if (token.isEmpty()) {
            throw new IllegalStateException("OAuth: нет access_token");
        }
        return token;
    }

    private static String fetchUsername(String token) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(USER_INFO_URL).openConnection();
            connection.setRequestProperty("Authorization", "OAuth " + token);
            String response = read(connection.getInputStream());
            return new JSONObject(response).optString("login", "");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String read(InputStream stream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, count);
        }
        return buffer.toString("UTF-8");
    }

    private static String randomVerifier() {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~";
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(64);
        for (int i = 0; i < 64; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    private static String randomState() {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            builder.append("0123456789abcdef".charAt(random.nextInt(16)));
        }
        return builder.toString();
    }

    private static byte[] sha256(String value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] data) {
        return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
