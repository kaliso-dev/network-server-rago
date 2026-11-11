package gg.rago.tools.kbanalyzer;

import gg.rago.combat.model.VelocityPacket;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compares the knockback and the hit timing of fights recorded with kb-recorder, both players
 * recording at once. Usage:
 *
 * <pre>
 * KbAnalyzer --fight hypixel a.jsonl b.jsonl --fight rago c.jsonl d.jsonl [--report report.md]
 * </pre>
 *
 * <p>For each fight, the report gives the most frequent vertical values of the velocity packets
 * by victim ground state and attacker sprint, the horizontal push, the intervals between two hits
 * and, for each attacker, which attacks counted depending on the time since the previous one.
 * With several fights, a table compares them.
 */
public final class KbAnalyzer {

    private KbAnalyzer() {
    }

    public static void main(String[] args) throws IOException {
        List<Fight> fights = new ArrayList<>();
        Path report = null;
        for (int i = 0; i < args.length; i++) {
            if ("--fight".equals(args[i]) && i + 3 < args.length) {
                fights.add(new Fight(args[i + 1], view(Paths.get(args[i + 2])), view(Paths.get(args[i + 3]))));
                i += 3;
            } else if ("--report".equals(args[i]) && i + 1 < args.length) {
                report = Paths.get(args[++i]);
            } else {
                System.err.println("usage: KbAnalyzer --fight <name> <recording> <recording> [--fight ...]"
                        + " [--report <file>]");
                System.exit(2);
                return;
            }
        }
        if (fights.isEmpty()) {
            System.err.println("give at least one --fight");
            System.exit(2);
            return;
        }
        String markdown = report(fights);
        System.out.println(markdown);
        if (report != null) {
            Files.write(report, markdown.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static PlayerView view(Path file) throws IOException {
        return new PlayerView(Recording.read(file));
    }

    public static String report(List<Fight> fights) {
        List<FightStats> stats = new ArrayList<>();
        for (Fight fight : fights) {
            stats.add(new FightStats(fight));
        }
        StringBuilder md = new StringBuilder("# Knockback analysis\n\n");
        if (stats.size() > 1) {
            comparison(stats, md);
        }
        for (FightStats fight : stats) {
            fight(fight, md);
        }
        return md.toString();
    }

    private static void comparison(List<FightStats> stats, StringBuilder md) {
        md.append("## Comparison\n\n| Fight | Ground, no sprint | Ground, sprint | Sprint step | Extra horizontal"
                + " | Hits at 425-474 ms | Hits at 475-524 ms |\n| --- | --- | --- | --- | --- | --- | --- |\n");
        for (FightStats fight : stats) {
            Long step = fight.sprintStep();
            Long extra = fight.extraHorizontal.mode();
            md.append("| ").append(fight.fight.name)
                    .append(" | ").append(orDash(fight.vertical.get("ground, no sprint").mode()))
                    .append(" | ").append(orDash(fight.vertical.get("ground, sprint").mode()))
                    .append(" | ").append(step == null ? "-" : step + " (" + blocks(step) + ")")
                    .append(" | ").append(extra == null ? "-" : String.format(Locale.ROOT, "%.3f", extra / 1000.0))
                    .append(" | ").append(FightStats.percent(fight.intervalShare(425)))
                    .append(" | ").append(FightStats.percent(fight.intervalShare(475)))
                    .append(" |\n");
        }
        md.append("\nVertical values are packet units (8000 = 1 block per tick), the most frequent of each kind."
                + " The sprint step is their difference on the ground.\n\n");
    }

    private static void fight(FightStats stats, StringBuilder md) {
        Fight fight = stats.fight;
        md.append("## ").append(fight.name).append("\n\n");
        md.append("| Player | Server | Duration | Ticks/s | Velocity packets | With hurt animation"
                + " | Attack to animation | Matched with the attack |\n| --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (Fight.Direction direction : fight.directions()) {
            PlayerView victim = direction.victim;
            int hurt = 0;
            for (PlayerView.Velocity velocity : victim.velocities) {
                if (velocity.hurt) {
                    hurt++;
                }
            }
            md.append("| ").append(victim.player).append(" | ").append(domain(victim.server))
                    .append(" | ").append(victim.durationMillis / 1000).append(" s")
                    .append(" | ").append(String.format(Locale.ROOT, "%.1f", victim.ticksPerSecond()))
                    .append(" | ").append(victim.velocities.size())
                    .append(" | ").append(hurt)
                    .append(" | ").append(millis(fight.other(direction).registration.delayMillis))
                    .append(" | ").append(direction.matched())
                    .append(" |\n");
        }
        md.append("\n\"Attack to animation\" is the player's own delay, measured on its isolated attacks.\n\n");

        md.append("### Vertical\n\n| Hit | Hits | Most frequent values (packet units) |\n| --- | --- | --- |\n");
        for (Map.Entry<String, FightStats.Counts> entry : stats.vertical.entrySet()) {
            md.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue().total())
                    .append(" | ").append(entry.getValue().top(6)).append(" |\n");
        }
        Long step = stats.sprintStep();
        md.append("\nSprint step on the ground: ").append(step == null ? "-" : step + " units (" + blocks(step) + ")")
                .append(". The attacker's sprint is the one the server used, from the attacker's own recording.\n\n");

        md.append("### Horizontal\n\n");
        Long base = stats.horizontalWithoutSprint.mode();
        md.append("- Without sprint: most frequent length ").append(base == null ? "-"
                : String.format(Locale.ROOT, "%.3f", base / 1000.0)).append(" block per tick, for ")
                .append(base == null ? 0 : stats.horizontalWithoutSprint.count(base)).append(" of ")
                .append(stats.horizontalWithoutSprint.total()).append(" hits.\n");
        Long extra = stats.extraHorizontal.mode();
        md.append("- Sprint: extra push along the attacker's look ").append(extra == null ? "-"
                : String.format(Locale.ROOT, "%.3f", extra / 1000.0)).append(" for ").append(stats.sprintHitsNearExtraMode)
                .append(" of ").append(stats.sprintHitsWithLook).append(" sprint hits (within ")
                .append(FightStats.EXTRA_RESOLUTION).append("); the others are combos or misread sprints.\n\n");

        md.append("### Time between two hits\n\nIntervals between two hurt animations of a fighter, seen by both"
                + " players (").append(stats.intervalTotal).append(" intervals under ")
                .append(FightStats.INTERVAL_MAX_MILLIS).append(" ms).\n\n| Interval | Share |\n| --- | --- |\n");
        long[] bins = FightStats.INTERVAL_BINS;
        for (int i = 0; i < bins.length; i++) {
            long upper = i + 1 < bins.length ? bins[i + 1] - 1 : FightStats.INTERVAL_MAX_MILLIS - 1;
            String label = bins[i] == 0 ? "under " + bins[1] + " ms" : bins[i] + "-" + upper + " ms ("
                    + ((bins[i] + 25) / 50) + " ticks)";
            md.append("| ").append(label).append(" | ").append(FightStats.percent(stats.intervalShare(bins[i])))
                    .append(" (").append(stats.intervals.get(bins[i])).append(") |\n");
        }
        md.append('\n');

        for (Fight.Direction direction : fight.directions()) {
            Registration registration = fight.other(direction).registration;
            md.append("### Attacks of ").append(direction.victim.player).append(" that counted\n\n")
                    .append(registration.countedAttacks()).append(" of ").append(registration.attacks.size())
                    .append(" attacks counted, for ").append(registration.hurts.size())
                    .append(" hurt animations of the target.\n\n| Since the previous counted attack | Counted |\n"
                            + "| --- | --- |\n");
            for (Map.Entry<Long, int[]> bucket : registration.curve().entrySet()) {
                int[] counts = bucket.getValue();
                if (counts[0] == 0) {
                    continue;
                }
                long lower = bucket.getKey();
                String label = lower == 0 ? "under " + Registration.CURVE_FIRST_BUCKET_MILLIS + " ms"
                        : lower + "-" + (lower + Registration.CURVE_BUCKET_MILLIS - 1) + " ms";
                md.append("| ").append(label).append(" | ").append(counts[1]).append(" of ").append(counts[0])
                        .append(" (").append(FightStats.percent(counts[1] / (double) counts[0])).append(") |\n");
            }
            md.append('\n');
        }
    }

    /**
     * The server's domain without the subdomain the player typed (any subdomain reaches Hypixel, so
     * it says nothing about the server and may be something the player would rather not share).
     * Addresses and names of one or two labels are kept as they are.
     */
    static String domain(String server) {
        if (server == null || server.isEmpty()) {
            return "-";
        }
        String host = server.toLowerCase(Locale.ROOT);
        int port = host.lastIndexOf(':');
        if (port > 0 && host.indexOf(':') == port) {
            host = host.substring(0, port);
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.matches("[0-9.]+") || host.contains(":")) {
            return host;
        }
        String[] labels = host.split("\\.");
        if (labels.length <= 2) {
            return host;
        }
        return labels[labels.length - 2] + "." + labels[labels.length - 1];
    }

    private static String orDash(Long value) {
        return value == null ? "-" : value.toString();
    }

    private static String blocks(long units) {
        return String.format(Locale.ROOT, "%.4f block/tick", units / VelocityPacket.UNITS_PER_BLOCK);
    }

    private static String millis(double value) {
        return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, "%.0f ms", value);
    }
}
