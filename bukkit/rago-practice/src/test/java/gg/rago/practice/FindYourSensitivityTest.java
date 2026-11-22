package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

/**
 * Find Your Sensitivity: the sensitivities played, how a flick is judged, how the series compare,
 * the setting of the sensitivity in the comparator.
 */
class FindYourSensitivityTest {

    private static final double SIZE = Sensitivity.halfAngle(AimTrainer.HALF_WIDTH, FindYourSensitivity.FLICK_DISTANCE);

    @Test
    void theTwoOtherSeriesTurnTwentyPercentSlowerAndFaster() {
        assertEquals(0.15, Sensitivity.degreesPerCount(100), 1e-9, "100%, the default: 0.15 degrees a count, as the client");
        assertEquals(Arrays.asList(100, 88, 110), Sensitivity.series(100), "on the client's curve, not 20 points");
        assertEquals(Arrays.asList(50, 42, 57), Sensitivity.series(50));
        assertEquals(73, Sensitivity.percentOf(Sensitivity.degreesPerCount(73)));
        assertEquals(Arrays.asList(0, 4), Sensitivity.series(0), "nothing under 0%");
        assertEquals(Arrays.asList(200, 181), Sensitivity.series(200), "nor over 200%");
        assertEquals(Arrays.asList(1, 0, 5), Sensitivity.series(1), "each one another");
    }

    @Test
    void everySeriesOfATestHasTheSameTargets() {
        List<Double> order = Sensitivity.order(42L);
        assertEquals(Sensitivity.FLICKS.length + Sensitivity.TURNS, order.size());
        assertEquals(order, Sensitivity.order(42L), "the same seed, the same order");
        assertEquals(Sensitivity.TURNS, Collections.frequency(order, Sensitivity.TURN), "the 180° turns");
    }

    @Test
    void aFlickPastTheTargetIsAnOvershootAndItsWayBackACorrection() {
        Sensitivity.Flick flick = turned(new Sensitivity.Flick(60, SIZE), 30, 35, 5, -6, -2, 0);
        assertEquals(Sensitivity.OVERSHOOT, flick.first, "70° for a target at 60°");
        assertEquals(1, flick.reversals);
    }

    @Test
    void aFlickThatStopsBeforeTheTargetFallsShort() {
        Sensitivity.Flick flick = turned(new Sensitivity.Flick(-90, SIZE), -40, -30, 0, -15, -5, 0);
        assertEquals(Sensitivity.SHORT, flick.first, "a stop at 70° for a target at 90° on the left");
        assertEquals(0, flick.reversals, "going on the same way is no correction");
        assertEquals(0, turned(new Sensitivity.Flick(45, SIZE), 25, 20, 0.2).first, "on the target at once: clean");
    }

    @Test
    void aTurnGoesTheWayThePlayerTurns() {
        Sensitivity.Flick left = turned(new Sensitivity.Flick(Sensitivity.TURN, SIZE), -5, -50, -80, -40, -3, 0);
        assertEquals(-1, left.side);
        assertEquals(0, left.first, "178°: on the target");
        assertEquals(2, left.left(), 1e-9);
        Sensitivity.Flick right = turned(new Sensitivity.Flick(Sensitivity.TURN, SIZE), 60, 90, 45, -15, 0);
        assertEquals(1, right.side);
        assertEquals(Sensitivity.OVERSHOOT, right.first, "195°");
        assertEquals(10, Sensitivity.wrap(370), 1e-9);
        assertEquals(170, Sensitivity.wrap(-190), 1e-9);
    }

    @Test
    void theMostPreciseSeriesHasTheBestRanksAndSaysWhichWayToGo() {
        Sensitivity.Series own = new Sensitivity.Series(100);
        Sensitivity.Series slower = new Sensitivity.Series(88);
        Sensitivity.Series faster = new Sensitivity.Series(110);
        Sensitivity.Flick clean = new Sensitivity.Flick(45, SIZE);
        Sensitivity.Flick over = turned(new Sensitivity.Flick(60, SIZE), 30, 35, 5, -6);
        Sensitivity.Flick shortOf = turned(new Sensitivity.Flick(-90, SIZE), -40, -30, 0);
        for (int i = 0; i < 10; i++) {
            own.hit(clean, 500);
            slower.hit(shortOf, 650);
            faster.hit(over, 450);
        }
        for (int i = 0; i < 20; i++) {
            own.tracked(i < 12);
            slower.tracked(i < 10);
            faster.tracked(i < 11);
        }
        assertEquals(0, Sensitivity.mostPrecise(Arrays.asList(own, slower, faster)), "quicker but overshooting loses");
        assertEquals(-1, Sensitivity.direction(faster), "overshoots: lower");
        assertEquals(1, Sensitivity.direction(slower), "shorts: higher");
        assertEquals(0, Sensitivity.direction(own));
        assertEquals(60, own.tracking());
        own.timedOut(clean);
        assertEquals((10 * 500 + Sensitivity.TIMEOUT_MS) / 11, own.averageTime(), "a target not clicked counts its whole time");
        String line = ChatColor.stripColor(FindYourSensitivity.line(own));
        assertEquals("100%: 727 ms to the target, 0 overshoots, 0 shorts, 0.0 corrections a target, tracking 60%,"
                + " 1 not clicked in time", line);
    }

    @Test
    void theSensitivityGoesByTensAndReadsInPercent() {
        Training.Setting setting = new Training.Setting("Sensitivity", Sensitivity.MIN, Sensitivity.MAX, 100, false).big(10, "%");
        assertEquals("100%", ChatColor.stripColor(setting.text()));
        assertTrue(setting.step(setting.bigStep));
        assertEquals(110, setting.value);
        setting.value = 195;
        assertTrue(setting.step(setting.bigStep));
        assertEquals(200, setting.value, "up to 200%");
        Training.Setting plain = new Training.Setting("Knockback", 1, 5, 3, false);
        assertEquals(0, plain.bigStep, "the other settings: steps of one");
        assertTrue(ChatColor.stripColor(plain.text()).endsWith("3/5"));
    }

    private static Sensitivity.Flick turned(Sensitivity.Flick flick, double... deltas) {
        for (double delta : deltas) {
            flick.turned(delta);
        }
        return flick;
    }
}
