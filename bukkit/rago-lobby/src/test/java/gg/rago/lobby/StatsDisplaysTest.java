package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void theMainBoardGoesFromTheEloToTheKillsAndTheLevel() throws Exception {
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

        List<String> elo = plain(displays.board(stats, key, alice, SUNDAY));
        assertEquals(StatsDisplays.BOARD_LINES, elo.size());
        assertEquals("Ranked Elo", elo.get(0));
        assertEquals("Ranked Bedwars 1v1", elo.get(1));
        assertEquals("Right click: next leaderboard", elo.get(elo.size() - 2));
        assertEquals("Left click: change the mode", elo.get(elo.size() - 1));
        displays.next(alice, stats, key, true);
        assertEquals("Ranked Bedwars 2v2", plain(displays.board(stats, key, alice, SUNDAY)).get(1), "a left click changes the mode");

        displays.next(alice, stats, key, false);
        List<String> kills = plain(displays.board(stats, key, alice, SUNDAY));
        assertEquals("Kills", kills.get(0), "a right click goes to the next leaderboard");
        assertEquals("All modes · Daily", kills.get(1), "from its first period");
        assertEquals("1. Alice - 2", kills.get(3));
        assertEquals("You: #1 - 2", kills.get(3 + StatsDisplays.TOP + 1));
        assertEquals("Left click: change the period", kills.get(kills.size() - 1));
        displays.next(alice, stats, key, true);
        assertEquals("All modes · Weekly", plain(displays.board(stats, key, alice, SUNDAY)).get(1));
        displays.next(alice, stats, key, true);
        assertEquals("All modes · Lifetime", plain(displays.board(stats, key, alice, SUNDAY)).get(1));

        displays.next(alice, stats, key, false);
        List<String> level = plain(displays.board(stats, key, alice, SUNDAY));
        assertEquals("Network level", level.get(0));
        assertEquals("1. Alice - 1", level.get(3), "60 XP: still level 1");
        assertEquals("2. Bob - 1", level.get(4));
        assertEquals(StatsDisplays.BOARD_LINES - 1, level.size(), "one click line: nothing to change on it");

        displays.next(alice, stats, key, false);
        assertEquals("Ranked Elo", plain(displays.board(stats, key, alice, SUNDAY)).get(0), "and back to the first");
        assertEquals("Ranked Elo", plain(displays.board(stats, key, player(BOB), SUNDAY)).get(0),
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
        assertEquals("Click to change the period", lines.get(lines.size() - 1));
        for (boolean left : new boolean[] {false, true}) {
            displays.next(bob, stats, "p#0", left);
        }
        assertEquals("All modes · Lifetime", plain(displays.board(stats, "p#0", bob, SUNDAY)).get(1),
                "both clicks change the period");
        displays.next(bob, stats, "p#0", false);
        assertEquals("All modes · Daily", plain(displays.board(stats, "p#0", bob, SUNDAY)).get(1));
    }

    @Test
    void theModeMenuShowsTheDailyStreaksAsOnMinemen() throws Exception {
        LobbySettings settings = bundledSettings();
        StatsBook book = new StatsBook(1000, settings.levels());
        ModeMenu menu = new ModeMenu(settings, null, book);
        long now = System.currentTimeMillis();
        assertEquals(Collections.singletonList("§eDaily Streak: 0"),
                menu.streakLines(settings.catalog().get("block-fight-2v2"), player(ALICE)), "nobody yet: no top");
        UUID carl = new UUID(0, 3);
        UUID dave = new UUID(0, 4);
        StatsBook.Names names = id -> id.equals(ALICE) ? "Alice" : id.equals(BOB) ? "bob" : id.equals(carl) ? "Carl" : "Dave";
        for (int i = 0; i < 2; i++) {
            book.record(settings.catalog().get("block-fight-2v2"), Arrays.asList(BOB, ALICE), Arrays.asList(carl, dave), names, now);
        }
        book.record(settings.catalog().get("block-fight-2v2"), Arrays.asList(carl, dave), Arrays.asList(ALICE, BOB), names, now);
        assertEquals(Arrays.asList("§eDaily Streak: 0", "§e 1. §fAlice, bob§e - 2", "§e 2. §fCarl, Dave§e - 1"),
                menu.streakLines(settings.catalog().get("block-fight-2v2"), player(ALICE)));
        assertEquals("§eDaily Streak: 1", menu.streakLines(settings.catalog().get("block-fight-2v2"), player(carl)).get(0),
                "the viewer's wins in a row");
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
        assertTrue(lines.contains("Network level: 6 [Iron]"), lines.toString());
        assertTrue(lines.contains("Kills: 1"), lines.toString());
        assertEquals("CLICK FOR YOUR STATS", lines.get(lines.size() - 1));
    }
}
