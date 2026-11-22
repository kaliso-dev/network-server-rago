package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.game.GameMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BotSettingsTest {

    private static Map<String, GameMode> modes() {
        Map<String, GameMode> byId = new HashMap<>();
        for (GameMode mode : Arrays.asList(new GameMode("top-bridge-1v1", "&aTop Bridge 1v1", 2, 1, false),
                new GameMode("top-bridge-2v2", "&aTop Bridge 2v2", 2, 2, false),
                new GameMode("sumo", "&aSumo duel", 2, 1, false),
                new GameMode("boxing", "&aBoxing", 2, 1, false),
                new GameMode("lobby-fight-1v1", "&aLobby Fight 1v1", 2, 1, false),
                new GameMode("ranked-sumo", "&6Ranked Sumo", 2, 1, true),
                new GameMode("clutch", "&eClutch", 1, 1, false))) {
            byId.put(mode.id(), mode);
        }
        return byId;
    }

    private static BotSettings read(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return BotSettings.read(yaml.getConfigurationSection("ragobot"), modes());
    }

    private static void assertInvalid(String text, String expectedStart) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> read(text));
        assertTrue(error.getMessage().startsWith(expectedStart), error.getMessage());
    }

    @Test
    void withoutItsSectionItPlaysTheModesTheOwnerAskedFor() {
        BotSettings bots = BotSettings.read(null, modes());
        assertTrue(bots.enabled);
        assertEquals("qRago", bots.skin);
        assertEquals(10, bots.max);
        assertEquals(BotSettings.DEFAULT_MODES, bots.modes);
        assertEquals("Top Bridge 1v1, Top Bridge 2v2, Sumo duel, Boxing and Lobby Fight 1v1", bots.modeNames,
                "Boxing and Lobby Fight 1v1: step 1 of the owner's order of 22/11 (17h54)");
        assertSame(BotBrain.Skill.BUNDLED, bots.skill);
        assertTrue(bots.plays("sumo"));
        assertTrue(bots.plays("boxing"));
        assertTrue(bots.plays("lobby-fight-1v1"));
        assertFalse(bots.plays("ranked-sumo"));
    }

    @Test
    void withoutItsSectionAConfigWithoutTheseModesStillStarts() {
        BotSettings bots = BotSettings.read(null, Collections.<String, GameMode>emptyMap());
        assertTrue(bots.modes.isEmpty());
        assertEquals("no mode", bots.modeNames);
    }

    @Test
    void readsItsSection() throws Exception {
        BotSettings bots = read("ragobot:\n  actif: false\n  skin: Notch\n  max: 3\n  modes: [sumo, sumo]\n"
                + "  skill:\n    cps: 8\n    precision: 0.5\n    portee: 2.8\n    reaction: 5\n");
        assertFalse(bots.enabled);
        assertEquals("Notch", bots.skin);
        assertEquals(3, bots.max);
        assertEquals(Collections.singletonList("sumo"), bots.modes, "once");
        assertEquals("Sumo duel", bots.modeNames);
        assertEquals(8, bots.skill.cps, 0);
        assertEquals(0.5, bots.skill.accuracy, 0);
        assertEquals(2.8, bots.skill.reach, 0);
        assertEquals(5, bots.skill.reaction);
        assertEquals(BotBrain.Skill.BUNDLED.turn, bots.skill.turn, 0, "the rest as bundled");
        assertEquals(BotBrain.Skill.BUNDLED.healAt, bots.skill.healAt, 0);
    }

    @Test
    void refusesWhatItCannotPlay() {
        assertInvalid("ragobot:\n  modes: [ranked-sumo]\n", "ragobot.modes: ranked-sumo is ranked");
        assertInvalid("ragobot:\n  modes: [clutch]\n", "ragobot.modes: clutch has one team");
        assertInvalid("ragobot:\n  modes: [nope]\n", "ragobot.modes: no mode nope");
        assertInvalid("ragobot:\n  modes: sumo\n", "ragobot.modes: a list");
        assertInvalid("ragobot:\n  max: 0\n", "ragobot.max");
        assertInvalid("ragobot:\n  max: 51\n", "ragobot.max");
        assertInvalid("ragobot:\n  skin: \"not a name\"\n", "ragobot.skin");
        assertInvalid("ragobot:\n  skill:\n    cps: 30\n", "ragobot.skill.cps: from 1 to 20");
        assertInvalid("ragobot:\n  skill:\n    portee: 3.5\n", "ragobot.skill.portee: from 1 to 3");
        assertInvalid("ragobot:\n  skill:\n    precision: high\n", "ragobot.skill.precision: a number");
        assertInvalid("ragobot:\n  skill:\n    reaction: 10\n", "ragobot.skill.reaction: from 0 to 9");
    }

    @Test
    void theBundledConfigHoldsTheBundledSkill() throws Exception {
        BotSettings bots = LobbySettings.load(LobbySettingsTest.bundledConfig()).bots();
        assertTrue(bots.enabled);
        assertEquals("qRago", bots.skin, "the owner's skin (22/11)");
        assertEquals(BotSettings.DEFAULT_MODES, bots.modes);
        BotBrain.Skill bundled = BotBrain.Skill.BUNDLED;
        assertEquals(bundled.cps, bots.skill.cps, 0);
        assertEquals(bundled.accuracy, bots.skill.accuracy, 0);
        assertEquals(bundled.reach, bots.skill.reach, 0);
        assertEquals(bundled.strafe, bots.skill.strafe, 0);
        assertEquals(bundled.wTap, bots.skill.wTap, 0);
        assertEquals(bundled.jumpReset, bots.skill.jumpReset, 0);
        assertEquals(bundled.reaction, bots.skill.reaction);
        assertEquals(bundled.turn, bots.skill.turn, 0);
        assertEquals(bundled.healAt, bots.skill.healAt, 0);
    }
}
