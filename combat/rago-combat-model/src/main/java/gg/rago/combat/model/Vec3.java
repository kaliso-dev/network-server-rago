package gg.rago.combat.model;

/** An immutable vector, in blocks or blocks per tick. */
public final class Vec3 {

    public static final Vec3 ZERO = new Vec3(0.0D, 0.0D, 0.0D);

    private final double x;
    private final double y;
    private final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    /** Length of the horizontal part (x and z). */
    public double horizontalLength() {
        return Math.sqrt(x * x + z * z);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Vec3)) {
            return false;
        }
        Vec3 vec = (Vec3) other;
        return Double.compare(x, vec.x) == 0
                && Double.compare(y, vec.y) == 0
                && Double.compare(z, vec.z) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(x);
        result = 31 * result + Double.hashCode(y);
        return 31 * result + Double.hashCode(z);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
