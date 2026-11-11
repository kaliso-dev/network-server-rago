package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The sumo on the owner's maps: the bundled list, its checks, and how a duel draws its zone. */
class SumoMapsTest {

    @Test
    void theBundledSumoMapsAreChecked() throws Exception {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        List<Map<?, ?>> maps = config.getMapList("sumo.cartes");
        assertEquals(5, maps.size());
        Set<String> names = new HashSet<>();
        for (Map<?, ?> map : maps) {
            assertNull(RagoPractice.checkZoneMap(map, config.getInt("espacement")), String.valueOf(map));
            assertTrue(names.add(String.valueOf(map.get("monde"))));
        }
        assertTrue(names.containsAll(Arrays.asList("Ponsen", "Fort Royale", "White Crystal", "Space Mine", "Sumo")));
    }

    @Test
    void theBundledBoxingArenasAreCheckedAndHaveTwoStarts() throws Exception {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        List<Map<?, ?>> maps = config.getMapList("boxing.cartes");
        assertEquals(8, maps.size());
        Set<String> names = new HashSet<>();
        for (Map<?, ?> map : maps) {
            assertNull(RagoPractice.checkZoneMap(map, config.getInt("espacement")), String.valueOf(map));
            assertTrue(names.add(String.valueOf(map.get("monde"))));
            assertEquals(2, ((List<?>) map.get("departs")).size(), String.valueOf(map.get("monde")));
        }
    }

    @Test
    void theSumoMapStartsOnItsPlatformNotOnItsDome() throws Exception {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        for (Map<?, ?> map : config.getMapList("sumo.cartes")) {
            if ("Sumo".equals(map.get("monde"))) {
                // the platform's lapis is at y 64; the glass of the dome's roof at y 95
                assertEquals(Arrays.asList(0, 64, 0), map.get("centre"));
            }
        }
    }

    @Test
    void theFireballMapHasTwoStartsAndPutsBackItsLostBed() throws Exception {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        List<Map<?, ?>> maps = config.getMapList("fireball.cartes");
        assertEquals(1, maps.size());
        Map<?, ?> map = maps.get(0);
        assertEquals("Fireball Fight", map.get("monde"));
        assertNull(RagoPractice.checkZoneMap(map, config.getInt("espacement")));
        assertEquals(2, ((List<?>) map.get("departs")).size());
        assertEquals(1, ((List<?>) map.get("lits")).size());
    }

    @Test
    void aBedPutBackFacesItsHead() {
        MapCopy copy = MapCopy.of(0, 0, 0, 1, 0, 1, 0, 0, 121, 0).withBeds(Arrays.asList(new int[] {1, 0, 0, 2, 0}));
        assertEquals(3, copy.size(), "the end stone under the foot gives way");
        assertEquals(26 << 4 | 3, copy.blockAt(1, 0, 0), "the foot, its head to the east");
        assertEquals(26 << 4 | 11, copy.blockAt(2, 0, 0), "the head");
        assertEquals(1 << 4, copy.blockAt(0, 0, 0), "the rest stays");
        assertEquals(0, MapCopy.bedDirection(0, 1));
        assertEquals(1, MapCopy.bedDirection(-1, 0));
        assertEquals(2, MapCopy.bedDirection(0, -1));
        Map<String, Object> map = map("A", Arrays.asList(0, 64, 0), Arrays.asList(-40, 60, -40, 40, 100, 40));
        map.put("lits", Arrays.asList(Arrays.asList(5, 65, 0, 6, 0)));
        assertNull(RagoPractice.checkZoneMap(map, 200));
        map.put("lits", Arrays.asList(Arrays.asList(5, 65, 0, 7, 0)));
        assertTrue(RagoPractice.checkZoneMap(map, 200).startsWith("lits"), "the head next to the foot");
        map.put("lits", Arrays.asList(Arrays.asList(40, 65, 0, 41, 0)));
        assertTrue(RagoPractice.checkZoneMap(map, 200).contains("in the zone"));
    }

    @Test
    void aWrongStartIsToldWhy() {
        Map<String, Object> map = map("A", Arrays.asList(0, 64, 0), Arrays.asList(-40, 60, -40, 40, 100, 40));
        map.put("departs", Arrays.asList(Arrays.asList(-30, 65, 0, -90), Arrays.asList(30, 65, 0, 90)));
        assertNull(RagoPractice.checkZoneMap(map, 200));
        map.put("departs", Arrays.asList(Arrays.asList(-30, 65, 0)));
        assertTrue(RagoPractice.checkZoneMap(map, 200).startsWith("departs"));
        map.put("departs", Arrays.asList(Arrays.asList(-50, 65, 0, -90)));
        assertTrue(RagoPractice.checkZoneMap(map, 200).contains("in the zone"));
    }

    @Test
    void aWrongMapIsToldWhy() {
        assertNull(RagoPractice.checkZoneMap(map("Ponsen", Arrays.asList(0, 64, 0), Arrays.asList(-47, 53, -47, 47, 116, 47)), 200));
        assertTrue(RagoPractice.checkZoneMap(map("", Arrays.asList(0, 64, 0), Arrays.asList(-4, 60, -4, 4, 70, 4)), 200)
                .startsWith("monde"));
        assertTrue(RagoPractice.checkZoneMap(map("A", Arrays.asList(0, 64), Arrays.asList(-4, 60, -4, 4, 70, 4)), 200)
                .startsWith("centre"));
        assertTrue(RagoPractice.checkZoneMap(map("A", Arrays.asList(0, 64, 0), Arrays.asList(-4, 60, -4, 4, 70)), 200)
                .startsWith("zone"));
        assertTrue(RagoPractice.checkZoneMap(map("A", Arrays.asList(0, 80, 0), Arrays.asList(-4, 60, -4, 4, 70, 4)), 200)
                .contains("holds the centre"));
        // wider than its zone of the arena world: it would reach the next one
        assertTrue(RagoPractice.checkZoneMap(map("A", Arrays.asList(0, 64, 0), Arrays.asList(-120, 60, -4, 4, 70, 4)), 200)
                .contains("99 blocks"));
    }

    @Test
    void aDuelDrawsAFreeReadyZone() {
        Random random = new Random(7);
        assertEquals(-1, MapZones.pick(new int[] {-1, -1, -1}, random));
        assertEquals(4, MapZones.pick(new int[] {-1, 4, -1}, random));
        Set<Integer> drawn = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            int zone = MapZones.pick(new int[] {0, -1, 7, 3}, random);
            assertTrue(zone == 0 || zone == 7 || zone == 3);
            drawn.add(zone);
        }
        assertEquals(3, drawn.size(), "every map comes up");
        assertFalse(drawn.contains(-1));
    }

    private static Map<String, Object> map(String world, List<Integer> centre, List<Integer> zone) {
        Map<String, Object> map = new HashMap<>();
        map.put("monde", world);
        map.put("centre", centre);
        map.put("zone", zone);
        return map;
    }
}
