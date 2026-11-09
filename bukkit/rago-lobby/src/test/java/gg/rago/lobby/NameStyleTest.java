package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.game.GameMode;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The colours of name, the badges and the PREMIUM rank. */
class NameStyleTest {

    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);

    @Test
    void aGradientGoesFromTheFirstColourToTheLast() {
        assertEquals("§5So§dfie§fne", NameStyle.gradient("Sofiene", "5df", false));
        assertEquals("§6Rago", NameStyle.gradient("Rago", "6", false), "one colour, written once");
        assertEquals("§6§lR§e§lag§6§lo", NameStyle.gradient("Rago", "6ee6", true), "bold again after each colour");
        assertEquals("§cA", NameStyle.gradient("A", "c6e", false), "one letter takes the first colour");
        assertEquals("", NameStyle.gradient("", "c6e", false));
        String rainbow = NameStyle.gradient("Rainbow", "c6eab9d", false);
        assertEquals("Rainbow", ChatColor.stripColor(rainbow));
        assertTrue(rainbow.startsWith("§c") && rainbow.contains("§d"), rainbow);
    }

    @Test
    void theCatalogueFitsTheNameTagsOfOnePointEight() {
        Map<String, NameStyle> all = NameStyle.catalogue();
        Set<String> badges = new HashSet<>();
        for (NameStyle style : all.values()) {
            if (style.kind() == NameStyle.Kind.BADGE) {
                badges.add(ChatColor.stripColor(style.text()));
                assertTrue(style.text().length() <= NameStyle.MAX_BADGE, style.key());
            } else {
                assertTrue(NameStyle.isStops(style.text()), style.key());
                // the rank prefix and the colour above the head fit in a team prefix
                assertTrue((Ranks.premium().prefix() + style.mainColour()).length() <= RankDef.MAX_PREFIX, style.key());
            }
        }
        assertTrue(badges.containsAll(java.util.Arrays.asList("RAGO", "BETTER", "PVP", "YT")), badges.toString());
        assertEquals(NameStyle.Obtain.PREMIUM, all.get("couleur-rose").obtain());
        assertEquals(NameStyle.Obtain.STORE, all.get("badge-rago").obtain());
        assertEquals("§d", all.get("couleur-rose").mainColour(), "the middle of purple, pink, white");
        assertEquals("§e§l", all.get("couleur-legende").mainColour());
    }

    @Test
    void aPlayerWearsWhatTheyChoseElseWhatTheirRankGives() {
        Map<String, NameStyle> all = NameStyle.catalogue();
        java.util.function.Function<String, NameStyle> colours = id -> all.get(NameStyle.key(NameStyle.Kind.COLOUR, id));
        Set<String> owned = new HashSet<>(java.util.Arrays.asList("couleur-rose", "couleur-feu"));
        assertSame(all.get("couleur-feu"), NameLooks.wear("feu", "rose", colours, s -> owned.contains(s.key())));
        assertSame(all.get("couleur-rose"), NameLooks.wear(null, "rose", colours, s -> owned.contains(s.key())));
        assertSame(all.get("couleur-rose"), NameLooks.wear("ocean", "rose", colours, s -> owned.contains(s.key())),
                "a colour no longer owned gives way to the rank's");
        assertNull(NameLooks.wear("", "rose", colours, s -> true), "none, by choice");
        assertNull(NameLooks.wear(null, null, colours, s -> true));
        assertNull(NameLooks.wear("gone", null, colours, s -> true), "a colour taken out of config.yml");
    }

    @Test
    void premiumGetsThePinkNameTheBadgeAndTheBoost() {
        ServerSettings server = ServerSettings.load(new YamlConfiguration());
        StatsBook book = new StatsBook(0, NetworkLevel.DEFAULT);
        NameLooks names = new NameLooks(book, server);
        book.setBoost(names::xpBoost);
        PlayerStats alice = book.profile(ALICE, "Alice");
        PlayerStats bob = book.profile(BOB, "Bob");
        alice.boughtRanks().add("premium");
        assertEquals("§d[PREMIUM] " + NameStyle.gradient("Alice", "5ddfd", false), names.display(ALICE, "Alice"));
        assertEquals(" §d§l✦", names.badgeAfter(ALICE));
        assertEquals("§d", names.headColour(ALICE));
        assertEquals("§7Bob", names.display(BOB, "Bob"));
        assertEquals("", names.badgeAfter(BOB));

        NameStyle rago = server.style(NameStyle.Kind.BADGE, "rago");
        assertTrue(!names.owns(bob, rago), "a store badge is bought");
        bob.cosmetics().add(rago.key());
        bob.choose(NameStyle.Kind.BADGE, "rago");
        assertEquals(" §c§lRAGO", names.badgeAfter(BOB));
        assertTrue(!names.owns(bob, server.style(NameStyle.Kind.COLOUR, "rose")), "the pink of PREMIUM is not his");
        bob.choose(NameStyle.Kind.COLOUR, "rose");
        assertEquals("§7Bob", names.display(BOB, "Bob"), "he cannot wear what he does not own");

        alice.setRank("admin");
        assertEquals("§c[ADMIN] §cAlice", names.display(ALICE, "Alice"), "a staff rank keeps its colour by default");
        alice.choose(NameStyle.Kind.COLOUR, "rose");
        assertTrue(names.display(ALICE, "Alice").startsWith("§c[ADMIN] §5"), "and may wear the pink of PREMIUM");

        GameMode sumo = new GameMode("sumo", "Sumo", 2, 1, false);
        book.record(sumo, Collections.singletonList(ALICE), Collections.singletonList(BOB), id -> "?", 0L);
        assertEquals(75, alice.xp(), "a win with the boost of PREMIUM: 50 x 1.5");
        assertEquals(15, bob.xp(), "a loss without boost");
        assertEquals("", StatsService.boostText(1.0));
        assertEquals("§d x1.5 boost", StatsService.boostText(1.5));
        assertEquals("§d x2 boost", StatsService.boostText(2.0));
    }

    @Test
    void theBadgeAndTheLevelShareTheSuffix() {
        assertEquals(" §d§l✦ §7[12✫]", LobbyScoreboard.suffix(" §d§l✦", " §7[12✫]"));
        assertEquals(" §c§lRAGO", LobbyScoreboard.suffix(" §c§lRAGO", " §7[12✫]"), "too long: the badge alone");
        assertEquals(" §7[12✫]", LobbyScoreboard.suffix("", " §7[12✫]"));
        for (NameStyle style : NameStyle.catalogue().values()) {
            if (style.kind() == NameStyle.Kind.BADGE) {
                assertTrue(LobbyScoreboard.suffix(" " + style.text(), " §7[100✫]").length() <= 16, style.key());
            }
        }
    }

    @Test
    void theStyleCommandFindsAStyleByItsKeyOrItsId() {
        ServerSettings server = ServerSettings.load(new YamlConfiguration());
        assertEquals("couleur-feu", NetworkCommands.styleNamed(server, "couleur-feu").key());
        assertEquals("couleur-feu", NetworkCommands.styleNamed(server, "FEU").key());
        assertEquals("badge-yt", NetworkCommands.styleNamed(server, "yt").key());
        assertEquals("badge-premium", NetworkCommands.styleNamed(server, "premium").key(), "only a badge has this id");
    }

    @Test
    void theWoolOfEachColour() {
        assertEquals(2, StyleMenu.wool('d'), "pink: magenta wool");
        assertEquals(14, StyleMenu.wool('c'));
        assertEquals(0, StyleMenu.wool('f'));
        assertEquals(0, StyleMenu.wool('z'));
    }
}
