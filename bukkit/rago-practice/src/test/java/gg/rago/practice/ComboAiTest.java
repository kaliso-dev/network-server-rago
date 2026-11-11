package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The combo bot plays as a 1.8 player: its speed, its reach, its knockback, its levels. */
class ComboAiTest {

    @Test
    void itRunsAtTheSpeedOfASprintingPlayer() {
        // straight forward on the ground: 0.13 a tick, then the ground slows it (0.546)
        double v = 0;
        double moved = 0;
        for (int tick = 0; tick < 40; tick++) {
            moved = v + ComboAi.push(0, 1, 1, 0, true, true)[1];
            v = moved * 0.546;
        }
        assertEquals(0.2863, moved, 1e-3, "a sprinting player: 5.6 blocks a second");
        // a diagonal input is no faster
        double[] diagonal = ComboAi.push(0, 1, 1, 1, true, true);
        assertEquals(0.13, Math.hypot(diagonal[0], diagonal[1]), 1e-9);
        // strafing right while facing south (+z) goes west (-x)
        assertTrue(ComboAi.push(0, 1, 0, 1, true, false)[0] < 0);
        assertEquals(0.026, ComboAi.push(1, 0, 1, 0, false, true)[0], 1e-9, "in the air");
    }

    @Test
    void itsHitPushesAsAPlayersHit() {
        // the bot 3 blocks north (-z) of a player standing still: a sprint hit sends them south
        double[] sprint = ComboAi.knockback(0, -0.0784, 0, 0, -3, 0, 0, true, 0, 3);
        assertArrayEquals(new double[] {0, 0.4608, 0.9}, sprint, 1e-9);
        double[] plain = ComboAi.knockback(0, -0.0784, 0, 0, -3, 0, 0, false, 0, 3);
        assertArrayEquals(new double[] {0, 0.3608, 0.4}, plain, 1e-9);
        // never more than 0.4 up before the sprint extra
        assertEquals(0.4, ComboAi.knockback(0, 0.5, 0, 1, 0, 0, 0, false, 0, 0)[1], 1e-9);
    }

    @Test
    void itsReachIsMeasuredToTheBox() {
        // eyes 3 blocks away from the side of the box, at its height
        assertEquals(3.0, ComboAi.reach(3.4, 1.62, 0, 0, 0, 0), 1e-9);
        assertEquals(0, ComboAi.reach(0.2, 1.0, 0.2, 0, 0, 0), 1e-9, "inside the box");
        assertTrue(ComboAi.reach(0, 5.0, 3.4, 0, 0, 0) > 3.0, "from above it is further");
    }

    @Test
    void theLevelsGetHarder() {
        ComboAi.Level[] levels = ComboAi.LEVELS;
        assertEquals("easy", levels[0].id);
        assertEquals("expert", levels[levels.length - 1].id);
        for (int i = 1; i < levels.length; i++) {
            assertTrue(levels[i].cps > levels[i - 1].cps);
            assertTrue(levels[i].accuracy > levels[i - 1].accuracy);
            assertTrue(levels[i].reach >= levels[i - 1].reach);
            assertTrue(levels[i].reaction < levels[i - 1].reaction);
            assertTrue(levels[i].reach <= 3.0, "no more reach than a player");
            assertTrue(levels[i].reaction < ComboAi.MEMORY);
        }
        assertEquals("hard", ComboAi.level("hard").id);
        assertEquals("easy", ComboAi.level("nothing").id);
    }

    @Test
    void itSteersWhereThePlayerWasItsReactionTimeAgo() {
        ComboAi ai = new ComboAi(ComboAi.level("normal"), new Random(1));
        for (int tick = 0; tick < 10; tick++) {
            ai.see(tick, 0, 0);
        }
        assertEquals(9 - ComboAi.level("normal").reaction, ai.believed()[0], 1e-9);
    }

    @Test
    void itClicksAtItsCps() {
        ComboAi ai = new ComboAi(ComboAi.level("hard"), new Random(7));
        int clicks = 0;
        for (int tick = 0; tick < 20 * 60; tick++) {
            clicks += ai.clicks();
        }
        assertEquals(11 * 60, clicks, 11 * 60 * 0.05, "about 11 clicks a second over a minute");
        assertFalse(ai.lands(3.2), "out of reach never lands");
    }

    @Test
    void itRunsToThePlayerThenFightsInReach() {
        ComboAi ai = new ComboAi(ComboAi.level("expert"), new Random(3));
        double x = 0;
        double z = -8;
        double vx = 0;
        double vz = 0;
        int inReach = -1;
        for (int tick = 0; tick < 100; tick++) {
            ai.see(0, 0, 0);
            double[] next = ai.steer(x, z, vx, 0, vz, true);
            x += next[0];
            z += next[2];
            vx = next[0] * 0.546;
            vz = next[2] * 0.546;
            double reach = ComboAi.reach(x, ComboAi.EYES, z, 0, 0, 0);
            if (inReach < 0 && reach <= 3.0) {
                inReach = tick;
            }
            if (tick > 60) {
                assertTrue(Math.hypot(x, z) < 3.4, "it stays at fighting distance");
                assertTrue(Math.hypot(x, z) > 0.5, "without walking into the player");
            }
        }
        assertTrue(inReach > 0 && inReach < 30, "in reach in under a second and a half: " + inReach);
    }
}
