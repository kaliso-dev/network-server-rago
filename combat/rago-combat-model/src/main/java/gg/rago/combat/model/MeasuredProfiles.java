package gg.rago.combat.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Knockback profiles built from recordings of another server, never from a guess. Each profile
 * names the report that holds its measurements, and a new measurement gives a new version
 * instead of changing an existing one.
 */
public final class MeasuredProfiles {

    /**
     * Hypixel as measured on 7 November 2026 (docs/combat/mesures-hypixel.md): 1v1 in Housing,
     * kb-recorder on both players, 544 velocity packets. The packets match vanilla 1.8.8 except
     * for the vertical extra of a sprint hit, 0.07 instead of 0.1. The cooldown between two hits
     * (9 ticks there, 10 in vanilla) is not part of a knockback profile: it is the engine's
     * {@code hit-delay-ticks} setting. Hits with the Knockback enchantment were not measured.
     */
    public static final String HYPIXEL_APPROX_V1_NAME = "hypixel-approx-v1";

    private static final KnockbackProfile HYPIXEL_APPROX_V1 = KnockbackProfile.of(
            HYPIXEL_APPROX_V1_NAME,
            KnockbackProfile.vanilla().friction(),
            KnockbackProfile.vanilla().horizontal(),
            KnockbackProfile.vanilla().vertical(),
            KnockbackProfile.vanilla().verticalLimit(),
            KnockbackProfile.vanilla().extraHorizontal(),
            0.07D);

    private static final List<KnockbackProfile> ALL = Collections.unmodifiableList(Arrays.asList(HYPIXEL_APPROX_V1));

    private MeasuredProfiles() {
    }

    public static KnockbackProfile hypixelApproxV1() {
        return HYPIXEL_APPROX_V1;
    }

    /** Every measured profile, in the order they were added. */
    public static List<KnockbackProfile> all() {
        return ALL;
    }
}
