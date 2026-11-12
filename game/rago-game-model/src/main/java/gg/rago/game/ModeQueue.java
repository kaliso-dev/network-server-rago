package gg.rago.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * The queue of one game mode. Players wait in the order they joined; {@link #poll(long)} takes
 * out the matches that can start now.
 *
 * <ul>
 *   <li>Unranked mode: first come, first served. The teams are dealt in turn (the first player in
 *       the first team, the second in the second, and so on).
 *   <li>Ranked mode: the player who waited longest is matched with the closest ratings that both
 *       sides accept, each side's tolerance growing with its waiting time ({@link RatingWindow}).
 *       The teams are then balanced by rating.
 * </ul>
 *
 * <p>In both, the players of a match agree on the map: a player who chose a map only plays with
 * players who chose the same one or none. The players of a party ({@link QueueEntry#party()}) wait
 * as one: they play the same match, in the same team, with the party's average rating in a ranked
 * mode. A party never has more players than a team.
 *
 * <p>A priority can go first ({@link #poll(long, ToIntFunction)}: the ranks of the web store): the
 * higher priorities are matched first, then the waiting time decides. It never changes who plays
 * whom in a ranked mode (the gap of ratings first), and after {@link #PRIORITY_MILLIS} of waiting
 * everyone is first in line, so that nobody waits long behind them.
 *
 * <p>Not thread-safe: on a Bukkit server it lives on the main thread, where it only does a few
 * comparisons per second.
 */
public final class ModeQueue {

    private static final Comparator<QueueEntry> BY_JOIN_TIME =
            Comparator.comparingLong(QueueEntry::joinedAtMillis);
    /** The most combinations tried for one oldest player at each poll, so that a long queue stays cheap. */
    private static final int MAX_STEPS = 5_000;
    /** After this long in the queue, a player goes first whatever their priority. */
    public static final long PRIORITY_MILLIS = 60_000L;

    private final GameMode mode;
    private final RatingWindow window;
    /** Sorted by join time, oldest first. */
    private final List<QueueEntry> waiting = new ArrayList<>();

    public ModeQueue(GameMode mode, RatingWindow window) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.window = Objects.requireNonNull(window, "window");
    }

    public GameMode mode() {
        return mode;
    }

    public int size() {
        return waiting.size();
    }

    public boolean contains(UUID player) {
        return find(player) != null;
    }

    /** The entry of this player, or null. */
    public QueueEntry find(UUID player) {
        for (QueueEntry entry : waiting) {
            if (entry.player().equals(player)) {
                return entry;
            }
        }
        return null;
    }

    /** Adds the entry at its place by join time. False if the player is already waiting. */
    public boolean add(QueueEntry entry) {
        if (contains(entry.player())) {
            return false;
        }
        int index = Collections.binarySearch(waiting, entry, BY_JOIN_TIME);
        // Equal join times keep their arrival order: insert after the last equal one.
        int at = index < 0 ? -index - 1 : index + 1;
        while (at < waiting.size() && waiting.get(at).joinedAtMillis() == entry.joinedAtMillis()) {
            at++;
        }
        waiting.add(at, entry);
        return true;
    }

    /** Puts back the entries of a match that could not start, with their original join times. */
    public void requeue(Collection<QueueEntry> entries) {
        for (QueueEntry entry : entries) {
            add(entry);
        }
    }

    /** Takes this player's entry out (alone: their party stays). */
    public boolean remove(UUID player) {
        QueueEntry entry = find(player);
        return entry != null && waiting.remove(entry);
    }

    /** Takes this player out with their whole party, and returns the entries taken (none if not waiting). */
    public List<QueueEntry> removeWithParty(UUID player) {
        List<QueueEntry> removed = withParty(player);
        waiting.removeAll(removed);
        return removed;
    }

    /** This player's entry and those of their party, oldest first; empty if not waiting. */
    public List<QueueEntry> withParty(UUID player) {
        QueueEntry entry = find(player);
        List<QueueEntry> party = new ArrayList<>();
        if (entry == null) {
            return party;
        }
        for (QueueEntry other : waiting) {
            if (other.unit().equals(entry.unit())) {
                party.add(other);
            }
        }
        return party;
    }

    /** The waiting entries, oldest first. */
    public List<QueueEntry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(waiting));
    }

    /** Takes out every match that can start now and returns them, the oldest players first. */
    public List<MatchProposal> poll(long nowMillis) {
        return poll(nowMillis, player -> 0);
    }

    /**
     * Takes out every match that can start now: the highest priority first (of a party: its
     * highest), then the oldest; a player waiting for {@link #PRIORITY_MILLIS} or more goes first
     * whatever their priority.
     */
    public List<MatchProposal> poll(long nowMillis, ToIntFunction<UUID> priority) {
        return poll(nowMillis, priority, Collections.<UUID>emptySet());
    }

    /**
     * As {@link #poll(long, ToIntFunction)}, without the players of {@code busy} (matched in another
     * queue in the same pass): they, and their party, keep their place here but play nothing now.
     */
    public List<MatchProposal> poll(long nowMillis, ToIntFunction<UUID> priority, Set<UUID> busy) {
        List<MatchProposal> matches = new ArrayList<>();
        Set<UUID> taken = new HashSet<>();
        List<Unit> units = units();
        if (!busy.isEmpty()) {
            units.removeIf(unit -> unit.entries.stream().anyMatch(entry -> busy.contains(entry.player())));
        }
        final Map<UUID, Integer> first = new HashMap<>();
        for (Unit unit : units) {
            int highest = 0;
            for (QueueEntry entry : unit.entries) {
                highest = Math.max(highest, priority.applyAsInt(entry.player()));
            }
            first.put(unit.id, unit.waitedMillis(nowMillis) >= PRIORITY_MILLIS ? Integer.MAX_VALUE : highest);
        }
        // Stable: the same priority keeps the order of the waiting time.
        units.sort((one, other) -> Integer.compare(first.get(other.id), first.get(one.id)));
        for (Unit anchor : units) {
            if (taken.contains(anchor.id)) {
                continue;
            }
            List<Unit> candidates = new ArrayList<>();
            for (Unit other : units) {
                if (other != anchor && !taken.contains(other.id) && (!mode.ranked() || closeEnough(anchor, other, nowMillis))) {
                    candidates.add(other);
                }
            }
            if (mode.ranked()) {
                // Closest rating first; equal gaps go to whoever waited longest (the sort is stable).
                candidates.sort(Comparator.comparingDouble(other -> Math.abs(other.rating - anchor.rating)));
            }
            List<List<QueueEntry>> teams = pick(anchor, candidates);
            if (teams == null) {
                continue;
            }
            MatchProposal match = new MatchProposal(mode, teams);
            for (QueueEntry entry : match.entries()) {
                taken.add(entry.unit());
            }
            matches.add(match);
        }
        waiting.removeIf(entry -> taken.contains(entry.unit()));
        return matches;
    }

    /** Both sides accept the gap between their ratings, each with the tolerance of its waiting time. */
    private boolean closeEnough(Unit anchor, Unit other, long nowMillis) {
        double gap = Math.abs(other.rating - anchor.rating);
        int width = Math.min(window.width(anchor.waitedMillis(nowMillis)), window.width(other.waitedMillis(nowMillis)));
        return gap <= width;
    }

    // --- Who plays -------------------------------------------------------------------------------

    /** A player alone, or a party: they wait, and are matched, as one. */
    private static final class Unit {
        final UUID id;
        final List<QueueEntry> entries = new ArrayList<>();
        /** The map chosen (a party chose one map for all), or null. */
        String map;
        long joinedAtMillis = Long.MAX_VALUE;
        /** The average rating of the players. */
        double rating;

        Unit(UUID id) {
            this.id = id;
        }

        int size() {
            return entries.size();
        }

        long waitedMillis(long nowMillis) {
            return Math.max(0L, nowMillis - joinedAtMillis);
        }

        boolean accepts(String chosen) {
            return map == null || chosen == null || map.equalsIgnoreCase(chosen);
        }
    }

    /** The waiting players as units, in the order of their oldest player. */
    private List<Unit> units() {
        Map<UUID, Unit> units = new LinkedHashMap<>();
        for (QueueEntry entry : waiting) {
            Unit unit = units.get(entry.unit());
            if (unit == null) {
                unit = new Unit(entry.unit());
                units.put(entry.unit(), unit);
            }
            unit.entries.add(entry);
            if (unit.map == null) {
                unit.map = entry.map();
            }
            unit.joinedAtMillis = Math.min(unit.joinedAtMillis, entry.joinedAtMillis());
        }
        for (Unit unit : units.values()) {
            double sum = 0;
            for (QueueEntry entry : unit.entries) {
                sum += entry.rating();
            }
            unit.rating = sum / unit.size();
        }
        return new ArrayList<>(units.values());
    }

    /**
     * The teams of a match of the anchor and the first of these candidates that agree with it on the
     * map, fill the match exactly and fit in its teams with each party whole; null if there are not
     * enough. The first who chose a map sets it for the match.
     */
    private List<List<QueueEntry>> pick(Unit anchor, List<Unit> candidates) {
        int needed = mode.playersPerMatch();
        int available = anchor.size();
        for (Unit candidate : candidates) {
            if (candidate.accepts(anchor.map)) {
                available += candidate.size();
            }
        }
        if (available < needed || anchor.size() > mode.playersPerTeam()) {
            return null;
        }
        List<Unit> chosen = new ArrayList<>();
        chosen.add(anchor);
        return search(candidates, 0, chosen, anchor.size(), anchor.map, new int[] {MAX_STEPS});
    }

    /**
     * Depth first, the oldest candidates first: the first combination found is the fairest. Returns
     * its teams, or null.
     */
    private List<List<QueueEntry>> search(List<Unit> candidates, int from, List<Unit> chosen, int count, String map,
            int[] steps) {
        if (count == mode.playersPerMatch()) {
            return teams(chosen);
        }
        for (int i = from; i < candidates.size(); i++) {
            if (--steps[0] < 0) {
                return null;
            }
            Unit unit = candidates.get(i);
            if (count + unit.size() > mode.playersPerMatch() || unit.size() > mode.playersPerTeam() || !unit.accepts(map)) {
                continue;
            }
            chosen.add(unit);
            List<List<QueueEntry>> teams = search(candidates, i + 1, chosen, count + unit.size(),
                    map == null ? unit.map : map, steps);
            if (teams != null) {
                return teams;
            }
            chosen.remove(chosen.size() - 1);
        }
        return null;
    }

    // --- The teams -------------------------------------------------------------------------------

    /** The teams of these units, each party whole in one team; null if they do not fit. */
    private List<List<QueueEntry>> teams(List<Unit> units) {
        boolean alone = true;
        for (Unit unit : units) {
            alone &= unit.size() == 1;
        }
        if (alone) {
            List<QueueEntry> players = new ArrayList<>();
            for (Unit unit : units) {
                players.add(unit.entries.get(0));
            }
            return mode.ranked() ? snake(players) : dealt(players);
        }
        List<List<QueueEntry>> teams = mode.ranked() ? balancedParties(units) : partiesInTurn(units);
        return teams != null ? teams : packed(units);
    }

    /** In turn: the first player in the first team, the second in the second, and so on. */
    private List<List<QueueEntry>> dealt(List<QueueEntry> players) {
        List<List<QueueEntry>> teams = emptyTeams();
        for (int i = 0; i < players.size(); i++) {
            teams.get(i % mode.teams()).add(players.get(i));
        }
        return teams;
    }

    /** Snake draft by rating: 1-2-2-1 for two teams, so that the totals stay close. */
    private List<List<QueueEntry>> snake(List<QueueEntry> players) {
        List<QueueEntry> sorted = new ArrayList<>(players);
        sorted.sort(Comparator.comparingInt(QueueEntry::rating).reversed());
        List<List<QueueEntry>> teams = emptyTeams();
        int teamCount = mode.teams();
        for (int i = 0; i < sorted.size(); i++) {
            int round = i / teamCount;
            int position = i % teamCount;
            int team = round % 2 == 0 ? position : teamCount - 1 - position;
            teams.get(team).add(sorted.get(i));
        }
        return teams;
    }

    /** In the order they came, each party or player in the team with the most room left; null if one does not fit. */
    private List<List<QueueEntry>> partiesInTurn(List<Unit> units) {
        List<List<QueueEntry>> teams = emptyTeams();
        for (Unit unit : units) {
            int best = -1;
            for (int t = 0; t < teams.size(); t++) {
                int room = mode.playersPerTeam() - teams.get(t).size();
                if (room >= unit.size() && (best < 0 || room > mode.playersPerTeam() - teams.get(best).size())) {
                    best = t;
                }
            }
            if (best < 0) {
                return null;
            }
            teams.get(best).addAll(unit.entries);
        }
        return teams;
    }

    /**
     * The biggest parties first, the best rated first, each in the team with room whose total rating
     * is the lowest; null if one does not fit.
     */
    private List<List<QueueEntry>> balancedParties(List<Unit> units) {
        List<Unit> sorted = new ArrayList<>(units);
        sorted.sort(Comparator.comparingInt(Unit::size).reversed()
                .thenComparing(Comparator.comparingDouble((Unit unit) -> unit.rating).reversed()));
        List<List<QueueEntry>> teams = emptyTeams();
        long[] totals = new long[teams.size()];
        for (Unit unit : sorted) {
            int best = -1;
            for (int t = 0; t < teams.size(); t++) {
                if (mode.playersPerTeam() - teams.get(t).size() >= unit.size() && (best < 0 || totals[t] < totals[best])) {
                    best = t;
                }
            }
            if (best < 0) {
                return null;
            }
            teams.get(best).addAll(unit.entries);
            for (QueueEntry entry : unit.entries) {
                totals[best] += entry.rating();
            }
        }
        return teams;
    }

    /** Any way the parties fit in the teams (the biggest first), or null if none. */
    private List<List<QueueEntry>> packed(List<Unit> units) {
        List<Unit> sorted = new ArrayList<>(units);
        sorted.sort(Comparator.comparingInt(Unit::size).reversed());
        List<List<QueueEntry>> teams = emptyTeams();
        return pack(sorted, 0, teams) ? teams : null;
    }

    private boolean pack(List<Unit> units, int index, List<List<QueueEntry>> teams) {
        if (index == units.size()) {
            return true;
        }
        Unit unit = units.get(index);
        Set<Integer> triedSizes = new HashSet<>();
        for (List<QueueEntry> team : teams) {
            // Two teams with as many players are the same choice: try one.
            if (mode.playersPerTeam() - team.size() < unit.size() || !triedSizes.add(team.size())) {
                continue;
            }
            team.addAll(unit.entries);
            if (pack(units, index + 1, teams)) {
                return true;
            }
            team.subList(team.size() - unit.size(), team.size()).clear();
        }
        return false;
    }

    private List<List<QueueEntry>> emptyTeams() {
        List<List<QueueEntry>> teams = new ArrayList<>();
        for (int i = 0; i < mode.teams(); i++) {
            teams.add(new ArrayList<>());
        }
        return teams;
    }
}
