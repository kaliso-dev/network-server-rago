package gg.rago.combat.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClientTrajectoryTest {

    private static final double JUMP_MOTION = (double) 0.42F;

    @Test
    void verticalMotionFollowsGravityThenDragEachTick() {
        ClientTrajectory.Trajectory jump = ClientTrajectory.simulate(new Vec3(0.0D, JUMP_MOTION, 0.0D), true, 40);
        List<ClientTrajectory.Tick> ticks = jump.ticks();

        assertEquals(0.41999998688697815D, ticks.get(0).position().y());
        assertEquals((0.41999998688697815D - 0.08D) * 0.9800000190734863D, ticks.get(0).motion().y());
        assertEquals(0.33319999363422365D, ticks.get(0).motion().y(), 1.0E-15D);
    }

    @Test
    void aStandingJumpPeaksAt1Point2492BlocksAndLastsTwelveTicks() {
        ClientTrajectory.Trajectory jump = ClientTrajectory.simulate(new Vec3(0.0D, JUMP_MOTION, 0.0D), true, 40);
        List<ClientTrajectory.Tick> ticks = jump.ticks();

        // The 1.8 client zeroes motions under 0.005, so the last 0.003 of the climb is lost and the
        // player hangs one tick at the top (1.9 lowered the threshold to 0.003: peak 1.2522 there).
        assertEquals(0.0030162615090425808D, ticks.get(4).motion().y(), 1.0E-15D);
        assertEquals(ticks.get(4).position().y(), ticks.get(5).position().y());
        assertEquals(1.2491870787446813D, jump.peakHeight(), 1.0E-12D);
        assertEquals(1.1707870772188023D, ticks.get(6).position().y(), 1.0E-12D);
        assertEquals(12, jump.landingTick());
        assertEquals(0.0D, jump.horizontalDistanceAtLanding());
    }

    @Test
    void groundFrictionAppliesWhileStandingOnTheGround() {
        // A standing player always carries one tick of gravity, which keeps it on the ground.
        double standingY = (0.0D - 0.08D) * 0.9800000190734863D;
        ClientTrajectory.Trajectory slide = ClientTrajectory.simulate(new Vec3(0.5D, standingY, 0.0D), true, 3);
        double groundFriction = (double) (0.6F * 0.91F);

        assertEquals(0.5D, slide.ticks().get(0).position().x());
        assertEquals(0.0D, slide.ticks().get(0).position().y());
        assertEquals(0.5D * groundFriction, slide.ticks().get(0).motion().x());
        assertTrue(slide.ticks().get(0).onGround());
        assertEquals(0.5D * groundFriction * groundFriction, slide.ticks().get(1).motion().x());
        assertEquals(-1, slide.landingTick());
    }

    @Test
    void aVelocityWithZeroVerticalMotionLiftsTheGroundFlagForOneTick() {
        // Vanilla moveEntity only reports the ground when a downward move is blocked.
        ClientTrajectory.Trajectory flat = ClientTrajectory.simulate(new Vec3(0.5D, 0.0D, 0.0D), true, 3);

        assertFalse(flat.ticks().get(0).onGround());
        assertTrue(flat.ticks().get(1).onGround());
        assertEquals(0.0D, flat.ticks().get(1).position().y());
    }

    @Test
    void airFrictionAppliesAfterLeavingTheGround() {
        ClientTrajectory.Trajectory knockback = ClientTrajectory.simulate(new Vec3(0.0D, 0.36075D, 0.4D), true, 60);
        List<ClientTrajectory.Tick> ticks = knockback.ticks();

        // First tick: friction is chosen before the move, while still on the ground.
        assertEquals(0.4D * (double) (0.6F * 0.91F), ticks.get(0).motion().z());
        assertFalse(ticks.get(0).onGround());
        // Second tick: in the air.
        assertEquals(ticks.get(0).motion().z() * (double) 0.91F, ticks.get(1).motion().z());
        assertTrue(knockback.landingTick() > 0);
        assertTrue(knockback.horizontalDistanceAtLanding() > 0.4D);
    }

    @Test
    void smallMotionsAreZeroedBeforeMoving() {
        ClientTrajectory.Trajectory tiny = ClientTrajectory.simulate(new Vec3(0.004D, 0.0D, -0.004D), true, 1);

        assertEquals(Vec3.ZERO, tiny.ticks().get(0).position());
    }
}
