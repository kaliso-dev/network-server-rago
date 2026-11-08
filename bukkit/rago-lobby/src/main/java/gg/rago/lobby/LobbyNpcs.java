package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The NPCs and leaderboards of the lobby, standing on the /lobby points named in config.yml. For
 * now they are armor stands: a visible one holding the icon of the first mode, with the NPC's name
 * and its queue count above it; a leaderboard is two floating lines until rago-stats exists. A
 * click on an NPC (left or right) joins its mode's queue, or opens a menu of its modes when it has
 * several. Main thread only.
 */
final class LobbyNpcs implements Listener {

    /** Starts the name of every stand we spawn, so that a restart finds the old ones. */
    private static final String MARKER = ChatColor.RESET.toString() + ChatColor.RESET;
    private static final double LINE = 0.3;
    private static final long CLICK_COOLDOWN_MS = 300;

    private final LobbySettings settings;
    private final LobbyState state;
    private final LobbyPoints points;
    private final QueueController queues;
    private final ModeMenu menu;

    /** Every stand we spawned, to the point it belongs to. */
    private final Map<UUID, String> stands = new HashMap<>();
    /** The line under each NPC's name, kept up to date with the queue count. */
    private final Map<String, ArmorStand> countLines = new HashMap<>();
    private final Set<Long> keptChunks = new HashSet<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    LobbyNpcs(LobbySettings settings, LobbyState state, LobbyPoints points, QueueController queues, ModeMenu menu) {
        this.settings = settings;
        this.state = state;
        this.points = points;
        this.queues = queues;
        this.menu = menu;
    }

    /** (Re)spawns everything, after the start or after a point moved. Returns how many stand. */
    int spawnAll() {
        removeAll();
        World world = state.world();
        int placed = 0;
        for (LobbyNpc npc : settings.npcs()) {
            SpawnPoint point = points.get(npc.point());
            if (point == null) {
                continue;
            }
            Location base = point.in(world);
            // Facing the spawn, where players come from, whatever way the builder looked.
            Location spawn = state.spawn();
            base.setYaw(yawTowards(base.getX(), base.getZ(), spawn.getX(), spawn.getZ()));
            base.setPitch(0f);
            Chunk chunk = base.getChunk();
            chunk.load();
            keptChunks.add(key(chunk.getX(), chunk.getZ()));
            removeOldStands(base);
            if (npc.leaderboard()) {
                label(npc, base.clone().add(0, LINE, 0), npc.name());
                label(npc, base.clone(), ChatColor.GRAY + "Le classement de la saison arrive bientôt");
            } else {
                body(npc, base);
                label(npc, base.clone().add(0, LINE, 0), npc.name());
                countLines.put(npc.point(), label(npc, base.clone(), countText(npc)));
            }
            placed++;
        }
        return placed;
    }

    /** Removes every stand we spawned (on disable, and before a respawn). */
    void removeAll() {
        for (Entity entity : state.world().getEntities()) {
            if (stands.containsKey(entity.getUniqueId())) {
                entity.remove();
            }
        }
        stands.clear();
        countLines.clear();
        keptChunks.clear();
    }

    /** Once a second: the queue counts. */
    void update() {
        for (LobbyNpc npc : settings.npcs()) {
            ArmorStand line = countLines.get(npc.point());
            if (line != null && line.isValid()) {
                String text = MARKER + countText(npc);
                if (!text.equals(line.getCustomName())) {
                    line.setCustomName(text);
                }
            }
        }
    }

    private String countText(LobbyNpc npc) {
        int waiting = 0;
        boolean playable = false;
        for (String id : npc.modeIds()) {
            if (queues.isPlayable(id)) {
                playable = true;
                waiting += queues.waiting(id);
            }
        }
        if (!playable) {
            return ChatColor.DARK_GRAY + "Bientôt disponible";
        }
        return ChatColor.WHITE + "" + waiting + ChatColor.GRAY + " en file " + ChatColor.DARK_GRAY + "· "
                + ChatColor.GREEN + "Clic pour jouer";
    }

    private void body(LobbyNpc npc, Location at) {
        ArmorStand stand = spawn(npc, at);
        stand.setVisible(true);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setCustomNameVisible(false);
        ModeCategory category = npc.category() == null ? null : settings.category(npc.category());
        stand.setItemInHand(new ItemStack(category != null ? category.icon()
                : settings.display(npc.modeIds().get(0)).icon()));
    }

    private ArmorStand label(LobbyNpc npc, Location at, String text) {
        ArmorStand stand = spawn(npc, at);
        stand.setVisible(false);
        stand.setSmall(true);
        stand.setCustomName(MARKER + text);
        stand.setCustomNameVisible(true);
        return stand;
    }

    private ArmorStand spawn(LobbyNpc npc, Location at) {
        ArmorStand stand = at.getWorld().spawn(at, ArmorStand.class);
        stand.setGravity(false);
        stand.setCustomName(MARKER);
        stand.setRemoveWhenFarAway(false);
        stands.put(stand.getUniqueId(), npc.point());
        return stand;
    }

    /** Stands left by an earlier run (a crash skips onDisable): ours start with the marker. */
    private void removeOldStands(Location at) {
        for (Entity entity : at.getWorld().getNearbyEntities(at, 1.5, 3, 1.5)) {
            if (entity instanceof ArmorStand && entity.getCustomName() != null
                    && entity.getCustomName().startsWith(MARKER) && !stands.containsKey(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    /** The Minecraft yaw that looks from (x, z) to (toX, toZ): 0 is south (+z), 90 is west (-x). */
    static float yawTowards(double x, double z, double toX, double toZ) {
        double dx = toX - x;
        double dz = toZ - z;
        if (dx == 0 && dz == 0) {
            return 0f;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    // --- Clicks and protection ------------------------------------------------------------------

    private void click(Player player, Entity entity) {
        String point = stands.get(entity.getUniqueId());
        LobbyNpc npc = point == null ? null : settings.npc(point);
        if (npc == null || npc.leaderboard() || !state.isProtected(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        if (last != null && now - last < CLICK_COOLDOWN_MS) {
            return;
        }
        if (npc.category() != null) {
            menu.openCategory(player, npc.category());
        } else if (npc.modeIds().size() == 1) {
            GameMode mode = settings.catalog().get(npc.modeIds().get(0));
            menu.toggle(player, mode);
        } else {
            menu.openNpc(player, npc);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (stands.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
            click(event.getPlayer(), event.getRightClicked());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (stands.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (!stands.containsKey(event.getEntity().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent
                && ((EntityDamageByEntityEvent) event).getDamager() instanceof Player) {
            click((Player) ((EntityDamageByEntityEvent) event).getDamager(), event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (event.getWorld().equals(state.world())
                && keptChunks.contains(key(event.getChunk().getX(), event.getChunk().getZ()))) {
            event.setCancelled(true);
        }
    }

    /** The NPC points that config.yml names and no /lobby point places yet. */
    List<String> missingPoints() {
        List<String> missing = new ArrayList<>();
        for (LobbyNpc npc : settings.npcs()) {
            if (points.get(npc.point()) == null) {
                missing.add(npc.point());
            }
        }
        return missing;
    }
}
