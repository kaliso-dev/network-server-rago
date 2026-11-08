package gg.rago.lobby.api;

import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The lobby as the game plugins see it, from Bukkit's services manager:
 * {@code getServer().getServicesManager().load(LobbyService.class)}. Main thread only.
 *
 * <p>On the local test server the lobby and the games run on one server and talk through this
 * interface. On the network the queues move to the proxy (rago-proxy) and the games receive their
 * matches from it; the {@link MatchHost} side of a game plugin stays the same.
 */
public interface LobbyService {

    /**
     * Makes a mode of the lobby's catalogue playable: its queue opens in the selector. Returns
     * false if the catalogue has no such mode (see the lobby's config.yml). A mode has one host:
     * a second registration replaces the first.
     */
    boolean registerHost(Plugin plugin, String modeId, MatchHost host);

    /** Closes the queues of every mode this plugin hosts, when it is disabled. */
    void unregisterHosts(Plugin plugin);

    /**
     * Brings the player back to the lobby spawn, as on joining the server: lobby items, adventure
     * mode, full health, the lobby scoreboard. The player no longer belongs to a game.
     */
    void sendToLobby(Player player);

    /** True while the player belongs to a game started by a {@link MatchHost}. */
    boolean isInGame(Player player);

    /**
     * A game of this mode is over: one side won, the other lost (a forfeit is a loss). The lobby
     * counts it in the stats, the leaderboards and, for a ranked mode, the ELO. Players may have
     * left the server already.
     */
    void recordMatch(String modeId, List<UUID> winners, List<UUID> losers);

    /**
     * A kill in a game of this mode (in a sumo: the victim fell off and the killer got the point).
     * The killer gets a kill and some Network XP, the victim a death (null when nobody died).
     * Counted in the stats and the kill leaderboards, of the day, the week and ever.
     */
    void recordKill(String modeId, UUID killer, UUID victim);
}
