package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The KB displace of a Bed Fight top fight: the run, the front, the notch, the roles. */
class KbDisplaceTest {

    @Test
    void theAttackerHasARunForTheMomentum() {
        assertTrue(KbDisplace.RUN >= 8, "a sprint needs a few blocks");
        assertTrue(KbDisplace.KNOCK_TICKS >= 20, "a hit defender goes up before they fall");
    }

    @Test
    void onlyAHitFromTheFrontCounts() {
        assertTrue(KbDisplace.fromTheFront(-1.2, 0.0), "from the bridge, in front");
        assertTrue(KbDisplace.fromTheFront(-2.8, 0.4), "at the end of the reach");
        assertFalse(KbDisplace.fromTheFront(1.2, 0.0), "from behind");
        assertFalse(KbDisplace.fromTheFront(-0.2, 1.0), "from a side");
        assertFalse(KbDisplace.fromTheFront(-1.0, 1.5), "from beside the bridge");
    }

    @Test
    void theDefenderHoldsTheirNotchUntilHit() {
        assertFalse(KbDisplace.outOfTheNotch(0.5, 0.0, 0.5));
        assertFalse(KbDisplace.outOfTheNotch(0.8, 0.3, 0.3), "a small move");
        assertTrue(KbDisplace.outOfTheNotch(0.5, 1.0, 0.5), "a jump onto the bridge");
        assertTrue(KbDisplace.outOfTheNotch(1.2, 0.0, 0.5), "a step onto the block behind");
        assertTrue(KbDisplace.outOfTheNotch(0.5, 0.0, 1.1), "a step to the side");
    }

    @Test
    void oneBlockDownIsOffTheTop() {
        assertFalse(KbDisplace.offTheTop(0.0));
        assertFalse(KbDisplace.offTheTop(-0.5), "knocked up, then back");
        assertTrue(KbDisplace.offTheTop(-1.0));
    }

    @Test
    void theRolesSwapEachAttempt() {
        assertEquals(0, KbDisplace.attackerOf(1));
        assertEquals(1, KbDisplace.attackerOf(2));
        assertEquals(0, KbDisplace.attackerOf(3));
    }

    @Test
    void aHitCountsOnlyWithTheLookOnTheDefender() {
        // the attacker on the bridge one block in front, eyes 1.62 above the bridge (y 1): the
        // defender's feet at y 0 in the notch
        double eyeX = -0.5;
        double eyeY = 2.62;
        double eyeZ = 0.5;
        double down = Math.toRadians(30);
        // looking at them, a little down
        assertTrue(KbDisplace.aimedAt(eyeX, eyeY, eyeZ, Math.cos(down), -Math.sin(down), 0, 0.5, 0, 0.5));
        // the head turned a little: the look still on the edge of their body
        double edge = Math.toRadians(15);
        assertTrue(KbDisplace.aimedAt(eyeX, eyeY, eyeZ, Math.cos(edge) * Math.cos(down), -Math.sin(down),
                Math.sin(edge) * Math.cos(down), 0.5, 0, 0.5));
        // the head turned to the side, as a cheat hits: not on them
        double side = Math.toRadians(70);
        assertFalse(KbDisplace.aimedAt(eyeX, eyeY, eyeZ, Math.cos(side) * Math.cos(down), -Math.sin(down),
                Math.sin(side) * Math.cos(down), 0.5, 0, 0.5));
        assertFalse(KbDisplace.aimedAt(eyeX, eyeY, eyeZ, 0, 0, 1, 0.5, 0, 0.5), "looking along the side");
        assertFalse(KbDisplace.aimedAt(eyeX, eyeY, eyeZ, -1, 0, 0, 0.5, 0, 0.5), "looking back");
        assertFalse(KbDisplace.aimedAt(-4.5, eyeY, eyeZ, 1, -0.2, 0, 0.5, 0, 0.5), "out of reach");
    }
}
