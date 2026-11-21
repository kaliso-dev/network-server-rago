package gg.rago.kbrecorder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.kbrecorder.PacketBytes.EntityIdFormat;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class PacketBytesTest {

    @Test
    void readsVarIntEntityIds() {
        assertEquals(0, PacketBytes.entityId(bytes(0x00), EntityIdFormat.VARINT));
        assertEquals(127, PacketBytes.entityId(bytes(0x7F, 0x55), EntityIdFormat.VARINT));
        assertEquals(300, PacketBytes.entityId(bytes(0xAC, 0x02), EntityIdFormat.VARINT));
        assertEquals(2_097_151, PacketBytes.entityId(bytes(0xFF, 0xFF, 0x7F), EntityIdFormat.VARINT));
        assertEquals(-1, PacketBytes.entityId(bytes(0xFF, 0xFF, 0xFF, 0xFF, 0x0F), EntityIdFormat.VARINT));
    }

    @Test
    void readsIntEntityIds() {
        assertEquals(258, PacketBytes.entityId(bytes(0x00, 0x00, 0x01, 0x02, 0x02), EntityIdFormat.INT));
        assertEquals(-2, PacketBytes.entityId(bytes(0xFF, 0xFF, 0xFF, 0xFE), EntityIdFormat.INT));
    }

    @Test
    void truncatedOrMalformedDataHasNoEntity() {
        assertEquals(PacketBytes.NO_ENTITY, PacketBytes.entityId(bytes(), EntityIdFormat.VARINT));
        assertEquals(PacketBytes.NO_ENTITY, PacketBytes.entityId(bytes(0x80), EntityIdFormat.VARINT));
        assertEquals(PacketBytes.NO_ENTITY, PacketBytes.entityId(bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x01), EntityIdFormat.VARINT));
        assertEquals(PacketBytes.NO_ENTITY, PacketBytes.entityId(bytes(0x01, 0x02, 0x03), EntityIdFormat.INT));
        assertEquals(PacketBytes.NO_ENTITY, PacketBytes.entityId(bytes(0x01), EntityIdFormat.NONE));
    }

    @Test
    void readsTheObjectTypeOfASpawnedObject() {
        assertEquals(63, PacketBytes.objectType(bytes(0xAC, 0x02, 63, 0, 0, 0, 1)));
        assertEquals(50, PacketBytes.objectType(bytes(0x05, 50)));
        assertEquals(200, PacketBytes.objectType(bytes(0x05, 200)));
        assertEquals(-1, PacketBytes.objectType(bytes(0x05)));
        assertEquals(-1, PacketBytes.objectType(bytes(0x85)));
        assertTrue(PacketBytes.isExplosive(50));
        assertTrue(PacketBytes.isExplosive(63));
        assertTrue(PacketBytes.isExplosive(64));
        assertFalse(PacketBytes.isExplosive(60));
        assertFalse(PacketBytes.isExplosive(-1));
        assertEquals(300, PacketBytes.entityId(bytes(0xAC, 0x02, 63), EntityIdFormat.SPAWN_OBJECT));
    }

    @Test
    void readsTheEntitiesOfADestroyEntitiesPacket() {
        assertArrayEquals(new int[] {42, 300}, PacketBytes.entityIds(bytes(2, 42, 0xAC, 0x02)));
        assertArrayEquals(new int[0], PacketBytes.entityIds(bytes(0)));
        assertNull(PacketBytes.entityIds(bytes(2, 42)));
        assertNull(PacketBytes.entityIds(bytes()));
        assertNull(PacketBytes.entityIds(bytes(0xFF, 0xFF, 0xFF, 0xFF, 0x0F)), "a negative count");
    }

    @Test
    void readsTheCentreAndStrengthOfAnExplosion() {
        ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 12);
        buffer.putFloat(20.5f).putFloat(4.25f).putFloat(-3.5f).putFloat(4.0f).putInt(0)
                .putFloat(0f).putFloat(0f).putFloat(0f);
        assertArrayEquals(new float[] {20.5f, 4.25f, -3.5f, 4.0f}, PacketBytes.explosion(buffer.array()));
        assertNull(PacketBytes.explosion(new byte[15]));
    }

    private static byte[] bytes(int... values) {
        byte[] data = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            data[i] = (byte) values[i];
        }
        return data;
    }
}
