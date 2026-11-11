package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

class MatchRecapTest {

    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);

    @Test
    void countsTheHitsAndTheBestComboOfEachPlayer() {
        MatchRecap recap = new MatchRecap();
        assertTrue(recap.isEmpty());
        recap.hit(ALICE, BOB);
        recap.hit(ALICE, BOB);
        recap.hit(ALICE, BOB);
        recap.hit(BOB, ALICE);
        recap.hit(ALICE, BOB);
        recap.hit(ALICE, BOB);
        recap.hit(BOB, ALICE);
        recap.hit(BOB, ALICE);
        assertEquals(5, recap.hits(ALICE));
        assertEquals(3, recap.bestCombo(ALICE), "three in a row before Bob hit back");
        assertEquals(3, recap.hits(BOB));
        assertEquals(2, recap.bestCombo(BOB));
        assertEquals(0, recap.hits(new UUID(0, 9)));
    }

    @Test
    void tellsOneLinePerPlayerInTheColourOfTheirSide() {
        MatchRecap recap = new MatchRecap();
        recap.hit(ALICE, BOB);
        List<String> lines = recap.lines(Arrays.asList(Collections.singletonList(ALICE), Collections.singletonList(BOB)),
                id -> id.equals(ALICE) ? ChatColor.GREEN + "Alice" : "Bob", Duel.CHAT);
        assertEquals(3, lines.size());
        assertEquals("Match recap", ChatColor.stripColor(lines.get(0)));
        assertEquals(" - Alice: 1 hit, best combo 1", ChatColor.stripColor(lines.get(1)));
        assertTrue(lines.get(1).contains(ChatColor.RED + "Alice"), "red side, the name's own colour left out");
        assertEquals(" - Bob: 0 hits, best combo 0", ChatColor.stripColor(lines.get(2)));
        assertTrue(lines.get(2).contains(ChatColor.BLUE + "Bob"));
    }
}
