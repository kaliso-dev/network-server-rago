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
 * Joining another queue leaves the first one. Not thread-safe (see {@link ModeQueue}).
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
        UNKNOWN_MODE
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

    public JoinResult join(UUID player, String modeId, int rating, long nowMillis) {
        ModeQueue queue = queues.get(modeId);
        if (queue == null) {
            return JoinResult.UNKNOWN_MODE;
        }
        String current = queueOf.get(player);
        if (modeId.equals(current)) {
            return JoinResult.ALREADY_WAITING;
        }
        if (current != null) {
            queues.get(current).remove(player);
        }
        queue.add(new QueueEntry(player, rating, nowMillis));
        queueOf.put(player, modeId);
        return current == null ? JoinResult.JOINED : JoinResult.SWITCHED;
    }

    /** Leaves whatever queue the player is in. Returns the mode left, or null. */
    public GameMode leave(UUID player) {
        String current = queueOf.remove(player);
        if (current == null) {
            return null;
        }
        ModeQueue queue = queues.get(current);
        queue.remove(player);
        return queue.mode();
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

    public int waitingTotal() {
        return queueOf.size();
    }

    /**
     * Puts the players of a match that could not start back in its queue, with their original
     * waiting times. A player who joined another queue in the meantime stays there.
     */
    public void requeue(MatchProposal match) {
        ModeQueue queue = queues.get(match.mode().id());
        if (queue == null) {
            return;
        }
        for (QueueEntry entry : match.entries()) {
            if (!queueOf.containsKey(entry.player())) {
                queue.add(entry);
                queueOf.put(entry.player(), match.mode().id());
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
