package gg.rago.lobby;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The ranked queues closed for a while to a player who left a ranked game they still played (a
 * forfeit: /spawn, Play again, leaving the server), as on the ranked servers: the game counts as
 * lost, and the queues of the ranked modes stay closed to them for file.abandon-classe-minutes
 * (5 by default, 0 for never). Only a game that moved the ELO: not a /duel, nor a party's private
 * game. In memory: a restart opens them again. Main thread only.
 */
final class RankedBans {

    /** The longest it can be set to. */
    static final int MAX_MINUTES = 60;

    private final long millis;
    /** When the ranked queues open again for each player. */
    private final Map<UUID, Long> until = new HashMap<>();

    RankedBans(int minutes) {
        this.millis = Math.max(0, Math.min(MAX_MINUTES, minutes)) * 60_000L;
    }

    /** The minutes without the ranked queues after a forfeit; 0: never closed. */
    int minutes() {
        return (int) (millis / 60_000L);
    }

    /** The player forfeited a ranked game now: the ranked queues are closed to them for the minutes. */
    void forfeited(UUID player, long now) {
        if (millis <= 0) {
            return;
        }
        until.put(player, now + millis);
        // the ones over go, so that the map never grows with players who left long ago
        Iterator<Long> ends = until.values().iterator();
        while (ends.hasNext()) {
            if (ends.next() <= now) {
                ends.remove();
            }
        }
    }

    /** The milliseconds before the player may join a ranked queue again; 0 if they may now. */
    long left(UUID player, long now) {
        Long end = until.get(player);
        if (end == null) {
            return 0;
        }
        if (end <= now) {
            until.remove(player);
            return 0;
        }
        return end - now;
    }

    /** "4:32", rounded up: the time before the ranked queues open again. */
    static String clock(long millis) {
        long seconds = (Math.max(0, millis) + 999) / 1000;
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
