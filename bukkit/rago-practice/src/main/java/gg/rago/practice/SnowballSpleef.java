package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Snowball Spleef, the spleef of FUN PRACTICE (plan 6.1): floors of snow over the void, one under
 * the other (two, the owner's choice of 22/11), a diamond shovel that breaks the snow at once, and
 * a snowball for each block broken. A snowball breaks the snow it lands on. No hits with the hand
 * (the owner, 22/11); a snowball hits a player (the owner, 22/11, 19h55): it pushes them as in
 * Minecraft, without damage, and breaks no snow there. Nobody breaks the snow of a floor above
 * them, with the shovel or a snowball: the floors are far enough apart for the shovel to be out of
 * reach from below. Under them, a narrower floor, closer (the owner's ask of 23/11: « un troisième
 * étage plus petit (étroit) et moins bas (4-5 blocks) »). The first to fall under the lowest floor
 * loses the round; the floors are whole again at each round. Its settings: snowball-spleef in
 * config.yml.
 */
final class SnowballSpleef extends Duel {

    /** The settings of the mode (snowball-spleef in config.yml). */
    static final class Settings {
        /** Radius of each floor, in blocks. */
        final int radius;
        /** How many floors, one under the other. */
        final int floors;
        /** Blocks from one floor down to the next. */
        final int gap;
        /** Snowballs for each block of snow the shovel breaks. */
        final int snowballs;
        /** Radius of the narrow floor under the others, 0 for none. */
        final int narrowRadius;
        /** Blocks from the last of the floors down to the narrow one. */
        final int narrowGap;

        Settings(int radius, int floors, int gap, int snowballs) {
            this(radius, floors, gap, snowballs, 0, 0);
        }

        Settings(int radius, int floors, int gap, int snowballs, int narrowRadius, int narrowGap) {
            this.radius = radius;
            this.floors = floors;
            this.gap = gap;
            this.snowballs = snowballs;
            this.narrowRadius = narrowRadius;
            this.narrowGap = narrowRadius > 0 ? narrowGap : 0;
        }

        /** The height of the lowest floor under the top one (the narrow one if there is one). */
        int lowestDy() {
            return floorDy(floors - 1, gap) - narrowGap;
        }

        /** What is wrong with these values, or null. */
        static String check(int radius, int floors, int gap, int snowballs) {
            return check(radius, floors, gap, snowballs, 0, 0);
        }

        /** What is wrong with these values, or null. */
        static String check(int radius, int floors, int gap, int snowballs, int narrowRadius, int narrowGap) {
            if (radius < 5 || radius > 40) {
                return "rayon must be from 5 to 40";
            }
            if (floors < 1 || floors > 5) {
                return "etages must be from 1 to 5";
            }
            if (gap < 3 || gap > 20) {
                return "ecart-etages must be from 3 to 20";
            }
            if (narrowRadius != 0 && (narrowRadius < 3 || narrowRadius >= radius)) {
                return "etage-etroit-rayon must be 0 (none) or from 3 to less than rayon";
            }
            if (narrowRadius != 0 && (narrowGap < 3 || narrowGap > 20)) {
                return "etage-etroit-ecart must be from 3 to 20";
            }
            if (floors * gap + (narrowRadius != 0 ? narrowGap : 0) > 60) {
                return "etages x ecart-etages, with etage-etroit-ecart, must be 60 blocks at most";
            }
            if (snowballs < 0 || snowballs > 16) {
                return "boules-par-bloc must be from 0 to 16";
            }
            return null;
        }
    }

    /** Under the lowest floor by this much, a player has fallen (as in the sumo). */
    static final double FALL_DEPTH = SumoMatch.FALL_DEPTH;
    /**
     * How far along its way a snowball's landing looks for the block it hit, in blocks, at least: it
     * lands where it was before its last move, so the block is up to a move away (its speed, plus a
     * little).
     */
    static final double REACH = 1.5;

    private final Settings settings;
    /** The snowballs that hit a player: they break no snow where they land. */
    private final Set<UUID> struckPlayer = new HashSet<>();

    SnowballSpleef(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target, Settings settings) {
        super(host, mode, arena, teams, target);
        this.settings = settings;
    }

    @Override
    String title() {
        return "Snowball Spleef";
    }

    @Override
    void intro(Player player) {
        super.intro(player);
        player.sendMessage(ChatColor.GRAY + "Break the snow under them with your shovel; each block gives a snowball, which"
                + " breaks the snow it lands on, or pushes them when it hits them. No hits with the hand: dig them down."
                + " The first to fall under the last floor loses the round.");
    }

    @Override
    boolean colouredBlocks() {
        return false;
    }

    /** The height of floor n (0: the top one) under the top one. */
    static int floorDy(int n, int gap) {
        return -n * gap;
    }

    @Override
    void build() {
        for (int n = 0; n < settings.floors; n++) {
            Shapes.disc(arena, 0, 0, floorDy(n, settings.gap), settings.radius, Material.SNOW_BLOCK, 0);
        }
        if (settings.narrowRadius > 0) {
            Shapes.disc(arena, 0, 0, settings.lowestDy(), settings.narrowRadius, Material.SNOW_BLOCK, 0);
        }
    }

    @Override
    void roundReset() {
        super.roundReset();
        struckPlayer.clear();
    }

    /** How far from the middle each side starts. */
    static double startDistance(int radius) {
        return Math.max(3, radius / 2);
    }

    @Override
    Location spawnOf(UUID player) {
        int side = Math.max(0, sideOf(player));
        double distance = startDistance(settings.radius);
        double z = across(player) * 2 + 0.5;
        return side == 0 ? arena.at(0.5 - distance, 1, z, -90f) : arena.at(0.5 + distance, 1, z, 90f);
    }

    /** Under the lowest floor: out. */
    @Override
    double fallY() {
        return arena.floorY() + settings.lowestDy() - FALL_DEPTH;
    }

    @Override
    Location viewPoint() {
        Location view = arena.at(0.5, 12, -(settings.radius + 10) + 0.5, 0f);
        view.setPitch(35f);
        return view;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.DIAMOND_SPADE)};
    }

    /** Efficiency V on the shovel, wherever the player put it. */
    @Override
    void afterKit(Player player) {
        ItemStack[] items = player.getInventory().getContents();
        for (ItemStack item : items) {
            if (item != null && item.getType() == Material.DIAMOND_SPADE) {
                item.addUnsafeEnchantment(Enchantment.DIG_SPEED, 5);
            }
        }
        player.getInventory().setContents(items);
    }

    /**
     * No hit with the hand and no fall damage; a snowball of the other player hits: it pushes as in
     * Minecraft, without damage, and breaks no snow where it lands.
     */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        Entity damager = damager(event);
        if (damager instanceof Snowball) {
            struckPlayer.add(damager.getUniqueId());
            if (attacker != null) {
                noDamage(event);
                return true;
            }
        }
        return false;
    }

    /** The snow of the floors breaks, nothing else, and never a floor above the player. */
    @Override
    boolean canBreak(Player player, Block block) {
        return block.getType() == Material.SNOW_BLOCK && onFloors(block) && !above(block.getY(), player.getLocation().getBlockY());
    }

    /** True if a block at this height is above a player whose feet are at this height: a floor over their head. */
    static boolean above(int blockY, int feetY) {
        return blockY >= feetY;
    }

    /** True if the block is in the box of the floors. */
    private boolean onFloors(Block block) {
        return arena.contains(block, settings.radius + 1, -settings.lowestDy(), 0);
    }

    /** A snowball for each block broken with the shovel. */
    @Override
    void broke(Player player, Block block) {
        if (settings.snowballs > 0) {
            player.getInventory().addItem(new ItemStack(Material.SNOW_BALL, settings.snowballs));
        }
    }

    /** A snowball breaks the snow it lands on, unless it stopped on a player. */
    @Override
    @SuppressWarnings("deprecation") // Material#getId: the block of the effect
    void projectileHit(Player shooter, Projectile projectile) {
        if (!(projectile instanceof Snowball) || phase != Phase.PLAYING) {
            return;
        }
        if (struckPlayer.remove(projectile.getUniqueId())) {
            return;
        }
        Location at = projectile.getLocation();
        Vector way = projectile.getVelocity();
        int[] hit = struck(at.getX(), at.getY(), at.getZ(), way.getX(), way.getY(), way.getZ(),
                (x, y, z) -> at.getWorld().getBlockAt(x, y, z).getType() != Material.AIR);
        if (hit == null) {
            return;
        }
        Block block = at.getWorld().getBlockAt(hit[0], hit[1], hit[2]);
        if (block.getType() != Material.SNOW_BLOCK || !onFloors(block)
                || (shooter != null && above(block.getY(), shooter.getLocation().getBlockY()))) {
            // a floor above the thrower: thrown up from the floor below, it breaks nothing
            return;
        }
        arena.removed(block);
        block.setType(Material.AIR);
        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, Material.SNOW_BLOCK.getId());
    }

    /** Whether there is a block at x, y, z. */
    interface Solid {
        boolean at(int x, int y, int z);
    }

    /**
     * The block a projectile landing at (x, y, z) on its way (vx, vy, vz, a move of a tick) hit: the
     * first block from there along its way, as far as a move and a half block ({@value #REACH} at
     * least); null if none, or if it does not move. Every block the way crosses is looked at, one
     * after the other (Amanatides and Woo), even one it only clips at a corner or an edge, as the
     * server's own ray finds it: a snowball that lands on a block always breaks that block (the
     * owner's ask of 23/11, « 100% du temps »; before, a look every tenth of a block could pass by
     * a corner).
     */
    static int[] struck(double x, double y, double z, double vx, double vy, double vz, Solid solid) {
        double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (length < 1e-6) {
            return null;
        }
        double[] dir = {vx / length, vy / length, vz / length};
        double[] from = {x, y, z};
        int[] block = {(int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)};
        int[] step = new int[3];
        double[] next = new double[3];
        double[] across = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            if (dir[axis] > 0) {
                step[axis] = 1;
                next[axis] = (block[axis] + 1 - from[axis]) / dir[axis];
                across[axis] = 1 / dir[axis];
            } else if (dir[axis] < 0) {
                step[axis] = -1;
                next[axis] = (from[axis] - block[axis]) / -dir[axis];
                across[axis] = -1 / dir[axis];
            } else {
                next[axis] = Double.POSITIVE_INFINITY;
                across[axis] = Double.POSITIVE_INFINITY;
            }
        }
        double reach = Math.max(REACH, length + 0.5);
        double along = 0;
        while (along <= reach + 1e-9) {
            if (solid.at(block[0], block[1], block[2])) {
                return block;
            }
            int axis = next[0] <= next[1] && next[0] <= next[2] ? 0 : next[1] <= next[2] ? 1 : 2;
            along = next[axis];
            next[axis] += across[axis];
            block[axis] += step[axis];
        }
        return null;
    }
}
