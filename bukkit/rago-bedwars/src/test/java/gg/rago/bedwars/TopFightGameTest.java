package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** Top Fight: where each player starts on the defense of the bed. */
class TopFightGameTest {

    private static Set<String> defense(int x, int y, int z, int hx, int hz) {
        Set<String> blocks = new HashSet<>();
        for (List<int[]> layer : Defense.layers(x, y, z, hx, hz, TopFightGame.DEFENSE.length)) {
            for (int[] place : layer) {
                blocks.add(place[0] + "," + place[1] + "," + place[2]);
            }
        }
        // the bed itself
        blocks.add(x + "," + y + "," + z);
        blocks.add((x + hx) + "," + y + "," + (z + hz));
        return blocks;
    }

    @Test
    void everyoneStartsOnTheDefenseFaceToFace() {
        int[][] beds = {{10, 70, 20, 1, 0}, {-5, 64, 3, 0, -1}, {0, 80, 0, -1, 0}, {7, 66, -9, 0, 1}};
        for (int[] bed : beds) {
            Set<String> blocks = defense(bed[0], bed[1], bed[2], bed[3], bed[4]);
            for (int team = 0; team < 2; team++) {
                for (int index = 0; index < 4; index++) {
                    double[] start = TopFightGame.start(bed[0], bed[1], bed[2], bed[3], bed[4],
                            TopFightGame.DEFENSE.length, team == 0, index);
                    int x = (int) Math.floor(start[0]);
                    int y = (int) start[1];
                    int z = (int) Math.floor(start[2]);
                    String where = "bed " + bed[0] + "," + bed[2] + ", team " + team + ", player " + index;
                    assertTrue(blocks.contains(x + "," + (y - 1) + "," + z), where + ": on a block of the defense");
                    assertFalse(blocks.contains(x + "," + y + "," + z), where + ": the feet in the air");
                    assertFalse(blocks.contains(x + "," + (y + 1) + "," + z), where + ": the head in the air");
                }
            }
            double[] first = TopFightGame.start(bed[0], bed[1], bed[2], bed[3], bed[4], 4, true, 0);
            double[] other = TopFightGame.start(bed[0], bed[1], bed[2], bed[3], bed[4], 4, false, 0);
            assertEquals(bed[1] + 5, first[1], 1e-9, "the first of each team on the very top, wool over the bed");
            assertEquals(bed[1] + 5, other[1], 1e-9);
            assertEquals(bed[3], other[0] - first[0], 1e-9, "one block apart, along the bed");
            assertEquals(bed[4], other[2] - first[2], 1e-9);
            assertEquals(bed[1] + 4, TopFightGame.start(bed[0], bed[1], bed[2], bed[3], bed[4], 4, true, 1)[1], 1e-9,
                    "the second one step down");
            // each looks at the other
            float yaw = (float) first[3];
            assertEquals(bed[3], -Math.sin(Math.toRadians(yaw)), 1e-6);
            assertEquals(bed[4], Math.cos(Math.toRadians(yaw)), 1e-6);
            assertEquals(180, Math.abs(((other[3] - first[3]) % 360 + 540) % 360 - 180), 1e-6);
        }
    }

    @Test
    void theDefenseIsTheRankedOne() {
        assertArrayEquals(new Material[] {Material.ENDER_STONE, Material.STAINED_GLASS, Material.WOOD, Material.WOOL},
                TopFightGame.DEFENSE, "end stone on the bed, then glass, wood and wool on top");
        assertEquals(3, TopFightGame.POINTS_TO_WIN);
    }
}
