package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void theServerHostsItsOwnEventsWhenTheLobbyIsFullAndNothingRan() {
        assertTrue(Events.Settings.of(null).auto.on, "on without the section, with its default settings");
        assertEquals(20, Events.Settings.of(null).auto.minutes);
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection section = config.createSection("evenements");
        ConfigurationSection auto = section.createSection("automatiques");
        assertNull(Events.Settings.check(section));
        Events.Auto read = Events.Settings.of(section).auto;
        assertTrue(read.on, "on as soon as the section is there");
        assertEquals(20, read.minutes);
        assertEquals(6, read.lobbyPlayers);
        assertEquals(java.util.Arrays.asList(EventGame.Kind.values()), read.kinds, "every event by default");
        auto.set("types", java.util.Arrays.asList("lms", "sumo", "lms"));
        assertEquals(java.util.Arrays.asList(EventGame.Kind.LMS, EventGame.Kind.SUMO), Events.Settings.of(section).auto.kinds);
        auto.set("types", java.util.Arrays.asList("sumo", "spleef"));
        assertEquals("evenements.automatiques.types: sumo, brackets or lms, not \"spleef\"", Events.Settings.check(section));
        auto.set("types", java.util.Arrays.asList("sumo"));
        auto.set("minutes", 2);
        assertEquals("evenements.automatiques.minutes: from 5 to 1440", Events.Settings.check(section));
        auto.set("minutes", 10);
        auto.set("joueurs-lobby", 1);
        assertEquals("evenements.automatiques.joueurs-lobby: from 2 to 200", Events.Settings.check(section));
        auto.set("joueurs-lobby", 6);
        assertNull(Events.Settings.check(section));

        Events.Auto ten = Events.Settings.of(section).auto;
        long quiet = 1_000_000L;
        long later = quiet + 10 * 60_000L;
        assertTrue(ten.due(later, quiet, false, 6), "ten quiet minutes, six in the lobby");
        assertTrue(!ten.due(later - 1, quiet, false, 6), "not before the ten minutes");
        assertTrue(!ten.due(later, quiet, false, 5), "not with five in the lobby");
        assertTrue(!ten.due(later, quiet, true, 30), "not while an event runs or is announced");
        auto.set("actif", false);
        assertTrue(!Events.Settings.of(section).auto.due(later, quiet, false, 30), "off");
    }

    @Test
    void theShippedConfigIsValid() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/config.yml"));
        ConfigurationSection section = config.getConfigurationSection("evenements");
        assertTrue(section != null, "the config.yml has its section evenements");
        assertNull(Events.Settings.check(section));
        assertTrue(Events.Settings.of(section).auto.on, "the server hosts its own events");
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
    void theAnnouncementRunsJoin() {
        String json = Events.announcement(EventGame.Kind.SUMO, "Steve", 40, 3, 40, 0, false);
        assertTrue(json.contains("\"action\":\"run_command\",\"value\":\"/join\""), json);
        assertTrue(json.contains("Click to join the Sumo event"), json);
        assertTrue(json.contains("[CLICK TO JOIN]"), json);
        assertTrue(json.contains("SUMO EVENT") && json.contains("Steve") && json.contains("40s"), json);
        // a line above and a line under
        assertEquals(2, json.split(java.util.regex.Pattern.quote(Events.SEPARATOR), -1).length - 1, json);
        com.google.gson.JsonObject parsed = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
        assertEquals("/join", parsed.getAsJsonObject("clickEvent").get("value").getAsString());
        // once the wait is over: how many more players it needs
        String waiting = Events.announcement(EventGame.Kind.LMS, Events.SERVER_HOST, 0, 1, 40, 1, false);
        assertTrue(waiting.contains("Needs 1 more player"), waiting);
        new com.google.gson.JsonParser().parse(waiting);
        // its own players: no button, nothing to click
        String in = Events.announcement(EventGame.Kind.BRACKETS, "Steve", 20, 2, 40, 0, true);
        assertTrue(in.contains("You are in!") && !in.contains("[CLICK TO JOIN]") && !in.contains("clickEvent"), in);
        new com.google.gson.JsonParser().parse(in);
    }

    @Test
    void theLinesAreCentred() {
        String pad = Events.pad("[CLICK TO JOIN]", true);
        int middle = Events.width(pad, false) + Events.width("[CLICK TO JOIN]", true) / 2;
        assertTrue(Math.abs(Events.CHAT_WIDTH / 2 - middle) <= 4, "centred: " + middle);
        assertTrue(Events.width(Events.SEPARATOR, false) <= Events.CHAT_WIDTH, "the line fits the chat");
    }

    @Test
    void anEventIsAnnouncedEveryTwentySeconds() {
        assertEquals(20, Events.ANNOUNCE_EVERY);
    }

    @Test
    void theStatusSaysWhenItStarts() {
        assertEquals("1/40 players, starts in 50s", Events.status(1, 40, 0, 50, 2));
        assertEquals("3/40 players (2 in a game), starts in 5s", Events.status(3, 40, 2, 5, 2));
        assertEquals("3/40 players, starting", Events.status(3, 40, 0, 0, 2));
        assertEquals("3/40 players (2 in a game), starting", Events.status(3, 40, 2, 0, 2),
                "those in a game leave it when it starts");
        assertEquals("4/40 players (4 in a game), starting", Events.status(4, 40, 4, 0, 3));
        assertEquals("1/40 players, needs 1 more player to start", Events.status(1, 40, 0, 0, 2));
        assertEquals("1/40 players (1 in a game), needs 2 more players to start", Events.status(1, 40, 1, 0, 3));
    }

    @Test
    void itStartsAfterTheWaitWithEnoughPlayersInGamesOrNot() {
        assertFalse(Events.launches(1, 10, 2), "the wait is not over");
        assertFalse(Events.launches(0, 1, 2), "one player in, two needed");
        assertTrue(Events.launches(0, 2, 2), "two players in, in a game or not: they leave their games");
        assertTrue(Events.launches(0, 5, 2));
    }
}
