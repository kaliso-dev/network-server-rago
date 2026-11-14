package gg.rago.practice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where the pearl practice plays: a part of each of the owner's Bed Wars maps, read once from its
 * world, around the points of its bedwars.yml (a team's base, the one or two diamond islands
 * nearest to it, the middle), and the situations found on it by the shape of its islands:
 *
 * <ul>
 *   <li>{@link Kind#PEARL}: from the edge of an island, pearl onto another one (the base and its
 *       diamond islands, a diamond island and the middle, both ways);
 *   <li>{@link Kind#SAVE}: on the edge of an island, a hit throws the player into the void, and
 *       they pearl back onto it;
 *   <li>{@link Kind#CLUTCH}: on the end of a bridge out in the void, a few blocks under an island,
 *       a hit throws the player off; the pearl reaches the side of the island, a wall, and the
 *       player places blocks under themselves at once (a pearl block clutch).
 * </ul>
 *
 * The islands are the columns of blocks of the part that touch each other (seen from above), as
 * the map tool of the Bed Wars plugin finds them. Every place is checked: a start where one can
 * stand and throw, a gap a pearl crosses (6 to 45 blocks), a void where the hit throws, a wall in
 * front of a bridge. Pure (no Bukkit): the tests run it on islands of their own. Immutable once made.
 */
final class PearlMaps {

    /** What the player does in a situation. */
    enum Kind {
        PEARL, SAVE, CLUTCH
    }

    /** The shortest and the longest gap between two islands that a pearl of a situation crosses. */
    static final double GAP_MIN = 6;
    static final double GAP_MAX = 45;
    /** How much more or less high the island aimed at may be. */
    static final int HEIGHT_MAX = 12;
    /** Around the points of the map, the part copied: this far on each side, at most this wide. */
    static final int MARGIN = 22;
    static final int MAX_SPAN = 196;
    /** Under the lowest point and over the highest one, the part copied. */
    static final int BELOW = 40;
    static final int ABOVE = 28;
    /** No block goes this far under the floor of the lowest island of a part, as under the islands on Hypixel. */
    static final int LIMIT_UNDER = 16;
    /** A diamond island this far from the base at most is one of its own. */
    static final double DIAMOND_REACH = 75;

    /** The points of a map, as its bedwars.yml gives them: x, y, z each, the y of the feet. */
    static final class Anchors {
        final String team;
        final int wool;
        final double[] spawn;
        final double[] bed;
        final double[] generator;
        final List<double[]> diamonds;
        final List<double[]> emeralds;

        Anchors(String team, int wool, double[] spawn, double[] bed, double[] generator, List<double[]> diamonds,
                List<double[]> emeralds) {
            this.team = team;
            this.wool = wool;
            this.spawn = spawn;
            this.bed = bed;
            this.generator = generator;
            this.diamonds = diamonds;
            this.emeralds = emeralds;
        }

        /** The middle of the emerald generators, at the lowest of them; null without any. */
        double[] middle() {
            if (emeralds.isEmpty()) {
                return null;
            }
            double x = 0;
            double z = 0;
            double y = Double.MAX_VALUE;
            for (double[] emerald : emeralds) {
                x += emerald[0];
                z += emerald[2];
                y = Math.min(y, emerald[1]);
            }
            return new double[] {x / emeralds.size(), y, z / emeralds.size()};
        }

        /** The diamond generators of the base, the nearest first: two at most, not too far. */
        List<double[]> ownDiamonds() {
            List<double[]> near = new ArrayList<>();
            for (double[] diamond : diamonds) {
                if (flat(diamond, spawn) <= DIAMOND_REACH) {
                    near.add(diamond);
                }
            }
            near.sort((a, b) -> Double.compare(flat(a, spawn), flat(b, spawn)));
            return near.size() > 2 ? new ArrayList<>(near.subList(0, 2)) : near;
        }
    }

    /** A situation of a part: what to do, where from, and the island to end on. */
    static final class Situation {
        final Kind kind;
        /** One line for the chat. */
        final String what;
        /** A few words for the title. */
        final String title;
        /** The start, from the origin of the part: x, y (the feet), z, yaw. */
        final double[] start;
        /** The island to end on, by its number in the part. */
        final int target;
        /** The floor of that island where it is aimed at (the feet), from the origin. */
        final int targetFloor;
        /** The way the hit throws the player (x, z, of length 1); null without a hit. */
        final double[] push;
        /** The block of the bridge under the start (x, y, z from the origin) in a clutch; null otherwise. */
        final int[] platform;

        Situation(Kind kind, String what, String title, double[] start, int target, int targetFloor, double[] push,
                int[] platform) {
            this.kind = kind;
            this.what = what;
            this.title = title;
            this.start = start;
            this.target = target;
            this.targetFloor = targetFloor;
            this.push = push;
            this.platform = platform;
        }
    }

    /** A part of a map: its blocks (from the origin, which lands on the centre of a zone), its islands, its situations. */
    static final class Part {
        final String map;
        /** The colour of the base, its wool. */
        final String team;
        final int wool;
        final MapCopy copy;
        /** The islands, by column: the number of the island of column (x, z) is at labels[(x - x1) * sz + z - z1]; 0: none. */
        private final int x1;
        private final int z1;
        private final int sx;
        private final int sz;
        private final int[] labels;
        /** No block goes under this height (from the origin). */
        final int limitY;
        final List<Situation> situations;

        Part(String map, String team, int wool, MapCopy copy, int x1, int z1, int sx, int sz, int[] labels, int limitY,
                List<Situation> situations) {
            this.map = map;
            this.team = team;
            this.wool = wool;
            this.copy = copy;
            this.x1 = x1;
            this.z1 = z1;
            this.sx = sx;
            this.sz = sz;
            this.labels = labels;
            this.limitY = limitY;
            this.situations = Collections.unmodifiableList(new ArrayList<>(situations));
        }

        /** The island of this column (from the origin), 0 for none. */
        int island(int x, int z) {
            int i = x - x1;
            int k = z - z1;
            return i < 0 || k < 0 || i >= sx || k >= sz ? 0 : labels[i * sz + k];
        }

        /** True if a column of this island is at most reach columns from (x, z) (from the origin). */
        boolean near(int island, double x, double z, int reach) {
            int bx = floor(x);
            int bz = floor(z);
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (island(bx + dx, bz + dz) == island) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** The situations of this kind (all of them for null). */
        List<Situation> of(Kind kind) {
            List<Situation> found = new ArrayList<>();
            for (Situation situation : situations) {
                if (kind == null || situation.kind == kind) {
                    found.add(situation);
                }
            }
            return found;
        }
    }

    private PearlMaps() {
    }

    // --- The part of a map ----------------------------------------------------------------------

    /**
     * The box of the map to copy for this base (x1, y1, z1, x2, y2, z2) and its origin (x, y, z):
     * the base, its diamond islands and the middle, with a margin, as wide as a zone holds; the
     * origin in the middle of the box, at the floor under the spawn.
     */
    static int[][] box(Anchors anchors) {
        List<double[]> points = new ArrayList<>();
        points.add(anchors.spawn);
        points.add(anchors.bed);
        points.add(anchors.generator);
        points.addAll(anchors.ownDiamonds());
        double[] middle = anchors.middle();
        if (middle != null) {
            points.add(middle);
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (double[] p : points) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            minZ = Math.min(minZ, p[2]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
            maxZ = Math.max(maxZ, p[2]);
        }
        int[] xs = span(floor(minX) - MARGIN, floor(maxX) + MARGIN);
        int[] zs = span(floor(minZ) - MARGIN, floor(maxZ) + MARGIN);
        int y1 = Math.max(0, floor(minY) - BELOW);
        int y2 = Math.min(255, floor(maxY) + ABOVE);
        int[] origin = {(xs[0] + xs[1]) / 2, floor(anchors.spawn[1] + 0.01) - 1, (zs[0] + zs[1]) / 2};
        return new int[][] {{xs[0], y1, zs[0], xs[1], y2, zs[1]}, origin};
    }

    /** From a to b, cut evenly on both sides when it is wider than a zone holds. */
    static int[] span(int a, int b) {
        if (b - a > MAX_SPAN) {
            int cut = (b - a - MAX_SPAN + 1) / 2;
            a += cut;
            b = Math.min(b - cut, a + MAX_SPAN);
        }
        return new int[] {a, b};
    }

    /**
     * The part of the map copied with {@link #box}: its islands and its situations, the islands
     * cut by the edge of the box left out (but those of the situations). Null when no situation
     * holds on it. The anchors and the box are those of the map (not from the origin).
     */
    static Part analyse(String map, Anchors anchors, MapCopy copy, int[] box, int[] origin) {
        Grid grid = Grid.of(copy);
        if (grid == null) {
            return null;
        }
        int[] labels = grid.islands();
        Finder finder = new Finder(grid, labels, anchors, origin);
        List<Situation> situations = finder.situations();
        if (situations.isEmpty()) {
            return null;
        }
        Set<Integer> kept = new HashSet<>(finder.used);
        boolean[] cut = grid.cutByTheEdge(labels, box[0] - origin[0], box[2] - origin[2], box[3] - origin[0], box[5] - origin[2]);
        for (int label = 1; label < cut.length; label++) {
            if (!cut[label]) {
                kept.add(label);
            }
        }
        MapCopy part = copy.only((x, z) -> {
            int label = grid.label(labels, x, z);
            return label != 0 && kept.contains(label);
        });
        int[] cropped = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            cropped[i] = kept.contains(labels[i]) ? labels[i] : 0;
        }
        return new Part(map, anchors.team, anchors.wool, part, grid.x1, grid.z1, grid.sx, grid.sz, cropped,
                finder.lowestFloor - LIMIT_UNDER, situations);
    }

    // --- The blocks, in a box -------------------------------------------------------------------

    /** Blocks a player goes through (air, plants, torches, carpets, signs...). */
    private static final Set<Integer> PASSABLE = ids(0, 6, 27, 28, 30, 31, 32, 37, 38, 39, 40, 50, 51, 55, 59, 63, 66, 68, 69,
            70, 72, 75, 76, 77, 78, 83, 90, 93, 94, 104, 105, 106, 111, 115, 119, 131, 132, 141, 142, 143, 147, 148, 149, 150,
            157, 171, 175, 176, 177);
    /** Blocks not to start on: fences, walls, panes, barriers, leaves, beds, chests, ladders, liquids... */
    private static final Set<Integer> NOT_FLOOR = ids(8, 9, 10, 11, 26, 54, 130, 146, 85, 113, 188, 189, 190, 191, 192, 107,
            183, 184, 185, 186, 187, 139, 101, 102, 160, 166, 18, 161, 65, 81, 92, 120, 127, 140, 144, 64, 71, 193, 194, 195,
            196, 197, 96, 167, 117, 118, 116, 145);

    private static Set<Integer> ids(int... ids) {
        Set<Integer> set = new HashSet<>();
        for (int id : ids) {
            set.add(id);
        }
        return set;
    }

    static boolean passable(int id) {
        return PASSABLE.contains(id);
    }

    /** A block to stand on at a start. */
    static boolean floor(int id) {
        return id != 0 && !PASSABLE.contains(id) && !NOT_FLOOR.contains(id);
    }

    /** The blocks of a copy in a box around its origin, by place: ids only. */
    static final class Grid {
        final int x1;
        final int y1;
        final int z1;
        final int sx;
        final int sy;
        final int sz;
        private final short[] ids;

        private Grid(int x1, int y1, int z1, int sx, int sy, int sz) {
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.ids = new short[sx * sy * sz];
        }

        static Grid of(MapCopy copy) {
            int[] blocks = copy.blocks();
            if (blocks.length == 0) {
                return null;
            }
            int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
            int[] hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            for (int i = 0; i < blocks.length; i += 5) {
                for (int a = 0; a < 3; a++) {
                    lo[a] = Math.min(lo[a], blocks[i + a]);
                    hi[a] = Math.max(hi[a], blocks[i + a]);
                }
            }
            Grid grid = new Grid(lo[0], lo[1], lo[2], hi[0] - lo[0] + 1, hi[1] - lo[1] + 1, hi[2] - lo[2] + 1);
            for (int i = 0; i < blocks.length; i += 5) {
                grid.ids[grid.index(blocks[i], blocks[i + 1], blocks[i + 2])] = (short) blocks[i + 3];
            }
            return grid;
        }

        private int index(int x, int y, int z) {
            return ((x - x1) * sy + (y - y1)) * sz + (z - z1);
        }

        /** The id of the block there, 0 (air) out of the box. */
        int id(int x, int y, int z) {
            if (x < x1 || y < y1 || z < z1 || x >= x1 + sx || y >= y1 + sy || z >= z1 + sz) {
                return 0;
            }
            return ids[index(x, y, z)];
        }

        boolean inside(int x, int z) {
            return x >= x1 && z >= z1 && x < x1 + sx && z < z1 + sz;
        }

        /** True if a player can stand there, the feet at y: a floor under them, room for the body. */
        boolean standable(int x, int y, int z) {
            return floor(id(x, y - 1, z)) && passable(id(x, y, z)) && passable(id(x, y + 1, z));
        }

        /** The highest place to stand in this column with the feet from y1 to y2; Integer.MIN_VALUE for none. */
        int highestStand(int x, int z, int from, int to) {
            for (int y = to; y >= from; y--) {
                if (standable(x, y, z)) {
                    return y;
                }
            }
            return Integer.MIN_VALUE;
        }

        /** True if no block is in this column from y1 to y2. */
        boolean empty(int x, int z, int from, int to) {
            for (int y = from; y <= to; y++) {
                if (id(x, y, z) != 0) {
                    return false;
                }
            }
            return true;
        }

        /**
         * The islands: the columns with a block, eight-neighbours together (seen from above), each
         * group numbered from 1; labels[(x - x1) * sz + z - z1].
         */
        int[] islands() {
            boolean[] solid = new boolean[sx * sz];
            for (int i = 0; i < sx; i++) {
                for (int k = 0; k < sz; k++) {
                    for (int j = 0; j < sy; j++) {
                        if (ids[(i * sy + j) * sz + k] != 0) {
                            solid[i * sz + k] = true;
                            break;
                        }
                    }
                }
            }
            int[] labels = new int[sx * sz];
            int[] queue = new int[sx * sz];
            int next = 0;
            for (int start = 0; start < solid.length; start++) {
                if (!solid[start] || labels[start] != 0) {
                    continue;
                }
                next++;
                int head = 0;
                int tail = 0;
                queue[tail++] = start;
                labels[start] = next;
                while (head < tail) {
                    int cell = queue[head++];
                    int i = cell / sz;
                    int k = cell % sz;
                    for (int di = -1; di <= 1; di++) {
                        for (int dk = -1; dk <= 1; dk++) {
                            int ni = i + di;
                            int nk = k + dk;
                            if (ni < 0 || nk < 0 || ni >= sx || nk >= sz) {
                                continue;
                            }
                            int n = ni * sz + nk;
                            if (solid[n] && labels[n] == 0) {
                                labels[n] = next;
                                queue[tail++] = n;
                            }
                        }
                    }
                }
            }
            return labels;
        }

        int label(int[] labels, int x, int z) {
            return inside(x, z) ? labels[(x - x1) * sz + (z - z1)] : 0;
        }

        /**
         * By island number: true for those that reach the edge of the box copied (from the origin,
         * corners included): only a part of them is in the copy.
         */
        boolean[] cutByTheEdge(int[] labels, int bx1, int bz1, int bx2, int bz2) {
            int most = 0;
            for (int label : labels) {
                most = Math.max(most, label);
            }
            boolean[] cut = new boolean[most + 1];
            for (int i = 0; i < sx; i++) {
                for (int k = 0; k < sz; k++) {
                    int x = x1 + i;
                    int z = z1 + k;
                    if (x <= bx1 || z <= bz1 || x >= bx2 || z >= bz2) {
                        cut[labels[i * sz + k]] = true;
                    }
                }
            }
            cut[0] = false;
            return cut;
        }
    }

    // --- The situations -------------------------------------------------------------------------

    /** An island of the part: its number, its point (from the origin), its floor there, its name in the messages. */
    private static final class Island {
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

    /** Finds the situations of a part. */
    private static final class Finder {
        final Grid grid;
        final int[] labels;
        final Set<Integer> used = new HashSet<>();
        final List<Situation> found = new ArrayList<>();
        Island base;
        final List<Island> diamonds = new ArrayList<>();
        Island middle;
        int lowestFloor = Integer.MAX_VALUE;

        Finder(Grid grid, int[] labels, Anchors anchors, int[] origin) {
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

        List<Situation> situations() {
            if (base == null || diamonds.isEmpty()) {
                return found;
            }
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

        /** Pearl from the edge of one island onto another. */
        void pearl(Island from, Island to) {
            double[] start = edge(from, to.x, to.z);
            double[] end = edge(to, from.x, from.z);
            if (start == null || end == null) {
                return;
            }
            double gap = Math.hypot(end[0] - start[0], end[2] - start[2]);
            if (gap < GAP_MIN || gap > GAP_MAX || Math.abs(end[1] - start[1]) > HEIGHT_MAX) {
                return;
            }
            double[] way = unit(start[0], start[2], to.x, to.z);
            if (!clearAhead(start, way, 2.5)) {
                return;
            }
            String onto = to.base ? "back to your base" : "onto " + to.name;
            add(new Situation(Kind.PEARL, "Pearl " + onto + ".", "Pearl " + onto,
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
            add(new Situation(Kind.SAVE, "A hit throws you off " + what + ": pearl back onto it, blocks if you land short.",
                    "Save: back onto " + what, new double[] {bx + 0.5, feet, bz + 0.5, yaw(new double[] {-out[0], -out[1]})},
                    on.label, feet, out, null), on, towards);
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
                double face = -1;
                for (double t = 0; t < length; t += 0.25) {
                    int bx = floor(on.x + out[0] * t);
                    int bz = floor(on.z + out[1] * t);
                    if (grid.label(labels, bx, bz) == on.label) {
                        int wall = 0;
                        for (int y = feet - 2; y <= feet + 1; y++) {
                            if (!passable(grid.id(bx, y, bz))) {
                                wall++;
                            }
                        }
                        if (wall >= 2) {
                            face = t;
                        }
                    } else if (face >= 0 && t > face + 3) {
                        break;
                    }
                }
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
                    add(new Situation(Kind.CLUTCH, "Hit off your bridge under " + what + ": pearl to its side, then place"
                            + " blocks under you at once.", "Clutch: pearl to the wall of " + what,
                            new double[] {at[0], feet, at[2], yaw(new double[] {-out[0], -out[1]})}, on.label, (int) edge[1],
                            out, new int[] {bx, feet - 1, bz}), on, side);
                    return;
                }
            }
        }

        private void add(Situation situation, Island a, Island b) {
            found.add(situation);
            used.add(a.label);
            used.add(b.label);
            lowestFloor = Math.min(lowestFloor, Math.min(a.floor, b.floor));
            lowestFloor = Math.min(lowestFloor, (int) Math.floor(situation.start[1]));
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
    }

    // --- Without the maps -----------------------------------------------------------------------

    /** The name of the plugin's own islands, in the messages. */
    static final String PLAIN = "Practice islands";

    /**
     * The plugin's own islands, when the server has none of the maps: a base, a diamond island 33
     * blocks in front of it and a middle further on, six blocks thick (their sides are walls).
     */
    static Part plain() {
        MapCopy blocks = boxes(Arrays.asList(
                new int[] {-10, -5, -30, 10, 0, -12, 5},
                new int[] {-7, -4, 22, 7, 1, 36, 24},
                new int[] {26, -6, 52, 46, 0, 72, 98}));
        Anchors anchors = new Anchors("red", 14, new double[] {0.5, 1, -21.5}, new double[] {0, 1, -27},
                new double[] {0.5, 1, -28.5}, Collections.singletonList(new double[] {0.5, 2, 29.5}),
                Collections.singletonList(new double[] {36.5, 1, 62.5}));
        return analyse(PLAIN, anchors, blocks, new int[] {-99, -40, -99, 99, 40, 99}, new int[] {0, 0, 0});
    }

    /** Full boxes of stone (x1, y1, z1, x2, y2, z2, the id of their top layer), one block each place. */
    static MapCopy boxes(List<int[]> boxes) {
        int count = 0;
        for (int[] b : boxes) {
            count += (b[3] - b[0] + 1) * (b[4] - b[1] + 1) * (b[5] - b[2] + 1);
        }
        int[] blocks = new int[count * 5];
        int n = 0;
        for (int[] b : boxes) {
            for (int x = b[0]; x <= b[3]; x++) {
                for (int y = b[1]; y <= b[4]; y++) {
                    for (int z = b[2]; z <= b[5]; z++) {
                        blocks[n++] = x;
                        blocks[n++] = y;
                        blocks[n++] = z;
                        blocks[n++] = y == b[4] ? b[6] : 1;
                        blocks[n++] = 0;
                    }
                }
            }
        }
        return MapCopy.of(blocks);
    }

    // --- Little helpers -------------------------------------------------------------------------

    static int floor(double value) {
        return (int) Math.floor(value);
    }

    /** The distance across (x and z) between two points. */
    static double flat(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[2] - b[2]);
    }

    /** The way from (ax, az) to (bx, bz), of length 1: x, z. */
    static double[] unit(double ax, double az, double bx, double bz) {
        double length = Math.hypot(bx - ax, bz - az);
        return length == 0 ? new double[] {0, 1} : new double[] {(bx - ax) / length, (bz - az) / length};
    }

    /** The yaw of Minecraft looking along this way (x, z). */
    static float yaw(double[] way) {
        return (float) Math.toDegrees(Math.atan2(-way[0], way[1]));
    }

    /** For the logs: the situations of a part, one per line. */
    static List<String> describe(Part part) {
        List<String> lines = new ArrayList<>();
        for (Situation situation : part.situations) {
            lines.add(situation.kind + " " + situation.title + " from " + Arrays.toString(situation.start));
        }
        return lines;
    }
}
