package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NextGameTest {

    @Test
    void playAgainQueuesTheSameModeAndRematchChallengesTheSameOpponent() {
        String json = NextGame.json("sumo", true, "Sumo", "Bob");
        assertTrue(json.startsWith("[\"\","), json);
        assertTrue(json.contains("\"text\":\"[PLAY AGAIN]\""), json);
        assertTrue(json.contains("\"value\":\"/queue sumo\""), json);
        assertTrue(json.contains("\"value\":\"Queue Sumo again\""), json);
        assertTrue(json.contains("\"text\":\"[REMATCH]\""), json);
        assertTrue(json.contains("\"value\":\"/duel Bob sumo\""), json);
        assertTrue(json.indexOf("[PLAY AGAIN]") < json.indexOf("[REMATCH]"));
    }

    @Test
    void eachButtonComesAloneWhenTheOtherDoesNotApply() {
        String again = NextGame.json("ranked-2v2", true, "Ranked 2v2", null);
        assertTrue(again.contains("/queue ranked-2v2"));
        assertFalse(again.contains("REMATCH"), "no rematch out of a 1v1");
        String rematch = NextGame.json("sumo", false, "Sumo", "Bob");
        assertFalse(rematch.contains("PLAY AGAIN"), "a party member does not queue the party, nor one back in the queue");
        assertTrue(rematch.contains("\"value\":\"/duel Bob sumo\""), "the same mode again: " + rematch);
        assertNull(NextGame.json("sumo", false, "Sumo", null));
    }

    @Test
    void theNamesOfTheModesCannotBreakTheJson() {
        assertEquals("Bed \\\"Wars\\\" \\\\ 1v1", NextGame.escape("Bed \"Wars\" \\ 1v1\n"));
        String json = NextGame.json("x", true, "A \"quoted\" mode", null);
        assertTrue(json.contains("Queue A \\\"quoted\\\" mode again"), json);
    }
}
