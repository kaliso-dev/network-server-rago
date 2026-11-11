package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class TopBedFightGameTest {

    /** The owner's map: red at z 22.5, blue at z 31.5, the blocks at their backs at z 21 and 32. */
    private static final double[] ENDS = {3.5, 22.5, 3.5, 31.5};

    @Test
    void aWoodenSwordAStackOfWoolAndShears() {
        ItemStack[] kit = BedFightGame.items(TopBedFightGame.KIT);
        assertEquals(3, kit.length);
        assertEquals(Material.WOOD_SWORD, kit[0].getType());
        assertEquals(Material.WOOL, kit[1].getType());
        assertEquals(64, kit[1].getAmount(), "one stack of blocks");
        assertEquals(Material.SHEARS, kit[2].getType());
    }

    @Test
    void thePlayersStayBetweenTheBlocksAtTheEnds() {
        double margin = TopBedFightGame.BEHIND;
        assertFalse(TopBedFightGame.behind(ENDS, 3.5, 22.5, margin), "on the red spawn");
        assertFalse(TopBedFightGame.behind(ENDS, 3.5, 22.3, margin), "against the block behind red");
        assertTrue(TopBedFightGame.behind(ENDS, 3.5, 21.9, margin), "on the block behind red");
        assertTrue(TopBedFightGame.behind(ENDS, 3.5, 18.0, margin), "on the stairs down to the red island");
        assertFalse(TopBedFightGame.behind(ENDS, 3.5, 31.7, margin), "against the block behind blue");
        assertTrue(TopBedFightGame.behind(ENDS, 3.5, 32.1, margin), "on the block behind blue");
        assertFalse(TopBedFightGame.behind(ENDS, 8.0, 27.0, margin), "off the side of the bridge: the fall kills");
    }

    @Test
    void noBlockGoesBehindTheBlocksAtTheEnds() {
        double margin = TopBedFightGame.BEHIND;
        // the centres of blocks: the one on the spawn may be placed, the one behind it not
        assertFalse(TopBedFightGame.behind(ENDS, 3.5, 22.5, margin));
        assertTrue(TopBedFightGame.behind(ENDS, 3.5, 21.5, margin));
        assertTrue(TopBedFightGame.behind(ENDS, 4.5, 21.5, margin), "nor beside it, to go round");
        assertFalse(TopBedFightGame.behind(ENDS, 3.5, 31.5, margin));
        assertTrue(TopBedFightGame.behind(ENDS, 3.5, 32.5, margin));
    }
}
