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
    void firstRushPlaysOnNeighbouringIslands() {
        // Gelato: the islands go by pairs, red and blue side by side in the north
        BedwarsMap gelato = new BedwarsMap("Gelato");
        double[][] spawns = {{-18.5, -78.5}, {19.5, -78.5}, {79.5, -18.5}, {79.5, 19.5}, {19.5, 79.5}, {-18.5, 79.5},
            {-78.5, 19.5}, {-78.5, -18.5}};
        for (int i = 0; i < spawns.length; i++) {
            island(gelato, TeamColor.values()[i], spawns[i][0], spawns[i][1]);
        }
        assertEquals(Arrays.asList(TeamColor.RED, TeamColor.BLUE), gelato.neighbourTeams(2));
        // then the island next to blue: the pairs go around the map
        assertEquals(Arrays.asList(TeamColor.RED, TeamColor.BLUE, TeamColor.GREEN), gelato.neighbourTeams(3));
        BedwarsMap map = new BedwarsMap("Square");
        island(map, TeamColor.RED, 0, 0);
        island(map, TeamColor.BLUE, 100, 0);
        island(map, TeamColor.GREEN, 0, 60);
        assertEquals(Arrays.asList(TeamColor.RED, TeamColor.GREEN), map.neighbourTeams(2));
        assertEquals(Arrays.asList(TeamColor.RED, TeamColor.BLUE), demo().neighbourTeams(2));
        BedwarsMap alone = new BedwarsMap("Alone");
        island(alone, TeamColor.RED, 0, 0);
        assertEquals(Collections.singletonList(TeamColor.RED), alone.neighbourTeams(2));
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

    @Test
    void anAttackAndDefenseMapHasOneBaseAndTheAttackers() throws Exception {
        String text = "actif: true\n"
                + "equipes:\n"
                + "  gray:\n"
                + "    spawn: {x: 96.5, y: 65, z: 31.5, yaw: 90}\n"
                + "    lit: {x: 86, y: 65, z: 31}\n"
                + "    generateur: {x: 105.5, y: 64.5, z: 31.5}\n"
                + "    shop: {x: 98.5, y: 65, z: 37.5, yaw: 180}\n"
                + "attaquants:\n"
                + "  equipe: pink\n"
                + "  departs:\n"
                + "    - {x: 94.5, y: 77, z: 20.5, yaw: 27}\n"
                + "    - {x: 94.5, y: 77, z: 18.5, yaw: 27}\n";
        BedwarsMap map = read(text);
        assertEquals(Collections.singletonList(TeamColor.GRAY), map.completeTeams());
        assertTrue(map.hasAttackers());
        assertEquals(TeamColor.PINK, map.attackerColor());
        assertEquals(2, map.attackerStarts().size());
        assertEquals(new MapPoint(94.5, 77, 20.5, 27f, 0f), map.attackerStarts().get(0));
        assertEquals(2, map.sides(), "the base and the attackers: a game of two sides");
        assertTrue(map.missing().isEmpty(), map.missing().toString());
        assertTrue(map.playable());
        BedwarsMap copy = map.copy();
        assertEquals(TeamColor.PINK, copy.attackerColor());
        assertEquals(map.attackerStarts(), copy.attackerStarts());
        BedwarsMap noStart = read(text.substring(0, text.indexOf("  departs:")));
        assertFalse(noStart.hasAttackers(), "no start, no attackers");
        assertFalse(noStart.playable());
        IllegalArgumentException taken = assertThrows(IllegalArgumentException.class,
                () -> BedwarsMap.fromYaml("m", text.replace("equipe: pink", "equipe: gray")));
        assertTrue(taken.getMessage().startsWith("attaquants.equipe"), taken.getMessage());
        assertThrows(IllegalArgumentException.class, () -> BedwarsMap.fromYaml("m", text.replace("equipe: pink",
                "equipe: violet")));
        assertEquals(demo().completeTeams().size(), demo().sides(), "a map of islands: its complete teams");
    }

    @Test
    void anFfaMapHasItsSpawnsToChooseFrom() throws Exception {
        String text = "actif: true\n"
                + "apparitions:\n"
                + "  Top bridge:\n"
                + "    - {x: 1.5, y: 86, z: -6.5, yaw: 0}\n"
                + "    - {x: -1.5, y: 86, z: 7.5, yaw: 180}\n"
                + "  Low:\n"
                + "    - {x: 0.5, y: 66, z: -3.5}\n"
                + "  Mid:\n"
                + "    - {x: -4.5, y: 76, z: 0.5, yaw: 270}\n";
        BedwarsMap map = read(text);
        assertEquals(java.util.Arrays.asList("Top bridge", "Low", "Mid"), new java.util.ArrayList<>(map.spawnChoices().keySet()),
                "in their order");
        assertEquals(new MapPoint(-1.5, 86, 7.5, 180f, 0f), map.spawnChoices().get("Top bridge").get(1));
        assertEquals(map.spawnChoices(), map.copy().spawnChoices(), "written back as read");
        assertTrue(demo().spawnChoices().isEmpty(), "none: the islands");
        assertThrows(IllegalArgumentException.class, () -> BedwarsMap.fromYaml("m", "apparitions:\n  Low: []\n"));
    }

    @Test
    void theSpawnsOfTheMenuAreCentred() {
        assertEquals(2, FfaSpawnMenu.slotOf(0, 4), "Top bridge, Low, Mid and Random: slots 2 to 5");
        assertEquals(5, FfaSpawnMenu.slotOf(3, 4));
        assertEquals(4, FfaSpawnMenu.slotOf(0, 1));
    }

    private static BedwarsMap read(String text) {
        try {
            return BedwarsMap.fromYaml("m", text);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
