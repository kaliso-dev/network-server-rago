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
import org.bukkit.potion.PotionEffectType;
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
 * player in a team of their own, whose prefix shows their rank (in a game: their team, as the game
 * says) and whose suffix shows their badge and Network level, above their head: "§c[ADMIN] §cSofiene
 * §6[12✫]" (a name takes one colour there: the middle one of a gradient). In the lobby the tab list
 * shows the whole look, the gradient too: "§6[12✫] §d[PREMIUM§f+§d] §5So§dfi§fene §d§l✦". The tab list
 * sorts the teams by name: the players of a game first, then the highest ranks. In a game, the
 * sidebar shows the mode and the player's ping, and in a 1v1 the opponent and their ping; a game
 * that gives its own lines and shows no ping gets them after its lines. And in a game, the health
 * of the players around (their absorption too) shows under their names, as on Hypixel: "20 ❤".
 */
final class LobbyScoreboard {

    static final String NPC_TEAM = "pnj";

    /** The objective under the names, in a game: the health. */
    static final String HEALTH = "vie";
    /** How far the health under the names is kept up to date, in blocks. */
    private static final double HEALTH_RANGE = 48;

    /** Entries of a 1.8 scoreboard are 40 characters at most. */
    private static final int MAX_LINE = 40;
    /** A sidebar shows 15 lines: a game gives 12, around the first blank and the address. */
    static final int MAX_GAME_LINES = 12;

    /** Team names are 16 characters at most, team prefixes and suffixes too. */
    private static final int MAX_TEAM = 16;

    /** A player's team on every scoreboard: its name (for the order of the tab list), prefix and suffix. */
    static final class Tag {
        final String team;
        final String prefix;
        final String suffix;
        /** Invisible: no name above the head (a 1.8 team shows it otherwise, potion or not). */
        final boolean hidden;

        Tag(String team, String prefix, String suffix, boolean hidden) {
            this.team = team;
            this.prefix = prefix;
            this.suffix = suffix;
            this.hidden = hidden;
        }
    }

    /** Below this ping (in milliseconds) it shows in green, below the next one in yellow, else red. */
    static final int GOOD_PING = 80;
    static final int FAIR_PING = 150;

    private static Method getHandle;
    private static Field pingField;
    private static boolean noPing;
    private static Method absorptionMethod;
    private static boolean noAbsorption;

    private final LobbySettings settings;
    private final QueueController queues;
    private final StatsBook book;
    private final LobbyState state;
    private final Ranks ranks;
    private final NameLooks names;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    /** The name each player shows in the tab list, as last sent (null: their plain name). */
    private final Map<UUID, String> listNames = new HashMap<>();
    /** A number for each player seen, for the names of their teams. */
    private final Map<UUID, Integer> numbers = new HashMap<>();
    /** On each player's scoreboard, the team of each other player. */
    private final Map<UUID, Map<UUID, String>> placed = new HashMap<>();
    private final Map<UUID, List<String>> shown = new HashMap<>();
    /** The lines a game gave for its players (LobbyService#setGameSidebar). */
    private final Map<UUID, List<String>> gameLines = new HashMap<>();
    /** The lines of the replay each player watches (out of any game). */
    private final Map<UUID, List<String>> replayLines = new HashMap<>();
    /** What a game puts before the names of its players, their team (LobbyService#setGameNameTag). */
    private final Map<UUID, String> gamePrefixes = new HashMap<>();
    /** The number of each of those prefixes, the same on every scoreboard: its teams sort together. */
    private final Map<String, Integer> prefixTeams = new HashMap<>();

    LobbyScoreboard(LobbySettings settings, QueueController queues, StatsBook book, LobbyState state, NameLooks names) {
        this.settings = settings;
        this.queues = queues;
        this.book = book;
        this.state = state;
        this.names = names;
        this.ranks = names.server().ranks();
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

    /**
     * The hearts of absorption of the player (a golden apple's), in health points: the 1.8.8 API
     * has no method for it, the server's player has (getAbsorptionHearts). 0 if this server does not say.
     */
    static float absorptionOf(Player player) {
        if (noAbsorption) {
            return 0;
        }
        try {
            if (getHandle == null) {
                getHandle = player.getClass().getMethod("getHandle");
            }
            Object handle = getHandle.invoke(player);
            if (absorptionMethod == null) {
                absorptionMethod = handle.getClass().getMethod("getAbsorptionHearts");
            }
            return ((Number) absorptionMethod.invoke(handle)).floatValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            noAbsorption = true;
            return 0;
        }
    }

    /** The number under a name: the health and the absorption, in health points, rounded up (20 for full). */
    static int healthPoints(double health, double absorption) {
        return (int) Math.ceil(Math.max(0, health) + Math.max(0, absorption));
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
     * The team of a player: in a game, "g" and the number of their game's prefix; in the lobby, "r"
     * and the position of their rank (00 the highest); then the player's own number. The tab list
     * sorts the teams by name.
     */
    static String teamName(boolean inGame, int order, int number) {
        // both keys have a fixed width: two teams never end up with the same name
        String game = Integer.toString(Math.min(order, 1295), 36);
        String key = inGame ? "g" + (game.length() == 1 ? "0" : "") + game : String.format("r%02d", Math.min(order, 99));
        return key + Integer.toString(number, 36);
    }

    /** A prefix or a suffix fits in 16 characters. */
    static String fit(String text) {
        return text.length() <= MAX_TEAM ? text : text.substring(0, MAX_TEAM);
    }

    /** Shows the lobby sidebar to this player (on joining and on coming back from a game). */
    void show(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = board.registerNewObjective("rago", "dummy");
            objective.setDisplayName(settings.title());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            board.registerNewObjective(HEALTH, "dummy").setDisplayName(ChatColor.RED + "\u2764");
            Team npcs = board.registerNewTeam(NPC_TEAM);
            npcs.setNameTagVisibility(NameTagVisibility.NEVER);
            for (int i = 0; i < settings.npcs().size(); i++) {
                npcs.addEntry(LobbyNpcs.bodyName(i));
            }
            for (int i = 0; i < LobbySettings.MAX_PODIUM; i++) {
                npcs.addEntry(LobbyNpcs.podiumName(i));
            }
            npcs.addEntry(LobbyNpcs.questsName());
            placed.remove(player.getUniqueId());
            boards.put(player.getUniqueId(), board);
            shown.put(player.getUniqueId(), new ArrayList<String>());
        }
        player.setScoreboard(board);
        update(player);
    }

    void update(Player player) {
        update(player, tags());
    }

    private void update(Player player, Map<UUID, Tag> tags) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null || player.getScoreboard() != board) {
            return;
        }
        syncTags(player.getUniqueId(), board, tags);
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

    /** The team of every online player, worked out once for every scoreboard. */
    private Map<UUID, Tag> tags() {
        Map<UUID, Tag> tags = new HashMap<>();
        NetworkLevel levels = book.levels();
        for (Player other : Bukkit.getOnlinePlayers()) {
            UUID id = other.getUniqueId();
            Integer number = numbers.get(id);
            if (number == null) {
                number = numbers.size();
                numbers.put(id, number);
            }
            String badge = names.shownBadge(id);
            String game = gamePrefixes.get(id);
            // A rainbow level (100) in its first colour when it does not fit after the badge.
            String level = " " + levels.tag(names.shownLevel(id), MAX_TEAM - 1 - (game != null ? 0 : badge.length()));
            boolean hidden = other.hasPotionEffect(PotionEffectType.INVISIBILITY);
            String suffix = game != null ? fit(level) : suffix(badge, level);
            listName(other, game != null ? null : listNameOf(other));
            if (game != null) {
                Integer order = prefixTeams.get(game);
                if (order == null) {
                    order = prefixTeams.size();
                    prefixTeams.put(game, order);
                }
                tags.put(id, new Tag(teamName(true, order, number), game, suffix, hidden));
            } else {
                RankDef rank = names.shownRank(id);
                tags.put(id, new Tag(teamName(false, ranks.position(rank), number), fit(names.headPrefix(id)), suffix, hidden));
            }
        }
        return tags;
    }

    /** The badge and the level after the name, or the badge alone when both do not fit in 16 characters. */
    static String suffix(String badge, String level) {
        if ((badge + level).length() <= MAX_TEAM) {
            return badge + level;
        }
        return badge.isEmpty() ? fit(level) : fit(badge);
    }

    /** "§7[12✫] §d[PREMIUM§f+§d] §5So§dfi§fene §d§l✦": the level, the rank, the name and the badge, in the tab list. */
    private String listNameOf(Player player) {
        UUID id = player.getUniqueId();
        return book.levels().tag(names.shownLevel(id)) + " " + names.shownStyled(id, player.getName()) + names.shownBadge(id);
    }

    /** The name of the player in the tab list (null: their plain name, which the game's team colours). */
    private void listName(Player player, String name) {
        String before = listNames.get(player.getUniqueId());
        if (name == null ? before == null : name.equals(before)) {
            return;
        }
        player.setPlayerListName(name);
        if (name == null) {
            listNames.remove(player.getUniqueId());
        } else {
            listNames.put(player.getUniqueId(), name);
        }
    }

    /**
     * Every online player in their own team on this scoreboard, with their prefix and suffix; the
     * teams of the players who left, or who changed team, go.
     */
    private void syncTags(UUID viewer, Scoreboard board, Map<UUID, Tag> tags) {
        Map<UUID, String> mine = placed.get(viewer);
        if (mine == null) {
            mine = new HashMap<>();
            placed.put(viewer, mine);
        }
        java.util.Iterator<Map.Entry<UUID, String>> gone = mine.entrySet().iterator();
        while (gone.hasNext()) {
            Map.Entry<UUID, String> entry = gone.next();
            Tag tag = tags.get(entry.getKey());
            if (tag == null || !tag.team.equals(entry.getValue())) {
                Team old = board.getTeam(entry.getValue());
                if (old != null) {
                    old.unregister();
                }
                gone.remove();
            }
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            Tag tag = tags.get(other.getUniqueId());
            if (tag == null) {
                continue;
            }
            Team team = board.getTeam(tag.team);
            if (team == null) {
                team = board.registerNewTeam(tag.team);
            }
            if (!tag.prefix.equals(team.getPrefix())) {
                team.setPrefix(tag.prefix);
            }
            if (!tag.suffix.equals(team.getSuffix())) {
                team.setSuffix(tag.suffix);
            }
            NameTagVisibility visibility = tag.hidden ? NameTagVisibility.NEVER : NameTagVisibility.ALWAYS;
            if (team.getNameTagVisibility() != visibility) {
                team.setNameTagVisibility(visibility);
            }
            // a client knows a nicked player by the nick, but the player knows themselves by their name
            String entry = other.getUniqueId().equals(viewer) ? other.getName() : names.shownName(other.getUniqueId(),
                    other.getName());
            if (!team.hasEntry(entry)) {
                // addEntry takes the player out of their old team.
                team.addEntry(entry);
            }
            mine.put(other.getUniqueId(), tag.team);
        }
    }

    /** What shows before the player's name while they play (16 characters at most), or null for their rank. */
    void setGamePrefix(UUID player, String prefix) {
        if (prefix == null) {
            gamePrefixes.remove(player);
        } else {
            gamePrefixes.put(player, prefix.length() <= MAX_TEAM ? prefix : prefix.substring(0, MAX_TEAM));
        }
    }

    /**
     * In a game, the health of the players around under their names (each tick: a hit shows at
     * once); in the lobby, nothing under the names.
     */
    void updateHealth() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = boards.get(viewer.getUniqueId());
            if (board == null || viewer.getScoreboard() != board) {
                continue;
            }
            Objective health = board.getObjective(HEALTH);
            if (health == null) {
                continue;
            }
            if (state.gameOf(viewer.getUniqueId()) == null) {
                if (health.getDisplaySlot() == DisplaySlot.BELOW_NAME) {
                    board.clearSlot(DisplaySlot.BELOW_NAME);
                }
                continue;
            }
            if (health.getDisplaySlot() != DisplaySlot.BELOW_NAME) {
                health.setDisplaySlot(DisplaySlot.BELOW_NAME);
            }
            for (Player other : viewer.getWorld().getPlayers()) {
                if (other == viewer || other.getLocation().distanceSquared(viewer.getLocation()) > HEALTH_RANGE * HEALTH_RANGE) {
                    continue;
                }
                // the client knows a nicked player by the nick
                String entry = names.shownName(other.getUniqueId(), other.getName());
                int points = healthPoints(other.getHealth(), absorptionOf(other));
                // a score is sent only when it changes
                health.getScore(entry).setScore(points);
            }
        }
    }

    /**
     * The names in the tab list only, for the animated colours, which move there and nowhere else:
     * a name is sent only when it changed, so only the animated ones are.
     */
    void updateListNames() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!gamePrefixes.containsKey(player.getUniqueId())) {
                listName(player, listNameOf(player));
            }
        }
    }

    /** The names above the heads and in the tab list (an invisible player's name hides), twice a second. */
    void updateNames() {
        Map<UUID, Tag> tags = tags();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = boards.get(player.getUniqueId());
            if (board != null && player.getScoreboard() == board) {
                syncTags(player.getUniqueId(), board, tags);
            }
        }
    }

    void updateAll() {
        Map<UUID, Tag> tags = tags();
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player, tags);
        }
    }

    void forget(UUID player) {
        boards.remove(player);
        placed.remove(player);
        shown.remove(player);
        gameLines.remove(player);
        replayLines.remove(player);
        gamePrefixes.remove(player);
        listNames.remove(player);
    }

    /** The lines of the replay the player watches, or null when they watch none. */
    void setReplayLines(UUID player, List<String> lines) {
        if (lines == null) {
            replayLines.remove(player);
        } else {
            replayLines.put(player, new ArrayList<>(lines));
        }
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
        List<String> replay = replayLines.get(player.getUniqueId());
        if (replay != null) {
            return givenLines(replay, settings.address());
        }
        // Few words, room between them: the player, their money and ranked, the server, the address.
        List<String> lines = new ArrayList<>();
        lines.add(blank(0));
        NetworkLevel levels = book.levels();
        PlayerStats stats = book.get(player.getUniqueId());
        int xp = stats == null ? 0 : stats.xp();
        int level = levels.level(xp);
        RankDef rank = NetworkRanks.rankOf(book, ranks, player.getUniqueId());
        lines.add(cut(ChatColor.WHITE + "Rank: " + (rank == Ranks.DEFAULT ? ChatColor.GRAY + "Default"
                : rank.prefix(names.plusColour(player.getUniqueId())).trim())));
        lines.add(ChatColor.WHITE + "Level: " + levels.tag(level));
        lines.add(ChatColor.WHITE + "Progress: " + ChatColor.AQUA
                + (level >= NetworkLevel.MAX_LEVEL ? "max" : percent(levels.progress(xp))));
        lines.add(blank(1));
        lines.add(ChatColor.WHITE + "Points: " + ChatColor.GOLD + String.format(java.util.Locale.US, "%,d", stats == null ? 0 : stats.points()));
        int best = bestElo(player.getUniqueId());
        if (best >= 0) {
            lines.add(cut(ChatColor.WHITE + "Ranked: " + book.ladder().format(best)));
        }
        lines.add(blank(2));
        lines.add(ChatColor.WHITE + "Ping: " + pingText(pingOf(player)));
        lines.add(ChatColor.WHITE + "Players: " + ChatColor.GREEN + String.format(java.util.Locale.US, "%,d", Bukkit.getOnlinePlayers().size()));
        GameMode mode = queues.queueOf(player);
        if (mode != null) {
            lines.add(blank(3));
            lines.add(cut(ChatColor.WHITE + "Queue: " + mode.name()));
            lines.add(ChatColor.WHITE + "Waiting: " + ChatColor.YELLOW
                    + clock(queues.waited(player, System.currentTimeMillis())));
        }
        lines.add(blank(4));
        lines.add(cut(settings.address()));
        return lines;
    }

    /** "45%" for 0.45; never 100% before the level is reached. */
    static String percent(float progress) {
        int value = (int) Math.floor(Math.max(0f, Math.min(1f, progress)) * 100f);
        return Math.min(99, value) + "%";
    }

    /** The player's highest ELO of the ranked modes, or -1 without a ranked mode. */
    private int bestElo(UUID player) {
        int best = -1;
        for (GameMode mode : settings.catalog().all()) {
            if (mode.ranked()) {
                best = Math.max(best, book.elo(player, mode.id()));
            }
        }
        return best;
    }

    /** In a game: the mode, the player's ping, and in a 1v1 the opponent and their ping. */
    private List<String> gameLines(Player player, String modeId) {
        List<String> given = gameLines.get(player.getUniqueId());
        if (given != null) {
            return givenLines(given, showsPing(given) ? new ArrayList<String>() : pingLines(player), settings.address());
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

    /** True if these lines of a game show a ping already. */
    static boolean showsPing(List<String> given) {
        for (String line : given) {
            if (line != null && ChatColor.stripColor(line).toLowerCase(java.util.Locale.ROOT).contains("ping")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The ping lines the lobby adds to a game's own: a blank line, the player's ping, and the
     * opponents': the one of a 1v1, each of up to three, or the average of more.
     */
    private List<String> pingLines(Player player) {
        List<Integer> theirs = new ArrayList<>();
        for (UUID opponent : state.opponentsOf(player.getUniqueId())) {
            Player other = Bukkit.getPlayer(opponent);
            if (other != null) {
                theirs.add(pingOf(other));
            }
        }
        return pingLines(pingOf(player), theirs, !state.opponentsOf(player.getUniqueId()).isEmpty());
    }

    /** The ping lines, for a player of this ping against opponents of these (online ones; none: left). */
    static List<String> pingLines(int mine, List<Integer> theirs, boolean opponents) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(ChatColor.WHITE + "Your ping: " + pingText(mine));
        if (!opponents) {
            return lines;
        }
        if (theirs.isEmpty()) {
            lines.add(ChatColor.WHITE + "Their ping: " + ChatColor.GRAY + "left");
        } else if (theirs.size() == 1) {
            lines.add(ChatColor.WHITE + "Their ping: " + pingText(theirs.get(0)));
        } else if (theirs.size() <= 3) {
            StringBuilder text = new StringBuilder(ChatColor.WHITE + "Their ping: ");
            for (int i = 0; i < theirs.size(); i++) {
                String one = pingText(theirs.get(i));
                // "§a42 ms": the unit once, at the end
                text.append(i > 0 ? ChatColor.GRAY + "/" : "").append(i < theirs.size() - 1 ? one.replace(" ms", "") : one);
            }
            lines.add(cut(text.toString()));
        } else {
            int sum = 0;
            int known = 0;
            for (int ping : theirs) {
                if (ping >= 0) {
                    sum += ping;
                    known++;
                }
            }
            lines.add(ChatColor.WHITE + "Their ping: " + (known == 0 ? pingText(-1) : ChatColor.GRAY + "~" + pingText(sum / known)));
        }
        return lines;
    }

    /**
     * The lines a game gave, as sidebar entries: an entry shows once, so a blank line or a line
     * already there gets an invisible colour code of its own.
     */
    static List<String> givenLines(List<String> given, String address) {
        return givenLines(given, new ArrayList<String>(), address);
    }

    /**
     * The lines a game gave, then these lines of the lobby (the ping), as sidebar entries: the
     * game's are cut to leave them room; an entry shows once, so a blank line or a line already
     * there gets an invisible colour code of its own.
     */
    static List<String> givenLines(List<String> given, List<String> added, String address) {
        List<String> all = new ArrayList<>(given.subList(0, Math.min(given.size(), MAX_GAME_LINES - added.size())));
        all.addAll(added);
        List<String> lines = new ArrayList<>();
        lines.add(blank(0));
        int unique = 4;
        for (String line : all) {
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
