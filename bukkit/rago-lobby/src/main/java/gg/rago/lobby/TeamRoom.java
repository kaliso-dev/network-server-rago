package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The teams a party chooses before its private game of Bedwars Normal (the owner's answers of
 * 22/11): the teams of the mode, in the order of the map's colours (8 teams of 2 on a map of 2s, 4
 * of 4 on a map of 4s), each player in the one they click. A team full refuses them, and so does a
 * team that would then hold the whole party (a game needs two teams). When the countdown ends,
 * those who chose none fill the teams as on Hypixel: as many teams as the party needs at the size
 * of the mode's (two at least), each in the emptiest of them, the first in the order of the colours
 * when two are as empty. Pure logic, for the tests.
 */
final class TeamRoom {

    /** What a click on a team did. */
    enum Choice {
        /** In the team now. */
        CHOSEN,
        /** Already in it. */
        ALREADY,
        /** The team is full. */
        FULL,
        /** Everyone would be in this team: a game needs two. */
        ALONE,
        /** No such team, or not a player of the room. */
        UNKNOWN
    }

    private final int teams;
    private final int size;
    private final List<UUID> players;
    private final Map<UUID, Integer> chosen = new LinkedHashMap<>();

    /** The players in the order of the party (its leader first), choosing among this many teams of this size. */
    TeamRoom(List<UUID> players, int teams, int size) {
        if (teams < 2 || size < 1) {
            throw new IllegalArgumentException("two teams of one player at least");
        }
        if (players.size() < 2 || players.size() > teams * size) {
            throw new IllegalArgumentException(players.size() + " players for " + teams + " teams of " + size);
        }
        this.players = Collections.unmodifiableList(new ArrayList<>(players));
        this.teams = teams;
        this.size = size;
    }

    List<UUID> players() {
        return players;
    }

    int teamCount() {
        return teams;
    }

    int teamSize() {
        return size;
    }

    /** The team this player chose, or -1. */
    int teamOf(UUID player) {
        Integer team = chosen.get(player);
        return team == null ? -1 : team;
    }

    /** The players who chose this team, in the order they did. */
    List<UUID> membersOf(int team) {
        List<UUID> members = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : chosen.entrySet()) {
            if (entry.getValue() == team) {
                members.add(entry.getKey());
            }
        }
        return members;
    }

    /** The player clicks this team (0 for the first colour). */
    Choice choose(UUID player, int team) {
        if (team < 0 || team >= teams || !players.contains(player)) {
            return Choice.UNKNOWN;
        }
        if (teamOf(player) == team) {
            return Choice.ALREADY;
        }
        List<UUID> members = membersOf(team);
        if (members.size() >= size) {
            return Choice.FULL;
        }
        if (members.size() + 1 == players.size()) {
            return Choice.ALONE;
        }
        // in the order of the choices: a player who changes team goes to its end
        chosen.remove(player);
        chosen.put(player, team);
        return Choice.CHOSEN;
    }

    /** As many teams as the party needs at the size of the mode's, two at least. */
    int teamsNeeded() {
        return Math.max(2, (players.size() + size - 1) / size);
    }

    /**
     * The teams of the game, in the order of the colours: those chosen, then those who chose none,
     * in the order of the party, each in the emptiest team (see the class). Empty teams stay in
     * the list, empty: nobody plays them.
     */
    List<List<UUID>> teams() {
        List<List<UUID>> result = new ArrayList<>();
        for (int t = 0; t < teams; t++) {
            result.add(membersOf(t));
        }
        int needed = teamsNeeded();
        for (UUID player : players) {
            if (chosen.containsKey(player)) {
                continue;
            }
            int used = 0;
            for (List<UUID> team : result) {
                used += team.isEmpty() ? 0 : 1;
            }
            int best = -1;
            for (int t = 0; t < teams; t++) {
                List<UUID> team = result.get(t);
                if (team.size() >= size || (team.isEmpty() && used >= needed)) {
                    continue;
                }
                if (best < 0 || team.size() < result.get(best).size()) {
                    best = t;
                }
            }
            if (best < 0) {
                // every team in play is full: one more
                for (int t = 0; t < teams && best < 0; t++) {
                    if (result.get(t).isEmpty()) {
                        best = t;
                    }
                }
            }
            result.get(best).add(player);
        }
        return result;
    }
}
