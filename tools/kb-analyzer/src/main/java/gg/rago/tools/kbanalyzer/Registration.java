package gg.rago.tools.kbanalyzer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which attacks of a player counted, seen from the attacker's recording: an attack counts when the
 * target's hurt animation comes back about one attack-to-animation delay later.
 *
 * <p>The delay is measured on isolated attacks, the only attack in the {@link #ISOLATION_MILLIS}
 * before an animation. Each animation is then given to the attack sent closest to that delay before
 * it, within {@link #MATCH_MILLIS}.
 */
public final class Registration {

    static final long ISOLATION_MILLIS = 700L;
    static final long MATCH_MILLIS = 60L;
    /** Intervals since the previous counted attack longer than this are left out of the curve. */
    static final long CURVE_MAX_MILLIS = 700L;
    static final long CURVE_FIRST_BUCKET_MILLIS = 400L;
    static final long CURVE_BUCKET_MILLIS = 25L;

    public final int target;
    public final List<Long> attacks;
    public final List<Long> hurts;
    /** Median delay from an isolated attack to the target's hurt animation, or NaN. */
    public final double delayMillis;
    public final int isolated;
    /** For each attack, whether it counted. */
    public final boolean[] counted;
    /** For each hurt animation, the index of the attack that caused it, or -1. */
    public final int[] causeOf;

    public Registration(PlayerView attacker, int target) {
        this.target = target;
        this.attacks = attacker.attacks.getOrDefault(target, new ArrayList<>());
        this.hurts = attacker.hurts.getOrDefault(target, new ArrayList<>());
        List<Long> delays = new ArrayList<>();
        for (long hurt : hurts) {
            List<Long> before = Times.between(attacks, hurt - ISOLATION_MILLIS, hurt);
            if (before.size() == 1) {
                delays.add(hurt - before.get(0));
            }
        }
        this.isolated = delays.size();
        this.delayMillis = Times.median(delays);
        this.counted = new boolean[attacks.size()];
        this.causeOf = new int[hurts.size()];
        for (int h = 0; h < hurts.size(); h++) {
            causeOf[h] = -1;
            if (Double.isNaN(delayMillis)) {
                continue;
            }
            long hurt = hurts.get(h);
            double expected = hurt - delayMillis;
            int best = -1;
            for (int a = 0; a < attacks.size(); a++) {
                long attack = attacks.get(a);
                if (attack > hurt || attack < expected - MATCH_MILLIS) {
                    continue;
                }
                if (attack > expected + MATCH_MILLIS) {
                    break;
                }
                if (best < 0 || Math.abs(attack - expected) < Math.abs(attacks.get(best) - expected)) {
                    best = a;
                }
            }
            causeOf[h] = best;
            if (best >= 0) {
                counted[best] = true;
            }
        }
    }

    public int countedAttacks() {
        int count = 0;
        for (boolean c : counted) {
            if (c) {
                count++;
            }
        }
        return count;
    }

    /**
     * For buckets of the time since the previous counted attack: attacks sent, and how many
     * counted. The first bucket holds everything under {@link #CURVE_FIRST_BUCKET_MILLIS}.
     */
    public Map<Long, int[]> curve() {
        Map<Long, int[]> buckets = new LinkedHashMap<>();
        buckets.put(0L, new int[2]);
        for (long b = CURVE_FIRST_BUCKET_MILLIS; b < CURVE_MAX_MILLIS; b += CURVE_BUCKET_MILLIS) {
            buckets.put(b, new int[2]);
        }
        long previous = Long.MIN_VALUE;
        for (int a = 0; a < attacks.size(); a++) {
            long attack = attacks.get(a);
            if (previous != Long.MIN_VALUE && attack > previous && attack - previous <= CURVE_MAX_MILLIS) {
                long since = attack - previous;
                long bucket = since < CURVE_FIRST_BUCKET_MILLIS ? 0L
                        : CURVE_FIRST_BUCKET_MILLIS + (since - CURVE_FIRST_BUCKET_MILLIS) / CURVE_BUCKET_MILLIS
                                * CURVE_BUCKET_MILLIS;
                int[] stats = buckets.get(Math.min(bucket, CURVE_MAX_MILLIS - CURVE_BUCKET_MILLIS));
                stats[0]++;
                if (counted[a]) {
                    stats[1]++;
                }
            }
            if (counted[a] && attack > previous) {
                previous = attack;
            }
        }
        return buckets;
    }
}
