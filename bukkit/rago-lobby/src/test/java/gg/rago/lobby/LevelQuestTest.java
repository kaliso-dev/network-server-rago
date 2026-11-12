package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The rewards of the Network levels and the quests. */
class LevelQuestTest {

    private static final UUID ALICE = new UUID(0, 1);

    private static YamlConfiguration bundled() throws Exception {
        try (Reader reader = new InputStreamReader(LevelQuestTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    @Test
    void aRewardEveryFiveLevelsUpToAHundredThenAtEachPrestige() throws Exception {
        LevelRewards rewards = LevelRewards.defaults();
        assertEquals(29, rewards.all().size());
        for (LevelRewards.Reward reward : rewards.all()) {
            assertEquals(0, reward.level() % (reward.level() > NetworkLevel.PRESTIGE ? NetworkLevel.PRESTIGE : 5), "level " + reward.level());
            assertTrue(reward.points() > 0 || !reward.items().isEmpty(), "level " + reward.level());
        }
        assertEquals(100, rewards.all().get(19).level());
        assertEquals(100, NetworkLevel.PRESTIGE);
        assertEquals(1000, NetworkLevel.MAX_LEVEL);
        assertEquals(20_000, rewards.at(200).points());
        assertEquals(25_000, rewards.at(300).points());
        assertEquals(55_000, rewards.at(900).points());
        assertEquals(100_000, rewards.at(1000).points());
        assertEquals(200, rewards.next(100).level());
        assertEquals(25, rewards.levelOf("couleur-aqua"));
        assertEquals(100, rewards.levelOf("badge-centurion"));
        assertEquals(0, rewards.levelOf("badge-rago"), "a badge of the store");
        assertEquals(5, rewards.next(1).level());
        assertEquals(10, rewards.next(5).level());
        assertNull(rewards.next(NetworkLevel.MAX_LEVEL));
        assertEquals(2, rewards.between(4, 12).size(), "levels 5 and 10");
        assertTrue(rewards.between(12, 12).isEmpty());

        ServerSettings server = ServerSettings.load(bundled());
        assertEquals(29, server.levelRewards().all().size(), "config.yml has the same rewards");
        for (LevelRewards.Reward reward : rewards.all()) {
            assertEquals(reward.items(), server.levelRewards().at(reward.level()).items());
            assertEquals(reward.points(), server.levelRewards().at(reward.level()).points());
        }
        for (NameStyle style : server.styles().values()) {
            if (style.obtain() == NameStyle.Obtain.LEVEL) {
                assertTrue(rewards.levelOf(style.key()) > 0, style.key() + " is the reward of a level");
            }
        }
    }

    @Test
    void wrongRewardsAndQuestsAreRefused() {
        assertInvalid("niveau:\n  recompenses:\n    10: {objets: [nothing]}\n", "niveau.recompenses.10.objets");
        assertInvalid("niveau:\n  recompenses:\n    101: {points: 5}\n", "niveau.recompenses.101");
        assertInvalid("niveau:\n  prestiges:\n    150: {points: 5}\n", "niveau.prestiges.150");
        assertInvalid("niveau:\n  prestiges:\n    1100: {points: 5}\n", "niveau.prestiges.1100");
        assertInvalid("niveau:\n  prestiges:\n    200: {objets: [nothing]}\n", "niveau.prestiges.200.objets");
        assertInvalid("niveau:\n  prestiges:\n    200: 5\n", "niveau.prestiges.200");
        assertInvalid("quetes:\n  quotidiennes:\n    a: {nom: 'A', type: dance, objectif: 1, xp: 5}\n", "quetes.quotidiennes.a.type");
        assertInvalid("quetes:\n  quotidiennes:\n    a: {nom: 'A', type: kills, objectif: 0, xp: 5}\n", "quetes.quotidiennes.a.objectif");
    }

    private static void assertInvalid(String text, String where) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(yaml));
        assertTrue(error.getMessage().startsWith(where), error.getMessage());
    }

    @Test
    void questsCountOnceAndStartAgainEachDayOrWeek() throws Exception {
        Quests bundled = ServerSettings.load(bundled()).quests();
        assertEquals(8, bundled.of(Quests.Period.DAILY).size(), "the list the daily quests are drawn from");
        assertEquals(5, bundled.of(Quests.Period.WEEKLY).size());
        assertEquals(3, bundled.perDay());
        for (int i = 0; i < 4; i++) {
            assertTrue(bundled.of(Quests.Period.WEEKLY).get(i).xp() > bundled.of(Quests.Period.DAILY).get(i).xp(),
                    "a week is worth more than a day");
        }
        // every daily quest at once: how a quest counts, whatever the draw
        Quests quests = new Quests(bundled.all(), 0);
        PlayerStats stats = new PlayerStats(ALICE, "Alice");
        String monday = "2026-11-09";
        String week = "2026-W46";
        List<Quests.Quest> done = quests.advance(stats, Quests.Goal.WINS, "sumo", 1, monday, week);
        assertTrue(done.isEmpty());
        quests.advance(stats, Quests.Goal.WINS, "sumo", 1, monday, week);
        done = quests.advance(stats, Quests.Goal.WINS, "sumo", 1, monday, week);
        assertEquals(1, done.size(), "the third win of the day");
        assertEquals("gagner", done.get(0).id());
        assertEquals(Quests.Period.DAILY, done.get(0).period());
        assertTrue(quests.advance(stats, Quests.Goal.WINS, "sumo", 1, monday, week).isEmpty(), "done once a day");
        Quests.Quest dailyWins = quests.of(Quests.Period.DAILY).get(1);
        Quests.Quest weeklyWins = quests.of(Quests.Period.WEEKLY).get(1);
        assertEquals(3, Quests.progress(stats, dailyWins, monday, week));
        assertEquals(4, Quests.progress(stats, weeklyWins, monday, week));
        assertEquals(0, Quests.progress(stats, dailyWins, "2026-11-10", week), "a new day");
        assertEquals(4, Quests.progress(stats, weeklyWins, "2026-11-10", week), "the same week");
        assertEquals(0, Quests.progress(stats, weeklyWins, "2026-11-16", "2026-W47"), "a new week");

        quests.advance(stats, Quests.Goal.KILLS, "sumo", 3, "2026-11-16", "2026-W47");
        PlayerStats read = StatsFiles.fromYaml(ALICE, StatsFiles.toYaml(stats));
        assertEquals(3, read.questProgress("d:kills"));
        assertEquals(3, read.questProgress("w:kills"));
        assertEquals("2026-11-16", read.questDay());
        assertEquals("2026-W47", read.questWeek());

        Quests one = new Quests(java.util.Collections.singletonList(new Quests.Quest(Quests.Period.DAILY, "sumo", "Win 2 sumos",
                Quests.Goal.WINS, 2, 100, java.util.Collections.singletonList("sumo"))));
        one.advance(stats, Quests.Goal.WINS, "bridge", 1, monday, week);
        assertEquals(0, stats.questProgress("d:sumo"), "another mode does not count");
        assertEquals(1, one.advance(stats, Quests.Goal.WINS, "sumo", 5, monday, week).size());
        assertEquals(2, stats.questProgress("d:sumo"), "never above the target");
    }

    @Test
    void theRewardsLevelIsKept() throws Exception {
        PlayerStats stats = new PlayerStats(ALICE, "Alice");
        stats.setRewardsLevel(35);
        assertEquals(35, StatsFiles.fromYaml(ALICE, StatsFiles.toYaml(stats)).rewardsLevel());
        NetworkLevel levels = NetworkLevel.DEFAULT;
        assertEquals(20, LevelMenu.tierStart(levels, 27));
        assertEquals(1, LevelMenu.tierStart(levels, 3));
        assertEquals(100, LevelMenu.tierStart(levels, 100));
    }
}
