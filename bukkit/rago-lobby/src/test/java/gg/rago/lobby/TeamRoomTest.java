package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamRoomTest {

    private static List<UUID> party(int size) {
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            players.add(UUID.randomUUID());
        }
        return players;
    }

    private static List<Integer> sizes(List<List<UUID>> teams) {
        List<Integer> sizes = new ArrayList<>();
        for (List<UUID> team : teams) {
            sizes.add(team.size());
        }
        return sizes;
    }

    @Test
    void withoutChoicesTheTeamsFollowTheParty() {
        // the examples of the proposal the owner said yes to: 5 on a map of 2s, 6 on a map of 4s
        assertEquals(Arrays.asList(2, 2, 1, 0, 0, 0, 0, 0), sizes(new TeamRoom(party(5), 8, 2).teams()));
        assertEquals(Arrays.asList(3, 3, 0, 0), sizes(new TeamRoom(party(6), 4, 4).teams()));
        assertEquals(Arrays.asList(1, 1, 0, 0), sizes(new TeamRoom(party(2), 4, 4).teams()), "two teams at least");
        assertEquals(Arrays.asList(2, 2, 2, 2, 2, 2, 2, 2), sizes(new TeamRoom(party(16), 8, 2).teams()));
        assertEquals(Arrays.asList(4, 4, 4, 4), sizes(new TeamRoom(party(16), 4, 4).teams()));
    }

    @Test
    void eachPlayerPlaysTheTeamTheyChose() {
        List<UUID> players = party(4);
        TeamRoom room = new TeamRoom(players, 8, 2);
        assertEquals(TeamRoom.Choice.CHOSEN, room.choose(players.get(0), 3));
        assertEquals(TeamRoom.Choice.CHOSEN, room.choose(players.get(1), 3));
        assertEquals(TeamRoom.Choice.FULL, room.choose(players.get(2), 3), "two in a team of 2s");
        assertEquals(TeamRoom.Choice.ALREADY, room.choose(players.get(0), 3));
        assertEquals(TeamRoom.Choice.CHOSEN, room.choose(players.get(2), 6));
        List<List<UUID>> teams = room.teams();
        assertEquals(Arrays.asList(players.get(0), players.get(1)), teams.get(3));
        // the last one, who chose none, joins the emptiest team in play: the one of the third
        assertEquals(Arrays.asList(players.get(2), players.get(3)), teams.get(6));
        assertEquals(Arrays.asList(0, 0, 0, 2, 0, 0, 2, 0), sizes(teams));
    }

    @Test
    void aPlayerMayChangeTeam() {
        List<UUID> players = party(3);
        TeamRoom room = new TeamRoom(players, 4, 4);
        room.choose(players.get(0), 0);
        assertEquals(TeamRoom.Choice.CHOSEN, room.choose(players.get(0), 2));
        assertEquals(2, room.teamOf(players.get(0)));
        assertEquals(Arrays.asList(), room.membersOf(0));
        assertEquals(-1, room.teamOf(players.get(1)));
    }

    @Test
    void theWholePartyCannotChooseOneTeam() {
        List<UUID> players = party(3);
        TeamRoom room = new TeamRoom(players, 4, 4);
        room.choose(players.get(0), 1);
        room.choose(players.get(1), 1);
        assertEquals(TeamRoom.Choice.ALONE, room.choose(players.get(2), 1), "a game needs two teams");
        // the one who chose none opens a second team
        assertEquals(Arrays.asList(1, 2, 0, 0), sizes(room.teams()));
    }

    @Test
    void choicesCanOpenMoreTeamsThanNeeded() {
        List<UUID> players = party(3);
        TeamRoom room = new TeamRoom(players, 8, 2);
        for (int i = 0; i < 3; i++) {
            room.choose(players.get(i), 7 - i);
        }
        assertEquals(Arrays.asList(0, 0, 0, 0, 0, 1, 1, 1), sizes(room.teams()));
    }

    @Test
    void refusesWhatIsNotATeamOfTheRoom() {
        List<UUID> players = party(2);
        TeamRoom room = new TeamRoom(players, 4, 4);
        assertEquals(TeamRoom.Choice.UNKNOWN, room.choose(players.get(0), 4));
        assertEquals(TeamRoom.Choice.UNKNOWN, room.choose(UUID.randomUUID(), 0));
        assertThrows(IllegalArgumentException.class, () -> new TeamRoom(party(17), 8, 2), "16 at most on a map of 2s");
        assertThrows(IllegalArgumentException.class, () -> new TeamRoom(party(1), 8, 2), "a party of two at least");
    }
}
