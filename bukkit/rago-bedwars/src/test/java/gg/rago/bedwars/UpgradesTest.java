package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.bedwars.Upgrades.Kind;
import gg.rago.bedwars.Upgrades.Trap;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** The team upgrades and traps: their prices, their names, the forge, the base, the menu. */
class UpgradesTest {

    @Test
    void theUpgradesCostWhatTheyCostOnHypixel() {
        // solo and doubles
        assertEquals(4, Kind.SHARPENED_SWORDS.price(1, 1));
        assertEquals(8, Kind.REINFORCED_ARMOR.price(3, 2));
        assertEquals(16, Kind.REINFORCED_ARMOR.price(4, 2));
        assertEquals(4, Kind.MANIAC_MINER.price(2, 2));
        assertEquals(6, Kind.FORGE.price(3, 1));
        assertEquals(1, Kind.HEAL_POOL.price(1, 2));
        // 3v3v3v3 and 4v4v4v4
        assertEquals(8, Kind.SHARPENED_SWORDS.price(1, 3));
        assertEquals(20, Kind.REINFORCED_ARMOR.price(3, 4));
        assertEquals(30, Kind.REINFORCED_ARMOR.price(4, 4));
        assertEquals(6, Kind.MANIAC_MINER.price(2, 4));
        assertEquals(16, Kind.FORGE.price(4, 3));
        assertEquals(3, Kind.HEAL_POOL.price(1, 4));
        assertEquals(4, Kind.REINFORCED_ARMOR.tiers());
        assertEquals(1, Kind.HEAL_POOL.tiers());
        // the traps: 1, 2, then 4 diamonds, three at most
        assertEquals(1, Upgrades.trapPrice(0));
        assertEquals(2, Upgrades.trapPrice(1));
        assertEquals(4, Upgrades.trapPrice(2));
        assertEquals(3, Upgrades.MAX_TRAPS);
    }

    @Test
    void eachTierHasItsName() {
        assertEquals("Sharpened Swords", Kind.SHARPENED_SWORDS.tierName(1));
        assertEquals("Reinforced Armor II", Kind.REINFORCED_ARMOR.tierName(2));
        assertEquals("Maniac Miner I", Kind.MANIAC_MINER.tierName(1));
        assertEquals("Iron Forge", Kind.FORGE.tierName(1));
        assertEquals("Golden Forge", Kind.FORGE.tierName(2));
        assertEquals("Emerald Forge", Kind.FORGE.tierName(3));
        assertEquals("Molten Forge", Kind.FORGE.tierName(4));
        assertEquals("Protection IV", Kind.REINFORCED_ARMOR.tierEffect(4));
        assertEquals("Haste II", Kind.MANIAC_MINER.tierEffect(2));
        assertEquals("+200% Resources", Kind.FORGE.tierEffect(4));
        for (Kind kind : Kind.values()) {
            assertTrue(kind.description.length > 0, kind + " says what it does");
        }
        for (Trap trap : Trap.values()) {
            assertTrue(trap.description.length > 0, trap + " says what it does");
        }
    }

    @Test
    void theForgeGoesFasterTierAfterTier() {
        assertEquals(1.0, Upgrades.forgeSpeed(0));
        assertEquals(1.5, Upgrades.forgeSpeed(1));
        assertEquals(2.0, Upgrades.forgeSpeed(2));
        assertEquals(2.0, Upgrades.forgeSpeed(3), "the Emerald Forge adds emeralds, not speed");
        assertEquals(3.0, Upgrades.forgeSpeed(4));
        BedwarsSettings.Rate iron = Upgrades.faster(new BedwarsSettings.Rate(30, 48), 1.5);
        assertEquals(20, iron.ticks);
        assertEquals(48, iron.max, "as many items on the ground");
        assertEquals(1, Upgrades.faster(new BedwarsSettings.Rate(1, 4), 3).ticks, "never under a tick");
    }

    @Test
    void theBaseIsTheIslandAroundTheSpawn() {
        BedwarsMap.Team island = new BedwarsMap.Team();
        assertEquals(Upgrades.MIN_BASE, Upgrades.baseRadius(island), "no spawn yet");
        island.spawn = new MapPoint(0, 64, 0, 0f, 0f);
        island.bed = new MapPoint(0, 64, 10, 0f, 0f);
        island.generator = new MapPoint(-6, 64, 0, 0f, 0f);
        assertEquals(16.0, Upgrades.baseRadius(island), 1e-9, "out to the bed, and a little more");
        island.generator = new MapPoint(40, 64, 0, 0f, 0f);
        assertEquals(Upgrades.MAX_BASE, Upgrades.baseRadius(island), "never out to the next island");
        assertTrue(Upgrades.inBase(5, 0, 5, 12));
        assertTrue(Upgrades.inBase(0, 18, 0, 12), "a bridge over the base");
        assertFalse(Upgrades.inBase(13, 0, 0, 12));
        assertFalse(Upgrades.inBase(0, -9, 0, 12), "under the island");
    }

    @Test
    void theMenuSellsEachUpgradeAndTrapInItsPlace() {
        assertEquals(Kind.values().length, UpgradeMenu.UPGRADE_SLOTS.length);
        assertEquals(Trap.values().length, UpgradeMenu.TRAP_SLOTS.length);
        assertEquals(Upgrades.MAX_TRAPS, UpgradeMenu.QUEUE_SLOTS.length);
        assertEquals(Kind.SHARPENED_SWORDS, UpgradeMenu.upgradeAt(10));
        assertEquals(Kind.HEAL_POOL, UpgradeMenu.upgradeAt(20));
        assertEquals(Trap.ITS_A_TRAP, UpgradeMenu.trapAt(14));
        assertEquals(Trap.MINER_FATIGUE, UpgradeMenu.trapAt(23));
        assertNull(UpgradeMenu.upgradeAt(13));
        assertNull(UpgradeMenu.trapAt(39), "the queue sells nothing");
        for (int slot : UpgradeMenu.QUEUE_SLOTS) {
            assertTrue(slot < UpgradeMenu.SIZE);
        }
    }

    @Test
    void sharpenedSwordsSharpensTheSwordsAndTheAxes() {
        assertTrue(BedwarsGame.sharpened(Material.WOOD_SWORD));
        assertTrue(BedwarsGame.sharpened(Material.DIAMOND_SWORD));
        assertTrue(BedwarsGame.sharpened(Material.IRON_AXE));
        assertFalse(BedwarsGame.sharpened(Material.WOOD_PICKAXE));
        assertFalse(BedwarsGame.sharpened(Material.SHEARS));
        Upgrades upgrades = new Upgrades();
        assertFalse(upgrades.sharpness());
        upgrades.setTier(Kind.SHARPENED_SWORDS, 1);
        upgrades.setTier(Kind.REINFORCED_ARMOR, 3);
        assertTrue(upgrades.sharpness());
        assertEquals(3, upgrades.protection());
        assertEquals(0, upgrades.haste());
        assertFalse(upgrades.healPool());
    }
}
