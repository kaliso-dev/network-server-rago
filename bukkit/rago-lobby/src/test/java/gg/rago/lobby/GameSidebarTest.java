package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    @Test
    void aGameWithoutPingGetsTheLobbysAfterItsLines() {
        List<String> given = Arrays.asList("Diamond II in 5:00", "", "Red: OK", "Blue: OK", "", "Kills: 0");
        assertFalse(LobbyScoreboard.showsPing(given));
        assertTrue(LobbyScoreboard.showsPing(Arrays.asList("Time: 0:10", "\u00a7fYour ping: \u00a7a40 ms")));
        List<String> ping = LobbyScoreboard.pingLines(42, Collections.singletonList(130), true);
        List<String> lines = LobbyScoreboard.givenLines(given, ping, "play.rago.gg");
        assertEquals(12, lines.size(), lines.toString());
        assertEquals("Kills: 0", lines.get(6));
        assertEquals("\u00a7fYour ping: \u00a7a42 ms", lines.get(8));
        assertEquals("\u00a7fTheir ping: \u00a7e130 ms", lines.get(9));
        assertEquals("play.rago.gg", lines.get(11));
        assertEquals(lines.size(), new HashSet<>(lines).size(), lines.toString());
    }

    @Test
    void thePingStaysWhenTheGameFillsTheSidebar() {
        List<String> given = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            given.add("Team " + i);
        }
        List<String> lines = LobbyScoreboard.givenLines(given, LobbyScoreboard.pingLines(20, new ArrayList<Integer>(), false),
                "play.rago.gg");
        assertEquals(LobbyScoreboard.MAX_GAME_LINES + 3, lines.size());
        assertEquals("\u00a7fYour ping: \u00a7a20 ms", lines.get(lines.size() - 3));
        assertEquals("Team 9", lines.get(10));
    }

    @Test
    void aPlayerWhoWaitsInQueuesWhileTrainingSeesHowLong() {
        List<gg.rago.game.GameMode> waiting = Arrays.asList(new gg.rago.game.GameMode("sumo", "Sumo", 2, 1, false),
                new gg.rago.game.GameMode("bridge", "Bridge", 2, 1, false));
        assertEquals("\u00a7fQueue: \u00a7c0:42\u00a77 (Sumo +1)", LobbyScoreboard.queueLine(waiting, 42_000L));
        assertEquals("\u00a7fQueue: \u00a7c1:05\u00a77 (Sumo)", LobbyScoreboard.queueLine(waiting.subList(0, 1), 65_000L));
        List<String> given = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            given.add("Line " + i);
        }
        List<String> lines = LobbyScoreboard.givenLines(given, LobbyScoreboard.pingLines(20, new ArrayList<Integer>(), false),
                LobbyScoreboard.queueLine(waiting, 0L), "play.rago.gg");
        assertEquals(LobbyScoreboard.MAX_GAME_LINES + 3, lines.size(), "no more lines: " + lines);
        assertEquals("\u00a7fQueue: \u00a7c0:00\u00a77 (Sumo +1)", lines.get(lines.size() - 2), "over the address, for the blank line");
        assertEquals("\u00a7fYour ping: \u00a7a20 ms", lines.get(lines.size() - 3), "the game and the ping keep their room");
        assertEquals("Line 9", lines.get(10));
        assertEquals(lines.size(), new HashSet<>(lines).size(), lines.toString());
    }

    @Test
    void theOpponentsPingsShowOneByOneOrAsAnAverage() {
        assertEquals(2, LobbyScoreboard.pingLines(20, new ArrayList<Integer>(), false).size());
        assertEquals("\u00a7fTheir ping: \u00a77left", LobbyScoreboard.pingLines(20, new ArrayList<Integer>(), true).get(2));
        assertEquals("\u00a7fTheir ping: \u00a7a40\u00a77/\u00a7c200 ms",
                LobbyScoreboard.pingLines(20, Arrays.asList(40, 200), true).get(2));
        assertEquals("\u00a7fTheir ping: \u00a77~\u00a7a60 ms", LobbyScoreboard.pingLines(20, Arrays.asList(40, 60, 80, -1, 60), true).get(2));
    }

    @Test
    void theHealthUnderTheNamesCountsTheAbsorption() {
        assertEquals(20, LobbyScoreboard.healthPoints(20, 0));
        assertEquals(28, LobbyScoreboard.healthPoints(20, 8));
        assertEquals(1, LobbyScoreboard.healthPoints(0.5, 0));
        assertEquals(0, LobbyScoreboard.healthPoints(-1, 0));
    }
}
