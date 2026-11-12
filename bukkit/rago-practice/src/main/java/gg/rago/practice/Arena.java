package gg.rago.practice;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

/**
 * The zone of one game in the arena world: its centre, the blocks the mode builds there once (the
 * platform, the islands, the bed...), and every block changed since, so that a round starts again
 * on the arena as it was built. Main thread only.
 */
final class Arena {

    /** A block as it was before the players touched it. */
    private static final class Before {
        final int id;
        final byte data;

        Before(int id, byte data) {
            this.id = id;
            this.data = data;
        }
    }

    private final World world;
    private final int slot;
    private final int x;
    private final int y;
    private final int z;
    private final boolean fresh;
    private final Map<Block, Before> changed = new LinkedHashMap<>();
    private final Set<Block> placed = new HashSet<>();
    /** The map copied into the zone, or null when the mode builds its own arena. */
    private MapZones.ZoneMap map;
    /** The blocks the mode builds while the zone is fresh, for the replays (five numbers each); null: not kept. */
    private int[] design;
    private int designLength;

    /**
     * @param fresh true the first time this zone is used by its mode: the mode builds it then
     */
    Arena(World world, int slot, int x, int y, int z, boolean fresh) {
        this.world = world;
        this.slot = slot;
        this.x = x;
        this.y = y;
        this.z = z;
        this.fresh = fresh;
    }

    World world() {
        return world;
    }

    /** The map copied into the zone, or null when the mode builds its own arena. */
    MapZones.ZoneMap map() {
        return map;
    }

    void onMap(MapZones.ZoneMap map) {
        this.map = map;
    }

    /** Where player number n starts on the map of the zone, or null without a map that says. */
    Location mapStart(int n) {
        if (map == null || map.starts.length == 0) {
            return null;
        }
        double[] start = map.starts[n % map.starts.length];
        return at(start[0], start[1], start[2], (float) start[3]);
    }

    int slot() {
        return slot;
    }

    /** True the first time the zone is used: its blocks are not built yet. */
    boolean fresh() {
        return fresh;
    }

    /** The y of the floor of the arena: players stand on y + 1. */
    int floorY() {
        return y;
    }

    int centreX() {
        return x;
    }

    int centreZ() {
        return z;
    }

    /** The point at this offset from the centre block (x, floor y, z). */
    Location at(double dx, double dy, double dz) {
        return new Location(world, x + dx, y + dy, z + dz);
    }

    /** The point at this offset, looking this way. */
    Location at(double dx, double dy, double dz, float yaw) {
        Location location = at(dx, dy, dz);
        location.setYaw(yaw);
        location.setPitch(0f);
        return location;
    }

    Block block(int dx, int dy, int dz) {
        return world.getBlockAt(x + dx, y + dy, z + dz);
    }

    /** The offset of this location from the centre, as {x, y, z}. */
    double[] offset(Location location) {
        return new double[] {location.getX() - x, location.getY() - y, location.getZ() - z};
    }

    /** Builds a block of the arena itself (kept from game to game, not undone by {@link #restore}). */
    @SuppressWarnings("deprecation") // Block#setTypeIdAndData: the 1.8 way to set a block with its data
    void build(int dx, int dy, int dz, Material type, int data) {
        block(dx, dy, dz).setTypeIdAndData(type.getId(), (byte) data, false);
        if (design != null) {
            if (designLength + 5 > design.length) {
                design = Arrays.copyOf(design, Math.max(1024, design.length * 2));
            }
            design[designLength++] = dx;
            design[designLength++] = dy;
            design[designLength++] = dz;
            design[designLength++] = type.getId();
            design[designLength++] = data;
        }
    }

    /** The blocks the mode builds from now on are kept, for the replays of the zone (see {@link #design}). */
    void keepDesign() {
        design = new int[1024];
        designLength = 0;
    }

    /** The blocks the mode built since {@link #keepDesign}: x, y, z from the centre, id, data each; null if not kept. */
    int[] design() {
        return design == null ? null : Arrays.copyOf(design, designLength);
    }

    /**
     * The arena as it is now, from a design (its blocks as built, five numbers each, from the
     * centre): the design, and over it the blocks changed since the last restore as they are now
     * (a bed of this round...). The design itself when nothing changed.
     */
    @SuppressWarnings("deprecation") // Block#getTypeId and getData: the 1.8 block id and data
    int[] now(int[] design) {
        if (changed.isEmpty()) {
            return design;
        }
        Map<Long, int[]> current = new HashMap<>();
        for (Block block : changed.keySet()) {
            int dx = block.getX() - x;
            int dy = block.getY() - y;
            int dz = block.getZ() - z;
            current.put(key(dx, dy, dz), new int[] {dx, dy, dz, block.getTypeId(), block.getData()});
        }
        int[] stage = new int[design.length + current.size() * 5];
        int length = 0;
        for (int i = 0; i + 4 < design.length; i += 5) {
            if (!current.containsKey(key(design[i], design[i + 1], design[i + 2]))) {
                System.arraycopy(design, i, stage, length, 5);
                length += 5;
            }
        }
        for (int[] block : current.values()) {
            if (block[3] != 0) {
                System.arraycopy(block, 0, stage, length, 5);
                length += 5;
            }
        }
        return Arrays.copyOf(stage, length);
    }

    private static long key(int dx, int dy, int dz) {
        return ((long) dx << 40) ^ (((long) dz & 0xFFFFFL) << 12) ^ (dy & 0xFFFL);
    }

    void build(int dx, int dy, int dz, Material type) {
        build(dx, dy, dz, type, 0);
    }

    /** A box of blocks of the arena, corners included. */
    void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material type, int data) {
        for (int bx = Math.min(x1, x2); bx <= Math.max(x1, x2); bx++) {
            for (int by = Math.min(y1, y2); by <= Math.max(y1, y2); by++) {
                for (int bz = Math.min(z1, z2); bz <= Math.max(z1, z2); bz++) {
                    build(bx, by, bz, type, data);
                }
            }
        }
    }

    /**
     * A block of this round only, as if a player had placed it: it can be broken and blown up, and
     * goes at the next restore (a bed of a bed fight, the defense of a fireball fight...).
     */
    @SuppressWarnings("deprecation") // Block#setTypeIdAndData
    void put(int dx, int dy, int dz, Material type, int data) {
        Block block = block(dx, dy, dz);
        touch(block);
        block.setTypeIdAndData(type.getId(), (byte) data, false);
        placed.add(block);
    }

    /**
     * A block of the map, as it is, now this round's as if a player had placed it: it can be
     * broken and blown up, and comes back at the next restore (the defense of a map's bed).
     */
    void free(Block block) {
        touch(block);
        placed.add(block);
    }

    /** Remembers the block as it is, before a player or the game changes it in a round. */
    @SuppressWarnings("deprecation") // Block#getTypeId and getData: the 1.8 block id and data
    void touch(Block block) {
        if (!changed.containsKey(block)) {
            changed.put(block, new Before(block.getTypeId(), block.getData()));
        }
    }

    /**
     * A player placed this block, over what the event says was there: it can be broken again, and
     * goes at the next restore. (When the place event comes, the world already holds the new block.)
     */
    @SuppressWarnings("deprecation") // BlockState#getTypeId and getRawData
    void placed(Block block, BlockState replaced) {
        if (!changed.containsKey(block)) {
            changed.put(block, new Before(replaced.getTypeId(), replaced.getRawData()));
        }
        placed.add(block);
    }

    boolean isPlaced(Block block) {
        return placed.contains(block);
    }

    /**
     * A block leaves the world (broken, blown up): it comes back at the next restore. The other
     * half of a bed goes with it.
     */
    void removed(Block block) {
        touch(block);
        placed.remove(block);
        Block other = otherHalf(block);
        if (other != null) {
            touch(other);
            placed.remove(other);
        }
    }

    /** The other half of a bed, or null if the block is not a bed. */
    @SuppressWarnings("deprecation") // Block#getData: the direction of the bed
    static Block otherHalf(Block block) {
        if (block.getType() != Material.BED_BLOCK) {
            return null;
        }
        int data = block.getData();
        int direction = data & 3;
        int sign = (data & 8) != 0 ? -1 : 1;
        int hx = direction == 1 ? -1 : direction == 3 ? 1 : 0;
        int hz = direction == 0 ? 1 : direction == 2 ? -1 : 0;
        Block other = block.getRelative(sign * hx, 0, sign * hz);
        return other.getType() == Material.BED_BLOCK ? other : null;
    }

    /** True if this block is in the box around the centre, this far horizontally and from below to above. */
    boolean contains(Block block, int radius, int below, int above) {
        return block.getWorld().equals(world) && Math.abs(block.getX() - x) <= radius && Math.abs(block.getZ() - z) <= radius
                && block.getY() >= y - below && block.getY() <= y + above;
    }

    /** True if this point is less than this far from the centre, horizontally. */
    boolean near(Location location, double distance) {
        if (!location.getWorld().equals(world)) {
            return false;
        }
        double dx = location.getX() - x;
        double dz = location.getZ() - z;
        return dx * dx + dz * dz <= distance * distance;
    }

    /** Every block changed since the last restore goes back to what it was. */
    @SuppressWarnings("deprecation") // Block#setTypeIdAndData
    void restore() {
        // the last changes first, so that a block changed twice ends as it was at the very start
        Block[] blocks = changed.keySet().toArray(new Block[0]);
        for (int i = blocks.length - 1; i >= 0; i--) {
            Before before = changed.get(blocks[i]);
            blocks[i].setTypeIdAndData(before.id, before.data, false);
        }
        changed.clear();
        placed.clear();
    }

    /** One block back to what it was before this round (a placed block that decays). */
    @SuppressWarnings("deprecation") // Block#setTypeIdAndData
    void restore(Block block) {
        Before before = changed.remove(block);
        placed.remove(block);
        if (before != null) {
            block.setTypeIdAndData(before.id, before.data, false);
        }
    }

    /** How many blocks changed since the last restore. */
    int changes() {
        return changed.size();
    }
}
