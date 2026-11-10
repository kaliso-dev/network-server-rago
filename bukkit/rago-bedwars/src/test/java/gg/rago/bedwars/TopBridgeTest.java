package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** The top bridge: the owner's Antenna platform, on any map. */
class TopBridgeTest {

    /**
     * Two islands on a line of z, as on Antenna: from z 74 to 99 on each side (and the same towards
     * the negative z), their floor from y 76 to 82, beds at y 83.
     */
    private static TopBridge.Solid islands(int edge) {
        return (x, y, z) -> Math.abs(z) >= edge && Math.abs(z) <= 99 && Math.abs(x) <= 11 && y >= 76 && y <= 82;
    }

    private static Map<String, TopBridge.Piece> byPlace(TopBridge bridge) {
        Map<String, TopBridge.Piece> places = new HashMap<>();
        for (TopBridge.Piece piece : bridge.pieces()) {
            places.put(piece.x + " " + piece.y + " " + piece.z, piece);
        }
        return places;
    }

    @Test
    void onAntennaItIsTheOwnersPlatform() {
        MapPoint red = new MapPoint(0, 83, 79, 0f, 0f);
        MapPoint blue = new MapPoint(0, 83, -79, 0f, 0f);
        TopBridge bridge = TopBridge.plan(TeamColor.RED, red, TeamColor.BLUE, blue, islands(74));
        assertNotNull(bridge);
        assertEquals(99, bridge.height(), "16 above the beds");
        Map<String, TopBridge.Piece> places = byPlace(bridge);
        // the platform: x -8 to 8, z -39 to 39, y 99, a white block in the middle
        for (int x = -8; x <= 8; x++) {
            for (int z = -39; z <= 39; z++) {
                assertTrue(places.containsKey(x + " 99 " + z), x + " 99 " + z);
            }
        }
        assertEquals(null, places.get("0 99 0").team, "white in the middle");
        assertEquals(TeamColor.RED, places.get("0 99 1").team);
        assertEquals(TeamColor.BLUE, places.get("0 99 -1").team);
        assertEquals(TeamColor.RED, places.get("-3 99 0").team, "the middle row: each half of it");
        assertEquals(TeamColor.BLUE, places.get("3 99 0").team);
        assertTrue(!places.containsKey("9 99 0") && !places.containsKey("0 99 40"));
        // the stairway of the owner, block for block: from z 73 at y 82 up to z 40 at y 98
        int[][] blueSide = {{-73, 82}, {-72, 82}, {-71, 82}, {-71, 83}, {-70, 83}, {-69, 83}, {-69, 84}, {-41, 97},
            {-41, 98}, {-40, 98}, {-39, 98}};
        for (int[] block : blueSide) {
            assertEquals(TeamColor.BLUE, places.get("0 " + block[1] + " " + block[0]).team, block[0] + " " + block[1]);
            assertEquals(TeamColor.RED, places.get("0 " + block[1] + " " + -block[0]).team, -block[0] + " " + block[1]);
        }
        assertTrue(!places.containsKey("0 81 -72") && !places.containsKey("0 82 -74") && !places.containsKey("1 82 -73"),
                "nothing more");
        Set<String> stairs = new HashSet<>();
        for (TopBridge.Piece piece : bridge.pieces()) {
            if (piece.y < 99) {
                stairs.add(piece.x + " " + piece.y + " " + piece.z);
            }
        }
        // per side: 34 steps, 16 blocks under them, the one under the platform's first row
        assertEquals(2 * (34 + 16 + 1), stairs.size());
        assertEquals(17 * 79 + stairs.size(), bridge.pieces().size(), "as many blocks as the owner's");
        assertEquals(14, places.get("0 99 1").woolData(), "red wool");
        assertEquals(0, places.get("0 99 0").woolData(), "white wool");
    }

    @Test
    void nearerIslandsHaveALowerPlatform() {
        MapPoint red = new MapPoint(0, 83, 40, 0f, 0f);
        MapPoint blue = new MapPoint(0, 83, -40, 0f, 0f);
        TopBridge.Solid solid = (x, y, z) -> Math.abs(z) >= 35 && Math.abs(z) <= 50 && Math.abs(x) <= 11 && y >= 76
                && y <= 82;
        TopBridge bridge = TopBridge.plan(TeamColor.RED, red, TeamColor.BLUE, blue, solid);
        assertNotNull(bridge);
        assertTrue(bridge.height() < 99, "no room for 16 blocks up: " + bridge.height());
        int shortest = Integer.MAX_VALUE;
        int longest = Integer.MIN_VALUE;
        for (TopBridge.Piece piece : bridge.pieces()) {
            if (piece.y == bridge.height() && piece.x == 0) {
                shortest = Math.min(shortest, piece.z);
                longest = Math.max(longest, piece.z);
            }
        }
        assertTrue(longest - shortest + 1 >= 2 * TopBridge.MIN_HALF_LENGTH, (longest - shortest + 1) + " long");
        assertNull(TopBridge.plan(TeamColor.RED, new MapPoint(0, 83, 6, 0f, 0f), TeamColor.BLUE,
                new MapPoint(0, 83, -6, 0f, 0f), (x, y, z) -> false), "beds too near");
    }

    @Test
    void betweenBedsOnASlantTheStairsCanBeClimbed() {
        // Invasion: beds at (39, -39) and (-39, 39), islands of 6 around them
        MapPoint red = new MapPoint(39, 94, -39, 0f, 0f);
        MapPoint green = new MapPoint(-39, 94, 39, 0f, 0f);
        TopBridge.Solid solid = (x, y, z) -> y >= 88 && y <= 93
                && (Math.abs(x - 39) <= 6 && Math.abs(z + 39) <= 6 || Math.abs(x + 39) <= 6 && Math.abs(z - 39) <= 6);
        TopBridge bridge = TopBridge.plan(TeamColor.RED, red, TeamColor.GREEN, green, solid);
        assertNotNull(bridge);
        Map<String, Integer> top = new HashMap<>();
        for (TopBridge.Piece piece : bridge.pieces()) {
            top.merge(piece.x + " " + piece.z, piece.y, Math::max);
        }
        // walking from any block of a stairway to a neighbour: one block up at most
        for (Map.Entry<String, Integer> cell : top.entrySet()) {
            String[] xz = cell.getKey().split(" ");
            int x = Integer.parseInt(xz[0]);
            int z = Integer.parseInt(xz[1]);
            boolean connected = false;
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                Integer next = top.get((x + d[0]) + " " + (z + d[1]));
                connected |= next != null && Math.abs(next - cell.getValue()) <= 1;
            }
            assertTrue(connected, "a cell no neighbour reaches: " + cell.getKey());
        }
        assertTrue(bridge.height() > 94 && bridge.height() <= 94 + TopBridge.ABOVE_BED);
    }

    @Test
    void aMapLeavesOutItsBoxes() throws Exception {
        String yaml = "actif: true\nretirer:\n- de: {x: -8, y: 81, z: -73}\n  a: {x: 8, y: 120, z: 73}\n  blocs: [WOOL]\n";
        BedwarsMap map = BedwarsMap.fromYaml("Antenna", yaml);
        assertEquals(1, map.removals().size());
        BedwarsMap.Removal box = map.removals().get(0);
        assertEquals(-8, box.minX);
        assertEquals(120, box.maxY);
        assertTrue(box.takes(Material.WOOL));
        assertTrue(!box.takes(Material.STONE) && !box.takes(Material.AIR));
        BedwarsMap again = BedwarsMap.fromYaml("Antenna", map.toYaml());
        assertEquals(73, again.removals().get(0).maxZ, "kept by /bw");
        assertEquals(Material.WOOL, again.removals().get(0).materials.get(0));
        assertTrue(map.copy().removals().size() == 1);
    }

    @Test
    void eachTeamStartsOnItsHalfFacingTheOther() {
        MapPoint red = new MapPoint(0, 83, 79, 0f, 0f);
        MapPoint blue = new MapPoint(0, 83, -79, 0f, 0f);
        TopBridge bridge = TopBridge.plan(TeamColor.RED, red, TeamColor.BLUE, blue, islands(74));
        assertNotNull(bridge);
        assertEquals(78, bridge.length(), 1e-9, "from its first row to its last: 79 rows");
        // red, bed A, is on the side of the positive z: 12 blocks from the white middle, looking north
        double[] redStart = bridge.start(true, 0, 1);
        assertEquals(0.5, redStart[0], 1e-9);
        assertEquals(100, redStart[1], 1e-9, "on the platform");
        assertEquals(12.5, redStart[2], 1e-9);
        assertEquals(180, Math.abs(redStart[3]), 1e-3, "facing the blue half");
        double[] blueStart = bridge.start(false, 0, 1);
        assertEquals(-11.5, blueStart[2], 1e-9);
        assertEquals(0, blueStart[3], 1e-3, "facing the red half");
        // two of a team side by side, across the platform
        double[] first = bridge.start(true, 0, 2);
        double[] second = bridge.start(true, 1, 2);
        assertEquals(first[2], second[2], 1e-9);
        assertEquals(TopBridge.START_SPACING, Math.abs(second[0] - first[0]), 1.01);
        assertTrue(Math.abs(first[0]) <= TopBridge.HALF_WIDTH && Math.abs(second[0]) <= TopBridge.HALF_WIDTH);
        assertEquals(100 - TopBridge.FALL_BELOW, bridge.fallLine(), "ten blocks under the platform: fallen");
        assertEquals(106, bridge.above()[1], 1e-9, "the dead watch from above the middle");
        assertEquals(0.5, bridge.above()[2], 1e-9);
    }

    @Test
    void anywhereIsOnThePlatform() {
        MapPoint red = new MapPoint(0, 83, 79, 0f, 0f);
        MapPoint blue = new MapPoint(0, 83, -79, 0f, 0f);
        TopBridge bridge = TopBridge.plan(TeamColor.RED, red, TeamColor.BLUE, blue, islands(74));
        Set<String> platform = new HashSet<>();
        for (TopBridge.Piece piece : bridge.pieces()) {
            if (piece.y == bridge.height()) {
                platform.add(piece.x + " " + piece.z);
            }
        }
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < 500; i++) {
            double[] spot = bridge.anywhere(random);
            String cell = (int) Math.floor(spot[0]) + " " + (int) Math.floor(spot[2]);
            assertTrue(platform.contains(cell), "off the platform: " + cell);
            assertEquals(bridge.height() + 1, spot[1], 1e-9);
            if (spot[2] != 0.5) {
                assertEquals(spot[2] > 0.5 ? 180 : 0, Math.abs(spot[3]), 1e-3, "facing the middle");
            }
        }
    }

    @Test
    void onASlantTheStartsFollowTheLine() {
        MapPoint red = new MapPoint(60, 83, 60, 0f, 0f);
        MapPoint blue = new MapPoint(-60, 83, -60, 0f, 0f);
        TopBridge.Solid solid = (x, y, z) -> Math.hypot(Math.abs(x) - 60, Math.abs(z) - 60) <= 6 && y >= 76 && y <= 82
                && Math.signum(x) == Math.signum(z);
        TopBridge bridge = TopBridge.plan(TeamColor.RED, red, TeamColor.BLUE, blue, solid);
        assertNotNull(bridge);
        double[] redStart = bridge.start(true, 0, 1);
        double[] blueStart = bridge.start(false, 0, 1);
        assertTrue(redStart[0] > 0 && redStart[2] > 0 && blueStart[0] < 0 && blueStart[2] < 0, "each on its side");
        assertEquals(24, Math.hypot(redStart[0] - blueStart[0], redStart[2] - blueStart[2]), 1.5);
        assertEquals(135, Math.abs(redStart[3]), 1e-3, "red looks to the north-west, towards blue");
    }
}
