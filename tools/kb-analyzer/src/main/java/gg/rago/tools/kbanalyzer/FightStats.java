package gg.rago.tools.kbanalyzer;

import gg.rago.combat.model.VelocityPacket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** The numbers of a fight that the report shows, for both players together. */
public final class FightStats {

    /** Frequencies of whole values, most frequent first on read. */
    public static final class Counts {
        private final Map<Long, Integer> counts = new TreeMap<>();
        private int total;

        void add(long value) {
            counts.merge(value, 1, Integer::sum);
            total++;
        }

        public int total() {
            return total;
        }

        public int count(long value) {
            return counts.getOrDefault(value, 0);
        }

        /** The most frequent value (the smallest one on a tie), or null when empty. */
        public Long mode() {
            Long best = null;
            for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
                if (best == null || entry.getValue() > counts.get(best)) {
                    best = entry.getKey();
                }
            }
            return best;
        }

        /** The {@code n} most frequent values, as "value (count)". */
        public String top(int n) {
            List<Map.Entry<Long, Integer>> entries = new ArrayList<>(counts.entrySet());
            entries.sort((x, y) -> y.getValue().equals(x.getValue()) ? Long.compare(x.getKey(), y.getKey())
                    : Integer.compare(y.getValue(), x.getValue()));
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < Math.min(n, entries.size()); i++) {
                if (i > 0) {
                    text.append(", ");
                }
                text.append(entries.get(i).getKey()).append(" (").append(entries.get(i).getValue()).append(')');
            }
            return text.length() == 0 ? "-" : text.toString();
        }
    }

    /** Hit categories of the vertical table, in report order. */
    public static final List<String> CATEGORIES = java.util.Arrays.asList(
            "ground, no sprint", "ground, sprint", "air, no sprint", "air, sprint", "sprint unknown");

    /** Interval bins centred on whole ticks of 50 ms: the label is the lower bound in ms. */
    public static final long[] INTERVAL_BINS = {0, 425, 475, 525, 575, 625};
    public static final long INTERVAL_MAX_MILLIS = 700L;
    /** Extra horizontal pushes are compared at this resolution. */
    static final double EXTRA_RESOLUTION = 0.005;

    public final Fight fight;
    public final Map<String, Counts> vertical = new LinkedHashMap<>();
    /** Horizontal lengths of hits without sprint, in 1/1000 block per tick. */
    public final Counts horizontalWithoutSprint = new Counts();
    /** Extra horizontal push of sprint hits along the attacker's look, in 1/1000 block per tick. */
    public final Counts extraHorizontal = new Counts();
    public int sprintHitsWithLook;
    public int sprintHitsNearExtraMode;
    /** Intervals between two hurt animations of a fighter, from both recordings, by bin. */
    public final Map<Long, Integer> intervals = new LinkedHashMap<>();
    public int intervalTotal;

    public FightStats(Fight fight) {
        this.fight = fight;
        for (String category : CATEGORIES) {
            vertical.put(category, new Counts());
        }
        for (long bin : INTERVAL_BINS) {
            intervals.put(bin, 0);
        }
        List<Fight.Hit> sprintHits = new ArrayList<>();
        for (Fight.Direction direction : fight.directions()) {
            for (Fight.Hit hit : direction.hits) {
                if (!hit.velocity.hurt) {
                    continue;
                }
                vertical.get(category(hit)).add(hit.velocity.packet.y());
                if (Boolean.FALSE.equals(hit.sprint)) {
                    horizontalWithoutSprint.add(Math.round(hit.velocity.horizontal() * 1000));
                } else if (Boolean.TRUE.equals(hit.sprint) && !Double.isNaN(hit.attackerYaw)) {
                    sprintHits.add(hit);
                }
            }
            addIntervals(direction.victim.hurts.get(direction.victim.entityId));
            addIntervals(direction.victim.hurts.get(direction.attacker.entityId));
        }
        Long base = horizontalWithoutSprint.mode();
        if (base != null) {
            List<double[]> roots = new ArrayList<>();
            for (Fight.Hit hit : sprintHits) {
                double[] k = extraPush(hit, base / 1000.0);
                roots.add(k);
                for (double value : k) {
                    extraHorizontal.add(Math.round(value / EXTRA_RESOLUTION) * 5);
                }
            }
            sprintHitsWithLook = sprintHits.size();
            Long mode = extraHorizontal.mode();
            if (mode != null) {
                for (double[] k : roots) {
                    for (double value : k) {
                        if (Math.abs(value - mode / 1000.0) <= EXTRA_RESOLUTION) {
                            sprintHitsNearExtraMode++;
                            break;
                        }
                    }
                }
            }
        }
    }

    static String category(Fight.Hit hit) {
        if (hit.sprint == null) {
            return "sprint unknown";
        }
        return (hit.velocity.ground ? "ground, " : "air, ") + (hit.sprint ? "sprint" : "no sprint");
    }

    private void addIntervals(List<Long> hurts) {
        if (hurts == null) {
            return;
        }
        for (int i = 1; i < hurts.size(); i++) {
            long interval = hurts.get(i) - hurts.get(i - 1);
            if (interval <= 0 || interval >= INTERVAL_MAX_MILLIS) {
                continue;
            }
            long bin = 0;
            for (long lower : INTERVAL_BINS) {
                if (interval >= lower) {
                    bin = lower;
                }
            }
            intervals.merge(bin, 1, Integer::sum);
            intervalTotal++;
        }
    }

    /**
     * The extra push k along the attacker's look L that gives the received horizontal velocity V
     * from a base push of length {@code base}: |V - kL| = base. Returns the solutions between 0.05
     * and 1.2 (none, one or two).
     */
    static double[] extraPush(Fight.Hit hit, double base) {
        double yaw = Math.toRadians(hit.attackerYaw);
        double lx = -Math.sin(yaw);
        double lz = Math.cos(yaw);
        double vx = hit.velocity.packet.x() / VelocityPacket.UNITS_PER_BLOCK;
        double vz = hit.velocity.packet.z() / VelocityPacket.UNITS_PER_BLOCK;
        double dot = vx * lx + vz * lz;
        double discriminant = dot * dot - (vx * vx + vz * vz) + base * base;
        if (discriminant < 0) {
            return new double[0];
        }
        double root = Math.sqrt(discriminant);
        List<Double> found = new ArrayList<>();
        for (double k : new double[] {dot - root, dot + root}) {
            if (k > 0.05 && k < 1.2 && !found.contains(k)) {
                found.add(k);
            }
        }
        double[] result = new double[found.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = found.get(i);
        }
        return result;
    }

    /** Vertical extra of a sprint hit: most frequent sprint value minus most frequent value without, on the ground. */
    public Long sprintStep() {
        Long sprint = vertical.get("ground, sprint").mode();
        Long without = vertical.get("ground, no sprint").mode();
        return sprint == null || without == null ? null : sprint - without;
    }

    public double intervalShare(long bin) {
        return intervalTotal == 0 ? Double.NaN : intervals.getOrDefault(bin, 0) / (double) intervalTotal;
    }

    static String percent(double share) {
        return Double.isNaN(share) ? "-" : String.format(Locale.ROOT, "%.0f %%", share * 100);
    }
}
