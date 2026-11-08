package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.game.GameMode;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LobbySettingsTest {

    private static YamlConfiguration bundledConfig() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(LobbySettingsTest.class.getResourceAsStream("/config.yml"),
                StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        return yaml;
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    @Test
    void readsTheBundledConfig() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        assertEquals("lobby", settings.world());
        assertEquals(0, settings.voidHeight());
        assertEquals(1000, settings.defaultRating());
        assertEquals(35, settings.modes().size());

        GameMode ranked = settings.catalog().get("ranked-1v1");
        assertTrue(ranked.ranked());
        assertEquals("1v1", ranked.format());
        assertTrue(settings.catalog().get("ranked-2v2").ranked());
        assertEquals("2v2", settings.catalog().get("ranked-2v2").format());
        assertEquals("solo", settings.catalog().get("clutch").format());
        assertEquals("2v2", settings.catalog().get("block-fight-2v2").format());
        assertFalse(settings.catalog().get("sumo").ranked());
        assertEquals(Material.BED, settings.display("ranked-1v1").icon());
        assertEquals(Material.FIREBALL, settings.display("fireball").icon());
        assertEquals("ranked-1v1", settings.modes().get(0).mode().id(), "the order of config.yml");
        assertTrue(settings.title().length() <= 32);
        assertEquals(500, settings.window().width(3_600_000));
    }

    @Test
    void groupsTheBundledModesByCategory() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        assertEquals(3, settings.categories().size());
        assertEquals("duels", settings.categories().get(0).id(), "left to right, as in the lobby");
        assertEquals("ranked", settings.categories().get(1).id());
        assertEquals(Material.IRON_SWORD, settings.categories().get(0).icon());
        assertEquals("sumo", settings.modesIn("duels").get(0).mode().id());
        int total = 0;
        for (ModeCategory category : settings.categories()) {
            int count = settings.modesIn(category.id()).size();
            assertTrue(count > 0 && count <= LobbySettings.MAX_MODES_PER_CATEGORY, category.id() + ": " + count);
            total += count;
        }
        assertEquals(settings.modes().size(), total, "every mode is in a category");
    }

    @Test
    void readsTheBundledNpcs() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        assertEquals(21, settings.npcs().size());
        LobbyNpc ranked = settings.npc("rankedbedwars1v1ou2v2elo");
        assertEquals(java.util.Arrays.asList("ranked-1v1", "ranked-2v2"), ranked.modeIds());
        assertTrue(settings.npc("leaderboardprincipal").leaderboard());
        assertFalse(ranked.leaderboard());
    }

    @Test
    void checksNpcs() {
        assertInvalid("pnj:\n  pnj-sumo:\n    modes: [autre]\nmodes:\n  sumo: {}\n", "pnj.pnj-sumo.modes");
        assertInvalid("pnj:\n  pnj-sumo: {}\nmodes:\n  sumo: {}\n", "pnj.pnj-sumo");
        assertInvalid("pnj:\n  a:\n    modes: [sumo]\nclassements:\n  a: Titre\nmodes:\n  sumo: {}\n", "classements.a");
    }

    @Test
    void checksCategories() {
        String categories = "categories:\n  duels:\n    nom: Duels\n";
        assertInvalid(categories + "modes:\n  sumo: {}\n", "modes.sumo.categorie");
        assertInvalid(categories + "modes:\n  sumo:\n    categorie: autre\n", "modes.sumo.categorie");
        assertInvalid("categories:\n  Duels: {}\nmodes:\n  sumo: {}\n", "categories.Duels");
    }

    @Test
    void translatesColours() throws Exception {
        LobbySettings settings = LobbySettings.load(yaml("modes:\n  sumo:\n    nom: '&aSumo'\n    description: [Ligne]\n"));
        assertEquals("§aSumo", settings.catalog().get("sumo").name());
        assertEquals("§7Ligne", settings.display("sumo").description().get(0));
        assertEquals(Material.PAPER, settings.display("sumo").icon(), "default icon");
    }

    @Test
    void refusesInvalidConfigs() {
        assertInvalid("monde: lobby\n", "modes");
        assertInvalid("modes:\n  Sumo:\n    nom: Sumo\n", "modes.Sumo");
        assertInvalid("modes:\n  sumo:\n    icone: PAS_UN_OBJET\n", "modes.sumo.icone");
        assertInvalid("modes:\n  sumo:\n    equipes: 1\n    classe: true\n", "modes.sumo");
        assertInvalid("monde: 'mon lobby'\nmodes:\n  sumo: {}\n", "monde");
        assertInvalid("file:\n  ecart-elo-secondes: 0\nmodes:\n  sumo: {}\n", "file");
        assertInvalid("titre: '&b&lUN TITRE BEAUCOUP TROP LONG POUR 1.8'\nmodes:\n  sumo: {}\n", "titre");
    }

    private static void assertInvalid(String text, String expectedStart) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> LobbySettings.load(yaml(text)));
        assertTrue(error.getMessage().startsWith(expectedStart), error.getMessage());
    }
}
