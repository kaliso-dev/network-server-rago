package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

/**
 * The enemy of the Block in training: it hits only through its side open, breaks the player's
 * block there in the time a 1.8 player takes, and has to break again a block put back. The world
 * is a stand-in: a type at each place, air elsewhere, and the places of the player's blocks.
 */
class BlockInEnemyTest {

    /** The player stands here: the feet in (0, 65, 0), the head in (0, 66, 0). */
    private static final int Y = 65;

    private final Map<String, Material> types = new HashMap<>();
    private final Set<String> theirs = new HashSet<>();
    /** What the enemy did, tick by tick: "hit dx dz", "cracks x y z stage", "breaks x y z". */
    private final List<String> done = new ArrayList<>();
    private int now;

    private final BlockInEnemy enemy = new BlockInEnemy(new Random(7), block -> theirs.contains(key(block)),
            new BlockInEnemy.Hands() {
                @Override
                public void hit(int[] side) {
                    done.add(now + ": hit " + side[0] + " " + side[1]);
                }

                @Override
                public void cracks(Block block, int stage) {
                    done.add(now + ": cracks " + key(block) + " " + stage);
                }

                @Override
                public void breaks(Block block) {
                    done.add(now + ": breaks " + key(block));
                    // as the arena's restore: the block goes back to the air it was
                    types.remove(key(block));
                    theirs.remove(key(block));
                }
            });

    private static String key(int x, int y, int z) {
        return x + " " + y + " " + z;
    }

    private static String key(Block block) {
        return key(block.getX(), block.getY(), block.getZ());
    }

    private Block block(int x, int y, int z) {
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getX":
                            return x;
                        case "getY":
                            return y;
                        case "getZ":
                            return z;
                        case "getType":
                            return types.getOrDefault(key(x, y, z), Material.AIR);
                        case "getRelative":
                            return block(x + (Integer) args[0], y + (Integer) args[1], z + (Integer) args[2]);
                        case "equals":
                            return args[0] instanceof Block && key((Block) args[0]).equals(key(x, y, z));
                        case "hashCode":
                            return key(x, y, z).hashCode();
                        case "toString":
                            return "block " + key(x, y, z);
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private Block feet() {
        return block(0, Y, 0);
    }

    /** A block of the player at this side, at the feet (up 0) or the head (up 1). */
    private void wool(int[] side, int up) {
        types.put(key(side[0], Y + up, side[1]), Material.WOOL);
        theirs.add(key(side[0], Y + up, side[1]));
    }

    /** The player blocks themselves in: wool on the four sides, at the feet and at the head. */
    private void blockedIn() {
        for (int[] side : BlockInEnemy.SIDES) {
            wool(side, 0);
            wool(side, 1);
        }
    }

    private void ticks(int count) {
        for (int i = 0; i < count; i++) {
            enemy.tick(feet(), now);
            now++;
        }
    }

    private List<String> of(String what) {
        List<String> found = new ArrayList<>();
        for (String line : done) {
            if (line.contains(": " + what)) {
                found.add(line);
            }
        }
        return found;
    }

    private static int tickOf(String line) {
        return Integer.parseInt(line.substring(0, line.indexOf(':')));
    }

    @Test
    void woolTakesFiveTicksWithShearsAsForA18Player() {
        assertEquals(5, BlockInEnemy.breakTicks(Material.WOOL));
        // the hand on wool: 1 / 0.8 / 30 of it a tick, 24 ticks (1.2 s)
        assertEquals(24, BlockInEnemy.ticksToBreak(0.8f, 1.0f));
        assertEquals(-1, BlockInEnemy.breakTicks(Material.ENDER_STONE));
    }

    @Test
    void aPlayerInTheOpenIsHitAboutEverySecondAndNothingIsBroken() {
        ticks(200);
        List<String> hits = of("hit");
        assertTrue(hits.size() >= 200 / (BlockInEnemy.MOVE_TICKS + BlockInEnemy.MOVE_SPREAD), done.toString());
        for (int i = 1; i < hits.size(); i++) {
            int gap = tickOf(hits.get(i)) - tickOf(hits.get(i - 1));
            assertTrue(gap >= BlockInEnemy.MOVE_TICKS - BlockInEnemy.MOVE_SPREAD
                    && gap <= BlockInEnemy.MOVE_TICKS + BlockInEnemy.MOVE_SPREAD, done.toString());
        }
        assertTrue(of("breaks").isEmpty());
    }

    @Test
    void aPlayerBlockedInHasABlockOfTheEnemysSideBrokenFirstWithItsCracksThenIsHitThroughTheHole() {
        blockedIn();
        ticks(6);
        int[] side = enemy.side();
        assertNotNull(side);
        assertEquals(1, of("breaks").size(), done.toString());
        String broken = of("breaks").get(0);
        String place = broken.substring(broken.indexOf("breaks ") + "breaks ".length());
        assertTrue(place.equals(key(side[0], Y, side[1])) || place.equals(key(side[0], Y + 1, side[1])), done.toString());
        // the five ticks of the shears, the cracks growing on the block, then it goes; no hit before
        assertEquals(Arrays.asList("0: cracks " + place + " 0", "1: cracks " + place + " 2", "2: cracks " + place + " 4",
                "3: cracks " + place + " 6", "4: cracks " + place + " 8", "5: cracks " + place + " -1", "5: breaks " + place), done);
        // through the hole, at its next move
        ticks(BlockInEnemy.MOVE_TICKS + BlockInEnemy.MOVE_SPREAD);
        List<String> hits = of("hit");
        assertEquals(1, hits.size(), done.toString());
        assertTrue(hits.get(0).endsWith("hit " + side[0] + " " + side[1]));
        assertTrue(tickOf(hits.get(0)) - 5 >= BlockInEnemy.MOVE_TICKS - BlockInEnemy.MOVE_SPREAD, done.toString());
    }

    @Test
    void aBlockPutBackStopsTheHitsAndHasToBeBrokenAgain() {
        blockedIn();
        // the player puts each block broken back at once
        for (int i = 0; i < 300; i++) {
            int before = of("breaks").size();
            ticks(1);
            if (of("breaks").size() > before) {
                blockedIn();
            }
        }
        assertTrue(of("hit").isEmpty(), "no hit while the side is closed: " + done);
        List<String> breaks = of("breaks");
        assertTrue(breaks.size() >= 300 / (BlockInEnemy.MOVE_TICKS + BlockInEnemy.MOVE_SPREAD + 5), done.toString());
        int[] side = enemy.side();
        for (int i = 0; i < breaks.size(); i++) {
            String place = breaks.get(i).substring(breaks.get(i).indexOf("breaks ") + "breaks ".length());
            assertTrue(place.equals(key(side[0], Y, side[1])) || place.equals(key(side[0], Y + 1, side[1])), done.toString());
            if (i > 0) {
                // its move, then the five ticks of the shears
                assertTrue(tickOf(breaks.get(i)) - tickOf(breaks.get(i - 1)) >= BlockInEnemy.MOVE_TICKS - BlockInEnemy.MOVE_SPREAD + 5,
                        done.toString());
            }
        }
    }

    @Test
    void itNeverHitsThroughAnotherSideOpen() {
        blockedIn();
        ticks(1);
        int[] side = enemy.side();
        // the side across from the enemy left open
        types.remove(key(-side[0], Y, -side[1]));
        theirs.remove(key(-side[0], Y, -side[1]));
        ticks(4);
        assertTrue(of("hit").isEmpty(), done.toString());
        assertTrue(of("breaks").isEmpty(), done.toString());
        ticks(1);
        assertEquals(1, of("breaks").size(), "its own side first: " + done);
        ticks(100);
        assertFalse(of("hit").isEmpty(), done.toString());
        for (String hit : of("hit")) {
            assertTrue(hit.endsWith("hit " + side[0] + " " + side[1]), done.toString());
        }
    }

    @Test
    void aSideClosedByTheDefenseIsNotItsSideAndWithNoSideToReachItWaits() {
        blockedIn();
        // three sides of the defense (end stone, not the player's): only the fourth is reachable
        int[] wool = BlockInEnemy.SIDES[2];
        for (int[] side : BlockInEnemy.SIDES) {
            if (side != wool) {
                for (int up = 0; up <= 1; up++) {
                    types.put(key(side[0], Y + up, side[1]), Material.ENDER_STONE);
                    theirs.remove(key(side[0], Y + up, side[1]));
                }
            }
        }
        ticks(6);
        assertEquals(wool, enemy.side());
        assertEquals(1, of("breaks").size(), done.toString());
        // the block put back as end stone: no side left to reach, nothing more
        for (int up = 0; up <= 1; up++) {
            types.put(key(wool[0], Y + up, wool[1]), Material.ENDER_STONE);
            theirs.remove(key(wool[0], Y + up, wool[1]));
        }
        done.clear();
        ticks(200);
        assertNull(enemy.side());
        assertTrue(done.isEmpty(), done.toString());
    }

    @Test
    void theCracksGoWhenThePlayerBreaksTheBlockThemselvesAndTheHoleIsHitThrough() {
        blockedIn();
        ticks(2);
        int[] side = enemy.side();
        String target = null;
        for (String line : of("cracks")) {
            target = line.substring(line.indexOf("cracks ") + "cracks ".length(), line.lastIndexOf(' '));
        }
        assertNotNull(target, done.toString());
        types.remove(target);
        theirs.remove(target);
        ticks(1);
        assertTrue(done.contains("2: cracks " + target + " -1"), done.toString());
        // it was at its move: the hit comes at once through the hole
        assertEquals(1, of("hit").size(), done.toString());
        assertTrue(of("hit").get(0).endsWith("hit " + side[0] + " " + side[1]));
        assertTrue(of("breaks").isEmpty());
    }
}
