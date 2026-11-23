package vot.extension;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Клиент API Yandex VOT (protobuf + HMAC, как в @vot.js/core). */
public final class VotApiClient {
    private static final String DEFAULT_HOST = "https://api.browser.yandex.ru";
    private static final String HMAC_KEY = "bt8xH3VOlb4mqf0nqAibnDOoiPlXsisf";
    private static final String COMPONENT_VERSION = "26.11.0.1993";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/154.0.0.0 YaBrowser/26.11.0.0 Safari/537.36";

    private static final int STATUS_FINISHED = 1;
    private static final int STATUS_PART_CONTENT = 5;

    public static final class Result {
        public final boolean ready;
        public final String audioUrl;
        public final int remainingSeconds;
        public final int status;
        public final String message;

        Result(boolean ready, String audioUrl, int remainingSeconds, int status, String message) {
            this.ready = ready;
            this.audioUrl = audioUrl;
            this.remainingSeconds = remainingSeconds;
            this.status = status;
            this.message = message;
        }
    }

    private final String host;
    private final String apiToken;

    private String secretKey;
    private String uuid;
    private long sessionExpiresAt;

    public VotApiClient() {
        this(DEFAULT_HOST, null);
    }

    public VotApiClient(String host) {
        this(host, null);
    }

    public VotApiClient(String host, String apiToken) {
        this.host = host == null || host.isEmpty() ? DEFAULT_HOST : host;
        this.apiToken = apiToken;
    }

    public Result translate(
            String videoUrl,
            int durationSeconds,
            String sourceLang,
            String targetLang,
            boolean useLivelyVoice) throws Exception {
        ensureSession();

        byte[] body = new VotProtobuf.Writer()
                .string(3, videoUrl)
                .bool(5, true)
                .doubleField(6, durationSeconds)
                .bool(7, true)
                .string(8, sourceLang == null || sourceLang.isEmpty() ? "auto" : sourceLang)
                .string(14, targetLang == null || targetLang.isEmpty() ? "ru" : targetLang)
                .bool(15, true)
                .int32(16, 2)
                .bool(18, useLivelyVoice)
                .toByteArray();

        String path = "/video-translation/translate";
        String token = uuid + ":" + path + ":" + COMPONENT_VERSION;

        HttpURLConnection connection = open(path);
        connection.setRequestProperty("Vtrans-Signature", hex(hmac(body)));
        connection.setRequestProperty("Sec-Vtrans-Sk", secretKey);
        connection.setRequestProperty("Sec-Vtrans-Token",
                hex(hmac(token.getBytes(StandardCharsets.UTF_8))) + ":" + token);
        if (apiToken != null && !apiToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "OAuth " + apiToken);
        }

        Map<Integer, Object> fields = VotProtobuf.parse(request(connection, body));
        String audio = VotProtobuf.asString(fields, 1);
        int status = (int) VotProtobuf.asLong(fields, 4);
        int remaining = (int) VotProtobuf.asLong(fields, 5);
        String message = VotProtobuf.asString(fields, 9);
        if (message == null) {
            message = VotProtobuf.asString(fields, 14);
        }

        boolean ready = audio != null && !audio.isEmpty()
                && (status == STATUS_FINISHED || status == STATUS_PART_CONTENT);
        return new Result(ready, audio, Math.max(remaining, 0), status, message);
    }

    private void ensureSession() throws Exception {
        long now = System.currentTimeMillis() / 1000L;
        if (secretKey != null && now < sessionExpiresAt) {
            return;
        }

        uuid = randomUuid();
        byte[] body = new VotProtobuf.Writer()
                .string(1, uuid)
                .string(2, "video-translation")
                .toByteArray();

        HttpURLConnection connection = open("/session/create");
        connection.setRequestProperty("Vtrans-Signature", hex(hmac(body)));

        Map<Integer, Object> fields = VotProtobuf.parse(request(connection, body));
        String key = VotProtobuf.asString(fields, 1);
        long expires = VotProtobuf.asLong(fields, 2);
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException("VOT: сервер не выдал secretKey");
        }
        secretKey = key;
        sessionExpiresAt = now + Math.max(expires, 60);
    }

    private HttpURLConnection open(String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(host + path).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(120_000);
        connection.setRequestProperty("Content-Type", "application/x-protobuf");
        connection.setRequestProperty("Accept", "application/x-protobuf");
        connection.setRequestProperty("Accept-Language", "en");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setRequestProperty("Cache-Control", "no-cache");
        return connection;
    }

    private byte[] request(HttpURLConnection connection, byte[] body) throws Exception {
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            throw new IllegalStateException("VOT: пустой ответ, HTTP " + code);
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        if (code >= 400 && buffer.size() == 0) {
            throw new IllegalStateException("VOT: HTTP " + code);
        }
        return buffer.toByteArray();
    }

    private static byte[] hmac(byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(HMAC_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static String hex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte b : data) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    private static String randomUuid() {
        Random random = new Random();
        StringBuilder builder = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            builder.append("0123456789ABCDEF".charAt(random.nextInt(16)));
        }
        return builder.toString();
    }
}
