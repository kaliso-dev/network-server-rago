package gg.rago.lobby;

/**
 * One player's stats in one mode: ever, and for the current day and week (the counters of a day
 * that is over count as zero, and start again at the next game). Main thread only.
 */
final class ModeStats {

    int wins;
    int losses;
    /** Games without a winner (the time ran out): played, neither won nor lost. */
    int draws;
    /** Wins in a row, up to now. */
    int streak;
    int bestStreak;
    /** The ELO of a ranked mode; 0 before the first ranked game. */
    int elo;
    /** The highest ELO of the season in this mode. */
    int peakElo;
    int kills;
    int deaths;
    /** Bed Wars: the final kills, the final deaths (out of the game, the bed gone) and the beds broken, ever. */
    int finalKills;
    int finalDeaths;
    int beds;

    String day = "";
    int dayWins;
    int dayGames;
    int dayBest;
    int dayKills;
    /**
     * The losses, the final kills, the final deaths and the ELO won (lost, under 0) of the day, for
     * the leaderboards of the day of the selector (counted since the update that brought them).
     */
    int dayLosses;
    int dayFinalKills;
    int dayFinalDeaths;
    int dayElo;

    String week = "";
    int weekWins;
    int weekGames;
    int weekBest;
    int weekKills;
    /** The same of the week. */
    int weekLosses;
    int weekFinalKills;
    int weekFinalDeaths;
    int weekElo;

    /** One game, won or lost, played on this day of this week. */
    void record(boolean won, String dayKey, String weekKey) {
        record(won, dayKey, weekKey, true);
    }

    /** A game: without streaks (a /duel), the win or loss counts but the streaks stay as they are. */
    void record(boolean won, String dayKey, String weekKey, boolean streaks) {
        roll(dayKey, weekKey);
        dayGames++;
        weekGames++;
        if (won) {
            wins++;
            dayWins++;
            weekWins++;
            if (streaks) {
                streak++;
                bestStreak = Math.max(bestStreak, streak);
                dayBest = Math.max(dayBest, streak);
                weekBest = Math.max(weekBest, streak);
            }
        } else {
            losses++;
            dayLosses++;
            weekLosses++;
            if (streaks) {
                streak = 0;
            }
        }
    }

    /** A Bed Wars final kill of the player, on this day of this week. */
    void finalKill(String dayKey, String weekKey) {
        roll(dayKey, weekKey);
        finalKills++;
        dayFinalKills++;
        weekFinalKills++;
    }

    /** A Bed Wars final death of the player (out of the game, the bed gone), on this day of this week. */
    void finalDeath(String dayKey, String weekKey) {
        roll(dayKey, weekKey);
        finalDeaths++;
        dayFinalDeaths++;
        weekFinalDeaths++;
    }

    /** The ELO of a ranked game: this many points won (under 0: lost), on this day of this week. */
    void eloChange(int change, String dayKey, String weekKey) {
        roll(dayKey, weekKey);
        dayElo += change;
        weekElo += change;
    }

    int losses(StatsPeriod period, String dayKey, String weekKey) {
        switch (period) {
            case DAY:
                return dayKey.equals(day) ? dayLosses : 0;
            case WEEK:
                return weekKey.equals(week) ? weekLosses : 0;
            default:
                return losses;
        }
    }

    int finalKills(StatsPeriod period, String dayKey, String weekKey) {
        switch (period) {
            case DAY:
                return dayKey.equals(day) ? dayFinalKills : 0;
            case WEEK:
                return weekKey.equals(week) ? weekFinalKills : 0;
            default:
                return finalKills;
        }
    }

    int finalDeaths(StatsPeriod period, String dayKey, String weekKey) {
        switch (period) {
            case DAY:
                return dayKey.equals(day) ? dayFinalDeaths : 0;
            case WEEK:
                return weekKey.equals(week) ? weekFinalDeaths : 0;
            default:
                return finalDeaths;
        }
    }

    /** The ELO won over the day or the week (under 0: lost); for {@link StatsPeriod#ALL}, the ELO itself. */
    int elo(StatsPeriod period, String dayKey, String weekKey) {
        switch (period) {
            case DAY:
                return dayKey.equals(day) ? dayElo : 0;
            case WEEK:
                return weekKey.equals(week) ? weekElo : 0;
            default:
                return elo;
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

    /** A game without a winner, on this day of this week: a game played; the streaks stay as they are. */
    void draw(String dayKey, String weekKey) {
        roll(dayKey, weekKey);
        dayGames++;
        weekGames++;
        draws++;
    }

    /** Every game played: won, lost or drawn. */
    int games() {
        return wins + losses + draws;
    }

    /** The games won or lost: the ELO of a ranked mode is the player's own from the first one. */
    int decided() {
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
            dayLosses = 0;
            dayFinalKills = 0;
            dayFinalDeaths = 0;
            dayElo = 0;
        }
        if (!weekKey.equals(week)) {
            week = weekKey;
            weekWins = 0;
            weekGames = 0;
            weekBest = 0;
            weekKills = 0;
            weekLosses = 0;
            weekFinalKills = 0;
            weekFinalDeaths = 0;
            weekElo = 0;
        }
    }
}
