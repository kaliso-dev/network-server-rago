package gg.rago.bedwars;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;

/**
 * The Bed Wars maps of the server: every folder of the world folder that holds a
 * {@value BedwarsMap#FILE_NAME}, read when the plugin starts. A change made with /bw is written at
 * once, away from the main thread. Main thread only.
 */
final class MapStore {

    private final File container;
    private final Executor io;
    private final Logger logger;
    private final Map<String, BedwarsMap> maps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    MapStore(File container, Executor io, Logger logger) {
        this.container = container;
        this.io = io;
        this.logger = logger;
    }

    /** Reads the maps of the world folder. A file that cannot be read is reported and skipped. */
    void loadAll() {
        maps.clear();
        File[] folders = container.listFiles(File::isDirectory);
        if (folders == null) {
            return;
        }
        for (File folder : folders) {
            File file = new File(folder, BedwarsMap.FILE_NAME);
            if (!file.isFile() || Instances.isCopy(folder)) {
                continue;
            }
            try {
                maps.put(folder.getName(), BedwarsMap.fromYaml(folder.getName(), Disk.read(file)));
            } catch (IOException | InvalidConfigurationException | IllegalArgumentException e) {
                logger.log(Level.WARNING, "The Bed Wars map " + folder.getName() + " is not read: "
                        + e.getMessage() + " (in " + file + ")");
            }
        }
    }

    /** The map of this world, or null. */
    BedwarsMap get(String world) {
        return maps.get(world);
    }

    /** The map of this world, new and empty if it was not one yet. */
    BedwarsMap getOrCreate(String world) {
        return maps.computeIfAbsent(world, BedwarsMap::new);
    }

    /** A new map, set up with /bw: from now on a map of the server. */
    void add(BedwarsMap map) {
        maps.put(map.world(), map);
    }

    Collection<BedwarsMap> all() {
        return Collections.unmodifiableCollection(maps.values());
    }

    /** The maps a game of this many teams can be played on: see {@link #choose}. */
    List<BedwarsMap> playable(int teams) {
        return choose(maps.values(), teams);
    }

    /** The maps of these names (empty: every map) a game of this many teams can be played on: see {@link #named}. */
    List<BedwarsMap> playable(int teams, List<String> names) {
        return named(maps.values(), teams, names);
    }

    /**
     * Among these maps, those of these names (in their order; empty: all of them) that a game of
     * this many teams can be played on (see {@link #choose}). The demo map stands in when none of
     * them can.
     */
    static List<BedwarsMap> named(Collection<BedwarsMap> maps, int teams, List<String> names) {
        if (names.isEmpty()) {
            return choose(maps, teams);
        }
        List<BedwarsMap> chosen = new ArrayList<>();
        for (String name : names) {
            for (BedwarsMap map : maps) {
                if (map.world().equalsIgnoreCase(name) && !chosen.contains(map)) {
                    chosen.add(map);
                }
            }
        }
        for (BedwarsMap map : maps) {
            if (DemoMap.WORLD.equals(map.world()) && !chosen.contains(map)) {
                chosen.add(map);
            }
        }
        return choose(chosen, teams);
    }

    /**
     * The maps among these that a game of this many teams can be played on: enabled, with at least
     * that many complete teams (a duel on a map of four islands plays on two opposite islands). A
     * map with exactly that many teams does not shut out the others: Antenna, of two teams, is one
     * of the 20 maps of the ranked duels, not the only one. The demo map is only played when no
     * other map can be.
     */
    static List<BedwarsMap> choose(Collection<BedwarsMap> maps, int teams) {
        List<BedwarsMap> real = new ArrayList<>();
        List<BedwarsMap> demo = new ArrayList<>();
        for (BedwarsMap map : maps) {
            (DemoMap.WORLD.equals(map.world()) ? demo : real).add(map);
        }
        List<BedwarsMap> chosen = fitting(real, teams);
        return chosen.isEmpty() ? fitting(demo, teams) : chosen;
    }

    /**
     * These playable maps without the demo map, unless it is the only one: the maps a copy is made
     * of before anyone asks for a game.
     */
    static List<BedwarsMap> inAdvance(Collection<BedwarsMap> playable) {
        List<BedwarsMap> real = new ArrayList<>();
        for (BedwarsMap map : playable) {
            if (!DemoMap.WORLD.equals(map.world())) {
                real.add(map);
            }
        }
        return real.isEmpty() ? new ArrayList<>(playable) : real;
    }

    private static List<BedwarsMap> fitting(Collection<BedwarsMap> maps, int teams) {
        List<BedwarsMap> fitting = new ArrayList<>();
        for (BedwarsMap map : maps) {
            if (map.playable() && map.completeTeams().size() >= teams) {
                fitting.add(map);
            }
        }
        return fitting;
    }

    /** Writes the map's file in its world folder, away from the main thread. */
    void save(BedwarsMap map) {
        final File file = new File(new File(container, map.world()), BedwarsMap.FILE_NAME);
        final String text = map.toYaml();
        io.execute(() -> {
            try {
                Disk.write(file, text);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Cannot write " + file, e);
            }
        });
    }
}
