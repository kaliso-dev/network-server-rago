package gg.rago.practice;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The record book of the trainings in plugins/RagoPractice/records.yml: read once when the plugin
 * starts, written after each new best, off the main thread (the text is made on the main thread,
 * the disk is written by an async task, the last text written last).
 */
final class Records {

    private final RagoPractice plugin;
    private final File file;
    private final RecordBook book = new RecordBook();
    private final Object writing = new Object();
    private long version;

    Records(RagoPractice plugin, File file) {
        this.plugin = plugin;
        this.file = file;
    }

    RecordBook book() {
        return book;
    }

    /** Reads the file, if there is one. */
    void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String board : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(board);
            if (section == null) {
                continue;
            }
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                try {
                    book.load(fromKey(board), UUID.fromString(key), entry.getString("nom", "?"), entry.getLong("record"));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("records.yml: " + board + "." + key + " is not a player, skipped");
                }
            }
        }
    }

    /** A new score: kept and written if it is the player's best. */
    RecordBook.Result submit(String board, UUID player, String name, long value) {
        RecordBook.Result result = book.submit(board, player, name, value);
        if (result.personalBest) {
            save();
        }
        return result;
    }

    /** The boards are written with dots in YAML keys as sections: "clutch.single" is clutch_single. */
    private static String toKey(String board) {
        return board.replace('.', '_');
    }

    private static String fromKey(String key) {
        int dash = key.indexOf('_');
        return dash < 0 ? key : key.substring(0, dash) + "." + key.substring(dash + 1);
    }

    /** Writes the whole book, off the main thread. */
    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Map<UUID, RecordBook.Entry>> board : book.boards().entrySet()) {
            for (RecordBook.Entry entry : board.getValue().values()) {
                String path = toKey(board.getKey()) + "." + entry.player;
                yaml.set(path + ".nom", entry.name);
                yaml.set(path + ".record", entry.value);
            }
        }
        final String text = "# The personal bests of the trainings (written by RagoPractice).\n" + yaml.saveToString();
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
            plugin.getLogger().log(Level.WARNING, "records.yml could not be written", e);
        }
    }
}
