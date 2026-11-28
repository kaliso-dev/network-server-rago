package gg.rago.kbrecorder;

/**
 * Reads the few fields of a serialized packet that decide whether it is kept: entity ids, the type
 * of a spawned object, the centre of an explosion. The rest is decoded by the analysis tools.
 */
final class PacketBytes {

    /** How a packet names its entities, as in protocol 47 (Minecraft 1.8). */
    enum EntityIdFormat {
        /** The packet has no entity id, or it is always recorded. */
        NONE,
        /** A VarInt entity id. */
        VARINT,
        /** A 4-byte big-endian entity id (entity status). */
        INT,
        /** Spawn object: a VarInt entity id, then the object type byte. */
        SPAWN_OBJECT,
        /** Destroy entities: a VarInt count, then as many VarInt entity ids. */
        ENTITY_LIST,
        /** Join game and respawn: no entity id, but the entities the client knew are gone. */
        NEW_WORLD
    }

    /** Returned when the packet is too short or malformed. */
    static final int NO_ENTITY = Integer.MIN_VALUE;

    /** Object types of the spawn object packet whose explosions are measured. */
    static final int OBJECT_PRIMED_TNT = 50;
    static final int OBJECT_FIREBALL = 63;
    static final int OBJECT_SMALL_FIREBALL = 64;

    /** A Destroy entities packet never names more entities than this in practice. */
    private static final int MAX_LISTED_ENTITIES = 4096;

    private PacketBytes() {
    }

    static int entityId(byte[] data, EntityIdFormat format) {
        switch (format) {
            case VARINT:
            case SPAWN_OBJECT:
                return readVarInt(data);
            case INT:
                if (data.length < 4) {
                    return NO_ENTITY;
                }
                return readInt(data, 0);
            default:
                return NO_ENTITY;
        }
    }

    /** True for the objects whose explosions are measured: primed TNT and fireballs. */
    static boolean isExplosive(int objectType) {
        return objectType == OBJECT_PRIMED_TNT || objectType == OBJECT_FIREBALL || objectType == OBJECT_SMALL_FIREBALL;
    }

    /** The object type of a spawn object packet (the byte after the entity id), or -1. */
    static int objectType(byte[] data) {
        int[] offset = {0};
        if (readVarInt(data, offset) == NO_ENTITY || offset[0] >= data.length) {
            return -1;
        }
        return data[offset[0]] & 0xFF;
    }

    /** The entity ids of a destroy entities packet, or null when the packet is malformed. */
    static int[] entityIds(byte[] data) {
        int[] offset = {0};
        int count = readVarInt(data, offset);
        if (count < 0 || count > MAX_LISTED_ENTITIES) {
            return null;
        }
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            ids[i] = readVarInt(data, offset);
            if (ids[i] == NO_ENTITY) {
                return null;
            }
        }
        return ids;
    }

    /**
     * The centre and the strength of an explosion packet: three floats, then the strength (the
     * radius) as a float. Null when the packet is shorter than that.
     */
    static float[] explosion(byte[] data) {
        if (data.length < 16) {
            return null;
        }
        float[] values = new float[4];
        for (int i = 0; i < 4; i++) {
            values[i] = Float.intBitsToFloat(readInt(data, 4 * i));
        }
        return values;
    }

    /** Reads a VarInt at the start of the data, at most 5 bytes as in Minecraft. */
    static int readVarInt(byte[] data) {
        return readVarInt(data, new int[] {0});
    }

    /** Reads a VarInt at the offset and moves the offset past it; NO_ENTITY if it does not fit. */
    private static int readVarInt(byte[] data, int[] offset) {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            if (offset[0] >= data.length) {
                return NO_ENTITY;
            }
            int b = data[offset[0]++];
            value |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        return NO_ENTITY;
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16 | (data[offset + 2] & 0xFF) << 8
                | data[offset + 3] & 0xFF;
    }
}
