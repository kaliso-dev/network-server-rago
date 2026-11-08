package gg.rago.lobby;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Named spots of the lobby, placed with /lobby point while the map is built and kept in
 * points.yml: where the mode NPCs, the leaderboards and the other decorations will stand once
 * they exist. Main thread only; the file is written elsewhere from {@link #toYaml()}.
 */
final class LobbyPoints {

    static final String FILE_NAME = "points.yml";
    static final int MAX_POINTS = 100;
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");

    private final Map<String, SpawnPoint> points = new TreeMap<>();

    static boolean validName(String name) {
        return NAME.matcher(name).matches();
    }

    /** The points of this file, none if there is no file. */
    static LobbyPoints read(File file) throws IOException {
        LobbyPoints result = new LobbyPoints();
        if (!file.isFile()) {
            return result;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (InvalidConfigurationException e) {
            throw new IOException(file + " is not valid YAML: " + e.getMessage(), e);
        }
        for (String name : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(name);
            if (!validName(name) || section == null || !section.isSet("x") || !section.isSet("y") || !section.isSet("z")) {
                throw new IOException(file + ": " + name + " is not a point");
            }
            result.points.put(name, new SpawnPoint(section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                    (float) section.getDouble("yaw"), (float) section.getDouble("pitch")));
        }
        return result;
    }

    /** Places or moves a point. False if the name is invalid or there are too many points. */
    boolean set(String name, SpawnPoint point) {
        if (!validName(name) || (!points.containsKey(name) && points.size() >= MAX_POINTS)) {
            return false;
        }
        points.put(name, point);
        return true;
    }

    boolean remove(String name) {
        return points.remove(name) != null;
    }

    SpawnPoint get(String name) {
        return points.get(name);
    }

    Map<String, SpawnPoint> all() {
        return Collections.unmodifiableMap(points);
    }

    String toYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("Les points du lobby, placés par /lobby point <nom>.");
        for (Map.Entry<String, SpawnPoint> entry : points.entrySet()) {
            SpawnPoint p = entry.getValue();
            ConfigurationSection section = yaml.createSection(entry.getKey());
            section.set("x", p.x());
            section.set("y", p.y());
            section.set("z", p.z());
            section.set("yaw", (double) p.yaw());
            section.set("pitch", (double) p.pitch());
        }
        return yaml.saveToString();
    }
}
