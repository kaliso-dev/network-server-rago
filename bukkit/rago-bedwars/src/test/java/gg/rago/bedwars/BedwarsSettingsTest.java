package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BedwarsSettingsTest {

    /** The config.yml of the plugin (the tests run in the module's folder). */
    private static YamlConfiguration bundled() throws Exception {
        try (Reader reader = new InputStreamReader(new FileInputStream("src/main/resources/config.yml"),
                StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    @Test
    void readsTheBundledConfig() throws Exception {
        BedwarsSettings settings = BedwarsSettings.read(bundled());
        assertEquals(Arrays.asList("ranked-1v1", "ranked-2v2", "ranked-3v3", "ranked-4v4"), settings.modes());
        assertEquals(8, settings.maxGames());
        assertTrue(settings.demo());
        assertEquals(5, settings.respawnSeconds());
        assertEquals(20, settings.iron().ticks);
        assertEquals(120, settings.gold().ticks);
        assertEquals(600, settings.diamond().get(0).ticks);
        assertEquals(240, settings.diamond().get(2).ticks);
        assertEquals(4, settings.diamond().get(1).max);
        assertEquals(1300, settings.emerald().get(0).ticks);
        assertEquals(6 * 60, settings.diamondII());
        assertEquals(30 * 60, settings.bedsGone());
        assertEquals(45 * 60, settings.gameEnd());
    }

    @Test
    void anEmptyConfigGivesTheDefaults() {
        BedwarsSettings settings = BedwarsSettings.read(new YamlConfiguration());
        assertTrue(settings.modes().isEmpty());
        assertEquals(20, settings.iron().ticks);
        assertEquals(3, settings.emerald().size());
        assertEquals(24 * 60, settings.emeraldIII());
    }

    @Test
    void wrongValuesNameTheSetting() {
        assertProblem("modes: [\"Ranked 1v1\"]", "modes");
        assertProblem("parties-max: 0", "parties-max");
        assertProblem("reapparition-secondes: 60", "reapparition-secondes");
        assertProblem("generateurs:\n  fer:\n    secondes: 0", "generateurs.fer.secondes");
        assertProblem("generateurs:\n  diamant:\n    secondes: [30, 20]", "generateurs.diamant.secondes");
        assertProblem("evenements:\n  diamant-2: 20", "evenements.emeraude-2");
    }

    private static void assertProblem(String yaml, String setting) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        IllegalArgumentException problem = assertThrows(IllegalArgumentException.class, () -> BedwarsSettings.read(config));
        assertTrue(problem.getMessage().startsWith(setting), problem.getMessage());
    }
}
