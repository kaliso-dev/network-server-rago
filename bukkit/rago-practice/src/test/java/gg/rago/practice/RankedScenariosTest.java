package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** The bed defense of the ranked scenarios: where the rushers dig, from where, and how long it takes. */
class RankedScenariosTest {

    private static final List<List<int[]>> LAYERS = Shapes.defense(RankedScenarios.BED_X, RankedScenarios.BED_Y, 0, -1, 0,
            RankedScenarios.DEFENSE.length);

    private static String key(int[] place) {
        return place[0] + "," + place[1] + "," + place[2];
    }

    private static int[] dig(Set<String> open, double x, double y, double z) {
        return RankedScenarios.tunnel(LAYERS, place -> !open.contains(key(place)), x, y, z);
    }

    @Test
    void aRusherDigsItsWayInFromWhereItStands() {
        Set<String> open = new HashSet<>();
        // come from the bridge, in front of the bed: the wool there, the end stone behind it, then the bed
        int[] first = dig(open, -4.0, 2.0, 0.5);
        assertEquals("-6,1,0", key(first), "the wool in front");
        open.add(key(first));
        int[] second = dig(open, -4.0, 2.0, 0.5);
        assertEquals("-7,1,0", key(second), "then the end stone behind it");
        open.add(key(second));
        assertNull(dig(open, -4.0, 2.0, 0.5), "then the bed");
        // a block put back in the hole has to be dug again
        open.remove("-6,1,0");
        assertEquals("-6,1,0", key(dig(open, -4.0, 2.0, 0.5)));
    }

    @Test
    void fromTheSideOrFromAboveItDigsThatSide() {
        Set<String> none = new HashSet<>();
        assertEquals(2, dig(none, -8.0, 2.0, 4.3)[2], "the wool beside the bed");
        assertEquals(3, dig(none, -8.0, 5.0, 0.5)[1], "the wool on top of it");
    }

    @Test
    void aRusherDigsOnlyAtTheBed() {
        assertTrue(RankedScenarios.atBed(-4.7, 2, 0.5), "stopped by the wool in front");
        assertTrue(RankedScenarios.atBed(-8.0, 2, 4.3), "beside it");
        assertTrue(RankedScenarios.atBed(-8.0, 5, 0.5), "on top of it");
        assertFalse(RankedScenarios.atBed(RankedScenarios.BRIDGE_FROM + 0.5, 2, 0.5), "on the bridge");
        assertFalse(RankedScenarios.atBed(-3.5, 2, 0.5), "where the player starts");
    }

    @Test
    void theDefenseHoldsAboutTwoSecondsAgainstOneRusher() {
        int front = RankedScenarios.digTicks(Material.WOOL) + RankedScenarios.digTicks(Material.ENDER_STONE)
                + RankedScenarios.digTicks(Material.BED_BLOCK);
        assertTrue(front >= 35 && front <= 50, front + " ticks");
        assertTrue(RankedScenarios.digTicks(Material.ENDER_STONE) > RankedScenarios.digTicks(Material.WOOL));
    }

    @Test
    void theDefenseStandsOnTheIslandAwayFromTheStart() {
        assertEquals(Material.ENDER_STONE, RankedScenarios.DEFENSE[0]);
        assertEquals(Material.WOOL, RankedScenarios.DEFENSE[1]);
        assertEquals(RankedScenarios.ISLAND_EDGE + 1, RankedScenarios.BRIDGE_FROM, "the bridge starts at the island");
        int places = 0;
        for (List<int[]> layer : LAYERS) {
            for (int[] place : layer) {
                places++;
                assertTrue(place[0] >= -14 && place[0] <= -6, "on the island, west of the start: " + key(place));
                assertTrue(Math.abs(place[2]) <= 5, key(place));
            }
        }
        assertEquals(26, places);
    }
}
