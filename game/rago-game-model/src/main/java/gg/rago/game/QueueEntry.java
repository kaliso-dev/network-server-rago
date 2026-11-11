package gg.rago.game;

import java.util.Objects;
import java.util.UUID;

/**
 * A player waiting in a queue: who, with which rating, since when (milliseconds), the map they
 * chose (null: any map), and the party they queue with (null: alone). The players of a party play
 * the same match, in the same team. Immutable.
 */
public final class QueueEntry {

    private final UUID player;
    private final int rating;
    private final long joinedAtMillis;
    private final String map;
    private final UUID party;

    public QueueEntry(UUID player, int rating, long joinedAtMillis) {
        this(player, rating, joinedAtMillis, null);
    }

    public QueueEntry(UUID player, int rating, long joinedAtMillis, String map) {
        this(player, rating, joinedAtMillis, map, null);
    }

    /**
     * A player of a party: the entries of one party share its id (its leader's id, for example),
     * their join time and their map.
     */
    public QueueEntry(UUID player, int rating, long joinedAtMillis, String map, UUID party) {
        this.player = Objects.requireNonNull(player, "player");
        this.rating = rating;
        this.joinedAtMillis = joinedAtMillis;
        this.map = map == null || map.isEmpty() ? null : map;
        this.party = party;
    }

    public UUID player() {
        return player;
    }

    public int rating() {
        return rating;
    }

    public long joinedAtMillis() {
        return joinedAtMillis;
    }

    public long waitedMillis(long nowMillis) {
        return Math.max(0L, nowMillis - joinedAtMillis);
    }

    /** The map the player chose, or null for any map. */
    public String map() {
        return map;
    }

    /** The party the player queues with, or null: alone. */
    public UUID party() {
        return party;
    }

    /** What the queue matches as one: the party, or the player alone. */
    UUID unit() {
        return party == null ? player : party;
    }

    /** The same entry for another map (null: any map), at the same place. */
    QueueEntry onMap(String other) {
        return new QueueEntry(player, rating, joinedAtMillis, other, party);
    }

    /** True if this player can play on this map: the one they chose, or any if they chose none (null: any map). */
    public boolean accepts(String chosen) {
        return map == null || chosen == null || map.equalsIgnoreCase(chosen);
    }

    /** The same choice of map: both null, or the same name whatever the case. */
    static boolean sameMap(String one, String other) {
        return one == null ? other == null : one.equalsIgnoreCase(other);
    }

    @Override
    public String toString() {
        return player + "(" + rating + (map == null ? "" : ", " + map) + (party == null ? "" : ", party " + party) + ")";
    }
}
