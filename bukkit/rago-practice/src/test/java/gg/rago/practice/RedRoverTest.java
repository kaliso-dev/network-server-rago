package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import gg.rago.game.RedRoverRotation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Red Rover: its bundled settings (the turns and the settings themselves: RedRoverRotationTest, in the game model). */
class RedRoverTest {

    @Test
    void theBundledSettingsAreAccepted() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertNotNull(config.getConfigurationSection("red-rover"));
        assertNull(RedRoverRotation.Settings.check(config.getString("red-rover.variante"), config.getString("red-rover.reste"),
                config.getInt("red-rover.points-pour-gagner"), config.getInt("red-rover.secondes-par-1v1")));
    }
}
