package gg.rago.practice;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * The challenge of the day on the server: /challenge (its training, the top of the day, the
 * player's place and days in a row, a [PLAY] button), the XP of each first score of the day and of
 * the podium when the day turns (announced to everyone), a reminder at the arrival of a player who
 * has not played it today, and plugins/RagoPractice/defi.yml (written off the main thread). Main
 * thread only.
 */
final class Challenges implements CommandExecutor, Listener {

    /** How long a [PLAY] keeps its variant for the next training of the mode. */
    private static final long WANTED_MS = 60_000L;
    private static final String REASON = "daily challenge";
    private static final ChatColor[] PODIUM = {ChatColor.GOLD, ChatColor.WHITE, ChatColor.RED};

    private final RagoPractice plugin;
    private final File file;
    private final DailyChallenge.Settings settings;
    /** The challenge, once the modes are known (start); null before, or when there is none. */
    private DailyChallenge challenge;
    /** The variant a player asked for with [PLAY], for their next training of its mode. */
    private final Map<UUID, Wanted> wanted = new HashMap<>();
    /** The players told about the challenge today. */
    private final Set<UUID> reminded = new HashSet<>();
    private String remindedDay = "";
    private final Object writing = new Object();
    private long version;

    private static final class Wanted {
        final DailyChallenge.Board board;
        final long at;

        Wanted(DailyChallenge.Board board, long at) {
            this.board = board;
            this.at = at;
        }
    }

    Challenges(RagoPractice plugin, DailyChallenge.Settings settings, File file) {
        this.plugin = plugin;
        this.file = file;
        this.settings = settings;
    }

    boolean on() {
        return challenge != null && challenge.settings().on;
    }

    /**
     * The trainings of the challenge that this server plays (the others are left out), defi.yml
     * read again, the podium of a day that ended while the server was off, and the day checked
     * every 20 s.
     */
    void start(List<String> hosted) {
        if (!settings.on) {
            return;
        }
        List<String> boards = new java.util.ArrayList<>();
        for (String board : settings.boards) {
            DailyChallenge.Board known = DailyChallenge.CATALOG.get(board);
            if (known != null && hosted.contains(known.modeId)) {
                boards.add(board);
            } else {
                plugin.getLogger().warning("defi-du-jour.epreuves: " + board + " is not played on this server (no mode \""
                        + (known == null ? board : known.modeId) + "\"): left out of the challenge.");
            }
        }
        if (boards.isEmpty()) {
            plugin.getLogger().warning("defi-du-jour: none of its trainings is played on this server, there is no challenge.");
            return;
        }
        challenge = new DailyChallenge(new DailyChallenge.Settings(true, boards, settings.participationXp, settings.streakXp,
                settings.streakCap, settings.podiumXp), System.currentTimeMillis());
        if (file.isFile()) {
            try {
                DailyChallenge.Ended ended = challenge.load(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                if (ended != null && on()) {
                    reward(ended);
                    save();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "defi.yml could not be read: the challenge starts again from nothing", e);
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 400L, 400L);
        plugin.getLogger().info("Challenge of the day: " + challenge.board().label() + " (" + challenge.board().id() + ")");
    }

    /** The day turned: the podium gets its XP, everyone hears it and the new challenge. */
    void tick() {
        if (!on()) {
            return;
        }
        DailyChallenge.Ended ended = challenge.roll(System.currentTimeMillis());
        if (ended == null) {
            return;
        }
        reward(ended);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(ChatColor.GRAY + "Today's challenge: " + ChatColor.WHITE + challenge.board().label() + ChatColor.GRAY
                    + ". " + ChatColor.YELLOW + "/challenge");
        }
        save();
    }

    private void reward(DailyChallenge.Ended ended) {
        List<DailyChallenge.Score> top = ended.top;
        if (top.isEmpty()) {
            return;
        }
        StringBuilder line = new StringBuilder(ChatColor.GOLD + "" + ChatColor.BOLD + "Daily challenge " + ChatColor.GRAY + "of "
                + ended.day + " (" + ended.board.label() + "):");
        int places = Math.min(top.size(), Math.max(3, challenge.settings().podiumXp.size()));
        for (int i = 0; i < places; i++) {
            DailyChallenge.Score score = top.get(i);
            int xp = challenge.settings().podium(i + 1);
            line.append(' ').append(i < PODIUM.length ? PODIUM[i] : ChatColor.GRAY).append('#').append(i + 1).append(' ')
                    .append(score.name).append(' ').append(ChatColor.WHITE).append(ended.board.format(score.value));
            if (xp > 0) {
                line.append(ChatColor.GRAY).append(" (+").append(xp).append(" XP)");
                plugin.lobby().giveXp(ended.board.modeId, score.player, xp, REASON + " #" + (i + 1));
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(line.toString());
        }
        plugin.getLogger().info(ChatColor.stripColor(line.toString()));
    }

    // --- The trainings --------------------------------------------------------------------------

    /** True if this board (mode.variant) is today's challenge. */
    boolean isToday(String board) {
        return on() && challenge.board().id().equals(board);
    }

    /** A score of a training: the challenge counts it when it is today's, with its XP and a word to the player. */
    void scored(UUID id, String name, String board, long value) {
        if (!on()) {
            return;
        }
        tick();
        DailyChallenge.Result result = challenge.submit(board, id, name, value);
        if (result == null) {
            return;
        }
        if (result.xp > 0) {
            plugin.lobby().giveXp(challenge.board().modeId, id, result.xp, REASON);
        }
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            if (result.first) {
                player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Daily challenge! " + ChatColor.GRAY + "You are "
                        + ChatColor.WHITE + "#" + result.place + ChatColor.GRAY + " of the day" + (result.days > 1 ? " ("
                        + ChatColor.GOLD + result.days + " days in a row" + ChatColor.GRAY + ")" : "") + ". The top 3 at midnight win "
                        + podiumText() + ". " + ChatColor.YELLOW + "/challenge");
            } else if (result.best) {
                player.sendMessage(ChatColor.GOLD + "Daily challenge: " + ChatColor.GREEN + "new best of the day, " + ChatColor.WHITE
                        + "#" + result.place + ChatColor.GREEN + "!");
                if (result.place == 1) {
                    player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1.5f);
                }
            }
        }
        save();
    }

    /** "750, 500 and 300 XP". */
    private String podiumText() {
        List<Integer> xp = challenge.settings().podiumXp;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < xp.size(); i++) {
            text.append(i == 0 ? "" : i == xp.size() - 1 ? " and " : ", ").append(xp.get(i));
        }
        return text.append(" XP").toString();
    }

    /**
     * The variant the player asked for with [PLAY] for this mode, used once; null when they asked
     * nothing (or for another mode, or too long ago).
     */
    String takeVariant(UUID player, String modeId) {
        Wanted asked = wanted.remove(player);
        if (!on()) {
            return null;
        }
        if (asked == null || !asked.board.modeId.equals(modeId) || System.currentTimeMillis() - asked.at > WANTED_MS) {
            return null;
        }
        return asked.board.variant.id;
    }

    // --- The players ----------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!on()) {
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> remind(id), 100L);
    }

    /** At the arrival, once a day: the challenge waits for them. */
    private void remind(UUID id) {
        Player player = Bukkit.getPlayer(id);
        tick();
        if (!challenge.day().equals(remindedDay)) {
            reminded.clear();
            remindedDay = challenge.day();
        }
        if (player == null || plugin.lobby().isInGame(player) || challenge.played(id) || !reminded.add(id)) {
            return;
        }
        int days = challenge.days(id);
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Daily challenge: " + ChatColor.WHITE + challenge.board().label()
                + ChatColor.GRAY + ". The top 3 win " + podiumText() + (days > 0 ? ", and you are " + ChatColor.GOLD + days
                + (days == 1 ? " day" : " days") + " in a row" + ChatColor.GRAY : "") + ".");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + playButton());
    }

    private String playButton() {
        DailyChallenge.Board board = challenge.board();
        return "[\"\",{\"text\":\"[PLAY]\",\"color\":\"green\",\"bold\":true,"
                + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/challenge play\"},"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"" + escape(board.label()) + "\"}},"
                + "{\"text\":\" or /challenge\",\"color\":\"gray\"}]";
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c >= ' ') {
                out.append(c);
            }
        }
        return out.toString();
    }

    // --- /challenge -----------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!on()) {
            sender.sendMessage(ChatColor.GRAY + "There is no challenge of the day on this server.");
            return true;
        }
        tick();
        if (args.length > 0 && (args[0].equalsIgnoreCase("play") || args[0].equalsIgnoreCase("jouer"))) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only a player plays the challenge.");
                return true;
            }
            Player player = (Player) sender;
            if (plugin.lobby().isInGame(player)) {
                player.sendMessage(ChatColor.RED + "Leave your game first (/spawn).");
                return true;
            }
            wanted.put(player.getUniqueId(), new Wanted(challenge.board(), System.currentTimeMillis()));
            player.performCommand("queue " + challenge.board().modeId);
            return true;
        }
        DailyChallenge.Board board = challenge.board();
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Daily challenge " + ChatColor.GRAY + "- " + ChatColor.WHITE
                + board.label());
        sender.sendMessage(ChatColor.GRAY + (board.variant.timed ? "The best time" : "The best score") + " of the day wins. Ends in "
                + left(DailyChallenge.millisLeft(System.currentTimeMillis())) + ".");
        List<DailyChallenge.Score> top = challenge.ranked();
        for (int i = 0; i < 5 && i < top.size(); i++) {
            DailyChallenge.Score score = top.get(i);
            sender.sendMessage((i < PODIUM.length ? PODIUM[i] : ChatColor.GRAY) + "#" + (i + 1) + " " + score.name + " "
                    + ChatColor.WHITE + board.format(score.value));
        }
        if (top.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No score yet today: be the first!");
        }
        if (sender instanceof Player) {
            UUID id = ((Player) sender).getUniqueId();
            Long best = challenge.best(id);
            int days = challenge.days(id);
            sender.sendMessage(ChatColor.WHITE + "You: " + (best == null ? ChatColor.GRAY + "no score today"
                    : ChatColor.GREEN + board.format(best) + ChatColor.GRAY + " (#" + challenge.place(id) + ")")
                    + (days > 0 ? ChatColor.DARK_GRAY + " | " + ChatColor.GOLD + days + (days == 1 ? " day" : " days") + " in a row" : ""));
        }
        DailyChallenge.Settings settings = challenge.settings();
        sender.sendMessage(ChatColor.GRAY + "Top 3 at midnight: " + ChatColor.WHITE + podiumText() + ChatColor.GRAY
                + ". Everyone: " + ChatColor.WHITE + settings.participationXp + " XP" + ChatColor.GRAY + " a day, "
                + (settings.streakXp > 0 ? "+" + settings.streakXp + " for each day in a row (" + settings.streakCap + " at most)" : ""));
        if (sender instanceof Player) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + sender.getName() + " " + playButton());
        }
        return true;
    }

    /** "5h 12m", "12m". */
    static String left(long millis) {
        long minutes = Math.max(1, (millis + 59_999L) / 60_000L);
        return (minutes >= 60 ? minutes / 60 + "h " : "") + minutes % 60 + "m";
    }

    // --- defi.yml -------------------------------------------------------------------------------

    /** Writes the challenge, off the main thread (the last text written last). */
    void save() {
        final String text = challenge.toYaml();
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
            plugin.getLogger().log(Level.WARNING, "defi.yml could not be written", e);
        }
    }
}
