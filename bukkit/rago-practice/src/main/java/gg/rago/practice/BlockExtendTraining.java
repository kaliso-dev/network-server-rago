package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Block extend, as in a real bed fight, on the owner's map (BlockClutchExtend): the player stands
 * at an exact place of a bridge, frozen (they can neither move nor turn) until a hit, and saves
 * themselves with their wool: they must turn first, as in a real fight, and the hit always comes
 * along the same line. Three levels: gold, a big high hit from the front, straight back, and they
 * clutch; diamond, a sprint hit from the right, they turn to the bridge and extend to its diamond
 * blocks; emerald, from the emerald block at the end of the bridge, a long and low hit from the
 * front, they clutch on the emerald blocks below. The gold, diamond and emerald markers of the map
 * are wool in the game. Each save adds one
 * to the streak of the level; a fall ends it. The kit has ladders too, for the ladder clutches (a
 * setting takes them out).
 */
final class BlockExtendTraining extends Training {

    /** Where a hit comes from, seen by the player at the start: it throws them the other way. */
    enum From {
        FRONT, RIGHT, LEFT, BACK;

        /** The word of config.yml: devant, droite, gauche, derriere. Null if it is none of them. */
        static From of(String word) {
            switch (word.trim().toLowerCase(Locale.ROOT)) {
                case "devant":
                case "front":
                    return FRONT;
                case "droite":
                case "right":
                    return RIGHT;
                case "gauche":
                case "left":
                    return LEFT;
                case "derriere":
                case "derrière":
                case "back":
                    return BACK;
                default:
                    return null;
            }
        }
    }

    /** One level of the course. Offsets are from the origin of the map (the centre of the zone). */
    static final class Level {
        final String id;
        final String label;
        /** x, y, z of the feet, yaw, pitch: exactly where the player starts. */
        final double[] start;
        /** The block to save oneself on (its top is the landing). */
        final int[] target;
        /** The way the hit throws, on x and z, length 1, as the player looks at the start. */
        final double[] way;
        /** Where the hit comes from, or null when it throws towards a point. */
        final From from;
        final double horizontal;
        final double vertical;

        Level(String id, String label, double[] start, int[] target, double[] way, From from, double horizontal,
                double vertical) {
            this.id = id;
            this.label = label;
            this.start = start;
            this.target = target;
            this.way = way;
            this.from = from;
            this.horizontal = horizontal;
            this.vertical = vertical;
        }
    }

    /** The course: the copy of the map (or null: plain blocks at the places) and its levels. */
    static final class Course {
        final MapCopy copy;
        final Level[] levels;

        Course(MapCopy copy, Level[] levels) {
            this.copy = copy;
            this.levels = levels;
        }
    }

    /** The ids of the levels, as config.yml names them, and their names in the game. */
    static final String[] IDS = {"or", "diamant", "emeraude"};
    static final String[] LABELS = {"Gold: big hit, clutch", "Diamond: sprint hit from the right, extend",
        "Emerald: long low hit, clutch below"};
    /** The block ids of the markers: gold, diamond and emerald blocks. */
    static final int[] MARKERS = {41, 57, 133};
    /** Ticks on the ground after the hit that make a save: half a second. */
    static final int SAVED_TICKS = 10;
    /** Ticks from a save back to the start. */
    static final long AFTER_SAVE = 4L;
    /** A landing this many blocks under the target is not a save. */
    private static final double TOO_LOW = 3.0;
    /** Before the hit: at least this many ticks, and up to this many more at random. */
    static final int WAIT_TICKS = 10;
    static final int WAIT_RANDOM = 10;
    /** The blue wool of the map's kit. */
    private static final int WOOL_COLOUR = Shapes.BLUE;

    private final Course course;
    private final Setting ladders;
    private final Random random = new Random();
    private int hitIn;
    private boolean hit;
    private int hitTick;
    private int groundTicks;
    private int placedSinceHit;
    private int streak;
    private boolean over;

    BlockExtendTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, Course course) {
        super(host, mode, arena, teams, variants(course));
        this.course = course;
        this.ladders = toggle("Ladders", true);
    }

    /**
     * The levels without the section of config.yml: those of the owner's map, from its centre
     * (870603, 91, 870629).
     */
    static Level[] plainLevels() {
        double[] gold = {1.589, 10, 1.939, -179.9f, 1.3};
        double[] diamond = {10.518, 10, -8.661, 179.7f, 2.6};
        double[] emerald = {-4.576, 7, -16.773, 179.4f, 1.4};
        return new Level[] {
            new Level(IDS[0], LABELS[0], gold, new int[] {-3, 5, 6}, thrown((float) gold[3], From.FRONT), From.FRONT, 0.80, 0.55),
            new Level(IDS[1], LABELS[1], diamond, new int[] {2, 4, -10}, thrown((float) diamond[3], From.RIGHT), From.RIGHT,
                0.80, 0.43),
            new Level(IDS[2], LABELS[2], emerald, new int[] {-5, 0, -16}, thrown((float) emerald[3], From.FRONT), From.FRONT,
                0.85, 0.12),
        };
    }

    /** The levels of every course: gold, diamond, emerald, whatever the map. */
    static Variant[] variants() {
        Variant[] variants = new Variant[IDS.length];
        for (int i = 0; i < variants.length; i++) {
            variants[i] = new Variant(IDS[i], LABELS[i], false, "saves");
        }
        return variants;
    }

    private static Variant[] variants(Course course) {
        Variant[] variants = new Variant[course.levels.length];
        for (int i = 0; i < variants.length; i++) {
            variants[i] = new Variant(course.levels[i].id, course.levels[i].label, false, "saves");
        }
        return variants;
    }

    private Level level() {
        return course.levels[variant];
    }

    /**
     * The way a hit from there throws a player who looks along this yaw (degrees, as Minecraft
     * gives it: 0 is south, +z), on x and z.
     */
    static double[] thrown(float yaw, From from) {
        double r = Math.toRadians(yaw);
        // where the player looks, and their right hand
        double fx = -Math.sin(r);
        double fz = Math.cos(r);
        double rx = -Math.cos(r);
        double rz = -Math.sin(r);
        switch (from) {
            case FRONT:
                return new double[] {-fx, -fz};
            case BACK:
                return new double[] {fx, fz};
            case RIGHT:
                return new double[] {-rx, -rz};
            default:
                return new double[] {rx, rz};
        }
    }

    /** The way from the start towards a point (x, z), length 1; straight back if it is right under the start. */
    static double[] towards(double[] start, double x, double z) {
        double dx = x - start[0];
        double dz = z - start[2];
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.01) {
            return thrown((float) start[3], From.FRONT);
        }
        return new double[] {dx / length, dz / length};
    }

    /** A fall or a miss gives the next attempt by itself: the bed in the hotbar. */
    @Override
    boolean restartUsed() {
        return false;
    }

    @Override
    String describe() {
        From from = level().from;
        String hit = from == null ? "a hit" : from == From.FRONT ? "a hit from the front" : from == From.BACK
                ? "a hit from behind" : from == From.RIGHT ? "a hit from your right" : "a hit from your left";
        return "you are frozen until " + hit + ": then turn and save yourself with your wool or a ladder.";
    }

    @Override
    void build() {
        if (course.copy != null) {
            course.copy.build(arena);
            return;
        }
        // without the map: a block under each start, and a short strip of wool at each target
        for (Level each : course.levels) {
            arena.build((int) Math.floor(each.start[0]), (int) Math.floor(each.start[1]) - 1, (int) Math.floor(each.start[2]),
                    Material.WOOL, WOOL_COLOUR);
            int[] t = each.target;
            boolean alongX = Math.abs(each.way[0]) < Math.abs(each.way[1]);
            for (int d = -1; d <= 1; d++) {
                arena.build(t[0] + (alongX ? d : 0), t[1], t[2] + (alongX ? 0 : d), Material.WOOL, WOOL_COLOUR);
            }
        }
    }

    @Override
    Location spawnOf(UUID player) {
        double[] start = level().start;
        Location at = arena.at(start[0], start[1], start[2], (float) start[3]);
        at.setPitch((float) start[4]);
        return at;
    }

    @Override
    double fallY() {
        return arena.floorY() + level().target[1] - 7;
    }

    @Override
    ItemStack[] kit() {
        if (ladders.on()) {
            return new ItemStack[] {stack(Material.WOOL, WOOL_COLOUR), stack(Material.LADDER)};
        }
        return new ItemStack[] {stack(Material.WOOL, WOOL_COLOUR)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void onFight() {
        hitIn = WAIT_TICKS + random.nextInt(WAIT_RANDOM);
        hit = false;
        over = false;
        groundTicks = 0;
        placedSinceHit = 0;
        Player player = player();
        if (player != null) {
            title(player, ChatColor.YELLOW + "Get ready...", ChatColor.GRAY + level().label);
        }
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || over) {
            return;
        }
        if (!hit) {
            hold(player);
            if (--hitIn <= 0) {
                hit(player);
            }
            refresh();
            return;
        }
        if (tickCount - hitTick < 8) {
            return;
        }
        if (!onGround(player)) {
            groundTicks = 0;
            return;
        }
        double feet = player.getLocation().getY() - arena.floorY();
        if (feet < level().target[1] + 1 - TOO_LOW) {
            lost(player, "You landed too low.");
            return;
        }
        if (++groundTicks < SAVED_TICKS) {
            return;
        }
        over = true;
        if (placedSinceHit == 0) {
            player.sendMessage(ChatColor.GRAY + "You landed without a block: no save, again.");
            again(10L);
            return;
        }
        streak++;
        player.sendMessage(ChatColor.GREEN + "Saved! " + ChatColor.GRAY + placedSinceHit + (placedSinceHit == 1 ? " block" : " blocks")
                + ", streak: " + ChatColor.WHITE + streak);
        title(player, ChatColor.GREEN + "" + ChatColor.BOLD + "SAVED!", ChatColor.GRAY + "Streak " + streak);
        sound(player, Sound.ORB_PICKUP, 1.2f);
        score(streak, true);
        again(AFTER_SAVE);
    }

    /**
     * Until the hit, the player is frozen at the start, look included: each move or turn takes them
     * back (and this, in case a move got through).
     */
    private void hold(Player player) {
        Location at = player.getLocation();
        Location start = spawnOf(me);
        if (!still(at, start)) {
            player.teleport(start);
            player.setFallDistance(0f);
        }
    }

    /** True if this place and look are the start's (to a hair). */
    static boolean still(Location at, Location start) {
        double dx = at.getX() - start.getX();
        double dy = at.getY() - start.getY();
        double dz = at.getZ() - start.getZ();
        return dx * dx + dy * dy + dz * dz < 0.0025 && Math.abs(angle(at.getYaw() - start.getYaw())) < 0.5
                && Math.abs(at.getPitch() - start.getPitch()) < 0.5;
    }

    /** An angle between -180 and 180 degrees. */
    private static float angle(float degrees) {
        float a = degrees % 360f;
        return a > 180f ? a - 360f : a < -180f ? a + 360f : a;
    }

    /** Frozen at the start until the hit: the moves and turns of the player are undone at once. */
    @Override
    Location heldAt(Player player) {
        return hit || over ? null : spawnOf(me);
    }

    /** The hit of the level, as a player would give it, always along the line of the start. */
    private void hit(Player player) {
        Level level = level();
        double[] way = level.way;
        player.setVelocity(new Vector(way[0] * level.horizontal, level.vertical, way[1] * level.horizontal));
        player.playEffect(EntityEffect.HURT);
        sound(player, Sound.HURT_FLESH, 1f);
        hit = true;
        hitTick = tickCount;
        placedSinceHit = 0;
    }

    private void lost(Player player, String why) {
        over = true;
        String text = streak > 0 ? why + " The streak of " + streak + " starts again." : why;
        streak = 0;
        failed(text);
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        if (!hit) {
            return false;
        }
        return near(block, 0, 0, 60, 30, 60);
    }

    @Override
    void placed(Player player, Block block) {
        placedSinceHit++;
    }

    @Override
    void fell(Player player) {
        lost(player, "You fell.");
    }

    /** The sidebar keeps to the jump: no ranking. */
    @Override
    boolean showsRanking() {
        return false;
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
        lines.add(ChatColor.WHITE + "Blocks: " + ChatColor.GREEN + (hit ? placedSinceHit : 0));
        lines.add(ChatColor.GRAY + (hit ? "Save yourself!" : "Get ready..."));
    }
}
