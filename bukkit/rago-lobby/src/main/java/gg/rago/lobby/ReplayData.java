package gg.rago.lobby;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A replay as a file, and as the viewer reads it back. The file: "RAGOREPLAY" and a version, then
 * three gzipped blocks, each after its length: the info (the menu reads only it), the stage (the
 * arena's blocks, for a replay that holds them) and the body: what happened, tick after tick.
 *
 * <p>Positions are in 1/32 of a block from the origin of the game (as the 1.8 protocol sends
 * them), angles in 1/256 of a turn. In memory, each player's position, angles, flags and health
 * are kept for every tick, so that a jump to any moment is immediate; the other events (hits,
 * blocks, projectiles, chat) are listed with their tick. No Bukkit here: tested as it is.
 */
final class ReplayData {

    static final String MAGIC = "RAGOREPLAY";
    static final int VERSION = 1;

    /** The records of the body. A tick ends with TICK; what comes before it happened in it. */
    static final int TICK = 0;
    static final int POS_ABS = 1;
    static final int POS_REL = 2;
    static final int FLAGS = 3;
    static final int EQUIP = 4;
    static final int SWING = 5;
    static final int HURT = 6;
    static final int HEALTH = 7;
    static final int BLOCK = 8;
    static final int ENT_ADD = 9;
    static final int ENT_MOVE = 10;
    static final int ENT_GONE = 11;
    static final int CHAT = 12;
    static final int EXPLODE = 13;

    /** The flags of a player. */
    static final int SNEAK = 0x01;
    static final int SPRINT = 0x02;
    static final int USING = 0x04;
    static final int INVISIBLE = 0x08;
    static final int BURNING = 0x10;
    /** Not in the game this tick (dead, spectating, gone): not shown. */
    static final int HIDDEN = 0x20;

    /** The slots of the equipment, as the 1.8 protocol numbers them: the hand, then the armour from the boots. */
    static final int HAND = 0;
    static final int SLOTS = 5;

    /** The kinds of entity of ENT_ADD. */
    static final int ARROW = 1;
    static final int SNOWBALL = 2;
    static final int EGG = 3;
    static final int FIREBALL = 4;
    static final int SMALL_FIREBALL = 5;
    static final int ENDER_PEARL = 6;
    static final int TNT = 7;
    static final int FALLING_BLOCK = 8;
    static final int POTION = 9;

    /** One block of positions: 32 units. */
    static final int UNIT = 32;

    /** What the menu shows of a replay. */
    static final class Info {
        String id = "";
        String modeId = "";
        String modeName = "";
        String mapName = "";
        long startedAt;
        int ticks;
        String result = "";
        String stageKind = "";
        String stageKey = "";
        final List<Actor> actors = new ArrayList<>();

        /** The same, its players without their skins: what the menu keeps in memory of each replay. */
        Info light() {
            Info light = new Info();
            light.id = id;
            light.modeId = modeId;
            light.modeName = modeName;
            light.mapName = mapName;
            light.startedAt = startedAt;
            light.ticks = ticks;
            light.result = result;
            light.stageKind = stageKind;
            light.stageKey = stageKey;
            for (Actor actor : actors) {
                light.actors.add(new Actor(actor.id, actor.name, actor.colour, null, null));
            }
            return light;
        }

        /** The player's place among the actors, or -1. */
        int actorOf(UUID player) {
            for (int i = 0; i < actors.size(); i++) {
                if (actors.get(i).id.equals(player)) {
                    return i;
                }
            }
            return -1;
        }
    }

    /** A player of the replay. */
    static final class Actor {
        final UUID id;
        final String name;
        /** The colour code of their team (the character after the section sign). */
        final char colour;
        final String skinValue;
        final String skinSignature;

        Actor(UUID id, String name, char colour, String skinValue, String skinSignature) {
            this.id = id;
            this.name = name;
            this.colour = colour;
            this.skinValue = skinValue == null ? "" : skinValue;
            this.skinSignature = skinSignature == null ? "" : skinSignature;
        }
    }

    /** An item as a replay shows it: its id and data, shining or not, the colour of leather (-1: none). */
    static final class Item {
        static final Item NONE = new Item(0, 0, false, -1);

        final int id;
        final int data;
        final boolean glow;
        final int colour;

        Item(int id, int data, boolean glow, int colour) {
            this.id = id;
            this.data = data;
            this.glow = glow;
            this.colour = colour;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Item)) {
                return false;
            }
            Item item = (Item) other;
            return id == item.id && data == item.data && glow == item.glow && colour == item.colour;
        }

        @Override
        public int hashCode() {
            return ((id * 31 + data) * 31 + (glow ? 1 : 0)) * 31 + colour;
        }
    }

    /** Something that happened in a tick, other than the players' state. */
    static final class Event {
        final int tick;
        final int type;
        final int a;
        final int b;
        final int c;
        final int d;
        final int e;
        final int f;
        final int g;
        final String text;

        Event(int tick, int type, int a, int b, int c, int d, int e, int f, int g, String text) {
            this.tick = tick;
            this.type = type;
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
            this.f = f;
            this.g = g;
            this.text = text;
        }
    }

    /** A projectile or other entity at a moment: its kind, where, how it looks, and its extra number. */
    static final class Thing {
        final int id;
        final int kind;
        final int extra;
        int x;
        int y;
        int z;
        int yaw;
        int pitch;

        Thing(int id, int kind, int extra) {
            this.id = id;
            this.kind = kind;
            this.extra = extra;
        }
    }

    final Info info;
    /** The stage's blocks, five numbers a block from the origin: x, y, z, id, data. */
    final int[] stage;
    final int ticks;
    final int[][] x;
    final int[][] y;
    final int[][] z;
    final byte[][] yaw;
    final byte[][] pitch;
    final byte[][] flags;
    final byte[][] health;
    /** For each player and slot, the ticks its item changed, and the items. */
    private final int[][][] equipTicks;
    private final Item[][][] equipItems;
    private final List<Event> events;
    /** The events of tick k are those from firstEvent[k] to firstEvent[k + 1]. */
    private final int[] firstEvent;
    /** Each projectile's speed at its start, in blocks a tick (from its first move). */
    private final Map<Integer, double[]> velocities = new HashMap<>();

    private ReplayData(Info info, int[] stage, int ticks, int actors, List<Event> events, int[] firstEvent,
            int[][][] equipTicks, Item[][][] equipItems) {
        this.info = info;
        this.stage = stage;
        this.ticks = ticks;
        this.x = new int[actors][];
        this.y = new int[actors][];
        this.z = new int[actors][];
        this.yaw = new byte[actors][];
        this.pitch = new byte[actors][];
        this.flags = new byte[actors][];
        this.health = new byte[actors][];
        this.events = events;
        this.firstEvent = firstEvent;
        this.equipTicks = equipTicks;
        this.equipItems = equipItems;
    }

    int actors() {
        return info.actors.size();
    }

    /** The events of this tick. */
    List<Event> events(int tick) {
        if (tick < 0 || tick >= ticks) {
            return Collections.emptyList();
        }
        return events.subList(firstEvent[tick], firstEvent[tick + 1]);
    }

    /** The item in this slot of this player at this tick. */
    Item item(int actor, int slot, int tick) {
        int[] changes = equipTicks[actor][slot];
        int index = Arrays.binarySearch(changes, tick);
        if (index < 0) {
            index = -index - 2;
        } else {
            // several changes in one tick: the last one
            while (index + 1 < changes.length && changes[index + 1] == tick) {
                index++;
            }
        }
        return index < 0 ? Item.NONE : equipItems[actor][slot][index];
    }

    /**
     * A projectile's speed at its start, in blocks a tick, from where it went next (a client
     * flies it between two positions sent); a small fall for one that never moved.
     */
    double[] velocityOf(int thing) {
        double[] velocity = velocities.get(thing);
        return velocity == null ? new double[] {0, -0.05, 0} : velocity.clone();
    }

    /** The projectiles and other entities there at the end of this tick, by their id. */
    Map<Integer, Thing> thingsAt(int tick) {
        Map<Integer, Thing> things = new LinkedHashMap<>();
        int end = firstEvent[Math.max(0, Math.min(ticks, tick + 1))];
        for (int i = 0; i < end; i++) {
            Event event = events.get(i);
            if (event.type == ENT_ADD) {
                Thing thing = new Thing(event.a, event.b, event.g);
                moveThing(thing, event.c, event.d, event.e, event.f);
                things.put(event.a, thing);
            } else if (event.type == ENT_MOVE) {
                Thing thing = things.get(event.a);
                if (thing != null) {
                    moveThing(thing, event.b, event.c, event.d, event.e);
                }
            } else if (event.type == ENT_GONE) {
                things.remove(event.a);
            }
        }
        return things;
    }

    /** The angles of ENT_ADD and ENT_MOVE are packed together: yaw in the high byte, pitch in the low one. */
    private static void moveThing(Thing thing, int x, int y, int z, int angles) {
        thing.x = x;
        thing.y = y;
        thing.z = z;
        thing.yaw = (byte) (angles >> 8);
        thing.pitch = (byte) angles;
    }

    // --- Writing -------------------------------------------------------------------------------

    /** The body of a replay as it is recorded, gzipped on the way, in memory. */
    static final class BodyWriter {

        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final DataOutputStream out;
        private int ticks;

        BodyWriter() {
            try {
                out = new DataOutputStream(new GZIPOutputStream(bytes, 8192));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        int ticks() {
            return ticks;
        }

        void tick() {
            write(TICK);
            ticks++;
        }

        void position(int actor, int x, int y, int z, int yaw, int pitch) {
            try {
                out.writeByte(POS_ABS);
                out.writeByte(actor);
                out.writeInt(x);
                out.writeInt(y);
                out.writeInt(z);
                out.writeByte(yaw);
                out.writeByte(pitch);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /** A move of at most 127 units on each axis from the last position. */
        void move(int actor, int dx, int dy, int dz, int yaw, int pitch) {
            write(POS_REL, actor, dx, dy, dz, yaw, pitch);
        }

        void flags(int actor, int flags) {
            write(FLAGS, actor, flags);
        }

        void equip(int actor, int slot, Item item) {
            try {
                out.writeByte(EQUIP);
                out.writeByte(actor);
                out.writeByte(slot);
                out.writeShort(item.id);
                out.writeShort(item.data);
                out.writeBoolean(item.glow);
                out.writeInt(item.colour);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        void swing(int actor) {
            write(SWING, actor);
        }

        void hurt(int actor) {
            write(HURT, actor);
        }

        void health(int actor, int halfHearts) {
            write(HEALTH, actor, halfHearts);
        }

        /** A block, in blocks from the origin, as it is now. */
        void block(int x, int y, int z, int id, int data) {
            try {
                out.writeByte(BLOCK);
                out.writeInt(x);
                out.writeShort(y);
                out.writeInt(z);
                out.writeShort(id);
                out.writeByte(data);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        void thingAdded(int id, int kind, int x, int y, int z, int yaw, int pitch, int extra) {
            try {
                out.writeByte(ENT_ADD);
                out.writeInt(id);
                out.writeByte(kind);
                out.writeInt(x);
                out.writeInt(y);
                out.writeInt(z);
                out.writeByte(yaw);
                out.writeByte(pitch);
                out.writeInt(extra);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        void thingMoved(int id, int x, int y, int z, int yaw, int pitch) {
            try {
                out.writeByte(ENT_MOVE);
                out.writeInt(id);
                out.writeInt(x);
                out.writeInt(y);
                out.writeInt(z);
                out.writeByte(yaw);
                out.writeByte(pitch);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        void thingGone(int id) {
            try {
                out.writeByte(ENT_GONE);
                out.writeInt(id);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        void chat(String line) {
            try {
                out.writeByte(CHAT);
                out.writeUTF(line.length() > 2000 ? line.substring(0, 2000) : line);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        void explosion(int x, int y, int z) {
            try {
                out.writeByte(EXPLODE);
                out.writeInt(x);
                out.writeInt(y);
                out.writeInt(z);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /** The body, gzipped: nothing more can be written. */
        byte[] finish() {
            try {
                out.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return bytes.toByteArray();
        }

        private void write(int... values) {
            try {
                for (int value : values) {
                    out.writeByte(value);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Writes a whole replay: the info, the stage's blocks (air left out) and the body from {@link BodyWriter#finish}. */
    static void write(OutputStream target, Info info, int[] stage, byte[] body) throws IOException {
        DataOutputStream out = new DataOutputStream(target);
        out.writeUTF(MAGIC);
        out.writeInt(VERSION);
        block(out, infoBytes(info));
        ByteArrayOutputStream stageBytes = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(stageBytes))) {
            int count = 0;
            for (int i = 0; i + 4 < stage.length; i += 5) {
                if (stage[i + 3] != 0) {
                    count++;
                }
            }
            data.writeInt(count);
            for (int i = 0; i + 4 < stage.length; i += 5) {
                if (stage[i + 3] == 0) {
                    continue;
                }
                data.writeInt(stage[i]);
                data.writeShort(stage[i + 1]);
                data.writeInt(stage[i + 2]);
                data.writeShort(stage[i + 3]);
                data.writeByte(stage[i + 4]);
            }
        }
        block(out, stageBytes.toByteArray());
        block(out, body);
        out.flush();
    }

    private static byte[] infoBytes(Info info) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeUTF(info.id);
            out.writeUTF(info.modeId);
            out.writeUTF(info.modeName);
            out.writeUTF(info.mapName);
            out.writeLong(info.startedAt);
            out.writeInt(info.ticks);
            out.writeUTF(info.result);
            out.writeUTF(info.stageKind);
            out.writeUTF(info.stageKey);
            out.writeShort(info.actors.size());
            for (Actor actor : info.actors) {
                out.writeLong(actor.id.getMostSignificantBits());
                out.writeLong(actor.id.getLeastSignificantBits());
                out.writeUTF(actor.name);
                out.writeChar(actor.colour);
                out.writeUTF(actor.skinValue);
                out.writeUTF(actor.skinSignature);
            }
        }
        return bytes.toByteArray();
    }

    private static void block(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    // --- Reading -------------------------------------------------------------------------------

    /** The info alone, for the menu: the blocks after it are not read. */
    static Info readInfo(InputStream source) throws IOException {
        DataInputStream in = new DataInputStream(source);
        header(in);
        return info(gunzip(nextBlock(in)));
    }

    /** The whole replay. */
    static ReplayData read(InputStream source) throws IOException {
        DataInputStream in = new DataInputStream(source);
        header(in);
        Info info = info(gunzip(nextBlock(in)));
        int[] stage = stage(gunzip(nextBlock(in)));
        DataInputStream body = gunzip(nextBlock(in));
        return body(info, stage, body);
    }

    private static void header(DataInputStream in) throws IOException {
        if (!MAGIC.equals(in.readUTF())) {
            throw new IOException("not a replay");
        }
        int version = in.readInt();
        if (version != VERSION) {
            throw new IOException("replay version " + version + ", this server reads " + VERSION);
        }
    }

    private static byte[] nextBlock(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 512 * 1024 * 1024) {
            throw new IOException("bad block length " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }

    private static DataInputStream gunzip(byte[] bytes) throws IOException {
        return new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes), 8192));
    }

    private static Info info(DataInputStream in) throws IOException {
        Info info = new Info();
        info.id = in.readUTF();
        info.modeId = in.readUTF();
        info.modeName = in.readUTF();
        info.mapName = in.readUTF();
        info.startedAt = in.readLong();
        info.ticks = in.readInt();
        info.result = in.readUTF();
        info.stageKind = in.readUTF();
        info.stageKey = in.readUTF();
        int actors = in.readUnsignedShort();
        for (int i = 0; i < actors; i++) {
            UUID id = new UUID(in.readLong(), in.readLong());
            String name = in.readUTF();
            char colour = in.readChar();
            info.actors.add(new Actor(id, name, colour, in.readUTF(), in.readUTF()));
        }
        return info;
    }

    private static int[] stage(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > 20_000_000) {
            throw new IOException("bad stage size " + count);
        }
        int[] blocks = new int[count * 5];
        for (int i = 0; i < blocks.length; i += 5) {
            blocks[i] = in.readInt();
            blocks[i + 1] = in.readShort();
            blocks[i + 2] = in.readInt();
            blocks[i + 3] = in.readShort();
            blocks[i + 4] = in.readUnsignedByte();
        }
        return blocks;
    }

    private static ReplayData body(Info info, int[] stage, DataInputStream in) throws IOException {
        int actors = info.actors.size();
        int ticks = Math.max(1, info.ticks);
        List<Event> events = new ArrayList<>();
        int[] firstEvent = new int[ticks + 1];
        List<List<int[]>> equipChanges = new ArrayList<>();
        List<List<Item>> equipItems = new ArrayList<>();
        for (int i = 0; i < actors * SLOTS; i++) {
            equipChanges.add(new ArrayList<int[]>());
            equipItems.add(new ArrayList<Item>());
        }
        ReplayData data = new ReplayData(info, stage, ticks, actors, events, firstEvent, new int[actors][SLOTS][],
                new Item[actors][SLOTS][]);
        int[] cx = new int[actors];
        int[] cy = new int[actors];
        int[] cz = new int[actors];
        byte[] cyaw = new byte[actors];
        byte[] cpitch = new byte[actors];
        byte[] cflags = new byte[actors];
        byte[] chealth = new byte[actors];
        for (int a = 0; a < actors; a++) {
            data.x[a] = new int[ticks];
            data.y[a] = new int[ticks];
            data.z[a] = new int[ticks];
            data.yaw[a] = new byte[ticks];
            data.pitch[a] = new byte[ticks];
            data.flags[a] = new byte[ticks];
            data.health[a] = new byte[ticks];
            cflags[a] = (byte) HIDDEN;
            chealth[a] = 20;
        }
        Map<Integer, int[]> starts = new HashMap<>();
        int tick = 0;
        while (tick < ticks) {
            int type;
            try {
                type = in.readUnsignedByte();
            } catch (EOFException e) {
                break;
            }
            switch (type) {
                case TICK:
                    for (int a = 0; a < actors; a++) {
                        data.x[a][tick] = cx[a];
                        data.y[a][tick] = cy[a];
                        data.z[a][tick] = cz[a];
                        data.yaw[a][tick] = cyaw[a];
                        data.pitch[a][tick] = cpitch[a];
                        data.flags[a][tick] = cflags[a];
                        data.health[a][tick] = chealth[a];
                    }
                    tick++;
                    firstEvent[tick] = events.size();
                    break;
                case POS_ABS: {
                    int a = actor(in.readUnsignedByte(), actors);
                    cx[a] = in.readInt();
                    cy[a] = in.readInt();
                    cz[a] = in.readInt();
                    cyaw[a] = in.readByte();
                    cpitch[a] = in.readByte();
                    break;
                }
                case POS_REL: {
                    int a = actor(in.readUnsignedByte(), actors);
                    cx[a] += in.readByte();
                    cy[a] += in.readByte();
                    cz[a] += in.readByte();
                    cyaw[a] = in.readByte();
                    cpitch[a] = in.readByte();
                    break;
                }
                case FLAGS: {
                    int a = actor(in.readUnsignedByte(), actors);
                    cflags[a] = in.readByte();
                    break;
                }
                case EQUIP: {
                    int a = actor(in.readUnsignedByte(), actors);
                    int slot = in.readUnsignedByte();
                    if (slot >= SLOTS) {
                        throw new IOException("bad slot " + slot);
                    }
                    Item item = new Item(in.readShort(), in.readShort(), in.readBoolean(), in.readInt());
                    equipChanges.get(a * SLOTS + slot).add(new int[] {tick});
                    equipItems.get(a * SLOTS + slot).add(item);
                    break;
                }
                case SWING:
                case HURT: {
                    int a = actor(in.readUnsignedByte(), actors);
                    events.add(new Event(tick, type, a, 0, 0, 0, 0, 0, 0, null));
                    break;
                }
                case HEALTH: {
                    int a = actor(in.readUnsignedByte(), actors);
                    chealth[a] = (byte) in.readUnsignedByte();
                    break;
                }
                case BLOCK:
                    events.add(new Event(tick, type, in.readInt(), in.readShort(), in.readInt(), in.readShort(),
                            in.readUnsignedByte(), 0, 0, null));
                    break;
                case ENT_ADD: {
                    int id = in.readInt();
                    int kind = in.readUnsignedByte();
                    int ex = in.readInt();
                    int ey = in.readInt();
                    int ez = in.readInt();
                    int angles = (in.readUnsignedByte() << 8) | in.readUnsignedByte();
                    events.add(new Event(tick, type, id, kind, ex, ey, ez, angles, in.readInt(), null));
                    starts.put(id, new int[] {tick, ex, ey, ez});
                    break;
                }
                case ENT_MOVE: {
                    int id = in.readInt();
                    int ex = in.readInt();
                    int ey = in.readInt();
                    int ez = in.readInt();
                    int angles = (in.readUnsignedByte() << 8) | in.readUnsignedByte();
                    events.add(new Event(tick, type, id, ex, ey, ez, angles, 0, 0, null));
                    int[] start = starts.remove(id);
                    if (start != null) {
                        double ticksOn = Math.max(1, tick - start[0]);
                        data.velocities.put(id, new double[] {speed(ex - start[1], ticksOn), speed(ey - start[2], ticksOn),
                            speed(ez - start[3], ticksOn)});
                    }
                    break;
                }
                case ENT_GONE:
                    events.add(new Event(tick, type, in.readInt(), 0, 0, 0, 0, 0, 0, null));
                    break;
                case CHAT:
                    events.add(new Event(tick, type, 0, 0, 0, 0, 0, 0, 0, in.readUTF()));
                    break;
                case EXPLODE:
                    events.add(new Event(tick, type, in.readInt(), in.readInt(), in.readInt(), 0, 0, 0, 0, null));
                    break;
                default:
                    throw new IOException("unknown record " + type + " at tick " + tick);
            }
        }
        // a body cut short (a server stopped while writing): the last state stays until the end
        for (int t = tick; t < ticks; t++) {
            for (int a = 0; a < actors; a++) {
                data.x[a][t] = cx[a];
                data.y[a][t] = cy[a];
                data.z[a][t] = cz[a];
                data.yaw[a][t] = cyaw[a];
                data.pitch[a][t] = cpitch[a];
                data.flags[a][t] = cflags[a];
                data.health[a][t] = chealth[a];
            }
            firstEvent[t + 1] = events.size();
        }
        for (int a = 0; a < actors; a++) {
            for (int slot = 0; slot < SLOTS; slot++) {
                List<int[]> changes = equipChanges.get(a * SLOTS + slot);
                int[] at = new int[changes.size()];
                for (int i = 0; i < at.length; i++) {
                    at[i] = changes.get(i)[0];
                }
                data.equipTicks[a][slot] = at;
                data.equipItems[a][slot] = equipItems.get(a * SLOTS + slot).toArray(new Item[0]);
            }
        }
        return data;
    }

    /** A distance in 1/32 of a block over some ticks, in blocks a tick, as a 1.8 velocity can say it. */
    private static double speed(int units, double ticks) {
        return Math.max(-3.9, Math.min(3.9, units / (double) UNIT / ticks));
    }

    private static int actor(int index, int actors) throws IOException {
        if (index >= actors) {
            throw new IOException("no player " + index);
        }
        return index;
    }

    // --- Helpers -------------------------------------------------------------------------------

    /** A position in 1/32 of a block from the origin. */
    static int fixed(double position, int origin) {
        return (int) Math.floor((position - origin) * UNIT);
    }

    /** An angle in degrees as 1/256 of a turn, as a signed byte. */
    static int angle(float degrees) {
        return (byte) (int) Math.floor(degrees * 256.0F / 360.0F);
    }

    /** Back to degrees. */
    static float degrees(byte angle) {
        return angle * 360.0F / 256.0F;
    }

    /** The time of a tick as m:ss. */
    static String clock(int tick) {
        int seconds = Math.max(0, tick) / 20;
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    /** Groups the events by kind, for the tests. */
    static Map<Integer, Integer> countByType(ReplayData data) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Event event : data.events) {
            counts.merge(event.type, 1, Integer::sum);
        }
        return counts;
    }
}
