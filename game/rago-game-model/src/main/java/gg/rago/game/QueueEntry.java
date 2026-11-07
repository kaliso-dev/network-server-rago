package gg.rago.game;

import java.util.Objects;
import java.util.UUID;

/** A player waiting in a queue: who, with which rating, since when (milliseconds). Immutable. */
public final class QueueEntry {

    private final UUID player;
    private final int rating;
    private final long joinedAtMillis;

    public QueueEntry(UUID player, int rating, long joinedAtMillis) {
        this.player = Objects.requireNonNull(player, "player");
        this.rating = rating;
        this.joinedAtMillis = joinedAtMillis;
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

    @Override
    public String toString() {
        return player + "(" + rating + ")";
    }
}
