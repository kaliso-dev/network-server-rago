package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The KB displace of a Bed Fight top fight: the top of the defense, the roles, knocked off the top. */
class KbDisplaceTest {

    private static Set<String> defense() {
        Set<String> places = new HashSet<>();
        for (List<int[]> layer : Shapes.defense(0, 1, 0, 1, 0, 3)) {
            for (int[] place : layer) {
                places.add(place[0] + "," + place[1] + "," + place[2]);
            }
        }
        return places;
    }

    @Test
    void theBoxedPlayerStandsOnTheTopOfTheDefense() {
        Set<String> defense = defense();
        // the two blocks under their feet are the top of the defense, nothing above them
        assertTrue(defense.contains("0," + (KbDisplace.TOP - 1) + ",0"));
        assertTrue(defense.contains("1," + (KbDisplace.TOP - 1) + ",0"));
        for (int y = KbDisplace.TOP; y <= KbDisplace.TOP + 2; y++) {
            for (int x = -1; x <= 2; x++) {
                assertFalse(defense.contains(x + "," + y + ",0"), "the box is free at " + x + "," + y);
            }
        }
        // open on the sides: one step down, as on a real defense
        assertFalse(defense.contains("0," + (KbDisplace.TOP - 1) + ",1"));
        assertTrue(defense.contains("0," + (KbDisplace.TOP - 2) + ",1"));
    }

    @Test
    void theAttackerStartsAtTheFootOfTheDefense() {
        Set<String> defense = defense();
        for (int x = 0; x <= 1; x++) {
            assertFalse(defense.contains(x + ",1,4"), "free where the attacker stands");
        }
    }

    @Test
    void oneStepDownIsOffTheTop() {
        assertFalse(KbDisplace.offTheTop(KbDisplace.TOP));
        assertFalse(KbDisplace.offTheTop(KbDisplace.TOP - 0.5), "a jump that lands back");
        assertTrue(KbDisplace.offTheTop(KbDisplace.TOP - 1));
    }

    @Test
    void theRolesSwapEachRound() {
        assertEquals(0, KbDisplace.attackerOf(1));
        assertEquals(1, KbDisplace.attackerOf(2));
        assertEquals(0, KbDisplace.attackerOf(3));
    }
}
