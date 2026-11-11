package gg.rago.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private static final GameMode DOUBLES = new GameMode("bw-2v2", "Bedwars 2v2", 2, 2, false);
    private static final UUID C = new UUID(0, 3);
    private static final UUID D = new UUID(0, 4);

    private static Map<UUID, Integer> party(UUID... players) {
        Map<UUID, Integer> ratings = new LinkedHashMap<>();
        for (UUID player : players) {
            ratings.put(player, 1000);
        }
        return ratings;
    }

    @Test
    void aPartyJoinsAndLeavesAsOne() {
        Matchmaker matchmaker = new Matchmaker(Arrays.asList(SUMO, DOUBLES), RatingWindow.DEFAULT);
        assertEquals(Matchmaker.JoinResult.PARTY_TOO_BIG, matchmaker.joinParty(A, party(A, B), "sumo", 0, null));
        assertNull(matchmaker.queueOf(A));
        matchmaker.join(B, "sumo", 1000, 0);
        assertEquals(Matchmaker.JoinResult.SWITCHED, matchmaker.joinParty(A, party(A, B), "bw-2v2", 1, null));
        assertEquals(0, matchmaker.waiting("sumo"));
        assertEquals(Arrays.asList(A, B), matchmaker.partyOf(B));
        assertEquals(Matchmaker.JoinResult.ALREADY_WAITING, matchmaker.joinParty(A, party(A, B), "bw-2v2", 2, null));
        assertEquals(Matchmaker.JoinResult.SWITCHED, matchmaker.joinParty(A, party(A, B), "bw-2v2", 3, "Lotus"));
        assertEquals(1, matchmaker.entryOf(B).joinedAtMillis(), "the same place in the queue");
        assertEquals(2, matchmaker.waitingFor("bw-2v2", "lotus"));
        assertEquals(DOUBLES, matchmaker.leave(B));
        assertNull(matchmaker.queueOf(A), "the party left together");
        assertEquals(0, matchmaker.waitingTotal());
    }

    @Test
    void aPlayerOfAPartyQueueingAloneBreaksTheParty() {
        Matchmaker matchmaker = new Matchmaker(Arrays.asList(SUMO, DOUBLES), RatingWindow.DEFAULT);
        matchmaker.joinParty(A, party(A, B), "bw-2v2", 0, null);
        assertEquals(Matchmaker.JoinResult.SWITCHED, matchmaker.join(B, "bw-2v2", 1000, 5));
        assertNull(matchmaker.queueOf(A));
        assertEquals(Arrays.asList(B), matchmaker.partyOf(B));
        assertNull(matchmaker.entryOf(B).party());
    }

    @Test
    void aPartyComesBackWholeOrNotAtAll() {
        Matchmaker matchmaker = new Matchmaker(Arrays.asList(SUMO, DOUBLES), RatingWindow.DEFAULT);
        matchmaker.joinParty(A, party(A, B), "bw-2v2", 0, null);
        matchmaker.joinParty(C, party(C, D), "bw-2v2", 1, null);
        MatchProposal match = matchmaker.poll(2).get(0);
        assertEquals(Arrays.asList(A, B, C, D), match.players());
        matchmaker.join(D, "sumo", 1000, 3);
        matchmaker.requeue(match);
        assertEquals(Arrays.asList(A, B), matchmaker.partyOf(A));
        assertNull(matchmaker.entryOf(C), "without D, C's party does not come back");
        assertEquals(SUMO, matchmaker.queueOf(D));
    }
}
