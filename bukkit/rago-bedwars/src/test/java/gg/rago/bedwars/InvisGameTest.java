package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** The invis practice: where the attacker starts, the defense of the bed. */
class InvisGameTest {

    @Test
    void theAttackerStartsAtTheMidInFrontOfTheBaseToAttack() {
        MapPoint north = new MapPoint(0.5, 75, -10.5, 0f, 0f);
        MapPoint south = new MapPoint(0.5, 75, 11.5, 0f, 0f);
        MapPoint southBed = new MapPoint(0, 83, 79, 0f, 0f);
        MapPoint northBed = new MapPoint(0, 83, -79, 0f, 0f);
        assertEquals(south, InvisGame.inFrontOf(Arrays.asList(north, south), southBed));
        assertEquals(north, InvisGame.inFrontOf(Arrays.asList(south, north), northBed), "whatever their order");
        MapPoint only = new MapPoint(0.5, 74, 0.5, 0f, 0f);
        assertEquals(only, InvisGame.inFrontOf(Collections.singletonList(only), southBed), "one mid: that one");
        assertNull(InvisGame.inFrontOf(Collections.<MapPoint>emptyList(), southBed));
    }

    @Test
    void theBedHasTheDefenseOfARankedGame() {
        assertArrayEquals(new Material[] {Material.ENDER_STONE, Material.STAINED_GLASS, Material.WOOD},
                InvisGame.DEFENSE, "end stone on the bed, then glass and wood: no wool outside");
        assertEquals(180f, Math.abs(InvisGame.yawTowards(0, -1)), 1e-3, "north");
        assertEquals(0f, InvisGame.yawTowards(0, 1), 1e-3, "south");
    }
}
