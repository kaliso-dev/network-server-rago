package gg.rago.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A match the matchmaker found: the mode, and the players of each team, as queue entries so that a
 * match that cannot start (no free arena) goes back to the queue with its original waiting times.
 * Immutable.
 */
public final class MatchProposal {

    private final GameMode mode;
    private final List<List<QueueEntry>> teams;

    public MatchProposal(GameMode mode, List<List<QueueEntry>> teams) {
        this.mode = Objects.requireNonNull(mode, "mode");
        if (teams.size() != mode.teams()) {
            throw new IllegalArgumentException(mode + ": " + teams.size() + " teams instead of " + mode.teams());
        }
        List<List<QueueEntry>> copy = new ArrayList<>();
        for (List<QueueEntry> team : teams) {
            if (team.size() != mode.playersPerTeam()) {
                throw new IllegalArgumentException(mode + ": a team of " + team.size() + " instead of "
                        + mode.playersPerTeam());
            }
            copy.add(Collections.unmodifiableList(new ArrayList<>(team)));
        }
        this.teams = Collections.unmodifiableList(copy);
    }

    public GameMode mode() {
        return mode;
    }

    public List<List<QueueEntry>> teams() {
        return teams;
    }

    /** Every entry, team after team. */
    public List<QueueEntry> entries() {
        List<QueueEntry> all = new ArrayList<>();
        for (List<QueueEntry> team : teams) {
            all.addAll(team);
        }
        return all;
    }

    /** Every player, team after team. */
    public List<UUID> players() {
        List<UUID> all = new ArrayList<>();
        for (QueueEntry entry : entries()) {
            all.add(entry.player());
        }
        return all;
    }

    @Override
    public String toString() {
        return mode + " " + teams;
    }
}
