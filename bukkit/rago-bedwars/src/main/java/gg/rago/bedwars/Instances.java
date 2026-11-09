package gg.rago.bedwars;

import gg.rago.lobby.api.VoidGenerator;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;

/**
 * The game worlds: copies of the maps, named bw_&lt;map&gt;_&lt;n&gt;, each playing one game at a
 * time and made ready again after it (see {@link Instance}). A copy is made the first time a map is
 * needed and none is free: its files are copied away from the main thread, then the world is loaded
 * (on the main thread, as Bukkit wants). The copies are deleted when the plugin starts again, so
 * that each start copies the maps as they are now. Main thread only.
 */
final class Instances {

    static final String PREFIX = "bw_";
    /** In each copy's folder: marks the folder as ours to delete. */
    static final String MARKER = "rago-bedwars-copy.txt";
    /** After saving a loaded map, the time the server takes to write its chunks before the copy. */
    static final long SAVE_DELAY_TICKS = 40L;
    static final long RETRY_MS = 60_000L;

    private final Plugin plugin;
    private final File container;
    private final Executor io;
    private final int max;
    private final List<Instance> instances = new ArrayList<>();
    /** Maps being copied right now. */
    private final Set<String> preparing = new HashSet<>();
    /** When the copy of a map last failed: no new try for a minute, instead of one each second. */
    private final Map<String, Long> failedAt = new HashMap<>();
    private int counter;

    Instances(Plugin plugin, File container, Executor io, int max) {
        this.plugin = plugin;
        this.container = container;
        this.io = io;
        this.max = max;
    }

    static boolean isCopy(File folder) {
        return new File(folder, MARKER).isFile();
    }

    /** Deletes the copies an earlier start left, before any new one is made. */
    void deleteLeftovers() {
        File[] folders = container.listFiles(File::isDirectory);
        if (folders == null) {
            return;
        }
        for (final File folder : folders) {
            if (isCopy(folder) && Bukkit.getWorld(folder.getName()) == null) {
                io.execute(() -> {
                    try {
                        Disk.delete(folder);
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.WARNING, "Cannot delete the old game world " + folder, e);
                    }
                });
            }
        }
    }

    /** The game world of this world, or null. */
    Instance of(World world) {
        for (Instance instance : instances) {
            if (instance.world().equals(world)) {
                return instance;
            }
        }
        return null;
    }

    /** True for the world of a copy, or a folder named like one. */
    boolean isGameWorld(World world) {
        return of(world) != null || world.getName().startsWith(PREFIX);
    }

    /**
     * A free copy of one of these maps, now taken, chosen at random among the maps that have one;
     * null if none is free (then {@link #prepare} makes one).
     */
    Instance acquire(List<BedwarsMap> maps) {
        List<Instance> free = new ArrayList<>();
        for (Instance instance : instances) {
            if (instance.busy() || instance.discarded()) {
                continue;
            }
            for (BedwarsMap map : maps) {
                if (map.world().equalsIgnoreCase(instance.map().world())) {
                    free.add(instance);
                }
            }
        }
        if (free.isEmpty()) {
            return null;
        }
        Collections.shuffle(free);
        Instance chosen = free.get(0);
        chosen.setBusy(true);
        return chosen;
    }

    /** True if one of these maps has a copy free for a game. */
    boolean hasFree(List<BedwarsMap> maps) {
        for (Instance instance : instances) {
            if (instance.busy() || instance.discarded()) {
                continue;
            }
            for (BedwarsMap map : maps) {
                if (map.world().equalsIgnoreCase(instance.map().world())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Starts copying one of these maps, at random, if the server may have one more game world and
     * no copy is already on its way. The game can start once it is loaded. At the limit of game
     * worlds, {@code makeRoom} unloads a free copy of another map first (a match is waiting for one
     * of these maps); without it, nothing is done.
     */
    void prepare(List<BedwarsMap> maps, boolean makeRoom) {
        if (maps.isEmpty() || !preparing.isEmpty()) {
            return;
        }
        if (instances.size() >= max && !(makeRoom && unloadFreeOtherThan(maps))) {
            return;
        }
        List<BedwarsMap> shuffled = new ArrayList<>(maps);
        Collections.shuffle(shuffled);
        prepare(shuffled.get(0));
    }

    /** Unloads a free copy of a map that is not one of these. False if every copy is busy or of these maps. */
    private boolean unloadFreeOtherThan(List<BedwarsMap> maps) {
        for (Instance instance : new ArrayList<>(instances)) {
            if (instance.busy()) {
                continue;
            }
            boolean wanted = false;
            for (BedwarsMap map : maps) {
                wanted |= map.world().equalsIgnoreCase(instance.map().world());
            }
            if (!wanted) {
                plugin.getLogger().info("Game world " + instance.world().getName() + " unloaded: room for a copy of "
                        + maps.get(0).world() + (maps.size() > 1 ? " or another map of its mode" : ""));
                unload(instance);
                return true;
            }
        }
        return false;
    }

    /**
     * Copies this map into a new game world, unless one is already being made. A map whose world is
     * loaded (being edited, or kept loaded by RagoBuildTools) is saved first, and copied a moment
     * later, once the server has written its chunks.
     */
    void prepare(BedwarsMap map) {
        final File source = new File(container, map.world());
        String key = map.world().toLowerCase(java.util.Locale.ROOT);
        Long failed = failedAt.get(key);
        if (failed != null && System.currentTimeMillis() - failed < RETRY_MS) {
            return;
        }
        if (!preparing.add(key)) {
            return;
        }
        final BedwarsMap snapshot = map.copy();
        final String name = PREFIX + map.world() + "_" + (++counter);
        final File target = new File(container, name);
        World loaded = Bukkit.getWorld(map.world());
        if (loaded != null) {
            loaded.save();
            Bukkit.getScheduler().runTaskLater(plugin, () -> copy(snapshot, source, target, name), SAVE_DELAY_TICKS);
        } else {
            copy(snapshot, source, target, name);
        }
    }

    private void copy(final BedwarsMap snapshot, final File source, final File target, final String name) {
        io.execute(() -> {
            String problem = null;
            try {
                Disk.delete(target);
                Disk.copyWorld(source, target);
                Disk.write(new File(target, MARKER), "A copy of the Bed Wars map " + snapshot.world()
                        + " made by RagoBedwars: deleted when the server starts again.\n");
            } catch (IOException e) {
                problem = e.toString();
            }
            final String failed = problem;
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> loaded(snapshot, name, failed));
        });
    }

    private void loaded(BedwarsMap map, String name, String problem) {
        String key = map.world().toLowerCase(java.util.Locale.ROOT);
        preparing.remove(key);
        if (problem != null) {
            failedAt.put(key, System.currentTimeMillis());
            plugin.getLogger().warning("Cannot copy the map " + map.world() + ": " + problem
                    + " (next try in " + RETRY_MS / 1000 + " seconds)");
            return;
        }
        World world = new WorldCreator(name).generator(new VoidGenerator()).generateStructures(false).createWorld();
        if (world == null) {
            failedAt.put(key, System.currentTimeMillis());
            plugin.getLogger().warning("The game world " + name + " did not load");
            return;
        }
        failedAt.remove(key);
        // Never saved: the copy only lives until the server stops (and its chunks stay loaded).
        world.setAutoSave(false);
        world.setKeepSpawnInMemory(false);
        world.setDifficulty(Difficulty.NORMAL);
        world.setPVP(true);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("mobGriefing", "false");
        world.setGameRuleValue("keepInventory", "true");
        world.setTime(6000L);
        for (BedwarsMap.Removal removal : map.removals()) {
            plugin.getLogger().info("Game world " + name + ": " + removal.apply(world) + " blocks left out (retirer in "
                    + BedwarsMap.FILE_NAME + ")");
        }
        instances.add(new Instance(map, world));
        plugin.getLogger().info("Game world " + name + " ready (map " + map.world() + ")");
    }

    /** The game is over: the copy is made ready for the next one, or thrown away if its map changed. */
    void release(Instance instance) {
        instance.rollback();
        instance.setBusy(false);
        if (instance.discarded()) {
            unload(instance);
        }
    }

    /** The map changed: its free copies go, the busy ones once their game is over. */
    void discard(String mapWorld) {
        for (Instance instance : new ArrayList<>(instances)) {
            if (!instance.map().world().equalsIgnoreCase(mapWorld)) {
                continue;
            }
            instance.discard();
            if (!instance.busy()) {
                unload(instance);
            }
        }
    }

    private void unload(Instance instance) {
        instances.remove(instance);
        final World world = instance.world();
        for (org.bukkit.entity.Player player : world.getPlayers()) {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
        final File folder = world.getWorldFolder();
        if (Bukkit.unloadWorld(world, false)) {
            io.execute(() -> {
                try {
                    Disk.delete(folder);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Cannot delete the game world " + folder, e);
                }
            });
        }
    }

    /**
     * The plugin stops, on the server's thread: every game world is unloaded and deleted at once, so
     * that the backups of the worlds made before the next start do not hold them (what is left is
     * deleted at the start).
     */
    void unloadAll() {
        for (Instance instance : new ArrayList<>(instances)) {
            for (org.bukkit.entity.Player player : instance.world().getPlayers()) {
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
            File folder = instance.world().getWorldFolder();
            if (Bukkit.unloadWorld(instance.world(), false)) {
                try {
                    Disk.delete(folder);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Cannot delete the game world " + folder, e);
                }
            }
        }
        instances.clear();
    }

    /** How many game worlds are loaded, and how many play a game. */
    int loaded() {
        return instances.size();
    }

    int busy() {
        int busy = 0;
        for (Instance instance : instances) {
            if (instance.busy()) {
                busy++;
            }
        }
        return busy;
    }
}
