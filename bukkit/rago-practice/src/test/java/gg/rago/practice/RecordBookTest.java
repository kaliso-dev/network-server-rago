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
