package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.lobby.GettingStarted.Step;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Getting started, the quests of a new player, and the menu of the first arrival. */
class GettingStartedTest {

    private static final UUID ALICE = new UUID(0, 1);

    private static YamlConfiguration bundled() throws Exception {
        try (Reader reader = new InputStreamReader(GettingStartedTest.class.getResourceAsStream("/config.yml"),
                StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    @Test
    void fiveStepsOnceEachThenTheReward() {
        GettingStarted quests = GettingStarted.defaults();
        List<Step> all = quests.steps(true, true);
        assertEquals(Arrays.asList(Step.TRAINING, Step.RECORD, Step.DUEL, Step.QUICK_BUY, Step.DISCORD), all);
        assertEquals(Arrays.asList(Step.TRAINING, Step.RECORD, Step.DUEL), quests.steps(false, false),
                "no Hypixel key, no Discord link: those steps are left out");
        assertTrue(GettingStarted.OFF.steps(true, true).isEmpty());
        assertEquals(600, quests.totalPoints(all));
        assertEquals("trail-emeralds", quests.reward());

        PlayerStats alice = new PlayerStats(ALICE, "Alice");
        assertEquals(Step.TRAINING, GettingStarted.next(null, all), "a player without stats yet: the first step");
        assertEquals(0, GettingStarted.done(null, all));
        assertTrue(GettingStarted.complete(alice, Step.DUEL), "in any order");
        assertFalse(GettingStarted.complete(alice, Step.DUEL), "once");
        assertEquals(Step.TRAINING, GettingStarted.next(alice, all), "the next: the first not done");
        assertTrue(GettingStarted.complete(alice, Step.TRAINING));
        assertEquals(Step.RECORD, GettingStarted.next(alice, all));
        assertEquals(2, GettingStarted.done(alice, all));
        assertFalse(GettingStarted.finish(alice, all), "not all done");
        GettingStarted.complete(alice, Step.RECORD);
        assertTrue(GettingStarted.finish(alice, quests.steps(false, false)), "all the steps of this server: the reward");
        assertFalse(GettingStarted.finish(alice, quests.steps(false, false)), "once");
        GettingStarted.complete(alice, Step.QUICK_BUY);
        GettingStarted.complete(alice, Step.DISCORD);
        assertNull(GettingStarted.next(alice, all));
        assertFalse(GettingStarted.finish(alice, all), "a step that came later gives no second reward");
        assertFalse(GettingStarted.finish(new PlayerStats(ALICE, "Alice"), Collections.<Step>emptyList()));
    }

    @Test
    void theProgressStaysInTheFileAndNeverResets() throws Exception {
        PlayerStats alice = new PlayerStats(ALICE, "Alice");
        GettingStarted.complete(alice, Step.TRAINING);
        GettingStarted.finish(alice, Collections.singletonList(Step.TRAINING));
        long now = ZonedDateTime.parse("2026-11-11T12:00:00Z").toInstant().toEpochMilli();
        alice.newQuestPeriod(StatsPeriod.dayKey(now), StatsPeriod.weekKey(now));
        PlayerStats read = StatsFiles.fromYaml(ALICE, StatsFiles.toYaml(alice));
        assertTrue(GettingStarted.done(read, Step.TRAINING));
        assertEquals(1, read.questProgress(GettingStarted.REWARDED));
        long nextWeek = now + 8 * 24 * 3600_000L;
        read.newQuestPeriod(StatsPeriod.dayKey(nextWeek), StatsPeriod.weekKey(nextWeek));
        assertTrue(GettingStarted.done(read, Step.TRAINING), "a new day and a new week keep it");
    }

    @Test
    void theBundledConfigGivesSixHundredPointsAndATrail() throws Exception {
        ServerSettings server = ServerSettings.load(bundled());
        GettingStarted quests = server.gettingStarted();
        assertTrue(quests.on());
        for (Step step : Step.values()) {
            assertEquals(GettingStarted.defaults().points(step), quests.points(step), step.id());
        }
        assertEquals("trail-emeralds", quests.reward());
        assertTrue(server.cosmetics().containsKey(quests.reward()), "a cosmetic of the shop");
        assertEquals("trail-emeralds", ServerSettings.load(new YamlConfiguration()).gettingStarted().reward(),
                "without the section: the plugin's own");
    }

    @Test
    void itsSettingsAreChecked() throws Exception {
        ServerSettings server = ServerSettings.load(bundled());
        GettingStarted some = ServerSettings.readGettingStarted(yaml("points: {duel: 300}\nrecompense: ''\n"), server.cosmetics(),
                server.styles());
        assertEquals(300, some.points(Step.DUEL));
        assertEquals(100, some.points(Step.TRAINING), "a step left out: its points by default");
        assertNull(some.reward(), "nothing at the end");
        assertEquals("couleur-or", ServerSettings.readGettingStarted(yaml("recompense: couleur-or\n"), server.cosmetics(),
                server.styles()).reward(), "a style");
        assertFalse(ServerSettings.readGettingStarted(yaml("actif: false\n"), server.cosmetics(), server.styles()).on());
        IllegalArgumentException wrongStep = assertThrows(IllegalArgumentException.class, () -> ServerSettings.readGettingStarted(
                yaml("points: {parkour: 10}\n"), server.cosmetics(), server.styles()));
        assertTrue(wrongStep.getMessage().startsWith("quetes.debut.points.parkour"), wrongStep.getMessage());
        IllegalArgumentException wrongReward = assertThrows(IllegalArgumentException.class, () -> ServerSettings.readGettingStarted(
                yaml("recompense: trail-rainbow\n"), server.cosmetics(), server.styles()));
        assertTrue(wrongReward.getMessage().startsWith("quetes.debut.recompense"), wrongReward.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ServerSettings.readGettingStarted(yaml("points: {duel: -1}\n"),
                server.cosmetics(), server.styles()));
    }

    @Test
    void theQuestsNpcCountsTheStepsFirst() {
        Quests quests = Quests.defaults();
        long now = ZonedDateTime.parse("2026-11-11T12:00:00Z").toInstant().toEpochMilli();
        List<Step> steps = GettingStarted.defaults().steps(true, true);
        assertEquals("Start 0/5 · Daily 0/4 · Weekly 0/4", ChatColor.stripColor(QuestsNpc.lines(quests, null, now, steps).get(1)));
        PlayerStats alice = new PlayerStats(ALICE, "Alice");
        for (Step step : steps) {
            GettingStarted.complete(alice, step);
        }
        assertEquals("Daily 0/4 · Weekly 0/4", ChatColor.stripColor(QuestsNpc.lines(quests, alice, now, steps).get(1)),
                "all done: no more");
    }

    @Test
    void theFirstArrivalOffersFourWaysToPlay() throws Exception {
        LobbySettings settings = LobbySettings.load(LobbySettingsTest.bundledConfig());
        List<FirstChoice> choices = settings.firstChoices();
        assertEquals(4, choices.size());
        assertEquals("clutch", choices.get(0).modeId(), "train a skill: the block clutch, where a new player starts");
        assertTrue(settings.display(choices.get(0).modeId()).startHere());
        assertEquals("ranked-1v1", choices.get(1).modeId());
        assertEquals("duels", choices.get(2).categoryId(), "duel a friend: the Duels");
        assertNull(choices.get(2).modeId());
        assertEquals("ffa", choices.get(3).modeId(), "fight now: the FFA");
        assertEquals("§bTrain a skill", choices.get(0).name());
        assertEquals(Material.BED, choices.get(1).icon());
        assertEquals("§7Start with the block clutch:", choices.get(0).description().get(0));
        for (int i = 0; i < choices.size(); i++) {
            FirstChoice mine = FirstChoice.defaults().get(i);
            assertEquals(mine.id(), choices.get(i).id(), "the plugin's own are the same");
            assertEquals(mine.name(), choices.get(i).name());
            assertEquals(mine.description(), choices.get(i).description());
            assertEquals(mine.modeId(), choices.get(i).modeId());
            assertEquals(mine.categoryId(), choices.get(i).categoryId());
        }
    }

    @Test
    void theChoicesOfTheFirstArrivalAreChecked() throws Exception {
        YamlConfiguration config = LobbySettingsTest.bundledConfig();
        config.set("premiere-venue", null);
        assertEquals(4, LobbySettings.load(config).firstChoices().size(), "left out: the plugin's own");
        config.createSection("premiere-venue");
        assertTrue(LobbySettings.load(config).firstChoices().isEmpty(), "{}: no menu");
        assertTrue(LobbySettings.load(yaml("modes:\n  sumo: {}\n")).firstChoices().isEmpty(),
                "the plugin's own, without the modes and categories this server lacks");
        assertEquals("sumo", LobbySettings.load(yaml("modes:\n  sumo: {}\npremiere-venue:\n  one: {nom: Sumo, mode: sumo}\n"))
                .firstChoices().get(0).modeId());
        for (String wrong : Arrays.asList("{nom: Both, mode: sumo, categorie: duels}", "{nom: None}", "{nom: X, mode: boxing}",
                "{nom: X, categorie: duels}", "{nom: '', mode: sumo}", "{nom: X, mode: sumo, icone: NOTHING}")) {
            assertThrows(IllegalArgumentException.class, () -> LobbySettings.load(yaml("modes:\n  sumo: {}\npremiere-venue:\n"
                    + "  one: " + wrong + "\n")), wrong);
        }
        IllegalArgumentException hidden = assertThrows(IllegalArgumentException.class, () -> LobbySettings.load(yaml(
                "modes:\n  sumo: {}\n  event: {cache: true}\npremiere-venue:\n  one: {nom: X, mode: event}\n")));
        assertTrue(hidden.getMessage().startsWith("premiere-venue.one.mode"), hidden.getMessage());
        StringBuilder many = new StringBuilder("modes:\n  sumo: {}\npremiere-venue:\n");
        for (int i = 0; i <= FirstChoice.MAX; i++) {
            many.append("  c").append(i).append(": {nom: X, mode: sumo}\n");
        }
        assertThrows(IllegalArgumentException.class, () -> LobbySettings.load(yaml(many.toString())));
    }
}
