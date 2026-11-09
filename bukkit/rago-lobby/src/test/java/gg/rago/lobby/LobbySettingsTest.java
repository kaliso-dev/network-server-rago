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
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LobbySettingsTest {

    private static YamlConfiguration bundledConfig() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(LobbySettingsTest.class.getResourceAsStream("/config.yml"),
                StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        return yaml;
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    @Test
    void readsTheBundledConfig() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        assertEquals("lobby", settings.world());
        assertEquals(0, settings.voidHeight());
        assertEquals(0, settings.defaultRating(), "a ranked ELO starts at 0, in Coal");
        assertEquals(42, settings.modes().size());

        GameMode ranked = settings.catalog().get("ranked-1v1");
        assertTrue(ranked.ranked());
        assertEquals("1v1", ranked.format());
        assertTrue(settings.catalog().get("ranked-2v2").ranked());
        assertEquals("2v2", settings.catalog().get("ranked-2v2").format());
        assertEquals("solo", settings.catalog().get("clutch").format());
        assertEquals("2v2", settings.catalog().get("block-fight-2v2").format());
        assertFalse(settings.catalog().get("sumo").ranked());
        assertEquals(Material.BED, settings.display("ranked-1v1").icon());
        assertEquals(Material.FIREBALL, settings.display("fireball").icon());
        assertEquals("ranked-1v1", settings.modes().get(0).mode().id(), "the order of config.yml");
        assertTrue(settings.title().length() <= 32);
        assertEquals(500, settings.window().width(3_600_000));
    }

    @Test
    void groupsTheBundledModesByCategory() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        assertEquals(3, settings.categories().size());
        assertEquals("duels", settings.categories().get(0).id(), "left to right, as in the lobby");
        assertEquals("ranked", settings.categories().get(1).id());
        assertEquals(Material.IRON_SWORD, settings.categories().get(0).icon());
        assertEquals("sumo", settings.modesIn("duels").get(0).mode().id());
        int total = 0;
        for (ModeCategory category : settings.categories()) {
            int count = settings.modesIn(category.id()).size();
            assertTrue(count > 0 && count <= LobbySettings.MAX_MODES_PER_CATEGORY, category.id() + ": " + count);
            total += count;
        }
        assertEquals(settings.modes().size(), total, "every mode is in a category");
    }

    @Test
    void readsTheBundledNpcs() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        assertEquals(17, settings.npcs().size());
        LobbyNpc duels = settings.npc("attackdefenseversionrbw");
        assertEquals("duels", duels.category());
        assertEquals(settings.modesIn("duels").size(), duels.modeIds().size(), "a category NPC holds its modes");
        assertTrue(duels.modeIds().contains("kb-displace"));
        LobbyNpc ranked = settings.npc("rankedbedwars1v1ou2v2elo");
        assertEquals(java.util.Arrays.asList("ranked-1v1", "ranked-2v2", "ranked-3v3", "ranked-4v4"), ranked.modeIds());
        assertTrue(settings.npc("leaderboardprincipal").leaderboard());
        assertEquals(Collections.singletonList(Arrays.asList(BoardStat.ELO, BoardStat.KILLS, BoardStat.LEVEL)),
                settings.npc("leaderboardprincipal").boards(), "one board, a right click goes to the next stat");
        assertEquals(Arrays.asList(Collections.singletonList(BoardStat.WINS), Collections.singletonList(BoardStat.STREAK),
                Collections.singletonList(BoardStat.GAMES)), settings.npc("leaderboardstats").boards());
        assertEquals(Arrays.asList(Collections.singletonList(BoardStat.STREAK), Collections.singletonList(BoardStat.KILLS),
                Collections.singletonList(BoardStat.LEVEL)), settings.npc("leaderboardplayer").boards(),
                "the kills in the middle, on the point");
        assertFalse(ranked.leaderboard());
        assertEquals(LobbyNpc.Kind.STATS, settings.npc("statsjoueur1").kind());
        assertEquals(LobbyNpc.Kind.STATS, settings.npc("statsjoueur2").kind());
        assertTrue(settings.npc("statsjoueur1").hasBody());
        assertEquals(LobbyNpc.Kind.MODES, ranked.kind());
    }

    @Test
    void readsTheShortLeaderboardForm() throws Exception {
        LobbySettings settings = LobbySettings.load(yaml("classements:\n  mur: '&eTitre'\nmodes:\n  sumo: {}\n"));
        assertEquals(Collections.singletonList(Collections.singletonList(BoardStat.WINS)), settings.npc("mur").boards());
        assertEquals("§eTitre", settings.npc("mur").name());
    }

    @Test
    void checksNpcs() {
        assertInvalid("pnj:\n  pnj-sumo:\n    modes: [autre]\nmodes:\n  sumo: {}\n", "pnj.pnj-sumo.modes");
        assertInvalid("pnj:\n  pnj-sumo: {}\nmodes:\n  sumo: {}\n", "pnj.pnj-sumo");
        assertInvalid("pnj:\n  pnj-duels:\n    categorie: autre\nmodes:\n  sumo: {}\n", "pnj.pnj-duels.categorie");
        assertInvalid("pnj:\n  a:\n    modes: [sumo]\nclassements:\n  a: Titre\nmodes:\n  sumo: {}\n", "classements.a");
        assertInvalid("pnj:\n  a:\n    type: stats\n    modes: [sumo]\nmodes:\n  sumo: {}\n", "pnj.a");
        assertInvalid("pnj:\n  a:\n    type: autre\nmodes:\n  sumo: {}\n", "pnj.a.type");
        assertInvalid("classements:\n  a:\n    tableaux: [morts]\nmodes:\n  sumo: {}\n", "classements.a.tableaux");
        assertInvalid("classements:\n  a:\n    tableaux: [[elo, morts]]\nmodes:\n  sumo: {}\n", "classements.a.tableaux");
        assertInvalid("classements:\n  a:\n    tableaux: [[]]\nmodes:\n  sumo: {}\n", "classements.a.tableaux");
        assertInvalid("classements:\n  a:\n    nom: Mur\nmodes:\n  sumo: {}\n", "classements.a.tableaux");
    }

    @Test
    void readsBoardsOfSeveralStats() throws Exception {
        LobbySettings settings = LobbySettings.load(yaml(
                "classements:\n  mur:\n    tableaux: [[kills, niveau, kills], victoires]\nmodes:\n  sumo: {}\n"));
        assertEquals(Arrays.asList(Arrays.asList(BoardStat.KILLS, BoardStat.LEVEL), Collections.singletonList(BoardStat.WINS)),
                settings.npc("mur").boards(), "a stat twice on a board counts once");
    }

    @Test
    void readsTheNetworkLevel() throws Exception {
        NetworkLevel bundled = LobbySettings.load(bundledConfig()).levels();
        assertEquals(50, bundled.winXp());
        assertEquals(15, bundled.lossXp());
        assertEquals(5, bundled.killXp());
        assertEquals(100, bundled.xpToNext(1));
        assertEquals(150, bundled.xpToNext(2));
        assertEquals(NetworkLevel.DEFAULT.colours(), bundled.colours());

        NetworkLevel levels = LobbySettings.load(yaml("niveau:\n  xp-victoire: 10\n  xp-niveau-2: 20\n"
                + "  xp-en-plus-par-niveau: 0\n  couleurs:\n    3: '&a'\nmodes:\n  sumo: {}\n")).levels();
        assertEquals(10, levels.winXp());
        assertEquals(15, levels.lossXp(), "the default when missing");
        assertEquals(20, levels.xpToNext(7));
        assertEquals(Collections.singletonMap(3, "§a"), levels.colours());
        assertEquals("§a[5" + NetworkLevel.STAR + "]", levels.tag(5));
        assertEquals(NetworkLevel.DEFAULT.colours(), LobbySettings.load(yaml("modes:\n  sumo: {}\n")).levels().colours());
        assertEquals(NetworkLevel.DEFAULT.colours(), LobbySettings.load(yaml(
                "niveau:\n  rangs:\n    5: '&f[Iron]'\nmodes:\n  sumo: {}\n")).levels().colours(),
                "the level ranks of the first version are ignored");

        assertInvalid("niveau:\n  xp-niveau-2: 0\nmodes:\n  sumo: {}\n", "niveau.xp-niveau-2");
        assertInvalid("niveau:\n  xp-kill: -1\nmodes:\n  sumo: {}\n", "niveau.xp-kill");
        assertInvalid("niveau:\n  couleurs:\n    un: '&a'\nmodes:\n  sumo: {}\n", "niveau.couleurs.un");
        assertInvalid("niveau:\n  couleurs:\n    0: '&a'\nmodes:\n  sumo: {}\n", "niveau.couleurs.0");
        assertInvalid("niveau:\n  couleurs:\n    5: '&a[VIP]'\nmodes:\n  sumo: {}\n", "niveau.couleurs.5");
    }

    @Test
    void readsTheKits() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        assertNull(settings.display("sumo").kit(), "a sumo is fought bare-handed");
        assertEquals(Material.DIAMOND_SWORD, settings.display("boxing").kit().item(0).material());
        assertEquals(KitItem.parse("FIREBALL 6"), settings.display("fireball").kit().item(3), "the kit of the fireball maps");
        assertEquals(KitItem.parse("LADDER 16"), settings.display("block-fight-1v1").kit().item(7), "the kit of the block placement maps");
        assertEquals(Material.IRON_SWORD, settings.display("ffa").kit().item(0).material());
        assertEquals(Material.GOLD_PICKAXE, settings.display("invis-practice").kit().item(4).material(),
                "the attacker's pickaxe, for the end stone of the defense");
        assertEquals(KitItem.parse("POTION:8206"), settings.display("invis-practice").kit().item(9), "the invisibility");
        assertEquals(KitItem.parse("EGG 2"), settings.display("invis-practice").kit().item(8), "two Bridge Eggs");

        Kit bySlot = LobbySettings.load(yaml("modes:\n  sumo:\n    kit:\n      3: STICK\n      9: 'WOOL:14 64'\n"))
                .display("sumo").kit();
        assertNull(bySlot.item(0));
        assertEquals(Material.STICK, bySlot.item(3).material());
        assertEquals(KitItem.parse("WOOL:14 64"), bySlot.item(9), "slot 9: the top left of the inventory");
        Kit withGaps = LobbySettings.load(yaml("modes:\n  sumo:\n    kit: [STICK, '', AIR, ENDER_PEARL]\n"))
                .display("sumo").kit();
        assertNull(withGaps.item(1));
        assertNull(withGaps.item(2));
        assertEquals(Material.ENDER_PEARL, withGaps.item(3).material());
        assertNull(LobbySettings.load(yaml("modes:\n  sumo:\n    kit: []\n")).display("sumo").kit(), "no items: no kit");

        assertInvalid("modes:\n  sumo:\n    kit: [BATON]\n", "modes.sumo.kit[0]");
        assertInvalid("modes:\n  sumo:\n    kit: [STICK, 'WOOL 100']\n", "modes.sumo.kit[1]");
        assertInvalid("modes:\n  sumo:\n    kit:\n      36: STICK\n", "modes.sumo.kit.36");
        assertInvalid("modes:\n  sumo:\n    kit: STICK\n", "modes.sumo.kit");
    }

    @Test
    void readsTheHypixelKey() throws Exception {
        assertEquals("", LobbySettings.load(bundledConfig()).hypixelKey(), "never commit a key: the import is off");
        String key = "0f2e1c4a-1b2c-4d3e-8f90-a1b2c3d4e5f6";
        assertEquals(key, LobbySettings.load(yaml("hypixel:\n  cle-api: ' " + key + " '\nmodes:\n  sumo: {}\n"))
                .hypixelKey());
        assertEquals("", LobbySettings.load(yaml("modes:\n  sumo: {}\n")).hypixelKey());
        assertInvalid("hypixel:\n  cle-api: my-key\nmodes:\n  sumo: {}\n", "hypixel.cle-api");
    }

    @Test
    void checksCategories() {
        String categories = "categories:\n  duels:\n    nom: Duels\n";
        assertInvalid(categories + "modes:\n  sumo: {}\n", "modes.sumo.categorie");
        assertInvalid(categories + "modes:\n  sumo:\n    categorie: autre\n", "modes.sumo.categorie");
        assertInvalid("categories:\n  Duels: {}\nmodes:\n  sumo: {}\n", "categories.Duels");
    }

    @Test
    void privateGamesFollowThePartySizeFromTheSmallestMode() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        java.util.List<GameMode> rbw = settings.privateRoutes().get("partie-privee");
        assertEquals(Arrays.asList("ranked-1v1", "ranked-2v2", "ranked-3v3", "ranked-4v4"), ids(rbw));
        assertEquals(Arrays.asList("top-fight-1v1", "top-fight-2v2"), ids(settings.privateRoutes().get("top-fight-prive")));
        assertNull(settings.privateRoutes().get("ranked-1v1"));

        LobbySettings unordered = LobbySettings.load(yaml("parties-privees:\n  prive: [duo, solo]\nmodes:\n  prive: {}\n"
                + "  solo: {equipes: 2, joueurs-par-equipe: 1}\n  duo: {equipes: 2, joueurs-par-equipe: 2}\n"));
        assertEquals(Arrays.asList("solo", "duo"), ids(unordered.privateRoutes().get("prive")));
    }

    @Test
    void refusesPrivateGamesOfUnknownOrOneTeamModes() {
        assertInvalid("parties-privees:\n  prive: [solo]\nmodes:\n  prive: {}\n", "parties-privees.prive");
        assertInvalid("parties-privees:\n  prive: [solo]\nmodes:\n  prive: {}\n  solo: {equipes: 1}\n",
                "parties-privees.prive");
        assertInvalid("parties-privees:\n  autre: [solo]\nmodes:\n  solo: {}\n", "parties-privees.autre");
        assertInvalid("parties-privees:\n  prive: []\nmodes:\n  prive: {}\n", "parties-privees.prive");
    }

    private static java.util.List<String> ids(java.util.List<GameMode> modes) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (GameMode mode : modes) {
            ids.add(mode.id());
        }
        return ids;
    }

    @Test
    void translatesColours() throws Exception {
        LobbySettings settings = LobbySettings.load(yaml("modes:\n  sumo:\n    nom: '&aSumo'\n    description: [Ligne]\n"));
        assertEquals("§aSumo", settings.catalog().get("sumo").name());
        assertEquals("§7Ligne", settings.display("sumo").description().get(0));
        assertEquals(Material.PAPER, settings.display("sumo").icon(), "default icon");
    }

    @Test
    void refusesInvalidConfigs() {
        assertInvalid("monde: lobby\n", "modes");
        assertInvalid("modes:\n  Sumo:\n    nom: Sumo\n", "modes.Sumo");
        assertInvalid("modes:\n  sumo:\n    icone: PAS_UN_OBJET\n", "modes.sumo.icone");
        assertInvalid("modes:\n  sumo:\n    equipes: 1\n    classe: true\n", "modes.sumo");
        assertInvalid("monde: 'mon lobby'\nmodes:\n  sumo: {}\n", "monde");
        assertInvalid("file:\n  ecart-elo-secondes: 0\nmodes:\n  sumo: {}\n", "file");
        assertInvalid("titre: '&b&lUN TITRE BEAUCOUP TROP LONG POUR 1.8'\nmodes:\n  sumo: {}\n", "titre");
    }

    private static void assertInvalid(String text, String expectedStart) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> LobbySettings.load(yaml(text)));
        assertTrue(error.getMessage().startsWith(expectedStart), error.getMessage());
    }
}
