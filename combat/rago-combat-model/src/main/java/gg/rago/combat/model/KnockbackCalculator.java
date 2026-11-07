package gg.rago.combat.model;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Computes the velocity a player receives from a melee hit.
 *
 * <p>The computation follows the 1.8.8 server step by step, with the profile's values in place of
 * the vanilla constants:
 * <ol>
 *   <li>{@code EntityLiving.damageEntity}: the direction is the attacker's position minus the
 *       victim's. When the two are closer than 0.01 block horizontally, the server draws a random
 *       direction instead.</li>
 *   <li>{@code EntityLiving} knockback: the victim's motion is divided by {@code friction}, the
 *       horizontal push is added away from the attacker, the vertical push is added and the result
 *       is capped at {@code verticalLimit}.</li>
 *   <li>{@code EntityHuman.attack}: if the knockback level is above zero, an extra push is added
 *       in the direction the attacker looks, after the cap.</li>
 * </ol>
 *
 * <p>With {@link KnockbackProfile#vanilla()} the result is the vanilla velocity bit for bit, which
 * the tests in this module check. This class has no state besides the random source and is safe
 * to share between threads if the random source is.
 */
public final class KnockbackCalculator {

    private final DoubleSupplier random;

    /** A calculator that draws random directions like the server does, with {@link Math#random()}. */
    public KnockbackCalculator() {
        this(Math::random);
    }

    /**
     * @param random source of numbers in [0, 1), used only when attacker and victim stand at the
     *               same horizontal position
     */
    public KnockbackCalculator(DoubleSupplier random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /** The victim's velocity after the hit, before it is encoded in a packet. */
    public Vec3 compute(KnockbackProfile profile, MeleeHit hit) {
        double dx = hit.attackerX() - hit.victimX();
        double dz;
        for (dz = hit.attackerZ() - hit.victimZ(); dx * dx + dz * dz < 1.0E-4D;
                dz = (random.getAsDouble() - random.getAsDouble()) * 0.01D) {
            dx = (random.getAsDouble() - random.getAsDouble()) * 0.01D;
        }

        Vec3 motion = hit.victimMotion();
        double motionX = motion.x();
        double motionY = motion.y();
        double motionZ = motion.z();

        float distance = McMath.sqrt(dx * dx + dz * dz);
        motionX /= profile.friction();
        motionY /= profile.friction();
        motionZ /= profile.friction();
        motionX -= dx / (double) distance * profile.horizontal();
        motionY += profile.vertical();
        motionZ -= dz / (double) distance * profile.horizontal();
        if (motionY > profile.verticalLimit()) {
            motionY = profile.verticalLimit();
        }

        int level = hit.knockbackLevel();
        if (level > 0) {
            float yaw = McMath.yawToRadians(hit.attackerYaw());
            motionX += (double) (-McMath.sin(yaw) * (float) level * profile.extraHorizontal());
            motionY += profile.extraVertical();
            motionZ += (double) (McMath.cos(yaw) * (float) level * profile.extraHorizontal());
        }
        return new Vec3(motionX, motionY, motionZ);
    }
}
