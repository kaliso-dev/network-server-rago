package gg.rago.lobby;

import java.util.Collections;
import java.util.List;

/**
 * What stands on a lobby point (config.yml, pnj: and classements:): a mode NPC that opens its
 * modes or a whole category, a stats NPC that shows each player their own stats, or a wall of
 * leaderboards. Immutable.
 */
final class LobbyNpc {

    enum Kind {
        /** Opens its modes, or the menu of its category. */
        MODES,
        /** Shows each player their stats above it; a click opens them mode by mode. */
        STATS,
        /** A wall of leaderboards. */
        LEADERBOARDS
    }

    private final String point;
    private final String name;
    private final Kind kind;
    private final List<String> modeIds;
    private final String category;
    private final List<List<BoardStat>> boards;
    private final List<String> podium;

    private LobbyNpc(String point, String name, Kind kind, List<String> modeIds, String category,
            List<List<BoardStat>> boards, List<String> podium) {
        this.point = point;
        this.name = name;
        this.kind = kind;
        this.modeIds = Collections.unmodifiableList(modeIds);
        this.category = category;
        this.boards = Collections.unmodifiableList(boards);
        this.podium = Collections.unmodifiableList(podium);
    }

    /** An NPC of these modes, or of a whole category (then modeIds are the category's modes). */
    static LobbyNpc modes(String point, String name, List<String> modeIds, String category) {
        return new LobbyNpc(point, name, Kind.MODES, modeIds, category, Collections.<List<BoardStat>>emptyList(),
                Collections.<String>emptyList());
    }

    static LobbyNpc stats(String point, String name) {
        return new LobbyNpc(point, name, Kind.STATS, Collections.<String>emptyList(), null,
                Collections.<List<BoardStat>>emptyList(), Collections.<String>emptyList());
    }

    /** A wall of boards, left to right; each board shows its first stat, a right click the next one. */
    static LobbyNpc leaderboards(String point, String name, List<List<BoardStat>> boards) {
        return leaderboards(point, name, boards, Collections.<String>emptyList());
    }

    /**
     * A wall with a podium: on its steps (first place first), the players at the top of its first
     * board. A step is a /lobby point or the coordinates "x y z" of the feet of who stands on it.
     */
    static LobbyNpc leaderboards(String point, String name, List<List<BoardStat>> boards, List<String> podium) {
        return new LobbyNpc(point, name, Kind.LEADERBOARDS, Collections.<String>emptyList(), null, boards, podium);
    }

    /** The coordinates of a step written "x y z", or null for a point name or anything else. */
    static double[] coordinates(String step) {
        String[] parts = step.trim().split("\\s+");
        if (parts.length != 3) {
            return null;
        }
        double[] xyz = new double[3];
        try {
            for (int i = 0; i < 3; i++) {
                xyz[i] = Double.parseDouble(parts[i]);
                if (Double.isNaN(xyz[i]) || Double.isInfinite(xyz[i])) {
                    return null;
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return xyz;
    }

    Kind kind() {
        return kind;
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

    /** The modes it opens, in order; empty for a stats NPC or a leaderboard wall. */
    List<String> modeIds() {
        return modeIds;
    }

    /** The leaderboards of a wall, left to right, each with its stats; empty otherwise. */
    List<List<BoardStat>> boards() {
        return boards;
    }

    /** The steps of the podium of a wall, first place first: point names or "x y z"; often none. */
    List<String> podium() {
        return podium;
    }

    boolean leaderboard() {
        return kind == Kind.LEADERBOARDS;
    }

    /** Stands as a player (a mode or stats NPC), not as floating text. */
    boolean hasBody() {
        return kind != Kind.LEADERBOARDS;
    }
}
