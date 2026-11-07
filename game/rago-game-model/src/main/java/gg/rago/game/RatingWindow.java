package gg.rago.game;

/**
 * How far apart two ratings may be for a ranked match, as a function of how long a player has
 * waited: narrow at first for fair games, wider with time so that a quiet queue still finds a game.
 * Immutable.
 */
public final class RatingWindow {

    /** 100 points at first, 50 more every 10 seconds, 500 at most. */
    public static final RatingWindow DEFAULT = new RatingWindow(100, 50, 10, 500);

    private final int initial;
    private final int step;
    private final int stepSeconds;
    private final int maximum;

    public RatingWindow(int initial, int step, int stepSeconds, int maximum) {
        if (initial < 0 || step < 0 || stepSeconds < 1 || maximum < initial) {
            throw new IllegalArgumentException("invalid rating window " + initial + "/" + step + "/"
                    + stepSeconds + "s/" + maximum);
        }
        this.initial = initial;
        this.step = step;
        this.stepSeconds = stepSeconds;
        this.maximum = maximum;
    }

    /** The largest rating difference a player who waited this long accepts. */
    public int width(long waitedMillis) {
        long steps = Math.max(0L, waitedMillis) / (stepSeconds * 1000L);
        long width = initial + steps * step;
        return (int) Math.min(width, maximum);
    }
}
