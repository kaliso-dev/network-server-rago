package gg.rago.practice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Network XP of the trainings and the free arenas, where nobody wins a game: a little for
 * each minute played, a little for each attempt, more for each success, never more than a cap a
 * minute (falling on purpose earns no more than playing). The XP waits here and goes to the
 * lobby every two minutes and when the player leaves, in one message.
 */
final class PracticeXp {

    /** What the settings give (section xp-entrainement). */
    static final class Rules {
        final int perMinute;
        final int perAttempt;
        final int perSuccess;
        final int maxPerMinute;

        Rules(int perMinute, int perAttempt, int perSuccess, int maxPerMinute) {
            this.perMinute = perMinute;
            this.perAttempt = perAttempt;
            this.perSuccess = perSuccess;
            this.maxPerMinute = maxPerMinute;
        }
    }

    /** The window of the cap. */
    static final long MINUTE_MS = 60_000L;

    private static final class Pending {
        String modeId;
        int xp;
        long windowStart;
        int inWindow;
    }

    private final Rules rules;
    private final Map<UUID, Pending> pending = new HashMap<>();

    PracticeXp(Rules rules) {
        this.rules = rules;
    }

    Rules rules() {
        return rules;
    }

    /** A minute played in this mode. */
    int minute(UUID player, String modeId, long now) {
        return add(player, modeId, rules.perMinute, now);
    }

    /** An attempt that failed. */
    int attempt(UUID player, String modeId, long now) {
        return add(player, modeId, rules.perAttempt, now);
    }

    /** An attempt that worked (a score). */
    int success(UUID player, String modeId, long now) {
        return add(player, modeId, rules.perSuccess, now);
    }

    /** Adds XP within the cap of the minute; returns what was kept. */
    int add(UUID player, String modeId, int xp, long now) {
        if (player == null || xp <= 0) {
            return 0;
        }
        Pending p = pending.get(player);
        if (p == null) {
            p = new Pending();
            p.windowStart = now;
            pending.put(player, p);
        }
        if (now - p.windowStart >= MINUTE_MS) {
            p.windowStart = now;
            p.inWindow = 0;
        }
        int kept = Math.max(0, Math.min(xp, rules.maxPerMinute - p.inWindow));
        p.inWindow += kept;
        p.xp += kept;
        p.modeId = modeId;
        return kept;
    }

    /** The XP waiting for this player, now taken (0 if none). */
    int take(UUID player) {
        Pending p = pending.get(player);
        if (p == null) {
            return 0;
        }
        int xp = p.xp;
        p.xp = 0;
        return xp;
    }

    /** The mode the last XP of this player came from (the lobby counts it there), or null. */
    String modeOf(UUID player) {
        Pending p = pending.get(player);
        return p == null ? null : p.modeId;
    }

    /** The players with XP waiting. */
    List<UUID> waiting() {
        List<UUID> players = new ArrayList<>();
        for (Map.Entry<UUID, Pending> entry : pending.entrySet()) {
            if (entry.getValue().xp > 0) {
                players.add(entry.getKey());
            }
        }
        return players;
    }

    /** Forgets the players with nothing waiting whose minute is over (the cap no longer runs). */
    void prune(long now) {
        pending.values().removeIf(p -> p.xp == 0 && now - p.windowStart >= MINUTE_MS);
    }
}
