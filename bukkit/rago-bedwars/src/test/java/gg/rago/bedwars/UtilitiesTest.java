package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The special items of the shop: the Pop-up Tower, the Bridge Egg, what the shops sell. */
class UtilitiesTest {

    @Test
    void thePopUpTowerHasItsWallsItsLadderAndItsTop() {
        List<Utilities.Piece> tower = Utilities.towerLayout();
        Set<String> cells = new HashSet<>();
        int ladders = 0;
        for (Utilities.Piece piece : tower) {
            assertTrue(cells.add(piece.right + "," + piece.up + "," + piece.forward), "each block once");
            if (piece.ladder) {
                ladders++;
                assertEquals(0, piece.right, "the ladder in the shaft");
                assertEquals(1, piece.forward, "against the back wall");
            }
        }
        assertEquals(Utilities.TOWER_WALLS + 1, ladders, "the ladder goes up through the top");
        assertFalse(cells.contains("0,0,-1"), "the way in");
        assertFalse(cells.contains("0,1,-1"), "the way in, two high");
        assertTrue(cells.contains("0,2,-1"), "the wall above the way in");
        for (int up = 0; up < Utilities.TOWER_WALLS; up++) {
            assertFalse(cells.contains("0," + up + ",0"), "the shaft is open");
            assertTrue(cells.contains("0," + up + ",2"), "the wall the ladder hangs on");
        }
        assertTrue(cells.contains("2," + Utilities.TOWER_WALLS + ",3"), "the top, wider than the walls");
        assertTrue(cells.contains("-2," + (Utilities.TOWER_WALLS + 1) + ",-2"), "its battlements");
        for (int i = 1; i < tower.size(); i++) {
            assertTrue(tower.get(i).up >= tower.get(i - 1).up, "built bottom to top");
        }
        assertEquals(tower.size(), Utilities.TOWER.size());
    }

    @Test
    void theTowerFacesTheWayThePlayerLooks() {
        assertArrayEquals(new int[] {0, 1}, Utilities.forward(0f), "south");
        assertArrayEquals(new int[] {-1, 0}, Utilities.forward(90f), "west");
        assertArrayEquals(new int[] {0, -1}, Utilities.forward(180f), "north");
        assertArrayEquals(new int[] {1, 0}, Utilities.forward(-90f), "east");
        assertArrayEquals(new int[] {0, 1}, Utilities.forward(359f));
        assertArrayEquals(new int[] {0, 1}, Utilities.forward(44f));
        assertArrayEquals(new int[] {-1, 0}, Utilities.forward(46f));
        // the ladder faces the player: on the wall in front of them
        assertEquals(2, Utilities.ladderData(0, 1));
        assertEquals(3, Utilities.ladderData(0, -1));
        assertEquals(4, Utilities.ladderData(1, 0));
        assertEquals(5, Utilities.ladderData(-1, 0));
    }

    @Test
    void noBlockGoesIntoAPlayer() {
        assertTrue(Utilities.overlaps(0.5, 64, 0.5, 0, 64, 0), "their feet");
        assertTrue(Utilities.overlaps(0.5, 64, 0.5, 0, 65, 0), "their head");
        assertFalse(Utilities.overlaps(0.5, 64, 0.5, 0, 66, 0), "above them");
        assertFalse(Utilities.overlaps(0.5, 64, 0.5, 1, 64, 0), "beside them");
        assertTrue(Utilities.overlaps(0.9, 64, 0.5, 1, 64, 0), "half in the next block");
        assertFalse(Utilities.overlaps(0.5, 64, 0.5, 0, 63, 0), "the block they stand on");
        for (int[] offset : Utilities.EGG_BLOCKS) {
            assertEquals(-2, offset[1], "the Bridge Egg builds under its flight");
        }
    }

    @Test
    void theSpecialItemsAreSold() {
        ShopCatalog shop = ShopCatalog.standard();
        for (ShopItem item : shop.all()) {
            assertTrue(item.kind() != ShopItem.Kind.COMING_SOON, item.id() + " is sold");
        }
        for (String id : new String[] {"bedbug", "dream_defender", "bridge_egg", "milk", "tower"}) {
            assertEquals(ShopItem.Kind.UTILITY, shop.byId(id).kind(), id);
        }
        assertEquals("milk", shop.forHypixel("magic_milk").id());
        assertEquals("tower", shop.forHypixel("compact_pop-up_tower").id());
        // the arena sells those that help a fight, not those of a base
        ShopCatalog arena = ShopCatalog.arena();
        assertNotNull(arena.byId("bridge_egg"));
        assertNotNull(arena.byId("bedbug"));
        assertNotNull(arena.byId("tower"));
        assertNull(arena.byId("milk"));
        assertNull(arena.byId("dream_defender"));
    }
}
