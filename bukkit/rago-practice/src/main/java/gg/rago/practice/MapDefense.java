package gg.rago.practice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;

/**
 * The defense a map has on its beds (the owner's fireball fight map: wood, then end stone): the
 * blocks of a game (wool, end stone, wood, glass, clay, obsidian, ladders) joined to a bed, from
 * its height up and within {@value #REACH} blocks of it on each axis, are the side's own, as if
 * placed: the players break them, the blasts blow them up. Pure Java, as in RagoBedwars.
 */
final class MapDefense {

    /** How far from the bed, on each axis, a defense of the map goes at most. */
    static final int REACH = 4;
    private static final Set<Material> BLOCKS = EnumSet.of(Material.WOOL, Material.ENDER_STONE, Material.WOOD,
            Material.STAINED_GLASS, Material.GLASS, Material.STAINED_CLAY, Material.HARD_CLAY, Material.OBSIDIAN,
            Material.LADDER);
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** What stands at a place (x, y, z): the world, or a copy of the map. */
    interface Blocks {
        Material at(int x, int y, int z);
    }

    private MapDefense() {
    }

    /** The places (x, y, z each) of the defense joined to these bed blocks (both halves of each bed). */
    static List<int[]> of(List<int[]> beds, Blocks blocks) {
        List<int[]> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Deque<int[]> todo = new ArrayDeque<>();
        for (int[] bed : beds) {
            seen.add(key(bed[0], bed[1], bed[2]));
            todo.add(bed);
        }
        while (!todo.isEmpty()) {
            int[] block = todo.poll();
            for (int[] face : FACES) {
                int x = block[0] + face[0];
                int y = block[1] + face[1];
                int z = block[2] + face[2];
                if (!nearBed(x, y, z, beds) || !seen.add(key(x, y, z)) || !BLOCKS.contains(blocks.at(x, y, z))) {
                    continue;
                }
                int[] next = {x, y, z};
                found.add(next);
                todo.add(next);
            }
        }
        return found;
    }

    /** From the bed's height up, within {@value #REACH} blocks of one of its halves on each axis. */
    private static boolean nearBed(int x, int y, int z, List<int[]> beds) {
        for (int[] bed : beds) {
            if (y >= bed[1] && y - bed[1] <= REACH && Math.abs(x - bed[0]) <= REACH && Math.abs(z - bed[2]) <= REACH) {
                return true;
            }
        }
        return false;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
