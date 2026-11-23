package gg.rago.lobby;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 *
 * <p>In a game of blocks (the Block Placement, step 2 of the owner's order of 22/11), as a player
 * there: knocked off over the void, it clutches (a block of its kit under its feet, against the side
 * of the block beside it, looking at that side); the other standing over the void on one block of
 * theirs out of its hits, it breaks that block (its shears on the wool, its pickaxe on the end
 * stone, as long as a player's break takes), and they fall; it jumps up a step of one block on its
 * way. Its clicks on the blocks go where it looks, as a client's: it turns its head to the side it
 * clicks, at its turn a tick, and clicks once its look is on it, within a client's reach.
 *
 * <p>In the block edit (step 3 of the owner's order of 22/11, the choices by default of its
 * proposal, approved on 23/11), at the wall of wool between it and the other, with the rules of
 * the mode: the wall whole, nobody's point, it goes against the wall in front of the other, on its
 * side; once the other is in its hits through the block at the height of its head, it breaks that
 * block (its shears), hits them through the hole after its reaction time, and puts a block of wool
 * back in the hole against a block beside it: its point, if its hit was the last. The other out of
 * its hits again without its hit, it closes the hole all the same. Hit by the other (their point),
 * it hits back through the hole once they are in its hits, then closes; knocked away, it comes
 * back to the wall in front of the hole. A hole of the other's, it plays through it and breaks no
 * other block. Its hits there go only through a hole: never through a block.
 */
final class BotBrain {

    /** The skill of RagoBot (ragobot.skill of config.yml). Immutable. */
    static final class Skill {
        /**
         * The bundled skill: the "hard" level of the combo bot (its CPS, accuracy, reach, strafe,
         * w-tap, jump reset, reaction), a turn of 30 degrees a tick and a golden apple under 10
         * health, and a ping of 40 ms (the owner's ask of 23/11). To set in game.
         */
        static final Skill BUNDLED = new Skill(11, 0.88, 3.0, 0.8, 0.75, 0.45, 3, 30, 10, 6, 40);

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
        /**
         * Blocks it places a second at most (a clutch is one): to set in game with the owner, as
         * the rest.
         */
        final double blocksPerSecond;
        /**
         * Its ping, in milliseconds: its hits go by where the other was this long ago, as a
         * player's do (the owner's ask of 23/11: with none, it had the first hit every time).
         */
        final int ping;

        Skill(double cps, double accuracy, double reach, double strafe, double wTap, double jumpReset, int reaction,
                double turn, double healAt) {
            this(cps, accuracy, reach, strafe, wTap, jumpReset, reaction, turn, healAt, 6);
        }

        Skill(double cps, double accuracy, double reach, double strafe, double wTap, double jumpReset, int reaction,
                double turn, double healAt, double blocksPerSecond) {
            this(cps, accuracy, reach, strafe, wTap, jumpReset, reaction, turn, healAt, blocksPerSecond, 0);
        }

        Skill(double cps, double accuracy, double reach, double strafe, double wTap, double jumpReset, int reaction,
                double turn, double healAt, double blocksPerSecond, int ping) {
            this.cps = cps;
            this.accuracy = accuracy;
            this.reach = reach;
            this.strafe = strafe;
            this.wTap = wTap;
            this.jumpReset = jumpReset;
            this.reaction = reaction;
            this.turn = turn;
            this.healAt = healAt;
            this.blocksPerSecond = blocksPerSecond;
            this.ping = ping;
        }
    }

    /** The blocks around the bot: true where a block holds a player up (not air, not water). */
    interface Ground {
        boolean solid(int x, int y, int z);
    }

    /**
     * The blocks of the players it may break: the hotbar slot of its tool for the block there (its
     * shears for the wool of a kit), -1 for any other block, or one the game refused it before.
     */
    interface Breakable {
        int tool(int x, int y, int z);
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
        /** Its game is played with blocks (the Block Placement): it places and breaks them there. */
        boolean blocks;
        /** The hotbar slot of the blocks it places (the wool of its kit), -1: none. */
        int block = -1;
        /** The other one stands on the ground. */
        boolean targetOnGround;
        Breakable breakable;
        /**
         * Its game is a block edit (the Block edit 1v1, the Red Rover Block Edit): the wall of
         * wool between it and the other, the blocks it may break ({@link #breakable}).
         */
        boolean edit;
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
        /**
         * A right click with the block held on this side of this block, {x, y, z, side} (the sides
         * as a client numbers them: {@link #FACES}); null: none. The new block goes beside it.
         */
        int[] place;
        /** Where its look meets that side, from the corner of the block (0 to 1 each way). */
        float[] cursor;
        /** The left button held on this block, {x, y, z, side}: it digs it; null: it digs nothing. */
        int[] dig;
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
    /**
     * When every way of its keys goes off an edge, it tries them turned by these angles (degrees),
     * to one side then the other: never more than 60, so that the other beyond an edge with no way
     * to them still leaves it standing, as before.
     */
    static final double[] TURNS = {20, 40, 60};
    /**
     * The sides of a block, as a client numbers them in its clicks: down, up, north (-z), south
     * (+z), west (-x), east (+x); each the way out of the block.
     */
    static final int[][] FACES = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    static final int DOWN = 0;
    static final int UP = 1;
    /** A client reaches a block this far from its eyes, in survival. */
    static final double REACH_BLOCKS = 4.5;
    /**
     * Nothing this many blocks under it: the void (the fall line of the Block Placement is 10 under
     * its floor).
     */
    static final int VOID_DEPTH = 10;
    /** How many blocks under its feet it looks for a place to clutch, the first right under. */
    static final int CLUTCH_DEPTH = 3;
    /** Half the width of a player. */
    static final double HALF_WIDTH = 0.3;
    /** Further than this between two ticks, it was moved to its start: a new round of the block edit. */
    static final double MOVED = 1.5;
    /** Its place before the wall: this far from the middle of the wall, its body against it. */
    static final double AT_WALL = 0.5 + HALF_WIDTH + 0.02;
    /** How far around it (blocks) it looks for the wall of the block edit. */
    static final int WALL_SEARCH = 6;
    /** The points of the other it may hit through a hole, above their feet: their head, chest and legs. */
    static final double[] HIT_POINTS = {EYES, CHEST, 0.6};

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
    /** Ticks until its next block. */
    private int placeWait;

    /** The blocks of the wall of its block edit, {x, y, z} each; null until it saw it. */
    private int[][] wall;
    /** The axis across the wall (0: its blocks at one x, 2: at one z), and that x or z. */
    private int wallAxis;
    private int wallAt;
    /** The wall from one end to the other (along it), and from its bottom to its top. */
    private int wallLow;
    private int wallHigh;
    private int wallBottom;
    private int wallTop;
    /** Who hit last in its block edit: 1 it (its point), -1 the other (theirs), 0 nobody this round. */
    private int lastHit;
    /** The other was in its hits through a hole of the wall (or the block it opened for it). */
    private boolean hadInHits;
    /** Ticks in a row the other is in its hits through a hole. */
    private int inHitsTicks;
    /** Where it stood the tick before, in the block edit. */
    private double lastX = Double.NaN;
    private double lastZ;

    BotBrain(Skill skill, Random random) {
        this.skill = skill;
        this.random = random;
        this.sideTicks = nextSideTicks();
    }

    /** Its ping at most, in milliseconds: the ticks it remembers. */
    static final int MAX_PING = 300;

    /**
     * This tick's keys, from this tick's sight. In a fight, it decides by where the other was its
     * ping ago (its hits too, as a player's), and remembers where they are now.
     */
    Controls think(Sight sight) {
        if (!sight.fighting) {
            return decide(sight);
        }
        see(sight.tx, sight.ty, sight.tz);
        if (skill.ping <= 0) {
            return decide(sight);
        }
        double tx = sight.tx;
        double ty = sight.ty;
        double tz = sight.tz;
        double[] lagged = lagged();
        sight.tx = lagged[0];
        sight.ty = lagged[1];
        sight.tz = lagged[2];
        try {
            return decide(sight);
        } finally {
            sight.tx = tx;
            sight.ty = ty;
            sight.tz = tz;
        }
    }

    private Controls decide(Sight sight) {
        Controls controls = new Controls();
        controls.yaw = sight.yaw;
        controls.pitch = sight.pitch;
        if (placeWait > 0) {
            placeWait--;
        }
        if (!sight.fighting) {
            // nothing to fight (the lobby, a countdown, dead in a round): it stands, and forgets
            seenCount = 0;
            wTapTicks = 0;
            clicks = 0;
            forgetEdit();
            return controls;
        }
        double[] target = believed();
        if (sight.edit) {
            return edit(sight, controls, target);
        }
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
        // its hands on the blocks: a clutch first, else the block under the other
        boolean clutching = !eating && sight.blocks && sight.ground != null && clutch(sight, controls);
        boolean digging = !eating && !clutching && sight.blocks && sight.ground != null
                && undermine(sight, controls, target);
        boolean onBlocks = clutching || digging;

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
        if (digging && controls.dig != null) {
            // it stands while it breaks the block out of its hits, its look on it, as a player
            // does: a step on would take it into its hits or off the edge before the block goes
            controls.forward = 0;
            controls.strafe = 0;
            controls.jump = false;
        }
        if (sight.onGround && sight.ground != null) {
            keepOnGround(controls, sight);
            if (sight.blocks && stepAhead(sight, controls.yaw, controls.forward, controls.strafe)) {
                // a step of one block on its way (the hills of the block placement maps): a jump
                controls.jump = true;
            }
        }
        controls.sprint = !eating && !onBlocks && controls.forward >= 0.8;

        int now = clicks();
        if (!eating && !onBlocks && now > 0) {
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

    // --- Blocks ---------------------------------------------------------------------------------

    /**
     * Over the void, a block of its kit under its feet: in the highest place under it it can reach
     * (its top at its feet at most), against the side of the block beside that place. It holds the
     * block, turns its head to that side, and clicks once its look is on it. True while it clutches.
     */
    private boolean clutch(Sight sight, Controls controls) {
        if (sight.block < 0 || !overVoid(sight.ground, sight.x, sight.y, sight.z)) {
            return false;
        }
        int[] against = clutchPlace(sight);
        if (against == null) {
            return false;
        }
        controls.slot = sight.held == sight.block ? -1 : sight.block;
        lookAt(sight, controls, faceCentre(against, against[3]));
        double[] hit = rayHit(sight, controls.yaw, controls.pitch, against[0], against[1], against[2]);
        if (hit != null && (int) hit[0] == against[3] && placeWait == 0) {
            controls.place = against;
            controls.cursor = new float[] {(float) (hit[1] - against[0]), (float) (hit[2] - against[1]),
                (float) (hit[3] - against[2])};
            placeWait = (int) Math.ceil(20 / Math.max(0.1, skill.blocksPerSecond));
        }
        return true;
    }

    /**
     * Where it clutches: {x, y, z, side} of the block it clicks, the new block beside it under its
     * feet; null if no place under it touches a block it can reach.
     */
    static int[] clutchPlace(Sight sight) {
        int[][] columns = footprint(sight.x, sight.z);
        int top = (int) Math.floor(sight.y + 1e-6) - 1;
        double ex = sight.x;
        double ey = sight.y + EYES;
        double ez = sight.z;
        for (int cy = top; cy > top - CLUTCH_DEPTH; cy--) {
            for (int[] column : columns) {
                if (sight.ground.solid(column[0], cy, column[1])) {
                    continue;
                }
                for (int face = 2; face < FACES.length; face++) {
                    int nx = column[0] + FACES[face][0];
                    int ny = cy + FACES[face][1];
                    int nz = column[1] + FACES[face][2];
                    if (!sight.ground.solid(nx, ny, nz)) {
                        continue;
                    }
                    // the side of that block towards the place: the other way
                    int side = face ^ 1;
                    double[] centre = faceCentre(new int[] {nx, ny, nz}, side);
                    if (distance(ex, ey, ez, centre[0], centre[1], centre[2]) <= REACH_BLOCKS) {
                        return new int[] {nx, ny, nz, side};
                    }
                }
            }
        }
        return null;
    }

    /**
     * The other stands on one block over the void, a block of the players it can break, out of its
     * hits but within its reach: it holds its tool, turns its head to the side of that block towards
     * it, and digs it once its look is on it. True while it does.
     */
    private boolean undermine(Sight sight, Controls controls, double[] other) {
        if (!sight.targetOnGround || sight.breakable == null) {
            return false;
        }
        if (reach(sight.x, sight.y + EYES, sight.z, sight.tx, sight.ty, sight.tz) <= skill.reach) {
            // within its hits: it fights
            return false;
        }
        int[] under = holding(sight.ground, other[0], other[1], other[2]);
        if (under == null) {
            return false;
        }
        int tool = sight.breakable.tool(under[0], under[1], under[2]);
        if (tool < 0 || nearest(sight, under) > REACH_BLOCKS) {
            return false;
        }
        controls.slot = sight.held == tool ? -1 : tool;
        lookAt(sight, controls, faceCentre(under, sideTowards(sight, under)));
        double[] hit = rayHit(sight, controls.yaw, controls.pitch, under[0], under[1], under[2]);
        if (hit != null) {
            controls.dig = new int[] {under[0], under[1], under[2], (int) hit[0]};
        }
        return true;
    }

    /**
     * The one block that holds up a player standing at (x, y, z), {x, y, z}, when nothing else
     * does and the void is under it; else null.
     */
    static int[] holding(Ground ground, double x, double y, double z) {
        int level = (int) Math.floor(y - 0.01);
        int[] found = null;
        for (int[] column : footprint(x, z)) {
            if (ground.solid(column[0], level, column[1])) {
                if (found != null && (found[0] != column[0] || found[2] != column[1])) {
                    return null;
                }
                found = new int[] {column[0], level, column[1]};
            }
        }
        if (found == null) {
            return null;
        }
        for (int[] column : footprint(x, z)) {
            for (int down = 1; down <= VOID_DEPTH; down++) {
                if (ground.solid(column[0], level - down, column[1])) {
                    return null;
                }
            }
        }
        return found;
    }

    /** True if no block is under a player at (x, y, z) for VOID_DEPTH blocks: it would fall into the void. */
    static boolean overVoid(Ground ground, double x, double y, double z) {
        int feet = (int) Math.floor(y - 0.01);
        for (int[] column : footprint(x, z)) {
            for (int down = 0; down < VOID_DEPTH; down++) {
                if (ground.solid(column[0], feet - down, column[1])) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The columns of blocks under a player at (x, z), {x, z} each, the one under its middle first. */
    static int[][] footprint(double x, double z) {
        int mx = (int) Math.floor(x);
        int mz = (int) Math.floor(z);
        int[][] all = new int[5][];
        int n = 0;
        all[n++] = new int[] {mx, mz};
        for (double cx : new double[] {x - HALF_WIDTH, x + HALF_WIDTH}) {
            for (double cz : new double[] {z - HALF_WIDTH, z + HALF_WIDTH}) {
                int bx = (int) Math.floor(cx);
                int bz = (int) Math.floor(cz);
                boolean known = false;
                for (int i = 0; i < n; i++) {
                    known |= all[i][0] == bx && all[i][1] == bz;
                }
                if (!known) {
                    all[n++] = new int[] {bx, bz};
                }
            }
        }
        int[][] columns = new int[n][];
        System.arraycopy(all, 0, columns, 0, n);
        return columns;
    }

    /** True if a step of one block stands where these keys take it on the ground, with room above. */
    boolean stepAhead(Sight sight, float yaw, double forward, double strafe) {
        if (forward == 0 && strafe == 0) {
            return false;
        }
        double[] way = way(yaw, forward, strafe);
        int feet = (int) Math.floor(sight.y + 0.01);
        int ax = (int) Math.floor(sight.x + way[0] * (HALF_WIDTH + 0.2));
        int az = (int) Math.floor(sight.z + way[1] * (HALF_WIDTH + 0.2));
        if (ax == (int) Math.floor(sight.x) && az == (int) Math.floor(sight.z)) {
            return false;
        }
        Ground ground = sight.ground;
        return ground.solid(ax, feet, az) && !ground.solid(ax, feet + 1, az) && !ground.solid(ax, feet + 2, az)
                && !ground.solid((int) Math.floor(sight.x), feet + 2, (int) Math.floor(sight.z));
    }

    /** It turns its head towards this point, at its turn a tick. */
    private void lookAt(Sight sight, Controls controls, double[] point) {
        double dx = point[0] - sight.x;
        double dy = point[1] - (sight.y + EYES);
        double dz = point[2] - sight.z;
        controls.yaw = turn(sight.yaw, yawTo(dx, dz), skill.turn);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(Math.sqrt(dx * dx + dz * dz), 1e-6)));
        controls.pitch = (float) clamp(turn(sight.pitch, pitch, skill.turn), -90, 90);
    }

    /** The middle of this side of the block {x, y, z}. */
    static double[] faceCentre(int[] block, int side) {
        return new double[] {block[0] + 0.5 + FACES[side][0] * 0.5, block[1] + 0.5 + FACES[side][1] * 0.5,
            block[2] + 0.5 + FACES[side][2] * 0.5};
    }

    /** The side of the block it sees best: the one towards its eyes along the way they are furthest from it. */
    static int sideTowards(Sight sight, int[] block) {
        double dx = sight.x - (block[0] + 0.5);
        double dy = sight.y + EYES - (block[1] + 0.5);
        double dz = sight.z - (block[2] + 0.5);
        if (Math.abs(dy) > Math.max(Math.abs(dx), Math.abs(dz))) {
            return dy > 0 ? UP : DOWN;
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? 5 : 4;
        }
        return dz > 0 ? 3 : 2;
    }

    /**
     * Where the look of its eyes (yaw, pitch) enters the block {x, y, z} within a client's reach:
     * {side, x, y, z} of that point; null if it misses it.
     */
    static double[] rayHit(Sight sight, float yaw, float pitch, int bx, int by, int bz) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double[] from = {sight.x, sight.y + EYES, sight.z};
        double[] dir = {-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad)};
        double[] low = {bx, by, bz};
        double enter = 0;
        double leave = REACH_BLOCKS;
        int side = -1;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dir[axis]) < 1e-9) {
                if (from[axis] < low[axis] || from[axis] > low[axis] + 1) {
                    return null;
                }
                continue;
            }
            double t1 = (low[axis] - from[axis]) / dir[axis];
            double t2 = (low[axis] + 1 - from[axis]) / dir[axis];
            // entering through the low side (moving up the axis) or the high side
            int nearSide = dir[axis] > 0 ? 2 * axisFace(axis) : 2 * axisFace(axis) + 1;
            double near = Math.min(t1, t2);
            double far = Math.max(t1, t2);
            if (near > enter) {
                enter = near;
                side = nearSide;
            }
            leave = Math.min(leave, far);
            if (enter > leave) {
                return null;
            }
        }
        if (side < 0) {
            // its eyes inside the block
            return null;
        }
        return new double[] {side, from[0] + dir[0] * enter, from[1] + dir[1] * enter, from[2] + dir[2] * enter};
    }

    /**
     * The pair of sides of an axis in {@link #FACES}, as its first index halved: y is 0 (down, up),
     * z 1 (north, south), x 2 (west, east).
     */
    private static int axisFace(int axis) {
        return axis == 0 ? 2 : axis == 1 ? 0 : 1;
    }

    /** From its eyes to the nearest point of the block {x, y, z}. */
    static double nearest(Sight sight, int[] block) {
        double ex = sight.x;
        double ey = sight.y + EYES;
        double ez = sight.z;
        return distance(ex, ey, ez, clamp(ex, block[0], block[0] + 1), clamp(ey, block[1], block[1] + 1),
                clamp(ez, block[2], block[2] + 1));
    }

    private static double distance(double ax, double ay, double az, double bx, double by, double bz) {
        double x = ax - bx;
        double y = ay - by;
        double z = az - bz;
        return Math.sqrt(x * x + y * y + z * z);
    }

    // --- The block edit -------------------------------------------------------------------------

    /** A new round, or no game: it forgets the wall and who hit last. */
    private void forgetEdit() {
        wall = null;
        lastHit = 0;
        hadInHits = false;
        inHitsTicks = 0;
        lastX = Double.NaN;
    }

    /** Its tick in the block edit; believed: where it believes the other is. */
    private Controls edit(Sight sight, Controls controls, double[] believed) {
        if (!Double.isNaN(lastX) && Math.hypot(sight.x - lastX, sight.z - lastZ) > MOVED) {
            // moved to its start: a new round, the wall as built again
            forgetEdit();
        }
        lastX = sight.x;
        lastZ = sight.z;
        lookAt(sight, controls, new double[] {believed[0], believed[1] + CHEST, believed[2]});
        if (wall == null && sight.breakable != null && sight.ground != null) {
            findWall(sight);
        }
        if (wall == null) {
            // no wall in sight: it stands, and hits nothing
            return controls;
        }
        List<int[]> holes = holes(sight.ground);
        if (holes.isEmpty()) {
            hadInHits = false;
        } else if (sight.knocked) {
            // the other hit it through a hole: their point (the wall whole, a knock is the game's:
            // its speed set to nothing at the start of a round)
            lastHit = -1;
        }
        double point = hitPoint(sight, null);
        boolean inHits = !Double.isNaN(point);
        inHitsTicks = inHits ? inHitsTicks + 1 : 0;
        hadInHits |= inHits;

        // 1. its point: it closes the wall
        if (lastHit == 1 && !holes.isEmpty() && close(sight, controls, holes)) {
            return controls;
        }
        // 2. the other in its hits through a hole: it hits them, after its reaction time
        if (inHits && lastHit != 1) {
            hit(sight, controls, believed, point);
            return controls;
        }
        // 3. the other out of its hits again without its hit: it closes all the same (not their point)
        if (hadInHits && lastHit == 0 && !holes.isEmpty() && close(sight, controls, holes)) {
            return controls;
        }
        int head = (int) Math.floor(sight.y + 0.01) + 1;
        int lat = wallAxis == 0 ? 2 : 0;
        double[] other = {sight.tx, sight.ty, sight.tz};
        // 4. a hole to hit through, at the height of its head: it stands in front of it, for the other
        int[] usable = null;
        for (int[] hole : holes) {
            if (hole[1] == head && (usable == null
                    || Math.abs(hole[lat] + 0.5 - other[lat]) < Math.abs(usable[lat] + 0.5 - other[lat]))) {
                usable = hole;
            }
        }
        if (usable != null) {
            standBefore(sight, controls, usable[lat]);
            return controls;
        }
        // 5. none: it opens one, the block at the height of its head in front of the other
        int lane = (int) Math.max(wallLow, Math.min(wallHigh, Math.floor(other[lat])));
        int[] block = new int[3];
        block[wallAxis] = wallAt;
        block[lat] = lane;
        block[1] = Math.max(wallBottom, Math.min(wallTop, head));
        if (!standBefore(sight, controls, lane) || !sight.blocks) {
            return controls;
        }
        int tool = sight.breakable.tool(block[0], block[1], block[2]);
        if (tool < 0 || nearest(sight, block) > REACH_BLOCKS || Double.isNaN(hitPoint(sight, block))) {
            // out of its hits even through it: it waits for the other at the wall
            return controls;
        }
        hadInHits = true;
        controls.slot = sight.held == tool ? -1 : tool;
        lookAt(sight, controls, faceCentre(block, sideTowards(sight, block)));
        double[] hit = rayHit(sight, controls.yaw, controls.pitch, block[0], block[1], block[2]);
        if (hit != null) {
            controls.dig = new int[] {block[0], block[1], block[2], (int) hit[0]};
        }
        return controls;
    }

    /**
     * The wall of the block edit: the blocks it may break around it (the wool of the wall, the
     * only blocks the mode lets break), in the plane holding most of them; the holes between them
     * are blocks of the wall too.
     */
    private void findWall(Sight sight) {
        int bx = (int) Math.floor(sight.x);
        int by = (int) Math.floor(sight.y + 0.01);
        int bz = (int) Math.floor(sight.z);
        List<int[]> found = new ArrayList<>();
        Map<Integer, Integer> atX = new HashMap<>();
        Map<Integer, Integer> atZ = new HashMap<>();
        for (int x = bx - WALL_SEARCH; x <= bx + WALL_SEARCH; x++) {
            for (int z = bz - WALL_SEARCH; z <= bz + WALL_SEARCH; z++) {
                for (int y = by - 1; y <= by + 3; y++) {
                    if (sight.breakable.tool(x, y, z) >= 0) {
                        found.add(new int[] {x, y, z});
                        atX.merge(x, 1, Integer::sum);
                        atZ.merge(z, 1, Integer::sum);
                    }
                }
            }
        }
        int[] bestX = most(atX);
        int[] bestZ = most(atZ);
        if (Math.max(bestX[1], bestZ[1]) < 3) {
            return;
        }
        wallAxis = bestX[1] >= bestZ[1] ? 0 : 2;
        wallAt = wallAxis == 0 ? bestX[0] : bestZ[0];
        int lat = wallAxis == 0 ? 2 : 0;
        wallLow = Integer.MAX_VALUE;
        wallHigh = Integer.MIN_VALUE;
        wallBottom = Integer.MAX_VALUE;
        wallTop = Integer.MIN_VALUE;
        for (int[] block : found) {
            if (block[wallAxis] == wallAt) {
                wallLow = Math.min(wallLow, block[lat]);
                wallHigh = Math.max(wallHigh, block[lat]);
                wallBottom = Math.min(wallBottom, block[1]);
                wallTop = Math.max(wallTop, block[1]);
            }
        }
        int[][] blocks = new int[(wallHigh - wallLow + 1) * (wallTop - wallBottom + 1)][];
        int n = 0;
        for (int along = wallLow; along <= wallHigh; along++) {
            for (int y = wallBottom; y <= wallTop; y++) {
                int[] block = new int[3];
                block[wallAxis] = wallAt;
                block[lat] = along;
                block[1] = y;
                blocks[n++] = block;
            }
        }
        wall = blocks;
    }

    /** The key counted most, {key, count}; {0, 0} for none. */
    private static int[] most(Map<Integer, Integer> counts) {
        int[] best = {0, 0};
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > best[1]) {
                best = new int[] {entry.getKey(), entry.getValue()};
            }
        }
        return best;
    }

    /** The blocks of the wall missing now. */
    private List<int[]> holes(Ground ground) {
        List<int[]> holes = new ArrayList<>();
        for (int[] block : wall) {
            if (!ground.solid(block[0], block[1], block[2])) {
                holes.add(block);
            }
        }
        return holes;
    }

    /**
     * The other in its hits through the wall: the height above their feet of a point of theirs
     * within its reach with nothing solid between its eyes and it (open: a block counted as gone,
     * or null); NaN if none.
     */
    double hitPoint(Sight sight, int[] open) {
        double ex = sight.x;
        double ey = sight.y + EYES;
        double ez = sight.z;
        if (reach(ex, ey, ez, sight.tx, sight.ty, sight.tz) > skill.reach) {
            return Double.NaN;
        }
        for (double height : HIT_POINTS) {
            if (clear(sight.ground, ex, ey, ez, sight.tx, sight.ty + height, sight.tz, open)) {
                return height;
            }
        }
        return Double.NaN;
    }

    /** True if no solid block is between these two points (open: a block counted as gone, or null). */
    static boolean clear(Ground ground, double ax, double ay, double az, double bx, double by, double bz, int[] open) {
        double length = distance(ax, ay, az, bx, by, bz);
        int steps = Math.max(1, (int) Math.ceil(length / 0.05));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.floor(ax + (bx - ax) * t);
            int y = (int) Math.floor(ay + (by - ay) * t);
            int z = (int) Math.floor(az + (bz - az) * t);
            if (open != null && x == open[0] && y == open[1] && z == open[2]) {
                continue;
            }
            if (ground.solid(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * It hits the other through the hole, its sword in hand, its look on the point it sees of them
     * (height: above their feet), after its reaction time: a hit lands when its look meets their
     * box within its reach, with nothing solid before.
     */
    private void hit(Sight sight, Controls controls, double[] believed, double height) {
        if (sight.sword >= 0 && sight.held != sight.sword) {
            controls.slot = sight.sword;
        }
        lookAt(sight, controls, new double[] {believed[0], believed[1] + height, believed[2]});
        int now = clicks();
        if (now <= 0) {
            return;
        }
        controls.swings = now;
        if (inHitsTicks > skill.reaction && looksAtTheOther(sight, controls.yaw, controls.pitch)
                && random.nextDouble() < skill.accuracy) {
            controls.hit = true;
            lastHit = 1;
        }
    }

    /** True if its look (yaw, pitch) meets the box of the other within its reach, nothing solid before. */
    boolean looksAtTheOther(Sight sight, float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double[] from = {sight.x, sight.y + EYES, sight.z};
        double[] dir = {-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad)};
        double[] low = {sight.tx - 0.4, sight.ty - 0.1, sight.tz - 0.4};
        double[] high = {sight.tx + 0.4, sight.ty + 1.9, sight.tz + 0.4};
        double enter = 0;
        double leave = skill.reach;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dir[axis]) < 1e-9) {
                if (from[axis] < low[axis] || from[axis] > high[axis]) {
                    return false;
                }
                continue;
            }
            double t1 = (low[axis] - from[axis]) / dir[axis];
            double t2 = (high[axis] - from[axis]) / dir[axis];
            enter = Math.max(enter, Math.min(t1, t2));
            leave = Math.min(leave, Math.max(t1, t2));
            if (enter > leave) {
                return false;
            }
        }
        return clear(sight.ground, from[0], from[1], from[2], from[0] + dir[0] * enter, from[1] + dir[1] * enter,
                from[2] + dir[2] * enter, null);
    }

    /**
     * It puts a block of wool in a hole of the wall: against the side of a block beside the hole
     * (the wall around it, the ground under it, the beam over it), the nearest to its eyes within a
     * client's reach, its look on that side. Out of reach of every hole, it goes in front of the
     * nearest. True while it does either.
     */
    private boolean close(Sight sight, Controls controls, List<int[]> holes) {
        if (sight.block < 0 || !sight.blocks) {
            return false;
        }
        double ex = sight.x;
        double ey = sight.y + EYES;
        double ez = sight.z;
        int[] against = null;
        double best = Double.MAX_VALUE;
        int[] nearestHole = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int[] hole : holes) {
            double distance = nearest(sight, hole);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestHole = hole;
            }
            for (int face = 0; face < FACES.length; face++) {
                int nx = hole[0] + FACES[face][0];
                int ny = hole[1] + FACES[face][1];
                int nz = hole[2] + FACES[face][2];
                if (!sight.ground.solid(nx, ny, nz)) {
                    continue;
                }
                // the side of that block towards the hole: the other way
                int side = face ^ 1;
                double[] centre = faceCentre(new int[] {nx, ny, nz}, side);
                double away = distance(ex, ey, ez, centre[0], centre[1], centre[2]);
                if (away <= REACH_BLOCKS && away < best) {
                    best = away;
                    against = new int[] {nx, ny, nz, side};
                }
            }
        }
        if (against == null) {
            if (nearestHole == null) {
                return false;
            }
            standBefore(sight, controls, nearestHole[wallAxis == 0 ? 2 : 0]);
            return true;
        }
        controls.slot = sight.held == sight.block ? -1 : sight.block;
        lookAt(sight, controls, faceCentre(against, against[3]));
        double[] hit = rayHit(sight, controls.yaw, controls.pitch, against[0], against[1], against[2]);
        if (hit != null && (int) hit[0] == against[3] && placeWait == 0) {
            controls.place = against;
            controls.cursor = new float[] {(float) (hit[1] - against[0]), (float) (hit[2] - against[1]),
                (float) (hit[3] - against[2])};
            placeWait = (int) Math.ceil(20 / Math.max(0.1, skill.blocksPerSecond));
            // its point made if its hit was the last; either way, nobody's now as far as it knows
            lastHit = 0;
            hadInHits = false;
        }
        return true;
    }

    /**
     * It walks to its place against the wall, on its side, in front of the blocks of the wall at
     * along (along the wall), and stands there. True once there.
     */
    private boolean standBefore(Sight sight, Controls controls, int along) {
        int lat = wallAxis == 0 ? 2 : 0;
        double[] me = {sight.x, sight.y, sight.z};
        int side = me[wallAxis] > wallAt + 0.5 ? 1 : -1;
        double[] spot = new double[3];
        spot[wallAxis] = wallAt + 0.5 + side * AT_WALL;
        spot[lat] = along + 0.5;
        double across = spot[wallAxis] - me[wallAxis];
        double sideways = spot[lat] - me[lat];
        if (Math.abs(across) <= 0.15 && Math.abs(sideways) <= 0.2) {
            return true;
        }
        double dx = spot[0] - me[0];
        double dz = spot[2] - me[2];
        double length = Math.sqrt(dx * dx + dz * dz);
        double speed = length > 0.5 ? 1 : Math.max(0.3, length * 2);
        double radians = Math.toRadians(controls.yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        // the keys that take it that way, facing where it looks (the inverse of way())
        controls.forward = (-dx * sin + dz * cos) / length * speed;
        controls.strafe = (dx * cos + dz * sin) / length * speed;
        return false;
    }

    // --- The other, as it saw them ------------------------------------------------------------

    private void see(double x, double y, double z) {
        seenAt = (seenAt + 1) % MEMORY;
        seen[seenAt][0] = x;
        seen[seenAt][1] = y;
        seen[seenAt][2] = z;
        seenCount = Math.min(MEMORY, seenCount + 1);
    }

    /**
     * Where the other was its ping ago: between where they were at the ticks around then (40 ms,
     * 0.8 of a tick, is mostly where they were the tick before).
     */
    double[] lagged() {
        double back = skill.ping / 50.0;
        int whole = (int) Math.floor(back);
        double part = back - whole;
        double[] newer = seenBack(whole);
        double[] older = seenBack(whole + 1);
        return new double[] {newer[0] + (older[0] - newer[0]) * part, newer[1] + (older[1] - newer[1]) * part,
            newer[2] + (older[2] - newer[2]) * part};
    }

    /** Where the other was this many ticks ago, or the furthest back it saw them. */
    private double[] seenBack(int ticks) {
        int back = Math.max(0, Math.min(ticks, seenCount - 1));
        return seen[((seenAt - back) % MEMORY + MEMORY) % MEMORY];
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
     * no forward, or no strafe, or the same keys turned a little to one side ({@link #TURNS}), or it
     * stands. The turn is for a path one block wide (the stairway of the Top Bridge) when it stands
     * near its side and the other is a little to that side: every way at the other goes off the
     * path, the one turned back towards its middle does not, and the next ticks it walks at them.
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
        for (double turn : TURNS) {
            for (int way = 1; way >= -1; way -= 2) {
                double radians = Math.toRadians(turn * way);
                double cos = Math.cos(radians);
                double sin = Math.sin(radians);
                double forward = controls.forward * cos - controls.strafe * sin;
                double strafe = controls.forward * sin + controls.strafe * cos;
                if (safe(sight, controls.yaw, forward, strafe)) {
                    controls.forward = forward;
                    controls.strafe = strafe;
                    return;
                }
            }
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
