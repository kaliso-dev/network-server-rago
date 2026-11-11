package gg.rago.lobby.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** The two buttons of the end of a game: valid JSON for /tellraw, each running its command. */
class EndOfGameTest {

    @Test
    void theButtonsPlayAgainOrLeave() {
        JsonArray parts = new JsonParser().parse(EndOfGame.json()).getAsJsonArray();
        JsonObject again = parts.get(1).getAsJsonObject();
        JsonObject leave = parts.get(3).getAsJsonObject();
        assertEquals("[PLAY AGAIN]", again.get("text").getAsString());
        assertEquals("/playagain", again.getAsJsonObject("clickEvent").get("value").getAsString());
        assertEquals("[LEAVE]", leave.get("text").getAsString());
        assertEquals("/spawn", leave.getAsJsonObject("clickEvent").get("value").getAsString());
        assertTrue(EndOfGame.PLAY_AGAIN_SLOT != EndOfGame.LEAVE_SLOT);
    }
}
