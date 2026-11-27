package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

class Bedwars2012GameTest {

    private static final int[] EVENTS = {360, 720, 1080, 1440, 1800, 2700};

    /** The classic version has no diamond nor emerald generator: no tiers of them, the beds and the end as set. */
    @Test
    void theClassicVersionHasNoTiersOfDiamondsNorEmeralds() {
        assertArrayEquals(new int[] {-1, -1, -1, -1, 1800, 2700}, Bedwars2012Game.timeline(EVENTS, false));
        assertArrayEquals(EVENTS, Bedwars2012Game.timeline(EVENTS, true), "with the generators: the events of a game");
        assertArrayEquals(new int[] {360, 720, 1080, 1440, 1800, 2700}, EVENTS, "the settings stay as they are");
    }

    /** The bed goes the way the player looks: its head in front of its foot. */
    @Test
    void theBedGoesTheWayThePlayerLooks() {
        assertEquals(0, Bedwars2012Game.direction(0f), "south");
        assertEquals(1, Bedwars2012Game.direction(90f), "west");
        assertEquals(2, Bedwars2012Game.direction(180f), "north");
        assertEquals(3, Bedwars2012Game.direction(270f), "east");
        assertEquals(3, Bedwars2012Game.direction(-90f), "east");
        assertEquals(1, Bedwars2012Game.direction(-270f), "west");
        assertEquals(0, Bedwars2012Game.direction(359f), "south");
        assertEquals(BlockFace.SOUTH, Bedwars2012Game.towardsHead((byte) 0));
        assertEquals(BlockFace.WEST, Bedwars2012Game.towardsHead((byte) 1));
        assertEquals(BlockFace.NORTH, Bedwars2012Game.towardsHead((byte) 2));
        assertEquals(BlockFace.EAST, Bedwars2012Game.towardsHead((byte) 3));
        assertEquals(60, Bedwars2012Game.HIDE_SECONDS, "one minute to hide it (the owner, 27/11)");
    }

    @Test
    void theWelcomeSaysHowToHideTheBed() {
        String rules = ChatColor.stripColor(Bedwars2012Game.rules(true));
        assertTrue(rules.startsWith("Hide your bed in the first minute"), rules);
        assertTrue(rules.endsWith("Clay, no team upgrades."), rules);
        assertTrue(ChatColor.stripColor(Bedwars2012Game.rules(false)).endsWith("no diamonds nor emeralds."));
    }

    /** Clay in the place of the wool (the owner, 27/11): the item of the wool sells clay, at its price. */
    @Test
    void theShopSellsClayInThePlaceOfTheWool() {
        ShopCatalog standard = ShopCatalog.standard();
        ShopCatalog clay = standard.clayForWool();
        ShopItem wool = standard.byId("wool");
        ShopItem sold = clay.byId("wool");
        assertNotNull(sold, "its place in the Quick Buy and the hotbars kept");
        assertEquals(Material.STAINED_CLAY, sold.material());
        assertEquals(ShopItem.Kind.TEAM_BLOCK, sold.kind(), "in the colour of the team");
        assertEquals(wool.amount(), sold.amount());
        assertEquals(wool.price(), sold.price());
        assertEquals(wool.currency(), sold.currency());
        assertNull(clay.byId("clay"), "the Hardened Clay goes: the same");
        assertEquals(standard.all().size() - 1, clay.all().size());
        assertFalse(clay.reduced(), "a whole shop");
    }
}
