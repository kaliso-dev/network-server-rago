package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
 * list, the highest ranks first. In a game, the sidebar shows the mode and the player's ping, and
 * in a 1v1 the opponent and their ping.
 */
final class LobbyScoreboard {

    static final String NPC_TEAM = "pnj";

    /** Entries of a 1.8 scoreboard are 40 characters at most. */
    private static final int MAX_LINE = 40;
    /** A sidebar shows 15 lines: a game gives 12, around the first blank and the address. */
    static final int MAX_GAME_LINES = 12;

    /** Team names are 16 characters at most, team prefixes too. */
    private static final int MAX_TEAM = 16;

    /** Below this ping (in milliseconds) it shows in green, below the next one in yellow, else red. */
    static final int GOOD_PING = 80;
    static final int FAIR_PING = 150;

    private static Method getHandle;
    private static Field pingField;
    private static boolean noPing;

    private final LobbySettings settings;
    private final QueueController queues;
    private final StatsBook book;
    private final LobbyState state;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, List<String>> shown = new HashMap<>();
    /** The lines a game gave for its players (LobbyService#setGameSidebar). */
    private final Map<UUID, List<String>> gameLines = new HashMap<>();

    LobbyScoreboard(LobbySettings settings, QueueController queues, StatsBook book, LobbyState state) {
        this.settings = settings;
        this.queues = queues;
        this.book = book;
        this.state = state;
    }

    /**
     * The player's ping in milliseconds, as the server measures it (the keep-alive round trip), or
     * -1 if this server does not say. The 1.8.8 API has no method for it: it is the field ping of
     * the server's player, read once found.
     */
    static int pingOf(Player player) {
        if (noPing) {
            return -1;
        }
        try {
            if (getHandle == null) {
                getHandle = player.getClass().getMethod("getHandle");
            }
            Object handle = getHandle.invoke(player);
            if (pingField == null) {
                pingField = handle.getClass().getField("ping");
            }
            return pingField.getInt(handle);
        } catch (ReflectiveOperationException | RuntimeException e) {
            noPing = true;
            return -1;
        }
    }

    /** "§a42 ms": green when good, yellow when fair, red when bad; "§7?" when unknown. */
    static String pingText(int ping) {
        if (ping < 0) {
            return ChatColor.GRAY + "?";
        }
        ChatColor colour = ping < GOOD_PING ? ChatColor.GREEN : ping < FAIR_PING ? ChatColor.YELLOW : ChatColor.RED;
        return colour + "" + ping + " ms";
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
        gameLines.remove(player);
    }

    /** The lines a game shows in the player's sidebar, or null for the lobby's own game lines. */
    void setGameLines(UUID player, List<String> lines) {
        if (lines == null) {
            gameLines.remove(player);
        } else {
            gameLines.put(player, new ArrayList<>(lines));
        }
    }

    /** The lines, top to bottom. Blank lines differ by an invisible colour code. */
    private List<String> lines(Player player) {
        String modeId = state.gameOf(player.getUniqueId());
        if (modeId != null) {
            return gameLines(player, modeId);
        }
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

    /** In a game: the mode, the player's ping, and in a 1v1 the opponent and their ping. */
    private List<String> gameLines(Player player, String modeId) {
        List<String> given = gameLines.get(player.getUniqueId());
        if (given != null) {
            return givenLines(given, settings.address());
        }
        List<String> lines = new ArrayList<>();
        lines.add(blank(0));
        GameMode mode = settings.catalog().get(modeId);
        lines.add(cut(ChatColor.WHITE + "Mode: " + (mode == null ? modeId : mode.name())));
        lines.add(blank(1));
        List<UUID> opponents = state.opponentsOf(player.getUniqueId());
        if (opponents.size() == 1) {
            Player opponent = Bukkit.getPlayer(opponents.get(0));
            PlayerStats stats = book.get(opponents.get(0));
            String name = opponent != null ? opponent.getName() : stats != null ? stats.name() : "?";
            lines.add(cut(ChatColor.WHITE + "Opponent: " + ChatColor.RED + name));
            lines.add(blank(3));
            lines.add(ChatColor.WHITE + "Your ping: " + pingText(pingOf(player)));
            lines.add(ChatColor.WHITE + "Their ping: " + (opponent == null ? ChatColor.GRAY + "left" : pingText(pingOf(opponent))));
        } else {
            lines.add(ChatColor.WHITE + "Your ping: " + pingText(pingOf(player)));
        }
        lines.add(blank(2));
        lines.add(cut(settings.address()));
        return lines;
    }

    /**
     * The lines a game gave, as sidebar entries: an entry shows once, so a blank line or a line
     * already there gets an invisible colour code of its own.
     */
    static List<String> givenLines(List<String> given, String address) {
        List<String> lines = new ArrayList<>();
        lines.add(blank(0));
        int unique = 4;
        for (String line : given.subList(0, Math.min(given.size(), MAX_GAME_LINES))) {
            String text = line == null ? "" : line;
            if (text.isEmpty() || lines.contains(cut(text))) {
                text = (text.length() > MAX_LINE - 4 ? text.substring(0, MAX_LINE - 4) : text) + blank(unique++);
            }
            lines.add(cut(text));
        }
        lines.add(blank(2));
        lines.add(cut(address));
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
