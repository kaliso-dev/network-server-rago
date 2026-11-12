package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class TrainingsTest {

    @Test
    void aSettingKeepsItsDefaultForTheReset() {
        Training.Setting knockback = new Training.Setting("Knockback", 1, 5, 3, false);
        assertTrue(knockback.step(1));
        assertTrue(knockback.step(1));
        assertFalse(knockback.step(1), "already at its highest");
        assertEquals(5, knockback.value);
        assertEquals(3, knockback.initial, "shift and a right click on the blaze rod give it back");
        assertEquals("KB stick", TrainingPrefs.key("KB stick"));
        assertEquals("Power I_5", TrainingPrefs.key("Power I.5"), "no dot: in YAML a dot is a path");
    }

    @Test
    void theControlsGoWhereThePlayerPutThem() {
        java.util.Set<Training.Control> all = java.util.EnumSet.allOf(Training.Control.class);
        java.util.Set<Training.Control> noDye = java.util.EnumSet.of(Training.Control.TRAININGS,
                Training.Control.SETTINGS, Training.Control.RESTART);
        // by default, side by side at the end of the hotbar: the red dye in 4, the green one 5, the
        // compass 6, the comparator 7, the blaze rod 8
        Map<Training.Control, Integer> at = Training.placement(null, all);
        assertEquals(Integer.valueOf(4), at.get(Training.Control.PREVIOUS));
        assertEquals(Integer.valueOf(5), at.get(Training.Control.NEXT));
        assertEquals(Integer.valueOf(6), at.get(Training.Control.TRAININGS));
        assertEquals(Integer.valueOf(8), at.get(Training.Control.RESTART));
        at = Training.placement(null, noDye);
        assertEquals(3, at.size(), "no dye: three controls");
        assertEquals(Integer.valueOf(6), at.get(Training.Control.TRAININGS));
        // no blaze rod (a fall gives the next attempt): the others move up to the last slot
        at = Training.placement(null, java.util.EnumSet.of(Training.Control.PREVIOUS, Training.Control.NEXT,
                Training.Control.TRAININGS, Training.Control.SETTINGS));
        assertEquals(Integer.valueOf(5), at.get(Training.Control.PREVIOUS));
        assertEquals(Integer.valueOf(8), at.get(Training.Control.SETTINGS));
        // the blaze rod put in 0 and the compass in the inventory: they stay there
        Map<String, Integer> saved = new java.util.HashMap<>();
        saved.put("recommencer", 0);
        saved.put("trainings", 20);
        saved.put("quitter", 3);
        at = Training.placement(saved, all);
        assertEquals(Integer.valueOf(0), at.get(Training.Control.RESTART));
        assertEquals(Integer.valueOf(20), at.get(Training.Control.TRAININGS));
        assertEquals(Integer.valueOf(7), at.get(Training.Control.SETTINGS), "not moved: its own slot");
        // the blaze rod put where the green dye goes: the dye takes the first free slot from the end
        saved.put("recommencer", 5);
        at = Training.placement(saved, all);
        assertEquals(Integer.valueOf(5), at.get(Training.Control.RESTART));
        assertEquals(Integer.valueOf(8), at.get(Training.Control.NEXT), "the first free slot from the end of the hotbar");
        assertEquals(5, new java.util.HashSet<>(at.values()).size(), "never two controls in a slot");
        // a control without a place of its own never takes the one of another that has it
        saved.clear();
        saved.put("suivant", 4);
        at = Training.placement(saved, all);
        assertEquals(Integer.valueOf(4), at.get(Training.Control.NEXT));
        assertEquals(Integer.valueOf(5), at.get(Training.Control.PREVIOUS), "the slot left free by the green dye");
        assertEquals(Integer.valueOf(8), at.get(Training.Control.RESTART));
        assertEquals(5, new java.util.HashSet<>(at.values()).size());
        // a slot out of the inventory is not kept
        saved.put("recommencer", 40);
        assertEquals(Integer.valueOf(8), Training.placement(saved, all).get(Training.Control.RESTART));
    }

    @Test
    void theSidebarKeepsTheTopTheBestAndThePingWithinTwelveLines() {
        // mode, "Mode:", blank, 2 lines of the attempt, blank, top 3, blank, you, ping: 12 lines, all kept
        List<String> fits = sidebar(2, 3);
        assertEquals(12, fits.size());
        assertEquals(fits, Training.fit(fits, 3));
        assertEquals(java.util.Arrays.asList("#3", "", "you"), fits.subList(8, 11), "room between the third and the best");
        // 4 lines of the attempt: the blank and the "Mode:" line go, the top 3 and the room under it stay
        List<String> fitted = Training.fit(sidebar(4, 3), 3);
        assertEquals(java.util.Arrays.asList("mode", "a0", "a1", "a2", "a3", "", "#1", "#2", "#3", "", "you", "ping"), fitted);
        // more: the third, then the second of the top go, never the best nor the ping
        fitted = Training.fit(sidebar(6, 3), 3);
        assertEquals(java.util.Arrays.asList("mode", "a0", "a1", "a2", "a3", "a4", "a5", "", "#1", "", "you", "ping"), fitted);
        // even more: the room under the top goes too
        fitted = Training.fit(sidebar(7, 3), 3);
        assertEquals(java.util.Arrays.asList("mode", "a0", "a1", "a2", "a3", "a4", "a5", "a6", "", "#1", "you", "ping"), fitted);
    }

    @Test
    void aSidebarWithoutRankingKeepsTheAttemptAndThePing() {
        List<String> lines = new java.util.ArrayList<>(java.util.Arrays.asList("mode", "Mode:", "", "a0", "a1", "a2", "", "ping"));
        assertEquals(lines, Training.fitHead(lines));
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
        lines.add("");
        lines.add("you");
        lines.add("ping");
        return lines;
    }

    @Test
    void theRankedDefenseIsEndStoneUnderGlass() {
        Map<String, Material> ranked = FastDefenseTraining.rankedDefense();
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
    void theDuoDefenseIsTheOwners() {
        Map<String, Material> duo = FastDefenseTraining.duoDefense();
        // "35 blocks": 12 end stone, 23 wool, as on the owner's map "Defense Claude"
        assertEquals(12, count(duo, Material.ENDER_STONE));
        assertEquals(23, count(duo, Material.WOOL));
        assertEquals(Material.ENDER_STONE, duo.get("0,2,0"), "on the bed");
        assertEquals(Material.ENDER_STONE, duo.get("0,1,-1"), "the end on the player's side");
        assertEquals(Material.ENDER_STONE, duo.get("0,1,2"), "past the head...");
        assertEquals(Material.ENDER_STONE, duo.get("0,1,3"), "...two of them");
        assertEquals(Material.ENDER_STONE, duo.get("1,1,2"));
        assertEquals(Material.WOOL, duo.get("0,1,4"));
        assertEquals(Material.WOOL, duo.get("0,3,2"), "the top");
        assertNull(duo.get("0,1,0"), "not on the bed");
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
