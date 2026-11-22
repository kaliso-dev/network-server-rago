package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** The maps of Capture the Flag the plugin makes: what a game needs on them, where it needs it. */
class CtfMapsTest {

    private static BedwarsMap bridge() {
        BedwarsMap map = new BedwarsMap(CtfMaps.BRIDGE);
        CtfMaps.bridgeLayout(map);
        return map;
    }

    /** The block a player stands on at this point. */
    private static CtfMaps.Piece under(CtfMaps.Plan plan, MapPoint point) {
        return plan.at(point.blockX(), point.blockY() - 1, point.blockZ());
    }

    @Test
    void theBridgeIsPlayableByTwoTeams() {
        BedwarsMap map = bridge();
        assertTrue(map.playable());
        assertEquals(2, map.sides());
        assertTrue(map.diamonds().isEmpty() && map.emeralds().isEmpty(), "no generator in the middle");
        assertTrue(CtfMaps.isCtfMap("ctfbridge"));
        assertFalse(CtfMaps.isCtfMap(DemoMap.WORLD));
    }

    @Test
    void eachTeamStandsOnItsBaseAndFacesTheBridge() {
        BedwarsMap map = bridge();
        CtfMaps.Plan plan = CtfMaps.bridgePlan();
        BedwarsMap.Team red = map.teams().get(TeamColor.RED);
        BedwarsMap.Team blue = map.teams().get(TeamColor.BLUE);
        assertTrue(red.spawn.x() < 0 && blue.spawn.x() > 0, "red in the west, blue in the east");
        assertEquals(-90f, red.spawn.yaw(), "red looks east, to the bridge");
        assertEquals(90f, blue.spawn.yaw());
        for (BedwarsMap.Team team : new BedwarsMap.Team[] {red, blue}) {
            assertNotNull(under(plan, team.spawn), "ground under the spawn " + team.spawn);
            assertTrue(plan.at(team.spawn.blockX(), team.spawn.blockY(), team.spawn.blockZ()) == null
                    && plan.at(team.spawn.blockX(), team.spawn.blockY() + 1, team.spawn.blockZ()) == null,
                    "room to stand at " + team.spawn);
        }
    }

    /** The flag stands on gold, in the open: nothing where its banner goes, nor over it. */
    @Test
    void eachFlagIsOnGoldBehindItsSpawn() {
        BedwarsMap map = bridge();
        CtfMaps.Plan plan = CtfMaps.bridgePlan();
        for (BedwarsMap.Team team : map.teams().values()) {
            if (team.spawn == null) {
                continue;
            }
            MapPoint flag = team.bed;
            assertEquals(Material.GOLD_BLOCK, under(plan, flag).material, "gold under the flag " + flag);
            assertNull(plan.at(flag.blockX(), flag.blockY(), flag.blockZ()));
            assertNull(plan.at(flag.blockX(), flag.blockY() + 1, flag.blockZ()));
            assertTrue(Math.abs(flag.x()) > Math.abs(team.spawn.x()), "the flag at the back of its base");
            double spawn = BedwarsGame.SPAWN_PROTECTION;
            assertTrue(Math.abs(flag.x() - team.spawn.x()) > spawn + 2, "blocks can go around the flag");
        }
    }

    /** One block wide and 41 long, red on the red half, blue on the blue one, white in the middle. */
    @Test
    void theBridgeJoinsTheBases() {
        CtfMaps.Plan plan = CtfMaps.bridgePlan();
        for (int x = -CtfMaps.BRIDGE_HALF; x <= CtfMaps.BRIDGE_HALF; x++) {
            CtfMaps.Piece piece = plan.at(x, CtfMaps.FLOOR, 0);
            assertNotNull(piece, "the bridge at x " + x);
            assertEquals(Material.STAINED_CLAY, piece.material, "clay at x " + x);
            assertEquals(x < 0 ? TeamColor.RED.woolData() : x > 0 ? TeamColor.BLUE.woolData() : 0, piece.data);
            assertNull(plan.at(x, CtfMaps.FLOOR, 1), "one block wide at x " + x);
            assertNull(plan.at(x, CtfMaps.FLOOR, -1));
        }
        assertEquals(41, 2 * CtfMaps.BRIDGE_HALF + 1);
        assertNotNull(plan.at(CtfMaps.BASE_FRONT, CtfMaps.FLOOR, 0), "the blue base right after the bridge");
        assertNotNull(plan.at(-CtfMaps.BASE_FRONT, CtfMaps.FLOOR, 0), "the red one");
        assertNull(plan.at(0, CtfMaps.FLOOR - 1, 0), "the void under the bridge");
    }

    /** The bridge breaks: it is in the 9 blocks of height where players build, over the void line. */
    @Test
    void playersBuildAroundTheBridge() {
        BedwarsMap map = bridge();
        assertEquals(CtfMaps.FLOOR + 4, map.buildLimit());
        assertEquals(CtfMaps.FLOOR - 4, map.floorY());
        assertEquals(9, map.buildLimit() - map.floorY() + 1);
        assertTrue(map.voidY() < map.floorY(), "who falls under the lowest blocks dies");
    }

    @Test
    void theTwoBasesAreTheSameMirrored() {
        CtfMaps.Plan plan = CtfMaps.bridgePlan();
        int checked = 0;
        for (long key : plan.blocks.keySet()) {
            int x = Instance.xOf(key);
            int y = Instance.yOf(key);
            int z = Instance.zOf(key);
            if (x <= 0) {
                continue;
            }
            CtfMaps.Piece blue = plan.at(x, y, z);
            CtfMaps.Piece red = plan.at(-x, y, z);
            assertNotNull(red, "the red base has what the blue one has at " + x + " " + y + " " + z);
            assertEquals(blue.material, red.material);
            if (blue.data == TeamColor.BLUE.woolData()) {
                assertEquals(TeamColor.RED.woolData(), red.data, "in the colour of its team");
            } else {
                assertEquals(blue.data, red.data);
            }
            checked++;
        }
        assertTrue(checked > 500, checked + " blocks");
    }
}
