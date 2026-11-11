package gg.rago.combat.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HitRegistrationTest {

    @Test
    void hitsCountFullyOnlyInTheSecondHalfOfTheCooldown() {
        assertTrue(HitRegistration.isFullHit(0, 20));
        assertTrue(HitRegistration.isFullHit(10, 20));
        assertFalse(HitRegistration.isFullHit(11, 20));
        assertFalse(HitRegistration.isFullHit(20, 20));
    }

    @Test
    void oddCooldownLengthsUseTheFloatHalf() {
        assertTrue(HitRegistration.isFullHit(7, 15));
        assertFalse(HitRegistration.isFullHit(8, 15));
    }
}
