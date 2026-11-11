package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The line above the name and /nick. */
class HeadLineNickTest {

    private static final UUID ALICE = new UUID(0, 1);

    @Test
    void eachChoiceGivesItsLine() {
        assertEquals("§6Gold 245§7 ELO", HeadLines.line(HeadLines.Shown.ELO, 245, "§6Gold 245", "§7[3✫]", 0, 0, 0, null));
        assertEquals("§7Unranked", HeadLines.line(HeadLines.Shown.ELO, -1, "", "§7[3✫]", 0, 0, 0, null));
        assertEquals("§7Level §7[3✫]", HeadLines.line(HeadLines.Shown.LEVEL, -1, "", "§7[3✫]", 0, 0, 0, null));
        assertEquals("§a1,234§7 wins", HeadLines.line(HeadLines.Shown.WINS, -1, "", "", 1234, 0, 0, null));
        assertEquals("§a1§7 win", HeadLines.line(HeadLines.Shown.WINS, -1, "", "", 1, 0, 0, null));
        assertEquals("§c56§7 kills", HeadLines.line(HeadLines.Shown.KILLS, -1, "", "", 0, 56, 0, null));
        assertEquals("§612§7 best win streak", HeadLines.line(HeadLines.Shown.STREAK, -1, "", "", 0, 0, 12, null));
        assertEquals("§dGG", HeadLines.line(HeadLines.Shown.MESSAGE, -1, "", "", 0, 0, 0, "§dGG"));
        assertEquals("", HeadLines.line(HeadLines.Shown.NONE, 300, "§6Gold 300", "", 9, 9, 9, null));
        assertEquals(HeadLines.Shown.WINS, HeadLines.Shown.of("victoires"));
        assertEquals(HeadLines.Shown.WINS, HeadLines.Shown.of("wins"));
        assertEquals(HeadLines.Shown.LEVEL, HeadLines.Shown.of("level"));
        assertEquals(HeadLines.Shown.NONE, HeadLines.Shown.of("none"));
        assertNull(HeadLines.Shown.of("dance"));
    }

    @Test
    void aMessageIsShortColouredAndNeverObfuscated() {
        assertEquals("§dGG §fwell played", HeadLines.cleanMessage("  &dGG &fwell played "));
        assertEquals("§cno magic", HeadLines.cleanMessage("&c&kno magic"));
        StringBuilder fifty = new StringBuilder("&a");
        for (int i = 0; i < 50; i++) {
            fifty.append('x');
        }
        String longOne = HeadLines.cleanMessage(fifty.toString());
        assertEquals(HeadLines.MAX_MESSAGE, ChatColor.stripColor(longOne).length());
        String colours = HeadLines.cleanMessage("&a&b&c&d&e&f&1&2&3&4&5&6&7&8&9&0&a&b&c&d&e&f&1&2&3&4&5&6&7&8&9&0&aHello");
        assertTrue(colours.length() <= 64, colours);
    }

    @Test
    void aNickIsANameOfMinecraft() {
        assertTrue(Nicks.validName("Rago_42"));
        assertFalse(Nicks.validName("ab"));
        assertFalse(Nicks.validName("a name"));
        assertFalse(Nicks.validName("way_too_long_name_x"));
        assertFalse(Nicks.validName(null));
    }

    @Test
    void aNickedPlayerLooksNew() {
        ServerSettings server = ServerSettings.load(new YamlConfiguration());
        StatsBook book = new StatsBook(0, NetworkLevel.DEFAULT);
        NameLooks names = new NameLooks(book, server);
        Map<UUID, String> nicks = new HashMap<>();
        names.setNicks(nicks::get);
        PlayerStats alice = book.profile(ALICE, "Alice");
        alice.setRank("admin");
        alice.addXp(5000);
        alice.cosmetics().add("badge-rago");
        alice.choose(NameStyle.Kind.BADGE, "rago");
        assertEquals("§c[ADMIN] §cAlice", names.shown(ALICE, "Alice"));
        assertEquals(" §c§lRAGO", names.shownBadge(ALICE));
        assertTrue(names.shownLevel(ALICE) > 1);
        nicks.put(ALICE, "Shadow");
        assertEquals("§7Shadow", names.shown(ALICE, "Alice"));
        assertEquals("Shadow", names.shownName(ALICE, "Alice"));
        assertEquals("", names.shownBadge(ALICE));
        assertEquals(1, names.shownLevel(ALICE));
        assertEquals(Ranks.DEFAULT, names.shownRank(ALICE));
        assertEquals(Ranks.DEFAULT.tagPrefix(), names.headPrefix(ALICE));
        assertEquals("§c[ADMIN] §cAlice", names.display(ALICE, "Alice"), "the stats and the staff keep the real name");
    }

    @Test
    void theChoicesAndTheNickAreKept() throws Exception {
        PlayerStats alice = new PlayerStats(ALICE, "Alice");
        alice.setHeadLine("kills");
        alice.setHeadMessage("§dGG");
        alice.setNick("Shadow");
        PlayerStats read = StatsFiles.fromYaml(ALICE, StatsFiles.toYaml(alice));
        assertEquals("kills", read.headLine());
        assertEquals("§dGG", read.headMessage());
        assertEquals("Shadow", read.nick());
        alice.setNick("");
        assertNull(alice.nick());
    }
}
