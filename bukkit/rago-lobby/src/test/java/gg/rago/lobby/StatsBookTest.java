package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.game.GameMode;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatsBookTest {

    private static final GameMode SUMO = new GameMode("sumo", "Sumo", 2, 1, false);
    private static final GameMode RANKED = new GameMode("ranked-1v1", "Ranked 1v1", 2, 1, true);
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID CARL = new UUID(0, 3);
    private static final UUID DAVE = new UUID(0, 4);
    private static final GameMode DUO = new GameMode("block-fight-2v2", "Block fight 2v2", 2, 2, false);
    /** Saturday 7 November 2026, 22:00 in Paris. */
    private static final long SATURDAY = at(2026, 11, 7, 22, 0);
    private static final long SUNDAY = at(2026, 11, 8, 10, 0);
    private static final long MONDAY = at(2026, 11, 9, 10, 0);

    private static long at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, StatsPeriod.ZONE).toInstant().toEpochMilli();
    }

    private static final StatsBook.Names NAMES = id -> id.equals(ALICE) ? "Alice" : id.equals(BOB) ? "Bob"
            : id.equals(CARL) ? "Carl" : "Dave";

    private static void win(StatsBook book, GameMode mode, UUID winner, UUID loser, long millis) {
        book.record(mode, Collections.singletonList(winner), Collections.singletonList(loser), NAMES, millis);
    }

    @Test
    void countsWinsLossesAndStreaks() {
        StatsBook book = new StatsBook(1000, NetworkLevel.DEFAULT);
        win(book, SUMO, ALICE, BOB, SATURDAY);
        win(book, SUMO, ALICE, BOB, SATURDAY);
        win(book, SUMO, BOB, ALICE, SATURDAY);
        win(book, SUMO, ALICE, BOB, SATURDAY);
        ModeStats alice = book.get(ALICE).mode("sumo");
        assertEquals(3, alice.wins);
        assertEquals(1, alice.losses);
        assertEquals(1, alice.streak);
        assertEquals(2, alice.bestStreak);
        assertEquals("Bob", book.get(BOB).name());
        assertEquals(1, book.get(BOB).wins(StatsPeriod.ALL, null, "", ""));
        assertEquals(3, book.get(BOB).losses());
        assertNull(book.get(CARL));
    }

    @Test
    void dayAndWeekCountersStartAgain() {
        StatsBook book = new StatsBook(1000, NetworkLevel.DEFAULT);
        win(book, SUMO, ALICE, BOB, SATURDAY);
        win(book, SUMO, ALICE, BOB, SUNDAY);
        PlayerStats alice = book.get(ALICE);
        String sunday = StatsPeriod.dayKey(SUNDAY);
        String week45 = StatsPeriod.weekKey(SUNDAY);
        assertEquals(1, alice.wins(StatsPeriod.DAY, null, sunday, week45));
        assertEquals(2, alice.wins(StatsPeriod.WEEK, null, sunday, week45));
        // Monday is in a new week: nothing yet, until the next game.
        String monday = StatsPeriod.dayKey(MONDAY);
        String week46 = StatsPeriod.weekKey(MONDAY);
        assertEquals(0, alice.wins(StatsPeriod.DAY, null, monday, week46));
        assertEquals(0, alice.wins(StatsPeriod.WEEK, null, monday, week46));
        assertEquals(2, alice.wins(StatsPeriod.ALL, null, monday, week46));
        assertEquals(0, book.ranking(BoardStat.WINS, StatsPeriod.DAY, null, MONDAY).rows().size());
        win(book, SUMO, ALICE, BOB, MONDAY);
        assertEquals(1, alice.wins(StatsPeriod.WEEK, null, monday, week46));
        assertEquals(3, alice.mode("sumo").streak, "a streak goes on from one day to the next");
        assertEquals(3, alice.best(StatsPeriod.DAY, null, monday, week46));
    }

    @Test
    void ranksBestFirstAndLeavesZerosOut() {
        StatsBook book = new StatsBook(1000, NetworkLevel.DEFAULT);
        win(book, SUMO, BOB, CARL, SUNDAY);
        win(book, SUMO, ALICE, CARL, SUNDAY);
        win(book, SUMO, ALICE, CARL, SUNDAY);
        StatsBook.Ranking wins = book.ranking(BoardStat.WINS, StatsPeriod.DAY, null, SUNDAY);
        assertEquals(Arrays.asList("Alice", "Bob"), names(wins.rows()));
        assertEquals(2, wins.rows().get(0).value());
        assertEquals(1, wins.position(ALICE));
        assertEquals(0, wins.position(CARL), "no win, not on the board");
        StatsBook.Ranking games = book.ranking(BoardStat.GAMES, StatsPeriod.ALL, null, SUNDAY);
        assertEquals(Arrays.asList("Carl", "Alice", "Bob"), names(games.rows()));
        // A new game is counted at once.
        win(book, SUMO, BOB, ALICE, SUNDAY);
        win(book, SUMO, BOB, ALICE, SUNDAY);
        assertEquals(Arrays.asList("Bob", "Alice"), names(book.ranking(BoardStat.WINS, StatsPeriod.DAY, null, SUNDAY).rows()));
        assertEquals(Arrays.asList("Bob", "Alice"), names(book.ranking(BoardStat.STREAK, StatsPeriod.DAY, null, SUNDAY).rows()));
    }

    @Test
    void tiesKeepTheAlphabeticalOrder() {
        StatsBook book = new StatsBook(1000, NetworkLevel.DEFAULT);
        win(book, SUMO, CARL, BOB, SUNDAY);
        win(book, SUMO, ALICE, BOB, SUNDAY);
        assertEquals(Arrays.asList("Alice", "Carl"), names(book.ranking(BoardStat.WINS, StatsPeriod.ALL, null, SUNDAY).rows()));
    }

    @Test
    void rankedGamesMoveTheElo() {
        StatsBook book = new StatsBook(0, NetworkLevel.DEFAULT);
        assertEquals(0, book.elo(ALICE, "ranked-1v1"));
        win(book, RANKED, ALICE, BOB, SUNDAY);
        assertEquals(35, book.elo(ALICE, "ranked-1v1"), "a win in Coal");
        assertEquals(0, book.elo(BOB, "ranked-1v1"), "never below 0");
        win(book, RANKED, ALICE, BOB, SUNDAY);
        assertEquals(70, book.elo(ALICE, "ranked-1v1"));
        book.record(RANKED, Collections.singletonList(BOB), Collections.singletonList(ALICE), NAMES, SUNDAY,
                Collections.singletonList(ALICE));
        assertEquals(70, book.elo(ALICE, "ranked-1v1"), "a loss in Coal (-5) for the MVP (+5)");
        assertEquals(35, book.elo(BOB, "ranked-1v1"));
        StatsBook.Ranking elo = book.ranking(BoardStat.ELO, StatsPeriod.DAY, "ranked-1v1", SUNDAY);
        assertEquals(Arrays.asList("Alice", "Bob"), names(elo.rows()));
        assertTrue(new StatsBook(0, NetworkLevel.DEFAULT).ranking(BoardStat.ELO, StatsPeriod.DAY, "ranked-1v1", SUNDAY)
                .rows().isEmpty(), "nobody before the first game");
        // An unranked mode does not touch the ELO.
        win(book, SUMO, BOB, ALICE, SUNDAY);
        assertEquals(0, book.get(BOB).mode("sumo").elo);
    }

    @Test
    void aDuelCountsButMovesNeitherTheEloNorTheStreaks() {
        StatsBook book = new StatsBook(0, NetworkLevel.DEFAULT);
        win(book, RANKED, ALICE, BOB, SUNDAY);
        assertEquals(35, book.elo(ALICE, "ranked-1v1"));
        assertEquals(1, book.get(ALICE).mode("ranked-1v1").streak);
        // two friends farming a ranked 1v1 with /duel: the games count, nothing else moves
        for (int i = 0; i < 5; i++) {
            book.record(RANKED, Collections.singletonList(ALICE), Collections.singletonList(BOB), NAMES, SUNDAY,
                    Collections.<UUID>emptyList(), false);
        }
        ModeStats alice = book.get(ALICE).mode("ranked-1v1");
        assertEquals(35, book.elo(ALICE, "ranked-1v1"), "no ELO from a duel");
        assertEquals(0, book.elo(BOB, "ranked-1v1"));
        assertEquals(6, alice.wins, "the wins count");
        assertEquals(1, alice.streak, "the streak stays where the queue left it");
        assertEquals(1, alice.bestStreak);
        assertEquals(6, book.get(BOB).mode("ranked-1v1").losses, "one in the queue, five in duels");
        for (TeamStreak team : book.teams("ranked-1v1")) {
            assertTrue(team.streak <= 1, "no team streak either");
        }
        // a duel loss does not end a streak won in the queue
        book.record(RANKED, Collections.singletonList(BOB), Collections.singletonList(ALICE), NAMES, SUNDAY,
                Collections.<UUID>emptyList(), false);
        assertEquals(1, book.get(ALICE).mode("ranked-1v1").streak);
        assertTrue(book.get(ALICE).xp() > 0, "the XP of the games is still given");
    }

    @Test
    void gamesAndKillsGiveNetworkXp() {
        StatsBook book = new StatsBook(1000, NetworkLevel.DEFAULT);
        assertEquals(1, book.level(ALICE), "everyone starts at level 1");
        win(book, SUMO, ALICE, BOB, SUNDAY);
        assertEquals(50, book.get(ALICE).xp(), "a win");
        assertEquals(15, book.get(BOB).xp(), "a game played");
        assertEquals(2, book.kill("sumo", ALICE, BOB, NAMES, SUNDAY).size());
        assertEquals(55, book.get(ALICE).xp(), "a kill");
        assertEquals(15, book.get(BOB).xp(), "dying gives nothing");
        win(book, SUMO, ALICE, BOB, SUNDAY);
        assertEquals(2, book.level(ALICE), "105 XP: level 2 from 100");
        StatsBook.Ranking level = book.ranking(BoardStat.LEVEL, StatsPeriod.DAY, "sumo", SUNDAY);
        assertEquals(Arrays.asList("Alice", "Bob"), names(level.rows()));
        assertEquals(105, level.rows().get(0).value(), "ranked by XP");
        assertTrue(book.kill("sumo", ALICE, ALICE, NAMES, SUNDAY).isEmpty(), "no kill of oneself");
        assertEquals(1, book.kill("sumo", CARL, null, NAMES, SUNDAY).size(), "a kill without a known victim");
        assertEquals(5, book.get(CARL).xp());
    }

    @Test
    void aTrainingGivesTheXpItEarned() {
        StatsBook book = new StatsBook(1000, NetworkLevel.DEFAULT);
        assertEquals(37, book.giveXp(ALICE, 37, NAMES).xp());
        assertEquals("Alice", book.get(ALICE).name());
        assertNull(book.giveXp(ALICE, 0, NAMES), "nothing for nothing");
        assertNull(book.giveXp(BOB, -5, NAMES));
        assertNull(book.get(BOB));
        book.giveXp(ALICE, 63, NAMES);
        assertEquals(2, book.level(ALICE), "100 XP: level 2");
    }

    @Test
    void killsHaveTheirDayWeekAndLifetime() {
        StatsBook book = new StatsBook(1000, NetworkLevel.DEFAULT);
        book.kill("sumo", ALICE, BOB, NAMES, SATURDAY);
        book.kill("sumo", ALICE, BOB, NAMES, SUNDAY);
        book.kill("ranked-1v1", BOB, ALICE, NAMES, SUNDAY);
        book.kill("sumo", BOB, ALICE, NAMES, MONDAY);
        PlayerStats alice = book.get(ALICE);
        assertEquals(1, alice.kills(StatsPeriod.DAY, null, StatsPeriod.dayKey(SUNDAY), StatsPeriod.weekKey(SUNDAY)));
        assertEquals(2, alice.kills(StatsPeriod.WEEK, null, StatsPeriod.dayKey(SUNDAY), StatsPeriod.weekKey(SUNDAY)));
        assertEquals(0, alice.kills(StatsPeriod.WEEK, null, StatsPeriod.dayKey(MONDAY), StatsPeriod.weekKey(MONDAY)),
                "a new week");
        assertEquals(2, alice.kills(StatsPeriod.ALL, "sumo", "", ""));
        assertEquals(2, alice.deaths());
        assertEquals(1, alice.mode("ranked-1v1").deaths);
        assertEquals(Arrays.asList("Bob"), names(book.ranking(BoardStat.KILLS, StatsPeriod.DAY, null, MONDAY).rows()));
        assertEquals(Arrays.asList("Bob"), names(book.ranking(BoardStat.KILLS, StatsPeriod.WEEK, null, MONDAY).rows()));
        StatsBook.Ranking ever = book.ranking(BoardStat.KILLS, StatsPeriod.ALL, null, MONDAY);
        assertEquals(Arrays.asList("Alice", "Bob"), names(ever.rows()), "a tie: alphabetical");
        assertEquals(2, ever.rows().get(1).value());
        assertEquals(Arrays.asList("Alice", "Bob"),
                names(book.ranking(BoardStat.KILLS, StatsPeriod.ALL, "sumo", MONDAY).rows()), "one mode");
        assertEquals(1, book.ranking(BoardStat.KILLS, StatsPeriod.ALL, "sumo", MONDAY).rows().get(1).value());
    }

    @Test
    void filesKeepTheKillsAndTheXp() throws Exception {
        StatsBook book = new StatsBook(1000, NetworkLevel.DEFAULT);
        win(book, SUMO, ALICE, BOB, SUNDAY);
        book.kill("sumo", ALICE, BOB, NAMES, SUNDAY);
        book.kill("sumo", BOB, ALICE, NAMES, SUNDAY);
        PlayerStats read = StatsFiles.fromYaml(ALICE, StatsFiles.toYaml(book.get(ALICE)));
        assertEquals(55, read.xp());
        assertEquals(1, read.mode("sumo").kills);
        assertEquals(1, read.mode("sumo").deaths);
        assertEquals(1, read.kills(StatsPeriod.DAY, null, StatsPeriod.dayKey(SUNDAY), StatsPeriod.weekKey(SUNDAY)));
        assertEquals(1, read.kills(StatsPeriod.WEEK, null, StatsPeriod.dayKey(SUNDAY), StatsPeriod.weekKey(SUNDAY)));
    }

    private static void duo(StatsBook book, UUID winner1, UUID winner2, UUID loser1, UUID loser2, long millis) {
        book.record(DUO, Arrays.asList(winner1, winner2), Arrays.asList(loser1, loser2), NAMES, millis);
    }

    private static List<String> teamNames(List<StatsBook.TeamRow> rows) {
        List<String> names = new java.util.ArrayList<>();
        for (StatsBook.TeamRow row : rows) {
            names.add(row.names() + " - " + row.value());
        }
        return names;
    }

    @Test
    void teamsHaveTheirOwnWinStreaks() {
        StatsBook book = new StatsBook(1000, NetworkLevel.DEFAULT);
        duo(book, BOB, ALICE, CARL, DAVE, SUNDAY);
        duo(book, ALICE, BOB, CARL, DAVE, SUNDAY);
        duo(book, ALICE, CARL, BOB, DAVE, SUNDAY);
        assertEquals(Arrays.asList("Alice, Bob - 2", "Alice, Carl - 1"),
                teamNames(book.topStreaks("block-fight-2v2", StatsPeriod.DAY, SUNDAY)),
                "the names of the whole team; Alice and Bob keep their streak when they play apart");
        duo(book, CARL, DAVE, ALICE, BOB, SUNDAY);
        assertEquals(Arrays.asList("Alice, Bob - 2", "Alice, Carl - 1", "Carl, Dave - 1"),
                teamNames(book.topStreaks("block-fight-2v2", StatsPeriod.DAY, SUNDAY)), "a lost game ends the streak, not the best");
        duo(book, ALICE, BOB, CARL, DAVE, SUNDAY);
        assertEquals("Alice, Bob - 2", teamNames(book.topStreaks("block-fight-2v2", StatsPeriod.DAY, SUNDAY)).get(0));
        assertEquals(1, book.teams("block-fight-2v2").stream().filter(t -> t.players().contains(BOB)
                && t.players().contains(ALICE)).findFirst().get().streak, "starting again from one");
        assertTrue(book.topStreaks("block-fight-2v2", StatsPeriod.DAY, MONDAY).isEmpty(), "a new day");
        assertEquals("Alice, Bob - 2", teamNames(book.topStreaks("block-fight-2v2", StatsPeriod.ALL, MONDAY)).get(0));
        assertTrue(book.topStreaks("sumo", StatsPeriod.DAY, SUNDAY).isEmpty());
        win(book, SUMO, CARL, ALICE, SUNDAY);
        assertEquals(Arrays.asList("Carl - 1"), teamNames(book.topStreaks("sumo", StatsPeriod.DAY, SUNDAY)), "a 1v1: one name");
    }

    @Test
    void theLosersOfAGameOfSeveralTeamsAllLoseTheirStreak() {
        GameMode ffa = new GameMode("ffa", "FFA", 4, 1, false);
        StatsBook book = new StatsBook(1000, NetworkLevel.DEFAULT);
        book.record(ffa, Collections.singletonList(ALICE), Arrays.asList(BOB, CARL, DAVE), NAMES, SUNDAY);
        book.record(ffa, Collections.singletonList(BOB), Arrays.asList(ALICE, CARL, DAVE), NAMES, SUNDAY);
        book.record(ffa, Collections.singletonList(BOB), Arrays.asList(ALICE, CARL, DAVE), NAMES, SUNDAY);
        for (TeamStreak team : book.teams("ffa")) {
            assertEquals(team.players().contains(BOB) ? 2 : 0, team.streak);
        }
    }

    @Test
    void filesKeepTheTeamStreaks() throws Exception {
        StatsBook book = new StatsBook(1000, NetworkLevel.DEFAULT);
        duo(book, ALICE, BOB, CARL, DAVE, SATURDAY);
        duo(book, ALICE, BOB, CARL, DAVE, SUNDAY);
        String yaml = StatsFiles.teamsToYaml("block-fight-2v2", book.teams("block-fight-2v2"));
        List<TeamStreak> read = StatsFiles.teamsFromYaml(yaml);
        assertEquals(1, read.size());
        TeamStreak team = read.get(0);
        assertEquals(TeamStreak.key(Arrays.asList(BOB, ALICE)), team.key());
        assertEquals(2, team.streak);
        assertEquals(2, team.best);
        assertEquals(2, team.best(StatsPeriod.DAY, StatsPeriod.dayKey(SUNDAY), StatsPeriod.weekKey(SUNDAY)),
                "a streak that goes on past midnight counts whole on the new day");
        assertEquals(0, team.best(StatsPeriod.DAY, StatsPeriod.dayKey(MONDAY), StatsPeriod.weekKey(MONDAY)));
        assertEquals(2, team.best(StatsPeriod.WEEK, StatsPeriod.dayKey(SUNDAY), StatsPeriod.weekKey(SUNDAY)));
        assertEquals(yaml, StatsFiles.teamsToYaml("block-fight-2v2", read));
        StatsBook loaded = new StatsBook(1000, NetworkLevel.DEFAULT);
        assertTrue(loaded.loadTeam("block-fight-2v2", team));
        assertEquals(1, loaded.topStreaks("block-fight-2v2", StatsPeriod.ALL, SUNDAY).size());
        assertEquals("?, ?", loaded.topStreaks("block-fight-2v2", StatsPeriod.ALL, SUNDAY).get(0).names(),
                "no player stats loaded: no names");
    }

    @Test
    void keepsTheNameUpToDate() {
        StatsBook book = new StatsBook(1000, NetworkLevel.DEFAULT);
        win(book, SUMO, ALICE, BOB, SUNDAY);
        assertNull(book.seen(ALICE, "Alice"));
        assertEquals("Alicia", book.seen(ALICE, "Alicia").name());
        assertEquals("Alicia", book.ranking(BoardStat.WINS, StatsPeriod.ALL, null, SUNDAY).rows().get(0).name());
        assertNull(book.seen(CARL, "Carl"), "no stats, nothing to rename");
    }

    @Test
    void filesKeepEverything() throws Exception {
        StatsBook book = new StatsBook(0, NetworkLevel.DEFAULT);
        win(book, SUMO, ALICE, BOB, SATURDAY);
        win(book, RANKED, ALICE, BOB, SUNDAY);
        PlayerStats alice = book.get(ALICE);
        alice.addPoints(1234);
        alice.setRank("admin");
        alice.boughtRanks().add("premium");
        alice.cosmetics().add("trail-flames");
        alice.cosmetics().add("couleur-feu");
        alice.choose(NameStyle.Kind.COLOUR, "feu");
        alice.choose(NameStyle.Kind.BADGE, "");
        alice.choose(NameStyle.Kind.PLUS, "noir");
        alice.setBoosters(2);
        alice.setBoosterLeftMs(754_000L);
        alice.setBoosterDay("2026-11-10");
        alice.equip(Cosmetic.Type.TRAIL, "trail-flames");
        alice.friends().add(BOB);
        alice.setMessages(PlayerStats.MessageSetting.FRIENDS);
        alice.setPartyInvites(false);
        alice.setDaily("2026-11-08", 3);
        alice.setAutoRequeue(true);
        alice.setLobbyFly(true);
        alice.setTimeOfDay(PlayerStats.TimeOfDay.NIGHT);
        PlayerStats read = StatsFiles.fromYaml(ALICE, StatsFiles.toYaml(alice));
        assertEquals("Alice", read.name());
        assertEquals(StatsFiles.toYaml(alice), StatsFiles.toYaml(read));
        assertEquals(35, read.mode("ranked-1v1").elo);
        assertEquals(0, read.mode("sumo").elo);
        assertEquals(1234, read.points());
        assertEquals("admin", read.rank());
        assertTrue(read.boughtRanks().contains("premium"));
        assertEquals("feu", read.colourChoice());
        assertEquals("", read.badgeChoice(), "no badge, by choice");
        assertEquals("noir", read.plusChoice());
        assertEquals(2, read.boosters());
        assertEquals(754_000L, read.boosterLeftMs());
        assertEquals("2026-11-10", read.boosterDay());
        assertEquals("12:34", XpBoosters.clock(754_000L));
        assertEquals("30:00", XpBoosters.clock(XpBoosters.DURATION_MS));
        assertEquals("trail-flames", read.equipped(Cosmetic.Type.TRAIL));
        assertTrue(read.friends().contains(BOB));
        assertEquals(PlayerStats.MessageSetting.FRIENDS, read.messages());
        assertTrue(!read.partyInvites());
        assertEquals(3, read.dailyStreak());
        assertTrue(read.autoRequeue());
        assertTrue(read.lobbyFly(), "/fly is kept for the next time");
        assertEquals(PlayerStats.TimeOfDay.NIGHT, read.timeOfDay());
        assertEquals(PlayerStats.TimeOfDay.DAY, StatsFiles.fromYaml(BOB, StatsFiles.toYaml(book.get(BOB))).timeOfDay(),
                "the server's own sky by default");
        StatsBook loaded = new StatsBook(0, NetworkLevel.DEFAULT);
        assertTrue(loaded.load(read));
        assertEquals(35, loaded.elo(ALICE, "ranked-1v1"));
        // a file of the first season (ELO from 1000) starts the divisions again from 0
        String old = StatsFiles.toYaml(alice).replace("elo-saison: " + StatsFiles.ELO_SEASON, "elo-saison: 1");
        assertEquals(0, StatsFiles.fromYaml(ALICE, old).mode("ranked-1v1").elo);
        assertEquals(1234, StatsFiles.fromYaml(ALICE, old).points(), "the rest of the file stays");
    }

    private static List<String> names(List<StatsBook.Row> rows) {
        List<String> names = new java.util.ArrayList<>();
        for (StatsBook.Row row : rows) {
            names.add(row.name());
        }
        return names;
    }

    @Test
    void theFinalKillsAndTheBedsAreCountedEveryModeTogetherAndKeptInTheFile() throws Exception {
        StatsBook book = new StatsBook(1000, NetworkLevel.DEFAULT);
        book.finalKill("ranked-1v1", ALICE, NAMES);
        book.finalKill("ranked-1v1", ALICE, NAMES);
        PlayerStats alice = book.finalKill("ranked-2v2", ALICE, NAMES);
        assertEquals(3, alice.finalKills());
        assertEquals(1, book.bedBroken("ranked-2v2", ALICE, NAMES).beds());
        PlayerStats read = StatsFiles.fromYaml(ALICE, StatsFiles.toYaml(book.get(ALICE)));
        assertEquals(3, read.finalKills());
        assertEquals(1, read.beds());
        assertEquals(2, read.modes().get("ranked-1v1").finalKills);
    }
}
