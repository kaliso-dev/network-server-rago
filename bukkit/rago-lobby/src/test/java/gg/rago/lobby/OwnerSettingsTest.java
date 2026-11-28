package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class OwnerSettingsTest {

    private static OwnerTools.Settings read(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return OwnerTools.Settings.read(config.getConfigurationSection("outils-owner"));
    }

    @Test
    void withoutItsSectionTheToolsAreTheOwnersAccountOnly() throws Exception {
        OwnerTools.Settings settings = read("autre: 1\n");
        assertEquals("qRago", settings.account, "qRago only (the owner's answer of 22/11)");
        assertNull(settings.uuid);
    }

    @Test
    void readsItsSection() throws Exception {
        OwnerTools.Settings settings = read("outils-owner:\n  compte: Notch\n  uuid: 069a79f4-44e9-4726-a5be-fca90e38aaf5\n");
        assertEquals("Notch", settings.account);
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), settings.uuid);
        assertEquals("", read("outils-owner:\n  compte: \"\"\n").account, "no tool for anyone");
        assertNull(read("outils-owner:\n  compte: qRago\n  uuid: \"\"\n").uuid);
    }

    @Test
    void refusesWhatIsNotAnAccountOrAnId() {
        assertThrows(IllegalArgumentException.class, () -> read("outils-owner:\n  compte: q Rago\n"));
        assertThrows(IllegalArgumentException.class, () -> read("outils-owner:\n  compte: qRago\n  uuid: qRago\n"));
    }

    @Test
    void theBundledConfigGivesTheToolsToQRago() throws Exception {
        OwnerTools.Settings settings = OwnerTools.Settings.read(
                LobbySettingsTest.bundledConfig().getConfigurationSection("outils-owner"));
        assertEquals("qRago", settings.account);
        assertNull(settings.uuid, "online mode or BungeeCord: the name, verified");
    }
}
