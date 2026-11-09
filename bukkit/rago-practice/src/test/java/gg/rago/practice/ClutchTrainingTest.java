package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The block clutch: its knockback settings, its top fight platform, its islands side by side. */
class ClutchTrainingTest {

    @Test
    void moreKnockbackThrowsFartherAndTheStickFarthest() {
        for (int kb = 1; kb < 5; kb++) {
            assertTrue(ClutchTraining.throwOf(kb + 1, false)[0] > ClutchTraining.throwOf(kb, false)[0]);
        }
        assertEquals(0.42, ClutchTraining.throwOf(3, false)[0], 1e-9, "3: a hit of a fight");
        assertTrue(ClutchTraining.throwOf(1, true)[0] > ClutchTraining.throwOf(5, false)[0], "the stick beats any hit");
        assertTrue(ClutchTraining.throwOf(3, true)[1] > ClutchTraining.throwOf(3, false)[1]);
    }

    @Test
    void theTopFightPlatformTapersAtBothEnds() {
        int blocks = 0;
        for (int x = -12; x <= 12; x++) {
            assertEquals(ClutchTraining.halfWidth(x), ClutchTraining.halfWidth(-x), "the same at both ends");
            if (x > 0) {
                assertTrue(ClutchTraining.halfWidth(x) <= ClutchTraining.halfWidth(x - 1), "narrower towards the tips");
            }
            blocks += Math.max(0, 2 * ClutchTraining.halfWidth(x) + 1);
        }
        assertEquals(7, 2 * ClutchTraining.halfWidth(0) + 1, "seven wide in the middle");
        assertEquals(0, ClutchTraining.halfWidth(10), "one block at the tips");
        assertEquals(-1, ClutchTraining.halfWidth(11));
        assertEquals(11 * 7 + 4 * 5 + 4 * 3 + 2, blocks);
    }

    @Test
    void eachPlayerBuildsOnlyAroundTheirOwnZone() {
        assertTrue(ClutchTraining.PLACE_ALONG >= 10, "the whole platform");
        assertTrue(2 * ClutchTraining.PLACE_ALONG < ClutchTraining.SPACING, "never on the next zone");
        assertTrue(ClutchTraining.SPACING <= 32, "close enough to see the others");
        assertTrue(ClutchTraining.SPACING >= gg.rago.game.ArenaGrid.MIN_SPACING, "a grid of zones takes it");
    }
}
