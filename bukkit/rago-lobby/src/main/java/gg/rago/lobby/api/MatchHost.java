package gg.rago.lobby.api;

import gg.rago.game.MatchProposal;
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
}
