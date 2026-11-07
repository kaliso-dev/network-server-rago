package gg.rago.combat.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KnockbackProfileTest {

    @Test
    void vanillaProfileHoldsTheServerConstants() {
        KnockbackProfile vanilla = KnockbackProfile.vanilla();

        assertEquals("vanilla", vanilla.name());
        assertEquals(2.0D, vanilla.friction());
        assertEquals(0.4000000059604645D, vanilla.horizontal());
        assertEquals(0.4000000059604645D, vanilla.vertical());
        assertEquals(0.4000000059604645D, vanilla.verticalLimit());
        assertEquals(0.5F, vanilla.extraHorizontal());
        assertEquals(0.1D, vanilla.extraVertical());
    }

    @Test
    void readsAProfileFromConfigurationValues() {
        Map<String, Object> values = vanillaValues();

        KnockbackProfile profile = KnockbackProfile.fromMap("copy", values);

        assertEquals("copy", profile.name());
        assertTrue(profile.sameValuesAs(KnockbackProfile.vanilla()));
        assertFalse(profile.equals(KnockbackProfile.vanilla()));
    }

    @Test
    void acceptsIntegersFromYaml() {
        Map<String, Object> values = vanillaValues();
        values.put(KnockbackProfile.KEY_FRICTION, 2);

        assertEquals(2.0D, KnockbackProfile.fromMap("ints", values).friction());
    }

    @Test
    void refusesUnknownKeysSoThatTyposAreNotSilent() {
        Map<String, Object> values = vanillaValues();
        values.put("horizonal", 0.5D);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> KnockbackProfile.fromMap("typo", values));
        assertTrue(error.getMessage().contains("horizonal"), error.getMessage());
    }

    @Test
    void refusesMissingKeysAndNonNumbers() {
        Map<String, Object> missing = vanillaValues();
        missing.remove(KnockbackProfile.KEY_VERTICAL_LIMIT);
        Map<String, Object> text = vanillaValues();
        text.put(KnockbackProfile.KEY_VERTICAL, "0.4");

        assertTrue(assertThrows(IllegalArgumentException.class, () -> KnockbackProfile.fromMap("a", missing))
                .getMessage().contains("vertical-limit"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> KnockbackProfile.fromMap("b", text))
                .getMessage().contains("vertical"));
    }

    @Test
    void refusesValuesOutsideWhatAPacketCanCarry() {
        assertThrows(IllegalArgumentException.class, () -> KnockbackProfile.of("big", 2.0D, 4.0D, 0.4D, 0.4D, 0.5D, 0.1D));
        assertThrows(IllegalArgumentException.class, () -> KnockbackProfile.of("nan", 2.0D, Double.NaN, 0.4D, 0.4D, 0.5D, 0.1D));
        assertThrows(IllegalArgumentException.class, () -> KnockbackProfile.of("zero", 0.0D, 0.4D, 0.4D, 0.4D, 0.5D, 0.1D));
    }

    @Test
    void refusesInvalidNames() {
        assertThrows(IllegalArgumentException.class, () -> KnockbackProfile.of("Hypixel", 2.0D, 0.4D, 0.4D, 0.4D, 0.5D, 0.1D));
        assertThrows(IllegalArgumentException.class, () -> KnockbackProfile.of("", 2.0D, 0.4D, 0.4D, 0.4D, 0.5D, 0.1D));
        assertThrows(IllegalArgumentException.class, () -> KnockbackProfile.of("a b", 2.0D, 0.4D, 0.4D, 0.4D, 0.5D, 0.1D));
        assertEquals("test-1_b", KnockbackProfile.of("test-1_b", 2.0D, 0.4D, 0.4D, 0.4D, 0.5D, 0.1D).name());
    }

    @Test
    void extraHorizontalIsStoredAsFloatLikeTheServerUsesIt() {
        KnockbackProfile profile = KnockbackProfile.of("f", 2.0D, 0.4D, 0.4D, 0.4D, 0.425D, 0.1D);

        assertEquals(0.425F, profile.extraHorizontal());
    }

    private static Map<String, Object> vanillaValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(KnockbackProfile.KEY_FRICTION, 2.0D);
        values.put(KnockbackProfile.KEY_HORIZONTAL, 0.4000000059604645D);
        values.put(KnockbackProfile.KEY_VERTICAL, 0.4000000059604645D);
        values.put(KnockbackProfile.KEY_VERTICAL_LIMIT, 0.4000000059604645D);
        values.put(KnockbackProfile.KEY_EXTRA_HORIZONTAL, 0.5D);
        values.put(KnockbackProfile.KEY_EXTRA_VERTICAL, 0.1D);
        return new HashMap<>(values);
    }
}
