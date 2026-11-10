package gg.rago.lobby;

/**
 * One player's stats in one mode: ever, and for the current day and week (the counters of a day
 * that is over count as zero, and start again at the next game). Main thread only.
 */
final class ModeStats {

    int wins;
    int losses;
    /** Wins in a row, up to now. */
    int streak;
    int bestStreak;
    /** The ELO of a ranked mode; 0 before the first ranked game. */
    int elo;
    int kills;
    int deaths;
    /** Bed Wars: the final kills and the beds broken, ever. */
    int finalKills;
    int beds;

    String day = "";
    int dayWins;
    int dayGames;
    int dayBest;
    int dayKills;

    String week = "";
    int weekWins;
    int weekGames;
    int weekBest;
    int weekKills;

    /** One game, won or lost, played on this day of this week. */
    void record(boolean won, String dayKey, String weekKey) {
        roll(dayKey, weekKey);
        dayGames++;
        weekGames++;
        if (won) {
            wins++;
            dayWins++;
            weekWins++;
            streak++;
            bestStreak = Math.max(bestStreak, streak);
            dayBest = Math.max(dayBest, streak);
            weekBest = Math.max(weekBest, streak);
        } else {
            losses++;
            streak = 0;
        }
    }

    /** The player killed someone (in a sumo: knocked them off), on this day of this week. */
    void kill(String dayKey, String weekKey) {
        roll(dayKey, weekKey);
        kills++;
        dayKills++;
        weekKills++;
    }

    void death() {
        deaths++;
    }

    int games() {
        return wins + losses;
    }

    int kills(StatsPeriod period, String dayKey, String weekKey) {
        switch (period) {
            case DAY:
                return dayKey.equals(day) ? dayKills : 0;
            case WEEK:
                return weekKey.equals(week) ? weekKills : 0;
            default:
                return kills;
        }
    }

    boolean rated() {
        return elo > 0;
    }

    int wins(StatsPeriod period, String dayKey, String weekKey) {
        switch (period) {
            case DAY:
                return dayKey.equals(day) ? dayWins : 0;
            case WEEK:
                return weekKey.equals(week) ? weekWins : 0;
            default:
                return wins;
        }
    }

    int games(StatsPeriod period, String dayKey, String weekKey) {
        switch (period) {
            case DAY:
                return dayKey.equals(day) ? dayGames : 0;
            case WEEK:
                return weekKey.equals(week) ? weekGames : 0;
            default:
                return games();
        }
    }

    int best(StatsPeriod period, String dayKey, String weekKey) {
        switch (period) {
            case DAY:
                return dayKey.equals(day) ? dayBest : 0;
            case WEEK:
                return weekKey.equals(week) ? weekBest : 0;
            default:
                return bestStreak;
        }
    }

    private void roll(String dayKey, String weekKey) {
        if (!dayKey.equals(day)) {
            day = dayKey;
            dayWins = 0;
            dayGames = 0;
            dayBest = 0;
            dayKills = 0;
        }
        if (!weekKey.equals(week)) {
            week = weekKey;
            weekWins = 0;
            weekGames = 0;
            weekBest = 0;
            weekKills = 0;
        }
    }
}
