package gg.rago.practice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The measures of Find Your Sensitivity, without Bukkit: the sensitivities played around the
 * player's own, how a flick went (an overshoot, short of the target, clean) and its corrections, a
 * series summed up, the most precise series and which way to go from it.
 *
 * <p>The numbers here (the angles, the time a target waits, the 20% of turn speed between the
 * series, how the series are compared) are starting values of the proposal of step 7, to confirm
 * with the owner.
 */
final class Sensitivity {

    /** The sensitivity slider of the 1.8.9 options, in %: 0 ("*yawn*") to 200 ("HYPERSPEED!!!"); 100 by default. */
    static final int MIN = 0;
    static final int MAX = 200;
    static final int DEFAULT = 100;
    /** The two other series turn this much slower and faster than the player's own (to confirm). */
    static final double SPEED_STEP = 0.2;
    /** The flicks of a series, in degrees from the crosshair (to confirm); then the 180° turns. */
    static final double[] FLICKS = {35, -35, 45, -45, 60, -60, 75, -75, 90, -90, 120, -120};
    static final int TURNS = 4;
    static final double TURN = 180;
    /** A target not clicked in this time counts as missed, with this time (to confirm). */
    static final long TIMEOUT_MS = 3000L;
    /** A turn of the look in one tick: more than this is a move, this or less a stop (degrees). */
    static final double MOVE_DEGREES = 1.0;
    static final double STOP_DEGREES = 0.5;
    /** A 180° turn goes the way the player turns first, once they have turned this much. */
    static final double TURN_START_DEGREES = 10;

    static final char OVERSHOOT = 'O';
    static final char SHORT = 'S';

    private Sensitivity() {
    }

    /**
     * The turn of the look for one count of the mouse at this sensitivity (%), in degrees: as the
     * 1.8.9 client computes it (f = slider * 0.6 + 0.2, then f³ * 8, then * 0.15 per count).
     */
    static double degreesPerCount(int percent) {
        double f = percent / 200.0 * 0.6 + 0.2;
        return f * f * f * 8 * 0.15;
    }

    /** The sensitivity (%) of this turn per count, rounded, from 0 to 200. */
    static int percentOf(double degreesPerCount) {
        double f = Math.cbrt(Math.max(0, degreesPerCount) / 1.2);
        long percent = Math.round((f - 0.2) / 0.6 * 200);
        return (int) Math.max(MIN, Math.min(MAX, percent));
    }

    /**
     * The sensitivities of the series: the player's own, then one that turns 20% slower, then one
     * 20% faster (on the client's curve, not 20 points), each one another than the ones before.
     */
    static List<Integer> series(int own) {
        int start = Math.max(MIN, Math.min(MAX, own));
        List<Integer> series = new ArrayList<>();
        series.add(start);
        int lower = Math.min(start - 1, percentOf(degreesPerCount(start) * (1 - SPEED_STEP)));
        int higher = Math.max(start + 1, percentOf(degreesPerCount(start) * (1 + SPEED_STEP)));
        for (int percent : new int[] {lower, higher}) {
            if (percent >= MIN && percent <= MAX && !series.contains(percent)) {
                series.add(percent);
            }
        }
        return series;
    }

    /** The targets of a series in their order: the flicks and the turns, mixed by the seed of the test. */
    static List<Double> order(long seed) {
        List<Double> order = new ArrayList<>();
        for (double angle : FLICKS) {
            order.add(angle);
        }
        for (int i = 0; i < TURNS; i++) {
            order.add(TURN);
        }
        Collections.shuffle(order, new Random(seed));
        return order;
    }

    /** An angle between -180 and 180 degrees. */
    static double wrap(double degrees) {
        double wrapped = degrees % 360;
        if (wrapped > 180) {
            wrapped -= 360;
        } else if (wrapped <= -180) {
            wrapped += 360;
        }
        return wrapped;
    }

    /** Half the angle a target this wide (half its width) takes this far away, in degrees. */
    static double halfAngle(double halfWidth, double distance) {
        return Math.toDegrees(Math.atan(halfWidth / distance));
    }

    /**
     * A flick, tick after tick: how far the look turned toward the target, whether it went past its
     * far edge (an overshoot) or first stopped before its near edge (short), and how many times it
     * turned back (the corrections).
     */
    static final class Flick {
        /** How far the target is from the look at its start (degrees), each side of its centre the half it takes. */
        final double angle;
        final double size;
        /** The side of the target: 1 (yaw going up) or -1; 0 for a 180° turn not begun yet. */
        int side;
        private double travelled;
        private double before;
        private boolean moved;
        private boolean stopped;
        private boolean reached;
        private int lastDirection;
        /** The first mistake: OVERSHOOT, SHORT, or 0 for a clean flick. */
        char first;
        int reversals;

        Flick(double angle, double size) {
            this.angle = Math.abs(angle);
            this.size = size;
            this.side = this.angle >= TURN ? 0 : angle > 0 ? 1 : -1;
        }

        /** The look turned by this much (yaw, degrees) since the tick before. */
        void turned(double delta) {
            if (Math.abs(delta) > MOVE_DEGREES) {
                int direction = delta > 0 ? 1 : -1;
                if (lastDirection != 0 && direction != lastDirection) {
                    reversals++;
                }
                lastDirection = direction;
            }
            if (side == 0) {
                // a 180° turn: its side is the way the player turns
                before += delta;
                if (Math.abs(before) < TURN_START_DEGREES) {
                    return;
                }
                side = before > 0 ? 1 : -1;
                delta = before;
            }
            double toward = side * delta;
            travelled += toward;
            double left = angle - travelled;
            if (left <= size) {
                reached = true;
            }
            if (left < -size && first == 0) {
                first = OVERSHOOT;
            }
            if (toward > MOVE_DEGREES) {
                moved = true;
            } else if (moved && !stopped && toward <= STOP_DEGREES) {
                stopped = true;
                if (!reached && first == 0) {
                    first = SHORT;
                }
            }
        }

        /** How far the look still is from the centre of the target, on its side (degrees; below 0: past it). */
        double left() {
            return angle - travelled;
        }
    }

    /** A series at one sensitivity: its flicks and its tracking. */
    static final class Series {
        final int percent;
        private long totalTime;
        int flicks;
        int overshoots;
        int shorts;
        int timeouts;
        int corrections;
        int trackingTicks;
        int trackingOn;

        Series(int percent) {
            this.percent = percent;
        }

        /** The target clicked this long after it appeared. */
        void hit(Flick flick, long millis) {
            count(flick, Math.max(0, Math.min(TIMEOUT_MS, millis)));
        }

        /** The target not clicked in time: it counts with the whole time. */
        void timedOut(Flick flick) {
            timeouts++;
            count(flick, TIMEOUT_MS);
        }

        private void count(Flick flick, long millis) {
            flicks++;
            totalTime += millis;
            corrections += flick.reversals;
            if (flick.first == OVERSHOOT) {
                overshoots++;
            } else if (flick.first == SHORT) {
                shorts++;
            }
        }

        /** A tick of the tracking: the crosshair on the target or not. */
        void tracked(boolean on) {
            trackingTicks++;
            if (on) {
                trackingOn++;
            }
        }

        long averageTime() {
            return flicks == 0 ? TIMEOUT_MS : totalTime / flicks;
        }

        double correctionsPerFlick() {
            return flicks == 0 ? 0 : (double) corrections / flicks;
        }

        /** The tracking, in % of its ticks on the target. */
        int tracking() {
            return trackingTicks == 0 ? 0 : (int) Math.round(100.0 * trackingOn / trackingTicks);
        }

        int errors() {
            return overshoots + shorts;
        }
    }

    /**
     * The most precise series: each series is ranked on the time to the target, the overshoots and
     * shorts, the corrections and the tracking; the lowest sum of the ranks wins, then the better
     * tracking, then the quicker time, then the first played (to confirm).
     */
    static int mostPrecise(List<Series> all) {
        int best = 0;
        int bestRanks = Integer.MAX_VALUE;
        for (int i = 0; i < all.size(); i++) {
            Series series = all.get(i);
            int ranks = 0;
            for (Series other : all) {
                ranks += other.averageTime() < series.averageTime() ? 1 : 0;
                ranks += other.errors() < series.errors() ? 1 : 0;
                ranks += other.correctionsPerFlick() < series.correctionsPerFlick() - 1e-9 ? 1 : 0;
                ranks += other.tracking() > series.tracking() ? 1 : 0;
            }
            Series kept = all.get(best);
            boolean tieWon = series.tracking() > kept.tracking()
                    || series.tracking() == kept.tracking() && series.averageTime() < kept.averageTime();
            if (ranks < bestRanks || ranks == bestRanks && tieWon) {
                best = i;
                bestRanks = ranks;
            }
        }
        return best;
    }

    /** Which way to go from this series: -1 lower (more overshoots than shorts), 1 higher (more shorts), 0 neither. */
    static int direction(Series series) {
        return Integer.compare(series.shorts, series.overshoots);
    }
}
