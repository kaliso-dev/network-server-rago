package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The fireball practice: its situations on the bridges between the islands of a map, and the loop of its levels. */
class FireballMapsTest {

    /** The plugin's own islands: a base, two diamond islands and a middle. */
    private static final MapParts.Part PLAIN = MapParts.plain();
    private static final MapParts.Grid GRID = MapParts.Grid.of(PLAIN.copy);

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    /** The way a yaw of Minecraft looks along (x, z). */
    private static double[] way(double yaw) {
        double r = Math.toRadians(yaw);
        return new double[] {-Math.sin(r), Math.cos(r)};
    }

    /** The blocks of a situation's bridge, as keys "x y z". */
    private static Set<String> bridge(MapParts.Situation s) {
        Set<String> blocks = new HashSet<>();
        for (int i = 0; i < s.bridge.length; i += 3) {
            blocks.add(s.bridge[i] + " " + s.bridge[i + 1] + " " + s.bridge[i + 2]);
        }
        return blocks;
    }

    /** True if a player standing there (feet at y) has a block under them: of the map, or of the bridge. */
    private static boolean floorUnder(MapParts.Situation s, double x, int y, double z) {
        return bridge(s).contains(floor(x) + " " + (y - 1) + " " + floor(z)) || MapParts.floor(GRID.id(floor(x), y - 1, floor(z)));
    }

    @Test
    void thePluginsOwnIslandsHaveEveryKindOfTheFireballs() {
        for (MapParts.Kind kind : FireballTraining.LOOP) {
            assertTrue(kind.fireball);
            assertFalse(PLAIN.of(kind).isEmpty(), kind.name());
        }
        assertFalse(MapParts.Kind.CLUTCH.fireball);
        assertTrue(PLAIN.hasAny(true) && PLAIN.hasAny(false));
        assertTrue(MapParts.Kind.BRIDGER.enemyBridge() && MapParts.Kind.RUSHER.enemyBridge() && MapParts.Kind.SIDE.enemyBridge());
        assertFalse(MapParts.Kind.JUMP.enemyBridge() || MapParts.Kind.SEND.enemyBridge() || MapParts.Kind.CLIMB.enemyBridge());
        assertEquals(10, MapParts.kindsOf(PLAIN));
    }

    @Test
    void aJumpStartsOnABrokenBridgeTheGapAheadTheFarSideBeyond() {
        for (MapParts.Kind kind : new MapParts.Kind[] {MapParts.Kind.JUMP, MapParts.Kind.ESCAPE}) {
            for (MapParts.Situation s : PLAIN.of(kind)) {
                int feet = (int) s.start[1];
                assertTrue(bridge(s).contains(floor(s.start[0]) + " " + (feet - 1) + " " + floor(s.start[2])), s.what);
                assertEquals(0, PLAIN.island(floor(s.start[0]), floor(s.start[2])), s.what + ": out in the void");
                assertTrue(s.reach >= 7.5 && s.reach <= 11, s.what + ": " + s.reach);
                double[] ahead = way(s.start[3]);
                // the gap: no block from a step ahead to the far side
                for (double t = 1.2; t < s.reach - 1.2; t += 0.25) {
                    double x = s.start[0] + ahead[0] * t;
                    double z = s.start[2] + ahead[1] * t;
                    assertFalse(floorUnder(s, x, feet, z), s.what + ": a block in the gap at " + t);
                    assertTrue(GRID.empty(floor(x), floor(z), feet - 6, feet + 2), s.what + ": the void at " + t);
                }
                // the far side, at the same height
                assertTrue(floorUnder(s, s.start[0] + ahead[0] * s.reach, feet, s.start[2] + ahead[1] * s.reach)
                        || floorUnder(s, s.start[0] + ahead[0] * (s.reach + 0.5), feet, s.start[2] + ahead[1] * (s.reach + 0.5)),
                        s.what + ": no far side");
                assertTrue(FireballTraining.landed(s, PLAIN, new double[] {s.start[0] + ahead[0] * s.reach, feet,
                    s.start[2] + ahead[1] * s.reach}), s.what);
                assertFalse(FireballTraining.landed(s, PLAIN, new double[] {s.start[0] + ahead[0], feet, s.start[2] + ahead[1]}),
                        s.what + ": short");
                assertFalse(FireballTraining.landed(s, PLAIN, s.start), s.what + ": where it started");
            }
        }
        // from the base to the first diamond island: straight on (south), a gap of 7 blocks
        MapParts.Situation first = PLAIN.of(MapParts.Kind.JUMP).get(0);
        assertArrayEquals(new double[] {0.5, 2, 1.5, 0}, first.start, 1e-6);
        assertEquals(8, first.reach, 1e-6);
        assertEquals("Your bridge to the diamond island is broken: fireball jump over the gap.", first.what);
    }

    @Test
    void noFireballJumpUnderACeilingTheBodyWouldBumpIntoIt() {
        // every jump of the plugin's islands has room over it, all the way to the far side
        for (MapParts.Kind kind : new MapParts.Kind[] {MapParts.Kind.JUMP, MapParts.Kind.ESCAPE}) {
            for (MapParts.Situation s : PLAIN.of(kind)) {
                double[] ahead = way(s.start[3]);
                for (double t = 0; t <= s.reach + 1; t += 0.5) {
                    int x = floor(s.start[0] + ahead[0] * t);
                    int z = floor(s.start[2] + ahead[1] * t);
                    assertTrue(GRID.empty(x, z, (int) s.start[1], (int) s.start[1] + MapFinder.JUMP_ROOM), s.what + " at " + t);
                }
            }
        }
        // a block of the map three blocks over the middle of the gap of the first jump: that jump is gone
        MapParts.Situation first = PLAIN.of(MapParts.Kind.JUMP).get(0);
        int[] blocks = PLAIN.copy.blocks();
        int[] roofed = java.util.Arrays.copyOf(blocks, blocks.length + 5);
        double[] ahead = way(first.start[3]);
        int[] roof = {floor(first.start[0] + ahead[0] * first.reach / 2), (int) first.start[1] + 3,
            floor(first.start[2] + ahead[1] * first.reach / 2), 1, 0};
        System.arraycopy(roof, 0, roofed, blocks.length, 5);
        MapParts.Part part = MapParts.plain(MapCopy.of(roofed));
        assertNotNull(part);
        for (MapParts.Situation s : part.of(MapParts.Kind.JUMP)) {
            assertFalse(java.util.Arrays.equals(first.start, s.start), s.what);
        }
        assertFalse(part.of(MapParts.Kind.JUMP).isEmpty());
        assertEquals(PLAIN.of(MapParts.Kind.BRIDGER).size(), part.of(MapParts.Kind.BRIDGER).size());
    }

    @Test
    void anEscapeHasTheEnemyBehindOnTheBridgeRunningToThePlayer() {
        for (MapParts.Situation s : PLAIN.of(MapParts.Kind.ESCAPE)) {
            assertNotNull(s.enemy, s.what);
            assertNotNull(s.path, s.what);
            // he starts on the island behind, on the first step of his way, the player on its last
            int steps = s.path.length / 3;
            assertEquals(floor(s.enemy[0]), s.path[0], s.what);
            assertEquals(floor(s.enemy[2]), s.path[2], s.what);
            assertNotEquals(0, PLAIN.island(s.path[0], s.path[2]), s.what);
            assertEquals(floor(s.start[0]), s.path[3 * (steps - 1)], s.what);
            assertEquals(floor(s.start[2]), s.path[3 * (steps - 1) + 2], s.what);
            assertTrue(Math.hypot(s.enemy[0] - s.start[0], s.enemy[2] - s.start[2]) >= MapFinder.ESCAPE_BEHIND, s.what);
            assertSteps(s);
            assertFalse(s.what.contains("from your base"), s.what);
        }
    }

    @Test
    void aClimbStartsOnABridgeAgainstTheSideOfAnIslandAFewBlocksUnder() {
        for (MapParts.Situation s : PLAIN.of(MapParts.Kind.CLIMB)) {
            int feet = (int) s.start[1];
            assertEquals(MapFinder.CLIMB_OUT * 3, s.bridge.length, s.what);
            assertArrayEquals(new int[] {floor(s.start[0]), feet - 1, floor(s.start[2])}, new int[] {s.bridge[0], s.bridge[1],
                s.bridge[2]}, s.what);
            int up = s.targetFloor - feet;
            assertTrue(up == 3 || up == 4, s.what + ": " + up);
            // facing the island: its wall right ahead, from the feet up to its floor
            double[] ahead = way(s.start[3]);
            int wx = -1;
            int wz = -1;
            for (double t = 0.5; t <= 2.5 && wx < 0; t += 0.25) {
                int x = floor(s.start[0] + ahead[0] * t);
                int z = floor(s.start[2] + ahead[1] * t);
                if (PLAIN.island(x, z) == s.target) {
                    wx = x;
                    wz = z;
                }
            }
            assertTrue(wx >= 0 || wz >= 0 || PLAIN.island(wx, wz) == s.target, s.what + ": no wall ahead");
            for (int y = feet; y < s.targetFloor; y++) {
                assertFalse(MapParts.passable(GRID.id(wx, y, wz)), s.what + ": no wall at " + y);
            }
            // room over the bridge for the jump
            assertTrue(GRID.empty(floor(s.start[0]), floor(s.start[2]), feet, s.targetFloor + 2), s.what);
            assertTrue(FireballTraining.landed(s, PLAIN, new double[] {wx + 0.5, s.targetFloor, wz + 0.5}), s.what);
            assertFalse(FireballTraining.landed(s, PLAIN, s.start), s.what + ": still on the bridge");
        }
    }

    /** The steps of the way of an enemy follow each other block by block, a block up or down at most. */
    private static void assertSteps(MapParts.Situation s) {
        for (int i = 3; i + 2 < s.path.length; i += 3) {
            int dx = Math.abs(s.path[i] - s.path[i - 3]);
            int dz = Math.abs(s.path[i + 2] - s.path[i - 1]);
            assertEquals(1, dx + dz, s.what + ": steps " + (i / 3 - 1) + " and " + i / 3 + " do not touch");
            assertTrue(Math.abs(s.path[i + 1] - s.path[i - 2]) <= 1, s.what + ": a step of more than a block");
        }
    }

    @Test
    void anEnemyBridgesOrRunsFromAnotherIslandToTheOneOfThePlayer() {
        for (MapParts.Kind kind : new MapParts.Kind[] {MapParts.Kind.BRIDGER, MapParts.Kind.RUSHER}) {
            for (MapParts.Situation s : PLAIN.of(kind)) {
                int steps = s.path.length / 3;
                assertSteps(s);
                // from the edge of his island to the edge of the player's, where the player stands
                int from = PLAIN.island(s.path[0], s.path[2]);
                assertNotEquals(0, from, s.what);
                assertNotEquals(s.target, from, s.what);
                assertEquals(s.target, PLAIN.island(s.path[3 * (steps - 1)], s.path[3 * (steps - 1) + 2]), s.what);
                assertEquals(s.target, PLAIN.island(floor(s.start[0]), floor(s.start[2])), s.what);
                assertNotEquals(PLAIN.island(0, -20), from, s.what + ": the enemy does not come from the player's base");
                // his bridge is on his way, and he stands on it
                Set<String> way = new HashSet<>();
                for (int i = 0; i < s.path.length; i += 3) {
                    way.add(s.path[i] + " " + s.path[i + 1] + " " + s.path[i + 2]);
                }
                assertTrue(way.containsAll(bridge(s)), s.what);
                int on = FireballTraining.nearestStep(s.path, s.enemy[0], s.enemy[2]);
                assertEquals(floor(s.enemy[0]), s.path[3 * on], s.what);
                assertEquals(floor(s.enemy[2]), s.path[3 * on + 2], s.what);
                assertEquals(s.path[3 * on + 1] + 1, (int) s.enemy[1], s.what);
                // the player faces him
                double[] ahead = way(s.start[3]);
                double[] toHim = MapParts.unit(s.start[0], s.start[2], s.enemy[0], s.enemy[2]);
                assertTrue(ahead[0] * toHim[0] + ahead[1] * toHim[1] > 0.9, s.what);
                if (kind == MapParts.Kind.BRIDGER) {
                    // a few blocks left to bridge
                    int left = steps - 1 - on;
                    assertTrue(left >= 5 && left <= MapFinder.BRIDGER_LEFT + 1, s.what + ": " + left);
                } else {
                    // the whole bridge is there, he runs on it
                    assertTrue(steps - 2 <= s.bridge.length / 3, s.what);
                    assertTrue(steps - 1 - on <= MapFinder.RUSH_RUN + 1, s.what);
                }
            }
        }
    }

    @Test
    void anEnemyBridgesPastTheIslandOfThePlayerTheyKnockHimOffFromTheSide() {
        List<MapParts.Situation> sides = PLAIN.of(MapParts.Kind.SIDE);
        assertEquals(2, sides.size());
        for (MapParts.Situation s : sides) {
            assertSteps(s);
            int steps = s.path.length / 3;
            int from = PLAIN.island(s.path[0], s.path[2]);
            int to = PLAIN.island(s.path[3 * (steps - 1)], s.path[3 * (steps - 1) + 2]);
            // between two other islands
            assertNotEquals(0, from);
            assertNotEquals(0, to);
            assertNotEquals(s.target, from);
            assertNotEquals(s.target, to);
            assertEquals(s.target, PLAIN.island(floor(s.start[0]), floor(s.start[2])));
            // his way passes in front of the player, from the side
            double nearest = Double.MAX_VALUE;
            for (int i = 0; i < s.path.length; i += 3) {
                nearest = Math.min(nearest, Math.hypot(s.path[i] + 0.5 - s.start[0], s.path[i + 2] + 0.5 - s.start[2]));
            }
            assertTrue(nearest >= MapFinder.SIDE_MIN - 1 && nearest <= MapFinder.SIDE_MAX + 1, s.what + ": " + nearest);
        }
    }

    @Test
    void anEnemyThrowsFireballsFromAnotherIslandAtThePlayerOnTheirBridge() {
        for (MapParts.Situation s : PLAIN.of(MapParts.Kind.SEND)) {
            int feet = (int) s.start[1];
            assertEquals(MapFinder.SEND_OUT * 3, s.bridge.length, s.what);
            // the player on the last block of their bridge, out in the void
            assertEquals(floor(s.start[0]), s.bridge[s.bridge.length - 3], s.what);
            assertEquals(feet - 1, s.bridge[s.bridge.length - 2], s.what);
            assertEquals(floor(s.start[2]), s.bridge[s.bridge.length - 1], s.what);
            assertEquals(0, PLAIN.island(floor(s.start[0]), floor(s.start[2])), s.what);
            assertNull(s.path, s.what);
            // the enemy on another island, facing the player, far enough
            int his = PLAIN.island(floor(s.enemy[0]), floor(s.enemy[2]));
            assertNotEquals(0, his, s.what);
            assertNotEquals(PLAIN.island(0, -20), his, s.what + ": not on the player's base");
            assertTrue(Math.hypot(s.enemy[0] - s.start[0], s.enemy[2] - s.start[2]) >= MapFinder.SEND_LEFT, s.what);
            double[] ahead = way(s.enemy[3]);
            double[] toPlayer = MapParts.unit(s.enemy[0], s.enemy[2], s.start[0], s.start[2]);
            assertTrue(ahead[0] * toPlayer[0] + ahead[1] * toPlayer[1] > 0.9, s.what);
        }
    }

    @Test
    void aStraightWayGoesThroughColumnsThatTouchBySide() {
        // straight on: the columns between the two ends
        assertArrayEquals(new int[] {0, 1, 0, 2, 0, 3}, MapFinder.raster(0.5, 0.5, 0.5, 4.5));
        // across: each column touches the one before by a side, the ends left out
        int[] diagonal = MapFinder.raster(0.5, 0.5, 6.5, 4.5);
        int lastX = 0;
        int lastZ = 0;
        for (int i = 0; i < diagonal.length; i += 2) {
            assertEquals(1, Math.abs(diagonal[i] - lastX) + Math.abs(diagonal[i + 1] - lastZ), "column " + i / 2);
            lastX = diagonal[i];
            lastZ = diagonal[i + 1];
        }
        assertEquals(1, Math.abs(6 - lastX) + Math.abs(4 - lastZ), "the last column touches the end");
        assertEquals(0, MapFinder.raster(0.5, 0.5, 1.5, 0.5).length);
    }

    @Test
    void theStepOfAWayNearestToTheEnemy() {
        int[] path = {0, 5, 0, 0, 5, 1, 1, 5, 1, 1, 6, 2};
        assertEquals(0, FireballTraining.nearestStep(path, 0.4, 0.2));
        assertEquals(1, FireballTraining.nearestStep(path, 0.5, 1.6));
        assertEquals(3, FireballTraining.nearestStep(path, 1.6, 2.9));
    }

    @Test
    void theLoopPlaysEveryKindTheLevelsTheirOwn() {
        Set<MapParts.Kind> loop = new HashSet<>();
        for (int turn = 0; turn < FireballTraining.LOOP.length; turn++) {
            loop.add(FireballTraining.kind("loop", turn));
        }
        assertEquals(7, loop.size());
        assertEquals(MapParts.Kind.JUMP, FireballTraining.kind("loop", 0));
        assertEquals(MapParts.Kind.JUMP, FireballTraining.kind("loop", 7));
        for (int turn = 0; turn < 6; turn++) {
            assertTrue(FireballTraining.kind("jumps", turn) == MapParts.Kind.JUMP || FireballTraining.kind("jumps", turn)
                    == MapParts.Kind.CLIMB || FireballTraining.kind("jumps", turn) == MapParts.Kind.ESCAPE);
            assertTrue(FireballTraining.kind("knock", turn).enemyBridge());
            assertEquals(MapParts.Kind.SEND, FireballTraining.kind("sendback", turn));
        }
        assertEquals("loop", FireballTraining.variants()[0].id);
        assertEquals(4, FireballTraining.variants().length);
    }

    @Test
    void theLoopGoesThroughEveryMapEachPassInANewOrderAndFindsAKindOnOneMapOnly() {
        java.util.Random random = new java.util.Random(7);
        // three maps and a level of three kinds: in one fixed order, each map would always come with the same kind
        List<String> maps = java.util.Arrays.asList("A", "B", "C");
        MapLoopTraining.Loop<String> loop = new MapLoopTraining.Loop<>(maps, random);
        Set<String> pairs = new HashSet<>();
        String last = null;
        for (int pass = 0; pass < 30; pass++) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < maps.size(); i++) {
                String next = loop.next(each -> true, last);
                assertNotNull(next);
                // never the same map twice in a row, not even from one pass to the next
                assertNotEquals(last, next);
                seen.add(next);
                pairs.add(next + i);
                last = next;
            }
            assertEquals(3, seen.size());
        }
        assertEquals(9, pairs.size());
        // only one map of five has the kind: found wherever the pass is
        MapLoopTraining.Loop<String> five = new MapLoopTraining.Loop<>(java.util.Arrays.asList("A", "B", "C", "D", "E"), random);
        for (int i = 0; i < 30; i++) {
            assertEquals("D", five.next("D"::equals, last));
            last = five.next(each -> true, "D");
        }
        assertNull(five.next(each -> false, last));
    }

    @Test
    void theMapsWithoutAnySituationOfAPracticeAreLeftToTheOther() {
        // a part with pearls only: the fireball practice falls back on the plugin's own islands
        MapParts.Part pearlsOnly = new MapParts.Part("Pearls", "red", 14, PLAIN.copy, 0, 0, 1, 1, new int[] {0}, -20,
                PLAIN.of(MapParts.Kind.PEARL));
        MapLoopTraining.Places fireballs = new MapLoopTraining.Places(java.util.Collections.singletonList(pearlsOnly), true);
        assertEquals(1, fireballs.parts.size());
        assertEquals(MapParts.PLAIN, fireballs.parts.get(0).map);
        MapLoopTraining.Places pearls = new MapLoopTraining.Places(java.util.Collections.singletonList(pearlsOnly), false);
        assertEquals("Pearls", pearls.parts.get(0).map);
    }
}
