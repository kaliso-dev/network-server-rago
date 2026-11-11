package gg.rago.combat.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Random;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

class KnockbackCalculatorTest {

    /** Server-side motY of a player standing on the ground: one tick of gravity and drag. */
    private static final double STANDING_MOTION_Y = (0.0D - 0.08D) * 0.9800000190734863D;

    private static final Vec3 STANDING = new Vec3(0.0D, STANDING_MOTION_Y, 0.0D);

    private final KnockbackCalculator calculator = new KnockbackCalculator(failingRandom());

    @Test
    void standingMotionIsOneTickOfGravity() {
        assertEquals(-0.0784000015258789D, STANDING_MOTION_Y);
    }

    @Test
    void firstHitWithoutSprintGivesTheKnownVanillaVelocity() {
        // Attacker 2.5 blocks north of the victim, victim pushed south (+z).
        MeleeHit hit = new MeleeHit(0.5D, 0.5D, 0.0F, 0, 0.5D, 3.0D, STANDING);

        Vec3 velocity = calculator.compute(KnockbackProfile.vanilla(), hit);

        assertEquals(0.0D, velocity.x());
        assertEquals(0.36080000519752503D, velocity.y());
        assertEquals(0.4000000059604645D, velocity.z());
        assertEquals(new VelocityPacket(0, 2886, 3200), VelocityPacket.encode(velocity));
    }

    @Test
    void sprintHitAddsHalfABlockForwardAndATenthUp() {
        // Yaw 0 looks south (+z), the direction the victim is pushed.
        MeleeHit hit = new MeleeHit(0.5D, 0.5D, 0.0F, 1, 0.5D, 3.0D, STANDING);

        Vec3 velocity = calculator.compute(KnockbackProfile.vanilla(), hit);

        assertEquals(0.0D, velocity.x(), 1.0E-15D);
        assertEquals(0.36080000519752503D + 0.1D, velocity.y());
        assertEquals(0.4000000059604645D + 0.5D, velocity.z());
        assertEquals(new VelocityPacket(0, 3686, 7200), VelocityPacket.encode(velocity));
    }

    @Test
    void extraPushFollowsTheAttackerYawNotThePositions() {
        // Victim east of the attacker, but the attacker looks south: base push east, extra push south.
        MeleeHit hit = new MeleeHit(0.0D, 0.0D, 0.0F, 1, 3.0D, 0.0D, STANDING);

        Vec3 velocity = calculator.compute(KnockbackProfile.vanilla(), hit);

        assertEquals(0.4000000059604645D, velocity.x(), 1.0E-15D);
        assertEquals(0.5D, velocity.z(), 1.0E-15D);
    }

    @Test
    void verticalMotionIsCappedBeforeTheExtraPush() {
        MeleeHit rising = new MeleeHit(0.0D, 0.0D, 0.0F, 0, 0.0D, 3.0D, new Vec3(0.0D, 0.5D, 0.0D));
        MeleeHit risingSprint = new MeleeHit(0.0D, 0.0D, 0.0F, 1, 0.0D, 3.0D, new Vec3(0.0D, 0.5D, 0.0D));

        assertEquals(0.4000000059604645D, calculator.compute(KnockbackProfile.vanilla(), rising).y());
        assertEquals(0.4000000059604645D + 0.1D, calculator.compute(KnockbackProfile.vanilla(), risingSprint).y());
    }

    @Test
    void frictionHalvesTheVictimsServerSideMotion() {
        Vec3 falling = new Vec3(0.2D, -0.3D, -0.1D);
        MeleeHit hit = new MeleeHit(0.0D, 0.0D, 0.0F, 0, 0.0D, 3.0D, falling);

        Vec3 velocity = calculator.compute(KnockbackProfile.vanilla(), hit);

        assertEquals(0.1D, velocity.x());
        assertEquals(-0.15D + (double) 0.4F, velocity.y());
        assertEquals(-0.05D + (double) 0.4F, velocity.z());
    }

    @Test
    void customProfileReplacesEveryConstant() {
        KnockbackProfile profile = KnockbackProfile.of("test", 4.0D, 0.3D, 0.25D, 0.3D, 0.25D, 0.05D);
        MeleeHit hit = new MeleeHit(0.0D, 0.0D, 0.0F, 1, 0.0D, 3.0D, new Vec3(0.0D, -0.2D, 0.0D));

        Vec3 velocity = calculator.compute(profile, hit);

        assertEquals(0.0D, velocity.x(), 1.0E-15D);
        assertEquals(-0.05D + 0.25D + 0.05D, velocity.y(), 1.0E-15D);
        assertEquals(0.3D + 0.25D, velocity.z(), 1.0E-15D);
    }

    @Test
    void sameHorizontalPositionDrawsADirectionLikeTheServer() {
        // dx = (0.9 - 0.1) * 0.01, dz = (0.1 - 0.9) * 0.01: far enough apart to stop the loop.
        DoubleSupplier sequence = sequence(0.9D, 0.1D, 0.1D, 0.9D);
        DoubleSupplier sameSequence = sequence(0.9D, 0.1D, 0.1D, 0.9D);
        MeleeHit hit = new MeleeHit(1.0D, 1.0D, 0.0F, 0, 1.0D, 1.0D, STANDING);

        Vec3 velocity = new KnockbackCalculator(sequence).compute(KnockbackProfile.vanilla(), hit);
        double[] expected = VanillaReference.knockback(1.0D, 1.0D, 0.0F, 0, 1.0D, 1.0D,
                0.0D, STANDING_MOTION_Y, 0.0D, sameSequence);

        assertEquals(new Vec3(expected[0], expected[1], expected[2]), velocity);
        assertEquals(-velocity.x(), velocity.z(), 1.0E-15D);
    }

    @Test
    void vanillaProfileMatchesTheLiteralServerCodeBitForBit() {
        Random random = new Random(18_000_000L);
        for (int i = 0; i < 200_000; i++) {
            double attackerX = (random.nextDouble() - 0.5D) * 2000.0D;
            double attackerZ = (random.nextDouble() - 0.5D) * 2000.0D;
            double victimX = attackerX + (random.nextDouble() - 0.5D) * 8.0D;
            double victimZ = attackerZ + (random.nextDouble() - 0.5D) * 8.0D;
            float yaw = (random.nextFloat() - 0.5F) * 1440.0F;
            int level = random.nextInt(4);
            double motX = (random.nextDouble() - 0.5D) * 2.0D;
            double motY = (random.nextDouble() - 0.5D) * 2.0D;
            double motZ = (random.nextDouble() - 0.5D) * 2.0D;
            long seed = random.nextLong();
            MeleeHit hit = new MeleeHit(attackerX, attackerZ, yaw, level, victimX, victimZ, new Vec3(motX, motY, motZ));

            Vec3 actual = new KnockbackCalculator(new Random(seed)::nextDouble).compute(KnockbackProfile.vanilla(), hit);
            double[] expected = VanillaReference.knockback(attackerX, attackerZ, yaw, level, victimX, victimZ,
                    motX, motY, motZ, new Random(seed)::nextDouble);

            assertEquals(new Vec3(expected[0], expected[1], expected[2]), actual, () -> hit.toString());
        }
    }

    private static DoubleSupplier sequence(double... values) {
        Deque<Double> queue = new ArrayDeque<>();
        Arrays.stream(values).forEach(queue::add);
        return () -> {
            Double next = queue.poll();
            if (next == null) {
                throw new AssertionError("random source called more often than expected");
            }
            return next;
        };
    }

    private static DoubleSupplier failingRandom() {
        return () -> {
            throw new AssertionError("random source must not be used when positions differ");
        };
    }

    @Test
    void randomDirectionIsOnlyDrawnBelowOneHundredthOfABlock() {
        // 1/64 block apart (exact in float): no random draw, a full push away from the attacker.
        MeleeHit close = new MeleeHit(0.0D, 0.0D, 0.0F, 0, 0.015625D, 0.0D, STANDING);
        assertEquals((double) 0.4F, calculator.compute(KnockbackProfile.vanilla(), close).x());

        MeleeHit samePosition = new MeleeHit(0.0D, 0.0D, 0.0F, 0, 0.0D, 0.0D, STANDING);
        assertThrows(AssertionError.class, () -> calculator.compute(KnockbackProfile.vanilla(), samePosition));
    }
}
