package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
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
        assertEquals(Arrays.asList("ranked-1v1", "ranked-2v2", "ranked-3v3", "ranked-4v4", "skyrise-1v1", "skyrise-2v2",
                "first-rush", "first-rush-duo", "ffa", "free-ffa", "invis-practice", "shop-practice"), settings.modes());
        assertEquals(BedwarsSettings.Kind.CLASSIC, settings.kind("ranked-1v1"));
        assertEquals(BedwarsSettings.Kind.FFA, settings.kind("ffa"));
        assertEquals(BedwarsSettings.Kind.ARENA, settings.kind("free-ffa"));
        assertEquals(BedwarsSettings.Kind.INVIS, settings.kind("invis-practice"));
        assertEquals(BedwarsSettings.Kind.FAST_SHOP, settings.kind("shop-practice"));
        assertEquals(Arrays.asList("Archway", "Paladin"), settings.mapsOf("ffa"));
        assertEquals(Collections.singletonList("Archway"), settings.mapsOf("free-ffa"));
        assertEquals(Arrays.asList("Invasion", "Lectus", "Archway"), settings.mapsOf("invis-practice"));
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
        assertTrue(!settings.neighbours("skyrise-1v1"));
        assertEquals(8, settings.maxGames());
        assertTrue(settings.demo());
        assertEquals(5, settings.respawnSeconds());
        BedwarsSettings.Forge forge = settings.forge();
        // Sky Rise, 8 islands, fast: 3 nodes of an ingot every 3 s
        assertEquals(20, forge.iron("SkyRise", 1, 8).ticks);
        assertEquals(48, forge.iron("SkyRise", 1, 8).max);
        // Lotus: slow in solo, medium in doubles
        assertEquals(60, forge.iron("Lotus", 1, 8).ticks);
        assertEquals(30, forge.iron("lotus", 2, 8).ticks);
        // a ranked map of 4 islands, not listed: fast, and a node more
        assertEquals(15, forge.iron("Archway", 2, 4).ticks);
        assertEquals(240, forge.gold().ticks);
        assertEquals(16, forge.gold().max);
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
        assertEquals(20, settings.forge().iron("Gelato", 1, 8).ticks);
        assertEquals(60, settings.forge().iron("Lighthouse", 1, 8).ticks);
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
}
