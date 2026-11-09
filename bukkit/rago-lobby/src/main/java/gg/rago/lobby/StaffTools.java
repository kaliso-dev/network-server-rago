package gg.rago.lobby;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * The staff's tools and the guard of the chat. The chat: muted players, the chat muted for all,
 * a cooldown between two messages and no message sent twice in a row for players without a rank
 * that lifts it (rago.perk.chat), and the staff channel. The tools: /staffchat, /vanish, /freeze,
 * /report, /clearchat, /mutechat. The chat runs on its own threads: what it reads is concurrent.
 * Registered before {@link Parties}, so that a muted player cannot talk in the party channel either.
 */
final class StaffTools implements Listener {

    /** The same message again within this long is a repeat. */
    private static final long REPEAT_MILLIS = 30_000L;
    private static final long REPORT_COOLDOWN_MILLIS = 60_000L;

    private final RagoLobby lobby;
    private final ServerSettings server;
    private final Punishments punishments;
    private final Map<UUID, Long> lastMessage = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastText = new ConcurrentHashMap<>();
    private final Set<UUID> staffChannel = ConcurrentHashMap.newKeySet();
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastReport = new HashMap<>();
    private final Set<UUID> vanished = new HashSet<>();
    private volatile boolean chatMuted;

    StaffTools(RagoLobby lobby, ServerSettings server, Punishments punishments) {
        this.lobby = lobby;
        this.server = server;
        this.punishments = punishments;
    }

    static boolean staff(Player player) {
        return player.hasPermission("rago.staff");
    }

    /** "[STAFF] [ADMIN] Sofiene: message", to every member of the staff online. */
    void staffChat(Player from, String message) {
        String line = ChatColor.AQUA + "[STAFF] " + lobby.profiles().displayName(from.getUniqueId(), from.getName())
                + ChatColor.WHITE + ": " + message;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (staff(player)) {
                player.sendMessage(line);
            }
        }
        lobby.getLogger().info("[Staff] " + from.getName() + ": " + message);
    }

    /** /staffchat with no message: everything the player writes goes to the staff, or no longer. */
    void toggleStaffChannel(Player player) {
        if (staffChannel.remove(player.getUniqueId())) {
            player.sendMessage(ChatColor.GREEN + "You are back in the public chat.");
        } else {
            staffChannel.add(player.getUniqueId());
            player.sendMessage(ChatColor.AQUA + "Your messages now go to the staff chat. /sc again to leave it.");
        }
    }

    // --- The chat -------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final String message = event.getMessage();
        if (staffChannel.contains(player.getUniqueId())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(lobby, () -> {
                if (player.isOnline()) {
                    staffChat(player, message);
                }
            });
            return;
        }
        String muted = punishments.mutedMessage(player.getUniqueId());
        if (muted != null) {
            event.setCancelled(true);
            player.sendMessage(muted);
            return;
        }
        boolean staff = staff(player);
        if (chatMuted && !staff) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "The chat is muted by the staff for now.");
            return;
        }
        if (staff || player.hasPermission("rago.perk.chat")) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(player.getUniqueId());
        long cooldown = server.chatCooldownMillis();
        if (last != null && now - last < cooldown) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You can only chat once every " + cooldown / 1000 + " seconds. "
                    + ChatColor.GRAY + "Ranks have no cooldown: /shop");
            return;
        }
        String text = message.trim().toLowerCase(Locale.ROOT);
        if (server.blockRepeats() && last != null && now - last < REPEAT_MILLIS && text.equals(lastText.get(player.getUniqueId()))) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot say the same message twice!");
            return;
        }
        lastMessage.put(player.getUniqueId(), now);
        lastText.put(player.getUniqueId(), text);
    }

    void toggleChatMute(Player by) {
        chatMuted = !chatMuted;
        Bukkit.broadcastMessage(chatMuted ? ChatColor.RED + "The chat was muted by " + by.getName() + "."
                : ChatColor.GREEN + "The chat is open again.");
    }

    void clearChat(Player by) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!staff(player)) {
                for (int i = 0; i < 100; i++) {
                    player.sendMessage(" ");
                }
            }
            player.sendMessage(ChatColor.YELLOW + "The chat was cleared by a staff member.");
        }
        lobby.getLogger().info(by.getName() + " cleared the chat");
    }

    // --- Vanish ---------------------------------------------------------------------------------

    boolean vanished(UUID player) {
        return vanished.contains(player);
    }

    void toggleVanish(Player player) {
        if (!vanished.contains(player.getUniqueId()) && lobby.isInGame(player)) {
            player.sendMessage(ChatColor.RED + "Not during a game: the others must see who they fight.");
            return;
        }
        if (vanished.remove(player.getUniqueId())) {
            player.sendMessage(ChatColor.GREEN + "You are visible again.");
        } else {
            vanished.add(player.getUniqueId());
            player.sendMessage(ChatColor.AQUA + "You are vanished: only the staff sees you. /vanish again to come back.");
        }
        lobby.applyVanish(player);
    }

    /** A vanished member of the staff who joins a game: seen by everyone in it, as a player. */
    void unvanishForGame(Player player) {
        if (vanished.remove(player.getUniqueId())) {
            player.sendMessage(ChatColor.GREEN + "You are visible again: you joined a game.");
        }
    }

    // --- Freeze ---------------------------------------------------------------------------------

    void toggleFreeze(Player by, Player target) {
        if (frozen.remove(target.getUniqueId())) {
            target.sendMessage(ChatColor.GREEN + "You are no longer frozen.");
            by.sendMessage(ChatColor.GREEN + target.getName() + " is no longer frozen.");
            return;
        }
        if (staff(target) && !by.hasPermission("rago.admin")) {
            by.sendMessage(ChatColor.RED + "You cannot freeze a staff member.");
            return;
        }
        frozen.add(target.getUniqueId());
        tellFrozen(target);
        by.sendMessage(ChatColor.AQUA + target.getName() + " is frozen. /freeze " + target.getName() + " to let them go.");
    }

    private static void tellFrozen(Player target) {
        target.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "You are frozen by the staff. " + ChatColor.RED
                + "Do not log out: join our Discord (/discord) and wait for a staff member.");
    }

    boolean frozen(UUID player) {
        return frozen.contains(player);
    }

    /** Once a second: the frozen players are reminded every five. */
    void tick(int second) {
        if (second % 5 != 0) {
            return;
        }
        for (UUID id : frozen) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                tellFrozen(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!frozen.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && frozen.contains(event.getDamager().getUniqueId())
                || event.getEntity() instanceof Player && frozen.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** A frozen player may only talk to the staff: /msg, /r, /discord. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!frozen.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (!label.matches("msg|tell|w|r|reply|discord|rules|regles|helpop")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You are frozen: you cannot use commands.");
        }
    }

    // --- Reports --------------------------------------------------------------------------------

    void report(Player from, Player target, String reason) {
        long now = System.currentTimeMillis();
        Long last = lastReport.get(from.getUniqueId());
        if (last != null && now - last < REPORT_COOLDOWN_MILLIS && !staff(from)) {
            from.sendMessage(ChatColor.RED + "Wait a minute before reporting again.");
            return;
        }
        lastReport.put(from.getUniqueId(), now);
        String mode = lobby.state().gameOf(target.getUniqueId());
        String line = ChatColor.RED + "[REPORT] " + ChatColor.YELLOW + from.getName() + ChatColor.GRAY + " reported "
                + ChatColor.YELLOW + target.getName() + ChatColor.GRAY + (mode == null ? " (lobby)" : " (" + mode + ")") + ": "
                + ChatColor.WHITE + reason;
        int told = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (staff(player)) {
                player.sendMessage(line);
                told++;
            }
        }
        lobby.getLogger().info(ChatColor.stripColor(line));
        from.sendMessage(ChatColor.GREEN + "Thanks for your report! " + (told > 0 ? "The staff online has it."
                : "The staff will read it soon."));
    }

    /** The player left: a frozen player who logs out is reported to the staff. */
    void quit(Player player) {
        if (frozen.remove(player.getUniqueId())) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (staff(other)) {
                    other.sendMessage(ChatColor.RED + "[STAFF] " + player.getName() + " logged out while frozen!");
                }
            }
        }
        lastMessage.remove(player.getUniqueId());
        lastText.remove(player.getUniqueId());
        staffChannel.remove(player.getUniqueId());
        lastReport.remove(player.getUniqueId());
        // a vanished member of the staff stays vanished when they come back
    }
}
