package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BedwarsSettingsTest {

    @Test
    void teammatesAtTheForgeGetWhatOnePicksUpThere() throws Exception {
        assertEquals(2.5, BedwarsSettings.read(bundled()).forgeShare(), 0.0, "the 5 x 5 blocks around the forge");
        YamlConfiguration config = new YamlConfiguration();
        assertEquals(2.5, BedwarsSettings.read(config).forgeShare(), 0.0, "the same without the setting");
        config.set("forge.partage-rayon", 0);
        assertEquals(0.0, BedwarsSettings.read(config).forgeShare(), 0.0, "0: each keeps what they pick up");
        config.set("forge.partage-rayon", 9);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> BedwarsSettings.read(config));
        assertTrue(error.getMessage().startsWith("forge.partage-rayon: "), error.getMessage());
        Location forge = new Location(null, 10.5, 64.5, 10.5);
        assertTrue(BedwarsGame.atForge(forge, new Location(null, 11.9, 64.5, 10.5), 2.5), "standing on it");
        assertTrue(BedwarsGame.atForge(forge, new Location(null, 12.5, 63.5, 10.5), 2.5), "next to it, a step lower");
        assertTrue(BedwarsGame.atForge(forge, new Location(null, 8.1, 64.5, 12.9), 2.5), "in a corner of its 5 x 5 blocks");
        assertFalse(BedwarsGame.atForge(forge, new Location(null, 13.6, 64.5, 10.5), 2.5), "a block too far");
        assertFalse(BedwarsGame.atForge(forge, new Location(null, 10.5, 67, 10.5), 2.5), "above it, on a bridge");
        assertFalse(BedwarsGame.atForge(forge, new Location(null, 11.5, 64.5, 10.5), 0), "0: nobody");
    }

    /**
     * The split of Hypixel: a teammate waits next to the forge, just out of reach of its items,
     * while another walks onto it. A 1.8 player picks up an item less than 1.425 blocks away on each
     * side (half their width, 0.3, a block more, and half the item's, 0.125): a square. Wherever they
     * wait around it, on a side or at a corner, up to a block out of reach, they get the split.
     */
    @Test
    void aTeammateWaitingNextToTheForgeGetsTheSplit() {
        Location forge = new Location(null, -13.5, 64.5, -78.5);
        double reach = 1.425;
        for (double out = 0.01; out <= 1; out += 0.07) {
            for (int angle = 0; angle < 360; angle += 3) {
                double cos = Math.cos(Math.toRadians(angle));
                double sin = Math.sin(Math.toRadians(angle));
                // on the square the items reach, this much out of it
                double scale = (reach + out) / Math.max(Math.abs(cos), Math.abs(sin));
                Location waiting = new Location(null, forge.getX() + cos * scale, 65, forge.getZ() + sin * scale);
                assertTrue(BedwarsGame.atForge(forge, waiting, 2.5), out + " out of reach at " + angle + "°");
            }
        }
        assertTrue(BedwarsGame.splits(Material.IRON_INGOT));
        assertTrue(BedwarsGame.splits(Material.GOLD_INGOT));
        assertFalse(BedwarsGame.splits(Material.EMERALD), "the Emerald Forge's emeralds: whoever picks them up");
        assertFalse(BedwarsGame.splits(Material.DIAMOND));
    }

    /** The config.yml of the plugin (the tests run in the module's folder). */
    private static YamlConfiguration bundled() throws Exception {
        try (Reader reader = new InputStreamReader(new FileInputStream("src/main/resources/config.yml"),
                StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    @Test
    void readsTheBundledConfig() throws Exception {
        BedwarsSettings settings = BedwarsSettings.read(bundled());
        assertEquals(Arrays.asList("ranked-1v1", "ranked-2v2", "ranked-3v3", "ranked-4v4", "top-bridge-1v1",
                "top-bridge-2v2", "skyrise-1v1", "skyrise-2v2", "skyrise-resources-1v1", "skyrise-resources-2v2",
                "first-rush", "first-rush-duo", "first-rush-full", "first-rush-full-duo", "top-bridge-ffa", "ffa",
                "invis-practice-1v1", "invis-practice-1v2", "invis-practice-1v3", "invis-practice-1v4", "shop-practice",
                "attack-defense-1v1", "attack-defense-2v2", "clutch-scenario-1v1", "clutch-scenario-2v2",
                "clutch-scenario-3v3", "clutch-scenario-4v4", "bed-fight-1v1", "bed-fight-2v2", "top-bed-fight-1v1",
                "lobby-fight-1v1", "lobby-fight-ffa", "bed-break-simulator-1v1", "bed-break-simulator-1v2",
                "bed-break-simulator-1v3", "bed-break-simulator-1v4"),
                settings.modes());
        for (int defenders = 1; defenders <= 4; defenders++) {
            String size = "bed-break-simulator-1v" + defenders;
            assertEquals(BedwarsSettings.Kind.BED_BREAK, settings.kind(size), size);
            assertEquals(Arrays.asList("Archway", "Invasion"), settings.mapsOf(size), size);
            assertFalse(settings.topBridge(size), "its top bridge is its own, not the Top Bridge modes'");
            assertEquals(BedwarsSettings.Kind.INVIS, settings.kind("invis-practice-1v" + defenders));
            assertEquals(settings.mapsOf("ranked-1v1"), settings.mapsOf("invis-practice-1v" + defenders),
                    "the invis practice on the 20 maps");
        }
        assertEquals(Arrays.asList("Classic", "Fireball", "Fighter"), kitNames(settings.bedBreakKits()), "the config's kits");
        for (int i = 0; i < RoundKit.BED_BREAK.size(); i++) {
            assertEquals(RoundKit.BED_BREAK.get(i).items, settings.bedBreakKits().get(i).items, "the config gives the defaults");
        }
        assertEquals(2, settings.bedBreakProtection(), "Protection II on everyone's armour");
        assertEquals(BedwarsSettings.Kind.LOBBY_FIGHT, settings.kind("lobby-fight-1v1"));
        assertEquals(BedwarsSettings.Kind.LOBBY_FIGHT_FFA, settings.kind("lobby-fight-ffa"));
        assertTrue(settings.kind("lobby-fight-1v1").inWaitingLobby() && settings.kind("lobby-fight-ffa").inWaitingLobby());
        assertFalse(settings.kind("ffa").inWaitingLobby() || settings.kind("top-bridge-1v1").inWaitingLobby());
        assertEquals(Collections.singletonList("ArchwayFFA"), settings.mapsOf("lobby-fight-1v1"),
                "the waiting lobby of Archway FFA");
        assertEquals(settings.mapsOf("lobby-fight-1v1"), settings.mapsOf("lobby-fight-ffa"));
        assertFalse(settings.topBridge("lobby-fight-1v1"), "no platform: the map has its lobby");
        assertEquals(BedwarsSettings.Kind.CLASSIC, settings.kind("ranked-1v1"));
        assertEquals(BedwarsSettings.Kind.TOP_BED_FIGHT, settings.kind("top-bed-fight-1v1"));
        assertFalse(settings.topBridge("top-bed-fight-1v1"), "no platform: the map has its bridge");
        assertEquals(BedwarsSettings.Kind.FFA, settings.kind("ffa"));
        assertEquals(BedwarsSettings.Kind.ARENA, BedwarsSettings.Kind.of(" Arene "), "the kind stays for a mode that wants it");
        assertFalse(settings.modes().contains("free-ffa"), "Arena Free is on the owner's Arena, in the practice plugin");
        assertEquals(BedwarsSettings.Kind.CLASSIC, settings.kind("invis-practice"),
                "not a mode of its own any more: its sizes are (it holds their kit in the lobby)");
        assertEquals(BedwarsSettings.Kind.FAST_SHOP, settings.kind("shop-practice"));
        assertEquals(Arrays.asList("ArchwayFFA"), settings.mapsOf("ffa"), "the owner's Archway FFA only: no map to choose");
        assertEquals(BedwarsSettings.Kind.FFA, settings.kind("top-bridge-ffa"), "a free for all...");
        assertTrue(settings.topBridge("top-bridge-ffa"), "...with the top bridge");
        assertEquals(java.util.Collections.singletonList("Invasion"), settings.mapsOf("top-bridge-ffa"),
                "the free for all on the top bridge of Invasion only");
        assertEquals(settings.mapsOf("ranked-1v1"), settings.mapsOf("top-bridge-1v1"), "the top bridge on the 20 maps");
        assertEquals(settings.mapsOf("ranked-1v1"), settings.mapsOf("invis-practice-1v4"), "the invis practice too");
        assertEquals(BedwarsSettings.Kind.TOP_BRIDGE, settings.kind("top-bridge-1v1"));
        assertEquals(BedwarsSettings.Kind.TOP_BRIDGE, settings.kind("top-bridge-2v2"));
        assertTrue(settings.mapsOf("top-fight-1v1").isEmpty(), "no top fight: there are bridge fight and top bridge");
        assertEquals(BedwarsSettings.Kind.ATTACK_DEFENSE, settings.kind("attack-defense-1v1"));
        assertEquals(BedwarsSettings.Kind.ATTACK_DEFENSE, settings.kind("attack-defense-2v2"));
        assertEquals(Collections.singletonList("BioHazard"), settings.mapsOf("attack-defense-2v2"), "the owner's Bio Hazard");
        assertEquals(BedwarsSettings.Kind.CLUTCH_SCENARIO, settings.kind("clutch-scenario-3v3"));
        assertEquals(Arrays.asList("Rusher", "Breaker", "Ninja"), kitNames(settings.roundKits(true)), "the config's kits");
        assertEquals(Arrays.asList("Guardian", "Archer", "Builder"), kitNames(settings.roundKits(false)));
        assertEquals(RoundKit.ATTACK.get(1).items, settings.roundKits(true).get(1).items, "the config gives the defaults");
        assertEquals(RoundKit.DEFENSE.get(2).items, settings.roundKits(false).get(2).items);
        assertEquals(2, settings.attackDefenseProtection(), "Protection II on both sides' armour");
        assertEquals(BedwarsSettings.Kind.BED_FIGHT, settings.kind("bed-fight-1v1"));
        assertEquals(BedwarsSettings.Kind.BED_FIGHT, settings.kind("bed-fight-2v2"));
        assertEquals(Collections.singletonList("BedFight"), settings.mapsOf("bed-fight-2v2"), "the owner's Bed Fight map");
        for (String size : Arrays.asList("1v1", "2v2", "3v3", "4v4")) {
            assertEquals(Arrays.asList("SkyRise", "Archway"), settings.mapsOf("clutch-scenario-" + size),
                    "the owner's two maps for it");
        }
        assertEquals(Arrays.asList("Antenna", "Aquarium", "Archway", "Artemis", "Boletum", "Carapace", "Catalyst",
                "Chained", "Eastwood", "Extinction", "Invasion", "Katsu", "Lectus", "Obelisk", "Paladin", "Planet98", "Rise",
                "Stonekeep", "Swashbuckle", "Treenan"), settings.mapsOf("ranked-2v2"), "the maps of the RBW queue");
        assertEquals(settings.mapsOf("ranked-2v2"), settings.mapsOf("ranked-4v4"));
        assertEquals(Collections.singletonList("SkyRise"), settings.mapsOf("skyrise-1v1"));
        assertEquals(10, settings.mapsOf("first-rush").size());
        assertEquals(settings.mapsOf("first-rush"), settings.mapsOf("first-rush-duo"));
        assertTrue(settings.mapsOf("sumo").isEmpty());
        assertTrue(settings.neighbours("first-rush"));
        assertTrue(settings.neighbours("first-rush-duo"));
        assertTrue(settings.neighbours("first-rush-full") && settings.neighbours("first-rush-full-duo"));
        assertEquals(settings.mapsOf("first-rush"), settings.mapsOf("first-rush-full-duo"));
        assertTrue(settings.allForges("first-rush-full") && settings.allForges("first-rush-full-duo"),
                "the full map First Rush: every forge of the map");
        assertTrue(!settings.allForges("first-rush"));
        assertTrue(settings.allShops("first-rush-full") && settings.allShops("first-rush-full-duo"),
                "the full map First Rush: an item shop on every island");
        assertTrue(!settings.allShops("first-rush"));
        assertTrue(settings.allShops("clutch-scenario-1v1") && settings.allShops("clutch-scenario-4v4"),
                "Clutch Scenario: the shop and upgrades of the islands whose forge runs");
        for (String size : new String[] {"1v1", "2v2", "3v3", "4v4"}) {
            assertTrue(settings.allForges("ranked-" + size) && settings.allShops("ranked-" + size),
                    "Ranked Bedwars: the forges and the item shops of the side islands too");
        }
        for (String size : new String[] {"1v1", "2v2", "3v3", "4v4"}) {
            assertTrue(settings.allForges("clutch-scenario-" + size), "Clutch Scenario: every forge of the map too");
        }
        assertTrue(!settings.neighbours("skyrise-1v1"));
        for (String size : new String[] {"1v1", "2v2"}) {
            String mode = "skyrise-resources-" + size;
            assertEquals(Collections.singletonList("SkyRise"), settings.mapsOf(mode));
            assertTrue(settings.allForges(mode) && settings.allShops(mode), mode + ": every island's forge and shop");
            assertFalse(settings.neighbours(mode), mode + ": the teams opposite, as in Sky Rise");
        }
        assertFalse(settings.allForges("skyrise-1v1") || settings.allShops("skyrise-1v1"), "Sky Rise keeps its own island");
        assertEquals(settings.mapsOf("ranked-1v1"), settings.mapsOf("top-bridge-1v1"), "Top Bridge on the 20 maps");
        assertEquals(settings.mapsOf("ranked-1v1"), settings.mapsOf("top-bridge-2v2"));
        assertTrue(settings.topBridge("top-bridge-1v1") && settings.topBridge("top-bridge-2v2"));
        assertTrue(!settings.topBridge("ranked-1v1"), "the ranked games stay as on Hypixel");
        assertEquals(8, settings.maxGames());
        assertTrue(settings.demo());
        assertEquals(5, settings.respawnSeconds());
        BedwarsSettings.Forge forge = settings.forge();
        // as counted on Hypixel: Sky Rise, 8 islands, fast: 3 nodes of an ingot every 2 s, 90 a minute
        assertEquals(1200 / 90, forge.iron("SkyRise", 1, 8).ticks);
        assertEquals(48, forge.iron("SkyRise", 1, 8).max);
        // Lotus: slow in solo (30 a minute), medium in doubles (60)
        assertEquals(40, forge.iron("Lotus", 1, 8).ticks);
        assertEquals(20, forge.iron("lotus", 2, 8).ticks);
        // a ranked map of 4 islands, not listed: fast, and a node more: 120 a minute
        assertEquals(10, forge.iron("Archway", 2, 4).ticks);
        assertArrayEquals(new int[] {2}, forge.iron("Archway", 2, 4).batches, "2 ingots at once");
        // gold: 8 a minute, 12 on the maps of four teams or fewer
        assertEquals(150, forge.gold(8).ticks);
        assertEquals(100, forge.gold(4).ticks);
        assertEquals(100, forge.gold(2).ticks);
        assertEquals(16, forge.gold(8).max);
        assertArrayEquals(new int[] {1}, forge.gold(4).batches);
        // the RBW queue, slower than a 4v4: 60 iron and 8 gold a minute in 1v1, 90 and 12 in teams
        BedwarsSettings.Forge solo = forge.forMode("ranked-1v1");
        assertEquals(1200 / 60, solo.iron("Archway", 1, 4).ticks);
        assertEquals(1200 / 60, solo.iron("Antenna", 1, 2).ticks, "on every map");
        assertEquals(150, solo.gold(4).ticks);
        for (String teams : new String[] {"ranked-2v2", "ranked-3v3", "ranked-4v4"}) {
            assertEquals(1200 / 90, forge.forMode(teams).iron("Archway", 2, 4).ticks, teams);
            assertEquals(100, forge.forMode(teams).gold(4).ticks, teams);
        }
        // Sky Rise Resources: slow forges on every island, 40 iron and 5 gold a minute each
        BedwarsSettings.Forge resources = forge.forMode("skyrise-resources-2v2");
        assertEquals(1200 / 40, resources.iron("SkyRise", 2, 8).ticks);
        assertArrayEquals(new int[] {2}, resources.iron("SkyRise", 2, 8).batches);
        assertEquals(240, resources.gold(8).ticks);
        assertEquals(1200 / 90, forge.forMode("skyrise-2v2").iron("SkyRise", 2, 8).ticks, "Sky Rise keeps its fast forge");
        assertEquals(30, forge.fastStartSeconds);
        assertEquals(600, settings.diamond().get(0).ticks);
        assertEquals(240, settings.diamond().get(2).ticks);
        assertEquals(4, settings.diamond().get(1).max);
        assertEquals(1300, settings.emerald().get(0).ticks);
        assertEquals(6 * 60, settings.diamondII());
        assertEquals(30 * 60, settings.bedsGone());
        assertEquals(45 * 60, settings.gameEnd());
        BedwarsSettings.Fireball fireball = settings.fireball();
        assertEquals(1.0, fireball.speed);
        assertEquals(2.0, fireball.self);
        assertEquals(2.0, fireball.enemies);
        assertEquals(0.0, fireball.teammates);
    }

    @Test
    void theFireballPushesAsOnHypixel() {
        BedwarsSettings.Fireball fireball = BedwarsSettings.read(new YamlConfiguration()).fireball();
        // under the feet: straight up, 0.65 x 1.5
        assertPush(0, 0.975, 0, fireball.push(0, 0, 0, 0, 0, 0));
        assertPush(0, 0.975, 0, fireball.push(0, -0.5, 0, 0, 0, 0));
        // level with the player, in front: thrown back, and up
        assertPush(-1.0, 0.975, 0, fireball.push(1, 0, 0, 0, 0, 0));
        // above and in front: back, and up as much as the blast is above
        double[] push = fireball.push(0.5, 1, 0, 0, 0, 0);
        assertPush(-0.5 / Math.sqrt(1.25), 0.975 / Math.sqrt(1.25), 0, push);
        assertTrue(fireball.reaches(3, -3, 3));
        assertTrue(!fireball.reaches(3.1, 0, 0));
    }

    private static void assertPush(double x, double y, double z, double[] push) {
        assertEquals(x, push[0], 1e-9, "x");
        assertEquals(y, push[1], 1e-9, "y");
        assertEquals(z, push[2], 1e-9, "z");
    }

    @Test
    void eachModeHasItsKindOfGame() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("modes:\n  ffa: [Invasion]\n  invis-practice: toutes\njeux:\n  ffa: FFA\n  invis-practice: invis");
        BedwarsSettings settings = BedwarsSettings.read(config);
        assertEquals(BedwarsSettings.Kind.FFA, settings.kind("ffa"));
        assertEquals(BedwarsSettings.Kind.INVIS, settings.kind("invis-practice"));
        assertEquals(BedwarsSettings.Kind.CLASSIC, settings.kind("ranked-1v1"), "not listed: a classic game");
        YamlConfiguration wrong = new YamlConfiguration();
        wrong.loadFromString("jeux:\n  ffa: battle-royale");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> BedwarsSettings.read(wrong));
        assertTrue(error.getMessage().startsWith("jeux.ffa: "), error.getMessage());
        YamlConfiguration list = new YamlConfiguration();
        list.loadFromString("jeux: [ffa]");
        assertThrows(IllegalArgumentException.class, () -> BedwarsSettings.read(list));
    }

    @Test
    void anEmptyConfigGivesTheDefaults() {
        BedwarsSettings settings = BedwarsSettings.read(new YamlConfiguration());
        assertTrue(settings.modes().isEmpty());
        assertEquals(13, settings.forge().iron("Gelato", 1, 8).ticks, "90 a minute");
        assertEquals(40, settings.forge().iron("Lighthouse", 1, 8).ticks, "30 a minute");
        assertArrayEquals(new int[] {2}, settings.forge().iron("Gelato", 1, 8).batches);
        assertEquals(3, settings.emerald().size());
        assertEquals(24 * 60, settings.emeraldIII());
        assertEquals(2.0, settings.fireball().self);
        assertTrue(settings.neighbours("first-rush"));
        assertEquals(RoundKit.BED_BREAK, settings.bedBreakKits(), "the owner's three attack kits");
        assertEquals(2, settings.bedBreakProtection());
    }

    @Test
    void modesTakeTheirMaps() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("modes:\n  ranked-1v1: toutes\n  first-rush: [Gelato, Nebuc]\n"
                + "iles-voisines: [first-rush]\nforge:\n  vitesse-par-defaut: lente\n  vitesses:\n    Gelato: moyenne\n"
                + "    Nebuc: {solo: rapide, equipes: lente}");
        BedwarsSettings settings = BedwarsSettings.read(config);
        assertEquals(Arrays.asList("ranked-1v1", "first-rush"), settings.modes());
        assertTrue(settings.mapsOf("ranked-1v1").isEmpty(), "toutes: every map");
        assertEquals(Arrays.asList("Gelato", "Nebuc"), settings.mapsOf("first-rush"));
        assertTrue(settings.neighbours("first-rush"));
        assertTrue(!settings.neighbours("first-rush-duo"));
        BedwarsSettings.Forge forge = settings.forge();
        assertEquals(BedwarsSettings.Speed.MEDIUM, forge.speed("Gelato", 1));
        assertEquals(BedwarsSettings.Speed.FAST, forge.speed("Nebuc", 1));
        assertEquals(BedwarsSettings.Speed.SLOW, forge.speed("Nebuc", 4));
        assertEquals(BedwarsSettings.Speed.SLOW, forge.speed("Lotus", 2), "not listed: the default");
        // the list of the first version: each mode on every map
        config = new YamlConfiguration();
        config.loadFromString("modes:\n  - ranked-1v1\n  - ranked-2v2\niles-voisines: []");
        settings = BedwarsSettings.read(config);
        assertEquals(Arrays.asList("ranked-1v1", "ranked-2v2"), settings.modes());
        assertTrue(settings.mapsOf("ranked-2v2").isEmpty());
        assertTrue(!settings.neighbours("first-rush"), "none on neighbouring islands");
    }

    @Test
    void wrongValuesNameTheSetting() {
        assertProblem("modes: [\"Ranked 1v1\"]", "modes");
        assertProblem("parties-max: 0", "parties-max");
        assertProblem("reapparition-secondes: 60", "reapparition-secondes");
        assertProblem("forge:\n  fer-secondes-par-noeud: 0", "forge.fer-secondes-par-noeud");
        assertProblem("forge:\n  vitesses:\n    Lotus: vite", "forge.vitesses.Lotus");
        assertProblem("forge:\n  vitesses:\n    Lotus: {solo: lente}", "forge.vitesses.Lotus.equipes");
        assertProblem("forge:\n  vitesses: [Lotus]", "forge.vitesses");
        assertProblem("forge:\n  vitesse-par-defaut: vite", "forge.vitesse-par-defaut");
        assertProblem("forge:\n  fer-par-lot: [2, 0]", "forge.fer-par-lot");
        assertProblem("forge:\n  fer-par-lot: []", "forge.fer-par-lot");
        assertProblem("generateurs:\n  diamant:\n    secondes: [30, 20]", "generateurs.diamant.secondes");
        assertProblem("evenements:\n  diamant-2: 20", "evenements.emeraude-2");
        assertProblem("boule-de-feu:\n  degats-sur-soi: -1", "boule-de-feu.degats-sur-soi");
        assertProblem("boule-de-feu:\n  rayon: 20", "boule-de-feu.rayon");
        assertProblem("iles-voisines: [\"First Rush\"]", "iles-voisines");
        assertProblem("modes:\n  first-rush: Gelato", "modes.first-rush");
        assertProblem("modes:\n  first-rush: []", "modes.first-rush");
        assertProblem("modes:\n  first-rush: [\"Gel ato\"]", "modes.first-rush");
        assertProblem("modes:\n  First Rush: [Gelato]", "modes");
        assertProblem("bed-break:\n  protection: 5", "bed-break.protection");
        assertProblem("bed-break:\n  kits:\n    - {nom: X, icone: TNT, objets: [\"tnt 99\"]}", "bed-break.kits[1].objets");
    }

    private static void assertProblem(String yaml, String setting) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        IllegalArgumentException problem = assertThrows(IllegalArgumentException.class, () -> BedwarsSettings.read(config));
        assertTrue(problem.getMessage().startsWith(setting), problem.getMessage());
    }

    private static List<String> kitNames(List<RoundKit> kits) {
        List<String> names = new java.util.ArrayList<>();
        for (RoundKit kit : kits) {
            names.add(kit.name);
        }
        return names;
    }
}
