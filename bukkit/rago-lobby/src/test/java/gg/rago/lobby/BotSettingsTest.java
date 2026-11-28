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
                new GameMode("block-fight-1v1", "&aBlock placement 1v1", 2, 1, false),
                new GameMode("block-fight-2v2", "&aBlock placement 2v2", 2, 2, false),
                new GameMode("red-rover-block-placement", "&aRed Rover Block Placement", 1, 3, false),
                new GameMode("block-edit-duel", "&aBlock edit 1v1", 2, 1, false),
                new GameMode("red-rover-block-edit", "&aRed Rover Block Edit", 1, 3, false),
                new GameMode("ranked-sumo", "&6Ranked Sumo", 2, 1, true),
                new GameMode("clutch", "&eClutch", 1, 1, false),
                new GameMode("block-method-1v1", "&aBlock Method 1v1", 2, 1, false))) {
            byId.put(mode.id(), mode);
        }
        return byId;
    }

    /** The Red Rovers among these modes (their format: 1v1v1 in config.yml). */
    private static final java.util.Set<String> IN_TURNS = new java.util.HashSet<>(
            Arrays.asList("red-rover-block-placement", "red-rover-block-edit"));

    private static BotSettings read(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return BotSettings.read(yaml.getConfigurationSection("ragobot"), modes(), IN_TURNS);
    }

    private static void assertInvalid(String text, String expectedStart) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> read(text));
        assertTrue(error.getMessage().startsWith(expectedStart), error.getMessage());
    }

    @Test
    void withoutItsSectionItPlaysTheModesTheOwnerAskedFor() {
        BotSettings bots = BotSettings.read(null, modes(), IN_TURNS);
        assertTrue(bots.enabled);
        assertEquals("qRago", bots.skin);
        assertEquals(10, bots.max);
        assertEquals(BotSettings.DEFAULT_MODES, bots.modes);
        assertEquals("Top Bridge 1v1, Top Bridge 2v2, Sumo duel, Boxing, Lobby Fight 1v1, Block placement 1v1,"
                + " Block placement 2v2, Red Rover Block Placement, Block edit 1v1 and Red Rover Block Edit", bots.modeNames,
                "Boxing and Lobby Fight 1v1: step 1 of the owner's order of 22/11 (17h54); the Block Placement: step 2;"
                + " the Block edit: step 3");
        assertSame(BotBrain.Skill.BUNDLED, bots.skill);
        assertTrue(bots.plays("sumo"));
        assertTrue(bots.plays("boxing"));
        assertTrue(bots.plays("lobby-fight-1v1"));
        assertTrue(bots.plays("block-fight-1v1"));
        assertTrue(bots.plays("block-fight-2v2"));
        assertTrue(bots.plays("red-rover-block-placement"));
        assertTrue(bots.plays("block-edit-duel"));
        assertTrue(bots.plays("red-rover-block-edit"));
        assertFalse(bots.plays("ranked-sumo"));
    }

    @Test
    void itUsesTheBlocksInTheBlockPlacementOnly() {
        assertEquals(new java.util.HashSet<>(Arrays.asList("block-fight-1v1", "block-fight-2v2", "red-rover-block-placement")),
                BotSettings.BLOCK_MODES, "step 2 of the owner's order of 22/11 (17h54)");
        assertTrue(BotSettings.DEFAULT_MODES.containsAll(BotSettings.BLOCK_MODES));
    }

    @Test
    void itEditsTheWallInTheBlockEditOnly() {
        assertEquals(new java.util.HashSet<>(Arrays.asList("block-edit-duel", "red-rover-block-edit")),
                BotSettings.EDIT_MODES, "step 3 of the owner's order of 22/11 (17h54)");
        assertTrue(BotSettings.DEFAULT_MODES.containsAll(BotSettings.EDIT_MODES));
        for (String mode : BotSettings.EDIT_MODES) {
            assertFalse(BotSettings.BLOCK_MODES.contains(mode), mode + ": the wall, not the clutch nor the block under the other");
        }
    }

    @Test
    void aRedRoverIsAGameOfOneTeamItPlays() throws Exception {
        assertEquals(Collections.singletonList("red-rover-block-placement"),
                read("ragobot:\n  modes: [red-rover-block-placement]\n").modes, "each player against the others in turn");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString("ragobot:\n  modes: [red-rover-block-placement]\n");
            BotSettings.read(yaml.getConfigurationSection("ragobot"), modes());
        });
        assertTrue(error.getMessage().startsWith("ragobot.modes: red-rover-block-placement has one team"), error.getMessage());
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
        assertEquals(BotBrain.Skill.BUNDLED.blocksPerSecond, bots.skill.blocksPerSecond, 0);
    }

    @Test
    void aPlayerAlonePlaysBlockMethodAgainstAWeakerRagoBot() throws Exception {
        BotSettings bots = BotSettings.read(null, modes(), IN_TURNS);
        assertEquals(Collections.singletonList("block-method-1v1"), bots.soloModes);
        assertSame(BotBrain.Skill.SOLO, bots.soloSkill);
        assertTrue(bots.solo("block-method-1v1"));
        assertFalse(bots.solo("sumo"), "a party's RagoBot there");
        assertTrue(BotBrain.Skill.SOLO.cps < BotBrain.Skill.BUNDLED.cps, "much weaker (the owner, 28/11)");
        assertTrue(BotBrain.Skill.SOLO.accuracy < BotBrain.Skill.BUNDLED.accuracy);
        assertTrue(BotBrain.Skill.SOLO.reach < BotBrain.Skill.BUNDLED.reach);
        assertFalse(read("ragobot:\n  actif: false\n").solo("block-method-1v1"), "no RagoBot at all");
        BotSettings own = read("ragobot:\n  seul:\n    modes: [sumo, sumo]\n    skill:\n      cps: 5\n");
        assertEquals(Collections.singletonList("sumo"), own.soloModes, "once");
        assertEquals(5, own.soloSkill.cps, 0);
        assertEquals(BotBrain.Skill.SOLO.accuracy, own.soloSkill.accuracy, 0, "the rest as the bundled weaker one");
        assertEquals(BotBrain.Skill.BUNDLED.cps, own.skill.cps, 0, "the party's RagoBot as it was");
    }

    @Test
    void refusesWhatItCannotPlayAlone() {
        assertInvalid("ragobot:\n  seul:\n    modes: [top-bridge-2v2]\n", "ragobot.seul.modes: top-bridge-2v2 is not a 1v1");
        assertInvalid("ragobot:\n  seul:\n    modes: [ranked-sumo]\n", "ragobot.seul.modes: ranked-sumo is not a 1v1");
        assertInvalid("ragobot:\n  seul:\n    modes: [nope]\n", "ragobot.seul.modes: no mode nope");
        assertInvalid("ragobot:\n  seul: oui\n", "ragobot.seul:");
        assertInvalid("ragobot:\n  seul:\n    skill:\n      cps: 30\n", "ragobot.seul.skill.cps: from 1 to 20");
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
        assertInvalid("ragobot:\n  skill:\n    blocs-par-seconde: 0\n", "ragobot.skill.blocs-par-seconde: from 1 to 20");
    }

    @Test
    void theBundledConfigHoldsTheBundledSkill() throws Exception {
        BotSettings bots = LobbySettings.load(LobbySettingsTest.bundledConfig()).bots();
        assertTrue(bots.enabled);
        assertEquals("qRago", bots.skin, "the owner's skin (22/11)");
        assertEquals(BotSettings.DEFAULT_SOLO_MODES, bots.soloModes);
        BotBrain.Skill solo = BotBrain.Skill.SOLO;
        assertEquals(solo.cps, bots.soloSkill.cps, 0);
        assertEquals(solo.accuracy, bots.soloSkill.accuracy, 0);
        assertEquals(solo.reach, bots.soloSkill.reach, 0);
        assertEquals(solo.strafe, bots.soloSkill.strafe, 0);
        assertEquals(solo.wTap, bots.soloSkill.wTap, 0);
        assertEquals(solo.jumpReset, bots.soloSkill.jumpReset, 0);
        assertEquals(solo.reaction, bots.soloSkill.reaction);
        assertEquals(solo.turn, bots.soloSkill.turn, 0);
        assertEquals(solo.healAt, bots.soloSkill.healAt, 0);
        assertEquals(solo.blocksPerSecond, bots.soloSkill.blocksPerSecond, 0);
        assertEquals(solo.ping, bots.soloSkill.ping);
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
        assertEquals(bundled.blocksPerSecond, bots.skill.blocksPerSecond, 0);
        assertEquals(6, bundled.blocksPerSecond, 0, "6 blocks a second, to confirm in game");
        assertEquals(bundled.ping, bots.skill.ping);
        assertEquals(40, bundled.ping, "40 ms, the owner's ask of 23/11");
    }
}
