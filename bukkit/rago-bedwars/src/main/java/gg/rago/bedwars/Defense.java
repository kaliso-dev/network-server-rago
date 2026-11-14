package gg.rago.bedwars;

import gg.rago.game.BedDefense;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * A bed defense built by the game, as players build theirs: layer after layer around the bed, the
 * first layer touching it (the 8 blocks around and on top), each next one around the last (26
 * blocks for two layers, 58 for three). Its blocks are marked as placed, so that players can break
 * them and the copy of the map loses them after the game; {@link #repair} puts back the ones
 * broken. Main thread only.
 */
final class Defense {

    /** What a defense of the map on a bed is made of: the blocks of a game, which players may break. */
    private static final Set<Material> MAP_DEFENSE = EnumSet.of(Material.WOOL, Material.ENDER_STONE, Material.WOOD,
            Material.STAINED_GLASS, Material.GLASS, Material.STAINED_CLAY, Material.HARD_CLAY, Material.OBSIDIAN,
            Material.LADDER);
    /** How far from the bed, on each axis, a defense of the map goes at most. */
    static final int REACH = 4;
    private static final BlockFace[] FACES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
        BlockFace.UP, BlockFace.DOWN};

    /** What a defense block can replace: air, and what grows on the island. */
    private static final Set<Material> REPLACEABLE = EnumSet.of(Material.AIR, Material.LONG_GRASS,
            Material.YELLOW_FLOWER, Material.RED_ROSE, Material.DOUBLE_PLANT, Material.DEAD_BUSH, Material.SNOW);

    /** One block of the defense: where, what, and what the map had there. */
    private static final class Piece {
        final Block block;
        final Material type;
        final short data;
        final int mapId;
        final byte mapData;

        Piece(Block block, Material type, short data, int mapId, byte mapData) {
            this.block = block;
            this.type = type;
            this.data = data;
            this.mapId = mapId;
            this.mapData = mapData;
        }
    }

    private final Instance instance;
    private final List<Block> bed;
    private final List<Piece> pieces = new ArrayList<>();

    private Defense(Instance instance, List<Block> bed) {
        this.instance = instance;
        this.bed = bed;
    }

    /**
     * The places of each layer around a bed whose foot is at (x, y, z) and head one block towards
     * (hx, hz): layer n holds the places at n blocks from the nearer half (along the axes, as a
     * player counts them), at the bed's height or above.
     */
    static List<List<int[]>> layers(int x, int y, int z, int hx, int hz, int count) {
        List<List<int[]>> layers = new ArrayList<>();
        for (int layer = 1; layer <= count; layer++) {
            List<int[]> places = new ArrayList<>();
            for (int ox = -layer - 1; ox <= layer + 1; ox++) {
                for (int oz = -layer - 1; oz <= layer + 1; oz++) {
                    for (int oy = 0; oy <= layer; oy++) {
                        int foot = Math.abs(ox) + oy + Math.abs(oz);
                        int head = Math.abs(ox - hx) + oy + Math.abs(oz - hz);
                        if (Math.min(foot, head) == layer) {
                            places.add(new int[] {x + ox, y + oy, z + oz});
                        }
                    }
                }
            }
            layers.add(places);
        }
        return layers;
    }

    /**
     * Builds a defense around the bed this block is part of, one material a layer from the inside
     * out (the colour given for wool, clay and glass), on the island only: no block over the void,
     * none in place of a block of the map. Null if the block is not a bed.
     */
    static Defense build(Instance instance, Block bedBlock, Material[] types, short colour) {
        List<Block> halves = Instance.bedOf(bedBlock);
        if (halves.isEmpty()) {
            return null;
        }
        Defense defense = new Defense(instance, halves);
        Block foot = halves.get(0);
        int hx = halves.size() > 1 ? halves.get(1).getX() - foot.getX() : 0;
        int hz = halves.size() > 1 ? halves.get(1).getZ() - foot.getZ() : 0;
        List<List<int[]>> layers = layers(foot.getX(), foot.getY(), foot.getZ(), hx, hz, types.length);
        for (int i = 0; i < layers.size(); i++) {
            Material type = types[i];
            short data = type == Material.WOOL || type == Material.STAINED_CLAY || type == Material.STAINED_GLASS ? colour : 0;
            for (int[] place : layers.get(i)) {
                defense.add(foot, place[0], place[1], place[2], type, data);
            }
        }
        return defense;
    }

    /**
     * Builds a defense of the owner's around the bed this block is part of, from a template of
     * {@link BedDefense} (end stone, and wool of this colour), on the island only. Null if the
     * block is not a bed.
     */
    static Defense build(Instance instance, Block bedBlock, List<String[]> template, short colour) {
        return build(instance, bedBlock, template, colour, null);
    }

    /**
     * The same defense as the attacker who broke the bed left it: with their hole through it, from
     * its side the most towards (towardX, towardZ) to the bed (see {@link #broken}). The bed is
     * still there: the game takes it away after.
     */
    static Defense buildBroken(Instance instance, Block bedBlock, List<String[]> template, short colour, double towardX,
            double towardZ) {
        return build(instance, bedBlock, template, colour, new double[] {towardX, towardZ});
    }

    /**
     * The places of a template around a bed (as {@link BedDefense#places}: the foot at 0, the head
     * a block towards (hx, hz)) less the hole of the attacker who broke the bed: one block wide and
     * two high, at the bed's height and the one over it, straight through the defense from the
     * side the most towards (towardX, towardZ) to the half of the bed on that side. The blocks
     * over the bed stay.
     */
    static List<int[]> broken(List<int[]> places, int hx, int hz, double towardX, double towardZ) {
        int dx = 0;
        int dz = 0;
        if (Math.abs(towardX) >= Math.abs(towardZ)) {
            dx = towardX >= 0 ? 1 : -1;
        } else {
            dz = towardZ >= 0 ? 1 : -1;
        }
        boolean head = hx * towardX + hz * towardZ > 0;
        int fromX = head ? hx : 0;
        int fromZ = head ? hz : 0;
        List<int[]> left = new ArrayList<>();
        for (int[] place : places) {
            int ox = place[0] - fromX;
            int oz = place[2] - fromZ;
            boolean dug = place[1] <= 1 && (dx != 0 ? oz == 0 && ox * dx > 0 : ox == 0 && oz * dz > 0);
            if (!dug) {
                left.add(place);
            }
        }
        return left;
    }

    private static Defense build(Instance instance, Block bedBlock, List<String[]> template, short colour, double[] hole) {
        List<Block> halves = Instance.bedOf(bedBlock);
        if (halves.isEmpty()) {
            return null;
        }
        Defense defense = new Defense(instance, halves);
        Block foot = halves.get(0);
        int hx = halves.size() > 1 ? halves.get(1).getX() - foot.getX() : 0;
        int hz = halves.size() > 1 ? halves.get(1).getZ() - foot.getZ() : 1;
        List<int[]> places = BedDefense.places(template, hx, hz);
        if (hole != null) {
            places = broken(places, hx, hz, hole[0], hole[1]);
        }
        for (int[] place : places) {
            boolean wool = place[3] == BedDefense.WOOL;
            defense.add(foot, foot.getX() + place[0], foot.getY() + place[1], foot.getZ() + place[2],
                    wool ? Material.WOOL : Material.ENDER_STONE, wool ? colour : 0);
        }
        return defense;
    }

    /**
     * One block of the defense, where the map has nothing (or grass) and the island a floor under
     * it at the bed's height.
     */
    @SuppressWarnings("deprecation") // Block#getTypeId, getData: the 1.8 block id and data
    private void add(Block foot, int x, int y, int z, Material type, short data) {
        if (y < 1 || y > 255) {
            return;
        }
        World world = foot.getWorld();
        Block block = world.getBlockAt(x, y, z);
        if (!REPLACEABLE.contains(block.getType()) || instance.isPlaced(block)
                || !world.getBlockAt(x, foot.getY() - 1, z).getType().isSolid()) {
            return;
        }
        Piece piece = new Piece(block, type, data, block.getTypeId(), block.getData());
        pieces.add(piece);
        put(piece);
    }

    @SuppressWarnings("deprecation") // Block#setTypeIdAndData: the 1.8 way to set a block with its data
    private void put(Piece piece) {
        instance.markPlaced(piece.block, piece.mapId, piece.mapData);
        piece.block.setTypeIdAndData(piece.type.getId(), (byte) piece.data, false);
    }

    /** The broken blocks of the defense come back, where nothing else stands now. How many came back. */
    int repair() {
        int put = 0;
        for (Piece piece : pieces) {
            if (REPLACEABLE.contains(piece.block.getType()) && !instance.isPlaced(piece.block)) {
                put(piece);
                put++;
            }
        }
        return put;
    }

    /**
     * The defense the map itself has on a bed (built with the map, as on Bio Hazard or a bed fight
     * map): the blocks of a game (wool, end stone, wood, glass, clay, obsidian, ladders) joined to
     * the bed, from its height up and within {@value #REACH} blocks of it, become as if placed by a
     * player: breakable, and the rollback puts them back. How many; 0 where no bed stands.
     */
    static int free(Instance instance, Block bedBlock) {
        List<Block> bed = Instance.bedOf(bedBlock);
        if (bed.isEmpty()) {
            return 0;
        }
        Deque<Block> todo = new ArrayDeque<>(bed);
        Set<Block> seen = new HashSet<>(bed);
        int freed = 0;
        while (!todo.isEmpty()) {
            Block block = todo.poll();
            for (BlockFace face : FACES) {
                Block next = block.getRelative(face);
                if (!MAP_DEFENSE.contains(next.getType()) || !nearBed(next, bed) || !seen.add(next)) {
                    continue;
                }
                if (!instance.isPlaced(next)) {
                    instance.markPlaced(next);
                    freed++;
                }
                todo.add(next);
            }
        }
        return freed;
    }

    /** From the bed's height up, within {@value #REACH} blocks of one of its halves on each axis. */
    private static boolean nearBed(Block block, List<Block> bed) {
        for (Block half : bed) {
            if (block.getY() >= half.getY() && block.getY() - half.getY() <= REACH
                    && Math.abs(block.getX() - half.getX()) <= REACH && Math.abs(block.getZ() - half.getZ()) <= REACH) {
                return true;
            }
        }
        return false;
    }

    /** The bed it defends: its two halves. */
    List<Block> bed() {
        return bed;
    }

    /** How many blocks the defense has. */
    int size() {
        return pieces.size();
    }
}
