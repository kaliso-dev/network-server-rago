package gg.rago.practice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The events PREMIUM players host, as on Minemen: /host opens the menu of the events (Sumo,
 * Brackets, Last Man Standing), and the whole server is told every 10 seconds, with a
 * [Click to join], until it starts. /join (or /event join) puts a player in it without moving
 * them: they keep playing their games, and /event leave takes them out. Once the wait is over
 * (attente-secondes), it starts as soon as joueurs-min players are in: those still in a game
 * leave it (a forfeit while they play), and all go to it. /spawn leaves it, /event cancel calls it off (its
 * host or the staff). One event at a time, a pause between two, and a cooldown for each host. The
 * games are {@link EventGame}s, in modes of the lobby hidden from the selector. Main thread only.
 */
final class Events implements CommandExecutor, Listener {

    /** Hosting an event: the PREMIUM rank of the lobby gives it. */
    static final String PERMISSION = "rago.perk.host";
    /** The staff hosts without a cooldown, and calls any event off. */
    static final String STAFF = "rago.staff";
    /** The menu: where each event is. */
    static final int[] MENU_SLOTS = {11, 13, 15};

    /** The settings (section evenements of config.yml). */
    static final class Settings {
        final int waitSeconds;
        final int minPlayers;
        final int maxPlayers;
        final int cooldownMinutes;
        final int pauseSeconds;
        final int winXp;
        final int playXp;
        /** The events the server hosts on its own. */
        final Auto auto;

        Settings(int waitSeconds, int minPlayers, int maxPlayers, int cooldownMinutes, int pauseSeconds, int winXp, int playXp,
                Auto auto) {
            this.waitSeconds = waitSeconds;
            this.minPlayers = minPlayers;
            this.maxPlayers = maxPlayers;
            this.cooldownMinutes = cooldownMinutes;
            this.pauseSeconds = pauseSeconds;
            this.winXp = winXp;
            this.playXp = playXp;
            this.auto = auto;
        }

        /** The ranges of the settings: key, default, lowest, highest. */
        static final String[][] RANGES = {
            {"attente-secondes", "60", "15", "300"},
            {"joueurs-min", "2", "2", "16"},
            {"joueurs-max", "40", "2", "100"},
            {"cooldown-minutes", "30", "0", "1440"},
            {"pause-secondes", "120", "0", "3600"},
            {"xp-gagnant", "250", "0", "10000"},
            {"xp-participation", "25", "0", "1000"},
        };

        static Settings of(ConfigurationSection section) {
            int[] v = new int[RANGES.length];
            for (int i = 0; i < RANGES.length; i++) {
                v[i] = section == null ? Integer.parseInt(RANGES[i][1]) : section.getInt(RANGES[i][0], Integer.parseInt(RANGES[i][1]));
            }
            return new Settings(v[0], v[1], v[2], v[3], v[4], v[5], v[6],
                    Auto.of(section == null ? null : section.getConfigurationSection("automatiques")));
        }

        /** What is wrong with the section, or null. */
        static String check(ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            for (String[] range : RANGES) {
                int value = section.getInt(range[0], Integer.parseInt(range[1]));
                if (value < Integer.parseInt(range[2]) || value > Integer.parseInt(range[3])) {
                    return "evenements." + range[0] + ": from " + range[2] + " to " + range[3];
                }
            }
            if (section.getInt("joueurs-min", 2) > section.getInt("joueurs-max", 40)) {
                return "evenements.joueurs-min: not more than joueurs-max";
            }
            return Auto.check(section.getConfigurationSection("automatiques"));
        }
    }

    /**
     * The events the server hosts on its own (section evenements.automatiques): when the lobby has
     * joueurs-lobby players out of a game and no event ran for minutes minutes, the next of types,
     * in turn. Off without the section.
     */
    static final class Auto {
        /** Without the section: on, every 20 minutes at most, from 6 players in the lobby, every event in turn. */
        static final Auto DEFAULT = new Auto(true, 20, 6, java.util.Arrays.asList(EventGame.Kind.values()));

        final boolean on;
        final int minutes;
        final int lobbyPlayers;
        final List<EventGame.Kind> kinds;

        Auto(boolean on, int minutes, int lobbyPlayers, List<EventGame.Kind> kinds) {
            this.on = on;
            this.minutes = minutes;
            this.lobbyPlayers = lobbyPlayers;
            this.kinds = java.util.Collections.unmodifiableList(new ArrayList<>(kinds));
        }

        static Auto of(ConfigurationSection section) {
            if (section == null) {
                return DEFAULT;
            }
            List<EventGame.Kind> kinds = new ArrayList<>();
            for (String word : section.getStringList("types")) {
                EventGame.Kind kind = EventGame.Kind.of(word);
                if (kind != null && !kinds.contains(kind)) {
                    kinds.add(kind);
                }
            }
            if (kinds.isEmpty()) {
                kinds.addAll(java.util.Arrays.asList(EventGame.Kind.values()));
            }
            return new Auto(section.getBoolean("actif", true), section.getInt("minutes", 20), section.getInt("joueurs-lobby", 6),
                    kinds);
        }

        /** What is wrong with the section evenements.automatiques, or null. */
        static String check(ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            int minutes = section.getInt("minutes", 20);
            if (minutes < 5 || minutes > 1440) {
                return "evenements.automatiques.minutes: from 5 to 1440";
            }
            int players = section.getInt("joueurs-lobby", 6);
            if (players < 2 || players > 200) {
                return "evenements.automatiques.joueurs-lobby: from 2 to 200";
            }
            for (String word : section.getStringList("types")) {
                if (EventGame.Kind.of(word) == null) {
                    return "evenements.automatiques.types: sumo, brackets or lms, not \"" + word + "\"";
                }
            }
            return null;
        }

        /**
         * Whether the server hosts an event now: on, nothing running or announced, enough players
         * in the lobby, and no event for {@link #minutes} (since the last one ended or was
         * announced by the server).
         */
        boolean due(long now, long quietSince, boolean busy, int inLobby) {
            return on && !busy && inLobby >= lobbyPlayers && now - quietSince >= minutes * 60_000L;
        }
    }

    /** The name the server hosts its own events under. */
    static final String SERVER_HOST = "Rago";
    /** The public message of an event not started yet comes back every this many seconds (the plan's 10). */
    static final int ANNOUNCE_EVERY = 10;
    /** After the start, the seconds before an event that could not be made (no free arena) is called off. */
    static final int LAUNCH_SECONDS = 10;

    /**
     * The event announced, then played: its kind, its host (null for the server's own), and the
     * players who joined it, who stay where they are until it starts.
     */
    static final class Hosting {
        final EventGame.Kind kind;
        final UUID host;
        final String hostName;
        final long since;
        /** Its players, in the order they joined (its host first). */
        final Set<UUID> registered = new LinkedHashSet<>();
        /** The seconds left of its wait (attente-secondes), then 0. */
        int waitLeft;
        /** The seconds since it was announced. */
        int seconds;
        /** True once its players were sent to it; how many were. */
        boolean launched;
        int going;

        Hosting(EventGame.Kind kind, UUID host, String hostName, long since, int waitLeft) {
            this.kind = kind;
            this.host = host;
            this.hostName = hostName;
            this.since = since;
            this.waitLeft = waitLeft;
        }
    }

    /** The menu of /host. */
    static final class Menu implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final RagoPractice plugin;
    private final Settings settings;
    private final Map<UUID, Long> lastHosted = new HashMap<>();
    private long lastEnded;
    /** The event announced or played (null: none), and its game once it started. */
    private Hosting hosting;
    private EventGame current;
    /** When the server last announced an event of its own, and the next kind it hosts. */
    private long lastAuto;
    private int nextKind;

    Events(RagoPractice plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.lastAuto = System.currentTimeMillis();
    }

    /** The event announced, every second; the server's own events, a look every 20 seconds. */
    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        if (settings.auto.on) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::autoTick, 400L, 400L);
        }
    }

    Settings settings() {
        return settings;
    }

    Hosting hosting() {
        return hosting;
    }

    /** The event that runs (waiting for its players or played), or null. */
    EventGame current() {
        return current != null && current.phase != PracticeGame.Phase.ENDED ? current : null;
    }

    // --- The games ------------------------------------------------------------------------------

    /**
     * A game of an event mode was made: the event that just started (its players sent to it), or
     * one nobody hosted (refused at its start).
     */
    EventGame created(EventGame game) {
        if (hosting != null && hosting.launched && hosting.kind == game.kind() && current() == null) {
            current = game;
        }
        return game;
    }

    /** True if this game is the event being hosted (a game of an event mode nobody hosted is not). */
    boolean accepts(EventGame game) {
        return current == game;
    }

    /** How many players were sent to this event when it started (0 for another game). */
    int going(EventGame game) {
        return current == game && hosting != null ? hosting.going : 0;
    }

    /**
     * The public message of an event not started yet, to every player online, in a game too
     * (they may join it and keep playing): who hosts what, what it is, where it stands, and
     * [Click to join]. Its own players read that they are in instead of the button.
     */
    void announce(Hosting h) {
        String line = ChatColor.GOLD + "" + ChatColor.BOLD + "[Event] " + ChatColor.YELLOW + h.hostName + ChatColor.GRAY
                + " is hosting " + ChatColor.GOLD + h.kind.label + ChatColor.GRAY + "! " + String.join(" ", h.kind.description);
        String status = status(h);
        String json = button(h.kind, status);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(line);
            if (h.registered.contains(player.getUniqueId())) {
                player.sendMessage(ChatColor.GREEN + "You are in" + ChatColor.GRAY + " (" + status
                        + "). Keep playing: you go to it when it starts. /event leave to leave.");
            } else {
                // clickable through the vanilla /tellraw, as the lobby's duel buttons: names are letters, digits and _
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + json);
            }
            if (!plugin.lobby().isInGame(player)) {
                player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 1.4f);
            }
        }
    }

    /** Where this event stands: its players, and when it starts. */
    private String status(Hosting h) {
        int busy = 0;
        for (UUID id : h.registered) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && plugin.lobby().isInGame(player)) {
                busy++;
            }
        }
        return status(h.registered.size(), settings.maxPlayers, busy, h.waitLeft, settings.minPlayers);
    }

    /**
     * "3/40 players, starts in 45s": the players in (and how many of them play a game), and when
     * it starts: after the wait, as soon as the fewest players are in (those in a game leave it).
     */
    static String status(int in, int max, int busy, int waitLeft, int min) {
        String players = in + "/" + max + " players" + (busy > 0 ? " (" + busy + " in a game)" : "");
        if (waitLeft > 0) {
            return players + ", starts in " + waitLeft + "s";
        }
        if (in >= min) {
            return players + ", starting";
        }
        int more = min - in;
        return players + ", needs " + more + " more " + (more == 1 ? "player" : "players") + " to start";
    }

    /** The JSON of the [Click to join] line, with where the event stands. */
    static String button(EventGame.Kind kind, String status) {
        return "[\"\",{\"text\":\"[Click to join]\",\"color\":\"green\",\"bold\":true,"
                + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/join\"},"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Join the " + kind.label
                + " event: keep playing until it starts\"}},"
                + "{\"text\":\" or /join: " + status + "\",\"color\":\"gray\"}]";
    }

    /** The event starts: its host's cooldown runs from now. */
    void started(EventGame game) {
        Hosting h = hosting;
        if (h != null && h.host != null) {
            lastHosted.put(h.host, System.currentTimeMillis());
        }
    }

    /** The event is called off (not enough players, its host, the staff): the server is told. */
    void cancelled(EventGame game, String why) {
        tellLobby(ChatColor.GOLD + "[Event] " + ChatColor.GRAY + "The " + game.kind().label + " event of " + game.hostName()
                + " is called off: " + why + ".");
        if (current == game) {
            current = null;
            hosting = null;
        }
    }

    /** The event is over: the winner is told to the whole server and gets their XP. */
    void ended(EventGame game, UUID winner) {
        if (winner != null) {
            Player player = Bukkit.getPlayer(winner);
            String name = player != null ? player.getName() : "?";
            for (Player each : Bukkit.getOnlinePlayers()) {
                each.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[Event] " + ChatColor.YELLOW + "★ " + name + ChatColor.GRAY
                        + " won the " + ChatColor.GOLD + game.kind().label + ChatColor.GRAY + " event"
                        + (name.equals(game.hostName()) ? " they hosted!" : " of " + game.hostName() + "!"));
            }
            plugin.lobby().giveXp(game.kind().modeId(), winner, settings.winXp, "event won");
        }
        if (current == game) {
            current = null;
            hosting = null;
            lastEnded = System.currentTimeMillis();
        }
    }

    /** A player out of the event: a little XP for taking part. */
    void participated(UUID player, EventGame.Kind kind) {
        plugin.lobby().giveXp(kind.modeId(), player, settings.playXp, "event");
    }

    private void tellLobby(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.lobby().isInGame(player) || (current != null && current.players().contains(player.getUniqueId()))) {
                player.sendMessage(message);
            }
        }
    }

    // --- The wait -------------------------------------------------------------------------------

    /**
     * Every second, while an event is announced and not started: its wait runs, its public message
     * comes back every {@link #ANNOUNCE_EVERY} seconds, and once the wait is over it starts as soon
     * as joueurs-min players are in, in a game or not. The server's own event without them at the
     * end of its wait is dropped (without a word if nobody was in); a player's event keeps waiting,
     * unless its host left with nobody in.
     */
    void tick() {
        Hosting h = hosting;
        if (h == null || h.launched) {
            return;
        }
        h.registered.removeIf(id -> Bukkit.getPlayer(id) == null);
        if (h.waitLeft > 0) {
            h.waitLeft--;
        }
        h.seconds++;
        List<Player> ready = online(h);
        if (launches(h.waitLeft, ready.size(), settings.minPlayers)) {
            launch(h, ready);
            return;
        }
        if (h.host == null && h.waitLeft == 0) {
            if (h.registered.isEmpty()) {
                hosting = null;
            } else {
                callOff(h, "not enough players (" + ready.size() + "/" + settings.minPlayers + ")");
            }
            return;
        }
        if (h.host != null && h.registered.isEmpty() && Bukkit.getPlayer(h.host) == null) {
            callOff(h, "its host left");
            return;
        }
        if (h.seconds % ANNOUNCE_EVERY == 0) {
            announce(h);
        }
    }

    /** True when an event starts: its wait is over and enough players are in (in a game or not). */
    static boolean launches(int waitLeft, int in, int min) {
        return waitLeft <= 0 && in >= min;
    }

    /** The players of this event online, in the order they joined. */
    private List<Player> online(Hosting h) {
        List<Player> online = new ArrayList<>();
        for (UUID id : h.registered) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    /**
     * The event starts: its players still in a game leave it, as with /spawn (a forfeit while they
     * play: plan 4.2, as chosen on 21/11), and all go to it through the queue of its mode (the first one
     * makes the game, the others join it).
     */
    private void launch(Hosting h, List<Player> ready) {
        h.launched = true;
        h.going = ready.size();
        for (Player player : ready) {
            if (plugin.lobby().isInGame(player)) {
                player.sendMessage(ChatColor.GOLD + "[Event] " + ChatColor.GRAY + "The " + h.kind.label
                        + " event starts: you leave your game for it.");
                plugin.lobby().leaveGame(player);
            }
        }
        for (Player player : ready) {
            if (!plugin.lobby().queue(player, h.kind.modeId())) {
                h.going--;
                player.sendMessage(ChatColor.RED + "You could not go to the " + h.kind.label + " event.");
            }
        }
        // the queue makes it within a second; if nothing came of it, the players may host it again
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (hosting == h && current() == null) {
                callOff(h, "no free arena, try again in a moment");
            }
        }, LAUNCH_SECONDS * 20L);
    }

    /** An event called off before it started: its players and the lobby are told why. */
    private void callOff(Hosting h, String why) {
        if (hosting == h) {
            hosting = null;
        }
        String message = ChatColor.GOLD + "[Event] " + ChatColor.GRAY + "The " + h.kind.label + " event of " + h.hostName
                + " is called off: " + why + ".";
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.lobby().isInGame(player) || h.registered.contains(player.getUniqueId())) {
                player.sendMessage(message);
            }
        }
    }

    // --- The server's own events -----------------------------------------------------------------

    /**
     * Every 20 seconds: when the lobby has enough players and no event ran for a while, the server
     * announces the next event of its list; players join it as a hosted one, and it starts the same
     * way at the end of its wait. Without enough players then, it is dropped.
     */
    void autoTick() {
        long now = System.currentTimeMillis();
        int inLobby = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.lobby().isInGame(player)) {
                inLobby++;
            }
        }
        boolean busy = hosting != null || current() != null;
        if (!settings.auto.due(now, Math.max(lastAuto, lastEnded), busy, inLobby)) {
            return;
        }
        EventGame.Kind kind = settings.auto.kinds.get(nextKind % settings.auto.kinds.size());
        nextKind++;
        lastAuto = now;
        if (!plugin.hosts(kind.modeId())) {
            return;
        }
        hosting = new Hosting(kind, null, SERVER_HOST, now, settings.waitSeconds);
        announce(hosting);
    }

    // --- Hosting --------------------------------------------------------------------------------

    /** Why this player cannot host now (null: they can), at this time. */
    String whyNot(Player player, long now) {
        if (!player.hasPermission(PERMISSION)) {
            return "Hosting events is a [PREMIUM+] perk: get it on the store (/store).";
        }
        if (plugin.lobby().isInGame(player)) {
            return "Leave your game first (/spawn).";
        }
        if (current() != null || (hosting != null && hosting.launched)) {
            return "An event is already running: wait for its end.";
        }
        // the server's own event, with nobody in yet, gives way to a player's
        if (hosting != null && (hosting.host != null || !hosting.registered.isEmpty())) {
            return "An event is already announced: /join it.";
        }
        if (player.hasPermission(STAFF)) {
            return null;
        }
        long pauseLeft = lastEnded + settings.pauseSeconds * 1000L - now;
        if (pauseLeft > 0) {
            return "Another event can start in " + PracticeGame.clock((int) ((pauseLeft + 999) / 1000)) + ".";
        }
        Long last = lastHosted.get(player.getUniqueId());
        long cooldownLeft = cooldownLeft(last, settings.cooldownMinutes, now);
        if (cooldownLeft > 0) {
            return "You can host another event in " + PracticeGame.clock((int) ((cooldownLeft + 999) / 1000)) + ".";
        }
        return null;
    }

    /** The milliseconds left of a host's cooldown (0 when over or never hosted). */
    static long cooldownLeft(Long lastHosted, int cooldownMinutes, long now) {
        return lastHosted == null ? 0 : Math.max(0, lastHosted + cooldownMinutes * 60_000L - now);
    }

    /**
     * /host with a kind: the event is announced to the whole server, its host in first. Nobody
     * moves until it starts.
     */
    void host(Player player, EventGame.Kind kind) {
        long now = System.currentTimeMillis();
        String why = whyNot(player, now);
        if (why != null) {
            player.sendMessage(ChatColor.RED + why);
            return;
        }
        if (!plugin.hosts(kind.modeId())) {
            player.sendMessage(ChatColor.RED + "The " + kind.label + " event is not open on this server.");
            return;
        }
        Hosting h = new Hosting(kind, player.getUniqueId(), player.getName(), now, settings.waitSeconds);
        h.registered.add(player.getUniqueId());
        hosting = h;
        announce(h);
    }

    /**
     * /join: into the event announced, without moving: the player keeps playing (a game, a
     * training) and goes to it when it starts.
     */
    void join(Player player) {
        Hosting h = hosting;
        if (h == null) {
            player.sendMessage(ChatColor.GRAY + "No event to join right now. PREMIUM players host them with /host.");
            return;
        }
        UUID id = player.getUniqueId();
        if (h.launched) {
            EventGame game = current();
            player.sendMessage(ChatColor.GRAY + (game != null && plugin.games().gameOf(id) == game ? "You are in the event already."
                    : "The " + h.kind.label + " event has started: wait for the next one."));
            return;
        }
        if (h.registered.contains(id)) {
            player.sendMessage(ChatColor.GRAY + "You are in the " + h.kind.label + " event already (" + status(h)
                    + "). /event leave to leave.");
            return;
        }
        if (h.registered.size() >= settings.maxPlayers) {
            player.sendMessage(ChatColor.RED + "The " + h.kind.label + " event is full.");
            return;
        }
        h.registered.add(id);
        player.sendMessage(ChatColor.GREEN + "You joined the " + h.kind.label + " event of " + h.hostName + ChatColor.GRAY + " ("
                + status(h) + "). Keep playing: you go to it when it starts. /event leave to leave.");
    }

    /** /event leave: out of the event announced, or out of the event played. */
    void leave(Player player) {
        Hosting h = hosting;
        UUID id = player.getUniqueId();
        if (h != null && !h.launched && h.registered.remove(id)) {
            player.sendMessage(ChatColor.GRAY + "You left the " + h.kind.label + " event.");
            if (id.equals(h.host) && h.registered.isEmpty()) {
                callOff(h, "its host left");
            }
            return;
        }
        EventGame game = current();
        if (game != null && plugin.games().gameOf(id) == game) {
            game.leave(player, true);
            return;
        }
        player.sendMessage(ChatColor.GRAY + "You are not in an event.");
    }

    // --- Commands -------------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.equals("event") && args.length > 0 && (args[0].equalsIgnoreCase("cancel") || args[0].equalsIgnoreCase("stop"))) {
            cancel(sender);
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("In the game only.");
            return true;
        }
        Player player = (Player) sender;
        if (name.equals("host")) {
            if (args.length > 0) {
                EventGame.Kind kind = EventGame.Kind.of(String.join(" ", args));
                if (kind == null) {
                    player.sendMessage(ChatColor.RED + "Events: sumo, brackets, lms.");
                    return true;
                }
                host(player, kind);
            } else {
                openMenu(player);
            }
            return true;
        }
        if (name.equals("join") || (args.length > 0 && args[0].equalsIgnoreCase("join"))) {
            join(player);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("leave")) {
            leave(player);
            return true;
        }
        info(player);
        return true;
    }

    /** /event cancel: its host or the staff calls the event off, announced or played. */
    private void cancel(CommandSender sender) {
        Hosting h = hosting;
        EventGame game = current();
        if (h == null && game == null) {
            sender.sendMessage(ChatColor.GRAY + "No event is running.");
            return;
        }
        boolean own = sender instanceof Player && h != null && h.host != null && h.host.equals(((Player) sender).getUniqueId());
        if (!own && !sender.hasPermission(STAFF)) {
            sender.sendMessage(ChatColor.RED + "Only its host or the staff can call the event off.");
            return;
        }
        if (game != null) {
            game.cancel("called off by " + sender.getName());
        } else {
            callOff(h, "called off by " + sender.getName());
        }
    }

    private void info(Player player) {
        Hosting h = hosting;
        EventGame game = current();
        if (game != null) {
            player.sendMessage(ChatColor.GOLD + "[Event] " + ChatColor.YELLOW + game.kind().label + ChatColor.GRAY + " hosted by "
                    + game.hostName() + ", " + game.aliveCount() + " players"
                    + (game.stage() == EventGame.Stage.WAITING ? ", starting." : ", playing."));
            return;
        }
        if (h != null && !h.launched) {
            player.sendMessage(ChatColor.GOLD + "[Event] " + ChatColor.YELLOW + h.kind.label + ChatColor.GRAY + " hosted by "
                    + h.hostName + ": " + status(h) + ". " + (h.registered.contains(player.getUniqueId())
                            ? "You are in: /event leave to leave." : "/join to join."));
            return;
        }
        player.sendMessage(ChatColor.GOLD + "[Event] " + ChatColor.GRAY + "No event right now. "
                + (player.hasPermission(PERMISSION) ? "Host one with /host." : "PREMIUM players host them with /host."));
    }

    // --- The menu -------------------------------------------------------------------------------

    void openMenu(Player player) {
        Menu holder = new Menu();
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatColor.DARK_GRAY + "Host an event");
        holder.inventory = inventory;
        long now = System.currentTimeMillis();
        String why = whyNot(player, now);
        EventGame.Kind[] kinds = EventGame.Kind.values();
        for (int i = 0; i < kinds.length && i < MENU_SLOTS.length; i++) {
            inventory.setItem(MENU_SLOTS[i], item(kinds[i], why));
        }
        ItemStack glass = PracticeGame.named(Material.STAINED_GLASS_PANE, 1, 1, " ");
        for (int slot = 0; slot < 27; slot++) {
            if (inventory.getItem(slot) == null && (slot < 9 || slot >= 18)) {
                inventory.setItem(slot, glass);
            }
        }
        player.openInventory(inventory);
    }

    private ItemStack item(EventGame.Kind kind, String why) {
        List<String> lore = new ArrayList<>();
        for (String line : kind.description) {
            lore.add(ChatColor.GRAY + line);
        }
        lore.add("");
        lore.add(ChatColor.GRAY + "Players: " + ChatColor.WHITE + settings.minPlayers + " to " + settings.maxPlayers);
        lore.add(ChatColor.GRAY + "On a sumo map, the whole server told.");
        lore.add("");
        lore.add(why == null ? ChatColor.RED + "Click to host!" : ChatColor.GRAY + why);
        return PracticeGame.named(kind.icon, 1, 0, ChatColor.GOLD + "" + ChatColor.BOLD + kind.label, lore.toArray(new String[0]));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        final Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        EventGame.Kind[] kinds = EventGame.Kind.values();
        for (int i = 0; i < kinds.length && i < MENU_SLOTS.length; i++) {
            if (MENU_SLOTS[i] == slot) {
                final EventGame.Kind kind = kinds[i];
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.closeInventory();
                    host(player, kind);
                });
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
