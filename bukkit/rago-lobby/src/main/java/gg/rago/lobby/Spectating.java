package gg.rago.lobby;

import gg.rago.game.GameMode;
import gg.rago.lobby.api.MatchHost;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * /spectate: watching the game of a friend; the staff (a check for cheating) and the content
 * creators, any game. The watcher is invisible to its players (the spectator mode), flies through
 * it and touches nothing, until /spawn or the end of the game; a left click on a player shows the
 * game through their eyes. The lobby checks who may watch whom and counts the watcher in the game
 * (a queue they wait in keeps their place: the match found takes them out of it); the game's host
 * puts them in it ({@link MatchHost#spectate}) and gives them back as from a game, without a Play
 * again. Main thread only.
 */
final class Spectating {

    /** Watching any game, not only a friend's: the staff has it, a rank can give it (the content creators). */
    static final String ANY_GAME = "rago.perk.spectate";

    private final RagoLobby lobby;

    Spectating(RagoLobby lobby) {
        this.lobby = lobby;
    }

    /**
     * True if a player may watch the game of another: the staff any game; a friend any game of
     * theirs; a content creator any game, but not one of a player under a nick (who they are stays
     * hidden but from the staff and their friends).
     */
    static boolean mayWatch(boolean staff, boolean anyGame, boolean friend, boolean targetNicked) {
        return staff || friend || anyGame && !targetNicked;
    }

    /** /spectate [player]. */
    void command(Player watcher, String[] args) {
        if (args.length != 1) {
            watcher.sendMessage(ChatColor.RED + "Usage: /spectate <player>");
            return;
        }
        LobbyState state = lobby.state();
        if (state.isInGame(watcher) || !state.isInLobby(watcher)) {
            watcher.sendMessage(ChatColor.RED + (state.isWatching(watcher.getUniqueId()) ? "You are already spectating a game: /spawn first."
                    : "Go back to the lobby first: /spawn"));
            return;
        }
        if (lobby.isFrozen(watcher.getUniqueId())) {
            watcher.sendMessage(ChatColor.RED + "You are frozen by the staff.");
            return;
        }
        Player target = lobby.social().visible(watcher, args[0]);
        if (target == null) {
            watcher.sendMessage(ChatColor.RED + "Can't find a player by the name of '" + args[0] + "'");
            return;
        }
        if (target.equals(watcher)) {
            watcher.sendMessage(ChatColor.RED + "You cannot spectate yourself.");
            return;
        }
        UUID id = target.getUniqueId();
        boolean staff = watcher.hasPermission("rago.staff");
        boolean nicked = lobby.profiles().names().nick(id) != null;
        if (!mayWatch(staff, watcher.hasPermission(ANY_GAME), lobby.social().friends(id, watcher.getUniqueId()), nicked)) {
            watcher.sendMessage(ChatColor.RED + "You can only spectate your friends' games.");
            return;
        }
        String modeId = state.gameOf(id);
        MatchHost host = modeId == null ? null : lobby.queues().hostOf(modeId);
        if (host == null || state.isWatching(id)) {
            watcher.sendMessage(ChatColor.RED + target.getName() + " is not in a game.");
            return;
        }
        lobby.enterWatch(watcher, modeId, target);
        boolean taken;
        try {
            taken = host.spectate(watcher, target);
        } catch (RuntimeException e) {
            lobby.getLogger().log(Level.WARNING, "The mode " + modeId + " could not take a spectator", e);
            taken = false;
        }
        if (!taken) {
            lobby.cancelGame(watcher);
            watcher.sendMessage(ChatColor.RED + "This game cannot be spectated.");
            return;
        }
        watcher.sendMessage(ChatColor.GREEN + "Spectating " + target.getName() + ChatColor.GREEN + ": the players do not see you. "
                + ChatColor.GRAY + "Left click a player to see through their eyes, /spawn to leave.");
        List<GameMode> waiting = lobby.queues().queuesOf(watcher);
        if (!waiting.isEmpty()) {
            watcher.sendMessage(ChatColor.GRAY + "You keep your place in the " + QueueController.queuesFor(waiting, ChatColor.GRAY)
                    + ": the game found takes you out of the game you watch.");
        }
        lobby.getLogger().info(watcher.getName() + " spectates " + target.getName() + " (" + modeId + ")");
    }
}
