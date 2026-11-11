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
        assertEquals(11, ModeMenu.categorySlot(0, 3), "the practice on the left");
        assertEquals(13, ModeMenu.categorySlot(1, 3));
        assertEquals(15, ModeMenu.categorySlot(2, 3), "the duels on the right");
        assertEquals(12, ModeMenu.categorySlot(0, 2));
        assertEquals(14, ModeMenu.categorySlot(1, 2));
        assertEquals(10, ModeMenu.categorySlot(0, 4));
        assertEquals(16, ModeMenu.categorySlot(3, 4));
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

    @Test
    void npcsFaceTheSpawn() {
        assertEquals(0f, LobbyNpcs.yawTowards(0, 0, 0, 10), 0.001f, "south");
        assertEquals(90f, LobbyNpcs.yawTowards(0, 0, -10, 0), 0.001f, "west");
        assertEquals(-90f, LobbyNpcs.yawTowards(0, 0, 10, 0), 0.001f, "east");
        assertEquals(180f, Math.abs(LobbyNpcs.yawTowards(0, 0, 0, -10)), 0.001f, "north");
    }
}
