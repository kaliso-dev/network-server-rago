package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LobbySettingsTest {

    static YamlConfiguration bundledConfig() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(LobbySettingsTest.class.getResourceAsStream("/config.yml"),
                StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        return yaml;
    }

    /**
     * The bundled config with the NPCs of modes it leaves in comments (the centre shows only the
     * three ways to play), on points of their own.
     */
    static YamlConfiguration withModeNpcs() throws Exception {
        YamlConfiguration yaml = bundledConfig();
        modeNpc(yaml, "ranked", "ranked", "ranked-1v1", "ranked-2v2", "ranked-3v3", "ranked-4v4");
        modeNpc(yaml, "prive", "prive", "partie-privee", "top-bridge-prive");
        modeNpc(yaml, "ffa", "ffa", "ffa", "top-bridge-1v1", "top-bridge-2v2", "top-bridge-ffa");
        modeNpc(yaml, "clutchscenario", "clutch", "clutch-scenario-1v1", "clutch-scenario-2v2", "clutch-scenario-3v3",
                "clutch-scenario-4v4");
        modeNpc(yaml, "attackanddefense", "attaque", "attack-defense-1v1", "attack-defense-2v2");
        return yaml;
    }

    private static void modeNpc(YamlConfiguration yaml, String point, String skin, String... modes) {
        yaml.set("pnj." + point + ".nom", "&6" + point);
        yaml.set("pnj." + point + ".skin", skin);
        yaml.set("pnj." + point + ".modes", Arrays.asList(modes));
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
        assertEquals(59, settings.modes().size(), "with the private top bridge, the clutch scenario, the bed fight, Top"
                + " BedFight, the bridge fight 2v2, the full map first rush and Sky Rise Resources, without the top fight or the"
                + " ranked scenarios");
        assertEquals("skyrise", settings.display("skyrise-resources-1v1").group(), "beside Sky Rise");
        assertFalse(settings.catalog().get("top-bridge-1v1").ranked(), "Top Bridge: no ELO");
        assertEquals("2v2", settings.catalog().get("top-bridge-2v2").format());
        assertTrue(LobbySettings.load(withModeNpcs()).npc("ffa").modeIds().contains("top-bridge-1v1"));

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
        assertEquals("entrainement", settings.categories().get(0).id(), "left to right: the practice on the left");
        assertEquals("ranked", settings.categories().get(1).id());
        assertEquals("duels", settings.categories().get(2).id(), "the duels on the right");
        assertEquals(Material.IRON_SWORD, settings.categories().get(2).icon());
        // Three ways to play: train alone, duel, the RBW modes (not all ranked: no bed on it).
        assertEquals("§6§lRBW Modes", settings.categories().get(1).name());
        assertEquals(Material.BED, settings.categories().get(1).icon());
        for (ModeCategory category : settings.categories()) {
            assertTrue(category.slogan() != null && category.slogan().startsWith(category.name().substring(0, 2)),
                    category.id() + " has its slogan, in the colour of its name");
            assertTrue(category.slogan().contains("§7"), "the rest in grey");
        }
        assertTrue(settings.welcome(), "the welcome is on by default");
        assertEquals("bed-fight-1v1", settings.modesIn("duels").get(0).mode().id(), "the bed fight first");
        for (String duel : Arrays.asList("bed-fight-1v1", "bed-fight-2v2", "top-bridge-1v1", "top-bridge-2v2", "bridge-fight-1v1",
                "bridge-fight-2v2", "block-fight-1v1", "block-fight-2v2", "sumo")) {
            assertEquals("duels", settings.display(duel).category(), duel + ": in the Duels NPC and compass");
        }
        assertEquals("ranked", settings.display("top-bridge-ffa").category(), "the free for all stay in the RBW modes");
        assertEquals("ranked", settings.display("bridge-fight").category());
        assertEquals("ranked", settings.display("block-fight-arena").category());
        assertEquals("entrainement", settings.display("kb-displace-bot").category(), "against a bot: practice");
        int total = 0;
        for (ModeCategory category : settings.categories()) {
            int count = settings.modesIn(category.id()).size();
            int items = settings.entriesIn(category.id()).size();
            assertTrue(count > 0 && items <= LobbySettings.MAX_MODES_PER_CATEGORY, category.id() + ": " + items);
            total += count;
        }
        assertEquals("ranked", settings.display("attack-defense-1v1").category(), "Attack and Defense: in the RBW modes");
        assertEquals("ranked", settings.display("clutch-scenario-2v2").category());
        LobbySettings modeNpcs = LobbySettings.load(withModeNpcs());
        assertEquals(Arrays.asList("clutch-scenario-1v1", "clutch-scenario-2v2", "clutch-scenario-3v3",
                "clutch-scenario-4v4"), modeNpcs.npc("clutchscenario").modeIds(), "one NPC for Clutch Scenario");
        assertEquals(4, modeNpcs.entriesOf(modeNpcs.npc("clutchscenario").modeIds()).size(),
                "its only group: each size is an item of its menu");
        assertEquals(Arrays.asList("attack-defense-1v1", "attack-defense-2v2"), modeNpcs.npc("attackanddefense").modeIds(),
                "and one for Attack and Defense");
        assertEquals("clutch", modeNpcs.npc("clutchscenario").skin());
        assertEquals("attaque", modeNpcs.npc("attackanddefense").skin());
        assertEquals(settings.modes().size(), total, "every mode is in a category");
    }

    @Test
    void theSizesOfOneGameAreOneItemOfTheirCategory() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        List<LobbySettings.Entry> ranked = settings.entriesIn("ranked");
        assertEquals("ranked", ranked.get(0).group.id(), "Ranked Bedwars first");
        assertEquals(Arrays.asList("ranked-1v1", "ranked-2v2", "ranked-3v3", "ranked-4v4"),
                displayIds(settings.modesOfGroup("ranked")));
        assertEquals(Arrays.asList("top-bridge-1v1", "top-bridge-2v2"), displayIds(settings.modesOfGroup("top-bridge")));
        assertEquals(Arrays.asList("block-fight-1v1", "block-fight-2v2"), displayIds(settings.modesOfGroup("block-placement")));
        assertEquals(Arrays.asList("bridge-fight-1v1", "bridge-fight-2v2"), displayIds(settings.modesOfGroup("bridge-fight")));
        assertEquals(Arrays.asList("bed-fight-1v1", "bed-fight-2v2"), displayIds(settings.modesOfGroup("bed-fight")));
        List<LobbySettings.Entry> duels = settings.entriesIn("duels");
        assertEquals("bed-fight", duels.get(0).group.id(), "the duels: 1v1 or 2v2 in one item each");
        assertEquals("top-bed-fight-1v1", duels.get(1).mode.mode().id(), "Top BedFight next to the bed fight, in 1v1 only");
        assertEquals("top-bridge", duels.get(2).group.id());
        assertEquals("bridge-fight", duels.get(3).group.id());
        assertEquals("block-placement", duels.get(4).group.id());
        assertTrue(ranked.size() <= 14, "two rows at most: " + ranked.size());
        assertNull(settings.display("top-fight-1v1"), "no top fight: there are bridge fight and top bridge");
        assertNull(settings.privateRoutes().get("top-fight-prive"));
        assertEquals(Arrays.asList("attack-defense-1v1", "attack-defense-2v2"),
                displayIds(settings.modesOfGroup("attack-defense")));
        assertEquals(Arrays.asList("top-bridge-1v1", "top-bridge-2v2"), ids(settings.privateRoutes().get("top-bridge-prive")),
                "the practice of the top bridge, with the party");
        assertTrue(LobbySettings.load(withModeNpcs()).npc("prive").modeIds().contains("top-bridge-prive"));
        assertTrue(settings.display("top-bridge-1v1").kit() != null, "the kit of the rounds, arranged in the kit editor");
        assertEquals(Material.STONE_SWORD, settings.display("top-bridge-ffa").kit().item(0).material(), "the fight kit");
        int shown = 0;
        for (ModeCategory category : settings.categories()) {
            for (LobbySettings.Entry entry : settings.entriesIn(category.id())) {
                shown += entry.group != null ? settings.modesOfGroup(entry.group.id()).size() : 1;
            }
        }
        for (String event : Arrays.asList("event-sumo", "event-brackets", "event-lms")) {
            assertTrue(settings.display(event).hidden(), "the events of /host are not in the selector: " + event);
        }
        assertEquals(settings.modes().size() - 3, shown, "every mode but the hidden ones (the 3 events) is in the selector, once");
        assertInvalid("groupes:\n  g: {}\nmodes:\n  sumo:\n    groupe: autre\n", "modes.sumo.groupe");
    }

    @Test
    void anNpcShowsTheModesOfOneGroupAsOneItem() throws Exception {
        LobbySettings settings = LobbySettings.load(withModeNpcs());
        List<LobbySettings.Entry> ffa = settings.entriesOf(settings.npc("ffa").modeIds());
        assertEquals(3, ffa.size(), "the FFA, the Top Bridge 1v1 and 2v2 as one item, the Top Bridge FFA");
        assertEquals("ffa", ffa.get(0).mode.mode().id());
        assertEquals("top-bridge", ffa.get(1).group.id());
        assertEquals(Material.LADDER, ffa.get(1).group.icon(), "a ladder, which opens 1v1 and 2v2");
        assertEquals("top-bridge-ffa", ffa.get(2).mode.mode().id());
        List<LobbySettings.Entry> ranked = settings.entriesOf(settings.npc("ranked").modeIds());
        assertEquals(4, ranked.size(), "one group only: its modes, one by one, as before");
        assertEquals("ranked-1v1", ranked.get(0).mode.mode().id());
        assertEquals(2, settings.entriesOf(settings.npc("prive").modeIds()).size(),
                "the private game and the private top bridge: no group of two");
    }

    private static List<String> displayIds(List<ModeDisplay> displays) {
        List<String> ids = new java.util.ArrayList<>();
        for (ModeDisplay display : displays) {
            ids.add(display.mode().id());
        }
        return ids;
    }

    @Test
    void readsTheBundledNpcs() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        assertEquals(9, settings.npcs().size(), "three NPCs of modes, one per way to play; the shop, the stats, the boards");
        for (String gone : Arrays.asList("ranked", "prive", "ffa", "clutchscenario", "attackanddefense", "rbwsolo1v1ou2v2skyrise",
                "firstrushnebucgelatosoloduo", "invispracticearwhayinvasion", "realisticplat", "freearenacombo",
                "blockplacementarena", "attackdefenseversionrbw", "blockinpractice")) {
            assertNull(settings.npc(gone), gone + ": its modes stay in the compass");
        }
        List<String> categories = new java.util.ArrayList<>();
        for (LobbyNpc npc : settings.npcs()) {
            if (npc.kind() == LobbyNpc.Kind.MODES) {
                assertNotNull(npc.category(), npc.point() + ": only the NPCs of the three categories");
                categories.add(npc.category());
            }
        }
        assertEquals(Arrays.asList("entrainement", "ranked", "duels"), categories, "Practice, RBW Modes, Duels");
        LobbyNpc duels = settings.npc("rbwffa");
        assertEquals("duels", duels.category());
        assertEquals("§a§lDuels", duels.name());
        assertEquals("§aDuel your friends §7or §aanyone", duels.subtitle(), "under a category NPC, the slogan of its category,"
                + " in the colours of its category");
        assertEquals("§bTrain alone§7, at your §bown pace", settings.npc("privategame").subtitle());
        assertEquals("entrainement", settings.npc("privategame").category());
        assertEquals(settings.modesIn("duels").size(), duels.modeIds().size(), "a category NPC holds its modes");
        assertTrue(duels.modeIds().contains("kb-displace"));
        LobbyNpc ranked = settings.npc("rankedbedwars1v1ou2v2elo");
        assertEquals("§6§lRBW Modes", ranked.name());
        assertEquals("§6Ranked §7and §6RBW situations", ranked.subtitle());
        assertEquals("ranked", ranked.category());
        assertTrue(ranked.modeIds().containsAll(Arrays.asList("ranked-1v1", "ranked-2v2", "ranked-3v3", "ranked-4v4",
                "clutch-scenario-1v1", "attack-defense-1v1")), "every RBW mode, Ranked Bedwars first");
        assertEquals(settings.modesIn("ranked").size(), ranked.modeIds().size());
        assertTrue(settings.npc("leaderboardprincipal").leaderboard());
        assertEquals(Collections.singletonList(Arrays.asList(BoardStat.LEVEL, BoardStat.ELO, BoardStat.FKDR, BoardStat.KILLS, BoardStat.GAMES)),
                settings.npc("leaderboardprincipal").boards(), "one board, the Network level first (the podium too), a right"
                + " click goes to the next stat");
        assertEquals(Arrays.asList(Collections.singletonList(BoardStat.WINS), Collections.singletonList(BoardStat.STREAK),
                Collections.singletonList(BoardStat.GAMES)), settings.npc("leaderboardstats").boards());
        assertEquals(Arrays.asList(Collections.singletonList(BoardStat.STREAK), Collections.singletonList(BoardStat.KILLS),
                Collections.singletonList(BoardStat.LEVEL)), settings.npc("leaderboardplayer").boards(),
                "the kills in the middle, on the point");
        assertFalse(ranked.leaderboard());
        assertNull(LobbySettings.load(withModeNpcs()).npc("ranked").subtitle(), "an NPC of modes: its name and its queue");
        assertEquals(LobbyNpc.Kind.STATS, settings.npc("statsjoueur1").kind());
        assertEquals(LobbyNpc.Kind.STATS, settings.npc("statsjoueur2").kind());
        assertTrue(settings.npc("statsjoueur1").hasBody());
        assertEquals(LobbyNpc.Kind.MODES, ranked.kind());
        LobbyNpc shop = settings.npc("aimtrainer");
        assertEquals(LobbyNpc.Kind.SHOP, shop.kind(), "the centre NPC is the Shop");
        assertEquals("§a§lSHOP", shop.name());
        assertEquals("Starter Pack · VIP", org.bukkit.ChatColor.stripColor(shop.subtitle()), "the pack and VIP: no PREMIUM+ there");
        assertTrue(shop.modeIds().isEmpty());
        assertInvalid("pnj:\n  boutique:\n    type: shop\n    modes: [sumo]\nmodes:\n  sumo: {}\n", "pnj.boutique");
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
        assertInvalid("niveau:\n  couleurs:\n    5: '&l&o&n'\nmodes:\n  sumo: {}\n", "niveau.couleurs.5");
        assertEquals("§c§6§e", LobbySettings.load(yaml("niveau:\n  couleurs:\n    100: '&c&6&e'\nmodes:\n  sumo: {}\n"))
                .levels().colours().get(100), "a rainbow of three colours");
    }

    @Test
    void readsTheKits() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        assertNull(settings.display("sumo").kit(), "a sumo is fought bare-handed");
        assertEquals(Material.DIAMOND_SWORD, settings.display("boxing").kit().item(0).material());
        assertEquals(KitItem.parse("FIREBALL 6"), settings.display("fireball").kit().item(3), "the kit of the fireball maps");
        assertEquals(KitItem.parse("LADDER 16"), settings.display("block-fight-1v1").kit().item(7), "the kit of the block placement maps");
        assertEquals(Material.STONE_SWORD, settings.display("ffa").kit().item(0).material(), "the fight kit, fireballs and a pearl");
        assertEquals(Material.SHEARS, settings.display("ffa").kit().item(6).material(), "and shears for the wool");
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
        assertInvalid("categories:\n  duels:\n    slogan: '&fA slogan far too long to fit above an NPC head'\nmodes:\n"
                + "  sumo:\n    categorie: duels\n", "categories.duels.slogan");
    }

    @Test
    void aCategoryNpcShowsItsSloganUnlessItHasItsOwnLine() throws Exception {
        String config = "categories:\n  duels:\n    nom: '&a&lDuels'\n    slogan: '&fDuel anyone'\n  solo:\n    nom: Solo\n"
                + "modes:\n  sumo:\n    categorie: duels\n  bot:\n    categorie: solo\n"
                + "pnj:\n  a:\n    categorie: duels\n  b:\n    categorie: duels\n    sous-titre: '&eNew modes!'\n"
                + "  c:\n    categorie: solo\n  d:\n    modes: [sumo]\n  e:\n    modes: [sumo]\n    sous-titre: '&7Classic'\n"
                + "accueil: false\n";
        LobbySettings settings = LobbySettings.load(yaml(config));
        assertEquals("§fDuel anyone", settings.npc("a").subtitle());
        assertEquals("§eNew modes!", settings.npc("b").subtitle(), "its own line first");
        assertNull(settings.npc("c").subtitle(), "a category without slogan");
        assertNull(settings.category("solo").slogan());
        assertNull(settings.npc("d").subtitle());
        assertEquals("§7Classic", settings.npc("e").subtitle());
        assertFalse(settings.welcome());
    }

    @Test
    void privateGamesFollowThePartySizeFromTheSmallestMode() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        java.util.List<GameMode> rbw = settings.privateRoutes().get("partie-privee");
        assertEquals(Arrays.asList("ranked-1v1", "ranked-2v2", "ranked-3v3", "ranked-4v4"), ids(rbw));
        assertEquals(Arrays.asList("top-bridge-1v1", "top-bridge-2v2"), ids(settings.privateRoutes().get("top-bridge-prive")));
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
