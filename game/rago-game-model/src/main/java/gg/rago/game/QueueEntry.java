package gg.rago.game;

import java.util.Objects;
import java.util.UUID;

/**
 * A player waiting in a queue: who, with which rating, since when (milliseconds), and the map they
 * chose (null: any map). Immutable.
 */
public final class QueueEntry {

    private final UUID player;
    private final int rating;
    private final long joinedAtMillis;
    private final String map;

    public QueueEntry(UUID player, int rating, long joinedAtMillis) {
        this(player, rating, joinedAtMillis, null);
    }

    public QueueEntry(UUID player, int rating, long joinedAtMillis, String map) {
        this.player = Objects.requireNonNull(player, "player");
        this.rating = rating;
        this.joinedAtMillis = joinedAtMillis;
        this.map = map == null || map.isEmpty() ? null : map;
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
        return player + "(" + rating + (map == null ? "" : ", " + map) + ")";
    }
}
