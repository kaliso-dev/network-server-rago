package gg.rago.lobby;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One player's stats, mode by mode. Main thread only. */
final class PlayerStats {

    private final UUID id;
    private String name;
    private final Map<String, ModeStats> modes = new LinkedHashMap<>();
    /** Network XP, every mode together (see {@link NetworkLevel}). */
    private int xp;

    PlayerStats(UUID id, String name) {
        this.id = id;
        this.name = name == null ? "?" : name;
    }

    UUID id() {
        return id;
    }

    /** The last name seen. */
    String name() {
        return name;
    }

    /** True if the name changed. */
    boolean rename(String newName) {
        if (newName == null || newName.equals(name)) {
            return false;
        }
        name = newName;
        return true;
    }

    int xp() {
        return xp;
    }

    void addXp(int gained) {
        xp = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) xp + gained));
    }

    /** The stats of this mode, or null if the player never played it. */
    ModeStats mode(String modeId) {
        return modes.get(modeId);
    }

    ModeStats modeOrNew(String modeId) {
        ModeStats stats = modes.get(modeId);
        if (stats == null) {
            stats = new ModeStats();
            modes.put(modeId, stats);
        }
        return stats;
    }

    Map<String, ModeStats> modes() {
        return Collections.unmodifiableMap(modes);
    }

    /** Wins of one mode, or of every mode when modeId is null. */
    int wins(StatsPeriod period, String modeId, String day, String week) {
        int total = 0;
        for (Map.Entry<String, ModeStats> entry : modes.entrySet()) {
            if (modeId == null || modeId.equals(entry.getKey())) {
                total += entry.getValue().wins(period, day, week);
            }
        }
        return total;
    }

    int games(StatsPeriod period, String modeId, String day, String week) {
        int total = 0;
        for (Map.Entry<String, ModeStats> entry : modes.entrySet()) {
            if (modeId == null || modeId.equals(entry.getKey())) {
                total += entry.getValue().games(period, day, week);
            }
        }
        return total;
    }

    /** The longest run of wins, in one mode or in any. */
    int best(StatsPeriod period, String modeId, String day, String week) {
        int best = 0;
        for (Map.Entry<String, ModeStats> entry : modes.entrySet()) {
            if (modeId == null || modeId.equals(entry.getKey())) {
                best = Math.max(best, entry.getValue().best(period, day, week));
            }
        }
        return best;
    }

    /** Kills of one mode, or of every mode when modeId is null. */
    int kills(StatsPeriod period, String modeId, String day, String week) {
        int total = 0;
        for (Map.Entry<String, ModeStats> entry : modes.entrySet()) {
            if (modeId == null || modeId.equals(entry.getKey())) {
                total += entry.getValue().kills(period, day, week);
            }
        }
        return total;
    }

    int deaths() {
        int total = 0;
        for (ModeStats stats : modes.values()) {
            total += stats.deaths;
        }
        return total;
    }

    int losses() {
        int total = 0;
        for (ModeStats stats : modes.values()) {
            total += stats.losses;
        }
        return total;
    }
}
