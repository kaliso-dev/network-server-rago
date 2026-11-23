package gg.rago.lobby.api;

import gg.rago.game.GameMode;
import gg.rago.game.MatchProposal;
import java.util.Collections;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
     * /spectate: the player watches the game of {@code target}, a player of one of this host's
     * games. The lobby has checked who may watch whom and counts the watcher in that game from now
     * on; the host puts them in it: invisible to its players (the spectator mode), flying, touching
     * nothing, staying near the game. They leave it with /spawn ({@link #abandon}) or at its end,
     * through {@link LobbyService#sendToLobby(Player)}; a match found in a queue they wait in takes
     * them out of it ({@link #letGo}, which lets a watcher go at once). False, the default: this host
     * has no such game, or takes no watcher.
     */
    default boolean spectate(Player watcher, Player target) {
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

    /**
     * The settings the party leader chooses in the lobby for a private game of this mode, in the
     * order of the menu (Bedwars Normal: Speed, Max Diamond...); empty, the default, when it has none.
     */
    default List<PrivateSetting> privateSettings(GameMode mode) {
        return Collections.emptyList();
    }

    /**
     * The teams of this map in this mode, in their order, each as the icon of its colour (a wool
     * named in its colour): the team menu of a private game whose players choose their teams
     * (Bedwars Normal), whose match then lists them in this order
     * ({@link MatchProposal#withChosenTeams}). Empty, the default: the lobby shows numbered teams.
     */
    default List<ItemStack> teamIcons(GameMode mode, String map) {
        return Collections.emptyList();
    }

    /** What {@link #canGather} answers. */
    enum Gathering {
        /** This host has no waiting lobby for the private games of this mode: the lobby's menus, as before. */
        NONE,
        /** Its waiting lobby is being made ready (a copy of its map): the lobby asks again each second. */
        PREPARING,
        /** A party can go to it now ({@link #gather}). */
        READY
    }

    /**
     * May a party go now to the waiting lobby of a private game of this mode (the owner's ask of
     * 23/11: the party goes there at once, and there its leader chooses the map and sets the game,
     * everyone their team)? Asking may get one ready (start the copy of its map). NONE, the default:
     * this host has none.
     */
    default Gathering canGather(GameMode mode) {
        return Gathering.NONE;
    }

    /**
     * Takes these players, a party, its leader first, to a waiting lobby of their own for their
     * private game of this mode, after {@link #canGather} said READY; the lobby made them belong to
     * this mode's game first. They stay in it until their game starts ({@link #start} takes them from
     * it to the map) or they leave it ({@link #abandon}, back to the lobby). False if it could not
     * after all (the default): they are still in the lobby.
     */
    default boolean gather(GameMode mode, List<Player> players) {
        return false;
    }
}
