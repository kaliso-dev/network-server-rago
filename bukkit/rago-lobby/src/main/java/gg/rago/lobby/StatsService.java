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
    private final ServerSettings server;
    private final File folder;
    private final Executor io;
    private final Writer writer;
    private final XpListener xpListener;

    StatsService(Plugin plugin, int defaultRating, NetworkLevel levels, ServerSettings server, Executor io, Writer writer,
            XpListener xpListener) {
        this.plugin = plugin;
        this.server = server;
        this.book = new StatsBook(defaultRating, levels, server.elo());
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
     * A game is over: records it, tells everyone the XP, the Rago Points and in ranked the ELO they
     * got (and a new level or division), and writes the files of the players whose stats changed.
     * The MVPs (the most kills of the game) get the ELO bonus of the ladder.
     */
    void record(GameMode mode, Collection<UUID> winners, Collection<UUID> losers, Collection<UUID> mvps) {
        Map<UUID, Integer> before = xpOf(winners, losers);
        Map<UUID, Integer> eloBefore = new HashMap<>();
        if (mode.ranked()) {
            List<UUID> all = new ArrayList<>(winners);
            all.addAll(losers);
            for (UUID id : all) {
                eloBefore.put(id, book.elo(id, mode.id()));
            }
        }
        List<PlayerStats> changed = book.record(mode, winners, losers, StatsService::nameOf, System.currentTimeMillis(), mvps);
        if (!changed.isEmpty()) {
            writer.write(new File(new File(folder, StatsFiles.TEAMS_FOLDER), mode.id() + ".yml"),
                    StatsFiles.teamsToYaml(mode.id(), book.teams(mode.id())));
        }
        for (PlayerStats stats : changed) {
            Integer old = before.get(stats.id());
            Player player = Bukkit.getPlayer(stats.id());
            boolean won = winners.contains(stats.id());
            if (player != null && old != null && stats.xp() > old) {
                player.sendMessage(ChatColor.AQUA + "+" + (stats.xp() - old) + " Network XP"
                        + ChatColor.GRAY + " (" + (won ? "win" : "game played") + ")");
            }
            points(stats, won ? server.points().win : server.points().loss, won ? "win" : "game played");
            Integer oldElo = eloBefore.get(stats.id());
            if (player != null && oldElo != null) {
                tellElo(player, oldElo, book.elo(stats.id(), mode.id()), mvps.contains(stats.id()));
            }
            gained(stats, old);
            save(stats);
        }
    }

    /** "Ranked ELO: Gold 245 (+30)", and a new division. */
    private void tellElo(Player player, int before, int after, boolean mvp) {
        EloLadder ladder = book.ladder();
        int change = after - before;
        player.sendMessage(ChatColor.GRAY + "Ranked ELO: " + ladder.format(after) + " " + (change >= 0 ? ChatColor.GREEN + "(+"
                : ChatColor.RED + "(") + change + ")" + (mvp ? ChatColor.GOLD + " MVP +" + ladder.mvpBonus() : ""));
        EloLadder.Division from = ladder.division(before);
        EloLadder.Division to = ladder.division(after);
        if (to.min() > from.min()) {
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "PROMOTED! " + ChatColor.GRAY + "You reached "
                    + to.coloured() + ChatColor.GRAY + ".");
            player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1.5f);
        } else if (to.min() < from.min()) {
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "DEMOTED. " + ChatColor.GRAY + "You are back in "
                    + to.coloured() + ChatColor.GRAY + ".");
        }
    }

    /** Rago Points earned: added and told (the file is written by the caller). */
    private void points(PlayerStats stats, int amount, String reason) {
        if (amount <= 0) {
            return;
        }
        stats.addPoints(amount);
        Player player = Bukkit.getPlayer(stats.id());
        if (player != null) {
            player.sendMessage(ChatColor.GOLD + "+" + amount + " Rago Points" + ChatColor.GRAY + " (" + reason + ")");
        }
    }

    /** A kill in a game: counts it, gives the killer some XP, and writes both files. */
    void kill(String modeId, UUID killer, UUID victim) {
        Map<UUID, Integer> before = xpOf(Collections.singletonList(killer),
                victim == null ? Collections.<UUID>emptyList() : Collections.singletonList(victim));
        for (PlayerStats stats : book.kill(modeId, killer, victim, StatsService::nameOf, System.currentTimeMillis())) {
            Integer old = before.get(stats.id());
            Player player = Bukkit.getPlayer(stats.id());
            if (player != null && old != null && stats.xp() > old) {
                player.sendMessage(ChatColor.AQUA + "+" + (stats.xp() - old) + " Network XP" + ChatColor.GRAY + " (kill)");
            }
            if (stats.id().equals(killer)) {
                points(stats, server.points().kill, "kill");
            }
            gained(stats, old);
            save(stats);
        }
    }

    /** Network XP earned in a training: adds it, tells the player, and writes their file. */
    void giveXp(UUID id, int xp, String reason) {
        PlayerStats before = book.get(id);
        int old = before == null ? 0 : before.xp();
        PlayerStats stats = book.giveXp(id, xp, StatsService::nameOf);
        if (stats == null) {
            return;
        }
        Player player = Bukkit.getPlayer(id);
        if (player != null && stats.xp() > old) {
            player.sendMessage(ChatColor.AQUA + "+" + (stats.xp() - old) + " Network XP" + ChatColor.GRAY + " (" + reason + ")");
        }
        gained(stats, old);
        save(stats);
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

    /**
     * After an XP gain: a new level gives Rago Points (the level times the points of a level), maybe
     * a new colour of the level tag, and unlocks what the shop sells at that level. Then the listener.
     * The caller writes the file.
     */
    private void gained(PlayerStats stats, Integer oldXp) {
        if (oldXp == null || stats.xp() == oldXp) {
            return;
        }
        NetworkLevel levels = book.levels();
        int oldLevel = levels.level(oldXp);
        int level = levels.level(stats.xp());
        if (level > oldLevel) {
            int reward = 0;
            for (int reached = oldLevel + 1; reached <= level; reached++) {
                reward += reached * server.points().perLevel;
            }
            Player player = Bukkit.getPlayer(stats.id());
            if (player != null) {
                player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "LEVEL UP! " + ChatColor.GRAY + "You are now "
                        + ChatColor.AQUA + "Network Level " + level + " " + levels.tag(level) + ChatColor.GRAY + ".");
                player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);
                if (!levels.colour(level).equals(levels.colour(oldLevel))) {
                    player.sendMessage(ChatColor.GRAY + "Your level tag has a new colour: " + levels.tag(level));
                }
                for (String unlocked : unlocked(oldLevel, level)) {
                    player.sendMessage(ChatColor.GRAY + "Unlocked in the shop (/shop): " + unlocked);
                }
            }
            points(stats, reward, "level " + level);
        }
        xpListener.xpChanged(stats.id());
    }

    /** What the shop sells from a level above the old one up to the new one. */
    private List<String> unlocked(int oldLevel, int level) {
        List<String> unlocked = new ArrayList<>();
        for (RankDef rank : server.ranks().sold()) {
            if (rank.level() > oldLevel && rank.level() <= level) {
                unlocked.add(rank.prefix().trim());
            }
        }
        for (Cosmetic cosmetic : server.cosmetics().values()) {
            if (cosmetic.level() > oldLevel && cosmetic.level() <= level) {
                unlocked.add(ChatColor.YELLOW + cosmetic.name() + ChatColor.GRAY + " (" + cosmetic.type().title().toLowerCase(
                        java.util.Locale.ROOT) + ")");
            }
        }
        return unlocked;
    }

    /** The player joined: keeps their name up to date. */
    void seen(Player player) {
        PlayerStats renamed = book.seen(player.getUniqueId(), player.getName());
        if (renamed != null) {
            save(renamed);
        }
    }

    /** Writes the player's file, away from the main thread. */
    void save(PlayerStats stats) {
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
