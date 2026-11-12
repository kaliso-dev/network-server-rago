package gg.rago.lobby.api;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * One game being recorded (see {@link Replays#record}). The recording follows its players each
 * tick by itself; the game tells it who plays, what its chat says, and when it is over.
 * Main thread only.
 */
public interface ReplayRecording {

    /** A player of the game, in the colour of their team (their name above their head in the replay). */
    void actor(Player player, ChatColor colour);

    /** A line of the game's chat that everyone saw (a kill, a bed, a round won). */
    void chat(String line);

    /**
     * The game is over: the replay is written (away from the main thread) and kept. Returns its id,
     * for /replay, or null when there is nothing to keep (a game that never started).
     */
    String finish(String result);

    /** The game stops without a replay. */
    void cancel();
}
