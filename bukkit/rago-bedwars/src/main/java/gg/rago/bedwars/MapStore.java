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

    /**
     * The maps a game of this many teams can be played on: enabled, with exactly that many complete
     * teams; if none has exactly that many, those that have more (the game uses the first teams).
     */
    List<BedwarsMap> playable(int teams) {
        List<BedwarsMap> exact = new ArrayList<>();
        List<BedwarsMap> bigger = new ArrayList<>();
        for (BedwarsMap map : maps.values()) {
            if (!map.playable()) {
                continue;
            }
            int complete = map.completeTeams().size();
            if (complete == teams) {
                exact.add(map);
            } else if (complete > teams) {
                bigger.add(map);
            }
        }
        return exact.isEmpty() ? bigger : exact;
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
