package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The block placement: the points of a knock into the void, the cracks of the arena's blocks. */
class BlockFightRulesTest {

    @Test
    void inBlockPlacementAKnockIntoTheVoidGivesTwentyPoints() {
        assertEquals(65, BlockFight.afterFall(45), "the other side gets 20");
        assertEquals(20, BlockFight.afterFall(0));
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
