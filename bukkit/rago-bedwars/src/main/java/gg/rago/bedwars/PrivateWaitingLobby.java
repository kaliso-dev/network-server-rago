package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * The waiting lobby of a private game of Bedwars Normal (the owner's ask of 23/11: « ça doit
 * directement TP tous les joueurs au lobby d'attente bedwars de base et c'est là-bas que le host doit
 * faire les settings de la map et que les joueurs puissent choisir leur équipe »; since the owner's
 * ask of 25/11, the map chosen first, in the lobby: « dès qu'il choisit la map ça le tp lui et les
 * membres de la party sur le lobby d'attente au-dessus de la map qu'il a choisie »): the party in the
 * waiting lobby (salle-attente:) of a copy of its own of the map chosen (or of the map of
 * salle-attente-privee, for a map without one), while its leader sets the game and everyone chooses
 * their team, with the items and the menus of the lobby. Nobody is hurt or hungry there, nobody
 * builds (adventure mode); who falls off the lobby is back in its middle. It is over when the last
 * of them is gone: to their game (on this same copy when it is the map of the game), or back to the
 * lobby; its copy is then free again, unless their game plays on it. Main thread only.
 */
final class PrivateWaitingLobby {

    private final Instance instance;
    private final LobbyFight lobby;
    private final Set<UUID> players = new LinkedHashSet<>();

    PrivateWaitingLobby(Instance instance) {
        this.instance = instance;
        this.lobby = new LobbyFight(instance.map().waitingLobby(), instance.world());
    }

    Instance instance() {
        return instance;
    }

    /** The players in it now, in the order they came. */
    List<UUID> players() {
        return new ArrayList<>(players);
    }

    boolean has(UUID player) {
        return players.contains(player);
    }

    /** The player comes in: in the middle of the floor, nothing in hand, in adventure mode, hearts and food full. */
    void welcome(Player player) {
        players.add(player.getUniqueId());
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        BedwarsGame.heal(player);
        player.setLevel(0);
        player.setExp(0f);
        player.teleport(lobby.middle());
    }

    /** The player is gone (to their game, the lobby, off the server). True if they were in it. */
    boolean remove(UUID player) {
        return players.remove(player);
    }

    boolean empty() {
        return players.isEmpty();
    }

    /** Under this height a player has fallen off the lobby. */
    double fallLine() {
        return lobby.fallLine();
    }

    /** Where a player who fell off comes back: the middle of the floor. */
    Location middle() {
        return lobby.middle();
    }
}
