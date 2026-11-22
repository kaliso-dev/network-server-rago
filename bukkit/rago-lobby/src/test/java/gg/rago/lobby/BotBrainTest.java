package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class BotBrainTest {

    /** The bundled skill, but with these clicks, accuracy and reaction. */
    private static BotBrain.Skill skill(double cps, double accuracy, int reaction) {
        BotBrain.Skill bundled = BotBrain.Skill.BUNDLED;
        return new BotBrain.Skill(cps, accuracy, bundled.reach, bundled.strafe, bundled.wTap, bundled.jumpReset,
                reaction, bundled.turn, bundled.healAt);
    }

    /** It stands at (0.5, 1, 0.5) on a floor at y 0 everywhere, the other at (x, 1, z). */
    private static BotBrain.Sight facing(double x, double z) {
        BotBrain.Sight sight = new BotBrain.Sight();
        sight.x = 0.5;
        sight.y = 1;
        sight.z = 0.5;
        sight.onGround = true;
        sight.health = 20;
        sight.fighting = true;
        sight.tx = x;
        sight.ty = 1;
        sight.tz = z;
        sight.yaw = BotBrain.yawTo(x - sight.x, z - sight.z);
        sight.ground = (bx, by, bz) -> by == 0;
        return sight;
    }

    @Test
    void itFacesAndMovesAsAClient() {
        assertEquals(0, BotBrain.yawTo(0, 1), 1e-4, "yaw 0 faces +z");
        assertEquals(90, BotBrain.yawTo(-1, 0), 1e-4, "90 faces -x");
        assertEquals(-90, BotBrain.yawTo(1, 0), 1e-4);
        assertArrayEquals(new double[] {0, 1}, BotBrain.way(0, 1, 0), 1e-9, "forward at yaw 0: +z");
        assertArrayEquals(new double[] {-1, 0}, BotBrain.way(90, 1, 0), 1e-9, "forward at yaw 90: -x");
        assertArrayEquals(new double[] {1, 0}, BotBrain.way(0, 0, 1), 1e-9, "strafe to the left at yaw 0: +x");
        assertArrayEquals(new double[] {0, 0}, BotBrain.way(0, 0, 0), 1e-9);
    }

    @Test
    void itTurnsItsHeadAtItsSpeed() {
        assertEquals(30, BotBrain.turn(0, 90, 30), 1e-4);
        assertEquals(90, BotBrain.turn(80, 90, 30), 1e-4, "the last bit");
        assertEquals(190, BotBrain.turn(170, -170, 30), 1e-4, "the short way round");
        assertEquals(-170, BotBrain.wrap(190), 1e-9);
        assertEquals(170, BotBrain.wrap(-190), 1e-9);
    }

    @Test
    void itsReachIsFromItsEyesToTheBox() {
        assertEquals(3.0, BotBrain.reach(0, 1.62, 0, 3.4, 0, 0), 1e-9, "0.4 of the box before the feet");
        assertEquals(0, BotBrain.reach(0, 1.62, 0, 0.2, 0, 0), 1e-9, "inside the box");
        assertEquals(1.0, BotBrain.reach(0, 3.9, 0, 0, 1, 0), 1e-9, "above the head: the box ends at 1.9");
    }

    @Test
    void withNobodyToFightItStands() {
        BotBrain brain = new BotBrain(BotBrain.Skill.BUNDLED, new Random(1));
        BotBrain.Sight sight = facing(5, 0.5);
        sight.fighting = false;
        sight.yaw = 12;
        for (int tick = 0; tick < 40; tick++) {
            BotBrain.Controls controls = brain.think(sight);
            assertEquals(0, controls.forward, 0);
            assertEquals(0, controls.strafe, 0);
            assertFalse(controls.jump || controls.sprint || controls.eat || controls.hit);
            assertEquals(0, controls.swings);
            assertEquals(12, controls.yaw, 0, "it looks where it looked");
        }
    }

    @Test
    void farFromTheOtherItSprintsAtThem() {
        BotBrain brain = new BotBrain(BotBrain.Skill.BUNDLED, new Random(2));
        BotBrain.Sight sight = facing(10.5, 0.5);
        BotBrain.Controls controls = brain.think(sight);
        assertEquals(1, controls.forward, 0);
        assertEquals(0, controls.strafe, 0, "no strafe from afar");
        assertTrue(controls.sprint);
        assertFalse(controls.hit, "out of reach");
        assertEquals(-90, controls.yaw, 1e-3, "it faces them");
    }

    @Test
    void itTurnsTowardsTheOtherAtMostItsTurnATick() {
        BotBrain brain = new BotBrain(BotBrain.Skill.BUNDLED, new Random(3));
        BotBrain.Sight sight = facing(10.5, 0.5);
        sight.yaw = 0;
        BotBrain.Controls controls = brain.think(sight);
        assertEquals(-BotBrain.Skill.BUNDLED.turn, controls.yaw, 1e-3);
    }

    @Test
    void itSteersToWhereTheOtherWasItsReactionAgo() {
        BotBrain brain = new BotBrain(skill(11, 0.88, 3), new Random(4));
        for (int tick = 0; tick < 6; tick++) {
            brain.think(facing(10 + tick, 0.5));
        }
        assertEquals(12, brain.believed()[0], 1e-9, "three ticks behind 15");
        BotBrain fast = new BotBrain(skill(11, 0.88, 0), new Random(4));
        fast.think(facing(10, 0.5));
        fast.think(facing(11, 0.5));
        assertEquals(11, fast.believed()[0], 1e-9, "no reaction: where they are");
    }

    @Test
    void itNeverWalksOffAnEdgeOnItsOwn() {
        BotBrain brain = new BotBrain(BotBrain.Skill.BUNDLED, new Random(5));
        BotBrain.Sight sight = facing(10.5, 0.5);
        // a floor one block wide in x (the block 0), endless in z: the void at +x
        sight.ground = (bx, by, bz) -> by == 0 && bx == 0;
        for (int tick = 0; tick < 40; tick++) {
            BotBrain.Controls controls = brain.think(sight);
            assertTrue(BotBrain.safe(sight, controls.yaw, controls.forward, controls.strafe)
                    || controls.forward == 0 && controls.strafe == 0, "tick " + tick);
            assertEquals(0, controls.forward, 0, "the other is past the edge");
            assertFalse(controls.sprint);
        }
        sight.ground = (bx, by, bz) -> by == 0;
        assertEquals(1, brain.think(sight).forward, 0, "with ground ahead, it goes");
    }

    @Test
    void onAPathOneBlockWideItGoesOnToTheOtherALittleToItsSide() {
        // the stairway of the Top Bridge, seen from above: a path one block wide (the blocks of z 0),
        // endless in x; it stands at its side (z 0.02), the other down the path a little to that
        // side (z -0.4): every way at them goes off the path at first. As a player, it walks back
        // towards the middle of the path and on to them; in CI, it stood there for the whole round.
        BotBrain brain = new BotBrain(BotBrain.Skill.BUNDLED, new Random(6));
        BotBrain.Sight sight = facing(-6.5, -0.4);
        sight.z = 0.02;
        sight.yaw = BotBrain.yawTo(sight.tx - sight.x, sight.tz - sight.z);
        sight.ground = (bx, by, bz) -> by == 0 && bz == 0;
        assertFalse(BotBrain.safe(sight, sight.yaw, 1, 0), "straight at them, it would step off");
        boolean there = false;
        for (int tick = 0; tick < 100 && !there; tick++) {
            BotBrain.Controls controls = brain.think(sight);
            assertTrue(BotBrain.safe(sight, controls.yaw, controls.forward, controls.strafe)
                    || controls.forward == 0 && controls.strafe == 0, "tick " + tick);
            // a rough walk: 0.2 of a block a tick, the way of its keys
            double[] way = BotBrain.way(controls.yaw, controls.forward, controls.strafe);
            sight.x += way[0] * 0.2;
            sight.z += way[1] * 0.2;
            sight.yaw = controls.yaw;
            assertTrue(sight.z >= 0 && sight.z < 1, "it stays on the path, tick " + tick + ": z " + sight.z);
            there = Math.hypot(sight.tx - sight.x, sight.tz - sight.z) <= BotBrain.FAR;
        }
        assertTrue(there, "it reached the other: at x " + sight.x + ", z " + sight.z);
    }

    @Test
    void itEatsAGoldenAppleWhenLowAndAway() {
        BotBrain.Sight away = facing(10.5, 0.5);
        away.health = 8;
        away.sword = 0;
        away.gapple = 1;
        away.held = 0;
        BotBrain.Controls controls = new BotBrain(BotBrain.Skill.BUNDLED, new Random(6)).think(away);
        assertEquals(1, controls.slot);
        assertTrue(controls.eat);
        assertFalse(controls.sprint, "a client eating does not sprint");
        assertTrue(controls.forward <= BotBrain.EATING_SPEED);
        assertEquals(0, controls.swings, "no click while it eats");

        BotBrain.Sight close = facing(2.5, 0.5);
        close.health = 8;
        close.sword = 0;
        close.gapple = 1;
        close.held = 1;
        controls = new BotBrain(BotBrain.Skill.BUNDLED, new Random(6)).think(close);
        assertFalse(controls.eat, "close to the other, at 8 health it fights on");
        assertEquals(0, controls.slot, "the sword back in hand");

        close.health = 4;
        assertTrue(new BotBrain(BotBrain.Skill.BUNDLED, new Random(6)).think(close).eat, "very low: it eats even close");

        away.regenerating = true;
        assertFalse(new BotBrain(BotBrain.Skill.BUNDLED, new Random(6)).think(away).eat, "the apple before still works");

        away.regenerating = false;
        away.gapple = -1;
        assertFalse(new BotBrain(BotBrain.Skill.BUNDLED, new Random(6)).think(away).eat, "no apple in its kit (the sumo)");

        BotBrain.Sight eating = facing(2.5, 0.5);
        eating.eating = true;
        eating.health = 20;
        BotBrain.Controls more = new BotBrain(BotBrain.Skill.BUNDLED, new Random(6)).think(eating);
        assertTrue(more.eat, "it eats to the end");
        assertEquals(-1, more.slot);
    }

    @Test
    void itsHitsLandInReachWithItsAimOnTheOther() {
        BotBrain brain = new BotBrain(skill(20, 1, 0), new Random(7));
        int swings = 0;
        int hits = 0;
        for (int tick = 0; tick < 40; tick++) {
            BotBrain.Controls controls = brain.think(facing(2.5, 0.5));
            swings += controls.swings;
            hits += controls.hit ? 1 : 0;
            assertTrue(!controls.hit || controls.swings > 0, "a hit is a click");
        }
        assertTrue(swings >= 30 && swings <= 50, "about 20 clicks a second: " + swings);
        assertTrue(hits >= 25, "each tick with a click lands: " + hits);

        BotBrain missing = new BotBrain(skill(20, 0, 0), new Random(7));
        for (int tick = 0; tick < 40; tick++) {
            assertFalse(missing.think(facing(2.5, 0.5)).hit, "no accuracy");
        }
        BotBrain short_ = new BotBrain(skill(20, 1, 0), new Random(7));
        for (int tick = 0; tick < 40; tick++) {
            assertFalse(short_.think(facing(4.0, 0.5)).hit, "3.1 from its eyes to the box: out of reach");
        }
        BotBrain behind = new BotBrain(skill(20, 1, 0), new Random(7));
        BotBrain.Sight back = facing(2.5, 0.5);
        back.yaw += 180;
        assertFalse(behind.think(back).hit, "its back to the other");
    }

    // --- The blocks (the Block Placement, step 2 of the owner's order of 22/11) ------------------

    /**
     * Knocked off the edge of a floor (the blocks of x 0 and less, at y 0), in the air at
     * (1.5, 1.2, 0.5) over the void, facing the other at -x: as a player, it takes its wool and looks
     * down at the side of the edge block, then clicks it: the new block goes under its feet.
     */
    private static BotBrain.Sight offTheEdge() {
        BotBrain.Sight sight = facing(-5.5, 0.5);
        sight.x = 1.5;
        sight.y = 1.2;
        sight.onGround = false;
        sight.yaw = 90;
        sight.pitch = 0;
        sight.ground = (bx, by, bz) -> by == 0 && bx <= 0;
        sight.blocks = true;
        sight.sword = 0;
        sight.held = 0;
        sight.block = 4;
        return sight;
    }

    @Test
    void knockedOffOverTheVoidItClutches() {
        BotBrain brain = new BotBrain(BotBrain.Skill.BUNDLED, new Random(8));
        BotBrain.Sight sight = offTheEdge();
        assertTrue(BotBrain.overVoid(sight.ground, sight.x, sight.y, sight.z));
        assertArrayEquals(new int[] {0, 0, 0, 5}, BotBrain.clutchPlace(sight), "the east side of the edge block");
        BotBrain.Controls controls = null;
        int tick = 0;
        for (; tick < 6; tick++) {
            controls = brain.think(sight);
            assertEquals(0, controls.swings, "no click at the other while it clutches");
            assertFalse(controls.sprint);
            if (tick == 0) {
                assertEquals(4, controls.slot, "its wool in hand");
                sight.held = 4;
            }
            if (controls.place != null) {
                break;
            }
            assertTrue(controls.pitch - sight.pitch <= BotBrain.Skill.BUNDLED.turn + 1e-3, "its head at its turn");
            sight.yaw = controls.yaw;
            sight.pitch = controls.pitch;
        }
        assertArrayEquals(new int[] {0, 0, 0, 5}, controls.place, "a right click on the side it looks at, tick " + tick);
        assertTrue(tick >= 2, "it had to look down first: tick " + tick);
        assertTrue(controls.pitch > 70, "looking down at the side: " + controls.pitch);
        assertEquals(1.0, controls.cursor[0], 1e-3, "on that side");
        assertTrue(controls.cursor[1] >= 0 && controls.cursor[1] <= 1 && controls.cursor[2] >= 0 && controls.cursor[2] <= 1);
        BotBrain.Controls next = brain.think(sight);
        assertTrue(next.place == null, "one block, then it waits for its next (6 a second)");
    }

    @Test
    void itClutchesOnlyOverTheVoidAndWhereBlocksAreUsed() {
        BotBrain.Sight ground = offTheEdge();
        // ground six blocks under it: it lands there, no clutch
        ground.ground = (bx, by, bz) -> by == 0 && bx <= 0 || by == -5;
        assertFalse(BotBrain.overVoid(ground.ground, ground.x, ground.y, ground.z));
        BotBrain brain = new BotBrain(BotBrain.Skill.BUNDLED, new Random(9));
        for (int tick = 0; tick < 6; tick++) {
            assertTrue(brain.think(ground).place == null, "ground under it, tick " + tick);
        }
        BotBrain.Sight sumo = offTheEdge();
        sumo.blocks = false;
        brain = new BotBrain(BotBrain.Skill.BUNDLED, new Random(9));
        for (int tick = 0; tick < 6; tick++) {
            BotBrain.Controls controls = brain.think(sumo);
            assertTrue(controls.place == null, "not a game of blocks, tick " + tick);
            assertEquals(-1, controls.slot, "its sword stays in hand");
        }
        BotBrain.Sight none = offTheEdge();
        none.block = -1;
        brain = new BotBrain(BotBrain.Skill.BUNDLED, new Random(9));
        for (int tick = 0; tick < 6; tick++) {
            assertTrue(brain.think(none).place == null, "no block left in its kit");
        }
        BotBrain.Sight far = offTheEdge();
        // three blocks out: no block beside any place under it
        far.x = 3.5;
        assertTrue(BotBrain.clutchPlace(far) == null);
    }

    /**
     * Its platform at x 7 to 9 (y 0), the other at (4.5, 1, 0.5) on the end of their bridge of wool
     * (x -6 to 4, z 0) over the void, two blocks short of it: out of its hits.
     */
    private static BotBrain.Sight otherOnTheirBridge() {
        BotBrain.Sight sight = facing(4.5, 0.5);
        sight.x = 8.5;
        sight.yaw = 90;
        sight.pitch = 0;
        sight.ground = (bx, by, bz) -> by == 0 && (bx >= 7 && bx <= 9 || bx >= -6 && bx <= 4 && bz == 0);
        sight.blocks = true;
        sight.sword = 0;
        sight.held = 0;
        sight.block = 4;
        sight.targetOnGround = true;
        // its shears in slot 3 for the wool of the bridge, nothing for its own platform
        sight.breakable = (bx, by, bz) -> by == 0 && bz == 0 && bx >= -6 && bx <= 4 ? 3 : -1;
        return sight;
    }

    @Test
    void theOtherOverTheVoidOnABlockOfTheirsItBreaksIt() {
        BotBrain.Sight sight = otherOnTheirBridge();
        assertArrayEquals(new int[] {4, 0, 0}, BotBrain.holding(sight.ground, sight.tx, sight.ty, sight.tz));
        BotBrain brain = new BotBrain(BotBrain.Skill.BUNDLED, new Random(10));
        BotBrain.Controls controls = null;
        int tick = 0;
        for (; tick < 6; tick++) {
            controls = brain.think(sight);
            assertEquals(0, controls.swings, "no click at the other: its button is on the block");
            if (tick == 0) {
                assertEquals(3, controls.slot, "its shears for the wool");
                sight.held = 3;
            }
            if (controls.dig != null) {
                break;
            }
            sight.yaw = controls.yaw;
            sight.pitch = controls.pitch;
        }
        assertArrayEquals(new int[] {4, 0, 0, 5}, controls.dig, "the east side of the block under them, tick " + tick);
        assertEquals(0, controls.forward, "it stands while it breaks the block");
        assertEquals(0, controls.strafe, "it stands while it breaks the block");
        assertFalse(controls.sprint, "it stands while it breaks the block");
    }

    @Test
    void itBreaksNoBlockOfTheMapNorOneOverGroundNorWithinItsHits() {
        BotBrain.Sight map = otherOnTheirBridge();
        map.breakable = (bx, by, bz) -> -1;
        BotBrain brain = new BotBrain(BotBrain.Skill.BUNDLED, new Random(11));
        for (int tick = 0; tick < 6; tick++) {
            BotBrain.Controls controls = brain.think(map);
            assertTrue(controls.dig == null, "a block of the map, or one the game refused");
            map.yaw = controls.yaw;
            map.pitch = controls.pitch;
        }
        BotBrain.Sight ground = otherOnTheirBridge();
        ground.ground = (bx, by, bz) -> by == 0 && (bx >= 7 && bx <= 9 || bx >= -6 && bx <= 4 && bz == 0) || by == -3;
        assertTrue(BotBrain.holding(ground.ground, ground.tx, ground.ty, ground.tz) == null, "ground under it: no fall");
        BotBrain.Sight close = otherOnTheirBridge();
        close.tx = 6.5;
        close.ground = (bx, by, bz) -> by == 0 && (bx >= 7 && bx <= 9 || bx >= -6 && bx <= 6 && bz == 0);
        close.breakable = (bx, by, bz) -> by == 0 && bz == 0 && bx >= -6 && bx <= 6 ? 3 : -1;
        brain = new BotBrain(BotBrain.Skill.BUNDLED, new Random(12));
        for (int tick = 0; tick < 6; tick++) {
            assertTrue(brain.think(close).dig == null, "within its hits it fights");
        }
        BotBrain.Sight jumping = otherOnTheirBridge();
        jumping.targetOnGround = false;
        assertTrue(new BotBrain(BotBrain.Skill.BUNDLED, new Random(13)).think(jumping).dig == null, "in the air");
        BotBrain.Sight across = otherOnTheirBridge();
        // standing across two blocks: one broken holds them all the same
        assertTrue(BotBrain.holding(across.ground, 4.05, 1, 0.5) == null);
    }

    @Test
    void itJumpsUpAStepOfOneBlockOnItsWay() {
        BotBrain.Sight sight = facing(6.5, 0.5);
        sight.ty = 2;
        sight.blocks = true;
        // a step of one block at x 1, y 1 (on the floor at y 0), then the other up there
        sight.ground = (bx, by, bz) -> by == 0 || by == 1 && bx >= 1;
        BotBrain brain = new BotBrain(BotBrain.Skill.BUNDLED, new Random(14));
        assertTrue(brain.think(sight).jump, "the step before it");
        BotBrain.Sight wall = facing(6.5, 0.5);
        wall.blocks = true;
        wall.ground = (bx, by, bz) -> by == 0 || bx == 1 && (by == 1 || by == 2);
        assertFalse(new BotBrain(BotBrain.Skill.BUNDLED, new Random(14)).think(wall).jump, "a wall of two blocks");
        BotBrain.Sight top = facing(6.5, 0.5);
        top.ground = sight.ground;
        top.ty = 2;
        assertFalse(new BotBrain(BotBrain.Skill.BUNDLED, new Random(14)).think(top).jump,
                "outside the games of blocks, as before: it jumps only to the other right there");
    }

    @Test
    void itsLookMeetsTheSideOfABlockAsAClientsDoes() {
        BotBrain.Sight sight = facing(5.5, 0.5);
        // straight down from (0.5, 1, 0.5): the top of the block under its feet
        double[] down = BotBrain.rayHit(sight, 0, 90, 0, 0, 0);
        assertEquals(BotBrain.UP, (int) down[0]);
        assertEquals(1.0, down[2], 1e-9);
        // straight ahead (+x at yaw -90), at the height of its eyes (2.62): the west side of the block at x 2
        double[] ahead = BotBrain.rayHit(sight, -90, 0, 2, 2, 0);
        assertEquals(4, (int) ahead[0]);
        assertEquals(2.0, ahead[1], 1e-9);
        assertTrue(BotBrain.rayHit(sight, -90, 0, 2, 1, 0) == null, "the block under that height");
        assertTrue(BotBrain.rayHit(sight, -90, 0, 6, 2, 0) == null, "4.5 from its eyes at most");
        assertTrue(BotBrain.rayHit(sight, 90, 0, 2, 2, 0) == null, "behind it");
    }
}
