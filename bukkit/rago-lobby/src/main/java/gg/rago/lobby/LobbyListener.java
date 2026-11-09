package gg.rago.lobby;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * The lobby rules. In the lobby world, outside the build mode: nobody takes damage or gets
 * hungry, nothing is built, broken, dropped or picked up, the hotbar stays as it is, and falling
 * into the void brings you back to the spawn. Other worlds (games, the KB test world, build
 * worlds) are left alone.
 *
 * <p>Joining and coming back to the lobby world are handled at the HIGHEST priority, after
 * RagoBuildTools, which sets the game mode of its build worlds at the normal priority.
 */
final class LobbyListener implements Listener {

    private final RagoLobby lobby;
    private final LobbyState state;
    private final Visibility visibility;

    LobbyListener(RagoLobby lobby, LobbyState state, Visibility visibility) {
        this.lobby = lobby;
        this.state = state;
        this.visibility = visibility;
    }

    // --- Arriving and leaving -------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);
        lobby.joined(event.getPlayer());
        lobby.sendToLobby(event.getPlayer());
    }

    /** MONITOR: the game plugins see the player leave their game first, at the normal priority. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null);
        lobby.forget(event.getPlayer());
    }

    /** A player teleported into the lobby world by a command gets the lobby state. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().equals(state.world()) && !state.isInGame(player) && !state.isBuilding(player)) {
            lobby.resetForLobby(player);
        } else if (event.getFrom().equals(state.world())) {
            // Left the lobby without a game (a /monde tp, for example): no longer waits in a queue.
            lobby.queues().leave(player, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!state.isInGame(player) && player.getWorld().equals(state.world())) {
            event.setRespawnLocation(state.spawn());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo().getY() < lobby.settings().voidHeight() && state.isInLobby(event.getPlayer())) {
            event.getPlayer().teleport(state.spawn());
            event.getPlayer().setFallDistance(0f);
        }
    }

    // --- Hotbar ---------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!state.isProtected(player)) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            // Farmland, tripwires and pressure plates: no trampling. Plates still work for parkour later.
            if (event.getClickedBlock() != null && event.getClickedBlock().getType().name().equals("SOIL")) {
                event.setCancelled(true);
            }
            return;
        }
        // Chests, doors, buttons and the like are not used in the lobby.
        event.setUseInteractedBlock(Event.Result.DENY);
        HotbarItem item = HotbarItem.of(event.getItem());
        if (item == null) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            lobby.use(player, item);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (event.getView().getTopInventory().getHolder() instanceof KitEditor.Holder) {
            // The kit editor decides: the items of the kit move in the player's own inventory.
            lobby.kitEditor().click(event, (KitEditor.Holder) event.getView().getTopInventory().getHolder());
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof StatsMenu.Holder
                || event.getView().getTopInventory().getHolder() instanceof HypixelMenu.Holder) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof ClickMenu) {
            event.setCancelled(true);
            ClickMenu menu = (ClickMenu) event.getView().getTopInventory().getHolder();
            if (event.getRawSlot() >= 0 && event.getRawSlot() < menu.size()) {
                menu.click(player, event.getRawSlot(), event.isRightClick());
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof ModeMenu.Holder) {
            event.setCancelled(true);
            if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                lobby.menu().click(player, (ModeMenu.Holder) event.getView().getTopInventory().getHolder(),
                        event.getRawSlot());
            }
            return;
        }
        if (state.isProtected(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof KitEditor.Holder) {
            lobby.kitEditor().drag(event, (KitEditor.Holder) event.getView().getTopInventory().getHolder());
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof ModeMenu.Holder
                || event.getView().getTopInventory().getHolder() instanceof StatsMenu.Holder
                || event.getView().getTopInventory().getHolder() instanceof HypixelMenu.Holder
                || event.getView().getTopInventory().getHolder() instanceof ClickMenu
                || (event.getWhoClicked() instanceof Player && state.isProtected((Player) event.getWhoClicked()))) {
            event.setCancelled(true);
        }
    }

    // --- Protection -----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player && state.isInLobby((Player) entity)) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                entity.teleport(state.spawn());
                entity.setFallDistance(0f);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player && state.isInLobby((Player) event.getEntity())) {
            event.setCancelled(true);
            ((Player) event.getEntity()).setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        guard(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        guard(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        guard(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        guard(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        guard(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        guard(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        guard(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getEntity().getWorld().equals(state.world())) {
            if (!(event.getRemover() instanceof Player) || state.isProtected((Player) event.getRemover())) {
                event.setCancelled(true);
            }
        }
    }

    private void guard(Player player, Cancellable event) {
        if (state.isProtected(player)) {
            event.setCancelled(true);
        }
    }

    // --- The lobby world itself -----------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState() && event.getWorld().equals(state.world())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getLocation().getWorld().equals(state.world())
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    static String noPermission() {
        return ChatColor.RED + "This command is for the staff.";
    }

    /** A potion drunk (invisibility): the names above the heads follow at once, not within a second. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.POTION) {
            lobby.getServer().getScheduler().runTask(lobby, lobby::refreshBoards);
        }
    }
}
