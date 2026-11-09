package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The bridge fight: its four platforms, their pads, and where a new player goes. */
class BridgeArenaTest {

    @Test
    void thePlatformsTaperAtBothEnds() {
        for (int x = -BridgeArena.HALF_LENGTH; x <= BridgeArena.HALF_LENGTH; x++) {
            assertEquals(BridgeArena.halfWidth(x), BridgeArena.halfWidth(-x), "the same at both ends");
            if (x > 0) {
                assertTrue(BridgeArena.halfWidth(x) <= BridgeArena.halfWidth(x - 1), "narrower towards the tips");
            }
        }
        assertEquals(9, 2 * BridgeArena.halfWidth(0) + 1, "nine wide in the middle");
        assertEquals(0, BridgeArena.halfWidth(BridgeArena.HALF_LENGTH), "one block at the tips");
        assertEquals(-1, BridgeArena.halfWidth(BridgeArena.HALF_LENGTH + 1));
    }

    @Test
    void fourPlatformsOfTwoColoursEachAndApart() {
        assertEquals(4, BridgeArena.PLATFORMS.length);
        Set<Integer> rows = new HashSet<>();
        int widest = BridgeArena.halfWidth(0);
        for (int i = 0; i < BridgeArena.PLATFORMS.length; i++) {
            BridgeArena.Platform one = BridgeArena.PLATFORMS[i];
            assertNotEquals(one.first, one.second, one.name);
            assertEquals(one.first, one.colourAt(-1, 0), "the first colour on the west half");
            assertEquals(one.second, one.colourAt(1, 0));
            assertTrue(rows.add(one.z));
            for (int j = i + 1; j < BridgeArena.PLATFORMS.length; j++) {
                int gap = Math.abs(one.z - BridgeArena.PLATFORMS[j].z) - 2 * widest - 1;
                assertTrue(gap >= 8, one.name + " and " + BridgeArena.PLATFORMS[j].name + " are " + gap + " apart");
            }
        }
    }

    @Test
    void thePadsAreAShortBridgeFromTheTips() {
        // the pad is three wide, centred on PAD: its inner edge, then the gap to the tip
        int gap = BridgeArena.PAD - 1 - BridgeArena.HALF_LENGTH - 1;
        assertTrue(gap >= 2 && gap <= 5, "a bridge of " + gap + " blocks");
    }

    @Test
    void aNewPlayerGoesWhereThereAreFewest() {
        assertEquals(0, BridgeArena.leastCrowded(new int[] {0, 0, 0, 0}), "the first one on a tie");
        assertEquals(2, BridgeArena.leastCrowded(new int[] {3, 1, 0, 2}));
        assertEquals(1, BridgeArena.leastCrowded(new int[] {2, 1, 1, 2}));
    }

    @Test
    void theMenuShowsEveryPlatformInOneRow() {
        assertEquals(BridgeArena.PLATFORMS.length, BridgeArena.MENU_SLOTS.length);
        Set<Integer> slots = new HashSet<>();
        for (int slot : BridgeArena.MENU_SLOTS) {
            assertTrue(slot >= 0 && slot < 9);
            assertTrue(slots.add(slot));
        }
    }
}
