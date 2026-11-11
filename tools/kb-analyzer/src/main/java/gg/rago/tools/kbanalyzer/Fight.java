package gg.rago.tools.kbanalyzer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A fight recorded by both players. Each velocity packet a player received is matched with the
 * attack the other player sent, which gives what the victim's recording cannot: the attacker's
 * sprint as the server saw it, and the attacker's look.
 */
public final class Fight {

    /** A melee hit received, with what the attacker's recording says about it. */
    public static final class Hit {
        public final PlayerView.Velocity velocity;
        /** The attacker's sprint as the server used it, or null when the attack was not found. */
        public final Boolean sprint;
        /** The attacker's yaw in its last look packet before the attack, or NaN. */
        public final double attackerYaw;

        Hit(PlayerView.Velocity velocity, Boolean sprint, double attackerYaw) {
            this.velocity = velocity;
            this.sprint = sprint;
            this.attackerYaw = attackerYaw;
        }
    }

    /** One way of the fight: the hits a victim received from an attacker. */
    public static final class Direction {
        public final PlayerView victim;
        public final PlayerView attacker;
        /** Victim clock minus attacker clock, measured on the victim's hurt animations seen by both. */
        public final double clockOffsetMillis;
        public final int sharedAnimations;
        public final Registration registration;
        public final List<Hit> hits = new ArrayList<>();

        Direction(PlayerView victim, PlayerView attacker) {
            this.victim = victim;
            this.attacker = attacker;
            List<Long> seenByVictim = victim.hurts.getOrDefault(victim.entityId, new ArrayList<>());
            List<Long> seenByAttacker = attacker.hurts.getOrDefault(victim.entityId, new ArrayList<>());
            double[] offset = clockOffset(seenByVictim, seenByAttacker);
            this.clockOffsetMillis = offset[0];
            this.sharedAnimations = (int) offset[1];
            this.registration = new Registration(attacker, victim.entityId);
            Map<Integer, Boolean> sprintAtCause = sprintAtCauses(attacker, registration);
            for (PlayerView.Velocity velocity : victim.velocities) {
                Boolean sprint = null;
                double yaw = Double.NaN;
                if (velocity.hurt && !Double.isNaN(clockOffsetMillis)) {
                    long expected = Math.round(velocity.ms - clockOffsetMillis);
                    Long hurt = Times.nearest(registration.hurts, expected, MATCH_MILLIS);
                    if (hurt != null) {
                        int cause = registration.causeOf[registration.hurts.indexOf(hurt)];
                        if (cause >= 0) {
                            sprint = sprintAtCause.get(cause);
                            yaw = attacker.yawBefore(registration.attacks.get(cause));
                        }
                    }
                }
                hits.add(new Hit(velocity, sprint, yaw));
            }
        }

        public int matched() {
            int count = 0;
            for (Hit hit : hits) {
                if (hit.sprint != null) {
                    count++;
                }
            }
            return count;
        }
    }

    /** The same hurt animation reaches both clients within this window once the clocks are matched. */
    static final long MATCH_MILLIS = 40L;
    static final long OFFSET_SEARCH_MILLIS = 120_000L;
    static final long OFFSET_BIN_MILLIS = 5L;
    static final long OFFSET_KEEP_MILLIS = 30L;

    public final String name;
    public final Direction first;
    public final Direction second;

    public Fight(String name, PlayerView a, PlayerView b) {
        this.name = name;
        this.first = new Direction(a, b);
        this.second = new Direction(b, a);
    }

    /** The other way of the fight: the hits the victim of this direction gave. */
    public Direction other(Direction direction) {
        return direction == first ? second : first;
    }

    public List<Direction> directions() {
        List<Direction> both = new ArrayList<>();
        both.add(first);
        both.add(second);
        return both;
    }

    /**
     * The offset between two clocks from the same events: the most common difference in
     * {@link #OFFSET_BIN_MILLIS} bins, then the median of the differences close to it. Returns
     * {offset or NaN, number of events used}.
     */
    static double[] clockOffset(List<Long> a, List<Long> b) {
        Map<Long, Integer> bins = new HashMap<>();
        for (long x : a) {
            for (long y : Times.between(b, x - OFFSET_SEARCH_MILLIS, x + OFFSET_SEARCH_MILLIS)) {
                bins.merge(Math.floorDiv(x - y, OFFSET_BIN_MILLIS), 1, Integer::sum);
            }
        }
        if (bins.isEmpty()) {
            return new double[] {Double.NaN, 0};
        }
        long peak = bins.entrySet().stream().max(Map.Entry.<Long, Integer>comparingByValue()
                .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .get().getKey() * OFFSET_BIN_MILLIS;
        List<Long> close = new ArrayList<>();
        for (long x : a) {
            Long y = Times.nearest(b, x - peak, OFFSET_KEEP_MILLIS);
            if (y != null) {
                close.add(x - y);
            }
        }
        return new double[] {Times.median(close), close.size()};
    }

    /**
     * The attacker's sprint at each counted attack, replaying its sprint packets in order with
     * the vanilla rule that a sprint hit that counts ends the sprint on the server.
     */
    static Map<Integer, Boolean> sprintAtCauses(PlayerView attacker, Registration registration) {
        List<long[]> events = new ArrayList<>();
        for (long[] change : attacker.sprintChanges) {
            events.add(new long[] {change[0], 0, change[1]});
        }
        for (int a = 0; a < registration.attacks.size(); a++) {
            if (registration.counted[a]) {
                events.add(new long[] {registration.attacks.get(a), 1, a});
            }
        }
        // At the same time, the sprint packet was sent first: the client sends it before attacking.
        events.sort(Comparator.<long[]>comparingLong(e -> e[0]).thenComparingLong(e -> e[1]));
        Map<Integer, Boolean> sprint = new HashMap<>();
        boolean state = false;
        for (long[] event : events) {
            if (event[1] == 0) {
                state = event[2] == 1L;
            } else {
                sprint.put((int) event[2], state);
                state = false;
            }
        }
        return sprint;
    }
}
