package gg.rago.tools.kbcontrol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import gg.rago.combat.model.VelocityPacket;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * End-to-end check of kb-recorder (tools/kb-recorder): compares a recording made by the mod in a
 * real Minecraft 1.8.9 client with the hit log of the combat engine on the server that hit it.
 *
 * <ul>
 *   <li>the file is complete: a session line first, an end line last, nothing dropped and no error;</li>
 *   <li>one tick line per client tick, numbered without gaps, all with the same player entity;</li>
 *   <li>every hit the engine logged reached the recording as exactly one velocity packet for the
 *       player, equal to the packet the server sent, and the recording has no other velocity packet
 *       for the player;</li>
 *   <li>every hit has the hurt animation (entity status 2) of the player;</li>
 *   <li>in the last tick line before each hit, the attacker's sprint flag is the one the server used;</li>
 *   <li>every attack the bot sent gave one logged hit.</li>
 * </ul>
 */
public final class RecorderCheck {

    /** Protocol 47 ids of the packets read here. */
    static final int ENTITY_VELOCITY = 0x12;
    static final int ENTITY_STATUS = 0x1A;
    /** Entity status of a hurt living entity (the red flash and the hurt sound). */
    static final int STATUS_HURT = 2;
    /** Line format of the recordings this check reads (Recorder.FORMAT in the mod). */
    static final int FORMAT = 1;
    /**
     * Lowest acceptable client tick rate over the recording. Minecraft catches up missed ticks, so
     * a client that keeps up records 20 per second.
     */
    static final double MIN_TICKS_PER_SECOND = 15.0;

    private RecorderCheck() {
    }

    public static void main(String[] args) throws IOException {
        Path recording = null;
        Path hits = null;
        Path attacks = null;
        Path report = null;
        String label = null;
        String attacker = null;
        int minHits = 1;
        for (int i = 0; i < args.length; i++) {
            String value = i + 1 < args.length ? args[i + 1] : null;
            if (value == null) {
                usage(args[i] + " needs a value");
                return;
            }
            switch (args[i]) {
                case "--recording":
                    recording = Paths.get(value);
                    break;
                case "--hits":
                    hits = Paths.get(value);
                    break;
                case "--attacks":
                    attacks = Paths.get(value);
                    break;
                case "--report":
                    report = Paths.get(value);
                    break;
                case "--label":
                    label = value;
                    break;
                case "--attacker":
                    attacker = value;
                    break;
                case "--min-hits":
                    minHits = Integer.parseInt(value);
                    break;
                default:
                    usage("unknown argument " + args[i]);
                    return;
            }
            i++;
        }
        if (recording == null || hits == null || attacks == null || label == null || attacker == null) {
            usage("--recording, --hits, --attacks, --label and --attacker are required");
            return;
        }

        Result result = check(Recording.read(recording), ControlCheck.readHits(hits),
                ControlCheck.readAttacks(attacks), label, attacker, minHits);
        String markdown = result.toMarkdown();
        System.out.println(markdown);
        if (report != null) {
            Files.write(report, markdown.getBytes(StandardCharsets.UTF_8));
        }
        System.exit(result.passed() ? 0 : 1);
    }

    private static void usage(String problem) {
        System.err.println(problem);
        System.err.println("usage: RecorderCheck --recording <kb-*.jsonl> --hits <file or directory>"
                + " --attacks <file> --label <label> --attacker <name> [--min-hits N] [--report <file>]");
        System.exit(2);
    }

    static Result check(Recording recording, List<RecordedHit> hits, List<BotAttack> attacks, String label,
            String attacker, int minHits) {
        Result result = new Result(minHits);
        result.problems.addAll(recording.problems);
        checkSession(recording, label, result);
        Integer player = checkTicks(recording, result);
        if (player == null) {
            return result;
        }

        List<RecordedHit> sortedHits = new ArrayList<>(hits);
        sortedHits.sort(Comparator.comparingLong(hit -> hit.time));
        List<Packet> velocities = new ArrayList<>();
        List<Packet> hurts = new ArrayList<>();
        for (Packet packet : recording.packets) {
            if (!packet.inbound) {
                result.sentPackets++;
                continue;
            }
            try {
                if (packet.id == ENTITY_VELOCITY && packet.velocityEntity() == player) {
                    packet.velocity();
                    velocities.add(packet);
                } else if (packet.id == ENTITY_STATUS && packet.statusEntity() == player
                        && packet.status() == STATUS_HURT) {
                    hurts.add(packet);
                }
            } catch (IllegalArgumentException e) {
                result.problems.add("packet " + packet.id + " recorded at " + packet.ms + " cannot be decoded: "
                        + e.getMessage());
            }
        }
        if (result.sentPackets == 0) {
            result.problems.add("no packet sent by the client was recorded");
        }
        // Lines are written in the order the game and network threads submit them, which can differ
        // slightly from the order of their times.
        velocities.sort(Comparator.comparingLong(packet -> packet.ms));
        hurts.sort(Comparator.comparingLong(packet -> packet.ms));

        Map<RecordedHit, Packet> velocityOf = pair(sortedHits, velocities, player, result, "velocity packet");
        Map<RecordedHit, Packet> hurtOf = pair(sortedHits, hurts, player, null, null);
        for (RecordedHit hit : sortedHits) {
            if (hit.victimEntityId != player) {
                result.fail(hit, "the engine logged a hit on entity " + hit.victimEntityId + ", not on the player "
                        + player);
                continue;
            }
            Packet velocity = velocityOf.get(hit);
            String problem = null;
            if (velocity == null) {
                problem = "no velocity packet for the player in the recording";
            } else if (!velocity.velocity().equals(hit.packet)) {
                problem = "the recording has " + velocity.velocity() + " but the server sent " + hit.packet;
            } else if (!hurtOf.containsKey(hit)) {
                problem = "no hurt animation (entity status 2) for the player in the recording";
            } else {
                problem = checkAttackerSprint(recording, hit, attacker);
            }
            if (problem == null) {
                result.pass(hit);
            } else {
                result.fail(hit, problem);
            }
        }

        if (attacks.size() != hits.size()) {
            result.problems.add("the bot attacked " + attacks.size() + " times but the engine logged " + hits.size()
                    + " hits");
        }
        List<BotAttack> missed = ControlCheck.attacksWithoutHit(attacks, sortedHits);
        if (!missed.isEmpty()) {
            result.problems.add("no hit logged for " + missed.stream().limit(ControlCheck.LISTED_ATTACKS)
                    .map(BotAttack::toString).collect(Collectors.joining(", "))
                    + (missed.size() > ControlCheck.LISTED_ATTACKS
                            ? " and " + (missed.size() - ControlCheck.LISTED_ATTACKS) + " more" : ""));
        }
        return result;
    }

    private static void checkSession(Recording recording, String label, Result result) {
        JsonObject session = recording.session;
        if (session == null) {
            result.problems.add("the recording does not start with a session line");
        } else {
            if (!session.has("format") || session.get("format").getAsInt() != FORMAT) {
                result.problems.add("session line format " + session.get("format") + ", expected " + FORMAT);
            }
            if (!session.has("label") || !label.equals(session.get("label").getAsString())) {
                result.problems.add("session label " + session.get("label") + ", expected \"" + label + "\"");
            }
            if (!session.has("player") || session.get("player").isJsonNull()) {
                result.problems.add("the session line has no player name");
            } else {
                result.player = session.get("player").getAsString();
            }
        }
        JsonObject end = recording.end;
        if (end == null) {
            result.problems.add("the recording does not finish with an end line: the file was not closed");
        } else {
            if (end.get("dropped").getAsLong() != 0) {
                result.problems.add(end.get("dropped").getAsLong() + " lines were dropped");
            }
            if (end.get("errors").getAsLong() != 0) {
                result.problems.add(end.get("errors").getAsLong() + " recording errors");
            }
        }
        for (JsonObject error : recording.errors) {
            result.problems.add("recording error in " + error.get("where") + ": " + error.get("error"));
        }
    }

    /** Checks the tick lines and returns the player's entity id, or null if there is none. */
    private static Integer checkTicks(Recording recording, Result result) {
        List<Tick> ticks = recording.ticks;
        result.ticks = ticks.size();
        if (ticks.isEmpty()) {
            result.problems.add("no tick line");
            return null;
        }
        for (int i = 0; i < ticks.size(); i++) {
            if (ticks.get(i).tick != i + 1) {
                result.problems.add("tick line " + (i + 1) + " is numbered " + ticks.get(i).tick);
                break;
            }
        }
        if (recording.end != null && recording.end.get("ticks").getAsInt() != ticks.size()) {
            result.problems.add("the end line counts " + recording.end.get("ticks") + " ticks, the file has "
                    + ticks.size());
        }
        int player = ticks.get(0).entityId;
        for (Tick tick : ticks) {
            if (tick.entityId != player) {
                result.problems.add("the player's entity id changed from " + player + " to " + tick.entityId
                        + " at tick " + tick.tick);
                break;
            }
        }
        long millis = ticks.get(ticks.size() - 1).ms - ticks.get(0).ms;
        if (ticks.size() > 1 && millis > 0) {
            result.ticksPerSecond = (ticks.size() - 1) * 1000.0 / millis;
            if (result.ticksPerSecond < MIN_TICKS_PER_SECOND) {
                result.problems.add(String.format(Locale.ROOT,
                        "the client recorded %.1f ticks per second, at least %.0f expected",
                        result.ticksPerSecond, MIN_TICKS_PER_SECOND));
            }
        }
        result.playerEntity = player;
        return player;
    }

    /**
     * Gives each packet to the latest hit logged before it, within the windows of
     * {@link ControlCheck}, as ControlCheck does for the bots. With a result, a packet that matches
     * no hit, or a second packet for the same hit, is a problem.
     */
    private static Map<RecordedHit, Packet> pair(List<RecordedHit> sortedHits, List<Packet> packets, int player,
            Result result, String what) {
        Map<RecordedHit, Packet> pairs = new IdentityHashMap<>();
        for (Packet packet : packets) {
            RecordedHit owner = null;
            for (RecordedHit hit : sortedHits) {
                if (hit.time - ControlCheck.MATCH_BEFORE_MILLIS > packet.ms) {
                    break;
                }
                if (hit.victimEntityId == player && packet.ms <= hit.time + ControlCheck.MATCH_AFTER_MILLIS) {
                    owner = hit;
                }
            }
            if (owner == null || pairs.containsKey(owner)) {
                if (result != null) {
                    result.problems.add(what + " " + packet.velocity() + " recorded at " + packet.ms
                            + " matches no logged hit");
                }
            } else {
                pairs.put(owner, packet);
            }
        }
        return pairs;
    }

    /** Null if the last tick line before the hit shows the attacker with the server's sprint flag. */
    private static String checkAttackerSprint(Recording recording, RecordedHit hit, String attacker) {
        Tick before = null;
        for (Tick tick : recording.ticks) {
            if (tick.ms > hit.time) {
                break;
            }
            before = tick;
        }
        if (before == null) {
            return "no tick line before the hit";
        }
        Boolean sprint = before.sprintOf(attacker);
        if (sprint == null) {
            return "the attacker " + attacker + " is not in the tick line before the hit (tick " + before.tick + ")";
        }
        if (sprint != hit.sprinting) {
            return "the tick line before the hit shows the attacker " + (sprint ? "sprinting" : "not sprinting")
                    + " but the server used " + (hit.sprinting ? "a sprint hit" : "a hit without sprint");
        }
        return null;
    }

    /** The lines of a kb-recorder recording that this check reads. */
    static final class Recording {
        JsonObject session;
        JsonObject end;
        final List<JsonObject> errors = new ArrayList<>();
        final List<Tick> ticks = new ArrayList<>();
        final List<Packet> packets = new ArrayList<>();
        /** Lines that break the format. */
        final List<String> problems = new ArrayList<>();

        static Recording read(Path file) throws IOException {
            return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
        }

        static Recording parse(List<String> lines) {
            Recording recording = new Recording();
            int number = 0;
            for (String text : lines) {
                number++;
                if (text.trim().isEmpty()) {
                    continue;
                }
                try {
                    recording.add(number, JsonParser.parseString(text).getAsJsonObject());
                } catch (JsonParseException | IllegalStateException | ClassCastException
                        | UnsupportedOperationException | NullPointerException | IllegalArgumentException e) {
                    recording.problems.add("line " + number + " cannot be read: " + e.getMessage());
                }
            }
            return recording;
        }

        private void add(int number, JsonObject line) {
            for (String field : new String[] {"type", "ms", "nano", "tick"}) {
                if (!line.has(field)) {
                    problems.add("line " + number + " has no " + field);
                    return;
                }
            }
            if (end != null) {
                problems.add("line " + number + " comes after the end line");
            }
            String type = line.get("type").getAsString();
            if (session == null && !"session".equals(type)) {
                problems.add("line " + number + " (" + type + ") comes before the session line");
            }
            switch (type) {
                case "session":
                    if (session != null) {
                        problems.add("line " + number + " is a second session line");
                    }
                    session = line;
                    break;
                case "end":
                    end = line;
                    break;
                case "error":
                    errors.add(line);
                    break;
                case "tick":
                    ticks.add(Tick.fromJson(line));
                    break;
                case "packet":
                    packets.add(Packet.fromJson(line));
                    break;
                default:
                    break;
            }
        }
    }

    /** A tick line: the player's entity and the sprint flag of the players around. */
    static final class Tick {
        final long ms;
        final int tick;
        final int entityId;
        final Map<String, Boolean> sprintByName = new TreeMap<>();

        Tick(long ms, int tick, int entityId) {
            this.ms = ms;
            this.tick = tick;
            this.entityId = entityId;
        }

        static Tick fromJson(JsonObject json) {
            Tick tick = new Tick(json.get("ms").getAsLong(), json.get("tick").getAsInt(), json.get("eid").getAsInt());
            JsonArray players = json.getAsJsonArray("players");
            if (players != null) {
                for (JsonElement element : players) {
                    JsonObject other = element.getAsJsonObject();
                    tick.sprintByName.put(other.get("name").getAsString(), other.get("sprint").getAsBoolean());
                }
            }
            return tick;
        }

        Boolean sprintOf(String name) {
            return sprintByName.get(name);
        }
    }

    /** A packet line, with the decoding of the two packets this check reads. */
    static final class Packet {
        final long ms;
        final boolean inbound;
        final int id;
        final byte[] data;

        Packet(long ms, boolean inbound, int id, byte[] data) {
            this.ms = ms;
            this.inbound = inbound;
            this.id = id;
            this.data = data;
        }

        static Packet fromJson(JsonObject json) {
            String dir = json.get("dir").getAsString();
            if (!"in".equals(dir) && !"out".equals(dir)) {
                throw new IllegalArgumentException("packet direction " + dir);
            }
            return new Packet(json.get("ms").getAsLong(), "in".equals(dir), json.get("id").getAsInt(),
                    Base64.getDecoder().decode(json.get("data").getAsString()));
        }

        /** Entity velocity: VarInt entity id, then three big-endian shorts. */
        int velocityEntity() {
            return readVarInt(new int[] {0});
        }

        VelocityPacket velocity() {
            int[] offset = {0};
            readVarInt(offset);
            if (data.length != offset[0] + 6) {
                throw new IllegalArgumentException("entity velocity packet of " + data.length + " bytes");
            }
            return new VelocityPacket(readShort(offset[0]), readShort(offset[0] + 2), readShort(offset[0] + 4));
        }

        /** Entity status: 4-byte big-endian entity id, then the status byte. */
        int statusEntity() {
            if (data.length != 5) {
                throw new IllegalArgumentException("entity status packet of " + data.length + " bytes");
            }
            return (data[0] & 0xFF) << 24 | (data[1] & 0xFF) << 16 | (data[2] & 0xFF) << 8 | data[3] & 0xFF;
        }

        int status() {
            return data[4];
        }

        private int readShort(int offset) {
            return (short) ((data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF);
        }

        private int readVarInt(int[] offset) {
            int value = 0;
            for (int i = 0; i < 5; i++) {
                if (offset[0] >= data.length) {
                    throw new IllegalArgumentException("truncated VarInt");
                }
                int b = data[offset[0]++];
                value |= (b & 0x7F) << (7 * i);
                if ((b & 0x80) == 0) {
                    return value;
                }
            }
            throw new IllegalArgumentException("VarInt longer than 5 bytes");
        }
    }

    /** Outcome of the check. */
    static final class Result {
        private final int minHits;
        final List<String> problems = new ArrayList<>();
        /** Per category (mode / profile / attacker sprint): passed, failed. */
        private final Map<String, int[]> categories = new TreeMap<>();
        private int passed;
        private int observed;
        private int applied;
        private int sprint;
        private int noSprint;
        int ticks;
        double ticksPerSecond;
        int sentPackets;
        String player;
        int playerEntity;

        Result(int minHits) {
            this.minHits = minHits;
        }

        void pass(RecordedHit hit) {
            passed++;
            categories.computeIfAbsent(hit.category(), key -> new int[2])[0]++;
            if (hit.applied) {
                applied++;
            } else {
                observed++;
            }
            if (hit.sprinting) {
                sprint++;
            } else {
                noSprint++;
            }
        }

        void fail(RecordedHit hit, String reason) {
            categories.computeIfAbsent(hit.category(), key -> new int[2])[1]++;
            problems.add(hit + ": " + reason);
        }

        List<String> coverageProblems() {
            List<String> coverage = new ArrayList<>();
            if (passed < minHits) {
                coverage.add("only " + passed + " hits passed, at least " + minHits + " expected");
            }
            if (observed == 0) {
                coverage.add("no hit in observe mode (the server's own knockback)");
            }
            if (applied == 0) {
                coverage.add("no hit in override mode (knockback set by the engine)");
            }
            if (sprint == 0 || noSprint == 0) {
                coverage.add("the hits must include sprint and non-sprint hits");
            }
            return coverage;
        }

        boolean passed() {
            return problems.isEmpty() && coverageProblems().isEmpty();
        }

        String toMarkdown() {
            StringBuilder md = new StringBuilder();
            md.append("## kb-recorder end-to-end test: ").append(passed() ? "PASSED" : "FAILED").append("\n\n");
            md.append("A Minecraft 1.8.9 client with the mod (player ").append(player).append(", entity ")
                    .append(playerEntity).append(") recorded ").append(ticks).append(" ticks");
            if (ticksPerSecond > 0) {
                md.append(String.format(Locale.ROOT, " (%.1f per second)", ticksPerSecond));
            }
            md.append(" and ").append(sentPackets).append(" packets it sent.\n\n");
            md.append("| Mode / profile / attacker | Passed | Failed |\n");
            md.append("| --- | --- | --- |\n");
            for (Map.Entry<String, int[]> entry : categories.entrySet()) {
                md.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()[0]).append(" | ")
                        .append(entry.getValue()[1]).append(" |\n");
            }
            md.append("\nA hit passes when the recording has exactly one velocity packet for it, equal to the packet"
                    + " the server sent, the player's hurt animation, and the attacker's sprint flag the server"
                    + " used.\n");
            List<String> all = new ArrayList<>(problems);
            all.addAll(coverageProblems());
            if (!all.isEmpty()) {
                md.append("\n### Problems\n\n");
                for (String problem : all) {
                    md.append("- ").append(problem).append('\n');
                }
            }
            return md.toString();
        }
    }
}
