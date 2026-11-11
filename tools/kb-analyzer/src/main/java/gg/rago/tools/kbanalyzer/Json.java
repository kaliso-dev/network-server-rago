package gg.rago.tools.kbanalyzer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Reads optional fields: a recording from an older mod version may lack some of them. */
final class Json {

    private Json() {
    }

    static double number(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? Double.NaN : value.getAsDouble();
    }

    static boolean flag(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    static String text(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}
