package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The texts of the lobby (textes:): the thank-you text of the thank-you room (plan 5.7). */
class LobbyTextTest {

    /**
     * The left text (the owner's ask of 23/11: INFORMATION : on both sides in place of THANK YOU,
     * much shorter; the words to confirm).
     */
    private static final List<String> LEFT = Arrays.asList(
            "INFORMATION :",
            "A server I dreamed of for years, made by a PvP",
            "and Hypixel player who had nowhere to train.",
            "No pay to win: Premium gives nothing in gameplay.");

    /** The right one. */
    private static final List<String> RIGHT = Arrays.asList(
            "INFORMATION :",
            "Train Block Placement, Bedwars and RBW with",
            "Hypixel KB and low ping, alone or with friends.",
            "Join our Discord: discord.gg/ragooptimizer");

    @Test
    void informationOnBothSidesShortAndToThePoint() throws Exception {
        LobbySettings settings = LobbySettings.load(LobbySettingsTest.bundledConfig());
        LobbyText left = settings.text("textremerciement");
        LobbyText right = settings.text("textremerciement2");
        assertEquals(LEFT, plain(left));
        assertEquals(RIGHT, plain(right));
        assertEquals("§6§lINFORMATION :", left.lines.get(0), "its title in gold, as THANK YOU was");
        assertEquals("§6§lINFORMATION :", right.lines.get(0), "on both sides");
        assertEquals("§fJoin our Discord: §ediscord.gg/ragooptimizer", right.lines.get(right.lines.size() - 1),
                "the Discord, as on the products");
        assertTrue(left.lines.size() <= 5 && right.lines.size() <= 5, "short: people do not read long texts (21 lines before)");
        assertEquals(2, settings.texts().size());
        assertNull(settings.npc("textremerciement"), "a text, not an NPC");
        assertNull(settings.npc("textremerciement2"));
    }

    /** The room is 6 blocks high (air from y 69 to 74 of the lobby, above the point at y 69). */
    @Test
    void theThankYouTextFitsInItsRoom() throws Exception {
        LobbySettings settings = LobbySettings.load(LobbySettingsTest.bundledConfig());
        assertTrue(LobbyText.BOTTOM >= 0.3, "its last line above the floor");
        for (String side : Arrays.asList("textremerciement", "textremerciement2")) {
            // a name is drawn about a quarter of a block high, above where it floats
            LobbyText text = settings.text(side);
            assertTrue(text.top() + 0.25 < 6, side + ": its first line under the ceiling: " + text.top());
        }
    }

    private static List<String> plain(LobbyText text) {
        List<String> plain = new ArrayList<>();
        for (String line : text.lines) {
            assertTrue(line.length() <= LobbyText.MAX_LINE, line);
            plain.add(ChatColor.stripColor(line));
        }
        return plain;
    }

    @Test
    void emptyLinesAtTheEndsAreDropped() throws Exception {
        YamlConfiguration yaml = LobbySettingsTest.bundledConfig();
        yaml.set("textes.bienvenue.lignes", Arrays.asList("", "&aWelcome", "", "&7to Rago", " ", ""));
        LobbyText text = LobbySettings.load(yaml).text("bienvenue");
        assertEquals(Arrays.asList("§aWelcome", "", "§7to Rago"), text.lines, "the gap between two lines kept");
        assertEquals(LobbyText.BOTTOM + 2 * FakeHologram.LINE, text.top(), 1e-9);
    }

    @Test
    void wrongTextsAreRefused() throws Exception {
        assertRefused("textes.textremerciement", yaml -> yaml.set("textes.textremerciement.lignes", null));
        assertRefused("textes.textremerciement.lignes", yaml -> yaml.set("textes.textremerciement.lignes",
                Arrays.asList("", " ")));
        assertRefused("textes.textremerciement.lignes", yaml -> yaml.set("textes.textremerciement.lignes",
                new ArrayList<>(Collections.nCopies(LobbyText.MAX_LINES + 1, "&fline"))));
        String tooLong = "&f" + String.join("", Collections.nCopies(LobbyText.MAX_LINE - 1, "a"));
        assertRefused("textes.textremerciement.lignes", yaml -> yaml.set("textes.textremerciement.lignes",
                Collections.singletonList(tooLong)));
        assertRefused("textes.Merci!", yaml -> yaml.set("textes.Merci!.lignes", Collections.singletonList("&fThanks")));
        assertRefused("textes.qrago", yaml -> yaml.set("textes.qrago.lignes", Collections.singletonList("&fThanks")));
    }

    @Test
    void noTextWithoutTextes() throws Exception {
        YamlConfiguration yaml = LobbySettingsTest.bundledConfig();
        yaml.set("textes", null);
        LobbySettings settings = LobbySettings.load(yaml);
        assertTrue(settings.texts().isEmpty());
        assertNull(settings.text("textremerciement"));
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
