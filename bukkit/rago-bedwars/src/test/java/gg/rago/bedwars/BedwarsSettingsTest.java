package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import org.bukkit.Material;
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
        assertEquals(Arrays.asList("ranked-1v1", "ranked-2v2", "ranked-3v3", "ranked-4v4", "skyrise-1v1", "skyrise-2v2",
                "first-rush", "first-rush-duo"), settings.modes());
        assertEquals(Arrays.asList("Archway", "Invasion", "Lectus", "Extinction", "Paladin", "Rise"),
                settings.mapsOf("ranked-2v2"));
        assertEquals(Collections.singletonList("SkyRise"), settings.mapsOf("skyrise-1v1"));
        assertEquals(10, settings.mapsOf("first-rush").size());
        assertEquals(settings.mapsOf("first-rush"), settings.mapsOf("first-rush-duo"));
        assertTrue(settings.mapsOf("sumo").isEmpty());
        assertEquals(Collections.singletonList(Material.WOOL), settings.rushDefense());
        assertEquals(300, settings.rushSeconds());
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
        assertEquals(2.0, settings.fireballSelf());
        assertEquals(3.0, settings.fireballOthers());
    }

    @Test
    void anEmptyConfigGivesTheDefaults() {
        BedwarsSettings settings = BedwarsSettings.read(new YamlConfiguration());
        assertTrue(settings.modes().isEmpty());
        assertEquals(20, settings.iron().ticks);
        assertEquals(3, settings.emerald().size());
        assertEquals(24 * 60, settings.emeraldIII());
        assertEquals(2.0, settings.fireballSelf());
        assertEquals(Collections.singletonList(Material.WOOL), settings.rushDefense());
        assertEquals(300, settings.rushSeconds());
    }

    @Test
    void modesTakeTheirMaps() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("modes:\n  ranked-1v1: toutes\n  first-rush: [Gelato, Nebuc]\n"
                + "premier-rush:\n  defense: [END_STONE, wool]\n  minutes: 2.5");
        BedwarsSettings settings = BedwarsSettings.read(config);
        assertEquals(Arrays.asList("ranked-1v1", "first-rush"), settings.modes());
        assertTrue(settings.mapsOf("ranked-1v1").isEmpty(), "toutes: every map");
        assertEquals(Arrays.asList("Gelato", "Nebuc"), settings.mapsOf("first-rush"));
        assertEquals(Arrays.asList(Material.ENDER_STONE, Material.WOOL), settings.rushDefense());
        assertEquals(150, settings.rushSeconds());
        // the list of the first version: each mode on every map
        config = new YamlConfiguration();
        config.loadFromString("modes:\n  - ranked-1v1\n  - ranked-2v2\npremier-rush:\n  defense: []");
        settings = BedwarsSettings.read(config);
        assertEquals(Arrays.asList("ranked-1v1", "ranked-2v2"), settings.modes());
        assertTrue(settings.mapsOf("ranked-2v2").isEmpty());
        assertTrue(settings.rushDefense().isEmpty(), "no defense");
    }

    @Test
    void wrongValuesNameTheSetting() {
        assertProblem("modes: [\"Ranked 1v1\"]", "modes");
        assertProblem("parties-max: 0", "parties-max");
        assertProblem("reapparition-secondes: 60", "reapparition-secondes");
        assertProblem("generateurs:\n  fer:\n    secondes: 0", "generateurs.fer.secondes");
        assertProblem("generateurs:\n  diamant:\n    secondes: [30, 20]", "generateurs.diamant.secondes");
        assertProblem("evenements:\n  diamant-2: 20", "evenements.emeraude-2");
        assertProblem("boule-de-feu:\n  degats-sur-soi: -1", "boule-de-feu.degats-sur-soi");
        assertProblem("modes:\n  first-rush: Gelato", "modes.first-rush");
        assertProblem("modes:\n  first-rush: []", "modes.first-rush");
        assertProblem("modes:\n  first-rush: [\"Gel ato\"]", "modes.first-rush");
        assertProblem("modes:\n  First Rush: [Gelato]", "modes");
        assertProblem("premier-rush:\n  defense: [TORCH]", "premier-rush.defense");
        assertProblem("premier-rush:\n  defense: [WOOL, WOOL, WOOL, WOOL, WOOL]", "premier-rush.defense");
        assertProblem("premier-rush:\n  minutes: 0", "premier-rush.minutes");
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
