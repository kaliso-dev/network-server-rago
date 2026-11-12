package gg.rago.practice;

import gg.rago.game.ArenaGrid;
import gg.rago.game.MatchProposal;
import gg.rago.lobby.api.EndOfGame;
import gg.rago.lobby.api.MatchHost;
import gg.rago.lobby.api.ReplayRecording;
import gg.rago.lobby.api.ReplaySetup;
import gg.rago.lobby.api.Replays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Plays the "sumo" mode of the lobby: takes an arena slot for each match, on one of the sumo maps
 * of the server (see {@link MapZones}) or, without them, on a round platform built the first time
 * the slot is used, and applies the duel rules to its players (no damage, only knockback; no
 * blocks, no items; standing still during the countdown; the water under the maps is a fall).
 */
final class SumoHost implements MatchHost, Listener {

    static final String MODE_ID = "sumo";

    private final RagoPractice plugin;
    private final World world;
    private final ArenaGrid grid;
    private final int radius;
    private final int target;
    /** The sumo maps, copied into the slots; empty: the plain platform. */
    private final MapZones zones;
    private final Set<Integer> built = new HashSet<>();
    private final Map<UUID, SumoMatch> matches = new HashMap<>();
    private final List<SumoMatch> running = new ArrayList<>();
    /** The plain platform's blocks from its centre, for the replays (made at the first one). */
    private int[] plainDesign;

    SumoHost(RagoPractice plugin, World world, ArenaGrid grid, int radius, int target, List<MapZones.ZoneMap> maps) {
        this.plugin = plugin;
        this.world = world;
        this.grid = grid;
        this.radius = radius;
        this.target = target;
        this.zones = new MapZones(world, grid, RagoPractice.ARENA_Y, 0, maps);
        zones.start(plugin);
    }

    // --- MatchHost ------------------------------------------------------------------------------

    @Override
    public boolean start(MatchProposal match) {
        Player first = Bukkit.getPlayer(match.teams().get(0).get(0).player());
        Player second = Bukkit.getPlayer(match.teams().get(1).get(0).player());
        if (first == null || second == null) {
            return false;
        }
        int slot = zones.isEmpty() ? grid.acquire() : zones.take();
        if (slot < 0) {
            return false;
        }
        Location centre = platform(slot);
        String map = zones.isEmpty() ? null : zones.mapOf(slot).name;
        SumoMatch duel = new SumoMatch(plugin, this, slot, centre, map, first, second, target);
        matches.put(first.getUniqueId(), duel);
        matches.put(second.getUniqueId(), duel);
        running.add(duel);
        duel.begin();
        duel.record(record(slot, map, ChatColor.stripColor(match.mode().name())));
        return true;
    }

    /** The recording of a duel in this slot for /replay, or null when the lobby keeps no replays. */
    @SuppressWarnings("deprecation") // Material#getId: the 1.8 block id
    private ReplayRecording record(int slot, String map, String modeName) {
        Replays replays = plugin.lobby().replays();
        if (replays == null) {
            return null;
        }
        int[] design;
        if (!zones.isEmpty()) {
            design = zones.mapOf(slot).copy.blocks();
        } else {
            if (plainDesign == null) {
                List<Integer> blocks = new ArrayList<>();
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x * x + z * z <= radius * radius) {
                            blocks.addAll(java.util.Arrays.asList(x, 0, z, Material.QUARTZ_BLOCK.getId(), 0));
                        }
                    }
                }
                plainDesign = new int[blocks.size()];
                for (int i = 0; i < plainDesign.length; i++) {
                    plainDesign[i] = blocks.get(i);
                }
            }
            design = plainDesign;
        }
        int cx = grid.centreX(slot);
        int half = grid.spacing() / 2;
        int halfRow = RagoPractice.ROW_SPACING / 2;
        return replays.record(ReplaySetup.ofBlocks(MODE_ID, modeName, map == null ? "" : map, world, cx, RagoPractice.ARENA_Y, 0,
                cx - half, cx + half - 1, -halfRow, halfRow - 1, design));
    }

    @Override
    public void abandon(Player player) {
        SumoMatch duel = matches.get(player.getUniqueId());
        if (duel == null) {
            plugin.lobby().sendToLobby(player);
        } else {
            duel.leave(player, true);
        }
    }

    // --- Matches --------------------------------------------------------------------------------

    SumoMatch matchOf(UUID player) {
        return matches.get(player);
    }

    /** The player no longer belongs to their duel. */
    void release(UUID player) {
        matches.remove(player);
    }

    /** The duel is over and its players are gone: its slot is free again. */
    void finished(SumoMatch duel) {
        for (UUID id : duel.players()) {
            if (matches.get(id) == duel) {
                matches.remove(id);
            }
        }
        if (running.remove(duel)) {
            grid.release(duel.slot());
        }
    }

    /** The plugin is being disabled: every duel stops, its players go back to the lobby. */
    void abortAll() {
        zones.stop();
        for (SumoMatch duel : new ArrayList<>(running)) {
            duel.abort();
            finished(duel);
        }
    }

    int running() {
        return running.size();
    }

    /** The centre of the slot's platform, standing on it. The plain one is built the first time. */
    private Location platform(int slot) {
        int cx = grid.centreX(slot);
        int y = RagoPractice.ARENA_Y;
        if (zones.isEmpty() && built.add(slot)) {
            int r2 = radius * radius;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z <= r2) {
                        world.getBlockAt(cx + x, y, z).setType(Material.QUARTZ_BLOCK);
                    }
                }
            }
        }
        return new Location(world, cx + 0.5, y + 1, 0.5);
    }

    // --- Rules ----------------------------------------------------------------------------------

    /** The Play again paper and the Leave bed, at the end of a duel. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (matches.containsKey(event.getPlayer().getUniqueId())
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && EndOfGame.clicked(plugin, event.getPlayer(), event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        SumoMatch duel = matches.get(event.getPlayer().getUniqueId());
        if (duel == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (duel.phase() == SumoMatch.Phase.COUNTDOWN
                && (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY())) {
            Location still = from.clone();
            still.setYaw(to.getYaw());
            still.setPitch(to.getPitch());
            event.setTo(still);
            return;
        }
        if (to.getY() < duel.fallY() || to.getBlock().isLiquid()) {
            duel.fell(event.getPlayer());
        }
    }

    /** Hits between the two duellists count, with no damage: only the knockback. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        SumoMatch duel = matches.get(entity.getUniqueId());
        if (duel == null) {
            return;
        }
        if (duel.phase() == SumoMatch.Phase.FIGHT && event instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
            if (damager instanceof Player && matches.get(damager.getUniqueId()) == duel) {
                event.setDamage(0.0);
                return;
            }
        }
        event.setCancelled(true);
    }

    /** A hit that went through between the two duellists: counted for the recap of the duel. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();
        if (!(victim instanceof Player) || !(damager instanceof Player)) {
            return;
        }
        SumoMatch duel = matches.get(victim.getUniqueId());
        if (duel != null && matches.get(damager.getUniqueId()) == duel && !damager.equals(victim)) {
            duel.hit(damager.getUniqueId(), victim.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (matches.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
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
    public void onDrop(PlayerDropItemEvent event) {
        guard(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        guard(event.getPlayer(), event);
    }

    /** Leaving the server in the middle of a duel loses it. Before RagoLobby forgets the player. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SumoMatch duel = matches.get(event.getPlayer().getUniqueId());
        if (duel != null) {
            duel.leave(event.getPlayer(), false);
        }
    }

    private void guard(Player player, Cancellable event) {
        if (matches.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
