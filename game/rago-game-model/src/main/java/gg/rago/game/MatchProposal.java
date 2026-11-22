package gg.rago.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A match the matchmaker found: the mode, and the players of each team, as queue entries so that a
 * match that cannot start (no free arena) goes back to the queue with its original waiting times.
 * The players agree on the map: those who chose one chose the same. Each team is full, but in a
 * private game whose players chose their teams ({@link #withChosenTeams}). Immutable.
 */
public final class MatchProposal {

    private final GameMode mode;
    private final List<List<QueueEntry>> teams;

    public MatchProposal(GameMode mode, List<List<QueueEntry>> teams) {
        this(mode, teams, false);
    }

    /**
     * chosen: the teams the players of a private game chose (Bedwars Normal), each of 0 to
     * playersPerTeam players, two of them at least with players; else each team full.
     */
    private MatchProposal(GameMode mode, List<List<QueueEntry>> teams, boolean chosen) {
        this.mode = Objects.requireNonNull(mode, "mode");
        if (teams.size() != mode.teams()) {
            throw new IllegalArgumentException(mode + ": " + teams.size() + " teams instead of " + mode.teams());
        }
        List<List<QueueEntry>> copy = new ArrayList<>();
        int played = 0;
        for (List<QueueEntry> team : teams) {
            if (chosen ? team.size() > mode.playersPerTeam() : team.size() != mode.playersPerTeam()) {
                throw new IllegalArgumentException(mode + ": a team of " + team.size() + " instead of "
                        + (chosen ? "at most " : "") + mode.playersPerTeam());
            }
            if (!team.isEmpty()) {
                played++;
            }
            copy.add(Collections.unmodifiableList(new ArrayList<>(team)));
        }
        if (chosen && played < 2) {
            throw new IllegalArgumentException(mode + ": " + played + " team with players, two at least");
        }
        this.teams = Collections.unmodifiableList(copy);
    }

    /**
     * A private game whose players chose their teams (Bedwars Normal, in the Private games): the
     * mode's teams in their order (the colours of the map), each of 0 to playersPerTeam players, two
     * of them at least with players. An empty team is one nobody plays: the game goes without it.
     */
    public static MatchProposal withChosenTeams(GameMode mode, List<List<QueueEntry>> teams) {
        return new MatchProposal(mode, teams, true);
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

    /** The map the players chose (the first one who chose, the others chose the same or none), or null: any map. */
    public String map() {
        for (QueueEntry entry : entries()) {
            if (entry.map() != null) {
                return entry.map();
            }
        }
        return null;
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
