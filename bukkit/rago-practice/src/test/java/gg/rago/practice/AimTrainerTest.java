package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

/** The aim trainer: what counts as a second on the target, the XP it gives, and the top. */
class AimTrainerTest {

    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID CARL = new UUID(0, 3);

    @Test
    void theCrosshairIsOnTheTargetWhenTheLookMeetsItsBox() {
        // the eye 1.62 over the floor, the target 3 blocks ahead on x
        assertTrue(AimTrainer.looksAt(0, 1.62, 0, 1, 0, 0, 3, 0, 0), "straight at its chest");
        assertTrue(AimTrainer.looksAt(0, 1.62, 0, 1, 0, 0.12, 3, 0, 0), "a little to the side, still on it");
        assertFalse(AimTrainer.looksAt(0, 1.62, 0, 1, 0, 0.3, 3, 0, 0), "beside it");
        assertFalse(AimTrainer.looksAt(0, 1.62, 0, -1, 0, 0, 3, 0, 0), "behind the player");
        assertFalse(AimTrainer.looksAt(0, 1.62, 0, 1, 0.5, 0, 3, 0, 0), "over its head");
        assertTrue(AimTrainer.looksAt(0, 1.62, 0, 1, -0.4, 0, 3, 0, 0), "at its legs");
        assertFalse(AimTrainer.looksAt(0, 1.62, 0, 1, 0, 0, 12, 0, 0), "too far");
        assertFalse(AimTrainer.looksAt(0, 1.62, 0, 0, 0, 0, 3, 0, 0), "no direction");
        // circling: the target on z, the player looking along z
        assertTrue(AimTrainer.looksAt(0, 1.62, 0, 0, 0, -1, 0, 0, -2.8));
    }

    @Test
    void aSecondCountsOnTheTargetOrWithAHit() {
        assertTrue(AimTrainer.secondCounts(AimTrainer.LOOK_TICKS, 0), "most of the second on it");
        assertFalse(AimTrainer.secondCounts(AimTrainer.LOOK_TICKS - 1, 0));
        assertTrue(AimTrainer.secondCounts(0, 1), "one hit is enough");
        assertFalse(AimTrainer.secondCounts(0, 0), "off the target: nothing");
    }

    @Test
    void theXpIsTheSecondsOnTheTargetThenTheScore() {
        assertEquals(30 + 10, AimTrainer.xpOf(30, 1, 100, 10));
        assertEquals(24 + 4, AimTrainer.xpOf(24, 1, 49, 10), "one for each full ten hits");
        assertEquals(0, AimTrainer.xpOf(0, 1, 9, 10));
        assertEquals(60, AimTrainer.xpOf(30, 2, 0, 10));
        assertTrue(AimTrainer.pitchOf(1) < AimTrainer.pitchOf(5), "the sound rises with the streak");
        assertEquals(2f, AimTrainer.pitchOf(40), 1e-6, "up to the highest pitch");
    }

    @Test
    void theTopShowsTheBestThenThePlayersOwnPlace() {
        RecordBook book = new RecordBook();
        String board = "aim-trainer.strafe";
        book.define(board, false);
        assertEquals(2, AimTrainer.topLines(book, board, "Strafing target", ALICE, 5).size(), "a title, nobody yet");
        book.submit(board, ALICE, "Alice", 40);
        book.submit(board, BOB, "Bob", 87);
        book.submit(board, CARL, "Carl", 12);
        List<String> top = AimTrainer.topLines(book, board, "Strafing target", CARL, 2);
        assertEquals(4, top.size(), "a title, the two best, then Carl's own place");
        assertTrue(ChatColor.stripColor(top.get(1)).contains("#1 Bob - 87 hits"));
        assertTrue(ChatColor.stripColor(top.get(2)).contains("#2 Alice - 40 hits"));
        assertTrue(ChatColor.stripColor(top.get(3)).contains("#3 Carl - 12 hits"));
        assertEquals(3, AimTrainer.topLines(book, board, "Strafing target", BOB, 2).size(), "Bob is in it already");
    }

    @Test
    void theCommandFindsTheVariant() {
        assertEquals(0, AimTop.variant("strafe"));
        assertEquals(1, AimTop.variant("J"));
        assertEquals(2, AimTop.variant("circ"));
        assertEquals(-1, AimTop.variant("spin"));
        assertEquals(-1, AimTop.variant(""));
    }
}
