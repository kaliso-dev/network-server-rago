package gg.rago.bedwars;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The best times of Fast Shop, list by list: each player's best, and the name they had then, kept
 * in fastshop.yml of the plugin's folder, written away from the main thread. Main thread only.
 */
final class FastShopRecords {

    /** The best time of a list: who, and in how many milliseconds. */
    static final class Best {
        final UUID player;
        final String name;
        final long millis;

        Best(UUID player, String name, long millis) {
            this.player = player;
            this.name = name;
            this.millis = millis;
        }
    }

    private final File file;
    private final Executor io;
    private final Logger logger;
    /** By list id: each player's best time, in milliseconds. */
    private final Map<String, Map<UUID, Long>> times = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    FastShopRecords(File file, Executor io, Logger logger) {
        this.file = file;
        this.io = io;
        this.logger = logger;
    }

    /** Reads the file, at the start of the plugin. Nothing if there is none yet. */
    void load() {
        if (!file.isFile()) {
            return;
        }
        try {
            read(Disk.read(file));
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException e) {
            logger.log(Level.WARNING, "Cannot read " + file + ": the Fast Shop records start again from nothing", e);
        }
    }

    void read(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        ConfigurationSection records = yaml.getConfigurationSection("records");
        if (records != null) {
            for (String order : records.getKeys(false)) {
                ConfigurationSection players = records.getConfigurationSection(order);
                if (players == null) {
                    continue;
                }
                Map<UUID, Long> best = new HashMap<>();
                for (String id : players.getKeys(false)) {
                    best.put(UUID.fromString(id), players.getLong(id));
                }
                times.put(order, best);
            }
        }
        ConfigurationSection known = yaml.getConfigurationSection("noms");
        if (known != null) {
            for (String id : known.getKeys(false)) {
                names.put(UUID.fromString(id), known.getString(id));
            }
        }
    }

    String toYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Map<UUID, Long>> order : times.entrySet()) {
            for (Map.Entry<UUID, Long> time : order.getValue().entrySet()) {
                yaml.set("records." + order.getKey() + "." + time.getKey(), time.getValue());
            }
        }
        for (Map.Entry<UUID, String> name : names.entrySet()) {
            yaml.set("noms." + name.getKey(), name.getValue());
        }
        return yaml.saveToString();
    }

    /** A list bought in this time. True if it is the player's best (written to the file then). */
    boolean record(String order, UUID player, String name, long millis) {
        Map<UUID, Long> best = times.computeIfAbsent(order, key -> new HashMap<>());
        Long before = best.get(player);
        if (before != null && before <= millis) {
            return false;
        }
        best.put(player, millis);
        names.put(player, name);
        save();
        return true;
    }

    /** The player's best time on this list, or null. */
    Long best(String order, UUID player) {
        Map<UUID, Long> best = times.get(order);
        return best == null ? null : best.get(player);
    }

    /** The best time of everyone on this list, or null. */
    Best top(String order) {
        Map<UUID, Long> best = times.get(order);
        if (best == null) {
            return null;
        }
        Best top = null;
        for (Map.Entry<UUID, Long> time : best.entrySet()) {
            if (top == null || time.getValue() < top.millis) {
                String name = names.get(time.getKey());
                top = new Best(time.getKey(), name == null ? "?" : name, time.getValue());
            }
        }
        return top;
    }

    private void save() {
        if (io == null) {
            return;
        }
        final String text = toYaml();
        io.execute(() -> {
            try {
                Disk.write(file, text);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Cannot write " + file, e);
            }
        });
    }
}
