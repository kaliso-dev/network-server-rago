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
 * The NPCs and leaderboards of the lobby, standing on the /lobby points named in config.yml. A
 * mode NPC is a player with the Steve skin ({@link FakePlayer}, sent through PacketEvents)
 * holding the icon of its mode or category, facing the spawn, with its name and its queue count
 * floating above its head (two invisible armor stands). A click on it or its lines (left or
 * right) joins its mode's queue, or opens a menu of its modes when it has several or when its
 * mode is not open yet (the menu then says so). Stats NPCs and leaderboard walls are drawn by
 * {@link StatsDisplays}. Main thread only.
 */
final class LobbyNpcs implements Listener {

    /** Starts the name of every stand we spawn, so that a restart finds the old ones. */
    private static final String MARKER = ChatColor.RESET.toString() + ChatColor.RESET;
    private static final double LINE = FakeHologram.LINE;
    /** Height above the NPC's feet of its lowest line, just above its head (a player is 1.8 high). */
    static final double ABOVE_HEAD = 2.25;
    private static final long CLICK_COOLDOWN_MS = 300;
    /** Prefix of the NPC bodies' names: a colour code, so that no real player has one of them. */
    private static final String BODY_NAME = ChatColor.DARK_GRAY + "pnj";

    private final LobbySettings settings;
    private final LobbyState state;
    private final LobbyPoints points;
    private final QueueController queues;
    private final ModeMenu menu;
    private final FakeEntities fakes;
    private final StatsDisplays displays;

    /** Every stand we spawned, to the point it belongs to. */
    private final Map<UUID, String> stands = new HashMap<>();
    /** The line under each NPC's name, kept up to date with the queue count. */
    private final Map<String, ArmorStand> countLines = new HashMap<>();
    private final Set<Long> keptChunks = new HashSet<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    LobbyNpcs(LobbySettings settings, LobbyState state, LobbyPoints points, QueueController queues, ModeMenu menu,
            FakeEntities fakes, StatsDisplays displays) {
        this.settings = settings;
        this.state = state;
        this.points = points;
        this.queues = queues;
        this.menu = menu;
        this.fakes = fakes;
        this.displays = displays;
    }

    /**
     * The name of the body of the NPC at this index of {@link LobbySettings#npcs()}: the lobby
     * scoreboard hides the name tags of these names.
     */
    static String bodyName(int index) {
        return BODY_NAME + index;
    }

    /** (Re)spawns everything, after the start or after a point moved. Returns how many stand. */
    int spawnAll() {
        removeAll();
        World world = state.world();
        int placed = 0;
        List<LobbyNpc> npcs = settings.npcs();
        for (int index = 0; index < npcs.size(); index++) {
            LobbyNpc npc = npcs.get(index);
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
            if (npc.kind() == LobbyNpc.Kind.LEADERBOARDS) {
                displays.addWall(npc, base);
            } else if (npc.kind() == LobbyNpc.Kind.STATS) {
                displays.addStatsNpc(npc, bodyName(index), base);
            } else {
                body(npc, index, base);
                Location lowest = base.clone().add(0, ABOVE_HEAD - FakeHologram.NAME_HEIGHT, 0);
                label(npc, lowest.clone().add(0, LINE, 0), npc.name());
                countLines.put(npc.point(), label(npc, lowest, countText(npc)));
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
        fakes.clear();
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

    private void body(final LobbyNpc npc, int index, Location at) {
        ModeCategory category = npc.category() == null ? null : settings.category(npc.category());
        ItemStack icon = new ItemStack(category != null ? category.icon() : settings.display(npc.modeIds().get(0)).icon());
        fakes.add(new FakePlayer(at, bodyName(index), icon, player -> click(player, npc.point())));
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

    private void click(Player player, String point) {
        LobbyNpc npc = point == null ? null : settings.npc(point);
        if (npc == null || npc.kind() != LobbyNpc.Kind.MODES || !state.isProtected(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        if (last != null && now - last < CLICK_COOLDOWN_MS) {
            return;
        }
        if (npc.category() != null) {
            menu.openCategory(player, npc.category());
        } else if (npc.modeIds().size() == 1 && queues.isPlayable(npc.modeIds().get(0))) {
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
            click(event.getPlayer(), stands.get(event.getRightClicked().getUniqueId()));
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
            click((Player) ((EntityDamageByEntityEvent) event).getDamager(), stands.get(event.getEntity().getUniqueId()));
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
