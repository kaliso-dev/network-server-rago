package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

/**
 * The defense around the bed a rush breaks (a mode of one team), as a player would build it in the
 * first minute of a game: layer after layer around the bed and over it, never under it. Layer n is
 * every block n steps away from the bed (up, or sideways), at the bed's height or above.
 */
final class RushDefense {

    private RushDefense() {
    }

    /** The blocks of each layer, from the bed outwards: {x, y, z}. The bed's blocks share their height. */
    static List<List<int[]>> layers(List<int[]> bed, int count) {
        List<List<int[]>> layers = new ArrayList<>();
        if (bed.isEmpty()) {
            return layers;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] block : bed) {
            minX = Math.min(minX, block[0]);
            maxX = Math.max(maxX, block[0]);
            minZ = Math.min(minZ, block[2]);
            maxZ = Math.max(maxZ, block[2]);
        }
        int y0 = bed.get(0)[1];
        for (int n = 1; n <= count; n++) {
            List<int[]> layer = new ArrayList<>();
            for (int y = y0; y <= y0 + n; y++) {
                for (int x = minX - n; x <= maxX + n; x++) {
                    for (int z = minZ - n; z <= maxZ + n; z++) {
                        if (steps(bed, x, y, z) == n) {
                            layer.add(new int[] {x, y, z});
                        }
                    }
                }
            }
            layers.add(layer);
        }
        return layers;
    }

    /** The fewest steps from the bed to this block, one block at a time along an axis. */
    private static int steps(List<int[]> bed, int x, int y, int z) {
        int fewest = Integer.MAX_VALUE;
        for (int[] block : bed) {
            fewest = Math.min(fewest, Math.abs(x - block[0]) + Math.abs(y - block[1]) + Math.abs(z - block[2]));
        }
        return fewest;
    }

    /** True for the blocks that take the colour of the island: wool, stained clay and glass. */
    static boolean coloured(Material material) {
        return material == Material.WOOL || material == Material.STAINED_CLAY || material == Material.STAINED_GLASS;
    }
}
