package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MapStoreTest {

    private static BedwarsMap demo() {
        BedwarsMap map = new BedwarsMap(DemoMap.WORLD);
        DemoMap.layout(map);
        return map;
    }

    /** A map with this many complete teams, one island every 40 blocks. */
    private static BedwarsMap withTeams(String world, int count) {
        BedwarsMap map = new BedwarsMap(world);
        for (int i = 0; i < count; i++) {
            BedwarsMap.Team team = map.team(TeamColor.values()[i]);
            double x = i * 40;
            team.spawn = new MapPoint(x + 0.5, 65, 0.5, 0f, 0f);
            team.bed = new MapPoint(x, 65, 6, 0f, 0f);
            team.generator = new MapPoint(x + 0.5, 65, -5.5, 0f, 0f);
            team.shop = new MapPoint(x + 4.5, 65, 0.5, 90f, 0f);
        }
        map.setEnabled(true);
        return map;
    }

    @Test
    void theDemoMapIsOnlyPlayedWhenNoOtherMapCanBe() {
        BedwarsMap demo = demo();
        BedwarsMap archway = withTeams("Archway", 4);
        List<BedwarsMap> both = Arrays.asList(demo, archway);
        // the demo map has exactly two teams, but a real map of four is played instead
        assertEquals(Collections.singletonList(archway), MapStore.choose(both, 2));
        assertEquals(Collections.singletonList(archway), MapStore.choose(both, 4));
        archway.setEnabled(false);
        assertEquals(Collections.singletonList(demo), MapStore.choose(both, 2));
        assertTrue(MapStore.choose(both, 4).isEmpty());
        assertEquals(Collections.singletonList(demo), MapStore.choose(Collections.singletonList(demo), 2));
    }

    @Test
    void theFirstCopyIsOfARealMap() {
        BedwarsMap demo = demo();
        BedwarsMap archway = withTeams("Archway", 4);
        assertEquals(Collections.singletonList(archway), MapStore.inAdvance(Arrays.asList(demo, archway)));
        assertEquals(Collections.singletonList(demo), MapStore.inAdvance(Collections.singletonList(demo)));
        assertTrue(MapStore.inAdvance(Collections.<BedwarsMap>emptyList()).isEmpty());
    }

    @Test
    void mapsWithExactlyTheTeamsComeFirst() {
        BedwarsMap two = withTeams("Duel", 2);
        BedwarsMap four = withTeams("Archway", 4);
        BedwarsMap eight = withTeams("Lighthouse", 8);
        List<BedwarsMap> all = Arrays.asList(demo(), two, four, eight);
        assertEquals(Collections.singletonList(two), MapStore.choose(all, 2));
        assertEquals(Collections.singletonList(four), MapStore.choose(all, 4));
        assertEquals(Collections.singletonList(eight), MapStore.choose(all, 8));
        assertEquals(Arrays.asList(four, eight), MapStore.choose(Arrays.asList(four, eight), 2));
    }

    @Test
    void eachModePlaysOnItsOwnMaps() {
        BedwarsMap demo = demo();
        BedwarsMap archway = withTeams("Archway", 4);
        BedwarsMap gelato = withTeams("Gelato", 8);
        BedwarsMap nebuc = withTeams("Nebuc", 8);
        List<BedwarsMap> all = Arrays.asList(demo, archway, gelato, nebuc);
        assertEquals(Arrays.asList(nebuc, gelato), MapStore.named(all, 2, Arrays.asList("nebuc", "Gelato", "Missing")),
                "the maps named, in their order, whatever the case");
        assertEquals(Arrays.asList(archway, gelato, nebuc), MapStore.named(all, 2, Collections.<String>emptyList()),
                "every map: none has exactly two teams, so those with more, and never the demo map");
        assertEquals(Collections.singletonList(demo), MapStore.named(all, 2, Collections.singletonList("Missing")),
                "none of its maps is on the server: the demo map");
        assertTrue(MapStore.named(Arrays.asList(archway, gelato), 2, Collections.singletonList("Missing")).isEmpty());
        gelato.setEnabled(false);
        assertEquals(Collections.singletonList(nebuc), MapStore.named(all, 2, Arrays.asList("Gelato", "Nebuc")));
    }

    @Test
    void readsTheFilesOfTheMapTool() throws Exception {
        // bedwars.yml as tools/bedwars-maps writes it, for a map whose points it found
        String text = String.join("\n",
                "# The Bed Wars points of this map, found by the map tool from its beds and islands.",
                "actif: true",
                "equipes:",
                "  red:",
                "    spawn:",
                "      x: -13.5",
                "      y: 65.0",
                "      z: -72.5",
                "      yaw: 0.0",
                "    lit:",
                "      x: -15",
                "      y: 66",
                "      z: -67",
                "    generateur:",
                "      x: -13.5",
                "      y: 64.5",
                "      z: -79.5",
                "    shop:",
                "      x: -9.5",
                "      y: 66.0",
                "      z: -70.5",
                "      yaw: 135.0",
                "    ameliorations:",
                "      x: -17.5",
                "      y: 65.5",
                "      z: -74.5",
                "      yaw: 270.0",
                "  blue:",
                "    spawn:",
                "      x: 72.5",
                "      y: 65.0",
                "      z: -13.5",
                "      yaw: 90.0",
                "    lit:",
                "      x: 67",
                "      y: 66",
                "      z: -15",
                "    generateur:",
                "      x: 79.5",
                "      y: 64.5",
                "      z: -13.5",
                "    shop:",
                "      x: 70.5",
                "      y: 66.0",
                "      z: -9.5",
                "diamants:",
                "- x: -48.5",
                "  y: 66.0",
                "  z: -33.5",
                "- x: 49.5",
                "  y: 66.0",
                "  z: 34.5",
                "emeraudes:",
                "- x: 0.5",
                "  y: 65.0",
                "  z: 0.5",
                "spectateurs:",
                "  x: 0.5",
                "  y: 90.0",
                "  z: 0.5",
                "  yaw: 0.0",
                "  pitch: 90.0",
                "");
        BedwarsMap map = BedwarsMap.fromYaml("Archway", text);
        assertTrue(map.playable(), map.missing().toString());
        assertEquals(Arrays.asList(TeamColor.RED, TeamColor.BLUE), map.completeTeams());
        assertEquals(new MapPoint(-13.5, 64.5, -79.5, 0f, 0f), map.team(TeamColor.RED).generator);
        assertEquals(135f, map.team(TeamColor.RED).shop.yaw());
        assertEquals(2, map.diamonds().size());
        assertEquals(1, map.emeralds().size());
        assertNotNull(map.spectators());
    }
}
