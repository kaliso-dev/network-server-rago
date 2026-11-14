package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.game.BedDefense;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClutchScenarioGameTest {

    @Test
    void ironArmourAndTheDuoDefense() {
        assertEquals(2, ClutchScenarioGame.ARMOUR_TIER, "0 leather, 1 chainmail, 2 iron");
        assertSame(BedDefense.DUO, ClutchScenarioGame.DEFENSE, "the owner's duo defense: end stone, then wool");
        assertEquals(3, ClutchScenarioGame.DIAMOND_TIER, "a game half played: Diamond III...");
        assertEquals(2, ClutchScenarioGame.EMERALD_TIER, "...and Emerald II");
    }

    private static Set<String> places(List<int[]> places) {
        Set<String> set = new HashSet<>();
        for (int[] place : places) {
            set.add(place[0] + " " + place[1] + " " + place[2]);
        }
        return set;
    }

    /**
     * The bedless team's defense: the duo one, with the hole of the attacker who broke the bed,
     * one block wide and two high from the side of mid to the bed, the blocks over the bed kept.
     */
    @Test
    void theBedlessTeamsDefenseHasTheHoleOfTheAttacker() {
        // the head towards +z, mid on that side: through the end stone and wool past the head
        List<int[]> whole = BedDefense.places(BedDefense.DUO, 0, 1);
        assertEquals(35, whole.size(), "12 end stone, 23 wool");
        Set<String> broken = places(Defense.broken(whole, 0, 1, 3.0, 40.0));
        assertEquals(30, broken.size());
        for (String dug : new String[] {"0 0 2", "0 0 3", "0 0 4", "0 1 2", "0 1 3"}) {
            assertFalse(broken.contains(dug), dug);
        }
        assertTrue(broken.contains("0 1 1"), "the end stone over the head stays");
        assertTrue(broken.contains("0 2 2"), "the wool over the hole stays");
        assertTrue(broken.contains("1 0 2"), "the end stone beside the hole stays");

        // mid on the side (-x), a little towards the head: from the side of the bed, to the head
        Set<String> side = places(Defense.broken(whole, 0, 1, -40.0, 2.0));
        assertEquals(32, side.size());
        for (String dug : new String[] {"-1 0 1", "-2 0 1", "-1 1 1"}) {
            assertFalse(side.contains(dug), dug);
        }
        assertTrue(side.contains("-1 0 0"), "beside the foot, it stands");

        // mid behind the foot: from the foot's end
        Set<String> back = places(Defense.broken(whole, 0, 1, 0.0, -40.0));
        for (String dug : new String[] {"0 0 -1", "0 0 -2", "0 1 -1"}) {
            assertFalse(back.contains(dug), dug);
        }
        assertTrue(back.contains("0 1 0"), "the end stone over the foot stays");
        assertEquals(32, back.size());
    }
}
