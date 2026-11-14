package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.EntityEffect;
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
 * Pearl practice on the owner's Bed Wars maps, in a loop: each attempt on the next map (each
 * server start takes the side of one team of each map), at another place and in another direction,
 * the kinds of situation in turn (see {@link MapParts}): a pearl from one island onto another, a
 * hit that throws the player off an island and a pearl back, and above all the pearl block clutch,
 * where the player is hit off their bridge a few blocks under an island, pearls to its side and
 * places blocks under themselves against the wall. One pearl and a stack of wool a try; no block
 * goes under the lower limit of the part, as under the islands on Hypixel. The try counts once
 * the player stands on the island aimed at (or on their blocks against it); each one adds to the
 * streak, a miss ends it. The next one comes by itself: nothing to choose. The levels Pearls,
 * Saves and Clutches play one kind only, still map after map.
 *
 * <p>The zone holds one part at a time (see {@link MapLoopTraining}). Without any of the maps, the
 * plugin's own islands ({@link MapParts#plain}).
 */
final class PearlTraining extends MapLoopTraining {

    /** The kinds of the loop, in turn: the clutch every other time (it is what to train above all). */
    static final MapParts.Kind[] LOOP = {MapParts.Kind.PEARL, MapParts.Kind.CLUTCH, MapParts.Kind.SAVE, MapParts.Kind.CLUTCH};
    /** Ticks standing still on the island aimed at that make a good try. */
    private static final int STAND_TICKS = 10;
    /** A pearl in the air this long has fallen into the void. */
    private static final int LOST_TICKS = 140;
    /** A pearl falling this far under the lowest height of the blocks has missed, without waiting longer. */
    private static final int LOST_BELOW_LIMIT = 3;
    /** Columns around the island aimed at where a player still stands on it (on their own blocks against its side). */
    private static final int REACH = 2;

    private int thrownTick = -1;
    /** The pearl in the air, or null. */
    private Projectile pearl;
    private boolean landed;
    private boolean placedAny;
    /** Ticks before the hit; 0: no hit (any more). */
    private int hitIn;
    private int standing;
    private long toldAt;

    PearlTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, Places places) {
        super(host, mode, arena, teams, places, variants());
    }

    static Variant[] variants() {
        return new Variant[] {
            new Variant("loop", "Loop", false, "in a row"),
            new Variant("pearls", "Pearls", false, "pearls"),
            new Variant("saves", "Saves", false, "saves"),
            new Variant("clutches", "Pearl clutches", false, "clutches"),
        };
    }

    @Override
    MapParts.Kind kindOf(String variantId, int turn) {
        return kind(variantId, turn);
    }

    @Override
    int kindsOf(String variantId) {
        return "loop".equals(variantId) ? 3 : 1;
    }

    /** The kind of this level for the attempt after so many. */
    static MapParts.Kind kind(String variantId, int turn) {
        switch (variantId) {
            case "pearls":
                return MapParts.Kind.PEARL;
            case "saves":
                return MapParts.Kind.SAVE;
            case "clutches":
                return MapParts.Kind.CLUTCH;
            default:
                return LOOP[Math.floorMod(turn, LOOP.length)];
        }
    }

    /** True if no block may be placed at this height of the part (from its origin), as under the islands on Hypixel. */
    static boolean belowLimit(int y, int limitY) {
        return y < limitY;
    }

    @Override
    String describe() {
        switch (current().id) {
            case "pearls":
                return "pearl from one island onto another, a new map each time.";
            case "saves":
                return "a hit throws you off the island: pearl back onto it, a new map each time.";
            case "clutches":
                return "hit off your bridge under an island: pearl to its side, then place blocks under you at once.";
            default:
                return "a new map each time: pearl onto an island, pearl back after a hit, and pearl block clutches"
                        + " against the side of an island.";
        }
    }

    // --- The attempt ----------------------------------------------------------------------------

    @Override
    ItemStack[] kit() {
        int wool = part == null ? Shapes.RED : part.wool;
        return new ItemStack[] {new ItemStack(Material.ENDER_PEARL, 1), new ItemStack(Material.WOOL, 64, (short) wool)};
    }

    @Override
    void roundReset() {
        dropPearl();
        thrownTick = -1;
        landed = false;
        placedAny = false;
        standing = 0;
        hitIn = situation != null && situation.push != null ? 20 + random.nextInt(30) : 0;
        if (situation != null && situation.platform != null) {
            int[] at = situation.platform;
            for (int i = 0; i + 2 < at.length; i += 3) {
                arena.put(at[i], at[i + 1], at[i + 2], Material.WOOL, part.wool);
            }
        }
    }

    /** Where the player is, from the origin of the part. */
    private double[] here(org.bukkit.Location at) {
        return arena.offset(at);
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || situation == null || loading) {
            return;
        }
        if (thrownTick < 0 && tickCount % 10 == 0 && !player.getInventory().contains(Material.ENDER_PEARL)) {
            // no pearl thrown yet in this attempt, and none in the inventory (dropped...): one comes back
            player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
        }
        double[] at = here(player.getLocation());
        if (hitIn > 0) {
            if (at[1] < situation.start[1] - 1.5) {
                // off their place before the hit: they are falling already
                hitIn = 0;
            } else if (--hitIn == 0) {
                // the hit that throws them off, as a sprint hit with a knockback stick would
                double[] push = situation.push;
                double side = random.nextDouble() * 0.3 - 0.15;
                boolean save = situation.kind == MapParts.Kind.SAVE;
                double strength = save ? 1.1 + random.nextDouble() * 0.25 : 0.75 + random.nextDouble() * 0.2;
                player.setVelocity(new Vector(push[0] * strength - push[1] * side, save ? 0.45 : 0.32,
                        push[1] * strength + push[0] * side));
                player.playEffect(EntityEffect.HURT);
                sound(player, Sound.HURT_FLESH, 1f);
            }
        }
        if (thrownTick >= 0 && !landed && (tickCount - thrownTick > LOST_TICKS || lost(pearl))) {
            missed("Your pearl fell into the void.");
            return;
        }
        boolean tried = landed || (situation.push != null && hitIn == 0 && (thrownTick >= 0 || placedAny));
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
        if (part.near(situation.target, at[0], at[2], REACH) && at[1] >= situation.targetFloor - 12
                && at[1] <= situation.targetFloor + 8) {
            success(player);
        } else {
            missed("Not on the island you aimed at.");
        }
    }

    @Override
    void launched(Player player, Projectile projectile, ProjectileLaunchEvent event) {
        if (!(projectile instanceof EnderPearl)) {
            return;
        }
        if (hitIn > 0 || loading) {
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
        return thrown.getVelocity().getY() < 0 && here(thrown.getLocation())[1] < part.limitY - LOST_BELOW_LIMIT;
    }

    /** A pearl of an attempt that ended (or none thrown in this one): it does not take the player anywhere. */
    @Override
    void pearled(Player player, PlayerTeleportEvent event) {
        if (loading || thrownTick < 0) {
            event.setCancelled(true);
            return;
        }
        landed = true;
        standing = 0;
    }

    /** The pearl of the attempt still in the air goes: it would land during the next one. */
    private void dropPearl() {
        if (pearl != null && !pearl.isDead()) {
            pearl.remove();
        }
        pearl = null;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        if (situation == null || loading || hitIn > 0) {
            return false;
        }
        if (thrownTick < 0) {
            // the pearl first: the blocks save a pearl that lands short, or against a wall
            tell(player, ChatColor.RED + "Pearl first, then the blocks.");
            return false;
        }
        int y = block.getY() - arena.floorY();
        if (belowLimit(y, part.limitY)) {
            tell(player, ChatColor.RED + "You can't place blocks this low!");
            return false;
        }
        int x = block.getX() - arena.centreX();
        int z = block.getZ() - arena.centreZ();
        return Math.abs(x) < 100 && Math.abs(z) < 100 && y <= situation.targetFloor + 15;
    }

    @Override
    void placed(Player player, Block block) {
        placedAny = true;
    }

    /** A message of a refused block, once a second at most. */
    private void tell(Player player, String message) {
        if (now() - toldAt > 1000) {
            toldAt = now();
            player.sendMessage(message);
        }
    }

    private void success(Player player) {
        String what = situation.kind == MapParts.Kind.CLUTCH ? "Clutched!" : situation.kind == MapParts.Kind.SAVE ? "Saved!"
                : "On the island!";
        thrownTick = -1;
        pearl = null;
        landed = false;
        placedAny = false;
        standing = Integer.MIN_VALUE;
        succeeded(player, what);
    }

    @Override
    void missed(String why) {
        standing = Integer.MIN_VALUE;
        super.missed(why);
    }

    @Override
    String fellWhy() {
        return situation != null && situation.push != null ? "You fell: pearl as soon as you are hit." : "You fell.";
    }

    /** The pearl of the last attempt goes with the map. */
    @Override
    void leaving() {
        dropPearl();
    }

    @Override
    void trainingLines(List<String> lines) {
        super.trainingLines(lines);
        if (part != null) {
            lines.add(ChatColor.WHITE + "No block under " + ChatColor.RED + "y " + (arena.floorY() + part.limitY));
        }
    }
}
