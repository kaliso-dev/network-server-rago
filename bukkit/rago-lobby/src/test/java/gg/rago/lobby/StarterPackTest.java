package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void theBundledPackIsTheCheapestThingSold() throws Exception {
        ServerSettings server = ServerSettings.load(bundled());
        StarterPack pack = server.starterPack();
        assertEquals(0, pack.discount(), "no price crossed out: it was never sold at another");
        assertEquals("3.99€", pack.money(pack.price));
        assertEquals(20_000, pack.points);
        assertEquals(3, pack.boosters);
        assertEquals(Arrays.asList("trail-magic", "kill-firework", "win-fireworks"), pack.cosmetics);
        List<String> contents = pack.contents(server.cosmetics());
        assertEquals(2, pack.coinBoosters);
        assertNull(pack.rank, "no rank: VIP (4.99) and PREMIUM+ are sold on their own");
        assertEquals(6, contents.size(), "the points, the XP boosters, the coin boosters and three cosmetics");
        assertTrue(contents.get(0).contains("20,000 Rago Points"));
        assertTrue(contents.get(1).contains("3 XP boosters") && contents.get(1).contains("x3 XP"), contents.get(1));
        assertTrue(contents.get(2).contains("2 points boosters") && contents.get(2).contains("x3 Rago Points"), contents.get(2));
        assertTrue(contents.get(3).contains("Magic") && contents.get(3).contains("projectile trail"), contents.get(3));
        for (String cosmetic : pack.cosmetics) {
            assertTrue(!Ranks.VIP_COSMETICS.contains(cosmetic), cosmetic + ": VIP has its own");
        }
        // 20,000 points, 3 boosters of 2,500, and the cosmetics at their price
        int cosmetics = server.cosmetics().get("trail-magic").price() + server.cosmetics().get("kill-firework").price()
                + server.cosmetics().get("win-fireworks").price();
        assertEquals(20_000 + 3 * XpBoosters.PRICE + cosmetics, pack.pointsWorth(server.cosmetics()));
        assertEquals("3.99€", org.bukkit.ChatColor.stripColor(pack.priceLine()), "the price alone");
        assertTrue(server.ranks().get("vip").webPrice().equals("4.99€"), "VIP costs more than the pack");
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
        config.set("pack-depart.prix-normal", 3.99);
        assertEquals(0, ServerSettings.load(config).starterPack().discount(), "no saving: no crossed out price");
        config.set("pack-depart.prix-normal", 5.32);
        StarterPack sale = ServerSettings.load(config).starterPack();
        assertEquals(25, sale.discount(), "a price it was sold at before: crossed out, -25%");
        assertTrue(sale.priceLine().contains("5.32€") && sale.priceLine().contains("3.99€"));
        config.set("pack-depart.boosters-pieces", XpBoosters.MAX_STOCK + 1);
        assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(config), "too many coin boosters");
        config.set("pack-depart.boosters-pieces", 2);
        config.set("pack-depart.lien", "javascript:alert(1)\"}");
        assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(config));
    }

    @Test
    void itsRankIsOneOfTheShop() throws Exception {
        YamlConfiguration config = bundled();
        config.set("pack-depart.rang", "premium");
        assertEquals("premium", ServerSettings.load(config).starterPack().rank.id());
        config.set("pack-depart.rang", "vip");
        ServerSettings withVip = ServerSettings.load(config);
        assertEquals(7, withVip.starterPack().contents(withVip.cosmetics()).size());
        assertEquals("• The [VIP] rank, for good", org.bukkit.ChatColor.stripColor(withVip.starterPack().contents(withVip.cosmetics()).get(0)));
        config.set("pack-depart.rang", "");
        assertNull(ServerSettings.load(config).starterPack().rank, "\"\": no rank");
        for (String wrong : Arrays.asList("mvp", "admin", "default")) {
            config.set("pack-depart.rang", wrong);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(config), wrong);
            assertTrue(error.getMessage().startsWith("pack-depart.rang"), error.getMessage());
        }
        config.set("pack-depart.rang", null);
        assertNull(ServerSettings.load(config).starterPack().rank, "no rank when not said");
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
        assertNull(server.starterPack().rank, "no rank");
        assertEquals("3.99€", server.starterPack().money(server.starterPack().price));
    }
}
