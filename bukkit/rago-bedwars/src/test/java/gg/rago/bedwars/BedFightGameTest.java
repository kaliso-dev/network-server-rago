package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class BedFightGameTest {

    @Test
    void theKitOfMinemen() {
        ItemStack[] kit = BedFightGame.defaultKit();
        assertEquals(5, kit.length);
        assertEquals(Material.WOOD_SWORD, kit[0].getType());
        assertEquals(Material.WOOD_PICKAXE, kit[1].getType());
        assertEquals(Material.WOOD_AXE, kit[2].getType());
        assertEquals(Material.SHEARS, kit[3].getType());
        assertEquals(Material.WOOL, kit[4].getType());
        assertEquals(64, kit[4].getAmount(), "one stack of wool");
    }

    @Test
    void theBedsGoAtTenMinutesTheGameEndsAtFifteen() {
        assertEquals(ChatColor.WHITE + "Bed Gone in " + ChatColor.GREEN + "10:00", BedFightGame.nextEvent(0));
        assertEquals(ChatColor.WHITE + "Bed Gone in " + ChatColor.GREEN + "0:01", BedFightGame.nextEvent(599));
        assertEquals(ChatColor.WHITE + "Game End in " + ChatColor.GREEN + "5:00", BedFightGame.nextEvent(600));
        assertEquals(ChatColor.WHITE + "Game End", BedFightGame.nextEvent(900));
    }

    @Test
    void aLifeComesBackInThreeSecondsAtMost() {
        assertEquals(3, BedFightGame.respawnOf(5), "not the 5 seconds of Bed Wars");
        assertEquals(2, BedFightGame.respawnOf(2));
        assertEquals(0, BedFightGame.respawnOf(0));
    }

    /** The lobby's kit: sword, pickaxe, axe, shears, two stacks of wool, in the first six slots. */
    private static ItemStack[] lobbyKit() {
        ItemStack[] kit = new ItemStack[36];
        kit[0] = new ItemStack(Material.WOOD_SWORD);
        kit[1] = new ItemStack(Material.WOOD_PICKAXE);
        kit[2] = new ItemStack(Material.WOOD_AXE);
        kit[3] = new ItemStack(Material.SHEARS);
        kit[4] = new ItemStack(Material.WOOL, 64);
        kit[5] = new ItemStack(Material.WOOL, 64);
        return kit;
    }

    @Test
    void thePlayerAloneOfA1v2HasTheBetterSwordInItsSlot() {
        ItemStack[] kit = lobbyKit();
        kit[0] = null;
        kit[7] = new ItemStack(Material.WOOD_SWORD);
        assertEquals(java.util.Collections.singletonList(7), BedFightGame.swordsAs(kit, Material.STONE_SWORD),
                "the sword where the player put it");
        assertEquals(Material.STONE_SWORD, kit[7].getType());
        assertEquals(Material.WOOD_PICKAXE, kit[1].getType(), "the rest of the kit as it is");
        assertEquals(Material.WOOD_AXE, kit[2].getType());
        assertEquals(Material.SHEARS, kit[3].getType());
        assertEquals(64, kit[4].getAmount());
        assertNull(kit[0]);
        assertEquals(java.util.Collections.emptyList(), BedFightGame.swordsAs(kit, Material.STONE_SWORD),
                "already the better sword: nothing to change");
    }

    @Test
    void thePlayersReadTheBetterStuff() {
        assertEquals("iron leggings and boots, a stone sword",
                BedFightGame.stuffOf(new BedwarsSettings.Stuff(2, Material.STONE_SWORD, 0)));
        assertEquals("diamond leggings and boots, an iron sword, Protection II",
                BedFightGame.stuffOf(new BedwarsSettings.Stuff(3, Material.IRON_SWORD, 2)));
        assertEquals("a golden sword, Protection I", BedFightGame.stuffOf(new BedwarsSettings.Stuff(0, Material.GOLD_SWORD, 1)),
                "leather as the others: not said");
        assertEquals("a wooden sword", BedFightGame.stuffOf(new BedwarsSettings.Stuff(0, Material.WOOD_SWORD, 0)));
    }

    @Test
    void theFirstLifeHasTheKitOfTheLobby() {
        ItemStack[] kit = lobbyKit();
        assertSame(kit, BedFightGame.arranged(kit, null));
    }

    @Test
    void aDeathInTheVoidKeepsTheHotbarAsArrangedInTheGame() {
        // during the game: the wool moved to slots 0 and 8, the sword to 1, the pickaxe in the inventory
        ItemStack[] atDeath = new ItemStack[36];
        atDeath[0] = new ItemStack(Material.WOOL, 12);
        atDeath[8] = new ItemStack(Material.WOOL, 64);
        atDeath[1] = new ItemStack(Material.WOOD_SWORD);
        atDeath[20] = new ItemStack(Material.WOOD_PICKAXE);
        atDeath[2] = new ItemStack(Material.WOOD_AXE);
        atDeath[3] = new ItemStack(Material.SHEARS);
        Material[] layout = BedFightGame.layoutOf(atDeath, null);
        ItemStack[] next = BedFightGame.arranged(lobbyKit(), layout);
        assertEquals(Material.WOOL, next[0].getType());
        assertEquals(64, next[0].getAmount(), "a whole stack again");
        assertEquals(Material.WOOD_SWORD, next[1].getType());
        assertEquals(Material.WOOD_AXE, next[2].getType());
        assertEquals(Material.SHEARS, next[3].getType());
        assertEquals(Material.WOOL, next[8].getType());
        assertEquals(Material.WOOD_PICKAXE, next[20].getType());
        assertNull(next[4]);
        assertNull(next[5]);
    }

    @Test
    void aKindUsedUpKeepsItsSlotOfTheLastLife() {
        ItemStack[] first = new ItemStack[36];
        first[0] = new ItemStack(Material.WOOD_SWORD);
        first[7] = new ItemStack(Material.WOOL, 30);
        first[8] = new ItemStack(Material.WOOL, 64);
        Material[] before = BedFightGame.layoutOf(first, null);
        // the next life: every block of wool placed before the death
        ItemStack[] second = new ItemStack[36];
        second[0] = new ItemStack(Material.WOOD_SWORD);
        Material[] layout = BedFightGame.layoutOf(second, before);
        ItemStack[] next = BedFightGame.arranged(lobbyKit(), layout);
        assertEquals(Material.WOOL, next[7].getType());
        assertEquals(Material.WOOL, next[8].getType());
        assertEquals(Material.WOOD_SWORD, next[0].getType());
    }

    @Test
    void anItemPickedUpInTheGameTakesNoItemOfTheKit() {
        ItemStack[] atDeath = lobbyKit();
        atDeath[4] = new ItemStack(Material.ENDER_STONE, 3);
        atDeath[5] = null;
        atDeath[9] = new ItemStack(Material.WOOL, 64);
        ItemStack[] next = BedFightGame.arranged(lobbyKit(), BedFightGame.layoutOf(atDeath, null));
        int wool = 0;
        for (ItemStack item : next) {
            if (item != null && item.getType() == Material.WOOL) {
                wool++;
            }
            assertEquals(false, item != null && item.getType() == Material.ENDER_STONE, "never given");
        }
        assertEquals(2, wool, "both stacks of the kit");
        assertEquals(Material.WOOL, next[9].getType());
    }
}
