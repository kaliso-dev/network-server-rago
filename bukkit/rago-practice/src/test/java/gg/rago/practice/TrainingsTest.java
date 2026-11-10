package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class TrainingsTest {

    @Test
    void theSidebarKeepsTheTopTheBestAndThePingWithinTwelveLines() {
        // mode, "Mode:", blank, 2 lines of the attempt, blank, top 3, you, ping: 11 lines, all kept
        List<String> fits = sidebar(2, 3);
        assertEquals(fits, Training.fit(fits, 3));
        // 5 lines of the attempt: the blank and the "Mode:" line go, the top 3 stays
        List<String> fitted = Training.fit(sidebar(5, 3), 3);
        assertEquals(12, fitted.size());
        assertEquals(java.util.Arrays.asList("mode", "a0", "a1", "a2", "a3", "a4", "", "#1", "#2", "#3", "you", "ping"), fitted);
        // even more: the third, then the second of the top go, never the best nor the ping
        fitted = Training.fit(sidebar(7, 3), 3);
        assertEquals(java.util.Arrays.asList("mode", "a0", "a1", "a2", "a3", "a4", "a5", "a6", "", "#1", "you", "ping"), fitted);
    }

    private static List<String> sidebar(int attempt, int top) {
        List<String> lines = new java.util.ArrayList<>(java.util.Arrays.asList("mode", "Mode:", ""));
        for (int i = 0; i < attempt; i++) {
            lines.add("a" + i);
        }
        lines.add("");
        for (int i = 1; i <= top; i++) {
            lines.add("#" + i);
        }
        lines.add("you");
        lines.add("ping");
        return lines;
    }

    @Test
    void theRankedDefenseIsEndStoneUnderGlass() {
        Map<String, Material> ranked = FastDefenseTraining.defense(true);
        // "26 blocks": 12 end stone, 14 glass, as on the owner's pictures
        assertEquals(12, count(ranked, Material.ENDER_STONE));
        assertEquals(14, count(ranked, Material.STAINED_GLASS));
        assertEquals(Material.ENDER_STONE, ranked.get("0,2,0"), "on the bed");
        assertEquals(Material.STAINED_GLASS, ranked.get("0,3,0"), "over it");
        // two rows of end stone on each side, the outer one left bare
        for (int z : new int[] {-2, -1, 1, 2}) {
            assertEquals(Material.ENDER_STONE, ranked.get("1,1," + z));
        }
        assertNull(ranked.get("1,2,2"));
        assertNull(ranked.get("1,1,3"));
        assertEquals(Material.STAINED_GLASS, ranked.get("3,1,0"), "beyond the head");
    }

    @Test
    void theSoloDefenseIsEndStoneAndTheFrontThenWool() {
        Map<String, Material> solo = FastDefenseTraining.defense(false);
        // "32 blocks": 10 end stone, 22 wool; the bed from (0,1,0) to (0,1,1) points at the player (z below 0)
        assertEquals(10, count(solo, Material.ENDER_STONE));
        assertEquals(22, count(solo, Material.WOOL));
        assertEquals(Material.ENDER_STONE, solo.get("0,2,0"), "on the bed");
        assertEquals(Material.ENDER_STONE, solo.get("0,1,-1"), "the end on the player's side");
        assertEquals(Material.ENDER_STONE, solo.get("0,1,2"), "the far end");
        // each side one block longer on the player's side only
        for (int z = -1; z <= 1; z++) {
            assertEquals(Material.ENDER_STONE, solo.get("1," + "1," + z));
            assertEquals(Material.ENDER_STONE, solo.get("-1,1," + z));
        }
        assertEquals(Material.WOOL, solo.get("1,1,2"), "no surplus at the far end");
        assertEquals(Material.WOOL, solo.get("1,1,-2"), "the wool covers the surplus");
        assertEquals(Material.WOOL, solo.get("0,1,-2"));
        assertEquals(Material.WOOL, solo.get("0,1,3"));
        assertEquals(Material.WOOL, solo.get("0,3,1"));
        assertEquals(Material.WOOL, solo.get("2,1,0"));
        assertNull(solo.get("0,0,0"), "nothing under the ground");
        assertNull(solo.get("1,1,3"));
    }

    private static int count(Map<String, Material> defense, Material type) {
        int count = 0;
        for (Material each : defense.values()) {
            if (each == type) {
                count++;
            }
        }
        return count;
    }

    @Test
    void theDefensesTakeTheBlocksTheirDescriptionsSay() {
        // the layers of the defenses of the other trainings
        assertEquals(8, count(Shapes.defense(0, 1, 0, 1, 0, 1)));
        assertEquals(26, count(Shapes.defense(0, 1, 0, 1, 0, 2)));
        assertEquals(58, count(Shapes.defense(0, 1, 0, 1, 0, 3)));
    }

    private static int count(List<List<int[]>> layers) {
        int count = 0;
        for (List<int[]> layer : layers) {
            count += layer.size();
        }
        return count;
    }
}
