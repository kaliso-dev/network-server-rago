package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The RBW level of the block in (the owner's ask of 27/11): his RBW map, its red bed and its defense. */
class BlockInRbwTest {

    @Test
    void theRbwLevelIsTheLastLevel() {
        assertEquals("rbw", BlockInTraining.DEFENSES[BlockInTraining.DEFENSES.length - 1].id);
        assertEquals(4, BlockInTraining.DEFENSES.length, "wood, wool, 2 layers, RBW");
        assertTrue(BlockInTraining.DEFENSES[3].timed, "timed as the others");
    }

    @Test
    void theBundledRbwMapIsTheRedBed() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertEquals("Block In Practice RBW", config.getString("block-in.rbw.monde"));
        assertEquals(Arrays.asList(-15, 66, -67), config.getIntegerList("block-in.rbw.lit"), "the foot of the red bed");
        assertEquals(Arrays.asList(-15, 66, -66), config.getIntegerList("block-in.rbw.tete"), "its head, to the south");
        List<Integer> zone = config.getIntegerList("block-in.rbw.zone");
        assertEquals(6, zone.size());
        assertTrue(zone.get(0) <= -15 && -15 <= zone.get(3) && zone.get(2) <= -67 && -66 <= zone.get(5), "the bed in the zone");
    }

    @Test
    void theIslandTurnsToAnyBed() {
        // the island's own bed: its head to the west, nothing turns
        assertArrayEquals(new int[] {4, -4}, BlockInTraining.turned(4, -4, -1, 0));
        // the RBW map's bed, its head to the south: the island turned a quarter
        assertArrayEquals(new int[] {4, -4}, BlockInTraining.turned(4, 4, 0, 1));
        assertArrayEquals(new int[] {-4, -4}, BlockInTraining.turned(4, -4, 0, 1));
        assertEquals(135f, BlockInTraining.turnedYaw(135, -1, 0), 1e-3f);
        assertEquals(45f, BlockInTraining.turnedYaw(135, 0, 1), 1e-3f);
        assertEquals(-45f, BlockInTraining.turnedYaw(45, 0, 1), 1e-3f);
    }

    @Test
    void theRbwStartsLookAtTheBedFromTheRing() {
        double[][] starts = BlockInTraining.rbwStarts(0, 1);
        assertEquals(2, starts.length);
        for (double[] start : starts) {
            double x = start[0] - BlockInTraining.RBW_X - 0.5;
            double z = start[2] - 0.5;
            assertEquals(4, Math.max(Math.abs(x), Math.abs(z)), 1e-9, "on the ring, four blocks from the bed");
            assertEquals(11, start[1], 1e-9, "ten blocks over the bed");
            double yaw = Math.toRadians(start[3]);
            // the look goes towards the bed
            assertTrue(-Math.sin(yaw) * -x + Math.cos(yaw) * -z > 0, "looking at the bed: " + Arrays.toString(start));
        }
    }

    @Test
    void theDefenseIsReadAroundTheBed() {
        int bed = 26;
        MapCopy copy = MapCopy.of(
                0, 1, 0, bed, 0, 0, 1, 1, bed, 8,           // the bed, its head to the south
                1, 1, 0, 121, 0, -1, 1, 1, 121, 0,        // end stone beside it
                2, 1, 0, 95, 14, 0, 2, 0, 95, 14,          // red glass, over it too
                0, 0, 0, 24, 0,                            // the floor under it: not the defense
                9, 1, 9, 121, 0,                           // end stone far away: not the defense
                0, 5, 0, 35, 0);                           // wool high over it: not the defense
        List<int[]> defense = BlockInTraining.defenseOf(copy, 0, 1);
        assertEquals(4, defense.size(), "the end stone and the glass around the bed, nothing else");
        for (int[] block : defense) {
            assertTrue(block[3] == 121 || block[3] == 95, Arrays.toString(block));
        }
        MapCopy moved = MapCopy.of(1, 2, 3, 1, 0).shifted(BlockInTraining.RBW_X, 0, 0);
        assertArrayEquals(new int[] {1 + BlockInTraining.RBW_X, 2, 3, 1, 0}, moved.blocks());
    }
}
