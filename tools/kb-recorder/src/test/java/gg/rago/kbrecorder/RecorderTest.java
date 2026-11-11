package gg.rago.kbrecorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecorderTest {

    /** 2026-11-07 12:00:00 UTC. */
    private static final long START_MILLIS = 1_794_052_800_000L;

    @TempDir
    File directory;

    private final FakeClock clock = new FakeClock();

    @Test
    void writesASessionWithItsLinesInOrder() throws Exception {
        Recorder recorder = new Recorder(new File(directory, "recordings"), clock);
        File file = recorder.start("premier hit", "Sofiene", "uuid", "mc.hypixel.net", "0.1.0");
        assertTrue(recorder.isRecording());
        assertTrue(file.getName().startsWith("kb-"));
        assertTrue(file.getName().endsWith("-premier_hit.jsonl"));

        recorder.submit(recorder.nextTick().add("x", 1.5));
        clock.advance(50);
        recorder.packet(true, 0x12, "entity_velocity", new byte[] {7, 0, 10, 0, 20, 0, 30});
        recorder.mark("combo 3");
        recorder.context("sidebar", "BED WARS");
        assertEquals(file, recorder.stop());
        assertFalse(recorder.isRecording());

        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        assertEquals(6, lines.size());
        assertTrue(lines.get(0).startsWith("{\"type\":\"session\",\"ms\":" + START_MILLIS + ",\"nano\":1000,\"tick\":0,\"format\":1,"));
        assertTrue(lines.get(0).contains("\"label\":\"premier hit\",\"player\":\"Sofiene\",\"uuid\":\"uuid\",\"server\":\"mc.hypixel.net\""));
        assertEquals("{\"type\":\"tick\",\"ms\":" + START_MILLIS + ",\"nano\":1000,\"tick\":1,\"x\":1.5}", lines.get(1));
        // RecorderCheckTest.MOD_VELOCITY_LINE in tools/kb-control reads this exact line: change both together.
        assertEquals("{\"type\":\"packet\",\"ms\":" + (START_MILLIS + 50) + ",\"nano\":50001000,\"tick\":1,"
                + "\"dir\":\"in\",\"id\":18,\"name\":\"entity_velocity\",\"data\":\"BwAKABQAHg==\"}", lines.get(2));
        assertTrue(lines.get(3).startsWith("{\"type\":\"mark\"") && lines.get(3).endsWith("\"text\":\"combo 3\"}"));
        assertTrue(lines.get(4).endsWith("\"key\":\"sidebar\",\"value\":\"BED WARS\"}"));
        assertTrue(lines.get(5).startsWith("{\"type\":\"end\""));
        assertTrue(lines.get(5).endsWith("\"ticks\":1,\"dropped\":0,\"errors\":0}"));
    }

    @Test
    void linesOutsideASessionAreIgnored() throws Exception {
        Recorder recorder = new Recorder(directory, clock);
        recorder.mark("nothing");
        recorder.packet(false, 0x02, "use_entity", new byte[] {1});
        assertNull(recorder.stop());
        assertEquals(0, directory.listFiles().length);
    }

    @Test
    void onlyOneSessionAtATimeAndEachGetsItsOwnFile() throws Exception {
        Recorder recorder = new Recorder(directory, clock);
        File first = recorder.start("", "p", "u", null, "0.1.0");
        assertThrows(IllegalStateException.class, () -> recorder.start("", "p", "u", null, "0.1.0"));
        recorder.stop();
        File second = recorder.start("", "p", "u", null, "0.1.0");
        recorder.stop();
        assertEquals(directory.toPath().resolve(first.getName().replace(".jsonl", "-2.jsonl")).toFile(), second);
    }

    @Test
    void tracksOnlyTheGivenEntities() {
        Recorder recorder = new Recorder(directory, clock);
        recorder.setTrackedEntities(new int[] {42, 7, 1000});
        assertTrue(recorder.isTracked(7));
        assertTrue(recorder.isTracked(1000));
        assertFalse(recorder.isTracked(8));
        assertFalse(recorder.isTracked(PacketBytes.NO_ENTITY));
    }

    @Test
    void errorsAreCountedAndOnlyTheFirstOnesWritten() throws Exception {
        Recorder recorder = new Recorder(directory, clock);
        File file = recorder.start("", "p", "u", null, "0.1.0");
        for (int i = 0; i < 15; i++) {
            recorder.error("tick", new IllegalStateException("boom " + i));
        }
        recorder.stop();
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        assertEquals(12, lines.size());
        assertTrue(lines.get(1).contains("\"where\":\"tick\",\"error\":\"java.lang.IllegalStateException: boom 0\""));
        assertTrue(lines.get(11).endsWith("\"errors\":15}"));
    }

    @Test
    void aFullQueueDropsLinesInsteadOfWaiting() throws Exception {
        File file = new File(directory, "small.jsonl");
        RecordingWriter writer = new RecordingWriter(file, 1);
        int accepted = 0;
        for (int i = 0; i < 10_000; i++) {
            if (writer.submit("{\"i\":" + i + "}")) {
                accepted++;
            }
        }
        writer.close("{\"type\":\"end\"}");
        assertEquals(10_000, accepted + writer.dropped());
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        assertEquals(accepted + 1, lines.size());
        assertEquals("{\"type\":\"end\"}", lines.get(lines.size() - 1));
    }

    @Test
    void labelsAreMadeSafeForFileNames() {
        assertEquals("combo_3_sprint_reset", Recorder.fileSafe("combo 3 sprint/reset"));
        assertEquals("d_j__vu", Recorder.fileSafe("déjà vu"));
        assertEquals(40, Recorder.fileSafe(new String(new char[100]).replace('\0', 'a')).length());
    }

    private static final class FakeClock implements Clock {
        private long millis = START_MILLIS;
        private long nanos = 1000;

        void advance(long ms) {
            millis += ms;
            nanos += ms * 1_000_000;
        }

        @Override
        public long nanoTime() {
            return nanos;
        }

        @Override
        public long currentTimeMillis() {
            return millis;
        }
    }
}
