package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AttackDefenseGameTest {

    @Test
    void theFirstSideToThreeRoundsWins() {
        assertEquals(3, AttackDefenseGame.WINS);
        assertEquals(1, AttackDefenseGame.attackingSide(1), "the second side attacks first");
        assertEquals(0, AttackDefenseGame.attackingSide(2), "the roles swap at each round");
        assertEquals(1, AttackDefenseGame.attackingSide(3));
        assertEquals(0, AttackDefenseGame.winner(new int[] {3, 2}, new int[] {40, 60}));
        assertEquals(20L, AttackDefenseGame.BETWEEN_TICKS, "a second between two rounds");
    }

    @Test
    void theMoreRoundsWinThenTheQuickerAttack() {
        assertEquals(0, AttackDefenseGame.winner(new int[] {2, 0}, new int[] {40, -1}), "both rounds");
        assertEquals(1, AttackDefenseGame.winner(new int[] {0, 2}, new int[] {-1, 90}));
        assertEquals(1, AttackDefenseGame.winner(new int[] {1, 1}, new int[] {120, 45}), "both attacks won: the quicker");
        assertEquals(0, AttackDefenseGame.winner(new int[] {1, 1}, new int[] {30, 31}));
        assertEquals(-1, AttackDefenseGame.winner(new int[] {1, 1}, new int[] {-1, -1}), "both defenses held: a draw");
        assertEquals(-1, AttackDefenseGame.winner(new int[] {1, 1}, new int[] {60, 60}), "as quick: a draw");
    }

    @Test
    void threeKitsToAttackThreeToDefendAllFromTheShop() {
        ShopCatalog catalog = ShopCatalog.standard();
        assertEquals(Arrays.asList("Rusher", "Breaker", "Ninja"), names(RoundKit.ATTACK));
        assertEquals(Arrays.asList("Guardian", "Archer", "Builder"), names(RoundKit.DEFENSE));
        for (RoundKit kit : RoundKit.ATTACK) {
            assertEquals(kit.items.size(), kit.entries(catalog).size(), kit.name);
        }
        for (RoundKit kit : RoundKit.DEFENSE) {
            assertEquals(kit.items.size(), kit.entries(catalog).size(), kit.name);
        }
        RoundKit.Entry pickaxe = RoundKit.ATTACK.get(0).entries(catalog).get(3);
        assertEquals(org.bukkit.Material.GOLD_PICKAXE, pickaxe.item.material(), "the Rusher: the Golden Pickaxe of Hypixel...");
        assertEquals(org.bukkit.enchantments.Enchantment.DAMAGE_ALL, pickaxe.item.second(), "...with Sharpness II");
        assertEquals("Fireball x3", RoundKit.label(RoundKit.ATTACK.get(0).entries(catalog).get(2)));
        assertEquals("Diamond Pickaxe", RoundKit.label(RoundKit.ATTACK.get(1).entries(catalog).get(3)));
        assertEquals(2, AttackDefenseGame.ARMOUR, "iron: leggings and boots, for the defenders too");
        assertEquals(5, AttackDefenseGame.COUNTDOWN, "time to choose...");
        assertEquals(3, AttackDefenseGame.NEXT_COUNTDOWN, "...the last kit kept at the next rounds...");
        assertEquals(0, AttackDefenseGame.READY_COUNTDOWN, "...and at once when everyone has chosen");
    }

    @Test
    void theKitsOfTheConfigAreChecked() {
        Map<String, Object> kit = new LinkedHashMap<>();
        kit.put("nom", "Bow & TNT");
        kit.put("icone", "bow");
        kit.put("description", Arrays.asList("&7Far away."));
        kit.put("objets", Arrays.asList("bow", "arrow 32", "tnt"));
        List<RoundKit> kits = RoundKit.read(Collections.singletonList(kit), "attaque-defense.kits-attaque", RoundKit.ATTACK);
        assertEquals(1, kits.size());
        assertEquals(org.bukkit.Material.BOW, kits.get(0).icon);
        assertEquals("Arrow x32", RoundKit.label(kits.get(0).entries(ShopCatalog.standard()).get(1)));
        assertEquals(RoundKit.DEFENSE, RoundKit.read(null, "x", RoundKit.DEFENSE), "none in the config: the defaults");
        kit.put("objets", Arrays.asList("wool 65"));
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> RoundKit.read(Collections.singletonList(kit), "attaque-defense.kits-attaque", RoundKit.ATTACK));
        assertEquals("attaque-defense.kits-attaque[1].objets: \"wool 65\", the amount goes from 1 to 64", wrong.getMessage());
        kit.put("objets", Arrays.asList("diamond_hoe"));
        wrong = assertThrows(IllegalArgumentException.class,
                () -> RoundKit.read(Collections.singletonList(kit), "attaque-defense.kits-attaque", RoundKit.ATTACK));
        assertTrue(wrong.getMessage().startsWith("attaque-defense.kits-attaque[1].objets: \"diamond_hoe\" is not an item"));
    }

    @Test
    void theKitsSitOnTheMiddleRow() {
        assertEquals(Arrays.asList(11, 13, 15), Arrays.asList(KitMenu.slotOf(0, 3), KitMenu.slotOf(1, 3), KitMenu.slotOf(2, 3)));
        assertEquals(13, KitMenu.slotOf(0, 1));
        assertEquals(10, KitMenu.slotOf(0, 7));
        assertEquals(16, KitMenu.slotOf(6, 7));
        List<String> lore = KitMenu.lore(RoundKit.DEFENSE.get(1), ShopCatalog.standard(), true);
        assertEquals(Arrays.asList("§7Shoot them off the bridge", "§7before they land.", "", "§8- §fBow", "§8- §fArrow x24",
                "§8- §fWool x16", "", "§aYour kit"), lore);
    }

    @Test
    void theKitEditorSlotOfAPickaxeFitsEveryPickaxe() {
        assertTrue(AttackDefenseGame.sameKind(org.bukkit.Material.GOLD_PICKAXE, org.bukkit.Material.DIAMOND_PICKAXE));
        assertTrue(AttackDefenseGame.sameKind(org.bukkit.Material.STONE_SWORD, org.bukkit.Material.WOOD_SWORD));
        assertTrue(AttackDefenseGame.sameKind(org.bukkit.Material.WOOD_AXE, org.bukkit.Material.IRON_AXE));
        assertTrue(!AttackDefenseGame.sameKind(org.bukkit.Material.WOOD_AXE, org.bukkit.Material.IRON_PICKAXE), "an axe is no pickaxe");
        assertTrue(!AttackDefenseGame.sameKind(org.bukkit.Material.FIREBALL, org.bukkit.Material.TNT));
        assertTrue(AttackDefenseGame.sameKind(org.bukkit.Material.WOOL, org.bukkit.Material.WOOL));
    }

    private static List<String> names(List<RoundKit> kits) {
        List<String> names = new ArrayList<>();
        for (RoundKit kit : kits) {
            names.add(kit.name);
        }
        return names;
    }
}
