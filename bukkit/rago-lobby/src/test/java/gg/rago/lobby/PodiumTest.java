package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The podium behind the leaderboard, and the skins its players wear. */
class PodiumTest {

    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final long SUNDAY = ZonedDateTime.of(2026, 11, 8, 10, 0, 0, 0, StatsPeriod.ZONE).toInstant().toEpochMilli();
    private static final StatsBook.Names NAMES = id -> id.equals(ALICE) ? "Alice" : "Bob";

    private static LobbySettings bundledSettings() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(PodiumTest.class.getResourceAsStream("/config.yml"),
                StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        return LobbySettings.load(yaml);
    }

    private static LobbySettings settings(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return LobbySettings.load(yaml);
    }

    @Test
    void theMainBoardHasThePodiumOfTheLobbyMap() throws Exception {
        LobbyNpc wall = bundledSettings().npc("leaderboardprincipal");
        assertEquals(Arrays.asList("0.5 67 57.5", "-1.5 66 57.5", "2.5 65 57.5"), wall.podium());
        assertArrayEquals(new double[] {0.5, 67, 57.5}, LobbyNpc.coordinates(wall.podium().get(0)));
        assertArrayEquals(new double[] {-1.5, 66, 57.5}, LobbyNpc.coordinates("  -1.5   66 57.5 "));
        assertNull(LobbyNpc.coordinates("podium1"), "a point name");
        assertNull(LobbyNpc.coordinates("1 2"));
        assertNull(LobbyNpc.coordinates("1 2 NaN"));
        assertTrue(bundledSettings().npc("leaderboardplayer").podium().isEmpty(), "a podium is optional");
    }

    @Test
    void aStepIsAPointOrCoordinates() throws Exception {
        LobbySettings ok = settings("classements:\n  mur:\n    tableaux: [kills]\n    podium: [podium1, '1 64 2']\nmodes:\n  sumo: {}\n");
        assertEquals(Arrays.asList("podium1", "1 64 2"), ok.npc("mur").podium());
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> settings("classements:\n  mur:\n    tableaux: [kills]\n    podium: ['1 2']\nmodes:\n  sumo: {}\n"));
        assertTrue(wrong.getMessage().startsWith("classements.mur.podium"), wrong.getMessage());
        IllegalArgumentException many = assertThrows(IllegalArgumentException.class,
                () -> settings("classements:\n  mur:\n    tableaux: [kills]\n    podium: [a, b, c, d, e, f]\nmodes:\n  sumo: {}\n"));
        assertTrue(many.getMessage().startsWith("classements.mur.podium"), many.getMessage());
    }

    @Test
    void thePodiumShowsTheTopOfWhatEachPlayerReads() throws Exception {
        LobbySettings settings = bundledSettings();
        StatsBook book = new StatsBook(1000, settings.levels());
        book.record(settings.catalog().get("sumo"), Collections.singletonList(ALICE), Collections.singletonList(BOB),
                NAMES, SUNDAY);
        book.kill("sumo", ALICE, BOB, NAMES, SUNDAY);
        book.kill("sumo", ALICE, BOB, NAMES, SUNDAY);
        StatsDisplays displays = new StatsDisplays(settings, book, new StatsMenu(settings, book), new FakeEntities(null, null));
        List<BoardStat> main = settings.npc("leaderboardprincipal").boards().get(0);
        assertEquals(BoardStat.LEVEL, main.get(0), "the podium shows the Network level first");
        assertEquals(ALICE, displays.holder(main, "leaderboardprincipal#0", BOB, 0, SUNDAY).id(), "Alice won: more XP");
        assertEquals(Arrays.asList("#1 Alice", "Level 1"), plain(displays.podiumLines(main, "leaderboardprincipal#0", BOB, 0,
                SUNDAY)));
        // a board of three, the ELO first
        List<BoardStat> stats = Arrays.asList(BoardStat.ELO, BoardStat.KILLS, BoardStat.LEVEL);
        String key = "elo#0";

        assertNull(displays.holder(stats, key, ALICE, 0, SUNDAY), "nobody plays a ranked mode yet");
        assertEquals(Arrays.asList("#1 Nobody yet"), plain(displays.podiumLines(stats, key, ALICE, 0, SUNDAY)));

        assertEquals(1, book.top(BoardStat.KILLS, StatsPeriod.ALL, "sumo", 10, SUNDAY).size(), "Alice alone has kills");
        assertEquals(ALICE, book.top(BoardStat.KILLS, StatsPeriod.ALL, "sumo", 10, SUNDAY).get(0).id());
        assertTrue(book.top(BoardStat.KILLS, StatsPeriod.ALL, "sumo", 0, SUNDAY).isEmpty());
        displays.next(ALICE, stats, key, false);
        assertEquals(ALICE, displays.holder(stats, key, ALICE, 0, SUNDAY).id(), "Alice reads the kills");
        assertEquals(Arrays.asList("#1 Alice", "2 kills"), plain(displays.podiumLines(stats, key, ALICE, 0, SUNDAY)),
                "the name above, the score under it");
        assertTrue(displays.podiumLines(stats, key, ALICE, 0, SUNDAY).get(0).startsWith("§6§l#1"), "the first place in gold");
        NameLooks names = new NameLooks(book, ServerSettings.load(new YamlConfiguration()));
        displays.setNames(names::stillDisplay);
        book.get(ALICE).boughtRanks().add("premium");
        assertEquals(Arrays.asList("#1 [PREMIUM+] Alice", "2 kills"), plain(displays.podiumLines(stats, key, ALICE, 0, SUNDAY)),
                "her rank next to her name");
        assertEquals("§6§l#1 §d[PREMIUM§f+§d] " + NameStyle.gradient("Alice", "5ddfd", false),
                displays.podiumLines(stats, key, ALICE, 0, SUNDAY).get(0), "her name in its colour");
        assertNull(displays.holder(stats, key, ALICE, 1, SUNDAY), "Bob has no kill");
        assertEquals(Arrays.asList("#2 Nobody yet"), plain(displays.podiumLines(stats, key, ALICE, 1, SUNDAY)));
        assertNull(displays.holder(stats, key, BOB, 0, SUNDAY), "Bob still reads the ELO");

        displays.next(ALICE, stats, key, false);
        assertEquals(BOB, displays.holder(stats, key, ALICE, 1, SUNDAY).id(), "the level: Bob second");
        assertEquals(Arrays.asList("#2 Bob", "Level 1"), plain(displays.podiumLines(stats, key, ALICE, 1, SUNDAY)));
        assertNull(displays.holder(stats, key, ALICE, 2, SUNDAY), "nobody third");
    }

    @Test
    void aSkinComesFromMojangOrTheStats() throws Exception {
        String json = "{\"id\":\"00000000000000000000000000000001\",\"name\":\"Alice\",\"properties\":[{\"name\":\"textures\","
                + "\"value\":\"dGV4dHVyZXM=\",\"signature\":\"c2lnbmVk\"}]}";
        Skin skin = Skin.fromSession(json);
        assertEquals("dGV4dHVyZXM=", skin.value());
        assertEquals("c2lnbmVk", skin.signature());
        assertEquals(skin, new Skin("dGV4dHVyZXM=", "c2lnbmVk"));
        assertNotEquals(skin, new Skin("dGV4dHVyZXM=", "other"));
        assertNull(Skin.fromSession("{\"id\":\"x\",\"name\":\"Alice\",\"properties\":[{\"name\":\"textures\",\"value\":\"v\"}]}"),
                "no signature: a 1.8 client would not show it");
        assertNull(Skin.fromSession("{\"properties\":[]}"));
        assertNull(Skin.fromSession("not json"));
        assertNull(Skin.fromSession(null));

        PlayerStats alice = new PlayerStats(ALICE, "Alice");
        alice.setSkin(skin);
        assertEquals(skin, StatsFiles.fromYaml(ALICE, StatsFiles.toYaml(alice)).skin());
        assertNull(StatsFiles.fromYaml(BOB, StatsFiles.toYaml(new PlayerStats(BOB, "Bob"))).skin());
    }

    @Test
    void theBodiesOfThePodiumHaveNoNameTag() {
        assertEquals("§8pnjp0", LobbyNpcs.podiumName(0));
        assertNotEquals(LobbyNpcs.bodyName(0), LobbyNpcs.podiumName(0));
        assertTrue(LobbyNpcs.podiumName(LobbySettings.MAX_PODIUM - 1).length() <= 16, "a profile name");
    }

    private static List<String> plain(List<String> lines) {
        List<String> plain = new java.util.ArrayList<>();
        for (String line : lines) {
            plain.add(ChatColor.stripColor(line));
        }
        return plain;
    }
}
