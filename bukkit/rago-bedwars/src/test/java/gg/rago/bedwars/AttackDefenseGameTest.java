package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class AttackDefenseGameTest {

    @Test
    void eachSideAttacksOnce() {
        assertEquals(2, AttackDefenseGame.ROUNDS);
        assertEquals(1, AttackDefenseGame.attackingSide(1), "the second side attacks first");
        assertEquals(0, AttackDefenseGame.attackingSide(2), "the roles swap at the second round");
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
    void theAttackersHaveTheGoldenPickaxeOfHypixelAndIronArmour() {
        ShopItem pickaxe = ShopCatalog.standard().pickaxes().get(AttackDefenseGame.ATTACKER_PICKAXE);
        assertEquals(org.bukkit.Material.GOLD_PICKAXE, pickaxe.material());
        assertEquals(org.bukkit.enchantments.Enchantment.DIG_SPEED, pickaxe.enchantment());
        assertEquals(3, pickaxe.enchantmentLevel());
        assertEquals(org.bukkit.enchantments.Enchantment.DAMAGE_ALL, pickaxe.second(), "Sharpness II, as on Hypixel");
        assertEquals(2, pickaxe.secondLevel());
        assertEquals(2, AttackDefenseGame.ATTACKER_ARMOUR, "iron: leggings and boots");
    }

    @Test
    void theAttackersKitIsInTheShop() {
        ShopCatalog catalog = ShopCatalog.standard();
        for (String entry : AttackDefenseGame.ATTACKER_KIT) {
            assertNotNull(InvisGame.catalogItem(catalog, entry.split(" ")[0]), entry);
        }
    }
}
