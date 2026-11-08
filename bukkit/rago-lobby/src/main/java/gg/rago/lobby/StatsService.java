package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The stats on the local test server: the {@link StatsBook} in memory, one YAML file per player
 * in plugins/RagoLobby/stats. The files are read and written on the writer thread, never on the
 * main thread; the book is only touched on the main thread. On the network, the stats will move to
 * the database and this class to it.
 */
final class StatsService {

    interface Writer {
        /** Writes the text to the file away from the main thread, creating its folder if needed. */
        void write(File file, String text);
    }

    private final Plugin plugin;
    private final StatsBook book;
    private final File folder;
    private final Executor io;
    private final Writer writer;

    StatsService(Plugin plugin, int defaultRating, Executor io, Writer writer) {
        this.plugin = plugin;
        this.book = new StatsBook(defaultRating);
        this.folder = new File(plugin.getDataFolder(), StatsFiles.FOLDER);
        this.io = io;
        this.writer = writer;
    }

    StatsBook book() {
        return book;
    }

    /** Reads every player's file on the writer thread, then fills the book on the main thread. */
    void load() {
        io.execute(() -> {
            final List<PlayerStats> loaded = new ArrayList<>();
            File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    try {
                        UUID id = UUID.fromString(name.substring(0, name.length() - ".yml".length()));
                        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                        loaded.add(StatsFiles.fromYaml(id, text));
                    } catch (IllegalArgumentException | IOException | org.bukkit.configuration.InvalidConfigurationException e) {
                        plugin.getLogger().log(Level.WARNING, "Stats illisibles, ignorées : " + file, e);
                    }
                }
            }
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int added = 0;
                    for (PlayerStats stats : loaded) {
                        if (book.load(stats)) {
                            added++;
                        }
                    }
                    plugin.getLogger().info("Stats de " + added + " joueurs chargées");
                });
            }
        });
    }

    /** A game is over: records it and writes the files of the players whose stats changed. */
    void record(GameMode mode, Collection<UUID> winners, Collection<UUID> losers) {
        for (PlayerStats stats : book.record(mode, winners, losers, StatsService::nameOf, System.currentTimeMillis())) {
            save(stats);
        }
    }

    /** The player joined: keeps their name up to date. */
    void seen(Player player) {
        PlayerStats renamed = book.seen(player.getUniqueId(), player.getName());
        if (renamed != null) {
            save(renamed);
        }
    }

    private void save(PlayerStats stats) {
        writer.write(new File(folder, StatsFiles.fileName(stats.id())), StatsFiles.toYaml(stats));
    }

    private static String nameOf(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        return offline.getName() != null ? offline.getName() : "?";
    }
}
