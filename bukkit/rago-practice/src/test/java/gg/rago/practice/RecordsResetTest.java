package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The reset of the boards of a training whose rules changed (Block in, its new enemy): once, after
 * a copy of records.yml as it was, the other trainings' records kept.
 */
class RecordsResetTest {

    private static final UUID ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TWO = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Logger LOG = Logger.getLogger("records-test");

    @TempDir
    File folder;

    private Records records() {
        Records records = new Records(LOG, new File(folder, "records.yml"));
        records.load();
        return records;
    }

    @Test
    void theBlockInBoardsAreEmptiedOnceAfterACopyAndTheOthersKept() throws Exception {
        Records before = records();
        before.book().load("block-in.wood", ONE, "One", 9100);
        before.book().load("block-in.two", TWO, "Two", 7400);
        before.book().load("bridge-chrono.ninja", ONE, "One", 12000);
        before.save();
        byte[] original = Files.readAllBytes(new File(folder, "records.yml").toPath());

        Records records = records();
        assertEquals(2, records.resetOnce("block-in-2026-11", "block-in."));
        assertNull(records.book().best("block-in.wood", ONE));
        assertNull(records.book().best("block-in.two", TWO));
        assertEquals(12000L, records.book().best("bridge-chrono.ninja", ONE));
        // written at once: the file read again has the reset
        Records read = records();
        assertNull(read.book().best("block-in.wood", ONE));
        assertEquals(12000L, read.book().best("bridge-chrono.ninja", ONE));
        // the records of before, as they were
        assertArrayEquals(original, Files.readAllBytes(new File(folder, "records-sauvegarde-block-in-2026-11.yml").toPath()));
        assertTrue(new String(Files.readAllBytes(new File(folder, Records.RESETS_FILE).toPath()), StandardCharsets.UTF_8)
                .contains("block-in-2026-11"));
    }

    /** The owner's reset of every player's stats (23/11/2026): every board, after a copy, once. */
    @Test
    void theResetOfTheStatsEmptiesEveryBoardOnce() throws Exception {
        Records before = records();
        before.book().load("block-in.wood", ONE, "One", 9100);
        before.book().load("bridge-chrono.ninja", TWO, "Two", 12000);
        before.save();

        Records records = records();
        assertEquals(2, records.resetOnce("stats-2026-11", ""));
        assertNull(records.book().best("block-in.wood", ONE));
        assertNull(records.book().best("bridge-chrono.ninja", TWO));
        assertNull(records().book().best("bridge-chrono.ninja", TWO), "written at once");
        Records backup = new Records(LOG, new File(folder, "records-sauvegarde-stats-2026-11.yml"));
        backup.load();
        assertEquals(12000L, backup.book().best("bridge-chrono.ninja", TWO), "the copy of before");
        records().submit("bridge-chrono.ninja", ONE, "One", 15000);
        assertEquals(-1, records().resetOnce("stats-2026-11", ""), "once");
        assertEquals(15000L, records().book().best("bridge-chrono.ninja", ONE), "the times made after stay");
    }

    @Test
    void theTimesMadeAfterTheResetStayAtTheNextStarts() throws Exception {
        Records first = records();
        first.book().load("block-in.wood", ONE, "One", 9100);
        first.save();
        first = records();
        first.resetOnce("block-in-2026-11", "block-in.");
        first.submit("block-in.wood", TWO, "Two", 8800);

        Records next = records();
        assertEquals(-1, next.resetOnce("block-in-2026-11", "block-in."));
        assertEquals(8800L, next.book().best("block-in.wood", TWO));
        assertEquals(8800L, records().book().best("block-in.wood", TWO));
        // the copy is still the one of before the reset
        Records backup = new Records(LOG, new File(folder, "records-sauvegarde-block-in-2026-11.yml"));
        backup.load();
        assertEquals(9100L, backup.book().best("block-in.wood", ONE));
        assertNull(backup.book().best("block-in.wood", TWO));
    }
}
