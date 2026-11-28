package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The Snowball Spleef of FUN PRACTICE: its floors, the block a snowball breaks, its settings. */
class SnowballSpleefTest {

    /** A floor of snow at y = 64, everywhere. */
    private static final SnowballSpleef.Solid FLOOR = (x, y, z) -> y == 64;

    @Test
    void theFloorsGoDownOneUnderTheOther() {
        assertEquals(0, SnowballSpleef.floorDy(0, 6), "the top floor");
        assertEquals(-6, SnowballSpleef.floorDy(1, 6));
        assertEquals(-12, SnowballSpleef.floorDy(2, 6));
    }

    @Test
    void aSnowballFallingOnTheFloorBreaksTheBlockUnderIt() {
        // it lands where it was before its last move: above the floor, falling
        assertArrayEquals(new int[] {3, 64, -2}, SnowballSpleef.struck(3.5, 65.4, -1.5, 0, -0.8, 0, FLOOR));
        // thrown across and down, it hits the block ahead of it
        int[] ahead = SnowballSpleef.struck(0.5, 65.2, 0.5, 1.0, -0.6, 0, FLOOR);
        assertNotNull(ahead);
        assertEquals(64, ahead[1]);
        assertTrue(ahead[0] >= 0 && ahead[0] <= 2, "" + ahead[0]);
    }

    @Test
    void aSnowballThatClipsTheCornerOfABlockBreaksThatBlock() {
        // only the block (1, 64, 0): the way goes into it through its top 0.02 from its edge and out
        // through its side 0.02 under its top (the owner, 23/11: « 100% du temps »); a look every
        // tenth of a block passed by it
        SnowballSpleef.Solid one = (x, y, z) -> x == 1 && y == 64 && z == 0;
        assertArrayEquals(new int[] {1, 64, 0}, SnowballSpleef.struck(1.68, 65.3, 0.5, 0.6, -0.6, 0, one));
        // the same along z, and with all three axes moving
        SnowballSpleef.Solid alongZ = (x, y, z) -> x == 0 && y == 64 && z == 1;
        assertArrayEquals(new int[] {0, 64, 1}, SnowballSpleef.struck(0.5, 65.3, 1.68, 0, -0.6, 0.6, alongZ));
        assertArrayEquals(new int[] {1, 64, 0}, SnowballSpleef.struck(1.68, 65.3, 0.3, 0.6, -0.6, 0.1, one));
        // its edge going down the other way, x decreasing
        SnowballSpleef.Solid west = (x, y, z) -> x == -2 && y == 64 && z == 0;
        assertArrayEquals(new int[] {-2, 64, 0}, SnowballSpleef.struck(-0.68, 65.3, 0.5, -0.6, -0.6, 0, west));
    }

    @Test
    void theFirstBlockOnItsWayIsTheOneItBreaks() {
        // two blocks on its way: the nearer one
        SnowballSpleef.Solid two = (x, y, z) -> y == 64 && (x == 2 || x == 3) && z == 0;
        assertArrayEquals(new int[] {2, 64, 0}, SnowballSpleef.struck(0.5, 65.5, 0.5, 1.2, -0.5, 0, two));
        // landing inside a block: that block
        assertArrayEquals(new int[] {0, 64, 0}, SnowballSpleef.struck(0.5, 64.9, 0.5, 0.3, -0.2, 0, FLOOR));
    }

    @Test
    void aNarrowFloorUnderTheOthers() {
        // the owner, 23/11: a third floor, narrower, 4-5 blocks under the second
        SnowballSpleef.Settings settings = new SnowballSpleef.Settings(18, 2, 9, 1, 9, 5);
        assertEquals(-14, settings.lowestDy(), "9 blocks to the second floor, then 5 to the narrow one");
        assertEquals(-9, new SnowballSpleef.Settings(18, 2, 9, 1).lowestDy(), "without it, the second floor is the lowest");
        assertEquals(-9, new SnowballSpleef.Settings(18, 2, 9, 1, 0, 5).lowestDy(), "a radius of 0: none");
        assertNull(SnowballSpleef.Settings.check(18, 2, 9, 1, 9, 5));
        assertNull(SnowballSpleef.Settings.check(18, 2, 9, 1, 0, 0), "none");
        assertTrue(SnowballSpleef.Settings.check(18, 2, 9, 1, 2, 5).startsWith("etage-etroit-rayon"));
        assertTrue(SnowballSpleef.Settings.check(18, 2, 9, 1, 18, 5).startsWith("etage-etroit-rayon"), "narrower than the others");
        assertTrue(SnowballSpleef.Settings.check(18, 2, 9, 1, 9, 2).startsWith("etage-etroit-ecart"));
        assertTrue(SnowballSpleef.Settings.check(18, 2, 9, 1, 9, 21).startsWith("etage-etroit-ecart"));
        assertTrue(SnowballSpleef.Settings.check(12, 4, 12, 1, 9, 13).startsWith("etages x"), "48 + 13 blocks deep");
    }

    @Test
    void aFastSnowballStillFindsTheBlock() {
        // 2.5 blocks a tick, 2 blocks above the floor: farther than the least reach of 1.5
        assertArrayEquals(new int[] {0, 64, 0}, SnowballSpleef.struck(0.5, 66.0, 0.5, 0, -2.5, 0, FLOOR));
    }

    @Test
    void nothingAlongItsWayOrNotMovingBreaksNothing() {
        assertNull(SnowballSpleef.struck(0.5, 70, 0.5, 0, -0.8, 0, FLOOR), "far above the floor");
        assertNull(SnowballSpleef.struck(0.5, 65.4, 0.5, 0, 0.8, 0, FLOOR), "going up");
        assertNull(SnowballSpleef.struck(0.5, 65.4, 0.5, 0, 0, 0, FLOOR), "not moving");
    }

    @Test
    void theSidesStartApartOnTheSnow() {
        assertEquals(6, SnowballSpleef.startDistance(12), 1e-9);
        assertEquals(3, SnowballSpleef.startDistance(5), 1e-9);
    }

    @Test
    void checksItsSettings() {
        assertNull(SnowballSpleef.Settings.check(12, 1, 6, 1));
        assertNull(SnowballSpleef.Settings.check(12, 5, 12, 0), "5 floors 12 apart: 60 blocks");
        assertTrue(SnowballSpleef.Settings.check(4, 1, 6, 1).startsWith("rayon"));
        assertTrue(SnowballSpleef.Settings.check(41, 1, 6, 1).startsWith("rayon"));
        assertTrue(SnowballSpleef.Settings.check(12, 0, 6, 1).startsWith("etages"));
        assertTrue(SnowballSpleef.Settings.check(12, 6, 6, 1).startsWith("etages"));
        assertTrue(SnowballSpleef.Settings.check(12, 2, 2, 1).startsWith("ecart-etages"));
        assertTrue(SnowballSpleef.Settings.check(12, 4, 20, 1).startsWith("etages x"));
        assertTrue(SnowballSpleef.Settings.check(12, 1, 6, 17).startsWith("boules-par-bloc"));
    }

    @Test
    void theBundledSettingsAreAccepted() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertNotNull(config.getConfigurationSection("snowball-spleef"));
        assertNull(SnowballSpleef.Settings.check(config.getInt("snowball-spleef.rayon"), config.getInt("snowball-spleef.etages"),
                config.getInt("snowball-spleef.ecart-etages"), config.getInt("snowball-spleef.boules-par-bloc")));
        int points = config.getInt("snowball-spleef.points-pour-gagner");
        assertTrue(points >= 1 && points <= 10);
        assertEquals(2, config.getInt("snowball-spleef.etages"), "two floors (the owner, 22/11)");
        assertEquals(18, config.getInt("snowball-spleef.rayon"), "a bigger map than the first one, of 12");
        assertEquals(9, SnowballSpleef.startDistance(18), 1e-9, "each side starts 9 blocks from the middle");
        int gap = config.getInt("snowball-spleef.ecart-etages");
        assertEquals(9, gap, "the lower floor lower (the owner, 22/11, 19h55): 6 before");
        // from the lower floor (its top 1 above it), the eyes 1.62 above the feet and 1.25 higher at the
        // top of a jump, the 4.5 blocks of reach of 1.8 end under the top floor
        assertTrue(1 + 1.62 + 1.25 + 4.5 < gap, "the top floor out of the reach of a player on the lower one");
        // the narrow floor under them (the owner, 23/11: « plus petit (étroit) et moins bas (4-5 blocks) »)
        int narrowRadius = config.getInt("snowball-spleef.etage-etroit-rayon");
        int narrowGap = config.getInt("snowball-spleef.etage-etroit-ecart");
        assertNull(SnowballSpleef.Settings.check(18, 2, gap, 1, narrowRadius, narrowGap));
        assertTrue(narrowRadius > 0 && narrowRadius < 18, "narrower: " + narrowRadius);
        assertTrue(narrowGap >= 4 && narrowGap <= 5, "4-5 blocks under the second floor: " + narrowGap);
        assertTrue(narrowRadius >= 9, "the start of each side, 9 blocks from the middle, over it: who falls straight down lands on it");
    }

    @Test
    void nobodyBreaksAFloorAboveThem() {
        // standing on the top floor at y 64: the feet at 65
        assertFalse(SnowballSpleef.above(64, 65), "the floor they stand on breaks");
        assertFalse(SnowballSpleef.above(55, 65), "a floor under them breaks (a snowball thrown down a hole)");
        // standing on the lower floor at y 55: the feet at 56, the top floor at 64 over their head
        assertTrue(SnowballSpleef.above(64, 56), "the top floor, over the head of a player on the lower one");
        assertTrue(SnowballSpleef.above(56, 56));
    }
}
