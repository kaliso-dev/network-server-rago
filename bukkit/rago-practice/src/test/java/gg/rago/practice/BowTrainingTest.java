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

/** The bow training and the bow duel: their place on Archway, the bridges of the bots, the score. */
class BowTrainingTest {

    private static YamlConfiguration bundled() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
    }

    @Test
    void theBundledPlaceIsCheckedAndIsThePlainOne() {
        YamlConfiguration config = bundled();
        ConfigurationSection section = config.getConfigurationSection("bow-training");
        assertNotNull(section);
        assertNull(RagoPractice.checkBow(section, config.getInt("espacement")));
        assertEquals("Archway", section.getString("monde"));
        // the plain course stands where the map's would, from the same origin
        BowTraining.Course plain = BowTraining.plainCourse();
        List<Integer> origin = section.getIntegerList("centre");
        List<Double> player = section.getDoubleList("joueur");
        for (int k = 0; k < 3; k++) {
            assertEquals(player.get(k) - origin.get(k), plain.player[k], 1e-9);
        }
        assertEquals(section.getInt("pont-y") - origin.get(1), plain.bridgeY);
        List<?> bridges = section.getList("ponts");
        assertEquals(plain.lines.length, bridges.size());
        for (int i = 0; i < bridges.size(); i++) {
            List<?> line = (List<?>) bridges.get(i);
            assertArrayEquals(new int[] {((Number) line.get(0)).intValue() - origin.get(0), ((Number) line.get(1)).intValue() - origin.get(2),
                ((Number) line.get(2)).intValue() - origin.get(0), ((Number) line.get(3)).intValue() - origin.get(2)}, plain.lines[i]);
        }
        assertEquals(10, config.getInt("points-pour-gagner.bow"));
        assertNull(RagoPractice.checkModes(config));
    }

    @Test
    void aWrongPlaceIsToldWhy() {
        YamlConfiguration config = bundled();
        int spacing = config.getInt("espacement");
        ConfigurationSection section = config.getConfigurationSection("bow-training");
        section.set("ponts", Arrays.asList(Arrays.asList(1, 2, 1, 2)));
        assertTrue(RagoPractice.checkBow(section, spacing).startsWith("ponts"));
        section.set("ponts", Arrays.asList(Arrays.asList(-13, -22, -13, -61)));
        section.set("joueur", Arrays.asList(1, 2, 3));
        assertTrue(RagoPractice.checkBow(section, spacing).startsWith("joueur"));
    }

    @Test
    void aBotKnowsWhereItIsOnItsBridge() {
        // a bridge going north (-z) from (0, 20) to (0, -19)
        int[] line = {0, 20, 0, -19};
        assertArrayEquals(new double[] {0, -1}, BowTraining.way(line), 1e-9);
        assertEquals(39, BowTraining.length(line), 1e-9);
        assertEquals(0, BowTraining.along(line, 0.5, 20.5), 1e-9, "on its first block");
        assertEquals(10, BowTraining.along(line, 0.5, 10.5), 1e-9);
        // going north, the right hand is to the east: +x
        assertEquals(0.4, BowTraining.aside(line, 0.9, 10.5), 1e-9);
        assertEquals(-0.4, BowTraining.aside(line, 0.1, 10.5), 1e-9);
    }

    @Test
    void theBotsBridgeFasterWithTheSettingAndTheScoreCounts() {
        assertEquals(2.6 / 20, BowTraining.step(1), 1e-9);
        assertEquals(5.0 / 20, BowTraining.step(5), 1e-9);
        assertEquals(0, BowTraining.accuracy(0, 0));
        assertEquals(50, BowTraining.accuracy(3, 6));
        assertEquals(100, BowTraining.accuracy(7, 5), "never over 100");
    }

    @Test
    void inTheDuelBlueStartsOnTheMiddleFacingTheBase() {
        BowTraining.Course course = BowTraining.plainCourse();
        assertArrayEquals(course.player, BowDuel.startOf(course, 0), 1e-9);
        double[] blue = BowDuel.startOf(course, 1);
        assertEquals(course.lines[0][0] + 0.5, blue[0], 1e-9);
        assertEquals(course.lines[0][1] + 0.5, blue[2], 1e-9);
        // the bridge goes north: blue looks north (yaw 180)
        assertEquals(180, Math.abs(blue[3]), 1e-6);
    }
}
