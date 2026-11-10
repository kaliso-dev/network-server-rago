package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ClutchScenarioGameTest {

    @Test
    void ironArmourAndARankedDefense() {
        assertEquals(2, ClutchScenarioGame.ARMOUR_TIER, "0 leather, 1 chainmail, 2 iron");
        assertArrayEquals(new Material[] {Material.ENDER_STONE, Material.STAINED_GLASS, Material.WOOD, Material.WOOL},
                ClutchScenarioGame.DEFENSE, "end stone on the bed, then glass, wood and wool");
        assertEquals(3, ClutchScenarioGame.DIAMOND_TIER, "a game half played: Diamond III...");
        assertEquals(2, ClutchScenarioGame.EMERALD_TIER, "...and Emerald II");
    }
}
