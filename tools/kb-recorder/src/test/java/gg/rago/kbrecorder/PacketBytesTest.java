package gg.rago.kbrecorder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.rago.kbrecorder.PacketBytes.EntityIdFormat;
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

    private static byte[] bytes(int... values) {
        byte[] data = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            data[i] = (byte) values[i];
        }
        return data;
    }
}
