package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.lobby.Quests.Goal;
import gg.rago.lobby.Quests.Period;
import gg.rago.lobby.Quests.Quest;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The quests of the trainings, and the daily quests drawn each day from a longer list. */
class QuestDrawTest {

    private static final UUID ALICE = new UUID(0, 1);
    private static final String WEEK = "2026-W46";

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    private static YamlConfiguration bundled() throws Exception {
        try (Reader reader = new InputStreamReader(QuestDrawTest.class.getResourceAsStream("/config.yml"),
                StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    @Test
    void threeDailyQuestsADayOfTheGamesAndOfTheTrainings() {
        Quests quests = Quests.defaults();
        List<Quest> pool = quests.of(Period.DAILY);
        assertEquals(8, pool.size());
        Set<String> seen = new HashSet<>();
        int changed = 0;
        List<Quest> before = null;
        LocalDate day = LocalDate.parse("2026-11-09");
        for (int i = 0; i < 90; i++, day = day.plusDays(1)) {
            List<Quest> today = quests.active(Period.DAILY, day.toString(), WEEK);
            assertEquals(3, today.size(), day.toString());
            assertEquals(3, new HashSet<>(today).size(), "three different quests");
            assertEquals(today, quests.active(Period.DAILY, day.toString(), WEEK), "the same all day, for everyone");
            assertTrue(today.stream().anyMatch(q -> q.goal().training()), day + ": one of the trainings at least");
            assertTrue(today.stream().anyMatch(q -> !q.goal().training()), day + ": one of the games at least");
            for (int j = 1; j < today.size(); j++) {
                assertTrue(pool.indexOf(today.get(j - 1)) < pool.indexOf(today.get(j)), "in the order of the list");
            }
            today.forEach(q -> seen.add(q.id()));
            if (before != null && !before.equals(today)) {
                changed++;
            }
            before = today;
        }
        assertEquals(8, seen.size(), "every quest of the list comes up");
        assertTrue(changed >= 60, "other quests from one day to the next, most days: " + changed);
        assertEquals(quests.of(Period.WEEKLY), quests.active(Period.WEEKLY, "2026-11-12", WEEK), "every weekly quest");
        assertTrue(quests.active(Period.WEEKLY, "2026-11-12", WEEK).stream().anyMatch(q -> q.goal().training()),
                "one weekly quest of the trainings");
    }

    @Test
    void aDrawThatTakesThemAllOrAPoolOfOneKind() {
        List<Quest> pool = Quests.defaults().of(Period.DAILY);
        assertEquals(pool, new Quests(pool, 0).active(Period.DAILY, "2026-11-12", WEEK), "0: all of them");
        assertEquals(pool, new Quests(pool, 8).active(Period.DAILY, "2026-11-12", WEEK));
        assertEquals(pool, new Quests(pool, 12).active(Period.DAILY, "2026-11-12", WEEK));
        List<Quest> games = pool.subList(0, 4);
        List<Quest> drawn = Quests.draw(games, 2, "2026-11-12");
        assertEquals(2, drawn.size(), "only quests of the games: two of them all the same");
        assertEquals(1, Quests.draw(pool, 1, "2026-11-12").size(), "one a day: of any kind");
        assertThrows(IllegalArgumentException.class, () -> new Quests(pool, -1));
    }

    @Test
    void onlyTheQuestsOfTheDayMoveOn() {
        Quests quests = Quests.defaults();
        String day = "2026-11-09";
        while (quests.active(Period.DAILY, day, WEEK).stream().anyMatch(q -> q.id().equals("classe"))) {
            day = LocalDate.parse(day).plusDays(1).toString();
        }
        PlayerStats alice = new PlayerStats(ALICE, "Alice");
        assertTrue(quests.advance(alice, Goal.RANKED_WINS, "ranked-1v1", 1, day, WEEK).isEmpty());
        assertEquals(0, alice.questProgress("d:classe"), "not a quest of " + day);
        assertEquals(1, alice.questProgress("w:classe"), "the weekly one runs");
    }

    @Test
    void theQuestsOfTheTrainings() {
        Quests all = new Quests(Quests.defaults().all(), 0);
        String day = "2026-11-12";
        PlayerStats alice = new PlayerStats(ALICE, "Alice");
        assertTrue(all.wants(alice, Goal.SUCCESSES, "clutch", day, WEEK));
        assertFalse(all.wants(alice, Goal.SUCCESSES, "aim-trainer", day, WEEK), "25 clutches: the block clutches only");
        for (int i = 0; i < 12; i++) {
            assertTrue(all.advance(alice, Goal.SUCCESSES, "clutch", 1, day, WEEK).isEmpty());
        }
        List<Quest> done = null;
        for (int i = 0; i < 13; i++) {
            done = all.advance(alice, Goal.SUCCESSES, "clutch-top-fight", 1, day, WEEK);
        }
        assertEquals(Arrays.asList("clutchs"), ids(done), "the 25th, island and top fight together");
        assertFalse(all.wants(alice, Goal.SUCCESSES, "clutch", day, WEEK), "done: nothing more to count");

        assertEquals(Arrays.asList("record"), ids(all.advance(alice, Goal.RECORDS, "aim-trainer", 1, day, WEEK)));
        assertEquals(Arrays.asList("defi"), ids(all.advance(alice, Goal.CHALLENGE, "block-in", 1, day, WEEK)));
        for (int i = 0; i < 14; i++) {
            assertTrue(all.advance(alice, Goal.MINUTES, "ladder-practice", 1, day, WEEK).isEmpty());
        }
        assertEquals(Arrays.asList("entrainement"), ids(all.advance(alice, Goal.MINUTES, "clutch", 1, day, WEEK)),
                "15 minutes, any training");
        assertEquals(15, alice.questProgress("w:entrainement"), "and as many of the 2 hours of the week");
        assertTrue(all.wants(alice, Goal.MINUTES, "clutch", day, WEEK), "the weekly one goes on");
    }

    @Test
    void theBundledConfigIsThePluginsOwn() throws Exception {
        Quests read = ServerSettings.load(bundled()).quests();
        Quests mine = Quests.defaults();
        assertEquals(mine.perDay(), read.perDay());
        assertEquals(mine.all().size(), read.all().size());
        for (int i = 0; i < mine.all().size(); i++) {
            Quest a = mine.all().get(i);
            Quest b = read.all().get(i);
            assertEquals(a.key(), b.key());
            assertEquals(a.name(), b.name());
            assertEquals(a.goal(), b.goal());
            assertEquals(a.target(), b.target());
            assertEquals(a.xp(), b.xp());
            assertEquals(a.modes(), b.modes(), a.key());
        }
    }

    @Test
    void theSettingsOfTheQuestsAreChecked() throws Exception {
        Quests one = ServerSettings.load(yaml("quetes:\n  par-jour: 0\n  quotidiennes:\n"
                + "    c: {nom: Clutch, type: reussites, objectif: 5, xp: 10, mode: clutch}\n"
                + "    d: {nom: Duels, type: victoires, objectif: 2, xp: 10, mode: [sumo, boxing]}\n")).quests();
        assertEquals(Arrays.asList("clutch"), one.of(Period.DAILY).get(0).modes());
        assertEquals(Arrays.asList("sumo", "boxing"), one.of(Period.DAILY).get(1).modes());
        assertEquals(0, one.perDay());
        assertEquals(2, one.active(Period.DAILY, "2026-11-12", WEEK).size());
        for (String wrong : Arrays.asList("par-jour: -1", "par-jour: 6")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ServerSettings.load(yaml("quetes:\n  " + wrong + "\n")));
            assertTrue(e.getMessage().startsWith("quetes.par-jour"), e.getMessage());
        }
        StringBuilder six = new StringBuilder("quetes:\n  par-jour: 0\n  quotidiennes:\n");
        for (int i = 0; i < 6; i++) {
            six.append("    q").append(i).append(": {nom: Q, type: kills, objectif: 5, xp: 10}\n");
        }
        IllegalArgumentException many = assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(yaml(six.toString())));
        assertTrue(many.getMessage().startsWith("quetes.quotidiennes: 5 quests at most"), many.getMessage());
        assertEquals(5, ServerSettings.load(yaml(six.toString().replace("par-jour: 0", "par-jour: 5"))).quests()
                .active(Period.DAILY, "2026-11-12", WEEK).size(), "six, five of them drawn each day");
        IllegalArgumentException weekly = assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(yaml(
                six.toString().replace("par-jour: 0", "par-jour: 3").replace("quotidiennes", "hebdomadaires"))));
        assertTrue(weekly.getMessage().startsWith("quetes.hebdomadaires: 5 quests at most"), weekly.getMessage());
        IllegalArgumentException mode = assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(yaml(
                "quetes:\n  quotidiennes:\n    c: {nom: C, type: reussites, objectif: 5, xp: 10, mode: {a: 1}}\n")));
        assertTrue(mode.getMessage().startsWith("quetes.quotidiennes.c.mode"), mode.getMessage());
        IllegalArgumentException type = assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(yaml(
                "quetes:\n  quotidiennes:\n    c: {nom: C, type: dance, objectif: 5, xp: 10}\n")));
        assertTrue(type.getMessage().contains("reussites, records, defi or minutes"), type.getMessage());
    }

    private static List<String> ids(List<Quest> quests) {
        List<String> ids = new java.util.ArrayList<>();
        for (Quest quest : quests) {
            ids.add(quest.id());
        }
        return ids;
    }
}
