package gg.rago.lobby;

import gg.rago.game.GameMode;
import gg.rago.game.MatchProposal;
import gg.rago.game.QueueEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * /duel: a player challenges a friend in the lobby to a 1v1 mode, without the queue. The friend has
 * a minute to accept with /duel accepter. Main thread only.
 */
final class DuelRequests {

    static final long EXPIRY_MS = 60_000;

    private static final class Request {
        final String modeId;
        final long expiresAt;

        Request(String modeId, long expiresAt) {
            this.modeId = modeId;
            this.expiresAt = expiresAt;
        }
    }

    private final LobbySettings settings;
    private final LobbyState state;
    private final QueueController queues;
    /** Target, then challenger, then the request. */
    private final Map<UUID, Map<UUID, Request>> pending = new HashMap<>();
    /** Whether a player may challenge another (their settings): yes to everyone by default. */
    private java.util.function.BiPredicate<Player, Player> gate = (from, to) -> true;

    private java.util.function.Predicate<UUID> frozen = id -> false;

    /** Who may challenge whom: the gate tells the challenger why not. */
    void setGate(java.util.function.BiPredicate<Player, Player> gate) {
        this.gate = gate;
    }

    /** The players frozen by the staff: no duel for them, sent or accepted. */
    void setFrozen(java.util.function.Predicate<UUID> frozen) {
        this.frozen = frozen;
    }

    DuelRequests(LobbySettings settings, LobbyState state, QueueController queues) {
        this.settings = settings;
        this.state = state;
        this.queues = queues;
    }

    /** The modes one can duel in: two teams of one, in the order of config.yml. */
    List<ModeDisplay> duelModes() {
        List<ModeDisplay> modes = new ArrayList<>();
        for (ModeDisplay display : settings.modes()) {
            if (isDuelMode(display.mode())) {
                modes.add(display);
            }
        }
        return modes;
    }

    static boolean isDuelMode(GameMode mode) {
        return mode.teams() == 2 && mode.playersPerTeam() == 1;
    }

    /** Checks both players can duel; tells the challenger why not. */
    boolean canChallenge(Player challenger, Player target) {
        return canPlay(challenger, target) && gate.test(challenger, target);
    }

    /** Checks both players can play a duel now; tells the first why not. */
    private boolean canPlay(Player challenger, Player target) {
        if (target == null) {
            challenger.sendMessage(ChatColor.RED + "That player is not online.");
            return false;
        }
        if (target.equals(challenger)) {
            challenger.sendMessage(ChatColor.RED + "You cannot duel yourself.");
            return false;
        }
        if (!state.isInLobby(challenger) || state.isInGame(challenger)) {
            challenger.sendMessage(ChatColor.RED + "Go back to the lobby (/spawn) to start a duel.");
            return false;
        }
        if (!state.isInLobby(target) || state.isInGame(target)) {
            challenger.sendMessage(ChatColor.RED + target.getName() + " is already in a game.");
            return false;
        }
        if (frozen.test(challenger.getUniqueId()) || frozen.test(target.getUniqueId())) {
            challenger.sendMessage(ChatColor.RED + "No duel with a player frozen by the staff.");
            return false;
        }
        return true;
    }

    void challenge(Player challenger, Player target, GameMode mode) {
        if (!canChallenge(challenger, target)) {
            return;
        }
        if (!isDuelMode(mode)) {
            challenger.sendMessage(ChatColor.RED + "Duels are 1v1 only.");
            return;
        }
        if (!queues.isPlayable(mode.id())) {
            challenger.sendMessage(ChatColor.GRAY + mode.name() + ChatColor.GRAY + " is coming soon.");
            return;
        }
        Map<UUID, Request> forTarget = pending.get(target.getUniqueId());
        if (forTarget == null) {
            forTarget = new HashMap<>();
            pending.put(target.getUniqueId(), forTarget);
        }
        forTarget.put(challenger.getUniqueId(), new Request(mode.id(), System.currentTimeMillis() + EXPIRY_MS));
        challenger.sendMessage(ChatColor.GREEN + "Duel request sent to " + target.getName() + " in " + mode.name()
                + ChatColor.GREEN + ". They have one minute to accept.");
        target.sendMessage(ChatColor.YELLOW + challenger.getName() + " challenges you to " + mode.name() + ChatColor.YELLOW
                + "! Click to answer (or /duel accept " + challenger.getName() + "):");
        // Clickable buttons through the vanilla /tellraw: the chat component API (bungeecord-chat)
        // is not on our compile classpath. Player names are letters, digits and _ only.
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + target.getName() + " " + buttons(challenger.getName()));
    }

    /** The JSON of the [Accept] [Deny] line for a challenge from this player. */
    static String buttons(String challenger) {
        return "[\"\","
                + button("[Accept]", "green", "/duel accept " + challenger, "Start the duel")
                + ",{\"text\":\"   \"},"
                + button("[Deny]", "red", "/duel deny " + challenger, "Deny the duel")
                + "]";
    }

    private static String button(String text, String colour, String command, String hover) {
        return "{\"text\":\"" + text + "\",\"color\":\"" + colour + "\",\"bold\":true,"
                + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" + command + "\"},"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"" + hover + "\"}}";
    }

    void accept(Player target, String challengerName) {
        Player challenger = Bukkit.getPlayerExact(challengerName);
        Request request = take(target, challenger);
        if (request == null) {
            target.sendMessage(ChatColor.RED + "No pending duel request from " + challengerName + ".");
            return;
        }
        if (!canPlay(target, challenger)) {
            return;
        }
        GameMode mode = settings.catalog().get(request.modeId);
        if (mode == null || !queues.isPlayable(mode.id())) {
            target.sendMessage(ChatColor.GRAY + "This mode is not open any more.");
            return;
        }
        long now = System.currentTimeMillis();
        List<List<QueueEntry>> teams = Arrays.asList(
                Collections.singletonList(new QueueEntry(challenger.getUniqueId(), settings.defaultRating(), now)),
                Collections.singletonList(new QueueEntry(target.getUniqueId(), settings.defaultRating(), now)));
        if (queues.startDirect(new MatchProposal(mode, teams), false)) {
            pending.remove(challenger.getUniqueId());
        }
    }

    void refuse(Player target, String challengerName) {
        Player challenger = Bukkit.getPlayerExact(challengerName);
        if (take(target, challenger) == null) {
            target.sendMessage(ChatColor.RED + "No pending duel request from " + challengerName + ".");
            return;
        }
        target.sendMessage(ChatColor.GRAY + "Duel request denied.");
        challenger.sendMessage(ChatColor.RED + target.getName() + " denied your duel request.");
    }

    /** Removes and returns a live request of challenger to target, or null. */
    private Request take(Player target, Player challenger) {
        if (challenger == null) {
            return null;
        }
        Map<UUID, Request> forTarget = pending.get(target.getUniqueId());
        Request request = forTarget == null ? null : forTarget.remove(challenger.getUniqueId());
        if (forTarget != null && forTarget.isEmpty()) {
            pending.remove(target.getUniqueId());
        }
        return request == null || request.expiresAt < System.currentTimeMillis() ? null : request;
    }

    /** A player left: their requests go. */
    void forget(UUID player) {
        pending.remove(player);
        for (Map<UUID, Request> forTarget : pending.values()) {
            forTarget.remove(player);
        }
    }
}
