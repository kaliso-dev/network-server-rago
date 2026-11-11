package gg.rago.buildtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class VoidGeneratorTest {

    private final VoidGenerator generator = new VoidGenerator();

    @Test
    void spawnsAboveThePlatformBlock() {
        Location spawn = generator.getFixedSpawnLocation(null, new Random(1));
        assertEquals(0.5, spawn.getX());
        assertEquals(VoidGenerator.PLATFORM_Y + 1, spawn.getY());
        assertEquals(0.5, spawn.getZ());
    }

    @Test
    void addsNothingAfterGeneration() {
        assertTrue(generator.getDefaultPopulators(null).isEmpty());
        assertTrue(generator.canSpawn(null, 123, -456));
    }
}
