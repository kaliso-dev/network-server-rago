package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DailyChallengeTest {

    private static final UUID ANN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID CID = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    /** Noon in Paris on that day. */
    private static long noon(String day) {
        return ZonedDateTime.of(LocalDate.parse(day).atTime(12, 0), DailyChallenge.PARIS).toInstant().toEpochMilli();
    }

    private static DailyChallenge.Settings settings(String... boards) {
        return new DailyChallenge.Settings(true, Arrays.asList(boards), 100, 25, 7, Arrays.asList(750, 500, 300));
    }

    @Test
    void eachDayHasItsTrainingInTurnTheSameAllDayLong() {
        DailyChallenge.Settings settings = settings("block-in.wood", "clutch.island", "aim-trainer.strafe");
        Set<String> seen = new HashSet<>();
        String previous = null;
        for (int d = 0; d < 6; d++) {
            String day = LocalDate.parse("2026-11-11").plusDays(d).toString();
            DailyChallenge morning = new DailyChallenge(settings, noon(day) - 5 * 3600_000L);
            DailyChallenge evening = new DailyChallenge(settings, noon(day) + 11 * 3600_000L);
            assertEquals(day, morning.day());
            assertEquals(morning.board().id(), evening.board().id(), "a restart keeps the challenge of the day");
            assertNotEquals(previous, morning.board().id(), "two days in a row differ");
            previous = morning.board().id();
            seen.add(previous);
        }
        assertEquals(3, seen.size(), "every training comes in turn");
    }

    @Test
    void theDayFollowsParisNotUtc() {
        // 23:30 UTC on the 11th of November is 00:30 in Paris on the 12th
        long late = ZonedDateTime.parse("2026-11-11T23:30:00Z").toInstant().toEpochMilli();
        assertEquals("2026-11-12", DailyChallenge.dayKey(late));
        assertEquals(23 * 3600_000L + 30 * 60_000L, DailyChallenge.millisLeft(late));
    }

    @Test
    void theBestOfEachPlayerMakesTheTopTimesLowFirst() {
        DailyChallenge challenge = new DailyChallenge(settings("block-in.wood"), noon("2026-11-11"));
        assertEquals("block-in.wood", challenge.board().id());
        assertTrue(challenge.board().variant.timed);
        assertNull(challenge.submit("clutch.island", ANN, "Ann", 5), "another training does not count");
        DailyChallenge.Result first = challenge.submit("block-in.wood", ANN, "Ann", 9000);
        assertTrue(first.first && first.best);
        assertEquals(1, first.place);
        assertEquals(100, first.xp, "the participation XP, once a day");
        DailyChallenge.Result slower = challenge.submit("block-in.wood", ANN, "Ann", 9500);
        assertFalse(slower.first || slower.best);
        assertEquals(0, slower.xp);
        assertEquals(9000L, challenge.best(ANN));
        challenge.submit("block-in.wood", BOB, "Bob", 7000);
        DailyChallenge.Result faster = challenge.submit("block-in.wood", ANN, "Ann", 6500);
        assertTrue(faster.best);
        assertEquals(1, faster.place);
        assertEquals(2, challenge.place(BOB));
        assertEquals(0, challenge.place(CID));
        assertEquals(Arrays.asList(ANN, BOB), Arrays.asList(challenge.ranked().get(0).player, challenge.ranked().get(1).player));
        assertEquals("6.500 s", challenge.board().format(6500));
    }

    @Test
    void aCountRanksHighFirst() {
        DailyChallenge challenge = new DailyChallenge(settings("clutch.island"), noon("2026-11-11"));
        challenge.submit("clutch.island", ANN, "Ann", 12);
        challenge.submit("clutch.island", BOB, "Bob", 30);
        challenge.submit("clutch.island", ANN, "Ann", 8);
        assertEquals(12L, challenge.best(ANN), "a lower count is not a best");
        assertEquals(1, challenge.place(BOB));
        assertEquals("30 clutches", challenge.board().format(30));
    }

    @Test
    void theDayEndsWithItsTopAndANewChallenge() {
        DailyChallenge challenge = new DailyChallenge(settings("block-in.wood", "clutch.island"), noon("2026-11-11"));
        String first = challenge.board().id();
        challenge.submit(first, ANN, "Ann", 9000);
        challenge.submit(first, BOB, "Bob", 8000);
        assertNull(challenge.roll(noon("2026-11-11") + 3600_000L), "the same day goes on");
        DailyChallenge.Ended ended = challenge.roll(noon("2026-11-12"));
        assertNotNull(ended);
        assertEquals("2026-11-11", ended.day);
        assertEquals(first, ended.board.id());
        assertEquals(BOB, ended.top.get(0).player);
        assertEquals(ANN, ended.top.get(1).player);
        assertNotEquals(first, challenge.board().id());
        assertTrue(challenge.ranked().isEmpty());
        assertFalse(challenge.played(ANN));
        assertEquals(750, challenge.settings().podium(1));
        assertEquals(300, challenge.settings().podium(3));
        assertEquals(0, challenge.settings().podium(4));
    }

    @Test
    void daysInARowGiveMoreXpAndAMissedDayStartsAgain() {
        DailyChallenge.Settings settings = settings("clutch.island");
        DailyChallenge challenge = new DailyChallenge(settings, noon("2026-11-11"));
        int[] xp = new int[10];
        for (int d = 0; d < 9; d++) {
            challenge.roll(noon(LocalDate.parse("2026-11-11").plusDays(d).toString()));
            DailyChallenge.Result result = challenge.submit("clutch.island", ANN, "Ann", 5);
            assertEquals(d + 1, result.days);
            xp[d] = result.xp;
        }
        assertEquals(100, xp[0]);
        assertEquals(125, xp[1]);
        assertEquals(250, xp[6], "the 7th day: 100 + 6 x 25");
        assertEquals(250, xp[8], "and no more after");
        assertEquals(9, challenge.days(ANN));
        // a day without playing: still alive the day after it, gone after that
        challenge.roll(noon("2026-11-20"));
        assertEquals(9, challenge.days(ANN), "played yesterday: the streak still holds today");
        challenge.roll(noon("2026-11-21"));
        assertEquals(0, challenge.days(ANN));
        assertEquals(1, challenge.submit("clutch.island", ANN, "Ann", 5).days, "a missed day starts again from 1");
    }

    @Test
    void theFileKeepsTheDayAndTheStreaksAndRewardsADayThatEndedWhileOff() throws Exception {
        DailyChallenge.Settings settings = settings("block-in.wood", "clutch.island");
        DailyChallenge before = new DailyChallenge(settings, noon("2026-11-11"));
        String board = before.board().id();
        before.submit(board, ANN, "Ann", before.board().variant.timed ? 9000 : 10);
        before.submit(board, BOB, "Bob", before.board().variant.timed ? 8000 : 20);
        String text = before.toYaml();

        DailyChallenge sameDay = new DailyChallenge(settings, noon("2026-11-11") + 3600_000L);
        assertNull(sameDay.load(text));
        assertEquals(2, sameDay.ranked().size(), "a restart keeps the day's top");
        assertEquals(BOB, sameDay.ranked().get(0).player);
        assertEquals(1, sameDay.days(ANN));

        DailyChallenge nextDay = new DailyChallenge(settings, noon("2026-11-12"));
        DailyChallenge.Ended ended = nextDay.load(text);
        assertNotNull(ended, "the podium of a day that ended while the server was off still gets its XP");
        assertEquals("2026-11-11", ended.day);
        assertEquals(BOB, ended.top.get(0).player);
        assertTrue(nextDay.ranked().isEmpty());
        assertEquals(2, nextDay.submit(nextDay.board().id(), ANN, "Ann", 5).days, "the streak goes on across the restart");

        DailyChallenge later = new DailyChallenge(settings, noon("2026-11-14"));
        assertNotNull(later.load(text));
        assertEquals(0, later.days(ANN), "days missed while the server was off end the streak");
    }

    @Test
    void theShippedConfigIsValidAndEveryTrainingExists() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.load(new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertNull(DailyChallenge.Settings.check(config.getConfigurationSection("defi-du-jour")));
        DailyChallenge.Settings settings = DailyChallenge.Settings.of(config.getConfigurationSection("defi-du-jour"));
        assertTrue(settings.on);
        assertEquals(DailyChallenge.Settings.DEFAULT_BOARDS, settings.boards);
        for (String board : DailyChallenge.Settings.DEFAULT_BOARDS) {
            assertTrue(DailyChallenge.CATALOG.containsKey(board), board);
        }
        assertTrue(DailyChallenge.CATALOG.containsKey("aim-trainer." + AimTrainer.VARIANTS[2]));
        assertTrue(DailyChallenge.CATALOG.containsKey("combo-bot." + ComboAi.LEVELS[ComboAi.LEVELS.length - 1].id));
        assertEquals("Block in practice: 3 layers, wood side", DailyChallenge.CATALOG.get("block-in.wood").label());

        YamlConfiguration wrong = new YamlConfiguration();
        wrong.set("defi-du-jour.epreuves", Arrays.asList("block-in.wood", "bedwars.solo"));
        assertTrue(DailyChallenge.Settings.check(wrong.getConfigurationSection("defi-du-jour")).contains("\"bedwars.solo\""));
        wrong.set("defi-du-jour.epreuves", null);
        wrong.set("defi-du-jour.jours-de-suite-max", 0);
        assertNotNull(DailyChallenge.Settings.check(wrong.getConfigurationSection("defi-du-jour")));
        assertTrue(DailyChallenge.Settings.of(null).on, "without the section, the default challenge");
        assertEquals(DailyChallenge.Settings.DEFAULT_BOARDS, DailyChallenge.Settings.of(null).boards);
        assertFalse(DailyChallenge.Settings.OFF.on);
    }

    @Test
    void theTimeLeftReadsInHoursAndMinutes() {
        assertEquals("5h 12m", Challenges.left(5 * 3600_000L + 12 * 60_000L));
        assertEquals("1m", Challenges.left(10_000L));
        assertEquals("1h 0m", Challenges.left(3600_000L));
    }
}
