package gg.rago.kbrecorder;

/**
 * Builds one JSON object on a single line. The recorder writes many lines per second and must not
 * depend on the Gson version bundled with Minecraft, so it writes its JSON itself.
 */
final class JsonLine {

    private final StringBuilder json = new StringBuilder(256).append('{');
    private boolean empty = true;

    static JsonLine of(String type) {
        return new JsonLine().add("type", type);
    }

    JsonLine add(String key, String value) {
        key(key);
        if (value == null) {
            json.append("null");
        } else {
            quote(value);
        }
        return this;
    }

    JsonLine add(String key, long value) {
        key(key);
        json.append(value);
        return this;
    }

    /** Doubles are written so that parsing them gives back the same double. */
    JsonLine add(String key, double value) {
        key(key);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            json.append("null");
        } else {
            json.append(value);
        }
        return this;
    }

    /** Floats are written as floats, so that parsing then narrowing gives back the same float. */
    JsonLine add(String key, float value) {
        key(key);
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            json.append("null");
        } else {
            json.append(value);
        }
        return this;
    }

    JsonLine add(String key, boolean value) {
        key(key);
        json.append(value);
        return this;
    }

    /** Adds a value that is already JSON: a nested object or an array. */
    JsonLine raw(String key, String json) {
        key(key);
        this.json.append(json);
        return this;
    }

    String build() {
        return json.toString() + '}';
    }

    @Override
    public String toString() {
        return build();
    }

    /** Joins already-built JSON values into an array. */
    static String array(Iterable<String> values) {
        StringBuilder array = new StringBuilder().append('[');
        boolean first = true;
        for (String value : values) {
            if (!first) {
                array.append(',');
            }
            array.append(value);
            first = false;
        }
        return array.append(']').toString();
    }

    private void key(String key) {
        if (!empty) {
            json.append(',');
        }
        empty = false;
        quote(key);
        json.append(':');
    }

    private void quote(String text) {
        json.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    json.append("\\\"");
                    break;
                case '\\':
                    json.append("\\\\");
                    break;
                case '\n':
                    json.append("\\n");
                    break;
                case '\r':
                    json.append("\\r");
                    break;
                case '\t':
                    json.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
            }
        }
        json.append('"');
    }
}
