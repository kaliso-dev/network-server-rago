package gg.rago.lobby;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Who sees whom: everyone sees everyone, but a vanished member of the staff stays hidden from
 * everyone but the staff, in the lobby and in the games.
 */
final class Visibility {

    /** Who is vanished. */
    interface Vanish {
        boolean vanished(UUID player);
    }

    private Vanish vanish = player -> false;

    void setVanish(Vanish vanish) {
        this.vanish = vanish;
    }

    /** False for a vanished member of the staff, to a viewer outside the staff. */
    boolean canSee(Player viewer, Player target) {
        return viewer == target || !vanish.vanished(target.getUniqueId()) || viewer.hasPermission("rago.staff");
    }

    private void show(Player viewer, Player target) {
        if (canSee(viewer, target)) {
            viewer.showPlayer(target);
        } else {
            viewer.hidePlayer(target);
        }
    }

    /** The player vanished, came back, or joined: the others see them or not, and they see the vanished staff or not. */
    void applyVanish(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == player) {
                continue;
            }
            show(other, player);
            if (!canSee(player, other)) {
                player.hidePlayer(other);
            }
        }
    }

    /** The player arrives in the lobby or leaves it for a game: sees everyone (but the vanished staff). */
    void showAll(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != player) {
                show(player, other);
            }
        }
    }

    /** Someone leaves the lobby for a game: everyone in the game must see them. */
    void revealToAll(Player leaving) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != leaving) {
                show(other, leaving);
            }
        }
    }
}
