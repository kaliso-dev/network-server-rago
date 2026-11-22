package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Timed bridge: from a pad to the green one over 16, 32 or 64 blocks of void, as fast as
 * possible, with any technique (speed bridge, ninja, breezily, god bridge, telly...). Straight or
 * diagonal, flat or uphill (the green pad higher by a quarter of the distance), or with a turn
 * (Block Aim Practice, plan step 7: a turn to the right halfway, flat): the fifteen courses stand
 * side by side in the arena, each with its own record board. The time runs from the first block
 * to the first step on the green pad; the blocks go between the two pads only.
 */
final class BridgeTraining extends Training {

    /** The distances between the two pads, in blocks: a variant each. */
    static final int[] DISTANCES = {16, 32, 64};
    static final Variant[] VARIANTS = variants();
    /** The end of the board of a diagonal course, then of an uphill one: "bridge-chrono.32.diagonal.up". */
    static final String DIAGONAL = "diagonal";
    static final String UP = "up";
    /** The end of the board of a course with a turn: "bridge-chrono.32.turn". */
    static final String TURN = "turn";
    /**
     * Where the courses with a turn start (the front row of their start pad, from the centre), for
     * 16, 32 and 64 blocks: they face west, then turn right (north) halfway, in the room the other
     * courses leave within 64 blocks of the centre. The turn and where it comes are to confirm.
     */
    static final int[][] TURN_STARTS = {{16, 26}, {-12, 44}, {31, 41}};
    /** A pad is 5 by 5. */
    static final int PAD = 5;
    /** The blocks go this far at most on each side of a straight course (of a diagonal one: one more). */
    static final int SIDE = 3;
    /** And this far under the start pad, and over the green one. */
    static final int BELOW = 3;
    static final int ABOVE = 3;
    /** Under this height (from the floor of the arena) a player has fallen. */
    static final int FALL = -6;
    /** An attempt may take this long for each block of the distance. */
    static final long LIMIT_MS_PER_BLOCK = 3000L;
    /** Ticks from the green pad back to the start. */
    static final long AFTER_FINISH = 30L;

    /**
     * One course in the arena. A straight one runs along z from the middle of the front row of its
     * start pad (x, z); a diagonal one runs along x and z at once from the front corner (x, z) of its
     * start pad, which is behind and on the left of that corner; one with a turn runs west from the
     * middle of the front row (x, z) of its start pad for reach blocks, then north to the green pad.
     */
    static final class Course {
        final int distance;
        final boolean diagonal;
        final boolean turn;
        /** How much higher the top of the green pad is than the start's. */
        final int rise;
        final int x;
        final int z;
        /**
         * Straight: the first row of the green pad, from z; diagonal: its corner, from x and from z;
         * with a turn: the blocks to the turn, the rest of the distance after it.
         */
        final int reach;

        Course(int distance, boolean diagonal, int rise, int x, int z, int reach) {
            this(distance, diagonal, false, rise, x, z, reach);
        }

        Course(int distance, boolean diagonal, boolean turn, int rise, int x, int z, int reach) {
            this.distance = distance;
            this.diagonal = diagonal;
            this.turn = turn;
            this.rise = rise;
            this.x = x;
            this.z = z;
            this.reach = reach;
        }

        /** With a turn: the blocks after it, to the green pad. */
        int afterTurn() {
            return distance - reach;
        }

        /** With a turn: how far ahead (west) of the start pad's front row, and how far to the right (north). */
        private int ahead(int bx) {
            return x - bx;
        }

        private int right(int bz) {
            return z - bz;
        }

        /** True if the block (bx, by, bz) of the arena is between the two pads, where the blocks of the bridge go. */
        boolean between(int bx, int by, int bz) {
            if (by < -BELOW || by > rise + ABOVE) {
                return false;
            }
            int dx = bx - x;
            int dz = bz - z;
            if (turn) {
                int u = ahead(bx);
                int v = right(bz);
                // the first leg to beyond the turn, then the second one from beside the first to the green pad
                return Math.abs(v) <= SIDE && u >= 1 && u <= reach + SIDE
                        || Math.abs(u - reach) <= SIDE && v >= -SIDE && v <= afterTurn();
            }
            if (!diagonal) {
                return Math.abs(dx) <= SIDE && dz >= 1 && dz < reach;
            }
            return Math.abs(dx - dz) <= SIDE + 1 && !(dx <= 0 && dz <= 0) && !(dx >= reach && dz >= reach);
        }

        /** True if (bx, bz) is under the start pad. */
        boolean onStart(int bx, int bz) {
            int dx = bx - x;
            int dz = bz - z;
            if (turn) {
                return Math.abs(right(bz)) <= PAD / 2 && ahead(bx) > -PAD && ahead(bx) <= 0;
            }
            if (!diagonal) {
                return Math.abs(dx) <= PAD / 2 && dz > -PAD && dz <= 0;
            }
            return dx > -PAD && dx <= 0 && dz > -PAD && dz <= 0;
        }

        /** True if (bx, bz) is under the green pad. */
        boolean onFinish(int bx, int bz) {
            int dx = bx - x;
            int dz = bz - z;
            if (turn) {
                return Math.abs(ahead(bx) - reach) <= PAD / 2 && right(bz) > afterTurn() && right(bz) <= afterTurn() + PAD;
            }
            if (!diagonal) {
                return Math.abs(dx) <= PAD / 2 && dz >= reach && dz < reach + PAD;
            }
            return dx >= reach && dx < reach + PAD && dz >= reach && dz < reach + PAD;
        }

        /** True if the block (bx, by, bz) of the arena is the top of the green pad: the finish. */
        boolean finish(int bx, int by, int bz) {
            return by == rise && onFinish(bx, bz);
        }

        /** True for the first row (two, diagonally) of the green pad, in gold to be seen from afar. */
        boolean line(int bx, int bz) {
            if (turn) {
                return onFinish(bx, bz) && right(bz) == afterTurn() + 1;
            }
            return onFinish(bx, bz) && (diagonal ? bx - x == reach || bz - z == reach : bz - z == reach);
        }

        /**
         * The start: on the start pad, a step and a half back from the void, facing the green pad
         * (with a turn: facing the first leg, west).
         */
        double[] start() {
            if (turn) {
                return new double[] {x + 2.5, 1, z + 0.5, 90};
            }
            return diagonal ? new double[] {x - 0.5, 1, z - 0.5, -45} : new double[] {x + 0.5, 1, z - 1.5, 0};
        }

        /** The ms an attempt may take. */
        long limit() {
            return LIMIT_MS_PER_BLOCK * distance;
        }
    }

    private final Setting diagonal;
    private final Setting uphill;
    private final Setting turn;
    private long started;
    /** The time of the attempt, once on the green pad. */
    private long finishedIn;
    private int blocks;
    private boolean done;

    BridgeTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams, VARIANTS);
        this.diagonal = toggle("Diagonal", false);
        this.uphill = toggle("Uphill", false);
        this.turn = toggle("Turn", false);
    }

    static Variant[] variants() {
        Variant[] variants = new Variant[DISTANCES.length];
        for (int i = 0; i < DISTANCES.length; i++) {
            variants[i] = new Variant(String.valueOf(DISTANCES[i]), DISTANCES[i] + " blocks", true, "");
        }
        return variants;
    }

    /**
     * The course of a distance (its index), straight or diagonal, flat or uphill. The straight ones
     * stand in a row on the west, along z, the longest in the middle; the diagonal ones on the east,
     * each on its own band (x - z) of the diagonal, the 64 blocks in the middle: none is more than 64
     * blocks from the centre, where the spectators of the training see it.
     */
    static Course course(int index, boolean diagonal, boolean uphill) {
        return course(index, diagonal, uphill, false);
    }

    /** The course of a distance; with a turn, flat and not diagonal whatever the two others say. */
    static Course course(int index, boolean diagonal, boolean uphill, boolean turn) {
        int distance = DISTANCES[index];
        if (turn) {
            return new Course(distance, false, true, 0, TURN_STARTS[index][0], TURN_STARTS[index][1], distance / 2);
        }
        int rise = uphill ? distance / 4 : 0;
        // the courses from the middle out: 64, 64 uphill, 32, 32 uphill, 16, 16 uphill
        int out = DISTANCES.length - 1 - index;
        if (!diagonal) {
            int lane = 2 * out + (uphill ? 1 : 0);
            return new Course(distance, false, rise, -10 - 9 * lane, -(distance + 1) / 2, distance + 1);
        }
        // the void between the two corners as long as the distance: (reach - 1) blocks of diagonal
        int reach = (int) Math.round(distance / Math.sqrt(2)) + 1;
        int band = 36 + (uphill ? -1 : 1) * (6 + 12 * out);
        return new Course(distance, true, rise, 7, 7 - band, reach);
    }

    /** "bridge-chrono.32", "bridge-chrono.64.diagonal.up": the board of a course. */
    static String boardOf(String modeId, Variant v, boolean diagonal, boolean uphill) {
        return boardOf(modeId, v, diagonal, uphill, false);
    }

    /** The board of a course, "bridge-chrono.32.turn" with a turn. */
    static String boardOf(String modeId, Variant v, boolean diagonal, boolean uphill, boolean turn) {
        return modeId + "." + v.id + suffix(diagonal, uphill, turn);
    }

    /** What the settings add to the board of a distance: "", ".diagonal", ".up", ".diagonal.up". */
    static String suffix(boolean diagonal, boolean uphill) {
        return suffix(diagonal, uphill, false);
    }

    /** The same, ".turn" with a turn (which is flat and not diagonal). */
    static String suffix(boolean diagonal, boolean uphill, boolean turn) {
        if (turn) {
            return "." + TURN;
        }
        return (diagonal ? "." + DIAGONAL : "") + (uphill ? "." + UP : "");
    }

    /** "32 blocks", "64 blocks, diagonal, uphill": the name of a course's board. */
    static String label(Variant v, boolean diagonal, boolean uphill) {
        return label(v, diagonal, uphill, false);
    }

    /** The same, "32 blocks, with a turn" with a turn. */
    static String label(Variant v, boolean diagonal, boolean uphill, boolean turn) {
        if (turn) {
            return v.label + ", with a turn";
        }
        return v.label + (diagonal ? ", diagonal" : "") + (uphill ? ", uphill" : "");
    }

    /** {the distance's index, diagonal (1 or 0), uphill (1 or 0), turn (1 or 0)} of a board of the mode, or null. */
    static int[] parse(String modeId, String board) {
        for (int i = 0; i < VARIANTS.length; i++) {
            if (boardOf(modeId, VARIANTS[i], false, false, true).equals(board)) {
                return new int[] {i, 0, 0, 1};
            }
            for (int diagonal = 0; diagonal <= 1; diagonal++) {
                for (int uphill = 0; uphill <= 1; uphill++) {
                    if (boardOf(modeId, VARIANTS[i], diagonal == 1, uphill == 1).equals(board)) {
                        return new int[] {i, diagonal, uphill, 0};
                    }
                }
            }
        }
        return null;
    }

    private Course course() {
        return course(variant, diagonal.on(), uphill.on(), turn.on());
    }

    /** The board of the records: each course has its own; straight and flat, the distance's alone. */
    @Override
    String boardOf(Variant v) {
        return boardOf(mode.id(), v, diagonal.on(), uphill.on(), turn.on());
    }

    /** The challenge of the day is a distance, straight and flat. */
    @Override
    void forChallenge() {
        diagonal.value = 0;
        uphill.value = 0;
        turn.value = 0;
    }

    @Override
    boolean chooseBoard(String board) {
        int[] course = parse(mode.id(), board);
        if (course == null) {
            return false;
        }
        variant = course[0];
        diagonal.value = course[1];
        uphill.value = course[2];
        turn.value = course[3];
        return true;
    }

    /** A course with a turn is flat and not diagonal: one setting turned on turns the others off. */
    @Override
    void stepped(Setting setting) {
        if (setting == turn && turn.on()) {
            diagonal.value = 0;
            uphill.value = 0;
        } else if ((setting == diagonal || setting == uphill) && setting.on()) {
            turn.value = 0;
        }
    }

    @Override
    String describe() {
        Course course = course();
        if (course.turn) {
            return "bridge " + course.distance + " blocks to the green pad, turning right after " + course.reach
                    + ", with any technique. The time runs from your first block.";
        }
        return "bridge " + course.distance + " blocks" + (course.diagonal ? " diagonally" : "") + " to the green pad"
                + (course.rise > 0 ? ", " + course.rise + " blocks higher" : "")
                + ", with any technique. The time runs from your first block.";
    }

    @Override
    void build() {
        for (int i = 0; i < DISTANCES.length; i++) {
            for (boolean diagonalCourse : new boolean[] {false, true}) {
                for (boolean uphillCourse : new boolean[] {false, true}) {
                    build(arena, course(i, diagonalCourse, uphillCourse));
                }
            }
            build(arena, course(i, false, false, true));
        }
    }

    /** The two pads of a course: white clay at the start, lime with a gold line at the finish. */
    static void build(Arena arena, Course course) {
        int startX = course.diagonal ? course.x - PAD + 1 : course.x - PAD / 2;
        int finishX = course.diagonal ? course.x + course.reach : startX;
        int startZ = course.z - PAD + 1;
        int finishZ = course.z + course.reach;
        if (course.turn) {
            // west of the start pad's front row, then north of the turn
            startX = course.x;
            startZ = course.z - PAD / 2;
            finishX = course.x - course.reach - PAD / 2;
            finishZ = course.z - course.afterTurn() - PAD;
        }
        pad(arena, startX, startZ, 0, Shapes.WHITE, Shapes.LIGHT_GRAY);
        pad(arena, finishX, finishZ, course.rise, Shapes.LIME, Shapes.GREEN);
        for (int dx = 0; dx < PAD; dx++) {
            for (int dz = 0; dz < PAD; dz++) {
                if (course.line(finishX + dx, finishZ + dz)) {
                    arena.build(finishX + dx, course.rise, finishZ + dz, Material.GOLD_BLOCK, 0);
                }
            }
        }
    }

    /** A pad of 5 by 5 from (x, z), its top at this height, two smaller layers under it. */
    private static void pad(Arena arena, int x, int z, int top, int topData, int underData) {
        Shapes.floor(arena, x, z, x + PAD - 1, z + PAD - 1, top, Material.STAINED_CLAY, topData);
        Shapes.floor(arena, x + 1, z + 1, x + PAD - 2, z + PAD - 2, top - 1, Material.STAINED_CLAY, underData);
        arena.build(x + PAD / 2, top - 2, z + PAD / 2, Material.STAINED_CLAY, underData);
    }

    @Override
    Location spawnOf(UUID player) {
        double[] start = course().start();
        return arena.at(start[0], start[1], start[2], (float) start[3]);
    }

    @Override
    double fallY() {
        return arena.floorY() + FALL;
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {stack(Material.WOOL), stack(Material.SANDSTONE)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        started = 0;
        finishedIn = 0;
        blocks = 0;
        done = false;
    }

    /** The blocks go between the two pads of the course only. */
    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return !done && course().between(block.getX() - arena.centreX(), block.getY() - arena.floorY(), block.getZ() - arena.centreZ());
    }

    /** The first block starts the time. */
    @Override
    void placed(Player player, Block block) {
        if (started == 0) {
            started = now();
        }
        blocks++;
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || done || started == 0) {
            return;
        }
        long elapsed = now() - started;
        if (onGround(player) && onFinish(player)) {
            finished(player, elapsed);
            return;
        }
        Course course = course();
        if (elapsed > course.limit()) {
            failed("More than " + course.limit() / 1000 + " s on " + course.distance + " blocks: start again.");
            return;
        }
        refresh();
    }

    /** True if the player stands on the green pad (a block of the course, not one placed). */
    private boolean onFinish(Player player) {
        Block under = player.getLocation().subtract(0, 0.1, 0).getBlock();
        return !arena.isPlaced(under) && course().finish(under.getX() - arena.centreX(), under.getY() - arena.floorY(),
                under.getZ() - arena.centreZ());
    }

    private void finished(Player player, long elapsed) {
        done = true;
        finishedIn = elapsed;
        player.sendMessage(ChatColor.YELLOW + "You bridged " + course().distance + " blocks in " + ChatColor.AQUA + time(elapsed)
                + " s" + ChatColor.YELLOW + ": " + ChatColor.AQUA + blocks + ChatColor.YELLOW + (blocks == 1 ? " block, " : " blocks, ")
                + ChatColor.AQUA + perSecond(blocks, elapsed) + ChatColor.YELLOW + " a second.");
        title(player, ChatColor.GREEN + "" + ChatColor.BOLD + "FINISH!", ChatColor.GRAY + time(elapsed) + " s");
        sound(player, Sound.FIREWORK_LAUNCH, 1f);
        score(elapsed);
        again(AFTER_FINISH);
    }

    /** "4.2": the blocks placed a second. */
    static String perSecond(int blocks, long millis) {
        return String.format(Locale.ROOT, "%.1f", blocks * 1000.0 / Math.max(1, millis));
    }

    @Override
    void trainingLines(List<String> lines) {
        long elapsed = done ? finishedIn : started == 0 ? 0 : now() - started;
        Course course = course();
        lines.add(ChatColor.WHITE + "Course: " + ChatColor.GREEN + (course.turn ? "With a turn"
                : (course.diagonal ? "Diagonal" : "Straight") + ", " + (course.rise > 0 ? "uphill" : "flat")));
        lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + time(elapsed) + " s");
        lines.add(ChatColor.WHITE + "Blocks: " + ChatColor.GREEN + blocks);
    }
}
