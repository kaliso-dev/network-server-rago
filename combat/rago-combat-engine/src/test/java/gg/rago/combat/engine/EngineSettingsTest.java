package gg.rago.combat.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.combat.model.KnockbackProfile;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class EngineSettingsTest {

    @Test
    void shippedConfigLoadsAsVanillaOverride() throws Exception {
        EngineSettings settings = EngineSettings.load(shippedConfig());

        assertEquals(EngineMode.OVERRIDE, settings.mode());
        assertEquals("vanilla", settings.defaultProfile());
        assertEquals(1, settings.profiles().size());
        assertTrue(settings.worldProfiles().isEmpty());
        assertFalse(settings.recordHits());
        assertEquals(KnockbackProfile.vanilla(), settings.profileFor("world"));
    }

    @Test
    void readsProfilesAndWorldOverrides() throws Exception {
        EngineSettings settings = EngineSettings.load(yaml(
                "mode: observe\n"
                        + "active-profile: test-a\n"
                        + "record-hits: true\n"
                        + "world-profiles:\n"
                        + "  arena_b: vanilla\n"
                        + "profiles:\n"
                        + "  test-a:\n"
                        + "    friction: 2\n"
                        + "    horizontal: 0.35\n"
                        + "    vertical: 0.35\n"
                        + "    vertical-limit: 0.4\n"
                        + "    extra-horizontal: 0.5\n"
                        + "    extra-vertical: 0.1\n"));

        assertEquals(EngineMode.OBSERVE, settings.mode());
        assertTrue(settings.recordHits());
        assertEquals("test-a", settings.profileFor("arena_a").name());
        assertEquals(0.35D, settings.profileFor("arena_a").horizontal());
        assertEquals("vanilla", settings.profileFor("arena_b").name());
    }

    @Test
    void vanillaCannotBeRedefined() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> EngineSettings.load(yaml(
                "profiles:\n"
                        + "  vanilla:\n"
                        + "    friction: 1\n"
                        + "    horizontal: 1\n"
                        + "    vertical: 1\n"
                        + "    vertical-limit: 1\n"
                        + "    extra-horizontal: 1\n"
                        + "    extra-vertical: 1\n")));
        assertTrue(error.getMessage().contains("built in"), error.getMessage());
    }

    @Test
    void unknownProfilesAndTyposAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> EngineSettings.load(yaml("active-profile: hypixel\n")));
        assertThrows(IllegalArgumentException.class, () -> EngineSettings.load(yaml(
                "world-profiles:\n  arena: missing\n")));
        assertThrows(IllegalArgumentException.class, () -> EngineSettings.load(yaml("mode: replace\n")));
        assertThrows(IllegalArgumentException.class, () -> EngineSettings.load(yaml(
                "profiles:\n  test-a:\n    friction: 2\n")));
    }

    @Test
    void runtimeChangesBuildNewSnapshots() throws Exception {
        EngineSettings original = EngineSettings.load(shippedConfig());

        EngineSettings observed = original.withMode(EngineMode.OBSERVE);
        EngineSettings mapped = original.withWorldProfile("arena", "vanilla");

        assertEquals(EngineMode.OVERRIDE, original.mode());
        assertEquals(EngineMode.OBSERVE, observed.mode());
        assertEquals("vanilla", mapped.worldProfiles().get("arena"));
        assertTrue(mapped.withWorldProfile("arena", null).worldProfiles().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> original.withDefaultProfile("missing"));
    }

    private static YamlConfiguration shippedConfig() throws Exception {
        try (InputStream in = EngineSettingsTest.class.getResourceAsStream("/config.yml");
                Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(reader);
            return config;
        }
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(text);
        return config;
    }
}
