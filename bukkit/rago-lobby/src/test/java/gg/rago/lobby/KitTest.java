package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class KitTest {

    private static final KitItem SWORD = KitItem.parse("IRON_SWORD");
    private static final KitItem WOOL = KitItem.parse("WOOL 64");
    private static final KitItem PEARL = KitItem.parse("ENDER_PEARL");

    /** Sword, wool, wool, an empty slot, a pearl. */
    private static Kit kit() {
        return new Kit(Arrays.asList(SWORD, WOOL, WOOL, null, PEARL));
    }

    @Test
    void readsTheItemsOfConfigYml() {
        assertEquals(Material.IRON_SWORD, SWORD.material());
        assertEquals(1, SWORD.amount());
        assertEquals(64, WOOL.amount());
        KitItem red = KitItem.parse(" wool:14  32 ");
        assertEquals(Material.WOOL, red.material());
        assertEquals(14, red.data());
        assertEquals(32, red.amount());
        assertEquals("WOOL:14 32", red.toString());
        assertNull(KitItem.parse(""));
        assertNull(KitItem.parse("AIR"));
        assertNull(KitItem.parse(null));
        assertThrows(IllegalArgumentException.class, () -> KitItem.parse("NOT_AN_ITEM"));
        assertThrows(IllegalArgumentException.class, () -> KitItem.parse("WOOL 65"));
        assertThrows(IllegalArgumentException.class, () -> KitItem.parse("WOOL 0"));
        assertThrows(IllegalArgumentException.class, () -> KitItem.parse("WOOL many"));
        assertThrows(IllegalArgumentException.class, () -> KitItem.parse("WOOL:red 64"));
        assertThrows(IllegalArgumentException.class, () -> KitItem.parse("WOOL 64 64"));
    }

    @Test
    void anItemOfTheInventoryIsAKitItemWhenItIsTheSameStack() {
        assertEquals(WOOL, KitItem.of(new ItemStack(Material.WOOL, 64)));
        assertFalse(WOOL.equals(KitItem.of(new ItemStack(Material.WOOL, 32))), "a split stack is another stack");
        assertFalse(WOOL.equals(KitItem.of(new ItemStack(Material.WOOL, 64, (short) 14))), "red wool is not white wool");
        assertNull(KitItem.of(null));
        assertNull(KitItem.of(new ItemStack(Material.AIR)));
        ItemStack item = KitItem.parse("WOOL:14 32").toItem();
        assertEquals(Material.WOOL, item.getType());
        assertEquals(32, item.getAmount());
        assertEquals(14, item.getDurability());
    }

    @Test
    void withoutALayoutTheKitGivesItsOwnSlots() {
        KitItem[] arranged = kit().arrange(null);
        assertEquals(Kit.SLOTS, arranged.length);
        assertEquals(SWORD, arranged[0]);
        assertEquals(WOOL, arranged[1]);
        assertNull(arranged[3]);
        assertEquals(PEARL, arranged[4]);
        assertArrayEquals(arranged, kit().arrange(Kit.defaultLayout()));
        assertTrue(new Kit(Collections.<KitItem>emptyList()).isEmpty());
        assertFalse(kit().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new Kit(Collections.nCopies(Kit.SLOTS + 1, SWORD)));
    }

    @Test
    void theLayoutOfAnEditedInventoryGivesTheSameInventoryBack() {
        Kit kit = kit();
        KitItem[] edited = new KitItem[Kit.SLOTS];
        // The sword in the fourth hotbar slot, the pearl first, a wool in the inventory.
        edited[3] = SWORD;
        edited[0] = PEARL;
        edited[1] = WOOL;
        edited[20] = WOOL;
        int[] layout = kit.layoutOf(edited);
        assertEquals(3, layout[0]);
        assertEquals(1, layout[1], "a wool left in its slot stays there");
        assertEquals(20, layout[2], "the other wool is the one moved");
        assertEquals(0, layout[4]);
        assertEquals(-1, layout[3], "an empty slot of the kit");
        assertArrayEquals(edited, kit.arrange(layout));
        assertFalse(Kit.isDefault(layout));
    }

    @Test
    void equalStacksAreInterchangeable() {
        Kit kit = kit();
        KitItem[] edited = new KitItem[Kit.SLOTS];
        edited[0] = SWORD;
        edited[4] = PEARL;
        edited[7] = WOOL;
        edited[8] = WOOL;
        assertArrayEquals(edited, kit.arrange(kit.layoutOf(edited)));
    }

    @Test
    void theKitsOwnLayoutIsTheDefault() {
        Kit kit = kit();
        int[] layout = kit.layoutOf(kit.arrange(null));
        assertTrue(Kit.isDefault(layout), Arrays.toString(layout));
        assertTrue(Kit.isDefault(Kit.defaultLayout()));
    }

    @Test
    void aLayoutForAnOlderKitStillGivesEveryItem() {
        int[] layout = Kit.defaultLayout();
        // Two items asked for the same slot, one for a slot out of the inventory.
        layout[0] = 8;
        layout[1] = 8;
        layout[4] = 99;
        KitItem[] arranged = kit().arrange(layout);
        assertEquals(SWORD, arranged[8]);
        assertEquals(WOOL, arranged[1], "slot 8 is taken: back to its own slot");
        assertEquals(WOOL, arranged[2]);
        assertEquals(PEARL, arranged[4]);
        // A lost item (not in the edited inventory) keeps the kit's slot.
        KitItem[] edited = new KitItem[Kit.SLOTS];
        edited[0] = SWORD;
        int[] lost = kit().layoutOf(edited);
        assertEquals(-1, lost[4]);
        assertEquals(PEARL, kit().arrange(lost)[4]);
        // The sword's own slot taken by the pearl: the sword goes to the first free slot.
        int[] taken = Kit.defaultLayout();
        taken[4] = 0;
        KitItem[] moved = kit().arrange(taken);
        assertEquals(PEARL, moved[0]);
        assertEquals(SWORD, moved[3]);
    }

    @Test
    void layoutsGoToAFileAndBack() throws Exception {
        Map<String, int[]> layouts = new TreeMap<>();
        int[] boxing = Kit.defaultLayout();
        boxing[0] = 3;
        layouts.put("boxing", boxing);
        Map<String, int[]> read = KitLayouts.fromYaml(KitLayouts.toYaml(layouts));
        assertEquals(Collections.singleton("boxing"), read.keySet());
        assertArrayEquals(boxing, read.get("boxing"));
        Map<String, int[]> odd = KitLayouts.fromYaml("modes:\n  sumo: [5, 40, -7]\n");
        assertEquals(5, odd.get("sumo")[0]);
        assertEquals(-1, odd.get("sumo")[1], "out of the inventory");
        assertEquals(-1, odd.get("sumo")[2]);
        assertEquals(-1, odd.get("sumo")[35], "a short list");
        assertTrue(KitLayouts.fromYaml("").isEmpty());
    }

    @Test
    void anInventoryArrangedInAGameIsReadWithItsUsedBlocks() {
        Kit kit = kit();
        KitItem[] played = new KitItem[Kit.SLOTS];
        // the pearl first, the sword third; a few blocks of one wool already placed
        played[0] = PEARL;
        played[2] = SWORD;
        played[1] = WOOL;
        played[8] = KitItem.of(new ItemStack(Material.WOOL, 40));
        int[] strict = kit.layoutOf(played);
        assertFalse(kit.complete(strict), "a stack of 40 is not the kit's 64");
        int[] loose = kit.layoutOf(played, true);
        assertTrue(kit.complete(loose));
        assertEquals(2, loose[0]);
        assertEquals(0, loose[4]);
        assertEquals(1, loose[1], "the full wool stays in its slot");
        assertEquals(8, loose[2]);
        // another role's inventory (no pearl) is not this kit
        played[0] = null;
        assertFalse(kit.complete(kit.layoutOf(played, true)));
        assertEquals(-1, KitEditor.moves(loose)[1], "in its own slot: not a move");
        // a game gives the wool in its team's colour: still the kit's wool
        played[0] = PEARL;
        played[1] = KitItem.of(new ItemStack(Material.WOOL, 64, (short) 14));
        assertTrue(kit.complete(kit.layoutOf(played, true)));
        assertTrue(Kit.dyed(Material.STAINED_CLAY));
        assertFalse(Kit.dyed(Material.ENDER_STONE));
        assertEquals(2, KitEditor.moves(loose)[0]);
    }

    @Test
    void anArrangementInAGameIsKeptWithAPotionDrunk() {
        // the invis practice kit: 11 stacks, the two potions in the inventory
        Kit kit = new Kit(Arrays.asList(KitItem.parse("STONE_SWORD"), WOOL, WOOL, KitItem.parse("WOOD 16"),
                KitItem.parse("GOLD_PICKAXE"), KitItem.parse("IRON_AXE"), KitItem.parse("SHEARS"), KitItem.parse("FIREBALL"),
                KitItem.parse("EGG 2"), KitItem.parse("POTION:8206"), KitItem.parse("POTION:8203")));
        KitItem[] played = new KitItem[Kit.SLOTS];
        for (int slot = 0; slot < 11; slot++) {
            played[slot] = kit.item(slot);
        }
        assertTrue(Kit.isDefault(KitEditor.arrangedInGame(kit, played, null)), "nothing moved");
        // the invisibility drunk first, then the sword and the planks swapped
        played[9] = null;
        played[0] = kit.item(3);
        played[3] = kit.item(0);
        int[] layout = KitEditor.arrangedInGame(kit, played, null);
        assertEquals(3, layout[0]);
        assertEquals(0, layout[3]);
        assertEquals(9, layout[9], "the potion drunk keeps its slot");
        int[] before = Kit.defaultLayout();
        before[9] = 20;
        assertEquals(20, KitEditor.arrangedInGame(kit, played, before)[9], "or where it went before");
        // the defenders' items: the wool and the shears of the kit, not the kit
        KitItem[] defender = new KitItem[Kit.SLOTS];
        defender[0] = KitItem.parse("IRON_SWORD");
        defender[1] = WOOL;
        defender[2] = WOOL;
        defender[3] = KitItem.parse("SHEARS");
        assertNull(KitEditor.arrangedInGame(kit, defender, null));
        // the kit of the role the game gave last, in a game of its mode
        assertEquals("invis-practice/defense", KitEditor.kitKey("invis-practice", "invis-practice/defense"));
        assertEquals("invis-practice", KitEditor.kitKey("invis-practice", "invis-practice"));
        assertEquals("invis-practice", KitEditor.kitKey("invis-practice", "ffa"), "the kit of another game");
        assertEquals("ffa", KitEditor.kitKey("ffa", null));
    }

    @Test
    void theKitOfAGameIsPlacedSlotBySlot() {
        Kit kit = Kit.of(new ItemStack[] {new ItemStack(Material.IRON_SWORD), new ItemStack(Material.WOOL, 64), null,
            new ItemStack(Material.ENDER_PEARL)});
        assertEquals(SWORD, kit.item(0));
        assertEquals(PEARL, kit.item(3));
        int[] layout = Kit.defaultLayout();
        layout[0] = 3;
        layout[3] = 0;
        int[] to = kit.placement(layout);
        assertEquals(3, to[0]);
        assertEquals(1, to[1]);
        assertEquals(-1, to[2], "an empty slot");
        assertEquals(0, to[3]);
        KitItem[] arranged = kit.arrange(layout);
        assertEquals(PEARL, arranged[0]);
        assertEquals(SWORD, arranged[3]);
        layout[1] = 3;
        assertEquals(1, kit.placement(layout)[1], "a slot already taken: the item keeps its own");
    }
}
