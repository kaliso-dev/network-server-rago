package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The block clutch: its knockback settings, its top fight platform, its islands side by side. */
class ClutchTrainingTest {

    @Test
    void theBundledIslandIsTheOwnersClutchIsland() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        ConfigurationSection section = config.getConfigurationSection("clutch");
        assertNotNull(section);
        assertEquals("BlockClutchExtend", section.getString("monde"));
        assertNull(RagoPractice.checkClutch(section));
        // the player stands on the island, one block above its centre, facing south (towards its bed)
        List<Integer> centre = section.getIntegerList("centre");
        List<Double> player = section.getDoubleList("joueur");
        assertEquals(1, player.get(1) - centre.get(1), 1e-9);
        assertEquals(0, player.get(3), 1e-9);
        section.set("joueur", Arrays.asList(870607.5, 92, 870600.5, 0));
        assertTrue(RagoPractice.checkClutch(section).startsWith("joueur"));
        section.set("zone", Arrays.asList(870560, 80, 870643, 870616, 102, 870657));
        assertTrue(RagoPractice.checkClutch(section).startsWith("zone"), "wider than the room between two islands");
    }

    @Test
    void theHitThrowsBackwardsFromWhereThePlayerLooks() {
        // facing north (yaw 180): thrown south, as on the plugin's own island
        assertArrayEquals(new double[] {0, 1}, ClutchTraining.thrownWay(180, 0), 1e-9);
        // facing south (yaw 0), as on the owner's island: thrown north
        assertArrayEquals(new double[] {0, -1}, ClutchTraining.thrownWay(0, 0), 1e-9);
        // facing west (yaw 90): thrown east
        assertArrayEquals(new double[] {1, 0}, ClutchTraining.thrownWay(90, 0), 1e-9);
        assertEquals(180, ClutchTraining.plainIsland().player[3], 1e-9);
    }

    @Test
    void moreKnockbackThrowsFartherAndTheStickFarthest() {
        for (int kb = 1; kb < 5; kb++) {
            assertTrue(ClutchTraining.throwOf(kb + 1, false)[0] > ClutchTraining.throwOf(kb, false)[0]);
        }
        assertEquals(0.42, ClutchTraining.throwOf(3, false)[0], 1e-9, "3: a hit of a fight");
        assertTrue(ClutchTraining.throwOf(1, true)[0] > ClutchTraining.throwOf(5, false)[0], "the stick beats any hit");
        assertTrue(ClutchTraining.throwOf(3, true)[1] > ClutchTraining.throwOf(3, false)[1]);
    }

    @Test
    void theTopFightPlatformTapersAtBothEnds() {
        int blocks = 0;
        for (int x = -12; x <= 12; x++) {
            assertEquals(ClutchTraining.halfWidth(x), ClutchTraining.halfWidth(-x), "the same at both ends");
            if (x > 0) {
                assertTrue(ClutchTraining.halfWidth(x) <= ClutchTraining.halfWidth(x - 1), "narrower towards the tips");
            }
            blocks += Math.max(0, 2 * ClutchTraining.halfWidth(x) + 1);
        }
        assertEquals(7, 2 * ClutchTraining.halfWidth(0) + 1, "seven wide in the middle");
        assertEquals(0, ClutchTraining.halfWidth(10), "one block at the tips");
        assertEquals(-1, ClutchTraining.halfWidth(11));
        assertEquals(11 * 7 + 4 * 5 + 4 * 3 + 2, blocks);
    }

    @Test
    void eachPlayerBuildsOnlyAroundTheirOwnZone() {
        assertTrue(ClutchTraining.PLACE_ALONG >= 10, "the whole platform");
        assertTrue(2 * ClutchTraining.PLACE_ALONG < ClutchTraining.SPACING, "never on the next zone");
        assertTrue(ClutchTraining.SPACING <= 32, "close enough to see the others");
        assertTrue(ClutchTraining.SPACING >= gg.rago.game.ArenaGrid.MIN_SPACING, "a grid of zones takes it");
    }
}
