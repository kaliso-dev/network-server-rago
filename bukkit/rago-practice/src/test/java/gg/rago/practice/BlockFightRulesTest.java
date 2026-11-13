package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.Function;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.junit.jupiter.api.Test;

/**
 * The block placement: the points of a knock into the void, the hearts of the score, the cracks of
 * the arena's blocks, /center of Arena Free.
 */
class BlockFightRulesTest {

    @Test
    void inBlockPlacementAKnockIntoTheVoidGivesTenPoints() {
        assertEquals(55, BlockFight.afterFall(45), "the other side gets 10");
        assertEquals(10, BlockFight.afterFall(0));
    }

    @Test
    void theHeartsFallAsTheOtherSideNearsItsHundredHits() {
        assertEquals(20.0, BlockFight.healthFor(0, 100), 1e-9, "0 hits: 20 HP");
        assertEquals(10.0, BlockFight.healthFor(50, 100), 1e-9, "half way: half the hearts");
        assertEquals(2.0, BlockFight.healthFor(90, 100), 1e-9);
        assertEquals(19.8, BlockFight.healthFor(1, 100), 1e-9, "each hit takes a tenth of a heart");
        assertEquals(BlockFight.LEAST_HEALTH, BlockFight.healthFor(99, 100), 1e-9, "half a heart at least: the score never kills");
        assertEquals(BlockFight.LEAST_HEALTH, BlockFight.healthFor(100, 100), 1e-9);
        assertEquals(BlockFight.LEAST_HEALTH, BlockFight.healthFor(110, 100), 1e-9, "a fall past the target");
        assertEquals(10.0, BlockFight.healthFor(25, 50), 1e-9, "another target");
        assertEquals(20.0, BlockFight.healthFor(5, 0), 1e-9, "no target: full health");
    }

    /**
     * A critical of the wooden sword Sharpness I (8.75) on iron armour Protection IV: the armour
     * takes 60%, Protection 20 parts of 25 of what is left when the hit was worked out, and the
     * given draw each time it is worked out again (it is drawn at random).
     */
    @SuppressWarnings("deprecation")
    private static EntityDamageEvent hitOnProtection(int draw) {
        Map<DamageModifier, Double> modifiers = new EnumMap<>(DamageModifier.class);
        Map<DamageModifier, Function<? super Double, Double>> functions = new EnumMap<>(DamageModifier.class);
        modifiers.put(DamageModifier.BASE, 8.75);
        functions.put(DamageModifier.BASE, f -> -0.0);
        modifiers.put(DamageModifier.ARMOR, -5.25);
        functions.put(DamageModifier.ARMOR, f -> -f * 0.6);
        modifiers.put(DamageModifier.MAGIC, -3.5 * 20 / 25);
        functions.put(DamageModifier.MAGIC, f -> -f * draw / 25);
        return new EntityDamageEvent(null, EntityDamageEvent.DamageCause.ENTITY_ATTACK, modifiers, functions);
    }

    @Test
    void aHitOfTheBlockPlacementNeitherHurtsNorHeals() {
        for (int draw = 10; draw <= 20; draw++) {
            EntityDamageEvent hit = hitOnProtection(draw);
            PracticeGame.noDamage(hit);
            assertEquals(0.0, hit.getFinalDamage(), 0.0, "the hearts stay the score, Protection drawn " + draw);
        }
    }

    @Test
    void centerTakesTheNearestSpotToTheMiddleWhereOneCanStand() {
        assertArrayEquals(new int[] {0, 0}, BlockArena.nearestStand(30, (x, z) -> true), "the centre itself");
        assertArrayEquals(new int[] {-2, -2}, BlockArena.nearestStand(30,
                (x, z) -> (x == 3 && z == 1) || (x == -2 && z == -2) || (x == 5 && z == 0)), "the nearest ring first");
        assertArrayEquals(new int[] {0, 2}, BlockArena.nearestStand(30,
                (x, z) -> (x == 2 && z == 2) || (x == 0 && z == 2)), "on a ring, the nearest to the centre");
        assertArrayEquals(new int[] {0, 0}, BlockArena.nearestStand(30, (x, z) -> false), "none: the centre");
        assertArrayEquals(new int[] {0, 0}, BlockArena.nearestStand(3, (x, z) -> x == 10), "none near enough: the centre");
    }

    @Test
    void theBlocksOfTheArenaCrackMoreAndMoreBeforeTheyBreak() {
        assertEquals(0, BlockCracks.stage(0, BlockArena.DECAY_TICKS));
        assertEquals(0, BlockCracks.stage(29, BlockArena.DECAY_TICKS));
        assertEquals(1, BlockCracks.stage(30, BlockArena.DECAY_TICKS));
        assertEquals(5, BlockCracks.stage(150, BlockArena.DECAY_TICKS));
        assertEquals(9, BlockCracks.stage(299, BlockArena.DECAY_TICKS));
        assertEquals(9, BlockCracks.stage(1000, BlockArena.DECAY_TICKS), "never past the last stage");
        assertTrue(BlockCracks.newId() < 0, "a number no entity has");
    }

    @Test
    void aPlayerSunkUnderTheirStartIsSeenSoButNotOneWhoWalksOff() {
        Location start = new Location(null, 10.5, 89, 20.5);
        assertTrue(PracticeGame.sunk(new Location(null, 10.5, 86, 20.5), start), "in the shaft under the gold block");
        assertTrue(PracticeGame.sunk(new Location(null, 10.7, 87.9, 20.3), start));
        assertFalse(PracticeGame.sunk(new Location(null, 10.5, 88.2, 20.5), start), "less than a block lower");
        assertFalse(PracticeGame.sunk(new Location(null, 11.4, 86, 20.5), start), "walked off the side, falling");
        assertFalse(PracticeGame.sunk(new Location(null, 10.5, 89, 20.5), start), "on the start");
    }

    @Test
    void onlyWhatWearsIsMadeUnbreakable() {
        assertTrue(Unbreakable.wears(Material.WOOD_SWORD.getMaxDurability()));
        assertTrue(Unbreakable.wears(Material.SHEARS.getMaxDurability()));
        assertTrue(Unbreakable.wears(Material.IRON_CHESTPLATE.getMaxDurability()));
        assertFalse(Unbreakable.wears(Material.SANDSTONE.getMaxDurability()));
        assertFalse(Unbreakable.wears(Material.ENDER_PEARL.getMaxDurability()));
    }
}
