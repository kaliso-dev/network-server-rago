package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.bedwars.ShopItem.Currency;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The pieces of the Bed Wars modes that need no server: defenses, the arena's shop, Fast Shop. */
class BedwarsModesTest {

    @Test
    void aDefenseTakesTheBlocksPlayersPutAroundABed() {
        // a bed along x: 8 blocks for one layer, 26 for two, 58 for three
        assertEquals(Arrays.asList(8, 18, 32), sizes(Defense.layers(0, 64, 0, 1, 0, 3)));
        // along z, the same
        assertEquals(Arrays.asList(8, 18, 32), sizes(Defense.layers(10, 70, -4, 0, -1, 3)));
        for (List<int[]> layer : Defense.layers(0, 64, 0, 1, 0, 3)) {
            for (int[] place : layer) {
                assertTrue(place[1] >= 64, "never under the bed");
                assertFalse(place[1] == 64 && place[2] == 0 && (place[0] == 0 || place[0] == 1), "never on the bed");
            }
        }
        // the first layer: the six blocks around the bed, and the two on top
        Set<String> first = new HashSet<>();
        for (int[] place : Defense.layers(0, 64, 0, 1, 0, 1).get(0)) {
            first.add(place[0] + "," + place[1] + "," + place[2]);
        }
        assertEquals(new HashSet<>(Arrays.asList("-1,64,0", "2,64,0", "0,64,1", "1,64,1", "0,64,-1", "1,64,-1", "0,65,0",
                "1,65,0")), first);
    }

    private static List<Integer> sizes(List<List<int[]>> layers) {
        List<Integer> sizes = new ArrayList<>();
        for (List<int[]> layer : layers) {
            sizes.add(layer.size());
        }
        return sizes;
    }

    @Test
    void theArenaHasAShopOfItsOwn() {
        ShopCatalog arena = ShopCatalog.arena();
        for (ShopItem item : arena.all()) {
            assertTrue(item.kind() != ShopItem.Kind.COMING_SOON, item.id() + ": nothing still to come in the arena");
        }
        assertEquals(3, arena.byId("diamond_sword").price());
        assertEquals(Currency.EMERALD, arena.byId("diamond_sword").currency());
        assertEquals(Currency.DIAMOND, arena.byId("sharp_sword").currency());
        assertEquals(ShopItem.Kind.SWORD, arena.byId("sharp_sword").kind());
        assertEquals(Currency.DIAMOND, arena.byId("power_bow").currency());
        assertEquals(Currency.DIAMOND, arena.byId("strength").currency());
        assertEquals(1, arena.byId("enchanted_apple").data());
        // the rest as in a Bed Wars game
        assertEquals(ShopCatalog.standard().byId("wool").price(), arena.byId("wool").price());
        assertEquals(4, arena.byId("diamond_boots").price());
        // a quick buy copied from Hypixel finds its items there too
        assertEquals("diamond_sword", arena.forHypixel("diamond_sword").id());
        assertEquals("pickaxe", arena.forHypixel("wooden_pickaxe").id());
        assertNull(arena.forHypixel("magic_milk"), "not sold in the arena");
        for (ShopItem.Category category : ShopItem.Category.values()) {
            assertTrue(arena.in(category).size() <= ShopMenu.ITEM_SLOTS.length, category + " fits in the shop");
        }
    }

    @Test
    void eachFastShopListCostsWhatItsItemsCost() {
        ShopCatalog shop = ShopCatalog.standard();
        for (FastShopGame.Order order : FastShopGame.ORDERS) {
            for (String id : order.items) {
                assertNotNull(shop.byId(id), order.id + ": " + id + " is sold");
            }
        }
        Map<Currency, Integer> firstBuy = FastShopGame.cost(shop, FastShopGame.ORDERS.get(0).items);
        assertEquals(Integer.valueOf(4 + 4 + 10 + 24), firstBuy.get(Currency.IRON));
        assertEquals(1, firstBuy.size());
        // the pickaxe tier after tier: wooden then iron, 10 iron each; the axe 10, the shears 20
        assertEquals(Integer.valueOf(50), FastShopGame.cost(shop, Arrays.asList("pickaxe", "pickaxe", "axe", "shears"))
                .get(Currency.IRON));
        assertEquals(Integer.valueOf(3), FastShopGame.cost(shop, Arrays.asList("pickaxe", "pickaxe", "pickaxe"))
                .get(Currency.GOLD), "the third pickaxe is the golden one");
        Map<Currency, Integer> rush = FastShopGame.cost(shop, Arrays.asList("iron_sword", "wool", "wool", "wool", "tnt",
                "ender_pearl", "speed"));
        assertEquals(Integer.valueOf(12), rush.get(Currency.IRON));
        assertEquals(Integer.valueOf(11), rush.get(Currency.GOLD));
        assertEquals(Integer.valueOf(5), rush.get(Currency.EMERALD));
    }

    @Test
    void aRandomFastShopListCanAlwaysBeBought() {
        ShopCatalog shop = ShopCatalog.standard();
        List<String> armour = Arrays.asList("chainmail", "iron_boots", "diamond_boots");
        Random random = new Random(42);
        for (int i = 0; i < 500; i++) {
            List<String> list = FastShopGame.randomList(random);
            assertEquals(5, list.size());
            int armours = 0;
            for (String id : list) {
                assertNotNull(shop.byId(id), id);
                if (armour.contains(id)) {
                    armours++;
                }
            }
            assertTrue(armours <= 1, "one armour at most: the next would be refused " + list);
            assertTrue(list.indexOf("shears") == list.lastIndexOf("shears"), "shears once " + list);
        }
    }

    @Test
    void fastShopKeepsEachPlayersBest() {
        FastShopRecords records = new FastShopRecords(new java.io.File("unused"), null, null);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        assertTrue(records.record("first-buy", alice, "Alice", 4200));
        assertFalse(records.record("first-buy", alice, "Alice", 5000), "slower: not a best");
        assertTrue(records.record("first-buy", alice, "Alice", 3900));
        assertTrue(records.record("first-buy", bob, "Bob", 3100));
        assertEquals(Long.valueOf(3900), records.best("first-buy", alice));
        assertNull(records.best("tools", alice));
        assertEquals("Bob", records.top("first-buy").name);
        assertEquals(3100, records.top("first-buy").millis);
        assertNull(records.top("tools"));
    }

    @Test
    void fastShopRecordsSurviveARestart() throws Exception {
        FastShopRecords records = new FastShopRecords(new java.io.File("unused"), null, null);
        UUID alice = UUID.randomUUID();
        records.record("rush", alice, "Alice", 6543);
        FastShopRecords again = new FastShopRecords(new java.io.File("unused"), null, null);
        again.read(records.toYaml());
        assertEquals(Long.valueOf(6543), again.best("rush", alice));
        assertEquals("Alice", again.top("rush").name);
    }
}
