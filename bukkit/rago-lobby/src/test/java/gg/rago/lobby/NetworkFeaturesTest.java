package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.Test;

/** The ranks, the ELO divisions, the shop, the sanctions and the server settings of config.yml. */
class NetworkFeaturesTest {

    private static YamlConfiguration bundledConfig() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(NetworkFeaturesTest.class.getResourceAsStream("/config.yml"),
                StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        return yaml;
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    private static void assertInvalid(String text, String expectedStart) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(yaml(text)));
        assertTrue(error.getMessage().startsWith(expectedStart), error.getMessage());
    }

    // --- Ranks ----------------------------------------------------------------------------------

    @Test
    void theHighestRankShowsAndThePermissionsAddUp() {
        Ranks ranks = Ranks.defaults();
        assertEquals("owner", ranks.all().get(0).id(), "the highest first");
        assertEquals("vip", ranks.best(null, Collections.singletonList("vip")).id());
        assertEquals("elite", ranks.best(null, Arrays.asList("vip", "elite", "vipplus")).id());
        assertEquals("mod", ranks.best("mod", Collections.singletonList("legend")).id(), "the staff rank is higher");
        assertEquals(Ranks.DEFAULT, ranks.best(null, Collections.<String>emptyList()));
        assertEquals(Ranks.DEFAULT, ranks.best("gone", Collections.singletonList("gone too")), "unknown ranks left out");
        assertTrue(ranks.has(null, Collections.singletonList("elite"), "vip"), "ELITE inherits VIP+ and VIP");
        assertFalse(ranks.has(null, Collections.singletonList("vip"), "elite"));
        assertTrue(ranks.permissions("helper", Collections.<String>emptyList())
                .containsAll(Arrays.asList("rago.staff", "rago.perk.join", "rago.perk.fly", "rago.perk.chat")));
        assertFalse(ranks.permissions(null, Collections.singletonList("vip")).contains("rago.perk.fly"));
        List<String> sold = new ArrayList<>();
        for (RankDef rank : ranks.sold()) {
            sold.add(rank.id());
        }
        assertEquals(Arrays.asList("vip", "vipplus", "elite", "legend"), sold, "the cheapest first, no staff rank");
        assertTrue(ranks.position(ranks.get("owner")) < ranks.position(ranks.get("vip")));
        assertEquals(ranks.all().size(), ranks.position(Ranks.DEFAULT), "no rank comes last");
        assertEquals("VIP+", ranks.get("vipplus").displayName().replaceAll("§.", ""));
    }

    @Test
    void ranksCannotLoopOrRepeat() {
        RankDef a = new RankDef("a", "[A] ", "§a", "§f", false, Collections.<String>emptyList(), "b", 0, 0,
                Material.NAME_TAG, Collections.<String>emptyList());
        RankDef b = new RankDef("b", "[B] ", "§b", "§f", false, Collections.<String>emptyList(), "a", 0, 0,
                Material.NAME_TAG, Collections.<String>emptyList());
        assertThrows(IllegalArgumentException.class, () -> new Ranks(Arrays.asList(a, b)), "a loop");
        assertThrows(IllegalArgumentException.class, () -> new Ranks(Collections.singletonList(a)), "no rank b");
        RankDef c = new RankDef("c", "[C] ", "§c", "§f", false, Collections.<String>emptyList(), null, 0, 0,
                Material.NAME_TAG, Collections.<String>emptyList());
        assertThrows(IllegalArgumentException.class, () -> new Ranks(Arrays.asList(c, c)), "twice");
    }

    // --- ELO ------------------------------------------------------------------------------------

    @Test
    void theDivisionsGiveMoreAtTheBottomAndTakeMoreAtTheTop() {
        EloLadder ladder = EloLadder.DEFAULT;
        assertEquals("Coal", ladder.division(0).name());
        assertEquals("Coal", ladder.division(99).name());
        assertEquals("Iron", ladder.division(100).name());
        assertEquals("Obsidian", ladder.division(5000).name());
        assertEquals(35, ladder.after(0, true, false));
        assertEquals(0, ladder.after(0, false, false), "never below 0");
        assertEquals(40, ladder.after(0, true, true), "the MVP bonus");
        assertEquals(1010, ladder.after(1000, true, false));
        assertEquals(965, ladder.after(1000, false, false));
        assertEquals("§6Gold 245", ladder.format(245));
        for (int i = 1; i < ladder.divisions().size(); i++) {
            EloLadder.Division lower = ladder.divisions().get(i - 1);
            EloLadder.Division higher = ladder.divisions().get(i);
            assertTrue(higher.win() <= lower.win() && higher.loss() >= lower.loss(), higher.name());
        }
        assertThrows(IllegalArgumentException.class, () -> new EloLadder(Collections.singletonList(
                new EloLadder.Division("Iron", "§f", 100, 30, 10)), 5), "the first division starts at 0");
    }

    // --- Daily reward, shop, sanctions ----------------------------------------------------------

    @Test
    void theDailyStreakGoesOnDayAfterDay() {
        assertEquals(1, Profiles.nextStreak("", 0, "2026-11-09", "2026-11-08"), "a first day");
        assertEquals(4, Profiles.nextStreak("2026-11-08", 3, "2026-11-09", "2026-11-08"), "claimed yesterday");
        assertEquals(1, Profiles.nextStreak("2026-11-06", 3, "2026-11-09", "2026-11-08"), "a missed day");
        assertEquals(0, Profiles.nextStreak("2026-11-09", 4, "2026-11-09", "2026-11-08"), "already claimed today");
        ServerSettings.Points points = new ServerSettings.Points(20, 5, 2, 10, 100, 25);
        assertEquals(100, points.dailyReward(1));
        assertEquals(150, points.dailyReward(3));
        assertEquals(250, points.dailyReward(30), "up to seven days");
        assertEquals("2026-11-08", StatsPeriod.previousDayKey(java.time.ZonedDateTime.of(2026, 11, 9, 12, 0, 0, 0,
                StatsPeriod.ZONE).toInstant().toEpochMilli()));
    }

    @Test
    void theShopItemsStayAwayFromTheBorders() {
        assertEquals(10, ShopMenu.slotOf(0));
        assertEquals(16, ShopMenu.slotOf(6));
        assertEquals(19, ShopMenu.slotOf(7));
        assertTrue(ProfileMenu.bar(0.5f).replaceAll("§.", "").length() == 10);
    }

    @Test
    void sanctionsLastTheirTime() throws Exception {
        assertEquals(TimeUnit.MINUTES.toMillis(30), Punishments.parseDuration("30m"));
        assertEquals(TimeUnit.HOURS.toMillis(2), Punishments.parseDuration("2H"));
        assertEquals(TimeUnit.DAYS.toMillis(14), Punishments.parseDuration("2w"));
        assertEquals(0L, Punishments.parseDuration("perm"));
        assertEquals(-1L, Punishments.parseDuration("cheating"), "a reason, not a duration");
        assertEquals(-1L, Punishments.parseDuration("0d"));
        assertEquals("6d 23h", Punishments.formatDuration(TimeUnit.DAYS.toMillis(7) - TimeUnit.MINUTES.toMillis(30)));
        assertEquals("45m", Punishments.formatDuration(TimeUnit.MINUTES.toMillis(45)));

        long now = System.currentTimeMillis();
        Punishments.Sanction ban = new Punishments.Sanction(Punishments.Kind.BAN, "Cheating", "Sofiene", now - 1000, 0, null);
        Punishments.Sanction mute = new Punishments.Sanction(Punishments.Kind.MUTE, "Spam", "Sofiene", now - 2000, now - 1000, null);
        Punishments.Sanction warn = new Punishments.Sanction(Punishments.Kind.WARN, "Insults", "Mod", now, 0, null);
        assertTrue(ban.active(now));
        assertFalse(mute.active(now), "over");
        assertFalse(warn.active(now), "a warning is never active");
        UUID alice = new UUID(0, 1);
        Map<UUID, List<Punishments.Sanction>> history = new HashMap<>();
        history.put(alice, Arrays.asList(ban, mute, warn));
        Map<UUID, String> names = Collections.singletonMap(alice, "Alice");
        Map<UUID, List<Punishments.Sanction>> read = new HashMap<>();
        Map<UUID, String> readNames = new HashMap<>();
        Punishments.fromYaml(Punishments.toYaml(history, names), read, readNames);
        assertEquals("Alice", readNames.get(alice));
        assertEquals(3, read.get(alice).size());
        assertTrue(read.get(alice).get(0).active(now));
        assertEquals("Cheating", read.get(alice).get(0).reason);
        assertEquals(Punishments.Kind.WARN, read.get(alice).get(2).kind);
        ban.liftedBy = "Admin";
        assertFalse(ban.active(now), "lifted");
        Punishments.fromYaml(Punishments.toYaml(history, names), read, readNames);
        assertEquals("Admin", read.get(alice).get(0).liftedBy);
    }

    @Test
    void theTabHeaderIsSentAsJson() {
        assertEquals("{\"text\":\"\\u00a7bRAGO\\n\\\"hi\\\"\"}", ServerInfo.json("§bRAGO\n\"hi\""));
        assertTrue(Social.buttons("Alice").contains("/friend accept Alice"));
    }

    // --- config.yml -----------------------------------------------------------------------------

    @Test
    void readsTheBundledServerSettings() throws Exception {
        ServerSettings settings = ServerSettings.load(bundledConfig());
        Ranks ranks = settings.ranks();
        assertEquals(Ranks.defaults().all().size(), ranks.all().size());
        assertEquals("§c[ADMIN] ", ranks.get("admin").prefix());
        assertTrue(ranks.get("admin").staff());
        assertEquals("§a[VIP§6+§a] ", ranks.get("vipplus").prefix());
        assertEquals(15_000, ranks.get("vipplus").price());
        assertEquals(25, ranks.get("vipplus").level());
        assertEquals(Material.EMERALD_BLOCK, ranks.get("vipplus").icon());
        assertEquals(Ranks.defaults().permissions(null, Collections.singletonList("legend")),
                ranks.permissions(null, Collections.singletonList("legend")), "the same as the plugin's own ranks");
        assertEquals(EloLadder.DEFAULT.divisions().size(), settings.elo().divisions().size());
        assertEquals(35, settings.elo().after(0, true, false));
        assertEquals(5, settings.elo().mvpBonus());
        assertEquals(20, settings.points().win);
        assertEquals(18, settings.cosmetics().size());
        assertEquals(8, settings.cosmetics(Cosmetic.Type.TRAIL).size());
        assertEquals(2000L, settings.chatCooldownMillis());
        assertTrue(settings.blockRepeats());
        assertEquals(2, settings.tabHeader().size());
        assertEquals(2, settings.motd().size());
        assertEquals(5, settings.announceMinutes());
        assertTrue(settings.link("discord").get(0).contains("discord"));
        assertNull(settings.link("site"), "no website yet");
    }

    @Test
    void anEmptyConfigGivesThePluginsOwnSettings() throws Exception {
        ServerSettings settings = ServerSettings.load(yaml("modes:\n  sumo: {}\n"));
        assertEquals(Ranks.defaults().all().size(), settings.ranks().all().size());
        assertEquals(EloLadder.DEFAULT.format(450), settings.elo().format(450));
        assertEquals(Cosmetic.catalogue().size(), settings.cosmetics().size());
        ServerSettings priced = ServerSettings.load(yaml("boutique:\n  trail-flames: {prix: 10, niveau: 1}\n"));
        assertEquals(10, priced.cosmetics().get("trail-flames").price());
        assertEquals(1, priced.cosmetics().get("trail-flames").level());
        assertEquals(Cosmetic.catalogue().get("trail-clouds").price(), priced.cosmetics().get("trail-clouds").price());
    }

    @Test
    void refusesWrongServerSettings() {
        assertInvalid("rangs:\n  VIP:\n    prefixe: '[VIP] '\n", "rangs.VIP");
        assertInvalid("rangs:\n  vip:\n    prefixe: '&a[VERY LONG PREFIX] '\n", "rangs.vip.prefixe");
        assertInvalid("rangs:\n  vip:\n    couleur: 'green'\n", "rangs.vip.couleur");
        assertInvalid("rangs:\n  mod:\n    staff: true\n    prix: 100\n", "rangs.mod.prix");
        assertInvalid("rangs:\n  vip:\n    herite: nobody\n", "rangs.vip.herite");
        assertInvalid("rangs:\n  vip:\n    icone: NOT_AN_ITEM\n", "rangs.vip.icone");
        assertInvalid("elo:\n  divisions:\n    Iron: {elo: 100}\n", "elo.divisions");
        assertInvalid("boutique:\n  trail-rainbow: {prix: 10}\n", "boutique.trail-rainbow");
        assertInvalid("rago-points:\n  victoire: -1\n", "rago-points.victoire");
        assertInvalid("chat:\n  delai-secondes: 61\n", "chat.delai-secondes");
        assertInvalid("annonces:\n  minutes: 0\n", "annonces.minutes");
        assertInvalid("liens:\n  twitter: 'x'\n", "liens.twitter");
    }

    @Test
    void aPearlInTheAirGoesWhenItsThrowerIsMoved() {
        assertTrue(PearlGuard.moves(TeleportCause.PLUGIN), "a spawn, a respawn, the spectators' point");
        assertTrue(PearlGuard.moves(TeleportCause.COMMAND));
        assertFalse(PearlGuard.moves(TeleportCause.ENDER_PEARL), "the pearl itself");
        assertFalse(PearlGuard.moves(TeleportCause.UNKNOWN), "a move the server corrects");
    }
}
