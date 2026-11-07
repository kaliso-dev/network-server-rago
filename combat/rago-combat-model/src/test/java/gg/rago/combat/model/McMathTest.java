package gg.rago.combat.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class McMathTest {

    @Test
    void tableSineStaysCloseToTheExactSine() {
        for (int degrees = -720; degrees <= 720; degrees++) {
            float radians = McMath.yawToRadians(degrees);
            assertEquals(Math.sin(radians), McMath.sin(radians), 1.0E-4D, "sin " + degrees);
            assertEquals(Math.cos(radians), McMath.cos(radians), 1.0E-4D, "cos " + degrees);
        }
    }

    @Test
    void cardinalYawsGiveExactDirections() {
        // Minecraft yaw: 0 looks south (+z), -90 east (+x), 90 west (-x), 180 north (-z).
        // The attack pushes along (-sin(yaw), cos(yaw)).
        assertDirection(0.0F, 0.0F, 1.0F);
        assertDirection(-90.0F, 1.0F, 0.0F);
        assertDirection(90.0F, -1.0F, 0.0F);
        assertDirection(180.0F, 0.0F, -1.0F);
    }

    @Test
    void squareRootIsRoundedToFloat() {
        assertEquals((float) Math.sqrt(2.0D), McMath.sqrt(2.0D));
        assertEquals(2.5F, McMath.sqrt(6.25D));
    }

    private static void assertDirection(float yaw, float expectedX, float expectedZ) {
        float radians = McMath.yawToRadians(yaw);
        assertEquals(expectedX, -McMath.sin(radians), 1.0E-6F, "x for yaw " + yaw);
        assertEquals(expectedZ, McMath.cos(radians), 1.0E-6F, "z for yaw " + yaw);
    }
}
