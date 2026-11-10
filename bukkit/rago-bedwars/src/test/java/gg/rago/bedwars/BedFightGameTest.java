package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class BedFightGameTest {

    @Test
    void theKitOfMinemen() {
        ItemStack[] kit = BedFightGame.defaultKit();
        assertEquals(6, kit.length);
        assertEquals(Material.STONE_SWORD, kit[0].getType());
        assertEquals(Material.WOOD_PICKAXE, kit[1].getType());
        assertEquals(Material.WOOD_AXE, kit[2].getType());
        assertEquals(Material.SHEARS, kit[3].getType());
        assertEquals(Material.WOOL, kit[4].getType());
        assertEquals(64, kit[4].getAmount(), "two stacks of wool");
        assertEquals(64, kit[5].getAmount());
    }

    @Test
    void theBedsGoAtTenMinutesTheGameEndsAtFifteen() {
        assertEquals(ChatColor.WHITE + "Bed Gone in " + ChatColor.GREEN + "10:00", BedFightGame.nextEvent(0));
        assertEquals(ChatColor.WHITE + "Bed Gone in " + ChatColor.GREEN + "0:01", BedFightGame.nextEvent(599));
        assertEquals(ChatColor.WHITE + "Game End in " + ChatColor.GREEN + "5:00", BedFightGame.nextEvent(600));
        assertEquals(ChatColor.WHITE + "Game End", BedFightGame.nextEvent(900));
    }
}
