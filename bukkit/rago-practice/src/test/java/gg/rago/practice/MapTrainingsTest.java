package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The trainings made from the owner's maps: the ladder clutch, the extends and the block in. */
class MapTrainingsTest {

    @Test
    void theLadderPlatformsStandAsOnTheMap() {
        assertEquals(Arrays.asList(4, 8, 12, 16, 20, 24), Arrays.asList(4, 8, 12, 16, 20, 24).subList(0, LadderTraining.HEIGHTS.length));
        for (int i = 0; i < LadderTraining.HEIGHTS.length; i++) {
            // the player stands the height of the sign over the stone, the pad a step further each time
            assertEquals(LadderTraining.HEIGHTS[i], LadderTraining.platformY(i) + 1);
            assertEquals(LadderTraining.PAD_Z + i, LadderTraining.padZ(i));
            assertTrue(LadderTraining.padZ(i) - LadderTraining.PLATFORM_LENGTH >= 4, "a jump to reach the pad");
        }
        assertEquals(LadderTraining.SPACING, LadderTraining.platformX(1) - LadderTraining.platformX(0));
    }

    @Test
    void theExtendJumpsAreTheMapsJumps() {
        assertEquals(6, ExtendTraining.JUMPS.length);
        Set<Integer> xs = new HashSet<>();
        for (int i = 0; i < ExtendTraining.JUMPS.length; i++) {
            ExtendTraining.Jump jump = ExtendTraining.JUMPS[i];
            assertTrue(xs.add(ExtendTraining.jumpX(i)));
            assertEquals(ExtendTraining.PAD + jump.gap, ExtendTraining.farZ(jump), jump.label);
            assertTrue(jump.blocks == (jump.id.startsWith("double") ? 2 : 3), jump.label);
        }
        assertEquals(0, ExtendTraining.jumpX(0) + ExtendTraining.jumpX(ExtendTraining.JUMPS.length - 1), "around the centre");
        ExtendTraining.Jump five = ExtendTraining.JUMPS[0];
        assertEquals(5, five.gap);
        assertEquals(1, five.rise);
    }

    @Test
    void theChainLinesUpEveryJumpOnItsOwnPads() {
        int[] z = ExtendTraining.chainZ();
        int[] top = ExtendTraining.chainTop();
        assertEquals(ExtendTraining.CHAIN.length + 1, z.length);
        Set<Integer> kinds = new HashSet<>();
        for (int i = 0; i < ExtendTraining.CHAIN.length; i++) {
            ExtendTraining.Jump jump = ExtendTraining.JUMPS[ExtendTraining.CHAIN[i]];
            kinds.add(ExtendTraining.CHAIN[i]);
            assertEquals(ExtendTraining.PAD + jump.gap, z[i + 1] - z[i], "the gap of " + jump.label);
            assertEquals(jump.rise, top[i + 1] - top[i], jump.label);
        }
        assertEquals(ExtendTraining.JUMPS.length, kinds.size(), "every kind of jump");
        // in the zone of a game (espacement 200), clear of the six jumps
        assertTrue(z[0] > -99 && z[z.length - 1] + ExtendTraining.PAD < 99);
        assertTrue(ExtendTraining.chainX() - 3 > ExtendTraining.jumpX(ExtendTraining.JUMPS.length - 1) + 3);
        assertTrue(ExtendTraining.chainX() + 3 < 99);
        // the pads are found under the feet, the gaps are not pads
        assertEquals(0, ExtendTraining.chainPadAt(0, top[0], z[0] + 2));
        assertEquals(3, ExtendTraining.chainPadAt(-2, top[3], z[3] + 4));
        assertEquals(-1, ExtendTraining.chainPadAt(0, top[3], z[3] + ExtendTraining.PAD));
        assertEquals(-1, ExtendTraining.chainPadAt(3, top[3], z[3]));
        assertEquals(-1, ExtendTraining.chainPadAt(0, top[3] - 1, z[3] + 1), "under the top is not on the pad");
    }

    @Test
    void theBlocksOfAnExtendGoInItsGapOnly() {
        ExtendTraining.Jump jump = ExtendTraining.JUMPS[2];
        assertTrue(ExtendTraining.inGap(jump, 0, 0, ExtendTraining.PAD));
        assertTrue(ExtendTraining.inGap(jump, 0, -3, ExtendTraining.farZ(jump) - 1), "a ladder on the side of the far pad");
        assertFalse(ExtendTraining.inGap(jump, 0, 0, ExtendTraining.farZ(jump)), "not on the far pad");
        assertFalse(ExtendTraining.inGap(jump, 0, 0, ExtendTraining.PAD - 1), "not on the start pad");
        assertFalse(ExtendTraining.inGap(jump, 4, 0, ExtendTraining.PAD + 1));
    }

    @Test
    void theCopyOfAMapLosesItsCommandBlocksAndRedstone() {
        int stone = 1 << 4;
        int command = 137 << 4;
        int wire = 55 << 4;
        int sign = 68 << 4 | 3;
        int[] walls = {stone, stone, stone | 1, 0, 0, command};
        assertEquals(stone, MapCopy.cleaned(command, walls), "a command block in a wall becomes the wall");
        assertEquals(0, MapCopy.cleaned(command, new int[] {0, 0, wire, 0, command, 0}), "alone, it goes");
        assertEquals(0, MapCopy.cleaned(wire, walls));
        assertEquals(0, MapCopy.cleaned(sign, walls));
        assertEquals(stone | 2, MapCopy.cleaned(stone | 2, walls), "the build stays as it is");
        assertEquals(35 << 4 | 14, MapCopy.cleaned(35 << 4 | 14, walls));
        // the Block-In map: sand on a redstone wire would fall into its place and leave a hole
        int sand = 12 << 4;
        int sandstone = 24 << 4;
        assertEquals(sandstone, MapCopy.cleaned(wire, new int[] {sandstone, sand, command, sand, sandstone, 0}),
                "the wire under sand becomes the ground around it");
        assertEquals(stone, MapCopy.cleaned(wire, new int[] {sand, 0, 0, sand, wire, 0}), "stone when only sand is around");
        assertEquals(13 << 4, MapCopy.cleaned(13 << 4, walls), "the sand itself stays");
    }

    @Test
    void theCopyLeavesTheDefenseToTheRounds() {
        MapCopy copy = MapCopy.of(0, 1, 0, 26, 1, -1, 1, 0, 26, 9, 0, 0, 0, 24, 0, 5, 1, 0, 20, 0);
        MapCopy island = copy.without(BlockInTraining.defensePlaces(-1, 0));
        assertEquals(2, island.size(), "the ground under the bed and the glass far away stay");
        assertTrue(island.has(0, 0, 0, 24));
        assertTrue(island.has(5, 1, 0, 20));
        assertFalse(island.has(0, 1, 0, 26));
    }

    @Test
    void theDefenseIsTheMapsDefense() {
        // the bed and its three layers: 8 end stone, 18 glass, 32 wool and wood, as on the map
        assertEquals(2 + 8 + 18 + 32, BlockInTraining.defensePlaces(-1, 0).size());
        List<List<int[]>> layers = Shapes.defense(0, BlockInTraining.BED_Y, 0, -1, 0, 3);
        int wool = 0;
        int wood = 0;
        for (int[] place : layers.get(2)) {
            Material type = BlockInTraining.layerType(2, place, -1, 0);
            if (type == Material.WOOL) {
                wool++;
                assertTrue(place[2] < 0, "the wool on the north side");
            } else {
                assertEquals(Material.WOOD, type);
                wood++;
            }
        }
        assertEquals(12, wool, "12 wool on the map");
        assertEquals(20, wood, "20 planks on the map");
        assertEquals(Material.ENDER_STONE, BlockInTraining.layerType(0, layers.get(0).get(0), -1, 0));
        assertEquals(Material.STAINED_GLASS, BlockInTraining.layerType(1, layers.get(1).get(0), -1, 0));
    }

    @Test
    void eachLayerHasItsSplit() {
        assertEquals(0, BlockInTraining.splitOf(Material.WOOL));
        assertEquals(0, BlockInTraining.splitOf(Material.WOOD));
        assertEquals(1, BlockInTraining.splitOf(Material.STAINED_GLASS));
        assertEquals(2, BlockInTraining.splitOf(Material.ENDER_STONE));
        assertEquals(3, BlockInTraining.splitOf(Material.BED_BLOCK));
        assertEquals(-1, BlockInTraining.splitOf(Material.SANDSTONE));
    }

    @Test
    void theBundledBlockInSettingsAreTheMapsAndAreChecked() throws Exception {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        List<Integer> bed = config.getIntegerList("block-in.lit");
        List<Integer> head = config.getIntegerList("block-in.tete");
        List<Integer> zone = config.getIntegerList("block-in.zone");
        List<?> starts = config.getList("block-in.departs");
        assertNull(RagoPractice.checkBlockIn(bed, head, zone, starts));
        assertEquals("Ultimate Block-In Practice", config.getString("block-in.monde"));
        assertEquals(Arrays.asList(-72, 66, -26), bed);
        assertTrue(RagoPractice.checkBlockIn(bed, bed, zone, starts).startsWith("tete"));
        assertTrue(RagoPractice.checkBlockIn(bed, head, Arrays.asList(1, 2, 3), starts).startsWith("zone"));
        assertTrue(RagoPractice.checkBlockIn(bed, head, zone, Collections.emptyList()).startsWith("departs"));
        assertTrue(RagoPractice.checkBlockIn(bed, head, zone, Collections.singletonList(Arrays.asList(1, 2))).startsWith("departs"));
    }
}
