package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KitEditorTest {

    private static final Map<String, String> CATEGORY = new HashMap<>();
    private static final Map<String, String> NAME = new HashMap<>();

    private static void mode(String id, String category, String name) {
        CATEGORY.put(id, category);
        NAME.put(id, name);
    }

    static {
        // as in config.yml: mixed, coloured
        mode("block-edit", "entrainement", "&bBlock edit".replace('&', '\u00a7'));
        mode("invis-practice", "ranked", "\u00a76Invis practice");
        mode("bed-fight-2v2", "duels", "\u00a7aBed Fight 2v2");
        mode("bed-fight-1v1", "duels", "\u00a7aBed Fight 1v1");
        mode("ffa", "arenes", "\u00a7cFFA");
        mode("boxing", "duels", "\u00a7aBoxing");
        mode("attack-defense-1v1", "ranked", "\u00a76Attack and defense 1v1");
        mode("bridge-fight", "arenes", "\u00a7cbridge fight");
        mode("lost", null, "\u00a77A mode of no category");
        mode("elsewhere", "hidden", "\u00a77A mode of a category the selector does not have");
    }

    private static final List<String> CATEGORIES = Arrays.asList("entrainement", "ranked", "duels", "arenes");

    @Test
    void theKitsGoByCategoryThenByName() {
        List<String> modes = new ArrayList<>(NAME.keySet());
        Collections.shuffle(modes, new java.util.Random(7));
        List<KitEditor.Row> rows = KitEditor.rows(modes, CATEGORY, NAME, CATEGORIES);
        assertEquals(5, rows.size());
        assertEquals("entrainement", rows.get(0).category);
        assertEquals(Collections.singletonList("block-edit"), rows.get(0).modes);
        assertEquals("ranked", rows.get(1).category);
        assertEquals(Arrays.asList("attack-defense-1v1", "invis-practice"), rows.get(1).modes);
        assertEquals("duels", rows.get(2).category);
        assertEquals(Arrays.asList("bed-fight-1v1", "bed-fight-2v2", "boxing"), rows.get(2).modes);
        assertEquals("arenes", rows.get(3).category);
        // without the colours and the case: "bridge fight" before "FFA"
        assertEquals(Arrays.asList("bridge-fight", "ffa"), rows.get(3).modes);
        assertNull(rows.get(4).category, "the modes of no category the selector has, last");
        assertEquals(Arrays.asList("elsewhere", "lost"), rows.get(4).modes);
        for (KitEditor.Row row : rows) {
            assertTrue(row.first);
        }
    }

    @Test
    void aCategoryTakesMoreRowsWithMoreThanEightKits() {
        List<String> modes = new ArrayList<>();
        Map<String, String> category = new HashMap<>();
        Map<String, String> name = new HashMap<>();
        for (int i = 1; i <= 11; i++) {
            String id = String.format("duel-%02d", i);
            modes.add(id);
            category.put(id, "duels");
            name.put(id, "Duel " + String.format("%02d", i));
        }
        List<KitEditor.Row> rows = KitEditor.rows(modes, category, name, CATEGORIES);
        assertEquals(2, rows.size());
        assertEquals(KitEditor.ROW_KITS, rows.get(0).modes.size());
        assertTrue(rows.get(0).first);
        assertFalse(rows.get(1).first, "its label on its first row only");
        assertEquals(Arrays.asList("duel-09", "duel-10", "duel-11"), rows.get(1).modes);
    }

    @Test
    void theListTakesMorePagesWhenItDoesNotFit() {
        List<String> modes = new ArrayList<>();
        Map<String, String> category = new HashMap<>();
        for (int i = 0; i < 7; i++) {
            // seven categories of one kit each: seven rows, five a page
            String id = "mode-" + i;
            modes.add(id);
            category.put(id, "c" + i);
        }
        List<String> categories = Arrays.asList("c0", "c1", "c2", "c3", "c4", "c5", "c6");
        List<KitEditor.Row> rows = KitEditor.rows(modes, category, new HashMap<>(), categories);
        assertEquals(2, KitEditor.pages(rows));
        assertEquals(KitEditor.PAGE_ROWS, KitEditor.page(rows, 0).size());
        assertEquals(2, KitEditor.page(rows, 1).size());
        assertEquals("c5", KitEditor.page(rows, 1).get(0).category);
        assertEquals(1, KitEditor.pages(new ArrayList<>()), "one page, even empty");
        assertTrue(KitEditor.page(rows, 9).isEmpty());
    }

    @Test
    void theOtherModesOfAKitAreWrapped() {
        List<String> lines = KitEditor.wrap("Also for: Invis Practice 1v1, Invis Practice 1v2, Invis Practice 1v3.", 32, "");
        assertEquals(Arrays.asList("Also for: Invis Practice 1v1,", "Invis Practice 1v2, Invis", "Practice 1v3."), lines);
        for (String line : lines) {
            assertTrue(line.length() <= 32, line);
        }
    }
}
