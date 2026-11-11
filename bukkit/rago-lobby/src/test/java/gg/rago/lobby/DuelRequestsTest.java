package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.game.GameMode;
import org.junit.jupiter.api.Test;

class DuelRequestsTest {

    @Test
    void duelsAreOneAgainstOne() {
        assertTrue(DuelRequests.isDuelMode(new GameMode("sumo", "Sumo", 2, 1, false)));
        assertTrue(DuelRequests.isDuelMode(new GameMode("ranked-1v1", "Ranked", 2, 1, true)));
        assertFalse(DuelRequests.isDuelMode(new GameMode("ranked-2v2", "Ranked", 2, 2, true)));
        assertFalse(DuelRequests.isDuelMode(new GameMode("clutch", "Clutch", 1, 1, false)));
    }

    @Test
    void buttonsRunTheCommands() {
        String json = DuelRequests.buttons("Rago_1");
        assertTrue(json.contains("\"value\":\"/duel accept Rago_1\""), json);
        assertTrue(json.contains("\"value\":\"/duel deny Rago_1\""), json);
        assertTrue(json.contains("[Accept]") && json.contains("[Deny]"), json);
        assertEquals(count(json, '{'), count(json, '}'), "balanced braces: " + json);
        assertTrue(json.startsWith("[") && json.endsWith("]"));
    }

    @Test
    void aTeamOf2v2IsToldToTheLobbyWithAJoinButton() {
        String json = QueueController.teamQueueLine("Sofiene", "Bed fight 2v2", "bed-fight-2v2");
        assertTrue(json.contains("Sofiene's team") && json.contains(" is queuing ") && json.contains("Bed fight 2v2"), json);
        assertTrue(json.contains("[JOIN NOW]") && json.contains("\"value\":\"/queue bed-fight-2v2\""), json);
        assertEquals(count(json, '{'), count(json, '}'), "balanced braces: " + json);
        assertTrue(new com.google.gson.JsonParser().parse(json).isJsonArray(), "valid JSON");
        assertTrue(new com.google.gson.JsonParser().parse(QueueController.teamQueueLine("A", "Odd \"name\\", "m")).isJsonArray(),
                "a quote in a name is left out");
    }

    private static int count(String text, char c) {
        int n = 0;
        for (char x : text.toCharArray()) {
            if (x == c) {
                n++;
            }
        }
        return n;
    }
}
