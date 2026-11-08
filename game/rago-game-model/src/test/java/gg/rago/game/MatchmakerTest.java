package gg.rago.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchmakerTest {

    private static final GameMode SUMO = new GameMode("sumo", "Sumo", 2, 1, false);
    private static final GameMode PONT = new GameMode("pont", "Pont", 1, 1, false);
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    private static Matchmaker matchmaker() {
        return new Matchmaker(Arrays.asList(SUMO, PONT), RatingWindow.DEFAULT);
    }

    @Test
    void aPlayerWaitsInOneQueueAtMost() {
        Matchmaker matchmaker = matchmaker();
        assertEquals(Matchmaker.JoinResult.JOINED, matchmaker.join(A, "sumo", 1000, 0));
        assertEquals(Matchmaker.JoinResult.ALREADY_WAITING, matchmaker.join(A, "sumo", 1000, 5));
        assertEquals(0, matchmaker.entryOf(A).joinedAtMillis(), "joining again keeps the place");
        assertEquals(Matchmaker.JoinResult.SWITCHED, matchmaker.join(A, "pont", 1000, 10));
        assertEquals(0, matchmaker.waiting("sumo"));
        assertEquals(1, matchmaker.waiting("pont"));
        assertEquals(PONT, matchmaker.queueOf(A));
        assertEquals(Matchmaker.JoinResult.UNKNOWN_MODE, matchmaker.join(A, "nope", 1000, 10));
        assertEquals(PONT, matchmaker.queueOf(A));
    }

    @Test
    void leaving() {
        Matchmaker matchmaker = matchmaker();
        matchmaker.join(A, "sumo", 1000, 0);
        assertEquals(SUMO, matchmaker.leave(A));
        assertNull(matchmaker.leave(A));
        assertNull(matchmaker.queueOf(A));
        assertEquals(0, matchmaker.waitingTotal());
    }

    @Test
    void pollTakesMatchedPlayersOutOfTheirQueue() {
        Matchmaker matchmaker = matchmaker();
        matchmaker.join(A, "sumo", 1000, 0);
        matchmaker.join(B, "sumo", 1000, 1);
        List<MatchProposal> matches = matchmaker.poll(2);
        assertEquals(1, matches.size());
        assertEquals(Arrays.asList(A, B), matches.get(0).players());
        assertNull(matchmaker.queueOf(A));
        assertEquals(0, matchmaker.waitingTotal());

        matchmaker.requeue(matches.get(0));
        assertEquals(SUMO, matchmaker.queueOf(A));
        assertEquals(2, matchmaker.waiting("sumo"));
    }

    @Test
    void requeueLeavesAPlayerWhoMovedOn() {
        Matchmaker matchmaker = matchmaker();
        matchmaker.join(A, "sumo", 1000, 0);
        matchmaker.join(B, "sumo", 1000, 1);
        MatchProposal match = matchmaker.poll(2).get(0);
        matchmaker.join(B, "pont", 1000, 3);
        matchmaker.requeue(match);
        assertEquals(SUMO, matchmaker.queueOf(A));
        assertEquals(PONT, matchmaker.queueOf(B));
        assertEquals(1, matchmaker.waiting("sumo"));
    }

    @Test
    void choosingAnotherMapKeepsThePlace() {
        Matchmaker matchmaker = matchmaker();
        assertEquals(Matchmaker.JoinResult.JOINED, matchmaker.join(A, "sumo", 1000, 0, "Gelato"));
        assertEquals(Matchmaker.JoinResult.ALREADY_WAITING, matchmaker.join(A, "sumo", 1000, 5, "gelato"));
        assertEquals(1, matchmaker.waitingFor("sumo", "Gelato"));
        assertEquals(Matchmaker.JoinResult.SWITCHED, matchmaker.join(A, "sumo", 1000, 10, "Nebuc"));
        assertEquals("Nebuc", matchmaker.entryOf(A).map());
        assertEquals(0, matchmaker.entryOf(A).joinedAtMillis(), "the same place in the queue");
        assertEquals(0, matchmaker.waitingFor("sumo", "Gelato"));
        assertEquals(Matchmaker.JoinResult.SWITCHED, matchmaker.join(A, "sumo", 1000, 20));
        assertNull(matchmaker.entryOf(A).map(), "any map");
        assertEquals(1, matchmaker.waiting("sumo"));
    }
}
