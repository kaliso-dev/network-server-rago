package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }
}
