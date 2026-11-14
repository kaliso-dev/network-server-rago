package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The pearl practice: the situations found on the islands of a map, and the loop of its levels. */
class PearlMapsTest {

    /** A base, a diamond island 33 blocks in front of it and a middle further on: the plugin's own islands. */
    private static final PearlMaps.Part PLAIN = PearlMaps.plain();

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    /** The way a yaw of Minecraft looks along (x, z). */
    private static double[] way(double yaw) {
        double r = Math.toRadians(yaw);
        return new double[] {-Math.sin(r), Math.cos(r)};
    }

    /** From (x, z) along this way, the first whole step on a column of this island; -1 if none within max. */
    private static int stepsTo(PearlMaps.Part part, int island, double x, double z, double[] way, int max) {
        for (int t = 1; t <= max; t++) {
            if (part.island(floor(x + way[0] * t), floor(z + way[1] * t)) == island) {
                return t;
            }
        }
        return -1;
    }

    @Test
    void thePluginsOwnIslandsHaveEveryKindOfSituation() {
        assertNotNull(PLAIN);
        assertEquals(PearlMaps.PLAIN, PLAIN.map);
        assertEquals(14, PLAIN.wool);
        for (PearlMaps.Kind kind : PearlMaps.Kind.values()) {
            assertFalse(PLAIN.of(kind).isEmpty(), kind.name());
        }
        assertEquals(PLAIN.situations.size(), PLAIN.of(null).size());
        // pearls both ways between the base and its diamond island, and between it and the middle
        assertEquals(4, PLAIN.of(PearlMaps.Kind.PEARL).size());
        // the three islands are kept: their blocks are those of the boxes
        assertEquals(21 * 6 * 19 + 15 * 6 * 15 + 21 * 7 * 21, PLAIN.copy.size());
    }

    @Test
    void aPearlStartsOnTheEdgeOfAnIslandFacingAnotherOne() {
        PearlMaps.Grid grid = PearlMaps.Grid.of(PLAIN.copy);
        for (PearlMaps.Situation s : PLAIN.of(PearlMaps.Kind.PEARL)) {
            int x = floor(s.start[0]);
            int z = floor(s.start[2]);
            assertTrue(grid.standable(x, (int) s.start[1], z), s.title);
            int from = PLAIN.island(x, z);
            assertNotEquals(0, from, s.title);
            assertNotEquals(from, s.target, s.title);
            assertNull(s.push, s.title);
            assertNull(s.platform, s.title);
            // the island aimed at is ahead, as far as a pearl goes
            int steps = stepsTo(PLAIN, s.target, s.start[0], s.start[2], way(s.start[3]), 60);
            assertTrue(steps >= PearlMaps.GAP_MIN && steps <= PearlMaps.GAP_MAX + 2, s.title + ": " + steps);
            // the start is on the edge: one more step ahead is the void
            double[] ahead = way(s.start[3]);
            assertEquals(0, PLAIN.island(floor(s.start[0] + ahead[0] * 1.5), floor(s.start[2] + ahead[1] * 1.5)), s.title);
            assertTrue(Math.abs(s.targetFloor - s.start[1]) <= PearlMaps.HEIGHT_MAX, s.title);
        }
        // from the base to its diamond island: straight ahead (south), 34 blocks from edge to edge
        PearlMaps.Situation toDiamonds = PLAIN.of(PearlMaps.Kind.PEARL).get(0);
        assertArrayEquals(new double[] {0.5, 1, -11.5, 0}, toDiamonds.start, 1e-6);
        assertEquals(2, toDiamonds.targetFloor);
        assertEquals("Pearl onto the diamond island", toDiamonds.title);
    }

    @Test
    void aSaveStartsNearTheEdgeTheHitThrowingOutwards() {
        PearlMaps.Grid grid = PearlMaps.Grid.of(PLAIN.copy);
        for (PearlMaps.Situation s : PLAIN.of(PearlMaps.Kind.SAVE)) {
            int x = floor(s.start[0]);
            int z = floor(s.start[2]);
            assertTrue(grid.standable(x, (int) s.start[1], z), s.title);
            // back onto the island the player stands on
            assertEquals(s.target, PLAIN.island(x, z), s.title);
            assertEquals((int) s.start[1], s.targetFloor, s.title);
            assertNotNull(s.push, s.title);
            assertNull(s.platform, s.title);
            assertEquals(1, Math.hypot(s.push[0], s.push[1]), 1e-9);
            // facing the island, the back to the void the hit throws into
            assertArrayEquals(new double[] {-s.push[0], -s.push[1]}, way(s.start[3]), 1e-6);
            int edge = -1;
            for (int t = 1; t <= 6 && edge < 0; t++) {
                if (PLAIN.island(floor(s.start[0] + s.push[0] * t), floor(s.start[2] + s.push[1] * t)) == 0) {
                    edge = t;
                }
            }
            assertTrue(edge >= 1 && edge <= 3, s.title + ": the void " + edge + " blocks away");
        }
    }

    @Test
    void aClutchStartsOnABridgeUnderAnIslandFacingItsWall() {
        PearlMaps.Grid grid = PearlMaps.Grid.of(PLAIN.copy);
        assertTrue(PLAIN.of(PearlMaps.Kind.CLUTCH).size() >= 3);
        for (PearlMaps.Situation s : PLAIN.of(PearlMaps.Kind.CLUTCH)) {
            int x = floor(s.start[0]);
            int feet = (int) s.start[1];
            int z = floor(s.start[2]);
            // the block of the bridge is right under the feet, out in the void: no island there
            assertArrayEquals(new int[] {x, feet - 1, z}, s.platform, s.title);
            assertEquals(0, PLAIN.island(x, z), s.title);
            assertTrue(grid.empty(x, z, feet - 6, feet + 2), s.title);
            // a few blocks under the floor of the island
            assertTrue(s.targetFloor - feet >= 3 && s.targetFloor - feet <= 5, s.title + ": " + (s.targetFloor - feet));
            // the hit throws away from the island; the player faces its side, a wall at the height of the feet
            assertNotNull(s.push, s.title);
            double[] back = {-s.push[0], -s.push[1]};
            assertArrayEquals(back, way(s.start[3]), 1e-6);
            int steps = stepsTo(PLAIN, s.target, s.start[0], s.start[2], back, 20);
            assertTrue(steps >= 9 && steps <= 15, s.title + ": the island " + steps + " blocks away");
            int wx = floor(s.start[0] + back[0] * steps);
            int wz = floor(s.start[2] + back[1] * steps);
            assertFalse(grid.empty(wx, wz, feet - 1, feet + 1), s.title + ": no wall");
            // nothing between the bridge and the wall
            for (int t = 1; t < steps - 1; t++) {
                assertTrue(grid.empty(floor(s.start[0] + back[0] * t), floor(s.start[2] + back[1] * t), feet - 4, feet + 3),
                        s.title + " at " + t);
            }
        }
        // under the base, towards its diamond island: 12 blocks out, 4 under its floor, facing north
        PearlMaps.Situation underTheBase = PLAIN.of(PearlMaps.Kind.CLUTCH).get(0);
        assertEquals("Clutch: pearl to the wall of your base", underTheBase.title);
        assertArrayEquals(new double[] {0.5, -3, 0.5, 180}, underTheBase.start, 1e-6);
        assertEquals(PLAIN.island(0, -20), underTheBase.target);
    }

    @Test
    void noBlockGoesFarUnderTheLowestIsland() {
        // the lowest floor of the situations: a clutch 5 blocks under the base at most, 16 more under it
        int lowest = Integer.MAX_VALUE;
        for (PearlMaps.Situation s : PLAIN.situations) {
            lowest = Math.min(lowest, (int) Math.floor(s.start[1]));
        }
        assertEquals(Math.min(lowest, 1) - PearlMaps.LIMIT_UNDER, PLAIN.limitY);
        assertTrue(PearlTraining.belowLimit(PLAIN.limitY - 1, PLAIN.limitY));
        assertFalse(PearlTraining.belowLimit(PLAIN.limitY, PLAIN.limitY));
    }

    /** A base and a diamond island this far in front of it (from edge to edge), without a middle. */
    private static PearlMaps.Part twoIslands(int gap, int[] extra, int[] box) {
        int far = -12 + gap + 1;
        List<int[]> boxes = new java.util.ArrayList<>(Arrays.asList(
                new int[] {-10, -5, -30, 10, 0, -12, 5},
                new int[] {-7, -4, far, 7, 0, far + 14, 24}));
        if (extra != null) {
            boxes.add(extra);
        }
        PearlMaps.Anchors anchors = new PearlMaps.Anchors("blue", 11, new double[] {0.5, 1, -21.5}, new double[] {0, 1, -27},
                new double[] {0.5, 1, -28.5}, Collections.singletonList(new double[] {0.5, 1, far + 7.5}),
                Collections.<double[]>emptyList());
        return PearlMaps.analyse("Test", anchors, PearlMaps.boxes(boxes), box, new int[] {0, 0, 0});
    }

    @Test
    void aGapTooLongForAPearlIsLeftOut() {
        int[] box = {-99, -40, -99, 99, 40, 99};
        PearlMaps.Part near = twoIslands(30, null, box);
        assertNotNull(near);
        assertEquals(2, near.of(PearlMaps.Kind.PEARL).size());
        // 51 blocks from edge to edge: too far (and the diamond island still near enough to be the base's own)
        PearlMaps.Part far = twoIslands(50, null, box);
        assertNotNull(far, "the saves and the clutches still hold");
        assertTrue(far.of(PearlMaps.Kind.PEARL).isEmpty());
        assertFalse(far.of(PearlMaps.Kind.CLUTCH).isEmpty());
        // too near: a jump, not a pearl (and no void for a save or a clutch between them)
        PearlMaps.Part close = twoIslands(4, null, box);
        assertTrue(close == null || close.of(PearlMaps.Kind.PEARL).isEmpty());
    }

    @Test
    void anIslandCutByTheEdgeOfTheCopyIsLeftOutAWholeOneIsKept() {
        int[] box = {-60, -40, -60, 60, 40, 60};
        int[] cut = {40, -5, -20, 60, 0, -10, 5};
        int[] whole = {-40, -5, -20, -30, 0, -10, 5};
        PearlMaps.Part withCut = twoIslands(30, cut, box);
        PearlMaps.Part withWhole = twoIslands(30, whole, box);
        PearlMaps.Part alone = twoIslands(30, null, box);
        assertEquals(alone.copy.size(), withCut.copy.size());
        assertEquals(0, withCut.island(50, -15));
        assertEquals(alone.copy.size() + 11 * 6 * 11, withWhole.copy.size());
        assertNotEquals(0, withWhole.island(-35, -15));
    }

    @Test
    void withoutADiamondIslandNearTheBaseNothingHolds() {
        PearlMaps.Anchors anchors = new PearlMaps.Anchors("red", 14, new double[] {0.5, 1, -21.5}, new double[] {0, 1, -27},
                new double[] {0.5, 1, -28.5}, Collections.singletonList(new double[] {0.5, 1, 120.5}),
                Collections.<double[]>emptyList());
        assertTrue(anchors.ownDiamonds().isEmpty());
        MapCopy blocks = PearlMaps.boxes(Collections.singletonList(new int[] {-10, -5, -30, 10, 0, -12, 5}));
        assertNull(PearlMaps.analyse("Test", anchors, blocks, new int[] {-99, -40, -99, 99, 40, 99}, new int[] {0, 0, 0}));
    }

    @Test
    void theBaseHasTheNearestTwoDiamondIslands() {
        List<double[]> diamonds = Arrays.asList(new double[] {70, 66, 0}, new double[] {0, 66, 30}, new double[] {-40, 66, 0},
                new double[] {0, 66, -200});
        PearlMaps.Anchors anchors = new PearlMaps.Anchors("red", 14, new double[] {0, 65, 0}, new double[] {0, 66, -5},
                new double[] {0, 65, -9}, diamonds, Arrays.asList(new double[] {10, 70, 100}, new double[] {-10, 68, 104}));
        List<double[]> own = anchors.ownDiamonds();
        assertEquals(2, own.size());
        assertArrayEquals(new double[] {0, 66, 30}, own.get(0));
        assertArrayEquals(new double[] {-40, 66, 0}, own.get(1));
        // the middle: between the emeralds, at the lowest of them
        assertArrayEquals(new double[] {0, 68, 102}, anchors.middle(), 1e-9);
        assertNull(new PearlMaps.Anchors("red", 14, new double[] {0, 65, 0}, new double[] {0, 66, -5},
                new double[] {0, 65, -9}, diamonds, Collections.<double[]>emptyList()).middle());
    }

    @Test
    void theCopyHoldsInAZoneAroundTheFloorOfTheSpawn() {
        PearlMaps.Anchors anchors = new PearlMaps.Anchors("red", 14, new double[] {-13.5, 65, -72.5},
                new double[] {-15, 66, -67}, new double[] {-13.5, 64.5, -78.5},
                Arrays.asList(new double[] {-50.5, 64, -50.5}, new double[] {30.5, 64, -60.5}),
                Arrays.asList(new double[] {-10.5, 72, 9.5}, new double[] {9.5, 72, -10.5}));
        int[][] box = PearlMaps.box(anchors);
        int[] b = box[0];
        int[] origin = box[1];
        assertTrue(b[3] - b[0] <= PearlMaps.MAX_SPAN && b[5] - b[2] <= PearlMaps.MAX_SPAN);
        // the base, its diamonds and the middle with a margin around them
        assertEquals(-51 - PearlMaps.MARGIN, b[0]);
        assertEquals(30 + PearlMaps.MARGIN, b[3]);
        assertEquals(-79 - PearlMaps.MARGIN, b[2]);
        assertEquals(-1 + PearlMaps.MARGIN, b[5]);
        assertEquals(64 - PearlMaps.BELOW, b[1]);
        assertEquals(72 + PearlMaps.ABOVE, b[4]);
        // the origin: the middle of the box, the floor under the spawn
        assertArrayEquals(new int[] {(b[0] + b[3]) / 2, 64, (b[2] + b[5]) / 2}, origin);
        // too wide: cut on both sides
        assertArrayEquals(new int[] {52, 248}, PearlMaps.span(0, 300));
        assertArrayEquals(new int[] {0, 100}, PearlMaps.span(0, 100));
        int[] wide = PearlMaps.span(-130, 130);
        assertTrue(wide[1] - wide[0] <= PearlMaps.MAX_SPAN && wide[0] + wide[1] <= 1 && wide[0] + wide[1] >= -1);
    }

    @Test
    void theLoopPlaysEveryKindTheClutchEveryOtherTime() {
        PearlMaps.Kind[] first = new PearlMaps.Kind[8];
        for (int turn = 0; turn < 8; turn++) {
            first[turn] = PearlTraining.kindOf("loop", turn);
        }
        assertArrayEquals(new PearlMaps.Kind[] {PearlMaps.Kind.PEARL, PearlMaps.Kind.CLUTCH, PearlMaps.Kind.SAVE,
            PearlMaps.Kind.CLUTCH, PearlMaps.Kind.PEARL, PearlMaps.Kind.CLUTCH, PearlMaps.Kind.SAVE, PearlMaps.Kind.CLUTCH}, first);
        for (int turn = 0; turn < 5; turn++) {
            assertEquals(PearlMaps.Kind.PEARL, PearlTraining.kindOf("pearls", turn));
            assertEquals(PearlMaps.Kind.SAVE, PearlTraining.kindOf("saves", turn));
            assertEquals(PearlMaps.Kind.CLUTCH, PearlTraining.kindOf("clutches", turn));
        }
        assertEquals("loop", PearlTraining.variants()[0].id);
    }

    @Test
    void theTeamsOfABedwarsYmlAreItsSides() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("equipes:\n"
                + "  red:\n"
                + "    spawn: {x: -13.5, y: 65.0, z: -72.5, yaw: 0.0}\n"
                + "    lit: {x: -15, y: 66, z: -67}\n"
                + "    generateur: {x: -13.5, y: 64.5, z: -78.5}\n"
                + "  aqua:\n"
                + "    spawn: {x: 73.5, y: 65.0, z: -13.5, yaw: 90.0}\n"
                + "    lit: {x: 67, y: 66, z: -15}\n"
                + "    generateur: {x: 79.5, y: 64.5, z: -13.5}\n"
                + "  green:\n"
                + "    spawn: {x: 1, y: 65}\n"
                + "    lit: {x: 67, y: 66, z: -15}\n"
                + "    generateur: {x: 79.5, y: 64.5, z: -13.5}\n"
                + "diamants:\n"
                + "  - {x: -50.5, y: 64, z: -50.5}\n"
                + "  - {x: 50.5, y: 64}\n"
                + "emeraudes:\n"
                + "  - {x: -10.5, y: 72, z: 9.5}\n");
        List<PearlMaps.Anchors> sides = RagoPractice.pearlAnchors(yaml);
        assertEquals(2, sides.size(), "green has no z to its spawn");
        assertEquals("red", sides.get(0).team);
        assertEquals(14, sides.get(0).wool);
        assertEquals("aqua", sides.get(1).team);
        assertEquals(9, sides.get(1).wool);
        assertArrayEquals(new double[] {73.5, 65, -13.5}, sides.get(1).spawn, 1e-9);
        assertEquals(1, sides.get(0).diamonds.size(), "a diamond without a z is left out");
        assertEquals(1, sides.get(0).emeralds.size());
        assertTrue(RagoPractice.pearlAnchors(new YamlConfiguration()).isEmpty());
    }

    @Test
    void theBlocksOfAMapGoIntoTheirZoneChunkByChunk() {
        // from the centre (100, 64, -200): two blocks that go, two that come
        int[] gone = {0, 0, 0, 1, 0, 17, 1, -1, 1, 0};
        int[] coming = {-1, 2, 3, 5, 2, 15, 0, 15, 4, 0};
        List<FastBlocks.Chunk> chunks = FastBlocks.byChunk(gone, coming, 100, 64, -200);
        assertEquals(3, chunks.size());
        // (100, -200) and (99, -197) share the chunk (6, -13); (117, -201) is in (7, -13); (115, -185) in (7, -12)
        assertEquals(6, chunks.get(0).cx);
        assertEquals(-13, chunks.get(0).cz);
        assertEquals(2, chunks.get(0).changes());
        assertEquals(7, chunks.get(1).cx);
        assertEquals(-13, chunks.get(1).cz);
        assertEquals(1, chunks.get(1).changes());
        assertEquals(7, chunks.get(2).cx);
        assertEquals(-12, chunks.get(2).cz);
        assertEquals(1, chunks.get(2).changes());
        // a whole part: every block in one chunk or another
        List<FastBlocks.Chunk> part = FastBlocks.byChunk(new int[0], PLAIN.copy.blocks(), 0, 64, 0);
        int changes = 0;
        for (FastBlocks.Chunk chunk : part) {
            changes += chunk.changes();
        }
        assertEquals(PLAIN.copy.size(), changes);
    }

    @Test
    void theBundledConfigListsTheMapsOfTheLoop() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        List<String> maps = config.getStringList("pearl.cartes");
        assertTrue(maps.size() >= 5);
        assertEquals("SkyRise", maps.get(0));
        assertTrue(maps.contains("Archway") && maps.contains("Invasion"));
        assertFalse(config.isSet("pearl.limite-basse"), "the limit comes from the islands of each map now");
    }
}
