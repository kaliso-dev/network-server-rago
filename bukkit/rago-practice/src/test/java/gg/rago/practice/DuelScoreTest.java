package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DuelScoreTest {

    @Test
    void firstToTheTargetWins() {
        DuelScore score = new DuelScore(3);
        assertFalse(score.point(0));
        assertFalse(score.point(1));
        assertFalse(score.point(0));
        assertEquals("2 - 1", score.text(0));
        assertEquals("1 - 2", score.text(1));
        assertFalse(score.isOver());
        assertTrue(score.point(0));
        assertTrue(score.isOver());
        assertThrows(IllegalStateException.class, () -> score.point(1));
    }

    @Test
    void theNextRoundsStartAfterASecond() {
        assertEquals(3, PracticeGame.countdownOf(1, 3));
        assertEquals(1, PracticeGame.countdownOf(2, 3));
        assertEquals(1, PracticeGame.countdownOf(5, 3));
        assertEquals(0, PracticeGame.countdownOf(1, 0));
        assertEquals(0, PracticeGame.countdownOf(3, 0));
    }

    @Test
    void refusesAZeroTarget() {
        assertThrows(IllegalArgumentException.class, () -> new DuelScore(0));
    }
}
