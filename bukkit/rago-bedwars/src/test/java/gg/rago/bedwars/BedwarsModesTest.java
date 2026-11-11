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
    void anInvisibleLeavesFootprintsOnOneFootThenTheOther() {
        // looking south (yaw 0): the right foot is on the west, the left one on the east
        double[] right = BedwarsGame.footprint(10, 20, 0f, 1);
        double[] left = BedwarsGame.footprint(10, 20, 0f, -1);
        assertEquals(10 - BedwarsGame.FOOT, right[0], 1e-9);
        assertEquals(10 + BedwarsGame.FOOT, left[0], 1e-9);
        assertEquals(20, right[1], 1e-9);
        // looking west (yaw 90): the right foot is on the north
        assertEquals(20 - BedwarsGame.FOOT, BedwarsGame.footprint(10, 20, 90f, 1)[1], 1e-9);
        for (float colour : BedwarsGame.SWIRL) {
            assertTrue(colour > 0 && colour <= 1, "a colour of the swirl is a fraction");
        }
    }

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
        Map<Currency, Integer> rushStart = FastShopGame.cost(shop, FastShopGame.ORDERS.get(0).items);
        assertEquals(Integer.valueOf(4 + 4 + 10), rushStart.get(Currency.IRON));
        assertEquals(1, rushStart.size());
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
    void theFastShopListsAreRealBuysThatTheShopAccepts() {
        List<String> armour = Arrays.asList("chainmail", "iron_boots", "diamond_boots");
        Set<String> ids = new HashSet<>();
        for (FastShopGame.Order order : FastShopGame.ORDERS) {
            assertTrue(ids.add(order.id), "one record per list: " + order.id);
            assertTrue(order.items.size() >= 3 && order.items.size() <= 7, order.id + " fits the sidebar");
            int armours = 0;
            for (String id : order.items) {
                if (armour.contains(id)) {
                    armours++;
                }
            }
            assertTrue(armours <= 1, "one armour at most: the next would be refused " + order.id);
            assertTrue(order.items.indexOf("shears") == order.items.lastIndexOf("shears"), "shears once " + order.id);
        }
        // the defenses of the fast defense: 12 end stone and 16 glass, 12 end stone and 32 wool
        assertEquals(Arrays.asList("end_stone", "glass", "glass", "glass", "glass"), order("ranked-defense").items);
        assertEquals(Arrays.asList("end_stone", "wool", "wool"), order("solo-defense").items);
    }

    private static FastShopGame.Order order(String id) {
        for (FastShopGame.Order order : FastShopGame.ORDERS) {
            if (order.id.equals(id)) {
                return order;
            }
        }
        throw new AssertionError(id);
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

    @Test
    void theInvisAttackerHasTheOwnersKit() {
        ShopCatalog shop = ShopCatalog.standard();
        List<String> materials = new ArrayList<>();
        for (String entry : InvisGame.ATTACKER_KIT) {
            String[] parts = entry.split(" ");
            ShopItem item = InvisGame.catalogItem(shop, parts[0]);
            assertNotNull(item, entry);
            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            assertTrue(amount >= 1 && amount <= item.material().getMaxStackSize(), entry);
            materials.add(item.material().name() + (item.kind() == ShopItem.Kind.POTION ? ":" + item.data() : ""));
        }
        assertEquals(Arrays.asList("STONE_SWORD", "WOOL", "WOOL", "WOOD", "GOLD_PICKAXE", "IRON_AXE", "SHEARS",
                "FIREBALL", "EGG", "POTION:8206", "POTION:8203"), materials, "as the lobby's kit, slot by slot");
        assertEquals(180, InvisGame.ROUND_SECONDS, "three minutes");
        assertEquals(1, InvisGame.ATTACKER_PROTECTION);
        assertEquals(4, InvisGame.DEFENDER_PROTECTION);
        assertNull(InvisGame.catalogItem(shop, "nothing"));
    }

    @Test
    void theInvisibleLooksAtTheBedFromMid() {
        assertEquals(0f, InvisGame.yawTowards(0, 5), 0.001f, "south");
        assertEquals(90f, InvisGame.yawTowards(-5, 0), 0.001f, "west");
        assertEquals(-90f, InvisGame.yawTowards(5, 0), 0.001f, "east");
        assertEquals(180f, Math.abs(InvisGame.yawTowards(0, -5)), 0.001f, "north");
    }
}
