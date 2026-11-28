package gg.rago.practice;

/**
 * How RagoBot runs in Block Pattern Practice, without Bukkit: the way of its technique, then at
 * the player, sprinting, with the 1.8 physics of a player ({@link ComboAi#push}). A block in its
 * way at its feet with room over it, it jumps it; a wall it cannot jump (two high, or a roof over
 * it), it breaks it when its technique says so, else it walks round it on the technique's side.
 * Places are offsets in the arena (x, y from the floor, z); the player's frame of the techniques is
 * turned into them by {@link #arenaX}.
 */
final class PatternBot {

    /** The blocks of the arena, as RagoBot meets them. */
    interface Ground {
        boolean solid(int x, int y, int z);
    }

    /** How far ahead it looks for a block in its way. */
    static final double AHEAD = 0.7;
    /** A point of its way is reached this close. */
    static final double REACHED = 0.7;
    /** How strongly it turns aside along a wall it walks round, against its forward way. */
    static final double ROUND = 2.0;

    private final BlockPatterns.Technique technique;
    /** The points of its way in the arena, x, z each. */
    private final double[][] way;
    private int next = 1;
    private boolean sprinting;
    /** The block it breaks (x, y, z in the arena), or null. */
    private int[] breaking;

    PatternBot(BlockPatterns.Technique technique) {
        this.technique = technique;
        way = new double[technique.path.length][];
        for (int i = 0; i < way.length; i++) {
            way[i] = new double[] {arenaX(technique.path[i][0]) + 0.5, technique.path[i][1] + 0.5};
        }
    }

    /** The x in the arena of an x of the player's frame: the player faces +z, their right is -x. */
    static int arenaX(int x) {
        return -x;
    }

    static double arenaX(double x) {
        return -x;
    }

    /** Where it starts in the arena: x, z. */
    double[] start() {
        return way[0].clone();
    }

    boolean sprinting() {
        return sprinting;
    }

    /** The block it wants to break now (x, y, z in the arena), or null. */
    int[] breaking() {
        return breaking;
    }

    /**
     * Its speed for this tick: where it stands (x, y, z), its speed now (vx, vy, vz), on the
     * ground or not, where the player stands (px, pz). Returns vx, vy, vz.
     */
    double[] steer(double x, double y, double z, double vx, double vy, double vz, boolean onGround, double px, double pz,
            Ground ground) {
        while (next < way.length && Math.hypot(way[next][0] - x, way[next][1] - z) < REACHED) {
            next++;
        }
        double tx = next < way.length ? way[next][0] : px;
        double tz = next < way.length ? way[next][1] : pz;
        double dx = tx - x;
        double dz = tz - z;
        double distance = Math.hypot(dx, dz);
        breaking = null;
        if (distance < 1e-6) {
            sprinting = false;
            return new double[] {vx, vy, vz};
        }
        double fx = dx / distance;
        double fz = dz / distance;
        int feet = (int) Math.floor(y + 0.01);
        int bx = (int) Math.floor(x + fx * AHEAD);
        int bz = (int) Math.floor(z + fz * AHEAD);
        boolean low = ground.solid(bx, feet, bz);
        boolean high = ground.solid(bx, feet + 1, bz);
        boolean roof = ground.solid(bx, feet + 2, bz) || ground.solid((int) Math.floor(x), feet + 2, (int) Math.floor(z));
        boolean jump = false;
        double mx = fx;
        double mz = fz;
        double forward = 1;
        if (low && !high && !roof) {
            jump = onGround;
        } else if (low || high) {
            if (technique.breaks) {
                // its shears on the block in its way, the one at its head first
                breaking = high ? new int[] {bx, feet + 1, bz} : new int[] {bx, feet, bz};
                forward = 0;
            } else {
                // round the wall, on its technique's side (the player's right is -x)
                double ax = arenaX((double) technique.side);
                mx = fx + ax * ROUND;
                mz = fz;
                double length = Math.hypot(mx, mz);
                mx /= length;
                mz /= length;
            }
        }
        sprinting = forward > 0;
        double[] push = ComboAi.push(mx, mz, forward, 0, onGround, sprinting);
        double nx = vx + push[0];
        double ny = vy;
        double nz = vz + push[1];
        if (jump) {
            ny = ComboAi.JUMP;
            nx += mx * ComboAi.SPRINT_JUMP;
            nz += mz * ComboAi.SPRINT_JUMP;
        }
        return new double[] {nx, ny, nz};
    }

    /** A breaking it was told about went: it may go on. */
    void broke() {
        breaking = null;
    }
}
