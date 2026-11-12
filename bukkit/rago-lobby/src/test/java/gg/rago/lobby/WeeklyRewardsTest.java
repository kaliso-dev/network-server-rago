package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.game.GameMode;
import gg.rago.lobby.WeeklyRewards.Place;
import gg.rago.lobby.WeeklyRewards.Podium;
import gg.rago.lobby.WeeklyRewards.Results;
import gg.rago.lobby.WeeklyRewards.Settings;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The leaderboards of the week: the top 3 of each wins points and the crown, on Monday. */
class WeeklyRewardsTest {

    private static final GameMode SUMO = new GameMode("sumo", "Sumo", 2, 1, false);
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID CARL = new UUID(0, 3);
    private static final UUID DAVE = new UUID(0, 4);
    private static final StatsBook.Names NAMES = id -> id.equals(ALICE) ? "Alice" : id.equals(BOB) ? "Bob"
            : id.equals(CARL) ? "Carl" : "Dave";
    /** Saturday 7 November 2026 (week 45), Sunday 8 at 23:59, Monday 9 (week 46) in Paris. */
    private static final long SATURDAY = at(2026, 11, 7, 22, 0);
    private static final long SUNDAY_NIGHT = at(2026, 11, 8, 23, 59);
    private static final long MONDAY = at(2026, 11, 9, 0, 0);
    private static final String WEEK45 = "2026-W45";
    private static final String WEEK46 = "2026-W46";
    private static final String CROWN = "badge-semaine";

    private static long at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, StatsPeriod.ZONE).toInstant().toEpochMilli();
    }

    private static void win(StatsBook book, UUID winner, UUID loser, long millis) {
        book.record(SUMO, Collections.singletonList(winner), Collections.singletonList(loser), NAMES, millis);
    }

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    private static YamlConfiguration bundled() throws Exception {
        try (Reader reader = new InputStreamReader(WeeklyRewardsTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private static List<String> plain(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.add(ChatColor.stripColor(line));
        }
        return out;
    }

    private static String podium(Podium podium) {
        StringBuilder text = new StringBuilder(podium.board + ":");
        for (Place place : podium.places) {
            text.append(" #").append(place.place).append(' ').append(place.name).append(' ').append(place.score);
        }
        return text.toString();
    }

    /** A week of sumo: Alice 3 wins, Bob 2, Carl 1; Dave plays and loses; Alice kills twice. */
    private static StatsBook week45() {
        StatsBook book = new StatsBook(0, NetworkLevel.DEFAULT);
        win(book, ALICE, DAVE, SATURDAY);
        win(book, ALICE, DAVE, SATURDAY);
        win(book, BOB, DAVE, SATURDAY);
        win(book, CARL, DAVE, SATURDAY);
        win(book, BOB, CARL, SUNDAY_NIGHT);
        win(book, ALICE, BOB, SUNDAY_NIGHT);
        book.kill("sumo", ALICE, DAVE, NAMES, SATURDAY);
        book.kill("sumo", ALICE, BOB, NAMES, SUNDAY_NIGHT);
        return book;
    }

    @Test
    void theWeekTurnsOnceAndTheFirstCheckOnlyNotesIt() {
        WeeklyRewards.State state = new WeeklyRewards.State();
        assertNull(WeeklyRewards.turn(state, WEEK45), "the first check: the week before may have started again, nothing given");
        assertEquals(WEEK45, state.counting);
        assertNull(WeeklyRewards.turn(state, WEEK45));
        assertEquals(WEEK45, WeeklyRewards.turn(state, WEEK46), "Monday: week 45 is over");
        assertEquals(WEEK46, state.counting);
        assertNull(WeeklyRewards.turn(state, WEEK46), "once");
        assertEquals(WEEK46, WeeklyRewards.turn(state, "2026-W48"), "a server off for a week: the week it counted");
        assertEquals("week 45", WeeklyRewards.weekName(WEEK45));
        assertEquals("week 5", WeeklyRewards.weekName("2027-W05"));
    }

    @Test
    void theTopOfEachLeaderboardOfTheWeek() {
        StatsBook book = week45();
        Results results = WeeklyRewards.results(WEEK45, MONDAY, ServerSettings.readWeekly(null, NameStyle.catalogue()), book.all(),
                Collections.<String, List<Map.Entry<UUID, String>>>emptyMap(), id -> book.get(id).name());
        List<String> podiums = new ArrayList<>();
        for (Podium podium : results.podiums) {
            podiums.add(podium(podium));
        }
        assertEquals(Arrays.asList(
                "Wins: #1 Alice 3 #2 Bob 2 #3 Carl 1",
                "Best win streak: #1 Alice 3 #2 Bob 2 #3 Carl 1",
                "Kills: #1 Alice 2",
                "Games played: #1 Dave 4 #2 Alice 3 #3 Bob 3"), podiums,
                "the first three of each, as the walls rank them: ties in the order of the names, nobody at 0");
        assertEquals(new java.util.LinkedHashSet<>(Arrays.asList(ALICE, BOB, CARL, DAVE)), results.winners());

        // a game of the new week starts the counters again: the week before is gone for that player
        win(book, ALICE, BOB, MONDAY);
        assertEquals(0, WeeklyRewards.value(book.get(ALICE), BoardStat.WINS, WEEK45),
                "so the week before is rewarded before a game of the new week counts");
        assertEquals(1, WeeklyRewards.value(book.get(ALICE), BoardStat.WINS, WEEK46));
        assertEquals(1, WeeklyRewards.value(book.get(CARL), BoardStat.WINS, WEEK45), "who has not played yet still has it");
    }

    @Test
    void theWinnersGetThePointsAndTheCrownUntilTheNextResults() {
        StatsBook book = week45();
        Settings settings = ServerSettings.readWeekly(null, NameStyle.catalogue());
        WeeklyRewards.State state = new WeeklyRewards.State();
        Results results = WeeklyRewards.results(WEEK45, MONDAY, settings, book.all(),
                Collections.<String, List<Map.Entry<UUID, String>>>emptyMap(), id -> book.get(id).name());
        int alice = book.get(ALICE).points();
        int carl = book.get(CARL).points();
        Set<PlayerStats> changed = WeeklyRewards.give(results, settings, book, state);
        assertEquals(alice + 2000 + 2000 + 2000 + 1000, book.get(ALICE).points(), "first in wins, streaks and kills, second in games");
        assertEquals(carl + 500 + 500, book.get(CARL).points(), "third twice");
        for (UUID winner : Arrays.asList(ALICE, BOB, CARL, DAVE)) {
            assertTrue(book.get(winner).cosmetics().contains(CROWN), "the crown of the week");
            assertTrue(changed.contains(book.get(winner)), "written");
        }
        assertEquals(CROWN, state.badgeGiven);

        // week 46: only Bob and Carl play
        win(book, BOB, CARL, MONDAY);
        Results next = WeeklyRewards.results(WEEK46, MONDAY + 7 * 24 * 3600_000L, settings, book.all(),
                Collections.<String, List<Map.Entry<UUID, String>>>emptyMap(), id -> book.get(id).name());
        changed = WeeklyRewards.give(next, settings, book, state);
        assertFalse(book.get(ALICE).cosmetics().contains(CROWN), "the crown goes back at the next results");
        assertFalse(book.get(DAVE).cosmetics().contains(CROWN));
        assertTrue(changed.contains(book.get(ALICE)), "her file is written without it");
        assertTrue(book.get(BOB).cosmetics().contains(CROWN), "kept by a winner again");
        assertTrue(book.get(CARL).cosmetics().contains(CROWN));
    }

    @Test
    void theCrownIsWornUnlessTheyChoseABadge() {
        ServerSettings server = ServerSettings.load(new YamlConfiguration());
        StatsBook book = new StatsBook(0, NetworkLevel.DEFAULT);
        NameLooks names = new NameLooks(book, server);
        PlayerStats alice = book.profile(ALICE, "Alice");
        alice.boughtRanks().add("premium");
        assertEquals(" §d§l✦", names.badgeAfter(ALICE), "the badge of PREMIUM");
        alice.cosmetics().add(CROWN);
        assertEquals(" §6§l♛", names.badgeAfter(ALICE), "the crown of the week shows first");
        alice.choose(NameStyle.Kind.BADGE, "premium");
        assertEquals(" §d§l✦", names.badgeAfter(ALICE), "unless she chose a badge");
        alice.choose(NameStyle.Kind.BADGE, "");
        assertEquals("", names.badgeAfter(ALICE), "or none");
        alice.choose(NameStyle.Kind.BADGE, null);
        alice.cosmetics().remove(CROWN);
        assertEquals(" §d§l✦", names.badgeAfter(ALICE), "the week over: PREMIUM's again");
        NameStyle crown = server.weeklyBadge();
        assertEquals(NameStyle.Obtain.WEEK, crown.obtain());
        assertEquals("§6§l♛", crown.text());
    }

    @Test
    void theLeaderboardsOfTheOtherPluginsComeWithThem() {
        StatsBook book = week45();
        Map<String, WeeklyRewards.Board> boards = new LinkedHashMap<>();
        boards.put("Biggest clutches", week -> WEEK45.equals(week) ? Arrays.<Map.Entry<UUID, String>>asList(
                new AbstractMap.SimpleEntry<>(DAVE, "6.1 blocks"), new AbstractMap.SimpleEntry<>(DAVE, "5.0 blocks"),
                new AbstractMap.SimpleEntry<>(new UUID(0, 9), "4.0 blocks")) : Collections.<Map.Entry<UUID, String>>emptyList());
        boards.put("Broken", week -> {
            throw new IllegalStateException("broken");
        });
        List<String> broken = new ArrayList<>();
        Map<String, List<Map.Entry<UUID, String>>> rankings = WeeklyRewards.rankings(boards, WEEK45, (name, e) -> broken.add(name));
        assertEquals(Collections.singletonList("Broken"), broken, "a broken one is left out");
        Settings settings = ServerSettings.readWeekly(null, NameStyle.catalogue());
        Results results = WeeklyRewards.results(WEEK45, MONDAY, settings, book.all(), rankings, id -> {
            PlayerStats stats = book.get(id);
            return stats == null ? null : stats.name();
        });
        assertEquals("Biggest clutches: #1 Dave 6.1 blocks #2 ? 4.0 blocks", podium(results.podiums.get(4)),
                "once each, the names of the stats");
        assertTrue(WeeklyRewards.results(WEEK45, MONDAY, Settings.OFF, book.all(), rankings, id -> "?").podiums.isEmpty(), "off");
    }

    @Test
    void whatThePlayersRead() {
        StatsBook book = week45();
        Settings settings = ServerSettings.readWeekly(null, NameStyle.catalogue());
        Results results = WeeklyRewards.results(WEEK45, MONDAY, settings, book.all(),
                Collections.<String, List<Map.Entry<UUID, String>>>emptyMap(), id -> book.get(id).name());
        List<String> lines = plain(WeeklyRewards.announcement(results, settings, "§6§l♛"));
        assertEquals("LEADERBOARDS OF THE WEEK - week 45", lines.get(1));
        assertEquals("Wins » #1 Alice (3) · #2 Bob (2) · #3 Carl (1)", lines.get(2));
        assertEquals("The top 3 of each win 2,000, 1,000 and 500 Rago Points, and the badge ♛ for a week.", lines.get(6));
        assertEquals(8, lines.size(), "between two rules");
        assertEquals(Arrays.asList("TOP OF THE WEEK! You are #3 in Wins (week 45): +500 Rago Points",
                "TOP OF THE WEEK! You are #3 in Best win streak (week 45): +500 Rago Points",
                "You wear the badge ♛ until the next results, on Monday."), plain(WeeklyRewards.won(results, settings, CARL, "§6§l♛")));
        assertTrue(WeeklyRewards.won(results, settings, new UUID(0, 9), "§6§l♛").isEmpty());
        assertEquals("Top 3 on Monday: 2,000 · 1,000 · 500 points + ♛", ChatColor.stripColor(WeeklyRewards.boardLine(settings,
                "§6§l♛")));
        assertTrue(WeeklyRewards.boardLine(settings, "§6§l♛").length() <= 64, "a floating line: 64 characters at most");
        assertEquals("Top 5 on Monday: up to 900 points", ChatColor.stripColor(WeeklyRewards.boardLine(new Settings(true,
                Settings.DEFAULT_BOARDS, Arrays.asList(900, 500, 300, 200, 100), null), null)));
        assertTrue(WeeklyRewards.logLine(results).startsWith("Leaderboards of the week 2026-W45: Wins: #1 Alice (3) #2 Bob (2)"
                + " #3 Carl (1); Best win streak:"), WeeklyRewards.logLine(results));
    }

    @Test
    void theWeekAndItsResultsStayInTheirFile() throws Exception {
        StatsBook book = week45();
        Settings settings = ServerSettings.readWeekly(null, NameStyle.catalogue());
        WeeklyRewards.State state = new WeeklyRewards.State();
        state.counting = WEEK46;
        state.last = WeeklyRewards.results(WEEK45, MONDAY, settings, book.all(),
                Collections.<String, List<Map.Entry<UUID, String>>>emptyMap(), id -> book.get(id).name());
        state.untold.add(CARL);
        state.badgeGiven = CROWN;
        WeeklyRewards.State read = WeeklyRewards.State.fromYaml(state.toYaml());
        assertEquals(WEEK46, read.counting);
        assertEquals(CROWN, read.badgeGiven);
        assertEquals(Collections.singleton(CARL), read.untold);
        assertEquals(WEEK45, read.last.week);
        assertEquals(MONDAY, read.last.at);
        assertEquals(state.last.podiums.size(), read.last.podiums.size());
        for (int i = 0; i < state.last.podiums.size(); i++) {
            assertEquals(podium(state.last.podiums.get(i)), podium(read.last.podiums.get(i)));
        }
        WeeklyRewards.State empty = WeeklyRewards.State.fromYaml("");
        assertNull(empty.counting);
        assertNull(empty.last);
    }

    @Test
    void theSettingsAreChecked() throws Exception {
        ServerSettings server = ServerSettings.load(bundled());
        Settings bundled = server.weekly();
        assertTrue(bundled.on);
        assertEquals(Settings.DEFAULT_BOARDS, bundled.boards);
        assertEquals(Settings.DEFAULT_POINTS, bundled.points);
        assertEquals("semaine", bundled.badge);
        assertEquals("§6§l♛", server.weeklyBadge().text(), "the crown is in the bundled styles");
        assertTrue(bundled.rewards(BoardStat.WINS));
        assertFalse(bundled.rewards(BoardStat.FKDR));
        Settings some = ServerSettings.readWeekly(yaml("classements: [kills]\npoints: [100]\nbadge: ''\n"), NameStyle.catalogue());
        assertEquals(Collections.singletonList(BoardStat.KILLS), some.boards);
        assertEquals(1, some.places());
        assertNull(some.badge, "no badge");
        assertFalse(ServerSettings.readWeekly(yaml("actif: false\n"), NameStyle.catalogue()).rewards(BoardStat.WINS));
        Map<String, NameStyle> noCrown = new LinkedHashMap<>(NameStyle.catalogue());
        noCrown.remove(CROWN);
        assertNull(ServerSettings.readWeekly(null, noCrown).badge, "styles without the crown: points only");
        for (String[] wrong : new String[][] {{"classements: [fkdr]\n", "semaine.classements"},
                {"classements: kills\n", "semaine.classements"}, {"points: [100, -5]\n", "semaine.points"},
                {"points: []\n", "semaine.points"}, {"points: [1,1,1,1,1,1,1,1,1,1,1]\n", "semaine.points"},
                {"badge: rainbow\n", "semaine.badge"}}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ServerSettings.readWeekly(yaml(wrong[0]), NameStyle.catalogue()), wrong[0]);
            assertTrue(e.getMessage().startsWith(wrong[1]), e.getMessage());
        }
    }
}
