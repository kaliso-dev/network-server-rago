package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
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
        assertMirrored(CtfMaps.bridgePlan(), 500);
    }

    // --- CtfCastle ------------------------------------------------------------------------------

    private static BedwarsMap castle() {
        BedwarsMap map = new BedwarsMap(CtfMaps.CASTLE);
        CtfMaps.castleLayout(map);
        return map;
    }

    private static boolean free(CtfMaps.Plan plan, int x, int y, int z) {
        return !plan.solid(x, y, z);
    }

    /** A player stands at this point: ground under it, room for the feet and the head. */
    private static void assertStands(CtfMaps.Plan plan, MapPoint point, String what) {
        assertNotNull(under(plan, point), "ground under " + what + " " + point);
        assertTrue(free(plan, point.blockX(), point.blockY(), point.blockZ())
                && free(plan, point.blockX(), point.blockY() + 1, point.blockZ()), "room at " + what + " " + point);
    }

    @Test
    void theCastlesArePlayableAsBedWars() {
        BedwarsMap map = castle();
        assertTrue(map.playable());
        assertEquals(2, map.sides());
        assertEquals(2, map.diamonds().size(), "two diamond generators in the middle");
        assertEquals(1, map.emeralds().size(), "and the emerald one");
        assertTrue(CtfMaps.isCtfMap("ctfcastle"));
        CtfMaps.Plan plan = CtfMaps.castlePlan();
        for (MapPoint point : map.diamonds()) {
            assertEquals(Material.DIAMOND_BLOCK, under(plan, point).material, "on its block " + point);
            assertTrue(Math.abs(point.x()) <= CtfMaps.MID_RADIUS && Math.abs(point.z()) <= CtfMaps.MID_RADIUS, "in the middle");
        }
        assertEquals(Material.EMERALD_BLOCK, under(plan, map.emeralds().get(0)).material);
    }

    /** Each castle: its spawn in the courtyard facing the middle, its forge, shop and upgrades on the ground there. */
    @Test
    void eachTeamHasItsCourtyard() {
        BedwarsMap map = castle();
        CtfMaps.Plan plan = CtfMaps.castlePlan();
        BedwarsMap.Team red = map.teams().get(TeamColor.RED);
        BedwarsMap.Team blue = map.teams().get(TeamColor.BLUE);
        assertTrue(red.spawn.x() < 0 && blue.spawn.x() > 0, "red in the west, blue in the east");
        assertEquals(-90f, red.spawn.yaw(), "red looks east, to the middle");
        assertEquals(90f, blue.spawn.yaw());
        for (BedwarsMap.Team team : new BedwarsMap.Team[] {red, blue}) {
            for (MapPoint point : new MapPoint[] {team.spawn, team.generator, team.shop, team.upgrades}) {
                assertNotNull(point);
                assertStands(plan, point, "the courtyard");
                double x = Math.abs(point.x());
                assertTrue(x > CtfMaps.CASTLE_FRONT + 2 && x < CtfMaps.KEEP_FRONT, "inside the walls, before the keep: " + point);
            }
            assertEquals(Material.IRON_BLOCK, under(plan, team.generator).material, "the forge on iron");
        }
    }

    /** The flag on gold in the keep, at the back of the castle, under its roof, its door open to the courtyard. */
    @Test
    void eachFlagIsInItsKeep() {
        BedwarsMap map = castle();
        CtfMaps.Plan plan = CtfMaps.castlePlan();
        for (BedwarsMap.Team team : new BedwarsMap.Team[] {map.teams().get(TeamColor.RED), map.teams().get(TeamColor.BLUE)}) {
            MapPoint flag = team.bed;
            assertEquals(Material.GOLD_BLOCK, under(plan, flag).material, "gold under the flag " + flag);
            assertNull(plan.at(flag.blockX(), flag.blockY(), flag.blockZ()));
            assertNull(plan.at(flag.blockX(), flag.blockY() + 1, flag.blockZ()));
            assertTrue(Math.abs(flag.x()) > CtfMaps.KEEP_FRONT && Math.abs(flag.x()) < CtfMaps.KEEP_BACK, "in the keep");
            boolean roof = false;
            for (int y = flag.blockY() + 2; y <= CtfMaps.FLOOR + 10; y++) {
                roof |= plan.solid(flag.blockX(), y, flag.blockZ());
            }
            assertTrue(roof, "a roof over the flag");
            assertTrue(Math.abs(flag.x() - team.spawn.x()) > BedwarsGame.SPAWN_PROTECTION + 2, "blocks can go around the flag");
        }
    }

    /** From the middle side of the gate to the flag, along z 0: ground all the way, and room to walk. */
    @Test
    void theWayFromTheGateToTheFlagIsOpen() {
        CtfMaps.Plan plan = CtfMaps.castlePlan();
        for (int side : new int[] {-1, 1}) {
            for (int x = CtfMaps.CASTLE_FRONT; x <= CtfMaps.CASTLE_FLAG_X; x++) {
                for (int z = -1; z <= 1; z++) {
                    int at = side * x;
                    assertTrue(plan.solid(at, CtfMaps.FLOOR, z), "ground at " + at + " " + z);
                    assertTrue(free(plan, at, CtfMaps.Y, z) && free(plan, at, CtfMaps.Y + 1, z) && free(plan, at, CtfMaps.Y + 2, z),
                            "room to walk at " + at + " " + z);
                }
            }
            // the walls stand on each side of the gate, and higher than a jump
            assertTrue(plan.solid(side * CtfMaps.CASTLE_FRONT, CtfMaps.Y, 2));
            assertTrue(plan.solid(side * CtfMaps.CASTLE_FRONT, CtfMaps.WALL_TOP, -2));
        }
    }

    /** The players bridge from a castle to the middle, as in Bed Wars: the void between them. */
    @Test
    void theVoidIsBetweenTheCastlesAndTheMiddle() {
        BedwarsMap map = castle();
        CtfMaps.Plan plan = CtfMaps.castlePlan();
        for (int x = CtfMaps.MID_RADIUS + 1; x < CtfMaps.CASTLE_FRONT; x++) {
            for (int y = CtfMaps.FLOOR - 10; y <= CtfMaps.FLOOR + 10; y++) {
                assertNull(plan.at(x, y, 0), "the void at " + x + " " + y);
                assertNull(plan.at(-x, y, 0));
            }
        }
        assertTrue(map.buildLimit() > CtfMaps.TOWER_TOP + 1, "above the towers");
        int lowest = Integer.MAX_VALUE;
        for (long key : plan.blocks.keySet()) {
            lowest = Math.min(lowest, Instance.yOf(key));
        }
        assertTrue(map.voidY() < lowest, "who falls under the lowest blocks dies");
    }

    /** Each light is next to the air: none hidden in a wall or a tower. */
    @Test
    void everyLightOfTheCastlesIsSeen() {
        CtfMaps.Plan plan = CtfMaps.castlePlan();
        int lights = 0;
        for (Map.Entry<Long, CtfMaps.Piece> entry : plan.blocks.entrySet()) {
            if (entry.getValue().material != Material.GLOWSTONE) {
                continue;
            }
            int x = Instance.xOf(entry.getKey());
            int y = Instance.yOf(entry.getKey());
            int z = Instance.zOf(entry.getKey());
            boolean seen = false;
            for (int[] face : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                seen |= plan.at(x + face[0], y + face[1], z + face[2]) == null;
            }
            assertTrue(seen, "a light hidden at " + x + " " + y + " " + z);
            lights++;
        }
        assertTrue(lights > 20, lights + " lights");
    }

    @Test
    void theTwoCastlesAreTheSameMirrored() {
        assertMirrored(CtfMaps.castlePlan(), 5000);
    }

    /** What the east half has, the west half has at the same place mirrored, in its own colour. */
    private static void assertMirrored(CtfMaps.Plan plan, int atLeast) {
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
        assertTrue(checked > atLeast, checked + " blocks");
    }
}
