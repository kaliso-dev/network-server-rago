package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The boosters, the Rago Points of the games and trainings, the blocks of the level rewards, the gadgets. */
class BoostersTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");

    private static ServerSettings bundled() throws Exception {
        try (Reader reader = new InputStreamReader(BoostersTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            return ServerSettings.load(YamlConfiguration.loadConfiguration(reader));
        }
    }

    @Test
    void theBoughtBoostersBeatThoseOfPremium() throws Exception {
        double premium = bundled().ranks().xpBoost("premium", Collections.<String>emptyList());
        assertEquals(1.5, premium, 1e-9, "PREMIUM's own boost");
        assertTrue(XpBoosters.Kind.XP.rate > XpBoosters.Kind.DAILY.rate, "a bought XP booster beats the free one of PREMIUM");
        assertTrue(XpBoosters.Kind.DAILY.rate > premium);
        assertTrue(XpBoosters.Kind.COINS.rate >= XpBoosters.Kind.XP.rate);
        assertEquals(3.0, XpBoosters.Kind.XP.rate, 1e-9);
        assertEquals(3.0, XpBoosters.Kind.COINS.rate, 1e-9);
    }

    @Test
    void theRunningBoosterGivesItsRate() {
        PlayerStats alice = new PlayerStats(ALICE, "Alice");
        assertEquals(1.0, XpBoosters.xpRate(alice), 1e-9, "none running");
        alice.setBoosterLeftMs(60_000L);
        assertEquals(2.0, XpBoosters.xpRate(alice), 1e-9, "one started before the kinds: the x2 of then");
        alice.setBoosterRate(3.0);
        assertEquals(3.0, XpBoosters.xpRate(alice), 1e-9);
        alice.setBoosterLeftMs(0L);
        assertEquals(1.0, XpBoosters.xpRate(alice), 1e-9, "ran out");
    }

    @Test
    void eachKindHasItsStock() {
        PlayerStats alice = new PlayerStats(ALICE, "Alice");
        XpBoosters.setCount(alice, XpBoosters.Kind.XP, 2);
        XpBoosters.setCount(alice, XpBoosters.Kind.DAILY, 1);
        XpBoosters.setCount(alice, XpBoosters.Kind.COINS, XpBoosters.MAX_STOCK + 5);
        assertEquals(2, XpBoosters.count(alice, XpBoosters.Kind.XP));
        assertEquals(1, XpBoosters.count(alice, XpBoosters.Kind.DAILY));
        assertEquals(XpBoosters.MAX_STOCK, XpBoosters.count(alice, XpBoosters.Kind.COINS), "never more than the stock");
        XpBoosters.setCount(alice, XpBoosters.Kind.XP, -3);
        assertEquals(0, XpBoosters.count(alice, XpBoosters.Kind.XP));
    }

    @Test
    void theBoostersAreSaved() throws Exception {
        PlayerStats alice = new PlayerStats(ALICE, "Alice");
        alice.setBoosters(4);
        alice.setDailyBoosters(1);
        alice.setBoosterRate(3.0);
        alice.setBoosterLeftMs(90_000L);
        alice.setCoinBoosters(2);
        alice.setCoinBoosterLeftMs(45_000L);
        PlayerStats read = StatsFiles.fromYaml(ALICE, StatsFiles.toYaml(alice));
        assertEquals(4, read.boosters());
        assertEquals(1, read.dailyBoosters());
        assertEquals(3.0, read.boosterRate(), 1e-9);
        assertEquals(90_000L, read.boosterLeftMs());
        assertEquals(2, read.coinBoosters());
        assertEquals(45_000L, read.coinBoosterLeftMs());
        assertEquals(StatsFiles.toYaml(alice), StatsFiles.toYaml(read));
    }

    @Test
    void theCommandKnowsTheKinds() {
        assertEquals(XpBoosters.Kind.XP, NetworkCommands.boosterKind("xp"));
        assertEquals(XpBoosters.Kind.COINS, NetworkCommands.boosterKind("coins"));
        assertEquals(XpBoosters.Kind.COINS, NetworkCommands.boosterKind("Pieces"));
        assertEquals(XpBoosters.Kind.DAILY, NetworkCommands.boosterKind("daily"));
        assertNull(NetworkCommands.boosterKind("fly"));
    }

    @Test
    void everyGameAndTrainingGivesRagoPoints() throws Exception {
        ServerSettings.Points points = bundled().points();
        assertTrue(points.win > 0 && points.loss > 0 && points.kill > 0, "a game, lost too, and a kill");
        assertEquals(3, points.xpPerPoint);
        assertEquals(3, points.forXp(10), "a training: one point for 3 XP");
        assertEquals(1, points.forXp(1), "at least one");
        assertEquals(83, points.forXp(250), "an event won");
        assertEquals(0, points.forXp(0));
        assertEquals(0, new ServerSettings.Points(20, 5, 2, 10, 100, 25, 0).forXp(50), "0: the trainings give none");
    }

    @Test
    void theCoinBoosterMultipliesThePoints() {
        assertEquals(60, StatsService.boosted(20, XpBoosters.COIN_MULTIPLIER));
        assertEquals(5, StatsService.boosted(5, 1.0));
        assertEquals(5, StatsService.boosted(5, 0.5), "never less");
        assertEquals(0, StatsService.boosted(0, 3.0));
    }

    @Test
    void theRewardsOfTheLevelsAreGoldOrCoal() {
        assertEquals(Material.GOLD_BLOCK, LevelMenu.rewardBlock(true));
        assertEquals(Material.COAL_BLOCK, LevelMenu.rewardBlock(false));
    }

    @Test
    void thePremiumGadgetsAreGadgets() {
        // the right click hands every gadget of the fourth slot to the gadgets
        for (HotbarItem item : new HotbarItem[] {HotbarItem.BAT_BLASTER, HotbarItem.EXPLOSIVE_SHEEP, HotbarItem.DISCO_BALL,
                HotbarItem.TELEPORT, HotbarItem.LEAP}) {
            assertTrue(item.gadget(), item.name());
        }
    }
}
