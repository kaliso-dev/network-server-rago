package gg.rago.tools.kbanalyzer;

import com.google.gson.JsonObject;
import gg.rago.combat.model.VelocityPacket;
import java.util.Base64;

/**
 * A packet line: the bytes of one packet as on the wire after decompression, without the packet
 * id. Decoding methods throw {@link IllegalArgumentException} when the bytes do not fit.
 */
public final class Packet {

    /** Protocol 47 ids of received packets. */
    public static final int IN_JOIN_GAME = 0x01;
    public static final int IN_ANIMATION = 0x0B;
    public static final int IN_ENTITY_VELOCITY = 0x12;
    public static final int IN_ENTITY_STATUS = 0x1A;
    public static final int IN_EXPLOSION = 0x27;
    /** Protocol 47 ids of sent packets. */
    public static final int OUT_USE_ENTITY = 0x02;
    public static final int OUT_PLAYER_POSITION = 0x04;
    public static final int OUT_PLAYER_LOOK = 0x05;
    public static final int OUT_PLAYER_POSITION_LOOK = 0x06;
    public static final int OUT_ANIMATION = 0x0A;
    public static final int OUT_ENTITY_ACTION = 0x0B;

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

    /** True for a sent packet that carries the player's look. */
    public boolean isSentLook() {
        return !inbound && (id == OUT_PLAYER_LOOK || id == OUT_PLAYER_POSITION_LOOK);
    }

    private int readInt(int offset) {
        return (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16 | (data[offset + 2] & 0xFF) << 8
                | data[offset + 3] & 0xFF;
    }

    private int readShort(int offset) {
        return (short) ((data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF);
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
