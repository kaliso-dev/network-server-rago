package gg.rago.lobby.api;

import gg.rago.game.GameMode;
import gg.rago.game.MatchProposal;
import java.util.Collections;
import java.util.List;
import org.bukkit.entity.Player;

/**
 * What a game plugin gives the lobby for each mode it plays. The lobby calls it on the main
 * thread.
 */
public interface MatchHost {

    /**
     * Starts this match: the players are online and in the lobby. Returns false when it cannot
     * start now (no free arena, for example): the players then go back to the queue, at their place.
     * After true, the players belong to the game until it gives them back with
     * {@link LobbyService#sendToLobby(Player)}.
     */
    boolean start(MatchProposal match);

    /**
     * The player asked to go back to the lobby in the middle of the game (/spawn, /lobby). The
     * host ends the player's part (a forfeit) and calls {@link LobbyService#sendToLobby(Player)}.
     */
    void abandon(Player player);

    /**
     * The maps a player may choose from when joining this mode's queue, in the order the menu shows
     * them. With two or more, the selector asks which one (or any); the match then carries the
     * choice ({@link MatchProposal#map()}). Empty, the default: no choice, the host picks the map.
     */
    default List<String> maps(GameMode mode) {
        return Collections.emptyList();
    }
}
