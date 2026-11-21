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
 * Snowball Spleef, the spleef of FUN PRACTICE (plan 6.1): a floor of snow over the void (or
 * several, one under the other), a diamond shovel that breaks the snow at once, and a snowball
 * for each block broken. A snowball breaks the snow it lands on, and pushes the player it hits,
 * without damage; nobody hits with the hand. The first to fall under the lowest floor loses the
 * round; the floors are whole again at each round. Its settings: snowball-spleef in config.yml.
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

        Settings(int radius, int floors, int gap, int snowballs) {
            this.radius = radius;
            this.floors = floors;
            this.gap = gap;
            this.snowballs = snowballs;
        }

        /** What is wrong with these values, or null. */
        static String check(int radius, int floors, int gap, int snowballs) {
            if (radius < 5 || radius > 40) {
                return "rayon must be from 5 to 40";
            }
            if (floors < 1 || floors > 5) {
                return "etages must be from 1 to 5";
            }
            if (gap < 3 || gap > 20) {
                return "ecart-etages must be from 3 to 20";
            }
            if (floors * gap > 60) {
                return "etages x ecart-etages must be 60 blocks at most";
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
    /** The step of that search, in blocks. */
    static final double STEP = 0.1;

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
                + " breaks the snow it lands on and pushes the player it hits. The first to fall loses the round.");
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
        return arena.floorY() + floorDy(settings.floors - 1, settings.gap) - FALL_DEPTH;
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

    /** Only the snowballs push, without damage: no hit with the hand, no fall damage. */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        Entity damager = damager(event);
        if (attacker == null || !(damager instanceof Snowball)) {
            return false;
        }
        struckPlayer.add(damager.getUniqueId());
        noDamage(event);
        return true;
    }

    /** The snow of the floors breaks, nothing else. */
    @Override
    boolean canBreak(Player player, Block block) {
        return block.getType() == Material.SNOW_BLOCK && onFloors(block);
    }

    /** True if the block is in the box of the floors. */
    private boolean onFloors(Block block) {
        return arena.contains(block, settings.radius + 1, -floorDy(settings.floors - 1, settings.gap), 0);
    }

    /** A snowball for each block broken with the shovel. */
    @Override
    void broke(Player player, Block block) {
        if (settings.snowballs > 0) {
            player.getInventory().addItem(new ItemStack(Material.SNOW_BALL, settings.snowballs));
        }
    }

    /** A snowball breaks the snow it lands on, unless it hit a player. */
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
        if (block.getType() != Material.SNOW_BLOCK || !onFloors(block)) {
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
     * least); null if none, or if it does not move.
     */
    static int[] struck(double x, double y, double z, double vx, double vy, double vz, Solid solid) {
        double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (length < 1e-6) {
            return null;
        }
        double dx = vx / length;
        double dy = vy / length;
        double dz = vz / length;
        double reach = Math.max(REACH, length + 0.5);
        for (double along = 0; along <= reach + 1e-9; along += STEP) {
            int bx = (int) Math.floor(x + dx * along);
            int by = (int) Math.floor(y + dy * along);
            int bz = (int) Math.floor(z + dz * along);
            if (solid.at(bx, by, bz)) {
                return new int[] {bx, by, bz};
            }
        }
        return null;
    }
}
