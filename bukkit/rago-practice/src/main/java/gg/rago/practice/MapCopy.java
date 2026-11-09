package gg.rago.practice;

import gg.rago.lobby.api.VoidGenerator;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;

/**
 * A build copied from a world of the server into the zones of a training: a map of the owner's
 * own (in the folder of the worlds), read once at the start. What made the map work by itself goes:
 * its command blocks and blocks of redstone become the block around them, its wires, repeaters,
 * comparators, torches, levers, buttons, plates and signs become air. Blocks are kept relative to
 * an origin, which lands on the centre of the zone, its floor at the origin's y.
 */
final class MapCopy {

    /** Command block, block of redstone: in a wall, the wall's block takes their place. */
    private static final int[] SOLID_MECHANISMS = {137, 152};
    /** Redstone wire, repeaters, comparators, torches, levers, buttons, pressure plates, signs. */
    private static final int[] LOOSE_MECHANISMS = {55, 93, 94, 149, 150, 75, 76, 69, 77, 143, 70, 72, 147, 148, 63, 68};

    private static final int WOOL_ID = 35;

    /** Each block: x, y, z from the origin, id, data. */
    private final int[] blocks;

    private MapCopy(int[] blocks) {
        this.blocks = blocks;
    }

    int size() {
        return blocks.length / 5;
    }

    /**
     * Places the blocks from number {@code from}, {@code max} at most, the origin on (ox, oy, oz) of
     * the world; returns the number of the next block to place ({@link #size()} once all are).
     */
    @SuppressWarnings("deprecation") // Block#setTypeIdAndData: the 1.8 way to set a block with its data
    int place(World world, int ox, int oy, int oz, int from, int max) {
        int end = (int) Math.min(size(), (long) from + max);
        for (int n = from; n < end; n++) {
            int i = n * 5;
            int y = oy + blocks[i + 1];
            if (y >= 0 && y < 256) {
                world.getBlockAt(ox + blocks[i], y, oz + blocks[i + 2]).setTypeIdAndData(blocks[i + 3], (byte) blocks[i + 4], false);
            }
        }
        return end;
    }

    /** The blocks into the arena (built: kept from round to round). */
    void build(Arena arena) {
        for (int i = 0; i < blocks.length; i += 5) {
            Material type = material(blocks[i + 3]);
            if (type != null) {
                arena.build(blocks[i], blocks[i + 1], blocks[i + 2], type, blocks[i + 4]);
            }
        }
    }

    @SuppressWarnings("deprecation") // Material#getMaterial(int): the 1.8 block ids
    private static Material material(int id) {
        return Material.getMaterial(id);
    }

    static boolean solidMechanism(int id) {
        return contains(SOLID_MECHANISMS, id);
    }

    static boolean looseMechanism(int id) {
        return contains(LOOSE_MECHANISMS, id);
    }

    private static boolean contains(int[] ids, int id) {
        for (int each : ids) {
            if (each == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * What a block of the map becomes: itself, air for a loose mechanism, and for a solid one the
     * block most of its neighbours are (air when they are all air or mechanisms). Ids and data are
     * packed as id * 16 + data.
     */
    static int cleaned(int block, int[] neighbours) {
        int id = block >> 4;
        if (looseMechanism(id)) {
            return 0;
        }
        if (!solidMechanism(id)) {
            return block;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        int best = 0;
        int bestCount = 0;
        for (int neighbour : neighbours) {
            int nid = neighbour >> 4;
            if (nid == 0 || solidMechanism(nid) || looseMechanism(nid)) {
                continue;
            }
            int count = counts.merge(neighbour, 1, Integer::sum);
            if (count > bestCount) {
                best = neighbour;
                bestCount = count;
            }
        }
        return best;
    }

    /**
     * Reads the box (corners included) of the world with this name, if the server has it; the
     * origin lands on the centre of the zone. Null when the world is not there, with a word in the
     * log about what plays without it. A world the plugin loads for this goes again once read.
     */
    static MapCopy read(String worldName, int[] box, int[] origin, Logger log, String what) {
        World world = Bukkit.getWorld(worldName);
        boolean loaded = false;
        if (world == null) {
            File folder = new File(Bukkit.getWorldContainer(), worldName);
            if (!new File(folder, "level.dat").isFile()) {
                log.info("No world \"" + worldName + "\" in the folder of the worlds: " + what + " plays without it.");
                return null;
            }
            world = new WorldCreator(worldName).generator(new VoidGenerator()).generateStructures(false).createWorld();
            loaded = true;
        }
        int x1 = Math.min(box[0], box[3]);
        int y1 = Math.max(0, Math.min(box[1], box[4]));
        int z1 = Math.min(box[2], box[5]);
        int x2 = Math.max(box[0], box[3]);
        int y2 = Math.min(255, Math.max(box[1], box[4]));
        int z2 = Math.max(box[2], box[5]);
        int sx = x2 - x1 + 1;
        int sy = y2 - y1 + 1;
        int sz = z2 - z1 + 1;
        int[] raw = new int[sx * sy * sz];
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    raw[(x * sy + y) * sz + z] = packed(world.getBlockAt(x1 + x, y1 + y, z1 + z));
                }
            }
        }
        if (loaded) {
            Bukkit.unloadWorld(world, false);
        }
        int[] blocks = new int[raw.length * 5];
        int n = 0;
        int[] neighbours = new int[6];
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    int block = raw[(x * sy + y) * sz + z];
                    if (block == 0) {
                        continue;
                    }
                    neighbours[0] = at(raw, sx, sy, sz, x - 1, y, z);
                    neighbours[1] = at(raw, sx, sy, sz, x + 1, y, z);
                    neighbours[2] = at(raw, sx, sy, sz, x, y - 1, z);
                    neighbours[3] = at(raw, sx, sy, sz, x, y + 1, z);
                    neighbours[4] = at(raw, sx, sy, sz, x, y, z - 1);
                    neighbours[5] = at(raw, sx, sy, sz, x, y, z + 1);
                    int kept = cleaned(block, neighbours);
                    if (kept == 0) {
                        continue;
                    }
                    blocks[n++] = x1 + x - origin[0];
                    blocks[n++] = y1 + y - origin[1];
                    blocks[n++] = z1 + z - origin[2];
                    blocks[n++] = kept >> 4;
                    blocks[n++] = kept & 15;
                }
            }
        }
        MapCopy copy = new MapCopy(Arrays.copyOf(blocks, n));
        log.info(Character.toUpperCase(what.charAt(0)) + what.substring(1) + " plays on the map \"" + worldName + "\": " + copy.size() + " blocks copied.");
        return copy;
    }

    private static int at(int[] raw, int sx, int sy, int sz, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
            return 0;
        }
        return raw[(x * sy + y) * sz + z];
    }

    @SuppressWarnings("deprecation") // Block#getTypeId and getData: the 1.8 block id and data
    private static int packed(Block block) {
        return block.getTypeId() << 4 | (block.getData() & 15);
    }

    /** Leaves out every block of these places (offsets from the origin): the parts each round puts back. */
    MapCopy without(Iterable<int[]> places) {
        Set<Long> out = new HashSet<>();
        for (int[] place : places) {
            out.add(key(place[0], place[1], place[2]));
        }
        int[] kept = new int[blocks.length];
        int n = 0;
        for (int i = 0; i < blocks.length; i += 5) {
            if (!out.contains(key(blocks[i], blocks[i + 1], blocks[i + 2]))) {
                System.arraycopy(blocks, i, kept, n, 5);
                n += 5;
            }
        }
        return new MapCopy(Arrays.copyOf(kept, n));
    }

    /**
     * The markers the owner put on the map (blocks of these ids: the gold where a player starts...)
     * become wool, of the colour of a wool block next to them (white when there is none): the map
     * plays as it looks.
     */
    MapCopy markersAsWool(int... ids) {
        Map<Long, Integer> wool = new HashMap<>();
        for (int i = 0; i < blocks.length; i += 5) {
            if (blocks[i + 3] == WOOL_ID) {
                wool.put(key(blocks[i], blocks[i + 1], blocks[i + 2]), blocks[i + 4]);
            }
        }
        int[] changed = blocks.clone();
        for (int i = 0; i < changed.length; i += 5) {
            if (!contains(ids, changed[i + 3])) {
                continue;
            }
            int colour = 0;
            search:
            for (int dy : new int[] {0, -1, 1}) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Integer data = wool.get(key(changed[i] + dx, changed[i + 1] + dy, changed[i + 2] + dz));
                        if (data != null) {
                            colour = data;
                            break search;
                        }
                    }
                }
            }
            changed[i + 3] = WOOL_ID;
            changed[i + 4] = colour;
        }
        return new MapCopy(changed);
    }

    /** The id and data of the block at this place of the copy, packed as id * 16 + data; 0 for air. */
    int blockAt(int x, int y, int z) {
        for (int i = 0; i < blocks.length; i += 5) {
            if (blocks[i] == x && blocks[i + 1] == y && blocks[i + 2] == z) {
                return blocks[i + 3] << 4 | blocks[i + 4];
            }
        }
        return 0;
    }

    /** Where the copy holds blocks of this id: x, y, z from the origin each. */
    List<int[]> find(int id) {
        List<int[]> places = new ArrayList<>();
        for (int i = 0; i < blocks.length; i += 5) {
            if (blocks[i + 3] == id) {
                places.add(new int[] {blocks[i], blocks[i + 1], blocks[i + 2]});
            }
        }
        return places;
    }

    /** The blocks of these ids: x, y, z from the origin, id, data each. */
    List<int[]> blocksOf(int... ids) {
        List<int[]> found = new ArrayList<>();
        for (int i = 0; i < blocks.length; i += 5) {
            if (contains(ids, blocks[i + 3])) {
                found.add(Arrays.copyOfRange(blocks, i, i + 5));
            }
        }
        return found;
    }

    /** True if the copy holds a block of this id at this place. */
    boolean has(int x, int y, int z, int id) {
        for (int i = 0; i < blocks.length; i += 5) {
            if (blocks[i] == x && blocks[i + 1] == y && blocks[i + 2] == z && blocks[i + 3] == id) {
                return true;
            }
        }
        return false;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    /** A copy of these blocks, for the tests: x, y, z, id, data each. */
    static MapCopy of(int... blocks) {
        return new MapCopy(blocks.clone());
    }
}
