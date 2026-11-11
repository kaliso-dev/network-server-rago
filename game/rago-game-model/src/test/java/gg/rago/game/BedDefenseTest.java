package gg.rago.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BedDefenseTest {

    private static Set<String> of(List<int[]> places, char block) {
        Set<String> set = new HashSet<>();
        for (int[] place : places) {
            if (place[3] == block) {
                set.add(place[0] + "," + place[1] + "," + place[2]);
            }
        }
        return set;
    }

    @Test
    void theDuoDefenseOfTheOwnersMap() {
        // the bed of "Defense Claude": its foot at (0, 0, 0), its head one block south (z + 1)
        List<int[]> places = BedDefense.places(BedDefense.DUO, 0, 1);
        Set<String> endStone = of(places, BedDefense.END_STONE);
        Set<String> wool = of(places, BedDefense.WOOL);
        assertEquals(12, endStone.size(), "12 end stone");
        assertEquals(23, wool.size(), "23 wool");
        assertTrue(endStone.contains("0,0,-1") && endStone.contains("0,0,2") && endStone.contains("0,0,3"),
                "in front of the foot, and two past the head");
        assertTrue(endStone.contains("0,1,0") && endStone.contains("0,1,1") && endStone.contains("0,1,2"), "on the bed");
        assertTrue(wool.contains("0,0,-2") && wool.contains("0,0,4") && wool.contains("-2,0,0") && wool.contains("2,0,2"));
        assertTrue(wool.contains("0,2,0") && wool.contains("0,2,2"), "the top");
        assertTrue(!endStone.contains("0,0,0") && !wool.contains("0,0,1"), "not on the bed itself");
    }

    @Test
    void itTurnsWithTheBed() {
        // the head one block west (x - 1): past the head is west
        Set<String> endStone = of(BedDefense.places(BedDefense.DUO, -1, 0), BedDefense.END_STONE);
        assertEquals(12, endStone.size());
        assertTrue(endStone.contains("1,0,0") && endStone.contains("-2,0,0") && endStone.contains("-3,0,0"));
        assertTrue(endStone.contains("0,0,1") && endStone.contains("0,0,-1") && endStone.contains("-2,0,1"));
    }
}
