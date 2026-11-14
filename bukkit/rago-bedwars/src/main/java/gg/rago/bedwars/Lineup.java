package gg.rago.bedwars;

/**
 * How many players a game in rounds is for (the invis practice, the Bed Break Simulator), one of
 * them attacking the others in turn. Its size chosen in the lobby, 1v1 to 1v4 (one team of 2 to 5
 * players in its config.yml): the players come in together, and it starts as soon as they are in,
 * with whoever is still there. Or open (a mode queued alone, as before the sizes): from 2 players to
 * the most, whoever joins while it waits, 10 seconds after the second one. Immutable.
 */
final class Lineup {

    static final int MIN_PLAYERS = 2;
    /** Open: the seconds before the start once two are in. */
    static final int WAIT_SECONDS = 10;
    /** Once full, or once the players of its size are in: the seconds before the start. */
    static final int FULL_WAIT_SECONDS = 3;

    /** The players of its size, 0 for an open game. */
    private final int size;
    /** Open: at most so many. */
    private final int max;

    /** For a match of so many players (of the lobby's mode): its size from 2, else open up to max. */
    Lineup(int playersPerMatch, int max) {
        this.size = playersPerMatch >= MIN_PLAYERS ? playersPerMatch : 0;
        this.max = max;
    }

    /** True for an open game: players join while it waits. */
    boolean open() {
        return size == 0;
    }

    /** How many players it is for: its size, or at most so many when open. */
    int capacity() {
        return open() ? max : size;
    }

    /** True if one more player can join while it waits: an open game with room. */
    boolean joinable(int present) {
        return open() && present < max;
    }

    /**
     * The seconds before the start with so many players in, -1 below two: an open game waits for
     * more, a game of a size ends (nobody else comes).
     */
    int startIn(int present) {
        if (present < MIN_PLAYERS) {
            return -1;
        }
        return !open() || present >= max ? FULL_WAIT_SECONDS : WAIT_SECONDS;
    }

    /** Its size, "1v3", or "1v1 to 1v4" when open. */
    String format() {
        return open() ? "1v" + (MIN_PLAYERS - 1) + " to 1v" + (max - 1) : "1v" + (size - 1);
    }

    /** How many play, "4 players" or "2 to 5 players". */
    String players() {
        return open() ? MIN_PLAYERS + " to " + max + " players" : size + " players";
    }

    /** When it starts, for the players waiting in it. */
    String when() {
        return open() ? "It starts " + WAIT_SECONDS + " seconds after the second player joins"
                : "It starts as soon as the " + size + " players are in";
    }
}
