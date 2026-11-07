package gg.rago.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The game modes of the network, in the order they are shown to players. Immutable. */
public final class ModeCatalog {

    private final Map<String, GameMode> modes;

    public ModeCatalog(List<GameMode> modes) {
        Map<String, GameMode> byId = new LinkedHashMap<>();
        for (GameMode mode : modes) {
            if (byId.put(mode.id(), mode) != null) {
                throw new IllegalArgumentException("mode " + mode.id() + " is listed twice");
            }
        }
        this.modes = Collections.unmodifiableMap(byId);
    }

    /** The mode with this id, or null. */
    public GameMode get(String id) {
        return modes.get(id);
    }

    public List<GameMode> all() {
        return Collections.unmodifiableList(new ArrayList<>(modes.values()));
    }

    public int size() {
        return modes.size();
    }
}
