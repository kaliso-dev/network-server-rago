package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class InstanceTest {

    @Test
    void aBlockKeyGivesBackItsPosition() {
        int[][] blocks = {{0, 0, 0}, {-37, 64, -6}, {33554431, 255, -33554432}, {-1, 1, -1}, {1200, 90, -800}};
        for (int[] block : blocks) {
            long key = Instance.key(block[0], block[1], block[2]);
            assertEquals(block[0], Instance.xOf(key));
            assertEquals(block[1], Instance.yOf(key));
            assertEquals(block[2], Instance.zOf(key));
        }
        assertNotEquals(Instance.key(1, 64, 0), Instance.key(0, 64, 1));
        assertNotEquals(Instance.key(-1, 64, 0), Instance.key(1, 64, 0));
    }
}
