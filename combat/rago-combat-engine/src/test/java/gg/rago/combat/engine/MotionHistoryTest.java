package gg.rago.combat.engine;

import static org.junit.jupiter.api.Assertions.assertSame;

import gg.rago.combat.model.Vec3;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MotionHistoryTest {

    private static final UUID VICTIM = new UUID(1L, 1L);
    private static final UUID OTHER = new UUID(2L, 2L);

    private static Vec3 motion(long tick) {
        return new Vec3(0.0D, -tick / 100.0D, 0.0D);
    }

    @Test
    void aDelayTakesTheSampleThatManyTicksBeforeTheCurrentMotion() {
        MotionHistory history = new MotionHistory();
        Vec3[] samples = new Vec3[31];
        for (int tick = 1; tick <= 30; tick++) {
            samples[tick] = motion(tick);
            history.record(VICTIM, tick, samples[tick]);
        }
        Vec3 current = new Vec3(0.0D, 0.42D, 0.0D);

        // A hit during tick 31, after the victim's movement of tick 31: the latest sample, of
        // tick 30, is one movement old.
        assertSame(current, history.delayed(VICTIM, 30L, 0, current));
        assertSame(samples[30], history.delayed(VICTIM, 30L, 1, current));
        assertSame(samples[28], history.delayed(VICTIM, 30L, 3, current));
        assertSame(samples[21], history.delayed(VICTIM, 30L, 10, current));
    }

    @Test
    void aHitBeforeTheVictimsMovementOfTheTickGoesOneSampleFurther() {
        MotionHistory history = new MotionHistory();
        Vec3[] samples = new Vec3[31];
        for (int tick = 1; tick <= 30; tick++) {
            samples[tick] = motion(tick);
            history.record(VICTIM, tick, samples[tick]);
        }
        // The server has not handled the victim's movement of tick 31 yet: its motion is still the
        // one of the latest sample.
        Vec3 current = motion(30);

        assertSame(current, history.delayed(VICTIM, 30L, 0, current));
        assertSame(samples[29], history.delayed(VICTIM, 30L, 1, current));
        assertSame(samples[27], history.delayed(VICTIM, 30L, 3, current));
        assertSame(samples[30 - (MotionHistory.CAPACITY - 1)],
                history.delayed(VICTIM, 30L, MotionHistory.CAPACITY - 1, current));
    }

    @Test
    void missingSamplesGiveTheCurrentMotion() {
        MotionHistory history = new MotionHistory();
        Vec3 current = new Vec3(0.0D, -0.0784D, 0.0D);
        assertSame(current, history.delayed(VICTIM, 10L, 3, current));

        history.record(VICTIM, 10L, motion(10));
        // Tick 8 was never recorded (the simulation was just turned on).
        assertSame(current, history.delayed(VICTIM, 10L, 3, current));
        assertSame(current, history.delayed(OTHER, 10L, 1, current));

        // A slot reused by a later tick does not answer for the older one.
        Vec3 tick1 = motion(1);
        history.record(OTHER, 1L, tick1);
        history.record(OTHER, 2L, motion(2));
        history.record(OTHER, 3L, motion(3));
        assertSame(tick1, history.delayed(OTHER, 3L, 3, current));
        history.record(OTHER, 1L + MotionHistory.CAPACITY, motion(1 + MotionHistory.CAPACITY));
        assertSame(current, history.delayed(OTHER, 3L, 3, current));
    }

    @Test
    void forgottenPlayersHaveNoHistory() {
        MotionHistory history = new MotionHistory();
        Vec3 otherMotion = motion(5);
        history.record(VICTIM, 5L, motion(5));
        history.record(OTHER, 5L, otherMotion);
        Vec3 current = new Vec3(0.0D, 0.0D, 0.0D);

        history.forget(VICTIM);

        assertSame(current, history.delayed(VICTIM, 5L, 1, current));
        assertSame(otherMotion, history.delayed(OTHER, 5L, 1, current));
        history.clear();
        assertSame(current, history.delayed(OTHER, 5L, 1, current));
    }
}
