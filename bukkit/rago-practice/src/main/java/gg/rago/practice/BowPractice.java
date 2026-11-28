package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Hypixel Bow Practice (the owner's ask of 27/11), on his map BowPractice (bow-practice in
 * config.yml): a zombie comes straight at the player along a bridge of wool over the void. The
 * first arrow knocks it off (it does not count), then the player has to hit it {@value #AIR_HITS}
 * more times in the air before it dies in the void: a win. It wins if it gets to the end of its
 * bridge, or if it dies in the void without its hits in the air. The player has a bow (Power IV,
 * Infinity) and an arrow, and moves freely on {@value #ZONE} by {@value #ZONE} blocks around their
 * start. Three levels, the dyes of every training to change: a straight bridge, a diagonal one, a
 * high one. Each win adds to the streak; a new zombie comes at once, with the sounds of a win and of
 * a loss. Main thread only.
 */
final class BowPractice extends Training {

    /** A level: the player's start, and the bridge of the zombie. */
    static final class Level {
        /** x, y, z, yaw, pitch of the player's start, from the origin. */
        final double[] player;
        /** x, y, z of the first block of the bridge (the zombie starts on it), from the origin. */
        final int[] first;
        /** x, z of the last block of the bridge, from the origin. */
        final int[] last;

        Level(double[] player, int[] first, int[] last) {
            this.player = player;
            this.first = first;
            this.last = last;
        }

        /** The way of the zombie along its bridge, x and z, length 1. */
        double[] way() {
            double dx = last[0] - first[0];
            double dz = last[1] - first[2];
            double length = Math.max(1e-9, Math.hypot(dx, dz));
            return new double[] {dx / length, dz / length};
        }

        /** The length of the bridge, from the middle of its first block to the middle of its last. */
        double length() {
            return Math.hypot(last[0] - first[0], last[1] - first[2]);
        }

        /** How far along the bridge a point (x, z) is, from the middle of its first block. */
        double along(double x, double z) {
            double[] way = way();
            return (x - first[0] - 0.5) * way[0] + (z - first[2] - 0.5) * way[1];
        }

        /** How far to the side of the bridge a point (x, z) is (to the right of the way, positive). */
        double aside(double x, double z) {
            double[] way = way();
            return -(x - first[0] - 0.5) * way[1] + (z - first[2] - 0.5) * way[0];
        }
    }

    /** The place: the copy of the map (or null: plain platforms) and its levels. */
    static final class Course {
        final MapCopy copy;
        final Level[] levels;

        Course(MapCopy copy, Level[] levels) {
            this.copy = copy;
            this.levels = levels;
        }
    }

    /** The course without the map: three plain levels, a straight bridge, a diagonal one, a high one. */
    static Course plainCourse() {
        return new Course(null, new Level[] {
            new Level(new double[] {5.5, 1, 11.5, 155, 0}, new int[] {0, 0, 0}, new int[] {0, 8}),
            new Level(new double[] {5.5, 1, 11.5, 115, 0}, new int[] {-10, 0, 0}, new int[] {0, 10}),
            new Level(new double[] {-8.5, 1, -15.5, -45, -8}, new int[] {0, 2, 0}, new int[] {0, -13}),
        });
    }

    /** Hits in the air after the first arrow, to win (the owner: two). */
    static final int AIR_HITS = 2;
    /** The side of the square the player moves in, around their start (the owner: about five). */
    static final int ZONE = 5;
    /** Ticks a zombie flies after an arrow before it walks again, if it is back on its bridge. */
    static final int KNOCK_TICKS = 8;
    /** A zombie this far under its bridge died in the void. */
    static final double VOID = 25.0;
    /** Ticks before the next zombie, after a win or a loss: at once, or nearly. */
    static final int NEXT_TICKS = 10;
    /** How far the zombie walks in a tick: a zombie's pace, a little more than 2 blocks a second. */
    static final double STEP = 0.115;

    static final Variant LEVEL_1 = new Variant("straight", "Level 1: straight bridge", false, "wins in a row");
    static final Variant LEVEL_2 = new Variant("diagonal", "Level 2: diagonal bridge", false, "wins in a row");
    static final Variant LEVEL_3 = new Variant("high", "Level 3: high bridge", false, "wins in a row");
    static final Variant[] LEVELS = {LEVEL_1, LEVEL_2, LEVEL_3};

    private final Course course;
    private Zombie zombie;
    /** True once the first arrow hit the zombie (it does not count). */
    private boolean knocked;
    private int knockedAt = -100;
    private int airHits;
    private int streak;
    private int wins;
    private int losses;
    private boolean waiting;

    BowPractice(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, Course course) {
        super(host, mode, arena, teams, LEVELS);
        this.course = course;
    }

    private Level level() {
        return course.levels[Math.min(variant, course.levels.length - 1)];
    }

    @Override
    String describe() {
        return "a zombie comes along the bridge: knock it off with an arrow, then hit it " + AIR_HITS
                + " times in the air before it dies in the void.";
    }

    @Override
    void build() {
        if (course.copy != null) {
            course.copy.build(arena);
            return;
        }
        for (Level level : course.levels) {
            double[] at = level.player;
            int px = (int) Math.floor(at[0]);
            int py = (int) Math.floor(at[1]) - 1;
            int pz = (int) Math.floor(at[2]);
            arena.fill(px - ZONE / 2, py, pz - ZONE / 2, px + ZONE / 2, py, pz + ZONE / 2, Material.GRASS, 0);
            double[] way = level.way();
            int steps = (int) Math.ceil(level.length());
            for (int k = 0; k <= steps; k++) {
                int x = level.first[0] + (int) Math.round(way[0] * k);
                int z = level.first[2] + (int) Math.round(way[1] * k);
                arena.put(x, level.first[1], z, Material.WOOL, Shapes.CYAN);
            }
        }
    }

    @Override
    Location spawnOf(UUID player) {
        double[] at = level().player;
        Location location = arena.at(at[0], at[1], at[2], (float) at[3]);
        location.setPitch((float) at[4]);
        return location;
    }

    @Override
    double fallY() {
        return arena.floorY() + level().player[1] - 20;
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {new ItemStack(Material.BOW), new ItemStack(Material.ARROW)};
    }

    /** The bow never runs out of arrows (Infinity), Power IV. */
    @Override
    void afterKit(Player player) {
        ItemStack[] items = player.getInventory().getContents();
        for (ItemStack item : items) {
            if (item != null && item.getType() == Material.BOW) {
                item.addUnsafeEnchantment(Enchantment.ARROW_INFINITE, 1);
                item.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 4);
            }
        }
        player.getInventory().setContents(items);
        super.afterKit(player);
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        removeZombie();
        streak = 0;
        wins = 0;
        losses = 0;
        waiting = false;
    }

    @Override
    void onFight() {
        spawnZombie();
    }

    @Override
    void ended() {
        removeZombie();
    }

    private void removeZombie() {
        if (zombie != null) {
            Bots.remove(this, zombie);
            zombie = null;
        }
        knocked = false;
        airHits = 0;
    }

    private void spawnZombie() {
        removeZombie();
        waiting = false;
        Level level = level();
        double[] way = level.way();
        Location at = arena.at(level.first[0] + 0.5, level.first[1] + 1, level.first[2] + 0.5,
                (float) Math.toDegrees(Math.atan2(-way[0], way[1])));
        zombie = Bots.spawn(this, at, "Zombie", Color.GREEN, 0);
        // it never walks by itself: the game moves it along its bridge
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255, false, false), true);
    }

    @Override
    boolean mayTarget(Entity bot, Entity target) {
        return false;
    }

    @Override
    void tick() {
        refresh();
        Player player = player();
        if (player == null) {
            return;
        }
        keepInZone(player);
        if (zombie == null || waiting) {
            return;
        }
        Level level = level();
        double[] at = arena.offset(zombie.getLocation());
        if (!zombie.isValid() || at[1] < level.first[1] + 1 - VOID) {
            lost(player, knocked ? "Not hit " + AIR_HITS + " times in the air." : "It fell.");
            return;
        }
        if (tickCount - knockedAt < KNOCK_TICKS || !zombie.isOnGround()) {
            return;
        }
        if (at[1] < level.first[1] + 0.5) {
            // under its bridge, on something: it is falling past it, let it go
            return;
        }
        // back on its bridge: it walks again, the hits start over
        knocked = false;
        airHits = 0;
        double along = level.along(at[0], at[2]);
        if (along >= level.length() - 0.3) {
            lost(player, "It got to the end of the bridge.");
            return;
        }
        double[] way = level.way();
        double aside = level.aside(at[0], at[2]);
        double vx = way[0] * STEP + way[1] * aside * 0.3;
        double vz = way[1] * STEP - way[0] * aside * 0.3;
        zombie.setVelocity(new Vector(vx, zombie.getVelocity().getY(), vz));
    }

    /** The player stays on the square around their start: back on its edge when they step out. */
    private void keepInZone(Player player) {
        double[] start = level().player;
        double[] at = arena.offset(player.getLocation());
        double half = ZONE / 2.0;
        double cx = Math.floor(start[0]) + 0.5;
        double cz = Math.floor(start[2]) + 0.5;
        double x = clamp(at[0], cx - half + 0.3, cx + half - 0.3);
        double z = clamp(at[2], cz - half + 0.3, cz + half - 0.3);
        if (x != at[0] || z != at[2]) {
            Location back = arena.at(x, at[1], z, player.getLocation().getYaw());
            back.setPitch(player.getLocation().getPitch());
            player.teleport(back);
        }
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** The zombie got the win: the sound of a loss, the streak over, a new zombie at once. */
    private void lost(Player player, String why) {
        removeZombie();
        losses++;
        player.sendMessage(ChatColor.RED + "Missed! " + ChatColor.GRAY + why + " Streak: " + ChatColor.WHITE + streak);
        sound(player, Sound.VILLAGER_NO, 1f);
        if (streak > 0) {
            score(streak);
        }
        streak = 0;
        next();
    }

    /** The hits in the air are there: the sound of a win, the streak up, a new zombie at once. */
    private void won(Player player) {
        removeZombie();
        wins++;
        streak++;
        player.sendMessage(ChatColor.GREEN + "Nice shot! " + ChatColor.GRAY + "Streak: " + ChatColor.WHITE + streak);
        sound(player, Sound.LEVEL_UP, 1.2f);
        score(streak, true);
        next();
    }

    private void next() {
        waiting = true;
        final int roundNow = round;
        later(NEXT_TICKS, () -> {
            if (round == roundNow && waiting) {
                spawnZombie();
            }
        });
    }

    @Override
    void launched(Player player, Projectile projectile, ProjectileLaunchEvent event) {
        if (projectile instanceof Arrow) {
            // the bow does not wear out
            later(1L, () -> {
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() == Material.BOW) {
                        item.setDurability((short) 0);
                    }
                }
            });
        }
    }

    /** An arrow on the zombie: its knockback, no damage. The first knocks it off, the next ones count in the air. */
    @Override
    void hitEntity(Player attacker, Entity target, EntityDamageEvent event) {
        if (zombie == null || !zombie.equals(target) || event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(0.0);
        if (!knocked) {
            knocked = true;
            knockedAt = tickCount;
            sound(attacker, Sound.SUCCESSFUL_HIT, 1f);
            return;
        }
        knockedAt = tickCount;
        airHits++;
        sound(attacker, Sound.SUCCESSFUL_HIT, 1.2f + 0.2f * airHits);
        if (airHits >= AIR_HITS) {
            won(attacker);
        }
    }

    @Override
    void entityHurt(Entity entity, EntityDamageEvent event) {
        event.setCancelled(true);
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return false;
    }

    /** A fall from the start: back on it, the zombie goes on. */
    @Override
    void fell(Player player) {
        player.teleport(spawnOf(player.getUniqueId()));
        player.setFallDistance(0f);
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
        lines.add(ChatColor.WHITE + "Wins: " + ChatColor.GREEN + wins + ChatColor.GRAY + " · Missed: " + ChatColor.RED + losses);
        lines.add(ChatColor.WHITE + "Air hits: " + ChatColor.YELLOW + (knocked ? airHits : 0) + ChatColor.GRAY + "/" + AIR_HITS);
    }

    /** The levels of a course read from bow-practice in config.yml, from its origin (for the tests too). */
    static Level[] levels(List<double[]> players, List<int[]> bridges, int[] origin) {
        List<Level> levels = new ArrayList<>();
        for (int i = 0; i < Math.min(players.size(), bridges.size()); i++) {
            double[] p = players.get(i);
            int[] b = bridges.get(i);
            levels.add(new Level(new double[] {p[0] - origin[0], p[1] - origin[1], p[2] - origin[2], p[3], p[4]},
                    new int[] {b[0] - origin[0], b[1] - origin[1], b[2] - origin[2]},
                    new int[] {b[3] - origin[0], b[4] - origin[2]}));
        }
        return levels.toArray(new Level[0]);
    }
}
