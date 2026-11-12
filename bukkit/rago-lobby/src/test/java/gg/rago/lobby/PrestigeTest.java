package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The prestiges after the level 100, and the badges of the ranked seasons. */
class PrestigeTest {

    private static final NetworkLevel LEVELS = NetworkLevel.DEFAULT;
    private static final String STAR = NetworkLevel.STAR;
    /** The XP of the level 100 by default: 100, 150, 200... up to 5,000. */
    private static final int AT_100 = 252_450;
    private static final UUID ALICE = new UUID(0, 1);

    @Test
    void afterTheLevel100EveryLevelTakesTheSameXp() {
        assertEquals(99, LEVELS.level(AT_100 - 1));
        assertEquals(100, LEVELS.level(AT_100));
        assertEquals(5_050, LEVELS.xpToNext(100));
        assertEquals(5_050, LEVELS.xpToNext(101));
        assertEquals(5_050, LEVELS.xpToNext(999));
        assertEquals(101, LEVELS.level(AT_100 + 5_050));
        assertEquals(199, LEVELS.level(AT_100 + 505_000 - 1));
        assertEquals(200, LEVELS.level(AT_100 + 505_000), "a prestige every 505,000 XP");
        assertEquals(123, LEVELS.xpIntoLevel(AT_100 + 505_000 + 123));
        assertEquals(0.5f, LEVELS.progress(AT_100 + 505_000 + 2_525), 0.001f);
        assertEquals(999, LEVELS.level(AT_100 + 4_545_000 - 1));
        assertEquals(NetworkLevel.MAX_LEVEL, LEVELS.level(AT_100 + 4_545_000));
        assertEquals(NetworkLevel.MAX_LEVEL, LEVELS.level(Integer.MAX_VALUE), "the level stops at 1000");
        assertEquals(99_999, LEVELS.xpIntoLevel(AT_100 + 4_545_000 + 99_999), "the XP after the last level are kept");
        assertEquals(0f, LEVELS.progress(Integer.MAX_VALUE));
    }

    @Test
    void eachPrestigeHasItsLook() {
        assertEquals("§e[§f200" + "§e" + STAR + "]", LEVELS.tag(200), "the brackets and the star in the first colour");
        assertEquals("§c[§e345§c" + STAR + "]", LEVELS.tag(345));
        assertEquals("§c[§61§e5§a0§b" + STAR + "§d]", LEVELS.tag(150), "the rainbow of 100 up to 199");
        assertEquals("§6[§e1§f0§e0§60§e" + STAR + "§f]", LEVELS.tag(1000), "the last one shines");
        assertEquals("§e", LEVELS.colour(234));
        assertEquals("§e§f", LEVELS.codes(234));
        assertEquals("§e234", LEVELS.paint("234", 234), "a text in the first colour");
        assertEquals("§e[200" + STAR + "]", LEVELS.tag(200, 11), "in its first colour where both do not fit");
        assertEquals("§6[1000" + STAR + "]", LEVELS.tag(1000, 15));
        java.util.Set<String> looks = new java.util.HashSet<>();
        for (int level = 200; level <= 1000; level += 100) {
            assertTrue(looks.add(LEVELS.codes(level)), "a new look at " + level);
            assertTrue(LobbySettings.isColourCodes(LEVELS.codes(level)) || LobbySettings.isRainbow(LEVELS.codes(level)));
            assertTrue(("§fLevel: " + LEVELS.tag(level)).length() <= 40, "the line of the sidebar at " + level);
        }
        Map<Integer, String> bold = new HashMap<>();
        bold.put(1, "§e§f§l");
        NetworkLevel twoBold = new NetworkLevel(50, 15, 5, 100, 50, bold);
        assertEquals("§e§l[§f§l5§e§l" + STAR + "]", twoBold.tag(5));
        assertEquals("§e[§f5§e" + STAR + "]", twoBold.tag(5, 12), "without the bold where it does not fit");
    }

    @Test
    void thePrestigesAreEveryHundredLevels() {
        assertEquals(200, NetworkLevel.prestigeLevel("200", "x"));
        assertEquals(1000, NetworkLevel.prestigeLevel(" 1000 ", "x"));
        for (String wrong : Arrays.asList("100", "150", "1100", "0", "abc")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> NetworkLevel.prestigeLevel(wrong, "niveau.prestiges." + wrong));
            assertTrue(error.getMessage().startsWith("niveau.prestiges." + wrong), error.getMessage());
        }
        assertEquals(50, StatsService.levelPoints(1, 3, 10), "levels 2 and 3");
        assertEquals(2_000, StatsService.levelPoints(99, 101, 10), "the level 101 gives as much as the 100");
        assertEquals(1_000, StatsService.levelPoints(500, 501, 10));
        assertEquals(Arrays.asList(100, 200), StatsService.prestiges(99, 205));
        assertTrue(StatsService.prestiges(100, 199).isEmpty());
        assertEquals(Collections.singletonList(200), StatsService.prestiges(199, 200));
        assertEquals(Collections.singletonList(1000), StatsService.prestiges(950, 1000));
    }

    @Test
    void theMenuShowsTheRewardsAroundTheNextOne() throws Exception {
        LevelRewards rewards = LevelRewards.defaults();
        List<LevelRewards.Reward> all = rewards.all();
        assertEquals(0, LevelMenu.firstShown(all, rewards.next(1), 21), "a new player: from the first one");
        assertEquals(3, LevelMenu.firstShown(all, rewards.next(50), 21), "a row of those got, then the next one");
        assertEquals(55, all.get(3 + 7).level());
        assertEquals(8, LevelMenu.firstShown(all, null, 21), "all got: the last ones");
        assertEquals(8, LevelMenu.firstShown(all, rewards.next(150), 21));
        assertEquals(0, LevelMenu.firstShown(all.subList(0, 20), null, 21), "all of them when they fit");

        List<String> tiers = LevelMenu.tierLines(LEVELS, 150);
        assertTrue(tiers.contains("Prestiges, every 100 levels:"), tiers.toString());
        assertTrue(tiers.contains(LEVELS.tag(100) + "§a ◀ you"), tiers.toString());
        assertTrue(tiers.contains(LEVELS.tag(200)), tiers.toString());
        assertTrue(tiers.contains(LEVELS.tag(90) + "§a ✔"), tiers.toString());
    }

    @Test
    void theBundledConfigHasThePrestigesOfThePlugin() throws Exception {
        YamlConfiguration bundled;
        try (java.io.Reader reader = new java.io.InputStreamReader(PrestigeTest.class.getResourceAsStream("/config.yml"),
                java.nio.charset.StandardCharsets.UTF_8)) {
            bundled = YamlConfiguration.loadConfiguration(reader);
        }
        assertEquals(LEVELS.colours(), LobbySettings.load(bundled).levels().colours());
        ServerSettings server = ServerSettings.load(bundled);
        for (LevelRewards.Reward reward : LevelRewards.defaults().all()) {
            assertEquals(reward.points(), server.levelRewards().at(reward.level()).points(), "level " + reward.level());
        }
        YamlConfiguration none = new YamlConfiguration();
        none.loadFromString("niveau:\n  prestiges: {}\n");
        assertNull(ServerSettings.load(none).levelRewards().at(200), "{}: no prestige");
        assertEquals(20, ServerSettings.load(none).levelRewards().all().size(), "the rewards up to 100 are kept");
        YamlConfiguration mine = new YamlConfiguration();
        mine.loadFromString("niveau:\n  recompenses:\n    50: {points: 7}\n  prestiges:\n    300: {points: 9, objets: [couleur-or]}\n"
                + "    400: {couleur: '&a&b'}\n");
        LevelRewards custom = ServerSettings.load(mine).levelRewards();
        assertEquals(Arrays.asList(50, 300), Arrays.asList(custom.all().get(0).level(), custom.all().get(1).level()));
        assertEquals(Collections.singletonList("couleur-or"), custom.at(300).items());
        assertNull(custom.at(400), "a look without a reward");
        YamlConfiguration onlyTiers = new YamlConfiguration();
        onlyTiers.loadFromString("niveau:\n  recompenses:\n    50: {points: 7}\n");
        assertEquals(20_000, ServerSettings.load(onlyTiers).levelRewards().at(200).points(),
                "the prestiges of the plugin when left out, as in a config.yml of before them");
    }

    @Test
    void aSeasonEndedGivesItsBadge() {
        EloLadder ladder = EloLadder.DEFAULT;
        NameStyle diamond = NameLooks.seasonBadge(2, "Diamond", ladder);
        assertEquals("§bS2 Diamond", diamond.text(), "in the colour of the division");
        assertEquals("badge-saison-2", diamond.key());
        assertEquals(NameStyle.Obtain.SEASON, diamond.obtain());
        assertEquals("§7S3 Gone", NameLooks.seasonBadge(3, "Gone", ladder).text(), "grey for a division gone");
        assertEquals(NameStyle.MAX_BADGE, NameLooks.seasonBadge(1000, "Amethyst", ladder).text().length(), "cut to fit");
        assertEquals(2, NameLooks.seasonOf("saison-2"));
        assertEquals(0, NameLooks.seasonOf("rago"));
        assertEquals(0, NameLooks.seasonOf("saison-x"));
        assertEquals(0, NameLooks.seasonOf(null));

        ServerSettings server = ServerSettings.load(new YamlConfiguration());
        StatsBook book = new StatsBook(0, NetworkLevel.DEFAULT);
        NameLooks names = new NameLooks(book, server);
        PlayerStats alice = book.profile(ALICE, "Alice");
        assertEquals("", names.badgeAfter(ALICE), "no season ended");
        alice.seasons().put(2, new PlayerStats.SeasonResult(350, "Diamond", true));
        assertEquals(" §bS2 Diamond", names.badgeAfter(ALICE), "worn when she wears no other");
        alice.seasons().put(3, new PlayerStats.SeasonResult(40, "Coal", true));
        assertEquals("", names.badgeAfter(ALICE), "not the first division of her last season");
        assertEquals(Arrays.asList("§8S3 Coal", "§bS2 Diamond"), Arrays.asList(names.seasonBadges(alice).get(0).text(),
                names.seasonBadges(alice).get(1).text()), "the latest first");
        assertTrue(names.owns(alice, diamond));
        assertFalse(names.owns(alice, NameLooks.seasonBadge(5, "Diamond", ladder)), "a season she did not play");
        alice.choose(NameStyle.Kind.BADGE, "saison-2");
        assertEquals(" §bS2 Diamond", names.badgeAfter(ALICE), "the one she chose");
        alice.choose(NameStyle.Kind.BADGE, "saison-9");
        assertEquals("", names.badgeAfter(ALICE), "a season she did not end: as without a choice");
        alice.choose(NameStyle.Kind.BADGE, "");
        alice.seasons().remove(3);
        assertEquals("", names.badgeAfter(ALICE), "none when she chose none");
        alice.choose(NameStyle.Kind.BADGE, null);
        alice.boughtRanks().add("premium");
        assertEquals(" §d§l✦", names.badgeAfter(ALICE), "the badge of her rank first");
        alice.choose(NameStyle.Kind.BADGE, "saison-2");
        assertEquals(" §bS2 Diamond", names.badgeAfter(ALICE));
        names.setNicks(id -> "Nobody");
        assertEquals("", names.shownBadge(ALICE), "nothing under a nick");
    }

    @Test
    void theSeasonBadgesAreNotInConfig() {
        assertInvalidStyles("styles:\n  badges:\n    saison-2: {nom: 'S2', texte: '&bS2'}\n", "styles.badges.saison-2");
        assertInvalidStyles("styles:\n  badges:\n    best: {nom: 'Best', texte: '&bBest', obtention: saison}\n",
                "styles.badges.best.obtention");
    }

    private static void assertInvalidStyles(String text, String where) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(yaml));
        assertTrue(error.getMessage().startsWith(where), error.getMessage());
    }
}
