package gg.rago.lobby;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

/**
 * Entities that exist only in the packets sent to each player, through PacketEvents: the NPC
 * bodies (players with the Steve skin, which Bukkit 1.8.8 cannot spawn) and the holograms whose
 * text differs from one player to the next (each one's own stats, each one's leaderboard period).
 *
 * <p>Each player gets the fakes within {@link #SHOW_RANGE} blocks of them in the lobby world, and
 * loses them beyond {@link #HIDE_RANGE} (a fake may be seen from farther: {@link Fake#range}); a
 * change of world, a respawn or a long teleport (after which the client forgets the entities of
 * the chunks it unloads) sends them again. A click on a fake reaches its {@link Fake#click} on the
 * main thread. Main thread only, except the packet listener, which only reads {@link #byEntityId}.
 */
final class FakeEntities implements Listener {

    static final double SHOW_RANGE = 64;
    static final double HIDE_RANGE = 80;
    /** Farther than this from a fake, a click on it is not believed (reach is about 3 to 6 blocks). */
    private static final double CLICK_RANGE = 8;
    /** A teleport longer than this sends the fakes again. */
    private static final double LONG_TELEPORT = 24;
    /** Fake entity ids: far above the server's own counter, which starts at 0. */
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1_600_000_000);

    /** Something seen by players and known only to their clients. */
    abstract static class Fake {

        private final Location at;
        private final int[] ids;
        private final Clicker clicker;

        Fake(Location at, int entities, Clicker clicker) {
            this.at = at.clone();
            this.ids = new int[entities];
            for (int i = 0; i < entities; i++) {
                ids[i] = NEXT_ID.getAndIncrement();
            }
            this.clicker = clicker;
        }

        final Location at() {
            return at.clone();
        }

        final int[] ids() {
            return ids.clone();
        }

        final int id(int index) {
            return ids[index];
        }

        /** Sends the packets that create it for this player. */
        abstract void spawn(FakeEntities fakes, Player viewer);

        /** Twice a second, while the player sees it: sends what changed for them. */
        void update(FakeEntities fakes, Player viewer) {
        }

        /** How far it is seen: lost {@link #HIDE_RANGE} - {@link #SHOW_RANGE} blocks farther. */
        double range() {
            return SHOW_RANGE;
        }

        /** The player no longer sees it: forgets what was sent to them. */
        void forget(UUID viewer) {
        }

        void click(Player player, boolean left) {
            if (clicker != null) {
                clicker.click(player, left);
            }
        }
    }

    interface Clicker {
        /** @param left true for a left click (an attack), false for a right click */
        void click(Player player, boolean left);
    }

    private final Plugin plugin;
    private final World world;
    private final List<Fake> fakes = new ArrayList<>();
    /** Read by the packet listener on Netty threads. */
    private final Map<Integer, Fake> byEntityId = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Fake>> shown = new HashMap<>();
    private PacketListenerCommon listener;

    FakeEntities(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    /** Listens to the clicks. */
    void start() {
        listener = PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
                    return;
                }
                WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
                final Fake fake = byEntityId.get(packet.getEntityId());
                if (fake == null) {
                    return;
                }
                event.setCancelled(true);
                if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) {
                    // A right click sends INTERACT_AT and then INTERACT: one click is enough.
                    return;
                }
                final UUID clicker = event.getUser().getUUID();
                final boolean left = packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
                Bukkit.getScheduler().runTask(plugin, () -> clicked(clicker, fake, left));
            }
        });
    }

    /** Removes every fake from every client, and stops listening. */
    void stop() {
        clear();
        if (listener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            listener = null;
        }
    }

    void add(Fake fake) {
        fakes.add(fake);
        for (int id : fake.ids()) {
            byEntityId.put(id, fake);
        }
    }

    /** Removes every fake, from the clients too. */
    void clear() {
        for (Map.Entry<UUID, Set<Fake>> entry : shown.entrySet()) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null && !entry.getValue().isEmpty()) {
                send(viewer, new WrapperPlayServerDestroyEntities(idsOf(entry.getValue())));
            }
        }
        shown.clear();
        fakes.clear();
        byEntityId.clear();
    }

    /** Twice a second: who sees what, and what changed. */
    void tick() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Set<Fake> seen = shown.get(viewer.getUniqueId());
            if (!viewer.getWorld().equals(world)) {
                if (seen != null) {
                    forget(viewer.getUniqueId());
                }
                continue;
            }
            if (seen == null) {
                seen = new HashSet<>();
                shown.put(viewer.getUniqueId(), seen);
            }
            Location eyes = viewer.getLocation();
            List<Fake> hidden = new ArrayList<>();
            for (Fake fake : fakes) {
                double distance = fake.at.distanceSquared(eyes);
                double show = fake.range();
                double hide = show + HIDE_RANGE - SHOW_RANGE;
                if (seen.contains(fake)) {
                    if (distance > hide * hide) {
                        hidden.add(fake);
                    } else {
                        fake.update(this, viewer);
                    }
                } else if (distance < show * show) {
                    seen.add(fake);
                    fake.spawn(this, viewer);
                }
            }
            if (!hidden.isEmpty()) {
                seen.removeAll(hidden);
                for (Fake fake : hidden) {
                    fake.forget(viewer.getUniqueId());
                }
                send(viewer, new WrapperPlayServerDestroyEntities(idsOf(hidden)));
            }
        }
    }

    /** Sends a packet to one player. */
    void send(Player viewer, PacketWrapper<?> packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    /** Runs later on the main thread, if the plugin is still enabled. */
    void later(long ticks, Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
        }
    }

    /** True while this player's client has this fake. */
    boolean sees(Player viewer, Fake fake) {
        Set<Fake> seen = shown.get(viewer.getUniqueId());
        return seen != null && seen.contains(fake);
    }

    private void clicked(UUID clicker, Fake fake, boolean left) {
        Player player = Bukkit.getPlayer(clicker);
        if (player == null || !fakes.contains(fake) || !player.getWorld().equals(world)
                || player.getLocation().distanceSquared(fake.at) > CLICK_RANGE * CLICK_RANGE) {
            return;
        }
        fake.click(player, left);
    }

    /** The client no longer has any fake: sent again at the next tick. */
    private void forget(UUID viewer) {
        Set<Fake> seen = shown.remove(viewer);
        if (seen != null) {
            for (Fake fake : seen) {
                fake.forget(viewer);
            }
        }
    }

    private static int[] idsOf(Iterable<Fake> fakes) {
        List<Integer> ids = new ArrayList<>();
        for (Fake fake : fakes) {
            for (int id : fake.ids) {
                ids.add(id);
            }
        }
        int[] array = new int[ids.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = ids.get(i);
        }
        return array;
    }

    // --- When the client forgets its entities ---------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || !from.getWorld().equals(to.getWorld()) || from.distanceSquared(to) > LONG_TELEPORT * LONG_TELEPORT) {
            Set<Fake> seen = shown.get(event.getPlayer().getUniqueId());
            if (seen != null && !seen.isEmpty()) {
                // The client may keep some of them: removed now, sent again at the next tick.
                send(event.getPlayer(), new WrapperPlayServerDestroyEntities(idsOf(seen)));
            }
            forget(event.getPlayer().getUniqueId());
        }
    }
}
