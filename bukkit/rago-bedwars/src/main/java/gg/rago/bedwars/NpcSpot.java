package gg.rago.bedwars;

/**
 * Where a shop or upgrades villager really stands. Hypixel's maps often guard the front of an NPC's
 * niche with a slab and a line of barriers above it (Sky Rise, Extinction, Gelato...): a point found
 * under those barriers is one block in front of the niche, too low for a villager, whose head would
 * be in the barriers. Then the villager goes into the niche behind, walled in on its sides, facing
 * out of it. A point with room for a villager stays as it is. No Bukkit here: the blocks come
 * through {@link Blocks}.
 */
final class NpcSpot {

    /** How tall a villager is. */
    static final double HEIGHT = 1.95;

    /** The blocks of the world, as the villager sees them. */
    interface Blocks {
        /** True for a block one cannot stand in (barriers included). */
        boolean solid(int x, int y, int z);

        /** The height of the top of this block above its own y: 1 for a full block, 0.5 for a bottom slab, 0 for air. */
        double height(int x, int y, int z);
    }

    final double x;
    final double y;
    final double z;
    final float yaw;
    final boolean moved;

    private NpcSpot(double x, double y, double z, float yaw, boolean moved) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.moved = moved;
    }

    /**
     * The spot of a villager whose point is (x, y, z) facing yaw, on an island whose spawn is at
     * (spawnX, spawnZ). The yaw is turned to the nearest quarter, as Hypixel's NPCs stand.
     */
    static NpcSpot fit(Blocks blocks, double x, double y, double z, float yaw, double spawnX, double spawnZ) {
        float quarter = quarter(yaw);
        if (fits(blocks, floor(x), y, floor(z))) {
            return new NpcSpot(x, y, z, quarter, false);
        }
        int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] best = null;
        double bestTop = 0;
        int bestWalls = -1;
        double bestDistance = -1;
        for (int[] way : ways) {
            int bx = floor(x) + way[0];
            int bz = floor(z) + way[1];
            double top = floorTop(blocks, bx, y, bz);
            if (Double.isNaN(top) || Math.abs(top - y) > 1.0 || !fits(blocks, bx, top, bz)) {
                continue;
            }
            int body = (int) Math.floor(top + 0.01);
            if (top - body > 0.01) {
                body++;
            }
            // a niche: a wall at its back, and walls on both sides within two blocks
            if (!blocks.solid(bx + way[0], body, bz + way[1])) {
                continue;
            }
            int walls = 0;
            for (int sign = -1; sign <= 1; sign += 2) {
                for (int d = 1; d <= 2; d++) {
                    if (blocks.solid(bx + sign * way[1] * d, body, bz + sign * way[0] * d)) {
                        walls++;
                        break;
                    }
                }
            }
            double dx = x + way[0] - spawnX;
            double dz = z + way[1] - spawnZ;
            double distance = dx * dx + dz * dz;
            if (walls > bestWalls || (walls == bestWalls && distance > bestDistance)) {
                best = way;
                bestTop = top;
                bestWalls = walls;
                bestDistance = distance;
            }
        }
        if (best == null || bestWalls < 2) {
            // no niche behind: leave it where the map put it
            return new NpcSpot(x, y, z, quarter, false);
        }
        // out of the niche: towards the cell it came from
        return new NpcSpot(x + best[0], bestTop, z + best[1], yawOf(-best[0], -best[1]), true);
    }

    /** True if a villager standing on y in this column has room for its height. */
    static boolean fits(Blocks blocks, int x, double y, int z) {
        int from = (int) Math.ceil(y - 0.01);
        int to = (int) Math.floor(y + HEIGHT);
        for (int by = from; by <= to; by++) {
            if (blocks.solid(x, by, z)) {
                return false;
            }
        }
        return true;
    }

    /** The top of the floor of this column, at most a block above or two below y; NaN if none. */
    static double floorTop(Blocks blocks, int x, double y, int z) {
        for (int by = (int) Math.ceil(y); by >= (int) Math.floor(y) - 2; by--) {
            double height = blocks.height(x, by, z);
            if (height > 0) {
                return by + height;
            }
        }
        return Double.NaN;
    }

    /** The yaw to the nearest quarter turn, in [0, 360). */
    static float quarter(float yaw) {
        float turned = Math.round(yaw / 90f) * 90f;
        return ((turned % 360f) + 360f) % 360f;
    }

    /** Minecraft's yaw looking along (dx, dz): 0 south (+z), 90 west (-x), 180 north, 270 east. */
    static float yawOf(int dx, int dz) {
        if (dz > 0) {
            return 0f;
        }
        if (dx < 0) {
            return 90f;
        }
        if (dz < 0) {
            return 180f;
        }
        return 270f;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
