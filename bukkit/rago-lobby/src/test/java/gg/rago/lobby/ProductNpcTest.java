package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The products of Rago in the thank-you room (pnj: ... type: produit, plan 5.6A, OUR OTHER PRODUCTS). */
class ProductNpcTest {

    @Test
    void ragoClientAndRagoOptimizerOfTheBundledConfig() throws Exception {
        LobbySettings settings = LobbySettings.load(LobbySettingsTest.bundledConfig());
        for (String[] product : new String[][] {{"ragoclient", "§b§lRago Client", "Rago Client"},
                {"ragooptimizer", "§b§lRago Optimizer", "Rago Optimizer"}}) {
            LobbyNpc npc = settings.npc(product[0]);
            assertEquals(LobbyNpc.Kind.PRODUCT, npc.kind(), product[0]);
            assertTrue(npc.hasBody());
            assertEquals(product[1], npc.name(), "the name shown: " + product[2]);
            assertEquals("§6§lOUR OTHER PRODUCTS", npc.title(), "the title above its name");
            assertNull(npc.subtitle(), "no line under its name in the bundled config");
            assertEquals("qRago", npc.account(), "the skin of qRago");
            assertNull(npc.skin());
            assertNull(npc.rank());
            assertNull(npc.category());
            assertTrue(npc.modeIds().isEmpty());
            assertEquals(Material.DIAMOND_BLOCK, settings.pedestal(product[0]).material(), "on a block of diamond");
            assertEquals(Arrays.asList("§c§lRAGO §8» §f" + product[2] + ": §crago-optimizer.com",
                    "§c§lRAGO §8» §fJoin our Discord: §cdiscord.gg/ragooptimizer"), npc.links(),
                    "a click shows the website and the Discord");
        }
    }

    @Test
    void aProductKeepsItsLinksThroughItsSkinAndItsLine() throws Exception {
        YamlConfiguration yaml = LobbySettingsTest.bundledConfig();
        yaml.set("pnj.ragoclient.sous-titre", "&7Click for the links");
        yaml.set("pnj.ragoclient.titre", null);
        LobbyNpc npc = LobbySettings.load(yaml).npc("ragoclient");
        assertEquals("§7Click for the links", npc.subtitle());
        assertNull(npc.title(), "no title: its name alone at the top");
        assertEquals("qRago", npc.account());
        assertEquals(2, npc.links().size(), "the skin and the line keep the links");
    }

    @Test
    void wrongProductsAreRefused() throws Exception {
        assertRefused("pnj.ragoclient.liens", yaml -> yaml.set("pnj.ragoclient.liens", null));
        assertRefused("pnj.ragoclient.liens", yaml -> yaml.set("pnj.ragoclient.liens", Arrays.asList(" ", "")));
        List<String> nine = new ArrayList<>(Collections.nCopies(LobbySettings.MAX_PRODUCT_LINKS + 1, "&fline"));
        assertRefused("pnj.ragoclient.liens", yaml -> yaml.set("pnj.ragoclient.liens", nine));
        assertRefused("pnj.ragoclient", yaml -> yaml.set("pnj.ragoclient.modes", Collections.singletonList("sumo")));
        assertRefused("pnj.ragoclient", yaml -> yaml.set("pnj.ragoclient.categorie", "duels"));
        assertRefused("pnj.ragoclient", yaml -> yaml.set("pnj.ragoclient.rang", "owner"));
        assertRefused("pnj.ragoclient.type", yaml -> yaml.set("pnj.ragoclient.type", "produits"));
    }

    private interface Change {
        void apply(YamlConfiguration yaml);
    }

    private static void assertRefused(String where, Change change) throws Exception {
        YamlConfiguration yaml = LobbySettingsTest.bundledConfig();
        change.apply(yaml);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LobbySettings.load(yaml), where);
        assertTrue(e.getMessage().startsWith(where), e.getMessage());
    }
}
