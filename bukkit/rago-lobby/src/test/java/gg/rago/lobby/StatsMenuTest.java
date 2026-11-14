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
}
