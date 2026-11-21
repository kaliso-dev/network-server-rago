package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The modes of the stats menu: those played first, on pages of a chest. */
class StatsMenuTest {

    private static List<String> ids(List<ModeDisplay> modes) {
        List<String> ids = new ArrayList<>();
        for (ModeDisplay display : modes) {
            ids.add(display.mode().id());
        }
        return ids;
    }

    @Test
    void theFkdrAlwaysHasTwoDecimals() {
        assertEquals("6.42", StatsMenu.fkdr(642, 100), "the plan's example");
        assertEquals("2.50", StatsMenu.fkdr(5, 2));
        assertEquals("3.00", StatsMenu.fkdr(3, 1), "a whole number shows its decimals too");
        assertEquals("0.33", StatsMenu.fkdr(1, 3));
        assertEquals("0.67", StatsMenu.fkdr(2, 3), "rounded");
        assertEquals("0.07", StatsMenu.fkdr(1, 15));
        assertEquals("5.00", StatsMenu.fkdr(5, 0), "the final kills alone without a final death");
        assertEquals("0.00", StatsMenu.fkdr(0, 0));
        assertEquals("12.05", BoardStat.hundredths(1205), "the boards' values, in hundredths");
    }

    @Test
    void theModesPlayedComeFirstAndTheHiddenOnlyOncePlayed() throws Exception {
        List<ModeDisplay> all = LobbySettings.load(LobbySettingsTest.bundledConfig()).modes();
        int hidden = 0;
        for (ModeDisplay display : all) {
            hidden += display.hidden() ? 1 : 0;
        }
        assertTrue(hidden > 0, "the bundled config has hidden modes (the events, the kits of the sizes)");

        // nothing played: every mode shown, in the order of config.yml, none hidden
        List<String> none = ids(StatsMenu.ordered(all, id -> false));
        assertEquals(all.size() - hidden, none.size());
        assertEquals(all.get(0).mode().id(), none.get(0));
        assertFalse(none.contains("invis-practice"), "the kit of the invis practice sizes is hidden");
        assertTrue(none.contains("invis-practice-1v3"));

        // the sumo and an event played: first, in the order of config.yml, then the others
        Set<String> played = new HashSet<>(Arrays.asList("event-sumo", "sumo"));
        List<String> ordered = ids(StatsMenu.ordered(all, played::contains));
        assertEquals(Arrays.asList("sumo", "event-sumo"), ordered.subList(0, 2));
        assertEquals(all.size() - hidden + 1, ordered.size());
        assertEquals(1, Collections.frequency(ordered, "sumo"));
        assertEquals(none.get(0), ordered.get(2));
    }

    @Test
    void moreModesThanAChestHoldsGoOnPages() {
        assertEquals(1, StatsMenu.pages(0));
        assertEquals(1, StatsMenu.pages(StatsMenu.PAGE_SIZE));
        assertEquals(2, StatsMenu.pages(StatsMenu.PAGE_SIZE + 1));
        assertEquals(2, StatsMenu.pages(66));
        List<Integer> modes = new ArrayList<>();
        for (int i = 0; i < 66; i++) {
            modes.add(i);
        }
        assertEquals(StatsMenu.PAGE_SIZE, StatsMenu.page(modes, 0).size());
        assertEquals(0, (int) StatsMenu.page(modes, 0).get(0));
        assertEquals(66 - StatsMenu.PAGE_SIZE, StatsMenu.page(modes, 1).size());
        assertEquals(StatsMenu.PAGE_SIZE, (int) StatsMenu.page(modes, 1).get(0));
        assertTrue(StatsMenu.page(modes, 2).isEmpty());
    }

    @Test
    void theCategoriesStandOneSlotApartInTheMiddle() {
        assertEquals(Arrays.asList(19, 21, 23, 25), list(StatsMenu.categorySlots(4)), "as the selector");
        assertEquals(Collections.singletonList(22), list(StatsMenu.categorySlots(1)));
        assertEquals(Arrays.asList(20, 22, 24), list(StatsMenu.categorySlots(3)));
        assertEquals(Arrays.asList(18, 20, 22, 24, 26), list(StatsMenu.categorySlots(5)));
        assertEquals(Arrays.asList(18, 19, 20, 21, 22, 23), list(StatsMenu.categorySlots(6)), "side by side above five");
    }

    private static List<Integer> list(int[] slots) {
        List<Integer> list = new ArrayList<>();
        for (int slot : slots) {
            list.add(slot);
        }
        return list;
    }

    @Test
    void eachModeIsInItsCategory() throws Exception {
        LobbySettings settings = LobbySettings.load(LobbySettingsTest.bundledConfig());
        List<String> categories = new ArrayList<>();
        for (ModeCategory category : settings.categories()) {
            categories.add(category.id());
        }
        assertEquals(Arrays.asList("entrainement", "ranked", "duels", "arenes", "fun", "trois-joueurs"), categories);
        assertTrue(StatsMenu.modesOf(settings.modes(), "trois-joueurs", categories).isEmpty(),
                "3 Players shows modes of the Duels and the RBW modes (aussi:), none of its own");
        List<String> buttons = new ArrayList<>();
        for (ModeCategory category : StatsMenu.withModes(settings.categories(), settings.modes())) {
            buttons.add(category.id());
        }
        assertEquals(Arrays.asList("entrainement", "ranked", "duels", "arenes", "fun"), buttons,
                "no button for 3 Players: the stats of its modes are in their categories");
        List<String> fun = ids(StatsMenu.modesOf(settings.modes(), "fun", categories));
        assertTrue(fun.contains("fireball-knockback") && fun.contains("snowball-spleef"), fun.toString());
        List<String> duels = ids(StatsMenu.modesOf(settings.modes(), "duels", categories));
        assertTrue(duels.contains("sumo"));
        assertFalse(duels.contains("ffa"));
        assertTrue(ids(StatsMenu.modesOf(settings.modes(), "arenes", categories)).contains("ffa"));
        int each = 0;
        for (String category : categories) {
            each += StatsMenu.modesOf(settings.modes(), category, categories).size();
        }
        assertEquals(settings.modes().size(), each, "every mode in one category");
        assertTrue(StatsMenu.modesOf(settings.modes(), StatsMenu.OTHER, categories).isEmpty(),
                "the bundled modes all have a category of the selector");
    }

    @Test
    void aCategorySumsItsModes() {
        ModeStats sumo = new ModeStats();
        sumo.wins = 3;
        sumo.losses = 1;
        sumo.kills = 9;
        sumo.deaths = 4;
        sumo.bestStreak = 3;
        ModeStats bedFight = new ModeStats();
        bedFight.wins = 2;
        bedFight.losses = 2;
        bedFight.draws = 1;
        bedFight.finalKills = 5;
        bedFight.finalDeaths = 2;
        bedFight.beds = 4;
        bedFight.bestStreak = 2;
        ModeStats untouched = new ModeStats();
        StatsMenu.Totals totals = StatsMenu.totals(Arrays.asList(sumo, null, bedFight, untouched), "2026-11-21", "2026-W47");
        assertEquals(5, totals.wins);
        assertEquals(3, totals.losses);
        assertEquals(9, totals.games(), "the draws are played too");
        assertEquals(9, totals.kills);
        assertEquals(4, totals.deaths);
        assertEquals(5, totals.finalKills);
        assertEquals(2, totals.finalDeaths);
        assertEquals(4, totals.beds);
        assertEquals(3, totals.bestStreak, "the best of the modes, not their sum");
        assertEquals(2, totals.played, "a mode without a game is not played");
    }
}
