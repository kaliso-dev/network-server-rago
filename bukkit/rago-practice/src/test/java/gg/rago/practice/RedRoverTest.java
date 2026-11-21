package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Red Rover: who fights whom, how the game is won, its settings. */
class RedRoverTest {

    private static RedRoverRotation<String> rotation(RedRoverRotation.Variant variant, RedRoverRotation.Stays stays, int target) {
        return new RedRoverRotation<>(Arrays.asList("A", "B", "C"), variant, stays, target);
    }

    @Test
    void theFirstTwoFightTheThirdWaits() {
        RedRoverRotation<String> game = rotation(RedRoverRotation.Variant.POINTS, RedRoverRotation.Stays.WINNER, 3);
        assertEquals(Arrays.asList("A", "B"), game.fight());
        assertEquals(Collections.singletonList("C"), game.waiting());
        assertThrows(IllegalArgumentException.class, () -> game.won("C"), "C does not fight");
        assertThrows(IllegalArgumentException.class, () -> new RedRoverRotation<>(Collections.singletonList("A"),
                RedRoverRotation.Variant.POINTS, RedRoverRotation.Stays.WINNER, 3));
    }

    @Test
    void theWinnerStaysAndFightsTheOneWhoWaited() {
        RedRoverRotation<String> game = rotation(RedRoverRotation.Variant.POINTS, RedRoverRotation.Stays.WINNER, 3);
        assertNull(game.won("A"));
        assertEquals(Arrays.asList("A", "C"), game.fight(), "A stays on, C comes in");
        assertEquals(Collections.singletonList("B"), game.waiting(), "B waits");
        assertNull(game.won("C"));
        assertEquals(Arrays.asList("C", "B"), game.fight());
        assertEquals(Collections.singletonList("A"), game.waiting());
        assertEquals(1, game.wins("A"));
        assertEquals(1, game.wins("C"));
        assertEquals(0, game.wins("B"));
    }

    @Test
    void theLoserStaysWhenTheSettingsSaySo() {
        RedRoverRotation<String> game = rotation(RedRoverRotation.Variant.POINTS, RedRoverRotation.Stays.LOSER, 3);
        assertNull(game.won("A"));
        assertEquals(Arrays.asList("B", "C"), game.fight(), "B stays on, C comes in");
        assertEquals(Collections.singletonList("A"), game.waiting(), "A, who won, waits");
    }

    @Test
    void theFirstToTheTargetWinsTheGame() {
        RedRoverRotation<String> game = rotation(RedRoverRotation.Variant.POINTS, RedRoverRotation.Stays.WINNER, 2);
        assertNull(game.won("A"));
        assertEquals("A", game.won("A"), "A won 2 1v1");
        assertTrue(game.fight().isEmpty(), "the game is over");
        assertEquals(2, game.wins("A"));
    }

    @Test
    void inAnEliminationTheLoserIsOutAndTheLastOneInWins() {
        RedRoverRotation<String> game = rotation(RedRoverRotation.Variant.ELIMINATION, RedRoverRotation.Stays.LOSER, 3);
        assertNull(game.won("B"));
        assertFalse(game.isIn("A"), "A lost: out");
        assertEquals(Arrays.asList("B", "C"), game.fight(), "the winner stays on, whatever the settings say");
        assertTrue(game.waiting().isEmpty());
        assertEquals("C", game.won("C"));
        assertEquals(Collections.singletonList("C"), game.players());
    }

    @Test
    void oneWhoLeavesIsOutTheOtherFighterStaysOn() {
        RedRoverRotation<String> game = rotation(RedRoverRotation.Variant.POINTS, RedRoverRotation.Stays.WINNER, 3);
        game.won("A");
        assertNull(game.left("C"), "two are left");
        assertEquals(Arrays.asList("A", "B"), game.fight(), "B, who waited, comes in");
        assertTrue(game.waiting().isEmpty());
        assertNull(game.won("B"));
        assertEquals(Arrays.asList("B", "A"), game.fight(), "two left: the same two again");
        assertEquals("B", game.left("A"), "the last one in wins");
        assertNull(game.left("A"), "gone already");
        assertEquals(1, game.scores().get("A").intValue(), "the 1v1 won of one who left stay on the board");
    }

    @Test
    void oneWhoWaitsLeaves() {
        RedRoverRotation<String> game = rotation(RedRoverRotation.Variant.ELIMINATION, RedRoverRotation.Stays.WINNER, 3);
        assertNull(game.left("C"));
        assertEquals(Arrays.asList("A", "B"), game.fight());
        assertEquals("B", game.won("B"));
    }

    @Test
    void fourPlayersTakeTheirTurn() {
        RedRoverRotation<String> game = new RedRoverRotation<>(Arrays.asList("A", "B", "C", "D"),
                RedRoverRotation.Variant.POINTS, RedRoverRotation.Stays.WINNER, 5);
        game.won("A");
        assertEquals(Arrays.asList("A", "C"), game.fight());
        assertEquals(Arrays.asList("D", "B"), game.waiting(), "the one who waited longest comes in first");
    }

    @Test
    void checksItsSettings() {
        assertNull(RedRover.Settings.check("points", "gagnant", 3, 90));
        assertNull(RedRover.Settings.check("elimination", "perdant", 1, 0));
        assertTrue(RedRover.Settings.check("both", "gagnant", 3, 90).startsWith("variante"));
        assertTrue(RedRover.Settings.check("points", "nobody", 3, 90).startsWith("reste"));
        assertTrue(RedRover.Settings.check("points", "gagnant", 0, 90).startsWith("points-pour-gagner"));
        assertTrue(RedRover.Settings.check("points", "gagnant", 11, 90).startsWith("points-pour-gagner"));
        assertTrue(RedRover.Settings.check("points", "gagnant", 3, -1).startsWith("secondes-par-1v1"));
        assertEquals(RedRoverRotation.Variant.ELIMINATION, RedRover.Settings.variantOf("Elimination"));
        assertEquals(RedRoverRotation.Stays.LOSER, RedRover.Settings.staysOf("perdant"));
        assertEquals("First to 3", RedRover.goal(RedRoverRotation.Variant.POINTS, 3));
        assertEquals("Elimination", RedRover.goal(RedRoverRotation.Variant.ELIMINATION, 3));
    }

    @Test
    void theBundledSettingsAreAccepted() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertNotNull(config.getConfigurationSection("red-rover"));
        assertNull(RedRover.Settings.check(config.getString("red-rover.variante"), config.getString("red-rover.reste"),
                config.getInt("red-rover.points-pour-gagner"), config.getInt("red-rover.secondes-par-1v1")));
    }
}
