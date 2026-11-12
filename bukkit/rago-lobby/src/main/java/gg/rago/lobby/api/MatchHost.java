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
     * False while this match cannot start yet but soon will (a copy of its map is being made): the
     * lobby keeps the players where they are and asks again each second, before calling
     * {@link #start}. Asking may get the room ready (start the copy). True by default.
     */
    default boolean ready(MatchProposal match) {
        return true;
    }

    /**
     * The player asked to go back to the lobby in the middle of the game (/spawn, /lobby). The
     * host ends the player's part (a forfeit) and calls {@link LobbyService#sendToLobby(Player)}.
     */
    void abandon(Player player);

    /**
     * The player goes from their game straight into a game of another mode (from a training to
     * another, with the training's Trainings menu, or into the match found in a queue they wait in
     * while they train): the host lets them go at once, without a word and without sending them to
     * the lobby, and returns true. False, the default, keeps them in the game.
     */
    default boolean letGo(Player player) {
        return false;
    }

    /**
     * True if {@link #letGo} would let this player go now (a training): they may then wait in the
     * queues while they play, the match found takes them out of the game. False, the default: a
     * player who starts a game of this mode leaves the queues they waited in.
     */
    default boolean canLetGo(Player player) {
        return false;
    }

    /**
     * The maps a player may choose from when joining this mode's queue, in the order the menu shows
     * them. With two or more, the selector asks which one (or any); the match then carries the
     * choice ({@link MatchProposal#map()}). Empty, the default: no choice, the host picks the map.
     */
    default List<String> maps(GameMode mode) {
        return Collections.emptyList();
    }
}
