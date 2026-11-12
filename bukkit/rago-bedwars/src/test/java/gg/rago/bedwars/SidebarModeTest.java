package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** As in every mode of the server, the sidebar of a game starts with the mode, in the colour of its category. */
class SidebarModeTest {

    @Test
    void theModeComesFirstWhenThereIsRoom() {
        List<String> lines = Arrays.asList("Diamond II in 4:59", "", "R Red: ✔ YOU", "B Blue: ✔", "", "Kills: 0",
                "Final Kills: 0", "Beds Broken: 0");
        List<String> shown = BedwarsGame.withMode("§6Ranked Bedwars 2v2", lines);
        assertEquals("§6Ranked Bedwars 2v2", shown.get(0));
        assertEquals(lines, shown.subList(1, shown.size()), "then the game's own lines");
        assertEquals(BedwarsGame.SIDEBAR_ROOM, shown.size(), "a Bed Wars game keeps all its lines, the lobby's pings under them");

        assertSame(lines, BedwarsGame.withMode(null, lines), "a mode unknown: the game's lines");
        List<String> named = Arrays.asList("§6Invis practice §7Arwhay", "");
        assertSame(named, BedwarsGame.withMode("§6Invis practice", named), "a game that names its mode first");
        List<String> full = Collections.nCopies(BedwarsGame.SIDEBAR_ROOM, "x");
        assertSame(full, BedwarsGame.withMode("§6Attack and defense 1v1", full), "no room left: the game's lines");
    }
}
