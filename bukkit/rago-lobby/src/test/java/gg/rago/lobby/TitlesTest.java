package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.game.GameMode;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The titles of the modes, earned with wins: "Sumo Gold III". */
class TitlesTest {

    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);

    private static PlayerStats withWins(Object... modeAndWins) {
        PlayerStats stats = new PlayerStats(ALICE, "Alice");
        for (int i = 0; i < modeAndWins.length; i += 2) {
            stats.modeOrNew((String) modeAndWins[i]).wins = (Integer) modeAndWins[i + 1];
        }
        return stats;
    }

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    @Test
    void eachStepHasFiveLevelsFromItsFirstWins() {
        Titles titles = Titles.defaults();
        Titles.Family sumo = titles.family("sumo");
        assertNull(titles.of(sumo, 0));
        assertNull(titles.of(sumo, 9));
        assertEquals("§6Sumo Bronze I", titles.of(sumo, 10).text());
        assertEquals("§6Sumo Bronze II", titles.of(sumo, 29).text());
        assertEquals("§6Sumo Bronze V", titles.of(sumo, 74).text());
        assertEquals("§7Sumo Silver I", titles.of(sumo, 75).text());
        assertEquals("§eSumo Gold III", titles.of(sumo, 300).text());
        assertEquals("§8[§eSumo Gold III§8] ", titles.of(sumo, 300).tag());
        assertEquals("§dSumo Mythic V", titles.of(sumo, 1_000_000).text());
        assertTrue(titles.of(sumo, 75).order > titles.of(sumo, 74).order);
        assertEquals(titles.of(sumo, 300).order, titles.of(sumo, 349).order);
    }

    @Test
    void theNextTitleAndHowFarItIs() {
        Titles titles = Titles.defaults();
        assertEquals(10, titles.nextWins(0));
        assertEquals(20, titles.nextWins(10));
        assertEquals(75, titles.nextWins(50));
        assertEquals(350, titles.nextWins(300));
        assertEquals(-1, titles.nextWins(20_000));
        assertEquals("§eGold IV", titles.rankAt(350));
        assertNull(titles.rankAt(351));
        assertEquals(0f, titles.progress(0));
        assertEquals(0.5f, titles.progress(15));
        assertEquals(0.5f, titles.progress(325));
        assertEquals(1f, titles.progress(20_000));
    }

    @Test
    void aTitleCountsTheWinsOfAllItsModes() {
        Titles titles = Titles.defaults();
        PlayerStats alice = withWins("bed-fight-1v1", 120, "bed-fight-2v2", 60, "top-bed-fight-1v1", 20, "sumo", 3);
        Titles.Family bedFight = titles.family("bed-fight");
        assertEquals(200, Titles.wins(alice, bedFight));
        assertEquals("§eBed Fight Gold I", titles.title(alice, bedFight).text());
        assertNull(titles.title(alice, titles.family("sumo")));
        assertEquals(Arrays.asList(bedFight), titles.familiesOf("top-bed-fight-1v1"));
        assertTrue(titles.familiesOf("ranked-1v1").isEmpty(), "the ranked modes have their ELO divisions");
        assertEquals(0, Titles.wins(null, bedFight));
    }

    @Test
    void theBestIsWornUnlessAnotherOrNoneIsChosen() {
        Titles titles = Titles.defaults();
        PlayerStats alice = withWins("sumo", 300, "boxing", 80, "bow-duel", 300 + 20);
        // the same step and level: the more wins wins
        assertEquals("§eBow Duel Gold III", titles.best(alice).text());
        assertEquals("§eBow Duel Gold III", titles.worn(alice).text());
        alice.setTitleChoice("boxing");
        assertEquals("§7Boxing Silver I", titles.worn(alice).text());
        alice.setTitleChoice("fireball");
        assertEquals("§eBow Duel Gold III", titles.worn(alice).text(), "a title not earned yet: the best");
        alice.setTitleChoice("nothing-of-that-name");
        assertEquals("§eBow Duel Gold III", titles.worn(alice).text());
        alice.setTitleChoice(Titles.NONE);
        assertNull(titles.worn(alice));
        alice.setTitleChoice("");
        assertNull(alice.titleChoice());
        assertNull(titles.worn(new PlayerStats(BOB, "Bob")));
        assertNull(titles.worn(null));
        assertNull(Titles.off().worn(withWins("sumo", 5000)));
        assertTrue(Titles.off().families().isEmpty());
    }

    @Test
    void theChoiceIsKeptInThePlayersFile() throws Exception {
        PlayerStats alice = withWins("sumo", 300);
        alice.setTitleChoice("sumo");
        PlayerStats read = StatsFiles.fromYaml(ALICE, StatsFiles.toYaml(alice));
        assertEquals("sumo", read.titleChoice());
        assertTrue(StatsFiles.toYaml(alice).contains("titre: sumo"));
        assertNull(StatsFiles.fromYaml(ALICE, StatsFiles.toYaml(withWins("sumo", 1))).titleChoice());
    }

    @Test
    void aWinTellsANewTitleOrLevel() {
        // what StatsService tells after a win: the title of the wins now is above the one before
        Titles titles = Titles.defaults();
        Titles.Family sumo = titles.family("sumo");
        assertNull(titles.of(sumo, 9));
        assertEquals(titles.of(sumo, 10).order, titles.of(sumo, 10).order);
        assertTrue(titles.of(sumo, 20).order > titles.of(sumo, 19).order);
        assertEquals(titles.of(sumo, 21).order, titles.of(sumo, 20).order);
        StatsBook book = new StatsBook(0, NetworkLevel.DEFAULT);
        GameMode mode = new GameMode("sumo", "Sumo", 2, 1, false);
        for (int i = 0; i < 10; i++) {
            book.record(mode, Collections.singletonList(ALICE), Collections.singletonList(BOB), id -> "?", 0L);
        }
        assertEquals("§6Sumo Bronze I", titles.worn(book.get(ALICE)).text());
        assertNull(titles.worn(book.get(BOB)));
    }

    @Test
    void theBundledConfigHasThePluginsTitles() throws Exception {
        Titles bundled;
        try (Reader reader = new InputStreamReader(TitlesTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            bundled = ServerSettings.load(YamlConfiguration.loadConfiguration(reader)).titles();
        }
        Titles defaults = Titles.defaults();
        assertTrue(bundled.on());
        assertEquals(defaults.steps().size(), bundled.steps().size());
        for (int i = 0; i < defaults.steps().size(); i++) {
            Titles.Step mine = bundled.steps().get(i);
            Titles.Step plugin = defaults.steps().get(i);
            assertEquals(plugin.name + plugin.colour + plugin.first + "/" + plugin.every,
                    mine.name + mine.colour + mine.first + "/" + mine.every);
        }
        assertEquals(defaults.families().size(), bundled.families().size());
        for (int i = 0; i < defaults.families().size(); i++) {
            Titles.Family mine = bundled.families().get(i);
            Titles.Family plugin = defaults.families().get(i);
            assertEquals(plugin.key + " " + plugin.name + " " + plugin.modes, mine.key + " " + mine.name + " " + mine.modes);
        }
        assertEquals(Titles.defaults().families().size(), ServerSettings.readTitles(null).families().size());
    }

    @Test
    void theTitlesOfTheConfig() throws Exception {
        Titles titles = ServerSettings.readTitles(yaml("paliers:\n  Rookie: {couleur: \"&a\", depart: 5, pas: 5}\n"
                + "  Ace: {couleur: \"&c&l\", depart: 100, pas: 100}\n"
                + "familles:\n  duels: {nom: \"&bDuels\", modes: [sumo, boxing]}\n  bow: {nom: \"Bow\", modes: bow-duel}\n"));
        assertEquals(2, titles.steps().size());
        assertEquals("§c§lDuels Ace II", titles.of(titles.family("duels"), 200).text());
        assertEquals("Duels", titles.family("duels").name, "the colour of a title is that of its step");
        assertEquals(Arrays.asList("bow-duel"), titles.family("bow").modes);
        assertEquals(Titles.defaults().families().size(), ServerSettings.readTitles(yaml("actif: true\n")).families().size());
        Titles off = ServerSettings.readTitles(yaml("actif: false\n"));
        assertFalse(off.on());
        assertNull(off.worn(withWins("sumo", 900)));
    }

    @Test
    void aWrongConfigSaysWhere() {
        List<String> wrong = Arrays.asList(
                "paliers:\n  Gold: {couleur: \"&e\", depart: 200, pas: 50}\n  Silver: {couleur: \"&7\", depart: 75, pas: 25}\n",
                "paliers:\n  Gold: {couleur: \"gold\", depart: 200, pas: 50}\n",
                "paliers:\n  Gold: {couleur: \"&e\", depart: 0, pas: 50}\n",
                "paliers:\n  Gold: {couleur: \"&e\", depart: 200, pas: 0}\n",
                "paliers:\n  AVeryLongStepName: {couleur: \"&e\", depart: 200, pas: 50}\n",
                "paliers: [Gold, Silver]\n",
                "paliers: {}\n",
                "familles:\n  aucun: {nom: \"None\", modes: [sumo]}\n",
                "familles:\n  Sumo: {nom: \"Sumo\", modes: [sumo]}\n",
                "familles:\n  sumo: {nom: \"\", modes: [sumo]}\n",
                "familles:\n  sumo: {nom: \"A title name far too long\", modes: [sumo]}\n",
                "familles:\n  sumo: {nom: \"Sumo\"}\n",
                "familles:\n  sumo: {nom: \"Sumo\", modes: []}\n",
                "familles: sumo\n");
        for (String text : wrong) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ServerSettings.readTitles(yaml(text)),
                    text);
            assertTrue(e.getMessage().startsWith("titres"), e.getMessage());
        }
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(yaml("titres: true\n")));
        assertTrue(e.getMessage().startsWith("titres"), e.getMessage());
    }

    @Test
    void theTitleAboveTheName() {
        assertEquals("§eSumo Gold III", HeadLines.line(HeadLines.Shown.TITLE, -1, "", "", 0, 0, 0, null, "§eSumo Gold III"));
        assertEquals("", HeadLines.line(HeadLines.Shown.TITLE, -1, "", "", 0, 0, 0, null, null));
        assertEquals("", HeadLines.line(HeadLines.Shown.TITLE, -1, "", "", 0, 0, 0, null));
        assertEquals(HeadLines.Shown.TITLE, HeadLines.Shown.of("titre"));
        assertEquals(HeadLines.Shown.TITLE, HeadLines.Shown.of("title"));
    }

    @Test
    void theMenuWritesTheModesAndTheNextLevel() {
        Titles titles = Titles.defaults();
        assertEquals(10, TitlesMenu.slot(0));
        assertEquals(16, TitlesMenu.slot(6));
        assertEquals(19, TitlesMenu.slot(7));
        assertEquals(43, TitlesMenu.slot(Titles.MAX_FAMILIES - 1));
        assertEquals(Arrays.asList("Bed fight 1v1, Bed fight 2v2,", "Top BedFight 1v1"),
                TitlesMenu.wrap(Arrays.asList("Bed fight 1v1", "Bed fight 2v2", "Top BedFight 1v1")));
        List<String> lore = TitlesMenu.lore(titles, titles.family("sumo"), 312, titles.of(titles.family("sumo"), 312),
                Collections.singletonList("Sumo duel"));
        assertEquals("Wins: §a312", lore.get(0));
        assertEquals("§8Sumo duel", lore.get(1));
        assertEquals("Next: §eGold IV§7 at 350 wins", lore.get(3));
        assertTrue(lore.get(4).endsWith("§7 38 more"), lore.get(4));
        List<String> top = TitlesMenu.lore(titles, titles.family("sumo"), 50_000, titles.of(titles.family("sumo"), 50_000),
                Collections.<String>emptyList());
        assertEquals("§dThe highest title!", top.get(2));
        List<String> info = TitlesMenu.info(titles, null);
        assertTrue(info.contains("§6Bronze§7: 10 wins, +10 per level"), info.toString());
        assertTrue(info.contains("§dMythic§7: 10,000 wins, +2,500 per level"), info.toString());
        assertTrue(info.contains("You wear: §8no title"), info.toString());
        for (String line : info) {
            assertTrue(ChatColor.stripColor(line).length() <= 40, line);
        }
    }
}
