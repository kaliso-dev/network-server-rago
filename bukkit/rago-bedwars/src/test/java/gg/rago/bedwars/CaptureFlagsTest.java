package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The flags of Capture the Flag and their rules, the classic ones: red is team 0, blue team 1. */
class CaptureFlagsTest {

    private static final int RED = 0;
    private static final int BLUE = 1;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private static CaptureFlags flags() {
        return new CaptureFlags(2, 3, 30);
    }

    @Test
    void anEnemyTakesTheFlagAtItsBase() {
        CaptureFlags flags = flags();
        assertEquals(CaptureFlags.Touch.TAKEN, flags.touch(RED, alice, BLUE));
        assertEquals(CaptureFlags.State.CARRIED, flags.flag(BLUE).state);
        assertEquals(alice, flags.flag(BLUE).carrier);
        assertEquals(BLUE, flags.carriedBy(alice));
        assertEquals(-1, flags.carriedBy(bob));
        assertEquals("Taken", flags.stateLine(BLUE, 10));
        assertEquals("Safe", flags.stateLine(RED, 10));
    }

    @Test
    void aCarrierScoresAtTheirOwnFlagWhileItIsHome() {
        CaptureFlags flags = flags();
        flags.touch(RED, alice, BLUE);
        assertEquals(CaptureFlags.Touch.CAPTURED, flags.touch(RED, alice, RED));
        assertEquals(1, flags.score(RED));
        assertEquals(0, flags.score(BLUE));
        assertEquals(CaptureFlags.State.HOME, flags.flag(BLUE).state, "the flag they carried goes back to its base");
        assertNull(flags.flag(BLUE).carrier);
        assertEquals(-1, flags.carriedBy(alice));
    }

    @Test
    void noCaptureWhileTheirOwnFlagIsAway() {
        CaptureFlags flags = flags();
        flags.touch(RED, alice, BLUE);
        flags.touch(BLUE, bob, RED);
        assertEquals(CaptureFlags.Touch.NOT_HOME, flags.touch(RED, alice, RED), "theirs is carried by Bob");
        assertEquals(0, flags.score(RED));
        assertEquals(BLUE, flags.carriedBy(alice), "Alice keeps the blue flag");
        flags.drop(RED, 1, 65, 2, 40);
        assertEquals(CaptureFlags.Touch.RETURNED, flags.touch(RED, alice, RED),
                "dropped: touching it brings it home, the carrier keeps the enemy flag");
        assertEquals(CaptureFlags.State.HOME, flags.flag(RED).state);
        assertEquals(BLUE, flags.carriedBy(alice));
        assertEquals(CaptureFlags.Touch.CAPTURED, flags.touch(RED, alice, RED), "now home: a capture");
    }

    @Test
    void aPlayerCarriesOneFlagAtMostAndNeverTheirOwn() {
        CaptureFlags flags = new CaptureFlags(3, 3, 30);
        flags.touch(RED, alice, BLUE);
        assertEquals(CaptureFlags.Touch.NOTHING, flags.touch(RED, alice, 2), "already carrying one");
        assertEquals(CaptureFlags.State.HOME, flags.flag(2).state);
        assertEquals(CaptureFlags.Touch.NOTHING, flags.touch(RED, bob, BLUE), "carried by Alice: nobody takes it");
        assertEquals(CaptureFlags.Touch.NOTHING, flags.touch(BLUE, bob, BLUE), "at home, without an enemy flag: nothing");
        assertEquals(CaptureFlags.State.CARRIED, flags.flag(BLUE).state);
    }

    @Test
    void aDroppedFlagWaitsThenGoesBack() {
        CaptureFlags flags = flags();
        flags.touch(RED, alice, BLUE);
        flags.drop(BLUE, 10.5, 65, 0.5, 100);
        CaptureFlags.Flag blue = flags.flag(BLUE);
        assertEquals(CaptureFlags.State.DROPPED, blue.state);
        assertNull(blue.carrier);
        assertEquals(10.5, blue.x);
        assertEquals(-1, flags.carriedBy(alice));
        assertEquals("Dropped 30s", flags.stateLine(BLUE, 100));
        assertEquals(6, flags.secondsLeft(BLUE, 124));
        assertTrue(flags.expired(129).isEmpty());
        assertEquals(Collections.singletonList(BLUE), flags.expired(130), "30 seconds after");
        assertEquals(0, flags.secondsLeft(BLUE, 200));
        assertEquals(0, flags.secondsLeft(RED, 100), "not dropped");
    }

    @Test
    void anEnemyTakesADroppedFlagAgain() {
        CaptureFlags flags = flags();
        flags.touch(RED, alice, BLUE);
        flags.drop(BLUE, 0, 65, 0, 50);
        UUID carol = UUID.randomUUID();
        assertEquals(CaptureFlags.Touch.TAKEN, flags.touch(RED, carol, BLUE));
        assertEquals(carol, flags.flag(BLUE).carrier);
        assertTrue(flags.expired(500).isEmpty(), "carried: it does not go back by itself");
    }

    @Test
    void theFirstToTheCapturesWinsElseTheMostAtTheEnd() {
        CaptureFlags flags = flags();
        assertEquals(-1, flags.winner());
        assertEquals(-1, flags.leader(), "0 - 0: a draw");
        for (int i = 0; i < 2; i++) {
            flags.touch(BLUE, bob, RED);
            flags.touch(BLUE, bob, BLUE);
        }
        assertEquals(-1, flags.winner(), "2 of 3");
        assertEquals(BLUE, flags.leader());
        flags.touch(RED, alice, BLUE);
        flags.touch(RED, alice, RED);
        flags.touch(RED, alice, BLUE);
        flags.touch(RED, alice, RED);
        assertEquals(-1, flags.leader(), "2 - 2: a draw");
        flags.touch(RED, alice, BLUE);
        flags.touch(RED, alice, RED);
        assertEquals(RED, flags.winner(), "the third capture wins");
    }

    @Test
    void aPlayerTouchesAFlagFromNearIt() {
        assertTrue(CaptureFlags.touches(0, 0, 0));
        assertTrue(CaptureFlags.touches(1.0, 0.5, 1.0), "on a block beside it");
        assertTrue(CaptureFlags.touches(0, 2.0, 0), "standing two blocks over it");
        assertFalse(CaptureFlags.touches(1.2, 0, 1.2), "too far across");
        assertFalse(CaptureFlags.touches(0, 2.5, 0), "too high");
        assertFalse(CaptureFlags.touches(0, -2.5, 0), "too low");
    }
}
