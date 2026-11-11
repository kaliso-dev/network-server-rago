package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

/** The block placement: the points of a knock into the void, the cracks of the arena's blocks. */
class BlockFightRulesTest {

    @Test
    void inBlockPlacementAKnockIntoTheVoidGivesTenPoints() {
        assertEquals(55, BlockFight.afterFall(45), "the other side gets 10");
        assertEquals(10, BlockFight.afterFall(0));
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

    @Test
    void aPlayerSunkUnderTheirStartIsSeenSoButNotOneWhoWalksOff() {
        Location start = new Location(null, 10.5, 89, 20.5);
        assertTrue(PracticeGame.sunk(new Location(null, 10.5, 86, 20.5), start), "in the shaft under the gold block");
        assertTrue(PracticeGame.sunk(new Location(null, 10.7, 87.9, 20.3), start));
        assertFalse(PracticeGame.sunk(new Location(null, 10.5, 88.2, 20.5), start), "less than a block lower");
        assertFalse(PracticeGame.sunk(new Location(null, 11.4, 86, 20.5), start), "walked off the side, falling");
        assertFalse(PracticeGame.sunk(new Location(null, 10.5, 89, 20.5), start), "on the start");
    }
}
