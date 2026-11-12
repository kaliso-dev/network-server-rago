package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Friends and private messages, as on Hypixel: /friend add, accept, deny, remove, list; /msg and
 * /r; who may message, invite or challenge a player (their settings in the profile menu). The
 * friendships are written to both players' stats files. Main thread only.
 */
final class Social {

    static final int MAX_FRIENDS = 100;
    static final long REQUEST_MILLIS = 5 * 60_000L;
    static final String LINE = Brand.ACCENT + "" + ChatColor.STRIKETHROUGH + "-----------------------------------------------------";

    private final RagoLobby lobby;
    private final StatsService stats;
    private final Profiles profiles;
    /** Friend requests: for each player asked, who asked and when. */
    private final Map<UUID, Map<UUID, Long>> requests = new HashMap<>();
    /** Who each player last talked to in private, for /r. */
    private final Map<UUID, UUID> lastPartner = new HashMap<>();

    Social(RagoLobby lobby, StatsService stats, Profiles profiles) {
        this.lobby = lobby;
        this.stats = stats;
        this.profiles = profiles;
    }

    private static void boxed(Player player, String... lines) {
        player.sendMessage(LINE);
        for (String line : lines) {
            player.sendMessage(line);
        }
        player.sendMessage(LINE);
    }

    private String name(Player player) {
        return profiles.displayName(player.getUniqueId(), player.getName());
    }

    private String name(PlayerStats profile) {
        return profiles.displayName(profile.id(), profile.name());
    }

    boolean friends(UUID one, UUID two) {
        PlayerStats profile = stats.book().get(one);
        return profile != null && profile.friends().contains(two);
    }

    /** The online player of this name that the sender may see (vanished staff hidden), or null. */
    Player visible(Player sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        return target == null || !lobby.canSee(sender, target) ? null : target;
    }

    // --- Who may reach whom ---------------------------------------------------------------------

    /** False (and the sender is told) if the target turned party invites off and is no friend. */
    boolean acceptsParty(Player from, Player to) {
        PlayerStats profile = stats.book().get(to.getUniqueId());
        if (profile == null || profile.partyInvites() || friends(to.getUniqueId(), from.getUniqueId())
                || from.hasPermission("rago.staff")) {
            return true;
        }
        from.sendMessage(ChatColor.RED + "You cannot invite that player to your party: they only accept invites from friends.");
        return false;
    }

    /** False (and the challenger is told) if the target turned duel requests off and is no friend. */
    boolean acceptsDuel(Player from, Player to) {
        PlayerStats profile = stats.book().get(to.getUniqueId());
        if (profile == null || profile.duelRequests() || friends(to.getUniqueId(), from.getUniqueId())) {
            return true;
        }
        from.sendMessage(ChatColor.RED + to.getName() + " only accepts duel requests from friends.");
        return false;
    }

    // --- /friend ----------------------------------------------------------------------------------

    void command(Player player, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(java.util.Locale.ROOT);
        String target = args.length > 1 ? args[1] : null;
        switch (sub) {
            case "add":
                if (target == null) {
                    boxed(player, ChatColor.RED + "Usage: /friend add <player>");
                } else {
                    add(player, target);
                }
                break;
            case "accept":
            case "deny":
                if (target == null) {
                    boxed(player, ChatColor.RED + "Usage: /friend " + sub + " <player>");
                } else {
                    answer(player, target, sub.equals("accept"));
                }
                break;
            case "remove":
                if (target == null) {
                    boxed(player, ChatColor.RED + "Usage: /friend remove <player>");
                } else {
                    remove(player, target);
                }
                break;
            case "list":
                list(player, target == null ? 1 : parsePage(target));
                break;
            case "requests":
                requests(player);
                break;
            case "toggle":
                PlayerStats profile = profiles.of(player);
                profile.setFriendRequests(!profile.friendRequests());
                stats.save(profile);
                boxed(player, profile.friendRequests() ? ChatColor.GREEN + "Friend requests are now on."
                        : ChatColor.RED + "Friend requests are now off.");
                break;
            case "help":
                boxed(player, ChatColor.GREEN + "Friend commands:",
                        Brand.ACCENT + "/f add <player>" + ChatColor.GRAY + " - Sends a friend request",
                        Brand.ACCENT + "/f accept <player>" + ChatColor.GRAY + " - Accepts a friend request",
                        Brand.ACCENT + "/f deny <player>" + ChatColor.GRAY + " - Denies a friend request",
                        Brand.ACCENT + "/f remove <player>" + ChatColor.GRAY + " - Removes a friend",
                        Brand.ACCENT + "/f list [page]" + ChatColor.GRAY + " - Lists your friends",
                        Brand.ACCENT + "/f requests" + ChatColor.GRAY + " - Your pending requests",
                        Brand.ACCENT + "/f toggle" + ChatColor.GRAY + " - Turns friend requests on or off",
                        Brand.ACCENT + "/msg <player> <message>" + ChatColor.GRAY + " - A private message");
                break;
            default:
                // "/f Name" is "/f add Name", as on Hypixel
                add(player, args[0]);
                break;
        }
    }

    private static int parsePage(String text) {
        try {
            return Math.max(1, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void add(Player player, String name) {
        Player target = visible(player, name);
        if (target == null) {
            boxed(player, ChatColor.RED + "Can't find a player by the name of '" + name + "'");
            return;
        }
        if (target.equals(player)) {
            boxed(player, ChatColor.RED + "You can't add yourself as a friend!");
            return;
        }
        PlayerStats mine = profiles.of(player);
        PlayerStats theirs = profiles.of(target);
        if (mine.friends().contains(target.getUniqueId())) {
            boxed(player, ChatColor.RED + "You're already friends with this person!");
            return;
        }
        if (mine.friends().size() >= MAX_FRIENDS) {
            boxed(player, ChatColor.RED + "You have " + MAX_FRIENDS + " friends: remove one first.");
            return;
        }
        Map<UUID, Long> mineAsked = requests.get(player.getUniqueId());
        if (mineAsked != null && mineAsked.containsKey(target.getUniqueId())) {
            // they asked first: this is a yes
            answer(player, target.getName(), true);
            return;
        }
        if (!theirs.friendRequests() && !player.hasPermission("rago.staff")) {
            boxed(player, ChatColor.RED + "That player is not accepting friend requests.");
            return;
        }
        Map<UUID, Long> asked = requests.get(target.getUniqueId());
        if (asked == null) {
            asked = new HashMap<>();
            requests.put(target.getUniqueId(), asked);
        }
        if (asked.containsKey(player.getUniqueId())) {
            boxed(player, ChatColor.RED + "You've already sent a friend request to this person!");
            return;
        }
        asked.put(player.getUniqueId(), System.currentTimeMillis());
        boxed(player, ChatColor.GRAY + "You sent a friend request to " + name(target) + ChatColor.GRAY
                + "! They have 5 minutes to accept it!");
        target.sendMessage(LINE);
        target.sendMessage(ChatColor.GRAY + "Friend request from " + name(player));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + target.getName() + " " + buttons(player.getName()));
        target.sendMessage(LINE);
    }

    /** "[ACCEPT] - [DENY]" as clickable buttons. Names are letters, digits and _: nothing to escape. */
    static String buttons(String from) {
        return "[\"\",{\"text\":\"[ACCEPT]\",\"color\":\"green\",\"bold\":true,\"clickEvent\":{\"action\":\"run_command\","
                + "\"value\":\"/friend accept " + from + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to accept\"}},"
                + "{\"text\":\" - \",\"color\":\"dark_gray\"},"
                + "{\"text\":\"[DENY]\",\"color\":\"red\",\"bold\":true,\"clickEvent\":{\"action\":\"run_command\","
                + "\"value\":\"/friend deny " + from + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to deny\"}}]";
    }

    private void answer(Player player, String name, boolean accept) {
        PlayerStats from = stats.book().byName(name);
        Map<UUID, Long> asked = requests.get(player.getUniqueId());
        if (from == null || asked == null || asked.remove(from.id()) == null) {
            boxed(player, ChatColor.RED + "That person hasn't invited you to be friends! Try /friend " + name);
            return;
        }
        Player online = Bukkit.getPlayer(from.id());
        if (!accept) {
            boxed(player, ChatColor.GRAY + "Declined " + name(from) + ChatColor.GRAY + "'s friend request!");
            return;
        }
        PlayerStats mine = profiles.of(player);
        if (mine.friends().size() >= MAX_FRIENDS || from.friends().size() >= MAX_FRIENDS) {
            boxed(player, ChatColor.RED + "One of you already has " + MAX_FRIENDS + " friends.");
            return;
        }
        mine.friends().add(from.id());
        from.friends().add(player.getUniqueId());
        stats.save(mine);
        stats.save(from);
        boxed(player, ChatColor.GREEN + "You are now friends with " + name(from));
        if (online != null) {
            boxed(online, ChatColor.GREEN + "You are now friends with " + name(player));
        }
    }

    private void remove(Player player, String name) {
        PlayerStats mine = profiles.of(player);
        PlayerStats other = stats.book().byName(name);
        if (other == null || !mine.friends().remove(other.id())) {
            boxed(player, ChatColor.RED + name + " isn't on your friends list!");
            return;
        }
        other.friends().remove(player.getUniqueId());
        stats.save(mine);
        stats.save(other);
        boxed(player, ChatColor.GRAY + "You removed " + name(other) + ChatColor.GRAY + " from your friends list!");
    }

    private void requests(Player player) {
        expire();
        Map<UUID, Long> asked = requests.get(player.getUniqueId());
        if (asked == null || asked.isEmpty()) {
            boxed(player, ChatColor.GRAY + "You have no friend requests.");
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "Friend requests (" + asked.size() + ")");
        for (UUID from : asked.keySet()) {
            PlayerStats profile = stats.book().get(from);
            lines.add(ChatColor.GRAY + "- " + (profile == null ? "?" : name(profile)) + ChatColor.GRAY + ": /f accept "
                    + (profile == null ? "?" : profile.name()));
        }
        boxed(player, lines.toArray(new String[0]));
    }

    /** /f list: online friends first, ten per page, with where they are. */
    void list(Player player, int page) {
        List<PlayerStats> friends = friendsOf(player);
        if (friends.isEmpty()) {
            boxed(player, ChatColor.GRAY + "You don't have any friends yet! Add some with /friend add <player>");
            return;
        }
        int pages = (friends.size() + 9) / 10;
        int shown = Math.min(page, pages);
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "          Friends (Page " + shown + " of " + pages + ")");
        for (int i = (shown - 1) * 10; i < Math.min(friends.size(), shown * 10); i++) {
            PlayerStats friend = friends.get(i);
            lines.add(name(friend) + " " + where(player, friend.id()));
        }
        boxed(player, lines.toArray(new String[0]));
    }

    /** The player's friends, online first, then by name. */
    List<PlayerStats> friendsOf(final Player player) {
        List<PlayerStats> friends = new ArrayList<>();
        for (UUID id : profiles.of(player).friends()) {
            PlayerStats friend = stats.book().get(id);
            if (friend != null) {
                friends.add(friend);
            }
        }
        Collections.sort(friends, (a, b) -> {
            boolean onA = online(player, a.id());
            boolean onB = online(player, b.id());
            return onA != onB ? (onA ? -1 : 1) : a.name().compareToIgnoreCase(b.name());
        });
        return friends;
    }

    private boolean online(Player viewer, UUID id) {
        Player other = Bukkit.getPlayer(id);
        return other != null && lobby.canSee(viewer, other);
    }

    /** "is in the lobby", "is playing Sumo duel", "is currently offline". */
    String where(Player viewer, UUID friend) {
        Player other = Bukkit.getPlayer(friend);
        if (other == null || !lobby.canSee(viewer, other)) {
            return ChatColor.RED + "is currently offline";
        }
        String mode = lobby.state().gameOf(friend);
        if (mode != null && lobby.mode(mode) != null) {
            return ChatColor.GRAY + "is playing " + lobby.mode(mode).name();
        }
        return ChatColor.GRAY + "is in the lobby";
    }

    void expire() {
        long now = System.currentTimeMillis();
        for (Iterator<Map<UUID, Long>> it = requests.values().iterator(); it.hasNext();) {
            Map<UUID, Long> asked = it.next();
            asked.values().removeIf(when -> now - when > REQUEST_MILLIS);
            if (asked.isEmpty()) {
                it.remove();
            }
        }
    }

    // --- Arriving and leaving -------------------------------------------------------------------

    /** Tells the player's online friends that they joined or left. */
    void announce(Player player, boolean joined) {
        if (!lobby.canBeSeen(player)) {
            return;
        }
        PlayerStats profile = stats.book().get(player.getUniqueId());
        if (profile == null) {
            return;
        }
        String line = ChatColor.GREEN + "Friend > " + ChatColor.RESET + name(player)
                + (joined ? ChatColor.GRAY + " joined." : ChatColor.GRAY + " left.");
        for (UUID id : profile.friends()) {
            Player friend = Bukkit.getPlayer(id);
            if (friend != null && friend != player) {
                friend.sendMessage(line);
            }
        }
    }

    void forget(UUID player) {
        requests.remove(player);
        lastPartner.remove(player);
    }

    // --- Private messages -----------------------------------------------------------------------

    /** /msg: a private message, if the target lets the sender write to them. */
    void message(Player sender, String name, String text) {
        Player target = visible(sender, name);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Can't find a player by the name of '" + name + "'");
            return;
        }
        send(sender, target, text);
    }

    /** /r: to the last player this one talked to in private. */
    void reply(Player sender, String text) {
        UUID partner = lastPartner.get(sender.getUniqueId());
        Player target = partner == null ? null : Bukkit.getPlayer(partner);
        if (target == null || !lobby.canSee(sender, target)) {
            sender.sendMessage(ChatColor.RED + "You have nobody to reply to.");
            return;
        }
        send(sender, target, text);
    }

    private void send(Player sender, Player target, String text) {
        if (target.equals(sender)) {
            sender.sendMessage(ChatColor.RED + "You cannot message yourself!");
            return;
        }
        String muted = lobby.mutedFor(sender);
        if (muted != null) {
            sender.sendMessage(muted);
            return;
        }
        boolean staff = sender.hasPermission("rago.staff");
        PlayerStats theirs = stats.book().get(target.getUniqueId());
        PlayerStats.MessageSetting setting = theirs == null ? PlayerStats.MessageSetting.ALL : theirs.messages();
        if (!staff && setting == PlayerStats.MessageSetting.NONE) {
            sender.sendMessage(ChatColor.RED + "That player has private messages turned off.");
            return;
        }
        if (!staff && setting == PlayerStats.MessageSetting.FRIENDS && !friends(target.getUniqueId(), sender.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "That player only accepts private messages from friends.");
            return;
        }
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "To " + name(target) + ChatColor.GRAY + ": " + text);
        target.sendMessage(ChatColor.LIGHT_PURPLE + "From " + name(sender) + ChatColor.GRAY + ": " + text);
        lastPartner.put(sender.getUniqueId(), target.getUniqueId());
        lastPartner.put(target.getUniqueId(), sender.getUniqueId());
        lobby.getLogger().info("[MSG] " + sender.getName() + " -> " + target.getName() + ": " + text);
    }
}
