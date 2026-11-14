package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The bridges a Clutch Scenario game starts with, as a real game has them at 18 minutes: from the
 * base of each team, one to its diamond island and one to mid, one block wide, as players build
 * them. A bridge goes straight along an axis where its two islands face each other (Archway's
 * bases to mid, Sky Rise's to their diamond island). Else it follows the line between its two
 * ends, or a line at most {@value #SHIFT} blocks aside where that one is blocked (Archway's bases
 * to their diamond island, on the diagonal). Else it goes along one axis then the other (Sky
 * Rise's diamond islands to mid, round the tower on their corner, by the island between). It fills
 * the void between islands only: flat at the height of the edge it leaves, then a block up or down
 * a block, at its end, to the height of the edge it reaches, with room for a player over it all
 * along. Pure: the map comes through {@link Solid}.
 */
final class Bridges {

    /** Whether there is a solid block at this place of the map. */
    interface Solid {
        boolean at(int x, int y, int z);
    }

    /**
     * A column of the map is void where it has no solid block from this many blocks under the
     * lower floor of a bridge's two ends...
     */
    static final int BELOW = 4;
    /** ...up to this many over the higher one: room for a player standing there. */
    static final int ABOVE = 2;
    /** The edge a bridge reaches may be up to this many blocks higher still. */
    static final int CLIMB = 6;
    /** How many blocks aside from the line between its ends a bridge may go to find its way. */
    static final int SHIFT = 3;
    /** The bridge to mid leaves from the diamond island when that way is at most this much longer. */
    static final double VIA = 1.2;
    private static final int NONE = Integer.MIN_VALUE;

    private Bridges() {
    }

    /**
     * The bridges of a base whose spawn is here: one to the nearest diamond generator, and one to
     * mid (the nearest emerald generator). That one leaves from the diamond generator if it is on
     * the way (Sky Rise, where the diamond island stands between the base and mid), else from the
     * spawn (Archway). Each {from, to}, points {x, y, z} of the map where one stands.
     */
    static List<double[][]> routes(double[] spawn, List<double[]> diamonds, List<double[]> emeralds) {
        List<double[][]> routes = new ArrayList<>();
        double[] diamond = nearest(spawn, diamonds);
        double[] emerald = nearest(spawn, emeralds);
        if (diamond != null) {
            routes.add(new double[][] {spawn, diamond});
            double[] past = nearest(diamond, emeralds);
            if (past != null && distance(spawn, diamond) + distance(diamond, past) <= VIA * distance(spawn, emerald)) {
                routes.add(new double[][] {diamond, past});
                return routes;
            }
        }
        if (emerald != null) {
            routes.add(new double[][] {spawn, emerald});
        }
        return routes;
    }

    /**
     * The blocks of a bridge from a to b, points {x, y, z} where one stands (a spawn, a
     * generator): each {x, y, z}, from a's side. None if no bridge fits.
     */
    static List<int[]> plan(Solid solid, double[] a, double[] b) {
        int ax = floor(a[0]);
        int az = floor(a[2]);
        int bx = floor(b[0]);
        int bz = floor(b[2]);
        if (ax == bx && az == bz) {
            return Collections.emptyList();
        }
        Columns columns = new Columns(solid, floor(a[1]) - 1, floor(b[1]) - 1);
        boolean alongZ = Math.abs(b[2] - a[2]) >= Math.abs(b[0] - a[0]);
        // straight along an axis, the one of the longer way first, as near the middle as it can
        for (int turn = 0; turn < 2; turn++) {
            boolean z = (turn == 0) == alongZ;
            int from = z ? ax : az;
            int to = z ? bx : bz;
            double middle = z ? (a[0] + b[0]) / 2 : (a[2] + b[2]) / 2;
            for (int across : nearestFirst(Math.min(from, to), Math.max(from, to), middle)) {
                int[][] corners = z ? new int[][] {{ax, az}, {across, az}, {across, bz}, {bx, bz}}
                        : new int[][] {{ax, az}, {ax, across}, {bx, across}, {bx, bz}};
                List<int[]> blocks = columns.bridge(path(corners), 1);
                if (blocks != null) {
                    return blocks;
                }
            }
        }
        // along the line between the two ends, or beside it
        double length = Math.hypot(b[0] - a[0], b[2] - a[2]);
        double sideX = -(b[2] - a[2]) / length;
        double sideZ = (b[0] - a[0]) / length;
        for (int k = 0; k <= 2 * SHIFT; k++) {
            int shift = (k + 1) / 2 * (k % 2 == 1 ? 1 : -1);
            int[][] corners = {{ax, az}, {floor(a[0] + sideX * shift), floor(a[2] + sideZ * shift)},
                {floor(b[0] + sideX * shift), floor(b[2] + sideZ * shift)}, {bx, bz}};
            List<int[]> blocks = columns.bridge(path(corners), 1);
            if (blocks != null) {
                return blocks;
            }
        }
        // along one axis then the other, the longer way first: it may turn over the void
        int[][] turns = alongZ ? new int[][] {{ax, bz}, {bx, az}} : new int[][] {{bx, az}, {ax, bz}};
        for (int[] corner : turns) {
            List<int[]> blocks = columns.bridge(path(new int[][] {{ax, az}, corner, {bx, bz}}), -1);
            if (blocks != null) {
                return blocks;
            }
        }
        return Collections.emptyList();
    }

    /**
     * The columns from one corner to the next, each a step along x or z from the last (one can
     * walk from each to the next), the step that keeps nearer the straight line; each {x, z, n},
     * n the stretch it is on (from 0), the first corner's column on stretch 0.
     */
    static List<int[]> path(int[][] corners) {
        List<int[]> cells = new ArrayList<>();
        cells.add(new int[] {corners[0][0], corners[0][1], 0});
        for (int n = 1; n < corners.length; n++) {
            int ax = corners[n - 1][0];
            int az = corners[n - 1][1];
            int bx = corners[n][0];
            int bz = corners[n][1];
            int x = ax;
            int z = az;
            int stepX = bx > x ? 1 : -1;
            int stepZ = bz > z ? 1 : -1;
            while (x != bx || z != bz) {
                boolean alongX = z == bz || x != bx
                        && Math.abs(aside(ax, az, bx, bz, x + stepX, z)) <= Math.abs(aside(ax, az, bx, bz, x, z + stepZ));
                if (alongX) {
                    x += stepX;
                } else {
                    z += stepZ;
                }
                cells.add(new int[] {x, z, n - 1});
            }
        }
        return cells;
    }

    /** How far (times the length of the line) this column is from the line from a to b. */
    private static long aside(int ax, int az, int bx, int bz, int x, int z) {
        return (long) (x - ax) * (bz - az) - (long) (z - az) * (bx - ax);
    }

    /** The whole numbers from low to high, the nearest the middle (of their block) first. */
    private static List<Integer> nearestFirst(int low, int high, double middle) {
        List<Integer> numbers = new ArrayList<>();
        for (int n = low; n <= high; n++) {
            numbers.add(n);
        }
        numbers.sort((p, q) -> {
            int nearer = Double.compare(Math.abs(p + 0.5 - middle), Math.abs(q + 0.5 - middle));
            return nearer != 0 ? nearer : Integer.compare(p, q);
        });
        return numbers;
    }

    private static double[] nearest(double[] from, List<double[]> points) {
        double[] best = null;
        for (double[] point : points) {
            if (best == null || distance(from, point) < distance(from, best)) {
                best = point;
            }
        }
        return best;
    }

    private static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    /** The map between the two ends of a bridge: which columns are void, where their floor is. */
    private static final class Columns {
        private final Solid solid;
        private final int low;
        private final int high;
        private final Map<Long, Boolean> voids = new HashMap<>();

        Columns(Solid solid, int floorA, int floorB) {
            this.solid = solid;
            low = Math.min(floorA, floorB) - BELOW;
            high = Math.max(floorA, floorB) + ABOVE;
        }

        boolean empty(int x, int z) {
            Long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
            Boolean empty = voids.get(key);
            if (empty == null) {
                empty = true;
                for (int y = low; y <= high && empty; y++) {
                    empty = !solid.at(x, y, z);
                }
                voids.put(key, empty);
            }
            return empty;
        }

        /** The highest floor of this column up to this height: a solid block with room for a player on it. */
        int floor(int x, int z, int top) {
            for (int y = top; y >= low; y--) {
                if (solid.at(x, y, z) && !solid.at(x, y + 1, z) && !solid.at(x, y + 2, z)) {
                    return y;
                }
            }
            return NONE;
        }

        /**
         * The blocks of a bridge along these columns, over each run of void ones: null if the way
         * does not start and end on ground, if a run is off its main stretch (main -1: none), if an
         * edge of a run has no floor or the two are too far apart in height for a block a step, or
         * if something stands on the bridge or over it.
         */
        List<int[]> bridge(List<int[]> cells, int main) {
            int count = cells.size();
            boolean[] empty = new boolean[count];
            for (int i = 0; i < count; i++) {
                empty[i] = empty(cells.get(i)[0], cells.get(i)[1]);
            }
            if (empty[0] || empty[count - 1]) {
                return null;
            }
            List<int[]> blocks = new ArrayList<>();
            int i = 0;
            while (i < count) {
                if (!empty[i]) {
                    i++;
                    continue;
                }
                int j = i;
                while (empty[j]) {
                    if (main >= 0 && cells.get(j)[2] != main) {
                        return null;
                    }
                    j++;
                }
                int[] before = cells.get(i - 1);
                int[] after = cells.get(j);
                int from = floor(before[0], before[1], high);
                int to = floor(after[0], after[1], high + CLIMB);
                int length = j - i;
                if (from == NONE || to == NONE || Math.abs(to - from) > length) {
                    return null;
                }
                int steps = Math.abs(to - from);
                for (int k = 0; k < length; k++) {
                    int y = k < length - steps ? from : from + Integer.signum(to - from) * (k - (length - steps) + 1);
                    int[] cell = cells.get(i + k);
                    if (solid.at(cell[0], y, cell[1]) || solid.at(cell[0], y + 1, cell[1]) || solid.at(cell[0], y + 2, cell[1])) {
                        return null;
                    }
                    blocks.add(new int[] {cell[0], y, cell[1]});
                }
                i = j;
            }
            return blocks.isEmpty() ? null : blocks;
        }
    }
}
