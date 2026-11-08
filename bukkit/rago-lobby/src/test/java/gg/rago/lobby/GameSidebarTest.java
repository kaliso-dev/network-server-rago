package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The sidebar lines a game gives its players (LobbyService#setGameSidebar). */
class GameSidebarTest {

    @Test
    void theGameLinesGoBetweenABlankLineAndTheAddress() {
        List<String> lines = LobbyScoreboard.givenLines(Arrays.asList("Diamond II in 5:00", "", "Red: OK", "Blue: OK", "",
                "Kills: 0"), "play.rago.gg");
        assertEquals(9, lines.size());
        assertEquals("Diamond II in 5:00", lines.get(1));
        assertEquals("Kills: 0", lines.get(6));
        assertEquals("play.rago.gg", lines.get(8));
        assertEquals(lines.size(), new HashSet<>(lines).size(), "every entry of a sidebar shows once: " + lines);
    }

    @Test
    void repeatedLinesStayApartAndTheRestIsCut() {
        List<String> given = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            given.add(i % 2 == 0 ? "" : "Same line");
        }
        List<String> lines = LobbyScoreboard.givenLines(given, "play.rago.gg");
        assertEquals(LobbyScoreboard.MAX_GAME_LINES + 3, lines.size());
        assertEquals(lines.size(), new HashSet<>(lines).size(), lines.toString());
        for (String line : lines) {
            assertTrue(line.length() <= 40, line);
        }
    }
}
