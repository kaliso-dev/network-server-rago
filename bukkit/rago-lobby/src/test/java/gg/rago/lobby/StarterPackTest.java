package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class StarterPackTest {

    private static YamlConfiguration bundled() throws Exception {
        try (Reader reader = new InputStreamReader(StarterPackTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    @Test
    void theBundledPackIsHalfPrice() throws Exception {
        ServerSettings server = ServerSettings.load(bundled());
        StarterPack pack = server.starterPack();
        assertEquals(25, pack.discount());
        assertEquals("4.99€", pack.money(pack.price));
        assertEquals(20_000, pack.points);
        assertEquals(3, pack.boosters);
        assertEquals(Arrays.asList("trail-magic", "kill-firework", "win-fireworks"), pack.cosmetics);
        List<String> contents = pack.contents(server.cosmetics());
        assertEquals(2, pack.coinBoosters);
        assertEquals(6, contents.size(), "the points, the XP boosters, the coin boosters and three cosmetics");
        assertTrue(contents.get(0).contains("20,000 Rago Points"));
        assertTrue(contents.get(1).contains("3 XP boosters") && contents.get(1).contains("x3 XP"), contents.get(1));
        assertTrue(contents.get(2).contains("2 coin boosters") && contents.get(2).contains("x3 Rago Points"), contents.get(2));
        assertTrue(contents.get(3).contains("Magic") && contents.get(3).contains("projectile trail"), contents.get(3));
        // 20,000 points, 3 boosters of 2,500, and the cosmetics at their price
        int cosmetics = server.cosmetics().get("trail-magic").price() + server.cosmetics().get("kill-firework").price()
                + server.cosmetics().get("win-fireworks").price();
        assertEquals(20_000 + 3 * XpBoosters.PRICE + cosmetics, pack.pointsWorth(server.cosmetics()));
        assertTrue(pack.priceLine().contains("6.65€") && pack.priceLine().contains("4.99€"));
    }

    @Test
    void theButtonOpensTheStore() {
        StarterPack pack = StarterPack.defaults();
        String json = pack.button();
        assertTrue(json.contains("\"action\":\"open_url\",\"value\":\"https://rago-optimizer.com\""), json);
        assertEquals(json.chars().filter(c -> c == '{').count(), json.chars().filter(c -> c == '}').count());
    }

    @Test
    void aWrongPackIsRefused() throws Exception {
        YamlConfiguration config = bundled();
        config.set("pack-depart.cosmetiques", Arrays.asList("kill-squid"));
        IllegalArgumentException premium = assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(config));
        assertTrue(premium.getMessage().contains("kill-squid"), "a PREMIUM cosmetic is not in a pack");
        config.set("pack-depart.cosmetiques", Arrays.asList("trail-flames"));
        config.set("pack-depart.prix-normal", 2.0);
        assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(config), "the usual price is not below the price");
        config.set("pack-depart.prix-normal", 4.99);
        assertEquals(0, ServerSettings.load(config).starterPack().discount(), "no saving: no crossed out price");
        config.set("pack-depart.boosters-pieces", XpBoosters.MAX_STOCK + 1);
        assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(config), "too many coin boosters");
        config.set("pack-depart.boosters-pieces", 2);
        config.set("pack-depart.lien", "javascript:alert(1)\"}");
        assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(config));
    }

    @Test
    void thePluginDeclaresEveryCommand() throws Exception {
        // the server refuses a plugin.yml that does not parse: the lobby would not load at all
        YamlConfiguration plugin;
        try (Reader reader = new InputStreamReader(StarterPackTest.class.getResourceAsStream("/plugin.yml"), StandardCharsets.UTF_8)) {
            plugin = new YamlConfiguration();
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[4096];
            for (int n; (n = reader.read(buffer)) > 0; ) {
                text.append(buffer, 0, n);
            }
            plugin.loadFromString(text.toString());
        }
        for (String command : NetworkCommands.NAMES) {
            assertTrue(plugin.isConfigurationSection("commands." + command), command);
        }
    }

    @Test
    void withoutTheSectionThePackHasItsDefaults() {
        ServerSettings server = ServerSettings.load(new YamlConfiguration());
        assertEquals(StarterPack.defaults().points, server.starterPack().points);
    }
}
