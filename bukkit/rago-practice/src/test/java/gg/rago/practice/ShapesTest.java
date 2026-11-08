package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShapesTest {

    @Test
    void theFirstLayerTouchesTheBed() {
        // a bed along x: foot at 0, head at 1
        List<List<int[]>> layers = Shapes.defense(0, 1, 0, 1, 0, 1);
        assertEquals(1, layers.size());
        // the two ends, the two sides of each half, and above each half
        assertEquals(8, layers.get(0).size());
    }

    @Test
    void layersNeverOverlapNorGoUnderOrIntoTheBed() {
        List<List<int[]>> layers = Shapes.defense(5, 1, -3, 0, 1, 3);
        Set<String> seen = new HashSet<>();
        for (List<int[]> layer : layers) {
            for (int[] place : layer) {
                assertTrue(place[1] >= 1, "never under the bed");
                assertFalse(place[0] == 5 && place[1] == 1 && (place[2] == -3 || place[2] == -2), "never in the bed");
                assertTrue(seen.add(place[0] + "," + place[1] + "," + place[2]), "each place once");
            }
        }
        // each layer covers the one under it: more places each time
        assertTrue(layers.get(1).size() > layers.get(0).size());
        assertTrue(layers.get(2).size() > layers.get(1).size());
    }

    @Test
    void theTopOfThreeLayersIsAboveTheBed() {
        List<List<int[]>> layers = Shapes.defense(0, 1, 0, 1, 0, 3);
        int top = 0;
        for (int[] place : layers.get(2)) {
            top = Math.max(top, place[1]);
        }
        assertEquals(4, top);
    }

    @Test
    void aFireballJumpGoesUpAndAway() {
        // the blast just under the feet, a little behind: up, and forward
        double[] push = Fireballs.push(0, 0, -0.5, 0, 0.5, 0);
        assertTrue(push[1] > 0.7);
        assertTrue(push[2] > 0);
        assertTrue(Fireballs.reaches(2.5, -1, 2.9));
        assertFalse(Fireballs.reaches(3.5, 0, 0));
    }
}
