package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpawnProtectionTest {

    @Test
    void aRespawnIsProtectedForOneSecondAndAHalf() {
        assertEquals(1_500L, BedwarsGame.SPAWN_PROTECTION_MILLIS, "Minemen's, as the owner measured it");
        SpawnProtection protection = new SpawnProtection();
        assertFalse(protection.on(0L), "nobody is protected before a respawn");
        protection.start(10_000L, BedwarsGame.SPAWN_PROTECTION_MILLIS);
        assertTrue(protection.on(10_000L));
        assertTrue(protection.on(11_499L));
        assertFalse(protection.on(11_500L), "over after 1.5 s");
    }

    @Test
    void hittingAnEnemyEndsItAtOnce() {
        SpawnProtection protection = new SpawnProtection();
        protection.start(10_000L, BedwarsGame.SPAWN_PROTECTION_MILLIS);
        protection.engaged();
        assertFalse(protection.on(10_001L));
        protection.start(20_000L, BedwarsGame.SPAWN_PROTECTION_MILLIS);
        assertTrue(protection.on(20_001L), "the next life is protected again");
    }

    @Test
    void theOtherModesHaveNone() {
        SpawnProtection protection = new SpawnProtection();
        protection.start(10_000L, 0L);
        assertFalse(protection.on(10_000L));
    }
}
