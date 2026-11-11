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
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BedwarsSettingsTest {

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
                "top-bridge-2v2", "skyrise-1v1", "skyrise-2v2",
                "first-rush", "first-rush-duo", "first-rush-full", "first-rush-full-duo", "top-bridge-ffa", "ffa", "invis-practice", "shop-practice",
                "attack-defense-1v1", "attack-defense-2v2", "clutch-scenario-1v1", "clutch-scenario-2v2",
                "clutch-scenario-3v3", "clutch-scenario-4v4", "bed-fight-1v1", "bed-fight-2v2", "top-bed-fight-1v1"),
                settings.modes());
        assertEquals(BedwarsSettings.Kind.CLASSIC, settings.kind("ranked-1v1"));
        assertEquals(BedwarsSettings.Kind.TOP_BED_FIGHT, settings.kind("top-bed-fight-1v1"));
        assertFalse(settings.topBridge("top-bed-fight-1v1"), "no platform: the map has its bridge");
        assertEquals(BedwarsSettings.Kind.FFA, settings.kind("ffa"));
        assertEquals(BedwarsSettings.Kind.ARENA, BedwarsSettings.Kind.of(" Arene "), "the kind stays for a mode that wants it");
        assertFalse(settings.modes().contains("free-ffa"), "Arena Free is on the owner's Arena, in the practice plugin");
        assertEquals(BedwarsSettings.Kind.INVIS, settings.kind("invis-practice"));
        assertEquals(BedwarsSettings.Kind.FAST_SHOP, settings.kind("shop-practice"));
        assertEquals(Arrays.asList("ArchwayFFA"), settings.mapsOf("ffa"), "the owner's Archway FFA only: no map to choose");
        assertEquals(BedwarsSettings.Kind.FFA, settings.kind("top-bridge-ffa"), "a free for all...");
        assertTrue(settings.topBridge("top-bridge-ffa"), "...with the top bridge");
        assertEquals(java.util.Collections.singletonList("Invasion"), settings.mapsOf("top-bridge-ffa"),
                "the free for all on the top bridge of Invasion only");
        assertEquals(settings.mapsOf("ranked-1v1"), settings.mapsOf("top-bridge-1v1"), "the top bridge on the 20 maps");
        assertEquals(settings.mapsOf("ranked-1v1"), settings.mapsOf("invis-practice"), "the invis practice too");
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
        assertEquals(31, settings.mapsOf("clutch-scenario-1v1").size(), "the RBW maps, Sky Rise, the solo and doubles maps");
        assertTrue(settings.mapsOf("clutch-scenario-4v4").containsAll(settings.mapsOf("ranked-1v1")));
        assertTrue(settings.mapsOf("clutch-scenario-1v1").containsAll(settings.mapsOf("first-rush")));
        assertFalse(settings.mapsOf("clutch-scenario-2v2").contains("BioHazard"), "not on a map of attack and defense");
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
        assertTrue(!settings.allForges("first-rush") && !settings.allForges("ranked-1v1"));
        assertTrue(settings.allShops("first-rush-full") && settings.allShops("first-rush-full-duo"),
                "the full map First Rush: an item shop on every island");
        assertTrue(!settings.allShops("first-rush") && !settings.allShops("clutch-scenario-1v1")
                && !settings.allShops("ranked-1v1"));
        for (String size : new String[] {"1v1", "2v2", "3v3", "4v4"}) {
            assertTrue(settings.allForges("clutch-scenario-" + size), "Clutch Scenario: every forge of the map too");
        }
        assertTrue(!settings.neighbours("skyrise-1v1"));
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
        assertArrayEquals(new int[] {2, 4}, forge.iron("Archway", 2, 4).batches, "2 or 4 ingots at once");
        // gold: 8 a minute, 12 on the maps of four teams or fewer
        assertEquals(150, forge.gold(8).ticks);
        assertEquals(100, forge.gold(4).ticks);
        assertEquals(100, forge.gold(2).ticks);
        assertEquals(16, forge.gold(8).max);
        assertArrayEquals(new int[] {1}, forge.gold(4).batches);
        // the RBW 1v1: a Hypixel solo's forge on its maps of 4 islands, 90 iron and 8 gold a minute
        BedwarsSettings.Forge solo = forge.forMode("ranked-1v1");
        assertEquals(1200 / 90, solo.iron("Archway", 1, 4).ticks);
        assertEquals(1200 / 90, solo.iron("Lotus", 1, 4).ticks, "on every map");
        assertEquals(150, solo.gold(4).ticks);
        assertEquals(10, forge.forMode("ranked-2v2").iron("Archway", 2, 4).ticks, "the 2v2 keeps the 4v4 forge");
        assertEquals(100, forge.forMode("ranked-2v2").gold(4).ticks);
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
        assertArrayEquals(new int[] {2, 4}, settings.forge().iron("Gelato", 1, 8).batches);
        assertEquals(3, settings.emerald().size());
        assertEquals(24 * 60, settings.emeraldIII());
        assertEquals(2.0, settings.fireball().self);
        assertTrue(settings.neighbours("first-rush"));
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
