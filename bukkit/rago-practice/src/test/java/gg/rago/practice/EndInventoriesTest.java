package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

class EndInventoriesTest {

    @Test
    void aCodeIsSixLettersOrDigitsThatDoNotLookAlike() {
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            String code = EndInventories.code(random);
            assertTrue(code.matches("[a-z2-9]{6}"), code);
            assertFalse(code.contains("l") || code.contains("o") || code.contains("0") || code.contains("1"), code);
        }
    }

    @Test
    void theViewRunsTheCommandOfItsCode() {
        String json = EndInventories.json(ChatColor.RED + "Alice" + ChatColor.GRAY + ": 3 hits", "abc234", "Alice");
        assertTrue(json.startsWith("[\"\",{\"text\":\"\u00a7cAlice\u00a77: 3 hits\"}"), json);
        assertTrue(json.contains("\"text\":\" [View]\""), json);
        assertTrue(json.contains("\"value\":\"/matchinv abc234\""), json);
        assertTrue(json.contains("What Alice had at the end"), json);
        assertEquals("say \\\"hi\\\" \\\\o/", EndInventories.escape("say \"hi\" \\o/"));
    }

    @Test
    void aChestTitleFitsInThirtyTwoCharacters() {
        assertEquals("Alice's inventory", EndInventories.title("Alice"));
        assertEquals(32, EndInventories.title("Abcdefghijklmnopqrstuvwxyz").length());
    }

    @Test
    void theHotbarGoesUnderTheRestAsInThePlayersOwn() {
        assertEquals(27, EndInventories.chestSlot(0), "the first slot of the hotbar: the fourth row");
        assertEquals(35, EndInventories.chestSlot(8));
        assertEquals(0, EndInventories.chestSlot(9), "the rest on top");
        assertEquals(26, EndInventories.chestSlot(35));
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (int slot = 0; slot < 36; slot++) {
            assertTrue(used.add(EndInventories.chestSlot(slot)));
        }
        assertTrue(EndInventories.ARMOUR_SLOT > 35 && EndInventories.NUMBERS_SLOT < EndInventories.SIZE);
    }
}
