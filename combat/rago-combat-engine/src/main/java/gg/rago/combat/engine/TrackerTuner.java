package gg.rago.combat.engine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

/**
 * How often the server sends each player's position to the others (config.yml,
 * player-tracker-ticks). Minecraft 1.8.8 sends it every 2 ticks: the others see a player up to
 * 100 ms late, and aim at where they were. Every tick, as practice servers do, halves that delay:
 * hits land where the player is seen. The knockback itself is not touched.
 *
 * <p>The 1.8.8 API has no setting for it: the server's own tracker entry of each player is changed
 * by reflection, when they join, change world or respawn (each makes a new entry). If the server
 * is not the one expected, nothing is changed and the log says it once.
 */
final class TrackerTuner implements Listener {

    /** What Minecraft 1.8.8 uses for players. */
    static final int VANILLA_TICKS = 2;

    private final Plugin plugin;
    private final Logger logger;
    private final int ticks;
    private boolean broken;
    private Method handle;
    private Field tracker;
    private Field entries;
    private Method get;
    private Field interval;

    TrackerTuner(Plugin plugin, int ticks) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.ticks = ticks;
    }

    /** "player-tracker-ticks": 1 or 2; 2 when missing. */
    static int read(org.bukkit.configuration.ConfigurationSection config) {
        int value = config.getInt("player-tracker-ticks", VANILLA_TICKS);
        if (value < 1 || value > VANILLA_TICKS) {
            throw new IllegalArgumentException("player-tracker-ticks: 1 (every tick) or 2 (vanilla), not " + value);
        }
        return value;
    }

    boolean active() {
        return ticks != VANILLA_TICKS;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        later(event.getPlayer());
    }

    /** The entry exists once the player is in the world: a tick later. */
    private void later(final Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                apply(player);
            }
        }, 1L);
    }

    void apply(Player player) {
        if (broken) {
            return;
        }
        try {
            if (handle == null) {
                find(player);
            }
            Object world = handle.invoke(player.getWorld());
            Object entry = get.invoke(entries.get(tracker.get(world)), player.getEntityId());
            if (entry != null) {
                interval.setInt(entry, ticks);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            // a plain warning: a server without this tracker is not an error of the plugin
            logger.warning("player-tracker-ticks cannot be applied on this server, players are tracked every "
                    + VANILLA_TICKS + " ticks: " + String.valueOf(e.getMessage()).replace("Exception", "problem"));
        }
    }

    private void find(Player player) throws ReflectiveOperationException {
        handle = player.getWorld().getClass().getMethod("getHandle");
        Object world = handle.invoke(player.getWorld());
        tracker = field(world.getClass(), "tracker");
        Object entityTracker = tracker.get(world);
        entries = field(entityTracker.getClass(), "trackedEntities");
        Object map = entries.get(entityTracker);
        get = map.getClass().getMethod("get", int.class);
        String root = entityTracker.getClass().getPackage().getName();
        interval = Class.forName(root + ".EntityTrackerEntry").getDeclaredField("c");
        interval.setAccessible(true);
    }

    /** A field of the class or of one of its superclasses. */
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> at = type; at != null; at = at.getSuperclass()) {
            try {
                Field field = at.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                // look in the superclass
            }
        }
        throw new NoSuchFieldException(name);
    }
}
