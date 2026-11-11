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
}
