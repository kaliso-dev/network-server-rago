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
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * The lobby sidebar: players online, the queue one waits in and for how long. Each player has a
 * scoreboard of their own; a line is sent again only when its text changes, at most once a second.
 */
final class LobbyScoreboard {

    /** Entries of a 1.8 scoreboard are 40 characters at most. */
    private static final int MAX_LINE = 40;

    private final LobbySettings settings;
    private final QueueController queues;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, List<String>> shown = new HashMap<>();

    LobbyScoreboard(LobbySettings settings, QueueController queues) {
        this.settings = settings;
        this.queues = queues;
    }

    /** Shows the lobby sidebar to this player (on joining and on coming back from a game). */
    void show(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = board.registerNewObjective("rago", "dummy");
            objective.setDisplayName(settings.title());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
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
        lines.add(ChatColor.WHITE + "Joueurs : " + ChatColor.GREEN + Bukkit.getOnlinePlayers().size());
        lines.add(blank(1));
        GameMode mode = queues.queueOf(player);
        if (mode == null) {
            lines.add(ChatColor.WHITE + "File : " + ChatColor.GRAY + "aucune");
        } else {
            lines.add(cut(ChatColor.WHITE + "File : " + mode.name()));
            lines.add(ChatColor.WHITE + "Attente : " + ChatColor.YELLOW
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
