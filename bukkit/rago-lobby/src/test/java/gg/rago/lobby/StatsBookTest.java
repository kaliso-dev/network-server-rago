package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.game.GameMode;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatsBookTest {

    private static final GameMode SUMO = new GameMode("sumo", "Sumo", 2, 1, false);
    private static final GameMode RANKED = new GameMode("ranked-1v1", "Ranked 1v1", 2, 1, true);
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID CARL = new UUID(0, 3);
    /** Saturday 7 November 2026, 22:00 in Paris. */
    private static final long SATURDAY = at(2026, 11, 7, 22, 0);
    private static final long SUNDAY = at(2026, 11, 8, 10, 0);
    private static final long MONDAY = at(2026, 11, 9, 10, 0);

    private static long at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, StatsPeriod.ZONE).toInstant().toEpochMilli();
    }

    private static final StatsBook.Names NAMES = id -> id.equals(ALICE) ? "Alice" : id.equals(BOB) ? "Bob" : "Carl";

    private static void win(StatsBook book, GameMode mode, UUID winner, UUID loser, long millis) {
        book.record(mode, Collections.singletonList(winner), Collections.singletonList(loser), NAMES, millis);
    }

    @Test
    void countsWinsLossesAndStreaks() {
        StatsBook book = new StatsBook(1000);
        win(book, SUMO, ALICE, BOB, SATURDAY);
        win(book, SUMO, ALICE, BOB, SATURDAY);
        win(book, SUMO, BOB, ALICE, SATURDAY);
        win(book, SUMO, ALICE, BOB, SATURDAY);
        ModeStats alice = book.get(ALICE).mode("sumo");
        assertEquals(3, alice.wins);
        assertEquals(1, alice.losses);
        assertEquals(1, alice.streak);
        assertEquals(2, alice.bestStreak);
        assertEquals("Bob", book.get(BOB).name());
        assertEquals(1, book.get(BOB).wins(StatsPeriod.ALL, null, "", ""));
        assertEquals(3, book.get(BOB).losses());
        assertNull(book.get(CARL));
    }

    @Test
    void dayAndWeekCountersStartAgain() {
        StatsBook book = new StatsBook(1000);
        win(book, SUMO, ALICE, BOB, SATURDAY);
        win(book, SUMO, ALICE, BOB, SUNDAY);
        PlayerStats alice = book.get(ALICE);
        String sunday = StatsPeriod.dayKey(SUNDAY);
        String week45 = StatsPeriod.weekKey(SUNDAY);
        assertEquals(1, alice.wins(StatsPeriod.DAY, null, sunday, week45));
        assertEquals(2, alice.wins(StatsPeriod.WEEK, null, sunday, week45));
        // Monday is in a new week: nothing yet, until the next game.
        String monday = StatsPeriod.dayKey(MONDAY);
        String week46 = StatsPeriod.weekKey(MONDAY);
        assertEquals(0, alice.wins(StatsPeriod.DAY, null, monday, week46));
        assertEquals(0, alice.wins(StatsPeriod.WEEK, null, monday, week46));
        assertEquals(2, alice.wins(StatsPeriod.ALL, null, monday, week46));
        assertEquals(0, book.ranking(BoardStat.WINS, StatsPeriod.DAY, null, MONDAY).rows().size());
        win(book, SUMO, ALICE, BOB, MONDAY);
        assertEquals(1, alice.wins(StatsPeriod.WEEK, null, monday, week46));
        assertEquals(3, alice.mode("sumo").streak, "a streak goes on from one day to the next");
        assertEquals(3, alice.best(StatsPeriod.DAY, null, monday, week46));
    }

    @Test
    void ranksBestFirstAndLeavesZerosOut() {
        StatsBook book = new StatsBook(1000);
        win(book, SUMO, BOB, CARL, SUNDAY);
        win(book, SUMO, ALICE, CARL, SUNDAY);
        win(book, SUMO, ALICE, CARL, SUNDAY);
        StatsBook.Ranking wins = book.ranking(BoardStat.WINS, StatsPeriod.DAY, null, SUNDAY);
        assertEquals(Arrays.asList("Alice", "Bob"), names(wins.rows()));
        assertEquals(2, wins.rows().get(0).value());
        assertEquals(1, wins.position(ALICE));
        assertEquals(0, wins.position(CARL), "no win, not on the board");
        StatsBook.Ranking games = book.ranking(BoardStat.GAMES, StatsPeriod.ALL, null, SUNDAY);
        assertEquals(Arrays.asList("Carl", "Alice", "Bob"), names(games.rows()));
        // A new game is counted at once.
        win(book, SUMO, BOB, ALICE, SUNDAY);
        win(book, SUMO, BOB, ALICE, SUNDAY);
        assertEquals(Arrays.asList("Bob", "Alice"), names(book.ranking(BoardStat.WINS, StatsPeriod.DAY, null, SUNDAY).rows()));
        assertEquals(Arrays.asList("Bob", "Alice"), names(book.ranking(BoardStat.STREAK, StatsPeriod.DAY, null, SUNDAY).rows()));
    }

    @Test
    void tiesKeepTheAlphabeticalOrder() {
        StatsBook book = new StatsBook(1000);
        win(book, SUMO, CARL, BOB, SUNDAY);
        win(book, SUMO, ALICE, BOB, SUNDAY);
        assertEquals(Arrays.asList("Alice", "Carl"), names(book.ranking(BoardStat.WINS, StatsPeriod.ALL, null, SUNDAY).rows()));
    }

    @Test
    void rankedGamesMoveTheElo() {
        StatsBook book = new StatsBook(1000);
        assertEquals(1000, book.elo(ALICE, "ranked-1v1"));
        win(book, RANKED, ALICE, BOB, SUNDAY);
        assertEquals(1016, book.elo(ALICE, "ranked-1v1"));
        assertEquals(984, book.elo(BOB, "ranked-1v1"));
        win(book, RANKED, ALICE, BOB, SUNDAY);
        assertTrue(book.elo(ALICE, "ranked-1v1") < 1032, "beating a weaker player gives less");
        assertEquals(2000, book.elo(ALICE, "ranked-1v1") + book.elo(BOB, "ranked-1v1"));
        StatsBook.Ranking elo = book.ranking(BoardStat.ELO, StatsPeriod.DAY, "ranked-1v1", SUNDAY);
        assertEquals(Arrays.asList("Alice", "Bob"), names(elo.rows()));
        // An unranked mode does not touch the ELO.
        win(book, SUMO, BOB, ALICE, SUNDAY);
        assertEquals(0, book.get(BOB).mode("sumo").elo);
    }

    @Test
    void keepsTheNameUpToDate() {
        StatsBook book = new StatsBook(1000);
        win(book, SUMO, ALICE, BOB, SUNDAY);
        assertNull(book.seen(ALICE, "Alice"));
        assertEquals("Alicia", book.seen(ALICE, "Alicia").name());
        assertEquals("Alicia", book.ranking(BoardStat.WINS, StatsPeriod.ALL, null, SUNDAY).rows().get(0).name());
        assertNull(book.seen(CARL, "Carl"), "no stats, nothing to rename");
    }

    @Test
    void filesKeepEverything() throws Exception {
        StatsBook book = new StatsBook(1000);
        win(book, SUMO, ALICE, BOB, SATURDAY);
        win(book, RANKED, ALICE, BOB, SUNDAY);
        PlayerStats alice = book.get(ALICE);
        PlayerStats read = StatsFiles.fromYaml(ALICE, StatsFiles.toYaml(alice));
        assertEquals("Alice", read.name());
        assertEquals(StatsFiles.toYaml(alice), StatsFiles.toYaml(read));
        assertEquals(1016, read.mode("ranked-1v1").elo);
        assertEquals(0, read.mode("sumo").elo);
        StatsBook loaded = new StatsBook(1000);
        assertTrue(loaded.load(read));
        assertEquals(1016, loaded.elo(ALICE, "ranked-1v1"));
    }

    private static List<String> names(List<StatsBook.Row> rows) {
        List<String> names = new java.util.ArrayList<>();
        for (StatsBook.Row row : rows) {
            names.add(row.name());
        }
        return names;
    }
}
