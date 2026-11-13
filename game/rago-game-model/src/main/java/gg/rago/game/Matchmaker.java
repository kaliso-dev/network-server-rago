package gg.rago.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * Every queue of the network. A player alone may wait in several queues at once, at most
 * {@link #MAX_QUEUES} (a mode played alone, a training, not counted): the first match found takes
 * them out of all of them. A match of a mode played alone takes the player out of that queue only,
 * so that they keep their place in the others while they play it. A party waits as one, in one
 * queue: it joins together ({@link #joinParty}), and one of its players leaving or joining another
 * queue takes the whole party out. Not thread-safe (see {@link ModeQueue}).
 */
public final class Matchmaker {

    /** The most queues a player alone waits in at once, the modes played alone not counted. */
    public static final int MAX_QUEUES = 3;

    /** What happened when a player asked to join a queue. */
    public enum JoinResult {
        /** The player now waits in this queue, and in no other. */
        JOINED,
        /** The player now waits in this queue too, besides the ones they already waited in. */
        ALSO,
        /** The player left their party's queue for this one, or chose another map of this queue. */
        SWITCHED,
        /** The player was already waiting in this queue: nothing changed. */
        ALREADY_WAITING,
        /** The player already waits in {@link #MAX_QUEUES} queues: nothing changed. */
        TOO_MANY_QUEUES,
        /** No queue has this id. */
        UNKNOWN_MODE,
        /** The party has more players than a team of this mode. */
        PARTY_TOO_BIG
    }

    private final Map<String, ModeQueue> queues = new LinkedHashMap<>();
    /** The queues of each waiting player, in the order they joined them. */
    private final Map<UUID, List<String>> queuesOf = new HashMap<>();
    /** The priority of each player in the queues not ranked (their rank): 0 for none ({@link ModeQueue#poll(long, ToIntFunction)}). */
    private ToIntFunction<UUID> priorities = player -> 0;

    public Matchmaker(Collection<GameMode> modes, RatingWindow window) {
        for (GameMode mode : modes) {
            if (queues.put(mode.id(), new ModeQueue(mode, window)) != null) {
                throw new IllegalArgumentException("mode " + mode.id() + " is listed twice");
            }
        }
    }

    /** True for a mode played alone (one team of one player): a training, an open arena. */
    public static boolean alone(GameMode mode) {
        return mode.teams() == 1 && mode.playersPerTeam() == 1;
    }

    /** Joins this mode's queue, on any map. */
    public JoinResult join(UUID player, String modeId, int rating, long nowMillis) {
        return join(player, modeId, rating, nowMillis, null);
    }

    /**
     * Joins this mode's queue for this map (null: any map), keeping the other queues the player
     * waits in alone (ALSO); one waiting with a party leaves its queue first (SWITCHED). Choosing
     * another map of a mode one waits for keeps the place in the queue (SWITCHED).
     */
    public JoinResult join(UUID player, String modeId, int rating, long nowMillis, String map) {
        ModeQueue queue = queues.get(modeId);
        if (queue == null) {
            return JoinResult.UNKNOWN_MODE;
        }
        List<String> current = queuesOf.get(player);
        if (current != null && current.contains(modeId)) {
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
        boolean withParty = current != null && withParty(player);
        if (withParty) {
            leave(player);
        } else if (current != null && !alone(queue.mode()) && shared(current) >= MAX_QUEUES) {
            return JoinResult.TOO_MANY_QUEUES;
        }
        queue.add(new QueueEntry(player, rating, nowMillis, map));
        waitsIn(player, modeId);
        return withParty ? JoinResult.SWITCHED : current == null ? JoinResult.JOINED : JoinResult.ALSO;
    }

    /** How many of these queues are not of a mode played alone. */
    private int shared(List<String> modeIds) {
        int count = 0;
        for (String modeId : modeIds) {
            if (!alone(queues.get(modeId).mode())) {
                count++;
            }
        }
        return count;
    }

    /** True if the player waits with a party (a party waits in one queue). */
    private boolean withParty(UUID player) {
        List<String> current = queuesOf.get(player);
        if (current == null) {
            return false;
        }
        for (String modeId : current) {
            QueueEntry entry = queues.get(modeId).find(player);
            if (entry != null && entry.party() != null) {
                return true;
            }
        }
        return false;
    }

    private void waitsIn(UUID player, String modeId) {
        List<String> current = queuesOf.get(player);
        if (current == null) {
            current = new ArrayList<>();
            queuesOf.put(player, current);
        }
        if (!current.contains(modeId)) {
            current.add(modeId);
        }
    }

    /** The player no longer waits in this queue (their entry is out of it already). */
    private void forget(UUID player, String modeId) {
        List<String> current = queuesOf.get(player);
        if (current != null) {
            current.remove(modeId);
            if (current.isEmpty()) {
                queuesOf.remove(player);
            }
        }
    }

    /**
     * A party joins this mode's queue for this map (null: any map): each player with their rating,
     * in the order given, all with the party's id. They will play the same match, in the same team.
     * Each player leaves every queue they were in first. The same party choosing another map of the
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
        List<String> firstQueues = queuesOf.get(first);
        List<QueueEntry> waiting = firstQueues != null && firstQueues.contains(modeId) ? queue.withParty(first) : new ArrayList<>();
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
            waitsIn(player.getKey(), modeId);
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
     * Leaves every queue the player is in, with their whole party if they queued with one.
     * Returns the first mode left (the queue joined first), or null.
     */
    public GameMode leave(UUID player) {
        List<String> current = queuesOf.get(player);
        if (current == null) {
            return null;
        }
        GameMode first = null;
        for (String modeId : new ArrayList<>(current)) {
            GameMode left = leave(player, modeId);
            if (first == null) {
                first = left;
            }
        }
        queuesOf.remove(player);
        return first;
    }

    /** Leaves this queue only, with the whole party if the player queued with one. Returns its mode, or null if not waiting there. */
    public GameMode leave(UUID player, String modeId) {
        List<String> current = queuesOf.get(player);
        if (current == null || !current.contains(modeId)) {
            return null;
        }
        ModeQueue queue = queues.get(modeId);
        for (QueueEntry entry : queue.removeWithParty(player)) {
            forget(entry.player(), modeId);
        }
        forget(player, modeId);
        return queue.mode();
    }

    /** The players waiting with this one (this one included), in the order they joined; empty if not waiting. */
    public List<UUID> partyOf(UUID player) {
        List<String> current = queuesOf.get(player);
        List<UUID> party = new ArrayList<>();
        if (current != null) {
            for (QueueEntry entry : queues.get(current.get(0)).withParty(player)) {
                party.add(entry.player());
            }
        }
        return party;
    }

    /** The mode of the first queue the player joined among those they wait in, or null. */
    public GameMode queueOf(UUID player) {
        List<String> current = queuesOf.get(player);
        return current == null ? null : queues.get(current.get(0)).mode();
    }

    /** The modes of every queue the player waits in, in the order they joined them; empty if none. */
    public List<GameMode> queuesOf(UUID player) {
        List<GameMode> modes = new ArrayList<>();
        List<String> current = queuesOf.get(player);
        if (current != null) {
            for (String modeId : current) {
                modes.add(queues.get(modeId).mode());
            }
        }
        return modes;
    }

    /** True if the player waits in this mode's queue. */
    public boolean isWaiting(UUID player, String modeId) {
        List<String> current = queuesOf.get(player);
        return current != null && current.contains(modeId);
    }

    /** The player's entry in the first queue they joined, or null. */
    public QueueEntry entryOf(UUID player) {
        List<String> current = queuesOf.get(player);
        return current == null ? null : queues.get(current.get(0)).find(player);
    }

    /** The player's entry in this mode's queue, or null. */
    public QueueEntry entryOf(UUID player, String modeId) {
        return isWaiting(player, modeId) ? queues.get(modeId).find(player) : null;
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

    /** How many players wait, in one queue or more. */
    public int waitingTotal() {
        return queuesOf.size();
    }

    /**
     * Puts the players of a match that could not start back in its queue, with their original
     * waiting times (see {@link #requeue(String, Collection)}).
     */
    public void requeue(MatchProposal match) {
        requeue(match.mode().id(), match.entries());
    }

    /**
     * Puts these entries back in this mode's queue, at their place. A player alone comes back
     * besides the queues they joined in the meantime, unless they wait in this one again already
     * (their entry stays as it is), or in as many queues as they may. A party comes back only when
     * all of its players given here wait nowhere (a party waits in one queue).
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
                List<String> current = queuesOf.get(entry.player());
                free &= current == null || entry.party() == null && !current.contains(modeId) && !withParty(entry.player())
                        && (alone(queue.mode()) || shared(current) < MAX_QUEUES);
            }
            if (!free) {
                continue;
            }
            for (QueueEntry entry : unit) {
                queue.add(entry);
                waitsIn(entry.player(), modeId);
            }
        }
    }

    /**
     * Where each player stands in the queues: the higher first (their rank), read at each poll so
     * that a rank bought while waiting counts at once. 0 for everyone by default.
     */
    public void setPriorities(ToIntFunction<UUID> priorities) {
        this.priorities = java.util.Objects.requireNonNull(priorities, "priorities");
    }

    /**
     * Takes out of every queue the matches that can start now, a player in one match at most: the
     * first match found takes its players out of every other queue, but a match of a mode played
     * alone only out of its own (they keep their place in the others while they play it).
     */
    public List<MatchProposal> poll(long nowMillis) {
        List<MatchProposal> matches = new ArrayList<>();
        Set<UUID> busy = new HashSet<>();
        for (ModeQueue queue : queues.values()) {
            boolean alone = alone(queue.mode());
            for (MatchProposal match : queue.poll(nowMillis, priorities, busy)) {
                for (UUID player : match.players()) {
                    busy.add(player);
                    forget(player, queue.mode().id());
                    if (!alone) {
                        leave(player);
                    }
                }
                matches.add(match);
            }
        }
        return matches;
    }
}
