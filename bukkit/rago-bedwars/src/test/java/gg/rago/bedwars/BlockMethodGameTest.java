package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class BlockMethodGameTest {

    @Test
    void theBlazeRodAndTheMapGoWhereThePlayerHadPutThem() {
        ItemStack[] contents = new ItemStack[36];
        contents[0] = new ItemStack(Material.WOOD_SWORD);
        contents[3] = new ItemStack(Material.BLAZE_ROD);
        assertEquals(3, BlockMethodGame.slotOf(contents, BlockMethodGame.RESET_ITEM));
        assertEquals(-1, BlockMethodGame.slotOf(contents, BlockMethodGame.MAPS_ITEM));
        assertEquals(8, BlockMethodGame.freeSlot(contents, 8), "its slot, free");
        assertEquals(1, BlockMethodGame.freeSlot(contents, 0), "taken by the kit: the first free one");
        assertEquals(1, BlockMethodGame.freeSlot(contents, -1));
        ItemStack[] full = new ItemStack[2];
        full[0] = new ItemStack(Material.WOOL);
        full[1] = new ItemStack(Material.SHEARS);
        assertEquals(-1, BlockMethodGame.freeSlot(full, 1), "no room");
    }

    @Test
    void onlyItsTwoItemsStayInTheHotbar() {
        assertTrue(BlockMethodGame.isTool(Material.BLAZE_ROD));
        assertTrue(BlockMethodGame.isTool(Material.EMPTY_MAP));
        assertFalse(BlockMethodGame.isTool(Material.WOOL));
    }

    @Test
    void theMenuHasARowPerNineMaps() {
        assertEquals(1, BlockMethodMenu.rows(0));
        assertEquals(1, BlockMethodMenu.rows(4));
        assertEquals(2, BlockMethodMenu.rows(10));
        assertEquals(6, BlockMethodMenu.rows(80));
    }
}
