package gg.rago.practice;

import static gg.rago.practice.MapParts.floor;
import static gg.rago.practice.MapParts.passable;
import static gg.rago.practice.MapParts.unit;
import static gg.rago.practice.MapParts.yaw;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the situations of a part of a map (see {@link MapParts}): its islands (the base, its
 * diamond islands, the middle), the edge of each towards the others, the straight bridges through
 * the void between them, and on them the situations of the pearl practice and of the fireball
 * practice. Pure.
 */
final class MapFinder {

    /** The shortest and the longest bridge between the edges of two islands in a situation of the fireball practice. */
    static final double BRIDGE_MIN = 8;
    static final double BRIDGE_MAX = 45;
    /** Two islands this many blocks apart in height at most have a bridge between them (a step a block each). */
    static final int MAX_RISE = 4;
    /** A fireball jump: from the start to the far side of the gap (7 to 9 blocks of void between them). */
    static final int[] HOLES = {8, 9, 10};
    /** Blocks of air over the feet all along a fireball jump over a gap: the body goes some 4 blocks up (no ceiling to bump into). */
    static final int JUMP_ROOM = 5;
    /** An escape: the enemy starts on the edge of the island behind, this far from the player at least. */
    static final double ESCAPE_BEHIND = 7;
    /** The blocks a bridger has left to bridge at the start, at most: he started earlier. */
    static final int BRIDGER_LEFT = 14;
    /** The blocks a rusher runs at most: he started on the bridge. */
    static final int RUSH_RUN = 22;
    /** Knocking a bridger off from the side: from the player to his way, this far at least and at most. */
    static final double SIDE_MIN = 6;
    static final double SIDE_MAX = 42;
    /** Farther from his bridge than this, the player has bridged out of their island towards it, to this far. */
    static final double SIDE_NEAR = 22;
    /** A fireball jump up: the bridge of the player, out from the side of the island, this many blocks. */
    static final int CLIMB_OUT = 6;
    /** Sending fireballs back: the bridge of the player out of their island, and the blocks at least between it and the enemy. */
    static final int SEND_OUT = 5;
    static final int SEND_LEFT = 8;

    /** An island of the part: its number, its point (from the origin), its floor there, its name in the messages. */
    static final class Island {
        final int label;
        final double x;
        final double z;
        final int floor;
        final String name;
        /** "your base", "the diamond island", "the middle": and with "onto" ("onto your base"...). */
        final boolean base;

        Island(int label, double x, double z, int floor, String name, boolean base) {
            this.label = label;
            this.x = x;
            this.z = z;
            this.floor = floor;
            this.name = name;
            this.base = base;
        }
    }

    /**
     * A straight bridge from the edge of one island to the edge of another, through the void: flat
     * at the height of the higher one, a step down a block at a time by the lower one (a block up
     * each step from it, as a player bridges up).
     */
    static final class Line {
        final Island from;
        final Island to;
        /** The edges, x, y (the feet), z: the last places to stand on each island, facing the other. */
        final double[] start;
        final double[] end;
        /** The way from the start to the end: x, z, of length 1. */
        final double[] way;
        /** Its columns between the two edges, in order from the first island: x, z each. */
        final int[] cells;
        /** The height of the block of each column (the feet one higher). */
        final int[] ys;
        /** By column: true where the ground of an island is already there at that height (no block to put). */
        final boolean[] ground;

        Line(Island from, Island to, double[] start, double[] end, int[] cells, int[] ys, boolean[] ground) {
            this.from = from;
            this.to = to;
            this.start = start;
            this.end = end;
            this.way = unit(start[0], start[2], end[0], end[2]);
            this.cells = cells;
            this.ys = ys;
            this.ground = ground;
        }

        double length() {
            return Math.hypot(end[0] - start[0], end[2] - start[2]);
        }

        int count() {
            return cells.length / 2;
        }

        /** How far along the way (x, z) is, from the start. */
        double along(double x, double z) {
            return (x - start[0]) * way[0] + (z - start[2]) * way[1];
        }

        /** How far to the side of the way (x, z) is (to the right, positive). */
        double aside(double x, double z) {
            return -(x - start[0]) * way[1] + (z - start[2]) * way[0];
        }

        /** How far along the way the middle of column i is. */
        double alongOf(int i) {
            return along(cells[2 * i] + 0.5, cells[2 * i + 1] + 0.5);
        }

        /** The blocks to put from column i to j - 1 (not where the ground is): x, y, z each. */
        int[] blocks(int i, int j) {
            int count = 0;
            for (int k = i; k < j; k++) {
                if (!ground[k]) {
                    count++;
                }
            }
            int[] blocks = new int[count * 3];
            int n = 0;
            for (int k = i; k < j; k++) {
                if (!ground[k]) {
                    blocks[n++] = cells[2 * k];
                    blocks[n++] = ys[k];
                    blocks[n++] = cells[2 * k + 1];
                }
            }
            return blocks;
        }

        /**
         * The way of an enemy over the bridge, the block under each step (x, y, z each): the edge
         * of the first island, the columns up to column last (included), and the edge of the other
         * island when all of them.
         */
        int[] path(int last) {
            boolean whole = last >= count() - 1;
            int steps = Math.min(last + 1, count());
            int[] path = new int[3 * (1 + steps + (whole ? 1 : 0))];
            path[0] = floor(start[0]);
            path[1] = (int) start[1] - 1;
            path[2] = floor(start[2]);
            for (int k = 0; k < steps; k++) {
                path[3 + 3 * k] = cells[2 * k];
                path[4 + 3 * k] = ys[k];
                path[5 + 3 * k] = cells[2 * k + 1];
            }
            if (whole) {
                path[path.length - 3] = floor(end[0]);
                path[path.length - 2] = (int) end[1] - 1;
                path[path.length - 1] = floor(end[2]);
            }
            return path;
        }

        /** Where one stands on column i of the bridge (-1: on the edge of the first island), facing along it: x, y, z, yaw. */
        double[] standOn(int i) {
            if (i < 0) {
                return new double[] {start[0], start[1], start[2], yaw(way)};
            }
            return new double[] {cells[2 * i] + 0.5, ys[i] + 1, cells[2 * i + 1] + 0.5, yaw(way)};
        }
    }

    final MapParts.Grid grid;
    final int[] labels;
    final Set<Integer> used = new HashSet<>();
    final List<MapParts.Situation> found = new ArrayList<>();
    Island base;
    final List<Island> diamonds = new ArrayList<>();
    Island middle;
    int lowestFloor = Integer.MAX_VALUE;

    MapFinder(MapParts.Grid grid, int[] labels, MapParts.Anchors anchors, int[] origin) {
        this.grid = grid;
        this.labels = labels;
        base = island(anchors.spawn, origin, 3, "your base", true);
        for (double[] diamond : anchors.ownDiamonds()) {
            Island island = island(diamond, origin, 3, "the diamond island", false);
            if (island != null && (base == null || island.label != base.label)) {
                diamonds.add(island);
            }
        }
        double[] mid = anchors.middle();
        if (mid != null) {
            Island island = island(mid, origin, 8, "the middle", false);
            if (island != null && (base == null || island.label != base.label) && !sameAsADiamond(island)) {
                middle = island;
            }
        }
    }

    private boolean sameAsADiamond(Island island) {
        for (Island diamond : diamonds) {
            if (diamond.label == island.label) {
                return true;
            }
        }
        return false;
    }

    /** The island under a point of the map (or the nearest within reach), with the floor where one stands there. */
    private Island island(double[] point, int[] origin, int reach, String name, boolean base) {
        double x = point[0] - origin[0];
        double z = point[2] - origin[2];
        int y = floor(point[1] + 0.01) - origin[1];
        for (int d = 0; d <= reach; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) {
                        continue;
                    }
                    int bx = floor(x) + dx;
                    int bz = floor(z) + dz;
                    int label = grid.label(labels, bx, bz);
                    if (label == 0) {
                        continue;
                    }
                    int feet = grid.highestStand(bx, bz, y - 14, y + 2);
                    if (feet == Integer.MIN_VALUE) {
                        continue;
                    }
                    return new Island(label, bx + 0.5, bz + 0.5, feet, name, base);
                }
            }
        }
        return null;
    }

    /** Every situation of the part, those of the pearls first. */
    List<MapParts.Situation> situations() {
        if (base == null || diamonds.isEmpty()) {
            return found;
        }
        pearls();
        fireballs();
        return found;
    }

    /** The last place to stand on the island, walking from its point towards (tx, tz): x, y (feet), z; null for none. */
    double[] edge(Island from, double tx, double tz) {
        double dx = tx - from.x;
        double dz = tz - from.z;
        double length = Math.hypot(dx, dz);
        double ux = dx / length;
        double uz = dz / length;
        int feet = from.floor;
        double[] last = null;
        int lastX = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (double t = 0; t < length; t += 0.25) {
            int bx = floor(from.x + ux * t);
            int bz = floor(from.z + uz * t);
            if (bx == lastX && bz == lastZ) {
                continue;
            }
            lastX = bx;
            lastZ = bz;
            if (grid.label(labels, bx, bz) != from.label) {
                break;
            }
            // one walks up a block at most, and may drop a few
            int y = grid.highestStand(bx, bz, feet - 8, feet + 1);
            if (y != Integer.MIN_VALUE) {
                feet = y;
                last = new double[] {bx + 0.5, y, bz + 0.5};
            }
        }
        return last;
    }

    private void add(MapParts.Situation situation, Island... islands) {
        found.add(situation);
        for (Island island : islands) {
            used.add(island.label);
            lowestFloor = Math.min(lowestFloor, island.floor);
        }
        lowestFloor = Math.min(lowestFloor, (int) Math.floor(situation.start[1]));
    }

    // --- The pearls -----------------------------------------------------------------------------

    private void pearls() {
        for (Island diamond : diamonds) {
            pearl(base, diamond);
            pearl(diamond, base);
            if (middle != null) {
                pearl(diamond, middle);
                pearl(middle, diamond);
            }
        }
        for (Island diamond : diamonds) {
            save(base, diamond);
            clutch(base, diamond);
            save(diamond, middle != null ? middle : base);
            clutch(diamond, base);
            if (middle != null) {
                clutch(diamond, middle);
                save(middle, diamond);
                clutch(middle, diamond);
            }
        }
    }

    /** Pearl from the edge of one island onto another. */
    void pearl(Island from, Island to) {
        double[] start = edge(from, to.x, to.z);
        double[] end = edge(to, from.x, from.z);
        if (start == null || end == null) {
            return;
        }
        double gap = Math.hypot(end[0] - start[0], end[2] - start[2]);
        if (gap < MapParts.GAP_MIN || gap > MapParts.GAP_MAX || Math.abs(end[1] - start[1]) > MapParts.HEIGHT_MAX) {
            return;
        }
        double[] way = unit(start[0], start[2], to.x, to.z);
        if (!clearAhead(start, way, 2.5)) {
            return;
        }
        String onto = to.base ? "back to your base" : "onto " + to.name;
        add(new MapParts.Situation(MapParts.Kind.PEARL, "Pearl " + onto + ".", "Pearl " + onto,
                new double[] {start[0], start[1], start[2], yaw(way)}, to.label, (int) end[1], null, null), from, to);
    }

    /** On the edge of an island towards another one, a hit throws the player off: pearl back onto it. */
    void save(Island on, Island towards) {
        double[] edge = edge(on, towards.x, towards.z);
        if (edge == null) {
            return;
        }
        double[] out = unit(on.x, on.z, towards.x, towards.z);
        int bx = floor(edge[0] - out[0] * 1.5);
        int bz = floor(edge[2] - out[1] * 1.5);
        if (grid.label(labels, bx, bz) != on.label) {
            return;
        }
        int feet = grid.highestStand(bx, bz, (int) edge[1] - 1, (int) edge[1] + 1);
        if (feet == Integer.MIN_VALUE || !voidBeyond(edge, out, 1, 8, (int) edge[1] - 25, (int) edge[1] + 3)) {
            return;
        }
        // nothing stops the hit on the way to the void (a step up, a fence...), at the height of the body
        double[] from = {bx + 0.5, feet, bz + 0.5};
        double reach = Math.hypot(edge[0] - from[0], edge[2] - from[2]) + 1;
        for (double t = 0.5; t <= reach; t += 0.25) {
            int x = floor(from[0] + out[0] * t);
            int z = floor(from[2] + out[1] * t);
            if (!passable(grid.id(x, feet, z)) || !passable(grid.id(x, feet + 1, z))) {
                return;
            }
        }
        String what = on.base ? "your base" : on.name;
        add(new MapParts.Situation(MapParts.Kind.SAVE, "A hit throws you off " + what + ": pearl back onto it, blocks if you"
                + " land short.", "Save: back onto " + what, new double[] {bx + 0.5, feet, bz + 0.5,
                    yaw(new double[] {-out[0], -out[1]})}, on.label, feet, out, null), on, towards);
    }

    /** The outermost column of the island on the way from its point towards another, with this many blocks of wall from y1 to y2; -1 for none. */
    private double face(Island on, double[] out, double length, int y1, int y2, int walls) {
        double face = -1;
        for (double t = 0; t < length; t += 0.25) {
            int bx = floor(on.x + out[0] * t);
            int bz = floor(on.z + out[1] * t);
            if (grid.label(labels, bx, bz) == on.label) {
                int wall = 0;
                for (int y = y1; y <= y2; y++) {
                    if (!passable(grid.id(bx, y, bz))) {
                        wall++;
                    }
                }
                if (wall >= walls) {
                    face = t;
                }
            } else if (face >= 0 && t > face + 3) {
                break;
            }
        }
        return face;
    }

    /**
     * Out on a bridge a few blocks under an island, facing its side: a hit throws the player off,
     * the pearl reaches the wall and the player places blocks under themselves.
     */
    void clutch(Island on, Island side) {
        double[] edge = edge(on, side.x, side.z);
        if (edge == null) {
            return;
        }
        double[] out = unit(on.x, on.z, side.x, side.z);
        double length = Math.hypot(side.x - on.x, side.z - on.z);
        for (int down : new int[] {4, 3, 5}) {
            int feet = (int) edge[1] - down;
            // the face: the outermost column of the island on the line with a wall at the height of the feet
            double face = face(on, out, length, feet - 2, feet + 1, 2);
            if (face < 0) {
                continue;
            }
            for (int far : new int[] {12, 13, 11, 14, 10}) {
                double px = on.x + out[0] * (face + far);
                double pz = on.z + out[1] * (face + far);
                int bx = floor(px);
                int bz = floor(pz);
                if (!grid.inside(bx, bz) || grid.label(labels, bx, bz) != 0 || !grid.empty(bx, bz, feet - 6, feet + 2)) {
                    continue;
                }
                double[] at = {bx + 0.5, feet, bz + 0.5};
                // nothing between the bridge and the wall, and nothing behind it where the hit throws
                if (!voidBeyond(at, new double[] {-out[0], -out[1]}, 1, far - 1.5, feet - 4, feet + 3)
                        || !voidBeyond(at, out, 1, 6, feet - 10, feet + 3)) {
                    continue;
                }
                String what = on.base ? "your base" : on.name;
                add(new MapParts.Situation(MapParts.Kind.CLUTCH, "Hit off your bridge under " + what + ": pearl to its side,"
                        + " then place blocks under you at once.", "Clutch: pearl to the wall of " + what,
                        new double[] {at[0], feet, at[2], yaw(new double[] {-out[0], -out[1]})}, on.label, (int) edge[1],
                        out, new int[] {bx, feet - 1, bz}), on, side);
                return;
            }
        }
    }

    /** True if the way at the height of the eyes is free this far: the pearl is not stopped at once. */
    boolean clearAhead(double[] from, double[] way, double length) {
        for (double t = 0.5; t <= length; t += 0.5) {
            int bx = floor(from[0] + way[0] * t);
            int bz = floor(from[2] + way[1] * t);
            int y = (int) from[1];
            if (!passable(grid.id(bx, y, bz)) || !passable(grid.id(bx, y + 1, bz))) {
                return false;
            }
        }
        return true;
    }

    /** True if, from this far beyond this place along the way, no block stands from y1 to y2 for this length. */
    boolean voidBeyond(double[] from, double[] way, double start, double length, int y1, int y2) {
        for (double t = start; t <= length; t += 0.5) {
            int bx = floor(from[0] + way[0] * t);
            int bz = floor(from[2] + way[1] * t);
            if (!grid.inside(bx, bz) || !grid.empty(bx, bz, y1, y2)) {
                return false;
            }
        }
        return true;
    }

    // --- The fireballs --------------------------------------------------------------------------

    /** The islands of the part, the base first. */
    private List<Island> islands() {
        List<Island> all = new ArrayList<>();
        all.add(base);
        all.addAll(diamonds);
        if (middle != null) {
            all.add(middle);
        }
        return all;
    }

    private void fireballs() {
        // the bridges between the base and each diamond island, and between each diamond island and the middle, both ways
        List<Island[]> pairs = new ArrayList<>();
        for (Island diamond : diamonds) {
            pairs.add(new Island[] {base, diamond});
            pairs.add(new Island[] {diamond, base});
            if (middle != null) {
                pairs.add(new Island[] {diamond, middle});
                pairs.add(new Island[] {middle, diamond});
            }
        }
        List<Line> lines = new ArrayList<>();
        for (Island[] pair : pairs) {
            Line line = line(pair[0], pair[1], true);
            if (line != null) {
                lines.add(line);
            }
        }
        for (Line line : lines) {
            jump(line);
        }
        for (Line line : lines) {
            escape(line);
        }
        List<Island> all = islands();
        for (Island on : all) {
            for (Island towards : all) {
                if (on != towards) {
                    climb(on, towards);
                }
            }
        }
        for (Line line : lines) {
            bridger(line);
        }
        for (Line line : lines) {
            rusher(line);
        }
        for (Line line : lines) {
            for (Island island : all) {
                side(line, island);
            }
        }
        // the player only bridges out of their island: the bridge needs not reach the other one
        for (Island[] pair : pairs) {
            Line line = line(pair[0], pair[1], false);
            if (line != null) {
                send(line);
            }
        }
    }

    /**
     * The bridge from the edge of one island to the edge of the other, if it holds: through the
     * void, the islands {@link #MAX_RISE} blocks apart in height at most. When not whole, the bridge
     * holds as far as its first column that does not, and needs not reach the other island.
     */
    Line line(Island from, Island to, boolean whole) {
        double[] start = edge(from, to.x, to.z);
        double[] end = edge(to, from.x, from.z);
        if (start == null || end == null) {
            return null;
        }
        double length = Math.hypot(end[0] - start[0], end[2] - start[2]);
        int startFeet = (int) start[1];
        int endFeet = (int) end[1];
        if (length < BRIDGE_MIN || length > BRIDGE_MAX || whole && Math.abs(endFeet - startFeet) > MAX_RISE) {
            return null;
        }
        if (startFeet != endFeet) {
            // from the edge of the lower island, a jump up onto the bridge: room over the head for it
            double[] lower = startFeet < endFeet ? start : end;
            if (!grid.empty(floor(lower[0]), floor(lower[2]), (int) lower[1] + 2, (int) lower[1] + 2)) {
                return null;
            }
        }
        int[] cells = raster(start[0], start[2], end[0], end[2]);
        int n = cells.length / 2;
        int high = Math.max(startFeet, endFeet) - 1;
        int low = Math.min(startFeet, endFeet) - 1;
        int[] ys = new int[n];
        boolean[] ground = new boolean[n];
        int kept = n;
        for (int i = 0; i < n && kept == n; i++) {
            // a block up each column from the lower island, up to the height of the higher one
            int fromLow = endFeet > startFeet ? i + 1 : n - i;
            int y = Math.min(high, low + fromLow);
            int x = cells[2 * i];
            int z = cells[2 * i + 1];
            ys[i] = y;
            // room for the body, and for a jump up to the next column
            if (grid.label(labels, x, z) != 0 && grid.standable(x, y + 1, z) && grid.empty(x, z, y + 1, y + 3)) {
                // the ground of an island, at that height already
                ground[i] = true;
            } else if (!grid.empty(x, z, y - 2, y + 3)) {
                // something in the way, or right under it
                kept = i;
            }
        }
        if (kept < n) {
            if (whole || kept == 0) {
                return null;
            }
            cells = java.util.Arrays.copyOf(cells, 2 * kept);
            ys = java.util.Arrays.copyOf(ys, kept);
            ground = java.util.Arrays.copyOf(ground, kept);
        }
        return n == 0 ? null : new Line(from, to, start, end, cells, ys, ground);
    }

    /**
     * The columns the straight way from (ax, az) to (bx, bz) goes through, each touching the one
     * before by a side, without the first and the last: x, z each.
     */
    static int[] raster(double ax, double az, double bx, double bz) {
        double length = Math.hypot(bx - ax, bz - az);
        int lastX = floor(ax);
        int lastZ = floor(az);
        int endX = floor(bx);
        int endZ = floor(bz);
        List<int[]> cells = new ArrayList<>();
        for (double t = 0.05; t < length && !(lastX == endX && lastZ == endZ); t += 0.05) {
            double x = ax + (bx - ax) * t / length;
            double z = az + (bz - az) * t / length;
            int cx = floor(x);
            int cz = floor(z);
            if (cx == lastX && cz == lastZ) {
                continue;
            }
            if (cx != lastX && cz != lastZ) {
                // through a corner: first the column beside both whose middle is nearer the way
                double dx = (bx - ax) / length;
                double dz = (bz - az) / length;
                double a = Math.abs(-(cx + 0.5 - ax) * dz + (lastZ + 0.5 - az) * dx);
                double b = Math.abs(-(lastX + 0.5 - ax) * dz + (cz + 0.5 - az) * dx);
                cells.add(a <= b ? new int[] {cx, lastZ} : new int[] {lastX, cz});
            }
            cells.add(new int[] {cx, cz});
            lastX = cx;
            lastZ = cz;
        }
        // without the last column (the edge of the other island) and what may come after it
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i)[0] == endX && cells.get(i)[1] == endZ) {
                cells = cells.subList(0, i);
                break;
            }
        }
        int[] flat = new int[cells.size() * 2];
        for (int i = 0; i < cells.size(); i++) {
            flat[2 * i] = cells.get(i)[0];
            flat[2 * i + 1] = cells.get(i)[1];
        }
        return flat;
    }

    /** The name of an island after "to", "from", "onto": "your base", "the middle"... */
    private static String name(Island island) {
        return island.name;
    }

    /** The gap of a jump on this line, the next of {@link #HOLES} with each situation found. */
    private int hole() {
        return HOLES[found.size() % HOLES.length];
    }

    /**
     * The column of the line the player stands on before a gap this long, the near side starting
     * at least this far along the line, the far side two columns long at least, both at the same
     * height, the gap over the void: its number, or -1.
     */
    private int beforeGap(Line line, double hole, double nearAtLeast) {
        int n = line.count();
        double length = line.length();
        // the gap in the middle of the bridge, the near side as long as it must be
        double wanted = Math.max(nearAtLeast, (length - hole) / 2);
        int at = -1;
        for (int i = 0; i < n; i++) {
            if (line.alongOf(i) <= wanted + 0.01) {
                at = i;
            }
        }
        if (at < 0 || line.alongOf(at) < nearAtLeast - 0.01) {
            return -1;
        }
        int far = firstFrom(line, line.alongOf(at) + hole);
        if (far < 0 || n - far < 2 || line.ys[far] != line.ys[at]) {
            return -1;
        }
        for (int k = at + 1; k < far; k++) {
            if (line.ground[k]) {
                return -1;
            }
        }
        return at;
    }

    /**
     * Room for a fireball jump from this start along the way, over the gap and onto the far side:
     * {@link #JUMP_ROOM} blocks of air over the feet all the way (an island over the bridge would
     * stop the body a block or two up, and the player would fall into the gap).
     */
    boolean roomToJump(double[] start, double[] way, double length) {
        int feet = (int) Math.floor(start[1]);
        for (double t = 0; t <= length + 1; t += 0.5) {
            int x = floor(start[0] + way[0] * t);
            int z = floor(start[2] + way[1] * t);
            for (int y = feet; y <= feet + JUMP_ROOM; y++) {
                if (!passable(grid.id(x, y, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The first block of the line this far along it at least; -1 for none. */
    private static int firstFrom(Line line, double along) {
        for (int i = 0; i < line.count(); i++) {
            if (line.alongOf(i) >= along - 0.01) {
                return i;
            }
        }
        return -1;
    }

    /** The bridge of the player towards another island is broken: a fireball jump over the gap. */
    void jump(Line line) {
        int hole = hole();
        int at = beforeGap(line, hole, 3);
        if (at < 0) {
            return;
        }
        int far = firstFrom(line, line.alongOf(at) + hole);
        int[] near = line.blocks(0, at + 1);
        int[] after = line.blocks(far, line.count());
        double[] start = line.standOn(at);
        if (!roomToJump(start, line.way, line.alongOf(far) - line.alongOf(at))) {
            return;
        }
        add(new MapParts.Situation(MapParts.Kind.JUMP, "Your bridge to " + name(line.to) + " is broken: fireball jump over"
                + " the gap.", "Fireball jump over the gap", start, line.to.label, (int) line.end[1], null, null,
                join(near, after), null, null, line.alongOf(far) - line.alongOf(at)), line.from, line.to);
    }

    /** An enemy rushes the player from the island behind them, their bridge broken in front: a fireball jump over the gap, at once. */
    void escape(Line line) {
        if (line.from.base) {
            // the enemy does not come from the player's own base
            return;
        }
        int hole = hole();
        int at = beforeGap(line, hole, ESCAPE_BEHIND);
        if (at < 0) {
            return;
        }
        int far = firstFrom(line, line.alongOf(at) + hole);
        int[] near = line.blocks(0, at + 1);
        int[] after = line.blocks(far, line.count());
        if (!roomToJump(line.standOn(at), line.way, line.alongOf(far) - line.alongOf(at))) {
            return;
        }
        add(new MapParts.Situation(MapParts.Kind.ESCAPE, "An enemy rushes you from " + name(line.from) + ": fireball jump over"
                + " the gap to " + name(line.to) + "!", "Escape: jump the gap!", line.standOn(at), line.to.label,
                (int) line.end[1], null, null, join(near, after), line.path(at), line.standOn(-1),
                line.alongOf(far) - line.alongOf(at)), line.from, line.to);
    }

    /** The bridge of the player ends a few blocks under an island, against its side: a fireball jump up onto it. */
    void climb(Island on, Island towards) {
        double[] edge = edge(on, towards.x, towards.z);
        if (edge == null) {
            return;
        }
        double[] out = unit(on.x, on.z, towards.x, towards.z);
        double length = Math.hypot(towards.x - on.x, towards.z - on.z);
        int top = (int) edge[1];
        for (int down : new int[] {3, 4}) {
            int feet = top - down;
            // the side of the island: a wall from the feet up to its floor, nowhere to walk in
            double face = face(on, out, length, feet, top - 1, down);
            if (face < 0) {
                continue;
            }
            int faceX = floor(on.x + out[0] * face);
            int faceZ = floor(on.z + out[1] * face);
            // the floor to land on: on the side's own column or the one behind it
            int inX = floor(on.x + out[0] * (face - 1));
            int inZ = floor(on.z + out[1] * (face - 1));
            if (!grid.standable(faceX, top, faceZ) && !grid.standable(inX, top, inZ)) {
                continue;
            }
            double[] from = {on.x + out[0] * face, on.z + out[1] * face};
            int[] cells = raster(from[0], from[1], from[0] + out[0] * (CLIMB_OUT + 1.5), from[1] + out[1] * (CLIMB_OUT + 1.5));
            if (cells.length < 2 * CLIMB_OUT) {
                continue;
            }
            boolean clear = true;
            for (int i = 0; i < 2 * CLIMB_OUT && clear; i += 2) {
                // the bridge through the void, and nothing over the first block up to over the floor: room for the jump
                clear = grid.label(labels, cells[i], cells[i + 1]) != on.label
                        && grid.empty(cells[i], cells[i + 1], feet - 4, i == 0 ? top + 2 : feet + 3);
            }
            if (!clear) {
                continue;
            }
            int[] bridge = new int[CLIMB_OUT * 3];
            for (int i = 0; i < CLIMB_OUT; i++) {
                bridge[3 * i] = cells[2 * i];
                bridge[3 * i + 1] = feet - 1;
                bridge[3 * i + 2] = cells[2 * i + 1];
            }
            String what = on.base ? "your base" : on.name;
            add(new MapParts.Situation(MapParts.Kind.CLIMB, "Your bridge ends " + down + " blocks under " + what + ": fireball jump"
                    + " up onto it.", "Fireball jump onto " + what, new double[] {cells[0] + 0.5, feet, cells[1] + 0.5,
                        yaw(new double[] {-out[0], -out[1]})}, on.label, top, null, null, bridge, null, null, 0), on, towards);
            return;
        }
    }

    /** An enemy bridges from another island to the player's: knock him into the void before he gets there. */
    void bridger(Line line) {
        if (line.from.base || line.count() < 10) {
            return;
        }
        int built = Math.max(0, line.count() - BRIDGER_LEFT);
        add(new MapParts.Situation(MapParts.Kind.BRIDGER, "An enemy bridges from " + name(line.from) + " to " + name(line.to)
                + ": knock him into the void.", "Knock the bridger off", towards(line.end, line.start), line.to.label,
                (int) line.end[1], null, null, line.blocks(0, built), line.path(line.count() - 1), line.standOn(built - 1), 0),
                line.from, line.to);
    }

    /** An enemy runs over a whole bridge to the player's island: knock him into the void (or the bridge under him). */
    void rusher(Line line) {
        if (line.from.base || line.count() < 12) {
            return;
        }
        int from = Math.max(-1, line.count() - RUSH_RUN);
        add(new MapParts.Situation(MapParts.Kind.RUSHER, "An enemy runs over the bridge from " + name(line.from) + " to "
                + name(line.to) + ": knock him into the void.", "Knock the rusher off", towards(line.end, line.start),
                line.to.label, (int) line.end[1], null, null, line.blocks(0, line.count()), line.path(line.count() - 1),
                line.standOn(from), 0), line.from, line.to);
    }

    /** An enemy bridges between two other islands, past the player's: knock him off from the side. */
    void side(Line line, Island on) {
        if (line.from.base || on == line.from || on == line.to) {
            return;
        }
        double mx = (line.start[0] + line.end[0]) / 2;
        double mz = (line.start[2] + line.end[2]) / 2;
        double[] edge = edge(on, mx, mz);
        if (edge == null) {
            return;
        }
        double along = line.along(edge[0], edge[2]);
        double aside = Math.abs(line.aside(edge[0], edge[2]));
        double length = line.length();
        if (aside < SIDE_MIN || aside > SIDE_MAX || along < length * 0.15 || along > length * 0.95) {
            return;
        }
        // the column of his bridge in front of the player
        int facing = 0;
        for (int i = 1; i < line.count(); i++) {
            if (Math.abs(line.alongOf(i) - along) < Math.abs(line.alongOf(facing) - along)) {
                facing = i;
            }
        }
        double fx = line.cells[2 * facing] + 0.5;
        double fz = line.cells[2 * facing + 1] + 0.5;
        int fy = line.ys[facing] + 1;
        if (Math.abs(edge[1] - fy) > 6) {
            return;
        }
        // far from his bridge, the player has bridged out of their island towards it
        int[] own = bridgeOut(on, edge, fx, fz);
        if (own == null) {
            return;
        }
        double sx = own.length == 0 ? edge[0] : own[own.length - 3] + 0.5;
        double sz = own.length == 0 ? edge[2] : own[own.length - 1] + 0.5;
        if (!clearSight(sx, edge[1] + 1.62, sz, fx, fy + 0.5, fz)) {
            return;
        }
        int built = 0;
        while (built < line.count() && line.alongOf(built) <= length * 0.15) {
            built++;
        }
        String what = own.length == 0 ? "An enemy bridges from " + name(line.from) + " to " + name(line.to)
                + ": knock him off from the side." : "Out on your bridge, an enemy bridges from " + name(line.from) + " to "
                + name(line.to) + ": knock him off from the side.";
        add(new MapParts.Situation(MapParts.Kind.SIDE, what, "Knock him off from the side",
                new double[] {sx, edge[1], sz, yaw(unit(sx, sz, fx, fz))}, on.label, (int) edge[1], null,
                own.length == 0 ? null : own, line.blocks(0, built), line.path(line.count() - 1), line.standOn(built - 1), 0),
                line.from, line.to, on);
    }

    /**
     * The player's bridge out of their island from its edge straight towards (x, z), over the void,
     * until they are {@link #SIDE_NEAR} blocks from it: the blocks under it (x, y, z each), none
     * when the island is near enough; null when something is in the way.
     */
    private int[] bridgeOut(Island on, double[] edge, double x, double z) {
        int out = (int) Math.floor(Math.hypot(x - edge[0], z - edge[2]) - SIDE_NEAR);
        if (out <= 0) {
            return new int[0];
        }
        double[] way = unit(edge[0], edge[2], x, z);
        int feet = (int) edge[1];
        int[] cells = raster(edge[0], edge[2], edge[0] + way[0] * (out + 3.5), edge[2] + way[1] * (out + 3.5));
        int[] own = new int[3 * out];
        int n = 0;
        for (int i = 0; i + 1 < cells.length && n < out; i += 2) {
            boolean island = grid.label(labels, cells[i], cells[i + 1]) == on.label;
            if (island && n == 0) {
                // still on the island
                continue;
            }
            if (island || !grid.empty(cells[i], cells[i + 1], feet - 3, feet + 2)) {
                return null;
            }
            own[3 * n] = cells[i];
            own[3 * n + 1] = feet - 1;
            own[3 * n + 2] = cells[i + 1];
            n++;
        }
        return n == out ? own : null;
    }

    /** Bridging to another island, an enemy on it throws fireballs at the player: hit them back. */
    void send(Line line) {
        if (line.to.base || line.count() < SEND_OUT + SEND_LEFT) {
            return;
        }
        double[] start = line.standOn(SEND_OUT - 1);
        double[] thrower = {line.end[0], line.end[1], line.end[2], yaw(new double[] {-line.way[0], -line.way[1]})};
        if (!clearSight(thrower[0], thrower[1] + 1.62, thrower[2], start[0], start[1] + 1.62, start[2])) {
            return;
        }
        add(new MapParts.Situation(MapParts.Kind.SEND, "Bridging to " + name(line.to) + ", an enemy throws fireballs at you from"
                + " it: hit them back.", "Send the fireballs back", start, line.from.label, (int) line.start[1], null, null,
                line.blocks(0, SEND_OUT), null, thrower, 0), line.from, line.to);
    }

    /** Standing at a place, facing another one: x, y, z, yaw. */
    private static double[] towards(double[] at, double[] other) {
        return new double[] {at[0], at[1], at[2], yaw(unit(at[0], at[2], other[0], other[2]))};
    }

    /** True if nothing stops a throw on the straight way from one point to another (x, y, z each). */
    boolean clearSight(double ax, double ay, double az, double bx, double by, double bz) {
        double length = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        for (double t = 0.5; t < length - 0.5; t += 0.25) {
            double k = t / length;
            if (!passable(grid.id(floor(ax + (bx - ax) * k), floor(ay + (by - ay) * k), floor(az + (bz - az) * k)))) {
                return false;
            }
        }
        return true;
    }

    private static int[] join(int[] a, int[] b) {
        int[] both = new int[a.length + b.length];
        System.arraycopy(a, 0, both, 0, a.length);
        System.arraycopy(b, 0, both, a.length, b.length);
        return both;
    }
}
