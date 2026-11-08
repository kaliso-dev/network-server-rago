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
        if (target == null) {
            challenger.sendMessage(ChatColor.RED + "Ce joueur n'est pas connecté.");
            return false;
        }
        if (target.equals(challenger)) {
            challenger.sendMessage(ChatColor.RED + "Tu ne peux pas te défier toi-même.");
            return false;
        }
        if (!state.isInLobby(challenger) || state.isInGame(challenger)) {
            challenger.sendMessage(ChatColor.RED + "Reviens au lobby (/spawn) pour lancer un duel.");
            return false;
        }
        if (!state.isInLobby(target) || state.isInGame(target)) {
            challenger.sendMessage(ChatColor.RED + target.getName() + " est déjà en partie.");
            return false;
        }
        return true;
    }

    void challenge(Player challenger, Player target, GameMode mode) {
        if (!canChallenge(challenger, target)) {
            return;
        }
        if (!isDuelMode(mode)) {
            challenger.sendMessage(ChatColor.RED + "On ne peut se défier qu'en 1v1.");
            return;
        }
        if (!queues.isPlayable(mode.id())) {
            challenger.sendMessage(ChatColor.GRAY + mode.name() + ChatColor.GRAY + " arrive bientôt.");
            return;
        }
        Map<UUID, Request> forTarget = pending.get(target.getUniqueId());
        if (forTarget == null) {
            forTarget = new HashMap<>();
            pending.put(target.getUniqueId(), forTarget);
        }
        forTarget.put(challenger.getUniqueId(), new Request(mode.id(), System.currentTimeMillis() + EXPIRY_MS));
        challenger.sendMessage(ChatColor.GREEN + "Défi envoyé à " + target.getName() + " en " + mode.name()
                + ChatColor.GREEN + ". Il a une minute pour accepter.");
        target.sendMessage(ChatColor.YELLOW + challenger.getName() + " te défie en " + mode.name() + ChatColor.YELLOW
                + " ! Clique pour répondre (ou /duel accepter " + challenger.getName() + ") :");
        // Clickable buttons through the vanilla /tellraw: the chat component API (bungeecord-chat)
        // is not on our compile classpath. Player names are letters, digits and _ only.
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + target.getName() + " " + buttons(challenger.getName()));
    }

    /** The JSON of the [Accepter] [Refuser] line for a challenge from this player. */
    static String buttons(String challenger) {
        return "[\"\","
                + button("[Accepter]", "green", "/duel accepter " + challenger, "Lancer le duel")
                + ",{\"text\":\"   \"},"
                + button("[Refuser]", "red", "/duel refuser " + challenger, "Refuser le défi")
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
            target.sendMessage(ChatColor.RED + "Aucun défi de " + challengerName + " en attente.");
            return;
        }
        if (!canChallenge(target, challenger)) {
            return;
        }
        GameMode mode = settings.catalog().get(request.modeId);
        if (mode == null || !queues.isPlayable(mode.id())) {
            target.sendMessage(ChatColor.GRAY + "Ce mode n'est plus ouvert.");
            return;
        }
        long now = System.currentTimeMillis();
        List<List<QueueEntry>> teams = Arrays.asList(
                Collections.singletonList(new QueueEntry(challenger.getUniqueId(), settings.defaultRating(), now)),
                Collections.singletonList(new QueueEntry(target.getUniqueId(), settings.defaultRating(), now)));
        if (queues.startDirect(new MatchProposal(mode, teams))) {
            pending.remove(challenger.getUniqueId());
        }
    }

    void refuse(Player target, String challengerName) {
        Player challenger = Bukkit.getPlayerExact(challengerName);
        if (take(target, challenger) == null) {
            target.sendMessage(ChatColor.RED + "Aucun défi de " + challengerName + " en attente.");
            return;
        }
        target.sendMessage(ChatColor.GRAY + "Défi refusé.");
        challenger.sendMessage(ChatColor.RED + target.getName() + " a refusé ton défi.");
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
