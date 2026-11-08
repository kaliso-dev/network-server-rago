package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DemoMapTest {

    private static BedwarsMap demo() {
        BedwarsMap map = new BedwarsMap(DemoMap.WORLD);
        DemoMap.layout(map);
        return map;
    }

    @Test
    void isPlayableAsItIs() {
        BedwarsMap map = demo();
        assertTrue(map.playable());
        assertEquals(2, map.diamonds().size());
        assertEquals(1, map.emeralds().size());
        assertTrue(map.voidY() < DemoMap.FLOOR);
        assertTrue(map.buildLimit() > DemoMap.Y);
    }

    /** Standing on their spawn, players pick up the iron and gold of their generator. */
    @Test
    void theGeneratorIsWithinReachOfTheSpawn() {
        for (BedwarsMap.Team team : demo().teams().values()) {
            assertTrue(team.generator.flatDistanceSquared(team.spawn.blockX(), team.spawn.blockZ()) <= 1.0);
            assertEquals(team.spawn.y(), team.generator.y());
        }
    }

    /** Blocks can be placed around the bed to defend it: it is out of the spawn protection. */
    @Test
    void theBedIsOutOfTheSpawnProtection() {
        double spawn = BedwarsGame.SPAWN_PROTECTION * BedwarsGame.SPAWN_PROTECTION;
        double generator = BedwarsGame.GENERATOR_PROTECTION * BedwarsGame.GENERATOR_PROTECTION;
        for (BedwarsMap.Team team : demo().teams().values()) {
            // The bed's point is its foot; its head is one block towards the spawn, and the
            // defence one block further.
            int towardsSpawn = team.spawn.x() < team.bed.x() ? -1 : 1;
            int defence = team.bed.blockX() + 2 * towardsSpawn;
            assertTrue(team.spawn.flatDistanceSquared(defence, team.bed.blockZ()) >= spawn, "bed at " + team.bed);
            assertTrue(team.generator.flatDistanceSquared(defence, team.bed.blockZ()) >= generator, "bed at " + team.bed);
        }
    }

    @Test
    void theTeamsFaceTheMiddle() {
        BedwarsMap map = demo();
        assertEquals(-90f, map.teams().get(TeamColor.RED).spawn.yaw());
        assertEquals(90f, map.teams().get(TeamColor.BLUE).spawn.yaw());
        assertFalse(map.teams().get(TeamColor.RED).spawn.x() > 0);
        assertTrue(map.teams().get(TeamColor.BLUE).spawn.x() > 0);
    }

    @Test
    void sidebarClockAndTiers() {
        assertEquals("0:05", BedwarsGame.clock(5));
        assertEquals("6:00", BedwarsGame.clock(360));
        assertEquals("12:34", BedwarsGame.clock(754));
        assertEquals("II", Generator.roman(2));
        assertEquals("III", Generator.roman(3));
    }

    @Test
    void teamsAreFoundByTheirId() {
        assertEquals(TeamColor.RED, TeamColor.byId(" Red "));
        assertEquals(TeamColor.GRAY, TeamColor.byId("gray"));
        assertEquals(null, TeamColor.byId("violet"));
        assertTrue(TeamColor.ids().startsWith("red, blue, green"));
    }
}
