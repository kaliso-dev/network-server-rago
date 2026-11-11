package gg.rago.lobby;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Where each player is, for the lobby: the lobby world and its spawn, who belongs to a game (which
 * mode, against whom), and who is building in the lobby. Main thread only.
 */
final class LobbyState {

    private final World world;
    private SpawnPoint spawn;
    private final Map<UUID, String> inGame = new HashMap<>();
    private final Map<UUID, List<UUID>> opponents = new HashMap<>();
    private final Set<UUID> building = new HashSet<>();
    /** The players of a party's private game: it counts in no stats. */
    private final Set<UUID> privateGame = new HashSet<>();
    /**
     * The players of a game started without the queue (a /duel): it counts, but never moves the
     * ranked ELO nor the win streaks, which two friends could farm against each other.
     */
    private final Set<UUID> unrated = new HashSet<>();

    LobbyState(World world, SpawnPoint spawn) {
        this.world = world;
        this.spawn = spawn;
    }

    World world() {
        return world;
    }

    Location spawn() {
        return spawn.in(world);
    }

    void setSpawn(SpawnPoint spawn) {
        this.spawn = spawn;
    }

    /** In the lobby world, not in a game. Builders count as in the lobby. */
    boolean isInLobby(Player player) {
        return !inGame.containsKey(player.getUniqueId()) && player.getWorld().equals(world);
    }

    /** In the lobby, and the lobby rules apply (not building). */
    boolean isProtected(Player player) {
        return isInLobby(player) && !building.contains(player.getUniqueId());
    }

    boolean isInGame(Player player) {
        return inGame.containsKey(player.getUniqueId());
    }

    /** The mode of the player's game, or null. */
    String gameOf(UUID player) {
        return inGame.get(player);
    }

    void enterGame(UUID player, String modeId) {
        inGame.put(player, modeId);
        building.remove(player);
    }

    void leaveGame(UUID player) {
        inGame.remove(player);
        opponents.remove(player);
        privateGame.remove(player);
        unrated.remove(player);
    }

    /** The player's game is a party's private game: it counts in no stats. */
    void markPrivate(UUID player) {
        if (inGame.containsKey(player)) {
            privateGame.add(player);
        }
    }

    boolean isInPrivateGame(UUID player) {
        return privateGame.contains(player);
    }

    /** The player's game started without the queue (a /duel): no ELO, no win streak. */
    void markUnrated(UUID player) {
        if (inGame.containsKey(player)) {
            unrated.add(player);
        }
    }

    boolean isUnrated(UUID player) {
        return unrated.contains(player);
    }

    /** The players of the other teams of the player's game, for the sidebar. */
    void setOpponents(UUID player, List<UUID> others) {
        opponents.put(player, Collections.unmodifiableList(others));
    }

    /** The players of the other teams of the player's game; empty out of a game. */
    List<UUID> opponentsOf(UUID player) {
        List<UUID> others = inGame.containsKey(player) ? opponents.get(player) : null;
        return others == null ? Collections.<UUID>emptyList() : others;
    }

    boolean isBuilding(Player player) {
        return building.contains(player.getUniqueId());
    }

    /** Switches the build mode. Returns true if the player now builds. */
    boolean toggleBuilding(UUID player) {
        if (building.remove(player)) {
            return false;
        }
        building.add(player);
        return true;
    }

    void forget(UUID player) {
        inGame.remove(player);
        opponents.remove(player);
        building.remove(player);
        privateGame.remove(player);
        unrated.remove(player);
    }
}
