package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The anti full box of Block Placement: no block may close a player in completely. */
class FullBoxTest {

    /** The blocks placed, as "x y z". */
    private static FullBox.Solid of(Set<String> blocks) {
        return (x, y, z) -> blocks.contains(x + " " + y + " " + z);
    }

    /** A player whose feet are in the block (0, 1, 0): every cell of their box but the one left out. */
    private static Set<String> boxBut(int[] left) {
        Set<String> blocks = new HashSet<>();
        for (int[] cell : FullBox.AROUND) {
            if (cell != left) {
                blocks.add(cell[0] + " " + (1 + cell[1]) + " " + cell[2]);
            }
        }
        return blocks;
    }

    @Test
    void theLastBlockOfABoxIsRefused() {
        for (int[] last : FullBox.AROUND) {
            Set<String> blocks = boxBut(last);
            assertTrue(FullBox.closes(0, 1, 0, last[0], 1 + last[1], last[2], of(blocks)),
                    "the last cell " + last[0] + " " + last[1] + " " + last[2]);
        }
    }

    @Test
    void aBoxWithAWayOutIsAllowed() {
        // two cells open: placing one leaves the other
        Set<String> blocks = boxBut(FullBox.AROUND[0]);
        blocks.remove("0 3 0");
        assertFalse(FullBox.closes(0, 1, 0, 1, 1, 0, of(blocks)), "nothing above the head yet");
        // a block next to the box, not of it
        assertFalse(FullBox.closes(0, 1, 0, 2, 1, 0, of(boxBut(FullBox.AROUND[0]))), "not a cell of the box");
        // the floor is not of the box: a full box needs no block under the feet
        assertFalse(FullBox.closes(0, 1, 0, 0, 0, 0, of(boxBut(null))), "under the feet");
        // the head's sides and the top closed, the feet's open: walls of one block are no box
        Set<String> high = new HashSet<>();
        for (int[] cell : FullBox.AROUND) {
            if (cell[1] > 0) {
                high.add(cell[0] + " " + (1 + cell[1]) + " " + cell[2]);
            }
        }
        high.remove("0 3 0");
        assertFalse(FullBox.closes(0, 1, 0, 0, 3, 0, of(high)), "the feet's sides open");
    }
}
