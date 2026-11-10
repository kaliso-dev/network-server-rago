package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

class KillLinesTest {

    @Test
    void aFinalKillShowsTheKillersOwnCountAsOnHypixel() {
        String line = KillLines.finalKill(ChatColor.AQUA + "vaggww78", ChatColor.RED + "qAbel", 41000);
        assertEquals("vaggww78 was qAbel's final #41,000. FINAL KILL!", ChatColor.stripColor(line));
    }

    @Test
    void aBedBrokenShowsThePlayersOwnCount() {
        String line = KillLines.bed(ChatColor.RED + "Red Bed", ChatColor.BLUE + "qAbel", 1250);
        assertEquals("BED DESTRUCTION > Red Bed was destroyed by qAbel's bed #1,250!", ChatColor.stripColor(line));
    }

    @Test
    void noCountNoLine() {
        assertNull(KillLines.finalKill("a", "b", 0));
        assertNull(KillLines.bed("a", "b", 0));
        assertEquals("7", KillLines.count(7));
    }
}
