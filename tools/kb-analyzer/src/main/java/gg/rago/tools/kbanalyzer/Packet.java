package gg.rago.tools.kbanalyzer;

import com.google.gson.JsonObject;
import gg.rago.combat.model.VelocityPacket;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * A packet line: the bytes of one packet as on the wire after decompression, without the packet
 * id. Decoding methods throw {@link IllegalArgumentException} when the bytes do not fit.
 */
public final class Packet {

    /** Protocol 47 ids of received packets. */
    public static final int IN_JOIN_GAME = 0x01;
    public static final int IN_RESPAWN = 0x07;
    public static final int IN_ANIMATION = 0x0B;
    public static final int IN_SPAWN_OBJECT = 0x0E;
    public static final int IN_ENTITY_VELOCITY = 0x12;
    public static final int IN_DESTROY_ENTITIES = 0x13;
    public static final int IN_ENTITY_RELATIVE_MOVE = 0x15;
    public static final int IN_ENTITY_LOOK_RELATIVE_MOVE = 0x17;
    public static final int IN_ENTITY_TELEPORT = 0x18;
    public static final int IN_ENTITY_STATUS = 0x1A;
    public static final int IN_MULTI_BLOCK_CHANGE = 0x22;
    public static final int IN_BLOCK_CHANGE = 0x23;
    public static final int IN_EXPLOSION = 0x27;
    /** Protocol 47 ids of sent packets. */
    public static final int OUT_USE_ENTITY = 0x02;
    public static final int OUT_PLAYER_POSITION = 0x04;
    public static final int OUT_PLAYER_LOOK = 0x05;
    public static final int OUT_PLAYER_POSITION_LOOK = 0x06;
    public static final int OUT_BLOCK_PLACEMENT = 0x08;
    public static final int OUT_ANIMATION = 0x0A;
    public static final int OUT_ENTITY_ACTION = 0x0B;

    /** Object types of the spawn object packet (recorded from kb-recorder 0.2). */
    public static final int OBJECT_PRIMED_TNT = 50;
    public static final int OBJECT_FIREBALL = 63;
    public static final int OBJECT_SMALL_FIREBALL = 64;
    /** Positions of spawned objects and entity moves travel in 1/32 block. */
    public static final double POSITION_UNITS_PER_BLOCK = 32.0;

    /** Entity status of a hurt living entity (the red flash and the hurt sound). */
    public static final int STATUS_HURT = 2;
    /** Animation of a swinging arm. */
    public static final int ANIMATION_SWING = 0;
    /** Use entity action of an attack. */
    public static final int USE_ATTACK = 1;
    /** Entity actions that start and stop sprinting. */
    public static final int ACTION_START_SPRINTING = 3;
    public static final int ACTION_STOP_SPRINTING = 4;

    public final long ms;
    public final boolean inbound;
    public final int id;
    public final byte[] data;

    public Packet(long ms, boolean inbound, int id, byte[] data) {
        this.ms = ms;
        this.inbound = inbound;
        this.id = id;
        this.data = data;
    }

    static Packet fromJson(JsonObject json) {
        String dir = json.get("dir").getAsString();
        if (!"in".equals(dir) && !"out".equals(dir)) {
            throw new IllegalArgumentException("packet direction " + dir);
        }
        return new Packet(json.get("ms").getAsLong(), "in".equals(dir), json.get("id").getAsInt(),
                Base64.getDecoder().decode(json.get("data").getAsString()));
    }

    public boolean is(boolean inbound, int id) {
        return this.inbound == inbound && this.id == id;
    }

    /** Entity velocity: VarInt entity id, then three big-endian shorts. */
    public int velocityEntity() {
        return readVarInt(new int[] {0});
    }

    public VelocityPacket velocity() {
        int[] offset = {0};
        readVarInt(offset);
        if (data.length != offset[0] + 6) {
            throw new IllegalArgumentException("entity velocity packet of " + data.length + " bytes");
        }
        return new VelocityPacket(readShort(offset[0]), readShort(offset[0] + 2), readShort(offset[0] + 4));
    }

    /** Entity status: 4-byte big-endian entity id, then the status byte. */
    public int statusEntity() {
        if (data.length != 5) {
            throw new IllegalArgumentException("entity status packet of " + data.length + " bytes");
        }
        return readInt(0);
    }

    public int status() {
        statusEntity();
        return data[4];
    }

    /** Animation: VarInt entity id, then an unsigned byte. */
    public int animationEntity() {
        return readVarInt(new int[] {0});
    }

    public int animation() {
        int[] offset = {0};
        readVarInt(offset);
        if (data.length != offset[0] + 1) {
            throw new IllegalArgumentException("animation packet of " + data.length + " bytes");
        }
        return data[offset[0]] & 0xFF;
    }

    /** Join game: the player's new entity id, as a 4-byte big-endian int. */
    public int joinGameEntity() {
        if (data.length < 4) {
            throw new IllegalArgumentException("join game packet of " + data.length + " bytes");
        }
        return readInt(0);
    }

    /** Use entity (sent): VarInt target entity id, then a VarInt action. */
    public int useEntityTarget() {
        return readVarInt(new int[] {0});
    }

    public int useEntityAction() {
        int[] offset = {0};
        readVarInt(offset);
        return readVarInt(offset);
    }

    /** Entity action (sent): VarInt entity id, VarInt action, VarInt jump boost. */
    public int entityAction() {
        int[] offset = {0};
        readVarInt(offset);
        return readVarInt(offset);
    }

    /**
     * The yaw of a sent look packet: player look (yaw first) or player position and look (after
     * the three coordinates).
     */
    public float lookYaw() {
        int offset;
        if (id == OUT_PLAYER_LOOK) {
            offset = 0;
        } else if (id == OUT_PLAYER_POSITION_LOOK) {
            offset = 24;
        } else {
            throw new IllegalArgumentException("packet " + id + " has no look");
        }
        if (data.length < offset + 4) {
            throw new IllegalArgumentException("look packet of " + data.length + " bytes");
        }
        return Float.intBitsToFloat(readInt(offset));
    }

    /** Spawn object: VarInt id, type byte, x y z ints in 1/32 block, pitch, yaw, data int, then speeds. */
    public SpawnedObject spawnedObject() {
        int[] offset = {0};
        int entity = readVarInt(offset);
        need(offset[0] + 1 + 12 + 2 + 4, "spawn object");
        int type = data[offset[0]] & 0xFF;
        int at = offset[0] + 1;
        double x = readInt(at) / POSITION_UNITS_PER_BLOCK;
        double y = readInt(at + 4) / POSITION_UNITS_PER_BLOCK;
        double z = readInt(at + 8) / POSITION_UNITS_PER_BLOCK;
        int objectData = readInt(at + 14);
        int[] speed = null;
        if (objectData > 0) {
            need(at + 18 + 6, "spawn object");
            speed = new int[] {readShort(at + 18), readShort(at + 20), readShort(at + 22)};
        }
        return new SpawnedObject(entity, type, x, y, z, objectData, speed);
    }

    /** Destroy entities: VarInt count, then the VarInt ids. */
    public int[] destroyedEntities() {
        int[] offset = {0};
        int count = readVarInt(offset);
        if (count < 0 || count > data.length) {
            throw new IllegalArgumentException("destroy entities of " + count + " entities");
        }
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            ids[i] = readVarInt(offset);
        }
        return ids;
    }

    /**
     * Entity relative move (with or without look) and entity teleport: the entity, and its new
     * position for a teleport or its move for a relative move, in blocks.
     */
    public int movedEntity() {
        return readVarInt(new int[] {0});
    }

    public double[] relativeMove() {
        int[] offset = {0};
        readVarInt(offset);
        need(offset[0] + 3, "relative move");
        return new double[] {data[offset[0]] / POSITION_UNITS_PER_BLOCK, data[offset[0] + 1] / POSITION_UNITS_PER_BLOCK,
                data[offset[0] + 2] / POSITION_UNITS_PER_BLOCK};
    }

    public double[] teleport() {
        int[] offset = {0};
        readVarInt(offset);
        need(offset[0] + 12, "entity teleport");
        return new double[] {readInt(offset[0]) / POSITION_UNITS_PER_BLOCK,
                readInt(offset[0] + 4) / POSITION_UNITS_PER_BLOCK, readInt(offset[0] + 8) / POSITION_UNITS_PER_BLOCK};
    }

    /**
     * Explosion: x y z and strength as floats, an int count of affected blocks given as three
     * signed bytes from the centre cast to int (as the client reads them), then the push given to
     * the player as three floats.
     */
    public ExplosionPacket explosion() {
        need(20, "explosion");
        float x = Float.intBitsToFloat(readInt(0));
        float y = Float.intBitsToFloat(readInt(4));
        float z = Float.intBitsToFloat(readInt(8));
        float strength = Float.intBitsToFloat(readInt(12));
        int count = readInt(16);
        if (count < 0 || 20 + 3L * count + 12 != data.length) {
            throw new IllegalArgumentException("explosion of " + data.length + " bytes for " + count + " blocks");
        }
        int baseX = (int) x;
        int baseY = (int) y;
        int baseZ = (int) z;
        List<int[]> blocks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int at = 20 + 3 * i;
            blocks.add(new int[] {baseX + data[at], baseY + data[at + 1], baseZ + data[at + 2]});
        }
        int at = 20 + 3 * count;
        double[] push = {Float.intBitsToFloat(readInt(at)), Float.intBitsToFloat(readInt(at + 4)),
                Float.intBitsToFloat(readInt(at + 8))};
        return new ExplosionPacket(x, y, z, strength, blocks, push);
    }

    /**
     * Block change and multi block change: the changed positions with their new block, as
     * {@code [x, y, z, block id, metadata]}.
     */
    public List<int[]> blockChanges() {
        List<int[]> changes = new ArrayList<>();
        if (id == IN_BLOCK_CHANGE) {
            need(8, "block change");
            long position = (long) readInt(0) << 32 | readInt(4) & 0xFFFFFFFFL;
            int[] offset = {8};
            int state = readVarInt(offset);
            changes.add(new int[] {(int) (position >> 38), (int) (position << 26 >> 52), (int) (position << 38 >> 38),
                    state >> 4, state & 15});
        } else if (id == IN_MULTI_BLOCK_CHANGE) {
            need(8, "multi block change");
            int chunkX = readInt(0);
            int chunkZ = readInt(4);
            int[] offset = {8};
            int count = readVarInt(offset);
            for (int i = 0; i < count; i++) {
                need(offset[0] + 2, "multi block change");
                int horizontal = data[offset[0]] & 0xFF;
                int y = data[offset[0] + 1] & 0xFF;
                offset[0] += 2;
                int state = readVarInt(offset);
                changes.add(new int[] {chunkX * 16 + (horizontal >> 4), y, chunkZ * 16 + (horizontal & 15),
                        state >> 4, state & 15});
            }
        } else {
            throw new IllegalArgumentException("packet " + id + " changes no block");
        }
        return changes;
    }

    /**
     * Player block placement (sent, a right click): the item held, or -1 for an empty hand. The
     * position (a long) and the face (a byte) come first.
     */
    public int placedItem() {
        need(11, "player block placement");
        return readShort(9);
    }

    /** True for a sent packet that carries the player's look. */
    public boolean isSentLook() {
        return !inbound && (id == OUT_PLAYER_LOOK || id == OUT_PLAYER_POSITION_LOOK);
    }

    private void need(int length, String what) {
        if (data.length < length) {
            throw new IllegalArgumentException(what + " packet of " + data.length + " bytes");
        }
    }

    private int readInt(int offset) {
        return (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16 | (data[offset + 2] & 0xFF) << 8
                | data[offset + 3] & 0xFF;
    }

    private int readShort(int offset) {
        return (short) ((data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF);
    }

    /** A primed TNT or fireball seen spawning. */
    public static final class SpawnedObject {
        public final int entity;
        public final int type;
        public final double x;
        public final double y;
        public final double z;
        /** The object data: for a fireball, the entity id of its shooter (0 when none). */
        public final int data;
        /** For an object with data, its speed: for a fireball, its acceleration, in 1/8000 block per tick. */
        public final int[] speed;

        SpawnedObject(int entity, int type, double x, double y, double z, int data, int[] speed) {
            this.entity = entity;
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
            this.speed = speed;
        }
    }

    /** An explosion as the server sent it. */
    public static final class ExplosionPacket {
        public final float x;
        public final float y;
        public final float z;
        public final float strength;
        /** The blocks the explosion destroys, as {@code [x, y, z]}. */
        public final List<int[]> blocks;
        /** The push added to the player, in blocks per tick. */
        public final double[] push;

        ExplosionPacket(float x, float y, float z, float strength, List<int[]> blocks, double[] push) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.strength = strength;
            this.blocks = blocks;
            this.push = push;
        }
    }

    private int readVarInt(int[] offset) {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            if (offset[0] >= data.length) {
                throw new IllegalArgumentException("truncated VarInt");
            }
            int b = data[offset[0]++];
            value |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IllegalArgumentException("VarInt longer than 5 bytes");
    }
}
