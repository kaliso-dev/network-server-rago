package gg.rago.tools.kbcontrol;

import com.google.gson.JsonObject;
import gg.rago.tools.kbanalyzer.BlockSnapshot;
import gg.rago.tools.kbanalyzer.Explosions;
import gg.rago.tools.kbanalyzer.Explosions.Explosion;
import gg.rago.tools.kbanalyzer.Recording;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The explosions part of the kb-recorder end-to-end test. The attacker bot summons a primed TNT
 * and a fireball far from both players; the recording must then hold what kb-recorder keeps for an
 * explosion, read by kb-analyzer exactly as for a measurement on another server:
 * <ul>
 *   <li>the TNT or fireball's spawn and its destruction, which make it the explosion's source;</li>
 *   <li>the explosion packet, with the strength the server used;</li>
 *   <li>the blocks around the explosion, read before the client applied it: every block the
 *       explosion destroyed is still there;</li>
 *   <li>for the TNT, which always breaks the grass and dirt it stands on, the server's block
 *       changes that set those blocks to air.</li>
 * </ul>
 */
final class ExplosionCheck {

    /** How far the explosion can be from where the bot summoned its source, horizontally. */
    static final double MAX_OFFSET = 2.5;

    private ExplosionCheck() {
    }

    /** An explosion the bot caused (type "explosion" in attacks.jsonl). */
    static final class Expected {
        final String kind;
        final double x;
        final double y;
        final double z;
        final float strength;

        Expected(String kind, double x, double y, double z, float strength) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.strength = strength;
        }

        /** The source's kind as kb-analyzer names it. */
        String sourceKind() {
            return "tnt".equals(kind) ? "TNT" : kind;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s at %.1f %.1f %.1f", kind, x, y, z);
        }
    }

    static List<Expected> read(Path attacks) throws IOException {
        List<Expected> expected = new ArrayList<>();
        for (JsonObject json : ControlCheck.readJsonLines(attacks)) {
            if ("explosion".equals(json.get("type").getAsString())) {
                expected.add(new Expected(json.get("kind").getAsString(), json.get("x").getAsDouble(),
                        json.get("y").getAsDouble(), json.get("z").getAsDouble(), json.get("strength").getAsFloat()));
            }
        }
        return expected;
    }

    static Result check(Recording recording, List<Expected> expected) {
        Result result = new Result();
        List<Explosion> explosions = Explosions.find(recording);
        result.problems.addAll(recording.problems);
        if (expected.isEmpty()) {
            result.problems.add("the bot caused no explosion");
        }
        for (Expected cause : expected) {
            Explosion explosion = null;
            for (Explosion candidate : explosions) {
                if (Math.hypot(candidate.packet.x - cause.x, candidate.packet.z - cause.z) <= MAX_OFFSET) {
                    explosion = candidate;
                    break;
                }
            }
            String problem = problem(cause, explosion);
            result.rows.add(row(cause, explosion, problem));
            if (problem != null) {
                result.problems.add(cause + ": " + problem);
            }
        }
        return result;
    }

    private static String problem(Expected cause, Explosion explosion) {
        if (explosion == null) {
            return "no explosion packet near it";
        }
        if (explosion.source == null) {
            return "no primed TNT or fireball destroyed within " + Explosions.SOURCE_WINDOW_MILLIS
                    + " ms of the explosion (spawn or destroy packet missing)";
        }
        if (!cause.sourceKind().equals(explosion.source.kind())) {
            return "the explosion came from a " + explosion.source.kind();
        }
        if (Math.hypot(explosion.source.spawn.x - cause.x, explosion.source.spawn.z - cause.z) > MAX_OFFSET) {
            return String.format(Locale.ROOT, "its %s spawned at %.2f %.2f %.2f", explosion.source.kind(),
                    explosion.source.spawn.x, explosion.source.spawn.y, explosion.source.spawn.z);
        }
        if (explosion.packet.strength != cause.strength) {
            return "strength " + explosion.packet.strength + ", the server uses " + cause.strength;
        }
        if (explosion.before == null) {
            return "no blocks line for the explosion";
        }
        for (int[] block : explosion.packet.blocks) {
            if (explosion.before.at(block[0], block[1], block[2]) == null) {
                return "the blocks line has air at " + block[0] + " " + block[1] + " " + block[2]
                        + ", which the explosion destroyed: it was read after the explosion";
            }
        }
        if ("tnt".equals(cause.kind)) {
            if (explosion.packet.blocks.isEmpty()) {
                return "the TNT destroyed no block";
            }
            boolean confirmed = false;
            for (int[] change : explosion.changes) {
                for (int[] block : explosion.packet.blocks) {
                    confirmed |= change[3] == 0 && change[0] == block[0] && change[1] == block[1] && change[2] == block[2];
                }
            }
            if (!confirmed) {
                return "no block change setting a destroyed block to air";
            }
        }
        return null;
    }

    private static String row(Expected cause, Explosion explosion, String problem) {
        StringBuilder row = new StringBuilder("| ").append(cause.kind);
        if (explosion == null) {
            return row.append(" | - | - | - | - | - | FAILED |").toString();
        }
        int air = 0;
        for (int[] change : explosion.changes) {
            if (change[3] == 0) {
                air++;
            }
        }
        BlockSnapshot before = explosion.before;
        return row.append(" | ").append(explosion.source == null ? "-" : explosion.source.kind() + " "
                        + (explosion.ms - explosion.source.spawnMs) + " ms before")
                .append(" | ").append(explosion.packet.strength)
                .append(" | ").append(explosion.packet.blocks.size())
                .append(" | ").append(before == null ? "-" : String.valueOf(before.blocks().size()))
                .append(" | ").append(air)
                .append(" | ").append(problem == null ? "passed" : "FAILED")
                .append(" |").toString();
    }

    static final class Result {
        final List<String> problems = new ArrayList<>();
        final List<String> rows = new ArrayList<>();

        boolean passed() {
            return problems.isEmpty();
        }

        String toMarkdown() {
            StringBuilder md = new StringBuilder("\n### Explosions: ").append(passed() ? "PASSED" : "FAILED")
                    .append("\n\nA primed TNT and a fireball summoned by the bot, far from both players, and what"
                            + " the recording holds for them, read by kb-analyzer.\n\n")
                    .append("| Summoned | Source in the recording | Strength | Blocks destroyed | Blocks read before"
                            + " | Block changes to air | Result |\n| --- | --- | --- | --- | --- | --- | --- |\n");
            for (String row : rows) {
                md.append(row).append('\n');
            }
            if (!problems.isEmpty()) {
                md.append("\n#### Problems\n\n");
                for (String problem : problems) {
                    md.append("- ").append(problem).append('\n');
                }
            }
            return md.toString();
        }
    }
}
