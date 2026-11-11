package gg.rago.lobby;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * How each player arranged the kit of each mode in the kit editor, one YAML file per player in
 * plugins/RagoLobby/kits. A player's file is read on the writer thread when they join and written
 * there when they save; the layouts in memory are only touched on the main thread. On the
 * network, they will move to the database with the stats.
 */
final class KitLayouts {

    static final String FOLDER = "kits";

    private final Plugin plugin;
    private final File folder;
    private final Executor io;
    private final StatsService.Writer writer;
    /** Online players only: player, then mode id, then the layout. */
    private final Map<UUID, Map<String, int[]>> layouts = new HashMap<>();

    KitLayouts(Plugin plugin, Executor io, StatsService.Writer writer) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), FOLDER);
        this.io = io;
        this.writer = writer;
    }

    /** The player's layout of this mode's kit, or null for the kit's own. */
    int[] get(UUID player, String modeId) {
        Map<String, int[]> mine = layouts.get(player);
        int[] layout = mine == null ? null : mine.get(modeId);
        return layout == null ? null : layout.clone();
    }

    /** Keeps the player's layout of this mode (the kit's own for null) and writes their file. */
    void set(UUID player, String modeId, int[] layout) {
        Map<String, int[]> mine = layouts.computeIfAbsent(player, id -> new TreeMap<>());
        if (layout == null || Kit.isDefault(layout)) {
            mine.remove(modeId);
        } else {
            mine.put(modeId, layout.clone());
        }
        writer.write(file(player), toYaml(mine));
    }

    /**
     * Reads the player's file on the writer thread. A layout saved before the file was read (in the
     * first instants after joining) wins over the file's.
     */
    void load(final UUID player) {
        final File file = file(player);
        io.execute(() -> {
            if (!file.isFile()) {
                return;
            }
            final Map<String, int[]> read;
            try {
                read = fromYaml(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            } catch (IOException | InvalidConfigurationException e) {
                plugin.getLogger().log(Level.WARNING, "Unreadable kit layouts, ignored: " + file, e);
                return;
            }
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (Bukkit.getPlayer(player) == null) {
                        return;
                    }
                    Map<String, int[]> mine = layouts.computeIfAbsent(player, id -> new TreeMap<>());
                    for (Map.Entry<String, int[]> entry : read.entrySet()) {
                        mine.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                });
            }
        });
    }

    /** The player left: their file keeps their layouts. */
    void forget(UUID player) {
        layouts.remove(player);
    }

    private File file(UUID player) {
        return new File(folder, player + ".yml");
    }

    static String toYaml(Map<String, int[]> layouts) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("Kit layouts written by RagoLobby: for each mode, where each slot of the kit"
                + " goes (-1: where the kit puts it).");
        ConfigurationSection modes = yaml.createSection("modes");
        for (Map.Entry<String, int[]> entry : layouts.entrySet()) {
            List<Integer> slots = new ArrayList<>();
            for (int target : entry.getValue()) {
                slots.add(target);
            }
            modes.set(entry.getKey(), slots);
        }
        return yaml.saveToString();
    }

    /** The layouts of a file; a slot out of the inventory counts as -1. */
    static Map<String, int[]> fromYaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        Map<String, int[]> layouts = new LinkedHashMap<>();
        ConfigurationSection modes = yaml.getConfigurationSection("modes");
        if (modes == null) {
            return layouts;
        }
        for (String modeId : modes.getKeys(false)) {
            List<Integer> slots = modes.getIntegerList(modeId);
            int[] layout = Kit.defaultLayout();
            for (int slot = 0; slot < Kit.SLOTS && slot < slots.size(); slot++) {
                int target = slots.get(slot);
                layout[slot] = target >= 0 && target < Kit.SLOTS ? target : -1;
            }
            layouts.put(modeId, layout);
        }
        return layouts;
    }
}
