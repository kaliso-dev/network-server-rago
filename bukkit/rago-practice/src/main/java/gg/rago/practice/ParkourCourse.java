package gg.rago.practice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The course of the Parkour Duel (FUN PRACTICE, the owner's choice of 22/11): one lane per
 * player, the same for both, side by side along x. Offsets from the centre of the arena; y 0 is its
 * floor; z from the middle of the lane. Five themed sections from the start to the finish, a
 * checkpoint between two of them: quartz pillars, a stair of glass, a zigzag of prismarine with a
 * jump of three blocks at its end, a run of sea lanterns down and up again, a line of gold up to
 * the finish. No Bukkit here, so that the tests can walk it.
 */
final class ParkourCourse {

    /** The data of a block in the colour of the lane (red or blue stained clay). */
    static final int LANE_COLOUR = -1;
    /** How far each lane is from the middle of the arena, across (z): -5 for red, +5 for blue. */
    static final int LANE_Z = 5;

    /** A block of the course: where it is, its material (a Bukkit Material name) and its data. */
    static final class Piece {
        final int x;
        final int y;
        final int z;
        final String material;
        final int data;

        Piece(int x, int y, int z, String material, int data) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
            this.data = data;
        }
    }

    /** A flat place to stand on: the blocks from (x1, z1) to (x2, z2) at height y. */
    static final class Pad {
        final int x1;
        final int x2;
        final int z1;
        final int z2;
        final int y;

        Pad(int x1, int x2, int z1, int z2, int y) {
            this.x1 = Math.min(x1, x2);
            this.x2 = Math.max(x1, x2);
            this.z1 = Math.min(z1, z2);
            this.z2 = Math.max(z1, z2);
            this.y = y;
        }

        /** True if feet at (x, y, z), from the middle of the lane, stand on it (or on a plate on it). */
        boolean holds(double x, double y, double z) {
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            return bx >= x1 && bx <= x2 && bz >= z1 && bz <= z2 && y >= this.y + 1 - 1e-6 && y < this.y + 1.6;
        }

        /** The middle of its top, where a player is put back: x, y (the feet), z. */
        double[] middle() {
            return new double[] {(x1 + x2 + 1) / 2.0, y + 1, (z1 + z2 + 1) / 2.0};
        }
    }

    /** The one course of the mode. */
    static final ParkourCourse COURSE = new ParkourCourse();

    private final List<Piece> pieces = new ArrayList<>();
    /** The blocks to stand on one after the other, from the start to the finish: {x, y, z}. */
    private final List<int[]> steps = new ArrayList<>();
    /** The start (0), then the checkpoints in order. */
    private final List<Pad> checkpoints = new ArrayList<>();
    private Pad finish;

    private ParkourCourse() {
        // the start: quartz with a border of the lane's colour, under a gate of quartz
        Pad start = pad(-50, -46, -2, 2, 0, "QUARTZ_BLOCK", 0);
        checkpoints.add(start);
        steps.add(new int[] {-48, 0, 0});
        gate(-46, 0, 2, "QUARTZ_BLOCK", 2, "QUARTZ_BLOCK", 1);

        // 1. quartz pillars, flat
        jumps("QUARTZ_BLOCK", 2, new int[][] {{-43, 0, 0}, {-40, 0, 0}, {-38, 0, 0}, {-35, 0, 0}});
        checkpoint(-32, -30, 0);

        // 2. a stair of light blue glass, up
        jumps("STAINED_GLASS", 3, new int[][] {{-28, 1, 0}, {-26, 2, 0}, {-23, 3, 0}, {-20, 3, 0}, {-17, 4, 0}});
        checkpoint(-15, -13, 4);

        // 3. a zigzag of prismarine, then a run-up of dark prismarine and a jump of three blocks
        jumps("PRISMARINE", 0, new int[][] {{-10, 4, 1}, {-7, 4, -1}, {-4, 4, 1}, {-1, 4, -1}});
        for (int x = 2; x <= 4; x++) {
            pieces.add(new Piece(x, 4, 0, "PRISMARINE", 2));
        }
        steps.add(new int[] {2, 4, 0});
        steps.add(new int[] {4, 4, 0});
        jumps("PRISMARINE", 1, new int[][] {{8, 4, 0}});
        checkpoint(10, 12, 4);

        // 4. sea lanterns down, then up again
        jumps("SEA_LANTERN", 0, new int[][] {{15, 3, 0}, {18, 2, 0}, {22, 1, 0}, {24, 2, 0}, {26, 3, 0}, {29, 3, 0}});
        checkpoint(31, 33, 3);

        // 5. a line of gold, up to the finish
        jumps("GOLD_BLOCK", 0, new int[][] {{36, 4, 0}, {39, 4, 0}, {41, 5, 0}, {44, 5, 0}});
        finish = pad(46, 50, -2, 2, 5, "GOLD_BLOCK", 0);
        replace(new Piece(48, 5, 0, "DIAMOND_BLOCK", 0));
        steps.add(new int[] {48, 5, 0});
        gate(46, 5, 2, "GOLD_BLOCK", 0, "GLOWSTONE", 0);
    }

    /** A pad of this block with a border of the lane's colour; its blocks are pieces. */
    private Pad pad(int x1, int x2, int z1, int z2, int y, String material, int data) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean border = x == x1 || x == x2 || z == z1 || z == z2;
                pieces.add(border ? new Piece(x, y, z, "STAINED_CLAY", LANE_COLOUR) : new Piece(x, y, z, material, data));
            }
        }
        return new Pad(x1, x2, z1, z2, y);
    }

    /** A checkpoint: three by three of the lane's colour, a sea lantern under a gold plate in the middle. */
    private void checkpoint(int x1, int x2, int y) {
        Pad pad = pad(x1, x2, -1, 1, y, "SEA_LANTERN", 0);
        int mx = (x1 + x2) / 2;
        pieces.add(new Piece(mx, y + 1, 0, "GOLD_PLATE", 0));
        checkpoints.add(pad);
        steps.add(new int[] {mx, y, 0});
    }

    /** This block in place of the one there. */
    private void replace(Piece piece) {
        for (int i = 0; i < pieces.size(); i++) {
            Piece there = pieces.get(i);
            if (there.x == piece.x && there.y == piece.y && there.z == piece.z) {
                pieces.set(i, piece);
                return;
            }
        }
        pieces.add(piece);
    }

    /** Blocks to jump from one to the next, each a step. */
    private void jumps(String material, int data, int[][] blocks) {
        for (int[] block : blocks) {
            pieces.add(new Piece(block[0], block[1], block[2], material, data));
            steps.add(new int[] {block[0], block[1], block[2]});
        }
    }

    /** An arch over the pad at x: two pillars at z -half and +half, four high, a beam one higher. */
    private void gate(int x, int y, int half, String pillar, int pillarData, String beam, int beamData) {
        for (int dy = 1; dy <= 4; dy++) {
            pieces.add(new Piece(x, y + dy, -half, pillar, pillarData));
            pieces.add(new Piece(x, y + dy, half, pillar, pillarData));
        }
        for (int z = -half; z <= half; z++) {
            pieces.add(new Piece(x, y + 5, z, beam, beamData));
        }
    }

    /** Every block of one lane, from the middle of the lane. */
    List<Piece> pieces() {
        return Collections.unmodifiableList(pieces);
    }

    /** The blocks to stand on, in order: {x, y, z}. */
    List<int[]> steps() {
        return Collections.unmodifiableList(steps);
    }

    /** The start (0) and the checkpoints after it, in order. */
    List<Pad> checkpoints() {
        return Collections.unmodifiableList(checkpoints);
    }

    /** The checkpoints after the start. */
    int checkpointCount() {
        return checkpoints.size() - 1;
    }

    Pad finish() {
        return finish;
    }

    /** Where a player starts: x, y (the feet), z, from the middle of the lane; facing +x. */
    double[] start() {
        return new double[] {-47.5, 1, 0.5};
    }

    /** The lowest block of the course. */
    int lowest() {
        int lowest = Integer.MAX_VALUE;
        for (Piece piece : pieces) {
            lowest = Math.min(lowest, piece.y);
        }
        return lowest;
    }

    /** The highest block of the course. */
    int highest() {
        int highest = Integer.MIN_VALUE;
        for (Piece piece : pieces) {
            highest = Math.max(highest, piece.y);
        }
        return highest;
    }

    /** How far the course goes along x from the middle, either way. */
    int reach() {
        int reach = 0;
        for (Piece piece : pieces) {
            reach = Math.max(reach, Math.abs(piece.x));
        }
        return reach;
    }

    /** The z of the middle of a lane: -5 for side 0 (red), +5 for side 1 (blue). */
    static int laneZ(int side) {
        return side == 0 ? -LANE_Z : LANE_Z;
    }
}
