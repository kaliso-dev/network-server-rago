package gg.rago.lobby;

import java.util.Collections;
import java.util.List;

/**
 * What stands on a lobby point (config.yml, pnj: and classements:): a mode NPC that opens its
 * modes or a whole category, or a leaderboard. Immutable.
 */
final class LobbyNpc {

    private final String point;
    private final String name;
    private final List<String> modeIds;
    private final String category;

    LobbyNpc(String point, String name, List<String> modeIds, String category) {
        this.point = point;
        this.name = name;
        this.modeIds = Collections.unmodifiableList(modeIds);
        this.category = category;
    }

    /** The category whose menu it opens, or null for an NPC of its own modes. */
    String category() {
        return category;
    }

    /** The /lobby point it stands on. */
    String point() {
        return point;
    }

    /** Coloured. */
    String name() {
        return name;
    }

    /** The modes it opens, in order; empty for a leaderboard. */
    List<String> modeIds() {
        return modeIds;
    }

    boolean leaderboard() {
        return modeIds.isEmpty();
    }
}
