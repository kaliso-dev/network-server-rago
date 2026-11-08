package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every player's stats, in memory: what the games record, what the stats NPCs and the
 * leaderboards read. Rankings are kept until the next game or the next day. Main thread only.
 */
final class StatsBook {

    /** How much one ranked game moves the ELO at most. */
    static final int K_FACTOR = 32;

    /** One line of a leaderboard. */
    static final class Row {
        private final UUID id;
        private final String name;
        private final int value;

        Row(UUID id, String name, int value) {
            this.id = id;
            this.name = name;
            this.value = value;
        }

        UUID id() {
            return id;
        }

        String name() {
            return name;
        }

        int value() {
            return value;
        }
    }

    /** A leaderboard: its rows, best first, and where each player stands (1 for the first). */
    static final class Ranking {
        private final List<Row> rows;
        private final Map<UUID, Integer> positions = new HashMap<>();

        Ranking(List<Row> rows) {
            this.rows = Collections.unmodifiableList(rows);
            for (int i = 0; i < rows.size(); i++) {
                positions.put(rows.get(i).id(), i + 1);
            }
        }

        List<Row> rows() {
            return rows;
        }

        /** 1 for the first, 0 if the player is not on it. */
        int position(UUID player) {
            Integer position = positions.get(player);
            return position == null ? 0 : position;
        }

        Row rowOf(UUID player) {
            int position = position(player);
            return position == 0 ? null : rows.get(position - 1);
        }
    }

    private final int defaultRating;
    private final NetworkLevel levels;
    private final Map<UUID, PlayerStats> players = new HashMap<>();
    private final Map<String, Ranking> rankings = new HashMap<>();
    private String rankedDay = "";

    StatsBook(int defaultRating, NetworkLevel levels) {
        this.defaultRating = defaultRating;
        this.levels = levels;
    }

    NetworkLevel levels() {
        return levels;
    }

    /** The Network level of the player: 1 without stats. */
    int level(UUID player) {
        PlayerStats stats = players.get(player);
        return levels.level(stats == null ? 0 : stats.xp());
    }

    PlayerStats get(UUID player) {
        return players.get(player);
    }

    /** Adds stats read from a file, unless the player already has some in memory. */
    boolean load(PlayerStats stats) {
        if (players.containsKey(stats.id())) {
            return false;
        }
        players.put(stats.id(), stats);
        rankings.clear();
        return true;
    }

    /** The player's ELO in a ranked mode: the starting one before their first ranked game. */
    int elo(UUID player, String modeId) {
        PlayerStats stats = players.get(player);
        ModeStats mode = stats == null ? null : stats.mode(modeId);
        return mode != null && mode.rated() ? mode.elo : defaultRating;
    }

    /** The player is online under this name. Returns their stats if the name changed, else null. */
    PlayerStats seen(UUID player, String name) {
        PlayerStats stats = players.get(player);
        if (stats != null && stats.rename(name)) {
            rankings.clear();
            return stats;
        }
        return null;
    }

    /**
     * A game is over: one side won, the other lost. Everyone gets Network XP, the winners more. A
     * ranked game also moves the ELO of both sides (the classic formula, on the average of each
     * side). Returns the stats that changed.
     */
    List<PlayerStats> record(GameMode mode, Collection<UUID> winners, Collection<UUID> losers, Names names, long millis) {
        List<PlayerStats> changed = new ArrayList<>();
        if (winners.isEmpty() || losers.isEmpty()) {
            return changed;
        }
        String day = StatsPeriod.dayKey(millis);
        String week = StatsPeriod.weekKey(millis);
        Map<UUID, Integer> newElo = new HashMap<>();
        if (mode.ranked()) {
            double winnerRating = average(winners, mode.id());
            double loserRating = average(losers, mode.id());
            double expected = 1.0 / (1.0 + Math.pow(10.0, (loserRating - winnerRating) / 400.0));
            int gain = (int) Math.round(K_FACTOR * (1.0 - expected));
            for (UUID id : winners) {
                newElo.put(id, elo(id, mode.id()) + gain);
            }
            for (UUID id : losers) {
                newElo.put(id, Math.max(1, elo(id, mode.id()) - gain));
            }
        }
        for (UUID id : new LinkedHashSet<>(winners)) {
            PlayerStats stats = play(id, names, mode.id(), true, day, week, newElo.get(id));
            stats.addXp(levels.winXp());
            changed.add(stats);
        }
        for (UUID id : new LinkedHashSet<>(losers)) {
            if (!winners.contains(id)) {
                PlayerStats stats = play(id, names, mode.id(), false, day, week, newElo.get(id));
                stats.addXp(levels.lossXp());
                changed.add(stats);
            }
        }
        rankings.clear();
        return changed;
    }

    /**
     * A kill in a game of this mode (in a sumo: the victim fell): a kill and some XP for the killer,
     * a death for the victim (null if nobody died, unknown). Returns the stats that changed.
     */
    List<PlayerStats> kill(String modeId, UUID killer, UUID victim, Names names, long millis) {
        List<PlayerStats> changed = new ArrayList<>();
        if (killer == null || killer.equals(victim)) {
            return changed;
        }
        PlayerStats stats = statsOf(killer, names);
        stats.modeOrNew(modeId).kill(StatsPeriod.dayKey(millis), StatsPeriod.weekKey(millis));
        stats.addXp(levels.killXp());
        changed.add(stats);
        if (victim != null) {
            PlayerStats dead = statsOf(victim, names);
            dead.modeOrNew(modeId).death();
            changed.add(dead);
        }
        rankings.clear();
        return changed;
    }

    /** Where to find the name of a player who has no stats yet. */
    interface Names {
        String of(UUID player);
    }

    private PlayerStats statsOf(UUID id, Names names) {
        PlayerStats stats = players.get(id);
        if (stats == null) {
            stats = new PlayerStats(id, names.of(id));
            players.put(id, stats);
        }
        return stats;
    }

    private PlayerStats play(UUID id, Names names, String modeId, boolean won, String day, String week, Integer elo) {
        PlayerStats stats = statsOf(id, names);
        ModeStats mode = stats.modeOrNew(modeId);
        mode.record(won, day, week);
        if (elo != null) {
            mode.elo = elo;
        }
        return stats;
    }

    private double average(Collection<UUID> side, String modeId) {
        double total = 0;
        for (UUID id : side) {
            total += elo(id, modeId);
        }
        return total / side.size();
    }

    /**
     * A leaderboard: wins, games, best streak or kills over a period, of one mode or of all (modeId
     * null), the ELO of one ranked mode, or the Network XP (the period is then ignored; the board
     * shows the level). Players at zero are left out; ties keep the alphabetical order of the names.
     */
    Ranking ranking(BoardStat stat, StatsPeriod period, String modeId, long millis) {
        String day = StatsPeriod.dayKey(millis);
        String week = StatsPeriod.weekKey(millis);
        if (!day.equals(rankedDay)) {
            rankings.clear();
            rankedDay = day;
        }
        String key = stat + "/" + (stat.periodic() ? period : "") + "/" + (stat == BoardStat.LEVEL ? "" : modeId);
        Ranking ranking = rankings.get(key);
        if (ranking != null) {
            return ranking;
        }
        List<Row> rows = new ArrayList<>();
        for (PlayerStats stats : players.values()) {
            int value;
            switch (stat) {
                case WINS:
                    value = stats.wins(period, modeId, day, week);
                    break;
                case GAMES:
                    value = stats.games(period, modeId, day, week);
                    break;
                case STREAK:
                    value = stats.best(period, modeId, day, week);
                    break;
                case KILLS:
                    value = stats.kills(period, modeId, day, week);
                    break;
                case LEVEL:
                    value = stats.xp();
                    break;
                default:
                    ModeStats mode = modeId == null ? null : stats.mode(modeId);
                    value = mode != null && mode.rated() ? mode.elo : 0;
                    break;
            }
            if (value > 0) {
                rows.add(new Row(stats.id(), stats.name(), value));
            }
        }
        Collections.sort(rows, (a, b) -> a.value() != b.value() ? Integer.compare(b.value(), a.value())
                : a.name().compareToIgnoreCase(b.name()));
        ranking = new Ranking(rows);
        rankings.put(key, ranking);
        return ranking;
    }

    int size() {
        return players.size();
    }
}
