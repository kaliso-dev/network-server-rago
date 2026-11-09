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
        assertEquals("§e[" + NetworkLevel.MAX_LEVEL + NetworkLevel.STAR + "]", LEVELS.tag(NetworkLevel.MAX_LEVEL));
        assertEquals(10, LEVELS.nextTier(1));
        assertEquals(30, LEVELS.nextTier(25));
        assertEquals(0, LEVELS.nextTier(100), "the last tier");
        Map<Integer, String> one = new HashMap<>();
        one.put(5, "§a");
        NetworkLevel custom = new NetworkLevel(50, 15, 5, 100, 50, one);
        assertEquals("§7", custom.colour(4), "grey below the first tier");
        assertEquals("§a", custom.colour(50));
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
        assertTrue((" " + LEVELS.tag(NetworkLevel.MAX_LEVEL)).length() <= 16, "the level after the name fits");
    }

    @Test
    void chatPrefixShowsTheLevelAndTheRank() {
        StatsBook book = new StatsBook(0, LEVELS);
        Ranks ranks = Ranks.defaults();
        UUID alice = new UUID(0, 1);
        assertEquals("§7[1" + NetworkLevel.STAR + "] §7", NetworkRanks.prefix(book, ranks, alice));
        PlayerStats stats = new PlayerStats(alice, "Alice");
        stats.addXp(1000);
        stats.boughtRanks().add("vip");
        book.load(stats);
        assertEquals(LEVELS.level(1000), book.level(alice));
        assertEquals("§7[" + LEVELS.level(1000) + NetworkLevel.STAR + "] §a[VIP] §a", NetworkRanks.prefix(book, ranks, alice));
        stats.setRank("admin");
        assertEquals("admin", NetworkRanks.rankOf(book, ranks, alice).id(), "the highest of the ranks");
        stats.setRank("unknown");
        assertEquals("vip", NetworkRanks.rankOf(book, ranks, alice).id(), "an unknown rank is left out");
    }
}
