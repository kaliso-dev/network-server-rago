package gg.rago.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every queue of the network, with one rule across them: a player waits in one queue at most.
 * Joining another queue leaves the first one. A party waits as one: it joins together
 * ({@link #joinParty}), and one of its players leaving takes the whole party out of the queue. Not
 * thread-safe (see {@link ModeQueue}).
 */
public final class Matchmaker {

    /** What happened when a player asked to join a queue. */
    public enum JoinResult {
        /** The player now waits in this queue. */
        JOINED,
        /** The player left another queue for this one. */
        SWITCHED,
        /** The player was already waiting in this queue: nothing changed. */
        ALREADY_WAITING,
        /** No queue has this id. */
        UNKNOWN_MODE,
        /** The party has more players than a team of this mode. */
        PARTY_TOO_BIG
    }

    private final Map<String, ModeQueue> queues = new LinkedHashMap<>();
    private final Map<UUID, String> queueOf = new HashMap<>();

    public Matchmaker(Collection<GameMode> modes, RatingWindow window) {
        for (GameMode mode : modes) {
            if (queues.put(mode.id(), new ModeQueue(mode, window)) != null) {
                throw new IllegalArgumentException("mode " + mode.id() + " is listed twice");
            }
        }
    }

    /** Joins this mode's queue, on any map. */
    public JoinResult join(UUID player, String modeId, int rating, long nowMillis) {
        return join(player, modeId, rating, nowMillis, null);
    }

    /**
     * Joins this mode's queue for this map (null: any map). Choosing another map of the mode one
     * waits for keeps the place in the queue (SWITCHED).
     */
    public JoinResult join(UUID player, String modeId, int rating, long nowMillis, String map) {
        ModeQueue queue = queues.get(modeId);
        if (queue == null) {
            return JoinResult.UNKNOWN_MODE;
        }
        String current = queueOf.get(player);
        if (modeId.equals(current)) {
            QueueEntry waiting = queue.find(player);
            if (waiting.party() == null) {
                if (QueueEntry.sameMap(waiting.map(), map)) {
                    return JoinResult.ALREADY_WAITING;
                }
                queue.remove(player);
                queue.add(waiting.onMap(map));
                return JoinResult.SWITCHED;
            }
        }
        if (current != null) {
            leave(player);
        }
        queue.add(new QueueEntry(player, rating, nowMillis, map));
        queueOf.put(player, modeId);
        return current == null ? JoinResult.JOINED : JoinResult.SWITCHED;
    }

    /**
     * A party joins this mode's queue for this map (null: any map): each player with their rating,
     * in the order given, all with the party's id. They will play the same match, in the same team.
     * Each player leaves the queue they were in first. The same party choosing another map of the
     * mode keeps its place (SWITCHED).
     */
    public JoinResult joinParty(UUID party, Map<UUID, Integer> ratings, String modeId, long nowMillis, String map) {
        ModeQueue queue = queues.get(modeId);
        if (queue == null) {
            return JoinResult.UNKNOWN_MODE;
        }
        if (ratings.isEmpty()) {
            throw new IllegalArgumentException("a party of nobody");
        }
        if (ratings.size() > queue.mode().playersPerTeam()) {
            return JoinResult.PARTY_TOO_BIG;
        }
        UUID first = ratings.keySet().iterator().next();
        List<QueueEntry> waiting = modeId.equals(queueOf.get(first)) ? queue.withParty(first) : new ArrayList<>();
        if (samePlayers(waiting, ratings.keySet()) && party.equals(waiting.get(0).party())) {
            if (QueueEntry.sameMap(waiting.get(0).map(), map)) {
                return JoinResult.ALREADY_WAITING;
            }
            queue.removeWithParty(first);
            for (QueueEntry entry : waiting) {
                queue.add(entry.onMap(map));
            }
            return JoinResult.SWITCHED;
        }
        boolean switched = false;
        for (UUID player : ratings.keySet()) {
            switched |= leave(player) != null;
        }
        for (Map.Entry<UUID, Integer> player : ratings.entrySet()) {
            queue.add(new QueueEntry(player.getKey(), player.getValue(), nowMillis, map, party));
            queueOf.put(player.getKey(), modeId);
        }
        return switched ? JoinResult.SWITCHED : JoinResult.JOINED;
    }

    private static boolean samePlayers(List<QueueEntry> entries, Collection<UUID> players) {
        if (entries.size() != players.size()) {
            return false;
        }
        for (QueueEntry entry : entries) {
            if (!players.contains(entry.player())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Leaves whatever queue the player is in, with their whole party if they queued with one.
     * Returns the mode left, or null.
     */
    public GameMode leave(UUID player) {
        String current = queueOf.get(player);
        if (current == null) {
            return null;
        }
        ModeQueue queue = queues.get(current);
        for (QueueEntry entry : queue.removeWithParty(player)) {
            queueOf.remove(entry.player());
        }
        queueOf.remove(player);
        return queue.mode();
    }

    /** The players waiting with this one (this one included), in the order they joined; empty if not waiting. */
    public List<UUID> partyOf(UUID player) {
        String current = queueOf.get(player);
        List<UUID> party = new ArrayList<>();
        if (current != null) {
            for (QueueEntry entry : queues.get(current).withParty(player)) {
                party.add(entry.player());
            }
        }
        return party;
    }

    /** The mode whose queue the player is in, or null. */
    public GameMode queueOf(UUID player) {
        String current = queueOf.get(player);
        return current == null ? null : queues.get(current).mode();
    }

    /** The player's entry in their queue, or null. */
    public QueueEntry entryOf(UUID player) {
        String current = queueOf.get(player);
        return current == null ? null : queues.get(current).find(player);
    }

    public int waiting(String modeId) {
        ModeQueue queue = queues.get(modeId);
        return queue == null ? 0 : queue.size();
    }

    /** How many players wait in this mode's queue for this very map (not those who take any map). */
    public int waitingFor(String modeId, String map) {
        ModeQueue queue = queues.get(modeId);
        int count = 0;
        if (queue != null && map != null) {
            for (QueueEntry entry : queue.entries()) {
                if (map.equalsIgnoreCase(entry.map())) {
                    count++;
                }
            }
        }
        return count;
    }

    public int waitingTotal() {
        return queueOf.size();
    }

    /**
     * Puts the players of a match that could not start back in its queue, with their original
     * waiting times. A player who joined another queue in the meantime stays there, and their party
     * does not come back without them.
     */
    public void requeue(MatchProposal match) {
        requeue(match.mode().id(), match.entries());
    }

    /**
     * Puts these entries back in this mode's queue, at their place: those of the players who wait
     * nowhere, each party only when all of its players given here do.
     */
    public void requeue(String modeId, Collection<QueueEntry> entries) {
        ModeQueue queue = queues.get(modeId);
        if (queue == null) {
            return;
        }
        Map<UUID, List<QueueEntry>> units = new LinkedHashMap<>();
        for (QueueEntry entry : entries) {
            List<QueueEntry> unit = units.get(entry.unit());
            if (unit == null) {
                unit = new ArrayList<>();
                units.put(entry.unit(), unit);
            }
            unit.add(entry);
        }
        for (List<QueueEntry> unit : units.values()) {
            boolean free = true;
            for (QueueEntry entry : unit) {
                free &= !queueOf.containsKey(entry.player());
            }
            if (!free) {
                continue;
            }
            for (QueueEntry entry : unit) {
                queue.add(entry);
                queueOf.put(entry.player(), modeId);
            }
        }
    }

    /** Takes out of every queue the matches that can start now. */
    public List<MatchProposal> poll(long nowMillis) {
        List<MatchProposal> matches = new ArrayList<>();
        for (ModeQueue queue : queues.values()) {
            for (MatchProposal match : queue.poll(nowMillis)) {
                for (UUID player : match.players()) {
                    queueOf.remove(player);
                }
                matches.add(match);
            }
        }
        return matches;
    }
}
