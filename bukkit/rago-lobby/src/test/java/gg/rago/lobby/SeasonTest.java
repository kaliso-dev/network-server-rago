package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.game.GameMode;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SeasonTest {

    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final GameMode RANKED = new GameMode("ranked-1v1", "Ranked 1v1", 2, 1, true);

    /** Alice's file at the end of season 2: Gold 245 now in 1v1 after a peak at Diamond 310, Iron 120 in 2v2. */
    private static final String SEASON_TWO = "nom: Alice\nxp: 500\nelo-saison: 2\nprofil:\n  points: 40\nmodes:\n"
            + "  ranked-1v1:\n    victoires: 10\n    defaites: 4\n    elo: 245\n    elo-max: 310\n"
            + "  ranked-2v2:\n    victoires: 2\n    elo: 120\n";

    @Test
    void theBestEloOfTheSeasonIsKeptAndTheRankedEloStartsAgain() throws Exception {
        PlayerStats alice = StatsFiles.fromYaml(ALICE, SEASON_TWO, 3, EloLadder.DEFAULT);
        assertEquals(0, alice.mode("ranked-1v1").elo, "a new season starts from 0");
        assertEquals(0, alice.mode("ranked-1v1").peakElo);
        assertEquals(10, alice.mode("ranked-1v1").wins, "the wins stay");
        assertEquals(40, alice.points());
        PlayerStats.SeasonResult result = alice.seasons().get(2);
        assertEquals(310, result.elo, "the best ELO of the season, in any ranked mode");
        assertEquals("Diamond", result.division);
        assertFalse(result.rewarded, "its reward waits for the next arrival");

        // written in season 3 and read again: the result stays, not counted twice
        result.rewarded = true;
        PlayerStats again = StatsFiles.fromYaml(ALICE, StatsFiles.toYaml(alice, 3), 3, EloLadder.DEFAULT);
        assertEquals(1, again.seasons().size());
        assertEquals(310, again.seasons().get(2).elo);
        assertTrue(again.seasons().get(2).rewarded);
        // not rewarded yet when the file was written: still to give
        result.rewarded = false;
        assertFalse(StatsFiles.fromYaml(ALICE, StatsFiles.toYaml(alice, 3), 3, EloLadder.DEFAULT).seasons().get(2).rewarded);
    }

    @Test
    void theSameSeasonKeepsItsEloAndItsPeak() throws Exception {
        PlayerStats alice = StatsFiles.fromYaml(ALICE, SEASON_TWO, 2, EloLadder.DEFAULT);
        assertEquals(245, alice.mode("ranked-1v1").elo);
        assertEquals(310, alice.mode("ranked-1v1").peakElo);
        assertTrue(alice.seasons().isEmpty());
        assertTrue(StatsFiles.toYaml(alice, 2).contains("elo-max: 310"));
    }

    @Test
    void noResultForTheFirstSeasonNorForAPlayerWithoutRankedGames() throws Exception {
        // the first season counted from 1000: its ELO is no division
        String first = SEASON_TWO.replace("elo-saison: 2", "elo-saison: 1");
        assertTrue(StatsFiles.fromYaml(ALICE, first, 3, EloLadder.DEFAULT).seasons().isEmpty());
        String noRanked = "nom: Bob\nxp: 10\nelo-saison: 2\nmodes:\n  sumo:\n    victoires: 3\n";
        assertTrue(StatsFiles.fromYaml(BOB, noRanked, 3, EloLadder.DEFAULT).seasons().isEmpty());
    }

    @Test
    void theRankedGamesRaiseThePeakOfTheSeasonNeverLowerIt() {
        StatsBook book = new StatsBook(0, NetworkLevel.DEFAULT);
        StatsBook.Names names = id -> id.equals(ALICE) ? "Alice" : "Bob";
        long now = ZonedDateTime.parse("2026-11-11T12:00:00Z").toInstant().toEpochMilli();
        book.record(RANKED, Collections.singletonList(ALICE), Collections.singletonList(BOB), names, now);
        book.record(RANKED, Collections.singletonList(ALICE), Collections.singletonList(BOB), names, now);
        int peak = book.get(ALICE).mode("ranked-1v1").peakElo;
        assertEquals(book.elo(ALICE, "ranked-1v1"), peak);
        book.record(RANKED, Collections.singletonList(BOB), Collections.singletonList(ALICE), names, now);
        assertTrue(book.elo(ALICE, "ranked-1v1") < peak);
        assertEquals(peak, book.get(ALICE).mode("ranked-1v1").peakElo, "a loss does not lower the peak");
    }

    @Test
    void theSeasonOfConfigYml() throws Exception {
        Season none = Season.read(null, EloLadder.DEFAULT);
        assertEquals(Season.FIRST_WITH_DIVISIONS, none.number());
        assertEquals("Season 2", none.name());
        assertNull(none.end());
        assertEquals(10000, none.reward("Obsidian"));
        assertEquals(0, none.reward("Coal"));
        assertEquals("", none.endText(System.currentTimeMillis()));

        YamlConfiguration config = new YamlConfiguration();
        config.set("saison.numero", 3);
        config.set("saison.fin", "2027-01-31");
        config.set("saison.recompenses.Gold", 800);
        Season three = Season.read(config.getConfigurationSection("saison"), EloLadder.DEFAULT);
        assertEquals(3, three.number());
        assertEquals("Season 3", three.name());
        assertEquals(LocalDate.of(2027, 1, 31), three.end());
        assertEquals(800, three.reward("Gold"));
        assertEquals(250, three.reward("Iron"), "the other divisions keep their reward");
        long noon = ZonedDateTime.parse("2027-01-21T12:00:00Z").toInstant().toEpochMilli();
        assertEquals("ends on 31/01/2027 (10 days left)", three.endText(noon));
        assertEquals("ends today", three.endText(ZonedDateTime.parse("2027-01-31T12:00:00Z").toInstant().toEpochMilli()));
        assertTrue(three.endText(ZonedDateTime.parse("2027-02-02T12:00:00Z").toInstant().toEpochMilli()).startsWith("ended"));

        config.set("saison.recompenses.Bronze", 5);
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Season.read(config.getConfigurationSection("saison"), EloLadder.DEFAULT));
        assertTrue(wrong.getMessage().contains("saison.recompenses.Bronze"), wrong.getMessage());
        config.set("saison.recompenses.Bronze", null);
        config.set("saison.fin", "31/01");
        assertThrows(IllegalArgumentException.class, () -> Season.read(config.getConfigurationSection("saison"), EloLadder.DEFAULT));
        config.set("saison.fin", "");
        config.set("saison.numero", 1);
        assertThrows(IllegalArgumentException.class, () -> Season.read(config.getConfigurationSection("saison"), EloLadder.DEFAULT));
    }

    @Test
    void theShippedSeasonKeepsTheFilesOfTheDivisions() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.load(new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        Season season = Season.read(config.getConfigurationSection("saison"), EloLadder.DEFAULT);
        assertEquals(StatsFiles.ELO_SEASON, season.number(), "the ELO of the players stays as it is");
        assertEquals("Season 2", season.name());
        assertEquals(500, season.reward("Gold"));
    }
}
