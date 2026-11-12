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
 * Bow training on a real Bed Wars map: the red base of Archway and the north of its middle
 * (bow-training in config.yml), copied into the zone. The player stands on the front of the base
 * with a bow (Infinity; Power I as a setting). Bots bridge from the middle towards the base, a
 * block of wool at each step, as a rusher does. An arrow pushes a bot away from the shooter, so
 * from the side it falls off its bridge, which is a kill; a bot that reaches the base ends the
 * attempt. The score is the kills; the sidebar shows the hits, the accuracy and the longest shot.
 * Settings: how fast the bots bridge, and how many come at once.
 */
final class BowTraining extends Training {

    /** The place: the copy of the map (or null: plain platforms), the player's start and the bridges. */
    static final class Course {
        final MapCopy copy;
        /** x, y, z, yaw of the player's start, from the origin. */
        final double[] player;
        /** The height of the blocks of the bridges, from the origin. */
        final int bridgeY;
        /** Each bridge: x, z of its first block (on the middle) and of its last one (by the base). */
        final int[][] lines;

        Course(MapCopy copy, double[] player, int bridgeY, int[][] lines) {
            this.copy = copy;
            this.player = player;
            this.bridgeY = bridgeY;
            this.lines = lines;
        }
    }

    /**
     * The course without the map: the same places as on Archway, from its origin (-13, 64, -42):
     * the base to the north (z below -19), the middle to the south, three bridges between.
     */
    static Course plainCourse() {
        return new Course(null, new double[] {0.5, 2, -22.5, 0}, 0,
                new int[][] {{0, 20, 0, -19}, {4, 18, 4, -19}, {-3, 22, -3, -19}});
    }

    /** A bot and where it is in its bridge. */
    private static final class Bot {
        final Zombie zombie;
        final int line;
        int knockedAt = -100;
        final List<Block> blocks = new ArrayList<>();

        Bot(Zombie zombie, int line) {
            this.zombie = zombie;
            this.line = line;
        }
    }

    /** Ticks a bot flies after an arrow, before it bridges again (if it is still on its bridge). */
    static final int KNOCK_TICKS = 8;
    /** A bot this far under its bridge has fallen off. */
    static final double FALLEN = 3.0;
    /** Ticks before a new bot comes on the bridge of one that fell. */
    private static final int NEXT_BOT_TICKS = 40;
    private static final int WOOL = Shapes.BLUE;

    private final Course course;
    private final Setting speed;
    private final Setting count;
    private final Setting power;
    private final List<Bot> bots = new ArrayList<>();
    private int kills;
    private int hits;
    private int shots;
    private double longest;
    private boolean over;

    /** The bots bridging to the player's base, shot down. */
    static final Variant BOTS = new Variant("bots", "Bots bridging to your base", false, "kills");

    BowTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, Course course) {
        super(host, mode, arena, teams, BOTS);
        this.course = course;
        this.speed = setting("Bridging speed", 1, 5, 2);
        this.count = setting("Bots", 1, Math.min(3, course.lines.length), 1);
        this.power = toggle("Power I", false);
    }

    /** How far a bot bridges in a tick: from 2.6 blocks a second (1) to 5 (5). */
    static double step(int speed) {
        return (2.0 + 0.6 * speed) / 20.0;
    }

    @Override
    String describe() {
        return "bots bridge from the middle to your base: knock them off with your bow before one gets there."
                + " An arrow pushes away from you: shoot them from the side.";
    }

    @Override
    void build() {
        if (course.copy != null) {
            course.copy.build(arena);
            return;
        }
        int y = course.bridgeY;
        arena.fill(-7, y + 1, -27, 7, y + 1, -19, Material.STAINED_CLAY, Shapes.RED);
        arena.fill(-8, y, 17, 8, y, 26, Material.STAINED_CLAY, Shapes.WHITE);
    }

    @Override
    Location spawnOf(UUID player) {
        double[] at = course.player;
        return arena.at(at[0], at[1], at[2], (float) at[3]);
    }

    @Override
    double fallY() {
        return arena.floorY() + course.bridgeY - 20;
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {new ItemStack(Material.BOW), new ItemStack(Material.ARROW)};
    }

    /** The bow never runs out of arrows (Infinity), with Power I when the setting is on. */
    @Override
    void afterKit(Player player) {
        enchantBows(player, power.on());
        super.afterKit(player);
    }

    static void enchantBows(Player player, boolean power) {
        ItemStack[] items = player.getInventory().getContents();
        for (ItemStack item : items) {
            if (item != null && item.getType() == Material.BOW) {
                item.addUnsafeEnchantment(Enchantment.ARROW_INFINITE, 1);
                if (power) {
                    item.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 1);
                }
            }
        }
        player.getInventory().setContents(items);
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        for (Bot bot : bots) {
            Bots.remove(this, bot.zombie);
        }
        bots.clear();
        kills = 0;
        hits = 0;
        shots = 0;
        longest = 0;
        over = false;
    }

    @Override
    void onFight() {
        for (int i = 0; i < count.value; i++) {
            final int line = i;
            final int roundNow = round;
            later(20L * i + 20L, () -> {
                if (round == roundNow && !over) {
                    spawnBot(line);
                }
            });
        }
    }

    @Override
    void ended() {
        for (Bot bot : bots) {
            Bots.remove(this, bot.zombie);
        }
        bots.clear();
    }

    private void spawnBot(int line) {
        int[] l = course.lines[line];
        double[] way = way(l);
        Location at = arena.at(l[0] + 0.5, course.bridgeY + 1, l[1] + 0.5, (float) Math.toDegrees(Math.atan2(-way[0], way[1])));
        Zombie zombie = Bots.spawn(this, at, "Rusher", Color.BLUE, 0);
        // it never walks by itself: the game moves it along its bridge
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255, false, false), true);
        bots.add(new Bot(zombie, line));
    }

    /** The way along a bridge, x and z, length 1. */
    static double[] way(int[] line) {
        double dx = line[2] - line[0];
        double dz = line[3] - line[1];
        double length = Math.max(1e-9, Math.hypot(dx, dz));
        return new double[] {dx / length, dz / length};
    }

    /** How far along its bridge a point (x, z) is, from the middle of its first block. */
    static double along(int[] line, double x, double z) {
        double[] way = way(line);
        return (x - line[0] - 0.5) * way[0] + (z - line[1] - 0.5) * way[1];
    }

    /** How far to the side of its bridge a point (x, z) is (to the right of the way, positive). */
    static double aside(int[] line, double x, double z) {
        double[] way = way(line);
        return -(x - line[0] - 0.5) * way[1] + (z - line[1] - 0.5) * way[0];
    }

    /** The length of a bridge, from the middle of its first block to the middle of its last. */
    static double length(int[] line) {
        return Math.hypot(line[2] - line[0], line[3] - line[1]);
    }

    @Override
    boolean mayTarget(Entity bot, Entity target) {
        return false;
    }

    @Override
    void tick() {
        refresh();
        if (over || player() == null) {
            return;
        }
        for (Bot bot : new ArrayList<>(bots)) {
            if (!bot.zombie.isValid()) {
                fallen(bot);
                continue;
            }
            double[] at = arena.offset(bot.zombie.getLocation());
            if (at[1] < course.bridgeY + 1 - FALLEN) {
                fallen(bot);
                continue;
            }
            if (tickCount - bot.knockedAt < KNOCK_TICKS || !bot.zombie.isOnGround()) {
                continue;
            }
            int[] line = course.lines[bot.line];
            double along = along(line, at[0], at[2]);
            if (along >= length(line) - 0.3) {
                reached(bot);
                return;
            }
            bridge(bot, line, along, aside(line, at[0], at[2]));
        }
    }

    /** A step of the bot along its bridge: the wool ahead of it, and on it goes. */
    private void bridge(Bot bot, int[] line, double along, double aside) {
        double[] way = way(line);
        int last = (int) Math.floor(length(line));
        for (int k = Math.max(0, (int) Math.floor(along)); k <= Math.min(last, (int) Math.floor(along + 1.3)); k++) {
            int x = line[0] + (int) Math.round(way[0] * k);
            int z = line[1] + (int) Math.round(way[1] * k);
            Block block = arena.block(x, course.bridgeY, z);
            if (block.getType() == Material.AIR) {
                arena.put(x, course.bridgeY, z, Material.WOOL, WOOL);
                bot.blocks.add(block);
            }
        }
        double step = step(speed.value);
        // back to the middle of the bridge when it drifted
        double vx = way[0] * step + way[1] * aside * 0.3;
        double vz = way[1] * step - way[0] * aside * 0.3;
        bot.zombie.setVelocity(new Vector(vx, bot.zombie.getVelocity().getY(), vz));
    }

    /** The bot fell off its bridge: a kill, and a new one on that bridge, built again. */
    private void fallen(Bot bot) {
        bots.remove(bot);
        Bots.remove(this, bot.zombie);
        for (Block block : bot.blocks) {
            if (arena.isPlaced(block)) {
                arena.restore(block);
            }
        }
        kills++;
        Player player = player();
        if (player != null) {
            sound(player, Sound.ORB_PICKUP, 1.2f);
            player.sendMessage(ChatColor.GREEN + "Knocked off! " + ChatColor.GRAY + "Kills: " + ChatColor.WHITE + kills);
        }
        final int line = bot.line;
        final int roundNow = round;
        later(NEXT_BOT_TICKS, () -> {
            if (round == roundNow && !over) {
                spawnBot(line);
            }
        });
    }

    /** A bot got to the base: the attempt is over. */
    private void reached(Bot bot) {
        over = true;
        Player player = player();
        if (player != null) {
            player.sendMessage(ChatColor.RED + "A bot reached your base! " + ChatColor.GRAY + "Kills: " + ChatColor.WHITE + kills
                    + ChatColor.GRAY + ", hits: " + hits + ", accuracy: " + accuracy(hits, shots) + "%, longest shot: "
                    + Math.round(longest) + " blocks.");
            sound(player, Sound.VILLAGER_NO, 1f);
        }
        score(kills);
        again(50L);
    }

    /** The share of the arrows that hit, in percent. */
    static int accuracy(int hits, int shots) {
        return shots == 0 ? 0 : (int) Math.round(100.0 * Math.min(hits, shots) / shots);
    }

    @Override
    void launched(Player player, Projectile projectile, ProjectileLaunchEvent event) {
        if (projectile instanceof Arrow) {
            shots++;
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

    /** An arrow on a bot: its knockback, no damage; anything else does nothing. */
    @Override
    void hitEntity(Player attacker, Entity target, EntityDamageEvent event) {
        Bot bot = null;
        for (Bot each : bots) {
            if (each.zombie.equals(target)) {
                bot = each;
            }
        }
        if (bot == null || over || event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(0.0);
        bot.knockedAt = tickCount;
        hits++;
        double distance = attacker.getLocation().distance(target.getLocation());
        if (distance > longest) {
            longest = distance;
        }
        sound(attacker, Sound.SUCCESSFUL_HIT, 1f);
    }

    @Override
    void entityHurt(Entity entity, EntityDamageEvent event) {
        event.setCancelled(true);
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return false;
    }

    /** A fall from the base: back on it, the attempt goes on. */
    @Override
    void fell(Player player) {
        player.sendMessage(ChatColor.RED + "You fell: back on your base.");
        player.teleport(spawnOf(player.getUniqueId()));
        player.setFallDistance(0f);
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Kills: " + ChatColor.GREEN + kills);
        lines.add(ChatColor.WHITE + "Hits: " + ChatColor.GREEN + hits + ChatColor.GRAY + " (" + accuracy(hits, shots) + "%)");
        lines.add(ChatColor.WHITE + "Longest shot: " + ChatColor.GREEN + Math.round(longest) + ChatColor.GRAY + " blocks");
    }
}
