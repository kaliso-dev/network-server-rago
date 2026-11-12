package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The timed bridge: 16, 32 or 64 blocks, straight or diagonal, flat or uphill, a board each. */
class BridgeTrainingTest {

    private static final String MODE = "bridge-chrono";

    private static List<BridgeTraining.Course> all() {
        List<BridgeTraining.Course> all = new ArrayList<>();
        for (int i = 0; i < BridgeTraining.DISTANCES.length; i++) {
            for (boolean diagonal : new boolean[] {false, true}) {
                for (boolean uphill : new boolean[] {false, true}) {
                    all.add(BridgeTraining.course(i, diagonal, uphill));
                }
            }
        }
        return all;
    }

    @Test
    void theVoidBetweenThePadsIsTheDistance() {
        for (int i = 0; i < BridgeTraining.DISTANCES.length; i++) {
            int distance = BridgeTraining.DISTANCES[i];
            BridgeTraining.Course straight = BridgeTraining.course(i, false, false);
            assertEquals(distance, straight.reach - 1, "the rows of void between the two pads");
            assertEquals(0, straight.rise);
            BridgeTraining.Course diagonal = BridgeTraining.course(i, true, false);
            // from the corner of the start pad to the corner of the green one
            assertEquals(distance, (diagonal.reach - 1) * Math.sqrt(2), 1.0, "diagonally");
            assertEquals(distance / 4, BridgeTraining.course(i, true, true).rise, "uphill: a quarter of the distance higher");
            assertEquals(distance / 4, BridgeTraining.course(i, false, true).rise);
        }
        assertEquals(16, BridgeTraining.course(2, false, true).rise, "64 blocks: 16 higher");
        assertEquals("64 blocks", BridgeTraining.VARIANTS[2].label);
        assertTrue(BridgeTraining.VARIANTS[0].timed, "a time: the lowest first");
    }

    @Test
    void theBlocksGoBetweenThePadsOnly() {
        BridgeTraining.Course straight = BridgeTraining.course(1, false, false);
        int x = straight.x;
        int z = straight.z;
        assertTrue(straight.between(x, 0, z + 1), "the first block, in front of the start pad");
        assertTrue(straight.between(x + 3, -3, z + 32), "beside the line and under it");
        assertFalse(straight.between(x + 4, 0, z + 10), "too far on the side");
        assertFalse(straight.between(x, 1, z), "not on the start pad");
        assertFalse(straight.between(x, 1, z + 33), "nor on the green pad");
        assertFalse(straight.between(x, 4, z + 10), "nor high above the course");
        assertFalse(straight.between(x, -4, z + 10), "nor deep under it");

        BridgeTraining.Course up = BridgeTraining.course(1, false, true);
        assertTrue(up.between(up.x, 11, up.z + 20), "uphill: up to above the green pad");
        assertFalse(up.between(up.x, 12, up.z + 20));

        BridgeTraining.Course diagonal = BridgeTraining.course(1, true, false);
        int dx = diagonal.x;
        int dz = diagonal.z;
        assertTrue(diagonal.between(dx + 1, 0, dz), "beside the corner of the start pad");
        assertTrue(diagonal.between(dx, 0, dz + 1));
        assertTrue(diagonal.between(dx + 12, 0, dz + 12), "on the diagonal");
        assertTrue(diagonal.between(dx + 14, 0, dz + 10), "on its side");
        assertFalse(diagonal.between(dx + 15, 0, dz + 10), "too far on the side");
        assertFalse(diagonal.between(dx, 1, dz), "not on the start pad");
        assertFalse(diagonal.between(dx + diagonal.reach, 1, dz + diagonal.reach), "nor on the green pad");
        assertTrue(diagonal.between(dx + diagonal.reach - 1, 0, dz + diagonal.reach - 1), "up to its corner");
    }

    @Test
    void theFinishIsTheTopOfTheGreenPad() {
        BridgeTraining.Course straight = BridgeTraining.course(0, false, true);
        assertTrue(straight.finish(straight.x, 4, straight.z + 17), "its first row, the gold line");
        assertTrue(straight.line(straight.x - 2, straight.z + 17));
        assertFalse(straight.line(straight.x, straight.z + 18));
        assertTrue(straight.finish(straight.x + 2, 4, straight.z + 21), "its last row");
        assertFalse(straight.finish(straight.x, 4, straight.z + 22), "behind it");
        assertFalse(straight.finish(straight.x, 0, straight.z + 17), "under it");
        assertFalse(straight.finish(straight.x, 0, straight.z), "the start pad");

        BridgeTraining.Course diagonal = BridgeTraining.course(0, true, false);
        int corner = diagonal.reach;
        assertTrue(diagonal.finish(diagonal.x + corner, 0, diagonal.z + corner));
        assertTrue(diagonal.finish(diagonal.x + corner + 4, 0, diagonal.z + corner + 4));
        assertTrue(diagonal.line(diagonal.x + corner, diagonal.z + corner + 3), "the gold on its two edges facing the start");
        assertTrue(diagonal.line(diagonal.x + corner + 3, diagonal.z + corner));
        assertFalse(diagonal.line(diagonal.x + corner + 1, diagonal.z + corner + 1));
        assertFalse(diagonal.finish(diagonal.x + corner - 1, 0, diagonal.z + corner));
    }

    @Test
    void eachPlayerStartsOnTheirStartPadFacingTheGreenOne() {
        for (BridgeTraining.Course course : all()) {
            double[] start = course.start();
            assertTrue(course.onStart((int) Math.floor(start[0]), (int) Math.floor(start[2])), "on its start pad");
            assertEquals(1, start[1], 0.0, "on top of it");
            double yaw = Math.toRadians(start[3]);
            // the look of a yaw in Minecraft: x = -sin, z = cos
            double lookX = -Math.sin(yaw);
            double lookZ = Math.cos(yaw);
            assertEquals(course.diagonal ? Math.sqrt(0.5) : 0, lookX, 1e-9);
            assertEquals(course.diagonal ? Math.sqrt(0.5) : 1, lookZ, 1e-9);
        }
    }

    @Test
    void theTwelveCoursesStandApartNearTheCentre() {
        List<BridgeTraining.Course> all = all();
        assertEquals(12, all.size());
        for (int x = -100; x <= 100; x++) {
            for (int z = -100; z <= 100; z++) {
                int owners = 0;
                for (BridgeTraining.Course course : all) {
                    boolean mine = course.onStart(x, z) || course.onFinish(x, z);
                    for (int y = -BridgeTraining.BELOW; !mine && y <= 16 + BridgeTraining.ABOVE; y++) {
                        mine = course.between(x, y, z);
                    }
                    if (mine) {
                        owners++;
                        assertTrue(x * x + z * z <= 64 * 64, "within 64 blocks of the centre: " + x + ", " + z);
                    }
                }
                assertTrue(owners <= 1, "one course at most at " + x + ", " + z);
            }
        }
        assertTrue(BridgeTraining.FALL < -BridgeTraining.BELOW, "under the lowest block of a bridge");
    }

    @Test
    void aBoardForEachCourse() {
        Training.Variant v32 = BridgeTraining.VARIANTS[1];
        assertEquals("bridge-chrono.32", BridgeTraining.boardOf(MODE, v32, false, false), "straight and flat: the distance's");
        assertEquals("bridge-chrono.32.diagonal.up", BridgeTraining.boardOf(MODE, v32, true, true));
        assertEquals("32 blocks, diagonal, uphill", BridgeTraining.label(v32, true, true));
        assertArrayEquals(new int[] {1, 1, 1}, BridgeTraining.parse(MODE, "bridge-chrono.32.diagonal.up"));
        assertArrayEquals(new int[] {2, 0, 1}, BridgeTraining.parse(MODE, "bridge-chrono.64.up"));
        assertArrayEquals(new int[] {0, 0, 0}, BridgeTraining.parse(MODE, "bridge-chrono.16"));
        assertNull(BridgeTraining.parse(MODE, "bridge-chrono.32.up.diagonal"));
        assertNull(BridgeTraining.parse(MODE, "bridge-chrono.48"));
        assertNull(BridgeTraining.parse(MODE, "extend-practice.32"));

        RecordCatalog.Entry entry = RecordCatalog.of(MODE);
        assertNotNull(entry, "in /records and on the wall");
        assertEquals(12, entry.boards.size());
        Set<String> ids = new HashSet<>();
        for (RecordCatalog.Board board : entry.boards) {
            assertTrue(ids.add(board.id));
            int[] course = BridgeTraining.parse(MODE, board.id);
            assertNotNull(course, board.id);
            assertEquals(BridgeTraining.label(board.variant, course[1] == 1, course[2] == 1), board.label);
            assertEquals(course[1] == 0 && course[2] == 0 ? board.id : null, board.challenge, "the challenge: straight and flat");
            assertTrue(board.timed());
        }
        assertEquals("bridge-chrono.16", entry.boards.get(0).id, "the flat courses first");
        assertEquals("bridge-chrono.64.diagonal", entry.boards.get(5).id);
        assertEquals("bridge-chrono.16.up", entry.boards.get(6).id, "then the uphill ones");
        assertEquals("Timed bridge: 32 blocks", DailyChallenge.CATALOG.get("bridge-chrono.32").label(), "a challenge of the day");
        assertEquals("4.3", BridgeTraining.perSecond(30, 7000));
    }

    @Test
    void twelveBoardsInTwoRowsOfSix() {
        // the flat courses over the uphill ones, in the middle of the page
        assertEquals(19, RecordsMenu.boardSlot(0, 12));
        assertEquals(24, RecordsMenu.boardSlot(5, 12));
        assertEquals(28, RecordsMenu.boardSlot(6, 12));
        assertEquals(33, RecordsMenu.boardSlot(11, 12));
        // as before for the others: one row in the middle, or two of seven
        assertEquals(21, RecordsMenu.boardSlot(0, 3));
        assertEquals(19, RecordsMenu.boardSlot(0, 7));
        assertEquals(25, RecordsMenu.boardSlot(6, 7));
        assertEquals(28, RecordsMenu.boardSlot(7, 14));
    }
}
