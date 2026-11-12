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

    @Test
    void addsALineOfWhatTheDuelHad() {
        MatchRecap recap = new MatchRecap();
        recap.hit(ALICE, BOB);
        recap.ate(ALICE);
        recap.ate(ALICE);
        recap.placed(BOB);
        assertEquals(2, recap.apples(ALICE));
        assertEquals(1, recap.blocks(BOB));
        assertEquals("   2 golden apples, 0 blocks", ChatColor.stripColor(recap.details(ALICE)), "no health: nobody was hurt");
        assertEquals("   0 golden apples, 1 block", ChatColor.stripColor(recap.details(BOB)));
        recap.hurt();
        recap.health(ALICE, 15);
        recap.health(BOB, 0);
        recap.bed(ALICE);
        assertEquals("   7.5\u2764 left, 2 golden apples, 0 blocks, 1 bed broken", ChatColor.stripColor(recap.details(ALICE)));
        assertEquals("   0\u2764 left, 0 golden apples, 1 block, 0 beds broken", ChatColor.stripColor(recap.details(BOB)));
        List<String> lines = recap.lines(Arrays.asList(Collections.singletonList(ALICE), Collections.singletonList(BOB)),
                id -> id.equals(ALICE) ? "Alice" : "Bob", Duel.CHAT);
        assertEquals(5, lines.size(), "the title, then two lines for each");
        assertEquals(Arrays.asList("Hits: 1", "Best combo: 1", "Golden apples eaten: 2", "Blocks placed: 0", "Beds broken: 1"),
                strip(recap.numbers(ALICE)));
    }

    @Test
    void aBoxingHasNoSecondLine() {
        MatchRecap recap = new MatchRecap();
        recap.hit(ALICE, BOB);
        recap.health(ALICE, 20);
        assertEquals(null, recap.details(ALICE), "nobody hurt, nothing eaten, placed nor broken");
        assertEquals(Arrays.asList("Hits: 1", "Best combo: 1"), strip(recap.numbers(ALICE)));
    }

    @Test
    void showsTheHeartsAsTheGameDoes() {
        assertEquals("10", MatchRecap.hearts(20));
        assertEquals("7.5", MatchRecap.hearts(15));
        assertEquals("7", MatchRecap.hearts(13.6), "rounded up, as the hearts of the game");
        assertEquals("0.5", MatchRecap.hearts(0.2));
        assertEquals("0", MatchRecap.hearts(0));
        assertEquals("0", MatchRecap.hearts(-3));
    }

    private static List<String> strip(List<String> lines) {
        List<String> plain = new java.util.ArrayList<>();
        for (String line : lines) {
            plain.add(ChatColor.stripColor(line));
        }
        return plain;
    }
}
