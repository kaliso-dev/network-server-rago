package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** Lobby Fight: the waiting lobby of a map, where its players stand and start, where blocks go. */
class LobbyFightTest {

    /** The floor of a lobby as Archway FFA's: a round of radius 11 around the middle of 0 0, at y 117. */
    private static List<int[]> roundFloor() {
        List<int[]> floor = new ArrayList<>();
        for (int x = -11; x <= 11; x++) {
            for (int z = -11; z <= 11; z++) {
                if (x * x + z * z <= 121) {
                    floor.add(new int[] {x, z});
                }
            }
        }
        return floor;
    }

    /** That floor, with four posts across the middle (at 6 blocks along x and z, two blocks high). */
    private static LobbyFight.Blocks posts() {
        return new LobbyFight.Blocks() {
            @Override
            public boolean solid(int x, int y, int z) {
                if (y == 117) {
                    return x * x + z * z <= 121;
                }
                boolean post = (Math.abs(x) == 6 && z == 0) || (x == 0 && Math.abs(z) == 6);
                return post && (y == 118 || y == 119);
            }

            @Override
            public boolean empty(int x, int y, int z) {
                return !solid(x, y, z);
            }
        };
    }

    @Test
    void theLobbyIsTheRoundAroundItsFloor() {
        BedwarsMap.WaitingLobby lobby = BedwarsMap.WaitingLobby.around(roundFloor(), 117);
        assertEquals(0.5, lobby.centreX, 1e-9, "the middle of the box around the floor");
        assertEquals(0.5, lobby.centreZ, 1e-9);
        assertEquals(117, lobby.floorY);
        assertEquals(11.0, lobby.radius, 1e-9, "as far as the middle of the farthest block");
        assertTrue(lobby.covers(-11, 0) && lobby.covers(11, 0) && lobby.covers(0, -11));
        assertFalse(lobby.covers(12, 0), "past the floor");
        assertFalse(lobby.covers(9, 9), "past the round, in the corner of the box");
        List<int[]> odd = new ArrayList<>();
        odd.add(new int[] {4, 7});
        odd.add(new int[] {5, 7});
        odd.add(new int[] {4, 8});
        BedwarsMap.WaitingLobby small = BedwarsMap.WaitingLobby.around(odd, 80);
        assertEquals(5.0, small.centreX, 1e-9);
        assertEquals(8.0, small.centreZ, 1e-9);
        assertEquals(1.0, small.radius, 1e-9, "a half block above the farthest (0.707)");
        assertNull(BedwarsMap.WaitingLobby.around(new ArrayList<>(), 80), "no floor, no lobby");
    }

    @Test
    void theMapKeepsItsWaitingLobby() throws Exception {
        BedwarsMap map = BedwarsMap.fromYaml("ArchwayFFA", "actif: true\n"
                + "salle-attente: {centre: {x: 0.5, z: 0.5}, y: 117, rayon: 11}\n");
        BedwarsMap.WaitingLobby lobby = map.waitingLobby();
        assertNotNull(lobby);
        assertEquals(117, lobby.floorY);
        assertEquals(11.0, lobby.radius, 1e-9);
        BedwarsMap read = BedwarsMap.fromYaml(map.world(), map.toYaml());
        assertEquals(0.5, read.waitingLobby().centreX, 1e-9, "written and read back");
        assertEquals(11.0, read.waitingLobby().radius, 1e-9);
        assertEquals("floor at y 117, 11 blocks around 0.5 0.5", lobby.toString());
        assertNull(BedwarsMap.fromYaml("m", "actif: true\n").waitingLobby(), "none until set");
        assertNull(BedwarsMap.fromYaml("m", map.copy().toYaml().replaceAll("(?s)salle-attente:.*", "")).waitingLobby());
        IllegalArgumentException radius = assertThrows(IllegalArgumentException.class,
                () -> BedwarsMap.fromYaml("m", "salle-attente: {centre: {x: 0, z: 0}, y: 117, rayon: 0}\n"));
        assertTrue(radius.getMessage().startsWith("salle-attente.rayon"), radius.getMessage());
        IllegalArgumentException centre = assertThrows(IllegalArgumentException.class,
                () -> BedwarsMap.fromYaml("m", "salle-attente: {y: 117, rayon: 11}\n"));
        assertTrue(centre.getMessage().startsWith("salle-attente.centre"), centre.getMessage());
        IllegalArgumentException height = assertThrows(IllegalArgumentException.class,
                () -> BedwarsMap.fromYaml("m", "salle-attente: {centre: {x: 0, z: 0}, rayon: 11}\n"));
        assertTrue(height.getMessage().startsWith("salle-attente.y"), height.getMessage());
    }

    @Test
    void theDuelStartsFaceToFaceAsFarAsNothingIsBetween() {
        LobbyFight fight = new LobbyFight(BedwarsMap.WaitingLobby.around(roundFloor(), 117), null, posts());
        double[][] starts = fight.starts();
        // at 8 and 7 blocks the posts at 6 are between them, at 6 they stand on a post: 5 blocks out
        assertEquals(-4.5, starts[0][0], 1e-9, "west of the middle (0.5)");
        assertEquals(0.5, starts[0][1], 1e-9);
        assertEquals(5.5, starts[1][0], 1e-9, "east of it");
        assertEquals(0.5, starts[1][1], 1e-9);
        assertEquals(-90f, (float) starts[0][2], 1e-3, "the first looks east, at the other");
        assertEquals(90f, (float) starts[1][2], 1e-3, "the second west");
        Location first = fight.start(0, 0, 1);
        assertEquals(-4.5, first.getX(), 1e-9);
        assertEquals(118, first.getY(), 1e-9, "on the floor");
        assertEquals(-90f, first.getYaw(), 1e-3);
        Location mate = fight.start(1, 1, 2);
        assertEquals(5.5, mate.getX(), 1e-9);
        assertEquals(1.25, mate.getZ(), 1e-9, "teammates side by side across the line");
    }

    @Test
    void withoutAClearLineTheyStartAtTheTwoFarthestPlaces() {
        // a wall across the whole floor at x 0 and z 0: no clear line through the middle
        LobbyFight.Blocks walls = new LobbyFight.Blocks() {
            @Override
            public boolean solid(int x, int y, int z) {
                return y == 117 ? x * x + z * z <= 121 : (y == 118 || y == 119) && (x == 0 || z == 0);
            }

            @Override
            public boolean empty(int x, int y, int z) {
                return !solid(x, y, z);
            }
        };
        LobbyFight fight = new LobbyFight(BedwarsMap.WaitingLobby.around(roundFloor(), 117), null, walls);
        double[][] starts = fight.starts();
        assertNotNull(starts);
        double dx = starts[0][0] - starts[1][0];
        double dz = starts[0][1] - starts[1][1];
        assertTrue(Math.hypot(dx, dz) > 12, "far apart: " + Math.hypot(dx, dz));
        LobbyFight none = new LobbyFight(BedwarsMap.WaitingLobby.around(roundFloor(), 117), null,
                new LobbyFight.Blocks() {
                    @Override
                    public boolean solid(int x, int y, int z) {
                        return false;
                    }

                    @Override
                    public boolean empty(int x, int y, int z) {
                        return true;
                    }
                });
        assertNull(none.starts(), "no floor to stand on");
        assertEquals(0.5, none.start(0, 0, 1).getX(), 1e-9, "the middle then");
    }

    @Test
    void thePlayersStandOnTheFloorAwayFromItsEdgeAndThePosts() {
        LobbyFight fight = new LobbyFight(BedwarsMap.WaitingLobby.around(roundFloor(), 117), null, posts());
        List<int[]> spots = fight.spots();
        assertTrue(spots.size() > 250, "most of the floor: " + spots.size());
        for (int[] spot : spots) {
            double dx = spot[0] + 0.5 - 0.5;
            double dz = spot[1] + 0.5 - 0.5;
            assertTrue(Math.hypot(dx, dz) <= 9.5 + 1e-9, "a block and a half in from the edge");
            assertFalse((Math.abs(spot[0]) == 6 && spot[1] == 0) || (spot[0] == 0 && Math.abs(spot[1]) == 6), "not on a post");
        }
        assertEquals(114, fight.fallLine(), 1e-9, "dead 3 blocks under the floor");
        assertEquals(127, fight.watchPoint().getY(), 1e-9, "the dead watch from above");
        assertEquals(90f, fight.watchPoint().getPitch(), 1e-3, "looking down");
    }

    @Test
    void theBlocksGoInTheLobbyOnly() {
        LobbyFight fight = new LobbyFight(BedwarsMap.WaitingLobby.around(roundFloor(), 117), null, posts());
        assertNull(fight.cannotBuildAt(3, 118, 3), "on the floor");
        assertNull(fight.cannotBuildAt(10, 123, 0), "up to 6 above it");
        assertEquals("Build height limit reached!", fight.cannotBuildAt(0, 124, 0));
        assertEquals("You can only place blocks in the lobby!", fight.cannotBuildAt(12, 118, 0), "past its edge");
        assertEquals("You can only place blocks in the lobby!", fight.cannotBuildAt(0, 116, 0), "under its floor");
    }

    @Test
    void theKitIsTheFfas() {
        List<ItemStack> full = FfaGame.fightKit(true);
        assertEquals(7, full.size());
        assertEquals(Material.STONE_SWORD, full.get(0).getType());
        assertEquals(64, full.get(1).getAmount(), "a stack of golden apples");
        assertEquals(3, full.get(2).getAmount(), "3 fireballs");
        assertEquals(Material.ENDER_PEARL, full.get(3).getType());
        assertEquals(2, FfaGame.fightKit(false).size(), "on the top bridge: the sword and the apples");
        assertEquals(-90f, LobbyFight.yaw(1, 0), 1e-3, "east");
        assertEquals(0f, LobbyFight.yaw(0, 1), 1e-3, "south");
        assertEquals(180f, Math.abs(LobbyFight.yaw(0, -1)), 1e-3, "north");
    }

    /** The kit of the Lobby Fight FFA: a stone sword, 5 golden apples and 3 fireballs, in the first slots. */
    private static ItemStack[] lobbyFfaKit() {
        ItemStack[] kit = new ItemStack[36];
        kit[0] = new ItemStack(Material.STONE_SWORD);
        kit[1] = new ItemStack(Material.GOLDEN_APPLE, 5);
        kit[2] = new ItemStack(Material.FIREBALL, 3);
        return kit;
    }

    private static ItemStack[] copy(ItemStack[] stacks) {
        ItemStack[] copy = new ItemStack[stacks.length];
        for (int slot = 0; slot < stacks.length; slot++) {
            copy[slot] = stacks[slot] == null ? null : stacks[slot].clone();
        }
        return copy;
    }

    @Test
    void aKillBringsTheGoldenApplesBackToTheKits() {
        ItemStack[] kit = lobbyFfaKit();
        assertEquals(5, FfaGame.count(kit, Material.GOLDEN_APPLE));
        assertEquals(0, FfaGame.count(null, Material.GOLDEN_APPLE), "no kit");

        // 2 eaten: the stack held grows back to 5, the same stack (eating it goes on)
        ItemStack[] contents = copy(kit);
        contents[1].setAmount(3);
        ItemStack held = contents[1];
        assertTrue(FfaGame.refill(contents, kit, Material.GOLDEN_APPLE).isEmpty(), "no new stack");
        assertSame(held, contents[1]);
        assertEquals(5, held.getAmount());
        assertEquals(3, contents[2].getAmount(), "the fireballs as they were");

        // all 5 eaten: back in their slot of the kit
        contents = copy(kit);
        contents[1] = null;
        assertEquals(Collections.singletonList(1), FfaGame.refill(contents, kit, Material.GOLDEN_APPLE));
        assertEquals(Material.GOLDEN_APPLE, contents[1].getType());
        assertEquals(5, contents[1].getAmount());

        // the apples moved to slot 7, one left: it grows there, no second stack
        contents = copy(kit);
        contents[1] = null;
        contents[7] = new ItemStack(Material.GOLDEN_APPLE, 1);
        assertTrue(FfaGame.refill(contents, kit, Material.GOLDEN_APPLE).isEmpty());
        assertEquals(5, contents[7].getAmount());
        assertNull(contents[1]);

        // none left and the fireballs moved into their slot: the first empty slot
        contents = copy(kit);
        contents[1] = contents[2];
        contents[2] = null;
        assertEquals(Collections.singletonList(2), FfaGame.refill(contents, kit, Material.GOLDEN_APPLE));
        assertEquals(5, contents[2].getAmount());
        assertEquals(Material.FIREBALL, contents[1].getType());

        // as many as the kit (or more): nothing changes
        contents = copy(kit);
        assertTrue(FfaGame.refill(contents, kit, Material.GOLDEN_APPLE).isEmpty());
        assertEquals(5, contents[1].getAmount());
        contents[1].setAmount(9);
        assertTrue(FfaGame.refill(contents, kit, Material.GOLDEN_APPLE).isEmpty());
        assertEquals(9, contents[1].getAmount(), "never fewer");

        // no kit, or a kit without apples: nothing
        contents = copy(kit);
        contents[1] = null;
        assertTrue(FfaGame.refill(contents, null, Material.GOLDEN_APPLE).isEmpty());
        ItemStack[] noApples = copy(kit);
        noApples[1] = null;
        assertTrue(FfaGame.refill(contents, noApples, Material.GOLDEN_APPLE).isEmpty());
        assertEquals(Arrays.asList(null, null), Arrays.asList(contents[1], contents[3]), "nothing put");
    }
}
