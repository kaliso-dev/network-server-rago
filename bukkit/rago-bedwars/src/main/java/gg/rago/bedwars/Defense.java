package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * A bed defense built by the game, as players build theirs: layer after layer around the bed, the
 * first layer touching it (the 8 blocks around and on top), each next one around the last (26
 * blocks for two layers, 58 for three). Its blocks are marked as placed, so that players can break
 * them and the copy of the map loses them after the game; {@link #repair} puts back the ones
 * broken. Main thread only.
 */
final class Defense {

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
    @SuppressWarnings("deprecation") // Block#getTypeId, getData, setTypeIdAndData: the 1.8 block id and data
    static Defense build(Instance instance, Block bedBlock, Material[] types, short colour) {
        List<Block> halves = Instance.bedOf(bedBlock);
        if (halves.isEmpty()) {
            return null;
        }
        Defense defense = new Defense(instance, halves);
        Block foot = halves.get(0);
        int hx = halves.size() > 1 ? halves.get(1).getX() - foot.getX() : 0;
        int hz = halves.size() > 1 ? halves.get(1).getZ() - foot.getZ() : 0;
        World world = foot.getWorld();
        List<List<int[]>> layers = layers(foot.getX(), foot.getY(), foot.getZ(), hx, hz, types.length);
        for (int i = 0; i < layers.size(); i++) {
            Material type = types[i];
            short data = type == Material.WOOL || type == Material.STAINED_CLAY || type == Material.STAINED_GLASS ? colour : 0;
            for (int[] place : layers.get(i)) {
                if (place[1] < 1 || place[1] > 255) {
                    continue;
                }
                Block block = world.getBlockAt(place[0], place[1], place[2]);
                // on the island: the floor under the place, at the bed's height
                if (!REPLACEABLE.contains(block.getType()) || instance.isPlaced(block)
                        || !world.getBlockAt(place[0], foot.getY() - 1, place[2]).getType().isSolid()) {
                    continue;
                }
                Piece piece = new Piece(block, type, data, block.getTypeId(), block.getData());
                defense.pieces.add(piece);
                defense.put(piece);
            }
        }
        return defense;
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

    /** The bed it defends: its two halves. */
    List<Block> bed() {
        return bed;
    }

    /** How many blocks the defense has. */
    int size() {
        return pieces.size();
    }
}
