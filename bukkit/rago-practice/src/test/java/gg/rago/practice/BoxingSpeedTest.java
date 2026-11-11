package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoxingSpeedTest {

    @Test
    void speedComesOffThenBackForEachPlayer() {
        UUID one = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(Boxing.hasSpeed(one));
        assertFalse(Boxing.toggleSpeed(one));
        assertFalse(Boxing.hasSpeed(one));
        assertTrue(Boxing.hasSpeed(other));
        assertTrue(Boxing.toggleSpeed(one));
        assertTrue(Boxing.hasSpeed(one));
    }

    @Test
    void theLineSaysWhatTheSpeedIsNow() {
        assertTrue(SpeedCommand.line(true).contains("ON"));
        assertTrue(SpeedCommand.line(true).contains("take it off"));
        assertTrue(SpeedCommand.line(false).contains("OFF"));
        assertTrue(SpeedCommand.line(false).contains("get it back"));
    }

    @Test
    void theCommandIsDeclared() {
        InputStream in = BoxingSpeedTest.class.getResourceAsStream("/plugin.yml");
        try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            assertTrue(scanner.next().contains("\n  speed:\n"));
        }
    }
}
