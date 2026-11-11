package gg.rago.practice;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * What each player last chose in each training, in plugins/RagoPractice/reglages.yml: the variant
 * and the value of each setting, given back the next time they play it. Read once when the plugin
 * starts, written after each change off the main thread (the text is made on the main thread, the
 * disk is written by an async task, the last text written last).
 */
final class TrainingPrefs {

    /** A player's choice in one training: the id of the variant, and each setting by its key. */
    static final class Choice {
        String variant;
        final Map<String, Integer> settings = new LinkedHashMap<>();
    }

    private final RagoPractice plugin;
    private final File file;
    private final Map<UUID, Map<String, Choice>> choices = new HashMap<>();
    private final Object writing = new Object();
    private long version;

    TrainingPrefs(RagoPractice plugin, File file) {
        this.plugin = plugin;
        this.file = file;
    }

    /** The YAML key of a setting: its label, without the dots that would make it a path. */
    static String key(String label) {
        return label.replace('.', '_');
    }

    /** Reads the file, if there is one. */
    void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String player : yaml.getKeys(false)) {
            ConfigurationSection modes = yaml.getConfigurationSection(player);
            UUID id;
            try {
                id = UUID.fromString(player);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("reglages.yml: " + player + " is not a player, skipped");
                continue;
            }
            if (modes == null) {
                continue;
            }
            for (String mode : modes.getKeys(false)) {
                ConfigurationSection section = modes.getConfigurationSection(mode);
                if (section == null) {
                    continue;
                }
                Choice choice = new Choice();
                choice.variant = section.getString("variante");
                ConfigurationSection settings = section.getConfigurationSection("reglages");
                if (settings != null) {
                    for (String setting : settings.getKeys(false)) {
                        if (settings.isInt(setting)) {
                            choice.settings.put(setting, settings.getInt(setting));
                        }
                    }
                }
                choices.computeIfAbsent(id, k -> new HashMap<>()).put(mode, choice);
            }
        }
    }

    /** The player's last choice in this training, or null if they never changed anything there. */
    Choice choice(UUID player, String mode) {
        Map<String, Choice> modes = choices.get(player);
        return modes == null ? null : modes.get(mode);
    }

    /** The player's choice now in this training, kept and written. */
    void remember(UUID player, String mode, String variant, List<Training.Setting> settings) {
        Choice choice = new Choice();
        choice.variant = variant;
        for (Training.Setting setting : settings) {
            choice.settings.put(key(setting.label), setting.value);
        }
        choices.computeIfAbsent(player, k -> new HashMap<>()).put(mode, choice);
        save();
    }

    /** Writes every choice, off the main thread. */
    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Choice>> player : choices.entrySet()) {
            for (Map.Entry<String, Choice> mode : player.getValue().entrySet()) {
                String path = player.getKey() + "." + mode.getKey();
                yaml.set(path + ".variante", mode.getValue().variant);
                for (Map.Entry<String, Integer> setting : mode.getValue().settings.entrySet()) {
                    yaml.set(path + ".reglages." + setting.getKey(), setting.getValue());
                }
            }
        }
        final String text = "# The last variant and settings of each player in each training (written by RagoPractice).\n"
                + yaml.saveToString();
        final long mine;
        synchronized (writing) {
            mine = ++version;
        }
        Runnable write = () -> {
            synchronized (writing) {
                if (mine != version) {
                    return;
                }
                write(text);
            }
        };
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        } else {
            write.run();
        }
    }

    private void write(String text) {
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("cannot create " + folder);
            }
            File temporary = new File(file.getPath() + ".tmp");
            try (Writer out = new OutputStreamWriter(Files.newOutputStream(temporary.toPath()), StandardCharsets.UTF_8)) {
                out.write(text);
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write " + file.getName(), e);
        }
    }
}
