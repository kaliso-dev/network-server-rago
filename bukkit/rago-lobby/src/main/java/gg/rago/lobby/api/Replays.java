package gg.rago.lobby.api;

import org.bukkit.plugin.Plugin;

/**
 * The replays of the games: a game plugin records each game it plays, and /replay shows it again
 * to a player of the lobby (the players, their hits, their blocks and projectiles, the game's
 * messages). Main thread only.
 */
public interface Replays {

    /** Starts recording a game; null when the replays are off. */
    ReplayRecording record(ReplaySetup setup);

    /**
     * The plugin makes the worlds of this kind of stage for the replays of its games (Bed Wars: a
     * copy of a map, see {@link ReplaySetup#ofStage}).
     */
    void registerStages(Plugin plugin, String kind, ReplayStages stages);

    /** The plugin stops: its stages go, and the replays shown in them end. */
    void unregisterStages(Plugin plugin);
}
