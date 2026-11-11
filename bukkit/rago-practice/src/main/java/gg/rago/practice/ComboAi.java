package gg.rago.practice;

import java.util.Random;

/**
 * The mind of the combo bot, without Bukkit: it plays as a 1.8 player would. It sprints at the
 * player, strafes around them once close, clicks at its own CPS with its own aim, and its hits
 * push as a player's do (a sprint hit pushes further). After a hit it may w-tap (let go of
 * forward a few ticks, to keep the distance of a combo), and when it is hit on the ground it may
 * jump reset (jump in the same tick, which takes away part of the knockback). It steers towards
 * where the player was a few ticks ago, its reaction time. Each level sets these.
 *
 * <p>The movement follows the 1.8 physics of a player: an input of forward and strafe gives an
 * acceleration of 0.1 a tick on the ground (0.13 sprinting) and 0.02 in the air (0.026
 * sprinting), the server then moves the body and slows it (0.546 on the ground, 0.91 in the
 * air). A jump sets the vertical speed to 0.42 and, sprinting, adds 0.2 forward.
 */
final class ComboAi {

    /** One level of the bot. */
    static final class Level {
        final String id;
        final String label;
        /** Clicks a second. */
        final double cps;
        /** The chance that a click in reach lands. */
        final double accuracy;
        /** How far it hits, from its eyes to the box of the player. */
        final double reach;
        /** True if it sprints (its hits then push further). */
        final boolean sprints;
        /** How much it strafes around the player once close, 0 to 1. */
        final double strafe;
        /** The chance of a w-tap after one of its hits. */
        final double wTap;
        /** The chance of a jump reset when it is hit on the ground. */
        final double jumpReset;
        /** How many ticks behind the player it steers. */
        final int reaction;

        Level(String id, String label, double cps, double accuracy, double reach, boolean sprints, double strafe,
                double wTap, double jumpReset, int reaction) {
            this.id = id;
            this.label = label;
            this.cps = cps;
            this.accuracy = accuracy;
            this.reach = reach;
            this.sprints = sprints;
            this.strafe = strafe;
            this.wTap = wTap;
            this.jumpReset = jumpReset;
            this.reaction = reaction;
        }
    }

    static final Level[] LEVELS = {
        new Level("easy", "Easy", 5, 0.55, 2.7, false, 0.0, 0.0, 0.0, 8),
        new Level("normal", "Normal", 8, 0.75, 2.9, true, 0.45, 0.4, 0.15, 5),
        new Level("hard", "Hard", 11, 0.88, 3.0, true, 0.8, 0.75, 0.45, 3),
        new Level("expert", "Expert", 14, 0.95, 3.0, true, 1.0, 1.0, 0.8, 1),
    };

    /** The most ticks of reaction a level may have: the length of the memory of the player. */
    static final int MEMORY = 10;

    static final double WALK_GROUND = 0.1;
    static final double SPRINT_GROUND = 0.13;
    static final double WALK_AIR = 0.02;
    static final double SPRINT_AIR = 0.026;
    static final double JUMP = 0.42;
    static final double SPRINT_JUMP = 0.2;
    /** A sprint hit slows the one who hits to this much of its speed. */
    static final double HIT_SLOWDOWN = 0.6;
    /** Further than this, the bot runs straight at the player. */
    static final double FAR = 3.4;
    /**
     * Closer than this, the bot stops pressing forward (it would only push into the player, with
     * no sprint): the good ones step back.
     */
    static final double CLOSE = 1.6;
    /** Where the eyes of the bot are, above its feet. */
    static final double EYES = 1.62;

    final Level level;
    private final Random random;

    /** Where the player was, the last MEMORY ticks (x, y, z each). */
    private final double[][] seen = new double[MEMORY][3];
    private int seenCount;
    private int seenAt;

    /** 1 or -1: the side it strafes to. */
    private int side = 1;
    private int sideTicks;
    /** Ticks left of a w-tap (forward let go). */
    private int wTapTicks;
    /** True when it was hit and jumps (if it can) in its next tick. */
    private boolean jumpResetNext;
    private double clicks;
    private boolean sprinting;

    ComboAi(Level level, Random random) {
        this.level = level;
        this.random = random;
        this.sideTicks = nextSideTicks();
    }

    /** The level of this id, or the first one. */
    static Level level(String id) {
        for (Level level : LEVELS) {
            if (level.id.equals(id)) {
                return level;
            }
        }
        return LEVELS[0];
    }

    /** Where the player is this tick (call it once a tick, before {@link #steer}). */
    void see(double x, double y, double z) {
        seenAt = (seenAt + 1) % MEMORY;
        seen[seenAt][0] = x;
        seen[seenAt][1] = y;
        seen[seenAt][2] = z;
        seenCount = Math.min(MEMORY, seenCount + 1);
    }

    /** Where the bot believes the player is: where they were its reaction time ago. */
    double[] believed() {
        int back = Math.min(level.reaction, seenCount - 1);
        if (back < 0) {
            return new double[3];
        }
        return seen[((seenAt - back) % MEMORY + MEMORY) % MEMORY].clone();
    }

    /** True if the bot sprints now. */
    boolean sprinting() {
        return sprinting;
    }

    /**
     * The new speed of the bot this tick: its speed now (vx, vy, vz), where it stands (x, z), on
     * the ground or not. Returns vx, vy, vz.
     */
    double[] steer(double x, double z, double vx, double vy, double vz, boolean onGround) {
        double[] target = believed();
        double dx = target[0] - x;
        double dz = target[2] - z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double fx = distance < 1e-6 ? 0 : dx / distance;
        double fz = distance < 1e-6 ? 0 : dz / distance;
        if (--sideTicks <= 0) {
            side = -side;
            sideTicks = nextSideTicks();
        }
        double forward;
        double strafe;
        if (wTapTicks > 0) {
            wTapTicks--;
            forward = 0;
            strafe = side * level.strafe;
        } else if (distance > FAR) {
            forward = 1;
            strafe = 0;
        } else if (distance > CLOSE) {
            forward = 1;
            strafe = side * level.strafe;
        } else {
            // too close for a sprint hit: a good bot steps back (an s-tap), the others let go
            forward = level.wTap > 0.5 ? -0.5 : 0;
            strafe = side * level.strafe;
        }
        sprinting = level.sprints && forward > 0;
        double[] push = push(fx, fz, forward, strafe, onGround, sprinting);
        double nx = vx + push[0];
        double ny = vy;
        double nz = vz + push[1];
        if (jumpResetNext) {
            jumpResetNext = false;
            // the jump of the same tick: its own height in place of the knockback's, and the
            // sprint jump forward, against the push
            ny = Math.min(ny, JUMP);
            nx += fx * SPRINT_JUMP;
            nz += fz * SPRINT_JUMP;
        }
        return new double[] {nx, ny, nz};
    }

    /**
     * The acceleration of a player's input this tick: forward and strafe from -1 to 1, the
     * forward way (fx, fz). Returns the change of vx and vz.
     */
    static double[] push(double fx, double fz, double forward, double strafe, boolean onGround, boolean sprinting) {
        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length < 1e-6) {
            return new double[2];
        }
        double speed = onGround ? (sprinting ? SPRINT_GROUND : WALK_GROUND) : (sprinting ? SPRINT_AIR : WALK_AIR);
        // as the client does, a diagonal input is no faster than a straight one
        double scale = speed / Math.max(1.0, length);
        // the right hand of the forward way (fx, fz) is (-fz, fx)
        double rx = -fz;
        double rz = fx;
        return new double[] {(fx * forward + rx * strafe) * scale, (fz * forward + rz * strafe) * scale};
    }

    /** How many clicks it makes this tick, at its CPS with some unevenness. */
    int clicks() {
        clicks += level.cps / 20.0 * (0.6 + 0.8 * random.nextDouble());
        int now = (int) clicks;
        clicks -= now;
        return now;
    }

    /** True if a click from its eyes at this distance of the player's box lands. */
    boolean lands(double reachDistance) {
        return reachDistance <= level.reach && random.nextDouble() < level.accuracy;
    }

    /** It landed a hit: a sprint hit slows it, and it may w-tap. */
    void hitLanded() {
        if (random.nextDouble() < level.wTap) {
            wTapTicks = 2 + random.nextInt(3);
        }
        sprinting = false;
    }

    /** It was hit: it may jump reset (if on the ground), and a good bot changes its side. */
    void wasHit(boolean onGround) {
        if (onGround && random.nextDouble() < level.jumpReset) {
            jumpResetNext = true;
        }
        if (random.nextDouble() < level.strafe * 0.5) {
            side = -side;
            sideTicks = nextSideTicks();
        }
    }

    private int nextSideTicks() {
        return 8 + random.nextInt(18);
    }

    /**
     * The distance from the eyes (ex, ey, ez) to the hit box of a player standing at (px, py, pz):
     * a box 0.6 wide and 1.8 high, with the 0.1 the server adds on each side.
     */
    static double reach(double ex, double ey, double ez, double px, double py, double pz) {
        double cx = clamp(ex, px - 0.4, px + 0.4);
        double cy = clamp(ey, py - 0.1, py + 1.9);
        double cz = clamp(ez, pz - 0.4, pz + 0.4);
        double x = ex - cx;
        double y = ey - cy;
        double z = ez - cz;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    /**
     * The speed of a player after a hit, as a 1.8 server gives it: their speed halved, 0.4 away from
     * the one who hits and 0.4 up (at most 0.4 up), then, for a sprint hit, 0.5 more in the way the
     * hitter faces (wx, wz) and 0.1 up. (ax, az): the one who hits; (vx, vz): the one hit.
     */
    static double[] knockback(double vx, double vy, double vz, double ax, double az, double px, double pz,
            boolean sprint, double wx, double wz) {
        double dx = ax - px;
        double dz = az - pz;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.01) {
            dx = wx == 0 && wz == 0 ? 0.01 : -wx;
            dz = wx == 0 && wz == 0 ? 0 : -wz;
            distance = Math.sqrt(dx * dx + dz * dz);
        }
        double x = vx / 2 - dx / distance * 0.4;
        double y = Math.min(0.4, vy / 2 + 0.4);
        double z = vz / 2 - dz / distance * 0.4;
        if (sprint) {
            double way = Math.sqrt(wx * wx + wz * wz);
            if (way > 1e-6) {
                x += wx / way * 0.5;
                z += wz / way * 0.5;
            }
            y += 0.1;
        }
        return new double[] {x, y, z};
    }
}
