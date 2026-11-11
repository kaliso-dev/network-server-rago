package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class QuickDepositTest {

    @Test
    void onlyTheResourcesGoIntoAChestWithAHit() {
        assertEquals("Iron", BedwarsGame.resourceName(Material.IRON_INGOT));
        assertEquals("Gold", BedwarsGame.resourceName(Material.GOLD_INGOT));
        assertEquals("Diamond", BedwarsGame.resourceName(Material.DIAMOND));
        assertEquals("Emerald", BedwarsGame.resourceName(Material.EMERALD));
        assertNull(BedwarsGame.resourceName(Material.WOOL), "blocks are placed, not deposited");
        assertNull(BedwarsGame.resourceName(Material.STONE_SWORD));
    }
}
