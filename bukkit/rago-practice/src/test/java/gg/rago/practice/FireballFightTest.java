package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class FireballFightTest {

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    @Test
    void theDefenseOfTheMapOnABedIsFreedButNotTheIsland() {
        // the owner's map: a bed on grass, planks around it, end stone around the planks, a block of
        // end stone far away, and wool on the island under the bed (lower than the bed: not freed)
        Map<String, Material> map = new HashMap<>();
        for (int x = -6; x <= 7; x++) {
            for (int z = -6; z <= 6; z++) {
                map.put(key(x, 0, z), Material.GRASS);
            }
        }
        map.put(key(0, 0, 0), Material.WOOL);
        map.put(key(0, 1, 0), Material.BED_BLOCK);
        map.put(key(1, 1, 0), Material.BED_BLOCK);
        int[][] planks = {{-1, 1, 0}, {2, 1, 0}, {0, 1, 1}, {1, 1, 1}, {0, 1, -1}, {1, 1, -1}, {0, 2, 0}, {1, 2, 0}};
        int[][] endStone = {{-2, 1, 0}, {3, 1, 0}, {-1, 1, 1}, {2, 1, 1}, {0, 1, 2}, {1, 1, 2}, {0, 3, 0}, {1, 3, 0}};
        for (int[] at : planks) {
            map.put(key(at[0], at[1], at[2]), Material.WOOD);
        }
        for (int[] at : endStone) {
            map.put(key(at[0], at[1], at[2]), Material.ENDER_STONE);
        }
        map.put(key(9, 1, 0), Material.ENDER_STONE);
        List<int[]> beds = Arrays.asList(new int[] {0, 1, 0}, new int[] {1, 1, 0});
        Set<String> freed = new HashSet<>();
        for (int[] at : MapDefense.of(beds, (x, y, z) -> map.getOrDefault(key(x, y, z), Material.AIR))) {
            freed.add(key(at[0], at[1], at[2]));
        }
        assertEquals(planks.length + endStone.length, freed.size(), "the planks and the end stone, nothing else: " + freed);
        for (int[] at : planks) {
            assertTrue(freed.contains(key(at[0], at[1], at[2])), "a plank of the defense");
        }
        for (int[] at : endStone) {
            assertTrue(freed.contains(key(at[0], at[1], at[2])), "an end stone of the defense");
        }
    }

    @Test
    void aTntPushesAwayAndUpTheCloserTheStronger() {
        double[] onIt = Fireballs.tntPush(0.5, 64, 0.5, 0.5, 64, 0.5);
        assertEquals(0, onIt[0], 1e-9, "straight up when standing on it");
        assertEquals(1, onIt[1], 1e-9);
        double[] near = Fireballs.tntPush(0.5, 64, 0.5, 2.5, 64, 0.5);
        double[] far = Fireballs.tntPush(0.5, 64, 0.5, 6.5, 64, 0.5);
        assertTrue(near[0] > far[0] && far[0] > 0, "away from it, less from further");
        assertTrue(near[1] > 0 && far[1] > 0, "and up");
        assertNull(Fireballs.tntPush(0.5, 64, 0.5, 9.5, 64, 0.5), "out of reach at 9 blocks");
    }
}
