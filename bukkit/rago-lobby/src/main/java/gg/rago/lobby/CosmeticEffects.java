package gg.rago.lobby;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Effect;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

/**
 * The cosmetics at work in the games: the trail behind a player's projectiles, the effect where
 * their victim dies, the effect around them when they win. Nothing shows for an invisible player
 * (it would give them away), nor in the lobby, where nobody fights. Main thread only.
 */
final class CosmeticEffects implements Listener {

    /** A trail stops after this long, or once its projectile stops. */
    private static final int TRAIL_TICKS = 100;
    /** Where a victim died, kept this long for its kill effect. */
    private static final long DEATH_MEMORY_MS = 3_000L;
    private static final int WIN_SECONDS = 3;

    private static final class Trail {
        final Effect particle;
        int ticks;

        Trail(Effect particle) {
            this.particle = particle;
        }
    }

    private static final class Death {
        final Location where;
        final long when;

        Death(Location where, long when) {
            this.where = where;
            this.when = when;
        }
    }

    private final Plugin plugin;
    private final Profiles profiles;
    private final LobbyState state;
    private final Map<Projectile, Trail> trails = new HashMap<>();
    private final Map<UUID, Death> deaths = new HashMap<>();
    private BukkitTask task;

    CosmeticEffects(Plugin plugin, Profiles profiles, LobbyState state) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.state = state;
    }

    void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        trails.clear();
    }

    void forget(UUID player) {
        deaths.remove(player);
    }

    private static boolean hidden(Player player) {
        return player.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    // --- Trails ---------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileSource source = projectile.getShooter();
        if (!(source instanceof Player)) {
            return;
        }
        Player shooter = (Player) source;
        if (!state.isInGame(shooter) || hidden(shooter)) {
            return;
        }
        Cosmetic trail = profiles.equipped(shooter.getUniqueId(), Cosmetic.Type.TRAIL);
        if (trail != null) {
            trails.put(projectile, new Trail(trail.particle()));
        }
    }

    private void tick() {
        Iterator<Map.Entry<Projectile, Trail>> it = trails.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Projectile, Trail> entry = it.next();
            Projectile projectile = entry.getKey();
            Trail trail = entry.getValue();
            if (!projectile.isValid() || ++trail.ticks > TRAIL_TICKS
                    || (trail.ticks > 2 && projectile.getVelocity().lengthSquared() < 0.01)) {
                it.remove();
                continue;
            }
            Location at = projectile.getLocation();
            at.getWorld().spigot().playEffect(at, trail.particle, 0, 0, 0.05f, 0.05f, 0.05f, 0f, 2, 64);
        }
    }

    // --- Kills ----------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        deaths.put(dead.getUniqueId(), new Death(dead.getLocation(), System.currentTimeMillis()));
    }

    /** A kill counted in a game: the killer's kill effect where the victim died. */
    void kill(UUID killer, UUID victim) {
        Player by = killer == null ? null : plugin.getServer().getPlayer(killer);
        if (by == null || hidden(by)) {
            return;
        }
        Cosmetic effect = profiles.equipped(killer, Cosmetic.Type.KILL);
        if (effect == null) {
            return;
        }
        Location where = null;
        Death death = victim == null ? null : deaths.remove(victim);
        if (death != null && System.currentTimeMillis() - death.when < DEATH_MEMORY_MS) {
            where = death.where;
        } else if (victim != null && plugin.getServer().getPlayer(victim) != null) {
            where = plugin.getServer().getPlayer(victim).getLocation();
        }
        if (where == null || !where.getWorld().equals(by.getWorld())) {
            return;
        }
        playKill(effect, where.clone().add(0, 1, 0));
    }

    @SuppressWarnings("deprecation") // Material#getId: the block of the particle, as 1.8.8 takes it
    private void playKill(Cosmetic effect, Location at) {
        World world = at.getWorld();
        switch (effect.id()) {
            case "kill-blood":
                world.spigot().playEffect(at, Effect.TILE_BREAK, Material.REDSTONE_BLOCK.getId(), 0, 0.3f, 0.5f, 0.3f, 0f, 60, 64);
                world.playSound(at, Sound.HURT_FLESH, 1f, 0.8f);
                break;
            case "kill-lightning":
                world.strikeLightningEffect(at);
                break;
            case "kill-firework":
                firework(at, FireworkEffect.builder().withColor(org.bukkit.Color.RED, org.bukkit.Color.ORANGE)
                        .with(FireworkEffect.Type.BALL_LARGE).build());
                break;
            case "kill-explosion":
                world.spigot().playEffect(at, Effect.EXPLOSION_LARGE, 0, 0, 0.5f, 0.5f, 0.5f, 0f, 3, 64);
                world.playSound(at, Sound.EXPLODE, 1f, 1.2f);
                break;
            default:
                world.spigot().playEffect(at, effect.particle(), 0, 0, 0.4f, 0.6f, 0.4f, 0.05f, 25, 64);
                break;
        }
    }

    /** A firework that bursts at once and hurts nobody (1.8 fireworks do no damage). */
    private void firework(Location at, FireworkEffect effect) {
        Firework firework = (Firework) at.getWorld().spawnEntity(at, EntityType.FIREWORK);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.clearEffects();
        meta.addEffect(effect);
        meta.setPower(0);
        firework.setFireworkMeta(meta);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (firework.isValid()) {
                firework.detonate();
            }
        }, 2L);
    }

    // --- Wins -----------------------------------------------------------------------------------

    /** A player won a game: their victory effect around them for a few seconds. */
    void win(UUID winner) {
        final Player player = plugin.getServer().getPlayer(winner);
        if (player == null || hidden(player)) {
            return;
        }
        final Cosmetic effect = profiles.equipped(winner, Cosmetic.Type.WIN);
        if (effect == null) {
            return;
        }
        final int[] step = {0};
        final BukkitTask[] self = new BukkitTask[1];
        self[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || step[0]++ >= WIN_SECONDS * 4 || state.isInLobby(player)) {
                self[0].cancel();
                return;
            }
            playWin(effect, player.getLocation(), step[0]);
        }, 0L, 5L);
    }

    private void playWin(Cosmetic effect, Location base, int step) {
        World world = base.getWorld();
        switch (effect.id()) {
            case "win-fireworks":
                if (step % 4 == 1) {
                    firework(base.clone().add(0, 2, 0), FireworkEffect.builder()
                            .withColor(org.bukkit.Color.AQUA, org.bukkit.Color.WHITE, org.bukkit.Color.YELLOW)
                            .with(FireworkEffect.Type.STAR).withFlicker().build());
                }
                break;
            case "win-flames":
                for (int i = 0; i < 16; i++) {
                    double angle = 2 * Math.PI * i / 16 + step * 0.2;
                    Location at = base.clone().add(Math.cos(angle) * 1.2, 0.2, Math.sin(angle) * 1.2);
                    world.spigot().playEffect(at, Effect.FLAME, 0, 0, 0f, 0.1f, 0f, 0.01f, 1, 64);
                }
                break;
            case "win-party":
                world.spigot().playEffect(base.clone().add(0, 2.2, 0), Effect.NOTE, 0, 0, 0.8f, 0.4f, 0.8f, 1f, 6, 64);
                world.spigot().playEffect(base.clone().add(0, 1, 0), Effect.COLOURED_DUST, 0, 0, 0.8f, 0.8f, 0.8f, 1f, 20, 64);
                if (step % 2 == 0) {
                    world.playSound(base, Sound.NOTE_PLING, 1f, 0.5f + (step % 8) * 0.2f);
                }
                break;
            default:
                world.spigot().playEffect(base.clone().add(0, 2.3, 0), effect.particle(), 0, 0, 0.6f, 0.3f, 0.6f, 0f, 5, 64);
                break;
        }
    }
}
