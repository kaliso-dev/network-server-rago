package gg.rago.tools.kbanalyzer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A kb-recorder recording (tools/kb-recorder): one JSON line per event, written by the mod in
 * {@code .minecraft/rago-kb-recorder}. Unreadable lines do not stop the reading: they are listed
 * in {@link #problems}.
 */
public final class Recording {

    /** Line format this reader understands (Recorder.FORMAT in the mod). */
    public static final int FORMAT = 1;

    public JsonObject session;
    public JsonObject end;
    public final List<JsonObject> errors = new ArrayList<>();
    /** Tick lines, in file order (the client's tick order). */
    public final List<Tick> ticks = new ArrayList<>();
    /** Packet lines, in file order. */
    public final List<Packet> packets = new ArrayList<>();
    public final List<Mark> marks = new ArrayList<>();
    /** Context lines (server, sidebar, connection), as key and value. */
    public final List<String[]> contexts = new ArrayList<>();
    /** Lines that break the format. */
    public final List<String> problems = new ArrayList<>();

    public static Recording read(Path file) throws IOException {
        return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    public static Recording parse(List<String> lines) {
        Recording recording = new Recording();
        int number = 0;
        for (String text : lines) {
            number++;
            if (text.trim().isEmpty()) {
                continue;
            }
            try {
                recording.add(number, JsonParser.parseString(text).getAsJsonObject());
            } catch (JsonParseException | IllegalStateException | ClassCastException
                    | UnsupportedOperationException | NullPointerException | IllegalArgumentException e) {
                recording.problems.add("line " + number + " cannot be read: " + e.getMessage());
            }
        }
        return recording;
    }

    /** The packets ordered by time. Network and game threads can write lines a little out of order. */
    public List<Packet> packetsByTime() {
        List<Packet> sorted = new ArrayList<>(packets);
        sorted.sort(Comparator.comparingLong(packet -> packet.ms));
        return sorted;
    }

    /** The last tick line at or before the time, or null. */
    public Tick tickAtOrBefore(long ms) {
        Tick found = null;
        for (Tick tick : ticks) {
            if (tick.ms > ms) {
                break;
            }
            found = tick;
        }
        return found;
    }

    /** The first tick line at or after the time, or null. */
    public Tick tickAtOrAfter(long ms) {
        for (Tick tick : ticks) {
            if (tick.ms >= ms) {
                return tick;
            }
        }
        return null;
    }

    /** The text of the last mark at or before the time, or null. */
    public String markAt(long ms) {
        String text = null;
        for (Mark mark : marks) {
            if (mark.ms > ms) {
                break;
            }
            text = mark.text;
        }
        return text;
    }

    /** The values a context key took, in order, without repeats. */
    public List<String> contextValues(String key) {
        List<String> values = new ArrayList<>();
        for (String[] context : contexts) {
            if (key.equals(context[0]) && context[1] != null && !values.contains(context[1])) {
                values.add(context[1]);
            }
        }
        return values;
    }

    private void add(int number, JsonObject line) {
        for (String field : new String[] {"type", "ms", "nano", "tick"}) {
            if (!line.has(field)) {
                problems.add("line " + number + " has no " + field);
                return;
            }
        }
        if (end != null) {
            problems.add("line " + number + " comes after the end line");
        }
        String type = line.get("type").getAsString();
        if (session == null && !"session".equals(type)) {
            problems.add("line " + number + " (" + type + ") comes before the session line");
        }
        switch (type) {
            case "session":
                if (session != null) {
                    problems.add("line " + number + " is a second session line");
                }
                session = line;
                break;
            case "end":
                end = line;
                break;
            case "error":
                errors.add(line);
                break;
            case "tick":
                ticks.add(Tick.fromJson(line));
                break;
            case "packet":
                packets.add(Packet.fromJson(line));
                break;
            case "mark":
                marks.add(new Mark(line.get("ms").getAsLong(), Json.text(line, "text")));
                break;
            case "context":
                contexts.add(new String[] {Json.text(line, "key"), Json.text(line, "value")});
                break;
            default:
                break;
        }
    }

    /** A {@code /kbrec mark} line. */
    public static final class Mark {
        public final long ms;
        public final String text;

        Mark(long ms, String text) {
            this.ms = ms;
            this.text = text;
        }
    }
}
