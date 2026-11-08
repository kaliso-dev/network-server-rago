package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class BedwarsMapTest {

    private static BedwarsMap demo() {
        BedwarsMap map = new BedwarsMap(DemoMap.WORLD);
        DemoMap.layout(map);
        return map;
    }

    @Test
    void anEmptyMapSaysWhatItLacks() {
        BedwarsMap map = new BedwarsMap("archway");
        assertFalse(map.playable());
        map.team(TeamColor.RED).spawn = new MapPoint(1.5, 65, 1.5, 0f, 0f);
        assertTrue(map.missing().contains("red: bed, generator, shop"), map.missing().toString());
        assertTrue(map.missing().get(map.missing().size() - 1).startsWith("at least 2 complete teams"));
    }

    /** A complete team whose spawn is here. */
    private static void island(BedwarsMap map, TeamColor color, double x, double z) {
        BedwarsMap.Team team = map.team(color);
        team.spawn = new MapPoint(x, 65, z, 0f, 0f);
        team.bed = new MapPoint(x / 2, 66, z / 2, 0f, 0f);
        team.generator = new MapPoint(x, 64.5, z, 0f, 0f);
        team.shop = new MapPoint(x + 4, 65, z, 0f, 0f);
    }

    @Test
    void aDuelIsPlayedOnOppositeIslands() {
        // Archway: red in the north, blue in the east, green in the south, yellow in the west
        BedwarsMap map = new BedwarsMap("Archway");
        island(map, TeamColor.RED, -13.5, -72.5);
        island(map, TeamColor.BLUE, 73.5, -13.5);
        island(map, TeamColor.GREEN, 14.5, 73.5);
        island(map, TeamColor.YELLOW, -72.5, 14.5);
        assertEquals(Arrays.asList(TeamColor.RED, TeamColor.GREEN), map.playingTeams(2));
        assertEquals(Arrays.asList(TeamColor.RED, TeamColor.BLUE, TeamColor.GREEN), map.playingTeams(3));
        assertEquals(map.completeTeams(), map.playingTeams(4));
        assertEquals(map.completeTeams(), map.playingTeams(8));
        // eight islands on a circle, in the order of the colours: a duel takes the opposite one
        BedwarsMap eight = new BedwarsMap("Lighthouse");
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            island(eight, TeamColor.values()[i], 80 * Math.cos(angle), 80 * Math.sin(angle));
        }
        assertEquals(Arrays.asList(TeamColor.values()[0], TeamColor.values()[4]), eight.playingTeams(2));
        assertEquals(Arrays.asList(TeamColor.values()[0], TeamColor.values()[2], TeamColor.values()[4],
                TeamColor.values()[6]), eight.playingTeams(4));
        assertEquals(Arrays.asList(TeamColor.RED, TeamColor.BLUE), demo().playingTeams(2));
    }

    @Test
    void aRushTakesTheNearestIsland() {
        // Gelato: the islands go by pairs, red and blue side by side in the north
        BedwarsMap gelato = new BedwarsMap("Gelato");
        double[][] spawns = {{-18.5, -78.5}, {19.5, -78.5}, {79.5, -18.5}, {79.5, 19.5}, {19.5, 79.5}, {-18.5, 79.5},
            {-78.5, 19.5}, {-78.5, -18.5}};
        for (int i = 0; i < spawns.length; i++) {
            island(gelato, TeamColor.values()[i], spawns[i][0], spawns[i][1]);
        }
        assertEquals(Arrays.asList(TeamColor.RED, TeamColor.BLUE), gelato.rushTeams());
        BedwarsMap map = new BedwarsMap("Square");
        island(map, TeamColor.RED, 0, 0);
        island(map, TeamColor.BLUE, 100, 0);
        island(map, TeamColor.GREEN, 0, 60);
        assertEquals(Arrays.asList(TeamColor.RED, TeamColor.GREEN), map.rushTeams());
        assertEquals(Arrays.asList(TeamColor.RED, TeamColor.BLUE), demo().rushTeams());
        BedwarsMap alone = new BedwarsMap("Alone");
        island(alone, TeamColor.RED, 0, 0);
        assertEquals(Collections.singletonList(TeamColor.RED), alone.rushTeams());
    }

    @Test
    void aCompleteMapIsPlayedOnceEnabled() {
        BedwarsMap map = demo();
        assertTrue(map.missing().isEmpty(), map.missing().toString());
        assertEquals(Arrays.asList(TeamColor.RED, TeamColor.BLUE), map.completeTeams());
        assertTrue(map.playable());
        map.setEnabled(false);
        assertFalse(map.playable());
    }

    @Test
    void theUpgradesVillagerIsOptional() {
        BedwarsMap map = demo();
        map.team(TeamColor.BLUE).upgrades = null;
        assertTrue(map.missing().isEmpty());
        map.team(TeamColor.BLUE).shop = null;
        assertEquals(Collections.singletonList(TeamColor.RED), map.completeTeams());
        assertFalse(map.playable());
    }

    @Test
    void readsBackWhatItWrites() throws Exception {
        BedwarsMap map = demo();
        BedwarsMap read = BedwarsMap.fromYaml(map.world(), map.toYaml());
        assertTrue(read.enabled());
        assertEquals(map.completeTeams(), read.completeTeams());
        for (TeamColor color : map.teams().keySet()) {
            BedwarsMap.Team written = map.teams().get(color);
            BedwarsMap.Team back = read.teams().get(color);
            assertEquals(written.spawn, back.spawn);
            assertEquals(written.bed, back.bed);
            assertEquals(written.generator, back.generator);
            assertEquals(written.shop, back.shop);
            assertEquals(written.upgrades, back.upgrades);
        }
        assertEquals(map.diamonds(), read.diamonds());
        assertEquals(map.emeralds(), read.emeralds());
        assertEquals(map.spectators(), read.spectators());
        assertEquals(map.buildLimit(), read.buildLimit());
        assertEquals(map.voidY(), read.voidY());
    }

    @Test
    void theHeightsAreAutomaticUntilSet() throws Exception {
        BedwarsMap map = demo();
        map.setBuildLimit(0);
        map.setVoidY(Integer.MIN_VALUE);
        assertEquals(DemoMap.Y + BedwarsMap.AUTO_BUILD_ABOVE, map.buildLimit());
        assertEquals(DemoMap.Y - BedwarsMap.AUTO_VOID_BELOW, map.voidY());
        BedwarsMap read = BedwarsMap.fromYaml(map.world(), map.toYaml());
        assertFalse(read.hasBuildLimit());
        assertFalse(read.hasVoidY());
        assertFalse(read.toYaml().contains("hauteur-max"));
    }

    @Test
    void withoutSpectatorPointTheDeadLookFromAboveTheMiddle() {
        BedwarsMap map = demo();
        map.setSpectators(null);
        MapPoint above = map.spectatorPoint();
        assertEquals(0.5, above.x(), 1e-9);
        assertEquals(DemoMap.Y + 15, above.y(), 1e-9);
        assertEquals(90f, above.pitch());
    }

    @Test
    void aCopyIsIndependent() {
        BedwarsMap map = demo();
        BedwarsMap copy = map.copy();
        map.diamonds().clear();
        map.removeTeam(TeamColor.BLUE);
        assertEquals(2, copy.diamonds().size());
        assertEquals(2, copy.completeTeams().size());
    }

    @Test
    void wrongValuesNameTheirPlace() {
        IllegalArgumentException team = assertThrows(IllegalArgumentException.class,
                () -> BedwarsMap.fromYaml("m", "equipes:\n  violet:\n    spawn: {x: 1, y: 2, z: 3}\n"));
        assertTrue(team.getMessage().startsWith("equipes.violet"), team.getMessage());
        IllegalArgumentException point = assertThrows(IllegalArgumentException.class,
                () -> BedwarsMap.fromYaml("m", "equipes:\n  red:\n    lit: {x: 1, y: deux, z: 3}\n"));
        assertEquals("equipes.red.lit.y: a number", point.getMessage());
        assertThrows(IllegalArgumentException.class, () -> BedwarsMap.fromYaml("m", "hauteur-max: 300\n"));
        BedwarsMap empty = read("");
        assertNull(empty.spectators());
        assertFalse(empty.enabled());
    }

    private static BedwarsMap read(String text) {
        try {
            return BedwarsMap.fromYaml("m", text);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
