package gg.rago.combat.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The measured profiles give back the packets they were measured from
 * (docs/combat/mesures-hypixel.md).
 */
class MeasuredProfilesTest {

    private static final Vec3 STANDING = new Vec3(0.0D, -0.0784000015258789D, 0.0D);
    private static final Vec3 RISING = new Vec3(0.0D, 0.1D, 0.0D);

    private final KnockbackCalculator calculator = new KnockbackCalculator(() -> {
        throw new AssertionError("no random direction expected");
    });

    @Test
    void hypixelHitsWithoutSprintAreVanilla() {
        assertEquals(new VelocityPacket(0, 2886, 3200), packet(0, STANDING));
        assertEquals(new VelocityPacket(0, 3200, 3200), packet(0, RISING));
    }

    @Test
    void hypixelSprintHitsAddSevenHundredthsUp() {
        // The two values seen most often for sprint hits: from the ground, and at the cap.
        assertEquals(new VelocityPacket(0, 3446, 7200), packet(1, STANDING));
        assertEquals(new VelocityPacket(0, 3760, 7200), packet(1, RISING));
    }

    @Test
    void hypixelSprintAddsTheSameStepWhateverTheVictimMotion() {
        // Each common vertical value without sprint had its sprint twin 560 units (0.07 block per
        // tick) higher in the recordings: 2886/3446, 2898/3458, 2590/3150 and 3200/3760.
        for (double motionY = -0.6D; motionY <= 0.2D; motionY += 0.05D) {
            Vec3 motion = new Vec3(0.0D, motionY, 0.0D);
            int step = packet(1, motion).y() - packet(0, motion).y();
            assertTrue(step == 560 || step == 559 || step == 561, "motionY " + motionY + ": step " + step);
        }
    }

    @Test
    void onlyTheSprintVerticalDiffersFromVanilla() {
        KnockbackProfile hypixel = MeasuredProfiles.hypixelApproxV1();
        KnockbackProfile vanilla = KnockbackProfile.vanilla();

        assertEquals(vanilla.friction(), hypixel.friction());
        assertEquals(vanilla.horizontal(), hypixel.horizontal());
        assertEquals(vanilla.vertical(), hypixel.vertical());
        assertEquals(vanilla.verticalLimit(), hypixel.verticalLimit());
        assertEquals(vanilla.extraHorizontal(), hypixel.extraHorizontal());
        assertEquals(0.07D, hypixel.extraVertical());
        assertEquals(MeasuredProfiles.HYPIXEL_APPROX_V1_NAME, hypixel.name());
        assertTrue(MeasuredProfiles.all().contains(hypixel));
    }

    /** Attacker 2.5 blocks north of the victim, looking south, the way the victim is pushed. */
    private VelocityPacket packet(int level, Vec3 victimMotion) {
        MeleeHit hit = new MeleeHit(0.5D, 0.5D, 0.0F, level, 0.5D, 3.0D, victimMotion);
        return VelocityPacket.encode(calculator.compute(MeasuredProfiles.hypixelApproxV1(), hit));
    }
}
