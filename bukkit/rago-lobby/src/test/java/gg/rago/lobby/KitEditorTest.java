package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void theFirstChestShowsTheCategoriesWithAKitInTheirOrder() {
        // the owner's ask of 22/11 (19h51): as the compass, the categories first
        List<String> modes = new ArrayList<>(NAME.keySet());
        Collections.shuffle(modes, new java.util.Random(7));
        assertEquals(Arrays.asList("entrainement", "ranked", "duels", "arenes", KitEditor.OTHER),
                KitEditor.categories(modes, CATEGORY, CATEGORIES));
        List<String> listed = Arrays.asList("entrainement", "ranked", "duels", "arenes", "fun");
        assertEquals(Arrays.asList("entrainement", "duels"),
                KitEditor.categories(Arrays.asList("boxing", "block-edit"), CATEGORY, listed),
                "a category without a kit is left out, the order is the selector's");
        assertEquals(Collections.emptyList(), KitEditor.categories(new ArrayList<>(), CATEGORY, listed));
    }

    @Test
    void aCategoryShowsItsKitsByName() {
        List<String> modes = new ArrayList<>(NAME.keySet());
        Collections.shuffle(modes, new java.util.Random(7));
        assertEquals(Collections.singletonList("block-edit"), KitEditor.kitsOf("entrainement", modes, CATEGORY, NAME, CATEGORIES));
        assertEquals(Arrays.asList("attack-defense-1v1", "invis-practice"), KitEditor.kitsOf("ranked", modes, CATEGORY, NAME, CATEGORIES));
        assertEquals(Arrays.asList("bed-fight-1v1", "bed-fight-2v2", "boxing"), KitEditor.kitsOf("duels", modes, CATEGORY, NAME, CATEGORIES));
        // without the colours and the case: "bridge fight" before "FFA"
        assertEquals(Arrays.asList("bridge-fight", "ffa"), KitEditor.kitsOf("arenes", modes, CATEGORY, NAME, CATEGORIES));
        assertEquals(Arrays.asList("elsewhere", "lost"), KitEditor.kitsOf(KitEditor.OTHER, modes, CATEGORY, NAME, CATEGORIES),
                "the kits of no category the selector has");
        assertEquals(NAME.size(), KitEditor.kitsOf(KitEditor.ALL, modes, CATEGORY, NAME, CATEGORIES).size(),
                "without categories, every kit");
    }

    @Test
    void theKitsOfACategoryAreLaidAsInTheMenusOfTheCompass() {
        // seven a row under an empty row, the bottom row for the back button
        assertEquals(ModeMenu.slotOf(0), KitEditor.kitSlot(0, 3));
        assertEquals(10, KitEditor.kitSlot(0, 14));
        assertEquals(19, KitEditor.kitSlot(7, 14), "the eighth kit on the next row");
        assertEquals(3, KitEditor.kitRows(1));
        assertEquals(3, KitEditor.kitRows(7));
        assertEquals(4, KitEditor.kitRows(14), "the 14 kits of Duels: two rows");
        assertEquals(6, KitEditor.kitRows(KitEditor.FRAMED_KITS));
        // beyond, nine a row from the top, five rows at most
        assertEquals(KitEditor.FRAMED_KITS, KitEditor.kitSlot(KitEditor.FRAMED_KITS, KitEditor.FRAMED_KITS + 1));
        assertEquals(5, KitEditor.kitRows(KitEditor.FRAMED_KITS + 1));
        assertEquals(6, KitEditor.kitRows(KitEditor.MAX_KITS));
        assertEquals(6, KitEditor.kitRows(KitEditor.MAX_KITS + 10));
        for (int count = 1; count <= KitEditor.MAX_KITS; count++) {
            int size = 9 * KitEditor.kitRows(count);
            assertTrue(size <= 54, "a chest has six rows at most: " + count);
            for (int i = 0; i < count; i++) {
                assertTrue(KitEditor.kitSlot(i, count) < size - 9, "above the bottom row: kit " + i + " of " + count);
            }
        }
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
