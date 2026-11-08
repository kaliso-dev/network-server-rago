package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The win streak of one team in one mode (one player in a 1v1, two in a 2v2...): its wins in a
 * row, and the longest run it reached ever, during the day and during the week. A team is the
 * same players together: a player who wins with someone else starts another team's streak. Main
 * thread only.
 */
final class TeamStreak {

    private final List<UUID> players;
    int streak;
    int best;
    String day = "";
    int dayBest;
    String week = "";
    int weekBest;

    TeamStreak(Collection<UUID> players) {
        if (players.isEmpty()) {
            throw new IllegalArgumentException("a team without players");
        }
        this.players = Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(players)));
    }

    /** The same players, in any order, give the same key. */
    static String key(Collection<UUID> players) {
        StringBuilder key = new StringBuilder();
        for (UUID id : new TreeSet<>(players)) {
            key.append(key.length() == 0 ? "" : ",").append(id);
        }
        return key.toString();
    }

    String key() {
        return key(players);
    }

    /** The players, in the order of their UUIDs. */
    List<UUID> players() {
        return players;
    }

    /** The team won a game on this day of this week. */
    void win(String dayKey, String weekKey) {
        if (!dayKey.equals(day)) {
            day = dayKey;
            dayBest = 0;
        }
        if (!weekKey.equals(week)) {
            week = weekKey;
            weekBest = 0;
        }
        streak++;
        best = Math.max(best, streak);
        dayBest = Math.max(dayBest, streak);
        weekBest = Math.max(weekBest, streak);
    }

    /** The team lost: its streak ends. */
    void lose() {
        streak = 0;
    }

    /** The longest run of the period: 0 for a day or a week that is over. */
    int best(StatsPeriod period, String dayKey, String weekKey) {
        switch (period) {
            case DAY:
                return dayKey.equals(day) ? dayBest : 0;
            case WEEK:
                return weekKey.equals(week) ? weekBest : 0;
            default:
                return best;
        }
    }
}
