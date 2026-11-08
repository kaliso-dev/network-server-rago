package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
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
    }

    @Test
    void ranksComeWithTheLevels() {
        assertNull(LEVELS.rank(4));
        assertEquals("§f[Iron]", LEVELS.rank(5));
        assertEquals("§6[Gold]", LEVELS.rank(12), "the highest rank reached");
        assertEquals(10, LEVELS.rankLevel(19));
        assertEquals(0, LEVELS.rankLevel(4));
        assertEquals(20, (int) LEVELS.nextRank(12).getKey());
        assertNull(LEVELS.nextRank(100), "the last rank");
        assertThrows(IllegalArgumentException.class, () -> new NetworkLevel(50, 15, 5, 0, 50, Collections.<Integer, String>emptyMap()));
        assertThrows(IllegalArgumentException.class, () -> new NetworkLevel(-1, 15, 5, 100, 50, Collections.<Integer, String>emptyMap()));
    }

    @Test
    void rankTeamsSortTheHighestRanksFirst() {
        assertTrue(LobbyScoreboard.rankTeam(100).compareTo(LobbyScoreboard.rankTeam(5)) < 0);
        assertTrue(LobbyScoreboard.rankTeam(5).compareTo(LobbyScoreboard.rankTeam(0)) < 0);
        assertTrue(LobbyScoreboard.rankTeam(NetworkLevel.MAX_LEVEL).length() <= 16);
        assertEquals("§6[Gold] ", LobbyScoreboard.rankPrefix("§6[Gold]"));
        assertEquals("", LobbyScoreboard.rankPrefix(null));
        for (String rank : LEVELS.ranks().values()) {
            assertTrue(LobbyScoreboard.rankPrefix(rank).length() <= 16, rank);
        }
    }

    @Test
    void chatPrefixShowsTheLevelAndTheRank() {
        StatsBook book = new StatsBook(1000, LEVELS);
        UUID alice = new UUID(0, 1);
        assertEquals("§7[1] §7", NetworkRanks.prefix(book, alice));
        PlayerStats stats = new PlayerStats(alice, "Alice");
        stats.addXp(1000);
        book.load(stats);
        assertEquals(LEVELS.level(1000), book.level(alice));
        assertEquals("§7[" + LEVELS.level(1000) + "] §f[Iron] ", NetworkRanks.prefix(book, alice));
    }
}
