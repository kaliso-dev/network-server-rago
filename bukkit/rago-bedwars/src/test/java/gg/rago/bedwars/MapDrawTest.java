package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MapDrawTest {

    private static final List<String> RBW = Arrays.asList("Antenna", "Katsu", "Lectus", "Invasion");

    @Test
    void theNextMapStaysTheSameUntilAGameTakesIt() {
        MapDraw draw = new MapDraw(new Random(1));
        String first = draw.next("ranked-1v1", RBW);
        assertEquals(first, draw.next("ranked-1v1", RBW));
        assertEquals(first, draw.next("ranked-1v1", RBW));
    }

    @Test
    void aGameTakesTheMapAndTheNextOneIsAnother() {
        MapDraw draw = new MapDraw(new Random(2));
        String previous = draw.next("ranked-1v1", RBW);
        for (int i = 0; i < 50; i++) {
            String next = draw.played("ranked-1v1", previous, RBW);
            assertNotEquals(previous, next);
            assertEquals(next, draw.next("ranked-1v1", RBW));
            previous = next;
        }
    }

    @Test
    void everyMapComesUp() {
        MapDraw draw = new MapDraw(new Random(3));
        Set<String> seen = new HashSet<>();
        String map = draw.next("ranked-2v2", RBW);
        for (int i = 0; i < 100; i++) {
            seen.add(map);
            map = draw.played("ranked-2v2", map, RBW);
        }
        assertEquals(new HashSet<>(RBW), seen);
    }

    @Test
    void eachModeHasItsOwnNextMap() {
        MapDraw draw = new MapDraw(new Random(4));
        String one = draw.next("ranked-1v1", RBW);
        draw.played("ranked-2v2", draw.next("ranked-2v2", RBW), RBW);
        assertEquals(one, draw.next("ranked-1v1", RBW));
    }

    @Test
    void aLoneMapIsPlayedAgainAndAMapThatLeftIsDrawnAgain() {
        MapDraw draw = new MapDraw(new Random(5));
        List<String> alone = Collections.singletonList("SkyRise");
        assertEquals("SkyRise", draw.played("skyrise-1v1", "SkyRise", alone));
        assertNull(draw.next("skyrise-1v1", Collections.<String>emptyList()));
        String next = draw.next("ranked-1v1", RBW);
        List<String> without = new java.util.ArrayList<>(RBW);
        without.remove(next);
        assertNotEquals(next, draw.next("ranked-1v1", without));
    }
}
