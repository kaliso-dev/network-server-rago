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
    void onlyAHitFromTheFrontCounts() {
        assertTrue(KbDisplace.fromTheFront(-1.2, 0.0), "from the bridge, in front");
        assertTrue(KbDisplace.fromTheFront(-2.8, 0.4), "at the end of the reach");
        assertFalse(KbDisplace.fromTheFront(1.2, 0.0), "from behind");
        assertFalse(KbDisplace.fromTheFront(-0.2, 1.0), "from a side");
        assertFalse(KbDisplace.fromTheFront(-1.0, 1.5), "from beside the bridge");
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
        assertTrue(place.fromTheFront(0.0, -1.2), "from the bridge, in front");
        assertFalse(place.fromTheFront(0.0, 1.2), "from behind");
        assertFalse(place.fromTheFront(1.0, -0.2), "from a side");
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
        // a sprint hit from the bridge (attacker at smaller x, looking along +x... turned to +z: yaw 0)
        double[] full = KbDisplaceBot.knockback(-2.0, 0.5, 0f, true, 0.5, 0.5, 0, -0.0784, 0, 1.0);
        assertEquals(0.4, full[0], 1e-9, "away from the attacker");
        assertEquals(0.4 - 0.0392 + 0.1, full[1], 1e-9, "up, then the sprint");
        assertEquals(0.5, full[2], 1e-9, "the sprint, the way the attacker looks");
        double[] less = KbDisplaceBot.knockback(-2.0, 0.5, 0f, true, 0.5, 0.5, 0, -0.0784, 0, KbDisplaceBot.kbShare(1));
        assertEquals(0.6 * full[2], less[2], 1e-9);
        assertEquals(1.0, KbDisplaceBot.kbShare(5), 1e-9, "the whole knockback at the top");
        assertTrue(KbDisplaceBot.kbShare(KbDisplaceBot.DEFAULT_BOT_KB) < 1.0, "less than the game's by default");
        double[] walk = KbDisplaceBot.knockback(-2.0, 0.5, 0f, false, 0.5, 0.5, 0, -0.0784, 0, 1.0);
        assertEquals(0.0, walk[2], 1e-9, "no sprint: straight away from the attacker");
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
