package gg.rago.bedwars;

import gg.rago.lobby.api.ReplayStages;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * The worlds of the replays of the Bed Wars games (/replay of the lobby): a free copy of the game's
 * map, taken as a game takes one, and given back to the games when the replay is over. The replay
 * puts its blocks back as the copy had them before it gives it back. Main thread only.
 */
final class ReplayWorlds implements ReplayStages {

    /** The kind of stage of the Bed Wars replays. */
    static final String KIND = "bedwars";
    /** How long a replay waits for a copy of its map to be free or made, in seconds. */
    static final int WAIT_SECONDS = 30;

    private final RagoBedwars plugin;

    ReplayWorlds(RagoBedwars plugin) {
        this.plugin = plugin;
    }

    @Override
    public void prepare(String key, final Consumer<World> ready, final Consumer<String> failed) {
        BedwarsMap map = plugin.maps().get(key);
        if (map == null || !map.playable()) {
            failed.accept("The map of this game is no longer on the server.");
            return;
        }
        final List<BedwarsMap> one = Collections.singletonList(map);
        Instance free = plugin.instances().acquire(one);
        if (free != null) {
            ready.accept(free.world());
            return;
        }
        // a copy is made, if there is room for one more game world: the replay waits for it
        plugin.instances().prepare(one, false);
        new BukkitRunnable() {
            private int waited;

            @Override
            public void run() {
                Instance instance = plugin.instances().acquire(one);
                if (instance != null) {
                    cancel();
                    ready.accept(instance.world());
                } else if (++waited >= WAIT_SECONDS) {
                    cancel();
                    failed.accept("Every copy of this map is in a game: try again in a minute.");
                } else {
                    plugin.instances().prepare(one, false);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @Override
    public void release(World world) {
        Instance instance = plugin.instances().of(world);
        if (instance != null && instance.busy()) {
            plugin.instances().release(instance);
        }
    }
}
