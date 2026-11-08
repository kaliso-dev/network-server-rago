package gg.rago.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModeQueueTest {

    private static final RatingWindow WINDOW = new RatingWindow(100, 50, 10, 500);

    private static QueueEntry entry(int n, int rating, long joinedAt) {
        return new QueueEntry(new UUID(0, n), rating, joinedAt);
    }

    private static QueueEntry entry(int n, int rating, long joinedAt, String map) {
        return new QueueEntry(new UUID(0, n), rating, joinedAt, map);
    }

    private static List<UUID> players(List<QueueEntry> entries) {
        List<UUID> ids = new ArrayList<>();
        for (QueueEntry e : entries) {
            ids.add(e.player());
        }
        return ids;
    }

    @Test
    void unrankedPairsInJoinOrder() {
        ModeQueue queue = new ModeQueue(new GameMode("sumo", "Sumo", 2, 1, false), WINDOW);
        QueueEntry a = entry(1, 1000, 0);
        QueueEntry b = entry(2, 3000, 10);
        QueueEntry c = entry(3, 1000, 20);
        assertTrue(queue.add(a));
        assertTrue(queue.add(b));
        assertTrue(queue.add(c));
        assertFalse(queue.add(entry(1, 1000, 30)), "a player waits once");

        List<MatchProposal> matches = queue.poll(0);
        assertEquals(1, matches.size());
        assertEquals(Arrays.asList(Arrays.asList(a), Arrays.asList(b)), matches.get(0).teams());
        assertEquals(Arrays.asList(c), queue.entries());
        assertTrue(queue.poll(1_000_000).isEmpty(), "one player alone never starts a 1v1");
    }

    @Test
    void unrankedDealsTeamsInTurn() {
        ModeQueue queue = new ModeQueue(new GameMode("bw-2v2", "Bedwars 2v2", 2, 2, false), WINDOW);
        for (int i = 1; i <= 4; i++) {
            queue.add(entry(i, 1000, i));
        }
        MatchProposal match = queue.poll(0).get(0);
        assertEquals(Arrays.asList(new UUID(0, 1), new UUID(0, 3)), players(match.teams().get(0)));
        assertEquals(Arrays.asList(new UUID(0, 2), new UUID(0, 4)), players(match.teams().get(1)));
        assertEquals(0, queue.size());
    }

    @Test
    void soloModesStartAlone() {
        ModeQueue queue = new ModeQueue(new GameMode("pont", "Pont", 1, 1, false), WINDOW);
        queue.add(entry(1, 0, 0));
        queue.add(entry(2, 0, 0));
        assertEquals(2, queue.poll(0).size());
    }

    @Test
    void rankedWaitsForCloseRatings() {
        ModeQueue queue = new ModeQueue(new GameMode("ranked", "Ranked", 2, 1, true), WINDOW);
        QueueEntry low = entry(1, 1000, 0);
        QueueEntry high = entry(2, 1200, 0);
        queue.add(low);
        queue.add(high);

        assertTrue(queue.poll(0).isEmpty(), "200 apart, the window is 100");
        assertTrue(queue.poll(19_999).isEmpty(), "still 150 after 10 s");
        List<MatchProposal> matches = queue.poll(20_000);
        assertEquals(1, matches.size(), "200 after 20 s");
        assertEquals(0, queue.size());
    }

    @Test
    void rankedTakesTheClosestRatingForTheOldestPlayer() {
        ModeQueue queue = new ModeQueue(new GameMode("ranked", "Ranked", 2, 1, true), WINDOW);
        QueueEntry oldest = entry(1, 1000, 0);
        QueueEntry far = entry(2, 1090, 1);
        QueueEntry close = entry(3, 1010, 2);
        queue.add(oldest);
        queue.add(far);
        queue.add(close);

        MatchProposal match = queue.poll(5).get(0);
        assertEquals(Arrays.asList(oldest.player(), close.player()), sortedByRatingDesc(match));
        assertEquals(Arrays.asList(far), queue.entries());
    }

    private static List<UUID> sortedByRatingDesc(MatchProposal match) {
        List<QueueEntry> all = match.entries();
        all.sort((x, y) -> Integer.compare(x.rating(), y.rating()));
        return players(all);
    }

    @Test
    void rankedWindowMustSuitBothPlayers() {
        ModeQueue queue = new ModeQueue(new GameMode("ranked", "Ranked", 2, 1, true), WINDOW);
        // The first one waited a minute (window 400), the newcomer just arrived (window 100).
        queue.add(entry(1, 1000, 0));
        queue.add(entry(2, 1300, 60_000));
        assertTrue(queue.poll(60_000).isEmpty());
        assertEquals(1, queue.poll(100_000).size(), "300 apart once the newcomer waited 40 s");
    }

    @Test
    void rankedBalancesTeamsByRating() {
        ModeQueue queue = new ModeQueue(new GameMode("ranked-2v2", "Ranked 2v2", 2, 2, true), WINDOW);
        queue.add(entry(1, 1000, 0));
        queue.add(entry(2, 1030, 0));
        queue.add(entry(3, 1060, 0));
        queue.add(entry(4, 1090, 0));
        MatchProposal match = queue.poll(0).get(0);
        // Snake draft: 1090 and 1000 against 1060 and 1030.
        assertEquals(Arrays.asList(new UUID(0, 4), new UUID(0, 1)), players(match.teams().get(0)));
        assertEquals(Arrays.asList(new UUID(0, 3), new UUID(0, 2)), players(match.teams().get(1)));
    }

    @Test
    void requeueKeepsTheOriginalPlace() {
        ModeQueue queue = new ModeQueue(new GameMode("sumo", "Sumo", 2, 1, false), WINDOW);
        QueueEntry a = entry(1, 0, 0);
        QueueEntry b = entry(2, 0, 10);
        queue.add(a);
        queue.add(b);
        MatchProposal match = queue.poll(0).get(0);
        QueueEntry late = entry(3, 0, 50);
        queue.add(late);
        queue.requeue(match.entries());
        assertEquals(Arrays.asList(a, b, late), queue.entries());
    }

    @Test
    void equalJoinTimesKeepArrivalOrder() {
        ModeQueue queue = new ModeQueue(new GameMode("sumo", "Sumo", 2, 1, false), WINDOW);
        QueueEntry a = entry(1, 0, 5);
        QueueEntry b = entry(2, 0, 5);
        QueueEntry c = entry(3, 0, 5);
        queue.add(a);
        queue.add(b);
        queue.add(c);
        assertEquals(Arrays.asList(a, b, c), queue.entries());
        assertTrue(queue.remove(b.player()));
        assertFalse(queue.remove(b.player()));
        assertEquals(Arrays.asList(a, c), queue.entries());
    }

    @Test
    void playersWhoChoseAMapOnlyPlayThere() {
        ModeQueue queue = new ModeQueue(new GameMode("first-rush-duo", "First rush duo", 1, 2, false), WINDOW);
        QueueEntry gelato = entry(1, 0, 0, "Gelato");
        QueueEntry nebuc = entry(2, 0, 1, "Nebuc");
        QueueEntry any = entry(3, 0, 2, null);
        QueueEntry nebucToo = entry(4, 0, 3, "nebuc");
        queue.add(gelato);
        queue.add(nebuc);
        queue.add(any);
        queue.add(nebucToo);

        List<MatchProposal> matches = queue.poll(0);
        assertEquals(2, matches.size());
        assertEquals(Arrays.asList(gelato.player(), any.player()), matches.get(0).players(),
                "the oldest plays with the first who takes any map");
        assertEquals("Gelato", matches.get(0).map());
        assertEquals(Arrays.asList(nebuc.player(), nebucToo.player()), matches.get(1).players());
        assertEquals("Nebuc", matches.get(1).map());
        assertEquals(0, queue.size());
    }

    @Test
    void differentMapsNeverMeet() {
        ModeQueue queue = new ModeQueue(new GameMode("sumo", "Sumo", 2, 1, false), WINDOW);
        queue.add(entry(1, 0, 0, "Gelato"));
        queue.add(entry(2, 0, 1, "Nebuc"));
        assertTrue(queue.poll(0).isEmpty());
        queue.add(entry(3, 0, 2, null));
        MatchProposal match = queue.poll(0).get(0);
        assertEquals(Arrays.asList(new UUID(0, 1), new UUID(0, 3)), match.players());
        assertEquals(Arrays.asList(new UUID(0, 2)), players(queue.entries()));
    }

    @Test
    void aSoloPlayerPlaysOnTheMapChosenOrAny() {
        ModeQueue queue = new ModeQueue(new GameMode("first-rush", "First rush", 1, 1, false), WINDOW);
        queue.add(entry(1, 0, 0, "Gelato"));
        queue.add(entry(2, 0, 1, null));
        List<MatchProposal> matches = queue.poll(0);
        assertEquals("Gelato", matches.get(0).map());
        assertNull(matches.get(1).map(), "the host chooses");
    }

    @Test
    void rankedSkipsTheClosestRatingOnAnotherMap() {
        ModeQueue queue = new ModeQueue(new GameMode("ranked", "Ranked", 2, 1, true), WINDOW);
        QueueEntry oldest = entry(1, 1000, 0, "Lectus");
        QueueEntry close = entry(2, 1005, 1, "Archway");
        QueueEntry farther = entry(3, 1050, 2, null);
        queue.add(oldest);
        queue.add(close);
        queue.add(farther);
        MatchProposal match = queue.poll(5).get(0);
        assertEquals(Arrays.asList(oldest.player(), farther.player()), sortedByRatingDesc(match));
        assertEquals("Lectus", match.map());
        assertEquals(Arrays.asList(close), queue.entries());
    }
}
