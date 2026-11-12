package gg.rago.practice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * The players who watch a game without playing it (the lobby's /spectate): in the spectator mode,
 * which the players of the game do not see, flying near the game and touching nothing. A left
 * click on a player shows the game through their eyes; the spectator menu (the number keys) only
 * takes them to the players near the game. The game tells them what it tells its players, shows
 * them the sidebar of the player they came for, and gives them back to the lobby at its end. A
 * game of a practice mode or a sumo duel. Main thread only.
 */
final class Spectators implements Listener {

    /** A game that can be watched. */
    interface Watched {
        /** True if a watcher may be there: near the game. */
        boolean holds(Location location);

        /** Where a watcher who goes too far comes back, looking at the game. */
        Location watchPoint();
    }

    /** Who watches which game, for which of its players. */
    private static final class Watcher {
        final Watched game;
        final UUID target;

        Watcher(Watched game, UUID target) {
            this.game = game;
            this.target = target;
        }
    }

    private final RagoPractice plugin;
    private final Map<UUID, Watcher> watchers = new HashMap<>();

    Spectators(RagoPractice plugin) {
        this.plugin = plugin;
    }

    /** The player watches this game from now on, from where its player stands (or from its watch point). */
    void watch(Player watcher, Watched game, Player target) {
        watcher.closeInventory();
        watcher.getInventory().clear();
        watcher.getInventory().setArmorContents(null);
        watcher.updateInventory();
        PracticeGame.heal(watcher);
        watcher.setGameMode(GameMode.SPECTATOR);
        Location at = target.getLocation();
        watcher.teleport(game.holds(at) ? at : game.watchPoint());
        watchers.put(watcher.getUniqueId(), new Watcher(game, target.getUniqueId()));
    }

    boolean isWatching(UUID player) {
        return watchers.containsKey(player);
    }

    /** The player of the game this one came to watch, or null if they watch nothing. */
    UUID targetOf(UUID watcher) {
        Watcher watching = watchers.get(watcher);
        return watching == null ? null : watching.target;
    }

    /** Those who watch this game, online. */
    List<Player> of(Watched game) {
        List<Player> online = new ArrayList<>();
        for (Map.Entry<UUID, Watcher> entry : watchers.entrySet()) {
            Player player = entry.getValue().game == game ? Bukkit.getPlayer(entry.getKey()) : null;
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    /** A line of the game's chat for its watchers. */
    void tell(Watched game, String line) {
        for (Player watcher : of(game)) {
            watcher.sendMessage(line);
        }
    }

    /** /spawn: the watcher goes back to the lobby. False if they watch nothing. */
    boolean leave(Player watcher) {
        if (watchers.remove(watcher.getUniqueId()) == null) {
            return false;
        }
        back(watcher);
        return true;
    }

    /**
     * A match found in a queue they wait in takes the watcher out of the game: they belong to it no
     * more, where they stand, and the match puts them in their own. False if they watch nothing.
     */
    boolean letGo(Player watcher) {
        if (watchers.remove(watcher.getUniqueId()) == null) {
            return false;
        }
        ownEyes(watcher);
        return true;
    }

    /** The game is over: its watchers go back to the lobby, before its players. */
    void ended(Watched game) {
        for (Player watcher : of(game)) {
            watchers.remove(watcher.getUniqueId());
            back(watcher);
        }
        watchers.values().removeIf(watching -> watching.game == game);
    }

    /** A player leaves the game: those who saw through their eyes go back to the watch point. */
    void playerLeft(Watched game, Player player) {
        for (Player watcher : of(game)) {
            if (player.equals(watcher.getSpectatorTarget())) {
                ownEyes(watcher);
                watcher.teleport(game.watchPoint());
            }
        }
    }

    private void back(Player watcher) {
        ownEyes(watcher);
        plugin.lobby().sendToLobby(watcher);
    }

    /** No longer through the eyes of a player. */
    private static void ownEyes(Player watcher) {
        if (watcher.getGameMode() == GameMode.SPECTATOR && watcher.getSpectatorTarget() != null) {
            watcher.setSpectatorTarget(null);
        }
    }

    /**
     * The watcher of this game, or null. One the lobby took back by other means (or whom a command
     * took out of the spectator mode) is forgotten: they watch nothing any more.
     */
    private Watcher watching(Player player) {
        Watcher watching = watchers.get(player.getUniqueId());
        if (watching != null && (player.getGameMode() != GameMode.SPECTATOR || !plugin.lobby().isInGame(player))) {
            watchers.remove(player.getUniqueId());
            return null;
        }
        return watching;
    }

    // --- The rules ------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Watcher watching = watching(event.getPlayer());
        if (watching != null && !watching.game.holds(event.getTo())) {
            event.setTo(watching.game.watchPoint());
        }
    }

    /** The spectator menu takes them to any player of the server: only to those near the game. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return;
        }
        Watcher watching = watching(event.getPlayer());
        if (watching != null && !watching.game.holds(event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (watchers.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (watchers.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (watchers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (watchers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (watchers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (watchers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (watchers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        if (watchers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Before RagoLobby forgets the player. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        watchers.remove(event.getPlayer().getUniqueId());
    }
}
