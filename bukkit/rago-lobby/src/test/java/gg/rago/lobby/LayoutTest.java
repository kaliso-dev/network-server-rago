package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LayoutTest {

    @Test
    void selectorPutsSevenModesPerRowInsideAFrame() {
        assertEquals(10, ModeMenu.slotOf(0));
        assertEquals(16, ModeMenu.slotOf(6));
        assertEquals(19, ModeMenu.slotOf(7));
    }

    @Test
    void scoreboardClock() {
        assertEquals("0:00", LobbyScoreboard.clock(0));
        assertEquals("0:42", LobbyScoreboard.clock(42_999));
        assertEquals("12:05", LobbyScoreboard.clock(725_000));
        assertEquals("0:00", LobbyScoreboard.clock(-1));
    }
}
