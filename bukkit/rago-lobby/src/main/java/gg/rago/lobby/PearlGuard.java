package gg.rago.lobby;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * The 1.8 pearl glitch, for every mode of the server: a pearl still in the air when its thrower
 * dies, respawns or is sent back to a spawn still lands, and takes them from their spawn (or the
 * spectators' point) to where it fell. Here such a pearl goes as soon as its thrower is moved. A
 * teleport of the server itself (a move corrected by its checks) does not count.
 */
final class PearlGuard implements Listener {

    private final Map<UUID, List<EnderPearl>> inFlight = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl) || !(event.getEntity().getShooter() instanceof Player)) {
            return;
        }
        UUID thrower = ((Player) event.getEntity().getShooter()).getUniqueId();
        List<EnderPearl> pearls = inFlight.get(thrower);
        if (pearls == null) {
            pearls = new ArrayList<>(2);
            inFlight.put(thrower, pearls);
        }
        for (Iterator<EnderPearl> it = pearls.iterator(); it.hasNext();) {
            if (!it.next().isValid()) {
                it.remove();
            }
        }
        pearls.add((EnderPearl) event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (moves(event.getCause())) {
            drop(event.getPlayer());
        }
    }

    /** Teleports by a plugin or a command: a spawn, a respawn, the spectators' point, a /tp. */
    static boolean moves(PlayerTeleportEvent.TeleportCause cause) {
        return cause == PlayerTeleportEvent.TeleportCause.PLUGIN || cause == PlayerTeleportEvent.TeleportCause.COMMAND;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        drop(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        drop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        drop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        drop(event.getPlayer());
    }

    private void drop(Player player) {
        List<EnderPearl> pearls = inFlight.remove(player.getUniqueId());
        if (pearls == null) {
            return;
        }
        for (EnderPearl pearl : pearls) {
            if (pearl.isValid()) {
                pearl.remove();
            }
        }
    }
}
