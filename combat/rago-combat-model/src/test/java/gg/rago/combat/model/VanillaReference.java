package gg.rago.combat.model;

import java.util.function.DoubleSupplier;

/**
 * A literal transcription of the 1.8.8 server's melee knockback, with the vanilla constants
 * written in place, kept independent of the production model (its own sine table, no profile).
 *
 * <p>Sources: {@code EntityLiving.damageEntity} and the knockback method of {@code EntityLiving},
 * {@code EntityHuman.attack} and {@code MathHelper}, as shown by the lines PandaSpigot's
 * "Configurable knockback" patch replaces (docs/combat/knockback-1.8.8.md). The production
 * {@link KnockbackCalculator} must give exactly the same doubles with the vanilla profile.
 */
final class VanillaReference {

    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < 65536; ++i) {
            SIN[i] = (float) Math.sin((double) i * 3.141592653589793D * 2.0D / 65536.0D);
        }
    }

    private VanillaReference() {
    }

    static float sin(float f) {
        return SIN[(int) (f * 10430.378F) & '￿'];
    }

    static float cos(float f) {
        return SIN[(int) (f * 10430.378F + 16384.0F) & '￿'];
    }

    static double[] knockback(double attackerX, double attackerZ, float attackerYaw, int level,
            double victimX, double victimZ, double motX, double motY, double motZ, DoubleSupplier random) {
        // EntityLiving.damageEntity
        double d0 = attackerX - victimX;
        double d1;
        for (d1 = attackerZ - victimZ; d0 * d0 + d1 * d1 < 1.0E-4D;
                d1 = (random.getAsDouble() - random.getAsDouble()) * 0.01D) {
            d0 = (random.getAsDouble() - random.getAsDouble()) * 0.01D;
        }

        // EntityLiving knockback
        float f1 = (float) Math.sqrt(d0 * d0 + d1 * d1);
        float f2 = 0.4F;
        motX /= 2.0D;
        motY /= 2.0D;
        motZ /= 2.0D;
        motX -= d0 / (double) f1 * (double) f2;
        motY += (double) f2;
        motZ -= d1 / (double) f1 * (double) f2;
        if (motY > 0.4000000059604645D) {
            motY = 0.4000000059604645D;
        }

        // EntityHuman.attack
        if (level > 0) {
            motX += (double) (-sin(attackerYaw * 3.1415927F / 180.0F) * (float) level * 0.5F);
            motY += 0.1D;
            motZ += (double) (cos(attackerYaw * 3.1415927F / 180.0F) * (float) level * 0.5F);
        }
        return new double[] {motX, motY, motZ};
    }
}
