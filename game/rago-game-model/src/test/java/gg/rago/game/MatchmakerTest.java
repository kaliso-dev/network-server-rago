package gg.rago.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void aPlayerAloneWaitsInSeveralQueues() {
        Matchmaker matchmaker = many();
        assertEquals(Matchmaker.JoinResult.JOINED, matchmaker.join(A, "sumo", 1000, 0));
        assertEquals(Matchmaker.JoinResult.ALREADY_WAITING, matchmaker.join(A, "sumo", 1000, 5));
        assertEquals(0, matchmaker.entryOf(A).joinedAtMillis(), "joining again keeps the place");
        assertEquals(Matchmaker.JoinResult.ALSO, matchmaker.join(A, "bridge", 1000, 10));
        assertEquals(Matchmaker.JoinResult.ALSO, matchmaker.join(A, "bw-2v2", 1000, 20));
        assertEquals(1, matchmaker.waiting("sumo"), "the first queue keeps the player");
        assertEquals(Matchmaker.JoinResult.TOO_MANY_QUEUES, matchmaker.join(A, "boxing", 1000, 30));
        assertEquals(0, matchmaker.waiting("boxing"));
        assertEquals(Matchmaker.JoinResult.ALSO, matchmaker.join(A, "pont", 1000, 40), "a mode played alone is not counted");
        assertEquals(Arrays.asList(SUMO, BRIDGE, DOUBLES, PONT), matchmaker.queuesOf(A));
        assertEquals(SUMO, matchmaker.queueOf(A), "the first queue joined");
        assertEquals(1, matchmaker.waitingTotal(), "one player, however many queues");
        assertTrue(matchmaker.isWaiting(A, "bridge"));
        assertEquals(10, matchmaker.entryOf(A, "bridge").joinedAtMillis());
        assertNull(matchmaker.entryOf(A, "boxing"));

        assertEquals(BRIDGE, matchmaker.leave(A, "bridge"));
        assertNull(matchmaker.leave(A, "bridge"));
        assertFalse(matchmaker.isWaiting(A, "bridge"));
        assertEquals(Matchmaker.JoinResult.ALSO, matchmaker.join(A, "boxing", 1000, 50), "a queue left makes room");
        assertEquals(Matchmaker.JoinResult.UNKNOWN_MODE, matchmaker.join(A, "nope", 1000, 60));
        assertEquals(Arrays.asList(SUMO, DOUBLES, PONT, BOXING), matchmaker.queuesOf(A));
    }

    private static final GameMode BRIDGE = new GameMode("bridge", "Bridge", 2, 1, false);
    private static final GameMode BOXING = new GameMode("boxing", "Boxing", 2, 1, false);

    /** A mode played alone first (as a training), then four modes against others. */
    private static Matchmaker many() {
        return new Matchmaker(Arrays.asList(PONT, SUMO, BRIDGE, DOUBLES, BOXING), RatingWindow.DEFAULT);
    }

    @Test
    void theFirstMatchFoundTakesThePlayerOutOfEveryQueue() {
        Matchmaker matchmaker = many();
        matchmaker.join(A, "sumo", 1000, 0);
        matchmaker.join(A, "bridge", 1000, 0);
        matchmaker.join(B, "bridge", 1000, 1);
        matchmaker.join(C, "sumo", 1000, 2);
        List<MatchProposal> matches = matchmaker.poll(3);
        assertEquals(1, matches.size(), "A plays one match");
        assertEquals(Arrays.asList(A, C), matches.get(0).players(), "sumo, polled first");
        assertTrue(matchmaker.queuesOf(A).isEmpty(), "out of the bridge queue too");
        assertEquals(Arrays.asList(BRIDGE), matchmaker.queuesOf(B));
        assertEquals(1, matchmaker.waitingTotal());
    }

    @Test
    void aMatchPlayedAloneKeepsThePlaceInTheOtherQueues() {
        Matchmaker matchmaker = many();
        matchmaker.join(A, "sumo", 1000, 0);
        matchmaker.join(B, "sumo", 1000, 1);
        matchmaker.join(A, "pont", 1000, 2);
        List<MatchProposal> matches = matchmaker.poll(3);
        assertEquals(1, matches.size(), "one match for A in a pass, the first found");
        assertEquals(PONT, matches.get(0).mode());
        assertEquals(Arrays.asList(SUMO), matchmaker.queuesOf(A), "A plays alone and keeps their place");
        assertEquals(0, matchmaker.entryOf(A).joinedAtMillis());
        assertEquals(Arrays.asList(SUMO), matchmaker.queuesOf(B), "no match for B without A this time");

        matches = matchmaker.poll(4);
        assertEquals(1, matches.size());
        assertEquals(Arrays.asList(A, B), matches.get(0).players(), "the next pass: the sumo");
        assertEquals(0, matchmaker.waitingTotal());
    }

    @Test
    void leaving() {
        Matchmaker matchmaker = matchmaker();
        matchmaker.join(A, "sumo", 1000, 0);
        matchmaker.join(A, "pont", 1000, 1);
        assertEquals(SUMO, matchmaker.leave(A), "every queue, the first named");
        assertNull(matchmaker.leave(A));
        assertNull(matchmaker.queueOf(A));
        assertEquals(0, matchmaker.waiting("pont"));
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
    void requeueGivesBackThePlaceOfAMatchThatCouldNotStart() {
        Matchmaker matchmaker = many();
        matchmaker.join(A, "sumo", 1000, 0);
        matchmaker.join(B, "sumo", 1000, 1);
        MatchProposal match = matchmaker.poll(2).get(0);
        matchmaker.join(A, "pont", 1000, 3);
        matchmaker.join(B, "sumo", 1000, 4);
        matchmaker.requeue(match);
        assertEquals(Arrays.asList(PONT, SUMO), matchmaker.queuesOf(A), "back in the sumo, besides the queue joined since");
        assertEquals(0, matchmaker.entryOf(A, "sumo").joinedAtMillis(), "at the original place");
        assertEquals(4, matchmaker.entryOf(B, "sumo").joinedAtMillis(), "B waits there again already: as they are");
        assertEquals(2, matchmaker.waiting("sumo"));
    }

    @Test
    void requeueLeavesAPlayerWhoWaitsInAsManyQueuesAsTheyMay() {
        Matchmaker matchmaker = many();
        matchmaker.join(A, "sumo", 1000, 0);
        matchmaker.join(B, "sumo", 1000, 1);
        MatchProposal match = matchmaker.poll(2).get(0);
        matchmaker.join(B, "bridge", 1000, 3);
        matchmaker.join(B, "bw-2v2", 1000, 3);
        matchmaker.join(B, "boxing", 1000, 3);
        matchmaker.requeue(match);
        assertEquals(Arrays.asList(SUMO), matchmaker.queuesOf(A));
        assertEquals(Arrays.asList(BRIDGE, DOUBLES, BOXING), matchmaker.queuesOf(B), "three queues already");
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
        Matchmaker matchmaker = new Matchmaker(Arrays.asList(SUMO, DOUBLES, PONT), RatingWindow.DEFAULT);
        assertEquals(Matchmaker.JoinResult.PARTY_TOO_BIG, matchmaker.joinParty(A, party(A, B), "sumo", 0, null));
        assertNull(matchmaker.queueOf(A));
        matchmaker.join(B, "sumo", 1000, 0);
        matchmaker.join(B, "pont", 1000, 0);
        assertEquals(Matchmaker.JoinResult.SWITCHED, matchmaker.joinParty(A, party(A, B), "bw-2v2", 1, null));
        assertEquals(0, matchmaker.waiting("sumo"));
        assertEquals(Arrays.asList(DOUBLES), matchmaker.queuesOf(B), "a party waits in one queue");
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
        matchmaker.joinParty(A, party(A, B), "bw-2v2", 6, null);
        assertEquals(Matchmaker.JoinResult.SWITCHED, matchmaker.join(B, "sumo", 1000, 7), "not besides the party's queue");
        assertEquals(Arrays.asList(SUMO), matchmaker.queuesOf(B));
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
