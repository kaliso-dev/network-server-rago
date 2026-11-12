package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Pearl practice on a part of Sky Rise: the red island, the diamond island in front of it and the
 * emerald island of the middle. One pearl and a stack of wool a try: pearl onto the diamond
 * island, pearl to the middle, or pearl back to your island after a hit that sends you into the
 * void. A pearl that lands short can be saved with blocks (a pearl block clutch), but no block
 * goes below the lower limit of the map, as on Hypixel. The try counts once the player stands on
 * the island aimed at; each one adds to the streak, a miss ends it.
 *
 * <p>The islands are the map's when the server has the world (see {@link MapCopy}); else plain
 * islands of the same size, at the same places.
 */
final class PearlTraining extends Training {

    /** A situation of the map: where the player starts, and the island they must reach. */
    static final class Situation {
        final String id;
        final String label;
        final String what;
        /** The start on the map: x, y (the feet), z, yaw. */
        final double[] start;
        /** The island to reach on the map: x1, y1, z1, x2, y2, z2. */
        final int[] target;
        /** True when a hit sends the player into the void first. */
        final boolean hit;

        Situation(String id, String label, String what, double[] start, int[] target, boolean hit) {
            this.id = id;
            this.label = label;
            this.what = what;
            this.start = start;
            this.target = target;
            this.hit = hit;
        }
    }

    /** The map's place of the arena's centre: the floor under the red bed of Sky Rise. */
    static final int[] ORIGIN = {-36, 70, -81};
    /** The part of Sky Rise copied: x1, y1, z1, x2, y2, z2 on the map. */
    static final int[] BOX = {-60, 34, -108, -1, 80, -2};
    /** The red island, the diamond island and the emerald island of the middle, on Sky Rise. */
    static final int[] RED_ISLAND = {-59, 36, -107, -17, 80, -75};
    static final int[] DIAMOND_ISLAND = {-50, 30, -50, -23, 82, -22};
    static final int[] MIDDLE_ISLAND = {-18, 30, -18, -2, 78, -2};
    static final Situation[] SITUATIONS = {
        new Situation("diamond", "To the diamond island", "pearl from the front of your island onto the diamond island.",
                new double[] {-35.5, 71, -75.5, 0}, grown(DIAMOND_ISLAND), false),
        new Situation("middle", "To the middle", "pearl from the diamond island onto the emerald island of the middle.",
                new double[] {-36.5, 71, -22.5, -65}, grown(MIDDLE_ISLAND), false),
        new Situation("save", "Back to your island", "a hit sends you into the void: pearl back to your island, and place"
                + " blocks if you land short (not below the limit).", new double[] {-35.5, 71, -75.5, 180}, grown(RED_ISLAND), true),
    };
    /** Ticks standing still on the island aimed at that make a good pearl. */
    private static final int STAND_TICKS = 10;
    /** A pearl in the air this long has fallen into the void. */
    private static final int LOST_TICKS = 140;
    /** A pearl falling this far under the lowest height of the blocks has missed, without waiting longer. */
    private static final int LOST_BELOW_LIMIT = 3;

    /** The island of the training: the copy of Sky Rise (or null) and its lower limit on the map. */
    static final class Island {
        final MapCopy copy;
        final int limitY;

        Island(MapCopy copy, int limitY) {
            this.copy = copy;
            this.limitY = limitY;
        }
    }

    private final Island island;
    private final Random random = new Random();
    private int streak;
    private int thrownTick = -1;
    /** The pearl in the air, or null. */
    private Projectile pearl;
    private boolean landed;
    private boolean placedAny;
    private int hitIn;
    private int standing;
    private long limitTold;

    PearlTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, Island island) {
        super(host, mode, arena, teams, variants());
        this.island = island;
    }

    static Variant[] variants() {
        Variant[] variants = new Variant[SITUATIONS.length];
        for (int i = 0; i < variants.length; i++) {
            variants[i] = new Variant(SITUATIONS[i].id, SITUATIONS[i].label, false, SITUATIONS[i].hit ? "saves" : "pearls");
        }
        return variants;
    }

    /** The island's box one block wider on each side, from a little under its top: a player on it. */
    private static int[] grown(int[] box) {
        return new int[] {box[0] - 1, 66, box[2] - 1, box[3] + 1, box[4] + 4, box[5] + 1};
    }

    /** True if this point of the map is in the box (x1, y1, z1, x2, y2, z2). */
    static boolean inside(int[] box, double x, double y, double z) {
        return x >= box[0] && x < box[3] + 1 && y >= box[1] && y < box[4] + 1 && z >= box[2] && z < box[5] + 1;
    }

    /** True if no block may be placed at this height of the map, as under the islands on Hypixel. */
    static boolean belowLimit(int y, int limitY) {
        return y < limitY;
    }

    private Situation situation() {
        for (Situation situation : SITUATIONS) {
            if (situation.id.equals(current().id)) {
                return situation;
            }
        }
        return SITUATIONS[0];
    }

    @Override
    String describe() {
        return situation().what;
    }

    @Override
    void build() {
        if (island.copy != null) {
            island.copy.build(arena);
            return;
        }
        plain(RED_ISLAND, Material.WOOD);
        plain(DIAMOND_ISLAND, Material.SANDSTONE);
        plain(MIDDLE_ISLAND, Material.STONE);
    }

    /** A plain island over the footprint of a map's island, its top at the map's floor. */
    private void plain(int[] box, Material top) {
        Shapes.island(arena, box[0] - ORIGIN[0], box[2] - ORIGIN[2], box[3] - ORIGIN[0], box[5] - ORIGIN[2], top, 0,
                Material.STONE, 0);
    }

    @Override
    Location spawnOf(UUID player) {
        double[] start = situation().start;
        return arena.at(start[0] - ORIGIN[0], start[1] - ORIGIN[1], start[2] - ORIGIN[2], (float) start[3]);
    }

    @Override
    double fallY() {
        return arena.floorY() - 50;
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {new ItemStack(Material.ENDER_PEARL, 1), new ItemStack(Material.WOOL, 64, (short) Shapes.RED)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        thrownTick = -1;
        pearl = null;
        landed = false;
        placedAny = false;
        standing = 0;
        hitIn = situation().hit ? 20 + random.nextInt(30) : 0;
    }

    /** Where the player is, on the map. */
    private double[] onMap(Location at) {
        double[] offset = arena.offset(at);
        return new double[] {offset[0] + ORIGIN[0], offset[1] + ORIGIN[1], offset[2] + ORIGIN[2]};
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null) {
            return;
        }
        if (hitIn > 0 && --hitIn == 0) {
            // the hit that sends you off, as a sprint hit with a knockback stick would
            double side = random.nextDouble() * 0.4 - 0.2;
            player.setVelocity(new Vector(side, 0.45, 1.1 + random.nextDouble() * 0.25));
            player.playEffect(EntityEffect.HURT);
            sound(player, Sound.HURT_FLESH, 1f);
        }
        if (thrownTick >= 0 && !landed && (tickCount - thrownTick > LOST_TICKS || lost(pearl))) {
            missed("Your pearl fell into the void.");
            return;
        }
        boolean tried = landed || (situation().hit && hitIn == 0 && (thrownTick >= 0 || placedAny));
        if (!tried) {
            return;
        }
        if (!onGround(player)) {
            standing = 0;
            return;
        }
        if (++standing < STAND_TICKS) {
            return;
        }
        double[] at = onMap(player.getLocation());
        if (inside(situation().target, at[0], at[1], at[2])) {
            success(player, situation().hit ? "Saved!" : "On the island!");
        } else {
            missed("Not on the island you aimed at.");
        }
    }

    @Override
    void launched(Player player, Projectile projectile, ProjectileLaunchEvent event) {
        if (!(projectile instanceof EnderPearl)) {
            return;
        }
        if (hitIn > 0) {
            event.setCancelled(true);
            player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
            player.sendMessage(ChatColor.GRAY + "Wait for the hit.");
            return;
        }
        thrownTick = tickCount;
        pearl = projectile;
    }

    /**
     * True if this pearl can no longer land anywhere to stand on: gone without landing, or falling
     * already a few blocks under the lowest height where blocks go (nothing to save the player there).
     */
    private boolean lost(Projectile thrown) {
        if (thrown == null) {
            return false;
        }
        if (thrown.isDead()) {
            return true;
        }
        return thrown.getVelocity().getY() < 0 && onMap(thrown.getLocation())[1] < island.limitY - LOST_BELOW_LIMIT;
    }

    @Override
    void pearled(Player player, PlayerTeleportEvent event) {
        landed = true;
        standing = 0;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        int x = block.getX() - arena.centreX() + ORIGIN[0];
        int y = block.getY() - arena.floorY() + ORIGIN[1];
        int z = block.getZ() - arena.centreZ() + ORIGIN[2];
        if (hitIn > 0 || (!situation().hit && thrownTick < 0)) {
            // the pearl first: blocks only save a pearl that lands short
            return false;
        }
        if (belowLimit(y, island.limitY)) {
            if (now() - limitTold > 1000) {
                limitTold = now();
                player.sendMessage(ChatColor.RED + "You can't place blocks this low!");
            }
            return false;
        }
        return x >= BOX[0] && x <= BOX[3] && z >= BOX[2] && z <= BOX[5] && y <= ORIGIN[1] + 15;
    }

    @Override
    void placed(Player player, Block block) {
        placedAny = true;
    }

    private void success(Player player, String what) {
        streak++;
        player.sendMessage(ChatColor.GREEN + what + " " + ChatColor.GRAY + "Streak: " + ChatColor.WHITE + streak);
        sound(player, Sound.ORB_PICKUP, 1.2f);
        score(streak, true);
        thrownTick = -1;
        pearl = null;
        landed = false;
        placedAny = false;
        standing = Integer.MIN_VALUE;
        again(15L);
    }

    private void missed(String why) {
        String end = streak > 0 ? " The streak of " + streak + " ends." : "";
        streak = 0;
        standing = Integer.MIN_VALUE;
        failed(why + end);
    }

    @Override
    void fell(Player player) {
        missed(situation().hit ? "You fell: pearl back to your island as soon as you are hit." : "You fell.");
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
        lines.add(ChatColor.WHITE + "No block under " + ChatColor.RED + "y " + island.limitY);
    }
}
