package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The sizes of the invis practice and of the Bed Break Simulator: 1v1 to 1v4, or open. */
class LineupTest {

    @Test
    void aSizeStartsAsSoonAsItsPlayersAreIn() {
        Lineup oneVersusThree = new Lineup(4, InvisGame.MAX_PLAYERS);
        assertFalse(oneVersusThree.open());
        assertEquals(4, oneVersusThree.capacity());
        assertEquals("1v3", oneVersusThree.format());
        assertEquals("4 players", oneVersusThree.players());
        assertFalse(oneVersusThree.joinable(1), "its players came together: nobody joins it");
        assertEquals(Lineup.FULL_WAIT_SECONDS, oneVersusThree.startIn(4), "all in: 3 seconds");
        assertEquals(Lineup.FULL_WAIT_SECONDS, oneVersusThree.startIn(3), "one left: the others play at once");
        assertEquals(-1, oneVersusThree.startIn(1), "alone: it ends");
        assertEquals("1v1", new Lineup(2, BedBreakGame.MAX_PLAYERS).format());
        assertEquals("1v4", new Lineup(5, BedBreakGame.MAX_PLAYERS).format());
        assertTrue(new Lineup(2, 5).when().contains("as soon as the 2 players are in"));
    }

    @Test
    void openItWaitsForPlayersUpToFive() {
        Lineup open = new Lineup(1, InvisGame.MAX_PLAYERS);
        assertTrue(open.open());
        assertEquals(5, open.capacity(), "five at most now, as the Bed Break Simulator");
        assertEquals(5, InvisGame.MAX_PLAYERS);
        assertEquals("1v1 to 1v4", open.format());
        assertEquals("2 to 5 players", open.players());
        assertTrue(open.joinable(4));
        assertFalse(open.joinable(5), "full");
        assertEquals(-1, open.startIn(1), "waits for a second player");
        assertEquals(Lineup.WAIT_SECONDS, open.startIn(2));
        assertEquals(Lineup.FULL_WAIT_SECONDS, open.startIn(5));
        assertTrue(new Lineup(0, 5).open(), "no size: open");
    }
}
