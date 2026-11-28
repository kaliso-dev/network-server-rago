package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The records of the weeks (plan 3.3): a score on a board of records also goes on its board of the
 * week; the weeks are kept five weeks after theirs, at least a month; the lifetime records stay.
 */
class RecordsWeeksTest {

    private static final UUID ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TWO = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Logger LOG = Logger.getLogger("records-weeks-test");

    @TempDir
    File folder;

    /** The days the book lives, Friday 20 November 2026 (week 47) first. */
    private final AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 11, 20));

    private Records records() {
        Records records = new Records(LOG, new File(folder, "records.yml"), today::get);
        records.load();
        RecordCatalog.define(records.book());
        return records;
    }

    @Test
    void aScoreGoesOnTheBoardOfRecordsAndOnItsWeek() {
        Records records = records();
        assertTrue(records.submit("clutch.island", ONE, "One", 12).personalBest);
        records.submit("clutch.island", ONE, "One", 9);
        assertEquals(12L, records.book().best("clutch.island", ONE));
        assertEquals(12L, records.book().best("weekly.2026w47.clutch.island", ONE));

        // Monday: a new week, which starts empty; the lifetime best stays
        today.set(LocalDate.of(2026, 11, 23));
        RecordBook.Result result = records.submit("clutch.island", ONE, "One", 7);
        assertFalse(result.personalBest, "the result is the lifetime one");
        assertEquals(12L, result.previous);
        assertEquals(7L, records.book().best("weekly.2026w48.clutch.island", ONE), "the best of the new week");
        assertEquals(12L, records.book().best("weekly.2026w47.clutch.island", ONE), "last week kept");
        assertEquals("2026w48", records.weeks().get(0));

        // a time: the shortest is the best of the week too
        records.submit("block-in.wood", TWO, "Two", 9000);
        records.submit("block-in.wood", TWO, "Two", 8000);
        records.submit("block-in.wood", TWO, "Two", 8500);
        assertEquals(8000L, records.book().best("weekly.2026w48.block-in.wood", TWO));
    }

    @Test
    void onlyTheBoardsOfRecordsHaveWeeks() {
        Records records = records();
        records.submit("clutch.island-blocks.2026w47", ONE, "One", 52);
        assertEquals(52L, records.book().best("clutch.island-blocks.2026w47", ONE));
        assertFalse(records.book().boards().containsKey("weekly.2026w47.clutch.island-blocks.2026w47"),
                "the biggest clutches of a week are already a week's");
    }

    @Test
    void theWeeksAreInTheFileAndGoFiveWeeksAfterTheirs() {
        Records records = records();
        records.submit("clutch.island", ONE, "One", 12);
        records.submit("block-in.wood", TWO, "Two", 9100);
        Records read = records();
        assertEquals(12L, read.book().best("weekly.2026w47.clutch.island", ONE), "written, read back with its dots");
        assertEquals(9100L, read.book().best("weekly.2026w47.block-in.wood", TWO));

        // five weeks after (Friday 25 December, week 52): still there, at least a month
        today.set(LocalDate.of(2026, 12, 25));
        Records later = records();
        assertEquals(12L, later.book().best("weekly.2026w47.clutch.island", ONE));
        // six weeks after (Monday 28 December, week 53): gone from the book and from the file
        today.set(LocalDate.of(2026, 12, 28));
        Records gone = records();
        assertNull(gone.book().best("weekly.2026w47.clutch.island", ONE));
        assertNull(gone.book().best("weekly.2026w47.block-in.wood", TWO));
        assertEquals(12L, gone.book().best("clutch.island", ONE), "the lifetime records are kept");
        assertEquals(9100L, gone.book().best("block-in.wood", TWO));
        Records again = records();
        assertFalse(again.book().boards().containsKey("weekly.2026w47.clutch.island"), "written without them");
        assertEquals(12L, again.book().best("clutch.island", ONE));
    }

    @Test
    void aWeekGoesWhileTheServerRuns() {
        Records records = records();
        records.submit("clutch.island", ONE, "One", 12);
        today.set(LocalDate.of(2026, 12, 28));
        records.submit("clutch.island", TWO, "Two", 3);
        assertNull(records.book().best("weekly.2026w47.clutch.island", ONE), "six weeks after: gone at the next score");
        assertEquals(3L, records.book().best("weekly.2026w53.clutch.island", TWO));
        assertEquals(12L, records.book().best("clutch.island", ONE));
    }
}
