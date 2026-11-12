package gg.rago.bedwars;

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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;

/**
 * Those who watch a game without playing it (the lobby's /spectate): in the spectator mode, which
 * the players do not see, flying in the game's world and touching nothing, not even a chest. A
 * left click on a player shows the game through their eyes; the spectator menu (the number keys)
 * only takes them to the players of that world. They read what the game tells everyone (the kills,
 * the beds, the end), talk among its spectators, see the sidebar of the player they came for, and
 * go back to the lobby at its end. Main thread only.
 */
final class Watchers implements Listener {

    /** How far under the void of the map a watcher may fly. */
    private static final int UNDER_THE_VOID = 10;

    private final RagoBedwars plugin;
    private final Map<UUID, BedwarsGame> games = new HashMap<>();
    /** The player each watcher came for. */
    private final Map<UUID, UUID> targets = new HashMap<>();

    Watchers(RagoBedwars plugin) {
        this.plugin = plugin;
    }

    /** The player watches this game from now on, from where its player stands (or from its spectators' point). */
    void watch(Player watcher, BedwarsGame game, Player target) {
        watcher.closeInventory();
        watcher.getInventory().clear();
        watcher.getInventory().setArmorContents(null);
        watcher.updateInventory();
        for (PotionEffect effect : watcher.getActivePotionEffects()) {
            watcher.removePotionEffect(effect.getType());
        }
        watcher.setHealth(watcher.getMaxHealth());
        watcher.setFoodLevel(20);
        watcher.setFireTicks(0);
        watcher.setFallDistance(0f);
        watcher.setGameMode(GameMode.SPECTATOR);
        Location at = target.getLocation();
        watcher.teleport(holds(game, at) ? at : game.watchPoint());
        games.put(watcher.getUniqueId(), game);
        targets.put(watcher.getUniqueId(), target.getUniqueId());
    }

    /** True if a watcher of this game may be there: in its world, not far under its void. */
    static boolean holds(BedwarsGame game, Location location) {
        return location.getWorld() != null && location.getWorld().equals(game.instance().world())
                && location.getY() >= game.instance().map().voidY() - UNDER_THE_VOID;
    }

    boolean isWatching(UUID player) {
        return games.containsKey(player);
    }

    /** The game this player watches, or null. */
    BedwarsGame gameOf(UUID player) {
        return games.get(player);
    }

    /** The player of the game this one came to watch, or null. */
    UUID targetOf(UUID watcher) {
        return targets.get(watcher);
    }

    /** Those who watch this game, online. */
    List<Player> of(BedwarsGame game) {
        List<Player> online = new ArrayList<>();
        for (Map.Entry<UUID, BedwarsGame> entry : games.entrySet()) {
            Player player = entry.getValue() == game ? Bukkit.getPlayer(entry.getKey()) : null;
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    /** A line of the game's chat for its watchers. */
    void tell(BedwarsGame game, String line) {
        for (Player watcher : of(game)) {
            watcher.sendMessage(line);
        }
    }

    /** /spawn: the watcher goes back to the lobby. False if they watch nothing. */
    boolean leave(Player watcher) {
        if (!forget(watcher.getUniqueId())) {
            return false;
        }
        back(watcher);
        return true;
    }

    /** A match found in a queue they wait in takes the watcher out of the game, where they stand. */
    boolean letGo(Player watcher) {
        if (!forget(watcher.getUniqueId())) {
            return false;
        }
        ownEyes(watcher);
        return true;
    }

    /** The game is over: its watchers go back to the lobby, before its players. */
    void ended(BedwarsGame game) {
        for (Player watcher : of(game)) {
            forget(watcher.getUniqueId());
            back(watcher);
        }
        targets.keySet().removeIf(id -> games.get(id) == game);
        games.values().removeIf(watched -> watched == game);
    }

    /** A player leaves the game: those who saw through their eyes go back to the spectators' point. */
    void playerLeft(BedwarsGame game, Player player) {
        for (Player watcher : of(game)) {
            if (player.equals(watcher.getSpectatorTarget())) {
                ownEyes(watcher);
                watcher.teleport(game.watchPoint());
            }
        }
    }

    private boolean forget(UUID watcher) {
        targets.remove(watcher);
        return games.remove(watcher) != null;
    }

    private void back(Player watcher) {
        ownEyes(watcher);
        plugin.lobby().sendToLobby(watcher);
    }

    private static void ownEyes(Player watcher) {
        if (watcher.getGameMode() == GameMode.SPECTATOR && watcher.getSpectatorTarget() != null) {
            watcher.setSpectatorTarget(null);
        }
    }

    /**
     * The game this player watches, or null. One the lobby took back by other means (or whom a
     * command took out of the spectator mode) is forgotten: they watch nothing any more.
     */
    private BedwarsGame watching(Player player) {
        BedwarsGame game = games.get(player.getUniqueId());
        if (game != null && (player.getGameMode() != GameMode.SPECTATOR || !plugin.lobby().isInGame(player))) {
            forget(player.getUniqueId());
            return null;
        }
        return game;
    }

    // --- The rules ------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        BedwarsGame game = watching(event.getPlayer());
        if (game != null && !holds(game, event.getTo())) {
            event.setTo(game.watchPoint());
        }
    }

    /** The spectator menu takes them to any player of the server: only to those of the game's world. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return;
        }
        BedwarsGame game = watching(event.getPlayer());
        if (game != null && !holds(game, event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (games.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (games.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Not even a look in a chest: a spectator may open one. */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (games.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (games.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (games.containsKey(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (games.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (games.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (games.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        if (games.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Before RagoLobby forgets the player. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }
}
