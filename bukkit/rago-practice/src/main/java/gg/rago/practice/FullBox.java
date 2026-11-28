package gg.rago.practice;

/**
 * The anti full box of Block Placement (the owner's choice of 22/11, "Interdire de s'enfermer"): a
 * block is refused when it would close a player in completely, the four sides of their feet and of
 * their head and the block above their head all solid with it. The block under their feet does not
 * count: a player is boxed in on any floor.
 */
final class FullBox {

    /** Whether the block at (x, y, z) stops a player: solid. */
    interface Solid {
        boolean at(int x, int y, int z);
    }

    /**
     * The cells of the box of a player who stands in the block (x, y, z) of their feet: the four
     * sides of the feet, the four sides of the head (y + 1), and above the head (y + 2).
     */
    static final int[][] AROUND = {
        {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
        {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
        {0, 2, 0},
    };

    private FullBox() {
    }

    /**
     * True if the block placed at (bx, by, bz) is one of the box of the player whose feet are in the
     * block (x, y, z) and every other cell of that box is solid: with it, the player is closed in.
     */
    static boolean closes(int x, int y, int z, int bx, int by, int bz, Solid solid) {
        boolean ofTheBox = false;
        for (int[] cell : AROUND) {
            int cx = x + cell[0];
            int cy = y + cell[1];
            int cz = z + cell[2];
            if (cx == bx && cy == by && cz == bz) {
                ofTheBox = true;
            } else if (!solid.at(cx, cy, cz)) {
                return false;
            }
        }
        return ofTheBox;
    }
}
