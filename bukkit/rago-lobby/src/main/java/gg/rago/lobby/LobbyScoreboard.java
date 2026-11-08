package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * The lobby sidebar: players online, the player's Network level and rank, the queue one waits in
 * and for how long. Each player has a scoreboard of their own; a line is sent again only when its
 * text changes, at most once a second. The scoreboard also hides the name tags of the NPC bodies
 * (team {@link #NPC_TEAM}): their names float above them as lines of their own. And it puts every
 * player in the team of their rank, whose prefix shows the rank above their head and in the tab
 * list, the highest ranks first.
 */
final class LobbyScoreboard {

    static final String NPC_TEAM = "pnj";

    /** Entries of a 1.8 scoreboard are 40 characters at most. */
    private static final int MAX_LINE = 40;

    /** Team names are 16 characters at most, team prefixes too. */
    private static final int MAX_TEAM = 16;

    private final LobbySettings settings;
    private final QueueController queues;
    private final StatsBook book;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, List<String>> shown = new HashMap<>();

    LobbyScoreboard(LobbySettings settings, QueueController queues, StatsBook book) {
        this.settings = settings;
        this.queues = queues;
        this.book = book;
    }

    /**
     * The team of the players who reached this rank (0: no rank yet). The tab list sorts teams by
     * name, so the higher the rank, the smaller the name.
     */
    static String rankTeam(int rankLevel) {
        return String.format("r%05d", NetworkLevel.MAX_LEVEL - Math.max(0, rankLevel));
    }

    /** The prefix of a rank's team: the rank and a space, or nothing below the first rank. */
    static String rankPrefix(String rank) {
        if (rank == null) {
            return "";
        }
        String prefix = rank + " ";
        return prefix.length() <= MAX_TEAM ? prefix : rank.substring(0, Math.min(rank.length(), MAX_TEAM));
    }

    /** Shows the lobby sidebar to this player (on joining and on coming back from a game). */
    void show(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = board.registerNewObjective("rago", "dummy");
            objective.setDisplayName(settings.title());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            Team npcs = board.registerNewTeam(NPC_TEAM);
            npcs.setNameTagVisibility(NameTagVisibility.NEVER);
            for (int i = 0; i < settings.npcs().size(); i++) {
                npcs.addEntry(LobbyNpcs.bodyName(i));
            }
            board.registerNewTeam(rankTeam(0)).setPrefix("");
            for (Map.Entry<Integer, String> rank : settings.levels().ranks().entrySet()) {
                board.registerNewTeam(rankTeam(rank.getKey())).setPrefix(rankPrefix(rank.getValue()));
            }
            boards.put(player.getUniqueId(), board);
            shown.put(player.getUniqueId(), new ArrayList<String>());
        }
        player.setScoreboard(board);
        update(player);
    }

    void update(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null || player.getScoreboard() != board) {
            return;
        }
        syncRanks(board);
        Objective objective = board.getObjective("rago");
        List<String> lines = lines(player);
        List<String> before = shown.get(player.getUniqueId());
        if (lines.equals(before)) {
            return;
        }
        for (String old : before) {
            if (!lines.contains(old)) {
                board.resetScores(old);
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            objective.getScore(lines.get(i)).setScore(lines.size() - i);
        }
        shown.put(player.getUniqueId(), lines);
    }

    /** Every online player in the team of their rank, on this scoreboard. */
    private void syncRanks(Scoreboard board) {
        NetworkLevel levels = book.levels();
        for (Player other : Bukkit.getOnlinePlayers()) {
            Team team = board.getTeam(rankTeam(levels.rankLevel(book.level(other.getUniqueId()))));
            if (team != null && !team.hasEntry(other.getName())) {
                // addEntry takes the player out of their old team.
                team.addEntry(other.getName());
            }
        }
    }

    void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    void forget(UUID player) {
        boards.remove(player);
        shown.remove(player);
    }

    /** The lines, top to bottom. Blank lines differ by an invisible colour code. */
    private List<String> lines(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add(blank(0));
        lines.add(ChatColor.WHITE + "Players: " + ChatColor.GREEN + Bukkit.getOnlinePlayers().size());
        lines.add(blank(1));
        NetworkLevel levels = book.levels();
        PlayerStats stats = book.get(player.getUniqueId());
        int xp = stats == null ? 0 : stats.xp();
        int level = levels.level(xp);
        String rank = levels.rank(level);
        lines.add(ChatColor.WHITE + "Level: " + ChatColor.AQUA + level);
        lines.add(cut(ChatColor.WHITE + "Rank: " + (rank == null ? ChatColor.GRAY + "none" : rank)));
        lines.add(level >= NetworkLevel.MAX_LEVEL ? ChatColor.WHITE + "XP: " + ChatColor.AQUA + "max"
                : ChatColor.WHITE + "XP: " + ChatColor.AQUA + levels.xpIntoLevel(xp) + ChatColor.GRAY + "/"
                        + levels.xpToNext(level));
        lines.add(blank(3));
        GameMode mode = queues.queueOf(player);
        if (mode == null) {
            lines.add(ChatColor.WHITE + "Queue: " + ChatColor.GRAY + "none");
        } else {
            lines.add(cut(ChatColor.WHITE + "Queue: " + mode.name()));
            lines.add(ChatColor.WHITE + "Waiting: " + ChatColor.YELLOW
                    + clock(queues.waited(player, System.currentTimeMillis())));
        }
        lines.add(blank(2));
        lines.add(cut(settings.address()));
        return lines;
    }

    private static String blank(int n) {
        return ChatColor.values()[n].toString() + ChatColor.RESET;
    }

    private static String cut(String line) {
        return line.length() <= MAX_LINE ? line : line.substring(0, MAX_LINE);
    }

    /** 0:42, 12:05. */
    static String clock(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        long s = seconds % 60;
        return (seconds / 60) + ":" + (s < 10 ? "0" : "") + s;
    }
}
