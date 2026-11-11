package gg.rago.lobby.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FireballAimTest {

    @Test
    void aFireballSpeedsUpByATenthOfABlockEachTickAsInVanilla() {
        double[] up = FireballAim.acceleration(0, 5, 0);
        assertArrayEquals(new double[] {0, 0.1, 0}, up, 1e-12);
        double[] diagonal = FireballAim.acceleration(3, 0, 4);
        assertEquals(0.06, diagonal[0], 1e-12);
        assertEquals(0.08, diagonal[2], 1e-12);
        assertEquals(0.1, Math.sqrt(diagonal[0] * diagonal[0] + diagonal[2] * diagonal[2]), 1e-12, "whatever the look's length");
        assertArrayEquals(new double[] {0, -0.1, 0}, FireballAim.acceleration(0, 0, 0), 1e-12, "no direction: down");
    }
}
