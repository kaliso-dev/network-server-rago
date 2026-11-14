package gg.rago.lobby;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import javax.imageio.ImageIO;
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
import org.bukkit.plugin.Plugin;

/**
 * The billboards of the lobby (panneaux: of config.yml): their images, read from the folder
 * panneaux/ of the plugin and drawn on maps at the start, off the main thread ({@link MapArt});
 * their walls of frames, placed with the rest of the decor ({@link FakeBillboard}); and their
 * maps, sent to each player once for as long as their client keeps them (it forgets them with
 * its world: on a change of world, a respawn, a new connection), {@link #MAPS_PER_TICK} a tick.
 * Main thread only, but the drawing.
 */
final class Billboards implements Listener {

    /** The ids of the maps of the billboards: far above those of the server's maps, counted from 0. */
    static final int FIRST_MAP = 30000;
    /** 16 KB each: a board of 8 x 8 blocks reaches a player in 16 ticks. */
    static final int MAPS_PER_TICK = 4;
    static final String FOLDER = "panneaux";

    private final Plugin plugin;
    private final World world;
    private final List<Billboard> boards;
    private final File folder;
    /** The maps of each board drawn, by name; the colours of each map, by id. */
    private final Map<String, int[]> mapsOf = new HashMap<>();
    private final Map<Integer, byte[]> colours = new HashMap<>();
    /** The maps each client has, and those it waits for, in order. */
    private final Map<UUID, Set<Integer>> known = new HashMap<>();
    private final Map<UUID, LinkedHashSet<Integer>> waiting = new HashMap<>();
    /** The billboards whose wait for a point is in the log already. */
    private final Set<String> told = new HashSet<>();
    private FakeEntities fakes;

    Billboards(Plugin plugin, World world, List<Billboard> boards) {
        this.plugin = plugin;
        this.world = world;
        this.boards = boards;
        this.folder = new File(plugin.getDataFolder(), FOLDER);
    }

    /**
     * Reads and draws the images, off the main thread, then runs ready on it (to place the decor
     * again). The folder panneaux/ is made if it is not there.
     */
    void load(Runnable ready) {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            plugin.getLogger().warning("Cannot create the folder " + folder + " of the billboards");
        }
        if (boards.isEmpty()) {
            return;
        }
        final Map<String, int[]> ids = new HashMap<>();
        int next = FIRST_MAP;
        for (Billboard board : boards) {
            int[] maps = new int[board.maps()];
            for (int i = 0; i < maps.length; i++) {
                maps[i] = next++;
            }
            ids.put(board.name, maps);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final Map<String, byte[][]> drawn = new HashMap<>();
            for (Billboard board : boards) {
                byte[][] tiles = draw(board);
                if (tiles != null) {
                    drawn.put(board.name, tiles);
                }
            }
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> loaded(ids, drawn, ready));
            }
        });
    }

    /** The maps of a board, or null (said in the log) if its image cannot be read. */
    private byte[][] draw(Billboard board) {
        File file = new File(folder, board.image);
        if (!file.isFile()) {
            plugin.getLogger().warning("Billboard " + board.name + ": no image " + FOLDER + "/" + board.image
                    + " in the folder of the plugin (PNG or JPG): it does not show");
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                plugin.getLogger().warning("Billboard " + board.name + ": " + FOLDER + "/" + board.image
                        + " is not a PNG or JPG image: it does not show");
                return null;
            }
            byte[][] tiles = MapArt.tiles(image, board.columns, board.rows);
            plugin.getLogger().info("Billboard " + board.name + ": " + board.image + " (" + image.getWidth() + " x "
                    + image.getHeight() + ") on " + board.columns + " x " + board.rows + " blocks, "
                    + (board.columns * MapArt.SIZE) + " x " + (board.rows * MapArt.SIZE) + " pixels");
            return tiles;
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Billboard " + board.name + ": cannot read " + FOLDER + "/" + board.image
                    + ": it does not show", e);
            return null;
        }
    }

    private void loaded(Map<String, int[]> ids, Map<String, byte[][]> drawn, Runnable ready) {
        for (Map.Entry<String, byte[][]> entry : drawn.entrySet()) {
            int[] maps = ids.get(entry.getKey());
            byte[][] tiles = entry.getValue();
            for (int i = 0; i < maps.length; i++) {
                colours.put(maps[i], tiles[i]);
            }
            mapsOf.put(entry.getKey(), maps);
        }
        if (!drawn.isEmpty()) {
            ready.run();
        }
    }

    /**
     * Places the wall of each billboard drawn: on its point (/lobby point panneau-name) facing
     * where the one who placed it looked, else at its place of config.yml (lieu:), else nowhere
     * (said once in the log). The number placed.
     */
    int place(LobbyPoints points, Location spawn, FakeEntities fakes) {
        this.fakes = fakes;
        int placed = 0;
        for (Billboard board : boards) {
            int[] maps = mapsOf.get(board.name);
            if (maps == null) {
                continue;
            }
            SpawnPoint point = points.get(board.point());
            int[] block;
            int face;
            if (point != null) {
                block = new int[] {floor(point.x()), floor(point.y()), floor(point.z())};
                face = Billboard.face(point.yaw());
            } else if (board.block != null) {
                block = board.block;
                face = board.face != Billboard.TOWARDS_SPAWN ? board.face
                        : Billboard.faceTowards(block[0] + 0.5, block[2] + 0.5, spawn.getX(), spawn.getZ());
            } else {
                if (told.add(board.name)) {
                    plugin.getLogger().info("Billboard " + board.name + ": no lieu in config.yml, it waits for its point:"
                            + " /lobby point " + board.point());
                }
                continue;
            }
            fakes.add(new FakeBillboard(world, block, face, board.columns, board.rows, maps, this));
            placed++;
        }
        return placed;
    }

    /** The client of this player will need these maps: sent at the next ticks, those it does not have. */
    void send(Player viewer, int[] maps) {
        Set<Integer> has = known.get(viewer.getUniqueId());
        LinkedHashSet<Integer> queue = waiting.get(viewer.getUniqueId());
        for (int map : maps) {
            if (has != null && has.contains(map)) {
                continue;
            }
            if (queue == null) {
                queue = new LinkedHashSet<>();
                waiting.put(viewer.getUniqueId(), queue);
            }
            queue.add(map);
        }
    }

    /** Each tick: a few maps to each player who waits for some, while they are in the lobby. */
    void tick() {
        if (waiting.isEmpty() || fakes == null) {
            return;
        }
        Iterator<Map.Entry<UUID, LinkedHashSet<Integer>>> players = waiting.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, LinkedHashSet<Integer>> entry = players.next();
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer == null || !viewer.getWorld().equals(world)) {
                players.remove();
                continue;
            }
            Set<Integer> has = known.get(entry.getKey());
            if (has == null) {
                has = new HashSet<>();
                known.put(entry.getKey(), has);
            }
            Iterator<Integer> maps = entry.getValue().iterator();
            for (int sent = 0; sent < MAPS_PER_TICK && maps.hasNext(); sent++) {
                int map = maps.next();
                maps.remove();
                byte[] pixels = colours.get(map);
                if (pixels != null && has.add(map)) {
                    fakes.send(viewer, new WrapperPlayServerMapData(map, (byte) 0, false, false,
                            Collections.<WrapperPlayServerMapData.MapDecoration>emptyList(), MapArt.SIZE, MapArt.SIZE, 0, 0,
                            pixels));
                }
            }
            if (entry.getValue().isEmpty()) {
                players.remove();
            }
        }
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    // --- When the client forgets its maps ------------------------------------------------------

    private void forget(Player player) {
        known.remove(player.getUniqueId());
        waiting.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        forget(event.getPlayer());
    }
}
