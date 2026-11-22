package gg.rago.practice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The targets of Block aim, without Bukkit: the scene (an island with its bed, a bridge out of
 * it), its faces that light up one at a time (under the feet at the end of the bridge, on the side
 * of the bridge, on the bed to cover), the next one, the time each level gives, a streak summed up.
 *
 * <p>The scene, the faces, the times of the levels and how far a target may be from the one
 * before are starting values of the proposal of step 7, to confirm with the owner.
 */
final class BlockAim {

    /** The three places a face lights up. */
    static final int FEET = 0;
    static final int SIDE = 1;
    static final int BED = 2;
    static final String[] PLACES = {"under your feet", "on the side of the bridge", "on the bed"};
    /** The same, as the sidebar writes them. */
    static final String[] SHORT = {"Under your feet", "Bridge side", "Bed (shift)"};

    /** The island, at y 0 from the centre of the zone (players stand at y 1): x -4 to -1, z -1 to 1. */
    static final int ISLAND_MIN_X = -4;
    static final int ISLAND_MAX_X = -1;
    static final int ISLAND_HALF = 1;
    /** The bridge, out of the island: x 0 to BRIDGE_LENGTH - 1, at z 0 and y 0. */
    static final int BRIDGE_LENGTH = 3;
    /** The bed on the island, along x, in the middle row: its foot at (-3, 1, 0), its head at (-2, 1, 0). */
    static final int BED_FOOT_X = -3;
    static final int BED_HEAD_X = -2;
    static final int BED_Y = 1;
    /** A bed is 9/16 of a block high. */
    static final double BED_HEIGHT = 0.5625;

    /** The time a target waits at each level, in milliseconds (to confirm). */
    static final long[] LEVEL_MS = {3000L, 2500L, 2000L, 1500L, 1000L};
    /** The next target is at most this far from the one before, between their cells (blocks; to confirm). */
    static final double MAX_STEP = 4.5;
    /** The particles of a face lie this far out of it. */
    static final double OUT = 0.05;

    static final List<Target> TARGETS = Collections.unmodifiableList(targets());

    private BlockAim() {
    }

    /** A face of the scene: a block put against it goes to its cell. */
    static final class Target {
        final int place;
        /** The block whose face lights up. */
        final int x;
        final int y;
        final int z;
        /** The face, a unit step out of the block: the cell of the block to put is the block plus this step. */
        final int dx;
        final int dy;
        final int dz;

        Target(int place, int x, int y, int z, int dx, int dy, int dz) {
            this.place = place;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        int cellX() {
            return x + dx;
        }

        int cellY() {
            return y + dy;
        }

        int cellZ() {
            return z + dz;
        }

        /** True if a block put at this cell (from the centre of the zone) is on the target. */
        boolean hitBy(int bx, int by, int bz) {
            return bx == cellX() && by == cellY() && bz == cellZ();
        }

        /** How high the block of the face is: a bed is lower than a block. */
        double height() {
            return place == BED ? BED_HEIGHT : 1;
        }

        /**
         * The points of the face where its particles go, {x, y, z} from the centre of the zone: three
         * by three over it, a little out of it.
         */
        List<double[]> points() {
            List<double[]> points = new ArrayList<>();
            double[] steps = {0.2, 0.5, 0.8};
            for (double a : steps) {
                for (double b : steps) {
                    double px;
                    double py;
                    double pz;
                    if (dy != 0) {
                        px = x + a;
                        pz = z + b;
                        py = y + (dy > 0 ? height() + OUT : -OUT);
                    } else {
                        py = y + b * height();
                        if (dx != 0) {
                            px = x + (dx > 0 ? 1 + OUT : -OUT);
                            pz = z + a;
                        } else {
                            pz = z + (dz > 0 ? 1 + OUT : -OUT);
                            px = x + a;
                        }
                    }
                    points.add(new double[] {px, py, pz});
                }
            }
            return points;
        }

        /** How far the cells of two targets are from each other (blocks). */
        double distanceTo(Target other) {
            int ox = cellX() - other.cellX();
            int oy = cellY() - other.cellY();
            int oz = cellZ() - other.cellZ();
            return Math.sqrt(ox * ox + oy * oy + oz * oz);
        }
    }

    /**
     * Every face that lights up: the end of the bridge (under the feet of a player at its edge),
     * both sides of each block of the bridge, and the bed: its top, its two sides, its two ends.
     */
    private static List<Target> targets() {
        List<Target> targets = new ArrayList<>();
        targets.add(new Target(FEET, BRIDGE_LENGTH - 1, 0, 0, 1, 0, 0));
        for (int x = 0; x < BRIDGE_LENGTH; x++) {
            targets.add(new Target(SIDE, x, 0, 0, 0, 0, -1));
            targets.add(new Target(SIDE, x, 0, 0, 0, 0, 1));
        }
        for (int x : new int[] {BED_FOOT_X, BED_HEAD_X}) {
            targets.add(new Target(BED, x, BED_Y, 0, 0, 1, 0));
            targets.add(new Target(BED, x, BED_Y, 0, 0, 0, -1));
            targets.add(new Target(BED, x, BED_Y, 0, 0, 0, 1));
        }
        targets.add(new Target(BED, BED_FOOT_X, BED_Y, 0, -1, 0, 0));
        targets.add(new Target(BED, BED_HEAD_X, BED_Y, 0, 1, 0, 0));
        return targets;
    }

    /** True if this cell (from the centre) is a block of the scene, the bed included. */
    static boolean solid(int x, int y, int z) {
        if (y == 0) {
            boolean island = x >= ISLAND_MIN_X && x <= ISLAND_MAX_X && Math.abs(z) <= ISLAND_HALF;
            boolean bridge = x >= 0 && x < BRIDGE_LENGTH && z == 0;
            return island || bridge;
        }
        return y == BED_Y && z == 0 && (x == BED_FOOT_X || x == BED_HEAD_X);
    }

    /** The target the player starts with, untimed, to begin when they are ready. */
    static Target first(Random random) {
        return next(random, null);
    }

    /**
     * The next target: another one, at most {@link #MAX_STEP} from the one before; one of the
     * three places drawn first (among those the near targets offer), then a face of it.
     */
    static Target next(Random random, Target before) {
        List<Target> near = new ArrayList<>();
        for (Target target : TARGETS) {
            if (target != before && (before == null || target.distanceTo(before) <= MAX_STEP)) {
                near.add(target);
            }
        }
        List<Integer> places = new ArrayList<>();
        for (Target target : near) {
            if (!places.contains(target.place)) {
                places.add(target.place);
            }
        }
        int place = places.get(random.nextInt(places.size()));
        List<Target> of = new ArrayList<>();
        for (Target target : near) {
            if (target.place == place) {
                of.add(target);
            }
        }
        return of.get(random.nextInt(of.size()));
    }

    /** A streak: the targets put in time, their times, the blocks put elsewhere. */
    static final class Streak {
        int hits;
        long totalMs;
        int elsewhere;

        void hit(long millis) {
            hits++;
            totalMs += Math.max(0, millis);
        }

        void elsewhere() {
            elsewhere++;
        }

        /** The average time to a target, in milliseconds; 0 before the first. */
        long average() {
            return hits == 0 ? 0 : totalMs / hits;
        }

        /** "12 targets, 0.84 s a target, 2 blocks elsewhere". */
        String summary() {
            return hits + (hits == 1 ? " target" : " targets") + (hits == 0 ? "" : ", " + seconds(average()) + " s a target")
                    + ", " + elsewhere + (elsewhere == 1 ? " block" : " blocks") + " elsewhere";
        }
    }

    /** Milliseconds as seconds with two decimals: "0.84". */
    static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.2f", millis / 1000.0);
    }

    /** The seconds of a level, as its label says them: "3", "2.5". */
    static String levelSeconds(int level) {
        long millis = LEVEL_MS[level];
        return millis % 1000 == 0 ? String.valueOf(millis / 1000) : String.format(Locale.ROOT, "%.1f", millis / 1000.0);
    }
}
