package gg.rago.buildtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LevelVersionTest {

    @TempDir
    Path folder;

    @Test
    void aWorldWithoutDataVersionComesFrom18OrOlder() throws IOException {
        assertEquals(LevelVersion.BEFORE_1_9, LevelVersion.read(in(level(null))));
    }

    @Test
    void readsTheDataVersionPastEveryOtherKindOfTag() throws IOException {
        assertEquals(1343, LevelVersion.read(in(level(1343))));
        assertEquals(3953, LevelVersion.read(in(level(3953))));
    }

    @Test
    void readsTheLevelDatOfAWorldFolder() throws IOException {
        Files.write(folder.resolve("level.dat"), level(1139));
        assertEquals(1139, LevelVersion.read(folder.toFile()));
    }

    @Test
    void refusesWhatIsNotALevelDat() throws IOException {
        assertThrows(IOException.class, () -> LevelVersion.read(new ByteArrayInputStream("pas un level.dat".getBytes("UTF-8"))));
        byte[] whole = level(1343);
        byte[] truncated = gzip(java.util.Arrays.copyOf(gunzip(whole), 40));
        assertThrows(IOException.class, () -> LevelVersion.read(in(truncated)));
        assertThrows(IOException.class, () -> LevelVersion.read(folder.toFile()));
    }

    /** A level.dat shaped like Minecraft's, with every tag type before DataVersion. */
    private static byte[] level(Integer dataVersion) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(raw);
        out.writeByte(10);
        out.writeUTF("");
        // An unrelated compound before Data, as some tools write.
        out.writeByte(10);
        out.writeUTF("FML");
        out.writeByte(1);
        out.writeUTF("flag");
        out.writeByte(1);
        out.writeByte(0);
        out.writeByte(10);
        out.writeUTF("Data");
        out.writeByte(1);
        out.writeUTF("raining");
        out.writeByte(0);
        out.writeByte(2);
        out.writeUTF("short");
        out.writeShort(7);
        out.writeByte(3);
        out.writeUTF("SpawnX");
        out.writeInt(12);
        out.writeByte(4);
        out.writeUTF("RandomSeed");
        out.writeLong(123456789L);
        out.writeByte(5);
        out.writeUTF("float");
        out.writeFloat(1.5f);
        out.writeByte(6);
        out.writeUTF("BorderSize");
        out.writeDouble(6.0E7);
        out.writeByte(7);
        out.writeUTF("bytes");
        out.writeInt(3);
        out.write(new byte[] {1, 2, 3});
        out.writeByte(8);
        out.writeUTF("LevelName");
        out.writeUTF("Lobby é");
        out.writeByte(9);
        out.writeUTF("list");
        out.writeByte(10);
        out.writeInt(2);
        out.writeByte(3);
        out.writeUTF("x");
        out.writeInt(1);
        out.writeByte(0);
        out.writeByte(0);
        out.writeByte(11);
        out.writeUTF("ints");
        out.writeInt(2);
        out.writeInt(1);
        out.writeInt(2);
        out.writeByte(12);
        out.writeUTF("longs");
        out.writeInt(1);
        out.writeLong(9L);
        out.writeByte(10);
        out.writeUTF("GameRules");
        out.writeByte(8);
        out.writeUTF("doDaylightCycle");
        out.writeUTF("true");
        out.writeByte(0);
        if (dataVersion != null) {
            out.writeByte(3);
            out.writeUTF("DataVersion");
            out.writeInt(dataVersion);
        }
        out.writeByte(0);
        out.writeByte(0);
        out.flush();
        return gzip(raw.toByteArray());
    }

    private static byte[] gzip(byte[] bytes) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(bytes);
        }
        return compressed.toByteArray();
    }

    private static byte[] gunzip(byte[] bytes) throws IOException {
        try (java.util.zip.GZIPInputStream in = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(bytes))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static ByteArrayInputStream in(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }
}
