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
import java.util.ArrayList;
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
     * four ways to play), on points of their own.
     */
    static YamlConfiguration withModeNpcs() throws Exception {
        YamlConfiguration yaml = bundledConfig();
        modeNpc(yaml, "ranked", "ranked", "ranked-1v1", "ranked-2v2", "ranked-3v3", "ranked-4v4");
        modeNpc(yaml, "partieprivee", "prive", "bedwars-normal");
        modeNpc(yaml, "ffa", "ffa", "ffa", "top-bridge-1v1", "top-bridge-2v2", "top-bridge-ffa");
        modeNpc(yaml, "clutchscenario", "clutch", "clutch-scenario-1v1", "clutch-scenario-1v2", "clutch-scenario-1v3",
                "clutch-scenario-1v4");
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
        assertEquals(94, settings.modes().size(), "the six ranked duels (plan 3.7), the invis practice and the Bed Break Simulator in 1v1 to 1v4 (and"
                + " their kits), with Bedwars Normal (its 4s and 2s) and FFA Archway (its game), the clutch scenario, the bed fight (1v1, 2v2, 1v2), Top"
                + " BedFight, the bridge fight 2v2, the full map first rush, Sky Rise Resources, the timed bridge, Lobby"
                + " Fight (1v1, FFA), the Bed Break Simulator, the four modes of FUN PRACTICE (CTF The Bridge 1v1 and"
                + " 2v2, CTF Castle 2v2 and 4v4 with them, Fast Bedwars 1v1 and 2v2), Red Rover (sumo, block edit, block placement, top bridge),"
                + " Block aim and Parkour Duel, without the top fight, the ranked scenarios, the Combo bot or Find Your Sensitivity");
        assertNull(settings.display("find-your-sensitivity"), "no Find Your Sensitivity in the menus (the owner, 22/11, 19h51)");
        assertEquals("entrainement", settings.display("block-aim").category(), "a training of the placement");
        for (String id : Arrays.asList("red-rover-sumo", "red-rover-block-edit", "red-rover-block-placement",
                "red-rover-top-bridge")) {
            GameMode rover = settings.catalog().get(id);
            assertEquals(1, rover.teams(), id + ": one team of three, a party of three joins it together");
            assertEquals(3, rover.playersPerTeam(), id);
            assertEquals("1v1v1", settings.display(id).format(), id + ", as the menus show it");
            assertFalse(rover.ranked(), id);
            assertEquals("duels", settings.display(id).category(), id);
            assertEquals("red-rover", settings.display(id).group(), id + ": the Red Rovers in one item of the Duels");
        }
        assertEquals("block-fight-1v1", settings.kitMode("red-rover-block-placement"),
                "the kit of the block placement, as each player arranged it");
        assertEquals("block-edit-duel", settings.kitMode("red-rover-block-edit"), "the kit of the block edit");
        assertEquals("red-rover-sumo", settings.kitMode("red-rover-sumo"), "the sumo's own (none: the hand)");
        assertEquals("top-bridge-1v1", settings.kitMode("red-rover-top-bridge"), "the kit of Top Bridge 1v1");
        assertEquals("fun", settings.display("fireball-knockback").category(), "the Fireball Fight of FUN PRACTICE");
        GameMode fireball = settings.catalog().get("fireball-knockback");
        assertEquals(1, fireball.teams(), "the Fireball Fight: three players each for themselves (the owner, 22/11, 19h55)");
        assertEquals(3, fireball.playersPerTeam());
        assertEquals("1v1v1", settings.display("fireball-knockback").format(), "as the menus show it");
        assertFalse(fireball.ranked());
        assertEquals("fun", settings.display("snowball-spleef").category());
        assertEquals(Material.SNOW_BALL, settings.display("snowball-spleef").icon());
        assertEquals("fun", settings.display("parkour-duel").category(), "the Parkour Duel of FUN PRACTICE");
        assertEquals(Material.FEATHER, settings.display("parkour-duel").icon());
        assertFalse(settings.catalog().get("parkour-duel").ranked());
        assertNull(settings.display("parkour-duel").kit(), "no kit: the empty hand");
        for (String id : Arrays.asList("ctf-bridge-1v1", "ctf-bridge-2v2")) {
            assertEquals("fun", settings.display(id).category(), id + ": Capture the Flag in FUN PRACTICE");
            assertEquals("ctf-bridge", settings.display(id).group(), id + ": its two sizes in one item");
            assertFalse(settings.catalog().get(id).ranked(), id);
            assertEquals("ctf-bridge-1v1", settings.kitMode(id), id + ": one kit, the one of The Bridge");
        }
        assertEquals(2, settings.catalog().get("ctf-bridge-2v2").playersPerTeam());
        for (String id : Arrays.asList("ctf-castle-2v2", "ctf-castle-4v4")) {
            assertEquals("fun", settings.display(id).category(), id + ": the castles in FUN PRACTICE too");
            assertEquals("ctf-castle", settings.display(id).group(), id + ": its two sizes in one item");
            assertFalse(settings.catalog().get(id).ranked(), id);
            assertEquals(2, settings.catalog().get(id).teams(), id + ": castle against castle");
        }
        assertEquals(2, settings.catalog().get("ctf-castle-2v2").playersPerTeam());
        assertEquals(4, settings.catalog().get("ctf-castle-4v4").playersPerTeam());
        for (String id : Arrays.asList("fast-bedwars-1v1", "fast-bedwars-2v2")) {
            assertEquals("fun", settings.display(id).category(), id + ": Fast Bedwars in FUN PRACTICE (plan 6.1)");
            assertEquals("fast-bedwars", settings.display(id).group(), id + ": its two sizes in one item");
            assertFalse(settings.catalog().get(id).ranked(), id);
            assertEquals(2, settings.catalog().get(id).teams(), id);
        }
        assertEquals(1, settings.catalog().get("fast-bedwars-1v1").playersPerTeam());
        assertEquals(2, settings.catalog().get("fast-bedwars-2v2").playersPerTeam());
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
    void oneGameKeepsOneItemInAllItsModes() throws Exception {
        // the owner's ask of 22/11: a game played in several modes (FFA, duel, practice, ranked,
        // Red Rover) shows the same item everywhere, so that players find it again
        LobbySettings settings = LobbySettings.load(bundledConfig());
        String[][] games = {
            {"ranked-1v1", "ranked-2v2", "ranked-3v3", "ranked-4v4"},
            {"bed-fight-1v1", "bed-fight-2v2", "bed-fight-1v2", "ranked-bed-fight-1v1"},
            {"top-bridge-1v1", "top-bridge-2v2", "top-bridge-ffa", "ranked-top-bridge-1v1", "red-rover-top-bridge"},
            {"bridge-fight", "bridge-fight-1v1", "bridge-fight-2v2", "ranked-bridge-fight-1v1"},
            {"block-fight-1v1", "block-fight-2v2", "block-fight-arena", "ranked-block-fight-1v1", "red-rover-block-placement"},
            {"lobby-fight-ffa", "lobby-fight-1v1"},
            {"sumo", "ranked-sumo", "red-rover-sumo", "event-sumo"},
            {"boxing", "ranked-boxing"},
            {"block-edit", "block-edit-duel", "red-rover-block-edit"},
            {"kb-displace", "kb-displace-bot"},
            {"fireball", "fireball-knockback", "fireball-practice"},
            {"bow-duel", "bow-training", "aim-trainer"},
        };
        for (String[] game : games) {
            Material first = settings.display(game[0]).icon();
            for (String id : game) {
                assertEquals(first, settings.display(id).icon(), id + ": the item of " + game[0]);
            }
        }
        assertEquals(Material.BED, settings.group("ranked").icon(), "the group of Ranked Bedwars too");
        assertEquals(Material.ENDER_PEARL, settings.display("lobby-fight-ffa").icon(), "Lobby Fight: the pearl of its kit");
        assertEquals(Material.BOW, settings.display("bow-duel").icon());
    }

    @Test
    void groupsTheBundledModesByCategory() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        assertEquals(8, settings.categories().size());
        assertEquals("entrainement", settings.categories().get(0).id(), "left to right: the practice on the left");
        assertEquals("ranked", settings.categories().get(1).id());
        assertEquals("duels", settings.categories().get(2).id(), "the duels");
        assertEquals(Material.IRON_SWORD, settings.categories().get(2).icon());
        assertEquals("arenes", settings.categories().get(3).id(), "the arenas on the right");
        assertEquals("§c§lArenas", settings.categories().get(3).name());
        assertEquals(Material.DIAMOND_SWORD, settings.categories().get(3).icon());
        assertEquals("fun", settings.categories().get(4).id(), "FUN PRACTICE (plan 6.1), after the arenas");
        assertEquals("§d§lFun Practice", settings.categories().get(4).name());
        assertEquals("trois-joueurs", settings.categories().get(5).id(), "3 Players (plan 6.6): in the compass only");
        // plan 3.7: the Solo categories, then Party and Ranked, side by side in the top row of the compass
        assertEquals("party", settings.categories().get(6).id(), "Party, after the Solo categories");
        assertEquals("§9§lParty", settings.categories().get(6).name());
        assertTrue(settings.categories().get(6).party(), "the category of the parties");
        assertEquals("classe", settings.categories().get(7).id(), "Ranked, last");
        assertEquals("§6§lRanked", settings.categories().get(7).name());
        assertFalse(settings.categories().get(7).party());
        // Four ways to play: train alone, the RBW modes (not all ranked: no bed on it), duel, the arenas.
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
        for (String arena : Arrays.asList("top-bridge-ffa", "ffa", "lobby-fight-ffa", "free-ffa", "bridge-fight",
                "block-fight-arena")) {
            assertEquals("arenes", settings.display(arena).category(), arena + ": no queue, in the Arenas");
            assertNull(settings.display(arena).group(), arena + ": one item each, side by side");
        }
        assertEquals(Arrays.asList("top-bridge-ffa", "ffa", "lobby-fight-ffa", "free-ffa", "bridge-fight", "block-fight-arena"),
                displayIds(settings.modesIn("arenes")), "the six arenas, nothing else");
        assertEquals("duels", settings.display("lobby-fight-1v1").category(), "Lobby Fight 1v1: a duel");
        assertNull(settings.display("lobby-fight-1v1").kit(), "no kit of its own: Lobby Fight FFA's");
        assertEquals("lobby-fight-ffa", settings.kitMode("lobby-fight-1v1"), "one kit for both Lobby Fights (kit-de:)");
        Kit lobbyFfa = settings.display("lobby-fight-ffa").kit();
        assertEquals(Material.STONE_SWORD, lobbyFfa.item(0).material(), "Lobby Fight: a sword");
        assertEquals(KitItem.parse("GOLDEN_APPLE 5"), lobbyFfa.item(1), "5 golden apples");
        assertEquals(KitItem.parse("FIREBALL 3"), lobbyFfa.item(2), "fireballs");
        assertEquals(KitItem.parse("ENDER_STONE 12"), lobbyFfa.item(3), "12 end stone");
        assertEquals(Material.WOOD_PICKAXE, lobbyFfa.item(4).material(), "a wooden pickaxe");
        assertEquals(Material.ENDER_PEARL, lobbyFfa.item(5).material(), "the pearl of the 1v1 of before");
        assertEquals(KitItem.parse("WOOL 64"), lobbyFfa.item(6), "its wool");
        assertEquals(KitItem.parse("WOOL 64"), lobbyFfa.item(7), "2 stacks");
        assertEquals(Material.SHEARS, lobbyFfa.item(8).material(), "and its shears");
        assertNull(lobbyFfa.item(9), "nothing more");
        assertEquals("entrainement", settings.display("kb-displace-bot").category(), "against a bot: practice");
        int total = 0;
        for (ModeCategory category : settings.categories()) {
            int count = settings.modesIn(category.id()).size();
            int items = settings.entriesIn(category.id()).size();
            if (category.party()) {
                assertEquals(0, count, "Party has no modes of its own: those of the viewer's party, at its size");
                continue;
            }
            assertTrue(count > 0 && items <= LobbySettings.MAX_MODES_PER_CATEGORY, category.id() + ": " + items);
            for (ModeDisplay display : settings.modes()) {
                // its own modes: those it shows too (aussi:) are of another category
                total += category.id().equals(display.category()) ? 1 : 0;
            }
        }
        assertEquals("ranked", settings.display("attack-defense-1v1").category(), "Attack and Defense: in the RBW modes");
        assertEquals("ranked", settings.display("clutch-scenario-1v2").category());
        for (int against = 1; against <= 4; against++) {
            // as Invis Practice and the Bed Break Simulator: 1v1 to 1v4 (the owner's choice, 22/11)
            GameMode clutch = settings.display("clutch-scenario-1v" + against).mode();
            assertEquals(against + 1, clutch.playersPerMatch(), clutch.id());
            assertEquals("clutch-scenario", settings.display(clutch.id()).group());
        }
        for (String gone : Arrays.asList("clutch-scenario-2v2", "clutch-scenario-3v3", "clutch-scenario-4v4")) {
            assertNull(settings.display(gone), gone + ": now 1v2 to 1v4");
        }
        assertEquals("ranked", settings.display("bed-break-simulator-1v3").category(), "the Bed Break Simulator: an RBW situation");
        assertEquals("bed-break", settings.display("bed-break-simulator-1v3").group(),
                "its sizes as one item, beside Attack and Defense");
        assertEquals("invis", settings.display("invis-practice-1v1").group());
        LobbySettings modeNpcs = LobbySettings.load(withModeNpcs());
        assertEquals(Arrays.asList("clutch-scenario-1v1", "clutch-scenario-1v2", "clutch-scenario-1v3",
                "clutch-scenario-1v4"), modeNpcs.npc("clutchscenario").modeIds(), "one NPC for Clutch Scenario");
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
        List<LobbySettings.Entry> ranked = settings.entriesIn("classe");
        assertEquals("ranked", ranked.get(0).group.id(), "Ranked Bedwars first, in Ranked (plan 3.7)");
        assertEquals(Arrays.asList("ranked-1v1", "ranked-2v2", "ranked-3v3", "ranked-4v4"),
                displayIds(settings.modesOfGroup("ranked")));
        assertEquals(Arrays.asList("top-bridge-1v1", "top-bridge-2v2"), displayIds(settings.modesOfGroup("top-bridge")));
        assertEquals(Arrays.asList("block-fight-1v1", "block-fight-2v2"), displayIds(settings.modesOfGroup("block-placement")));
        assertEquals(Arrays.asList("bridge-fight-1v1", "bridge-fight-2v2"), displayIds(settings.modesOfGroup("bridge-fight")));
        assertEquals(Arrays.asList("bed-fight-1v1", "bed-fight-2v2", "bed-fight-1v2"),
                displayIds(settings.modesOfGroup("bed-fight")), "the 1v2 with them, its player alone with a better stuff");
        assertEquals(Arrays.asList("red-rover-sumo", "red-rover-block-edit", "red-rover-block-placement",
                "red-rover-top-bridge"), displayIds(settings.modesOfGroup("red-rover")), "the Red Rovers as one item of the Duels");
        List<LobbySettings.Entry> duels = settings.entriesIn("duels");
        assertEquals("bed-fight", duels.get(0).group.id(), "the duels: 1v1 or 2v2 in one item each");
        assertEquals("top-bed-fight-1v1", duels.get(1).mode.mode().id(), "Top BedFight next to the bed fight, in 1v1 only");
        assertEquals("top-bridge", duels.get(2).group.id());
        assertEquals("bridge-fight", duels.get(3).group.id());
        assertEquals("block-placement", duels.get(4).group.id());
        assertEquals("lobby-fight-1v1", duels.get(5).mode.mode().id(), "Lobby Fight after the block placement");
        assertTrue(ranked.size() <= 14, "two rows at most: " + ranked.size());
        assertNull(settings.display("top-fight-1v1"), "no top fight: there are bridge fight and top bridge");
        assertNull(settings.display("combo-bot"), "no Combo bot in the menus (the owner, 22/11)");
        assertNull(settings.privateRoutes().get("top-fight-prive"));
        assertEquals(Arrays.asList("attack-defense-1v1", "attack-defense-2v2"),
                displayIds(settings.modesOfGroup("attack-defense")));
        assertNull(settings.privateRoutes().get("top-bridge-prive"), "replaced by Bedwars Normal (the owner, 22/11)");
        assertTrue(LobbySettings.load(withModeNpcs()).npc("partieprivee").modeIds().contains("bedwars-normal"));
        assertTrue(settings.display("top-bridge-1v1").kit() != null, "the kit of the rounds, arranged in the kit editor");
        assertEquals(Material.STONE_SWORD, settings.display("top-bridge-ffa").kit().item(0).material(), "the fight kit");
        int shown = 0;
        for (ModeCategory category : settings.categories()) {
            List<LobbySettings.Entry> entries = settings.entriesIn(category.id());
            // its own items: those it shows too (aussi:, one each, last) are counted in their categories
            for (LobbySettings.Entry entry : entries.subList(0, entries.size() - category.also().size())) {
                shown += entry.group != null ? settings.modesOfGroup(entry.group.id()).size() : 1;
            }
        }
        for (String event : Arrays.asList("event-sumo", "event-brackets", "event-lms")) {
            assertTrue(settings.display(event).hidden(), "the events of /host are not in the selector: " + event);
        }
        for (String holder : Arrays.asList("invis-practice", "bed-break-simulator")) {
            assertTrue(settings.display(holder).hidden(), "a kit of the sizes of a game, not in the selector: " + holder);
        }
        for (String size : Arrays.asList("bedwars-normal-4s", "bedwars-normal-2s")) {
            assertTrue(settings.display(size).hidden(), "played only from Bedwars Normal, not in the selector: " + size);
        }
        assertTrue(settings.display("ffa-archway-partie").hidden(), "played only from FFA Archway, not in the selector");
        assertEquals(settings.modes().size() - 8, shown,
                "every mode but the hidden ones (the 3 events, the 2 kits of sizes, the 4s and 2s of Bedwars Normal, the game"
                        + " of FFA Archway) is in the selector, once");
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
        List<LobbySettings.Entry> privateGames = settings.entriesOf(settings.npc("partieprivee").modeIds());
        assertEquals(1, privateGames.size(), "Bedwars Normal alone, its 4s and 2s hidden");
        assertEquals("bedwars-normal", privateGames.get(0).mode.mode().id(), "a group of one: the mode itself");
    }

    @Test
    void theCompassPutsRankedPartyRedRoverAndThreePlayersOnTop() throws Exception {
        // the owner's ask of 22/11 (19h51): Ranked, Party, Red Rover (a group) and 3 Players on top
        LobbySettings settings = LobbySettings.load(bundledConfig());
        assertEquals(Arrays.asList("classe", "party", "red-rover", "trois-joueurs"), settings.compassTop());
        assertNotNull(settings.group("red-rover"), "Red Rover is a group, which the top row opens");
        assertNull(settings.category("red-rover"));
        List<String> middle = new ArrayList<>();
        for (ModeCategory category : settings.categories()) {
            if (!settings.compassTop().contains(category.id())) {
                middle.add(category.id());
            }
        }
        assertEquals(Arrays.asList("entrainement", "ranked", "duels", "arenes", "fun"), middle,
                "Practice, RBW Modes, Duels, Arenas and Fun Practice in the centre, in their order");
        YamlConfiguration without = bundledConfig();
        without.set("boussole", null);
        assertEquals(Collections.emptyList(), LobbySettings.load(without).compassTop(), "without boussole: the compass as before");
        YamlConfiguration other = bundledConfig();
        other.set("boussole.haut", Arrays.asList("red-rover", "fun"));
        assertEquals(Arrays.asList("red-rover", "fun"), LobbySettings.load(other).compassTop(), "a group, then a category");
        for (Object[] wrong : new Object[][] {{Arrays.asList("nope"), "boussole.haut: \"nope\" is neither"},
                {Arrays.asList("party", "party"), "boussole.haut: \"party\" twice"}}) {
            YamlConfiguration yaml = bundledConfig();
            yaml.set("boussole.haut", wrong[0]);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> LobbySettings.load(yaml));
            assertTrue(error.getMessage().startsWith((String) wrong[1]), error.getMessage());
        }
    }

    @Test
    void aNewPlayerStartsWithTheBlockClutch() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        List<String> start = new java.util.ArrayList<>();
        for (ModeDisplay display : settings.modes()) {
            if (display.startHere()) {
                start.add(display.mode().id());
            }
        }
        assertEquals(Collections.singletonList("clutch"), start, "\"Start here\" on the block clutch only");
        assertEquals("clutch", settings.display("clutch").group(), "in its group: the group shines too");
        assertFalse(LobbySettings.load(yaml("modes:\n  sumo: {}\n")).display("sumo").startHere(), "not by default");
        assertTrue(LobbySettings.load(yaml("modes:\n  sumo:\n    commencer-ici: true\n")).display("sumo").startHere());
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
        assertEquals(17, settings.npcs().size(), "five NPCs of modes, one per way to play (Fun Practice on the point"
                + " of the Private game NPC before); the shop, the stats, the leaderboard selector, the boards and the wall"
                + " of the week; qRago and kaliso_dev; Rago Client and Rago Optimizer (the moderators jncl and nativ out"
                + " since 22/11, 17h56)");
        for (String gone : Arrays.asList("ranked", "ffa", "clutchscenario", "attackanddefense", "rbwsolo1v1ou2v2skyrise",
                "firstrushnebucgelatosoloduo", "invispracticearwhayinvasion", "realisticplat",
                "blockplacementarena", "attackdefenseversionrbw", "blockinpractice", "partieprivee", "funmod2", "jncl", "nativ")) {
            assertNull(settings.npc(gone), gone + ": its modes stay in the compass");
        }
        List<String> categories = new java.util.ArrayList<>();
        for (LobbyNpc npc : settings.npcs()) {
            if (npc.kind() == LobbyNpc.Kind.MODES) {
                assertNotNull(npc.category(), npc.point() + ": only the NPCs of the five categories");
                categories.add(npc.category());
            }
        }
        assertEquals(Arrays.asList("entrainement", "ranked", "duels", "arenes", "fun"), categories,
                "Practice, RBW Modes, Duels, Arenas, Fun Practice");
        // the owner's ask of 22/11: the Fun Practice NPC in the place of the Private game NPC, on its point
        LobbyNpc fun = settings.npc("prive");
        assertEquals("§d§lFun Practice", fun.name());
        assertEquals("fun", fun.category());
        assertEquals("fun", fun.skin(), "the skin of the Fun Practice (22/11)");
        assertEquals(displayIds(settings.modesIn("fun")), fun.modeIds(), "it holds the modes of FUN PRACTICE");
        assertEquals(Arrays.asList("bedwars-normal", "ffa-archway"), displayIds(settings.modesOfGroup("prive")),
                "the private games stay in the RBW Modes, one item: Bedwars Normal in the place of the two of before, and FFA"
                        + " Archway (23/11)");
        assertTrue(settings.entriesIn("ranked").stream().anyMatch(entry -> entry.group != null && "prive".equals(entry.group.id())));
        LobbyNpc arenas = settings.npc("arenas");
        assertEquals("§c§lArenas", arenas.name());
        assertEquals("arena", arenas.skin());
        assertEquals(displayIds(settings.modesIn("arenes")), arenas.modeIds(), "the Arenas NPC holds the five arenas");
        LobbyNpc duels = settings.npc("duels");
        assertEquals("duels", duels.category());
        assertEquals("§a§lDuels", duels.name());
        assertEquals("§aDuel your friends §7or §aanyone", duels.subtitle(), "under a category NPC, the slogan of its category,"
                + " in the colours of its category");
        assertEquals("§bTrain alone§7, at your §bown pace", settings.npc("practice").subtitle());
        assertEquals("entrainement", settings.npc("practice").category());
        assertEquals(settings.modesIn("duels").size(), duels.modeIds().size(), "a category NPC holds its modes");
        assertTrue(duels.modeIds().contains("kb-displace"));
        LobbyNpc ranked = settings.npc("rbw");
        assertEquals("§6§lRBW Modes", ranked.name());
        assertEquals("§6RBW situations §7to practice", ranked.subtitle());
        assertEquals("ranked", ranked.category());
        assertTrue(ranked.modeIds().containsAll(Arrays.asList("skyrise-1v1", "clutch-scenario-1v1", "attack-defense-1v1",
                "bed-break-simulator-1v1")), "every RBW mode");
        assertFalse(ranked.modeIds().contains("ranked-1v1"), "Ranked Bedwars is in Ranked (plan 3.7), a tab of the compass"
                + " the NPC opens");
        assertEquals(settings.modesIn("ranked").size(), ranked.modeIds().size());
        assertTrue(settings.npc("top").leaderboard());
        assertEquals(Collections.singletonList(Arrays.asList(BoardStat.LEVEL, BoardStat.ELO, BoardStat.FKDR, BoardStat.KILLS, BoardStat.GAMES)),
                settings.npc("top").boards(), "one board, the Network level first (the podium too), a right"
                + " click goes to the next stat");
        assertEquals(Arrays.asList(Collections.singletonList(BoardStat.WINS), Collections.singletonList(BoardStat.STREAK),
                Collections.singletonList(BoardStat.GAMES)), settings.npc("leaderboards").boards());
        assertEquals(Arrays.asList(Collections.singletonList(BoardStat.STREAK), Collections.singletonList(BoardStat.KILLS),
                Collections.singletonList(BoardStat.LEVEL)), settings.npc("joueurs").boards(),
                "the kills in the middle, on the point");
        assertNull(settings.npc("joueurs").period(), "each player chooses the period");
        LobbyNpc week = settings.npc("semaine");
        assertEquals(StatsPeriod.WEEK, week.period(), "the wall of the week stays on the week");
        assertEquals(Arrays.asList(Collections.singletonList(BoardStat.WINS), Collections.singletonList(BoardStat.STREAK),
                Arrays.asList(BoardStat.KILLS, BoardStat.GAMES)), week.boards());
        assertFalse(ranked.leaderboard());
        assertNull(LobbySettings.load(withModeNpcs()).npc("ranked").subtitle(), "an NPC of modes: its name and its queue");
        assertEquals(LobbyNpc.Kind.STATS, settings.npc("profil").kind());
        assertEquals(LobbyNpc.Kind.STATS, settings.npc("profil2").kind());
        assertTrue(settings.npc("profil").hasBody());
        assertEquals(LobbyNpc.Kind.MODES, ranked.kind());
        LobbyNpc shop = settings.npc("shop");
        assertEquals(LobbyNpc.Kind.SHOP, shop.kind(), "the centre NPC is the Shop");
        assertEquals("§a§lSHOP", shop.name());
        assertEquals("Starter Pack · VIP", org.bukkit.ChatColor.stripColor(shop.subtitle()), "the pack and VIP: no PREMIUM+ there");
        assertTrue(shop.modeIds().isEmpty());
        assertInvalid("pnj:\n  boutique:\n    type: shop\n    modes: [sumo]\nmodes:\n  sumo: {}\n", "pnj.boutique");
    }

    @Test
    void aWallFixesThePeriodOfItsBoards() throws Exception {
        assertEquals(StatsPeriod.DAY, LobbySettings.load(yaml("classements:\n  mur: {tableaux: [kills], periode: jour}\n"
                + "modes:\n  sumo: {}\n")).npc("mur").period());
        assertEquals(StatsPeriod.ALL, LobbySettings.load(yaml("classements:\n  mur: {tableaux: [kills], periode: toujours}\n"
                + "modes:\n  sumo: {}\n")).npc("mur").period());
        assertInvalid("classements:\n  mur: {tableaux: [kills], periode: mois}\nmodes:\n  sumo: {}\n", "classements.mur.periode");
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
        assertEquals(Collections.singletonMap(3, "§a"), levels.colours().headMap(NetworkLevel.PRESTIGE + 1));
        assertEquals(NetworkLevel.DEFAULT.colours().tailMap(NetworkLevel.PRESTIGE + 1), levels.colours().tailMap(
                NetworkLevel.PRESTIGE + 1), "the prestiges of the plugin when left out");
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
        NetworkLevel none = LobbySettings.load(yaml("niveau:\n  prestiges: {}\nmodes:\n  sumo: {}\n")).levels();
        assertEquals(NetworkLevel.DEFAULT.colours().headMap(NetworkLevel.PRESTIGE + 1), none.colours(), "{}: no prestige");
        NetworkLevel mine = LobbySettings.load(yaml("niveau:\n  prestiges:\n    300: {couleur: '&a&b', points: 5}\n"
                + "    500: {points: 5}\nmodes:\n  sumo: {}\n")).levels();
        assertEquals("§a§b", mine.colours().get(300));
        assertNull(mine.colours().get(500), "a prestige without a couleur keeps the look");
        assertEquals("§a[§b345§a" + NetworkLevel.STAR + "]", mine.tag(345));
        assertInvalid("niveau:\n  couleurs:\n    200: '&a'\nmodes:\n  sumo: {}\n", "niveau.couleurs.200");
        assertInvalid("niveau:\n  prestiges:\n    250: {couleur: '&a'}\nmodes:\n  sumo: {}\n", "niveau.prestiges.250");
        assertInvalid("niveau:\n  prestiges:\n    200: {couleur: '&a[x]'}\nmodes:\n  sumo: {}\n", "niveau.prestiges.200.couleur");
        assertInvalid("niveau:\n  prestiges: false\nmodes:\n  sumo: {}\n", "niveau.prestiges");
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
        assertEquals(Material.GOLD_PICKAXE, settings.display("bed-break-simulator").kit().item(1).material(),
                "the Bed Break Simulator: the slots of the items of its three attack kits");
        assertEquals(KitItem.parse("LADDER 8"), settings.display("bed-break-simulator").kit().item(6));
        assertEquals(Material.STICK, settings.display("bed-break-simulator").kit().item(7).material());
        assertEquals(Material.IRON_AXE, settings.display("bed-break-simulator").kit().item(8).material(),
                "the axe and the shears of the three kits, last: the layouts saved before them stay as they were");
        assertEquals(Material.SHEARS, settings.display("bed-break-simulator").kit().item(9).material());

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
    void theThreePlayersCategoryShowsModesOfOtherCategories() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        ModeCategory three = settings.category("trois-joueurs");
        assertEquals("§e§l3 Players", three.name());
        assertEquals(Material.SKULL_ITEM, three.icon());
        List<LobbySettings.Entry> entries = settings.entriesIn("trois-joueurs");
        assertEquals(5, entries.size(), "Red Rover as one item, then the four 1v2");
        assertEquals("red-rover", entries.get(0).group.id());
        assertEquals("bed-break-simulator-1v2", entries.get(1).mode.mode().id());
        assertEquals("invis-practice-1v2", entries.get(2).mode.mode().id());
        assertEquals("clutch-scenario-1v2", entries.get(3).mode.mode().id());
        assertEquals("bed-fight-1v2", entries.get(4).mode.mode().id());
        assertEquals(Arrays.asList("red-rover-sumo", "red-rover-block-edit", "red-rover-block-placement", "red-rover-top-bridge",
                "bed-break-simulator-1v2", "invis-practice-1v2", "clutch-scenario-1v2", "bed-fight-1v2"),
                displayIds(settings.modesIn("trois-joueurs")));
        // the same modes and queues: they stay in their categories
        assertEquals("duels", settings.display("red-rover-sumo").category());
        assertEquals("ranked", settings.display("bed-break-simulator-1v2").category());
        assertEquals("invis", settings.display("invis-practice-1v2").group());
        assertTrue(displayIds(settings.modesIn("duels")).contains("red-rover-top-bridge"));
        for (LobbyNpc npc : settings.npcs()) {
            assertFalse("trois-joueurs".equals(npc.category()), "in the compass only, no NPC: " + npc.name());
        }

        String config = "categories:\n  duels:\n    nom: Duels\n  trois:\n    nom: Trois\n    aussi: [%s]\n"
                + "groupes:\n  rover: {}\nmodes:\n  sumo:\n    categorie: duels\n    groupe: rover\n"
                + "  boxing:\n    categorie: duels\n    groupe: rover\n  bot:\n    categorie: trois\n"
                + "  hid:\n    categorie: duels\n    cache: true\n";
        LobbySettings small = LobbySettings.load(yaml(String.format(config, "rover")));
        assertEquals("bot", small.entriesIn("trois").get(0).mode.mode().id(), "its own first");
        assertEquals("rover", small.entriesIn("trois").get(1).group.id(), "then the group, as one item");
        assertEquals(Arrays.asList("bot", "sumo", "boxing"), displayIds(small.modesIn("trois")));
        assertEquals(Arrays.asList("sumo", "boxing", "hid"), displayIds(small.modesIn("duels")), "the duels as before");
        assertEquals("sumo", LobbySettings.load(yaml(String.format(config, "Sumo"))).entriesIn("trois").get(1).mode.mode().id(),
                "a mode alone");
        assertInvalid(String.format(config, "nothing"), "categories.trois.aussi");
        assertInvalid(String.format(config, "sumo, sumo"), "categories.trois.aussi");
        assertInvalid(String.format(config, "bot"), "categories.trois.aussi");
        assertInvalid(String.format(config, "hid"), "categories.trois.aussi");
    }

    @Test
    void rankedHoldsEveryRankedModeWithItsOwnElo() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        List<LobbySettings.Entry> entries = settings.entriesIn("classe");
        assertEquals(7, entries.size(), "Ranked Bedwars as one item, then the six great duels in 1v1 (the owner's choice, 22/11)");
        assertEquals("ranked", entries.get(0).group.id());
        String[][] twins = {{"ranked-bed-fight-1v1", "bed-fight-1v1"}, {"ranked-top-bridge-1v1", "top-bridge-1v1"},
            {"ranked-bridge-fight-1v1", "bridge-fight-1v1"}, {"ranked-block-fight-1v1", "block-fight-1v1"},
            {"ranked-sumo", "sumo"}, {"ranked-boxing", "boxing"}};
        for (int i = 0; i < twins.length; i++) {
            String id = twins[i][0];
            String solo = twins[i][1];
            assertEquals(id, entries.get(i + 1).mode.mode().id());
            GameMode mode = settings.catalog().get(id);
            assertTrue(mode.ranked(), id + ": its own ELO");
            assertEquals(2, mode.teams(), id);
            assertEquals(1, mode.playersPerTeam(), id + ": in 1v1");
            assertFalse(settings.catalog().get(solo).ranked(), solo + ": the mode of Solo stays unranked");
            assertEquals("duels", settings.display(solo).category(), solo + ": still in Duels");
            assertNull(settings.display(id).kit(), id + ": no kit of its own");
            if (settings.display(solo).kit() != null) {
                assertEquals(solo, settings.kitMode(id), id + ": the kit of its mode of Solo, as each player arranged it");
            } else {
                assertEquals("sumo", solo, "the sumo, the only one without a kit");
            }
        }
        for (ModeDisplay display : settings.modes()) {
            if (display.mode().ranked()) {
                assertEquals("classe", display.category(), display.mode().id() + ": every ranked mode in Ranked");
            }
        }
        assertEquals("ranked", settings.display("skyrise-1v1").category(), "the RBW situations stay in RBW Modes");
    }

    @Test
    void partyShowsWhatAPartyPlaysAgainstItself() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        assertTrue(settings.category("party").party());
        assertTrue(settings.modesIn("party").isEmpty(), "no modes of its own");
        GameMode bedFight = settings.catalog().get("bed-fight-1v1");
        assertTrue(ModeMenu.fitsParty(bedFight, 2), "two players fill a 1v1: a private game");
        assertFalse(ModeMenu.fitsParty(bedFight, 3));
        assertFalse(ModeMenu.fitsParty(bedFight, 1), "alone: no party");
        GameMode bedFight2v2 = settings.catalog().get("bed-fight-2v2");
        assertFalse(ModeMenu.fitsParty(bedFight2v2, 2), "two players are one team of a 2v2: they queue, from Duels");
        assertTrue(ModeMenu.fitsParty(bedFight2v2, 4));
        assertTrue(ModeMenu.fitsParty(settings.catalog().get("bed-fight-1v2"), 3), "three players: the 1v2s");
        assertFalse(ModeMenu.fitsParty(settings.catalog().get("ranked-sumo"), 2), "a private game is never ranked");
        assertFalse(ModeMenu.fitsParty(settings.catalog().get("ranked-1v1"), 2), "Ranked Bedwars: through the private game");

        String config = "categories:\n  duels:\n    nom: Duels\n  party:\n    nom: Party\n    party: true\n%s"
                + "modes:\n  sumo:\n    categorie: %s\n";
        assertTrue(LobbySettings.load(yaml(String.format(config, "", "duels"))).category("party").party());
        assertFalse(LobbySettings.load(yaml(String.format(config, "", "duels"))).category("duels").party(), "false by default");
        assertInvalid(String.format(config, "", "party"), "modes.sumo.categorie");
        assertInvalid(String.format(config.replace("    party: true\n", "    party: true\n    aussi: [sumo]\n"), "", "duels"),
                "categories.party.aussi");
        assertInvalid(String.format(config, "  other:\n    party: true\n", "duels"), "categories.other.party");
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
        // the owner's answers of 22/11: the Private game and the Private top bridge replaced
        assertNull(settings.privateRoutes().get("partie-privee"));
        assertNull(settings.display("partie-privee"));
        assertNull(settings.display("top-bridge-prive"));
        assertNull(settings.privateRoutes().get("ranked-1v1"));

        LobbySettings unordered = LobbySettings.load(yaml("parties-privees:\n  prive: [duo, solo]\nmodes:\n  prive: {}\n"
                + "  solo: {equipes: 2, joueurs-par-equipe: 1}\n  duo: {equipes: 2, joueurs-par-equipe: 2}\n"));
        assertEquals(Arrays.asList("solo", "duo"), ids(unordered.privateRoutes().get("prive")));
    }

    @Test
    void bedwarsNormalHasItsPlayersChooseTheirTeams() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        // the owner's answers of 22/11: the maps of 4s and of 2s; 30 seconds to choose the teams (23/11, 20 before)
        assertEquals(Integer.valueOf(30), settings.teamRooms().get("bedwars-normal"));
        assertEquals(Arrays.asList("bedwars-normal-4s", "bedwars-normal-2s"), ids(settings.privateRoutes().get("bedwars-normal")),
                "a row of maps for each, the 4s first");
        GameMode fours = settings.catalog().get("bedwars-normal-4s");
        GameMode twos = settings.catalog().get("bedwars-normal-2s");
        assertEquals(Arrays.asList(4, 4, 8, 2), Arrays.asList(fours.teams(), fours.playersPerTeam(), twos.teams(), twos.playersPerTeam()));
        assertEquals(16, fours.playersPerMatch(), "4 x 4");
        assertEquals(16, twos.playersPerMatch(), "2 x 8");
        assertFalse(fours.ranked() || twos.ranked(), "in no stats");
        assertTrue(settings.display("bedwars-normal-4s").hidden() && settings.display("bedwars-normal-2s").hidden(),
                "not in the selector: their maps after the click");
        assertEquals("4s", settings.display("bedwars-normal-4s").format());
        assertEquals("2s", settings.display("bedwars-normal-2s").format());
        assertNull(settings.teamRooms().get("ranked-1v1"));

        LobbySettings short_ = LobbySettings.load(yaml("parties-privees-equipes:\n  prive: {cartes: [duo]}\nmodes:\n"
                + "  prive: {}\n  duo: {equipes: 2, joueurs-par-equipe: 2}\n"));
        assertEquals(Integer.valueOf(30), short_.teamRooms().get("prive"), "30 seconds by default");
    }

    @Test
    void ffaArchwayIsTheFfaOfAPartyAlone() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        // the owner's choice of 23/11: the FFA of Archway with the party alone, each for themselves
        assertEquals(Arrays.asList("ffa-archway-partie"), ids(settings.privateRoutes().get("ffa-archway")));
        assertNull(settings.teamRooms().get("ffa-archway"), "no team to choose: each for themselves");
        assertEquals("§6FFA Archway", settings.display("ffa-archway").mode().name());
        GameMode game = settings.catalog().get("ffa-archway-partie");
        assertEquals(Arrays.asList(16, 1), Arrays.asList(game.teams(), game.playersPerTeam()), "16 teams of one");
        assertEquals(PartyBook.MAX_SIZE, game.playersPerMatch(), "a full party fills it");
        assertFalse(game.ranked(), "in no stats");
        assertEquals("ffa", settings.kitMode("ffa-archway-partie"), "the kit of the FFA, as each player arranged it");
        assertTrue(QueueController.eachForThemselves(game));
        assertFalse(QueueController.takesParty(game, 1), "alone: no party");
        assertTrue(QueueController.takesParty(game, 2));
        assertTrue(QueueController.takesParty(game, 7), "any size, one player in each team");
        assertTrue(QueueController.takesParty(game, 16));
        assertFalse(QueueController.takesParty(game, 17));
        GameMode duel = settings.catalog().get("ranked-1v1");
        assertFalse(QueueController.eachForThemselves(duel), "a 1v1 is played at its size");
        assertTrue(QueueController.takesParty(duel, 2));
        assertFalse(QueueController.takesParty(duel, 3));
        assertFalse(QueueController.eachForThemselves(settings.catalog().get("bedwars-normal-4s")), "4 teams of 4");
        assertFalse(QueueController.eachForThemselves(settings.catalog().get("ffa")), "the open FFA: one team, joined one by one");
    }

    @Test
    void refusesWrongPrivateGamesWithTeamsChosen() {
        String modes = "modes:\n  prive: {}\n  duo: {equipes: 2, joueurs-par-equipe: 2}\n  solo: {equipes: 1}\n";
        assertInvalid("parties-privees-equipes:\n  prive: {cartes: [autre]}\n" + modes, "parties-privees-equipes.prive.cartes");
        assertInvalid("parties-privees-equipes:\n  prive: {cartes: [solo]}\n" + modes, "parties-privees-equipes.prive.cartes");
        assertInvalid("parties-privees-equipes:\n  prive: {cartes: []}\n" + modes, "parties-privees-equipes.prive.cartes");
        assertInvalid("parties-privees-equipes:\n  prive: {cartes: [duo], compte-a-rebours: 0}\n" + modes,
                "parties-privees-equipes.prive.compte-a-rebours");
        assertInvalid("parties-privees-equipes:\n  autre: {cartes: [duo]}\n" + modes, "parties-privees-equipes.autre");
        assertInvalid("parties-privees-equipes:\n  prive: [duo]\n" + modes, "parties-privees-equipes.prive");
        assertInvalid("parties-privees:\n  prive: [duo]\nparties-privees-equipes:\n  prive: {cartes: [duo]}\n" + modes,
                "parties-privees-equipes.prive");
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

    @Test
    void theSizesOfAGamePlayTheKitOfOneHiddenMode() throws Exception {
        LobbySettings settings = LobbySettings.load(bundledConfig());
        for (String game : Arrays.asList("invis-practice", "bed-break-simulator")) {
            assertTrue(settings.display(game).hidden(), "the kit holder is not in the selector: " + game);
            assertNotNull(settings.display(game).kit(), "it holds the kit: " + game);
            for (int defenders = 1; defenders <= 4; defenders++) {
                String size = game + "-1v" + defenders;
                ModeDisplay display = settings.display(size);
                assertEquals(game, display.kitFrom(), size);
                assertNull(display.kit(), "no kit of its own: " + size);
                assertEquals("1v" + defenders, display.format(), "1 attacker against " + defenders);
                assertEquals(defenders + 1, display.mode().playersPerTeam(), "one team of " + (defenders + 1));
                assertEquals(1, display.mode().teams());
                assertEquals(game, settings.kitMode(size), "its kit, arranged once for the four sizes");
                assertEquals(game + "/defense", settings.kitMode(size + "/defense"), "and the kit of a role");
            }
        }
        assertEquals("sumo", settings.kitMode("sumo"), "a mode without kit-de: its own kit");
        assertEquals("sumo/x", settings.kitMode("sumo/x"));
        assertNull(settings.kitMode(null));

        List<LobbySettings.Entry> ranked = settings.entriesIn("ranked");
        for (LobbySettings.Entry entry : ranked) {
            if (entry.mode != null) {
                assertFalse(entry.mode.hidden(), "no hidden mode in the selector: " + entry.mode.mode().id());
            }
        }
        List<String> groups = new ArrayList<>();
        for (LobbySettings.Entry entry : ranked) {
            if (entry.group != null) {
                groups.add(entry.group.id());
            }
        }
        assertTrue(groups.containsAll(Arrays.asList("invis", "bed-break")), "each game an item opening its sizes: " + groups);
        assertEquals(Arrays.asList("invis-practice-1v1", "invis-practice-1v2", "invis-practice-1v3", "invis-practice-1v4"),
                displayIds(settings.modesOfGroup("invis")));
        assertTrue(settings.entriesOf(Arrays.asList("invis-practice", "sumo")).size() == 1,
                "an NPC naming the holder does not show it");

        LobbySettings small = LobbySettings.load(yaml("modes:\n  base:\n    cache: true\n    kit: [STICK]\n"
                + "  base-1v2:\n    equipes: 1\n    joueurs-par-equipe: 3\n    format: 1v2\n    kit-de: base\n"));
        assertEquals("1v2", small.display("base-1v2").format());
        assertEquals("base", small.kitMode("base-1v2"));
        assertInvalid("modes:\n  base:\n    kit: [STICK]\n  autre:\n    kit: [STICK]\n    kit-de: base\n",
                "modes.autre: kit or kit-de");
        assertInvalid("modes:\n  base: {}\n  autre:\n    kit-de: base\n", "modes.autre.kit-de");
        assertInvalid("modes:\n  autre:\n    kit-de: personne\n", "modes.autre.kit-de");
        assertInvalid("modes:\n  autre:\n    format: ''\n", "modes.autre.format");
        assertInvalid("modes:\n  autre:\n    format: 1v1-to-1v4-forever\n", "modes.autre.format");
    }

    /** The one mode a config needs. */
    private static final String SUMO = "modes:\n  sumo: {}\n";

    @Test
    void aWallOfTheSelectorFollowsASelectorOnItsOwnPeriod() throws Exception {
        LobbySettings settings = LobbySettings.load(yaml("pnj:\n  choix:\n    type: selecteur\n    stats: [kills, wlr, kills]\n"
                + "    minimum-parties: {jour: 3}\nclassements:\n  mur:\n    selecteur: choix\n    periode: semaine\n" + SUMO));
        LobbyNpc wall = settings.npc("mur");
        assertEquals("choix", wall.selector());
        assertEquals(StatsPeriod.WEEK, wall.period());
        assertEquals(Collections.singletonList(Arrays.asList(BoardStat.KILLS, BoardStat.WLR)), wall.boards(), "once each");
        assertEquals(3, wall.minimumGames(StatsPeriod.DAY));
        assertEquals(LobbySettings.DEFAULT_MINIMUM_WEEK, wall.minimumGames(StatsPeriod.WEEK), "left out: the default");
        assertEquals(0, wall.minimumGames(StatsPeriod.ALL));
        assertEquals("§b§lLEADERBOARDS", settings.npc("choix").name());
        assertNull(settings.npc("choix").subtitle());
        assertInvalid("pnj:\n  choix:\n    type: selecteur\n    stats: [nope]\n" + SUMO, "pnj.choix.stats");
        assertInvalid("pnj:\n  choix:\n    type: selecteur\n    minimum-parties: {jour: -1}\n" + SUMO, "pnj.choix.minimum-parties.jour");
        assertInvalid("classements:\n  mur:\n    selecteur: personne\n    periode: jour\n" + SUMO, "classements.mur.selecteur");
        assertInvalid("pnj:\n  choix:\n    type: selecteur\nclassements:\n  mur:\n    selecteur: choix\n" + SUMO, "classements.mur:");
        assertInvalid("pnj:\n  choix:\n    type: selecteur\nclassements:\n  mur:\n    selecteur: choix\n    periode: jour\n"
                + "    tableaux: [kills]\n" + SUMO, "classements.mur:");
    }

    private static void assertInvalid(String text, String expectedStart) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> LobbySettings.load(yaml(text)));
        assertTrue(error.getMessage().startsWith(expectedStart), error.getMessage());
    }
}
