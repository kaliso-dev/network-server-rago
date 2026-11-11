package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * The bridge fight: its four platforms, their pads and walkways, and where a player belongs; on the
 * owner's map, its platforms and its portals.
 */
class BridgeArenaTest {

    @Test
    void thePlatformsTaperAtBothEnds() {
        for (int x = -BridgeArena.HALF_LENGTH; x <= BridgeArena.HALF_LENGTH; x++) {
            assertEquals(BridgeArena.halfWidth(x), BridgeArena.halfWidth(-x), "the same at both ends");
            if (x > 0) {
                assertTrue(BridgeArena.halfWidth(x) <= BridgeArena.halfWidth(x - 1), "narrower towards the tips");
            }
        }
        assertEquals(9, 2 * BridgeArena.halfWidth(0) + 1, "nine wide in the middle");
        assertEquals(0, BridgeArena.halfWidth(BridgeArena.HALF_LENGTH), "one block at the tips");
        assertEquals(-1, BridgeArena.halfWidth(BridgeArena.HALF_LENGTH + 1));
    }

    @Test
    void fourPlatformsOfTwoColoursEachAndApart() {
        assertEquals(4, BridgeArena.PLATFORMS.length);
        Set<Integer> rows = new HashSet<>();
        int widest = BridgeArena.halfWidth(0);
        for (int i = 0; i < BridgeArena.PLATFORMS.length; i++) {
            BridgeArena.Platform one = BridgeArena.PLATFORMS[i];
            assertNotEquals(one.first, one.second, one.name);
            assertEquals(one.first, one.colourAt(-1, 0), "the first colour on the west half");
            assertEquals(one.second, one.colourAt(1, 0));
            assertTrue(rows.add(one.z));
            for (int j = i + 1; j < BridgeArena.PLATFORMS.length; j++) {
                int gap = Math.abs(one.z - BridgeArena.PLATFORMS[j].z) - 2 * widest - 1;
                assertTrue(gap >= 8, one.name + " and " + BridgeArena.PLATFORMS[j].name + " are " + gap + " apart");
            }
        }
    }

    @Test
    void thePadsAreAShortBridgeFromTheTips() {
        // the pad is three wide, centred on PAD: its inner edge, then the gap to the tip
        int gap = BridgeArena.PAD - 1 - BridgeArena.HALF_LENGTH - 1;
        assertTrue(gap >= 2 && gap <= 5, "a bridge of " + gap + " blocks");
    }

    @Test
    void theWalkwaysLinkThePadsOfEveryPlatform() {
        for (BridgeArena.Platform platform : BridgeArena.PLATFORMS) {
            for (int z = platform.z - 1; z <= platform.z + 1; z++) {
                assertTrue(BridgeArena.onPad(z), platform.name + " has its pad at " + z);
            }
        }
        // between two platforms side by side, the walkway runs where no pad is
        assertFalse(BridgeArena.onPad(20));
        assertFalse(BridgeArena.onPad(0));
        assertFalse(BridgeArena.onPad(-20));
    }

    @Test
    void aPlayerBelongsToTheNearestPlatform() {
        List<BridgeArena.Platform> own = Arrays.asList(BridgeArena.PLATFORMS);
        for (int i = 0; i < BridgeArena.PLATFORMS.length; i++) {
            BridgeArena.Platform platform = BridgeArena.PLATFORMS[i];
            assertEquals(i, BridgeArena.nearest(own, 0.5, platform.z + 0.5), platform.name);
            assertEquals(i, BridgeArena.nearest(own, -BridgeArena.PAD, platform.z + 4.9), platform.name + ", at its widest edge");
            for (double[] end : platform.ends) {
                assertEquals(i, BridgeArena.nearest(own, end[0], end[2]), platform.name + ", on its pad");
                assertTrue(BridgeArena.distance(platform, end[0], end[2]) <= BridgeArena.FOLLOW_REACH);
            }
        }
        assertEquals(1, BridgeArena.nearest(own, 20.5, 29.0), "Red & White, at z 30");
        assertEquals(0, BridgeArena.nearest(own, 20.5, 19.0), "Blue & Yellow, at z 10, on the walkway");
        assertEquals(3, BridgeArena.nearest(own, 0, -60.0), "past the last one");
    }

    @Test
    void theOwnPlatformsHaveTheirPadsAtTheirEnds() {
        for (BridgeArena.Platform platform : BridgeArena.PLATFORMS) {
            assertEquals(2, platform.ends.length);
            assertEquals(-BridgeArena.PAD + 0.5, platform.ends[0][0], 1e-9, "the first colour's end, west");
            assertEquals(-90f, platform.ends[0][3], 1e-9, "facing east, the platform");
            assertEquals(BridgeArena.PAD + 0.5, platform.ends[1][0], 1e-9);
            assertEquals(90f, platform.ends[1][3], 1e-9);
            assertEquals(platform.z + 0.5, platform.ends[0][2], 1e-9);
            assertEquals(1, platform.spread, "anywhere on the pad, three wide");
        }
    }

    @Test
    void theOneVersusOneHasItsOwnPlatformOfTheColoursOfTheSides() {
        assertEquals(Shapes.RED, BridgeDuel.PLATFORM.colourAt(-1, 0), "red on the west half");
        assertEquals(Shapes.BLUE, BridgeDuel.PLATFORM.colourAt(1, 0));
        assertEquals(Duel.WOOL[0], BridgeDuel.PLATFORM.first);
        assertEquals(Duel.WOOL[1], BridgeDuel.PLATFORM.second);
    }

    @Test
    void aNewPlayerGoesWhereThereAreFewest() {
        assertEquals(0, BridgeArena.leastCrowded(new int[] {0, 0, 0, 0}), "the first one on a tie");
        assertEquals(2, BridgeArena.leastCrowded(new int[] {3, 1, 0, 2}));
        assertEquals(1, BridgeArena.leastCrowded(new int[] {2, 1, 1, 2}));
    }

    @Test
    void theMenuShowsEveryPlatformInOneRow() {
        assertArrayEquals(new int[] {1, 3, 5, 7}, BridgeArena.menuSlots(4), "the plugin's four");
        assertArrayEquals(new int[] {0, 2, 4, 6, 8}, BridgeArena.menuSlots(5), "the five of the owner's map");
        assertArrayEquals(new int[] {2, 4, 6}, BridgeArena.menuSlots(3));
        for (int count = 1; count <= BridgeArena.MAX_PLATFORMS; count++) {
            Set<Integer> slots = new HashSet<>();
            for (int slot : BridgeArena.menuSlots(count)) {
                assertTrue(slot >= 0 && slot < 9, count + " platforms: slot " + slot);
                assertTrue(slots.add(slot));
            }
            assertEquals(count, slots.size());
        }
    }

    // --- The owner's map ------------------------------------------------------------------------

    /**
     * A map laid out as the lowest floor of the owner's: two blue and yellow platforms in a column
     * (north and south), a booth between them, a red and black platform of clay to the east of the
     * southern one with its own booth in front of it and nothing beyond; a small square of wool, and
     * a floor one block up, that are no platforms.
     */
    private static MapCopy ownersFloor() {
        List<Integer> blocks = new ArrayList<>();
        rectangle(blocks, -23, -13, -28, -6, 0, 35, 11, 4);
        rectangle(blocks, -23, -13, 6, 28, 0, 35, 11, 4);
        rectangle(blocks, -6, 4, 6, 28, 0, 159, 14, 14);
        // the black H in the middle of the red one
        for (int z = 10; z <= 24; z++) {
            set(blocks, -1, 0, z, 159, 15);
            set(blocks, 0, 0, z, 159, 15);
        }
        for (int x = -6; x <= 4; x++) {
            set(blocks, x, 0, 17, 159, 15);
        }
        rectangle(blocks, 30, 32, 0, 2, 0, 35, 0, 0);
        rectangle(blocks, 30, 40, 20, 40, 1, 35, 5, 5);
        // the booths: a sea lantern on the floor, one in the roof
        for (int x : new int[] {-18, 0}) {
            set(blocks, x, 1, 0, 169, 0);
            set(blocks, x, 9, 0, 169, 0);
            set(blocks, x - 1, 1, 0, 155, 0);
        }
        int[] packed = new int[blocks.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = blocks.get(i);
        }
        return MapCopy.of(packed);
    }

    /** A floor of this block, the first colour on its north half, the second on the south one. */
    private static void rectangle(List<Integer> blocks, int x1, int x2, int z1, int z2, int y, int id, int north, int south) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                set(blocks, x, y, z, id, 2 * z < z1 + z2 ? north : south);
            }
        }
    }

    private static void set(List<Integer> blocks, int x, int y, int z, int id, int data) {
        for (int i = 0; i < blocks.size(); i += 5) {
            if (blocks.get(i) == x && blocks.get(i + 1) == y && blocks.get(i + 2) == z) {
                blocks.set(i + 3, id);
                blocks.set(i + 4, data);
                return;
            }
        }
        blocks.addAll(Arrays.asList(x, y, z, id, data));
    }

    @Test
    void theMapsPlatformsAreItsFloorsOfWoolAndClay() {
        List<BridgeArena.Platform> platforms = BridgeArena.platformsOf(ownersFloor());
        assertEquals(3, platforms.size(), "no square of 9, no floor one block up");
        BridgeArena.Platform north = platforms.get(0);
        BridgeArena.Platform south = platforms.get(1);
        BridgeArena.Platform h = platforms.get(2);
        assertEquals("Blue & Yellow (north)", north.name);
        assertEquals("Blue & Yellow (south)", south.name);
        assertEquals("Red & Black", h.name, "red on both halves: the next colour most of it is");
        assertEquals(Shapes.BLUE, north.first);
        assertEquals(Shapes.YELLOW, north.second);
        assertEquals(DyeColor.BLUE.getColor(), north.firstArmour);
        assertEquals(DyeColor.YELLOW.getColor(), north.secondArmour);
        assertEquals(Shapes.RED, h.first);
        assertEquals(Shapes.BLACK, h.second);
        assertEquals(Material.WOOL, north.block);
        assertEquals(Material.STAINED_CLAY, h.block);
        assertEquals(-18, north.x);
        assertEquals(-17, north.z);
        assertEquals(-1, h.x);
        assertEquals(17, h.z);
        // two blocks in from the tips, on the middle line, facing the middle
        assertArrayEquals(new double[] {-17.5, 1, -25.5, 0}, north.ends[0], 1e-9, "the north end, facing south");
        assertArrayEquals(new double[] {-17.5, 1, -7.5, 180}, north.ends[1], 1e-9, "the south end, facing north");
        assertEquals(0, north.spread);
        for (int i = 0; i < platforms.size(); i++) {
            for (double[] end : platforms.get(i).ends) {
                assertEquals(i, BridgeArena.nearest(platforms, end[0], end[2]), platforms.get(i).name);
            }
        }
    }

    @Test
    void twoPlatformsOfTheSameColoursSideBySideAreWestAndEast() {
        BridgeArena.Platform one = BridgeArena.PLATFORMS[0];
        List<BridgeArena.Platform> named = BridgeArena.withPlaces(Arrays.asList(
                new BridgeArena.Platform("Lime", one.chat, 5, one.firstArmour, 5, one.firstArmour, 30, 0, Material.WOOL, one.ends, 0),
                new BridgeArena.Platform("Lime", one.chat, 5, one.firstArmour, 5, one.firstArmour, -30, 2, Material.WOOL, one.ends, 0),
                one));
        assertEquals("Lime (east)", named.get(0).name);
        assertEquals("Lime (west)", named.get(1).name);
        assertEquals(one.name, named.get(2).name, "alone with its name");
    }

    @Test
    void aPortalLeadsToTheNextIslandOfTheArena() {
        MapCopy map = ownersFloor();
        List<BridgeArena.Platform> platforms = BridgeArena.platformsOf(map);
        List<int[]> portals = BridgeArena.portalsOf(map);
        assertEquals(2, portals.size(), "one for each column of sea lanterns");
        int[] west = portals.get(0);
        int[] middle = portals.get(1);
        assertArrayEquals(new int[] {-18, 1, 0}, west, "at its lowest lantern");
        assertArrayEquals(new int[] {0, 1, 0}, middle);
        // yaw 0: south, 180: north, 90: west, -90: east
        assertEquals(1, BridgeArena.nextIsland(portals, 0, -90f), "from the west island to the middle one");
        assertEquals(0, BridgeArena.nextIsland(portals, 1, 90f), "and back");
        assertEquals(1, BridgeArena.nextIsland(portals, 0, 90f), "nothing further west: the nearest one");
        // in at the end next to the portal
        assertEquals(0, BridgeArena.nearestEnd(platforms.get(1), west), "the north end of the south one");
        assertEquals(1, BridgeArena.nearestEnd(platforms.get(0), west));
    }

    @Test
    void thePlayersComeInOnTheSeaLanternOfTheBoothNextToTheirPlatform() {
        MapCopy map = ownersFloor();
        List<BridgeArena.Platform> platforms = BridgeArena.platformsOf(map);
        List<int[]> portals = BridgeArena.portalsOf(map);
        assertEquals(0, BridgeArena.boothOf(platforms.get(0), portals), "the north one: the west booth, south of it");
        assertEquals(0, BridgeArena.boothOf(platforms.get(1), portals), "the south one: the same booth, north of it");
        assertEquals(1, BridgeArena.boothOf(platforms.get(2), portals), "the red and black one: the middle booth");
        assertEquals(-1, BridgeArena.boothOf(platforms.get(0), Collections.<int[]>emptyList()), "no booth: at its ends");
        assertEquals(-1, BridgeArena.boothOf(platforms.get(0), Collections.singletonList(new int[] {80, 1, 80})), "too far");
        // yaw 0: south, 180: north, 90: west, -90: east
        assertEquals(180f, Math.abs(BridgeArena.yawTo(0.5, 0.5, 0.5, -10)), 1e-3, "the north one, from the booth");
        assertEquals(0f, BridgeArena.yawTo(0.5, 0.5, 0.5, 10), 1e-3);
        assertEquals(90f, BridgeArena.yawTo(0.5, 0.5, -10, 0.5), 1e-3);
        assertEquals(-90f, BridgeArena.yawTo(0.5, 0.5, 10, 0.5), 1e-3);
        assertTrue(BridgeArena.inBooth(portals, -17.5, 2, 0.5), "on the lantern of the west booth");
        assertTrue(!BridgeArena.inBooth(portals, -17.5, 2, -6.5), "on the platform");
    }

    @Test
    void theHitsHurtOnlyOnTheMiddlePlatform() {
        List<BridgeArena.Platform> platforms = BridgeArena.platformsOf(ownersFloor());
        assertEquals(2, BridgeArena.middleOf(platforms), "the red H, south of the middle booth (the centre)");
        assertEquals(-1, BridgeArena.middleOf(Arrays.asList(BridgeArena.PLATFORMS)),
                "the plugin's own four: two as near, no middle, Resistance everywhere");
        assertEquals(5, BridgeArena.RESISTANCE, "V takes all the damage of the hits");
    }

    @Test
    void aPlayerOutComesBackOnTheirPlatformAwayFromTheOthers() {
        BridgeArena.Platform north = BridgeArena.platformsOf(ownersFloor()).get(0);
        // its ends: z -25.5 (north) and -7.5 (south)
        assertEquals(-1, BridgeArena.farthestEnd(north, Collections.<double[]>emptyList()), "alone: either end");
        assertEquals(1, BridgeArena.farthestEnd(north, Collections.singletonList(new double[] {-17.5, -24})),
                "someone at the north end: the south one");
        assertEquals(0, BridgeArena.farthestEnd(north, Arrays.asList(new double[] {-17.5, -9}, new double[] {-17, -14})),
                "the fight in the south half: the north end");
    }

    @Test
    void aClickReachesTheBoothItsPlayerLooksAt() {
        double[][] booth = BridgeArena.booth(new int[] {-18, 1, 0});
        assertArrayEquals(new double[] {-21, 1, -3}, booth[0], 1e-9);
        assertArrayEquals(new double[] {-14, 10, 4}, booth[1], 1e-9);
        // at the south end of the north platform, eyes 1.62 above the feet
        double[] eye = {-17.5, 2.62, -6.5};
        assertTrue(BridgeArena.aims(eye, new double[] {0, 0, 1}, booth[0], booth[1], BridgeArena.PORTAL_REACH), "looking at it");
        double down = Math.toRadians(20);
        assertTrue(BridgeArena.aims(eye, new double[] {0, -Math.sin(down), Math.cos(down)}, booth[0], booth[1],
                BridgeArena.PORTAL_REACH), "a bit down, at its floor");
        assertTrue(!BridgeArena.aims(eye, new double[] {0, 0, -1}, booth[0], booth[1], BridgeArena.PORTAL_REACH), "behind");
        assertTrue(!BridgeArena.aims(eye, new double[] {1, 0, 0}, booth[0], booth[1], BridgeArena.PORTAL_REACH), "aside");
        assertTrue(!BridgeArena.aims(new double[] {-17.5, 2.62, -12}, new double[] {0, 0, 1}, booth[0], booth[1],
                BridgeArena.PORTAL_REACH), "too far");
        assertTrue(BridgeArena.aims(new double[] {-17.5, 2.62, 0.5}, new double[] {0, 0, 1}, booth[0], booth[1],
                BridgeArena.PORTAL_REACH), "from inside it");
    }

    @Test
    void theBundledBridgeFightIsTheLowestFloorOfTheOwnersMap() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        List<Map<?, ?>> maps = config.getMapList("bridge-fight.cartes");
        assertEquals(1, maps.size());
        assertEquals("Bridge Fight Plateform", maps.get(0).get("monde"));
        assertEquals(null, RagoPractice.checkZoneMap(maps.get(0), config.getInt("espacement")));
        assertEquals(Arrays.asList(85, 107, 83), maps.get(0).get("centre"), "the middle portal, at the height of the platforms");
    }

    @Test
    void theThreeIslandsOfTheOwnersFloor() {
        // as on the owner's first floor: three islands in a row, the middle one between the two others
        List<int[]> islands = Arrays.asList(new int[] {67, 100, 83}, new int[] {85, 100, 83}, new int[] {103, 100, 83},
                new int[] {85, 140, 83});
        // yaw 90: west (-x), -90: east (+x)
        assertEquals(0, BridgeArena.nextIsland(islands, 1, 90f), "from the middle, looking west: the west island");
        assertEquals(2, BridgeArena.nextIsland(islands, 1, -90f), "looking east: the east island");
        assertEquals(2, BridgeArena.nextIsland(islands, 1, -60f), "looking a bit aside");
        assertEquals(1, BridgeArena.nextIsland(islands, 0, -90f), "from the west island: the middle one");
        assertEquals(1, BridgeArena.nextIsland(islands, 0, 90f), "the way back too, nothing further west");
        assertEquals(1, BridgeArena.nextIsland(islands, 2, 90f), "from the east island: the middle one");
        assertEquals(1, BridgeArena.nextIsland(islands, 2, 0f), "looking south, at no island: the nearest one");
        assertEquals(-1, BridgeArena.nextIsland(islands, 3, 0f), "alone on its floor");
    }

    @Test
    void theSidebarShowsTheThreeWithTheMostKills() {
        List<java.util.Map.Entry<String, Integer>> players = new ArrayList<>();
        assertEquals(Arrays.asList("Top kills", "Nobody yet"), plain(KillBoard.topLines(players, 3)));
        players.add(new java.util.AbstractMap.SimpleEntry<>("Ali", 3));
        players.add(new java.util.AbstractMap.SimpleEntry<>("Zero", 0));
        players.add(new java.util.AbstractMap.SimpleEntry<>("Sofiene", 12));
        players.add(new java.util.AbstractMap.SimpleEntry<>("Bob", 8));
        players.add(new java.util.AbstractMap.SimpleEntry<>("Eve", 1));
        assertEquals(Arrays.asList("Top kills", "1. Sofiene 12", "2. Bob 8", "3. Ali 3"), plain(KillBoard.topLines(players, 3)),
                "three at most, with a kill");
    }

    @Test
    void theBoardShowsTheTenFirstEver() {
        List<java.util.Map.Entry<String, Integer>> top = new ArrayList<>();
        assertEquals(Arrays.asList("TOP KILLS", "Lifetime", "Nobody yet"), plain(KillBoard.lines(top)));
        for (int i = 0; i < 12; i++) {
            top.add(new java.util.AbstractMap.SimpleImmutableEntry<>("Player" + i, 100 - i));
        }
        List<String> lines = plain(KillBoard.lines(top));
        assertEquals(2 + KillBoard.ROWS, lines.size(), "the title, then the ten first");
        assertEquals("1. Player0 - 100", lines.get(2));
    }

    private static List<String> plain(List<String> lines) {
        List<String> plain = new ArrayList<>();
        for (String line : lines) {
            plain.add(org.bukkit.ChatColor.stripColor(line));
        }
        return plain;
    }
}
