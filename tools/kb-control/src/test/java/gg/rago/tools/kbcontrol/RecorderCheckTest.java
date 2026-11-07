package gg.rago.tools.kbcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.combat.model.KnockbackProfile;
import gg.rago.combat.model.MeleeHit;
import gg.rago.combat.model.Vec3;
import gg.rago.combat.model.VelocityPacket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecorderCheckTest {

    /**
     * A packet line exactly as kb-recorder writes it: RecorderTest in tools/kb-recorder checks that
     * the mod writes this line for the same packet, so the mod and this check cannot drift apart.
     */
    static final String MOD_VELOCITY_LINE = "{\"type\":\"packet\",\"ms\":1794052800050,\"nano\":50001000,\"tick\":1,"
            + "\"dir\":\"in\",\"id\":18,\"name\":\"entity_velocity\",\"data\":\"BwAKABQAHg==\"}";

    private static final int PLAYER = 7;
    private static final String ATTACKER = "RagoAttacker";
    private static final String LABEL = "e2e";
    private static final long START = 1_794_052_800_000L;
    private static final KnockbackProfile CONTROL_ALT =
            KnockbackProfile.of("control-alt", 1.7D, 0.36D, 0.37D, 0.39D, 0.45D, 0.085D);
    private static final Vec3 STANDING = new Vec3(0.0D, -0.0784000015258789D, 0.0D);

    /** Hits of a small correct run: observe and override, with and without sprint. */
    private final List<RecordedHit> hits = new ArrayList<>();
    private final List<BotAttack> attacks = new ArrayList<>();

    RecorderCheckTest() {
        hit(START + 1_000L, "observe", KnockbackProfile.vanilla(), false, new VelocityPacket(-1200, 3200, 2400), false);
        hit(START + 2_000L, "observe", KnockbackProfile.vanilla(), true, new VelocityPacket(-5100, 3608, 900), false);
        hit(START + 3_000L, "override", CONTROL_ALT, false, new VelocityPacket(2000, 2960, -1500), true);
        hit(START + 4_000L, "override", CONTROL_ALT, true, new VelocityPacket(4400, 3640, -3000), true);
    }

    private void hit(long time, String mode, KnockbackProfile profile, boolean sprinting, VelocityPacket packet,
            boolean applied) {
        MeleeHit input = new MeleeHit(2.0D, 0.5D, 90.0F, sprinting ? 1 : 0, 0.5D, 0.5D, STANDING);
        hits.add(new RecordedHit(time, mode, profile, PLAYER, sprinting, true, input,
                new Vec3(packet.x() / 8000.0D, packet.y() / 8000.0D, packet.z() / 8000.0D), packet, applied));
        attacks.add(new BotAttack(time - 2L, mode + " " + time));
    }

    /** What the mod records for these hits: a tick every 50 ms, a velocity and a hurt status per hit. */
    private final class RecordingBuilder {
        final List<String> lines = new ArrayList<>();
        boolean closed = true;
        int dropped;
        boolean hurt = true;
        Integer skippedTick;
        Boolean attackerSprintOverride;
        final List<String> extra = new ArrayList<>();

        List<String> build() {
            List<String> out = new ArrayList<>();
            out.add("{\"type\":\"session\",\"ms\":" + START + ",\"nano\":0,\"tick\":0,\"format\":1,\"mod\":\"0.1.0\","
                    + "\"minecraft\":\"1.8.9\",\"label\":\"" + LABEL + "\",\"player\":\"Player123\",\"uuid\":\"u\","
                    + "\"server\":null}");
            int tick = 0;
            int written = 0;
            for (long ms = START + 50L; ms <= START + 5_000L; ms += 50L) {
                tick++;
                for (RecordedHit hit : hits) {
                    if (hit.time > ms - 50L && hit.time <= ms) {
                        long at = hit.time + 3L;
                        out.add(packetLine(at, tick, true, RecorderCheck.ENTITY_VELOCITY, velocity(PLAYER, hit.packet)));
                        if (hurt) {
                            out.add(packetLine(at, tick, true, RecorderCheck.ENTITY_STATUS, status(PLAYER, 2)));
                        }
                    }
                }
                if (skippedTick != null && skippedTick == tick) {
                    continue;
                }
                written++;
                boolean sprint = attackerSprintAt(ms);
                if (attackerSprintOverride != null) {
                    sprint = attackerSprintOverride;
                }
                out.add("{\"type\":\"tick\",\"ms\":" + ms + ",\"nano\":0,\"tick\":" + tick + ",\"eid\":" + PLAYER
                        + ",\"x\":0.5,\"players\":[{\"eid\":8,\"name\":\"" + ATTACKER + "\",\"sprint\":" + sprint
                        + "}]}");
                if (tick % 20 == 0) {
                    out.add(packetLine(ms, tick, false, 0x00, new byte[] {1}));
                }
            }
            out.addAll(extra);
            if (closed) {
                out.add("{\"type\":\"end\",\"ms\":" + (START + 5_100L) + ",\"nano\":0,\"tick\":" + written
                        + ",\"ticks\":" + written + ",\"dropped\":" + dropped + ",\"errors\":0}");
            }
            return out;
        }

        /** The attacker sprints from 600 ms before a sprint hit until the hit. */
        private boolean attackerSprintAt(long ms) {
            for (RecordedHit hit : hits) {
                if (hit.sprinting && ms <= hit.time && ms > hit.time - 600L) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String packetLine(long ms, int tick, boolean inbound, int id, byte[] data) {
        return "{\"type\":\"packet\",\"ms\":" + ms + ",\"nano\":0,\"tick\":" + tick + ",\"dir\":\""
                + (inbound ? "in" : "out") + "\",\"id\":" + id + ",\"name\":\"x\",\"data\":\""
                + Base64.getEncoder().encodeToString(data) + "\"}";
    }

    private static byte[] velocity(int entity, VelocityPacket packet) {
        // Entity ids below 128 take one VarInt byte.
        return new byte[] {(byte) entity, (byte) (packet.x() >> 8), (byte) packet.x(), (byte) (packet.y() >> 8),
            (byte) packet.y(), (byte) (packet.z() >> 8), (byte) packet.z()};
    }

    private static byte[] status(int entity, int status) {
        return new byte[] {(byte) (entity >>> 24), (byte) (entity >>> 16), (byte) (entity >>> 8), (byte) entity,
            (byte) status};
    }

    private RecorderCheck.Result check(RecordingBuilder builder) {
        return check(builder.build(), hits, attacks);
    }

    private static RecorderCheck.Result check(List<String> lines, List<RecordedHit> hits, List<BotAttack> attacks) {
        return RecorderCheck.check(RecorderCheck.Recording.parse(lines), hits, attacks, LABEL, ATTACKER, hits.size());
    }

    private static boolean mentions(RecorderCheck.Result result, String text) {
        return result.problems.stream().anyMatch(problem -> problem.contains(text))
                || result.coverageProblems().stream().anyMatch(problem -> problem.contains(text));
    }

    @Test
    void aCorrectRecordingPasses() {
        RecorderCheck.Result result = check(new RecordingBuilder());

        assertTrue(result.passed(), result::toMarkdown);
        assertTrue(result.toMarkdown().startsWith("## kb-recorder end-to-end test: PASSED"));
        assertEquals(PLAYER, result.playerEntity);
        assertEquals("Player123", result.player);
        assertEquals(20.0D, result.ticksPerSecond, 1e-9);
    }

    @Test
    void readsTheVelocityLineTheModWrites() {
        RecorderCheck.Recording recording = RecorderCheck.Recording.parse(Collections.singletonList(MOD_VELOCITY_LINE));

        assertEquals(1, recording.packets.size());
        RecorderCheck.Packet packet = recording.packets.get(0);
        assertEquals(1794052800050L, packet.ms);
        assertTrue(packet.inbound);
        assertEquals(RecorderCheck.ENTITY_VELOCITY, packet.id);
        assertEquals(7, packet.velocityEntity());
        assertEquals(new VelocityPacket(10, 20, 30), packet.velocity());
    }

    @Test
    void decodesNegativeVelocitiesAndLongEntityIds() {
        byte[] data = {(byte) 0xAC, 0x02, (byte) 0xEC, 0x78, 0x0E, 0x18, (byte) 0xFF, (byte) 0xFF};
        RecorderCheck.Packet packet = new RecorderCheck.Packet(0L, true, RecorderCheck.ENTITY_VELOCITY, data);

        assertEquals(300, packet.velocityEntity());
        assertEquals(new VelocityPacket(-5000, 3608, -1), packet.velocity());
    }

    @Test
    void aVelocityThatDiffersFromTheServersFails() {
        RecordingBuilder builder = new RecordingBuilder();
        List<String> lines = builder.build();
        RecordedHit first = hits.get(0);
        hits.set(0, new RecordedHit(first.time, first.mode, first.profile, first.victimEntityId, first.sprinting,
                first.victimOnGround, first.hit, first.velocity, new VelocityPacket(-1201, 3200, 2400), first.applied));

        RecorderCheck.Result result = check(lines, hits, attacks);

        assertFalse(result.passed());
        assertTrue(mentions(result, "but the server sent [-1201, 3200, 2400]"), result::toMarkdown);
    }

    @Test
    void aMissingVelocityAndAnExtraOneBothFail() {
        RecordingBuilder builder = new RecordingBuilder();
        builder.extra.add(packetLine(START + 4_500L, 90, true, RecorderCheck.ENTITY_VELOCITY,
                velocity(PLAYER, new VelocityPacket(1, 2, 3))));
        List<String> lines = builder.build();
        List<RecordedHit> withMissing = new ArrayList<>(hits);
        MeleeHit input = new MeleeHit(2.0D, 0.5D, 90.0F, 0, 0.5D, 0.5D, STANDING);
        withMissing.add(new RecordedHit(START + 1_500L, "observe", KnockbackProfile.vanilla(), PLAYER, false, true,
                input, STANDING, new VelocityPacket(0, 0, 0), false));
        List<BotAttack> moreAttacks = new ArrayList<>(attacks);
        moreAttacks.add(new BotAttack(START + 1_498L, "extra"));

        RecorderCheck.Result result = check(lines, withMissing, moreAttacks);

        assertFalse(result.passed());
        assertTrue(mentions(result, "no velocity packet for the player"), result::toMarkdown);
        assertTrue(mentions(result, "[1, 2, 3] recorded at"), result::toMarkdown);
    }

    @Test
    void velocitiesOfOtherEntitiesAreIgnored() {
        RecordingBuilder builder = new RecordingBuilder();
        builder.extra.add(packetLine(START + 4_500L, 90, true, RecorderCheck.ENTITY_VELOCITY,
                velocity(8, new VelocityPacket(1, 2, 3))));

        assertTrue(check(builder).passed());
    }

    @Test
    void everyHitNeedsTheHurtAnimation() {
        RecordingBuilder builder = new RecordingBuilder();
        builder.hurt = false;

        RecorderCheck.Result result = check(builder);

        assertFalse(result.passed());
        assertTrue(mentions(result, "no hurt animation"), result::toMarkdown);
    }

    @Test
    void theAttackerSprintFlagMustBeTheServers() {
        RecordingBuilder builder = new RecordingBuilder();
        builder.attackerSprintOverride = false;

        RecorderCheck.Result result = check(builder);

        assertFalse(result.passed());
        assertTrue(mentions(result, "shows the attacker not sprinting but the server used a sprint hit"),
                result::toMarkdown);
    }

    @Test
    void anUnclosedFileFails() {
        RecordingBuilder builder = new RecordingBuilder();
        builder.closed = false;

        RecorderCheck.Result result = check(builder);

        assertFalse(result.passed());
        assertTrue(mentions(result, "does not finish with an end line"), result::toMarkdown);
    }

    @Test
    void droppedLinesFail() {
        RecordingBuilder builder = new RecordingBuilder();
        builder.dropped = 3;

        assertTrue(mentions(check(builder), "3 lines were dropped"));
    }

    @Test
    void aMissingTickLineFails() {
        RecordingBuilder builder = new RecordingBuilder();
        builder.skippedTick = 30;

        RecorderCheck.Result result = check(builder);

        assertFalse(result.passed());
        assertTrue(mentions(result, "tick line 30 is numbered 31"), result::toMarkdown);
    }

    @Test
    void aWrongLabelAndUnreadableLinesFail() {
        List<String> lines = new ArrayList<>(new RecordingBuilder().build());
        lines.set(0, lines.get(0).replace("\"label\":\"e2e\"", "\"label\":\"other\""));
        lines.add(1, "not json");

        RecorderCheck.Result result = check(lines, hits, attacks);

        assertTrue(mentions(result, "session label \"other\""), result::toMarkdown);
        assertTrue(mentions(result, "line 2 cannot be read"), result::toMarkdown);
    }

    @Test
    void everyAttackMustGiveAHit() {
        List<BotAttack> moreAttacks = new ArrayList<>(attacks);
        moreAttacks.add(new BotAttack(START + 4_600L, "observe-vanilla #9 (no sprint)"));

        RecorderCheck.Result result = check(new RecordingBuilder().build(), hits, moreAttacks);

        assertFalse(result.passed());
        assertTrue(mentions(result, "the bot attacked 5 times but the engine logged 4 hits"), result::toMarkdown);
        assertTrue(mentions(result, "no hit logged for observe-vanilla #9"), result::toMarkdown);
    }

    @Test
    void observeAndOverrideHitsWithAndWithoutSprintAreRequired() {
        hits.subList(2, 4).clear();
        attacks.subList(2, 4).clear();

        RecorderCheck.Result result = check(new RecordingBuilder());

        assertFalse(result.passed());
        assertEquals(Arrays.asList("no hit in override mode (knockback set by the engine)"),
                result.coverageProblems());
    }
}
