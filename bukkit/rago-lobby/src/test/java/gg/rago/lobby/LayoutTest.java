package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
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
    void theCompassShowsTheCategoriesInItsMiddleRow() {
        // without boussole.haut, as before 22/11 (17h54): eight categories side by side, Duels the third
        assertEquals(9, ModeMenu.categorySlot(0, 8), "Practice on the left");
        assertEquals(11, ModeMenu.categorySlot(2, 8), "Duels");
        assertEquals(15, ModeMenu.categorySlot(6, 8), "Party");
        assertEquals(16, ModeMenu.categorySlot(7, 8), "Ranked");
        for (int count = 1; count <= LobbySettings.MAX_CATEGORIES; count++) {
            for (int i = 0; i < count; i++) {
                int slot = ModeMenu.categorySlot(i, count);
                assertTrue(slot >= 9 && slot < 18, count + " categories: the middle row of three, under Popular now");
            }
        }
    }

    @Test
    void theRowsOfTheCompassAreCentredAndOneSlotApartUpToFive() {
        // the owner's ask of 22/11 (17h54): Red Rover, Party, 3 Players and Ranked on top, the five others under them
        assertEquals(Arrays.asList(1, 3, 5, 7), columns(4), "the top row");
        assertEquals(Arrays.asList(0, 2, 4, 6, 8), columns(5), "the middle row: Duels in the middle (column 4, slot 13)");
        assertEquals(Collections.singletonList(4), columns(1));
        assertEquals(Arrays.asList(3, 5), columns(2));
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), columns(6), "side by side beyond five");
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8), columns(9));
        for (int count = 1; count <= 9; count++) {
            assertEquals(count, new java.util.HashSet<>(columns(count)).size(), count + ": one column each");
        }
    }

    private static java.util.List<Integer> columns(int count) {
        java.util.List<Integer> columns = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            int column = ModeMenu.compassColumn(i, count);
            assertTrue(column >= 0 && column < 9, count + ": column " + column);
            columns.add(column);
        }
        return columns;
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

    @Test
    void theCompassIsSmallerAndCentred() {
        // the owner's ask of 27/11: five rows, the top row, the categories in the middle one, Popular now at the bottom
        assertEquals(45, ModeMenu.COMPASS_SIZE);
        assertEquals(ModeMenu.COMPASS_SIZE / 9 / 2, ModeMenu.CENTRE_ROW, "the categories in the middle row");
    }

    @Test
    void anItemHoldsThePlayersInAGameOfIt() {
        // as on Minemen (the owner, 27/11): no number from 0 to 1, then the players, 64 at most
        assertEquals(1, ModeMenu.stack(0));
        assertEquals(1, ModeMenu.stack(1));
        assertEquals(32, ModeMenu.stack(32));
        assertEquals(64, ModeMenu.stack(200));
    }

    @Test
    void theDailyChestLooksTheWayOfItsPoint() {
        assertEquals(3, DailyChest.chestFacing(0f), "south");
        assertEquals(4, DailyChest.chestFacing(89.3f), "west: the centre of the lobby, from the chest of the test server");
        assertEquals(4, DailyChest.chestFacing(-270.67f), "west, the yaw of its point");
        assertEquals(2, DailyChest.chestFacing(180f), "north");
        assertEquals(5, DailyChest.chestFacing(270.87f), "east");
        assertEquals(5, DailyChest.chestFacing(-90f), "east");
    }

    @Test
    void anNpcLooksTheWayOfItsPointWithRegardPoint() {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("pnj.newshop.regard", "point");
        yaml.set("pnj.practice.regard", "spawn");
        yaml.set("pnj.duels.nom", "Duels");
        assertEquals(Collections.singleton("newshop"), LobbySettings.readOwnWay(yaml.getConfigurationSection("pnj")));
        yaml.set("pnj.duels.regard", "centre");
        try {
            LobbySettings.readOwnWay(yaml.getConfigurationSection("pnj"));
            throw new AssertionError("regard: point or spawn only");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("pnj.duels.regard"));
        }
    }

    @Test
    void anIconCanHaveAColour() {
        assertEquals(14, LobbySettings.iconData("categories.arenes", "WOOL:14"), "a red wool");
        assertEquals(4, LobbySettings.iconData("modes.block-fight-1v1", "WOOL:4"), "a yellow wool");
        assertEquals(0, LobbySettings.iconData("modes.sumo", "LEASH"));
        try {
            LobbySettings.iconData("modes.sumo", "WOOL:16");
            throw new AssertionError("0 to 15 only");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("modes.sumo.icone"));
        }
    }
}
