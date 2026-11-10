package gg.rago.practice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Brackets, Last Man Standing), the whole server is told with a [Click to join] for a minute,
 * /join (or /event join) comes in, /spawn leaves, /event cancel calls it off (its host or the
 * staff). One event at a time, a pause between two, and a cooldown for each host. The games are
 * {@link EventGame}s, in modes of the lobby hidden from the selector. Main thread only.
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

        Settings(int waitSeconds, int minPlayers, int maxPlayers, int cooldownMinutes, int pauseSeconds, int winXp, int playXp) {
            this.waitSeconds = waitSeconds;
            this.minPlayers = minPlayers;
            this.maxPlayers = maxPlayers;
            this.cooldownMinutes = cooldownMinutes;
            this.pauseSeconds = pauseSeconds;
            this.winXp = winXp;
            this.playXp = playXp;
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
            return new Settings(v[0], v[1], v[2], v[3], v[4], v[5], v[6]);
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
            return null;
        }
    }

    /** The event being set up or played: its kind and its host. */
    static final class Hosting {
        final EventGame.Kind kind;
        final UUID host;
        final String hostName;
        final long since;

        Hosting(EventGame.Kind kind, UUID host, String hostName, long since) {
            this.kind = kind;
            this.host = host;
            this.hostName = hostName;
            this.since = since;
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
    private Hosting hosting;
    private EventGame current;

    Events(RagoPractice plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
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

    /** A game of an event mode was made: the event hosted, or one nobody hosted (refused at its start). */
    EventGame created(EventGame game) {
        if (hosting != null && hosting.kind == game.kind() && current() == null) {
            current = game;
        }
        return game;
    }

    /** True if this game is the event being hosted (a game of an event mode nobody hosted is not). */
    boolean accepts(EventGame game) {
        return current == game;
    }

    /** Tells the server: who hosts, what, when it starts, and the button to join. */
    void announce(EventGame game, int secondsLeft) {
        String line = ChatColor.GOLD + "" + ChatColor.BOLD + "[Event] " + ChatColor.YELLOW + game.hostName() + ChatColor.GRAY
                + " is hosting " + ChatColor.GOLD + game.kind().label + ChatColor.GRAY + "! Starts in " + ChatColor.WHITE
                + secondsLeft + "s" + ChatColor.GRAY + " (" + game.aliveCount() + "/" + settings.maxPlayers + ").";
        String json = button(game.kind());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.lobby().isInGame(player)) {
                continue;
            }
            player.sendMessage(line);
            // clickable through the vanilla /tellraw, as the lobby's duel buttons: names are letters, digits and _
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + json);
            player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 1.4f);
        }
    }

    /** The JSON of the [Click to join] line. */
    static String button(EventGame.Kind kind) {
        return "[\"\",{\"text\":\"[Click to join]\",\"color\":\"green\",\"bold\":true,"
                + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/join\"},"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Join the " + kind.label + " event\"}},"
                + "{\"text\":\" or /join\",\"color\":\"gray\"}]";
    }

    /** The event starts: its host's cooldown runs from now. */
    void started(EventGame game) {
        Hosting h = hosting;
        if (h != null) {
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

    // --- Hosting --------------------------------------------------------------------------------

    /** Why this player cannot host now (null: they can), at this time. */
    String whyNot(Player player, long now) {
        if (!player.hasPermission(PERMISSION)) {
            return "Hosting events is a [PREMIUM+] perk: get it on the store (/store).";
        }
        if (plugin.lobby().isInGame(player)) {
            return "Leave your game first (/spawn).";
        }
        EventGame running = current();
        if (running != null || (hosting != null && now - hosting.since < 10_000L)) {
            return "An event is already running" + (running != null && running.stage() == EventGame.Stage.WAITING
                    ? ": /join it." : ": wait for its end.");
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

    /** /host with a kind: the event is set up and its host goes in first. */
    void host(Player player, EventGame.Kind kind) {
        long now = System.currentTimeMillis();
        String why = whyNot(player, now);
        if (why != null) {
            player.sendMessage(ChatColor.RED + why);
            return;
        }
        final Hosting h = new Hosting(kind, player.getUniqueId(), player.getName(), now);
        hosting = h;
        if (!plugin.lobby().queue(player, kind.modeId())) {
            hosting = null;
            player.sendMessage(ChatColor.RED + "The " + kind.label + " event is not open on this server.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Your " + kind.label + " event is being set up...");
        // the queue starts it within a second; if nothing came of it, the host may try again
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (hosting == h && current() == null) {
                hosting = null;
                Player again = Bukkit.getPlayer(h.host);
                if (again != null) {
                    again.sendMessage(ChatColor.RED + "The event could not start (no free arena): try again in a moment.");
                }
            }
        }, 200L);
    }

    /** /join: into the event waiting for its players. */
    void join(Player player) {
        EventGame game = current();
        if (game == null) {
            player.sendMessage(ChatColor.GRAY + "No event to join right now. PREMIUM players host them with /host.");
            return;
        }
        if (game.players().contains(player.getUniqueId()) && plugin.games().gameOf(player.getUniqueId()) == game) {
            player.sendMessage(ChatColor.GRAY + "You are in the event already.");
            return;
        }
        if (game.stage() != EventGame.Stage.WAITING) {
            player.sendMessage(ChatColor.GRAY + "The " + game.kind().label + " event has started: wait for the next one.");
            return;
        }
        if (game.full()) {
            player.sendMessage(ChatColor.RED + "The " + game.kind().label + " event is full.");
            return;
        }
        if (plugin.lobby().isInGame(player)) {
            player.sendMessage(ChatColor.RED + "Leave your game first (/spawn).");
            return;
        }
        if (!plugin.lobby().queue(player, game.kind().modeId())) {
            player.sendMessage(ChatColor.RED + "You cannot join the event now.");
        }
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
            if (current() != null && plugin.games().gameOf(player.getUniqueId()) == current()) {
                current().leave(player, true);
            } else {
                player.sendMessage(ChatColor.GRAY + "You are not in an event.");
            }
            return true;
        }
        info(player);
        return true;
    }

    private void cancel(CommandSender sender) {
        EventGame game = current();
        if (game == null) {
            sender.sendMessage(ChatColor.GRAY + "No event is running.");
            return;
        }
        boolean own = sender instanceof Player && hosting != null && hosting.host.equals(((Player) sender).getUniqueId());
        if (!own && !sender.hasPermission(STAFF)) {
            sender.sendMessage(ChatColor.RED + "Only its host or the staff can call the event off.");
            return;
        }
        game.cancel("called off by " + sender.getName());
    }

    private void info(Player player) {
        EventGame game = current();
        if (game == null) {
            player.sendMessage(ChatColor.GOLD + "[Event] " + ChatColor.GRAY + "No event right now. "
                    + (player.hasPermission(PERMISSION) ? "Host one with /host." : "PREMIUM players host them with /host."));
            return;
        }
        player.sendMessage(ChatColor.GOLD + "[Event] " + ChatColor.YELLOW + game.kind().label + ChatColor.GRAY + " hosted by "
                + game.hostName() + ", " + game.aliveCount() + " players"
                + (game.stage() == EventGame.Stage.WAITING ? ", starts in " + game.waitLeft() + "s: /join" : ", playing."));
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
        lore.add(why == null ? ChatColor.YELLOW + "Click to host!" : ChatColor.RED + why);
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
