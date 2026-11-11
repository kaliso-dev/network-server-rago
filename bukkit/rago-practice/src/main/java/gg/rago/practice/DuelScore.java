package gg.rago.practice;

/**
 * The score of a duel between two sides, 0 and 1: first to {@code target} points wins. Pure Java,
 * main thread.
 */
final class DuelScore {

    private final int target;
    private final int[] points = new int[2];

    DuelScore(int target) {
        if (target < 1) {
            throw new IllegalArgumentException("at least one point to win");
        }
        this.target = target;
    }

    /** Gives a point to a side. Returns true if that point wins the duel. */
    boolean point(int side) {
        if (isOver()) {
            throw new IllegalStateException("the duel is over");
        }
        points[side]++;
        return points[side] >= target;
    }

    int points(int side) {
        return points[side];
    }

    int target() {
        return target;
    }

    boolean isOver() {
        return points[0] >= target || points[1] >= target;
    }

    /** "2 - 1" from the point of view of this side. */
    String text(int side) {
        return points[side] + " - " + points[1 - side];
    }
}
