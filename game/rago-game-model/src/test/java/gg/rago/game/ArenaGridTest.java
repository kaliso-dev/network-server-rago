package gg.rago.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArenaGridTest {

    @Test
    void takesTheLowestFreeSlot() {
        ArenaGrid grid = new ArenaGrid(3, 200);
        assertEquals(0, grid.acquire());
        assertEquals(1, grid.acquire());
        assertEquals(2, grid.acquire());
        assertEquals(-1, grid.acquire(), "full");
        grid.release(1);
        assertFalse(grid.inUse(1));
        assertEquals(1, grid.acquire());
        assertTrue(grid.inUse(1));
        assertEquals(3, grid.inUse());
        assertEquals(400, grid.centreX(2));
    }

    @Test
    void refusesBadUse() {
        ArenaGrid grid = new ArenaGrid(2, 200);
        assertThrows(IllegalArgumentException.class, () -> grid.release(0));
        assertThrows(IllegalArgumentException.class, () -> grid.release(5));
        assertThrows(IllegalArgumentException.class, () -> new ArenaGrid(0, 200));
        assertThrows(IllegalArgumentException.class, () -> new ArenaGrid(2, 50));
    }
}
