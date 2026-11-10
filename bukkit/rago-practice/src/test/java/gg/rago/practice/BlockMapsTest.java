package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The block placement on the owner's maps, and the block extend on BlockClutchExtend. */
class BlockMapsTest {

    private static YamlConfiguration bundled() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
    }

    @Test
    void theBundledBlockPlacementMapsAreCheckedAndHaveTwoStarts() {
        YamlConfiguration config = bundled();
        List<Map<?, ?>> maps = config.getMapList("block-fight.cartes");
        assertEquals(3, maps.size());
        Set<String> names = new HashSet<>();
        for (Map<?, ?> map : maps) {
            assertNull(RagoPractice.checkZoneMap(map, config.getInt("espacement")), String.valueOf(map));
            assertTrue(names.add(String.valueOf(map.get("monde"))));
            assertEquals(2, ((List<?>) map.get("departs")).size(), String.valueOf(map.get("monde")));
        }
        // as on Minemen: the hits are the points, first to 100
        assertEquals(100, config.getInt("points-pour-gagner.block-fight"));
    }

    @Test
    void theBundledBlockExtendIsChecked() {
        YamlConfiguration config = bundled();
        ConfigurationSection section = config.getConfigurationSection("block-extend");
        assertNotNull(section);
        assertNull(RagoPractice.checkBlockExtend(section, config.getInt("espacement")));
        assertEquals(870604.589, section.getDoubleList("niveaux.or.depart").get(0), 1e-9, "the exact start of the screenshot");
    }

    @Test
    void aWrongBlockExtendIsToldWhy() throws Exception {
        YamlConfiguration config = bundled();
        int spacing = config.getInt("espacement");
        ConfigurationSection section = config.getConfigurationSection("block-extend");
        section.set("niveaux.diamant.coup", "dessus");
        assertTrue(RagoPractice.checkBlockExtend(section, spacing).startsWith("niveaux.diamant.coup"));
        section.set("niveaux.diamant.coup", "droite");
        section.set("niveaux.or.depart", java.util.Arrays.asList(870604.589, 101, 870630.939));
        assertTrue(RagoPractice.checkBlockExtend(section, spacing).startsWith("niveaux.or.depart"));
        section.set("niveaux.emeraude", null);
        section.set("niveaux.or.depart", java.util.Arrays.asList(870604.589, 101, 870630.939, -179.9, 1.3));
        assertTrue(RagoPractice.checkBlockExtend(section, spacing).startsWith("niveaux.emeraude"));
    }

    @Test
    void aHitThrowsAwayFromWhereItComes() {
        // looking south (+z): the right hand is west (-x)
        assertArrayEquals(new double[] {0, -1}, BlockExtendTraining.thrown(0f, BlockExtendTraining.From.FRONT), 1e-9);
        assertArrayEquals(new double[] {0, 1}, BlockExtendTraining.thrown(0f, BlockExtendTraining.From.BACK), 1e-9);
        assertArrayEquals(new double[] {1, 0}, BlockExtendTraining.thrown(0f, BlockExtendTraining.From.RIGHT), 1e-9);
        assertArrayEquals(new double[] {-1, 0}, BlockExtendTraining.thrown(0f, BlockExtendTraining.From.LEFT), 1e-9);
        // looking north (180), as on the levels of the map: a hit from the front throws south
        assertArrayEquals(new double[] {0, 1}, BlockExtendTraining.thrown(180f, BlockExtendTraining.From.FRONT), 1e-9);
    }

    @Test
    void theWordsOfTheHitAreFrenchOrEnglish() {
        assertEquals(BlockExtendTraining.From.RIGHT, BlockExtendTraining.From.of("droite"));
        assertEquals(BlockExtendTraining.From.FRONT, BlockExtendTraining.From.of(" Devant "));
        assertEquals(BlockExtendTraining.From.BACK, BlockExtendTraining.From.of("derrière"));
        assertEquals(BlockExtendTraining.From.LEFT, BlockExtendTraining.From.of("left"));
        assertNull(BlockExtendTraining.From.of("dessus"));
    }

    @Test
    void aHitTowardsAPointHasLengthOne() {
        double[] start = {0, 10, 0, 180, 0};
        assertArrayEquals(new double[] {0.6, 0.8}, BlockExtendTraining.towards(start, 3, 4), 1e-9);
        // right under the start: straight back
        assertArrayEquals(BlockExtendTraining.thrown(180f, BlockExtendTraining.From.FRONT),
                BlockExtendTraining.towards(start, 0, 0), 1e-9);
    }

    @Test
    void theLevelsLandBelowAndAwayFromTheirStart() {
        for (BlockExtendTraining.Level level : BlockExtendTraining.plainLevels()) {
            assertTrue(level.target[1] < level.start[1], level.id + ": the target is below the start");
            assertEquals(1.0, Math.hypot(level.way[0], level.way[1]), 1e-9, level.id);
            assertTrue(level.horizontal > 0 && level.vertical > 0, level.id);
        }
        BlockExtendTraining.Level gold = BlockExtendTraining.plainLevels()[0];
        BlockExtendTraining.Level diamond = BlockExtendTraining.plainLevels()[1];
        BlockExtendTraining.Level emerald = BlockExtendTraining.plainLevels()[2];
        assertTrue(diamond.horizontal >= 0.8, "the diamond hit is a sprint hit, not a weak one");
        assertTrue(emerald.horizontal > gold.horizontal && emerald.vertical < gold.vertical - 0.25,
                "the emerald hit throws further and about a block lower than the gold one");
    }

    @Test
    void theHitAlwaysComesAlongTheLineOfTheStartAndThePlayerIsFrozenUntilIt() {
        BlockExtendTraining.Level gold = BlockExtendTraining.plainLevels()[0];
        assertEquals(BlockExtendTraining.From.FRONT, gold.from);
        // looking north at the start: always thrown south (the player cannot turn before the hit)
        assertArrayEquals(new double[] {0, 1}, gold.way, 1e-2);
        // the bundled levels: the gold hit is from the front, higher and stronger than a big hit
        ConfigurationSection or = bundled().getConfigurationSection("block-extend.niveaux.or");
        assertEquals("devant", or.getString("coup"));
        assertTrue(!or.contains("vers"));
        assertTrue(or.getDoubleList("force").get(0) > 0.68 && or.getDoubleList("force").get(1) > 0.40);
        assertTrue(bundled().getDoubleList("block-extend.niveaux.diamant.force").get(0) >= 0.8);
        // the emerald start: on the emerald block at the end of the bridge, a block nearer the island
        assertEquals(870612.227, bundled().getDoubleList("block-extend.niveaux.emeraude.depart").get(2), 1e-9);
        // frozen: a step or a turn is not still; the same place and look, a full turn apart, is
        org.bukkit.Location start = new org.bukkit.Location(null, 10.5, 64, 10.5, 179.4f, 1.4f);
        assertTrue(BlockExtendTraining.still(new org.bukkit.Location(null, 10.5, 64, 10.5, -180.6f, 1.4f), start));
        assertTrue(!BlockExtendTraining.still(new org.bukkit.Location(null, 10.5, 64, 10.6, 179.4f, 1.4f), start));
        assertTrue(!BlockExtendTraining.still(new org.bukkit.Location(null, 10.5, 64, 10.5, 90f, 1.4f), start));
        assertTrue(!BlockExtendTraining.still(new org.bukkit.Location(null, 10.5, 64, 10.5, 179.4f, 30f), start));
    }

    @Test
    void aBedOfAFireballMapIsTheSideOfTheNearerStart() {
        double[] west = {-80.5, 1, 0.5, -90};
        double[] east = {80.5, 1, 0.5, 90};
        assertEquals(0, FireballFight.nearer(west, east, -70, 3));
        assertEquals(1, FireballFight.nearer(west, east, 75, -3));
        assertEquals(0, FireballFight.nearer(west, east, 0, 0), "in the very middle: the first side");
        assertTrue(bundled().isList("fireball.cartes"));
    }

    @Test
    void theCopyFindsItsBeds() {
        MapCopy copy = MapCopy.of(1, 0, 0, 26, 1, 2, 0, 0, 26, 9, 5, 0, 0, 35, 0);
        assertEquals(2, copy.find(26).size());
        assertArrayEquals(new int[] {2, 0, 0}, copy.find(26).get(1));
        assertTrue(copy.find(41).isEmpty());
    }

    @Test
    void theMarkersOfTheMapBecomeTheWoolNextToThem() {
        MapCopy copy = MapCopy.of(
                0, 0, 0, 41, 0, // gold, next to blue wool
                1, 0, 0, 35, 11,
                5, 0, 5, 57, 0, // diamond, alone
                9, 0, 9, 133, 0, // emerald, on red wool
                9, -1, 9, 35, 14,
                3, 0, 3, 1, 0); // stone stays
        MapCopy played = copy.markersAsWool(BlockExtendTraining.MARKERS);
        assertEquals(35 << 4 | 11, played.blockAt(0, 0, 0));
        assertEquals(35 << 4, played.blockAt(5, 0, 5));
        assertEquals(35 << 4 | 14, played.blockAt(9, 0, 9));
        assertEquals(1 << 4, played.blockAt(3, 0, 3));
        assertEquals(41 << 4, copy.blockAt(0, 0, 0), "the copy itself is left as it was");
        assertEquals(0, played.blockAt(7, 7, 7));
    }

    @Test
    void theBundledArenaIsTheGreenOneInItsWalls() {
        YamlConfiguration config = bundled();
        List<Map<?, ?>> maps = config.getMapList("block-fight-arena.cartes");
        assertEquals(1, maps.size());
        assertNull(RagoPractice.checkZoneMap(maps.get(0), config.getInt("espacement")));
        assertEquals("Arena", maps.get(0).get("monde"));
    }

    @Test
    void theSlimeThrowsWhoeverStandsOnIt() {
        int[] pad = {36, -2, -37};
        // on its top, in the middle, at an edge, a bit above it
        assertTrue(BlockArena.onPad(36.5, -1, -36.5, pad));
        assertTrue(BlockArena.onPad(35.8, -1, -36.5, pad));
        assertTrue(BlockArena.onPad(36.5, -0.6, -36.5, pad));
        // beside it on the floor of the pit, off it, high above it, on the grass
        assertTrue(!BlockArena.onPad(35.6, -2, -36.5, pad));
        assertTrue(!BlockArena.onPad(38.0, -1, -36.5, pad));
        assertTrue(!BlockArena.onPad(36.5, 0.2, -36.5, pad));
        assertTrue(!BlockArena.onPad(36.5, 1, -36.5, pad));
    }

    @Test
    void theSlimeThrowsUpAndTheWayThePlayerLooks() {
        // yaw 0: south, +z; 90: west, -x
        double[] south = BlockArena.launch(0f);
        assertEquals(0, south[0], 1e-9);
        assertEquals(BlockArena.LAUNCH_FORWARD, south[2], 1e-9);
        double[] west = BlockArena.launch(90f);
        assertEquals(-BlockArena.LAUNCH_FORWARD, west[0], 1e-9);
        assertEquals(0, west[2], 1e-9);
        assertEquals(BlockArena.LAUNCH_UP, west[1], 1e-9);
        // the client's own physics: three blocks up, about two forward when back on a floor two above
        double y = 0;
        double x = 0;
        double vy = BlockArena.LAUNCH_UP;
        double vx = BlockArena.LAUNCH_FORWARD;
        double top = 0;
        do {
            y += vy;
            x += vx;
            vy = (vy - 0.08) * 0.98;
            vx *= 0.91;
            top = Math.max(top, y);
        } while (vy > 0 || y > 2);
        assertTrue(top > 3 && top < 3.3, "up " + top);
        assertTrue(x > 1.8 && x < 2.4, "forward " + x);
    }

    @Test
    void theGoldMarkerOfTheArenaGoes() {
        MapCopy copy = MapCopy.of(0, 0, 0, 2, 0, 0, 1, 0, 41, 0, 36, -2, -37, 165, 0);
        List<MapZones.ZoneMap> maps = RagoPractice.withoutBlocks(
                java.util.Collections.singletonList(new MapZones.ZoneMap("Arena", copy)), RagoPractice.GOLD_ID);
        MapCopy kept = maps.get(0).copy;
        assertEquals(2, kept.size());
        assertEquals(0, kept.blockAt(0, 1, 0));
        assertEquals(1, kept.find(BlockArena.SLIME_ID).size());
    }
}
