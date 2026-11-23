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
    void itsHitsGoByWhereTheOtherWasItsPingAgo() {
        // the owner's ask of 23/11: a ping of 40 ms, as a player's; without, it had the first hit
        // every time. Many clicks a tick, every one on its aim, no reaction: only the ping differs.
        BotBrain.Skill bundled = BotBrain.Skill.BUNDLED;
        BotBrain.Skill lagging = new BotBrain.Skill(40, 1, 3.0, bundled.strafe, 0, 0, 0, bundled.turn, bundled.healAt,
                bundled.blocksPerSecond, 40);
        BotBrain.Skill none = new BotBrain.Skill(40, 1, 3.0, bundled.strafe, 0, 0, 0, bundled.turn, bundled.healAt,
                bundled.blocksPerSecond, 0);
        assertEquals(40, BotBrain.Skill.BUNDLED.ping, "the bundled one: 40 ms");
        BotBrain late = new BotBrain(lagging, new Random(21));
        BotBrain instant = new BotBrain(none, new Random(21));
        // the other runs in: 3.5 from its eyes, then 2.9 (in its reach of 3)
        BotBrain.Sight far = facing(0.5 + 3.9, 0.5);
        assertFalse(late.think(far).hit);
        assertFalse(instant.think(facing(0.5 + 3.9, 0.5)).hit);
        BotBrain.Sight in = facing(0.5 + 3.3, 0.5);
        assertTrue(instant.think(facing(0.5 + 3.3, 0.5)).hit, "no ping: the hit the tick they come in");
        assertFalse(late.think(in).hit, "40 ms: it sees them where they were, 0.8 of the way back: out of its reach");
        assertEquals(0.5 + 3.3, in.tx, 1e-9, "the sight given back as it was");
        assertTrue(late.think(facing(0.5 + 3.3, 0.5)).hit, "the tick after, they were there: its hit");
        double[] lagged = late.lagged();
        assertEquals(0.5 + 3.3, lagged[0], 1e-9);
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

    // --- The block edit (step 3) ------------------------------------------------------------------

    /**
     * The station of the block edit 1v1 as EditStation builds it, from its centre (0, 0, 0): the
     * wall of wool at x 0, z -2 to 2, y 1 to 3, the beam over it, the floor at y 0, the glass case;
     * the holes: the blocks of the wall broken.
     */
    private static final class EditWorld {
        final java.util.Set<String> holes = new java.util.HashSet<>();

        boolean wall(int x, int y, int z) {
            return x == 0 && y >= 1 && y <= 3 && Math.abs(z) <= 2;
        }

        boolean solid(int x, int y, int z) {
            if (wall(x, y, z)) {
                return !holes.contains(x + "," + y + "," + z);
            }
            boolean inside = x >= -6 && x <= 6 && Math.abs(z) <= 3 && y >= 0 && y <= 5;
            boolean glass = inside && (x == -6 || x == 6 || Math.abs(z) == 3 || y == 0 || y == 5);
            return glass || y == 0 && Math.abs(x) <= 5 && Math.abs(z) <= 2 || x == 0 && y == 4 && Math.abs(z) <= 2;
        }

        void open(int x, int y, int z) {
            holes.add(x + "," + y + "," + z);
        }

        void close(int x, int y, int z) {
            holes.remove(x + "," + y + "," + z);
        }
    }

    /** RagoBot against the wall on its side (east), the other at (x, 1, z); its sword, shears, wool in slots 0, 1, 2. */
    private static BotBrain.Sight atTheWall(EditWorld world, double x, double z) {
        BotBrain.Sight sight = new BotBrain.Sight();
        sight.x = 0.5 + BotBrain.AT_WALL;
        sight.y = 1;
        sight.z = 0.5;
        sight.yaw = 90;
        sight.onGround = true;
        sight.health = 20;
        sight.fighting = true;
        sight.tx = x;
        sight.ty = 1;
        sight.tz = z;
        sight.targetOnGround = true;
        sight.ground = world::solid;
        sight.breakable = (bx, by, bz) -> world.wall(bx, by, bz) && world.solid(bx, by, bz) ? 1 : -1;
        sight.edit = true;
        sight.blocks = true;
        sight.sword = 0;
        sight.held = 0;
        sight.block = 2;
        return sight;
    }

    /** Its head, its hand: where it looked and what it held, for the next tick. */
    private static void follow(BotBrain.Sight sight, BotBrain.Controls controls) {
        sight.yaw = controls.yaw;
        sight.pitch = controls.pitch;
        if (controls.slot >= 0) {
            sight.held = controls.slot;
        }
    }

    /** The block a placement makes: beside the block clicked, on its side. */
    private static int[] placedAt(int[] place) {
        int[] face = BotBrain.FACES[place[3]];
        return new int[] {place[0] + face[0], place[1] + face[1], place[2] + face[2]};
    }

    /** It thinks until it digs (at most 20 ticks), with no click at the other before. */
    private static BotBrain.Controls untilItDigs(BotBrain brain, BotBrain.Sight sight) {
        for (int tick = 0; tick < 20; tick++) {
            BotBrain.Controls controls = brain.think(sight);
            assertFalse(controls.hit, "no hit through the wall, tick " + tick);
            assertTrue(controls.place == null, "nothing to close, tick " + tick);
            follow(sight, controls);
            if (controls.dig != null) {
                return controls;
            }
        }
        throw new AssertionError("it never opened the wall");
    }

    @Test
    void inTheBlockEditItOpensHitsThroughTheHoleAndClosesIt() {
        EditWorld world = new EditWorld();
        BotBrain.Sight sight = atTheWall(world, -0.5 - BotBrain.HALF_WIDTH + 0.18, 0.5);
        BotBrain brain = new BotBrain(skill(20, 1, 3), new Random(21));
        BotBrain.Controls controls = untilItDigs(brain, sight);
        assertArrayEquals(new int[] {0, 2, 0, 5}, controls.dig,
                "the block of the wall at the height of its head, in front of the other, its east side");
        assertEquals(1, sight.held, "its shears");
        assertEquals(0, controls.forward, 0, "it stands while it breaks the block");
        assertEquals(0, controls.strafe, 0);
        world.open(0, 2, 0);
        int hitAt = -1;
        for (int tick = 0; tick < 20 && hitAt < 0; tick++) {
            controls = brain.think(sight);
            follow(sight, controls);
            assertTrue(controls.dig == null && controls.place == null, "it hits first, tick " + tick);
            if (controls.hit) {
                hitAt = tick;
            }
        }
        assertTrue(hitAt >= 3, "its hit through the hole, after its reaction time (3 ticks): tick " + hitAt);
        assertEquals(0, sight.held, "its sword");
        int[] place = null;
        for (int tick = 0; tick < 20 && place == null; tick++) {
            controls = brain.think(sight);
            assertFalse(controls.hit, "its point: it closes, tick " + tick);
            follow(sight, controls);
            place = controls.place;
        }
        assertTrue(place != null, "it closes the wall after its hit");
        assertEquals(2, sight.held, "its wool");
        assertArrayEquals(new int[] {0, 2, 0}, placedAt(place), "the block back in the hole");
        assertTrue(world.wall(place[0], place[1], place[2]) || place[1] == 4, "against a block of the wall or the beam");
        assertTrue(controls.cursor != null);
    }

    @Test
    void theOtherOutOfItsHitsWithoutItsHitItClosesAllTheSame() {
        EditWorld world = new EditWorld();
        BotBrain.Sight sight = atTheWall(world, -0.5 - BotBrain.HALF_WIDTH + 0.18, 0.5);
        BotBrain brain = new BotBrain(skill(20, 1, 3), new Random(22));
        // its speed set to nothing at the start of the round, the wall whole: not a hit of the other
        sight.knocked = true;
        brain.think(sight);
        sight.knocked = false;
        untilItDigs(brain, sight);
        world.open(0, 2, 0);
        // the other steps back out of its hits before its first hit
        sight.tx = -3.5;
        int[] place = null;
        for (int tick = 0; tick < 20 && place == null; tick++) {
            BotBrain.Controls controls = brain.think(sight);
            assertFalse(controls.hit, "out of its hits, tick " + tick);
            follow(sight, controls);
            place = controls.place;
        }
        assertTrue(place != null, "it closes the hole");
        assertArrayEquals(new int[] {0, 2, 0}, placedAt(place));
    }

    @Test
    void hitByTheOtherItHitsBackThroughTheirHoleThenCloses() {
        EditWorld world = new EditWorld();
        // the other opened the wall in front of it, and hit it from their side, out of its hits
        world.open(0, 2, 0);
        BotBrain.Sight sight = atTheWall(world, -2.5, 0.5);
        BotBrain brain = new BotBrain(skill(20, 1, 3), new Random(23));
        sight.knocked = true;
        for (int tick = 0; tick < 30; tick++) {
            BotBrain.Controls controls = brain.think(sight);
            sight.knocked = false;
            follow(sight, controls);
            assertFalse(controls.hit, "out of its hits, tick " + tick);
            assertTrue(controls.dig == null, "no other block broken, tick " + tick);
            assertTrue(controls.place == null, "their point: it does not close, tick " + tick);
        }
        // the other comes to the wall: its hit back, then the wall closed
        sight.tx = -0.5 - BotBrain.HALF_WIDTH + 0.18;
        boolean hit = false;
        int[] place = null;
        for (int tick = 0; tick < 40 && place == null; tick++) {
            BotBrain.Controls controls = brain.think(sight);
            follow(sight, controls);
            assertTrue(controls.dig == null, "no other block broken, tick " + tick);
            hit |= controls.hit;
            if (controls.place != null) {
                assertTrue(hit, "its hit back first");
                place = controls.place;
            }
        }
        assertTrue(place != null, "it closes after its hit back");
        assertArrayEquals(new int[] {0, 2, 0}, placedAt(place));
    }

    @Test
    void itNeverHitsThroughTheWallAndOpensOnlyForAHit() {
        EditWorld world = new EditWorld();
        BotBrain.Sight far = atTheWall(world, -2.5, 0.5);
        BotBrain brain = new BotBrain(skill(20, 1, 0), new Random(24));
        for (int tick = 0; tick < 40; tick++) {
            BotBrain.Controls controls = brain.think(far);
            follow(far, controls);
            assertTrue(controls.dig == null, "the other out of its hits even through a hole: it waits, tick " + tick);
            assertFalse(controls.hit);
            assertEquals(0, controls.swings);
        }
        BotBrain.Sight close = atTheWall(world, -0.5 - BotBrain.HALF_WIDTH + 0.18, 0.5);
        close.blocks = false;
        brain = new BotBrain(skill(20, 1, 0), new Random(25));
        for (int tick = 0; tick < 40; tick++) {
            BotBrain.Controls controls = brain.think(close);
            follow(close, controls);
            assertFalse(controls.hit, "the wall whole between them, tick " + tick);
            assertTrue(controls.dig == null, "its clicks on the blocks do not work here");
        }
        world.open(0, 1, 0);
        brain = new BotBrain(skill(20, 1, 0), new Random(26));
        for (int tick = 0; tick < 40; tick++) {
            BotBrain.Controls controls = brain.think(close);
            follow(close, controls);
            assertFalse(controls.hit, "a hole at their feet only: no line from its eyes to them, tick " + tick);
        }
    }

    @Test
    void itGoesAgainstTheWallInFrontOfTheOther() {
        EditWorld world = new EditWorld();
        BotBrain.Sight sight = atTheWall(world, -0.5 - BotBrain.HALF_WIDTH + 0.18, -1.5);
        sight.x = 3.5;
        BotBrain.Controls controls = new BotBrain(skill(20, 1, 3), new Random(27)).think(sight);
        double[] way = BotBrain.way(controls.yaw, controls.forward, controls.strafe);
        double dx = 0.5 + BotBrain.AT_WALL - sight.x;
        double dz = -1.5 - sight.z;
        double length = Math.hypot(dx, dz);
        assertEquals(dx / length, way[0], 1e-6, "towards its place against the wall, in front of the other");
        assertEquals(dz / length, way[1], 1e-6);
        assertFalse(controls.sprint || controls.jump);
        assertTrue(controls.dig == null && !controls.hit);
    }
}
