package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The course of the Parkour Duel: its blocks, its jumps, its checkpoints, its two lanes. */
class ParkourCourseTest {

    private static final ParkourCourse COURSE = ParkourCourse.COURSE;

    private static String key(int x, int y, int z) {
        return x + " " + y + " " + z;
    }

    private static Map<String, ParkourCourse.Piece> byPlace() {
        Map<String, ParkourCourse.Piece> places = new HashMap<>();
        for (ParkourCourse.Piece piece : COURSE.pieces()) {
            places.put(key(piece.x, piece.y, piece.z), piece);
        }
        return places;
    }

    @Test
    void everyBlockIsABlockOfMinecraft18AndInAPlaceOfItsOwn() {
        Set<String> seen = new HashSet<>();
        for (ParkourCourse.Piece piece : COURSE.pieces()) {
            Material material = Material.valueOf(piece.material);
            assertTrue(material.isBlock(), piece.material);
            assertTrue(seen.add(key(piece.x, piece.y, piece.z)), "two blocks at " + key(piece.x, piece.y, piece.z));
            if (piece.data == ParkourCourse.LANE_COLOUR) {
                assertEquals(Material.STAINED_CLAY, material, "the colour of the lane is clay");
            } else {
                assertTrue(piece.data >= 0 && piece.data < 16, piece.material + ":" + piece.data);
            }
        }
    }

    @Test
    void eachStepIsASolidBlockWithRoomAboveIt() {
        Map<String, ParkourCourse.Piece> places = byPlace();
        for (int[] step : COURSE.steps()) {
            ParkourCourse.Piece piece = places.get(key(step[0], step[1], step[2]));
            assertNotNull(piece, "a block under the step " + key(step[0], step[1], step[2]));
            assertFalse(piece.material.endsWith("_PLATE"), "a whole block under the feet: " + piece.material);
            for (int dy = 1; dy <= 2; dy++) {
                ParkourCourse.Piece above = places.get(key(step[0], step[1] + dy, step[2]));
                assertTrue(above == null || above.material.equals("GOLD_PLATE"), "room above " + key(step[0], step[1], step[2]));
            }
        }
    }

    /** The last block of the run of blocks from (x, y, z) going this way along x. */
    private static int edge(Map<String, ParkourCourse.Piece> places, int x, int y, int z, int way) {
        while (places.containsKey(key(x + way, y, z))) {
            x += way;
        }
        return x;
    }

    /**
     * Every jump within what a player does in 1.8: three blocks of air at most (two when it goes up
     * a block), two across at most, a block up at most. Measured besides with the physics of 1.8
     * (prismarine-physics): each jump of the course lands with many timings.
     */
    @Test
    void everyJumpCanBeMade() {
        Map<String, ParkourCourse.Piece> places = byPlace();
        List<int[]> steps = COURSE.steps();
        int longest = 0;
        for (int i = 0; i + 1 < steps.size(); i++) {
            int[] from = steps.get(i);
            int[] to = steps.get(i + 1);
            assertTrue(to[0] > from[0], "always on towards the finish, step " + i);
            int takeOff = edge(places, from[0], from[1], from[2], 1);
            int landing = edge(places, to[0], to[1], to[2], -1);
            int up = to[1] - from[1];
            String jump = key(from[0], from[1], from[2]) + " to " + key(to[0], to[1], to[2]);
            if (landing <= takeOff) {
                // on the same blocks: the run-up of the long jump, walked
                assertEquals(0, up, jump);
                assertEquals(from[2], to[2], jump);
                continue;
            }
            int air = landing - takeOff - 1;
            assertTrue(up <= 1, jump + ": a block up at most");
            assertTrue(air <= (up > 0 ? 2 : 3), jump + ": " + air + " blocks of air");
            assertTrue(Math.abs(to[2] - from[2]) <= 2, jump + ": two blocks across at most");
            longest = Math.max(longest, air);
        }
        assertEquals(3, longest, "the jump of three blocks at the end of the prismarine, and on the way down");
    }

    @Test
    void fourCheckpointsBetweenTheStartAndTheFinish() {
        assertEquals(4, COURSE.checkpointCount());
        List<ParkourCourse.Pad> pads = COURSE.checkpoints();
        double[] start = COURSE.start();
        assertTrue(pads.get(0).holds(start[0], start[1], start[2]), "the start is the first checkpoint");
        for (int k = 1; k < pads.size(); k++) {
            double[] middle = pads.get(k).middle();
            assertTrue(pads.get(k).holds(middle[0], middle[1], middle[2]), "put back on checkpoint " + k);
            assertTrue(middle[0] > pads.get(k - 1).middle()[0], "the checkpoints in order");
            // on the gold plate in its middle too
            assertTrue(pads.get(k).holds(middle[0], middle[1] + 0.0625, middle[2]));
        }
        int[] last = COURSE.steps().get(COURSE.steps().size() - 1);
        assertTrue(COURSE.finish().holds(last[0] + 0.5, last[1] + 1, last[2] + 0.5), "the last step is on the finish");
    }

    @Test
    void aPadHoldsTheFeetOnItsTopOnly() {
        ParkourCourse.Pad pad = new ParkourCourse.Pad(10, 12, -1, 1, 4);
        assertTrue(pad.holds(10.0, 5, -1.0));
        assertTrue(pad.holds(12.99, 5.0625, 1.99), "on a plate");
        assertFalse(pad.holds(13.0, 5, 0), "past its end");
        assertFalse(pad.holds(11, 4.5, 0), "under its top");
        assertFalse(pad.holds(11, 6.7, 0), "jumping over it");
        assertFalse(pad.holds(11, 5, 2.0), "beside it");
    }

    @Test
    void theTwoLanesStayApart() {
        int widest = 0;
        for (ParkourCourse.Piece piece : COURSE.pieces()) {
            widest = Math.max(widest, Math.abs(piece.z));
        }
        assertEquals(-5, ParkourCourse.laneZ(0), "red");
        assertEquals(5, ParkourCourse.laneZ(1), "blue");
        int between = 2 * ParkourCourse.LANE_Z - 2 * widest - 1;
        assertTrue(between >= 5, between + " blocks of air between the lanes: nobody jumps to the other one");
        assertEquals(0, COURSE.lowest(), "the start on the floor of the arena");
        assertTrue(COURSE.highest() <= 10, "the beam of the finish's gate");
        assertTrue(COURSE.reach() + 10 < PracticeGames.SPECTATOR_RANGE, "in sight of those who watch");
    }

    @Test
    void theBundledSettingIsAccepted() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertNotNull(config.getConfigurationSection("parkour-duel"));
        assertEquals(1, config.getInt("parkour-duel.points-pour-gagner"), "one race, as on Hypixel");
    }
}
