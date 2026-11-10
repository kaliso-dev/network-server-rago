package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The map of the next "any map" game of each mode, drawn at random among the mode's maps. It is
 * drawn in advance, so that its copy can be made before the match is found, and drawn again (another
 * map when the mode has several) each time a game takes it: a mode does not keep playing the map
 * whose copies are already made. Main thread only.
 */
final class MapDraw {

    private final Random random;
    /** Mode id: the world of its next map. */
    private final Map<String, String> next = new HashMap<>();

    MapDraw(Random random) {
        this.random = random;
    }

    /** The world of the next map of this mode, among these worlds (drawn now if none is, or if it left the list). */
    String next(String modeId, List<String> worlds) {
        if (worlds.isEmpty()) {
            return null;
        }
        String drawn = next.get(modeId);
        if (drawn != null) {
            for (String world : worlds) {
                if (world.equalsIgnoreCase(drawn)) {
                    return world;
                }
            }
        }
        drawn = worlds.get(random.nextInt(worlds.size()));
        next.put(modeId, drawn);
        return drawn;
    }

    /** A game of this mode took this map: the next one is drawn among the others (this one again if it is alone). */
    String played(String modeId, String world, List<String> worlds) {
        List<String> others = new ArrayList<>();
        for (String candidate : worlds) {
            if (!candidate.equalsIgnoreCase(world)) {
                others.add(candidate);
            }
        }
        if (others.isEmpty()) {
            next.remove(modeId);
            return next(modeId, worlds);
        }
        String drawn = others.get(random.nextInt(others.size()));
        next.put(modeId, drawn);
        return drawn;
    }
}
