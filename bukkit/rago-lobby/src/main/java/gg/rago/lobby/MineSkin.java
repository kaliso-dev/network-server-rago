package gg.rago.lobby;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Signs a skin through MineSkin (api.mineskin.org, v2): the PNG goes up, a Minecraft account of
 * theirs wears it, and Mojang's signed textures come back, the only ones a 1.8 client shows on an
 * NPC. An API key is optional (account.mineskin.org/keys); without one the requests are slower.
 * Blocking: call it away from the main thread.
 */
final class MineSkin {

    static final String API = "https://api.mineskin.org";
    static final String USER_AGENT = "RagoLobby/1.0";
    private static final int CONNECT_MS = 10_000;
    private static final int READ_MS = 90_000;
    /** The longest a name of a skin may be there. */
    private static final int NAME_LENGTH = 20;

    /** A refusal of MineSkin: its HTTP status, its message, how long to wait before the next try. */
    static final class Refused extends IOException {
        private final int status;
        private final long waitMs;

        Refused(int status, String message, long waitMs) {
            super("MineSkin answered " + status + ": " + message);
            this.status = status;
            this.waitMs = waitMs;
        }

        int status() {
            return status;
        }

        /** How long MineSkin asks to wait before the next request, 0 if it does not say. */
        long waitMs() {
            return waitMs;
        }
    }

    private MineSkin() {
    }

    /** Uploads this PNG (a 64x64 skin, classic arms) and returns its signed textures. */
    static Skin upload(String api, byte[] png, String name, String key) throws IOException {
        String boundary = "rago" + Long.toHexString(System.nanoTime());
        byte[] body = body(boundary, png, name);
        HttpURLConnection connection = (HttpURLConnection) new URL(api + "/v2/generate").openConnection();
        try {
            connection.setConnectTimeout(CONNECT_MS);
            connection.setReadTimeout(READ_MS);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            if (key != null && !key.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + key);
            }
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int status = connection.getResponseCode();
            InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String answer = in == null ? "" : read(in);
            Skin skin = status == 200 ? skinOf(answer) : null;
            if (skin != null) {
                return skin;
            }
            long wait = waitOf(answer);
            String retry = connection.getHeaderField("Retry-After");
            if (wait == 0 && retry != null && retry.trim().matches("\\d{1,4}")) {
                wait = Long.parseLong(retry.trim()) * 1000L;
            }
            throw new Refused(status, errorOf(answer), wait);
        } finally {
            connection.disconnect();
        }
    }

    /** The multipart form: the options, then the file. */
    static byte[] body(String boundary, byte[] png, String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(png.length + 1024);
        field(out, boundary, "variant", "classic");
        field(out, boundary, "visibility", "unlisted");
        field(out, boundary, "name", shortName(name));
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + shortName(name)
                + ".png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(png);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    /** Letters, digits and dashes, 20 at most. */
    static String shortName(String name) {
        String clean = name.replaceAll("[^A-Za-z0-9-]", "");
        return clean.length() > NAME_LENGTH ? clean.substring(0, NAME_LENGTH) : clean;
    }

    /** The signed textures in an answer of /v2/generate (skin.texture.data), or null. */
    static Skin skinOf(String json) {
        JsonObject data = object(object(object(parse(json), "skin"), "texture"), "data");
        return data == null ? null : Skin.of(text(data, "value"), text(data, "signature"));
    }

    /** What went wrong, from an answer of MineSkin: its first error, or the body itself. */
    static String errorOf(String json) {
        JsonObject root = parse(json);
        if (root != null) {
            JsonElement errors = root.get("errors");
            if (errors != null && errors.isJsonArray() && ((JsonArray) errors).size() > 0
                    && ((JsonArray) errors).get(0).isJsonObject()) {
                JsonObject first = ((JsonArray) errors).get(0).getAsJsonObject();
                String code = text(first, "code");
                String message = text(first, "message");
                return (message != null ? message : "") + (code != null ? " (" + code + ")" : "");
            }
            String error = text(root, "error");
            if (error != null) {
                return error;
            }
        }
        String body = json == null ? "" : json.trim();
        return body.isEmpty() ? "no answer" : body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    /** How long MineSkin asks to wait (rateLimit.next.relative, in ms), 0 if it does not say. */
    static long waitOf(String json) {
        JsonObject next = object(object(parse(json), "rateLimit"), "next");
        JsonElement relative = next == null ? null : next.get("relative");
        try {
            return relative == null || !relative.isJsonPrimitive() ? 0 : Math.max(0, Math.min(120_000L, relative.getAsLong()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static JsonObject parse(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonElement root = new JsonParser().parse(json);
            return root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement child = parent == null ? null : parent.get(key);
        return child != null && child.isJsonObject() ? child.getAsJsonObject() : null;
    }

    private static String text(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = stream.read(buffer)) > 0 && out.size() < 1_000_000) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
