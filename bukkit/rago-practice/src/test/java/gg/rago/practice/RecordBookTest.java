package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordBookTest {

    private static final UUID ONE = UUID.randomUUID();
    private static final UUID TWO = UUID.randomUUID();

    @Test
    void aTimeIsBetterWhenItIsShorter() {
        RecordBook book = new RecordBook();
        book.define("bridge-chrono.ninja", true);
        RecordBook.Result first = book.submit("bridge-chrono.ninja", ONE, "One", 9000);
        assertTrue(first.personalBest);
        assertTrue(first.boardBest);
        assertNull(first.previous);
        RecordBook.Result slower = book.submit("bridge-chrono.ninja", ONE, "One", 9500);
        assertFalse(slower.personalBest);
        assertEquals(9000L, slower.previous);
        assertEquals(9000L, book.best("bridge-chrono.ninja", ONE));
        RecordBook.Result faster = book.submit("bridge-chrono.ninja", ONE, "One", 8000);
        assertTrue(faster.personalBest);
        assertEquals(8000L, book.best("bridge-chrono.ninja", ONE));
    }

    @Test
    void aCountIsBetterWhenItIsHigher() {
        RecordBook book = new RecordBook();
        book.define("clutch.single", false);
        book.submit("clutch.single", ONE, "One", 5);
        RecordBook.Result two = book.submit("clutch.single", TWO, "Two", 12);
        assertTrue(two.boardBest);
        assertEquals("Two", book.top("clutch.single").name);
        assertEquals(1, book.rank("clutch.single", TWO));
        assertEquals(2, book.rank("clutch.single", ONE));
        RecordBook.Result less = book.submit("clutch.single", ONE, "One", 3);
        assertFalse(less.personalBest);
        assertFalse(less.boardBest);
        assertEquals(5L, book.best("clutch.single", ONE));
    }

    @Test
    void theBoardsOfEachSettingFoldIntoOneEachPlayerKeepingTheirBest() {
        RecordBook book = new RecordBook();
        book.define("clutch.island", false);
        book.load("clutch.island.kb3.hits1", ONE, "One", 4);
        book.load("clutch.island.kb5.hits2.stick", ONE, "One", 9);
        book.load("clutch.island.kb1.hits1", TWO, "Two", 6);
        book.load("clutch.island-blocks", TWO, "Two", 43);
        book.load("clutch-top-fight.top.kb3.hits1", TWO, "Two", 20);
        book.submit("clutch.island", TWO, "Two", 2);
        assertEquals(3, book.fold("clutch.island"));
        assertEquals(9L, book.best("clutch.island", ONE));
        assertEquals(6L, book.best("clutch.island", TWO));
        assertEquals("One", book.ranked("clutch.island").get(0).name);
        assertFalse(book.boards().containsKey("clutch.island.kb3.hits1"), "the old boards are gone from the file");
        assertEquals(43L, book.best("clutch.island-blocks", TWO), "another board stays");
        assertEquals(20L, book.best("clutch-top-fight.top.kb3.hits1", TWO));
        assertEquals(0, book.fold("clutch.island"), "once is enough");
    }

    @Test
    void aBoardGoesIntoAnotherAndTheWeeksBeforeAreGone() {
        RecordBook book = new RecordBook();
        book.load("clutch.island-blocks", ONE, "One", 63);
        book.load("clutch.island-blocks.2026w45", TWO, "Two", 80);
        book.submit("clutch.island-blocks.2026w46", ONE, "One", 40);
        book.merge("clutch.island-blocks", "clutch.island-blocks.2026w46");
        assertEquals(63L, book.best("clutch.island-blocks.2026w46", ONE));
        assertFalse(book.boards().containsKey("clutch.island-blocks"));
        book.load("clutch.island", ONE, "One", 5);
        assertEquals(1, book.dropAllBut("clutch.island-blocks.", "clutch.island-blocks.2026w46"));
        assertNull(book.best("clutch.island-blocks.2026w45", TWO));
        assertEquals(63L, book.best("clutch.island-blocks.2026w46", ONE));
        assertEquals(5L, book.best("clutch.island", ONE), "another board stays");
    }

    @Test
    void boardsAreApart() {
        RecordBook book = new RecordBook();
        book.define("clutch.single", false);
        book.define("clutch.double", false);
        book.submit("clutch.single", ONE, "One", 5);
        assertNull(book.best("clutch.double", ONE));
        assertNull(book.top("clutch.double"));
        assertEquals(0, book.rank("clutch.double", ONE));
    }

    @Test
    void aRenamedPlayerKeepsTheirBestUnderTheNewName() {
        RecordBook book = new RecordBook();
        book.define("aim-trainer.strafe", false);
        book.submit("aim-trainer.strafe", ONE, "Old", 40);
        book.submit("aim-trainer.strafe", ONE, "New", 30);
        assertEquals("New", book.top("aim-trainer.strafe").name);
        assertEquals(40L, book.best("aim-trainer.strafe", ONE));
    }

    @Test
    void theBoardsOfTheWeeks() {
        assertEquals("weekly.2026w47.clutch.island", RecordBook.weekly("clutch.island", "2026w47"));
        assertEquals("block-edit.bot.speed2", RecordBook.weeklyBase("weekly.2026w47.block-edit.bot.speed2"));
        assertEquals("2026w47", RecordBook.weekOf("weekly.2026w47.block-edit.bot.speed2"));
        assertNull(RecordBook.weeklyBase("clutch.island"), "a board of records is no board of a week");
        assertNull(RecordBook.weekOf("clutch.island-blocks.2026w47"), "nor the biggest clutches of a week");
        assertNull(RecordBook.weeklyBase("weekly.2026w47"));
        assertNull(RecordBook.weekOf("weekly..clutch.island"));

        // the weeks from Monday, Paris dates: Sunday 22 November is still the 47th, Monday the 48th
        assertEquals("2026w47", RecordBook.week(java.time.LocalDate.of(2026, 11, 22)));
        assertEquals("2026w48", RecordBook.week(java.time.LocalDate.of(2026, 11, 23)));
        assertEquals("2026w53", RecordBook.week(java.time.LocalDate.of(2027, 1, 1)), "the week of its Thursday's year");
        assertEquals(java.util.Arrays.asList("2026w47", "2026w46", "2026w45", "2026w44", "2026w43", "2026w42"),
                RecordBook.weeksKept(java.time.LocalDate.of(2026, 11, 20)), "this week and five before: at least a month");
        assertEquals(RecordBook.WEEKS_KEPT + 1, RecordBook.weeksKept(java.time.LocalDate.of(2027, 1, 4)).size());
        assertEquals("2026w49", RecordBook.weeksKept(java.time.LocalDate.of(2027, 1, 4)).get(5), "over the turn of the year");
    }

    @Test
    void aBoardOfAWeekRanksAsItsBoard() {
        RecordBook book = new RecordBook();
        book.define("bridge-chrono.ninja", true);
        String week = RecordBook.weekly("bridge-chrono.ninja", "2026w47");
        book.submit(week, ONE, "One", 9000);
        assertTrue(book.submit(week, TWO, "Two", 8000).boardBest, "a time: the shortest is the best of the week too");
        assertEquals("Two", book.top(week).name);
        assertFalse(book.submit(week, ONE, "One", 9500).personalBest);
    }

    @Test
    void onlyTheWeeksNoLongerKeptAreDropped() {
        RecordBook book = new RecordBook();
        book.load("clutch.island", ONE, "One", 30);
        book.load("weekly.2026w47.clutch.island", ONE, "One", 12);
        book.load("weekly.2026w42.clutch.island", ONE, "One", 20);
        book.load("weekly.2026w41.clutch.island", TWO, "Two", 25);
        book.load("weekly.2026w41.block-in.wood", TWO, "Two", 9000);
        book.load("clutch.island-blocks.2026w40", ONE, "One", 52);
        assertEquals(2, book.dropWeeksBut(RecordBook.weeksKept(java.time.LocalDate.of(2026, 11, 20))));
        assertNull(book.best("weekly.2026w41.clutch.island", TWO), "six weeks ago: gone");
        assertNull(book.best("weekly.2026w41.block-in.wood", TWO));
        assertEquals(20L, book.best("weekly.2026w42.clutch.island", ONE), "five weeks ago: kept");
        assertEquals(12L, book.best("weekly.2026w47.clutch.island", ONE));
        assertEquals(30L, book.best("clutch.island", ONE), "a lifetime record is never dropped");
        assertEquals(52L, book.best("clutch.island-blocks.2026w40", ONE), "the biggest clutches keep their own weeks");

        book.load("weekly.2026w47.block-in.wood", TWO, "Two", 9000);
        book.load("block-in.wood", TWO, "Two", 9000);
        assertEquals(2, book.dropWeeksOf("clutch."), "the weeks of the boards of a training");
        assertNull(book.best("weekly.2026w47.clutch.island", ONE));
        assertEquals(9000L, book.best("weekly.2026w47.block-in.wood", TWO));
        assertEquals(30L, book.best("clutch.island", ONE), "not its lifetime board");
    }
}
