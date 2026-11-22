package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class CaptureTheFlagGameTest {

    /** Hypixel's kit of The Bridge, without its arrow (in the last slot of the hotbar). */
    @Test
    void theKitOfTheBridge() {
        ItemStack[] kit = BedFightGame.items(CaptureTheFlagGame.BRIDGE_KIT);
        assertEquals(6, kit.length);
        assertEquals(Material.IRON_SWORD, kit[0].getType());
        assertEquals(Material.BOW, kit[1].getType());
        assertEquals(Material.DIAMOND_PICKAXE, kit[2].getType());
        assertEquals(Material.STAINED_CLAY, kit[3].getType());
        assertEquals(64, kit[3].getAmount());
        assertEquals(Material.STAINED_CLAY, kit[4].getType(), "two stacks of clay");
        assertEquals(64, kit[4].getAmount());
        assertEquals(Material.GOLDEN_APPLE, kit[5].getType());
        assertEquals(8, kit[5].getAmount(), "8 golden apples");
        assertEquals(8, CaptureTheFlagGame.ARROW_SLOT, "the arrow in the last slot of the hotbar");
    }

    @Test
    void theArrowComesBackAfterThreeSecondsAndAHalf() {
        assertEquals(3_500L, CaptureTheFlagGame.ARROW_MILLIS);
        assertFalse(CaptureTheFlagGame.arrowBack(10_000L, 13_499L));
        assertTrue(CaptureTheFlagGame.arrowBack(10_000L, 13_500L));
    }

    /** The banner of a flag faces the team's spawn: its yaw to the 16 turns of a standing banner. */
    @Test
    void aBannerFacesTheWayOfItsSpawn() {
        assertEquals(0, CaptureTheFlagGame.rotation(0f), "south");
        assertEquals(4, CaptureTheFlagGame.rotation(90f), "west");
        assertEquals(8, CaptureTheFlagGame.rotation(180f), "north");
        assertEquals(12, CaptureTheFlagGame.rotation(-90f), "east: the red flag, towards the bridge");
        assertEquals(12, CaptureTheFlagGame.rotation(270f));
        assertEquals(0, CaptureTheFlagGame.rotation(360f));
    }

    @Test
    void theFlagIsInTheColourOfItsTeam() {
        assertEquals(DyeColor.RED, CaptureTheFlagGame.dye(TeamColor.RED));
        assertEquals(DyeColor.BLUE, CaptureTheFlagGame.dye(TeamColor.BLUE));
    }

    /** Nobody builds on a flag's block nor the one over it; around it, yes (a defense). */
    @Test
    void nobodyBuildsOnAFlag() {
        assertTrue(CaptureTheFlagGame.onFlag(0, 0, 0));
        assertTrue(CaptureTheFlagGame.onFlag(0, 1, 0));
        assertFalse(CaptureTheFlagGame.onFlag(0, 2, 0));
        assertFalse(CaptureTheFlagGame.onFlag(0, -1, 0), "its gold");
        assertFalse(CaptureTheFlagGame.onFlag(1, 0, 0));
        assertFalse(CaptureTheFlagGame.onFlag(0, 0, -1));
    }
}
