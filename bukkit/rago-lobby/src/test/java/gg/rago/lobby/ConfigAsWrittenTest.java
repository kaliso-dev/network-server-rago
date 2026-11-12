package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** A config.yml of before a new section: the section left out is the plugin's own, not an empty one. */
class ConfigAsWrittenTest {

    @Test
    void aSectionLeftOutIsThePluginsOwn() throws Exception {
        YamlConfiguration bundled;
        try (Reader reader = new InputStreamReader(ConfigAsWrittenTest.class.getResourceAsStream("/config.yml"),
                StandardCharsets.UTF_8)) {
            bundled = YamlConfiguration.loadConfiguration(reader);
        }
        YamlConfiguration before = older(bundled);
        assertNotNull(before.getConfigurationSection("titres"), "with the bundled one behind it, Bukkit makes it an empty section");
        assertTrue(before.getConfigurationSection("titres").getKeys(false).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(older(bundled)), "the lobby would not start");

        YamlConfiguration older = older(bundled);
        LobbySettings.asWritten(older);
        assertNull(older.getConfigurationSection("titres"));
        ServerSettings server = ServerSettings.load(older);
        assertEquals(Titles.defaults().families().size(), server.titles().families().size(), "the titles of the plugin");
        assertEquals(Titles.defaults().steps().size(), server.titles().steps().size());
        assertEquals("rago.gg", older.getString("adresse"), "what it holds is read as before");
    }

    /** A config.yml of before the titles, the bundled one as its defaults: what Bukkit's getConfig gives. */
    private static YamlConfiguration older(YamlConfiguration bundled) throws Exception {
        YamlConfiguration older = new YamlConfiguration();
        older.loadFromString("adresse: rago.gg\nmodes:\n  sumo: {}\n");
        older.setDefaults(bundled);
        return older;
    }
}
