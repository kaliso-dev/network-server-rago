package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every player's stats, in memory: what the games record, what the stats NPCs and the
 * leaderboards read. Rankings are kept until the next game or the next day. Main thread only.
 */
final class StatsBook {

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

    /** One line of a team streak leaderboard: the names of the team, and its streak. */
    static final class TeamRow {
        private final List<UUID> players;
        private final String names;
        private final int value;

        TeamRow(List<UUID> players, String names, int value) {
            this.players = players;
            this.names = names;
            this.value = value;
        }

        List<UUID> players() {
            return players;
        }

        /** "Alice, Bob": the names in alphabetical order. */
        String names() {
            return names;
        }

        int value() {
            return value;
        }
    }

    private final int defaultRating;
    private final NetworkLevel levels;
    private final EloLadder ladder;
    private final Map<UUID, PlayerStats> players = new HashMap<>();
    /** The streaks of the teams of each mode, by mode id, then by {@link TeamStreak#key}. */
    private final Map<String, Map<String, TeamStreak>> teams = new HashMap<>();
    private final Map<String, Ranking> rankings = new HashMap<>();
    private final Map<String, List<TeamRow>> teamRankings = new HashMap<>();
    private String rankedDay = "";
    /** What each player's Network XP is multiplied by (the boost of PREMIUM): 1 by default. */
    private java.util.function.ToDoubleFunction<UUID> boost = player -> 1.0;
    /**
     * Who has stats: everyone but RagoBot ({@link RagoBots}), whose games count for the players
     * against it, never for itself (nor in the leaderboards).
     */
    private java.util.function.Predicate<UUID> counted = player -> true;

    StatsBook(int defaultRating, NetworkLevel levels) {
        this(defaultRating, levels, EloLadder.DEFAULT);
    }

    StatsBook(int defaultRating, NetworkLevel levels, EloLadder ladder) {
        this.defaultRating = defaultRating;
        this.levels = levels;
        this.ladder = ladder;
    }

    NetworkLevel levels() {
        return levels;
    }

    void setBoost(java.util.function.ToDoubleFunction<UUID> boost) {
        this.boost = boost;
    }

    /** Who has stats (see {@link #counted}). */
    void setCounted(java.util.function.Predicate<UUID> counted) {
        this.counted = counted;
    }

    /** What the player's Network XP is multiplied by: 1 without a boost. */
    double boost(UUID player) {
        return Math.max(1.0, boost.applyAsDouble(player));
    }

    /** The XP the player gets for this much: more with a boost, rounded. */
    int boosted(UUID player, int xp) {
        return (int) Math.min(Integer.MAX_VALUE, Math.round(xp * boost(player)));
    }

    EloLadder ladder() {
        return ladder;
    }

    /** The player's stats and profile, made empty the first time (a new player). */
    PlayerStats profile(UUID player, String name) {
        PlayerStats stats = players.get(player);
        if (stats == null) {
            stats = new PlayerStats(player, name);
            players.put(player, stats);
        }
        return stats;
    }

    /** Every player the book knows, online or not. */
    Collection<PlayerStats> all() {
        return Collections.unmodifiableCollection(players.values());
    }

    /** The player who last had this name (ignoring case), or null. */
    PlayerStats byName(String name) {
        PlayerStats found = null;
        for (PlayerStats stats : players.values()) {
            if (stats.name().equalsIgnoreCase(name)) {
                found = stats;
            }
        }
        return found;
    }

    /** The Network level of the player: 1 without stats. */
    int level(UUID player) {
        PlayerStats stats = players.get(player);
        return levels.level(stats == null ? 0 : stats.xp());
    }

    PlayerStats get(UUID player) {
        return players.get(player);
    }

    /**
     * Adds stats read from a file. A profile already in memory was made before the files were read
     * (a player online during a /reload, or who joined in the first moments): the file's stats
     * take its place, else that empty profile would be written over the file at its next change.
     * Returns true if they took the place of such a profile.
     */
    boolean load(PlayerStats stats) {
        boolean replaced = players.put(stats.id(), stats) != null;
        forgetRankings();
        return replaced;
    }

    /** The player's ELO in a ranked mode: the starting one before their first ranked game. */
    int elo(UUID player, String modeId) {
        PlayerStats stats = players.get(player);
        ModeStats mode = stats == null ? null : stats.mode(modeId);
        // a player who played the mode has their ELO, even at 0: the floor of the ladder
        return mode != null && mode.decided() > 0 ? mode.elo : defaultRating;
    }

    /** The player is online under this name. Returns their stats if the name changed, else null. */
    PlayerStats seen(UUID player, String name) {
        PlayerStats stats = players.get(player);
        if (stats != null && stats.rename(name)) {
            forgetRankings();
            return stats;
        }
        return null;
    }

    /** Adds a team's streak read from a file, unless the team already has one in memory. */
    boolean loadTeam(String modeId, TeamStreak team) {
        Map<String, TeamStreak> mine = teamsOf(modeId);
        if (mine.containsKey(team.key())) {
            return false;
        }
        mine.put(team.key(), team);
        forgetRankings();
        return true;
    }

    /** The streaks of every team that won a game of this mode. */
    Collection<TeamStreak> teams(String modeId) {
        Map<String, TeamStreak> mine = teams.get(modeId);
        return mine == null ? Collections.<TeamStreak>emptyList() : Collections.unmodifiableCollection(mine.values());
    }

    private Map<String, TeamStreak> teamsOf(String modeId) {
        Map<String, TeamStreak> mine = teams.get(modeId);
        if (mine == null) {
            mine = new HashMap<>();
            teams.put(modeId, mine);
        }
        return mine;
    }

    /**
     * The winners' team goes on with its streak; the streak of every team among the losers ends.
     * The losers of a game of more than two teams come as one list: any team made only of losers
     * lost.
     */
    private void recordTeams(String modeId, Collection<UUID> winners, Collection<UUID> losers, String day, String week) {
        Map<String, TeamStreak> mine = teamsOf(modeId);
        String key = TeamStreak.key(winners);
        TeamStreak won = mine.get(key);
        if (won == null) {
            won = new TeamStreak(winners);
            mine.put(key, won);
        }
        won.win(day, week);
        Set<UUID> beaten = new HashSet<>(losers);
        beaten.removeAll(winners);
        for (TeamStreak team : mine.values()) {
            if (team != won && beaten.containsAll(team.players())) {
                team.lose();
            }
        }
    }

    /**
     * A game is over: one side won, the other lost. Everyone gets Network XP, the winners more. A
     * ranked game also moves the ELO of everyone in it, by their division ({@link EloLadder}): the
     * MVPs (the most kills of the game) get the bonus. The winners' team goes on with its win
     * streak. Returns the stats that changed.
     */
    List<PlayerStats> record(GameMode mode, Collection<UUID> winners, Collection<UUID> losers, Names names, long millis) {
        return record(mode, winners, losers, names, millis, Collections.<UUID>emptySet());
    }

    List<PlayerStats> record(GameMode mode, Collection<UUID> winners, Collection<UUID> losers, Names names, long millis,
            Collection<UUID> mvps) {
        return record(mode, winners, losers, names, millis, mvps, true);
    }

    /**
     * A game is over. Not rated (a /duel, started without the queue), it counts as a game, a win
     * or a loss with its XP, but moves neither the ELO nor any win streak (the player's, the
     * team's): two friends could farm them against each other.
     */
    List<PlayerStats> record(GameMode mode, Collection<UUID> winners, Collection<UUID> losers, Names names, long millis,
            Collection<UUID> mvps, boolean rated) {
        List<PlayerStats> changed = new ArrayList<>();
        if (winners.isEmpty() || losers.isEmpty()) {
            return changed;
        }
        String day = StatsPeriod.dayKey(millis);
        String week = StatsPeriod.weekKey(millis);
        Map<UUID, Integer> newElo = new HashMap<>();
        if (mode.ranked() && rated) {
            for (UUID id : winners) {
                newElo.put(id, ladder.after(elo(id, mode.id()), true, mvps.contains(id)));
            }
            for (UUID id : losers) {
                if (!winners.contains(id)) {
                    newElo.put(id, ladder.after(elo(id, mode.id()), false, mvps.contains(id)));
                }
            }
        }
        for (UUID id : new LinkedHashSet<>(winners)) {
            if (!counted.test(id)) {
                continue;
            }
            PlayerStats stats = play(id, names, mode.id(), true, day, week, newElo.get(id), rated);
            stats.earnXp(boosted(id, levels.winXp()), day, week);
            changed.add(stats);
        }
        for (UUID id : new LinkedHashSet<>(losers)) {
            if (!winners.contains(id) && counted.test(id)) {
                PlayerStats stats = play(id, names, mode.id(), false, day, week, newElo.get(id), rated);
                stats.earnXp(boosted(id, levels.lossXp()), day, week);
                changed.add(stats);
            }
        }
        if (rated) {
            recordTeams(mode.id(), winners, losers, day, week);
        }
        forgetRankings();
        return changed;
    }

    /**
     * A game of this mode without a winner (the time ran out, nobody left standing): a game played
     * for each of its players, with the XP of a game played, neither a win nor a loss. Neither the
     * ELO nor any win streak moves. Returns the stats that changed.
     */
    List<PlayerStats> draw(GameMode mode, Collection<UUID> players, Names names, long millis) {
        List<PlayerStats> changed = new ArrayList<>();
        String day = StatsPeriod.dayKey(millis);
        String week = StatsPeriod.weekKey(millis);
        for (UUID id : new LinkedHashSet<>(players)) {
            if (!counted.test(id)) {
                continue;
            }
            PlayerStats stats = statsOf(id, names);
            stats.modeOrNew(mode.id()).draw(day, week);
            stats.earnXp(boosted(id, levels.lossXp()), day, week);
            changed.add(stats);
        }
        forgetRankings();
        return changed;
    }

    /**
     * A kill in a game of this mode (in a sumo: the victim fell): a kill and some XP for the killer,
     * a death for the victim (null if nobody died, unknown). Returns the stats that changed.
     */
    List<PlayerStats> kill(String modeId, UUID killer, UUID victim, Names names, long millis) {
        return kill(modeId, killer, victim, false, names, millis);
    }

    /**
     * A kill in a game of this mode: a kill and some XP for the killer, a death for the victim
     * (null if nobody died, unknown). A death nobody gets the kill of (killer null: the void, a
     * fall; or the victim themselves) is a death all the same. A Bed Wars final kill gives the
     * killer the XP of a kill, but it is counted in the final kills and the final deaths only
     * ({@link #finalKill}, {@link #finalDeath}), not again in the kills and the deaths, as on
     * Hypixel. Returns the stats that changed.
     */
    List<PlayerStats> kill(String modeId, UUID killer, UUID victim, boolean finalKill, Names names, long millis) {
        List<PlayerStats> changed = new ArrayList<>();
        if (killer != null && killer.equals(victim)) {
            // no kill of oneself
            killer = null;
        }
        // RagoBot: its kill is a death of the player, its death a kill of the player
        if (killer != null && !counted.test(killer)) {
            killer = null;
        }
        if (victim != null && !counted.test(victim)) {
            victim = null;
        }
        if (killer != null) {
            PlayerStats stats = statsOf(killer, names);
            if (!finalKill) {
                stats.modeOrNew(modeId).kill(StatsPeriod.dayKey(millis), StatsPeriod.weekKey(millis));
            }
            stats.earnXp(boosted(killer, levels.killXp()), StatsPeriod.dayKey(millis), StatsPeriod.weekKey(millis));
            changed.add(stats);
        }
        if (victim != null && !finalKill) {
            PlayerStats dead = statsOf(victim, names);
            dead.modeOrNew(modeId).death();
            changed.add(dead);
        }
        forgetRankings();
        return changed;
    }

    /**
     * The kills and the deaths of these modes back to 0 for every player: ever, of the day and of
     * the week. The rest stays (wins, final kills, beds...). Returns the stats that changed.
     */
    List<PlayerStats> resetKills(Collection<String> modeIds) {
        List<PlayerStats> changed = new ArrayList<>();
        for (PlayerStats stats : players.values()) {
            boolean any = false;
            for (String modeId : modeIds) {
                ModeStats mode = stats.mode(modeId);
                if (mode != null && (mode.kills > 0 || mode.deaths > 0 || mode.dayKills > 0 || mode.weekKills > 0)) {
                    mode.kills = 0;
                    mode.deaths = 0;
                    mode.dayKills = 0;
                    mode.weekKills = 0;
                    any = true;
                }
            }
            if (any) {
                changed.add(stats);
            }
        }
        forgetRankings();
        return changed;
    }

    /**
     * Every player's stats back to nothing ({@link PlayerStats#resetStats}: the XP, the level, every
     * mode) and the streaks of every team. Returns the stats that changed.
     */
    List<PlayerStats> resetStats() {
        List<PlayerStats> changed = new ArrayList<>();
        for (PlayerStats stats : players.values()) {
            if (stats.resetStats()) {
                changed.add(stats);
            }
        }
        teams.clear();
        forgetRankings();
        return changed;
    }

    /** The modes in which a team has a streak (a file of stats/equipes each). */
    Set<String> teamModes() {
        return new HashSet<>(teams.keySet());
    }

    /** A Bed Wars final kill of this player in this mode: counted; their stats, changed. */
    PlayerStats finalKill(String modeId, UUID killer, Names names) {
        return finalKill(modeId, killer, names, System.currentTimeMillis());
    }

    /** A Bed Wars final kill at this time: counted, in its day and its week too. */
    PlayerStats finalKill(String modeId, UUID killer, Names names, long millis) {
        PlayerStats stats = statsOf(killer, names);
        stats.modeOrNew(modeId).finalKill(StatsPeriod.dayKey(millis), StatsPeriod.weekKey(millis));
        // the FKDR leaderboard follows at once
        forgetRankings();
        return stats;
    }

    /** A Bed Wars final death of this player in this mode (out of the game): counted; their stats, changed. */
    PlayerStats finalDeath(String modeId, UUID player, Names names) {
        return finalDeath(modeId, player, names, System.currentTimeMillis());
    }

    /** A Bed Wars final death at this time: counted, in its day and its week too. */
    PlayerStats finalDeath(String modeId, UUID player, Names names, long millis) {
        PlayerStats stats = statsOf(player, names);
        stats.modeOrNew(modeId).finalDeath(StatsPeriod.dayKey(millis), StatsPeriod.weekKey(millis));
        // the FKDR leaderboard follows at once
        forgetRankings();
        return stats;
    }

    /** A bed this player broke in this mode: counted; their stats, changed. */
    PlayerStats bedBroken(String modeId, UUID player, Names names) {
        PlayerStats stats = statsOf(player, names);
        stats.modeOrNew(modeId).beds++;
        return stats;
    }

    /** Network XP earned in a training (boosted): added to the player's stats, returned (null for 0 or less). */
    PlayerStats giveXp(UUID player, int xp, Names names) {
        if (player == null || xp <= 0) {
            return null;
        }
        PlayerStats stats = statsOf(player, names);
        long now = System.currentTimeMillis();
        stats.earnXp(boosted(player, xp), StatsPeriod.dayKey(now), StatsPeriod.weekKey(now));
        forgetRankings();
        return stats;
    }

    /** Network XP of a reward (the Daily Chest), as it is, not boosted: counted in its day and its week too. */
    void rewardXp(PlayerStats stats, int xp) {
        if (xp <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        stats.earnXp(xp, StatsPeriod.dayKey(now), StatsPeriod.weekKey(now));
        forgetRankings();
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

    private PlayerStats play(UUID id, Names names, String modeId, boolean won, String day, String week, Integer elo,
            boolean streaks) {
        PlayerStats stats = statsOf(id, names);
        ModeStats mode = stats.modeOrNew(modeId);
        // the ELO before the game, as elo(player, mode) reads it: the start for a first game
        int before = mode.decided() > 0 ? mode.elo : defaultRating;
        mode.record(won, day, week, streaks);
        if (elo != null) {
            mode.elo = elo;
            mode.peakElo = Math.max(mode.peakElo, elo);
            mode.eloChange(elo - before, day, week);
        }
        return stats;
    }

    /**
     * A leaderboard: wins, games, best streak or kills over a period, of one mode or of all (modeId
     * null), the ELO of one ranked mode, the FKDR (in hundredths) or the Network XP (the period is
     * then ignored; the board shows the level). Players at zero are left out, but for the ELO: a player of the mode at 0 is
     * in the first division. Ties keep the alphabetical order of the names.
     */
    Ranking ranking(BoardStat stat, StatsPeriod period, String modeId, long millis) {
        String day = StatsPeriod.dayKey(millis);
        String week = StatsPeriod.weekKey(millis);
        newDay(day);
        String key = stat + "/" + (stat.periodic() ? period : "") + "/" + (stat == BoardStat.LEVEL || stat == BoardStat.FKDR ? "" : modeId);
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
                case FKDR:
                    value = BoardStat.fkdr(stats.finalKills(), stats.finalDeaths());
                    break;
                case LEVEL:
                    value = stats.xp();
                    break;
                case WLR:
                    value = BoardStat.fkdr(stats.wins(StatsPeriod.ALL, null, day, week), stats.losses());
                    break;
                default:
                    // every player of the mode, even at 0 ELO (the bottom of Coal)
                    ModeStats mode = modeId == null ? null : stats.mode(modeId);
                    value = mode != null && mode.decided() > 0 ? mode.elo : -1;
                    break;
            }
            if (value > 0 || value == 0 && stat == BoardStat.ELO) {
                rows.add(new Row(stats.id(), stats.name(), value));
            }
        }
        Collections.sort(rows, (a, b) -> a.value() != b.value() ? Integer.compare(b.value(), a.value())
                : a.name().compareToIgnoreCase(b.name()));
        ranking = new Ranking(rows);
        rankings.put(key, ranking);
        return ranking;
    }

    /**
     * A leaderboard of the boards of the selector (plan 3.1), where every stat has its day, its week
     * and its lifetime. Lifetime is {@link #ranking} itself. Over a day or a week: the wins, the
     * games, the best streak and the kills of the period; the XP earned (Network level); the ELO
     * won in the ranked mode (under 0: lost), for every player who played it then; the FKDR and the
     * WLR of the counters of the period, for the players with at least {@code minimumGames} games
     * in it (for the FKDR, the games of the modes with final kills: Bed Wars), in hundredths.
     */
    Ranking ranking(BoardStat stat, StatsPeriod period, String modeId, long millis, int minimumGames) {
        if (period == StatsPeriod.ALL || stat.periodic()) {
            return ranking(stat, period, modeId, millis);
        }
        String day = StatsPeriod.dayKey(millis);
        String week = StatsPeriod.weekKey(millis);
        newDay(day);
        String key = "selector/" + stat + "/" + period + "/" + (stat == BoardStat.ELO ? modeId : "") + "/" + minimumGames;
        Ranking ranking = rankings.get(key);
        if (ranking != null) {
            return ranking;
        }
        // the modes with final kills (Bed Wars): those whose games count for the FKDR
        java.util.Set<String> finals = new java.util.HashSet<>();
        if (stat == BoardStat.FKDR) {
            for (PlayerStats stats : players.values()) {
                for (Map.Entry<String, ModeStats> mode : stats.modes().entrySet()) {
                    if (mode.getValue().finalKills > 0 || mode.getValue().finalDeaths > 0) {
                        finals.add(mode.getKey());
                    }
                }
            }
        }
        List<Row> rows = new ArrayList<>();
        for (PlayerStats stats : players.values()) {
            int value;
            boolean ranked;
            switch (stat) {
                case LEVEL:
                    value = stats.xp(period, day, week);
                    ranked = value > 0;
                    break;
                case ELO: {
                    ModeStats mode = modeId == null ? null : stats.mode(modeId);
                    boolean played = mode != null && mode.games(period, day, week) > 0;
                    value = played ? mode.elo(period, day, week) : 0;
                    ranked = played;
                    break;
                }
                case FKDR: {
                    int games = 0;
                    for (String mode : finals) {
                        ModeStats of = stats.mode(mode);
                        games += of == null ? 0 : of.games(period, day, week);
                    }
                    value = BoardStat.fkdr(stats.finalKills(period, day, week), stats.finalDeaths(period, day, week));
                    ranked = value > 0 && games >= minimumGames;
                    break;
                }
                case WLR:
                    value = BoardStat.fkdr(stats.wins(period, null, day, week), stats.losses(period, day, week));
                    ranked = value > 0 && stats.games(period, null, day, week) >= minimumGames;
                    break;
                default:
                    value = 0;
                    ranked = false;
                    break;
            }
            if (ranked) {
                rows.add(new Row(stats.id(), stats.name(), value));
            }
        }
        Collections.sort(rows, (a, b) -> a.value() != b.value() ? Integer.compare(b.value(), a.value())
                : a.name().compareToIgnoreCase(b.name()));
        ranking = new Ranking(rows);
        rankings.put(key, ranking);
        return ranking;
    }

    /** The first {@code count} rows of a leaderboard ({@link #ranking}), or fewer. */
    List<Row> top(BoardStat stat, StatsPeriod period, String modeId, int count, long millis) {
        List<Row> rows = ranking(stat, period, modeId, millis).rows();
        return count <= 0 ? Collections.<Row>emptyList() : rows.subList(0, Math.min(count, rows.size()));
    }

    /**
     * The teams with the longest win streaks of a mode over a period, best first: their names and
     * their streak. Teams at zero are left out; ties keep the alphabetical order of the names.
     */
    List<TeamRow> topStreaks(String modeId, StatsPeriod period, long millis) {
        String day = StatsPeriod.dayKey(millis);
        String week = StatsPeriod.weekKey(millis);
        newDay(day);
        String key = modeId + "/" + period;
        List<TeamRow> rows = teamRankings.get(key);
        if (rows != null) {
            return rows;
        }
        rows = new ArrayList<>();
        for (TeamStreak team : teams(modeId)) {
            int value = team.best(period, day, week);
            if (value > 0) {
                List<String> names = new ArrayList<>();
                for (UUID id : team.players()) {
                    PlayerStats stats = players.get(id);
                    names.add(stats == null ? "?" : stats.name());
                }
                Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
                rows.add(new TeamRow(team.players(), String.join(", ", names), value));
            }
        }
        Collections.sort(rows, (a, b) -> a.value() != b.value() ? Integer.compare(b.value(), a.value())
                : a.names().compareToIgnoreCase(b.names()));
        rows = Collections.unmodifiableList(rows);
        teamRankings.put(key, rows);
        return rows;
    }

    /** A new day: the leaderboards of the day before are forgotten. */
    private void newDay(String day) {
        if (!day.equals(rankedDay)) {
            forgetRankings();
            rankedDay = day;
        }
    }

    private void forgetRankings() {
        rankings.clear();
        teamRankings.clear();
    }

    int size() {
        return players.size();
    }
}
