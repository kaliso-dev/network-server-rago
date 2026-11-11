package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import gg.rago.game.BedDefense;
import org.junit.jupiter.api.Test;

class ClutchScenarioGameTest {

    @Test
    void ironArmourAndTheDuoDefense() {
        assertEquals(2, ClutchScenarioGame.ARMOUR_TIER, "0 leather, 1 chainmail, 2 iron");
        assertSame(BedDefense.DUO, ClutchScenarioGame.DEFENSE, "the owner's duo defense: end stone, then wool");
        assertEquals(3, ClutchScenarioGame.DIAMOND_TIER, "a game half played: Diamond III...");
        assertEquals(2, ClutchScenarioGame.EMERALD_TIER, "...and Emerald II");
    }
}
