package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class EventsTest {

    @Test
    void theEventsAreFoundByTheirModeAndByTheirName() {
        assertEquals(EventGame.Kind.SUMO, EventGame.Kind.ofMode("event-sumo"));
        assertEquals(EventGame.Kind.LMS, EventGame.Kind.ofMode("event-lms"));
        assertNull(EventGame.Kind.ofMode("sumo"), "the sumo duel is not an event");
        assertEquals(EventGame.Kind.BRACKETS, EventGame.Kind.of("Brackets"));
        assertEquals(EventGame.Kind.LMS, EventGame.Kind.of("last man standing"));
        assertNull(EventGame.Kind.of("bedwars"));
    }

    @Test
    void theFightersOfAOneVOneFaceEachOther() {
        double[] red = EventGame.startOf(0, 2, false);
        double[] blue = EventGame.startOf(1, 2, false);
        assertEquals(-EventGame.START_DISTANCE, red[0], 1e-9);
        assertEquals(EventGame.START_DISTANCE, blue[0], 1e-9);
        assertEquals(-90, red[2], 1e-9, "red looks towards +x");
        assertEquals(90, blue[2], 1e-9, "blue looks towards -x");
    }

    @Test
    void aLastManStandingIsACircleFacingTheCentre() {
        for (int count : new int[] {2, 7, 40}) {
            for (int i = 0; i < count; i++) {
                double[] start = EventGame.startOf(i, count, true);
                double radius = Math.hypot(start[0], start[1]);
                assertTrue(radius >= EventGame.START_DISTANCE - 1e-9 && radius <= EventGame.MAX_CIRCLE + 1e-9, "radius " + radius);
                // Minecraft's look: x = -sin(yaw), z = cos(yaw); it points from the player to the centre
                double yaw = Math.toRadians(start[2]);
                assertEquals(-start[0] / radius, -Math.sin(yaw), 1e-9);
                assertEquals(-start[1] / radius, Math.cos(yaw), 1e-9);
            }
        }
    }

    @Test
    void theSettingsHaveTheirDefaultsAndTheirRanges() {
        Events.Settings defaults = Events.Settings.of(null);
        assertEquals(60, defaults.waitSeconds);
        assertEquals(2, defaults.minPlayers);
        assertEquals(40, defaults.maxPlayers);
        assertEquals(30, defaults.cooldownMinutes);
        assertEquals(250, defaults.winXp);

        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection section = config.createSection("evenements");
        assertNull(Events.Settings.check(section));
        section.set("attente-secondes", 5);
        assertEquals("evenements.attente-secondes: from 15 to 300", Events.Settings.check(section));
        section.set("attente-secondes", 30);
        section.set("joueurs-min", 10);
        section.set("joueurs-max", 8);
        assertEquals("evenements.joueurs-min: not more than joueurs-max", Events.Settings.check(section));
        section.set("joueurs-max", 12);
        assertNull(Events.Settings.check(section));
        assertEquals(30, Events.Settings.of(section).waitSeconds);
    }

    @Test
    void theShippedConfigIsValid() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/config.yml"));
        ConfigurationSection section = config.getConfigurationSection("evenements");
        assertTrue(section != null, "the config.yml has its section evenements");
        assertNull(Events.Settings.check(section));
    }

    @Test
    void thePluginDeclaresTheCommandsOfTheEvents() throws Exception {
        // the server refuses a plugin.yml that does not parse: the plugin would not load at all
        YamlConfiguration plugin = new YamlConfiguration();
        plugin.loadFromString(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/plugin.yml")),
                java.nio.charset.StandardCharsets.UTF_8));
        for (String command : new String[] {"host", "event", "join", "aimtop", "speed"}) {
            assertTrue(plugin.isConfigurationSection("commands." + command), command);
        }
        assertEquals("op", plugin.getString("permissions." + Events.PERMISSION + ".default"));
    }

    @Test
    void aHostWaitsTheCooldown() {
        assertEquals(0, Events.cooldownLeft(null, 30, 1_000_000L), "never hosted");
        assertEquals(30 * 60_000L - 1_000L, Events.cooldownLeft(1_000L, 30, 2_000L));
        assertEquals(0, Events.cooldownLeft(1_000L, 30, 1_000L + 30 * 60_000L));
        assertEquals(0, Events.cooldownLeft(1_000L, 0, 1_000L), "no cooldown");
    }

    @Test
    void theButtonRunsJoin() {
        String json = Events.button(EventGame.Kind.SUMO);
        assertTrue(json.contains("\"action\":\"run_command\",\"value\":\"/join\""), json);
        assertTrue(json.contains("Join the Sumo event"), json);
        // well formed: as many { as }, as many [ as ]
        assertEquals(json.chars().filter(c -> c == '{').count(), json.chars().filter(c -> c == '}').count());
        assertEquals(json.chars().filter(c -> c == '[').count(), json.chars().filter(c -> c == ']').count());
    }
}
