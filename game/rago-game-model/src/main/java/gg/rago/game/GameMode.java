package gg.rago.game;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One game mode players can queue for: its id (used in configs, commands and the database), its
 * name shown to players, how many teams of how many players a match has, and whether it is ranked.
 * Immutable.
 */
public final class GameMode {

    /** Lowercase letters without accents, digits and {@code -}, at most 32 characters. */
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");

    /** Upper bound on the players of one match: a 1.8.8 arena slot is sized for small games. */
    public static final int MAX_PLAYERS = 16;

    private final String id;
    private final String name;
    private final int teams;
    private final int playersPerTeam;
    private final boolean ranked;

    public GameMode(String id, String name, int teams, int playersPerTeam, boolean ranked) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("mode id \"" + id
                    + "\": only lowercase letters, digits and '-', at most 32 characters");
        }
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("mode " + id + ": empty name");
        }
        if (teams < 1 || playersPerTeam < 1) {
            throw new IllegalArgumentException("mode " + id + ": at least one team of one player");
        }
        if ((long) teams * playersPerTeam > MAX_PLAYERS) {
            throw new IllegalArgumentException("mode " + id + ": more than " + MAX_PLAYERS + " players per match");
        }
        if (ranked && teams < 2) {
            throw new IllegalArgumentException("mode " + id + ": a ranked mode needs at least two teams");
        }
        this.id = id;
        this.name = name;
        this.teams = teams;
        this.playersPerTeam = playersPerTeam;
        this.ranked = ranked;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int teams() {
        return teams;
    }

    public int playersPerTeam() {
        return playersPerTeam;
    }

    public boolean ranked() {
        return ranked;
    }

    /** Players needed to start one match. */
    public int playersPerMatch() {
        return teams * playersPerTeam;
    }

    /** "1v1", "2v2", "1v1v1v1", or "solo" for a mode played alone. */
    public String format() {
        if (teams == 1) {
            return playersPerTeam == 1 ? "solo" : playersPerTeam + " joueurs";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < teams; i++) {
            if (i > 0) {
                text.append('v');
            }
            text.append(playersPerTeam);
        }
        return text.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GameMode && ((GameMode) other).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
