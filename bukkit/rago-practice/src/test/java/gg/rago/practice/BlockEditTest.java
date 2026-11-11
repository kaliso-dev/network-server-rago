package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The block edit: its wall, the reach and reaction of the bot, who scores in a duel. */
class BlockEditTest {

    @Test
    void theWallIsFiveWideAndThreeHighAcrossTheStation() {
        int cells = 0;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = 0; dy <= 5; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (EditStation.isWall(dx, dy, dz)) {
                        cells++;
                        assertEquals(0, dx);
                    }
                }
            }
        }
        assertEquals(15, cells);
        assertFalse(EditStation.isWall(0, 0, 0), "not the floor");
        assertFalse(EditStation.isWall(0, 4, 0), "not the beam over it");
    }

    @Test
    void theBotReachesAsFarAsAPlayer() {
        // the player against the wall, the bot against the other side: in reach
        assertTrue(BlockEditTraining.inReach(BlockEditTraining.BOT_MIN_X - (-0.3), 0));
        // the player at the start: out of reach
        assertFalse(BlockEditTraining.inReach(BlockEditTraining.BOT_MIN_X - (-2.0), 0));
        assertTrue(BlockEditTraining.BOT_MAX_X > BlockEditTraining.BOT_MIN_X);
        assertTrue(BlockEditTraining.BOT_MAX_X < EditStation.BOT_ROOM + 1, "in its room");
    }

    @Test
    void aFasterBotReactsSooner() {
        assertTrue(BlockEditTraining.reactionOf(1) > BlockEditTraining.reactionOf(2));
        assertTrue(BlockEditTraining.reactionOf(2) > BlockEditTraining.reactionOf(3));
        assertTrue(BlockEditTraining.reactionOf(3) >= 3, "still humanly beatable");
    }

    @Test
    void onlyTheSideThatHitLastScoresByClosing() {
        assertFalse(BlockEditDuel.scores(-1, 0), "no hit yet");
        assertTrue(BlockEditDuel.scores(0, 0));
        assertFalse(BlockEditDuel.scores(1, 0), "hit back: the point is theirs to take");
    }
}
