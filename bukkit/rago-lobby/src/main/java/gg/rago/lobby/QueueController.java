package gg.rago.lobby;

import gg.rago.game.GameMode;
import gg.rago.game.MatchProposal;
import gg.rago.game.Matchmaker;
import gg.rago.game.QueueEntry;
import gg.rago.lobby.api.MatchHost;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The local queues: the lobby's matchmaker and the game plugins that play each mode. Once a
 * second, the matches found are handed to their {@link MatchHost}. Main thread only.
 *
 * <p>This is the queue of the local test server, where the lobby and the games share one server.
 * On the network the same {@link Matchmaker} runs in the proxy (rago-proxy, jalon Ranked).
 */
final class QueueController {

    private final LobbySettings settings;
    private final LobbyState state;
    private final RagoLobby lobby;
    private final Logger logger;
    private final Matchmaker matchmaker;
    private final Map<String, MatchHost> hosts = new HashMap<>();
    private final Map<String, Plugin> hostPlugins = new HashMap<>();
    /** Players already told that no arena is free, so that they are told once, not every second. */
    private final Set<UUID> toldNoArena = new HashSet<>();
    private final Runnable onChange;

    QueueController(RagoLobby lobby, LobbySettings settings, LobbyState state, Runnable onChange) {
        this.lobby = lobby;
        this.settings = settings;
        this.state = state;
        this.logger = lobby.getLogger();
        this.matchmaker = new Matchmaker(settings.catalog().all(), settings.window());
        this.onChange = onChange;
    }

    // --- Hosts ----------------------------------------------------------------------------------

    boolean register(Plugin plugin, String modeId, MatchHost host) {
        if (settings.catalog().get(modeId) == null) {
            return false;
        }
        hosts.put(modeId, host);
        hostPlugins.put(modeId, plugin);
        logger.info("Mode " + modeId + " hosted by " + plugin.getName());
        onChange.run();
        return true;
    }

    void unregister(Plugin plugin) {
        List<String> closed = new ArrayList<>();
        for (Map.Entry<String, Plugin> entry : hostPlugins.entrySet()) {
            if (entry.getValue().equals(plugin)) {
                closed.add(entry.getKey());
            }
        }
        for (String modeId : closed) {
            hosts.remove(modeId);
            hostPlugins.remove(modeId);
            closeQueue(modeId);
        }
        if (!closed.isEmpty()) {
            onChange.run();
        }
    }

    /** True if a game plugin plays this mode: its queue is open. */
    boolean isPlayable(String modeId) {
        return hosts.containsKey(modeId);
    }

    MatchHost hostOf(String modeId) {
        return hosts.get(modeId);
    }

    // --- Players --------------------------------------------------------------------------------

    /** The player clicked a mode in the selector, or typed /queue. */
    void join(Player player, GameMode mode) {
        if (state.isInGame(player)) {
            player.sendMessage(ChatColor.RED + "You are already in a game.");
            return;
        }
        if (!isPlayable(mode.id())) {
            player.sendMessage(ChatColor.GRAY + mode.name() + ChatColor.GRAY + " is coming soon.");
            return;
        }
        Matchmaker.JoinResult result = matchmaker.join(player.getUniqueId(), mode.id(), settings.defaultRating(),
                System.currentTimeMillis());
        switch (result) {
            case JOINED:
            case SWITCHED:
                toldNoArena.remove(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "You joined the queue for " + mode.name() + ChatColor.GREEN + " ("
                        + mode.format() + ", " + matchmaker.waiting(mode.id()) + " waiting).");
                onChange.run();
                break;
            case ALREADY_WAITING:
                player.sendMessage(ChatColor.GRAY + "You are already in this queue.");
                break;
            default:
                player.sendMessage(ChatColor.RED + "This mode does not exist.");
                break;
        }
    }

    /** Returns the mode left, or null if the player was not waiting. */
    GameMode leave(Player player, boolean tell) {
        GameMode left = matchmaker.leave(player.getUniqueId());
        toldNoArena.remove(player.getUniqueId());
        if (left != null) {
            if (tell) {
                player.sendMessage(ChatColor.YELLOW + "You left the queue for " + left.name() + ChatColor.YELLOW + ".");
            }
            onChange.run();
        }
        return left;
    }

    /** The player left the server. */
    void forget(UUID player) {
        if (matchmaker.leave(player) != null) {
            onChange.run();
        }
        toldNoArena.remove(player);
    }

    GameMode queueOf(Player player) {
        return matchmaker.queueOf(player.getUniqueId());
    }

    /** How long the player has waited, in milliseconds, or -1 if not waiting. */
    long waited(Player player, long nowMillis) {
        QueueEntry entry = matchmaker.entryOf(player.getUniqueId());
        return entry == null ? -1L : entry.waitedMillis(nowMillis);
    }

    int waiting(String modeId) {
        return matchmaker.waiting(modeId);
    }

    // --- Matches --------------------------------------------------------------------------------

    /** Once a second: starts the matches that were found. */
    void tick() {
        List<MatchProposal> matches = matchmaker.poll(System.currentTimeMillis());
        for (MatchProposal match : matches) {
            start(match);
        }
        if (!matches.isEmpty()) {
            onChange.run();
        }
    }

    private void start(MatchProposal match) {
        start(match, true);
    }

    /**
     * Starts a match without the queue (a /duel between two players in the lobby). Both leave any
     * queue first. False, with the players told why, if the game could not start.
     */
    boolean startDirect(MatchProposal match) {
        for (UUID id : match.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                matchmaker.leave(id);
            }
        }
        boolean started = start(match, false);
        onChange.run();
        return started;
    }

    private boolean start(MatchProposal match, boolean requeue) {
        String modeId = match.mode().id();
        MatchHost host = hosts.get(modeId);
        List<Player> players = new ArrayList<>();
        for (UUID id : match.players()) {
            Player player = Bukkit.getPlayer(id);
            // A player who left the lobby without leaving the queue (sent elsewhere by a command)
            // is not started: the others go back to the queue.
            if (player != null && state.isInLobby(player)) {
                players.add(player);
            }
        }
        if (host == null || players.size() != match.players().size()) {
            if (requeue) {
                requeueOnline(match);
            }
            return false;
        }
        // The players belong to the game before it starts: the host teleports them during start().
        for (Player player : players) {
            lobby.enterGame(player, modeId);
        }
        for (List<QueueEntry> team : match.teams()) {
            List<UUID> others = new ArrayList<>(match.players());
            for (QueueEntry entry : team) {
                others.remove(entry.player());
            }
            for (QueueEntry entry : team) {
                state.setOpponents(entry.player(), others);
            }
        }
        boolean started;
        try {
            started = host.start(match);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "The mode " + modeId + " could not start a game", e);
            started = false;
        }
        if (started) {
            for (Player player : players) {
                toldNoArena.remove(player.getUniqueId());
            }
            return true;
        }
        // Not started: still in the lobby, unless the host had already teleported someone.
        for (Player player : players) {
            lobby.cancelGame(player);
        }
        if (!requeue) {
            for (Player player : players) {
                player.sendMessage(ChatColor.YELLOW + "No free arena right now: try again in a moment.");
            }
            return false;
        }
        requeueOnline(match);
        for (Player player : players) {
            if (toldNoArena.add(player.getUniqueId())) {
                player.sendMessage(ChatColor.YELLOW + "No free arena right now: you keep your place in the queue.");
            }
        }
        return false;
    }

    /**
     * Puts back in the queue, at their original place, the players of this match who are still
     * online, in the lobby and not waiting elsewhere.
     */
    private void requeueOnline(MatchProposal match) {
        for (QueueEntry entry : match.entries()) {
            Player player = Bukkit.getPlayer(entry.player());
            if (player != null && state.isInLobby(player) && matchmaker.queueOf(entry.player()) == null) {
                matchmaker.join(entry.player(), match.mode().id(), entry.rating(), entry.joinedAtMillis());
            }
        }
    }

    /** A mode closed (its plugin was disabled): its players leave the queue. */
    private void closeQueue(String modeId) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            GameMode mode = matchmaker.queueOf(player.getUniqueId());
            if (mode != null && mode.id().equals(modeId)) {
                matchmaker.leave(player.getUniqueId());
                player.sendMessage(ChatColor.RED + "The queue for " + mode.name() + ChatColor.RED + " is closed.");
            }
        }
    }
}
