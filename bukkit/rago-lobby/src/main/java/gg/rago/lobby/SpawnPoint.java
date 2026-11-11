package gg.rago.lobby;

import java.io.File;
import java.io.IOException;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** The lobby spawn, kept in spawn.yml in the lobby world's folder, so that it travels with the map. Immutable. */
final class SpawnPoint {

    static final String FILE_NAME = "spawn.yml";

    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    SpawnPoint(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    static SpawnPoint of(Location location) {
        return new SpawnPoint(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    Location in(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double z() {
        return z;
    }

    float yaw() {
        return yaw;
    }

    float pitch() {
        return pitch;
    }

    /** The spawn written in this file, or null if there is no file. */
    static SpawnPoint read(File file) throws IOException {
        if (!file.isFile()) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (InvalidConfigurationException e) {
            throw new IOException(file + " is not valid YAML: " + e.getMessage(), e);
        }
        for (String key : new String[] {"x", "y", "z"}) {
            if (!yaml.isDouble(key) && !yaml.isInt(key)) {
                throw new IOException(file + ": " + key + " is missing");
            }
        }
        return new SpawnPoint(yaml.getDouble("x"), yaml.getDouble("y"), yaml.getDouble("z"),
                (float) yaml.getDouble("yaw"), (float) yaml.getDouble("pitch"));
    }

    /** The text of spawn.yml for this spawn, built on the main thread and written elsewhere. */
    String toYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("The spawn of the lobby, written by /lobby setspawn.");
        yaml.set("x", x);
        yaml.set("y", y);
        yaml.set("z", z);
        yaml.set("yaw", (double) yaw);
        yaml.set("pitch", (double) pitch);
        return yaml.saveToString();
    }
}
