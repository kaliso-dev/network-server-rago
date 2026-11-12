package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void popularNowIsTheTopRowAboveTheCategories() {
        assertEquals(4, ModeMenu.popularSlot(0, 1), "one item: the middle of the top row");
        assertEquals(2, ModeMenu.popularSlot(0, 3));
        assertEquals(6, ModeMenu.popularSlot(2, 3));
        assertEquals(1, ModeMenu.popularSlot(0, 4), "three modes and the challenge of the day");
        assertEquals(7, ModeMenu.popularSlot(3, 4));
        for (int count = 1; count <= ModeMenu.MAX_POPULAR + 1; count++) {
            for (int i = 0; i < count; i++) {
                int slot = ModeMenu.popularSlot(i, count);
                assertTrue(slot >= 0 && slot < 9, count + " items: " + slot);
            }
        }
    }

    @Test
    void popularNowShowsTheModesWithTheMostPlayers() {
        java.util.Map<String, Integer> playing = new java.util.HashMap<>();
        java.util.Map<String, Integer> waiting = new java.util.HashMap<>();
        java.util.List<String> menu = java.util.Arrays.asList("sumo", "boxing", "clutch", "fireball", "bow-duel");
        java.util.function.ToIntFunction<String> inGame = id -> playing.getOrDefault(id, 0);
        java.util.function.ToIntFunction<String> inQueue = id -> waiting.getOrDefault(id, 0);
        assertEquals(java.util.Collections.emptyList(), ModeMenu.popular(menu, inGame, inQueue, 3), "nobody: no mode");
        playing.put("clutch", 1);
        playing.put("bow-duel", 1);
        assertEquals(java.util.Arrays.asList("clutch", "bow-duel"), ModeMenu.popular(menu, inGame, inQueue, 3),
                "one player is enough; equals keep the order of the menu");
        playing.remove("bow-duel");
        waiting.put("bow-duel", 1);
        assertEquals(java.util.Arrays.asList("bow-duel", "clutch"), ModeMenu.popular(menu, inGame, inQueue, 3),
                "as many players, but one waits: a game soon");
        playing.put("sumo", 5);
        playing.put("boxing", 3);
        waiting.put("boxing", 1);
        playing.put("fireball", 2);
        waiting.put("fireball", 2);
        assertEquals(java.util.Arrays.asList("sumo", "fireball", "boxing"), ModeMenu.popular(menu, inGame, inQueue, 3),
                "the most players first (playing and waiting), three at most; at 4 each, the most waiting first (a game soon)");
        assertEquals(java.util.Arrays.asList("sumo"), ModeMenu.popular(menu, inGame, inQueue, 1));
        assertEquals(java.util.Collections.emptyList(), ModeMenu.popular(menu, inGame, inQueue, 0));
        assertEquals(java.util.Arrays.asList("clutch"), ModeMenu.popular(java.util.Arrays.asList("clutch", "clutch"), inGame,
                inQueue, 3), "a mode once");
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
