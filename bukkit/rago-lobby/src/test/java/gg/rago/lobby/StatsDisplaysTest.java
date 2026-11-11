package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.game.GameMode;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class StatsDisplaysTest {

    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final long SUNDAY = ZonedDateTime.of(2026, 11, 8, 10, 0, 0, 0, StatsPeriod.ZONE).toInstant().toEpochMilli();
    private static final StatsBook.Names NAMES = id -> id.equals(ALICE) ? "Alice" : "Bob";

    /** A player that only knows its UUID: all a board asks of its viewer. */
    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUniqueId":
                            return id;
                        case "hashCode":
                            return id.hashCode();
                        case "equals":
                            return proxy == args[0];
                        default:
                            return null;
                    }
                });
    }

    private static LobbySettings bundledSettings() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(StatsDisplaysTest.class.getResourceAsStream("/config.yml"),
                StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        return LobbySettings.load(yaml);
    }

    private static List<String> plain(List<String> lines) {
        List<String> plain = new java.util.ArrayList<>();
        for (String line : lines) {
            plain.add(ChatColor.stripColor(line));
        }
        return plain;
    }

    @Test
    void theMainBoardGoesFromTheLevelToTheEloAndTheKills() throws Exception {
        LobbySettings settings = bundledSettings();
        StatsBook book = new StatsBook(1000, settings.levels());
        book.record(settings.catalog().get("sumo"), Collections.singletonList(ALICE), Collections.singletonList(BOB),
                NAMES, SUNDAY);
        book.kill("sumo", ALICE, BOB, NAMES, SUNDAY);
        book.kill("sumo", ALICE, BOB, NAMES, SUNDAY);
        StatsDisplays displays = new StatsDisplays(settings, book, new StatsMenu(settings, book), new FakeEntities(null, null));
        Player alice = player(ALICE);
        List<BoardStat> stats = settings.npc("leaderboardprincipal").boards().get(0);
        String key = "leaderboardprincipal#0";

        List<String> level = plain(displays.board(stats, key, alice, SUNDAY));
        assertEquals("Network level", level.get(0), "the Network level first");
        assertEquals("1. Alice - 1", level.get(3), "60 XP: still level 1");
        assertEquals("2. Bob - 1", level.get(4));
        assertEquals(StatsDisplays.BOARD_LINES - 1, level.size(), "one click line: nothing to change on it");

        displays.next(alice, stats, key, false);
        List<String> elo = plain(displays.board(stats, key, alice, SUNDAY));
        assertEquals(StatsDisplays.BOARD_LINES, elo.size());
        assertEquals("Ranked Elo", elo.get(0), "a right click goes to the next leaderboard");
        assertEquals("Ranked Bedwars 1v1", elo.get(1));
        assertEquals("» Click here: next leaderboard", elo.get(StatsDisplays.FIRST_CLICK_LINE));
        assertEquals("» Click here: change the mode", elo.get(StatsDisplays.FIRST_CLICK_LINE + 1));
        displays.next(alice, stats, key, true);
        assertEquals("Ranked Bedwars 2v2", plain(displays.board(stats, key, alice, SUNDAY)).get(1), "a left click changes the mode");

        displays.next(alice, stats, key, false);
        List<String> kills = plain(displays.board(stats, key, alice, SUNDAY));
        assertEquals("Kills", kills.get(0));
        assertEquals("All modes · Daily", kills.get(1), "from its first period");
        assertEquals("1. Alice - 2", kills.get(3));
        assertEquals("You: #1 - 2", kills.get(3 + StatsDisplays.TOP + 1));
        assertEquals("» Click here: change the period", kills.get(kills.size() - 1));
        displays.next(alice, stats, key, true);
        assertEquals("All modes · Weekly", plain(displays.board(stats, key, alice, SUNDAY)).get(1));
        displays.next(alice, stats, key, true);
        assertEquals("All modes · Lifetime", plain(displays.board(stats, key, alice, SUNDAY)).get(1));

        displays.next(alice, stats, key, false);
        List<String> games = plain(displays.board(stats, key, alice, SUNDAY));
        assertEquals("Games played", games.get(0), "then the games played");
        assertEquals("All modes · Daily", games.get(1));
        assertEquals("1. Alice - 1", games.get(3), "every game counts, won or lost");
        assertEquals("2. Bob - 1", games.get(4));

        displays.next(alice, stats, key, false);
        assertEquals("Network level", plain(displays.board(stats, key, alice, SUNDAY)).get(0), "and back to the first");
        displays.next(alice, stats, key, false);
        assertEquals("Ranked Elo", plain(displays.board(stats, key, alice, SUNDAY)).get(0));
        assertEquals("Network level", plain(displays.board(stats, key, player(BOB), SUNDAY)).get(0),
                "each player has their own choice");
    }

    @Test
    void theKillsBoardChangesItsPeriodOnAnyClick() throws Exception {
        LobbySettings settings = bundledSettings();
        StatsBook book = new StatsBook(1000, settings.levels());
        StatsDisplays displays = new StatsDisplays(settings, book, new StatsMenu(settings, book), new FakeEntities(null, null));
        Player bob = player(BOB);
        List<BoardStat> stats = settings.npc("leaderboardplayer").boards().get(1);
        assertEquals(Collections.singletonList(BoardStat.KILLS), stats);
        List<String> lines = plain(displays.board(stats, "p#0", bob, SUNDAY));
        assertEquals(Arrays.asList("Kills", "All modes · Daily", "", "Nobody yet"), lines.subList(0, 4));
        assertEquals("You: not ranked yet", lines.get(3 + StatsDisplays.TOP + 1));
        assertEquals("» Click here: change the period", lines.get(StatsDisplays.FIRST_CLICK_LINE));
        for (boolean left : new boolean[] {false, true}) {
            displays.next(bob, stats, "p#0", left);
        }
        assertEquals("All modes · Lifetime", plain(displays.board(stats, "p#0", bob, SUNDAY)).get(1),
                "both clicks change the period");
        displays.next(bob, stats, "p#0", false);
        assertEquals("All modes · Daily", plain(displays.board(stats, "p#0", bob, SUNDAY)).get(1));
    }

    @Test
    void eachClickLineDoesWhatItSaysWhicheverTheButton() throws Exception {
        LobbySettings settings = bundledSettings();
        StatsBook book = new StatsBook(1000, settings.levels());
        StatsDisplays displays = new StatsDisplays(settings, book, new StatsMenu(settings, book), new FakeEntities(null, null));
        Player alice = player(ALICE);
        // a board of three, the ELO first: its line of the mode, then the kills and the level
        List<BoardStat> stats = Arrays.asList(BoardStat.ELO, BoardStat.KILLS, BoardStat.LEVEL);
        String key = "main#0";
        displays.clickLine(ALICE, stats, key, StatsDisplays.FIRST_CLICK_LINE + 1, false);
        assertEquals("Ranked Bedwars 2v2", plain(displays.board(stats, key, alice, SUNDAY)).get(1),
                "a right click on the line of the mode changes the mode");
        displays.clickLine(ALICE, stats, key, StatsDisplays.FIRST_CLICK_LINE, true);
        assertEquals("Kills", plain(displays.board(stats, key, alice, SUNDAY)).get(0),
                "a left click on the line of the next leaderboard goes to it");
        displays.clickLine(ALICE, stats, key, 5, true);
        assertEquals("Network level", plain(displays.board(stats, key, alice, SUNDAY)).get(0),
                "elsewhere, a left click goes to the next leaderboard");
        displays.clickLine(ALICE, stats, key, 5, false);
        assertEquals("Ranked Elo", plain(displays.board(stats, key, alice, SUNDAY)).get(0),
                "and so does a right click");
        displays.clickLine(ALICE, stats, key, StatsDisplays.FIRST_CLICK_LINE + 1, true);
        assertEquals("Ranked Bedwars 2v2", plain(displays.board(stats, key, alice, SUNDAY)).get(1),
                "a left click on the line of the mode changes the mode");
    }

    @Test
    void aBoardIsAimedAtFromAfarOnItsText() {
        double line = FakeHologram.LINE;
        double bottom = 65;
        double low = bottom - line / 2;
        double high = bottom + (StatsDisplays.BOARD_LINES - 1) * line + line / 2;
        double reach = StatsDisplays.AIM_REACH;
        double half = StatsDisplays.AIM_HALF_WIDTH;
        // Ten blocks south of the board, looking north at the height of its last line.
        assertEquals(10, StatsDisplays.aim(0, bottom, 10, 0, 0, -1, 0, 0, low, high, half, reach), 1e-9);
        assertTrue(StatsDisplays.aim(2, bottom, 10, 0, 0, -1, 0, 0, low, high, half, reach) > 0, "two blocks aside: on its text");
        assertEquals(-1, StatsDisplays.aim(3, bottom, 10, 0, 0, -1, 0, 0, low, high, half, reach), "three: beside it");
        assertEquals(-1, StatsDisplays.aim(0, bottom, 10, 0, 0, 1, 0, 0, low, high, half, reach), "turned away");
        assertEquals(-1, StatsDisplays.aim(0, bottom, 30, 0, 0, -1, 0, 0, low, high, half, reach), "too far");
        assertEquals(-1, StatsDisplays.aim(0, high + 0.5, 10, 0, 0, -1, 0, 0, low, high, half, reach), "over it");
        // From the east, looking west and a little up: on its fifth line from the bottom.
        double up = 4 * line / 6;
        double norm = Math.sqrt(1 + up * up);
        double distance = StatsDisplays.aim(6, bottom, 0.5, -1 / norm, up / norm, 0, 0, 0, low, high, half, reach);
        assertTrue(distance > 0);
        assertEquals(StatsDisplays.BOARD_LINES - 5, StatsDisplays.lineAt(up / norm * distance));
        assertEquals(StatsDisplays.BOARD_LINES - 1, StatsDisplays.lineAt(0.1));
        assertEquals(StatsDisplays.FIRST_CLICK_LINE, StatsDisplays.lineAt(line + 0.05),
                "the line above the last one, on a board with two click lines");
    }

    @Test
    void theModeMenuShowsTheDailyStreaksAsOnMinemen() throws Exception {
        LobbySettings settings = bundledSettings();
        StatsBook book = new StatsBook(1000, settings.levels());
        ModeMenu menu = new ModeMenu(settings, null, book);
        long now = System.currentTimeMillis();
        assertEquals(Arrays.asList("§eDaily Streak: 0", "§eYour position: §7-"),
                menu.streakLines(settings.catalog().get("block-fight-2v2"), player(ALICE)), "nobody yet: no top");
        UUID carl = new UUID(0, 3);
        UUID dave = new UUID(0, 4);
        StatsBook.Names names = id -> id.equals(ALICE) ? "Alice" : id.equals(BOB) ? "bob" : id.equals(carl) ? "Carl" : "Dave";
        for (int i = 0; i < 2; i++) {
            book.record(settings.catalog().get("block-fight-2v2"), Arrays.asList(BOB, ALICE), Arrays.asList(carl, dave), names, now);
        }
        book.record(settings.catalog().get("block-fight-2v2"), Arrays.asList(carl, dave), Arrays.asList(ALICE, BOB), names, now);
        assertEquals(Arrays.asList("§eDaily Streak: 0", "§e 1. §fAlice, bob§e - 2", "§e 2. §fCarl, Dave§e - 1",
                "§eYour position: §f#1§7 (2)"), menu.streakLines(settings.catalog().get("block-fight-2v2"), player(ALICE)),
                "the best of the day of the viewer's team, then their place");
        List<String> carls = menu.streakLines(settings.catalog().get("block-fight-2v2"), player(carl));
        assertEquals("§eDaily Streak: 1", carls.get(0), "the viewer's wins in a row");
        assertEquals("§eYour position: §f#2§7 (1)", carls.get(carls.size() - 1));
        assertEquals("§eYour position: §7-", ModeMenu.positionLine(book.topStreaks("block-fight-2v2", StatsPeriod.DAY, now),
                new UUID(0, 9)), "not on the board today");
    }

    @Test
    void pingsAreColouredByQuality() {
        assertEquals("§a12 ms", LobbyScoreboard.pingText(12));
        assertEquals("§e" + LobbyScoreboard.GOOD_PING + " ms", LobbyScoreboard.pingText(LobbyScoreboard.GOOD_PING));
        assertEquals("§c300 ms", LobbyScoreboard.pingText(300));
        assertEquals("§7?", LobbyScoreboard.pingText(-1));
        assertEquals(-1, LobbyScoreboard.pingOf(player(ALICE)), "not a server's player: no ping");
    }

    @Test
    void theProfileShowsTheLevelAndTheKills() throws Exception {
        LobbySettings settings = bundledSettings();
        StatsBook book = new StatsBook(1000, settings.levels());
        PlayerStats stats = new PlayerStats(ALICE, "Alice");
        stats.addXp(1000);
        book.load(stats);
        book.kill("sumo", ALICE, BOB, NAMES, SUNDAY);
        StatsDisplays displays = new StatsDisplays(settings, book, new StatsMenu(settings, book), new FakeEntities(null, null));
        List<String> lines = plain(displays.profile(settings.npc("statsjoueur1"), player(ALICE)));
        assertTrue(lines.contains("Network level: [6" + NetworkLevel.STAR + "]"), lines.toString());
        assertTrue(lines.contains("Kills: 1 · Final kills: 0"), lines.toString());
        assertTrue(lines.contains("Games played: 0"), lines.toString());
        assertTrue(lines.contains("FKDR: 0"), lines.toString());
        assertEquals("CLICK FOR YOUR STATS", lines.get(lines.size() - 1));
        assertEquals(11, lines.size(), "the lines of the hologram");
    }

    @Test
    void theProfileShowsTheGamesTheWlrAndTheFkdr() throws Exception {
        LobbySettings settings = bundledSettings();
        StatsBook book = new StatsBook(1000, settings.levels());
        GameMode ranked = settings.catalog().get("ranked-1v1");
        GameMode sumo = settings.catalog().get("sumo");
        for (int i = 0; i < 3; i++) {
            book.record(ranked, Collections.singletonList(ALICE), Collections.singletonList(BOB), NAMES, SUNDAY);
        }
        book.record(sumo, Collections.singletonList(BOB), Collections.singletonList(ALICE), NAMES, SUNDAY);
        book.record(sumo, Collections.singletonList(BOB), Collections.singletonList(ALICE), NAMES, SUNDAY);
        for (int i = 0; i < 5; i++) {
            book.finalKill("ranked-1v1", ALICE, NAMES);
        }
        book.finalDeath("ranked-1v1", ALICE, NAMES);
        book.finalDeath("ranked-2v2", ALICE, NAMES);
        PlayerStats alice = book.get(ALICE);
        assertEquals(5, alice.games(), "every game, won or lost, every mode");
        assertEquals(2, alice.finalDeaths());
        StatsDisplays displays = new StatsDisplays(settings, book, new StatsMenu(settings, book), new FakeEntities(null, null));
        List<String> lines = plain(displays.profile(settings.npc("statsjoueur1"), player(ALICE)));
        assertTrue(lines.contains("Games played: 5"), lines.toString());
        assertTrue(lines.contains("Wins: 3 · Losses: 2"), lines.toString());
        assertTrue(lines.contains("WLR: 1.5"), lines.toString());
        assertTrue(lines.contains("FKDR: 2.5"), "5 final kills, 2 final deaths: " + lines);
        PlayerStats read = StatsFiles.fromYaml(ALICE, StatsFiles.toYaml(alice));
        assertEquals(2, read.finalDeaths(), "kept in the file");
        assertEquals(1, read.mode("ranked-2v2").finalDeaths);
        assertEquals(5, read.games());
    }
}
