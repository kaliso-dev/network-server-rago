package gg.rago.lobby;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * The sanctions of the staff: bans and mutes (for a time or for good), kicks and warnings, and the
 * history of each player, in plugins/RagoLobby/sanctions.yml. The file is read and written away
 * from the main thread. The bans are checked before a player logs in, the mutes in the chat, both
 * on other threads: what they read is kept in concurrent maps.
 */
final class Punishments implements Listener {

    static final String FILE_NAME = "sanctions.yml";

    enum Kind {
        BAN("ban", "Ban"), MUTE("mute", "Mute"), KICK("kick", "Kick"), WARN("warn", "Warning");

        private final String id;
        private final String label;

        Kind(String id, String label) {
            this.id = id;
            this.label = label;
        }

        String id() {
            return id;
        }

        String label() {
            return label;
        }

        static Kind of(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** One sanction. Only {@link #lifted} changes, on the main thread. */
    static final class Sanction {
        final Kind kind;
        final String reason;
        final String by;
        final long start;
        /** 0: for good. */
        final long end;
        volatile String liftedBy;

        Sanction(Kind kind, String reason, String by, long start, long end, String liftedBy) {
            this.kind = kind;
            this.reason = reason;
            this.by = by;
            this.start = start;
            this.end = end;
            this.liftedBy = liftedBy;
        }

        boolean permanent() {
            return end == 0;
        }

        boolean active(long now) {
            return liftedBy == null && (kind == Kind.BAN || kind == Kind.MUTE) && (end == 0 || now < end);
        }
    }

    private final RagoLobby lobby;
    private final StatsBook book;
    private final File file;
    private final Executor io;
    private final StatsService.Writer writer;
    private final Map<UUID, List<Sanction>> history = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Sanction> bans = new ConcurrentHashMap<>();
    private final Map<UUID, Sanction> mutes = new ConcurrentHashMap<>();
    private final CountDownLatch loaded = new CountDownLatch(1);

    Punishments(RagoLobby lobby, StatsBook book, Executor io, StatsService.Writer writer) {
        this.lobby = lobby;
        this.book = book;
        this.file = new File(lobby.getDataFolder(), FILE_NAME);
        this.io = io;
        this.writer = writer;
    }

    // --- The file -------------------------------------------------------------------------------

    void load() {
        io.execute(() -> {
            final Map<UUID, List<Sanction>> read = new HashMap<>();
            final Map<UUID, String> readNames = new HashMap<>();
            if (file.isFile()) {
                try {
                    fromYaml(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), read, readNames);
                } catch (IOException | InvalidConfigurationException | IllegalArgumentException e) {
                    lobby.getLogger().log(Level.WARNING, "Unreadable " + FILE_NAME + ", no sanction loaded", e);
                }
            }
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, List<Sanction>> player : read.entrySet()) {
                for (Sanction sanction : player.getValue()) {
                    if (sanction.active(now)) {
                        (sanction.kind == Kind.BAN ? bans : mutes).put(player.getKey(), sanction);
                    }
                }
            }
            loaded.countDown();
            if (lobby.isEnabled()) {
                Bukkit.getScheduler().runTask(lobby, () -> {
                    history.putAll(read);
                    names.putAll(readNames);
                    if (!read.isEmpty()) {
                        lobby.getLogger().info("Sanctions of " + read.size() + " players loaded");
                    }
                });
            }
        });
    }

    static void fromYaml(String text, Map<UUID, List<Sanction>> into, Map<UUID, String> names)
            throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        for (String key : yaml.getKeys(false)) {
            UUID id = UUID.fromString(key);
            names.put(id, yaml.getString(key + ".nom", "?"));
            List<Sanction> list = new ArrayList<>();
            for (Map<?, ?> entry : yaml.getMapList(key + ".sanctions")) {
                Kind kind = Kind.of(String.valueOf(entry.get("type")));
                if (kind == null) {
                    continue;
                }
                Object lifted = entry.get("levee-par");
                list.add(new Sanction(kind, String.valueOf(entry.get("raison")), String.valueOf(entry.get("par")),
                        number(entry.get("debut")), number(entry.get("fin")), lifted == null ? null : String.valueOf(lifted)));
            }
            into.put(id, list);
        }
    }

    private static long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    static String toYaml(Map<UUID, List<Sanction>> history, Map<UUID, String> names) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, List<Sanction>> player : history.entrySet()) {
            String key = player.getKey().toString();
            yaml.set(key + ".nom", names.containsKey(player.getKey()) ? names.get(player.getKey()) : "?");
            List<Map<String, Object>> list = new ArrayList<>();
            for (Sanction sanction : player.getValue()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", sanction.kind.id());
                entry.put("raison", sanction.reason);
                entry.put("par", sanction.by);
                entry.put("debut", sanction.start);
                entry.put("fin", sanction.end);
                if (sanction.liftedBy != null) {
                    entry.put("levee-par", sanction.liftedBy);
                }
                list.add(entry);
            }
            yaml.set(key + ".sanctions", list);
        }
        return yaml.saveToString();
    }

    private void save() {
        writer.write(file, toYaml(history, names));
    }

    // --- Durations ------------------------------------------------------------------------------

    /** "30m", "2h", "7d", "2w": in milliseconds; 0 for "perm"; -1 if this is no duration. */
    static long parseDuration(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.equals("perm") || lower.equals("permanent") || lower.equals("forever")) {
            return 0L;
        }
        if (!lower.matches("[0-9]{1,4}[smhdw]")) {
            return -1L;
        }
        long amount = Long.parseLong(lower.substring(0, lower.length() - 1));
        if (amount == 0) {
            return -1L;
        }
        switch (lower.charAt(lower.length() - 1)) {
            case 's':
                return TimeUnit.SECONDS.toMillis(amount);
            case 'm':
                return TimeUnit.MINUTES.toMillis(amount);
            case 'h':
                return TimeUnit.HOURS.toMillis(amount);
            case 'd':
                return TimeUnit.DAYS.toMillis(amount);
            default:
                return TimeUnit.DAYS.toMillis(amount * 7);
        }
    }

    /** "6d 23h", "2h 5m", "45m", "30s". */
    static String formatDuration(long millis) {
        long seconds = Math.max(1L, millis / 1000L);
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        if (days > 0) {
            return days + "d" + (hours > 0 ? " " + hours + "h" : "");
        }
        if (hours > 0) {
            return hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        }
        return minutes > 0 ? minutes + "m" : seconds + "s";
    }

    // --- Reading --------------------------------------------------------------------------------

    /** Any thread: the active ban of this player, or null. */
    Sanction ban(UUID player) {
        return active(bans, player);
    }

    /** Any thread: the active mute of this player, or null. */
    Sanction mute(UUID player) {
        return active(mutes, player);
    }

    private static Sanction active(Map<UUID, Sanction> map, UUID player) {
        Sanction sanction = map.get(player);
        if (sanction != null && !sanction.active(System.currentTimeMillis())) {
            map.remove(player, sanction);
            return null;
        }
        return sanction;
    }

    /** Any thread: what a muted player reads when they try to talk, or null if they may. */
    String mutedMessage(UUID player) {
        Sanction mute = mute(player);
        if (mute == null) {
            return null;
        }
        return ChatColor.RED + "You are muted" + (mute.permanent() ? "" : " for "
                + formatDuration(mute.end - System.currentTimeMillis())) + ". Reason: " + ChatColor.WHITE + mute.reason;
    }

    static String banScreen(Sanction ban) {
        return ChatColor.RED + "You are " + (ban.permanent() ? "permanently banned" : "temporarily banned for "
                + ChatColor.WHITE + formatDuration(ban.end - System.currentTimeMillis()) + ChatColor.RED) + " from this server!\n\n"
                + ChatColor.GRAY + "Reason: " + ChatColor.WHITE + ban.reason + "\n"
                + ChatColor.GRAY + "Banned by: " + ChatColor.WHITE + ban.by + "\n\n"
                + ChatColor.GRAY + "To appeal, join our Discord (/discord).";
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            // The very first logins after a start wait for the file, a few milliseconds at most.
            loaded.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Sanction ban = ban(event.getUniqueId());
        if (ban != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banScreen(ban));
        }
    }

    // --- Commands -------------------------------------------------------------------------------

    /** The player a command names: online, or seen once (their stats). Null, and the sender told. */
    private PlayerStats target(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        PlayerStats stats = online != null ? book.profile(online.getUniqueId(), online.getName()) : book.byName(name);
        if (stats == null) {
            sender.sendMessage(ChatColor.RED + "No player called " + name + " has ever played here.");
        }
        return stats;
    }

    /** Staff cannot sanction staff, only an admin or the console can. */
    private boolean protectedFrom(CommandSender sender, PlayerStats target) {
        if (!(sender instanceof Player) || sender.hasPermission("rago.admin")) {
            return false;
        }
        Player online = Bukkit.getPlayer(target.id());
        boolean staff = online != null ? online.hasPermission("rago.staff") : lobby.isStaffRank(target);
        if (staff) {
            sender.sendMessage(ChatColor.RED + "You cannot sanction a staff member.");
        }
        return staff;
    }

    private static String reason(String[] args, int from, String fallback) {
        String reason = String.join(" ", Arrays.copyOfRange(args, Math.min(from, args.length), args.length)).trim();
        return reason.isEmpty() ? fallback : reason;
    }

    private void add(PlayerStats target, Sanction sanction) {
        List<Sanction> list = history.get(target.id());
        if (list == null) {
            list = new ArrayList<>();
            history.put(target.id(), list);
        }
        list.add(sanction);
        names.put(target.id(), target.name());
        save();
    }

    private void tellStaff(String line) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("rago.staff")) {
                player.sendMessage(ChatColor.AQUA + "[STAFF] " + ChatColor.GRAY + line);
            }
        }
        lobby.getLogger().info(ChatColor.stripColor(line));
    }

    /** /ban and /mute: [duration] [reason]. Without a duration, for good. */
    void sanction(CommandSender sender, Kind kind, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + kind.id() + " <player> [30m|2h|7d|perm] [reason]");
            return;
        }
        PlayerStats target = target(sender, args[0]);
        if (target == null || protectedFrom(sender, target)) {
            return;
        }
        long duration = args.length > 1 ? parseDuration(args[1]) : 0L;
        int reasonFrom = args.length > 1 && duration >= 0 ? 2 : 1;
        if (duration < 0) {
            duration = 0L;
        }
        String reason = reason(args, reasonFrom, kind == Kind.BAN ? "Breaking the rules" : "Inappropriate chat");
        long now = System.currentTimeMillis();
        Sanction sanction = new Sanction(kind, reason, sender.getName(), now, duration == 0 ? 0 : now + duration, null);
        Map<UUID, Sanction> active = kind == Kind.BAN ? bans : mutes;
        Sanction old = active.put(target.id(), sanction);
        if (old != null) {
            old.liftedBy = sender.getName() + " (replaced)";
        }
        add(target, sanction);
        String length = duration == 0 ? "for good" : "for " + formatDuration(duration);
        tellStaff(sender.getName() + (kind == Kind.BAN ? " banned " : " muted ") + target.name() + " " + length + ": " + reason);
        Player online = Bukkit.getPlayer(target.id());
        if (online != null) {
            if (kind == Kind.BAN) {
                online.kickPlayer(banScreen(sanction));
            } else {
                online.sendMessage(ChatColor.RED + "You were muted " + length + ". Reason: " + ChatColor.WHITE + reason);
            }
        }
        if (!(sender instanceof Player) || !sender.hasPermission("rago.staff")) {
            sender.sendMessage(ChatColor.GREEN + target.name() + (kind == Kind.BAN ? " banned " : " muted ") + length + ".");
        }
    }

    /** /unban and /unmute. */
    void lift(CommandSender sender, Kind kind, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /un" + kind.id() + " <player>");
            return;
        }
        PlayerStats target = target(sender, args[0]);
        if (target == null) {
            return;
        }
        Sanction active = (kind == Kind.BAN ? bans : mutes).remove(target.id());
        if (active == null || !active.active(System.currentTimeMillis())) {
            sender.sendMessage(ChatColor.RED + target.name() + " is not " + (kind == Kind.BAN ? "banned." : "muted."));
            return;
        }
        active.liftedBy = sender.getName();
        save();
        tellStaff(sender.getName() + (kind == Kind.BAN ? " unbanned " : " unmuted ") + target.name() + ".");
        Player online = Bukkit.getPlayer(target.id());
        if (online != null && kind == Kind.MUTE) {
            online.sendMessage(ChatColor.GREEN + "You are no longer muted.");
        }
    }

    /** /kick and /warn: a reason, kept in the history. */
    void note(CommandSender sender, Kind kind, String[] args) {
        if (args.length == 0 || (kind == Kind.WARN && args.length < 2)) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + kind.id() + " <player> " + (kind == Kind.WARN ? "<reason>" : "[reason]"));
            return;
        }
        Player online = Bukkit.getPlayerExact(args[0]);
        if (online == null) {
            sender.sendMessage(ChatColor.RED + args[0] + " is not online.");
            return;
        }
        PlayerStats target = book.profile(online.getUniqueId(), online.getName());
        if (protectedFrom(sender, target)) {
            return;
        }
        String reason = reason(args, 1, "Breaking the rules");
        add(target, new Sanction(kind, reason, sender.getName(), System.currentTimeMillis(), 0, null));
        tellStaff(sender.getName() + (kind == Kind.KICK ? " kicked " : " warned ") + target.name() + ": " + reason);
        if (kind == Kind.KICK) {
            online.kickPlayer(ChatColor.RED + "You were kicked from the server.\n\n" + ChatColor.GRAY + "Reason: "
                    + ChatColor.WHITE + reason);
        } else {
            online.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "WARNING " + ChatColor.RED + "from the staff: "
                    + ChatColor.WHITE + reason);
            online.sendMessage(ChatColor.GRAY + "Keep breaking the rules and you will be muted or banned.");
        }
    }

    /** /history: every sanction of a player, the latest first. */
    void history(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /history <player>");
            return;
        }
        PlayerStats target = target(sender, args[0]);
        if (target == null) {
            return;
        }
        List<Sanction> list = history.containsKey(target.id()) ? new ArrayList<>(history.get(target.id()))
                : Collections.<Sanction>emptyList();
        if (list.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + target.name() + " has a clean record.");
            return;
        }
        Collections.reverse(list);
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        date.setTimeZone(TimeZone.getTimeZone(StatsPeriod.ZONE));
        long now = System.currentTimeMillis();
        sender.sendMessage(ChatColor.GOLD + "Sanctions of " + target.name() + " (" + list.size() + ")");
        for (Sanction sanction : list.subList(0, Math.min(15, list.size()))) {
            String state = sanction.kind == Kind.KICK || sanction.kind == Kind.WARN ? ""
                    : sanction.active(now) ? ChatColor.RED + " [active" + (sanction.permanent() ? "" : ", "
                            + formatDuration(sanction.end - now) + " left") + "]"
                    : sanction.liftedBy != null ? ChatColor.GREEN + " [lifted by " + sanction.liftedBy + "]"
                    : ChatColor.GRAY + " [over]";
            String length = sanction.kind == Kind.BAN || sanction.kind == Kind.MUTE
                    ? (sanction.permanent() ? " for good" : " " + formatDuration(sanction.end - sanction.start)) : "";
            sender.sendMessage(ChatColor.GRAY + date.format(new Date(sanction.start)) + " " + ChatColor.YELLOW
                    + sanction.kind.label() + length + ChatColor.GRAY + " by " + sanction.by + ": " + ChatColor.WHITE
                    + sanction.reason + state);
        }
    }
}
