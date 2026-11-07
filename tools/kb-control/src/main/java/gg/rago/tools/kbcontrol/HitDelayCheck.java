package gg.rago.tools.kbcontrol;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Checks the engine's {@code hit-delay-ticks} setting on a real server, with the bursts of
 * bots/hit-delay-bots.mjs: during a burst the attacker hits the victim twice per tick, so every
 * hit must count exactly the hit delay after the previous one, in server ticks as the engine logs
 * them.
 *
 * <ul>
 *   <li>no two hits of a burst closer than the delay;</li>
 *   <li>at least {@link #MIN_EXACT_SHARE} of the intervals equal to the delay (a slow server tick
 *       or a late attack can make one longer);</li>
 *   <li>at least {@link #MIN_HITS_PER_BURST} hits per burst;</li>
 *   <li>bursts with the vanilla delay, with another delay, and after the victim respawned.</li>
 * </ul>
 */
public final class HitDelayCheck {

    static final int MIN_HITS_PER_BURST = 5;
    static final double MIN_EXACT_SHARE = 0.75;
    /** The last hit of a burst can be logged shortly after the bot's end time. */
    static final long END_MARGIN_MILLIS = 250L;
    static final int VANILLA_DELAY_TICKS = 10;

    private HitDelayCheck() {
    }

    public static void main(String[] args) throws IOException {
        Path hits = null;
        Path bursts = null;
        Path report = null;
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--hits":
                    hits = Paths.get(args[i + 1]);
                    break;
                case "--bursts":
                    bursts = Paths.get(args[i + 1]);
                    break;
                case "--report":
                    report = Paths.get(args[i + 1]);
                    break;
                default:
                    usage("unknown argument " + args[i]);
                    return;
            }
        }
        if (hits == null || bursts == null || args.length % 2 != 0) {
            usage("--hits and --bursts are required");
            return;
        }
        Result result = check(readHits(hits), readBursts(bursts));
        String markdown = result.toMarkdown();
        System.out.println(markdown);
        if (report != null) {
            Files.write(report, markdown.getBytes(StandardCharsets.UTF_8));
        }
        System.exit(result.passed() ? 0 : 1);
    }

    private static void usage(String problem) {
        System.err.println(problem);
        System.err.println("usage: HitDelayCheck --hits <file or directory> --bursts <file> [--report <file>]");
        System.exit(2);
    }

    static List<Hit> readHits(Path path) throws IOException {
        List<Hit> hits = new ArrayList<>();
        for (JsonObject json : ControlCheck.readHitLines(path)) {
            hits.add(new Hit(json.get("time").getAsLong(), json.get("tick").getAsLong(),
                    json.get("victimEntityId").getAsInt()));
        }
        return hits;
    }

    static List<Burst> readBursts(Path path) throws IOException {
        List<Burst> bursts = new ArrayList<>();
        for (JsonObject json : ControlCheck.readJsonLines(path)) {
            String type = json.get("type").getAsString();
            if ("burst".equals(type)) {
                bursts.add(new Burst(json.get("name").getAsString(), json.get("delayTicks").getAsInt(),
                        json.get("victimEntityId").getAsInt(), json.get("start").getAsLong(),
                        json.get("end").getAsLong(), json.has("afterRespawn") && json.get("afterRespawn").getAsBoolean()));
            } else if ("error".equals(type)) {
                bursts.add(Burst.failed(json.get("message").getAsString()));
            }
        }
        return bursts;
    }

    static Result check(List<Hit> hits, List<Burst> bursts) {
        Result result = new Result();
        boolean vanilla = false;
        boolean other = false;
        boolean respawn = false;
        for (Burst burst : bursts) {
            if (burst.error != null) {
                result.problems.add("the bots stopped: " + burst.error);
                continue;
            }
            List<Long> ticks = new ArrayList<>();
            for (Hit hit : hits) {
                if (hit.victimEntityId == burst.victimEntityId && hit.time >= burst.start
                        && hit.time <= burst.end + END_MARGIN_MILLIS) {
                    ticks.add(hit.tick);
                }
            }
            ticks.sort(null);
            BurstResult outcome = new BurstResult(burst, ticks.size());
            for (int i = 1; i < ticks.size(); i++) {
                long interval = ticks.get(i) - ticks.get(i - 1);
                outcome.intervals.merge(interval, 1, Integer::sum);
                if (interval < burst.delayTicks) {
                    outcome.problems.add("two hits " + interval + " ticks apart, the delay is " + burst.delayTicks);
                }
            }
            if (ticks.size() < MIN_HITS_PER_BURST) {
                outcome.problems.add(ticks.size() + " hits, at least " + MIN_HITS_PER_BURST + " expected");
            } else if (outcome.exactShare() < MIN_EXACT_SHARE) {
                outcome.problems.add(String.format(Locale.ROOT, "%.0f%% of the intervals are %d ticks, at least %.0f%% expected",
                        outcome.exactShare() * 100, burst.delayTicks, MIN_EXACT_SHARE * 100));
            }
            result.bursts.add(outcome);
            if (outcome.problems.isEmpty()) {
                vanilla |= burst.delayTicks == VANILLA_DELAY_TICKS;
                other |= burst.delayTicks != VANILLA_DELAY_TICKS;
                respawn |= burst.afterRespawn && burst.delayTicks != VANILLA_DELAY_TICKS;
            }
        }
        if (!vanilla) {
            result.problems.add("no passing burst with the vanilla delay of " + VANILLA_DELAY_TICKS + " ticks");
        }
        if (!other) {
            result.problems.add("no passing burst with a delay other than vanilla");
        }
        if (!respawn) {
            result.problems.add("no passing burst with a non-vanilla delay after the victim respawned");
        }
        return result;
    }

    /** A hit of the engine's log: the fields this check reads. */
    static final class Hit {
        final long time;
        final long tick;
        final int victimEntityId;

        Hit(long time, long tick, int victimEntityId) {
            this.time = time;
            this.tick = tick;
            this.victimEntityId = victimEntityId;
        }
    }

    /** A burst line of the bots: attacks twice per tick from {@code start} to {@code end}. */
    static final class Burst {
        final String name;
        final int delayTicks;
        final int victimEntityId;
        final long start;
        final long end;
        final boolean afterRespawn;
        final String error;

        Burst(String name, int delayTicks, int victimEntityId, long start, long end, boolean afterRespawn) {
            this(name, delayTicks, victimEntityId, start, end, afterRespawn, null);
        }

        private Burst(String name, int delayTicks, int victimEntityId, long start, long end, boolean afterRespawn,
                String error) {
            this.name = name;
            this.delayTicks = delayTicks;
            this.victimEntityId = victimEntityId;
            this.start = start;
            this.end = end;
            this.afterRespawn = afterRespawn;
            this.error = error;
        }

        static Burst failed(String error) {
            return new Burst(null, 0, 0, 0L, 0L, false, error);
        }
    }

    static final class BurstResult {
        final Burst burst;
        final int hits;
        /** Interval in ticks, and how many times it occurred. */
        final Map<Long, Integer> intervals = new TreeMap<>();
        final List<String> problems = new ArrayList<>();

        BurstResult(Burst burst, int hits) {
            this.burst = burst;
            this.hits = hits;
        }

        double exactShare() {
            int total = 0;
            for (int count : intervals.values()) {
                total += count;
            }
            return total == 0 ? 0.0 : intervals.getOrDefault((long) burst.delayTicks, 0) / (double) total;
        }
    }

    static final class Result {
        final List<BurstResult> bursts = new ArrayList<>();
        final List<String> problems = new ArrayList<>();

        boolean passed() {
            if (!problems.isEmpty()) {
                return false;
            }
            for (BurstResult burst : bursts) {
                if (!burst.problems.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        String toMarkdown() {
            StringBuilder md = new StringBuilder();
            md.append("## Hit delay test: ").append(passed() ? "PASSED" : "FAILED").append("\n\n");
            md.append("| Burst | Delay (ticks) | Hits | Intervals (ticks: count) | Result |\n");
            md.append("| --- | --- | --- | --- | --- |\n");
            for (BurstResult outcome : bursts) {
                StringBuilder intervals = new StringBuilder();
                for (Map.Entry<Long, Integer> entry : outcome.intervals.entrySet()) {
                    if (intervals.length() > 0) {
                        intervals.append(", ");
                    }
                    intervals.append(entry.getKey()).append(": ").append(entry.getValue());
                }
                md.append("| ").append(outcome.burst.name).append(" | ").append(outcome.burst.delayTicks)
                        .append(" | ").append(outcome.hits).append(" | ").append(intervals).append(" | ")
                        .append(outcome.problems.isEmpty() ? "passed" : String.join("; ", outcome.problems))
                        .append(" |\n");
            }
            md.append('\n');
            md.append("The attacker hits twice per tick during each burst: every hit must count exactly the delay"
                    + " after the previous one, in server ticks.\n");
            if (!problems.isEmpty()) {
                md.append("\n### Problems\n\n");
                for (String problem : problems) {
                    md.append("- ").append(problem).append('\n');
                }
            }
            return md.toString();
        }
    }
}
