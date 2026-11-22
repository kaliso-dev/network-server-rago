package gg.rago.lobby;

import java.util.Random;

/**
 * The mind of RagoBot, without Bukkit: each tick it gets what a player sees ({@link Sight}) and
 * gives what a player presses ({@link Controls}): forward, strafe, jump, sprint, where it looks,
 * its clicks, its hotbar slot, eating. Its body ({@link BotBody}) gives these to the server as a
 * client would, and the server moves it with the 1.8 physics of a player: nothing here moves it.
 *
 * <p>It fights the nearest player of the other team: it runs at them sprinting, strafes around them
 * once close, clicks at its CPS, its hits land when its aim is on them within its reach, as a
 * player's do. After a hit of its own it may w-tap (lets go of forward a few ticks: its next hit is
 * a sprint hit again); hit on the ground, it may jump reset (jumps in the tick the knockback
 * arrives). It steers towards where the other was its reaction time ago. On the ground, it never
 * walks off an edge on its own (the void, the water of the sumo). Low on health, away from the
 * other one or very low, it eats a golden apple if it has one, once its regeneration is over. One
 * skill, the same for every RagoBot ({@link Skill}): no levels.
 */
final class BotBrain {

    /** The skill of RagoBot (ragobot.skill of config.yml). Immutable. */
    static final class Skill {
        /**
         * The bundled skill: the "hard" level of the combo bot (its CPS, accuracy, reach, strafe,
         * w-tap, jump reset, reaction), a turn of 30 degrees a tick and a golden apple under 10
         * health. To set in game: the owner has not tried it yet.
         */
        static final Skill BUNDLED = new Skill(11, 0.88, 3.0, 0.8, 0.75, 0.45, 3, 30, 10);

        /** Clicks a second. */
        final double cps;
        /** The chance that a click with its aim on the other lands. */
        final double accuracy;
        /** How far it hits, from its eyes to the box of the other (3 at most, as a fair player). */
        final double reach;
        /** How much it strafes around the other once close, 0 to 1. */
        final double strafe;
        /** The chance of a w-tap after one of its hits. */
        final double wTap;
        /** The chance of a jump reset when it is hit on the ground. */
        final double jumpReset;
        /** How many ticks behind the other it steers. */
        final int reaction;
        /** Degrees it turns its head a tick at most. */
        final double turn;
        /** Under this health (20 is full), it eats a golden apple. */
        final double healAt;

        Skill(double cps, double accuracy, double reach, double strafe, double wTap, double jumpReset, int reaction,
                double turn, double healAt) {
            this.cps = cps;
            this.accuracy = accuracy;
            this.reach = reach;
            this.strafe = strafe;
            this.wTap = wTap;
            this.jumpReset = jumpReset;
            this.reaction = reaction;
            this.turn = turn;
            this.healAt = healAt;
        }
    }

    /** The blocks around the bot: true where a block holds a player up (not air, not water). */
    interface Ground {
        boolean solid(int x, int y, int z);
    }

    /** What the bot knows this tick. */
    static final class Sight {
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        boolean onGround;
        double health;
        /** It regenerates (the golden apple eaten before is still at work). */
        boolean regenerating;
        /** A knockback reached it this tick. */
        boolean knocked;
        /** In a game, with a player of the other team to fight (tx, ty, tz: their feet). */
        boolean fighting;
        double tx;
        double ty;
        double tz;
        /** The hotbar slot held, and those of its sword and golden apples (-1: none). */
        int held;
        int sword = -1;
        int gapple = -1;
        /** It eats (an item in use). */
        boolean eating;
        Ground ground;
    }

    /** What the bot presses this tick. */
    static final class Controls {
        /** -1 to 1, as the keys of a client (forward, and strafe: to its left). */
        double forward;
        double strafe;
        boolean jump;
        boolean sprint;
        float yaw;
        float pitch;
        /** The hotbar slot to hold, -1: the same. */
        int slot = -1;
        /** It uses the item held (eats), or keeps eating. */
        boolean eat;
        /** Its clicks this tick (each one a swing of the arm). */
        int swings;
        /** The first click hits the other. */
        boolean hit;
    }

    /** The most ticks of reaction: the length of its memory of the other. */
    static final int MEMORY = 10;
    /** Further than this, it runs straight at the other. */
    static final double FAR = 3.4;
    /** Closer than this, it stops pressing forward: it would only push into the other. */
    static final double CLOSE = 1.6;
    /** Where its eyes are, above its feet. */
    static final double EYES = 1.62;
    /** Where it aims, above the other's feet. */
    static final double CHEST = 1.2;
    /** Its aim lands within this many degrees of the other. */
    static final double AIM = 30;
    /** Eating, a client moves at this much of its input, and does not sprint. */
    static final double EATING_SPEED = 0.2;
    /** How far ahead it looks for the ground before a step (blocks), and how far down (blocks). */
    static final double[] AHEAD = {0.6, 1.2};
    static final int DROP = 3;

    final Skill skill;
    private final Random random;

    /** Where the other was, the last MEMORY ticks (x, y, z each). */
    private final double[][] seen = new double[MEMORY][3];
    private int seenCount;
    private int seenAt;
    /** 1 or -1: the side it strafes to. */
    private int side = 1;
    private int sideTicks;
    /** Ticks left of a w-tap (forward let go). */
    private int wTapTicks;
    private double clicks;

    BotBrain(Skill skill, Random random) {
        this.skill = skill;
        this.random = random;
        this.sideTicks = nextSideTicks();
    }

    /** This tick's keys, from this tick's sight. */
    Controls think(Sight sight) {
        Controls controls = new Controls();
        controls.yaw = sight.yaw;
        controls.pitch = sight.pitch;
        if (!sight.fighting) {
            // nothing to fight (the lobby, a countdown, dead in a round): it stands, and forgets
            seenCount = 0;
            wTapTicks = 0;
            clicks = 0;
            return controls;
        }
        see(sight.tx, sight.ty, sight.tz);
        double[] target = believed();
        double dx = target[0] - sight.x;
        double dz = target[2] - sight.z;
        double distance = Math.sqrt(dx * dx + dz * dz);

        controls.yaw = turn(sight.yaw, yawTo(dx, dz), skill.turn);
        double rise = target[1] + CHEST - (sight.y + EYES);
        float pitch = (float) -Math.toDegrees(Math.atan2(rise, Math.max(distance, 0.05)));
        controls.pitch = (float) clamp(turn(sight.pitch, pitch, skill.turn), -90, 90);

        boolean eating = sight.eating;
        if (eating) {
            controls.eat = true;
        } else if (sight.gapple >= 0 && !sight.regenerating && sight.health <= skill.healAt
                && (distance > FAR || sight.health <= skill.healAt / 2)) {
            controls.slot = sight.gapple;
            controls.eat = true;
            eating = true;
        } else if (sight.sword >= 0 && sight.held != sight.sword) {
            controls.slot = sight.sword;
        }

        if (--sideTicks <= 0) {
            side = -side;
            sideTicks = nextSideTicks();
        }
        if (wTapTicks > 0) {
            wTapTicks--;
            controls.forward = 0;
            controls.strafe = side * skill.strafe;
        } else if (distance > FAR) {
            controls.forward = 1;
        } else if (distance > CLOSE) {
            controls.forward = 1;
            controls.strafe = side * skill.strafe;
        } else {
            // too close for a sprint hit: a good player steps back a little
            controls.forward = skill.wTap > 0.5 ? -0.5 : 0;
            controls.strafe = side * skill.strafe;
        }
        if (sight.knocked) {
            if (sight.onGround && random.nextDouble() < skill.jumpReset) {
                controls.jump = true;
            }
            if (random.nextDouble() < skill.strafe * 0.5) {
                side = -side;
                sideTicks = nextSideTicks();
                controls.strafe = -controls.strafe;
            }
        }
        if (target[1] > sight.y + 0.6 && distance < 2.5 && sight.onGround) {
            // the other stands higher, right there: a step up
            controls.jump = true;
        }
        if (eating) {
            controls.forward *= EATING_SPEED;
            controls.strafe *= EATING_SPEED;
        }
        if (sight.onGround && sight.ground != null) {
            keepOnGround(controls, sight);
        }
        controls.sprint = !eating && controls.forward >= 0.8;

        int now = clicks();
        if (!eating && now > 0) {
            controls.swings = now;
            double ex = sight.x;
            double ey = sight.y + EYES;
            double ez = sight.z;
            double real = reach(ex, ey, ez, sight.tx, sight.ty, sight.tz);
            double off = Math.abs(wrap(controls.yaw - yawTo(sight.tx - sight.x, sight.tz - sight.z)));
            if (real <= skill.reach && off <= AIM && random.nextDouble() < skill.accuracy) {
                controls.hit = true;
                if (random.nextDouble() < skill.wTap) {
                    wTapTicks = 2 + random.nextInt(3);
                }
            }
        }
        return controls;
    }

    // --- The other, as it saw them ------------------------------------------------------------

    private void see(double x, double y, double z) {
        seenAt = (seenAt + 1) % MEMORY;
        seen[seenAt][0] = x;
        seen[seenAt][1] = y;
        seen[seenAt][2] = z;
        seenCount = Math.min(MEMORY, seenCount + 1);
    }

    /** Where it believes the other is: where they were its reaction time ago. */
    double[] believed() {
        int back = Math.min(skill.reaction, seenCount - 1);
        if (back < 0) {
            return new double[3];
        }
        return seen[((seenAt - back) % MEMORY + MEMORY) % MEMORY].clone();
    }

    // --- Edges ----------------------------------------------------------------------------------

    /**
     * On the ground, it steps only where the ground goes on: else the other side of its strafe, or
     * no forward, or no strafe, or it stands.
     */
    private void keepOnGround(Controls controls, Sight sight) {
        if (controls.forward == 0 && controls.strafe == 0) {
            return;
        }
        if (safe(sight, controls.yaw, controls.forward, controls.strafe)) {
            return;
        }
        if (controls.strafe != 0 && safe(sight, controls.yaw, controls.forward, -controls.strafe)) {
            controls.strafe = -controls.strafe;
            side = -side;
            sideTicks = nextSideTicks();
            return;
        }
        if (controls.strafe != 0 && safe(sight, controls.yaw, 0, controls.strafe)) {
            controls.forward = 0;
            return;
        }
        if (controls.forward != 0 && safe(sight, controls.yaw, controls.forward, 0)) {
            controls.strafe = 0;
            return;
        }
        controls.forward = 0;
        controls.strafe = 0;
    }

    /** True if the ground goes on where these keys take it. */
    static boolean safe(Sight sight, float yaw, double forward, double strafe) {
        double[] way = way(yaw, forward, strafe);
        for (double ahead : AHEAD) {
            if (!groundUnder(sight, sight.x + way[0] * ahead, sight.z + way[1] * ahead)) {
                return false;
            }
        }
        return true;
    }

    /** True if a block holds a player up under (x, z), at most DROP blocks under its feet. */
    static boolean groundUnder(Sight sight, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int feet = (int) Math.floor(sight.y - 0.01);
        for (int down = 0; down < DROP; down++) {
            if (sight.ground.solid(bx, feet - down, bz)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The way (x, z) these keys move a player facing yaw, as the client's movement does: forward
     * is (-sin, cos) of the yaw, strafe (to the left) (cos, sin).
     */
    static double[] way(float yaw, double forward, double strafe) {
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double x = strafe * cos - forward * sin;
        double z = forward * cos + strafe * sin;
        double length = Math.sqrt(x * x + z * z);
        return length < 1e-9 ? new double[2] : new double[] {x / length, z / length};
    }

    // --- Aim, clicks -----------------------------------------------------------------------------

    /** The yaw that faces the way (dx, dz): 0 faces +z, 90 faces -x. */
    static float yawTo(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** From this angle towards that one, by at most step degrees. */
    static float turn(float from, float to, double step) {
        double change = clamp(wrap(to - from), -step, step);
        return (float) (from + change);
    }

    /** The angle in -180 to 180. */
    static double wrap(double degrees) {
        double angle = degrees % 360;
        if (angle >= 180) {
            angle -= 360;
        } else if (angle < -180) {
            angle += 360;
        }
        return angle;
    }

    /** How many clicks it makes this tick, at its CPS with some unevenness. */
    private int clicks() {
        clicks += skill.cps / 20.0 * (0.6 + 0.8 * random.nextDouble());
        int now = (int) clicks;
        clicks -= now;
        return now;
    }

    private int nextSideTicks() {
        return 8 + random.nextInt(18);
    }

    /**
     * The distance from the eyes (ex, ey, ez) to the hit box of a player standing at (px, py, pz):
     * a box 0.6 wide and 1.8 high, with the 0.1 the server adds on each side.
     */
    static double reach(double ex, double ey, double ez, double px, double py, double pz) {
        double x = ex - clamp(ex, px - 0.4, px + 0.4);
        double y = ey - clamp(ey, py - 0.1, py + 1.9);
        double z = ez - clamp(ez, pz - 0.4, pz + 0.4);
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
}
