package gg.rago.combat.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.combat.model.KnockbackProfile;
import gg.rago.combat.model.MeasuredProfiles;
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
        assertEquals(2, settings.profiles().size());
        assertTrue(settings.worldProfiles().isEmpty());
        assertFalse(settings.recordHits());
        assertEquals(KnockbackProfile.vanilla(), settings.profileFor("world"));
        assertEquals(10, settings.hitDelayTicks());
        assertEquals(20, settings.maximumNoDamageTicks());
        assertEquals(0, settings.pingSimulationMs());
        assertTrue(settings.pingSimulationDelaysKnockback());
        assertEquals(0, settings.knockbackDelayMs(0));
        assertEquals(0, settings.simulatedDelayTicks(0, 0));
    }

    @Test
    void measuredProfilesAreBuiltIn() throws Exception {
        EngineSettings settings = EngineSettings.load(yaml("active-profile: hypixel-approx-v1\n"));

        assertEquals(MeasuredProfiles.hypixelApproxV1(), settings.profileFor("world"));
        assertEquals(0.07D, settings.profileFor("world").extraVertical());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> EngineSettings.load(yaml(
                "profiles:\n"
                        + "  hypixel-approx-v1:\n"
                        + "    friction: 2\n"
                        + "    horizontal: 0.4\n"
                        + "    vertical: 0.4\n"
                        + "    vertical-limit: 0.4\n"
                        + "    extra-horizontal: 0.5\n"
                        + "    extra-vertical: 0.1\n")));
        assertTrue(error.getMessage().contains("built in"), error.getMessage());
    }

    @Test
    void hitDelaySetsHalfTheDamageCooldown() throws Exception {
        EngineSettings settings = EngineSettings.load(yaml("hit-delay-ticks: 9\n"));

        assertEquals(9, settings.hitDelayTicks());
        assertEquals(18, settings.maximumNoDamageTicks());
        assertEquals(10, settings.withHitDelayTicks(10).hitDelayTicks());
        assertEquals(9, settings.withMode(EngineMode.OBSERVE).hitDelayTicks());
        assertEquals(9, settings.withDefaultProfile("vanilla").withWorldProfile("arena", "vanilla").hitDelayTicks());
    }

    @Test
    void hitDelayOutOfRangeOrNotWholeIsRejected() throws Exception {
        for (String value : new String[] {"0", "21", "-1", "9.5", "neuf"}) {
            assertThrows(IllegalArgumentException.class, () -> EngineSettings.load(yaml("hit-delay-ticks: " + value + "\n")),
                    value);
        }
        EngineSettings settings = EngineSettings.load(shippedConfig());
        assertThrows(IllegalArgumentException.class, () -> settings.withHitDelayTicks(0));
        assertThrows(IllegalArgumentException.class, () -> settings.withHitDelayTicks(21));
        assertEquals(1, settings.withHitDelayTicks(1).hitDelayTicks());
        assertEquals(20, settings.withHitDelayTicks(20).hitDelayTicks());
    }

    @Test
    void pingSimulationDelaysTheKnockbackOfPlayersWithLessPing() throws Exception {
        EngineSettings settings = EngineSettings.load(yaml("ping-simulation-ms: 150\n"));

        assertEquals(150, settings.pingSimulationMs());
        assertTrue(settings.pingSimulationDelaysKnockback());
        assertEquals(150, settings.missingPingMs(0));
        assertEquals(102, settings.missingPingMs(48));
        assertEquals(0, settings.missingPingMs(150));
        assertEquals(0, settings.missingPingMs(400));
        assertEquals(150, settings.missingPingMs(-1));
        // The wait is the ping the victim lacks; the attacker's does not count.
        assertEquals(150, settings.knockbackDelayMs(0));
        assertEquals(102, settings.knockbackDelayMs(48));
        assertEquals(0, settings.knockbackDelayMs(150));
        assertEquals(0, settings.knockbackDelayMs(200));
        assertEquals(500, settings.withPingSimulationMs(500).knockbackDelayMs(0));
        assertEquals(0, settings.withPingSimulationMs(0).knockbackDelayMs(0));
        // The motion covers what the knockback's wait leaves of the victim's missing ping: nothing.
        assertEquals(0, settings.simulatedDelayTicks(0, settings.knockbackDelayMs(0)));
        assertEquals(0, settings.simulatedDelayTicks(48, settings.knockbackDelayMs(48)));
        assertEquals(2, settings.simulatedDelayTicks(0, 75));
        assertEquals(0, settings.simulatedDelayTicks(150, 0));
    }

    @Test
    void pingSimulationCanLeaveTheKnockbackOnTime() throws Exception {
        EngineSettings settings = EngineSettings.load(yaml(
                "ping-simulation-ms: 150\nping-simulation-delays-knockback: false\n"));

        assertFalse(settings.pingSimulationDelaysKnockback());
        assertEquals(0, settings.knockbackDelayMs(0));
        assertTrue(settings.withPingSimulationDelaysKnockback(true).pingSimulationDelaysKnockback());
        assertFalse(settings.withHitDelayTicks(9).withPingSimulationMs(100).pingSimulationDelaysKnockback());
        assertThrows(IllegalArgumentException.class,
                () -> EngineSettings.load(yaml("ping-simulation-delays-knockback: 1\n")));
        // Without a wait, the whole missing ping comes from the motion, rounded to ticks of 50 ms.
        assertEquals(3, settings.simulatedDelayTicks(0, 0));
        assertEquals(2, settings.simulatedDelayTicks(30, 0));
        assertEquals(2, settings.simulatedDelayTicks(48, 0));
        assertEquals(1, settings.simulatedDelayTicks(100, 0));
        assertEquals(0, settings.simulatedDelayTicks(140, 0));
        assertEquals(0, settings.simulatedDelayTicks(150, 0));
        assertEquals(0, settings.simulatedDelayTicks(400, 0));
        assertEquals(3, settings.simulatedDelayTicks(-1, 0));
        assertEquals(10, settings.withPingSimulationMs(500).simulatedDelayTicks(0, 0));
        assertEquals(0, settings.withPingSimulationMs(0).simulatedDelayTicks(0, 0));
    }

    @Test
    void pingSimulationRangeIsChecked() throws Exception {
        EngineSettings settings = EngineSettings.load(yaml("ping-simulation-ms: 150\n"));
        assertEquals(150, settings.withHitDelayTicks(9).withMode(EngineMode.OBSERVE).withDefaultProfile("vanilla")
                .withWorldProfile("arena", "vanilla").pingSimulationMs());
        for (String value : new String[] {"-1", "501", "1.5", "cent"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> EngineSettings.load(yaml("ping-simulation-ms: " + value + "\n")), value);
        }
        assertThrows(IllegalArgumentException.class, () -> settings.withPingSimulationMs(501));
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
