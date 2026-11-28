package gg.rago.practice;

/**
 * The techniques of Block Pattern Practice (the owner's ask of 27/11): the first ten of his video
 * Block Method (#1 to #10), simplest first. Each is the blocks to place while RagoBot runs at the
 * player, and the way it comes. Read from the video, to set with the owner in game. Bukkit-free.
 *
 * <p>The places are in the frame of the player: they stand on (0, 0, 0), the block of their feet,
 * x to their right, y up, z forward, towards RagoBot.
 */
final class BlockPatterns {

    private BlockPatterns() {
    }

    /** One technique: one level of the training. Immutable. */
    static final class Technique {
        /** The id of its level and of its board of records ("t1"). */
        final String id;
        /** Its name in the game ("#1 Block in front"). */
        final String name;
        /** What to do, in one sentence. */
        final String help;
        /** The blocks to place, x, y, z each, in an order a player can place them. */
        final int[][] blocks;
        /** Boxes of blocks there before the start, x1, y1, z1, x2, y2, z2 each (a tunnel): never broken. */
        final int[][] decor;
        /** The points RagoBot runs through, x, z each, from its start (the first); then at the player. */
        final double[][] path;
        /** Ticks it waits at its start before it runs: the time to place the blocks. */
        final int wait;
        /** True if it breaks the blocks of the player in its way (shears on wool). */
        final boolean breaks;
        /** The side it walks round a wall it can neither jump nor break: 1 the player's right, -1 their left. */
        final int side;

        Technique(String id, String name, String help, int[][] blocks, int[][] decor, double[][] path, int wait,
                boolean breaks, int side) {
            this.id = id;
            this.name = name;
            this.help = help;
            this.blocks = blocks;
            this.decor = decor;
            this.path = path;
            this.wait = wait;
            this.breaks = breaks;
            this.side = side;
        }
    }

    private static final int[][] NONE = new int[0][];

    /** The ten techniques, #1 to #10 of the video. */
    static final Technique[] TECHNIQUES = {
        new Technique("t1", "#1 Block in front",
                "place a block in front of you: RagoBot has to jump it. Hit it first.",
                new int[][] {{0, 0, 2}}, NONE, new double[][] {{0, 12}}, 20, false, 1),
        new Technique("t2", "#2 Pillar",
                "a pillar of two blocks in its way: it cannot jump it and goes round. Hit it as it comes out.",
                new int[][] {{0, 0, 2}, {0, 1, 2}}, NONE, new double[][] {{0, 12}}, 25, false, 1),
        new Technique("t3", "#3 Hook",
                "a pillar with an arm at head height: under the arm its eyes are blocked, its legs are not. Hit it there.",
                new int[][] {{0, 0, 2}, {0, 1, 2}, {-1, 1, 2}}, NONE, new double[][] {{-1, 12}}, 30, false, -1),
        new Technique("t4", "#4 T",
                "a pillar with an arm on each side: wherever it comes, hit it under an arm.",
                new int[][] {{0, 0, 2}, {0, 1, 2}, {-1, 1, 2}, {1, 1, 2}}, NONE, new double[][] {{1, 12}}, 35, false, 1),
        new Technique("t5", "#5 Arch",
                "an arch in front of you: RagoBot has to come through it and cannot jump in it. Hit it in the arch.",
                new int[][] {{-1, 0, 2}, {-1, 1, 2}, {-1, 2, 2}, {0, 2, 2}, {1, 2, 2}, {1, 1, 2}, {1, 0, 2}}, NONE,
                new double[][] {{0, 12}}, 50, false, 1),
        new Technique("t6", "#6 Roof",
                "a block in front, then a roof over the place it jumps from: it can neither jump nor come. Hit it.",
                new int[][] {{0, 0, 2}, {1, 0, 3}, {1, 1, 3}, {1, 2, 3}, {0, 2, 3}}, NONE, new double[][] {{0, 12}}, 45,
                false, -1),
        new Technique("t7", "#7 Door",
                "a wall with a door: RagoBot runs into the wall and has to find the door. Wait for it there.",
                new int[][] {{-2, 0, 2}, {-2, 1, 2}, {-1, 0, 2}, {-1, 1, 2}, {-1, 2, 2}, {0, 2, 2}, {1, 2, 2}, {1, 1, 2},
                    {1, 0, 2}}, NONE, new double[][] {{-2, 12}, {-2, 4}}, 60, false, 1),
        new Technique("t8", "#8 Far hook",
                "a hook three blocks out: RagoBot runs into the pillar, then into the arm. Hit it while it goes round.",
                new int[][] {{0, 0, 3}, {0, 1, 3}, {1, 1, 3}}, NONE, new double[][] {{0, 12}}, 30, false, 1),
        new Technique("t9", "#9 Window",
                "a wall of three by three with a window at head height: hit RagoBot through it before it hits you.",
                new int[][] {{-1, 0, 2}, {-1, 1, 2}, {-1, 2, 2}, {0, 0, 2}, {0, 2, 2}, {1, 0, 2}, {1, 1, 2}, {1, 2, 2}},
                NONE, new double[][] {{0, 12}}, 60, false, 1),
        new Technique("t10", "#10 Tunnel",
                "in a tunnel: close it with two blocks. RagoBot breaks through: hit it first in the hole.",
                new int[][] {{0, 0, 3}, {0, 1, 3}}, new int[][] {{-1, 0, -1, -1, 1, 13}, {1, 0, -1, 1, 1, 13},
                    {-1, 2, -1, 1, 2, 13}}, new double[][] {{0, 12}}, 25, true, 1),
    };

    /** The technique of this id, or null. */
    static Technique of(String id) {
        for (Technique technique : TECHNIQUES) {
            if (technique.id.equals(id)) {
                return technique;
            }
        }
        return null;
    }

    /** True if (x, y, z) is in one of these boxes. */
    static boolean inBoxes(int[][] boxes, int x, int y, int z) {
        for (int[] box : boxes) {
            if (x >= Math.min(box[0], box[3]) && x <= Math.max(box[0], box[3]) && y >= Math.min(box[1], box[4])
                    && y <= Math.max(box[1], box[4]) && z >= Math.min(box[2], box[5]) && z <= Math.max(box[2], box[5])) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if each block of the technique can be placed in its order: on the floor (y 0) or against
     * a block placed before it, or against its decor.
     */
    static boolean placeable(Technique technique) {
        for (int i = 0; i < technique.blocks.length; i++) {
            int[] b = technique.blocks[i];
            if (b[1] < 0 || inBoxes(technique.decor, b[0], b[1], b[2])) {
                return false;
            }
            boolean held = b[1] == 0;
            for (int j = 0; j < i && !held; j++) {
                int[] o = technique.blocks[j];
                held = Math.abs(o[0] - b[0]) + Math.abs(o[1] - b[1]) + Math.abs(o[2] - b[2]) == 1;
            }
            for (int[] side : SIDES) {
                held |= inBoxes(technique.decor, b[0] + side[0], b[1] + side[1], b[2] + side[2]);
            }
            if (!held) {
                return false;
            }
        }
        return true;
    }

    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
}
