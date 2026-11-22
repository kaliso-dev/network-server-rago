package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShopCatalogTest {

    private final ShopCatalog catalog = ShopCatalog.standard();

    private String forHypixel(String id) {
        ShopItem item = catalog.forHypixel(id);
        return item == null ? null : item.id();
    }

    @Test
    void findsTheItemsOfAHypixelQuickBuy() {
        assertEquals("wool", forHypixel("wool"));
        assertEquals("stone_sword", forHypixel("stone_sword"));
        assertEquals("chainmail", forHypixel("chainmail_boots"));
        assertEquals("iron_boots", forHypixel("iron_boots"));
        assertEquals("pickaxe", forHypixel("wooden_pickaxe"));
        assertEquals("axe", forHypixel("wooden_axe"));
        assertEquals("speed", forHypixel("speed_ii_potion_(45_seconds)"));
        assertEquals("jump", forHypixel("jump_v_potion_(45_seconds)"));
        assertEquals("invisibility", forHypixel("invisibility_potion_(30_seconds)"));
        assertEquals("bow", forHypixel("bow"));
        assertEquals("bow_(power_i)", forHypixel("bow_(power_i)"));
        assertEquals("bow_(power_i__punch_i)", forHypixel("bow_(power_i__punch_i)"));
        assertEquals("end_stone", forHypixel("end_stone"));
        assertEquals("golden_apple", forHypixel("golden_apple"));
        assertEquals("water", forHypixel("water_bucket"));
        assertEquals("tower", forHypixel("compact_pop-up_tower"));
        assertNull(forHypixel("null"));
        assertNull(forHypixel(null));
    }

    @Test
    void theQuickBuyHasTwentyOneSlots() {
        List<ShopItem> standard = catalog.quickBuy(null);
        assertEquals(21, standard.size());
        assertEquals("wool", standard.get(0).id());
        for (int i = 0; i < 20; i++) {
            assertNotNull(standard.get(i), "slot " + i + " of the default quick buy: " + ShopCatalog.DEFAULT_QUICK_BUY.get(i));
        }
        List<String> hypixel = new ArrayList<>(Arrays.asList("wooden_pickaxe", null, "diamond_sword", "magic_milk"));
        List<ShopItem> imported = catalog.quickBuy(hypixel);
        assertEquals(21, imported.size());
        assertEquals("pickaxe", imported.get(0).id());
        assertNull(imported.get(1));
        assertEquals("diamond_sword", imported.get(2).id());
        assertEquals("milk", imported.get(3).id());
        assertNull(imported.get(20));
    }

    @Test
    void theToolsGoUpTierByTier() {
        assertEquals(4, catalog.pickaxes().size());
        assertEquals(4, catalog.axes().size());
        for (int tier = 0; tier < 4; tier++) {
            assertEquals(tier, catalog.pickaxes().get(tier).tier());
            assertEquals(tier, catalog.axes().get(tier).tier());
        }
        assertEquals(3, catalog.in(ShopItem.Category.TOOLS).size());
    }

    @Test
    void everyItemHasAPriceAndAUniqueId() {
        Set<String> ids = new HashSet<>();
        for (ShopItem item : catalog.all()) {
            assertTrue(ids.add(item.id()), "two items " + item.id());
            assertTrue(item.price() > 0, item.id());
            assertTrue(item.amount() > 0, item.id());
            if (item.kind() == ShopItem.Kind.POTION) {
                assertNotNull(item.effect(), item.id());
                assertTrue(item.effectSeconds() > 0, item.id());
            }
        }
        for (ShopItem.Category category : ShopItem.Category.values()) {
            assertTrue(catalog.in(category).size() <= ShopMenu.ITEM_SLOTS.length, category.name());
        }
    }

    @Test
    void theShopHasTwentyOneItemSlotsUnderTheCategories() {
        Set<Integer> slots = new HashSet<>();
        for (int slot : ShopMenu.ITEM_SLOTS) {
            assertTrue(slot >= 18 && slot < ShopMenu.SIZE, "slot " + slot);
            assertTrue(slots.add(slot));
        }
        assertEquals(21, slots.size());
        assertTrue(ShopItem.Category.values().length < 9);
    }

    private static List<String> ids(List<ShopItem> items) {
        List<String> ids = new ArrayList<>();
        for (ShopItem item : items) {
            ids.add(item == null ? null : item.id());
        }
        return ids;
    }

    /** Fast Bedwars: the shop reduced to a list of items, in the order of the shop, by category. */
    @Test
    void aReducedShopSellsOnlyItsItems() {
        ShopCatalog fast = catalog.only(Arrays.asList("tnt", "wool", "pickaxe", "end_stone", "bow", "glass"));
        assertTrue(fast.reduced());
        assertTrue(!catalog.reduced() && !ShopCatalog.arena().reduced());
        assertEquals(Arrays.asList("wool", "glass", "end_stone", "pickaxe", "bow", "tnt"), ids(fast.all()),
                "in the order of the shop");
        assertEquals(Arrays.asList("wool", "glass", "end_stone"), ids(fast.in(ShopItem.Category.BLOCKS)));
        assertTrue(fast.in(ShopItem.Category.MELEE).isEmpty(), "no sword listed: an empty category");
        assertEquals(Arrays.asList("pickaxe"), ids(fast.in(ShopItem.Category.TOOLS)), "the pickaxe, not the axe");
        assertEquals(catalog.pickaxes(), fast.pickaxes(), "the pickaxe tier after tier");
        assertNull(fast.byId("stone_sword"));
        assertNotNull(fast.byId("tnt"));
    }

    @Test
    void theQuickBuyOfAReducedShopKeepsWhatItSells() {
        ShopCatalog fast = catalog.only(Arrays.asList("wool", "bow", "tnt"));
        List<ShopItem> standard = fast.quickBuy(null);
        assertEquals(21, standard.size());
        assertEquals("wool", standard.get(0).id(), "the default quick buy, in its places");
        assertNull(standard.get(1), "the stone sword is not sold");
        assertEquals("bow", standard.get(4).id());
        assertEquals("tnt", standard.get(6).id());
        List<String> hypixel = Arrays.asList("bow_(power_i)", "bow", "wool", "diamond_sword");
        // read in the whole shop: Hypixel's Bow (Power I) is not the plain bow of the reduced shop
        assertEquals(Arrays.asList(null, "bow", "wool", null), ids(fast.quickBuy(hypixel)).subList(0, 4));
        assertEquals(Arrays.asList("bow_(power_i)", "bow", "wool", "diamond_sword"),
                ids(catalog.quickBuy(hypixel)).subList(0, 4), "the whole shop, as before");
    }

    @Test
    void pricesReadAsOnHypixel() {
        assertEquals(org.bukkit.ChatColor.WHITE + "4 Iron", ShopItem.Currency.IRON.amount(4));
        assertEquals(org.bukkit.ChatColor.DARK_GREEN + "2 Emeralds", ShopItem.Currency.EMERALD.amount(2));
        assertEquals(org.bukkit.ChatColor.AQUA + "1 Diamond", ShopItem.Currency.DIAMOND.amount(1));
    }
}
