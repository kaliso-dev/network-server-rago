package gg.rago.practice;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * Many blocks of a map at once, written straight into the chunks of the server (1.8, v1_8_R3,
 * found by reflection as {@link BlockCracks} does): no light worked out and no update block by
 * block, which costs some 30 microseconds each the Bukkit way (a part of a map of 60 000 blocks:
 * two seconds of lag). The chunk is then lit by the sky from above, fully bright everywhere (under
 * the islands too), and sent again to the players who see it. No tile entity (a chest stays
 * empty, a sign blank) and no random tick (no leaf decays, no grass spreads): a map to play on, not
 * to live in. Main thread only. Null from {@link #of} when the server is not one it knows: the
 * blocks then go the Bukkit way.
 */
final class FastBlocks {

    private final World world;
    private final Object handle;
    private final Method chunkAt;
    private final Method sectionsOf;
    private final Constructor<?> newSection;
    private final Method idsOf;
    private final Method skyOf;
    private final Method bytesOf;
    private final Method initLighting;
    private final Field nonEmpty;
    private final Field ticking;
    /** PaperSpigot's mark of a changed section (its chunk packet, kept, is made again); null without it. */
    private final Field dirty;
    private final Method byCombined;
    private final Method combinedOf;
    private final boolean sky;
    /** The block the server keeps for (id << 4 | data), plus one; 0: not looked up yet. */
    private final char[] known = new char[1 << 16];

    private FastBlocks(World world, String nms, String craft) throws ReflectiveOperationException {
        this.world = world;
        handle = Class.forName(craft + ".CraftWorld").getMethod("getHandle").invoke(world);
        Class<?> nmsWorld = Class.forName(nms + "World");
        Class<?> chunk = Class.forName(nms + "Chunk");
        Class<?> section = Class.forName(nms + "ChunkSection");
        Class<?> nibbles = Class.forName(nms + "NibbleArray");
        Class<?> block = Class.forName(nms + "Block");
        chunkAt = nmsWorld.getMethod("getChunkAt", int.class, int.class);
        sectionsOf = chunk.getMethod("getSections");
        initLighting = chunk.getMethod("initLighting");
        newSection = section.getConstructor(int.class, boolean.class);
        idsOf = section.getMethod("getIdArray");
        skyOf = section.getMethod("getSkyLightArray");
        bytesOf = nibbles.getMethod("a");
        nonEmpty = section.getDeclaredField("nonEmptyBlockCount");
        nonEmpty.setAccessible(true);
        ticking = section.getDeclaredField("tickingBlockCount");
        ticking.setAccessible(true);
        Field mark;
        try {
            mark = section.getDeclaredField("isDirty");
            mark.setAccessible(true);
        } catch (NoSuchFieldException e) {
            mark = null;
        }
        dirty = mark;
        byCombined = block.getMethod("getByCombinedId", int.class);
        combinedOf = block.getMethod("getCombinedId", Class.forName(nms + "IBlockData"));
        sky = world.getEnvironment() == World.Environment.NORMAL;
        // a first block, to fail here rather than half way through a map
        id(1, 0);
    }

    /** The writer of this world's chunks; null (and a line in the logs) when this server's classes are not those of 1.8. */
    static FastBlocks of(World world, Logger logger) {
        try {
            String craft = Bukkit.getServer().getClass().getPackage().getName();
            String nms = "net.minecraft.server." + craft.substring(craft.lastIndexOf('.') + 1) + ".";
            return new FastBlocks(world, nms, craft);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.log(Level.WARNING, "The maps of the pearl practice go into their zones the slow way on this server"
                    + " (a short lag each time)", e);
            return null;
        }
    }

    /** The block as the server keeps it in a chunk: (id << 4 | data), its data made right. */
    char id(int id, int data) throws ReflectiveOperationException {
        int key = (id & 4095) << 4 | (data & 15);
        char found = known[key];
        if (found == 0) {
            int combined = (Integer) combinedOf.invoke(null, byCombined.invoke(null, (id & 4095) | (data & 15) << 12));
            found = (char) (((combined & 4095) << 4 | (combined >> 12 & 15)) + 1);
            known[key] = found;
        }
        return (char) (found - 1);
    }

    /**
     * Writes into the chunk (cx, cz): air at each place of {@code cleared} (x, y, z of the world,
     * three numbers each), then each block of {@code placed} (x, y, z of the world, id, data: five
     * each); lights it from the sky and sends it again to the players who see it.
     */
    @SuppressWarnings("deprecation") // World#refreshChunk: what it does on a 1.8 server is what is needed here
    void write(int cx, int cz, int[] cleared, int clearedLength, int[] placed, int placedLength)
            throws ReflectiveOperationException {
        Object chunk = chunkAt.invoke(handle, cx, cz);
        Object[] sections = (Object[]) sectionsOf.invoke(chunk);
        char[][] ids = new char[sections.length][];
        boolean[] touched = new boolean[sections.length];
        for (int i = 0; i + 2 < clearedLength; i += 3) {
            int y = cleared[i + 1];
            if (y < 0 || y >> 4 >= sections.length || sections[y >> 4] == null) {
                continue;
            }
            char[] array = ids(sections, ids, y >> 4);
            array[(y & 15) << 8 | (cleared[i + 2] & 15) << 4 | (cleared[i] & 15)] = 0;
            touched[y >> 4] = true;
        }
        for (int i = 0; i + 4 < placedLength; i += 5) {
            int y = placed[i + 1];
            if (y < 0 || y >> 4 >= sections.length) {
                continue;
            }
            char block = id(placed[i + 3], placed[i + 4]);
            if (block == 0 && sections[y >> 4] == null) {
                continue;
            }
            if (sections[y >> 4] == null) {
                sections[y >> 4] = newSection.newInstance(y >> 4 << 4, sky);
            }
            char[] array = ids(sections, ids, y >> 4);
            array[(y & 15) << 8 | (placed[i + 2] & 15) << 4 | (placed[i] & 15)] = block;
            touched[y >> 4] = true;
        }
        for (int s = 0; s < sections.length; s++) {
            if (!touched[s]) {
                continue;
            }
            int count = 0;
            for (char block : ids[s]) {
                if (block >> 4 != 0) {
                    count++;
                }
            }
            nonEmpty.setInt(sections[s], count);
            ticking.setInt(sections[s], 0);
        }
        // the height of each column, then the sky's light: fully bright everywhere
        initLighting.invoke(chunk);
        for (Object section : sections) {
            if (section == null) {
                continue;
            }
            Object light = skyOf.invoke(section);
            if (light != null) {
                Arrays.fill((byte[]) bytesOf.invoke(light), (byte) -1);
            }
            if (dirty != null) {
                dirty.setBoolean(section, true);
            }
        }
        world.refreshChunk(cx, cz);
    }

    /** The changes of one chunk of the world: the places to clear (x, y, z) and the blocks to place (x, y, z, id, data). */
    static final class Chunk {
        final int cx;
        final int cz;
        private int[] cleared = new int[48];
        private int clearedLength;
        private int[] placed = new int[80];
        private int placedLength;

        Chunk(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }

        private void clear(int x, int y, int z) {
            if (clearedLength + 3 > cleared.length) {
                cleared = Arrays.copyOf(cleared, cleared.length * 2);
            }
            cleared[clearedLength++] = x;
            cleared[clearedLength++] = y;
            cleared[clearedLength++] = z;
        }

        private void place(int x, int y, int z, int id, int data) {
            if (placedLength + 5 > placed.length) {
                placed = Arrays.copyOf(placed, placed.length * 2);
            }
            placed[placedLength++] = x;
            placed[placedLength++] = y;
            placed[placedLength++] = z;
            placed[placedLength++] = id;
            placed[placedLength++] = data;
        }

        int changes() {
            return clearedLength / 3 + placedLength / 5;
        }

        void write(FastBlocks fast) throws ReflectiveOperationException {
            fast.write(cx, cz, cleared, clearedLength, placed, placedLength);
        }
    }

    /**
     * The blocks of a part that go (five numbers each: x, y, z from the centre, id, data) and those
     * of the part that comes, chunk by chunk of the world, the centre at (x0, y0, z0).
     */
    static List<Chunk> byChunk(int[] gone, int[] coming, int x0, int y0, int z0) {
        Map<Long, Chunk> chunks = new LinkedHashMap<>();
        for (int i = 0; i + 4 < gone.length; i += 5) {
            int x = x0 + gone[i];
            int z = z0 + gone[i + 2];
            chunk(chunks, x >> 4, z >> 4).clear(x, y0 + gone[i + 1], z);
        }
        for (int i = 0; i + 4 < coming.length; i += 5) {
            int x = x0 + coming[i];
            int z = z0 + coming[i + 2];
            chunk(chunks, x >> 4, z >> 4).place(x, y0 + coming[i + 1], z, coming[i + 3], coming[i + 4]);
        }
        return new ArrayList<>(chunks.values());
    }

    private static Chunk chunk(Map<Long, Chunk> chunks, int cx, int cz) {
        long key = (long) cx << 32 | (cz & 0xFFFFFFFFL);
        Chunk chunk = chunks.get(key);
        if (chunk == null) {
            chunk = new Chunk(cx, cz);
            chunks.put(key, chunk);
        }
        return chunk;
    }

    private char[] ids(Object[] sections, char[][] ids, int s) throws ReflectiveOperationException {
        if (ids[s] == null) {
            ids[s] = (char[]) idsOf.invoke(sections[s]);
        }
        return ids[s];
    }
}
