package gg.rago.combat.model;

/**
 * Encoding of the 1.8 entity velocity packet ({@code PacketPlayOutEntityVelocity}, known as
 * {@code S12PacketEntityVelocity} on the client).
 *
 * <p>The server clamps each component to ±3.9 blocks per tick, multiplies it by 8000 and truncates
 * it to an integer, which travels as a short. The client divides it by 8000 again. Two velocities
 * that differ by less than 1/8000 can therefore reach the client as the same packet, which is the
 * resolution every comparison in the knockback tooling works at.
 */
public final class VelocityPacket {

    /** Largest velocity component the packet can carry, in blocks per tick. */
    public static final double MAX_COMPONENT = 3.9D;

    /** Packet units per block per tick. */
    public static final double UNITS_PER_BLOCK = 8000.0D;

    private final int x;
    private final int y;
    private final int z;

    public VelocityPacket(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** The packet the server sends for this velocity. */
    public static VelocityPacket encode(Vec3 velocity) {
        return new VelocityPacket(encode(velocity.x()), encode(velocity.y()), encode(velocity.z()));
    }

    /** One component as the server encodes it: clamped, scaled, truncated toward zero. */
    public static int encode(double component) {
        double clamped = component;
        if (clamped < -MAX_COMPONENT) {
            clamped = -MAX_COMPONENT;
        }
        if (clamped > MAX_COMPONENT) {
            clamped = MAX_COMPONENT;
        }
        return (int) (clamped * UNITS_PER_BLOCK);
    }

    /** The velocity the client sets when it receives this packet, in blocks per tick. */
    public Vec3 decode() {
        return new Vec3(x / UNITS_PER_BLOCK, y / UNITS_PER_BLOCK, z / UNITS_PER_BLOCK);
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    /** Largest difference between the components of two packets, in packet units. */
    public int maxDifference(VelocityPacket other) {
        return Math.max(Math.abs(x - other.x), Math.max(Math.abs(y - other.y), Math.abs(z - other.z)));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VelocityPacket)) {
            return false;
        }
        VelocityPacket packet = (VelocityPacket) other;
        return x == packet.x && y == packet.y && z == packet.z;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * x + y) + z;
    }

    @Override
    public String toString() {
        return "[" + x + ", " + y + ", " + z + "]";
    }
}
