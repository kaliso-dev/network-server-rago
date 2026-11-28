package gg.rago.lobby;

import java.util.Locale;
import java.util.UUID;

/**
 * The rules of the Owner's test tools (plan 7.1, outils-owner: of config.yml), without Bukkit: who
 * the Owner is, the reach the tool allows, where a hit lands, where the scaffold puts a block.
 */
final class OwnerRules {

    /** The reach of a fair player: the tool hits further than this only. */
    static final double FAIR_REACH = 3.0;
    /** The farthest a 1.8 server takes a hit from (6 blocks, 36 squared): no tool reach beyond. */
    static final double MAX_REACH = 6.0;
    /** The hit box of a player, as the server checks hits: 0.6 wide and 1.8 high, and 0.1 more on each side. */
    static final double HALF_WIDTH = 0.4;
    static final double BOX_BELOW = 0.1;
    static final double BOX_HEIGHT = 1.9;

    private OwnerRules() {
    }

    /**
     * True if this player is the Owner: their name is the account of outils-owner (compte), and
     * the server knows that name is theirs (online mode, or BungeeCord's), or their id is the one
     * outils-owner.uuid gives (a server whose names are not verified).
     */
    static boolean isOwner(String account, UUID configured, String name, UUID id, boolean namesVerified) {
        if (account == null || account.isEmpty() || name == null || !name.equalsIgnoreCase(account)) {
            return false;
        }
        return configured != null ? configured.equals(id) : namesVerified;
    }

    /** The reach of "/owner reach <blocks>": from 3 to 6, or NaN when it is not one. */
    static double reach(String text) {
        double value;
        try {
            value = Double.parseDouble(text.replace(',', '.').trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
        return value >= FAIR_REACH && value <= MAX_REACH ? value : Double.NaN;
    }

    /** "4.5" for 4.5, "4" for 4.0. */
    static String blocks(double reach) {
        return reach == Math.rint(reach) ? String.valueOf((long) reach) : String.format(Locale.ROOT, "%.1f", reach);
    }

    /**
     * How far along the ray from (ox, oy, oz), its direction (dx, dy, dz) of length 1, it enters
     * the hit box of a player standing at (px, py, pz); -1 if it misses. 0 from inside the box.
     */
    static double hit(double ox, double oy, double oz, double dx, double dy, double dz, double px, double py, double pz) {
        return box(ox, oy, oz, dx, dy, dz, px - HALF_WIDTH, py - BOX_BELOW, pz - HALF_WIDTH,
                px + HALF_WIDTH, py + BOX_HEIGHT, pz + HALF_WIDTH);
    }

    /** The same for the block at (x, y, z): how far along the ray it enters it, or -1. */
    static double block(double ox, double oy, double oz, double dx, double dy, double dz, int x, int y, int z) {
        return box(ox, oy, oz, dx, dy, dz, x, y, z, x + 1, y + 1, z + 1);
    }

    /** How far along the ray it enters the box from low to high, or -1 if it misses. */
    static double box(double ox, double oy, double oz, double dx, double dy, double dz, double lowX, double lowY,
            double lowZ, double highX, double highY, double highZ) {
        double[] low = {lowX, lowY, lowZ};
        double[] high = {highX, highY, highZ};
        double[] from = {ox, oy, oz};
        double[] way = {dx, dy, dz};
        double enter = 0;
        double leave = Double.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(way[axis]) < 1e-12) {
                if (from[axis] < low[axis] || from[axis] > high[axis]) {
                    return -1;
                }
                continue;
            }
            double a = (low[axis] - from[axis]) / way[axis];
            double b = (high[axis] - from[axis]) / way[axis];
            enter = Math.max(enter, Math.min(a, b));
            leave = Math.min(leave, Math.max(a, b));
            if (enter > leave) {
                return -1;
            }
        }
        return enter;
    }

    /** The blocks around the one under the feet: true where one is solid. */
    interface Solid {
        boolean at(int x, int y, int z);
    }

    /**
     * Where the scaffold puts a block for feet at (x, y, z): the block under them, if it is empty
     * and touches a solid block to place it against, as a player places one (beside it, on a
     * side, or under it). Returns {x, y, z, ax, ay, az} (the block, then the one it is placed
     * against), or null.
     */
    static int[] scaffold(double x, double y, double z, Solid solid) {
        int bx = (int) Math.floor(x);
        // the highest block wholly under the feet (falling at y 64.9: the block 63, not 64)
        int by = (int) Math.floor(y) - 1;
        int bz = (int) Math.floor(z);
        if (solid.at(bx, by, bz)) {
            return null;
        }
        int[][] sides = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
        for (int[] side : sides) {
            int ax = bx + side[0];
            int ay = by + side[1];
            int az = bz + side[2];
            if (solid.at(ax, ay, az)) {
                return new int[] {bx, by, bz, ax, ay, az};
            }
        }
        return null;
    }
}
