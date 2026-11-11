package gg.rago.combat.engine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Appends hit records to {@code hits/hits-YYYY-MM-DD.jsonl} on a background thread, so that the
 * main thread never waits for the disk. If the disk cannot keep up, records are dropped and
 * counted rather than slowing the server down.
 */
final class HitRecorder implements AutoCloseable {

    private static final int QUEUE_CAPACITY = 10_000;

    private final Path directory;
    private final Logger logger;
    private final AtomicLong dropped = new AtomicLong();
    private final ThreadPoolExecutor executor;

    // Only touched by the executor thread.
    private BufferedWriter writer;
    private LocalDate writerDate;
    private boolean failed;

    HitRecorder(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "RagoCombatEngine hit recorder");
                    thread.setDaemon(true);
                    return thread;
                },
                (runnable, pool) -> dropped.incrementAndGet());
    }

    void record(HitRecord record) {
        executor.execute(() -> write(record));
    }

    long droppedCount() {
        return dropped.get();
    }

    private void write(HitRecord record) {
        if (failed) {
            dropped.incrementAndGet();
            return;
        }
        try {
            LocalDate today = LocalDate.now();
            if (writer == null || !today.equals(writerDate)) {
                closeWriter();
                Files.createDirectories(directory);
                writer = Files.newBufferedWriter(directory.resolve("hits-" + today + ".jsonl"),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                writerDate = today;
            }
            writer.write(record.toJson());
            writer.newLine();
            writer.flush();
        } catch (IOException | RuntimeException e) {
            failed = true;
            dropped.incrementAndGet();
            logger.log(Level.SEVERE, "Hit log disabled after a write error in " + directory, e);
        }
    }

    private void closeWriter() throws IOException {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("Hit log: some records were still waiting when the plugin stopped");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        try {
            // The executor is stopped, so this thread is now the only one using the writer.
            closeWriter();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Hit log: could not close the file", e);
        }
    }
}
