package gg.rago.combat.engine;

import gg.rago.combat.model.MeleeHit;
import gg.rago.combat.model.Vec3;
import java.util.Locale;

/**
 * The hits of {@code /kb sparring}: an attacker nobody sees, standing in front of the player and
 * facing them, hits them at a fixed interval. Each hit is computed exactly like a real melee hit
 * (same {@link MeleeHit} inputs, same profile), so the player feels the profile without needing a
 * second player. Pure logic; {@link SparringSessions} runs it on the server.
 */
final class Sparring {

    /** Distance between the player and the attacker, a usual melee distance. */
    static final double ATTACKER_DISTANCE = 3.0;

    /**
     * A fresh full hit comes at most every 9 ticks on Hypixel (the engine's hit-delay-ticks; 10 in
     * vanilla, half of the 20-tick hurt cooldown): no real fight hits faster.
     */
    static final int MIN_INTERVAL_TICKS = 9;
    static final int MAX_INTERVAL_TICKS = 200;
    static final int DEFAULT_INTERVAL_TICKS = 20;

    /** Which hits are sprint hits. */
    enum Pattern {
        SPRINT,
        NORMAL,
        /** Sprint, normal, sprint, normal... to feel the difference. */
        ALTERNE;

        String configName() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Pattern parse(String value) {
            for (Pattern pattern : values()) {
                if (pattern.configName().equals(value.toLowerCase(Locale.ROOT))) {
                    return pattern;
                }
            }
            return null;
        }

        /** Whether hit number {@code index} (from 0) is a sprint hit. */
        boolean sprints(int index) {
            switch (this) {
                case SPRINT:
                    return true;
                case NORMAL:
                    return false;
                default:
                    return index % 2 == 0;
            }
        }
    }

    private Sparring() {
    }

    /** The interval typed by the player, or null when it is not a whole number in the allowed range. */
    static Integer parseInterval(String text) {
        try {
            int ticks = Integer.parseInt(text.trim());
            return ticks >= MIN_INTERVAL_TICKS && ticks <= MAX_INTERVAL_TICKS ? ticks : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The hit of an attacker standing {@link #ATTACKER_DISTANCE} blocks in front of the victim, in
     * the horizontal direction the victim looks at, and facing the victim: the victim is pushed
     * straight back, as in a fight face to face.
     *
     * @param knockbackLevel the attack's knockback level: 1 for a sprint hit, 0 otherwise (the
     *     invisible attacker has no Knockback enchantment)
     */
    static MeleeHit hit(double victimX, double victimZ, float victimYaw, Vec3 victimMotion, int knockbackLevel) {
        double yaw = Math.toRadians(victimYaw);
        // Minecraft's look direction for a yaw: x = -sin(yaw), z = cos(yaw).
        double attackerX = victimX - Math.sin(yaw) * ATTACKER_DISTANCE;
        double attackerZ = victimZ + Math.cos(yaw) * ATTACKER_DISTANCE;
        float attackerYaw = wrapDegrees(victimYaw + 180f);
        return new MeleeHit(attackerX, attackerZ, attackerYaw, knockbackLevel, victimX, victimZ, victimMotion);
    }

    /** An angle in [-180, 180), as Minecraft keeps yaws. */
    static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360f;
        if (wrapped >= 180f) {
            wrapped -= 360f;
        }
        if (wrapped < -180f) {
            wrapped += 360f;
        }
        return wrapped;
    }
}
