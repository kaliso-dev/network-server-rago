package gg.rago.practice;

import gg.rago.game.ArenaGrid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Maps copied into the zones of a duel mode, so that each duel plays on one of them: zone n holds
 * map n modulo their number. The zones are built from the start of the server, a few thousand
 * blocks each tick and zone after zone (the first zones hold one map each, so every map is soon
 * ready). A duel takes a ready zone of a map drawn at random; when none is free, the lowest free
 * zone, built at once. Nothing is placed or broken in these duels: a zone, once built, stays as it
 * is. Main thread only.
 */
final class MapZones {

    /** A map: the name of its world and its blocks, the middle of the top of its platform at the origin. */
    static final class ZoneMap {
        final String name;
        final MapCopy copy;

        ZoneMap(String name, MapCopy copy) {
            this.name = name;
            this.copy = copy;
        }
    }

    /** Blocks placed each tick while the zones are being built. */
    static final int BLOCKS_PER_TICK = 4000;

    private final World world;
    private final ArenaGrid grid;
    /** Where the origin of the maps lands: the y, and the z of the row of zones. */
    private final int y;
    private final int z;
    private final List<ZoneMap> maps;
    /** For each zone, the blocks of its map placed so far. */
    private final int[] built;
    private final Random random = new Random();
    private BukkitTask task;

    MapZones(World world, ArenaGrid grid, int y, int z, List<ZoneMap> maps) {
        this.world = world;
        this.grid = grid;
        this.y = y;
        this.z = z;
        this.maps = Collections.unmodifiableList(new ArrayList<>(maps));
        this.built = new int[grid.slots()];
    }

    boolean isEmpty() {
        return maps.isEmpty();
    }

    List<ZoneMap> maps() {
        return maps;
    }

    /** The map of a zone. */
    ZoneMap mapOf(int slot) {
        return maps.get(slot % maps.size());
    }

    boolean ready(int slot) {
        return built[slot] >= mapOf(slot).copy.size();
    }

    /** Starts building the zones, a few blocks each tick, until they are all built. */
    void start(Plugin plugin) {
        if (maps.isEmpty() || task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::step, 1L, 1L);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void step() {
        int left = BLOCKS_PER_TICK;
        for (int slot = 0; slot < built.length && left > 0; slot++) {
            if (!ready(slot)) {
                left -= buildSome(slot, left);
            }
        }
        if (left > 0) {
            stop();
        }
    }

    /** Places up to max more blocks of the zone's map; returns how many. */
    private int buildSome(int slot, int max) {
        int from = built[slot];
        built[slot] = mapOf(slot).copy.place(world, grid.centreX(slot), y, z, from, max);
        return built[slot] - from;
    }

    /**
     * A zone for a new duel, now taken: a ready one of a map drawn at random among those with one
     * free, else the lowest free zone, built at once. -1 when every zone is in use.
     */
    int take() {
        int slot = pick(readyFree(), random);
        if (slot >= 0 && grid.take(slot)) {
            return slot;
        }
        slot = grid.acquire();
        if (slot >= 0 && !ready(slot)) {
            buildSome(slot, Integer.MAX_VALUE);
        }
        return slot;
    }

    /** For each map, its lowest free and ready zone, or -1. */
    private int[] readyFree() {
        int[] free = new int[maps.size()];
        Arrays.fill(free, -1);
        for (int slot = 0; slot < built.length; slot++) {
            int map = slot % maps.size();
            if (free[map] < 0 && !grid.inUse(slot) && ready(slot)) {
                free[map] = slot;
            }
        }
        return free;
    }

    /** One of the zones (-1: none for that map), drawn at random; -1 if there is none. */
    static int pick(int[] zones, Random random) {
        List<Integer> choices = new ArrayList<>();
        for (int zone : zones) {
            if (zone >= 0) {
                choices.add(zone);
            }
        }
        return choices.isEmpty() ? -1 : choices.get(random.nextInt(choices.size()));
    }
}
