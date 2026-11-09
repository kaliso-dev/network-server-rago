package gg.rago.lobby;

import gg.rago.lobby.api.FireballAim;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

/**
 * The toys of the lobby hotbar. The fireball flies where the player looks, as in Bed Wars, and its
 * blast pushes the players around it as on Hypixel (a fireball under one's own feet is a fireball
 * jump); it hurts no one and breaks nothing. The teleport stick takes the player onto the block they
 * look at, {@link #REACH} blocks away at most, never through a wall: the first block in the way is
 * the one they land on (or next to, when there is no room on it); looking at the air, {@link #REACH}
 * blocks ahead, in the air. Once every two seconds. Main thread only.
 */
final class LobbyGadgets implements Listener {

    /** How far the teleport stick reaches, in blocks. */
    static final int REACH = 20;
    /** Half a second between two fireballs, as on Hypixel. */
    static final long FIREBALL_COOLDOWN_MS = 500L;
    static final long TELEPORT_COOLDOWN_MS = 2000L;
    /** From the eyes to the feet of a standing player. */
    static final double EYE_HEIGHT = 1.62;
    /** The blast of Hypixel's fireball (BedWars1058's values): who it reaches, how far it pushes. */
    static final double RADIUS = 3.0;
    static final double HORIZONTAL = 1.0;
    static final double VERTICAL = 0.65;
    /** A fireball that hits nothing goes after five seconds. */
    private static final long FIREBALL_LIFE_TICKS = 100L;

    private final Plugin plugin;
    private final Map<UUID, Long> lastFireball = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();
    /** The fireballs of the lobby in flight, by entity id. */
    private final Set<UUID> fireballs = new HashSet<>();

    LobbyGadgets(Plugin plugin) {
        this.plugin = plugin;
    }

    void forget(UUID player) {
        lastFireball.remove(player);
        lastTeleport.remove(player);
    }

    /** The player throws a fireball where they look; the item stays in the hotbar. */
    void throwFireball(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastFireball.get(player.getUniqueId());
        if (last != null && now - last < FIREBALL_COOLDOWN_MS) {
            return;
        }
        lastFireball.put(player.getUniqueId(), now);
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setIsIncendiary(false);
        fireball.setYield(0f);
        Vector direction = player.getEyeLocation().getDirection();
        // not Fireball#setDirection: on 1.8 it makes the fireball ten times too fast
        FireballAim.aim(fireball, direction);
        fireball.setVelocity(direction.clone().multiply(1.0));
        UUID id = fireball.getUniqueId();
        fireballs.add(id);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (fireballs.remove(id) && !fireball.isDead()) {
                fireball.remove();
            }
        }, FIREBALL_LIFE_TICKS);
    }

    /** The blast of a lobby fireball: the players around are pushed, nothing else happens. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (!fireballs.remove(event.getEntity().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        Location blast = event.getEntity().getLocation();
        World world = blast.getWorld();
        world.playEffect(blast, Effect.EXPLOSION_LARGE, 0);
        world.playSound(blast, Sound.EXPLODE, 1f, 1f);
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            if (!reaches(at.getX() - blast.getX(), at.getY() - blast.getY(), at.getZ() - blast.getZ())) {
                continue;
            }
            double[] push = push(blast.getX(), blast.getY(), blast.getZ(), at.getX(), at.getY(), at.getZ());
            player.setVelocity(new Vector(push[0], push[1], push[2]));
            player.setFallDistance(0f);
        }
    }

    /** A lobby fireball that hits a player straight on hurts no one. */
    @EventHandler(priority = EventPriority.LOW)
    public void onHit(EntityDamageByEntityEvent event) {
        if (fireballs.contains(event.getDamager().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Removes the fireballs in flight (the plugin stops). */
    void clear() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Fireball.class)) {
                if (fireballs.contains(entity.getUniqueId())) {
                    entity.remove();
                }
            }
        }
        fireballs.clear();
    }

    /** The teleport stick: onto the block the player looks at if it is near enough, else ahead in the air. */
    void teleport(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastTeleport.get(player.getUniqueId());
        if (last != null && now - last < TELEPORT_COOLDOWN_MS) {
            long left = TELEPORT_COOLDOWN_MS - (now - last);
            player.sendMessage(ChatColor.RED + "Wait " + String.format(java.util.Locale.ROOT, "%.1f", left / 1000.0)
                    + "s before teleporting again.");
            return;
        }
        List<int[]> ray = new ArrayList<>();
        Iterator<Block> blocks = new BlockIterator(player, REACH);
        while (blocks.hasNext() && ray.size() <= REACH + 1) {
            Block block = blocks.next();
            ray.add(new int[] {block.getX(), block.getY(), block.getZ()});
        }
        World world = player.getWorld();
        Predicate<int[]> passable = place -> place[1] < 0 || place[1] > 255
                || !world.getBlockAt(place[0], place[1], place[2]).getType().isSolid();
        int[] feet = destination(ray, passable);
        Location from = player.getLocation();
        Location to;
        if (feet != null) {
            to = new Location(world, feet[0] + 0.5, feet[1], feet[2] + 0.5, from.getYaw(), from.getPitch());
        } else if (allPassable(ray, passable)) {
            Location eye = player.getEyeLocation();
            Vector way = eye.getDirection();
            double[] air = airDestination(new double[] {eye.getX(), eye.getY(), eye.getZ()},
                    new double[] {way.getX(), way.getY(), way.getZ()}, REACH, passable);
            if (air == null) {
                player.sendMessage(ChatColor.GRAY + "No room to teleport to.");
                return;
            }
            to = new Location(world, air[0], air[1], air[2], from.getYaw(), from.getPitch());
        } else {
            player.sendMessage(ChatColor.GRAY + "No room to teleport to.");
            return;
        }
        lastTeleport.put(player.getUniqueId(), now);
        world.playEffect(from, Effect.ENDER_SIGNAL, 0);
        player.setFallDistance(0f);
        player.teleport(to);
        world.playSound(to, Sound.ENDERMAN_TELEPORT, 0.8f, 1.2f);
    }

    /**
     * Where the teleport stick takes the player: the block of the feet, or null. The ray holds the
     * blocks along the look, from the eyes on; the first block that is not passable is the one
     * looked at, and nothing beyond it counts (no going through a wall). On top of it when there is
     * room for a player there and the look did not come from under it; else in the last free block
     * before it, standing in it or just under it.
     */
    static int[] destination(List<int[]> ray, Predicate<int[]> passable) {
        int[] previous = null;
        for (int[] block : ray) {
            if (passable.test(block)) {
                previous = block;
                continue;
            }
            if (previous == null) {
                // the player's eyes are in the block: nowhere to go
                return null;
            }
            boolean fromBelow = previous[1] < block[1] && previous[0] == block[0] && previous[2] == block[2];
            int[] top = {block[0], block[1] + 1, block[2]};
            if (!fromBelow && room(top, passable)) {
                return top;
            }
            if (room(previous, passable)) {
                return previous;
            }
            int[] under = {previous[0], previous[1] - 1, previous[2]};
            if (room(under, passable)) {
                return under;
            }
            return null;
        }
        return null;
    }

    private static boolean allPassable(List<int[]> ray, Predicate<int[]> passable) {
        for (int[] block : ray) {
            if (!passable.test(block)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Where the stick takes a player who looks at the air: the eyes {@code reach} blocks ahead along
     * the look (eye, unit way), or nearer when the body would be in a block there; null when there
     * is no room on the whole way. The feet: x, y, z.
     */
    static double[] airDestination(double[] eye, double[] way, int reach, Predicate<int[]> passable) {
        for (double distance = reach; distance >= 1; distance -= 0.5) {
            double x = eye[0] + way[0] * distance;
            double y = eye[1] + way[1] * distance - EYE_HEIGHT;
            double z = eye[2] + way[2] * distance;
            int[] feet = {(int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)};
            int[] head = {feet[0], (int) Math.floor(y + 1.8), feet[2]};
            if (passable.test(feet) && passable.test(head)
                    && passable.test(new int[] {feet[0], feet[1] + 1, feet[2]})) {
                return new double[] {x, y, z};
            }
        }
        return null;
    }

    /** True if a player stands in these feet: the block and the one above are passable. */
    private static boolean room(int[] feet, Predicate<int[]> passable) {
        return passable.test(feet) && passable.test(new int[] {feet[0], feet[1] + 1, feet[2]});
    }

    /**
     * The velocity the blast at (bx, by, bz) gives a player standing at (px, py, pz), as
     * BedWars1058 computes it: away from the blast horizontally, and up; more up when the blast is
     * under the player's feet (a fireball jump).
     */
    static double[] push(double bx, double by, double bz, double px, double py, double pz) {
        double dx = bx - px;
        double dy = by - py;
        double dz = bz - pz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-6) {
            dx = 0;
            dy = 1;
            dz = 0;
            length = 1;
        }
        double nx = dx / length;
        double ny = dy / length;
        double nz = dz / length;
        double y = ny < 0 ? ny + 1.5 : ny;
        y = y <= 0.5 ? VERTICAL * 1.5 : y * VERTICAL * 1.5;
        return new double[] {-nx * HORIZONTAL, y, -nz * HORIZONTAL};
    }

    /** True if a player at this offset from the blast is pushed by it. */
    static boolean reaches(double dx, double dy, double dz) {
        return Math.abs(dx) <= RADIUS && Math.abs(dy) <= RADIUS && Math.abs(dz) <= RADIUS;
    }
}
