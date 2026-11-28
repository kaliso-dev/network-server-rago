package gg.rago.tools.kbanalyzer;

import gg.rago.tools.kbanalyzer.Explosions.Explosion;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** The Markdown section of the explosions of one recording. */
final class ExplosionReport {

    private ExplosionReport() {
    }

    static void append(String name, Recording recording, StringBuilder md) {
        List<Explosion> explosions = Explosions.find(recording);
        md.append("## Explosions: ").append(name).append("\n\n");
        String player = recording.session == null ? null : Json.text(recording.session, "player");
        String mod = recording.session == null ? null : Json.text(recording.session, "mod");
        md.append("Player ").append(player == null ? "-" : player).append(" on ")
                .append(KbAnalyzer.domain(recording.contextValues("server").isEmpty() ? null
                        : recording.contextValues("server").get(0)))
                .append(", kb-recorder ").append(mod == null ? "-" : mod).append(": ").append(explosions.size())
                .append(" explosions.");
        if (recording.blockSnapshots.isEmpty()) {
            md.append(" The recording has no blocks read around the explosions (kb-recorder 0.2 and later).");
        }
        md.append("\n\n");
        if (explosions.isEmpty()) {
            return;
        }
        long start = recording.ticks.isEmpty() ? explosions.get(0).ms : recording.ticks.get(0).ms;

        md.append("| # | Time | Mark | Source | Click to spawn | Spawn to explosion | Speed | Strength"
                + " | Player distance (feet / eyes) | Push x, y, z | From | Horizontal / vertical | Angle"
                + " | Rise / landing |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        int number = 0;
        for (Explosion explosion : explosions) {
            number++;
            Explosions.Projectile source = explosion.source;
            double[] push = explosion.push();
            md.append("| ").append(number)
                    .append(" | ").append(format("%.2f s", (explosion.ms - start) / 1000.0))
                    .append(" | ").append(explosion.mark == null ? "-" : explosion.mark.replace("|", "/"))
                    .append(" | ").append(source == null ? "-" : source.kind())
                    .append(" | ").append(source == null || source.useMs == null ? "-"
                            : (source.spawnMs - source.useMs) + " ms (item " + source.useItem + ")")
                    .append(" | ").append(source == null ? "-" : (explosion.ms - source.spawnMs) + " ms ("
                            + Math.round((explosion.ms - source.spawnMs) / 50.0) + " ticks)")
                    .append(" | ").append(source == null || Double.isNaN(source.speedPerTick()) ? "-"
                            : format("%.3f block/tick", source.speedPerTick()))
                    .append(" | ").append(format("%.2f", explosion.packet.strength))
                    .append(" | ").append(explosion.player == null ? "-"
                            : format("%.2f / %.2f", explosion.feetDistance(), explosion.eyeDistance()))
                    .append(" | ").append(push == null ? "-" : format("%.4f, %.4f, %.4f", push[0], push[1], push[2]))
                    .append(" | ").append(from(explosion))
                    .append(" | ").append(push == null ? "-" : format("%.4f / %.4f", Math.hypot(push[0], push[2]), push[1]))
                    .append(" | ").append(Double.isNaN(explosion.pushAngle()) ? "-" : format("%.0f°", explosion.pushAngle()))
                    .append(" | ").append(Double.isNaN(explosion.rise) ? "-" : format("%.2f / %s", explosion.rise,
                            Double.isNaN(explosion.landingDistance) ? "-" : format("%.2f", explosion.landingDistance)))
                    .append(" |\n");
        }
        md.append("\nClick to spawn: the right click before the TNT or fireball appeared, and its item id (385: fire"
                + " charge, 46: TNT). Speed: from the positions the server gave. Distances in blocks, push in blocks"
                + " per tick. From: the explosion packet's own push, or a velocity packet for the player within "
                + Explosions.PUSH_WINDOW_MILLIS + " ms. Angle: between the horizontal push and the line from the"
                + " centre to the player (0° is straight away). Rise / landing: highest point above where the player"
                + " stood, and the horizontal distance to where they landed.\n\n");

        md.append("| # | Broken (explosion packet) | Set to air afterwards | Left within the vanilla reach |\n"
                + "| --- | --- | --- | --- |\n");
        number = 0;
        for (Explosion explosion : explosions) {
            number++;
            md.append("| ").append(number).append(" | ").append(broken(explosion))
                    .append(" | ").append(setToAir(explosion))
                    .append(" | ").append(left(explosion)).append(" |\n");
        }
        md.append("\nEach kind of block is followed by its count and its distance from the centre (farthest broken,"
                + " nearest left). The vanilla reach of a strength s is 1.73 s blocks: no block farther than that can"
                + " break in vanilla.\n\n");
    }

    private static String from(Explosion explosion) {
        double[] own = explosion.packet.push;
        boolean packetPush = own[0] != 0 || own[1] != 0 || own[2] != 0;
        if (packetPush && explosion.velocity != null) {
            return "explosion (and a velocity packet after " + explosion.velocityDelayMs + " ms)";
        }
        if (packetPush) {
            return "explosion";
        }
        return explosion.velocity != null ? "velocity packet after " + explosion.velocityDelayMs + " ms" : "-";
    }

    private static String broken(Explosion explosion) {
        if (explosion.packet.blocks.isEmpty()) {
            return "none";
        }
        Map<String, double[]> kinds = new TreeMap<>();
        for (int[] position : explosion.packet.blocks) {
            int[] block = explosion.brokenBlock(position);
            String name = block == null ? (explosion.before == null ? "unknown" : "air") : Explosions.blockName(block[0], block[1]);
            add(kinds, name, distance(explosion, position), true);
        }
        return kinds(kinds, "up to");
    }

    private static String setToAir(Explosion explosion) {
        int air = 0;
        int other = 0;
        for (int[] change : explosion.changes) {
            if (change[3] == 0) {
                air++;
            } else {
                other++;
            }
        }
        if (air == 0 && other == 0) {
            return "none";
        }
        return air + (other > 0 ? " (and " + other + " other changes)" : "");
    }

    private static String left(Explosion explosion) {
        if (explosion.before == null) {
            return "-";
        }
        double reach = Explosions.vanillaReach(explosion.packet.strength);
        Set<Long> gone = new HashSet<>();
        for (int[] position : explosion.packet.blocks) {
            gone.add(BlockSnapshot.key(position[0], position[1], position[2]));
        }
        for (int[] change : explosion.changes) {
            if (change[3] == 0) {
                gone.add(BlockSnapshot.key(change[0], change[1], change[2]));
            }
        }
        Map<String, double[]> kinds = new TreeMap<>();
        for (Map.Entry<Long, int[]> entry : explosion.before.blocks().entrySet()) {
            if (gone.contains(entry.getKey())) {
                continue;
            }
            double distance = distance(explosion, BlockSnapshot.position(entry.getKey()));
            if (distance <= reach) {
                add(kinds, Explosions.blockName(entry.getValue()[0], entry.getValue()[1]), distance, false);
            }
        }
        return kinds.isEmpty() ? "none" : kinds(kinds, "from");
    }

    /** Counts a block of a kind and keeps its farthest ({@code farthest}) or nearest distance. */
    private static void add(Map<String, double[]> kinds, String name, double distance, boolean farthest) {
        double[] kind = kinds.computeIfAbsent(name, key -> new double[] {0, farthest ? 0 : Double.MAX_VALUE});
        kind[0]++;
        kind[1] = farthest ? Math.max(kind[1], distance) : Math.min(kind[1], distance);
    }

    private static String kinds(Map<String, double[]> kinds, String word) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, double[]> kind : kinds.entrySet()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(kind.getKey()).append(" ×").append((int) kind.getValue()[0]).append(' ').append(word)
                    .append(' ').append(format("%.1f", kind.getValue()[1]));
        }
        return text.toString();
    }

    /** From the explosion's centre to the centre of the block. */
    private static double distance(Explosion explosion, int[] position) {
        double dx = position[0] + 0.5 - explosion.packet.x;
        double dy = position[1] + 0.5 - explosion.packet.y;
        double dz = position[2] + 0.5 - explosion.packet.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static String format(String pattern, Object... values) {
        return String.format(Locale.ROOT, pattern, values);
    }
}
