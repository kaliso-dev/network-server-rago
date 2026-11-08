package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LayoutTest {

    @Test
    void selectorPutsSevenModesPerRowInsideAFrame() {
        assertEquals(10, ModeMenu.slotOf(0));
        assertEquals(16, ModeMenu.slotOf(6));
        assertEquals(19, ModeMenu.slotOf(7));
        assertEquals(52, ModeMenu.slotOf(LobbySettings.MAX_MODES - 1));
        assertEquals(43, ModeMenu.slotOf(LobbySettings.MAX_MODES_PER_CATEGORY - 1), "above the back button's row");
        assertEquals(12, ModeMenu.categorySlot(0, 3));
        assertEquals(13, ModeMenu.categorySlot(1, 3));
        assertEquals(10, ModeMenu.categorySlot(0, 7));
        assertEquals(49, ModeMenu.backSlot(54));
    }

    @Test
    void scoreboardClock() {
        assertEquals("0:00", LobbyScoreboard.clock(0));
        assertEquals("0:42", LobbyScoreboard.clock(42_999));
        assertEquals("12:05", LobbyScoreboard.clock(725_000));
        assertEquals("0:00", LobbyScoreboard.clock(-1));
    }
}
