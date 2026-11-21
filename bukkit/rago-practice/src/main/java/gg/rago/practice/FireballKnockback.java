package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

/**
 * The Fireball Fight of FUN PRACTICE (plan 6.1): a round platform over the void, a little bigger
 * than the sumo's; knock the other player off with fireballs that push harder than in Bed Wars,
 * and jump out of a bad spot with the wind gust (a feather, the leap of the lobby's leap feather).
 * No damage, only the push (a hit pushes too, as in the sumo); the first off the platform gives
 * the point to the other, as in the sumo. Not the Fireball fight of the Duels ({@link
 * FireballFight}, with beds). Its settings: fireball-knockback in config.yml.
 */
final class FireballKnockback extends Duel {

    /** The settings of the mode (fireball-knockback in config.yml). */
    static final class Settings {
        /** Radius of the plugin's platform, in blocks. */
        final int radius;
        /** How many times the push of a Bed Wars fireball the others get, across and up. */
        final double across;
        final double up;
        /** Fireballs of each round; 0: as many as the player likes (one every half second). */
        final int fireballs;
        /** Seconds between two wind gusts of a player. */
        final int gustSeconds;

        Settings(int radius, double across, double up, int fireballs, int gustSeconds) {
            this.radius = radius;
            this.across = across;
            this.up = up;
            this.fireballs = fireballs;
            this.gustSeconds = gustSeconds;
        }

        /** What is wrong with these values, or null. */
        static String check(int radius, double across, double up, int fireballs, int gustSeconds) {
            if (radius < 5 || radius > 30) {
                return "rayon must be from 5 to 30";
            }
            if (across < 0.5 || across > 5 || up < 0.5 || up > 5) {
                return "recul-horizontal and recul-vertical must be from 0.5 to 5";
            }
            if (fireballs < 0 || fireballs > 64) {
                return "fireballs must be from 0 (no limit) to 64";
            }
            if (gustSeconds < 0 || gustSeconds > 60) {
                return "souffle-secondes must be from 0 to 60";
            }
            return null;
        }
    }

    /** Below the platform by this much, a player has fallen off (as in the sumo). */
    static final double FALL_DEPTH = SumoMatch.FALL_DEPTH;
    /** The names of the two items, in the kit as the player arranged it. */
    static final String FIREBALL_NAME = ChatColor.GOLD + "Fireball";
    static final String GUST_NAME = ChatColor.AQUA + "Wind gust";

    private final Settings settings;
    private final Map<UUID, Long> lastFireball = new HashMap<>();
    /** When each player may use the wind gust again (System.currentTimeMillis). */
    private final Map<UUID, Long> gustReady = new HashMap<>();

    FireballKnockback(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target, Settings settings) {
        super(host, mode, arena, teams, target);
        this.settings = settings;
    }

    @Override
    String title() {
        return "Fireball Fight";
    }

    @Override
    void intro(Player player) {
        super.intro(player);
        player.sendMessage(ChatColor.GRAY + "Knock them off with your fireballs (right click); the wind gust (the feather)"
                + " throws you up and forward. No damage: the first off the platform loses the round.");
    }

    @Override
    boolean colouredBlocks() {
        return false;
    }

    @Override
    void build() {
        if (arena.map() != null) {
            return;
        }
        Shapes.disc(arena, 0, 0, 0, settings.radius, Material.QUARTZ_BLOCK, 0);
    }

    @Override
    void roundReset() {
        super.roundReset();
        lastFireball.clear();
        gustReady.clear();
    }

    /** How far from the middle each side starts on the plugin's platform. */
    static double startDistance(int radius) {
        return Math.max(3, radius / 2);
    }

    @Override
    Location spawnOf(UUID player) {
        int side = Math.max(0, sideOf(player));
        Location start = arena.mapStart(side);
        if (start != null) {
            return start;
        }
        double distance = startDistance(settings.radius);
        double z = across(player) * 2 + 0.5;
        return side == 0 ? arena.at(0.5 - distance, 1, z, -90f) : arena.at(0.5 + distance, 1, z, 90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - FALL_DEPTH;
    }

    @Override
    Location viewPoint() {
        Location view = arena.at(0.5, 10, -(settings.radius + 8) + 0.5, 0f);
        view.setPitch(35f);
        return view;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.FIREBALL, fireballsOf(settings.fireballs)), new ItemStack(Material.FEATHER)};
    }

    /** The fireballs of a round in the kit: the limit, or a full stack when there is none. */
    static int fireballsOf(int limit) {
        return limit <= 0 ? 64 : limit;
    }

    /** The fireballs and the feather named, the fireballs as many as the settings say. */
    @Override
    void afterKit(Player player) {
        ItemStack[] items = player.getInventory().getContents();
        for (ItemStack item : items) {
            if (item == null) {
                continue;
            }
            if (item.getType() == Material.FIREBALL) {
                item.setAmount(fireballsOf(settings.fireballs));
                rename(item, FIREBALL_NAME);
            } else if (item.getType() == Material.FEATHER) {
                rename(item, GUST_NAME);
            }
        }
        player.getInventory().setContents(items);
        player.setLevel(0);
        player.setExp(1f);
    }

    private static void rename(ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
    }

    /** A hit pushes as in the sumo, without damage; nothing else hurts (no fall damage). */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        if (attacker == null || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return false;
        }
        noDamage(event);
        return true;
    }

    /** The fireballs only push (their hit still counts for the point): no damage. */
    @Override
    void blasted(Player player, Player thrower, double damage) {
    }

    /** The others are pushed harder than in Bed Wars; the thrower's own fireball jump stays as it is. */
    @Override
    double[] fireballPush(Player player, Player thrower, double[] push) {
        if (thrower == null || thrower.equals(player)) {
            return push;
        }
        return scaled(push, settings.across, settings.up);
    }

    /** The push, this many times stronger across (x and z) and up (y). */
    static double[] scaled(double[] push, double across, double up) {
        return new double[] {push[0] * across, push[1] * up, push[2] * across};
    }

    @Override
    boolean ticks() {
        return true;
    }

    /** The bar of experience shows when the wind gust is ready again. */
    @Override
    void tick() {
        long now = System.currentTimeMillis();
        for (Player player : online()) {
            Long ready = gustReady.get(player.getUniqueId());
            player.setExp(gustBar(ready == null ? 0 : ready - now, settings.gustSeconds));
        }
    }

    /** The bar of experience (0 to 1) when the gust is ready again in this many milliseconds. */
    static float gustBar(long leftMs, int gustSeconds) {
        if (leftMs <= 0 || gustSeconds <= 0) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, 1f - leftMs / (gustSeconds * 1000f)));
    }

    @Override
    void interact(Player player, PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        if (item.getType() == Material.FIREBALL) {
            event.setCancelled(true);
            throwFireball(player);
        } else if (item.getType() == Material.FEATHER) {
            event.setCancelled(true);
            gust(player);
        }
    }

    private void throwFireball(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastFireball.get(player.getUniqueId());
        if (phase != Phase.PLAYING || (last != null && now - last < Fireballs.COOLDOWN_MS)) {
            return;
        }
        lastFireball.put(player.getUniqueId(), now);
        int before = player.getItemInHand().getAmount();
        Fireball fireball = Fireballs.launch(player);
        if (fireball != null && settings.fireballs <= 0) {
            // no limit: the stack stays full
            ItemStack hand = new ItemStack(Material.FIREBALL, Math.max(1, before));
            rename(hand, FIREBALL_NAME);
            player.setItemInHand(hand);
        }
    }

    /** The wind gust: the leap of the lobby's leap feather, once every few seconds. */
    private void gust(Player player) {
        if (phase != Phase.PLAYING) {
            return;
        }
        long now = System.currentTimeMillis();
        Long ready = gustReady.get(player.getUniqueId());
        if (ready != null && now < ready) {
            long left = (ready - now + 999) / 1000;
            player.sendMessage(ChatColor.GRAY + "Wind gust ready in " + left + (left == 1 ? " second." : " seconds."));
            return;
        }
        gustReady.put(player.getUniqueId(), now + settings.gustSeconds * 1000L);
        double[] leap = leap(player.getEyeLocation().getDirection());
        player.setVelocity(new Vector(leap[0], leap[1], leap[2]));
        player.setFallDistance(0f);
        player.getWorld().playSound(player.getLocation(), Sound.BAT_TAKEOFF, 1f, 1f);
        player.getWorld().spigot().playEffect(player.getLocation(), Effect.CLOUD, 0, 0, 0.3f, 0.1f, 0.3f, 0.05f, 15, 48);
    }

    /**
     * The velocity of the gust for a player looking this way: that of the lobby's leap feather
     * (LobbyGadgets#leap), 1.6 forward across, and up at least 0.7, more when looking up.
     */
    static double[] leap(Vector look) {
        double x = look.getX();
        double z = look.getZ();
        double across = Math.sqrt(x * x + z * z);
        if (across > 1e-6) {
            x = x / across * 1.6;
            z = z / across * 1.6;
        } else {
            x = 0;
            z = 0;
        }
        return new double[] {x, Math.max(0.7, look.getY() * 1.4 + 0.5), z};
    }
}
