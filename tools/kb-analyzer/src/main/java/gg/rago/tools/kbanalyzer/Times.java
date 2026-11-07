package gg.rago.tools.kbanalyzer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Helpers over sorted lists of times in milliseconds. */
final class Times {

    private Times() {
    }

    /** The time of the list nearest to {@code ms}, if at most {@code window} away, or null. */
    static Long nearest(List<Long> sorted, long ms, long window) {
        int index = Collections.binarySearch(sorted, ms);
        int insertion = index >= 0 ? index : -index - 1;
        Long best = null;
        for (int i = Math.max(0, insertion - 1); i <= Math.min(sorted.size() - 1, insertion); i++) {
            long value = sorted.get(i);
            if (Math.abs(value - ms) <= window && (best == null || Math.abs(value - ms) < Math.abs(best - ms))) {
                best = value;
            }
        }
        return best;
    }

    /** The values of the list in [from, to]. */
    static List<Long> between(List<Long> sorted, long from, long to) {
        List<Long> found = new ArrayList<>();
        int index = Collections.binarySearch(sorted, from);
        int start = index >= 0 ? index : -index - 1;
        while (start > 0 && sorted.get(start - 1) == from) {
            start--;
        }
        for (int i = start; i < sorted.size() && sorted.get(i) <= to; i++) {
            found.add(sorted.get(i));
        }
        return found;
    }

    static double median(List<? extends Number> values) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        List<Double> sorted = new ArrayList<>();
        for (Number value : values) {
            sorted.add(value.doubleValue());
        }
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }
}
