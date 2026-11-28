package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Hypixel Bow Practice (the owner's ask of 27/11): its three levels on the owner's map, and the rules. */
class BowPracticeTest {

    private static YamlConfiguration bundled() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
    }

    @Test
    void theBundledLevelsAreTheMarkersOfTheMap() {
        YamlConfiguration config = bundled();
        ConfigurationSection section = config.getConfigurationSection("bow-practice");
        assertNotNull(section);
        assertNull(RagoPractice.checkBowPractice(section, config.getInt("espacement")));
        assertEquals("BowPractice", section.getString("monde"));
        List<Map<?, ?>> levels = section.getMapList("niveaux");
        assertEquals(3, levels.size(), "gold, diamond, emerald");
        // the player stands on the two marker blocks, as the owner's F3 gave it (36.7 66 62.8, 31.5 66 62.6, 22.2 65 33.7)
        assertEquals(Arrays.asList(36.5, 66, 62.5, 153.5, 0), levels.get(0).get("joueur"));
        assertEquals(Arrays.asList(31.5, 66, 62.5, 114.8, 3.4), levels.get(1).get("joueur"));
        assertEquals(Arrays.asList(22.5, 65, 33.5, -46.4, -7.9), levels.get(2).get("joueur"));
        // the zombie starts on the marker block, the first of its bridge
        assertEquals(Arrays.asList(31, 66, 51, 31, 59), levels.get(0).get("pont"), "straight, to the south");
        assertEquals(Arrays.asList(15, 65, 52, 26, 62), levels.get(1).get("pont"), "diagonal");
        assertEquals(Arrays.asList(31, 67, 49, 31, 36), levels.get(2).get("pont"), "high, to the north");
    }

    @Test
    void aLevelIsReadFromTheOrigin() {
        BowPractice.Level[] levels = BowPractice.levels(
                Collections.singletonList(new double[] {36.5, 66, 62.5, 153.5, 0}),
                Collections.singletonList(new int[] {31, 66, 51, 31, 59}), new int[] {26, 66, 50});
        assertEquals(1, levels.length);
        BowPractice.Level level = levels[0];
        assertArrayEquals(new double[] {10.5, 0, 12.5, 153.5, 0}, level.player, 1e-9);
        assertArrayEquals(new int[] {5, 0, 1}, level.first);
        assertArrayEquals(new int[] {5, 9}, level.last);
        assertArrayEquals(new double[] {0, 1}, level.way(), 1e-9, "straight along +z");
        assertEquals(8.0, level.length(), 1e-9);
        assertEquals(0.0, level.along(5.5, 1.5), 1e-9, "the middle of its first block");
        assertEquals(8.0, level.along(5.5, 9.5), 1e-9, "the middle of its last block");
        assertEquals(0.0, level.aside(5.5, 4.5), 1e-9, "on the bridge");
        assertTrue(level.aside(6.5, 4.5) < 0 || level.aside(6.5, 4.5) > 0, "off the bridge, to a side");
    }

    @Test
    void theDiagonalBridgeGoesStraightFromItsFirstBlockToItsLast() {
        BowPractice.Level level = BowPractice.levels(
                Collections.singletonList(new double[] {31.5, 66, 62.5, 114.8, 3.4}),
                Collections.singletonList(new int[] {15, 65, 52, 26, 62}), new int[] {0, 0, 0})[0];
        double[] way = level.way();
        assertEquals(1.0, Math.hypot(way[0], way[1]), 1e-9);
        assertTrue(way[0] > 0.6 && way[1] > 0.6, "to the south-east, as on the map");
        assertEquals(Math.hypot(11, 10), level.length(), 1e-9);
    }

    @Test
    void theRulesOfTheOwner() {
        assertEquals(2, BowPractice.AIR_HITS, "two hits in the air after the first arrow (the owner, 27/11)");
        assertEquals(5, BowPractice.ZONE, "about five by five blocks to move on");
        assertEquals(3, BowPractice.LEVELS.length, "three levels, the dyes of every training");
        assertEquals(3, BowPractice.plainCourse().levels.length, "the plain course has them too");
        assertEquals(2.0, BowPractice.clamp(5.0, -2.0, 2.0), 1e-9);
        assertEquals(-2.0, BowPractice.clamp(-5.0, -2.0, 2.0), 1e-9);
        assertEquals(1.0, BowPractice.clamp(1.0, -2.0, 2.0), 1e-9);
    }

    @Test
    void aWrongSectionIsSaid() {
        YamlConfiguration config = bundled();
        int spacing = config.getInt("espacement");
        ConfigurationSection section = config.getConfigurationSection("bow-practice");
        section.set("niveaux", Collections.emptyList());
        assertTrue(RagoPractice.checkBowPractice(section, spacing).startsWith("niveaux"));
    }
}
