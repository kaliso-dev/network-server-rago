package gg.rago.kbrecorder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One recording session at a time, written as JSON lines. Everything here is safe to call from the
 * game thread and from the network threads at the same time, and nothing here waits for the disk.
 *
 * <p>Every line has {@code type}, {@code ms} (wall clock), {@code nano} (monotonic clock) and
 * {@code tick} (client ticks recorded so far in this session).
 */
final class Recorder {

    /** Version of the line format, written in the session line. Raise it when a field changes. */
    static final int FORMAT = 1;

    private static final int MAX_LOGGED_ERRORS = 10;

    private final File directory;
    private final Clock clock;
    private final Object lock = new Object();
    private final AtomicLong errors = new AtomicLong();

    private volatile RecordingWriter writer;
    private volatile int tick;
    private volatile int[] trackedEntities = new int[0];

    Recorder(File directory, Clock clock) {
        this.directory = directory;
        this.clock = clock;
    }

    boolean isRecording() {
        return writer != null;
    }

    /** Client ticks recorded so far in the current session. */
    int ticks() {
        return tick;
    }

    /** The file being written, or null. */
    File file() {
        RecordingWriter current = writer;
        return current == null ? null : current.file();
    }

    /** Starts a new file and writes its session line. */
    File start(String label, String player, String playerUuid, String server, String modVersion) throws IOException {
        synchronized (lock) {
            if (writer != null) {
                throw new IllegalStateException("a recording is already running");
            }
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("cannot create " + directory);
            }
            File file = newFile(label);
            tick = 0;
            errors.set(0);
            RecordingWriter started = new RecordingWriter(file);
            started.submit(line("session")
                    .add("format", FORMAT)
                    .add("mod", modVersion)
                    .add("minecraft", "1.8.9")
                    .add("label", label)
                    .add("player", player)
                    .add("uuid", playerUuid)
                    .add("server", server)
                    .build());
            writer = started;
            return file;
        }
    }

    /** Ends the session: writes the end line and closes the file. Returns the file, or null if none. */
    File stop() throws IOException, InterruptedException {
        RecordingWriter stopped;
        synchronized (lock) {
            stopped = writer;
            if (stopped == null) {
                return null;
            }
            writer = null;
        }
        stopped.close(line("end")
                .add("ticks", tick)
                .add("dropped", stopped.dropped())
                .add("errors", errors.get())
                .build());
        return stopped.file();
    }

    /** A line of the given type with the common fields, for the caller to complete and submit. */
    JsonLine line(String type) {
        return JsonLine.of(type)
                .add("ms", clock.currentTimeMillis())
                .add("nano", clock.nanoTime())
                .add("tick", tick);
    }

    /** Counts a new client tick and returns its line. */
    JsonLine nextTick() {
        tick++;
        return line("tick");
    }

    void submit(JsonLine line) {
        RecordingWriter current = writer;
        if (current != null) {
            current.submit(line.build());
        }
    }

    void mark(String text) {
        submit(line("mark").add("text", text));
    }

    /** Something around the fight changed: server address, scoreboard title, connection. */
    void context(String key, String value) {
        submit(line("context").add("key", key).add("value", value));
    }

    /** A packet received ({@code inbound}) or sent, serialized exactly as on the wire after decompression. */
    void packet(boolean inbound, int id, String name, byte[] data) {
        submit(line("packet")
                .add("dir", inbound ? "in" : "out")
                .add("id", id)
                .add("name", name)
                .add("data", java.util.Base64.getEncoder().encodeToString(data)));
    }

    /** The entities whose movement packets are kept: the player and the players around. */
    void setTrackedEntities(int[] entityIds) {
        int[] sorted = entityIds.clone();
        Arrays.sort(sorted);
        trackedEntities = sorted;
    }

    boolean isTracked(int entityId) {
        return Arrays.binarySearch(trackedEntities, entityId) >= 0;
    }

    /** Never lets a recording problem reach the game: counts it, and writes the first few. */
    void error(String where, Throwable error) {
        long count = errors.incrementAndGet();
        if (count <= MAX_LOGGED_ERRORS) {
            submit(line("error").add("where", where).add("error", String.valueOf(error)));
        }
    }

    private File newFile(String label) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(clock.currentTimeMillis()));
        String base = "kb-" + stamp + (label.isEmpty() ? "" : "-" + fileSafe(label));
        File file = new File(directory, base + ".jsonl");
        for (int i = 2; file.exists(); i++) {
            file = new File(directory, base + "-" + i + ".jsonl");
        }
        return file;
    }

    static String fileSafe(String label) {
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < label.length() && safe.length() < 40; i++) {
            char c = label.charAt(i);
            boolean plain = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '-' || c == '_';
            safe.append(plain ? c : '_');
        }
        return safe.toString();
    }
}
