package gg.rago.buildtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BuilderCommandsTest {

    @Test
    void speedOneIsMinecraftsNormalFlyingSpeed() {
        assertEquals(0.1f, BuilderCommands.flySpeed(1), 1e-6f);
        assertEquals(1.0f, BuilderCommands.flySpeed(10), 1e-6f);
    }

    @Test
    void parsesOnlyWholeSpeedsFromOneToTen() {
        assertEquals(Integer.valueOf(1), BuilderCommands.parseSpeed("1"));
        assertEquals(Integer.valueOf(10), BuilderCommands.parseSpeed(" 10 "));
        assertNull(BuilderCommands.parseSpeed("0"));
        assertNull(BuilderCommands.parseSpeed("11"));
        assertNull(BuilderCommands.parseSpeed("2.5"));
        assertNull(BuilderCommands.parseSpeed("vite"));
    }
}
