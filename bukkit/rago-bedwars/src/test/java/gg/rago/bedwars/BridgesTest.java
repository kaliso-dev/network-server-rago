package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The bridges a Clutch Scenario game starts with. */
class BridgesTest {

    /** An island: solid from y 60 to its floor, over x0..x1 and z0..z1. */
    private static final class Island {
        final int x0;
        final int x1;
        final int z0;
        final int z1;
        final int floor;

        Island(int x0, int x1, int z0, int z1, int floor) {
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.floor = floor;
        }

        boolean at(int x, int y, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1 && y >= 60 && y <= floor;
        }
    }

    private static Bridges.Solid map(Island... islands) {
        return (x, y, z) -> {
            for (Island island : islands) {
                if (island.at(x, y, z)) {
                    return true;
                }
            }
            return false;
        };
    }

    /** A base and mid face to face along z, the base off to the side: the bridge goes straight, halfway. */
    @Test
    void whereTheIslandsFaceEachOtherTheBridgeGoesStraightAlongAnAxis() {
        Bridges.Solid solid = map(new Island(-10, 10, -40, -30, 64), new Island(-15, 15, 0, 20, 64));
        List<int[]> blocks = Bridges.plan(solid, new double[] {-5.5, 65, -35.5}, new double[] {0.5, 65, 10.5});
        assertEquals(29, blocks.size(), "z -29 to -1: the void between them");
        for (int i = 0; i < blocks.size(); i++) {
            assertArrayEquals(new int[] {-3, 64, -29 + i}, blocks.get(i), "straight at x -3, halfway, flat");
        }
    }

    /** The edge it reaches two blocks higher: flat, then a block up a block, flush with it. */
    @Test
    void itStepsUpABlockABlockAtTheEnd() {
        Bridges.Solid solid = map(new Island(-10, 10, -40, -30, 64), new Island(-15, 15, 0, 20, 66));
        List<int[]> blocks = Bridges.plan(solid, new double[] {0.5, 65, -35.5}, new double[] {0.5, 67, 10.5});
        assertEquals(29, blocks.size());
        assertEquals(64, blocks.get(26)[1]);
        assertEquals(65, blocks.get(27)[1]);
        assertEquals(66, blocks.get(28)[1], "flush with the floor it reaches");
        List<int[]> back = Bridges.plan(solid, new double[] {0.5, 67, 10.5}, new double[] {0.5, 65, -35.5});
        assertEquals(66, back.get(0)[1], "the other way: from the higher edge, down at the end");
        assertEquals(65, back.get(27)[1]);
        assertEquals(64, back.get(28)[1]);
    }

    /** Islands on the diagonal, facing on no axis: the bridge follows the line, a block after a block. */
    @Test
    void onTheDiagonalItFollowsTheLineBetweenThem() {
        Bridges.Solid solid = map(new Island(-10, 0, -40, -30, 64), new Island(20, 30, -10, 0, 64));
        List<int[]> blocks = Bridges.plan(solid, new double[] {-5.5, 65, -35.5}, new double[] {25.5, 65, -5.5});
        assertFalse(blocks.isEmpty());
        walkable(solid, blocks);
        int[] first = blocks.get(0);
        int[] last = blocks.get(blocks.size() - 1);
        assertTrue(first[0] == 1 || first[2] == -29, "it leaves the corner of the base: " + Arrays.toString(first));
        assertTrue(last[0] == 19 || last[2] == -11, "it reaches the corner of the other: " + Arrays.toString(last));
        for (int[] block : blocks) {
            assertTrue(Math.abs((block[0] + 5.5) - (block[2] + 35.5)) <= 3, "along the line: " + Arrays.toString(block));
        }
    }

    /** A tower over the middle of the void: no line gets by it, the bridge goes along x then along z. */
    @Test
    void roundWhatStandsInTheWayItTurns() {
        Island tower = new Island(5, 15, -25, -15, 90);
        Bridges.Solid solid = map(new Island(-10, 0, -40, -30, 64), new Island(20, 30, -10, 0, 64), tower);
        List<int[]> blocks = Bridges.plan(solid, new double[] {-5.5, 65, -35.5}, new double[] {25.5, 65, -5.5});
        assertEquals(25 + 25, blocks.size(), "x 1 to 25 at z -36, then z -35 to -11 at x 25");
        walkable(solid, blocks);
        assertArrayEquals(new int[] {1, 64, -36}, blocks.get(0));
        assertArrayEquals(new int[] {25, 64, -36}, blocks.get(24), "the turn");
        assertArrayEquals(new int[] {25, 64, -11}, blocks.get(49));
        for (int[] block : blocks) {
            assertFalse(tower.at(block[0], 64, block[2]), "never through the tower");
        }
    }

    /** Islands too far apart in height for a bridge a block a step, or a target out of reach: none. */
    @Test
    void noBridgeWhereNoneFits() {
        Bridges.Solid steep = map(new Island(-10, 10, -40, -30, 64), new Island(-15, 15, -27, 20, 70));
        assertTrue(Bridges.plan(steep, new double[] {0.5, 65, -35.5}, new double[] {0.5, 71, 10.5}).isEmpty(),
                "6 blocks up over a void of 2");
        Bridges.Solid wall = map(new Island(-10, 10, -40, -30, 64), new Island(-15, 15, 0, 20, 64),
                new Island(-60, 60, -12, -10, 100));
        assertTrue(Bridges.plan(wall, new double[] {0.5, 65, -35.5}, new double[] {0.5, 65, 10.5}).isEmpty(),
                "a wall across the whole way");
        assertTrue(Bridges.plan(steep, new double[] {0.5, 65, -35.5}, new double[] {0.5, 65, -35.5}).isEmpty(),
                "from a place to itself");
    }

    /** Over islands between, it bridges each void and walks the ground. */
    @Test
    void itBridgesEachVoidOnTheWay() {
        Bridges.Solid solid = map(new Island(-10, 10, -40, -30, 64), new Island(-3, 3, -20, -15, 64),
                new Island(-15, 15, 0, 20, 64));
        List<int[]> blocks = Bridges.plan(solid, new double[] {0.5, 65, -35.5}, new double[] {0.5, 65, 10.5});
        assertEquals(9 + 14, blocks.size(), "z -29 to -21, then -14 to -1");
        for (int[] block : blocks) {
            assertTrue(block[2] < -20 || block[2] > -15, "nothing on the island between");
        }
    }

    /**
     * Sky Rise: the diamond island between the base and mid, so the bridge to mid leaves from it.
     * Archway: the diamond island aside, so both bridges leave from the base, the one to mid to the
     * lower emerald generator.
     */
    @Test
    void theBridgeToMidLeavesFromTheDiamondIslandWhenItIsOnTheWay() {
        double[] red = {-35.5, 71, -89.5};
        List<double[]> diamonds = Arrays.asList(new double[] {-36.5, 65, 37.5}, new double[] {-36.5, 69, -36.5},
                new double[] {37.5, 65, -36.5}, new double[] {37.5, 69, 37.5});
        List<double[]> emeralds = Arrays.asList(new double[] {-8.5, 66, 9.5}, new double[] {9.5, 66, -8.5},
                new double[] {-9.5, 70, -9.5}, new double[] {10.5, 70, 10.5});
        List<double[][]> skyRise = Bridges.routes(red, diamonds, emeralds);
        assertEquals(2, skyRise.size());
        assertArrayEquals(new double[][] {red, diamonds.get(1)}, skyRise.get(0), "to its diamond island");
        assertArrayEquals(new double[][] {diamonds.get(1), emeralds.get(2)}, skyRise.get(1), "on to mid");

        double[] archwayRed = {-13.5, 65, -72.5};
        List<double[]> archwayDiamonds = Arrays.asList(new double[] {-48.5, 66, -33.5}, new double[] {-33.5, 66, 49.5},
                new double[] {34.5, 66, -48.5}, new double[] {49.5, 66, 34.5});
        List<double[]> archwayEmeralds = Arrays.asList(new double[] {0.5, 75, 0.5}, new double[] {0.5, 65, 0.5});
        List<double[][]> archway = Bridges.routes(archwayRed, archwayDiamonds, archwayEmeralds);
        assertEquals(2, archway.size());
        assertArrayEquals(new double[][] {archwayRed, archwayDiamonds.get(0)}, archway.get(0));
        assertArrayEquals(new double[][] {archwayRed, archwayEmeralds.get(1)}, archway.get(1), "from the base, to mid's floor");

        assertTrue(Bridges.routes(red, new ArrayList<>(), new ArrayList<>()).isEmpty(), "no generator, no bridge");
        assertEquals(1, Bridges.routes(red, new ArrayList<>(), emeralds).size(), "to mid only");
    }

    /** Each step of a path is one block along x or z, from the first corner to the last. */
    @Test
    void aPathGoesABlockAtATime() {
        List<int[]> cells = Bridges.path(new int[][] {{0, 0}, {7, -3}, {7, 4}});
        assertArrayEquals(new int[] {0, 0, 0}, cells.get(0));
        assertArrayEquals(new int[] {7, 4, 1}, cells.get(cells.size() - 1));
        assertEquals(7 + 3 + 7 + 1, cells.size());
        for (int i = 1; i < cells.size(); i++) {
            int dx = Math.abs(cells.get(i)[0] - cells.get(i - 1)[0]);
            int dz = Math.abs(cells.get(i)[1] - cells.get(i - 1)[1]);
            assertEquals(1, dx + dz, "a step along x or z");
        }
    }

    /** One block after the other along x or z, each with room over it, none in an island. */
    private static void walkable(Bridges.Solid solid, List<int[]> blocks) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < blocks.size(); i++) {
            int[] block = blocks.get(i);
            assertTrue(seen.add(block[0] + " " + block[2]), "one block a column");
            assertFalse(solid.at(block[0], block[1], block[2]) || solid.at(block[0], block[1] + 1, block[2])
                    || solid.at(block[0], block[1] + 2, block[2]), "room over it");
            if (i > 0) {
                int[] last = blocks.get(i - 1);
                assertEquals(1, Math.abs(block[0] - last[0]) + Math.abs(block[2] - last[2]), "next to the last one");
                assertTrue(Math.abs(block[1] - last[1]) <= 1, "a block a step at most");
            }
        }
    }
}
