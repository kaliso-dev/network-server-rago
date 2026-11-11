package gg.rago.combat.model;

/**
 * The trigonometry and square root of Minecraft 1.8, reproduced bit for bit.
 *
 * <p>Minecraft 1.8 does not use {@link Math#sin(double)} in combat: it reads a table of 65,536
 * precomputed {@code float} values, and it rounds square roots to {@code float}. The difference
 * with exact maths is tiny, but a velocity packet is rounded to 1/8000 of a block per tick, so
 * an exact model has to use the same arithmetic as the server to produce the same packets.
 *
 * <p>Reference: {@code MathHelper.sin}, {@code MathHelper.cos} and {@code MathHelper.sqrt} in the
 * 1.8.8 server, and the yaw conversion in {@code EntityHuman.attack} (see
 * docs/combat/knockback-1.8.8.md).
 */
public final class McMath {

    private static final int TABLE_SIZE = 65536;
    private static final float[] SIN_TABLE = new float[TABLE_SIZE];

    static {
        for (int i = 0; i < TABLE_SIZE; i++) {
            SIN_TABLE[i] = (float) Math.sin((double) i * Math.PI * 2.0D / 65536.0D);
        }
    }

    private McMath() {
    }

    /** Sine of an angle in radians, read from the 1.8 lookup table. */
    public static float sin(float radians) {
        return SIN_TABLE[(int) (radians * 10430.378F) & 0xFFFF];
    }

    /** Cosine of an angle in radians, read from the 1.8 lookup table. */
    public static float cos(float radians) {
        return SIN_TABLE[(int) (radians * 10430.378F + 16384.0F) & 0xFFFF];
    }

    /** Square root rounded to {@code float}, as {@code MathHelper.sqrt(double)} returns it. */
    public static float sqrt(double value) {
        return (float) Math.sqrt(value);
    }

    /**
     * Converts a yaw in degrees to radians with the same {@code float} arithmetic as
     * {@code EntityHuman.attack}: {@code yaw * 3.1415927F / 180.0F}.
     */
    public static float yawToRadians(float yawDegrees) {
        return yawDegrees * 3.1415927F / 180.0F;
    }
}
