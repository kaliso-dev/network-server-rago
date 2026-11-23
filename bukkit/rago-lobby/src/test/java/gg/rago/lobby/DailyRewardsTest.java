package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The Daily Chest: its rewards and their chances, the draw, never a cosmetic owned, the spin. */
class DailyRewardsTest {

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    private static YamlConfiguration bundled() throws Exception {
        try (Reader reader = new InputStreamReader(DailyRewardsTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private static void assertInvalid(String text, String expectedStart) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DailyRewards.read(yaml(text).getConfigurationSection("coffre-du-jour")));
        assertTrue(error.getMessage().startsWith(expectedStart), error.getMessage());
    }

    @Test
    void theBundledConfigSaysWhatThePluginGives() throws Exception {
        DailyRewards bundled = ServerSettings.load(bundled()).dailyRewards();
        DailyRewards own = DailyRewards.defaults();
        assertEquals(own.entries().size(), bundled.entries().size());
        for (int i = 0; i < own.entries().size(); i++) {
            DailyRewards.Entry mine = own.entries().get(i);
            DailyRewards.Entry read = bundled.entries().get(i);
            assertEquals(mine.id, read.id);
            assertSame(mine.rarity, read.rarity, mine.id);
            assertSame(mine.kind, read.kind, mine.id);
            assertEquals(mine.min, read.min, mine.id);
            assertEquals(mine.max, read.max, mine.id);
            assertEquals(mine.chance, read.chance, 1e-9, mine.id);
        }
        assertEquals(own.entries().size(), ServerSettings.load(yaml("adresse: rago.gg\n")).dailyRewards().entries().size(),
                "a config.yml of before the chest: the plugin's own");
        // the owner's plan: Rago Points from 100 to 500, Network XP from 100 to 2,000, cosmetics
        int pointsMin = Integer.MAX_VALUE;
        int pointsMax = 0;
        int xpMin = Integer.MAX_VALUE;
        int xpMax = 0;
        boolean cosmetic = false;
        for (DailyRewards.Entry entry : own.entries()) {
            if (entry.kind == DailyRewards.Kind.POINTS) {
                pointsMin = Math.min(pointsMin, entry.min);
                pointsMax = Math.max(pointsMax, entry.max);
            } else if (entry.kind == DailyRewards.Kind.XP) {
                xpMin = Math.min(xpMin, entry.min);
                xpMax = Math.max(xpMax, entry.max);
            } else {
                cosmetic = true;
            }
        }
        assertEquals(100, pointsMin);
        assertEquals(500, pointsMax);
        assertEquals(100, xpMin);
        assertEquals(2000, xpMax);
        assertTrue(cosmetic);
    }

    @Test
    void theChestShowsTheChanceOfEachRarity() {
        Map<DailyRewards.Rarity, Double> odds = DailyRewards.defaults().odds();
        assertEquals(Arrays.asList(DailyRewards.Rarity.values()), new ArrayList<>(odds.keySet()), "from the most frequent");
        assertEquals(55, odds.get(DailyRewards.Rarity.COMMON), 1e-9);
        assertEquals(28, odds.get(DailyRewards.Rarity.RARE), 1e-9);
        assertEquals(12, odds.get(DailyRewards.Rarity.EPIC), 1e-9);
        assertEquals(5, odds.get(DailyRewards.Rarity.LEGENDARY), 1e-9);
        assertEquals(Arrays.asList("\u00a7fCommon\u00a77 55%", "\u00a79Rare\u00a77 28%", "\u00a75Epic\u00a77 12%",
                "\u00a76Legendary\u00a77 5%"), DailyRewards.defaults().oddsLines());
        assertEquals("2.5%", DailyRewards.percent(2.5));
        assertEquals("33.3%", DailyRewards.percent(100.0 / 3));
        assertEquals("100%", DailyRewards.percent(100));
    }

    @Test
    void aDrawFollowsTheChancesAndStaysInItsAmounts() {
        DailyRewards rewards = DailyRewards.defaults();
        List<Cosmetic> unowned = new ArrayList<>(Cosmetic.catalogue().values());
        Random random = new Random(22_11_2026L);
        Map<DailyRewards.Rarity, Integer> seen = new EnumMap<>(DailyRewards.Rarity.class);
        int draws = 200_000;
        for (int i = 0; i < draws; i++) {
            DailyRewards.Reward reward = rewards.draw(random, unowned);
            Integer count = seen.get(reward.rarity);
            seen.put(reward.rarity, count == null ? 1 : count + 1);
            switch (reward.kind) {
                case POINTS:
                    assertTrue(reward.amount >= 100 && reward.amount <= 500, "Rago Points: " + reward.amount);
                    break;
                case XP:
                    assertTrue(reward.amount >= 100 && reward.amount <= 2000, "XP: " + reward.amount);
                    break;
                default:
                    assertSame(DailyRewards.Rarity.LEGENDARY, reward.rarity);
                    assertTrue(unowned.contains(reward.cosmetic));
                    break;
            }
        }
        for (Map.Entry<DailyRewards.Rarity, Double> odds : rewards.odds().entrySet()) {
            double share = 100.0 * seen.get(odds.getKey()) / draws;
            assertEquals(odds.getValue(), share, 0.5, odds.getKey() + ": " + share + "%");
        }
        // both ends of a range come out
        DailyRewards.Entry common = rewards.entries().get(0);
        boolean low = false;
        boolean high = false;
        for (int i = 0; i < 20_000; i++) {
            int amount = rewards.of(common, random, unowned).amount;
            low |= amount == common.min;
            high |= amount == common.max;
        }
        assertTrue(low && high, "from " + common.min + " to " + common.max);
    }

    @Test
    void neverACosmeticTheyOwn() {
        DailyRewards rewards = DailyRewards.defaults();
        DailyRewards.Entry cosmetic = rewards.entries().get(rewards.entries().size() - 1);
        assertSame(DailyRewards.Kind.COSMETIC, cosmetic.kind);
        Map<String, Cosmetic> catalogue = Cosmetic.catalogue();
        List<Cosmetic> unowned = Arrays.asList(catalogue.get("trail-flames"), catalogue.get("win-party"));
        Random random = new Random(7);
        boolean flames = false;
        boolean party = false;
        for (int i = 0; i < 200; i++) {
            DailyRewards.Reward reward = rewards.of(cosmetic, random, unowned);
            assertTrue(unowned.contains(reward.cosmetic), "only the two they do not own: " + reward.cosmetic.id());
            assertFalse(reward.instead);
            flames |= reward.cosmetic.id().equals("trail-flames");
            party |= reward.cosmetic.id().equals("win-party");
        }
        assertTrue(flames && party);
        // they own every one: the largest Rago Points of the list instead, still legendary
        DailyRewards.Reward instead = rewards.of(cosmetic, random, Collections.<Cosmetic>emptyList());
        assertSame(DailyRewards.Kind.POINTS, instead.kind);
        assertEquals(500, instead.amount);
        assertNull(instead.cosmetic);
        assertTrue(instead.instead);
        assertSame(DailyRewards.Rarity.LEGENDARY, instead.rarity);
    }

    @Test
    void whatTheRewardsRead() {
        Cosmetic flames = Cosmetic.catalogue().get("trail-flames");
        assertEquals("\u00a76+250 Rago Points",
                new DailyRewards.Reward(DailyRewards.Rarity.RARE, DailyRewards.Kind.POINTS, 250, null, false).label());
        assertEquals("\u00a7e+1,250 Network XP",
                new DailyRewards.Reward(DailyRewards.Rarity.EPIC, DailyRewards.Kind.XP, 1250, null, false).label());
        assertEquals("\u00a76Flames\u00a77 (projectile trail)",
                new DailyRewards.Reward(DailyRewards.Rarity.LEGENDARY, DailyRewards.Kind.COSMETIC, 0, flames, false).label());
        assertEquals("\u00a76\u00a7lLEGENDARY", DailyRewards.Rarity.LEGENDARY.tag());
    }

    @Test
    void theSpinIsShorterAndItsItemsMuchFaster() {
        // the owner's ask of 23/11: a little shorter, the items much faster (before: 32 items in
        // 114 ticks, at most one a tick, 126 ticks from the click to the reward)
        int[] positions = DailyRewards.positions(DailyRewards.SHIFTS, DailyRewards.SPIN_TICKS);
        assertEquals(DailyRewards.SPIN_TICKS, positions.length);
        assertEquals(DailyRewards.SHIFTS, positions[positions.length - 1], "it stops on the reward");
        assertTrue(positions[0] >= 1, "it moves from the first tick");
        assertTrue(positions[19] >= 24, "the first second: more than 20 items (one a tick before): " + positions[19]);
        int stillAtTheEnd = 0;
        for (int i = 0; i < positions.length; i++) {
            int before = i == 0 ? 0 : positions[i - 1];
            assertTrue(positions[i] >= before && positions[i] - before <= 2, "never back, two items a tick at most: "
                    + Arrays.toString(positions));
            stillAtTheEnd = positions[i] == DailyRewards.SHIFTS - 1 ? stillAtTheEnd + 1 : stillAtTheEnd;
        }
        assertTrue(stillAtTheEnd >= 8 && stillAtTheEnd <= 12, "the last item, about half a second as before: " + stillAtTheEnd);
        int total = DailyRewards.SPIN_TICKS + DailyChest.PAUSE;
        assertTrue(total >= 90 && total <= 110, "from the click to the reward, in ticks: " + total);
        // the reel holds the steps and the 9 slots shown, the reward in the centre at the end
        assertEquals(4, DailyChest.CENTRE - DailyChest.REEL);
    }

    @Test
    void theAmountsAndTheMistakesOfConfigYml() throws Exception {
        assertArrayEquals(new int[] {250, 250}, DailyRewards.range("250", "x"));
        assertArrayEquals(new int[] {100, 200}, DailyRewards.range("100-200", "x"));
        assertArrayEquals(new int[] {100, 200}, DailyRewards.range(" 100 - 200 ", "x"));
        DailyRewards one = DailyRewards.read(yaml("coffre-du-jour:\n  recompenses:\n"
                + "    a: {rarete: rare, rago-points: 250, chance: 2.5}\n"
                + "    b: {rarete: commun, xp: 10-20, chance: 7.5}\n").getConfigurationSection("coffre-du-jour"));
        assertEquals(2, one.entries().size());
        assertEquals(25, one.odds().get(DailyRewards.Rarity.RARE), 1e-9, "out of the total of the list");
        assertEquals(250, one.entries().get(0).min);
        assertEquals(250, one.entries().get(0).max);

        String start = "coffre-du-jour:\n  recompenses:\n";
        assertInvalid(start + "    a: {rarete: mythique, xp: 10, chance: 5}\n", "coffre-du-jour.recompenses.a.rarete");
        assertInvalid(start + "    a: {rarete: rare, chance: 5}\n", "coffre-du-jour.recompenses.a: rago-points, xp or cosmetique");
        assertInvalid(start + "    a: {rarete: rare, xp: 10, rago-points: 10, chance: 5}\n", "coffre-du-jour.recompenses.a: one of");
        assertInvalid(start + "    a: {rarete: rare, xp: 200-100, chance: 5}\n", "coffre-du-jour.recompenses.a.xp");
        assertInvalid(start + "    a: {rarete: rare, xp: 0, chance: 5}\n", "coffre-du-jour.recompenses.a.xp");
        assertInvalid(start + "    a: {rarete: rare, xp: beaucoup, chance: 5}\n", "coffre-du-jour.recompenses.a.xp");
        assertInvalid(start + "    a: {rarete: rare, xp: 10, chance: 0}\n", "coffre-du-jour.recompenses.a.chance");
        assertInvalid(start + "    a: {rarete: rare, xp: 10}\n", "coffre-du-jour.recompenses.a.chance");
        assertInvalid(start + "    a: {rarete: rare, cosmetique: oui, chance: 5}\n", "coffre-du-jour.recompenses.a.cosmetique");
        assertInvalid(start + "    a: {rarete: legendaire, cosmetique: true, chance: 5}\n",
                "coffre-du-jour.recompenses: one reward of rago-points or xp at least");
        assertInvalid("coffre-du-jour:\n  recompenses: []\n", "coffre-du-jour.recompenses: a section");
        IllegalArgumentException notASection = assertThrows(IllegalArgumentException.class,
                () -> ServerSettings.load(yaml("coffre-du-jour: 3\n")));
        assertTrue(notASection.getMessage().startsWith("coffre-du-jour: a section"), notASection.getMessage());
    }
}
