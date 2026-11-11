package gg.rago.tools.kbcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HitDelayCheckTest {

    private static final int VICTIM = 7;
    private static final long START = 1_700_000_000_000L;

    @TempDir
    Path directory;

    /** Hits {@code delay} ticks apart from the start of the burst, 50 ms per tick. */
    private static List<HitDelayCheck.Hit> regular(long start, long firstTick, int delay, int count) {
        List<HitDelayCheck.Hit> hits = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            hits.add(new HitDelayCheck.Hit(start + 50L * delay * i, firstTick + (long) delay * i, VICTIM));
        }
        return hits;
    }

    private static List<HitDelayCheck.Burst> threeBursts() {
        return Arrays.asList(
                new HitDelayCheck.Burst("vanilla-delay", 10, VICTIM, START, START + 3_000L, false),
                new HitDelayCheck.Burst("delay-9", 9, VICTIM, START + 10_000L, START + 13_000L, false),
                new HitDelayCheck.Burst("delay-9-after-respawn", 9, VICTIM, START + 20_000L, START + 23_000L, true));
    }

    private static List<HitDelayCheck.Hit> threeBurstsOfHits() {
        List<HitDelayCheck.Hit> hits = new ArrayList<>(regular(START, 100L, 10, 7));
        hits.addAll(regular(START + 10_000L, 400L, 9, 7));
        hits.addAll(regular(START + 20_000L, 700L, 9, 7));
        return hits;
    }

    @Test
    void hitsExactlyTheDelayApartPass() {
        HitDelayCheck.Result result = HitDelayCheck.check(threeBurstsOfHits(), threeBursts());

        assertTrue(result.passed(), result.toMarkdown());
        assertEquals(3, result.bursts.size());
        assertEquals(Integer.valueOf(6), result.bursts.get(1).intervals.get(9L));
        assertTrue(result.toMarkdown().contains("| delay-9 | 9 | 7 | 9: 6 | passed |"), result.toMarkdown());
    }

    @Test
    void aHitBeforeTheDelayFails() {
        List<HitDelayCheck.Hit> hits = threeBurstsOfHits();
        // A hit 8 ticks after the previous one in the 9-tick burst.
        hits.add(new HitDelayCheck.Hit(START + 10_000L + 50L * 62, 462L, VICTIM));

        HitDelayCheck.Result result = HitDelayCheck.check(hits, threeBursts());

        assertFalse(result.passed());
        assertTrue(result.bursts.get(1).problems.get(0).contains("8 ticks apart"), result.toMarkdown());
    }

    @Test
    void theVanillaDelayInsteadOfNineFails() {
        List<HitDelayCheck.Hit> hits = new ArrayList<>(regular(START, 100L, 10, 7));
        hits.addAll(regular(START + 10_000L, 400L, 10, 7));
        hits.addAll(regular(START + 20_000L, 700L, 9, 7));

        HitDelayCheck.Result result = HitDelayCheck.check(hits, threeBursts());

        assertFalse(result.passed());
        assertTrue(result.bursts.get(1).problems.get(0).contains("0% of the intervals are 9 ticks"),
                result.toMarkdown());
    }

    @Test
    void aFewLongerIntervalsAreAccepted() {
        List<HitDelayCheck.Hit> hits = new ArrayList<>(regular(START, 100L, 10, 7));
        // 9, 9, 9, 10, 9, 9: one slow tick out of six intervals.
        long[] ticks = {400, 409, 418, 427, 437, 446, 455};
        for (int i = 0; i < ticks.length; i++) {
            hits.add(new HitDelayCheck.Hit(START + 10_000L + 450L * i, ticks[i], VICTIM));
        }
        hits.addAll(regular(START + 20_000L, 700L, 9, 7));

        HitDelayCheck.Result result = HitDelayCheck.check(hits, threeBursts());

        assertTrue(result.passed(), result.toMarkdown());
        assertEquals(Integer.valueOf(1), result.bursts.get(1).intervals.get(10L));
    }

    @Test
    void tooFewHitsAndHitsOnOtherPlayersFail() {
        List<HitDelayCheck.Hit> hits = new ArrayList<>(regular(START, 100L, 10, 7));
        hits.addAll(regular(START + 10_000L, 400L, 9, 3));
        for (HitDelayCheck.Hit hit : regular(START + 20_000L, 700L, 9, 7)) {
            hits.add(new HitDelayCheck.Hit(hit.time, hit.tick, VICTIM + 1));
        }

        HitDelayCheck.Result result = HitDelayCheck.check(hits, threeBursts());

        assertFalse(result.passed());
        assertTrue(result.bursts.get(1).problems.get(0).contains("3 hits, at least 5"), result.toMarkdown());
        assertTrue(result.bursts.get(2).problems.get(0).contains("0 hits"), result.toMarkdown());
        assertTrue(result.problems.contains("no passing burst with a non-vanilla delay after the victim respawned"),
                result.toMarkdown());
    }

    @Test
    void aMissingBurstOrABotErrorFails() {
        HitDelayCheck.Result noRespawn = HitDelayCheck.check(threeBurstsOfHits(), threeBursts().subList(0, 2));
        assertFalse(noRespawn.passed());

        List<HitDelayCheck.Burst> withError = new ArrayList<>(threeBursts());
        withError.add(HitDelayCheck.Burst.failed("the victim did not respawn"));
        HitDelayCheck.Result error = HitDelayCheck.check(threeBurstsOfHits(), withError);
        assertFalse(error.passed());
        assertTrue(error.problems.contains("the bots stopped: the victim did not respawn"), error.toMarkdown());
    }

    @Test
    void readsTheEngineLogAndTheBotLines() throws IOException {
        Path hits = Files.createDirectory(directory.resolve("hits"));
        Files.write(hits.resolve("hits-2026-11-07.jsonl"),
                (LogFormatTest.ENGINE_LINE + "\n").getBytes(StandardCharsets.UTF_8));
        Path bursts = directory.resolve("bursts.jsonl");
        Files.write(bursts, Arrays.asList(
                "{\"type\":\"burst\",\"name\":\"delay-9\",\"delayTicks\":9,\"victimEntityId\":7,"
                        + "\"start\":1700000000000,\"end\":1700000003000,\"afterRespawn\":true}",
                "{\"type\":\"done\",\"time\":1700000004000}"), StandardCharsets.UTF_8);

        List<HitDelayCheck.Hit> read = HitDelayCheck.readHits(hits);
        List<HitDelayCheck.Burst> readBursts = HitDelayCheck.readBursts(bursts);

        assertEquals(1, read.size());
        assertEquals(1700000000123L, read.get(0).time);
        assertEquals(42L, read.get(0).tick);
        assertEquals(7, read.get(0).victimEntityId);
        assertEquals(1, readBursts.size());
        HitDelayCheck.Burst burst = readBursts.get(0);
        assertEquals("delay-9", burst.name);
        assertEquals(9, burst.delayTicks);
        assertEquals(1700000003000L, burst.end);
        assertTrue(burst.afterRespawn);
    }
}
