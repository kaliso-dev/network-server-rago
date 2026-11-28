package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Block aim: the faces that light up, that each can be reached from the scene, the next one, the
 * levels, a streak summed up.
 */
class BlockAimTest {

    /** The reach of a player in survival (blocks), the eyes standing and with shift held. */
    private static final double REACH = 4.5;
    private static final double[] EYES = {1.62, 1.54};
    private static final double HALF_WIDTH = 0.3;

    @Test
    void fifteenFacesUnderTheFeetOnTheSideOfTheBridgeAndOnTheBed() {
        int[] count = new int[3];
        Set<String> cells = new HashSet<>();
        for (BlockAim.Target target : BlockAim.TARGETS) {
            count[target.place]++;
            assertTrue(BlockAim.solid(target.x, target.y, target.z), "the face of a block of the scene");
            assertFalse(BlockAim.solid(target.cellX(), target.cellY(), target.cellZ()), "a block goes against it");
            assertTrue(cells.add(target.cellX() + "," + target.cellY() + "," + target.cellZ()), "each its own cell");
            assertEquals(1, Math.abs(target.dx) + Math.abs(target.dy) + Math.abs(target.dz), "one of the six faces");
        }
        assertEquals(1, count[BlockAim.FEET], "the end of the bridge");
        assertEquals(2 * BlockAim.BRIDGE_LENGTH, count[BlockAim.SIDE], "both sides of each block of the bridge");
        assertEquals(8, count[BlockAim.BED], "the top, the sides and the ends of the bed");
        BlockAim.Target feet = BlockAim.TARGETS.get(0);
        assertTrue(feet.hitBy(BlockAim.BRIDGE_LENGTH, 0, 0), "past the end of the bridge");
        assertFalse(feet.hitBy(BlockAim.BRIDGE_LENGTH - 1, 0, 1));
    }

    @Test
    void eachFaceCanBeReachedFromAPlaceOfTheScene() {
        for (BlockAim.Target target : BlockAim.TARGETS) {
            assertTrue(reachable(target), "face of " + target.x + "," + target.y + "," + target.z + " to "
                    + target.cellX() + "," + target.cellY() + "," + target.cellZ());
        }
    }

    @Test
    void theDustLiesOnTheFaceALittleOutOfIt() {
        for (BlockAim.Target target : BlockAim.TARGETS) {
            List<double[]> points = target.points();
            assertEquals(9, points.size());
            for (double[] point : points) {
                // out of the block, in the cell of the block to put
                assertEquals(target.cellX(), (int) Math.floor(point[0]));
                assertEquals(target.cellZ(), (int) Math.floor(point[2]));
                double top = target.y + target.height();
                assertTrue(point[1] >= target.y && point[1] <= top + BlockAim.OUT + 1e-9, "as high as the face");
            }
        }
        BlockAim.Target bed = BlockAim.TARGETS.get(1 + 2 * BlockAim.BRIDGE_LENGTH);
        assertEquals(BlockAim.BED, bed.place);
        assertEquals(1, bed.dy, "the top of the bed");
        assertEquals(BlockAim.BED_Y + BlockAim.BED_HEIGHT + BlockAim.OUT, bed.points().get(0)[1], 1e-9, "a bed is lower");
    }

    @Test
    void theNextTargetIsAnotherNotFarAndEveryPlaceComes() {
        Random random = new Random(7);
        BlockAim.Target before = BlockAim.first(random);
        int[] count = new int[3];
        for (int i = 0; i < 3000; i++) {
            BlockAim.Target next = BlockAim.next(random, before);
            assertNotSame(before, next, "never the same face twice");
            assertTrue(next.distanceTo(before) <= BlockAim.MAX_STEP);
            count[next.place]++;
            before = next;
        }
        for (int place = 0; place < 3; place++) {
            assertTrue(count[place] > 300, BlockAim.PLACES[place] + ": " + count[place]);
        }
        for (BlockAim.Target target : BlockAim.TARGETS) {
            BlockAim.Target next = BlockAim.next(new Random(1), target);
            assertNotSame(target, next, "each face has others near it");
        }
    }

    @Test
    void theLevelsGiveLessAndLessTime() {
        for (int i = 1; i < BlockAim.LEVEL_MS.length; i++) {
            assertTrue(BlockAim.LEVEL_MS[i] < BlockAim.LEVEL_MS[i - 1]);
        }
        assertEquals("3", BlockAim.levelSeconds(0));
        assertEquals("2.5", BlockAim.levelSeconds(1));
        Training.Variant[] levels = BlockAimTraining.variants();
        assertEquals(BlockAim.LEVEL_MS.length, levels.length);
        assertEquals("level1", levels[0].id);
        assertEquals("Level 2 (2.5 s)", levels[1].label);
        assertFalse(levels[0].timed, "a streak: the most targets first");
        assertEquals("12 targets", Training.format(levels[0], 12));
    }

    @Test
    void aStreakCountsItsTimesAndTheBlocksPutElsewhere() {
        BlockAim.Streak streak = new BlockAim.Streak();
        assertEquals("0 targets, 0 blocks elsewhere", streak.summary());
        streak.hit(800);
        streak.hit(600);
        streak.hit(1000);
        streak.elsewhere();
        assertEquals(800, streak.average());
        assertEquals("3 targets, 0.80 s a target, 1 block elsewhere", streak.summary());
    }

    /**
     * True if the player can put a block against this face from somewhere: standing on the scene
     * (or past its edge with shift held, as far as their feet still touch it), not in the cell of
     * the block to put, their eyes in front of the face, within reach and nothing between.
     */
    private static boolean reachable(BlockAim.Target target) {
        for (double px = -6; px <= 5; px += 0.05) {
            for (double pz = -2.5; pz <= 2.5; pz += 0.05) {
                if (!standing(px, pz) || inCell(px, pz, target)) {
                    continue;
                }
                for (double eye : EYES) {
                    if (sees(new double[] {px, 1 + eye, pz}, target)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** True if the feet there touch a block of the scene, the body in none (the bed). */
    private static boolean standing(double px, double pz) {
        boolean on = false;
        for (int x = (int) Math.floor(px - HALF_WIDTH); x <= (int) Math.floor(px + HALF_WIDTH); x++) {
            for (int z = (int) Math.floor(pz - HALF_WIDTH); z <= (int) Math.floor(pz + HALF_WIDTH); z++) {
                if (BlockAim.solid(x, 1, z)) {
                    return false;
                }
                on |= BlockAim.solid(x, 0, z);
            }
        }
        return on;
    }

    private static boolean inCell(double px, double pz, BlockAim.Target target) {
        boolean x = target.cellX() < px + HALF_WIDTH && target.cellX() + 1 > px - HALF_WIDTH;
        boolean z = target.cellZ() < pz + HALF_WIDTH && target.cellZ() + 1 > pz - HALF_WIDTH;
        boolean y = target.cellY() >= 1 && target.cellY() <= 2;
        return x && y && z;
    }

    private static boolean sees(double[] eye, BlockAim.Target target) {
        double h = target.height();
        double[] centre = target.dy != 0 ? new double[] {target.x + 0.5, target.y + (target.dy > 0 ? h : 0), target.z + 0.5}
                : target.dx != 0 ? new double[] {target.x + (target.dx > 0 ? 1 : 0), target.y + h / 2, target.z + 0.5}
                : new double[] {target.x + 0.5, target.y + h / 2, target.z + (target.dz > 0 ? 1 : 0)};
        double front = (eye[0] - centre[0]) * target.dx + (eye[1] - centre[1]) * target.dy + (eye[2] - centre[2]) * target.dz;
        double distance = Math.sqrt(sq(eye[0] - centre[0]) + sq(eye[1] - centre[1]) + sq(eye[2] - centre[2]));
        if (front <= 0 || distance > REACH) {
            return false;
        }
        int steps = (int) (distance / 0.01);
        for (int s = 1; s < steps - 2; s++) {
            double t = (double) s / steps;
            double x = eye[0] + (centre[0] - eye[0]) * t;
            double y = eye[1] + (centre[1] - eye[1]) * t;
            double z = eye[2] + (centre[2] - eye[2]) * t;
            int bx = (int) Math.floor(x);
            int by = (int) Math.floor(y);
            int bz = (int) Math.floor(z);
            double top = by == BlockAim.BED_Y && bz == 0 && (bx == BlockAim.BED_FOOT_X || bx == BlockAim.BED_HEAD_X)
                    ? BlockAim.BED_HEIGHT : 1;
            if (BlockAim.solid(bx, by, bz) && y - by < top) {
                return false;
            }
        }
        return true;
    }

    private static double sq(double value) {
        return value * value;
    }
}
