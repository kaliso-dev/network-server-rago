package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NpcSpotTest {

    /** Blocks by position: 1 a full block, 0.5 a bottom slab; nothing is air. */
    private static final class Grid implements NpcSpot.Blocks {
        final Map<String, Double> blocks = new HashMap<>();

        Grid put(int x, int y, int z, double height) {
            blocks.put(x + "," + y + "," + z, height);
            return this;
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return blocks.containsKey(x + "," + y + "," + z);
        }

        @Override
        public double height(int x, int y, int z) {
            Double height = blocks.get(x + "," + y + "," + z);
            return height == null ? 0 : height;
        }
    }

    /**
     * The upgrades niche of Sky Rise's red island, as the map has it: a floor at y 70, the niche at
     * x -42 (z -94 to -92) walled at x -43 and z -95 and -91, a slab in front of it at x -41 under a
     * line of barriers at y 73.
     */
    private static Grid skyRise() {
        Grid grid = new Grid();
        for (int x = -44; x <= -36; x++) {
            for (int z = -96; z <= -88; z++) {
                grid.put(x, 70, z, 1);
            }
        }
        for (int z = -94; z <= -92; z++) {
            grid.put(-41, 71, z, 0.5).put(-41, 73, z, 1).put(-42, 74, z, 1);
            for (int y = 71; y <= 74; y++) {
                grid.put(-43, y, z, 1);
            }
        }
        for (int y = 71; y <= 74; y++) {
            grid.put(-42, y, -95, 1).put(-42, y, -91, 1);
        }
        return grid;
    }

    @Test
    void aVillagerUnderTheGuardGoesIntoItsNiche() {
        // the point the map tool found: on the slab, under the barriers
        NpcSpot spot = NpcSpot.fit(skyRise(), -40.5, 71.5, -92.5, 315f, -35.5, -89.5);
        assertTrue(spot.moved);
        assertEquals(-41.5, spot.x, 1e-9);
        assertEquals(71.0, spot.y, 1e-9);
        assertEquals(-92.5, spot.z, 1e-9);
        assertEquals(270f, spot.yaw, "facing east, out of the niche");
    }

    @Test
    void aVillagerWithRoomStaysWhereItIs() {
        NpcSpot spot = NpcSpot.fit(skyRise(), -41.5, 71.0, -92.5, 270f, -35.5, -89.5);
        assertFalse(spot.moved);
        assertEquals(-41.5, spot.x, 1e-9);
        assertEquals(270f, spot.yaw);
        // in the open, a 45 degree point turns to a quarter
        spot = NpcSpot.fit(skyRise(), -38.5, 71.0, -90.5, 315f, -35.5, -89.5);
        assertFalse(spot.moved);
        assertEquals(0f, spot.yaw);
    }

    @Test
    void withoutANicheTheVillagerStays() {
        Grid grid = new Grid();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                grid.put(x, 70, z, 1);
            }
        }
        grid.put(0, 72, 0, 1);
        NpcSpot spot = NpcSpot.fit(grid, 0.5, 71, 0.5, 90f, 5, 5);
        assertFalse(spot.moved, "open all round: no niche to go to");
        assertEquals(90f, spot.yaw);
    }

    @Test
    void quarters() {
        assertEquals(0f, NpcSpot.quarter(-30f));
        assertEquals(90f, NpcSpot.quarter(45f));
        assertEquals(0f, NpcSpot.quarter(359f));
        assertEquals(180f, NpcSpot.yawOf(0, -1));
        assertEquals(90f, NpcSpot.yawOf(-1, 0));
    }
}
