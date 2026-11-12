package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * /party (/p), /pc and /chat, as on Hypixel: invites that last a minute and are clicked to accept,
 * accept, leave, list, kick, transfer, disband, warp, the party chat. The messages sit between two
 * blue lines. Only the leader queues, for the whole party ({@link QueueController}). Main thread
 * only, but for the chat channel, which the chat reads from its own thread.
 */
final class Parties implements Listener {

    static final String LINE = Brand.ACCENT.toString() + ChatColor.STRIKETHROUGH
            + "-----------------------------------------------------";
    private static final List<String> SUBCOMMANDS = Arrays.asList("invite", "accept", "leave", "list", "disband",
            "kick", "remove", "transfer", "promote", "warp", "chat");

    private final RagoLobby lobby;
    private final PartyBook book;
    private final LobbyState state;
    private final NetworkRanks ranks;
    private QueueController queues;
    /** Whether a player may invite another (their settings): yes to everyone by default. */
    private java.util.function.BiPredicate<Player, Player> gate = (from, to) -> true;
    /** The players whose chat goes to their party (/chat party). Read by the chat thread. */
    private final Set<UUID> partyChannel = ConcurrentHashMap.newKeySet();

    Parties(RagoLobby lobby, PartyBook book, LobbyState state, NetworkRanks ranks) {
        this.lobby = lobby;
        this.book = book;
        this.state = state;
        this.ranks = ranks;
    }

    void setQueues(QueueController queues) {
        this.queues = queues;
    }

    /** Who may invite whom: the gate tells the inviter why not. */
    void setGate(java.util.function.BiPredicate<Player, Player> gate) {
        this.gate = gate;
    }

    PartyBook book() {
        return book;
    }

    // --- Commands -------------------------------------------------------------------------------

    /** /party and /p. */
    boolean command(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "invite":
                if (args.length < 2) {
                    boxed(player, ChatColor.RED + "Usage: /party invite <player>");
                } else {
                    for (int i = 1; i < args.length; i++) {
                        invite(player, args[i]);
                    }
                }
                return true;
            case "accept":
                if (args.length != 2) {
                    boxed(player, ChatColor.RED + "Usage: /party accept <player>");
                } else {
                    accept(player, args[1]);
                }
                return true;
            case "leave":
                leave(player);
                return true;
            case "list":
                list(player);
                return true;
            case "disband":
                disband(player);
                return true;
            case "kick":
            case "remove":
                if (args.length != 2) {
                    boxed(player, ChatColor.RED + "Usage: /party kick <player>");
                } else {
                    kick(player, args[1]);
                }
                return true;
            case "transfer":
            case "promote":
                if (args.length != 2) {
                    boxed(player, ChatColor.RED + "Usage: /party transfer <player>");
                } else {
                    transfer(player, args[1]);
                }
                return true;
            case "warp":
                warp(player);
                return true;
            case "chat":
                chat(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                return true;
            default:
                // /p <player> [player...]: the Hypixel shortcut for an invite.
                for (String name : args) {
                    invite(player, name);
                }
                return true;
        }
    }

    /** /chat party, /chat all: where the player's chat goes. */
    boolean channel(Player player, String[] args) {
        if (args.length != 1) {
            return false;
        }
        String channel = args[0].toLowerCase(Locale.ROOT);
        if (channel.equals("p") || channel.equals("party")) {
            if (book.together(player.getUniqueId()) == null) {
                boxed(player, ChatColor.RED + "You must be in a party to join the party channel!");
                return true;
            }
            partyChannel.add(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "You are now in the " + ChatColor.GOLD + "PARTY" + ChatColor.GREEN + " channel");
            return true;
        }
        if (channel.equals("a") || channel.equals("all")) {
            partyChannel.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "You are now in the " + ChatColor.GOLD + "ALL" + ChatColor.GREEN + " channel");
            return true;
        }
        return false;
    }

    private void help(Player player) {
        player.sendMessage(LINE);
        player.sendMessage(ChatColor.GREEN + "Party Commands:");
        help(player, "/party invite <player>", "Invites the player to your party");
        help(player, "/party accept <player>", "Accepts a party invite");
        help(player, "/party leave", "Leaves the current party");
        help(player, "/party list", "Lists the members of your party");
        help(player, "/party kick <player>", "Removes the player from your party");
        help(player, "/party transfer <player>", "Transfers the party to another player");
        help(player, "/party warp", "Brings your party members to you");
        help(player, "/party disband", "Disbands the party");
        help(player, "/pc <message>", "Sends a chat message to the entire party");
        help(player, "/chat <party|all>", "Sends your chat to the party, or to everyone");
        player.sendMessage(LINE);
    }

    private static void help(Player player, String command, String what) {
        player.sendMessage(Brand.ACCENT + command + ChatColor.DARK_GRAY + " - " + ChatColor.AQUA + what);
    }

    private void invite(Player player, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null || !lobby.canSee(player, target)) {
            boxed(player, ChatColor.RED + "Couldn't find a player with that name!");
            return;
        }
        if (!target.equals(player) && !gate.test(player, target)) {
            return;
        }
        PartyBook.Invite result = book.invite(player.getUniqueId(), target.getUniqueId(), System.currentTimeMillis());
        switch (result) {
            case SELF:
                boxed(player, ChatColor.RED + "You cannot party yourself!");
                return;
            case NOT_LEADER:
                boxed(player, ChatColor.RED + "You are not the party leader!");
                return;
            case ALREADY_MEMBER:
                boxed(player, name(target) + ChatColor.RED + " is already in the party.");
                return;
            case ALREADY_INVITED:
                boxed(player, ChatColor.RED + name(target) + ChatColor.RED + " has already been invited to the party.");
                return;
            case FULL:
                boxed(player, ChatColor.RED + "The party is full (" + PartyBook.MAX_SIZE + " players).");
                return;
            default:
                break;
        }
        PartyBook.Party party = book.of(player.getUniqueId());
        tellParty(party, name(player) + ChatColor.GRAY + " invited " + name(target) + ChatColor.GRAY
                + " to the party! They have " + ChatColor.WHITE + "60" + ChatColor.GRAY + " seconds to accept.");
        target.sendMessage(LINE);
        target.sendMessage(name(player) + ChatColor.GRAY + " has invited you to join their party!");
        // Clickable through the vanilla /tellraw, as the duel buttons: names are letters, digits and _.
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + target.getName() + " " + joinButton(player.getName()));
        target.sendMessage(LINE);
    }

    /** The JSON of "You have 60 seconds to accept. Click here to join!" for an invite of this player. */
    static String joinButton(String inviter) {
        return "[\"\",{\"text\":\"You have \",\"color\":\"yellow\"},{\"text\":\"60\",\"color\":\"red\"},"
                + "{\"text\":\" seconds to accept. \",\"color\":\"yellow\"},"
                + "{\"text\":\"Click here to join!\",\"color\":\"gold\",\"bold\":true,"
                + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/party accept " + inviter + "\"},"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to run /party accept " + inviter + "\"}}]";
    }

    private void accept(Player player, String name) {
        Player inviter = Bukkit.getPlayerExact(name);
        PartyBook.Accept result = inviter == null ? PartyBook.Accept.NO_INVITE
                : book.accept(player.getUniqueId(), inviter.getUniqueId(), System.currentTimeMillis());
        switch (result) {
            case NO_INVITE:
                boxed(player, ChatColor.RED + "That party invite has expired or does not exist.");
                return;
            case IN_A_PARTY:
                boxed(player, ChatColor.RED + "You are already in a party! Leave it to join another one.");
                return;
            case FULL:
                boxed(player, ChatColor.RED + "That party is full.");
                return;
            default:
                break;
        }
        PartyBook.Party party = book.of(player.getUniqueId());
        changed(party.members());
        Player leader = Bukkit.getPlayer(party.leader());
        boxed(player, ChatColor.GRAY + "You have joined " + (leader == null ? "the" : name(leader) + ChatColor.GRAY + "'s")
                + " party!");
        for (UUID member : party.members()) {
            Player other = Bukkit.getPlayer(member);
            if (other != null && !other.equals(player)) {
                boxed(other, name(player) + ChatColor.GRAY + " joined the party.");
            }
        }
    }

    private void leave(Player player) {
        PartyBook.Party party = book.of(player.getUniqueId());
        if (party == null) {
            boxed(player, ChatColor.RED + "You are not currently in a party.");
            return;
        }
        List<UUID> before = party.members();
        changed(before);
        book.leave(player.getUniqueId());
        partyChannel.remove(player.getUniqueId());
        boxed(player, ChatColor.GRAY + "You left the party.");
        told(party, before, name(player) + ChatColor.GRAY + " has left the party.");
    }

    private void list(Player player) {
        PartyBook.Party party = book.of(player.getUniqueId());
        if (party == null) {
            boxed(player, ChatColor.RED + "You are not currently in a party.");
            return;
        }
        List<UUID> members = party.members();
        player.sendMessage(LINE);
        player.sendMessage(ChatColor.GOLD + "Party Members (" + members.size() + ")");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Party Leader: " + nameOf(members.get(0)) + ChatColor.GREEN + " ●");
        if (members.size() > 1) {
            StringBuilder others = new StringBuilder(ChatColor.GRAY + "Party Members: ");
            for (UUID member : members.subList(1, members.size())) {
                others.append(nameOf(member)).append(ChatColor.GREEN).append(" ● ");
            }
            player.sendMessage(others.toString().trim());
        }
        player.sendMessage(LINE);
    }

    private void disband(Player player) {
        PartyBook.Party party = book.of(player.getUniqueId());
        if (party == null) {
            boxed(player, ChatColor.RED + "You are not currently in a party.");
            return;
        }
        if (!party.isLeader(player.getUniqueId())) {
            boxed(player, ChatColor.RED + "You are not the party leader!");
            return;
        }
        List<UUID> members = party.members();
        changed(members);
        book.disband(party);
        for (UUID member : members) {
            partyChannel.remove(member);
            Player other = Bukkit.getPlayer(member);
            if (other != null) {
                boxed(other, name(player) + ChatColor.GRAY + " has disbanded the party!");
            }
        }
    }

    private void kick(Player player, String name) {
        PartyBook.Party party = book.of(player.getUniqueId());
        if (party == null || !party.isLeader(player.getUniqueId())) {
            boxed(player, ChatColor.RED + (party == null ? "You are not currently in a party." : "You are not the party leader!"));
            return;
        }
        UUID target = member(party, name);
        if (target == null || target.equals(player.getUniqueId())) {
            boxed(player, ChatColor.RED + "That player is not in your party.");
            return;
        }
        List<UUID> before = new ArrayList<>(party.members());
        changed(before);
        book.kick(player.getUniqueId(), target);
        partyChannel.remove(target);
        Player kicked = Bukkit.getPlayer(target);
        if (kicked != null) {
            boxed(kicked, ChatColor.GRAY + "You have been kicked from the party by " + name(player));
        }
        before.remove(target);
        told(party, before, nameOf(target) + ChatColor.GRAY + " has been removed from the party.");
    }

    private void transfer(Player player, String name) {
        PartyBook.Party party = book.of(player.getUniqueId());
        if (party == null || !party.isLeader(player.getUniqueId())) {
            boxed(player, ChatColor.RED + (party == null ? "You are not currently in a party." : "You are not the party leader!"));
            return;
        }
        UUID target = member(party, name);
        if (target == null || target.equals(player.getUniqueId())) {
            boxed(player, ChatColor.RED + "That player is not in your party.");
            return;
        }
        changed(party.members());
        book.transfer(player.getUniqueId(), target);
        tellParty(party, ChatColor.GRAY + "The party was transferred to " + nameOf(target) + ChatColor.GRAY + " by "
                + name(player));
    }

    /**
     * As on Hypixel, every online member comes to the leader: a member in a game leaves it (as with
     * /spawn, a forfeit in a duel), a member elsewhere comes back to the lobby, then each one stands
     * where the leader stands. The leader warps from the lobby.
     */
    private void warp(Player player) {
        PartyBook.Party party = book.together(player.getUniqueId());
        if (party == null || !party.isLeader(player.getUniqueId())) {
            boxed(player, ChatColor.RED + (party == null ? "You are not currently in a party." : "You are not the party leader!"));
            return;
        }
        if (!state.isInLobby(player)) {
            boxed(player, ChatColor.RED + "Go back to the lobby (/l) to warp your party.");
            return;
        }
        int warped = 0;
        for (UUID member : party.members()) {
            Player other = Bukkit.getPlayer(member);
            if (other == null || other.equals(player)) {
                continue;
            }
            if (state.isInGame(other)) {
                lobby.goToSpawn(other);
            } else if (!state.isInLobby(other)) {
                lobby.sendToLobby(other);
            }
            if (!state.isInLobby(other)) {
                continue;
            }
            other.teleport(player.getLocation());
            boxed(other, name(player) + ChatColor.GRAY + " summoned you to their lobby.");
            warped++;
        }
        boxed(player, ChatColor.GRAY + "You summoned " + ChatColor.WHITE + warped + ChatColor.GRAY
                + (warped == 1 ? " player" : " players") + " to your lobby.");
    }

    /** /pc and /party chat: a message to the whole party. */
    void chat(Player player, String message) {
        PartyBook.Party party = book.together(player.getUniqueId());
        if (party == null) {
            partyChannel.remove(player.getUniqueId());
            boxed(player, ChatColor.RED + "You are not in a party.");
            return;
        }
        if (message.trim().isEmpty()) {
            boxed(player, ChatColor.RED + "Usage: /pc <message>");
            return;
        }
        String muted = lobby.mutedFor(player);
        if (muted != null) {
            player.sendMessage(muted);
            return;
        }
        String line = Brand.ACCENT + "Party " + ChatColor.DARK_GRAY + "> " + name(player) + ChatColor.WHITE + ": " + message;
        for (UUID member : party.members()) {
            Player other = Bukkit.getPlayer(member);
            if (other != null) {
                other.sendMessage(line);
            }
        }
        lobby.getLogger().info("[Party] " + player.getName() + ": " + message);
    }

    /** /chat party: what the player writes goes to the party, sent from the main thread. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!partyChannel.contains(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(lobby, () -> {
            if (player.isOnline()) {
                chat(player, message);
            }
        });
    }

    // --- Time and quits -------------------------------------------------------------------------

    /** Once a second: the invites of more than a minute expire. */
    void tick() {
        Set<PartyBook.Party> disbanded = new HashSet<>();
        for (PartyBook.Expired expired : book.expire(System.currentTimeMillis())) {
            Player invited = Bukkit.getPlayer(expired.invited);
            if (invited != null) {
                boxed(invited, ChatColor.GRAY + "The party invite from " + nameOf(expired.leader) + ChatColor.GRAY
                        + " has expired.");
            }
            String message = ChatColor.GRAY + "The party invite to " + nameOf(expired.invited) + ChatColor.GRAY
                    + " has expired.";
            if (!expired.party.isDisbanded()) {
                tellParty(expired.party, message);
                continue;
            }
            Player leader = Bukkit.getPlayer(expired.leader);
            if (leader == null) {
                continue;
            }
            if (expired.last && disbanded.add(expired.party)) {
                boxed(leader, message, ChatColor.RED + "The party was disbanded because all invites expired and the party was empty.");
            } else {
                boxed(leader, message);
            }
        }
    }

    /** The player left the server: out of the party (the next player leads if they led it). */
    void forget(UUID player) {
        partyChannel.remove(player);
        PartyBook.Party party = book.of(player);
        if (party == null) {
            return;
        }
        List<UUID> before = new ArrayList<>(party.members());
        changed(before);
        String name = nameOf(player);
        book.leave(player);
        before.remove(player);
        told(party, before, name + ChatColor.GRAY + " left the server and the party.");
    }

    // --- Helpers --------------------------------------------------------------------------------

    /** The party changes: it leaves the queue it waited in, and its players those they waited in. */
    private void changed(Collection<UUID> players) {
        if (queues != null) {
            queues.partyChanged(players);
        }
    }

    /** Tells these former members what happened, and whether the party is over. */
    private void told(PartyBook.Party party, List<UUID> others, String message) {
        for (UUID member : others) {
            Player other = Bukkit.getPlayer(member);
            if (other == null) {
                continue;
            }
            if (party.isDisbanded()) {
                partyChannel.remove(member);
                boxed(other, message, ChatColor.RED + "The party was disbanded because all members left.");
            } else {
                boxed(other, message);
            }
        }
    }

    private void tellParty(PartyBook.Party party, String message) {
        for (UUID member : party.members()) {
            Player other = Bukkit.getPlayer(member);
            if (other != null) {
                boxed(other, message);
            }
        }
    }

    /** The lines between two blue lines, as Hypixel writes its party messages. */
    static void boxed(Player player, String... lines) {
        player.sendMessage(LINE);
        for (String line : lines) {
            player.sendMessage(line);
        }
        player.sendMessage(LINE);
    }

    /** The member of the party with this name, or null. */
    private static UUID member(PartyBook.Party party, String name) {
        for (UUID member : party.members()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null && online.getName().equalsIgnoreCase(name)) {
                return member;
            }
        }
        return null;
    }

    /** The name with the level and rank, as in the chat. */
    private String name(Player player) {
        return ranks.chatName(player.getUniqueId(), player.getName());
    }

    private String nameOf(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player == null ? ChatColor.GRAY + "someone" : name(player);
    }

    /** The words after /party for tab completion. */
    static List<String> subcommands() {
        return SUBCOMMANDS;
    }
}
