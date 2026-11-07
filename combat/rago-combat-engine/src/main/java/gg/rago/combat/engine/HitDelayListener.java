package gg.rago.combat.engine;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Gives every player the damage cooldown of the {@code hit-delay-ticks} setting.
 *
 * <p>The cooldown length belongs to the server's player entity. Vanilla 1.8 makes a new entity,
 * with the default of 20 ticks (a hit delay of 10), when the player respawns or changes world;
 * CraftBukkit keeps the same one. So as not to depend on the server, the length is set again one
 * tick after each of these events, as well as on join and whenever the setting changes.
 */
final class HitDelayListener implements Listener {

    private final RagoCombatEngine plugin;

    HitDelayListener(RagoCombatEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.applyHitDelay(event.getPlayer());
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        plugin.applyHitDelay(event.getPlayer());
        later(event.getPlayer());
    }

    private void later(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.applyHitDelay(player);
            }
        });
    }
}
