package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * What comes after a game, as on Hypixel and Minemen: back in the lobby, [PLAY AGAIN] and the
 * Play again paper of the hotbar queue the same mode again, and after a 1v1 that is not ranked,
 * [REMATCH] challenges the same opponent to a
 * duel, and [REPLAY] shows the game again when it was recorded (/replay). A player with "Play
 * again" on in /settings is put back in the queue at once (not after leaving with /spawn). Nothing
 * for a mode hidden from the selector (the events) or closed; in a party, only its leader queues.
 * Main thread only.
 */
final class NextGame {

    private final RagoLobby lobby;

    NextGame(RagoLobby lobby) {
        this.lobby = lobby;
    }

    /**
     * The player is back in the lobby from a game of this mode, against these players (none in a
     * training); {@code left}: they left it themselves (/spawn), no queue on their own then.
     */
    void offer(Player player, String modeId, List<UUID> opponents, boolean left) {
        ModeDisplay display = lobby.settings().display(modeId);
        if (display == null || display.hidden() || !player.isOnline() || !lobby.state().isInLobby(player)
                || !lobby.queues().isPlayable(modeId) || lobby.queues().queueOf(player) != null) {
            return;
        }
        GameMode mode = display.mode();
        PartyBook.Party party = lobby.parties().book().together(player.getUniqueId());
        boolean queues = party == null || party.isLeader(player.getUniqueId());
        String rematch = null;
        if (!mode.ranked() && DuelRequests.isDuelMode(mode) && opponents.size() == 1) {
            Player opponent = Bukkit.getPlayer(opponents.get(0));
            if (opponent != null && opponent.isOnline()) {
                rematch = opponent.getName();
            }
        }
        if (queues) {
            // the Play again paper in the hotbar, as the [PLAY AGAIN] line
            lobby.offerPlayAgain(player, mode.id());
        }
        PlayerStats profile = lobby.profiles().of(player);
        if (queues && !left && profile != null && profile.autoRequeue()) {
            lobby.queues().join(player, mode);
            queues = false;
        }
        ReplayService replays = lobby.replayService();
        String replay = replays == null ? null : replays.recentOf(player.getUniqueId(), mode.id());
        String json = json(mode.id(), queues, ChatColor.stripColor(mode.name()), rematch, replay);
        if (json != null) {
            // clickable through the vanilla /tellraw, as the duel buttons: names are letters, digits and _
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + json);
        }
    }

    /**
     * The JSON of the line for a game of the mode {@code modeId}: [PLAY AGAIN] when {@code playAgain},
     * [REMATCH] in the same mode when {@code rematch} (the opponent's name) is set; null with neither.
     */
    static String json(String modeId, boolean playAgain, String modeName, String rematch) {
        return json(modeId, playAgain, modeName, rematch, null);
    }

    /** The same, with [REPLAY] last when the game was recorded ({@code replay}: its code). */
    static String json(String modeId, boolean playAgain, String modeName, String rematch, String replay) {
        if (!playAgain && rematch == null && replay == null) {
            return null;
        }
        StringBuilder json = new StringBuilder("[\"\"");
        boolean first = true;
        if (playAgain) {
            json.append(',').append(button("[PLAY AGAIN]", "green", "/queue " + modeId, "Queue " + modeName + " again"));
            first = false;
        }
        if (rematch != null) {
            if (!first) {
                json.append(",{\"text\":\"   \"}");
            }
            json.append(',').append(button("[REMATCH]", "gold", "/duel " + rematch + " " + modeId,
                    "Challenge " + rematch + " to another " + modeName));
            first = false;
        }
        if (replay != null) {
            if (!first) {
                json.append(",{\"text\":\"   \"}");
            }
            json.append(',').append(button("[REPLAY]", "aqua", "/replay " + replay, "Watch this game again"));
        }
        return json.append(']').toString();
    }

    private static String button(String text, String colour, String command, String hover) {
        return "{\"text\":\"" + text + "\",\"color\":\"" + colour + "\",\"bold\":true,"
                + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" + escape(command) + "\"},"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"" + escape(hover) + "\"}}";
    }

    /** A JSON string's inside: quotes and backslashes escaped, control characters dropped. */
    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c >= ' ') {
                out.append(c);
            }
        }
        return out.toString();
    }
}
