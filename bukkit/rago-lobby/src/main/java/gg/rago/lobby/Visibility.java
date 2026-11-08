package gg.rago.lobby;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * "Players: hidden": a player in the lobby may hide everyone else. Only in the lobby: a player
 * who leaves for a game sees everyone again, and the choice comes back on returning.
 */
final class Visibility {

    /** Hiding and showing everyone is cheap, but not something to spam. */
    private static final long COOLDOWN_MILLIS = 3_000L;

    private final Set<UUID> hiding = new HashSet<>();
    private final Map<UUID, Long> lastToggle = new HashMap<>();

    boolean hides(Player player) {
        return hiding.contains(player.getUniqueId());
    }

    /** Switches the choice. Returns false (nothing changed) during the cooldown. */
    boolean toggle(Player player, long nowMillis) {
        Long last = lastToggle.get(player.getUniqueId());
        if (last != null && nowMillis - last < COOLDOWN_MILLIS) {
            return false;
        }
        lastToggle.put(player.getUniqueId(), nowMillis);
        if (!hiding.remove(player.getUniqueId())) {
            hiding.add(player.getUniqueId());
        }
        return true;
    }

    /** Applies the player's choice to what the player sees: in the lobby. */
    void applyInLobby(Player player, LobbyState state) {
        boolean hide = hides(player);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == player) {
                continue;
            }
            if (hide && state.isInLobby(other)) {
                player.hidePlayer(other);
            } else {
                player.showPlayer(other);
            }
        }
    }

    /** The player leaves the lobby for a game: sees everyone. */
    void showAll(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != player) {
                player.showPlayer(other);
            }
        }
    }

    /** Someone arrives in the lobby: hidden from those who hide players. */
    void hideFromHiders(Player arriving, LobbyState state) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != arriving && hides(other) && state.isInLobby(other)) {
                other.hidePlayer(arriving);
            }
        }
    }

    /** Someone leaves the lobby for a game: everyone in the game must see them. */
    void revealToAll(Player leaving) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != leaving) {
                other.showPlayer(leaving);
            }
        }
    }

    void forget(UUID player) {
        hiding.remove(player);
        lastToggle.remove(player);
    }
}
