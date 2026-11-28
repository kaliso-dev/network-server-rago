package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The sizes of the invis practice and of the Bed Break Simulator: 1v1 to 1v4, or open. */
class LineupTest {

    @Test
    void aSizeStartsAsSoonAsItsPlayersAreIn() {
        Lineup oneVersusThree = new Lineup(4, InvisGame.MAX_PLAYERS);
        assertFalse(oneVersusThree.open());
        assertEquals(4, oneVersusThree.capacity());
        assertEquals("1v3", oneVersusThree.format());
        assertEquals("4 players", oneVersusThree.players());
        assertFalse(oneVersusThree.joinable(1), "its players came together: nobody joins it");
        assertEquals(Lineup.FULL_WAIT_SECONDS, oneVersusThree.startIn(4), "all in: 3 seconds");
        assertEquals(Lineup.FULL_WAIT_SECONDS, oneVersusThree.startIn(3), "one left: the others play at once");
        assertEquals(-1, oneVersusThree.startIn(1), "alone: it ends");
        assertEquals("1v1", new Lineup(2, BedBreakGame.MAX_PLAYERS).format());
        assertEquals("1v4", new Lineup(5, BedBreakGame.MAX_PLAYERS).format());
        assertTrue(new Lineup(2, 5).when().contains("as soon as the 2 players are in"));
    }

    @Test
    void openItWaitsForPlayersUpToFive() {
        Lineup open = new Lineup(1, InvisGame.MAX_PLAYERS);
        assertTrue(open.open());
        assertEquals(5, open.capacity(), "five at most now, as the Bed Break Simulator");
        assertEquals(5, InvisGame.MAX_PLAYERS);
        assertEquals("1v1 to 1v4", open.format());
        assertEquals("2 to 5 players", open.players());
        assertTrue(open.joinable(4));
        assertFalse(open.joinable(5), "full");
        assertEquals(-1, open.startIn(1), "waits for a second player");
        assertEquals(Lineup.WAIT_SECONDS, open.startIn(2));
        assertEquals(Lineup.FULL_WAIT_SECONDS, open.startIn(5));
        assertTrue(new Lineup(0, 5).open(), "no size: open");
    }

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    private static Map<UUID, Integer> points(int a, int b, int c) {
        Map<UUID, Integer> points = new HashMap<>();
        points.put(A, a);
        points.put(B, b);
        points.put(C, c);
        return points;
    }

    @Test
    void theMostPointsWinAndTheOthersLose() {
        List<UUID> winners = new ArrayList<>();
        List<UUID> losers = new ArrayList<>();
        Map<UUID, Integer> points = points(2, 3, 3);
        Lineup.result(points.keySet(), Arrays.asList(A, B, C), points, winners, losers);
        assertEquals(Arrays.asList(B, C), sorted(winners), "a tie: both win");
        assertEquals(Collections.singletonList(A), losers);
    }

    @Test
    void aPlayerWhoLeftBeforeTheEndLosesTheGame() {
        List<UUID> winners = new ArrayList<>();
        List<UUID> losers = new ArrayList<>();
        Map<UUID, Integer> points = points(1, 0, 2);
        // C had the most points and left: a forfeit, the best of the players still there wins
        Lineup.result(points.keySet(), Arrays.asList(A, B), points, winners, losers);
        assertEquals(Collections.singletonList(A), winners);
        assertEquals(Arrays.asList(B, C), sorted(losers), "counted in the games played of the one who left");
    }

    @Test
    void withoutAPointAmongThePlayersStillThereNobodyWins() {
        List<UUID> winners = new ArrayList<>();
        List<UUID> losers = new ArrayList<>();
        Map<UUID, Integer> points = points(0, 0, 4);
        Lineup.result(points.keySet(), Arrays.asList(A, B), points, winners, losers);
        assertTrue(winners.isEmpty());
        assertTrue(losers.isEmpty(), "no result");
    }

    private static List<UUID> sorted(List<UUID> ids) {
        List<UUID> copy = new ArrayList<>(ids);
        Collections.sort(copy);
        return copy;
    }
}
