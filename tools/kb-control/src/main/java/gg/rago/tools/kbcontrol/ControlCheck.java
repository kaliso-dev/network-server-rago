package gg.rago.tools.kbcontrol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.rago.combat.model.KnockbackCalculator;
import gg.rago.combat.model.KnockbackProfile;
import gg.rago.combat.model.Vec3;
import gg.rago.combat.model.VelocityPacket;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Checks a control run: the hits the engine logged on a real 1.8.8 server against the velocity
 * packets the victim bot actually received, and both against rago-combat-model.
 *
 * <ul>
 *   <li>override hits: the client must receive exactly the packet the engine set, and that
 *       velocity must be exactly what the model computes from the logged inputs;</li>
 *   <li>observe hits: the engine changes nothing, so the packet is the server's own knockback. It
 *       must be exactly what the model computes with the server's knockback settings, which
 *       checks the inputs the engine captures against the server's real computation, and within
 *       one packet unit of exact vanilla 1.8.8;</li>
 *   <li>every velocity packet the bots received must belong to a logged hit, and every attack the
 *       bots sent must give one (the report lists the attacks that gave none).</li>
 * </ul>
 *
 * <p>Usage: {@code ControlCheck --hits <file or directory> --received <file>
 * [--server-knockback pandaspigot|vanilla] [--min-hits N] [--report <file>]}. Exits with status 1
 * if any check fails.
 */
public final class ControlCheck {

    /** Packets may be stamped slightly before the log line, and arrive up to this long after it. */
    static final long MATCH_BEFORE_MILLIS = 50L;
    static final long MATCH_AFTER_MILLIS = 2000L;
    /** The server logs a hit within a tick or two of the attack; the bots attack once a second. */
    static final long ATTACK_MATCH_MILLIS = 900L;
    /** At most this many attacks without a hit are listed in the report. */
    static final int LISTED_ATTACKS = 10;
    /** Allowed difference between the server's own knockback and exact vanilla, in packet units. */
    static final int VANILLA_TOLERANCE = 1;

    /**
     * The knockback a PandaSpigot server computes with its default settings (world section
     * {@code knockback} of pandaspigot.yml, server patch 0014 "Configurable knockback"): the
     * vanilla formula with its constants as doubles, 0.4 where vanilla uses {@code 0.4F}. The
     * sprint extra is a float product widened then multiplied by the double 0.5, which gives
     * the same result as vanilla's {@code 0.5F}, so the model reproduces it exactly.
     */
    static final KnockbackProfile PANDASPIGOT_DEFAULT = KnockbackProfile.of(
            "pandaspigot-default", 2.0D, 0.4D, 0.4D, 0.4000000059604645D, 0.5D, 0.1D);

    private final KnockbackProfile serverKnockback;
    private final KnockbackCalculator calculator = new KnockbackCalculator(() -> {
        throw new IllegalStateException("attacker and victim less than 0.01 block apart: the server drew a random"
                + " direction, which cannot be checked");
    });

    ControlCheck(KnockbackProfile serverKnockback) {
        this.serverKnockback = serverKnockback;
    }

    public static void main(String[] args) throws IOException {
        Path hits = null;
        Path received = null;
        Path report = null;
        KnockbackProfile server = PANDASPIGOT_DEFAULT;
        int minHits = 1;
        for (int i = 0; i < args.length; i++) {
            String value = i + 1 < args.length ? args[i + 1] : null;
            switch (args[i]) {
                case "--hits":
                    hits = Paths.get(require(value, args[i]));
                    i++;
                    break;
                case "--received":
                    received = Paths.get(require(value, args[i]));
                    i++;
                    break;
                case "--report":
                    report = Paths.get(require(value, args[i]));
                    i++;
                    break;
                case "--min-hits":
                    minHits = Integer.parseInt(require(value, args[i]));
                    i++;
                    break;
                case "--server-knockback":
                    server = serverKnockback(require(value, args[i]));
                    i++;
                    break;
                default:
                    usage("unknown argument " + args[i]);
                    return;
            }
        }
        if (hits == null || received == null) {
            usage("--hits and --received are required");
            return;
        }

        Result result = new ControlCheck(server).check(readHits(hits), readReceived(received), readAttacks(received),
                minHits);
        String markdown = result.toMarkdown();
        System.out.println(markdown);
        if (report != null) {
            Files.write(report, markdown.getBytes(StandardCharsets.UTF_8));
        }
        System.exit(result.passed() ? 0 : 1);
    }

    private static void usage(String problem) {
        System.err.println(problem);
        System.err.println("usage: ControlCheck --hits <file or directory> --received <file>"
                + " [--server-knockback pandaspigot|vanilla] [--min-hits N] [--report <file>]");
        System.exit(2);
    }

    private static String require(String value, String option) {
        if (value == null) {
            usage(option + " needs a value");
        }
        return value;
    }

    static KnockbackProfile serverKnockback(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "pandaspigot":
                return PANDASPIGOT_DEFAULT;
            case "vanilla":
                return KnockbackProfile.vanilla();
            default:
                throw new IllegalArgumentException("unknown server knockback " + name + " (pandaspigot or vanilla)");
        }
    }

    static List<RecordedHit> readHits(Path path) throws IOException {
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.list(path)) {
                files.addAll(stream.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                        .sorted().collect(Collectors.toList()));
            }
        } else {
            files.add(path);
        }
        List<RecordedHit> hits = new ArrayList<>();
        for (Path file : files) {
            for (JsonObject json : readJsonLines(file)) {
                if ("melee".equals(json.get("type").getAsString())) {
                    hits.add(RecordedHit.fromJson(json));
                }
            }
        }
        return hits;
    }

    static List<ReceivedVelocity> readReceived(Path path) throws IOException {
        List<ReceivedVelocity> received = new ArrayList<>();
        for (JsonObject json : readJsonLines(path)) {
            if ("velocity".equals(json.get("type").getAsString())) {
                received.add(ReceivedVelocity.fromJson(json));
            }
        }
        return received;
    }

    /** The attacks the bots sent (type "attack" in received.jsonl). */
    static List<BotAttack> readAttacks(Path path) throws IOException {
        List<BotAttack> attacks = new ArrayList<>();
        for (JsonObject json : readJsonLines(path)) {
            if ("attack".equals(json.get("type").getAsString())) {
                attacks.add(BotAttack.fromJson(json));
            }
        }
        return attacks;
    }

    private static List<JsonObject> readJsonLines(Path file) throws IOException {
        List<JsonObject> objects = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.trim().isEmpty()) {
                objects.add(JsonParser.parseString(line).getAsJsonObject());
            }
        }
        return objects;
    }

    /**
     * Gives each received packet to the latest hit on the same entity logged before it (within
     * {@link #MATCH_BEFORE_MILLIS} and {@link #MATCH_AFTER_MILLIS}), then checks every hit. A hit
     * without a packet and a packet without a hit, or a second packet for the same hit, both fail;
     * a missing packet does not shift the packets of the following hits.
     *
     * @param attacks the attacks the bots sent, or null if unknown: every attack must give exactly
     *                one logged hit
     */
    Result check(List<RecordedHit> hits, List<ReceivedVelocity> received, List<BotAttack> attacks, int minHits) {
        List<RecordedHit> sortedHits = new ArrayList<>(hits);
        sortedHits.sort(Comparator.comparingLong(hit -> hit.time));
        List<ReceivedVelocity> sortedPackets = new ArrayList<>(received);
        sortedPackets.sort(Comparator.comparingLong(packet -> packet.time));

        Result result = new Result(minHits, serverKnockback);
        Map<RecordedHit, ReceivedVelocity> pairs = new IdentityHashMap<>();
        List<ReceivedVelocity> unexpected = new ArrayList<>();
        for (ReceivedVelocity packet : sortedPackets) {
            RecordedHit owner = null;
            for (RecordedHit hit : sortedHits) {
                if (hit.time - MATCH_BEFORE_MILLIS > packet.time) {
                    break;
                }
                if (hit.victimEntityId == packet.entityId && packet.time <= hit.time + MATCH_AFTER_MILLIS) {
                    owner = hit;
                }
            }
            if (owner == null || pairs.containsKey(owner)) {
                unexpected.add(packet);
            } else {
                pairs.put(owner, packet);
            }
        }
        for (RecordedHit hit : sortedHits) {
            ReceivedVelocity packet = pairs.get(hit);
            if (packet == null) {
                result.fail(hit, "no velocity packet reached the victim");
            } else {
                checkHit(hit, packet.packet, result);
            }
        }
        for (ReceivedVelocity packet : unexpected) {
            result.unexpected(packet);
        }
        if (attacks != null) {
            if (attacks.size() != hits.size()) {
                result.failures.add("the bots attacked " + attacks.size() + " times but the engine logged "
                        + hits.size() + " hits");
            }
            List<BotAttack> missed = attacksWithoutHit(attacks, sortedHits);
            if (!missed.isEmpty()) {
                List<BotAttack> listed = missed.subList(0, Math.min(missed.size(), LISTED_ATTACKS));
                result.failures.add("no hit logged for " + listed.stream().map(BotAttack::toString)
                        .collect(Collectors.joining(", "))
                        + (missed.size() > listed.size() ? " and " + (missed.size() - listed.size()) + " more" : ""));
            }
        }
        return result;
    }

    Result check(List<RecordedHit> hits, List<ReceivedVelocity> received, int minHits) {
        return check(hits, received, null, minHits);
    }

    /** The attacks followed by no logged hit within {@link #ATTACK_MATCH_MILLIS}; each hit answers one attack. */
    static List<BotAttack> attacksWithoutHit(List<BotAttack> attacks, List<RecordedHit> sortedHits) {
        List<BotAttack> sortedAttacks = new ArrayList<>(attacks);
        sortedAttacks.sort(Comparator.comparingLong(attack -> attack.time));
        boolean[] answered = new boolean[sortedHits.size()];
        List<BotAttack> missed = new ArrayList<>();
        for (BotAttack attack : sortedAttacks) {
            int hit = -1;
            for (int i = 0; i < sortedHits.size() && hit < 0; i++) {
                long delay = sortedHits.get(i).time - attack.time;
                if (!answered[i] && delay >= -MATCH_BEFORE_MILLIS && delay <= ATTACK_MATCH_MILLIS) {
                    hit = i;
                }
            }
            if (hit < 0) {
                missed.add(attack);
            } else {
                answered[hit] = true;
            }
        }
        return missed;
    }

    private void checkHit(RecordedHit hit, VelocityPacket received, Result result) {
        VelocityPacket vanillaPacket;
        try {
            vanillaPacket = VelocityPacket.encode(calculator.compute(KnockbackProfile.vanilla(), hit.hit));
        } catch (IllegalStateException e) {
            result.fail(hit, e.getMessage());
            return;
        }
        int vanillaDifference = received.maxDifference(vanillaPacket);
        if (hit.applied) {
            Vec3 expected = calculator.compute(hit.profile, hit.hit);
            VelocityPacket set = VelocityPacket.encode(hit.velocity);
            if (!expected.equals(hit.velocity)) {
                result.fail(hit, "the engine computed " + hit.velocity + " but the model gives " + expected);
            } else if (!received.equals(set)) {
                result.fail(hit, "the client received " + received + " but the engine set " + set);
            } else {
                result.pass(hit, vanillaDifference);
            }
        } else {
            VelocityPacket serverPacket = VelocityPacket.encode(calculator.compute(serverKnockback, hit.hit));
            if (!received.equals(serverPacket)) {
                result.fail(hit, "the server sent " + received + " but the model with " + serverKnockback.name()
                        + " settings gives " + serverPacket);
            } else if (vanillaDifference > VANILLA_TOLERANCE) {
                result.fail(hit, "the server sent " + received + ", " + vanillaDifference
                        + " units away from exact vanilla " + vanillaPacket);
            } else {
                result.pass(hit, vanillaDifference);
            }
        }
    }

    /** Outcome of a control run. */
    static final class Result {
        private final int minHits;
        private final KnockbackProfile serverKnockback;
        /** Per category: passed, failed, largest difference with exact vanilla (packet units). */
        private final Map<String, int[]> categories = new TreeMap<>();
        private final List<String> failures = new ArrayList<>();
        private int passed;
        private int observed;
        private int overriddenVanilla;
        private int overriddenChanged;
        private int overriddenSprint;
        private int overriddenNoSprint;
        private int enchanted;
        private int airborne;

        Result(int minHits, KnockbackProfile serverKnockback) {
            this.minHits = minHits;
            this.serverKnockback = serverKnockback;
        }

        void pass(RecordedHit hit, int vanillaDifference) {
            passed++;
            int[] stats = categories.computeIfAbsent(hit.category(), key -> new int[3]);
            stats[0]++;
            stats[2] = Math.max(stats[2], vanillaDifference);
            if (hit.hit.knockbackLevel() >= 2) {
                enchanted++;
            }
            if (!hit.victimOnGround) {
                airborne++;
            }
            if (!hit.applied) {
                observed++;
                return;
            }
            if (KnockbackProfile.VANILLA_NAME.equals(hit.profile.name())) {
                overriddenVanilla++;
            } else if (vanillaDifference > 0) {
                overriddenChanged++;
            }
            if (hit.sprinting) {
                overriddenSprint++;
            } else {
                overriddenNoSprint++;
            }
        }

        void fail(RecordedHit hit, String reason) {
            categories.computeIfAbsent(hit.category(), key -> new int[3])[1]++;
            failures.add(hit + ": " + reason);
        }

        void unexpected(ReceivedVelocity packet) {
            failures.add("velocity packet " + packet.packet + " received by entity " + packet.entityId + " at "
                    + packet.time + " matches no logged hit");
        }

        List<String> failures() {
            return failures;
        }

        List<String> coverageProblems() {
            List<String> problems = new ArrayList<>();
            if (passed < minHits) {
                problems.add("only " + passed + " hits passed, at least " + minHits + " expected");
            }
            if (observed == 0) {
                problems.add("no hit in observe mode: the model was not checked against the server's own knockback");
            }
            if (overriddenVanilla == 0) {
                problems.add("no hit with the vanilla profile in override mode");
            }
            if (overriddenChanged == 0) {
                problems.add("no override hit with a non-vanilla profile that changed the packet");
            }
            if (overriddenSprint == 0 || overriddenNoSprint == 0) {
                problems.add("override hits must include sprint and non-sprint hits");
            }
            if (enchanted == 0) {
                problems.add("no hit with a knockback level of 2 or more (Knockback enchantment)");
            }
            if (airborne == 0) {
                problems.add("no hit on a victim in the air");
            }
            return problems;
        }

        boolean passed() {
            return failures.isEmpty() && coverageProblems().isEmpty();
        }

        String toMarkdown() {
            StringBuilder md = new StringBuilder();
            md.append("## RagoCombatEngine control test: ").append(passed() ? "PASSED" : "FAILED").append("\n\n");
            md.append("| Mode / profile / attacker | Passed | Failed | Max difference with exact vanilla"
                    + " (packet units) |\n");
            md.append("| --- | --- | --- | --- |\n");
            for (Map.Entry<String, int[]> entry : categories.entrySet()) {
                int[] stats = entry.getValue();
                md.append("| ").append(entry.getKey()).append(" | ").append(stats[0]).append(" | ")
                        .append(stats[1]).append(" | ").append(stats[2]).append(" |\n");
            }
            md.append('\n');
            md.append("Override hits must reach the client exactly as the engine set them, and match the model"
                    + " exactly. Observe hits must match the model with the server's own settings (")
                    .append(serverKnockback.name()).append(") exactly, and exact vanilla within ")
                    .append(VANILLA_TOLERANCE).append(" unit (1/8000 block per tick).\n\n");
            md.append("Coverage: ").append(enchanted).append(" hits with Knockback enchantment, ")
                    .append(airborne).append(" hits on a victim in the air.\n");
            List<String> problems = new ArrayList<>(failures);
            problems.addAll(coverageProblems());
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
