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
import org.bukkit.Color;
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
        assertEquals("premium", ranks.best(null, Collections.singletonList("premium")).id());
        assertEquals("mod", ranks.best("mod", Collections.singletonList("premium")).id(), "the staff rank is higher");
        assertEquals(Ranks.DEFAULT, ranks.best(null, Collections.<String>emptyList()));
        assertEquals(Ranks.DEFAULT, ranks.best("gone", Collections.singletonList("gone too")), "unknown ranks left out");
        assertTrue(ranks.has("admin", Collections.<String>emptyList(), "premium"), "the staff has everything of PREMIUM");
        assertFalse(ranks.has(null, Collections.singletonList("premium"), "helper"));
        assertTrue(ranks.permissions("helper", Collections.<String>emptyList()).containsAll(Arrays.asList("rago.staff",
                "rago.perk.join", "rago.perk.fly", "rago.perk.chat", "rago.perk.nick", "rago.perk.message")));
        assertTrue(ranks.permissions(null, Collections.singletonList("premium")).contains("rago.perk.fly"));
        assertTrue(ranks.permissions(null, Collections.singletonList("premium")).contains("rago.perk.speed"),
                "Speed in the lobby, chosen in the settings");
        assertTrue(ranks.sold().isEmpty(), "nothing for Rago Points: PREMIUM+ and VIP are on the web store");
        List<String> shown = new ArrayList<>();
        for (RankDef rank : ranks.forSale()) {
            shown.add(rank.id());
        }
        assertEquals(Arrays.asList("vip", "premium"), shown, "two ranks to buy, the lowest first: VIP on the left of PREMIUM+");
        assertEquals(2, ranks.queuePriority(null, Collections.singletonList("premium")), "PREMIUM+ first in the queues");
        assertEquals(1, ranks.queuePriority(null, Collections.singletonList("vip")), "then VIP");
        assertEquals(0, ranks.queuePriority(null, Collections.<String>emptyList()), "then the others");
        assertEquals(2, ranks.queuePriority("mod", Collections.<String>emptyList()), "the staff inherits PREMIUM+");
        assertTrue(ranks.has(null, Collections.singletonList("premium"), "vip"), "PREMIUM+ has everything of VIP");
        assertTrue(ranks.permissions(null, Collections.singletonList("premium")).contains("rago.cosmetic.kill-hearts"));
        assertTrue(ranks.permissions(null, Collections.singletonList("vip")).contains("rago.perk.fly"), "VIP flies");
        assertEquals("§b[VIP] ", ranks.get("vip").prefix(), "light blue");
        assertEquals("4.99€", ranks.get("vip").webPrice());
        assertEquals("9.99€", ranks.get("premium").webPrice());
        assertEquals("premium", ranks.best(null, Arrays.asList("vip", "premium")).id(), "PREMIUM+ is above VIP");
        assertEquals("VIP", ranks.get("vip").displayName());
        assertFalse(ranks.get("vip").hasPlus());
        assertEquals(1.25, ranks.xpBoost(null, Collections.singletonList("vip")), 1e-9);
        assertTrue(ranks.permissions(null, Collections.singletonList("vip")).contains("rago.perk.speed"));
        assertFalse(ranks.permissions(null, Collections.singletonList("vip")).contains("rago.perk.gadgets"));
        assertEquals(Arrays.asList("§dPREMIUM+: +50% on everything", "§bVIP: +25% on everything"), LevelMenu.boostLines(ranks));
        assertTrue(ranks.position(ranks.get("owner")) < ranks.position(ranks.get("premium")));
        assertEquals(ranks.all().size(), ranks.position(Ranks.DEFAULT), "no rank comes last");
        assertEquals("PREMIUM+", ranks.get("premium").displayName());
        assertEquals(1.5, ranks.xpBoost(null, Collections.singletonList("premium")), 1e-9);
        assertEquals(1.5, ranks.xpBoost("mod", Collections.<String>emptyList()), 1e-9, "inherited");
        assertEquals(1.0, ranks.xpBoost(null, Collections.<String>emptyList()), 1e-9);
        assertEquals(new java.util.LinkedHashSet<>(Arrays.asList("couleur-rose", "badge-premium", "couleur-aqua")),
                ranks.styles(null, Collections.singletonList("premium")), "with the light blue of VIP");
        assertEquals(java.util.Collections.singleton("couleur-aqua"), ranks.styles(null, Collections.singletonList("vip")));
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

    @Test
    void theRanksOfTheWebStoreCanBeGiftedWithRagoPoints() throws Exception {
        Ranks ranks = Ranks.defaults();
        List<String> giftable = new ArrayList<>();
        for (RankDef rank : ranks.giftable()) {
            giftable.add(rank.id() + " " + rank.giftPrice());
        }
        assertEquals(Arrays.asList("vip 25000", "premium 50000"), giftable, "the cheapest first, no staff rank");
        assertFalse(ranks.get("admin").giftable());
        assertFalse(ranks.get("media").giftable(), "given by the staff only");
        RankDef shop = new RankDef("gold", "§6[GOLD] ", "§6", "§f", false, Collections.<String>emptyList(), null, 3000, 5,
                Material.GOLD_INGOT, Collections.<String>emptyList());
        assertEquals(3000, shop.giftPrice(), "a rank of the shop: its price");
        assertEquals(4000, shop.giftedFor(4000).giftPrice(), "unless it has its own");
        assertEquals(3000, shop.giftedFor(4000).price(), "bought for oneself, the same price");
        Ranks read = ServerSettings.load(bundledConfig()).ranks();
        assertEquals(25000, read.get("vip").giftPrice());
        assertEquals(50000, read.get("premium").giftPrice());
        assertInvalid("rangs:\n  mod:\n    staff: true\n    prix-cadeau: 10\n", "rangs.mod.prix-cadeau");
        assertInvalid("rangs:\n  vip:\n    prix-cadeau: -1\n", "rangs.vip.prix-cadeau");
        List<String> lines = Profiles.giftLines("§fAnn", "§d[PREMIUM+] §dBob", "§d[PREMIUM+]");
        assertTrue(lines.size() >= 5, "a big message");
        assertTrue(lines.get(0).contains("§m"), "between two lines");
        StringBuilder all = new StringBuilder();
        for (String line : lines) {
            all.append(org.bukkit.ChatColor.stripColor(line)).append('\n');
        }
        assertTrue(all.toString().contains("RANK GIFT"), all.toString());
        assertTrue(all.toString().contains("Ann gifted [PREMIUM+] to"), all.toString());
        assertTrue(all.toString().contains("[PREMIUM+] Bob!"), all.toString());
        assertTrue(lines.get(2).startsWith(" "), "in the middle of the chat");
        assertEquals(CosmeticEffects.GIFT_WAVES * CosmeticEffects.GIFT_PER_WAVE, 30, "thirty fireworks");
        assertTrue(CosmeticEffects.giftEffect(0).hasTrail());
        assertTrue(!CosmeticEffects.giftEffect(0).getColors().equals(CosmeticEffects.giftEffect(1).getColors()), "every colour");
    }

    @Test
    void onlyThePrivateGamesPlayThePractice() {
        LobbyState state = new LobbyState(null, null);
        UUID duels = UUID.randomUUID();
        UUID privateGames = UUID.randomUUID();
        for (UUID player : Arrays.asList(duels, privateGames)) {
            state.enterGame(player, "top-bridge-1v1");
            state.markPrivate(player);
        }
        state.markPractice(privateGames);
        assertTrue(state.isInPrivateGame(duels), "a party that fills Top Bridge 1v1 from the Duels: no stats");
        assertFalse(state.isInPractice(duels), "but the rounds to 5 points");
        assertTrue(state.isInPractice(privateGames), "the Private top bridge: the practice");
        state.leaveGame(privateGames);
        assertFalse(state.isInPractice(privateGames));
        UUID queue = UUID.randomUUID();
        state.enterGame(queue, "top-bridge-1v1");
        state.markPractice(queue);
        assertFalse(state.isInPractice(queue), "only a private game");
    }

    // --- config.yml -----------------------------------------------------------------------------

    @Test
    void readsTheBundledServerSettings() throws Exception {
        ServerSettings settings = ServerSettings.load(bundledConfig());
        Ranks ranks = settings.ranks();
        assertEquals(Ranks.defaults().all().size(), ranks.all().size());
        assertEquals("§c[ADMIN] ", ranks.get("admin").prefix());
        assertTrue(ranks.get("admin").staff());
        RankDef premium = ranks.get("premium");
        RankDef own = Ranks.premium();
        assertEquals(own.prefix(), premium.prefix());
        assertEquals(own.xpBoost(), premium.xpBoost(), 1e-9);
        assertEquals(own.style(), premium.style());
        assertEquals(own.badge(), premium.badge());
        assertTrue(premium.webStore());
        assertEquals(0, premium.price());
        assertEquals(Material.NETHER_STAR, premium.icon());
        assertEquals(own.perks().size(), premium.perks().size());
        RankDef vip = ranks.get("vip");
        RankDef ownVip = Ranks.vip();
        assertEquals(ownVip.prefix(), vip.prefix());
        assertEquals(ownVip.xpBoost(), vip.xpBoost(), 1e-9);
        assertEquals(ownVip.style(), vip.style());
        assertNull(vip.badge());
        assertTrue(vip.webStore());
        assertEquals(Material.DIAMOND, vip.icon());
        assertEquals("4.99€", vip.webPrice());
        assertEquals(1, vip.queuePriority());
        assertEquals(2, premium.queuePriority());
        assertEquals("vip", premium.parent());
        assertEquals(ownVip.perks().size(), vip.perks().size());
        assertEquals(Ranks.defaults().permissions(null, Collections.singletonList("vip")),
                ranks.permissions(null, Collections.singletonList("vip")));
        List<String> web = new ArrayList<>();
        for (RankDef rank : StoreMenu.webRanks(settings)) {
            web.add(rank.id());
        }
        assertEquals(Arrays.asList("vip", "premium"), web, "in the store, VIP on the left, PREMIUM+ on its right");
        assertEquals("9.99€", premium.webPrice());
        assertEquals(Ranks.defaults().permissions("admin", Collections.singletonList("premium")),
                ranks.permissions("admin", Collections.singletonList("premium")), "the same as the plugin's own ranks");
        assertEquals(NameStyle.catalogue().keySet(), settings.styles().keySet(), "the plugin's own styles");
        for (NameStyle style : NameStyle.catalogue().values()) {
            NameStyle read = settings.styles().get(style.key());
            assertEquals(style.text(), read.text(), style.key());
            assertEquals(style.obtain(), read.obtain(), style.key());
            assertEquals(style.sample(), read.sample(), style.key());
        }
        assertEquals(EloLadder.DEFAULT.divisions().size(), settings.elo().divisions().size());
        assertEquals(35, settings.elo().after(0, true, false));
        assertEquals(5, settings.elo().mvpBonus());
        assertEquals(20, settings.points().win);
        assertEquals(22, settings.cosmetics().size());
        assertEquals(8, settings.cosmetics(Cosmetic.Type.TRAIL).size());
        assertEquals(2000L, settings.chatCooldownMillis());
        assertTrue(settings.blockRepeats());
        // the colours of the server: its name in yellow, the slogan, rago.gg at the bottom of the tab list
        assertEquals(Arrays.asList(Brand.NAME, Brand.SLOGAN, ""), settings.tabHeader());
        assertEquals(4, settings.tabFooter().size());
        assertEquals(Brand.ADDRESS, settings.tabFooter().get(3));
        assertEquals(Arrays.asList("§eRAGO PRACTICE §8• §fBed Wars Practice, Ranked & Duels",
                "§6§lNEW §8» §fTimed bridge, prestiges & season badges"), settings.motd(), "what it is, then what is new");
        assertEquals(Collections.emptyList(), ServerInfo.tooWide(settings.motd()), "each line fits in the server list");
        assertEquals(5, settings.announceMinutes());
        for (String line : settings.announcements()) {
            assertTrue(line.startsWith(Brand.PREFIX), "one prefix for the server: " + line);
        }
        assertTrue(settings.link("discord").get(0).contains("discord"));
        assertTrue(settings.link("site").get(0).contains("rago-optimizer.com"));
        assertTrue(settings.link("boutique").get(0).contains("rago-optimizer.com"));
    }

    @Test
    void anEmptyConfigGivesThePluginsOwnSettings() throws Exception {
        ServerSettings settings = ServerSettings.load(yaml("modes:\n  sumo: {}\n"));
        assertEquals(Ranks.defaults().all().size(), settings.ranks().all().size());
        assertEquals(Brand.NAME, settings.tabHeader().get(0), "the colours of the server without config.yml too");
        assertEquals(Brand.ADDRESS, settings.tabFooter().get(settings.tabFooter().size() - 1));
        assertEquals(Collections.emptyList(), ServerInfo.tooWide(settings.motd()));
        assertTrue(settings.announcements().get(0).startsWith(Brand.PREFIX));
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
        assertInvalid("rangs:\n  vip:\n    style: nothing\n", "rangs.vip.style");
        assertInvalid("rangs:\n  vip:\n    badge: nothing\n", "rangs.vip.badge");
        assertInvalid("rangs:\n  vip:\n    boost-xp: 10\n", "rangs.vip.boost-xp");
        assertInvalid("rangs:\n  mod:\n    staff: true\n    boutique-en-ligne: true\n", "rangs.mod.boutique-en-ligne");
        assertInvalid("styles:\n  couleurs:\n    feu: {degrade: 'red'}\n", "styles.couleurs.feu.degrade");
        assertInvalid("styles:\n  badges:\n    long: {texte: '&c&lMUCH TOO LONG'}\n", "styles.badges.long.texte");
        assertInvalid("styles:\n  badges:\n    rago: {texte: '&cRAGO', obtention: free}\n", "styles.badges.rago.obtention");
    }

    @Test
    void theLobbyHearsEveryArrivalAndTheRanksInStyle() {
        String ranked = ServerInfo.arrivalLine("§a[VIP] §aSofiene", true);
        assertTrue(ranked.contains("§a[VIP] §aSofiene"), ranked);
        assertTrue(ranked.contains("joined the lobby!"), ranked);
        assertEquals("§7Sofiene joined the lobby.", ServerInfo.arrivalLine("§7Sofiene", false));
        assertEquals(Color.LIME, ServerInfo.colourOf("§a"), "the firework of a VIP");
        assertEquals(Color.AQUA, ServerInfo.colourOf("§b"));
        assertEquals(Color.WHITE, ServerInfo.colourOf(""));
    }

    @Test
    void theSidebarShowsTheProgressInPercent() {
        assertEquals("0%", LobbyScoreboard.percent(0f));
        assertEquals("45%", LobbyScoreboard.percent(0.456f));
        assertEquals("99%", LobbyScoreboard.percent(0.9999f), "never 100% before the level");
        assertTrue(LobbyPoints.validName(DailyChest.POINT), "/lobby point daily places the chest");
    }

    @Test
    void aPearlInTheAirGoesWhenItsThrowerIsMoved() {
        assertTrue(PearlGuard.moves(TeleportCause.PLUGIN), "a spawn, a respawn, the spectators' point");
        assertTrue(PearlGuard.moves(TeleportCause.COMMAND));
        assertFalse(PearlGuard.moves(TeleportCause.ENDER_PEARL), "the pearl itself");
        assertFalse(PearlGuard.moves(TeleportCause.UNKNOWN), "a move the server corrects");
    }
}
