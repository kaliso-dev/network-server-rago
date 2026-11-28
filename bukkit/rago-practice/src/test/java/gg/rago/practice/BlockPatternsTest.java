package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Block Pattern Practice (the owner's ask of 27/11): the ten first techniques of Block Method, and RagoBot's run. */
class BlockPatternsTest {

    @Test
    void tenTechniquesInTheOrderOfTheVideo() {
        assertEquals(10, BlockPatterns.TECHNIQUES.length, "the ten first, the owner's choice of 27/11");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < BlockPatterns.TECHNIQUES.length; i++) {
            BlockPatterns.Technique t = BlockPatterns.TECHNIQUES[i];
            assertEquals("t" + (i + 1), t.id);
            assertTrue(t.name.startsWith("#" + (i + 1) + " "), t.name);
            assertTrue(ids.add(t.id));
            assertTrue(t.blocks.length >= 1 && t.path.length >= 1, t.id);
            assertTrue(t.side == 1 || t.side == -1, t.id);
            assertTrue(t.wait >= 0 && t.wait <= 100, t.id + ": a few seconds at most to build");
            assertEquals(t, BlockPatterns.of(t.id));
        }
        assertNull(BlockPatterns.of("t11"));
        assertEquals(BlockPatternPractice.ids().size(), BlockPatterns.TECHNIQUES.length, "a level each");
        assertTrue(BlockPatternPractice.LEVELS[0].timed, "a time on each board");
    }

    @Test
    void everyPatternCanBePlacedInItsOrder() {
        for (BlockPatterns.Technique t : BlockPatterns.TECHNIQUES) {
            assertTrue(BlockPatterns.placeable(t), t.id + ": each block on the floor or against one placed before");
            for (int[] b : t.blocks) {
                assertTrue(b[2] >= 1 && b[2] < t.path[0][1], t.id + ": between the player and RagoBot");
                assertFalse(b[0] == 0 && b[1] <= 1 && b[2] == 0, t.id + ": never where the player stands");
            }
        }
        BlockPatterns.Technique floating = new BlockPatterns.Technique("x", "#0 x", "", new int[][] {{0, 2, 2}},
                new int[0][], new double[][] {{0, 12}}, 0, false, 1);
        assertFalse(BlockPatterns.placeable(floating), "a block in the air, against nothing");
    }

    @Test
    void theTunnelIsThereBeforeAndNeverInTheWay() {
        BlockPatterns.Technique tunnel = BlockPatterns.of("t10");
        assertTrue(tunnel.breaks, "RagoBot breaks through the blocks closing it");
        assertTrue(BlockPatterns.inBoxes(tunnel.decor, -1, 0, 5) && BlockPatterns.inBoxes(tunnel.decor, 1, 1, 5));
        assertTrue(BlockPatterns.inBoxes(tunnel.decor, 0, 2, 0), "a roof over the player");
        assertFalse(BlockPatterns.inBoxes(tunnel.decor, 0, 0, 0) || BlockPatterns.inBoxes(tunnel.decor, 0, 1, 0),
                "room for the player");
        for (double z = 0; z <= 12; z++) {
            assertFalse(BlockPatterns.inBoxes(tunnel.decor, 0, 0, (int) z), "room for RagoBot along it");
        }
    }

    /** A flat floor with these blocks (in the arena, y 0 the first over the floor). */
    private static PatternBot.Ground ground(int[]... blocks) {
        Set<String> solid = new HashSet<>();
        for (int[] b : blocks) {
            solid.add(b[0] + "," + b[1] + "," + b[2]);
        }
        return (x, y, z) -> y < 0 || solid.contains(x + "," + y + "," + z);
    }

    @Test
    void itStartsOnItsWayAndRunsAtThePlayer() {
        PatternBot bot = new PatternBot(BlockPatterns.of("t1"));
        double[] start = bot.start();
        assertEquals(0.5, start[0], 1e-9);
        assertEquals(12.5, start[1], 1e-9);
        double[] v = bot.steer(0.5, 0, 12.5, 0, 0, 0, true, 0.5, 0.5, ground());
        assertTrue(v[2] < 0 && Math.abs(v[0]) < 1e-9, "straight at the player, towards -z");
        assertEquals(ComboAi.SPRINT_GROUND, -v[2], 1e-9, "sprinting on the ground");
        assertTrue(bot.sprinting());
        // the player's right is -x in the arena: #3 comes in their left lane
        assertEquals(1.5, new PatternBot(BlockPatterns.of("t3")).start()[0], 1e-9);
    }

    @Test
    void itJumpsABlockAndGoesRoundAWall() {
        PatternBot bot = new PatternBot(BlockPatterns.of("t1"));
        // one block in its way, room over it: it jumps
        double[] v = bot.steer(0.5, 0, 2.5, 0, 0, -0.2, true, 0.5, -3, ground(new int[] {0, 0, 1}));
        assertEquals(ComboAi.JUMP, v[1], 1e-9);
        // in the air: no jump
        v = bot.steer(0.5, 0.4, 2.5, 0, 0.3, -0.2, false, 0.5, -3, ground(new int[] {0, 0, 1}));
        assertEquals(0.3, v[1], 1e-9);
        // a pillar of two: no jump, round it on its side (#2: the player's right, -x)
        PatternBot round = new PatternBot(BlockPatterns.of("t2"));
        v = round.steer(0.5, 0, 2.5, 0, 0, -0.2, true, 0.5, -3, ground(new int[] {0, 0, 1}, new int[] {0, 1, 1}));
        assertEquals(0, v[1], 1e-9, "no jump");
        assertTrue(v[0] < 0, "aside, to the player's right");
        assertNull(round.breaking(), "it does not break in #2");
        // an arm at head height over nothing: it cannot go under it either
        v = round.steer(0.5, 0, 2.5, 0, 0, -0.2, true, 0.5, -3, ground(new int[] {0, 1, 1}));
        assertTrue(v[0] < 0 && v[1] == 0);
        // a roof over the block in its way: no jump
        v = round.steer(0.5, 0, 2.5, 0, 0, -0.2, true, 0.5, -3, ground(new int[] {0, 0, 1}, new int[] {0, 2, 1}));
        assertEquals(0, v[1], 1e-9);
    }

    @Test
    void inTheTunnelItBreaksTheHeadBlockFirst() {
        PatternBot bot = new PatternBot(BlockPatterns.of("t10"));
        int[] roof3 = {0, 2, 3};
        int[] roof4 = {0, 2, 4};
        double[] v = bot.steer(0.5, 0, 4.5, 0, 0, 0, true, 0.5, 0.5,
                ground(new int[] {0, 0, 3}, new int[] {0, 1, 3}, roof3, roof4));
        int[] breaking = bot.breaking();
        assertNotNull(breaking);
        assertEquals(1, breaking[1], "the block at its head first");
        assertEquals(3, breaking[2]);
        assertEquals(0, v[2], 1e-9, "it stands still while it breaks");
        bot.steer(0.5, 0, 4.5, 0, 0, 0, true, 0.5, 0.5, ground(new int[] {0, 0, 3}, roof3, roof4));
        assertEquals(0, bot.breaking()[1], "then the one at its feet: the roof of the tunnel, no jump");
        bot.steer(0.5, 0, 4.5, 0, 0, 0, true, 0.5, 0.5, ground(roof3, roof4));
        assertNull(bot.breaking(), "the way open");
    }

    @Test
    void itFollowsTheWayOfTheDoor() {
        // #7: it comes down the left lane of the player (x 2 in the arena) to z 4, then at the player
        PatternBot bot = new PatternBot(BlockPatterns.of("t7"));
        double[] v = bot.steer(2.5, 0, 12.5, 0, 0, 0, true, 0.5, 0.5, ground());
        assertTrue(Math.abs(v[0]) < 1e-9 && v[2] < 0, "straight down its lane first");
        v = bot.steer(2.5, 0, 4.6, 0, 0, 0, true, 0.5, 0.5, ground());
        assertTrue(v[0] < 0, "then at the player");
    }
}
