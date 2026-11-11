package gg.rago.kbrecorder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Writes the lines of one recording on its own thread: the game thread and the network threads only
 * put lines in a queue and never wait for the disk.
 */
final class RecordingWriter {

    /** About a minute of a busy fight; past that, lines are dropped and counted rather than waiting. */
    static final int QUEUE_CAPACITY = 200_000;

    private static final String END_OF_STREAM = new String("end of stream");
    private static final long FLUSH_AFTER_IDLE_MILLIS = 200;

    private final File file;
    private final BlockingQueue<String> queue;
    private final AtomicLong dropped = new AtomicLong();
    private final Thread thread;
    private volatile IOException failure;

    RecordingWriter(File file) throws IOException {
        this(file, QUEUE_CAPACITY);
    }

    RecordingWriter(File file, int capacity) throws IOException {
        this.file = file;
        this.queue = new LinkedBlockingQueue<>(capacity);
        Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
        this.thread = new Thread(() -> drain(out), "Rago KB recorder writer");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    File file() {
        return file;
    }

    /** Queues a line without ever blocking; returns false and counts it when the queue is full. */
    boolean submit(String line) {
        if (queue.offer(line)) {
            return true;
        }
        dropped.incrementAndGet();
        return false;
    }

    long dropped() {
        return dropped.get();
    }

    /** Writes the last line, waits until everything is on disk and closes the file. */
    void close(String lastLine) throws IOException, InterruptedException {
        // Bounded waits: if the writer thread died on a disk error, nobody empties the queue.
        if (!queue.offer(lastLine, 2, TimeUnit.SECONDS)) {
            dropped.incrementAndGet();
        }
        queue.offer(END_OF_STREAM, 2, TimeUnit.SECONDS);
        thread.join(TimeUnit.SECONDS.toMillis(10));
        if (failure != null) {
            throw failure;
        }
    }

    private void drain(Writer out) {
        try {
            while (true) {
                String line = queue.poll(FLUSH_AFTER_IDLE_MILLIS, TimeUnit.MILLISECONDS);
                if (line == null) {
                    out.flush();
                } else if (line == END_OF_STREAM) {
                    break;
                } else {
                    out.write(line);
                    out.write('\n');
                }
            }
        } catch (IOException e) {
            failure = e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                }
            }
        }
    }
}
