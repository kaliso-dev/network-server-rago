package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingsCheckTest {

    @Test
    void acceptsTheBundledValues() {
        assertNull(RagoPractice.check("arenes", 200, 16, 7, 3));
    }

    @Test
    void refusesBadValues() {
        assertTrue(RagoPractice.check("mes arenes", 200, 16, 7, 3).startsWith("monde-arenes"));
        assertTrue(RagoPractice.check("arenes", 50, 16, 7, 3).startsWith("espacement"));
        assertTrue(RagoPractice.check("arenes", 200, 0, 7, 3).startsWith("arenes-max"));
        assertTrue(RagoPractice.check("arenes", 200, 16, 3, 3).startsWith("sumo.rayon"));
        assertTrue(RagoPractice.check("arenes", 200, 16, 7, 0).startsWith("sumo.points"));
    }
}
