package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.List;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

/** The welcome of the lobby: the three ways to play, clickable. */
class LobbyWelcomeTest {

    @Test
    void itShowsTheThreeWaysToPlayOfTheBundledConfig() throws Exception {
        LobbySettings settings = LobbySettings.load(LobbySettingsTest.bundledConfig());
        List<ModeCategory> categories = settings.categories();
        assertEquals("Practice · RBW Modes · Duels", ChatColor.stripColor(LobbyWelcome.subtitle(categories)), "the title, the first time");
        for (ModeCategory category : categories) {
            JsonObject line = new JsonParser().parse(LobbyWelcome.json(category)).getAsJsonObject();
            String text = ChatColor.stripColor(line.get("text").getAsString());
            assertTrue(text.startsWith(" » " + ChatColor.stripColor(category.name()) + " "), text);
            assertTrue(text.endsWith(ChatColor.stripColor(category.slogan())), "its name, then what you do there: " + text);
            JsonObject click = line.getAsJsonObject("clickEvent");
            assertEquals("run_command", click.get("action").getAsString());
            assertEquals("/play " + category.id(), click.get("value").getAsString());
            String hover = line.getAsJsonObject("hoverEvent").get("value").getAsString();
            assertTrue(hover.contains("\n") && hover.contains(category.description().get(0)), "what it holds, line by line");
            assertTrue(hover.endsWith("Click to see the modes"));
        }
        String practice = ChatColor.stripColor(new JsonParser().parse(LobbyWelcome.json(categories.get(0))).getAsJsonObject()
                .get("text").getAsString());
        assertEquals(" » Practice Train alone, at your own pace", practice);
    }

    @Test
    void itsJsonStaysValidWithQuotes() {
        ModeCategory odd = new ModeCategory("odd", "§a\"Odd\\", org.bukkit.Material.STONE, Arrays.asList("§7a \"quote\""), null);
        JsonObject line = new JsonParser().parse(LobbyWelcome.json(odd)).getAsJsonObject();
        assertEquals(" §8» §a\"Odd\\", line.get("text").getAsString(), "no slogan: the name alone");
    }

    @Test
    void playFindsACategoryByItsIdOrItsName() throws Exception {
        List<ModeCategory> categories = LobbySettings.load(LobbySettingsTest.bundledConfig()).categories();
        assertEquals("entrainement", LobbyWelcome.find(categories, "entrainement").id());
        assertEquals("entrainement", LobbyWelcome.find(categories, "Practice").id(), "its name, any case");
        assertEquals("ranked", LobbyWelcome.find(categories, "rbw modes").id());
        assertEquals("ranked", LobbyWelcome.find(categories, "RBW-Modes").id());
        assertSame(categories.get(2), LobbyWelcome.find(categories, "DUELS"));
        assertNull(LobbyWelcome.find(categories, "bedwars"));
        assertNull(LobbyWelcome.find(categories, " "));
        assertEquals(Arrays.asList("entrainement", "ranked", "duels"), LobbyWelcome.ids(categories));
    }

    @Test
    void theTitleIsInTheMiddleOfTheChat() {
        assertEquals(6 * 3, LobbyWelcome.width("abc"));
        assertEquals(7 * 3 + 5, LobbyWelcome.width("§e§lab c"), "bold: one more a character, the space too");
        assertEquals(7 + 6, LobbyWelcome.width("§lA§cB"), "a colour ends the bold");
        String centred = LobbyWelcome.centred("§e§lRAGO PRACTICE");
        int pad = centred.indexOf('§');
        assertTrue(pad > 20 && pad < 30, "" + pad);
        assertFalse(LobbyWelcome.centred("x").isEmpty());
    }
}
