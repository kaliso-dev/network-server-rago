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

    /** The text the owner validated on 22/11, word for word, line by line. */
    private static final List<String> VALIDATED = Arrays.asList(
            "THANK YOU",
            "Rago Practice took a very long time to build, but I have",
            "dreamed of it for years: my own server means a lot to me.",
            "It is not made to earn money: Premium ranks get nothing",
            "more in gameplay than anyone else.",
            "",
            "As a big PvP and Hypixel player, I found no way to train.",
            "I was tired of Hypixel RBW housings and of solo practice",
            "maps for Block Placement, and high ping on Hypixel is",
            "unbearable. So I made this server to help you train Block",
            "Placement, Hypixel Bedwars and RBW with Hypixel KB, and",
            "above all with low ping. Fight your friends in 1v1 and",
            "2v2 duels and train with lots of fun, like Minemen but much",
            "better: low ping, Hypixel KB and real modes to train. You",
            "never get bored, and you can train everything.",
            "",
            "Pure passion, no money goal: a real project with a real goal.",
            "As a player, I know what players want: this server is here",
            "to help you train and have more fun with your friends.",
            "Join our Discord: discord.gg/ragooptimizer");

    /** Its first part, on the left (the owner's ask of 22/11, 19h51: "une partit a gauche"). */
    private static final List<String> LEFT = Arrays.asList(
            "THANK YOU",
            "Rago Practice took a very long time to build, but I have",
            "dreamed of it for years: my own server means a lot to me.",
            "It is not made to earn money: Premium ranks get nothing",
            "more in gameplay than anyone else.",
            "",
            "As a big PvP and Hypixel player, I found no way to train.",
            "I was tired of Hypixel RBW housings and of solo practice",
            "maps for Block Placement, and high ping on Hypixel is",
            "unbearable.");

    /** The rest, on the right ("la suite a droite"), from the next sentence. */
    private static final List<String> RIGHT = Arrays.asList(
            "So I made this server to help you train Block Placement,",
            "Hypixel Bedwars and RBW with Hypixel KB, and above all with",
            "low ping. Fight your friends in 1v1 and 2v2 duels and train",
            "with lots of fun, like Minemen but much better: low ping,",
            "Hypixel KB and real modes to train. You never get bored, and",
            "you can train everything.",
            "",
            "Pure passion, no money goal: a real project with a real goal.",
            "As a player, I know what players want: this server is here",
            "to help you train and have more fun with your friends.",
            "Join our Discord: discord.gg/ragooptimizer");

    @Test
    void theThankYouTextAsValidatedCutInTwo() throws Exception {
        LobbySettings settings = LobbySettings.load(LobbySettingsTest.bundledConfig());
        LobbyText left = settings.text("textremerciement");
        LobbyText right = settings.text("textremerciement2");
        assertEquals(LEFT, plain(left));
        assertEquals(RIGHT, plain(right));
        assertEquals("§6§lTHANK YOU", left.lines.get(0), "its title in gold");
        assertEquals("§fJoin our Discord: §ediscord.gg/ragooptimizer", right.lines.get(right.lines.size() - 1),
                "the Discord, as on the products");
        // the left, then the right: the words of the validated text, in its order, none added
        List<String> both = new ArrayList<>(plain(left));
        both.addAll(plain(right));
        assertEquals(words(VALIDATED), words(both));
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

    private static List<String> words(List<String> lines) {
        List<String> words = new ArrayList<>();
        for (String line : lines) {
            for (String word : line.trim().split("\\s+")) {
                if (!word.isEmpty()) {
                    words.add(word);
                }
            }
        }
        return words;
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
