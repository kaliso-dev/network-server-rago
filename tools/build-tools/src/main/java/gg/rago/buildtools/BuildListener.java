package gg.rago.buildtools;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * What keeps a build world a building site: the game mode of the world for whoever enters, only
 * builders may change a protected world, and nature (rain, fire, leaves, melting ice, explosions,
 * mobs) leaves builds alone. Worlds that are not build worlds (the lobby, the arenas, the games)
 * keep the game mode their own plugin gives: only a builder leaving a build world in creative goes
 * back to survival.
 */
final class BuildListener implements Listener {

    static final String BUILD_PERMISSION = "rago.build.construire";

    private final BuildWorlds worlds;

    BuildListener(BuildWorlds worlds) {
        this.worlds = worlds;
    }

    // --- Game mode ------------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyMode(event.getPlayer(), null);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        applyMode(event.getPlayer(), event.getFrom());
    }

    private void applyMode(Player player, World from) {
        WorldSettings world = worlds.settingsOf(player.getWorld());
        boolean fromBuild = from != null && worlds.settingsOf(from) != null;
        GameMode mode = modeOnEntering(world == null ? null : world.mode(), fromBuild, player.getGameMode());
        if (mode != null && player.getGameMode() != mode) {
            player.setGameMode(mode);
        }
    }

    /**
     * The game mode to give a player who enters a world, or null to leave theirs as it is. A build
     * world has its own (creative or survival). Any other world (the lobby, the arenas, a Bed Wars
     * game, a spectator watching a game) is left to the plugin that sent the player there: only a
     * builder who leaves a build world in creative mode goes back to survival. (Forcing survival in
     * every world took the spectators of a game out of the spectator mode as soon as they were
     * teleported into its world: they could then be hit and die.)
     *
     * @param buildMode the mode of the build world entered, null when it is not a build world
     * @param fromBuildWorld true when the player comes from a build world
     */
    static GameMode modeOnEntering(WorldSettings.Mode buildMode, boolean fromBuildWorld, GameMode current) {
        if (buildMode != null) {
            return buildMode == WorldSettings.Mode.CREATIF ? GameMode.CREATIVE : GameMode.SURVIVAL;
        }
        return fromBuildWorld && current == GameMode.CREATIVE ? GameMode.SURVIVAL : null;
    }

    // --- Only builders change a protected world ---------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        guard(event.getPlayer(), event.getBlock().getWorld(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        guard(event.getPlayer(), event.getBlock().getWorld(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        guard(event.getPlayer(), event.getBlockClicked().getWorld(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        guard(event.getPlayer(), event.getBlockClicked().getWorld(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        guard(event.getPlayer(), event.getEntity().getWorld(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        World world = event.getEntity().getWorld();
        if (event.getRemover() instanceof Player) {
            guard((Player) event.getRemover(), world, event);
        } else if (isProtected(world)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        guard(event.getPlayer(), event.getRightClicked().getWorld(), event);
    }

    private void guard(Player player, World world, Cancellable event) {
        if (player != null && isProtected(world) && !player.hasPermission(BUILD_PERMISSION)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "This world is protected: only builders can change it.");
        }
    }

    private boolean isProtected(World world) {
        WorldSettings settings = worlds.settingsOf(world);
        return settings != null && settings.isProtected();
    }

    // --- Nature leaves builds alone --------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState() && isBuildWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        cancelInBuildWorld(event.getBlock().getWorld(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        cancelInBuildWorld(event.getBlock().getWorld(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        cancelInBuildWorld(event.getBlock().getWorld(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        cancelInBuildWorld(event.getBlock().getWorld(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        cancelInBuildWorld(event.getBlock().getWorld(), event);
    }

    /** Fire still lights with flint and steel (for a fireplace); it just never spreads. */
    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
            cancelInBuildWorld(event.getBlock().getWorld(), event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (isBuildWorld(event.getLocation().getWorld())) {
            event.blockList().clear();
        }
    }

    /** Mobs only appear when a builder places them (spawn egg, spawner) or a command summons them. */
    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        boolean natural = reason == CreatureSpawnEvent.SpawnReason.NATURAL
                || reason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN
                || reason == CreatureSpawnEvent.SpawnReason.JOCKEY
                || reason == CreatureSpawnEvent.SpawnReason.MOUNT
                || reason == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
                || reason == CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION;
        if (natural) {
            cancelInBuildWorld(event.getLocation().getWorld(), event);
        }
    }

    private boolean isBuildWorld(World world) {
        return world != null && worlds.settingsOf(world) != null;
    }

    private void cancelInBuildWorld(World world, Cancellable event) {
        if (isBuildWorld(world)) {
            event.setCancelled(true);
        }
    }
}
