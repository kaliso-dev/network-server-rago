package gg.rago.lobby;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * A billboard: an image on a wall of item frames, a map in each, as on Hypixel. The frames exist
 * only in the packets of the players who see them ({@link FakeEntities}), from {@link #RANGE}
 * blocks away; the colours of their maps come from {@link Billboards}, which sends each player
 * each map once. A frame holding a map shows no border: the maps side by side make one image.
 * The client hangs a frame where it is told, with or without a wall behind it.
 */
final class FakeBillboard extends FakeEntities.Fake {

    /** Seen from this far, farther than the other fakes: the boards are made to be seen from afar. */
    static final double RANGE = 128;
    /** 1.8 entity metadata of an item frame: the item it holds. */
    private static final int ITEM = 8;
    private static final Optional<Vector3d> STILL = Optional.of(new Vector3d(0, 0, 0));

    private final Billboards maps;
    private final int[][] blocks;
    private final int face;
    private final int[] mapIds;
    private final ItemStack[] items;

    /**
     * @param block the block of the middle of its bottom row
     * @param face the way it faces ({@link Billboard#SOUTH}...)
     * @param mapIds the map of each block, row by row from the top left as seen from in front
     */
    FakeBillboard(World world, int[] block, int face, int columns, int rows, int[] mapIds, Billboards maps) {
        super(new Location(world, block[0] + 0.5, block[1] + rows / 2.0, block[2] + 0.5), columns * rows, null);
        this.maps = maps;
        this.blocks = Billboard.blocks(block[0], block[1], block[2], face, columns, rows);
        this.face = face;
        this.mapIds = mapIds.clone();
        this.items = new ItemStack[mapIds.length];
        for (int i = 0; i < mapIds.length; i++) {
            items[i] = SpigotConversionUtil.fromBukkitItemStack(new org.bukkit.inventory.ItemStack(Material.MAP, 1,
                    (short) mapIds[i]));
        }
    }

    @Override
    double range() {
        return RANGE;
    }

    @Override
    void spawn(FakeEntities fakes, Player viewer) {
        for (int i = 0; i < blocks.length; i++) {
            int[] block = blocks[i];
            // an item frame (object 71): its block, its facing as data and as yaw, still
            fakes.send(viewer, new WrapperPlayServerSpawnEntity(id(i), Optional.<UUID>empty(), EntityTypes.ITEM_FRAME,
                    new Vector3d(block[0] + 0.5, block[1] + 0.5, block[2] + 0.5), 0f, face * 90f, 0f, face, STILL));
            List<EntityData<?>> metadata = Collections.<EntityData<?>>singletonList(
                    new EntityData<>(ITEM, EntityDataTypes.ITEMSTACK, items[i]));
            fakes.send(viewer, new WrapperPlayServerEntityMetadata(id(i), metadata));
        }
        maps.send(viewer, mapIds);
    }
}
