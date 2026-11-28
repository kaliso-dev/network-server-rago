package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.game.RedRoverRotation;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Red Rover Top Bridge: its settings, read in the config.yml of RagoPractice with those of every Red Rover. */
class RedRoverTopBridgeTest {

    /** red-rover in the config.yml of RagoPractice (the tests run in the module's folder). */
    private static ConfigurationSection practiceRedRover() throws Exception {
        try (Reader reader = new InputStreamReader(new FileInputStream("../rago-practice/src/main/resources/config.yml"),
                StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader).getConfigurationSection("red-rover");
        }
    }

    @Test
    void readsTheRedRoverSettingsOfThePractice() throws Exception {
        ConfigurationSection section = practiceRedRover();
        assertNotNull(section, "red-rover in RagoPractice's config.yml");
        RedRoverRotation.Settings rover = RedRoverTopBridgeGame.settingsOf(section);
        assertNotNull(rover, "the bundled settings are accepted");
        assertNull(RedRoverTopBridgeGame.problemOf(section));
        assertEquals(RedRoverRotation.Variant.POINTS, rover.variant);
        assertEquals(RedRoverRotation.Stays.WINNER, rover.stays);
        assertEquals(3, rover.target);
        assertEquals(90, rover.roundSeconds);
        assertEquals(1, RedRoverTopBridgeGame.roundsOf(section), "a round of one life wins a 1v1");
    }

    @Test
    void wrongSettingsAreRefused() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("red-rover.variante", "both");
        config.set("red-rover.points-par-1v1-top-bridge", 6);
        ConfigurationSection section = config.getConfigurationSection("red-rover");
        assertNull(RedRoverTopBridgeGame.settingsOf(section));
        assertTrue(RedRoverTopBridgeGame.problemOf(section).startsWith("variante"));
        assertEquals(-1, RedRoverTopBridgeGame.roundsOf(section), "more rounds than a game of Top Bridge");
        config.set("red-rover.points-par-1v1-top-bridge", 0);
        assertEquals(-1, RedRoverTopBridgeGame.roundsOf(section));
        config.set("red-rover.points-par-1v1-top-bridge", 5);
        assertEquals(5, RedRoverTopBridgeGame.roundsOf(section));
        assertEquals(RedRoverTopBridgeGame.ROUNDS_PER_1V1, RedRoverTopBridgeGame.roundsOf(new YamlConfiguration()),
                "missing: one round");
    }

    @Test
    void theTopOfTheEndReadsAsTheKillersOfABedWarsGame() {
        assertEquals("  " + ChatColor.GOLD + ChatColor.BOLD + "2nd" + ChatColor.GRAY + " - " + ChatColor.WHITE + "Bob"
                + ChatColor.GRAY + " - " + ChatColor.WHITE + 1 + ChatColor.GRAY + " 1v1 won", RedRoverTopBridgeGame.topLine(1, "Bob", 1));
        assertEquals(3, RedRoverTopBridgeGame.TOP);
    }
}
