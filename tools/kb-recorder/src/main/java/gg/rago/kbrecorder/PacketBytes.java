package gg.rago.kbrecorder;

/** Reads the entity id at the start of a serialized packet, to keep only the entities that matter. */
final class PacketBytes {

    /** How a packet starts with its entity id, as in protocol 47 (Minecraft 1.8). */
    enum EntityIdFormat {
        /** The packet has no entity id, or it is always recorded. */
        NONE,
        /** A VarInt entity id. */
        VARINT,
        /** A 4-byte big-endian entity id (entity status). */
        INT
    }

    /** Returned when the packet is too short or malformed. */
    static final int NO_ENTITY = Integer.MIN_VALUE;

    private PacketBytes() {
    }

    static int entityId(byte[] data, EntityIdFormat format) {
        switch (format) {
            case VARINT:
                return readVarInt(data);
            case INT:
                if (data.length < 4) {
                    return NO_ENTITY;
                }
                return (data[0] & 0xFF) << 24 | (data[1] & 0xFF) << 16 | (data[2] & 0xFF) << 8 | data[3] & 0xFF;
            default:
                return NO_ENTITY;
        }
    }

    /** Reads a VarInt at the start of the data, at most 5 bytes as in Minecraft. */
    static int readVarInt(byte[] data) {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            if (i >= data.length) {
                return NO_ENTITY;
            }
            int b = data[i];
            value |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        return NO_ENTITY;
    }
}
