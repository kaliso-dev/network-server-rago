package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.material.Bed;
import org.bukkit.material.MaterialData;

/**
 * A copy of a map, loaded as a world of its own, where one game is played at a time. Players can
 * only break the blocks placed during the game and the beds, and explosions only the placed blocks:
 * after a game, removing the placed blocks and putting the beds back gives the map as it was, and
 * the copy is ready for the next game. Main thread only.
 */
final class Instance {

    /** One half of a bed as the map has it. */
    private static final class BedHalf {
        final int x;
        final int y;
        final int z;
        final byte data;

        BedHalf(Block block) {
            this.x = block.getX();
            this.y = block.getY();
            this.z = block.getZ();
            this.data = dataOf(block);
        }
    }

    private final BedwarsMap map;
    private final World world;
    private final List<BedHalf> beds = new ArrayList<>();
    private final Set<Long> placed = new HashSet<>();
    /** What the placed blocks and the flowing water replaced, when it was not air: id << 4 | data. */
    private final Map<Long, Integer> replaced = new HashMap<>();
    private boolean busy;
    private boolean discarded;

    Instance(BedwarsMap map, World world) {
        this.map = map;
        this.world = world;
        for (BedwarsMap.Team team : map.teams().values()) {
            if (team.bed == null) {
                continue;
            }
            for (Block half : bedOf(team.bed.blockIn(world))) {
                beds.add(new BedHalf(half));
            }
        }
    }

    /** The map as it was when this copy was made. */
    BedwarsMap map() {
        return map;
    }

    World world() {
        return world;
    }

    boolean busy() {
        return busy;
    }

    void setBusy(boolean busy) {
        this.busy = busy;
    }

    /** True when the map changed since the copy: it is thrown away once its game is over. */
    boolean discarded() {
        return discarded;
    }

    void discard() {
        discarded = true;
    }

    /** A block position in one number: x and z on 26 bits each (signed), y on 12. */
    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static int xOf(long key) {
        return (int) (key >> 38);
    }

    static int yOf(long key) {
        return (int) (key & 0xFFF);
    }

    static int zOf(long key) {
        return (int) (key << 26 >> 38);
    }

    boolean isPlaced(Block block) {
        return placed.contains(key(block.getX(), block.getY(), block.getZ()));
    }

    /**
     * A block placed or flowed into during the game, over what the map had there (air, tall grass,
     * a flower...): the rollback puts that back.
     */
    void markPlaced(Block block, int replacedId, byte replacedData) {
        long key = key(block.getX(), block.getY(), block.getZ());
        if (placed.add(key) && replacedId != 0 && !replaced.containsKey(key)) {
            replaced.put(key, replacedId << 4 | (replacedData & 0xF));
        }
    }

    /** The same, before the block changes: what is there now is what it replaces. */
    @SuppressWarnings("deprecation") // Block#getTypeId: the 1.8 block id
    void markPlaced(Block block) {
        markPlaced(block, block.getTypeId(), dataOf(block));
    }

    void unmark(Block block) {
        placed.remove(key(block.getX(), block.getY(), block.getZ()));
    }

    /** A placed block goes: what the map had there comes back (air, tall grass...). */
    @SuppressWarnings("deprecation") // Block#setTypeIdAndData: the 1.8 way to set a block with its data
    void restore(Block block) {
        long key = key(block.getX(), block.getY(), block.getZ());
        if (!placed.remove(key)) {
            return;
        }
        Integer before = replaced.remove(key);
        if (before == null) {
            block.setType(Material.AIR, false);
        } else {
            block.setTypeIdAndData(before >> 4, (byte) (before & 0xF), false);
        }
    }

    /** The map as it was: no placed block, the beds back, no item or entity left. */
    @SuppressWarnings("deprecation") // Block#setTypeIdAndData: the 1.8 way to set a block with its data
    void rollback() {
        for (long key : placed) {
            world.getBlockAt(xOf(key), yOf(key), zOf(key)).setType(Material.AIR, false);
        }
        placed.clear();
        for (Map.Entry<Long, Integer> entry : replaced.entrySet()) {
            long key = entry.getKey();
            world.getBlockAt(xOf(key), yOf(key), zOf(key)).setTypeIdAndData(entry.getValue() >> 4,
                    (byte) (entry.getValue() & 0xF), false);
        }
        replaced.clear();
        for (BedHalf half : beds) {
            world.getBlockAt(half.x, half.y, half.z).setTypeIdAndData(Material.BED_BLOCK.getId(), half.data, false);
        }
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }

    /** The two halves of the bed this block is part of, or nothing if it is not a bed. */
    static List<Block> bedOf(Block block) {
        List<Block> halves = new ArrayList<>();
        if (block.getType() != Material.BED_BLOCK) {
            return halves;
        }
        halves.add(block);
        MaterialData data = block.getState().getData();
        if (data instanceof Bed) {
            Bed bed = (Bed) data;
            BlockFace towardsHead = bed.getFacing();
            Block other = bed.isHeadOfBed() ? block.getRelative(towardsHead.getOppositeFace()) : block.getRelative(towardsHead);
            if (other.getType() == Material.BED_BLOCK) {
                halves.add(other);
            }
        }
        return halves;
    }

    @SuppressWarnings("deprecation") // Block#getData: the 1.8 data value
    private static byte dataOf(Block block) {
        return block.getData();
    }
}
