package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The KB displace of a Bed Fight top fight: the run, the front, the notch, the roles. */
class KbDisplaceTest {

    @Test
    void theAttackerHasARunForTheMomentum() {
        assertTrue(KbDisplace.RUN >= 8, "a sprint needs a few blocks");
        assertTrue(KbDisplace.KNOCK_TICKS >= 20, "a hit defender goes up before they fall");
    }

    @Test
    void aDefenderBackOnTheBridgeHeldAtOnce() {
        assertFalse(KbDisplace.held(2, true), "still on the ground in the tick of the hit: the knockback has not begun");
        assertFalse(KbDisplace.held(KbDisplace.SETTLE_TICKS + 2, false), "in the air, maybe falling off");
        assertTrue(KbDisplace.held(KbDisplace.SETTLE_TICKS, true), "back on the bridge once the knockback is over");
        assertTrue(KbDisplace.SETTLE_TICKS < KbDisplace.KNOCK_TICKS, "sooner than the end of the knockback time");
    }

    @Test
    void onlyAHitFromTheBridgeInFrontCounts() {
        // the plugin's own bridge: along x, the notch at x 0 (feet at y 0), the bridge one block higher
        KbDisplace.Place plain = KbDisplace.Place.PLAIN;
        assertTrue(plain.onTheBridge(new double[] {-0.7, 1, 0.5}), "from the bridge, in front");
        assertTrue(plain.onTheBridge(new double[] {-2.3, 1.6, 0.9}), "at the end of the reach, in a jump");
        assertFalse(plain.onTheBridge(new double[] {1.7, 1, 0.5}), "from behind");
        assertFalse(plain.onTheBridge(new double[] {0.3, 0, 1.5}), "from a side");
        assertFalse(plain.onTheBridge(new double[] {0.3, 1, 0.5}), "over the notch, on the defender's head");
        assertFalse(plain.onTheBridge(new double[] {-0.5, -0.2, 1.4}), "from beside the bridge, falling");
        assertFalse(plain.onTheBridge(new double[] {-1.5, -0.8, 0.5}), "from below the bridge");
    }

    @Test
    void aDefenderPushedBackInTheNotchOpensNoHitFromBeside() {
        // the owner's map: the bridge along z, the defender's notch at z -3.5, the attacker's start at z 5.5
        KbDisplace.Place map = new KbDisplace.Place(null, new double[] {0.5, 1, 5.5, 180}, new double[] {0.5, 1, -3.5, 0});
        // the attacker dropped into the notch, beside the defender they pushed back to z -3.7: 0.4
        // in front of them and 0.75 to their side, which a check from the defender let through
        assertFalse(map.onTheBridge(new double[] {-0.25, 1, -3.3}), "inside the notch: never from there");
        assertTrue(map.onTheBridge(new double[] {0.3, 2, -2.9}), "on the raised block in front, its edge");
    }

    /**
     * How far sideways (x) a defender flies with this velocity until their feet are back at their
     * height: the motion of a player in the air in 1.8, the push x 0.91, the fall (y - 0.08) x 0.98.
     */
    private static double drift(double[] velocity) {
        double x = 0;
        double y = 0;
        double vx = velocity[0];
        double vy = velocity[1];
        for (int tick = 0; tick < 100; tick++) {
            x += vx;
            y += vy;
            vx *= 0.91;
            vy = (vy - 0.08) * 0.98;
            if (y <= 0) {
                break;
            }
        }
        return Math.abs(x);
    }

    @Test
    void aHitAlwaysPushesAlongTheBridgeOnlyTheTurnOfTheHeadSendsSideways() {
        // the owner's map: the bridge along z, the attacker coming from +z, looking at the defender: yaw 180
        double[] front = {0, 1};
        double still = -0.0784;
        // off the top once the body is past the edge of the block: 0.8 from the middle
        double edge = 0.8;
        // the old way out: from the edge of the raised block, 0.2 to a side and 0.5 in front, no turn
        // of the head. The game pushes away from where the attacker stands: 0.15 sideways, enough to
        // throw the defender off
        double side = 0.2 / Math.hypot(0.2, 0.5) * KbDisplace.PUSH;
        assertTrue(drift(new double[] {side, still / 2 + KbDisplace.UP}) > edge, "the game's push alone throws them off");
        double[] straight = KbDisplace.knockback(front, 180f, true, 0, still, 0, KbDisplace.SPRINT_UP_PLAYER, 1.0);
        assertEquals(0.0, straight[0], 1e-9, "here, whatever the attacker's place: nothing sideways");
        assertEquals(-KbDisplace.PUSH - KbDisplace.SPRINT_PUSH, straight[2], 1e-9, "straight back along the bridge");
        assertEquals(KbDisplace.UP + still / 2 + KbDisplace.SPRINT_UP_PLAYER, straight[1], 1e-9);
        // the head turned 30 degrees in the tick of the hit, with the sprint: off sideways
        double[] turned = KbDisplace.knockback(front, 150f, true, 0, still, 0, KbDisplace.SPRINT_UP_PLAYER, 1.0);
        assertEquals(-0.25, turned[0], 1e-9, "the sprint's push, the way the attacker looks");
        assertTrue(drift(turned) > edge, "a displace");
        // the same turn without the sprint: they hold
        double[] walking = KbDisplace.knockback(front, 150f, false, 0, still, 0, KbDisplace.SPRINT_UP_PLAYER, 1.0);
        assertEquals(0.0, walking[0], 1e-9);
        assertTrue(drift(walking) < edge);
    }

    @Test
    void theDefenderHoldsTheirNotchUntilHit() {
        assertFalse(KbDisplace.outOfTheNotch(0.5, 0.0, 0.5));
        assertFalse(KbDisplace.outOfTheNotch(0.8, 0.3, 0.3), "a small move");
        assertTrue(KbDisplace.outOfTheNotch(0.5, 1.0, 0.5), "a jump onto the bridge");
        assertTrue(KbDisplace.outOfTheNotch(1.2, 0.0, 0.5), "a step onto the block behind");
        assertTrue(KbDisplace.outOfTheNotch(0.5, 0.0, 1.1), "a step to the side");
    }

    @Test
    void oneBlockDownIsOffTheTop() {
        assertFalse(KbDisplace.offTheTop(0.0));
        assertFalse(KbDisplace.offTheTop(-0.5), "knocked up, then back");
        assertTrue(KbDisplace.offTheTop(-1.0));
    }

    @Test
    void theRolesSwapEachAttempt() {
        assertEquals(0, KbDisplace.attackerOf(1));
        assertEquals(1, KbDisplace.attackerOf(2));
        assertEquals(0, KbDisplace.attackerOf(3));
    }

    @Test
    void aHitCountsOnlyWithTheLookOnTheDefender() {
        // the attacker on the bridge one block in front, eyes 1.62 above the bridge (y 1): the
        // defender's feet at y 0 in the notch
        double eyeX = -0.5;
        double eyeY = 2.62;
        double eyeZ = 0.5;
        double down = Math.toRadians(30);
        // looking at them, a little down
        assertTrue(KbDisplace.aimedAt(eyeX, eyeY, eyeZ, Math.cos(down), -Math.sin(down), 0, 0.5, 0, 0.5));
        // the head turned a little: the look still on the edge of their body
        double edge = Math.toRadians(15);
        assertTrue(KbDisplace.aimedAt(eyeX, eyeY, eyeZ, Math.cos(edge) * Math.cos(down), -Math.sin(down),
                Math.sin(edge) * Math.cos(down), 0.5, 0, 0.5));
        // the head turned to the side, as a cheat hits: not on them
        double side = Math.toRadians(70);
        assertFalse(KbDisplace.aimedAt(eyeX, eyeY, eyeZ, Math.cos(side) * Math.cos(down), -Math.sin(down),
                Math.sin(side) * Math.cos(down), 0.5, 0, 0.5));
        assertFalse(KbDisplace.aimedAt(eyeX, eyeY, eyeZ, 0, 0, 1, 0.5, 0, 0.5), "looking along the side");
        assertFalse(KbDisplace.aimedAt(eyeX, eyeY, eyeZ, -1, 0, 0, 0.5, 0, 0.5), "looking back");
        assertFalse(KbDisplace.aimedAt(-4.5, eyeY, eyeZ, 1, -0.2, 0, 0.5, 0, 0.5), "out of reach");
    }

    @Test
    void theOwnersMapIsCheckedAndItsFrontIsTheBridgeTowardsTheAttacker() throws Exception {
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.load(new java.io.File("src/main/resources/config.yml"));
        org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("kb-displace");
        assertNull(RagoPractice.checkKbDisplace(section, config.getInt("espacement")));
        // on the map the bridge runs along z: the defender on the gold block looks north, the attacker comes from there
        KbDisplace.Place place = new KbDisplace.Place(null, new double[] {0.5, 1, -1.5, 0}, new double[] {0.5, 1, 5.5, 180});
        assertTrue(place.onTheBridge(new double[] {0.5, 1, 4.3}), "from the bridge, in front");
        assertFalse(place.onTheBridge(new double[] {0.5, 1, 6.7}), "from behind");
        assertFalse(place.onTheBridge(new double[] {1.5, 1, 5.3}), "from a side");
        assertFalse(place.outOfTheNotch(0.5, 1.0, 5.5));
        assertTrue(place.outOfTheNotch(0.5, 1.0, 6.2), "a step back");
        assertTrue(place.outOfTheNotch(0.5, 2.0, 5.5), "a jump");
        assertFalse(place.offTheTop(0.5), "knocked up, then back");
        assertTrue(place.offTheTop(0.0), "a block down, off the bridge");
        // the owner's map: the attacker on the gold block, the defender in the notch 9 blocks before it
        assertEquals(896090.5, ((Number) section.getList("attaquant").get(2)).doubleValue(), 1e-9);
        assertEquals(896081.5, ((Number) section.getList("defenseur").get(2)).doubleValue(), 1e-9);
        section.set("attaquant", java.util.Arrays.asList(896056.5, 98, 896083.5, 0));
        assertTrue(RagoPractice.checkKbDisplace(section, 200).startsWith("attaquant: from 3"));
        section.set("defenseur", java.util.Arrays.asList(896056.5, 98));
        assertTrue(RagoPractice.checkKbDisplace(section, 200).startsWith("defenseur"));
    }

    @Test
    void theBotTakesItsShareOfTheKnockbackOfTheGame() {
        // a sprint hit on the plugin's own bridge (the attacker at smaller x), the head turned to +z: yaw 0
        double[] front = KbDisplace.Place.PLAIN.front;
        double[] full = KbDisplace.knockback(front, 0f, true, 0, -0.0784, 0, KbDisplace.SPRINT_UP_MOB, 1.0);
        assertEquals(0.4, full[0], 1e-9, "away from the attacker, along the bridge");
        assertEquals(0.4 - 0.0392 + 0.1, full[1], 1e-9, "up, then the sprint (a mob's)");
        assertEquals(0.5, full[2], 1e-9, "the sprint, the way the attacker looks");
        double[] less = KbDisplace.knockback(front, 0f, true, 0, -0.0784, 0, KbDisplace.SPRINT_UP_MOB, KbDisplaceBot.kbShare(1));
        assertEquals(0.6 * full[2], less[2], 1e-9);
        assertEquals(1.0, KbDisplaceBot.kbShare(5), 1e-9, "the whole knockback at the top");
        assertTrue(KbDisplaceBot.kbShare(KbDisplaceBot.DEFAULT_BOT_KB) < 1.0, "less than the game's by default");
        double[] walk = KbDisplace.knockback(front, 0f, false, 0, -0.0784, 0, KbDisplace.SPRINT_UP_MOB, 1.0);
        assertEquals(0.0, walk[2], 1e-9, "no sprint: straight along the bridge");
    }

    @Test
    void invisibleWallsLineTheBridgeInFrontOfTheDefender() {
        java.util.List<int[]> walls = KbDisplace.Place.PLAIN.walls();
        java.util.Set<String> at = new java.util.HashSet<>();
        for (int[] wall : walls) {
            at.add(wall[0] + "," + wall[1] + "," + wall[2]);
            assertTrue(wall[2] == 1 || wall[2] == -1, "beside the bridge, never on it: z " + wall[2]);
            assertTrue(wall[0] <= -1, "in front of the defender, never at their sides: x " + wall[0]);
        }
        assertTrue(at.contains("-1,1,1") && at.contains("-1,1,-1"), "from the block in front of the defender");
        assertTrue(at.contains("-10,3,1"), "past the attacker's start, three blocks high");
        // the owner's map: along z, the defender at z -3.5, the attacker at z 5.5
        KbDisplace.Place map = new KbDisplace.Place(null, new double[] {0.5, 1, 5.5, 180}, new double[] {0.5, 1, -3.5, 0});
        for (int[] wall : map.walls()) {
            assertTrue(wall[0] == 1 || wall[0] == -1, "beside the bridge: x " + wall[0]);
            assertTrue(wall[2] >= -3, "never beside the defender: z " + wall[2]);
        }
    }
}
