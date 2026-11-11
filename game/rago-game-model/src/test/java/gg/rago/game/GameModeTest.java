package gg.rago.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class GameModeTest {

    @Test
    void describesItsFormat() {
        assertEquals("1v1", new GameMode("bedwars-1v1", "Bedwars 1v1", 2, 1, true).format());
        assertEquals("2v2", new GameMode("bedwars-2v2", "Bedwars 2v2", 2, 2, false).format());
        assertEquals("1v1v1v1", new GameMode("ffa-4", "FFA", 4, 1, false).format());
        assertEquals("solo", new GameMode("pont", "Pont", 1, 1, false).format());
        assertEquals(4, new GameMode("bedwars-2v2", "Bedwars 2v2", 2, 2, false).playersPerMatch());
    }

    @Test
    void refusesBadModes() {
        assertThrows(IllegalArgumentException.class, () -> new GameMode("Bedwars", "B", 2, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GameMode("été", "B", 2, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GameMode("-x", "B", 2, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GameMode("x", " ", 2, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GameMode("x", "X", 0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new GameMode("x", "X", 2, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new GameMode("x", "X", 9, 2, false));
        assertThrows(IllegalArgumentException.class, () -> new GameMode("x", "X", 1, 1, true));
    }

    @Test
    void catalogKeepsTheOrderAndRefusesDuplicates() {
        GameMode a = new GameMode("a", "A", 2, 1, false);
        GameMode b = new GameMode("b", "B", 2, 1, false);
        ModeCatalog catalog = new ModeCatalog(Arrays.asList(b, a));
        assertEquals(Arrays.asList(b, a), catalog.all());
        assertEquals(a, catalog.get("a"));
        assertEquals(null, catalog.get("c"));
        assertThrows(IllegalArgumentException.class, () -> new ModeCatalog(Arrays.asList(a, a)));
    }

    @Test
    void ratingWindowGrowsByStepsUpToItsMaximum() {
        RatingWindow window = new RatingWindow(100, 50, 10, 300);
        assertEquals(100, window.width(0));
        assertEquals(100, window.width(9_999));
        assertEquals(150, window.width(10_000));
        assertEquals(250, window.width(30_000));
        assertEquals(300, window.width(3_600_000));
        assertEquals(100, window.width(-5));
        assertThrows(IllegalArgumentException.class, () -> new RatingWindow(100, 50, 0, 300));
        assertThrows(IllegalArgumentException.class, () -> new RatingWindow(100, 50, 10, 50));
    }
}
