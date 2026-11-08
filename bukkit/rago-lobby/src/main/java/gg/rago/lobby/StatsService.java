package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
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

    interface XpListener {
        /** The player's Network XP changed (their level, their rank may have too). Main thread. */
        void xpChanged(UUID player);
    }

    private final Plugin plugin;
    private final StatsBook book;
    private final File folder;
    private final Executor io;
    private final Writer writer;
    private final XpListener xpListener;

    StatsService(Plugin plugin, int defaultRating, NetworkLevel levels, Executor io, Writer writer, XpListener xpListener) {
        this.plugin = plugin;
        this.book = new StatsBook(defaultRating, levels);
        this.xpListener = xpListener;
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
            final Map<String, List<TeamStreak>> loadedTeams = new HashMap<>();
            File[] teamFiles = new File(folder, StatsFiles.TEAMS_FOLDER).listFiles((dir, name) -> name.endsWith(".yml"));
            if (teamFiles != null) {
                for (File file : teamFiles) {
                    String name = file.getName();
                    try {
                        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                        loadedTeams.put(name.substring(0, name.length() - ".yml".length()), StatsFiles.teamsFromYaml(text));
                    } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
                        plugin.getLogger().log(Level.WARNING, "Unreadable team streaks, ignored: " + file, e);
                    }
                }
            }
            File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    try {
                        UUID id = UUID.fromString(name.substring(0, name.length() - ".yml".length()));
                        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                        loaded.add(StatsFiles.fromYaml(id, text));
                    } catch (IllegalArgumentException | IOException | org.bukkit.configuration.InvalidConfigurationException e) {
                        plugin.getLogger().log(Level.WARNING, "Unreadable stats, ignored: " + file, e);
                    }
                }
            }
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int added = 0;
                    for (PlayerStats stats : loaded) {
                        if (book.load(stats)) {
                            added++;
                            if (Bukkit.getPlayer(stats.id()) != null) {
                                // Online before the files were read (a /reload): level and rank follow.
                                xpListener.xpChanged(stats.id());
                            }
                        }
                    }
                    for (Map.Entry<String, List<TeamStreak>> mode : loadedTeams.entrySet()) {
                        for (TeamStreak team : mode.getValue()) {
                            book.loadTeam(mode.getKey(), team);
                        }
                    }
                    plugin.getLogger().info("Stats of " + added + " players loaded");
                });
            }
        });
    }

    /**
     * A game is over: records it, tells everyone the XP they got (and a new level or rank), and
     * writes the files of the players whose stats changed.
     */
    void record(GameMode mode, Collection<UUID> winners, Collection<UUID> losers) {
        Map<UUID, Integer> before = xpOf(winners, losers);
        List<PlayerStats> changed = book.record(mode, winners, losers, StatsService::nameOf, System.currentTimeMillis());
        if (!changed.isEmpty()) {
            writer.write(new File(new File(folder, StatsFiles.TEAMS_FOLDER), mode.id() + ".yml"),
                    StatsFiles.teamsToYaml(mode.id(), book.teams(mode.id())));
        }
        for (PlayerStats stats : changed) {
            save(stats);
            Integer old = before.get(stats.id());
            Player player = Bukkit.getPlayer(stats.id());
            if (player != null && old != null && stats.xp() > old) {
                player.sendMessage(ChatColor.AQUA + "+" + (stats.xp() - old) + " Network XP"
                        + ChatColor.GRAY + " (" + (winners.contains(stats.id()) ? "win" : "game played") + ")");
            }
            gained(stats, old);
        }
    }

    /** A kill in a game: counts it, gives the killer some XP, and writes both files. */
    void kill(String modeId, UUID killer, UUID victim) {
        Map<UUID, Integer> before = xpOf(Collections.singletonList(killer),
                victim == null ? Collections.<UUID>emptyList() : Collections.singletonList(victim));
        for (PlayerStats stats : book.kill(modeId, killer, victim, StatsService::nameOf, System.currentTimeMillis())) {
            save(stats);
            Integer old = before.get(stats.id());
            Player player = Bukkit.getPlayer(stats.id());
            if (player != null && old != null && stats.xp() > old) {
                player.sendMessage(ChatColor.AQUA + "+" + (stats.xp() - old) + " Network XP" + ChatColor.GRAY + " (kill)");
            }
            gained(stats, old);
        }
    }

    private Map<UUID, Integer> xpOf(Collection<UUID> some, Collection<UUID> others) {
        Map<UUID, Integer> xp = new HashMap<>();
        List<UUID> all = new ArrayList<>(some);
        all.addAll(others);
        for (UUID id : all) {
            PlayerStats stats = book.get(id);
            xp.put(id, stats == null ? 0 : stats.xp());
        }
        return xp;
    }

    /** After an XP gain: a new level, a new rank, and the listener. */
    private void gained(PlayerStats stats, Integer oldXp) {
        if (oldXp == null || stats.xp() == oldXp) {
            return;
        }
        NetworkLevel levels = book.levels();
        int oldLevel = levels.level(oldXp);
        int level = levels.level(stats.xp());
        Player player = Bukkit.getPlayer(stats.id());
        if (player != null && level > oldLevel) {
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "LEVEL UP! " + ChatColor.GRAY + "You are now "
                    + ChatColor.AQUA + "Network Level " + level + ChatColor.GRAY + ".");
            player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);
            if (levels.rankLevel(level) != levels.rankLevel(oldLevel) && levels.rank(level) != null) {
                player.sendMessage(ChatColor.GOLD + "You unlocked the rank " + levels.rank(level) + ChatColor.GOLD + "!");
            }
        }
        xpListener.xpChanged(stats.id());
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
