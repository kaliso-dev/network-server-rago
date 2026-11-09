package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The pearl practice on Sky Rise: its situations, the islands to reach, the lower limit. */
class PearlTrainingTest {

    @Test
    void eachSituationStartsOnAnIslandAndAimsAtAnother() {
        assertEquals(3, PearlTraining.SITUATIONS.length);
        for (PearlTraining.Situation situation : PearlTraining.SITUATIONS) {
            double[] start = situation.start;
            assertTrue(PearlTraining.inside(PearlTraining.BOX, start[0], start[1] - 1, start[2]), situation.id + ": in the copy");
            int[] target = situation.target;
            assertTrue(PearlTraining.inside(PearlTraining.BOX, target[0] + 1, PearlTraining.ORIGIN[1], target[2] + 1));
            assertTrue(PearlTraining.inside(PearlTraining.BOX, target[3] - 1, PearlTraining.ORIGIN[1], target[5] - 1));
            boolean onTarget = PearlTraining.inside(target, start[0], start[1], start[2]);
            assertEquals(situation.hit, onTarget, situation.id + ": a save starts on the island, a pearl elsewhere");
            if (!situation.hit) {
                double cx = (target[0] + target[3]) / 2.0;
                double cz = (target[2] + target[5]) / 2.0;
                double distance = Math.hypot(cx - start[0], cz - start[2]);
                assertTrue(distance > 20 && distance < 50, situation.id + ": a pearl's throw, " + distance);
            }
        }
        // the middle is thrown from the diamond island, the diamond island from the red one
        assertTrue(PearlTraining.inside(PearlTraining.SITUATIONS[0].target, -36, 71, -36));
        assertTrue(PearlTraining.inside(PearlTraining.SITUATIONS[1].target, -11, 71, -10), "the emerald");
        assertTrue(PearlTraining.inside(PearlTraining.SITUATIONS[2].target, -36, 71, -81), "the red bed");
    }

    @Test
    void theCopyFitsInAZone() {
        int half = 200 / 2 - 1;
        int[] box = PearlTraining.BOX;
        int[] origin = PearlTraining.ORIGIN;
        for (int i : new int[] {0, 2}) {
            assertTrue(Math.abs(box[i] - origin[i]) <= half && Math.abs(box[i + 3] - origin[i]) <= half);
        }
        assertTrue(box[1] < 55 && box[4] > origin[1], "under the limit and over the islands");
    }

    @Test
    void noBlockUnderTheLimit() {
        assertTrue(PearlTraining.belowLimit(54, 55));
        assertFalse(PearlTraining.belowLimit(55, 55));
        assertFalse(PearlTraining.belowLimit(70, 55));
    }
}
