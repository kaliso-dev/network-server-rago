package gg.rago.tools.kbcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.combat.model.KnockbackCalculator;
import gg.rago.combat.model.KnockbackProfile;
import gg.rago.combat.model.MeleeHit;
import gg.rago.combat.model.Vec3;
import gg.rago.combat.model.VelocityPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ControlCheckTest {

    private static final KnockbackProfile CONTROL_ALT =
            KnockbackProfile.of("control-alt", 1.7D, 0.36D, 0.37D, 0.39D, 0.45D, 0.085D);
    private static final Vec3 STANDING = new Vec3(0.0D, (0.0D - 0.08D) * 0.9800000190734863D, 0.0D);
    private static final Vec3 RISING = new Vec3(0.02D, 0.33319999363422365D, -0.01D);
    private static final int VICTIM = 42;

    private final KnockbackCalculator model = new KnockbackCalculator(() -> {
        throw new AssertionError("test hits never put the attacker on the victim");
    });
    private final ControlCheck pandaSpigot = new ControlCheck(ControlCheck.PANDASPIGOT_DEFAULT);

    /** A small run that covers every phase of the real control test, with the packets a correct server sends. */
    private final List<RecordedHit> hits = new ArrayList<>();
    private final List<ReceivedVelocity> received = new ArrayList<>();

    ControlCheckTest() {
        observe(1_000L, new MeleeHit(0.5D, 0.5D, 10.0F, 0, 0.9D, 2.8D, STANDING), true);
        observe(2_000L, new MeleeHit(3.2D, -1.0D, -95.5F, 1, 1.1D, -0.4D, STANDING), true);
        observe(3_000L, new MeleeHit(-2.0D, 4.0D, 181.0F, 3, -0.2D, 2.5D, RISING), false);
        override(4_000L, KnockbackProfile.vanilla(), new MeleeHit(0.0D, 0.0D, 45.0F, 0, 1.5D, 1.5D, STANDING), true);
        override(5_000L, KnockbackProfile.vanilla(), new MeleeHit(0.0D, 0.0D, 45.0F, 1, -1.5D, 2.0D, STANDING), true);
        override(6_000L, CONTROL_ALT, new MeleeHit(5.0D, 5.0D, -30.0F, 0, 6.2D, 7.1D, STANDING), true);
        override(7_000L, CONTROL_ALT, new MeleeHit(5.0D, 5.0D, 120.0F, 1, 3.0D, 4.4D, STANDING), true);
        override(8_000L, CONTROL_ALT, new MeleeHit(5.0D, 5.0D, 0.0F, 3, 5.3D, 7.9D, RISING), false);
    }

    private void observe(long time, MeleeHit hit, boolean onGround) {
        hits.add(record(time, "observe", KnockbackProfile.vanilla(), hit, onGround, false));
        Vec3 server = model.compute(ControlCheck.PANDASPIGOT_DEFAULT, hit);
        received.add(new ReceivedVelocity(time + 3L, VICTIM, VelocityPacket.encode(server)));
    }

    private void override(long time, KnockbackProfile profile, MeleeHit hit, boolean onGround) {
        RecordedHit recorded = record(time, "override", profile, hit, onGround, true);
        hits.add(recorded);
        received.add(new ReceivedVelocity(time + 3L, VICTIM, recorded.packet));
    }

    /** In this test data, levels 1 and 3 are sprint hits (3 = Knockback II and sprint). */
    private RecordedHit record(long time, String mode, KnockbackProfile profile, MeleeHit hit, boolean onGround,
            boolean applied) {
        Vec3 velocity = model.compute(profile, hit);
        boolean sprinting = hit.knockbackLevel() % 2 == 1;
        return new RecordedHit(time, mode, profile, VICTIM, sprinting, onGround, hit, velocity,
                VelocityPacket.encode(velocity), applied);
    }

    private ControlCheck.Result check() {
        return pandaSpigot.check(hits, received, hits.size());
    }

    @Test
    void aCorrectRunPasses() {
        ControlCheck.Result result = check();

        assertTrue(result.passed(), result::toMarkdown);
        assertTrue(result.toMarkdown().startsWith("## RagoCombatEngine control test: PASSED"));
    }

    @Test
    void anOverridePacketMustReachTheClientUnchanged() {
        ReceivedVelocity sent = received.get(5);
        VelocityPacket changed = new VelocityPacket(sent.packet.x() + 1, sent.packet.y(), sent.packet.z());
        received.set(5, new ReceivedVelocity(sent.time, VICTIM, changed));

        ControlCheck.Result result = check();

        assertFalse(result.passed());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).contains("the client received"), result::toMarkdown);
    }

    @Test
    void theEngineVelocityMustBeTheModelsExactly() {
        RecordedHit good = hits.get(6);
        Vec3 off = new Vec3(Math.nextUp(good.velocity.x()), good.velocity.y(), good.velocity.z());
        hits.set(6, new RecordedHit(good.time, good.mode, good.profile, VICTIM, good.sprinting, good.victimOnGround,
                good.hit, off, VelocityPacket.encode(off), true));

        ControlCheck.Result result = check();

        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).contains("the engine computed"), result::toMarkdown);
    }

    @Test
    void observeHitsMustMatchTheServerSettingsExactly() {
        MeleeHit hit = hitWherePandaSpigotDefaultsAndVanillaDiffer();
        VelocityPacket vanilla = VelocityPacket.encode(model.compute(KnockbackProfile.vanilla(), hit));
        VelocityPacket panda = VelocityPacket.encode(model.compute(ControlCheck.PANDASPIGOT_DEFAULT, hit));
        assertEquals(1, vanilla.maxDifference(panda));

        // A PandaSpigot server sends its own packet: it passes, one unit away from exact vanilla.
        List<RecordedHit> one = new ArrayList<>();
        one.add(record(1_000L, "observe", KnockbackProfile.vanilla(), hit, true, false));
        List<ReceivedVelocity> fromPanda = new ArrayList<>();
        fromPanda.add(new ReceivedVelocity(1_001L, VICTIM, panda));
        assertTrue(pandaSpigot.check(one, fromPanda, 1).failures().isEmpty());

        // The exact vanilla packet is not what a PandaSpigot server sends.
        List<ReceivedVelocity> fromVanilla = new ArrayList<>();
        fromVanilla.add(new ReceivedVelocity(1_001L, VICTIM, vanilla));
        assertFalse(pandaSpigot.check(one, fromVanilla, 1).failures().isEmpty());
        ControlCheck vanillaServer = new ControlCheck(ControlCheck.serverKnockback("vanilla"));
        assertTrue(vanillaServer.check(one, fromVanilla, 1).failures().isEmpty());
    }

    @Test
    void observeHitsMustStayWithinOneUnitOfVanilla() {
        MeleeHit hit = new MeleeHit(0.0D, 0.0D, 0.0F, 0, 0.0D, 3.0D, STANDING);
        KnockbackProfile strongServer = KnockbackProfile.of("strong", 2.0D, 0.5D, 0.4D, 0.4000000059604645D, 0.5D, 0.1D);
        List<RecordedHit> one = new ArrayList<>();
        one.add(record(1_000L, "observe", KnockbackProfile.vanilla(), hit, true, false));
        List<ReceivedVelocity> packets = new ArrayList<>();
        packets.add(new ReceivedVelocity(1_001L, VICTIM, VelocityPacket.encode(model.compute(strongServer, hit))));

        List<String> failures = new ControlCheck(strongServer).check(one, packets, 1).failures();

        assertEquals(1, failures.size());
        assertTrue(failures.get(0).contains("units away from exact vanilla"), failures::toString);
    }

    @Test
    void aMissingPacketFails() {
        received.remove(2);

        ControlCheck.Result result = check();

        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).contains("no velocity packet reached the victim"), result::toMarkdown);
    }

    @Test
    void aPacketThatBelongsToNoHitFails() {
        received.add(new ReceivedVelocity(8_500L, VICTIM, new VelocityPacket(0, -627, 0)));

        ControlCheck.Result result = check();

        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).contains("matches no logged hit"), result::toMarkdown);
    }

    @Test
    void packetsArePairedByVictimAndTime() {
        ReceivedVelocity sent = received.get(0);
        received.set(0, new ReceivedVelocity(sent.time, VICTIM + 1, sent.packet));
        ReceivedVelocity late = received.get(1);
        received.set(1, new ReceivedVelocity(late.time + 10_000L, VICTIM, late.packet));

        ControlCheck.Result result = check();

        // The first two hits have no packet and the two packets belong to no hit; the other six
        // hits keep their own packets.
        assertEquals(4, result.failures().size(), result::toMarkdown);
        assertTrue(result.failures().get(0).contains("no velocity packet"), result::toMarkdown);
        assertTrue(result.failures().get(1).contains("no velocity packet"), result::toMarkdown);
        assertTrue(result.failures().get(2).contains("matches no logged hit"), result::toMarkdown);
        assertTrue(result.failures().get(3).contains("matches no logged hit"), result::toMarkdown);
    }

    @Test
    void aSecondPacketForTheSameHitFails() {
        ReceivedVelocity sent = received.get(4);
        received.add(new ReceivedVelocity(sent.time + 50L, VICTIM, new VelocityPacket(0, -627, 0)));

        ControlCheck.Result result = check();

        assertEquals(1, result.failures().size(), result::toMarkdown);
        assertTrue(result.failures().get(0).contains("matches no logged hit"), result::toMarkdown);
    }

    @Test
    void everyAttackMustGiveOneLoggedHit() {
        assertTrue(pandaSpigot.check(hits, received, hits.size(), hits.size()).passed());

        ControlCheck.Result result = pandaSpigot.check(hits, received, hits.size() + 1, hits.size());

        assertEquals(1, result.failures().size(), result::toMarkdown);
        assertEquals("the bots attacked 9 times but the engine logged 8 hits", result.failures().get(0));
    }

    @Test
    void everyPhaseOfTheControlTestIsRequired() {
        hits.subList(0, 3).clear();
        received.subList(0, 3).clear();

        ControlCheck.Result result = pandaSpigot.check(hits, received, 1);

        assertTrue(result.failures().isEmpty());
        assertFalse(result.passed());
        assertTrue(result.coverageProblems().get(0).contains("no hit in observe mode"), result::toMarkdown);
    }

    @Test
    void sprintNonSprintEnchantedAndAirborneHitsAreRequired() {
        List<RecordedHit> standingOnly = new ArrayList<>();
        List<ReceivedVelocity> packets = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            RecordedHit hit = hits.get(i);
            if (!hit.sprinting && hit.victimOnGround) {
                standingOnly.add(hit);
                packets.add(received.get(i));
            }
        }

        List<String> problems = pandaSpigot.check(standingOnly, packets, 1).coverageProblems();

        assertTrue(problems.contains("override hits must include sprint and non-sprint hits"), problems::toString);
        assertTrue(problems.contains("no hit with a knockback level of 2 or more (Knockback enchantment)"));
        assertTrue(problems.contains("no hit on a victim in the air"));
    }

    @Test
    void aVanillaProfileThatChangesNothingIsNotCountedAsAChange() {
        hits.subList(5, 8).clear();
        received.subList(5, 8).clear();

        List<String> problems = check().coverageProblems();

        assertTrue(problems.contains("no override hit with a non-vanilla profile that changed the packet"));
    }

    @Test
    void aRandomDirectionCannotBeChecked() {
        List<RecordedHit> one = new ArrayList<>();
        MeleeHit samePlace = new MeleeHit(1.0D, 1.0D, 0.0F, 0, 1.0D, 1.0D, STANDING);
        one.add(new RecordedHit(1_000L, "observe", KnockbackProfile.vanilla(), VICTIM, false, true, samePlace,
                Vec3.ZERO, new VelocityPacket(0, 0, 0), false));
        List<ReceivedVelocity> packets = new ArrayList<>();
        packets.add(new ReceivedVelocity(1_001L, VICTIM, new VelocityPacket(10, 2886, -20)));

        List<String> failures = pandaSpigot.check(one, packets, 1).failures();

        assertEquals(1, failures.size());
        assertTrue(failures.get(0).contains("random direction"), failures::toString);
    }

    /** Finds an input for which 0.4 and 0.4F truncate to different packet units. */
    private MeleeHit hitWherePandaSpigotDefaultsAndVanillaDiffer() {
        Random random = new Random(1_8_8L);
        for (int i = 0; i < 1_000_000; i++) {
            MeleeHit hit = new MeleeHit(0.0D, 0.0D, (random.nextFloat() - 0.5F) * 360.0F, random.nextInt(3),
                    (random.nextDouble() - 0.5D) * 6.0D + 0.5D, (random.nextDouble() - 0.5D) * 6.0D + 0.5D,
                    new Vec3((random.nextDouble() - 0.5D) * 0.4D, -random.nextDouble() * 0.5D,
                            (random.nextDouble() - 0.5D) * 0.4D));
            VelocityPacket vanilla = VelocityPacket.encode(model.compute(KnockbackProfile.vanilla(), hit));
            VelocityPacket panda = VelocityPacket.encode(model.compute(ControlCheck.PANDASPIGOT_DEFAULT, hit));
            if (!vanilla.equals(panda)) {
                return hit;
            }
        }
        throw new AssertionError("no input where 0.4 and 0.4F give different packets");
    }

    @Test
    void pandaSpigotDefaultsDifferFromVanillaOnlyInTheConstantsPrecision() {
        KnockbackProfile vanilla = KnockbackProfile.vanilla();
        KnockbackProfile panda = ControlCheck.PANDASPIGOT_DEFAULT;

        assertNotEquals(vanilla.horizontal(), panda.horizontal());
        assertEquals((float) vanilla.horizontal(), (float) panda.horizontal());
        assertEquals((float) vanilla.vertical(), (float) panda.vertical());
        assertEquals(vanilla.friction(), panda.friction());
        assertEquals(vanilla.verticalLimit(), panda.verticalLimit());
        assertEquals(vanilla.extraHorizontal(), panda.extraHorizontal());
        assertEquals(vanilla.extraVertical(), panda.extraVertical());
    }
}
