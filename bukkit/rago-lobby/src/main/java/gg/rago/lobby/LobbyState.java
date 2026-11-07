package gg.rago.lobby;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Where each player is, for the lobby: the lobby world and its spawn, who belongs to a game (and
 * which mode), and who is building in the lobby. Main thread only.
 */
final class LobbyState {

    private final World world;
    private SpawnPoint spawn;
    private final Map<UUID, String> inGame = new HashMap<>();
    private final Set<UUID> building = new HashSet<>();

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
        building.remove(player);
    }
}
