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
    /** The Rago Points multiplier of a player's coin booster (1 without one). */
    private java.util.function.ToDoubleFunction<UUID> coinBoost = id -> 1.0;

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

    /** Where the coin boosters are: the points of the games and trainings are multiplied by it. */
    void setCoinBoost(java.util.function.ToDoubleFunction<UUID> coinBoost) {
        this.coinBoost = coinBoost;
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
                        loaded.add(StatsFiles.fromYaml(id, text, server.season().number(), server.elo()));
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
     * The MVPs (the most kills of the game) get the ELO bonus of the ladder. A game that is not
     * rated (a /duel, started without the queue) moves neither the ELO nor the win streaks.
     */
    void record(GameMode mode, Collection<UUID> winners, Collection<UUID> losers, Collection<UUID> mvps, boolean rated) {
        Map<UUID, Integer> before = xpOf(winners, losers);
        Map<UUID, Integer> eloBefore = new HashMap<>();
        if (mode.ranked() && rated) {
            List<UUID> all = new ArrayList<>(winners);
            all.addAll(losers);
            for (UUID id : all) {
                eloBefore.put(id, book.elo(id, mode.id()));
            }
        }
        List<PlayerStats> changed = book.record(mode, winners, losers, StatsService::nameOf, System.currentTimeMillis(), mvps,
                rated);
        if (!changed.isEmpty() && rated) {
            writer.write(new File(new File(folder, StatsFiles.TEAMS_FOLDER), mode.id() + ".yml"),
                    StatsFiles.teamsToYaml(mode.id(), book.teams(mode.id())));
        }
        for (PlayerStats stats : changed) {
            Integer old = before.get(stats.id());
            Player player = Bukkit.getPlayer(stats.id());
            boolean won = winners.contains(stats.id());
            if (player != null && old != null && stats.xp() > old) {
                player.sendMessage(ChatColor.AQUA + "+" + (stats.xp() - old) + " Network XP"
                        + ChatColor.GRAY + " (" + (won ? "win" : "game played") + ")" + boostNote(stats.id()));
            }
            played(stats, won ? server.points().win : server.points().loss, won ? "win" : "game played");
            Integer oldElo = eloBefore.get(stats.id());
            if (player != null && oldElo != null) {
                tellElo(player, oldElo, book.elo(stats.id(), mode.id()), mvps.contains(stats.id()));
            }
            List<Quests.Quest> done = new ArrayList<>(quest(stats, Quests.Goal.GAMES, mode.id()));
            if (won) {
                done.addAll(quest(stats, Quests.Goal.WINS, mode.id()));
                if (mode.ranked() && rated) {
                    done.addAll(quest(stats, Quests.Goal.RANKED_WINS, mode.id()));
                }
            }
            questsDone(stats, done);
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
        points(stats, amount, reason, "");
    }

    private void points(PlayerStats stats, int amount, String reason, String note) {
        if (amount <= 0) {
            return;
        }
        stats.addPoints(amount);
        Player player = Bukkit.getPlayer(stats.id());
        if (player != null) {
            player.sendMessage(ChatColor.GOLD + "+" + amount + " Rago Points" + ChatColor.GRAY + " (" + reason + ")" + note);
        }
    }

    /** Rago Points earned by playing (a game, a kill, a training): the coin booster multiplies them. */
    private void played(PlayerStats stats, int amount, String reason) {
        double boost = coinBoost.applyAsDouble(stats.id());
        points(stats, boosted(amount, boost), reason, boost > 1.0 ? ChatColor.GOLD + " x" + XpBoosters.rate(boost)
                + " points booster" : "");
    }

    static int boosted(int amount, double boost) {
        return amount <= 0 ? 0 : (int) Math.min(ServerSettings.MAX_POINTS, Math.round(amount * Math.max(1.0, boost)));
    }

    /** A kill in a game: counts it, gives the killer some XP, and writes both files. */
    void kill(String modeId, UUID killer, UUID victim) {
        Map<UUID, Integer> before = xpOf(Collections.singletonList(killer),
                victim == null ? Collections.<UUID>emptyList() : Collections.singletonList(victim));
        for (PlayerStats stats : book.kill(modeId, killer, victim, StatsService::nameOf, System.currentTimeMillis())) {
            Integer old = before.get(stats.id());
            Player player = Bukkit.getPlayer(stats.id());
            if (player != null && old != null && stats.xp() > old) {
                player.sendMessage(ChatColor.AQUA + "+" + (stats.xp() - old) + " Network XP" + ChatColor.GRAY + " (kill)"
                        + boostNote(stats.id()));
            }
            if (stats.id().equals(killer)) {
                played(stats, server.points().kill, "kill");
                questsDone(stats, quest(stats, Quests.Goal.KILLS, modeId));
            }
            gained(stats, old);
            save(stats);
        }
    }

    /** A Bed Wars final kill: counted and written; the player's final kills now, every mode together. */
    int finalKill(String modeId, UUID killer) {
        PlayerStats stats = book.finalKill(modeId, killer, StatsService::nameOf);
        save(stats);
        return stats.finalKills();
    }

    /** A Bed Wars final death: counted and written. */
    void finalDeath(String modeId, UUID player) {
        save(book.finalDeath(modeId, player, StatsService::nameOf));
    }

    /** A bed broken: counted and written; the beds the player broke now, every mode together. */
    int bedBroken(String modeId, UUID player) {
        PlayerStats stats = book.bedBroken(modeId, player, StatsService::nameOf);
        save(stats);
        return stats.beds();
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
            player.sendMessage(ChatColor.AQUA + "+" + (stats.xp() - old) + " Network XP" + ChatColor.GRAY + " (" + reason + ")"
                    + boostNote(id));
        }
        played(stats, server.points().forXp(xp), reason);
        gained(stats, old);
        save(stats);
    }

    // --- Quests and level rewards ----------------------------------------------------------------

    /** One more of what the quests count, today and this week: the quests it completed. */
    private List<Quests.Quest> quest(PlayerStats stats, Quests.Goal goal, String modeId) {
        long now = System.currentTimeMillis();
        return server.quests().advance(stats, goal, modeId, 1, StatsPeriod.dayKey(now), StatsPeriod.weekKey(now));
    }

    /**
     * The trainings moved a quest on (a success, a record, the daily challenge, a minute of
     * training): the quests done give their XP. The file is written only when a quest moved.
     */
    void trainingQuest(UUID id, Quests.Goal goal, String modeId) {
        long now = System.currentTimeMillis();
        String day = StatsPeriod.dayKey(now);
        String week = StatsPeriod.weekKey(now);
        PlayerStats stats = book.profile(id, nameOf(id));
        if (!server.quests().wants(stats, goal, modeId, day, week)) {
            return;
        }
        int old = stats.xp();
        questsDone(stats, server.quests().advance(stats, goal, modeId, 1, day, week));
        gained(stats, old);
        save(stats);
    }

    /** The quests done give their XP (boosted), told to the player. The caller then calls gained. */
    private void questsDone(PlayerStats stats, List<Quests.Quest> done) {
        Player player = Bukkit.getPlayer(stats.id());
        for (Quests.Quest quest : done) {
            int before = stats.xp();
            book.giveXp(stats.id(), quest.xp(), StatsService::nameOf);
            if (player != null) {
                player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "QUEST COMPLETE! " + ChatColor.WHITE + quest.name()
                        + ChatColor.GRAY + " (" + quest.period().title().toLowerCase(java.util.Locale.ROOT).replace(" quests",
                                "") + ")");
                player.sendMessage(ChatColor.AQUA + "+" + (stats.xp() - before) + " Network XP" + ChatColor.GRAY + " (quest)"
                        + boostNote(stats.id()));
                player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 2f);
            }
        }
    }

    /**
     * A step of Getting started done (a training, a record, a duel...), if this server offers it:
     * the first time, its Rago Points with a sound and the next step; the last one, the reward of
     * all of them. The file is written.
     */
    void gettingStarted(UUID id, GettingStarted.Step step, List<GettingStarted.Step> steps) {
        if (!steps.contains(step)) {
            return;
        }
        PlayerStats stats = book.profile(id, nameOf(id));
        if (!GettingStarted.complete(stats, step)) {
            return;
        }
        GettingStarted quests = server.gettingStarted();
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "QUEST COMPLETE! " + ChatColor.WHITE + step.title()
                    + ChatColor.GRAY + " (getting started " + GettingStarted.done(stats, steps) + "/" + steps.size() + ")");
            player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 2f);
        }
        points(stats, quests.points(step), "getting started");
        GettingStarted.Step next = GettingStarted.next(stats, steps);
        if (next != null && player != null) {
            player.sendMessage(ChatColor.YELLOW + "Next: " + ChatColor.WHITE + next.title() + ChatColor.GRAY + " - "
                    + String.join(" ", next.how()));
        }
        if (GettingStarted.finish(stats, steps)) {
            String reward = quests.reward();
            int points = 0;
            String got = null;
            if (reward != null) {
                Cosmetic cosmetic = server.cosmetics().get(reward);
                got = rewardName(server, reward);
                if (!stats.cosmetics().add(reward) && cosmetic != null) {
                    points += cosmetic.price();
                    got += ChatColor.GRAY + " (yours already: its price in points)";
                }
            }
            if (player != null) {
                player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "GETTING STARTED COMPLETE! " + ChatColor.GRAY
                        + (got == null ? "Well done." : got));
                if (got != null) {
                    player.sendMessage(ChatColor.GRAY + "Equip it in your profile, the shop or with /style.");
                }
                player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);
            }
            points(stats, points, "getting started");
        }
        save(stats);
    }

    /**
     * Gives the rewards of the levels the player reached and did not get yet (all of them for a
     * player of before the rewards): Rago Points, cosmetics, colours of name, badges. A cosmetic
     * already bought gives its price back in points. Returns true if anything was given.
     */
    /**
     * The ranked seasons that ended give their reward once, at the player's arrival: the Rago
     * Points of the best division they reached in it (saison.recompenses). True if one was given.
     */
    boolean claimSeasons(PlayerStats stats) {
        boolean changed = false;
        Player player = Bukkit.getPlayer(stats.id());
        Season season = server.season();
        for (Map.Entry<Integer, PlayerStats.SeasonResult> entry : stats.seasons().entrySet()) {
            PlayerStats.SeasonResult result = entry.getValue();
            if (result.rewarded) {
                continue;
            }
            result.rewarded = true;
            changed = true;
            if (player != null) {
                player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Season " + entry.getKey() + " is over! " + ChatColor.GRAY
                        + "Your best: " + book.ladder().format(result.elo) + ChatColor.GRAY + ". " + ChatColor.YELLOW
                        + season.name() + ChatColor.GRAY + " has begun: every ranked ELO starts again from 0. /season");
            }
            points(stats, season.reward(result.division), "season " + entry.getKey() + ": " + result.division);
        }
        return changed;
    }

    boolean claimRewards(PlayerStats stats) {
        int level = book.level(stats.id());
        if (level <= stats.rewardsLevel()) {
            return false;
        }
        List<LevelRewards.Reward> due = server.levelRewards().between(stats.rewardsLevel(), level);
        stats.setRewardsLevel(level);
        Player player = Bukkit.getPlayer(stats.id());
        for (LevelRewards.Reward reward : due) {
            List<String> got = new ArrayList<>();
            int points = reward.points();
            for (String item : reward.items()) {
                Cosmetic cosmetic = server.cosmetics().get(item);
                String name = rewardName(server, item);
                if (stats.cosmetics().add(item)) {
                    got.add(name);
                } else if (cosmetic != null) {
                    points += cosmetic.price();
                    got.add(name + ChatColor.GRAY + " (yours already: its price in points)");
                }
            }
            if (player != null) {
                player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "LEVEL " + reward.level() + " REWARD! "
                        + ChatColor.GRAY + (got.isEmpty() ? "Rago Points" : String.join(ChatColor.GRAY + ", ", got)));
                if (!got.isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "Equip it in your profile, the shop or with /style.");
                }
            }
            points(stats, points, "level " + reward.level() + " reward");
        }
        return !due.isEmpty();
    }

    /** "§eClouds §7(trail)", "name colour §bAqua", "badge §e§l✫100": a cosmetic or a style, for the chat. */
    static String rewardName(ServerSettings server, String item) {
        Cosmetic cosmetic = server.cosmetics().get(item);
        if (cosmetic != null) {
            String type = cosmetic.type().title().toLowerCase(java.util.Locale.ROOT);
            return ChatColor.YELLOW + cosmetic.name() + ChatColor.GRAY + " (" + type.substring(0, type.length() - 1) + ")";
        }
        NameStyle style = server.styles().get(item);
        if (style == null) {
            return item;
        }
        return style.kind() == NameStyle.Kind.COLOUR ? ChatColor.GRAY + "name colour " + style.sample()
                : style.kind() == NameStyle.Kind.PLUS ? ChatColor.GRAY + "colour of + " + style.sample()
                : ChatColor.GRAY + "badge " + style.text();
    }

    /** " x1.5 boost" after the XP of a player with a boost, else "". */
    private String boostNote(UUID player) {
        return boostText(book.boost(player));
    }

    static String boostText(double boost) {
        if (boost <= 1.0) {
            return "";
        }
        String times = boost == Math.rint(boost) ? String.valueOf((long) boost) : String.valueOf(Math.round(boost * 100) / 100.0);
        return ChatColor.LIGHT_PURPLE + " x" + times + " boost";
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
        claimRewards(stats);
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

    /**
     * The player joined: keeps their name and skin (for the podiums) up to date, gives the rewards
     * of levels passed before.
     */
    void seen(Player player) {
        PlayerStats renamed = book.seen(player.getUniqueId(), player.getName());
        PlayerStats stats = book.get(player.getUniqueId());
        boolean changed = renamed != null;
        if (stats != null) {
            Skin skin = Skin.of(player);
            if (skin != null && !skin.equals(stats.skin())) {
                stats.setSkin(skin);
                changed = true;
            }
            changed |= claimRewards(stats);
            changed |= claimSeasons(stats);
        }
        if (changed && stats != null) {
            save(stats);
        }
    }

    /** Writes the player's file, away from the main thread. */
    void save(PlayerStats stats) {
        writer.write(new File(folder, StatsFiles.fileName(stats.id())), StatsFiles.toYaml(stats, server.season().number()));
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
