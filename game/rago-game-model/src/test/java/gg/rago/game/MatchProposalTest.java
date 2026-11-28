package gg.rago.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchProposalTest {

    private static final GameMode TWOS = new GameMode("bedwars-normal-2s", "Bedwars Normal 2s", 8, 2, false);

    private static List<QueueEntry> team(int players) {
        List<QueueEntry> team = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            team.add(new QueueEntry(UUID.randomUUID(), 1000, 0L, "Lotus"));
        }
        return team;
    }

    private static List<List<QueueEntry>> teams(int... sizes) {
        List<List<QueueEntry>> teams = new ArrayList<>();
        for (int size : sizes) {
            teams.add(team(size));
        }
        return teams;
    }

    @Test
    void aMatchOfTheQueueHasItsTeamsFull() {
        GameMode duo = new GameMode("duo", "Duo", 2, 2, false);
        assertEquals(4, new MatchProposal(duo, teams(2, 2)).players().size());
        assertThrows(IllegalArgumentException.class, () -> new MatchProposal(duo, teams(2, 1)));
        assertThrows(IllegalArgumentException.class, () -> new MatchProposal(duo, teams(2, 2, 2)));
    }

    @Test
    void chosenTeamsMayBeShortOrEmpty() {
        // five players of a party on a map of 2s: red and blue full, green with one, the rest empty
        MatchProposal match = MatchProposal.withChosenTeams(TWOS, teams(2, 2, 1, 0, 0, 0, 0, 0));
        assertEquals(8, match.teams().size(), "the teams of the map, in their order");
        assertEquals(5, match.players().size());
        assertEquals(Collections.emptyList(), match.teams().get(3));
        assertEquals("Lotus", match.map());
        assertEquals(2, MatchProposal.withChosenTeams(TWOS, teams(0, 0, 0, 1, 0, 0, 0, 1)).players().size(),
                "any two teams of the map");
    }

    @Test
    void chosenTeamsNeedTwoTeamsWithPlayersAndNoTeamTooBig() {
        assertThrows(IllegalArgumentException.class,
                () -> MatchProposal.withChosenTeams(TWOS, teams(2, 0, 0, 0, 0, 0, 0, 0)), "one team alone");
        assertThrows(IllegalArgumentException.class,
                () -> MatchProposal.withChosenTeams(TWOS, teams(3, 1, 0, 0, 0, 0, 0, 0)), "three in a team of two");
        assertThrows(IllegalArgumentException.class,
                () -> MatchProposal.withChosenTeams(TWOS, teams(1, 1)), "the eight teams of the mode");
        assertEquals(Arrays.asList(1, 1), Arrays.asList(
                MatchProposal.withChosenTeams(new GameMode("x", "X", 2, 4, false), teams(1, 1)).teams().get(0).size(),
                MatchProposal.withChosenTeams(new GameMode("x", "X", 2, 4, false), teams(1, 1)).teams().get(1).size()));
    }
}
