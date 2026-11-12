package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NetworkLevelTest {

    private static final NetworkLevel LEVELS = NetworkLevel.DEFAULT;

    @Test
    void eachLevelTakesMoreXp() {
        assertEquals(100, LEVELS.xpToNext(1));
        assertEquals(150, LEVELS.xpToNext(2));
        assertEquals(1, LEVELS.level(0));
        assertEquals(1, LEVELS.level(99));
        assertEquals(2, LEVELS.level(100));
        assertEquals(2, LEVELS.level(249));
        assertEquals(3, LEVELS.level(250));
        assertEquals(20, LEVELS.xpIntoLevel(120));
        assertEquals(0.5f, LEVELS.progress(50), 0.001f);
        assertTrue(LEVELS.progress(99) < 1f);
        assertEquals(NetworkLevel.MAX_LEVEL, LEVELS.level(Long.MAX_VALUE / 2), "the level stops counting");
        assertThrows(IllegalArgumentException.class, () -> new NetworkLevel(50, 15, 5, 0, 50, Collections.<Integer, String>emptyMap()));
        assertThrows(IllegalArgumentException.class, () -> new NetworkLevel(-1, 15, 5, 100, 50, Collections.<Integer, String>emptyMap()));
    }

    @Test
    void theTagChangesColourByTier() {
        assertEquals("§7[1" + NetworkLevel.STAR + "]", LEVELS.tag(1));
        assertEquals("§7[9" + NetworkLevel.STAR + "]", LEVELS.tag(9));
        assertEquals("§f[10" + NetworkLevel.STAR + "]", LEVELS.tag(10));
        assertEquals("§6[25" + NetworkLevel.STAR + "]", LEVELS.tag(25));
        assertEquals("§c[§61§e0§a0§b" + NetworkLevel.STAR + "§d]", LEVELS.tag(NetworkLevel.PRESTIGE),
                "level 100 in a rainbow, one colour per character");
        assertEquals("§c", LEVELS.colour(NetworkLevel.PRESTIGE), "its first colour where one is enough");
        assertEquals("§c[100" + NetworkLevel.STAR + "]", LEVELS.tag(NetworkLevel.PRESTIGE, 11),
                "in its first colour where the rainbow does not fit");
        assertEquals("§c[1§60§e0§a" + NetworkLevel.STAR + "]", LEVELS.tag(NetworkLevel.PRESTIGE, 15),
                "above the head, the brackets take the colours of their neighbours");
        assertEquals(LEVELS.tag(NetworkLevel.PRESTIGE), LEVELS.tag(NetworkLevel.PRESTIGE, 18), "the whole rainbow where it fits");
        assertEquals("§6[25" + NetworkLevel.STAR + "]", LEVELS.tag(25, 15));
        assertEquals("§c1§60§e0", LEVELS.paint("100", NetworkLevel.PRESTIGE));
        assertEquals("§625", LEVELS.paint("25", 25));
        assertEquals(10, LEVELS.nextTier(1));
        assertEquals(30, LEVELS.nextTier(25));
        assertEquals(200, LEVELS.nextTier(100), "the first prestige");
        assertEquals(0, LEVELS.nextTier(NetworkLevel.MAX_LEVEL), "the last tier");
        Map<Integer, String> one = new HashMap<>();
        one.put(5, "§a");
        NetworkLevel custom = new NetworkLevel(50, 15, 5, 100, 50, one);
        assertEquals("§7", custom.colour(4), "grey below the first tier");
        assertEquals("§a", custom.colour(50));
        Map<Integer, String> bold = new HashMap<>();
        bold.put(1, "§a§b§c§l");
        NetworkLevel rainbow = new NetworkLevel(50, 15, 5, 100, 50, bold);
        assertEquals("§a§lA§b§lB§c§lC§a§lD", rainbow.paint("ABCD", 3), "the format after each colour");
        assertEquals("§a§l", rainbow.colour(3));
        Map<Integer, String> two = new HashMap<>();
        two.put(1, "§a§b");
        assertEquals("§aABC", new NetworkLevel(50, 15, 5, 100, 50, two).paint("ABC", 3), "two colours: a text in the first");
    }

    @Test
    void theTeamsSortTheTabByRankThenNumber() {
        assertTrue(LobbyScoreboard.teamName(false, 0, 7).compareTo(LobbyScoreboard.teamName(false, 3, 2)) < 0,
                "the highest rank first");
        assertTrue(LobbyScoreboard.teamName(false, 3, 2).compareTo(LobbyScoreboard.teamName(false, 3, 9)) < 0);
        assertTrue(LobbyScoreboard.teamName(false, 99, 99_999).length() <= 16);
        assertTrue(LobbyScoreboard.teamName(true, 99, 99_999).length() <= 16);
        assertTrue(!LobbyScoreboard.teamName(true, 0, 1).equals(LobbyScoreboard.teamName(false, 0, 1)));
        for (RankDef rank : Ranks.defaults().all()) {
            assertTrue(rank.tagPrefix().length() <= 16, rank.id());
        }
        for (int level : LEVELS.colours().keySet()) {
            assertTrue((" " + LEVELS.tag(level, 15)).length() <= 16, "the level " + level + " after the name fits");
        }
    }

    @Test
    void chatPrefixShowsTheLevelAndTheRank() {
        StatsBook book = new StatsBook(0, LEVELS);
        Ranks ranks = Ranks.defaults();
        UUID alice = new UUID(0, 1);
        assertEquals("§7[1" + NetworkLevel.STAR + "] §7", NetworkRanks.prefix(book, ranks, alice));
        PlayerStats stats = new PlayerStats(alice, "Alice");
        stats.addXp(1000);
        stats.boughtRanks().add("premium");
        book.load(stats);
        assertEquals(LEVELS.level(1000), book.level(alice));
        assertEquals("§7[" + LEVELS.level(1000) + NetworkLevel.STAR + "] §d[PREMIUM§f+§d] §d", NetworkRanks.prefix(book, ranks, alice));
        stats.setRank("admin");
        assertEquals("admin", NetworkRanks.rankOf(book, ranks, alice).id(), "the highest of the ranks");
        stats.setRank("unknown");
        assertEquals("premium", NetworkRanks.rankOf(book, ranks, alice).id(), "an unknown rank is left out");
    }
}
