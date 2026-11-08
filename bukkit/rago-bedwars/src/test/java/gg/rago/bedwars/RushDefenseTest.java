package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class RushDefenseTest {

    /** A bed along x, at height 64: its foot at 10, its head at 11. */
    private static final List<int[]> BED = Arrays.asList(new int[] {10, 64, 0}, new int[] {11, 64, 0});

    private static Set<String> keys(List<int[]> blocks) {
        Set<String> keys = new HashSet<>();
        for (int[] block : blocks) {
            keys.add(block[0] + "," + block[1] + "," + block[2]);
        }
        return keys;
    }

    @Test
    void theFirstLayerCoversTheBedAndItsSides() {
        List<List<int[]>> layers = RushDefense.layers(BED, 1);
        assertEquals(1, layers.size());
        assertEquals(new HashSet<>(Arrays.asList("9,64,0", "12,64,0", "10,64,1", "10,64,-1", "11,64,1", "11,64,-1",
                "10,65,0", "11,65,0")), keys(layers.get(0)));
    }

    @Test
    void eachLayerWrapsTheOneBefore() {
        List<List<int[]>> layers = RushDefense.layers(BED, 3);
        assertEquals(8, layers.get(0).size());
        // at the bed's height 10, one higher 6, on top 2
        assertEquals(18, layers.get(1).size());
        Set<String> all = new HashSet<>();
        for (List<int[]> layer : layers) {
            for (String key : keys(layer)) {
                assertTrue(all.add(key), key + " is in two layers");
            }
        }
        assertTrue(all.contains("10,67,0"), "the third layer is over the second");
        assertTrue(all.contains("8,65,0"));
        for (List<int[]> layer : layers) {
            for (int[] block : layer) {
                assertTrue(block[1] >= 64, "nothing under the bed");
            }
        }
        assertFalse(all.contains("10,64,0"), "not on the bed itself");
        assertTrue(RushDefense.layers(BED, 0).isEmpty());
    }

    @Test
    void woolClayAndGlassTakeTheColourOfTheIsland() {
        assertTrue(RushDefense.coloured(Material.WOOL));
        assertTrue(RushDefense.coloured(Material.STAINED_CLAY));
        assertTrue(RushDefense.coloured(Material.STAINED_GLASS));
        assertFalse(RushDefense.coloured(Material.ENDER_STONE));
    }
}
