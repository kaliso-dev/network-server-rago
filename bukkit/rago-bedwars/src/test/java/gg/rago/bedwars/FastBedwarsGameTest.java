package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

class FastBedwarsGameTest {

    private static final int[] EVENTS = {180, 360, 540, 720, 900, 1350};

    /** Without the upgrades, no diamond generator: no Diamond II nor III, the other events as set. */
    @Test
    void withoutUpgradesTheDiamondsDoNotGoUp() {
        BedwarsSettings.Fast fast = new BedwarsSettings.Fast(EVENTS, Arrays.asList("wool"), false);
        assertArrayEquals(new int[] {-1, 360, -1, 720, 900, 1350}, FastBedwarsGame.timeline(fast, false));
        assertArrayEquals(EVENTS, FastBedwarsGame.timeline(fast, true), "with the upgrades, the diamonds too");
        assertArrayEquals(EVENTS, fast.events, "the settings stay as they are");
    }

    @Test
    void theWelcomeSaysWhatIsFast() {
        BedwarsSettings.Fast none = new BedwarsSettings.Fast(EVENTS, Arrays.asList("wool"), false);
        assertEquals("Faster forges, a smaller shop, no team upgrades. The beds go at 15:00, the game ends at 22:30.",
                ChatColor.stripColor(FastBedwarsGame.rules(none)));
        BedwarsSettings.Fast upgrades = new BedwarsSettings.Fast(EVENTS, Arrays.asList("wool"), true);
        assertEquals("Faster forges, a smaller shop. The beds go at 15:00, the game ends at 22:30.",
                ChatColor.stripColor(FastBedwarsGame.rules(upgrades)));
    }
}
