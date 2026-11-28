package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The RagoBot of Block Method 1v1 on its way to the other: a bridge as a player's, out of a box. */
class BotBrainTravelTest {

    /**
     * On its island (the blocks of x 0 and less at y 0), at (x, 1, 0.5), its back to the other, who
     * stands on theirs from x 30 on: the void between them.
     */
    private static BotBrain.Sight onItsIsland(double x) {
        BotBrain.Sight sight = new BotBrain.Sight();
        sight.x = x;
        sight.y = 1;
        sight.z = 0.5;
        sight.onGround = true;
        sight.health = 20;
        sight.fighting = true;
        sight.tx = 32.5;
        sight.ty = 1;
        sight.tz = 0.5;
        sight.targetOnGround = true;
        sight.yaw = 90;
        sight.ground = (bx, by, bz) -> by == 0 && (bx <= 0 || bx >= 30);
        sight.bridge = true;
        sight.blocks = true;
        sight.block = 4;
        sight.sword = 0;
        sight.held = 0;
        return sight;
    }

    private static BotBrain brain() {
        return new BotBrain(BotBrain.Skill.SOLO, new Random(28));
    }

    @Test
    void itWalksBackwardsToTheEdgeItsBlocksInHand() {
        BotBrain.Controls controls = brain().think(onItsIsland(0.2));
        assertEquals(4, controls.slot, "its wool");
        assertTrue(controls.forward < -0.9, "backwards along the way: " + controls.forward);
        assertEquals(0, controls.strafe, 1e-6, "down the middle of its block");
        assertFalse(controls.sneak, "far from the edge yet");
        assertFalse(controls.sprint);
        assertEquals(0, controls.swings, "nobody to hit");
    }

    @Test
    void itSneaksAtTheEdge() {
        BotBrain.Controls controls = brain().think(onItsIsland(0.7));
        assertTrue(controls.sneak, "half a block from the edge");
        assertEquals(-BotBrain.SNEAK_INPUT, controls.forward, 0.05, "its keys as a sneaking client's");
    }

    @Test
    void pastTheEdgeItPlacesAgainstTheSideOfItsLastBlock() {
        BotBrain brain = brain();
        BotBrain.Sight sight = onItsIsland(1.25);
        sight.held = 4;
        BotBrain.Controls controls = null;
        for (int tick = 0; tick < 12; tick++) {
            controls = brain.think(sight);
            assertTrue(controls.sneak, "held by the sneak over the void, tick " + tick);
            if (controls.place != null) {
                break;
            }
            sight.yaw = controls.yaw;
            sight.pitch = controls.pitch;
        }
        assertNotNull(controls.place, "a block placed within 12 ticks");
        assertArrayEquals(new int[] {0, 0, 0, 5}, controls.place, "the east side of its last block");
        assertEquals(1.0, controls.cursor[0], 1e-3, "on that side");
        assertTrue(controls.pitch > 70, "looking down, back at its last block: " + controls.pitch);
    }

    @Test
    void itGoesAlongTheAxisTheOtherIsFurthestAlong() {
        BotBrain brain = brain();
        brain.chooseAxis(10, 3);
        assertEquals(0, brain.axis());
        assertEquals(1, brain.along());
        brain.chooseAxis(2, 3);
        assertEquals(0, brain.axis(), "kept while the other is still ahead on it");
        brain.chooseAxis(1, 6);
        assertEquals(2, brain.axis(), "much further along the other axis");
        assertEquals(1, brain.along());
        brain.chooseAxis(-5, 1);
        assertEquals(0, brain.axis());
        assertEquals(-1, brain.along(), "behind it now");
    }

    @Test
    void aBlockAtItsHeadIsInItsWayAStepIsNot() {
        BotBrain.Sight sight = onItsIsland(0.5);
        sight.ground = (bx, by, bz) -> by == 0 || bx == 1 && by == 2 && bz == 0;
        assertArrayEquals(new int[] {1, 2, 0}, BotBrain.inTheWay(sight, 1, 1, 0));
        sight.ground = (bx, by, bz) -> by == 0 || bx == 1 && by == 1 && bz == 0;
        assertNull(BotBrain.inTheWay(sight, 1, 1, 0), "a step: it jumps it");
        sight.ground = (bx, by, bz) -> by == 0 || bx == 1 && by == 1 && bz == 0 || bx == 0 && by == 3 && bz == 0;
        assertArrayEquals(new int[] {1, 1, 0}, BotBrain.inTheWay(sight, 1, 1, 0), "under a roof it cannot jump it");
        assertNull(BotBrain.inTheWay(sight, 1, -1, 0), "nothing the other way");
    }

    /** At (0.5, 1, 0.5), walled in by wool two blocks high on its four sides, on the floor of the map. */
    private static BotBrain.Sight boxed() {
        BotBrain.Sight sight = onItsIsland(0.5);
        sight.ground = (bx, by, bz) -> by == 0 || (by == 1 || by == 2) && Math.abs(bx) + Math.abs(bz) == 1;
        sight.breakable = (bx, by, bz) -> by >= 1 ? 2 : -1;
        return sight;
    }

    @Test
    void inAFullBoxItDigsOutAwayFromTheOther() {
        BotBrain.Sight sight = boxed();
        assertArrayEquals(new int[] {-1, 2, 0}, brain().boxedOut(sight, 1, 0, 0, new double[] {32.5, 1, 0.5}),
                "the side away from the other, at its head first");
        sight.tx = -30;
        assertArrayEquals(new int[] {1, 2, 0}, brain().boxedOut(sight, 1, 0, 0, new double[] {-30, 1, 0.5}));
        BotBrain.Controls controls = brain().think(boxed());
        assertEquals(2, controls.slot, "its shears for the wool");
        assertEquals(0, controls.forward, 1e-6, "still while it digs");
    }

    @Test
    void aWayOutOnFootIsNoBox() {
        BotBrain.Sight sight = boxed();
        sight.ground = (bx, by, bz) -> by == 0 || (by == 1 || by == 2) && Math.abs(bx) + Math.abs(bz) == 1 && bx != -1;
        assertNull(brain().boxedOut(sight, 1, 0, 0, new double[] {32.5, 1, 0.5}), "open to the west");
        sight.ground = (bx, by, bz) -> by == 0 || by == 1 && Math.abs(bx) + Math.abs(bz) == 1;
        assertNull(brain().boxedOut(sight, 1, 0, 0, new double[] {32.5, 1, 0.5}), "one block high: it jumps out");
        sight.ground = (bx, by, bz) -> by == 0 || by == 1 && Math.abs(bx) + Math.abs(bz) == 1 || by == 3 && bx == 0 && bz == 0;
        assertArrayEquals(new int[] {-1, 1, 0}, brain().boxedOut(sight, 1, 0, 0, new double[] {32.5, 1, 0.5}),
                "one block high under a roof: it digs");
    }

    @Test
    void groundBelowLooksDownAFewBlocks() {
        BotBrain.Ground ground = (bx, by, bz) -> by == -2;
        assertTrue(BotBrain.groundBelow(ground, 0, 1, 0));
        assertFalse(BotBrain.groundBelow((bx, by, bz) -> by == 1 - BotBrain.DROP - 1, 0, 1, 0));
    }
}
