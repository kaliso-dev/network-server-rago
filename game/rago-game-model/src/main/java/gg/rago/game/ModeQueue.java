package gg.rago.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
 * <p>Not thread-safe: on a Bukkit server it lives on the main thread, where it only does a few
 * comparisons per second.
 */
public final class ModeQueue {

    private static final Comparator<QueueEntry> BY_JOIN_TIME =
            Comparator.comparingLong(QueueEntry::joinedAtMillis);

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

    public boolean remove(UUID player) {
        QueueEntry entry = find(player);
        return entry != null && waiting.remove(entry);
    }

    /** The waiting entries, oldest first. */
    public List<QueueEntry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(waiting));
    }

    /** Takes out every match that can start now and returns them, the oldest players first. */
    public List<MatchProposal> poll(long nowMillis) {
        return mode.ranked() ? pollRanked(nowMillis) : pollInOrder();
    }

    private List<MatchProposal> pollInOrder() {
        List<MatchProposal> matches = new ArrayList<>();
        int needed = mode.playersPerMatch();
        while (waiting.size() >= needed) {
            List<QueueEntry> players = new ArrayList<>(waiting.subList(0, needed));
            waiting.subList(0, needed).clear();
            List<List<QueueEntry>> teams = emptyTeams();
            for (int i = 0; i < players.size(); i++) {
                teams.get(i % mode.teams()).add(players.get(i));
            }
            matches.add(new MatchProposal(mode, teams));
        }
        return matches;
    }

    private List<MatchProposal> pollRanked(long nowMillis) {
        List<MatchProposal> matches = new ArrayList<>();
        int needed = mode.playersPerMatch();
        Set<UUID> taken = new HashSet<>();
        for (QueueEntry anchor : waiting) {
            if (taken.contains(anchor.player())) {
                continue;
            }
            int anchorWidth = window.width(anchor.waitedMillis(nowMillis));
            List<QueueEntry> candidates = new ArrayList<>();
            for (QueueEntry other : waiting) {
                if (other == anchor || taken.contains(other.player())) {
                    continue;
                }
                int gap = Math.abs(other.rating() - anchor.rating());
                int width = Math.min(anchorWidth, window.width(other.waitedMillis(nowMillis)));
                if (gap <= width) {
                    candidates.add(other);
                }
            }
            if (candidates.size() < needed - 1) {
                continue;
            }
            // Closest rating first; equal gaps go to whoever waited longest (the list is stable).
            candidates.sort(Comparator.comparingInt(other -> Math.abs(other.rating() - anchor.rating())));
            List<QueueEntry> players = new ArrayList<>();
            players.add(anchor);
            players.addAll(candidates.subList(0, needed - 1));
            for (QueueEntry entry : players) {
                taken.add(entry.player());
            }
            matches.add(new MatchProposal(mode, balance(players)));
        }
        waiting.removeIf(entry -> taken.contains(entry.player()));
        return matches;
    }

    /** Snake draft by rating: 1-2-2-1 for two teams, so that the totals stay close. */
    private List<List<QueueEntry>> balance(List<QueueEntry> players) {
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

    private List<List<QueueEntry>> emptyTeams() {
        List<List<QueueEntry>> teams = new ArrayList<>();
        for (int i = 0; i < mode.teams(); i++) {
            teams.add(new ArrayList<>());
        }
        return teams;
    }
}
