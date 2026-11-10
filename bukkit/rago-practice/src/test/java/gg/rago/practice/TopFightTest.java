package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The top fight starts on the top of the defense. */
class TopFightTest {

    @Test
    void bothSidesStartOnTheTopOfTheDefenseFaceToFace() {
        Set<String> blocks = new HashSet<>();
        for (List<int[]> layer : Shapes.defense(0, 1, 0, 1, 0, 3)) {
            for (int[] place : layer) {
                blocks.add(place[0] + "," + place[1] + "," + place[2]);
            }
        }
        for (int side = 0; side < 2; side++) {
            for (int index = 0; index < 2; index++) {
                double[] start = TopFight.startOf(side, index);
                int x = (int) Math.floor(start[0]);
                int y = (int) start[1];
                int z = (int) Math.floor(start[2]);
                String where = "side " + side + ", player " + index;
                assertTrue(blocks.contains(x + "," + (y - 1) + "," + z), where + ": on a block of the defense");
                assertFalse(blocks.contains(x + "," + y + "," + z), where + ": the feet in the air");
                assertFalse(blocks.contains(x + "," + (y + 1) + "," + z), where + ": the head in the air");
            }
        }
        assertEquals(5, TopFight.startOf(0, 0)[1], 1e-9, "the first of each side on the very top");
        assertEquals(5, TopFight.startOf(1, 0)[1], 1e-9);
        assertEquals(1, TopFight.startOf(1, 0)[0] - TopFight.startOf(0, 0)[0], 1e-9, "face to face, one block apart");
        assertEquals(-90, TopFight.startOf(0, 0)[3], 1e-9, "the west side looks east");
        assertEquals(90, TopFight.startOf(1, 1)[3], 1e-9, "the east side looks west");
    }

    @Test
    void inBlockPlacementTheVoidCostsTenPointsAndNeverGoesUnderNothing() {
        assertEquals(35, BlockFight.afterFall(45));
        assertEquals(0, BlockFight.afterFall(4));
        assertEquals(0, BlockFight.afterFall(0));
    }

    @Test
    void theBlocksOfTheArenaCrackMoreAndMoreBeforeTheyBreak() {
        assertEquals(0, BlockCracks.stage(0, BlockArena.DECAY_TICKS));
        assertEquals(0, BlockCracks.stage(29, BlockArena.DECAY_TICKS));
        assertEquals(1, BlockCracks.stage(30, BlockArena.DECAY_TICKS));
        assertEquals(5, BlockCracks.stage(150, BlockArena.DECAY_TICKS));
        assertEquals(9, BlockCracks.stage(299, BlockArena.DECAY_TICKS));
        assertEquals(9, BlockCracks.stage(1000, BlockArena.DECAY_TICKS), "never past the last stage");
        assertTrue(BlockCracks.newId() < 0, "a number no entity has");
    }
}
