package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class HypixelMenuTest {

    private static Material shown(String id) {
        return HypixelMenu.shopMaterial(id).getType();
    }

    @Test
    void namesTheHypixelIds() {
        assertEquals("Speed II Potion (45 Seconds)", HypixelMenu.pretty("speed_ii_potion_(45_seconds)"));
        assertEquals("Stick (Knockback I)", HypixelMenu.pretty("stick_(knockback_i)"));
        assertEquals("Bow (Power I Punch I)", HypixelMenu.pretty("bow_(power_i__punch_i)"));
        assertEquals("Wool", HypixelMenu.pretty("wool"));
    }

    @Test
    @SuppressWarnings("deprecation") // ItemStack#getDurability is the 1.8 data value
    void showsEachIdWithItsItem() {
        assertEquals(Material.WOOL, shown("wool"));
        assertEquals(Material.STAINED_CLAY, shown("hardened_clay"));
        assertEquals(Material.STAINED_GLASS, shown("blast-proof_glass"));
        assertEquals(Material.ENDER_STONE, shown("end_stone"));
        assertEquals(Material.WOOD, shown("oak_wood_planks"));
        assertEquals(Material.STONE_SWORD, shown("stone_sword"));
        assertEquals(Material.DIAMOND_SWORD, shown("diamond_sword"));
        assertEquals(Material.STICK, shown("stick_(knockback_i)"));
        assertEquals(Material.CHAINMAIL_BOOTS, shown("chainmail_boots"));
        assertEquals(Material.IRON_BOOTS, shown("iron_boots"));
        assertEquals(Material.WOOD_PICKAXE, shown("wooden_pickaxe"));
        assertEquals(Material.DIAMOND_PICKAXE, shown("diamond_pickaxe"));
        assertEquals(Material.WOOD_AXE, shown("wooden_axe"));
        assertEquals(Material.SHEARS, shown("shears"));
        assertEquals(Material.BOW, shown("bow_(power_i__punch_i)"));
        assertEquals(Material.ARROW, shown("arrow"));
        assertEquals(Material.GOLDEN_APPLE, shown("golden_apple"));
        assertEquals(Material.SNOW_BALL, shown("bedbug"));
        assertEquals(Material.FIREBALL, shown("fireball"));
        assertEquals(Material.TNT, shown("tnt"));
        assertEquals(Material.ENDER_PEARL, shown("ender_pearl"));
        assertEquals(Material.WATER_BUCKET, shown("water_bucket"));
        assertEquals(Material.EGG, shown("bridge_egg"));
        assertEquals(Material.MILK_BUCKET, shown("magic_milk"));
        assertEquals(Material.CHEST, shown("compact_pop-up_tower"));
        assertEquals(Material.PAPER, shown("something_new"), "an id added by Hypixel later");

        ItemStack golem = HypixelMenu.shopMaterial("dream_defender");
        assertEquals(Material.MONSTER_EGG, golem.getType());
        assertEquals(99, golem.getDurability(), "the iron golem egg");
        ItemStack speed = HypixelMenu.shopMaterial("speed_ii_potion_(45_seconds)");
        assertEquals(Material.POTION, speed.getType());
        assertEquals(8194, speed.getDurability());
        assertEquals(8206, HypixelMenu.shopMaterial("invisibility_potion_(30_seconds)").getDurability());
    }
}
