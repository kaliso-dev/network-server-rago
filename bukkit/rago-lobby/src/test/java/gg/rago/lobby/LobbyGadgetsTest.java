package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** The fireball and the teleport stick of the lobby hotbar. */
class LobbyGadgetsTest {

    /** A world of air with these solid blocks. */
    private static Predicate<int[]> airBut(int[]... solid) {
        Set<String> blocks = new HashSet<>();
        for (int[] block : solid) {
            blocks.add(block[0] + "," + block[1] + "," + block[2]);
        }
        return place -> !blocks.contains(place[0] + "," + place[1] + "," + place[2]);
    }

    /** The blocks along x from (x1, y, z), one by one. */
    private static List<int[]> alongX(int x1, int x2, int y, int z) {
        List<int[]> ray = new ArrayList<>();
        for (int x = x1; x <= x2; x++) {
            ray.add(new int[] {x, y, z});
        }
        return ray;
    }

    @Test
    void theStickLandsOnTopOfTheBlockLookedAt() {
        // looking down a slope onto the floor block (5, 0, 0)
        List<int[]> ray = new ArrayList<>();
        ray.add(new int[] {0, 2, 0});
        ray.add(new int[] {2, 1, 0});
        ray.add(new int[] {4, 1, 0});
        ray.add(new int[] {5, 0, 0});
        assertArrayEquals(new int[] {5, 1, 0}, LobbyGadgets.destination(ray, airBut(new int[] {5, 0, 0})));
    }

    @Test
    void theStickNeverGoesThroughAWall() {
        // a wall three high at x = 6, a free floor beyond it: the player stays on this side
        Predicate<int[]> world = airBut(new int[] {6, 1, 0}, new int[] {6, 2, 0}, new int[] {6, 3, 0}, new int[] {12, 1, 0});
        int[] feet = LobbyGadgets.destination(alongX(1, 20, 2, 0), world);
        assertArrayEquals(new int[] {5, 2, 0}, feet, "in front of the wall");
        assertTrue(feet[0] < 6);
    }

    @Test
    void underACeilingThePlayerStaysUnderIt() {
        // looking straight up at a ceiling at y 10: never on top of it
        List<int[]> ray = new ArrayList<>();
        for (int y = 2; y <= 12; y++) {
            ray.add(new int[] {0, y, 0});
        }
        assertArrayEquals(new int[] {0, 8, 0}, LobbyGadgets.destination(ray, airBut(new int[] {0, 10, 0})));
    }

    @Test
    void nothingInReachMeansNoTeleport() {
        assertNull(LobbyGadgets.destination(alongX(1, 20, 2, 0), airBut()));
        // the eyes in a block: nowhere to go
        assertNull(LobbyGadgets.destination(alongX(1, 3, 2, 0), airBut(new int[] {1, 2, 0})));
    }

    @Test
    void aFireballUnderTheFeetThrowsUp() {
        double[] jump = LobbyGadgets.push(0, 0, 0, 0, 0.5, 0);
        assertEquals(0, jump[0], 1e-9);
        assertEquals(0, jump[2], 1e-9);
        assertTrue(jump[1] > 0.9, "a fireball jump: " + jump[1]);
        double[] side = LobbyGadgets.push(0, 1, 0, 2, 1, 0);
        assertEquals(LobbyGadgets.HORIZONTAL, side[0], 1e-9, "away from the blast");
        assertTrue(LobbyGadgets.reaches(2, 1, -3));
        assertTrue(!LobbyGadgets.reaches(3.5, 0, 0));
    }
}
