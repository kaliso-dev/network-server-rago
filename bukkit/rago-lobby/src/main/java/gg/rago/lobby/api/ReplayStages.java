package gg.rago.lobby.api;

import java.util.function.Consumer;
import org.bukkit.World;

/**
 * The worlds a game plugin makes for the replays of its games: a world holding the map as at the
 * start of a game, used by one replay at a time, the positions of the replay being its own.
 * Main thread only.
 */
public interface ReplayStages {

    /**
     * Makes a world of this map ready, now or later, and gives it to {@code ready}; or says why it
     * cannot to {@code failed} (in the player's language: English). Exactly one of them is called,
     * on the main thread.
     */
    void prepare(String key, Consumer<World> ready, Consumer<String> failed);

    /** The replay is over: the world is the plugin's again, its blocks as {@code ready} gave it. */
    void release(World world);
}
