package gg.rago.buildtools;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Reads which Minecraft version last saved a world, from its {@code level.dat}, before the 1.8
 * server opens it. A world saved by 1.13 or later uses a block format 1.8 does not understand:
 * opening it would empty its chunks. Worlds from 1.9 to 1.12 open, but blocks added after 1.8 are
 * lost.
 *
 * <p>Minecraft writes the version as {@code Data.DataVersion} since 1.9 (15w32a); a world without
 * it was saved by 1.8 or older.
 */
final class LevelVersion {

    /** No {@code DataVersion}: saved by Minecraft 1.8 or older. */
    static final int BEFORE_1_9 = -1;

    /** The data version of Minecraft 1.12.2, the last version whose worlds a 1.8 server can open. */
    static final int LAST_OPENABLE = 1343;

    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;

    /** A level.dat is small; anything deeper than this is not one. */
    private static final int MAX_DEPTH = 64;

    private LevelVersion() {
    }

    /**
     * The {@code DataVersion} of a world folder's level.dat, or {@link #BEFORE_1_9}.
     *
     * @throws IOException if level.dat is missing or is not a compressed NBT file
     */
    static int read(File worldFolder) throws IOException {
        try (InputStream in = new FileInputStream(new File(worldFolder, "level.dat"))) {
            return read(in);
        }
    }

    /** Same as {@link #read(File)}, from the bytes of a level.dat (gzip-compressed NBT). */
    static int read(InputStream compressed) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(compressed)));
        if (in.readUnsignedByte() != TAG_COMPOUND) {
            throw new IOException("level.dat does not start with an NBT compound");
        }
        in.readUTF();
        // Root compound: look for "Data", skip the rest.
        while (true) {
            int type = in.readUnsignedByte();
            if (type == TAG_END) {
                return BEFORE_1_9;
            }
            String name = in.readUTF();
            if (type == TAG_COMPOUND && "Data".equals(name)) {
                return dataVersionIn(in);
            }
            skip(in, type, 1);
        }
    }

    private static int dataVersionIn(DataInputStream in) throws IOException {
        while (true) {
            int type = in.readUnsignedByte();
            if (type == TAG_END) {
                return BEFORE_1_9;
            }
            String name = in.readUTF();
            if (type == TAG_INT && "DataVersion".equals(name)) {
                return in.readInt();
            }
            skip(in, type, 2);
        }
    }

    private static void skip(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("level.dat is nested too deeply");
        }
        switch (type) {
            case TAG_BYTE:
                skipBytes(in, 1);
                break;
            case TAG_SHORT:
                skipBytes(in, 2);
                break;
            case TAG_INT:
            case TAG_FLOAT:
                skipBytes(in, 4);
                break;
            case TAG_LONG:
            case TAG_DOUBLE:
                skipBytes(in, 8);
                break;
            case TAG_BYTE_ARRAY:
                skipBytes(in, length(in));
                break;
            case TAG_STRING:
                skipBytes(in, in.readUnsignedShort());
                break;
            case TAG_LIST: {
                int elementType = in.readUnsignedByte();
                int count = length(in);
                for (int i = 0; i < count; i++) {
                    skip(in, elementType, depth + 1);
                }
                break;
            }
            case TAG_COMPOUND:
                while (true) {
                    int child = in.readUnsignedByte();
                    if (child == TAG_END) {
                        break;
                    }
                    in.readUTF();
                    skip(in, child, depth + 1);
                }
                break;
            case TAG_INT_ARRAY:
                skipBytes(in, 4L * length(in));
                break;
            case TAG_LONG_ARRAY:
                skipBytes(in, 8L * length(in));
                break;
            default:
                throw new IOException("unknown NBT type in level.dat: " + type);
        }
    }

    private static int length(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0) {
            throw new IOException("negative length in level.dat");
        }
        return length;
    }

    private static void skipBytes(DataInputStream in, long count) throws IOException {
        long left = count;
        while (left > 0) {
            long skipped = in.skip(left);
            if (skipped <= 0) {
                // skip() may return 0 before the end: read one byte to tell "slow" from "ended".
                if (in.read() < 0) {
                    throw new IOException("level.dat is truncated");
                }
                skipped = 1;
            }
            left -= skipped;
        }
    }
}
