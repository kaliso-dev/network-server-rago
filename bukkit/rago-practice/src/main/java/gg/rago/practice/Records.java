package gg.rago.practice;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The record book of the trainings in plugins/RagoPractice/records.yml: read once when the plugin
 * starts, written after each new best, off the main thread (the text is made on the main thread,
 * the disk is written by an async task, the last text written last).
 */
final class Records {

    /** The plugin, whose tasks write the file; null in the tests (the file is then written at once). */
    private final RagoPractice plugin;
    private final Logger logger;
    private final File file;
    private final RecordBook book = new RecordBook();
    private final Object writing = new Object();
    private long version;

    Records(RagoPractice plugin, File file) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.file = file;
    }

    /** A book written at once, with no plugin (the tests). */
    Records(Logger logger, File file) {
        this.plugin = null;
        this.logger = logger;
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
                    logger.warning("records.yml: " + board + "." + key + " is not a player, skipped");
                }
            }
        }
    }

    /** Beside records.yml: the names of the resets done ({@link #resetOnce}), one a line. */
    static final String RESETS_FILE = "records-remis-a-zero.txt";
    /** Beside records.yml: the copy of records.yml made before a reset, this name then the reset's. */
    static final String BACKUP_PREFIX = "records-sauvegarde-";

    /**
     * Once for this name: every board whose id starts with the prefix is emptied (a training whose
     * rules changed: the times of before do not compare with the new ones), after a copy of
     * records.yml as it was (records-sauvegarde-&lt;name&gt;.yml). Nothing is reset if the copy
     * cannot be made; the reset is noted as done once records.yml is written without those boards
     * (if the note cannot be written, the reset is made again at the next start, the first copy
     * kept). Main thread, after {@link #load}, before the boards are defined (a board emptied
     * forgets how it ranks). Returns how many boards were emptied, -1 when nothing was done.
     */
    int resetOnce(String name, String prefix) {
        File folder = file.getAbsoluteFile().getParentFile();
        File resets = new File(folder, RESETS_FILE);
        List<String> done = new ArrayList<>();
        try {
            if (resets.isFile()) {
                for (String line : Files.readAllLines(resets.toPath(), StandardCharsets.UTF_8)) {
                    if (!line.trim().isEmpty()) {
                        done.add(line.trim());
                    }
                }
            }
            if (done.contains(name)) {
                return -1;
            }
            File backup = new File(folder, BACKUP_PREFIX + name + ".yml");
            if (file.isFile() && !backup.exists()) {
                Files.copy(file.toPath(), backup.toPath());
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "The records of " + prefix + "* are not reset (" + name + ")", e);
            return -1;
        }
        int emptied = book.dropAllBut(prefix);
        // at once, on this thread: the reset is written before anything else can happen
        boolean written;
        synchronized (writing) {
            version++;
            written = write(text());
        }
        if (!written) {
            return emptied;
        }
        done.add(name);
        try {
            Files.write(resets.toPath(), (String.join("\n", done) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.log(Level.WARNING, RESETS_FILE + " could not be written: the reset " + name + " is made again at the next start", e);
        }
        logger.info("Records of " + prefix + "* reset (" + name + "): " + emptied
                + " boards emptied; the records before are in " + BACKUP_PREFIX + name + ".yml");
        return emptied;
    }

    /** A new score: kept and written if it is the player's best. */
    RecordBook.Result submit(String board, UUID player, String name, long value) {
        RecordBook.Result result = book.submit(board, player, name, value);
        if (result.personalBest) {
            save();
        }
        return result;
    }

    /**
     * A board as a key of the file, whose dots would make sections: "block-edit.bot.speed2" is
     * block-edit_bot_speed2. No board has a "_" of its own (the ids of the modes and of the
     * variants have dashes), so that the key gives the board back.
     */
    static String toKey(String board) {
        return board.replace('.', '_');
    }

    /** The board of a key of the file: every "_" is a dot ("clutch_island-blocks_2026w46"). */
    static String fromKey(String key) {
        return key.replace('_', '.');
    }

    /** Writes the whole book, off the main thread. */
    void save() {
        final String text = text();
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
        if (plugin != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        } else {
            write.run();
        }
    }

    /** The whole book, as the text of records.yml. */
    private String text() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Map<UUID, RecordBook.Entry>> board : book.boards().entrySet()) {
            for (RecordBook.Entry entry : board.getValue().values()) {
                String path = toKey(board.getKey()) + "." + entry.player;
                yaml.set(path + ".nom", entry.name);
                yaml.set(path + ".record", entry.value);
            }
        }
        return "# The personal bests of the trainings (written by RagoPractice).\n" + yaml.saveToString();
    }

    /** Writes this text to records.yml; false (and said in the log) if it could not be written. */
    private boolean write(String text) {
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
            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "records.yml could not be written", e);
            return false;
        }
    }
}
