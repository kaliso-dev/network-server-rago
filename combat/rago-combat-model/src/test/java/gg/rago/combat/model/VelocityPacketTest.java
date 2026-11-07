package gg.rago.combat.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VelocityPacketTest {

    @Test
    void componentsAreScaledBy8000AndTruncatedTowardZero() {
        assertEquals(2886, VelocityPacket.encode(0.36080000519752503D));
        assertEquals(3200, VelocityPacket.encode(0.4000000059604645D));
        assertEquals(-3200, VelocityPacket.encode(-0.4000000059604645D));
        assertEquals(0, VelocityPacket.encode(0.0001249D));
        assertEquals(0, VelocityPacket.encode(-0.0001249D));
        assertEquals(1, VelocityPacket.encode(0.000125D));
    }

    @Test
    void componentsAreClampedTo3Point9() {
        assertEquals(31200, VelocityPacket.encode(3.95D));
        assertEquals(-31200, VelocityPacket.encode(-10.0D));
        assertEquals(31200, VelocityPacket.encode(Double.POSITIVE_INFINITY));
    }

    @Test
    void decodeDividesBy8000LikeTheClient() {
        Vec3 decoded = new VelocityPacket(0, 2886, -3200).decode();

        assertEquals(0.0D, decoded.x());
        assertEquals(2886 / 8000.0D, decoded.y());
        assertEquals(-0.4D, decoded.z());
    }

    @Test
    void maxDifferenceIsCountedInPacketUnits() {
        VelocityPacket a = new VelocityPacket(10, 2886, 3200);
        VelocityPacket b = new VelocityPacket(9, 2888, 3200);

        assertEquals(2, a.maxDifference(b));
        assertEquals(0, a.maxDifference(a));
    }
}
