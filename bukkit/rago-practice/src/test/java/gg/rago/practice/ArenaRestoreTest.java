package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

/**
 * The restore of a zone: a block of the map a player clicked (a trapdoor, a fence gate, a door)
 * comes back as it was, the way a block placed or broken does. The world is a stand-in of the 1.8
 * blocks (ids and data), as the arena reads and writes them.
 */
class ArenaRestoreTest {

    /** The blocks of a world: id and data at each place; air elsewhere. */
    private static final class Blocks {
        final Map<String, int[]> at = new HashMap<>();
        final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getBlockAt":
                            return block((Integer) args[0], (Integer) args[1], (Integer) args[2]);
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return "the test world";
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });

        void set(int x, int y, int z, Material type, int data) {
            at.put(x + "," + y + "," + z, new int[] {idOf(type), data});
        }

        int[] get(int x, int y, int z) {
            return at.getOrDefault(x + "," + y + "," + z, new int[] {0, 0});
        }

        Block block(int x, int y, int z) {
            return block(x, y, z, false);
        }

        /**
         * A block kept from an event, once the players left the zone: the server unloaded its
         * chunk, and a block set through it is lost (the chunk comes back from the disk as it was).
         */
        Block unloaded(int x, int y, int z) {
            return block(x, y, z, true);
        }

        private Block block(int x, int y, int z, boolean unloaded) {
            return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getX":
                                return x;
                            case "getY":
                                return y;
                            case "getZ":
                                return z;
                            case "getWorld":
                                return world;
                            case "getTypeId":
                                return get(x, y, z)[0];
                            case "getData":
                                return (byte) get(x, y, z)[1];
                            case "getType":
                                return materialOf(get(x, y, z)[0]);
                            case "setTypeIdAndData":
                                if (unloaded) {
                                    return false;
                                }
                                at.put(x + "," + y + "," + z, new int[] {(Integer) args[0], (Byte) args[1]});
                                return true;
                            case "getRelative":
                                return block(x + (Integer) args[0], y + (Integer) args[1], z + (Integer) args[2]);
                            case "equals":
                                return args[0] instanceof Block && ((Block) args[0]).getX() == x && ((Block) args[0]).getY() == y
                                        && ((Block) args[0]).getZ() == z;
                            case "hashCode":
                                return (y << 24) ^ x ^ (z << 8);
                            case "toString":
                                return "block " + x + " " + y + " " + z;
                            default:
                                throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }

    @SuppressWarnings("deprecation") // Material#getId and getMaterial(int): the 1.8 block ids
    private static int idOf(Material type) {
        return type.getId();
    }

    @SuppressWarnings("deprecation")
    private static Material materialOf(int id) {
        return Material.getMaterial(id);
    }

    private static Arena arena(Blocks blocks) {
        return new Arena(blocks.world, 0, 0, 64, 1800, false);
    }

    @Test
    void aTrapdoorOfTheMapOpenedByAClickIsClosedAgainAtTheRestore() {
        Blocks blocks = new Blocks();
        blocks.set(3, 65, 2, Material.TRAP_DOOR, 2);
        Arena arena = arena(blocks);
        Block trapdoor = blocks.block(3, 65, 2);
        arena.clicked(trapdoor);
        // the click opens it (bit 4), with no event of its own
        blocks.set(3, 65, 2, Material.TRAP_DOOR, 2 | 4);
        arena.restore();
        assertArrayEquals(new int[] {idOf(Material.TRAP_DOOR), 2}, blocks.get(3, 65, 2));
    }

    @Test
    void aFenceGateOpenedAndClosedSeveralTimesEndsAsItWasAtTheStart() {
        Blocks blocks = new Blocks();
        blocks.set(-4, 65, 0, Material.FENCE_GATE, 1);
        Arena arena = arena(blocks);
        Block gate = blocks.block(-4, 65, 0);
        arena.clicked(gate);
        blocks.set(-4, 65, 0, Material.FENCE_GATE, 1 | 4);
        arena.clicked(gate);
        blocks.set(-4, 65, 0, Material.FENCE_GATE, 3 | 4);
        arena.restore();
        assertArrayEquals(new int[] {idOf(Material.FENCE_GATE), 1}, blocks.get(-4, 65, 0));
    }

    @Test
    void aDoorClickedOnItsUpperHalfComesBackClosedWithItsLowerHalf() {
        Blocks blocks = new Blocks();
        blocks.set(0, 65, 5, Material.WOODEN_DOOR, 1);
        blocks.set(0, 66, 5, Material.WOODEN_DOOR, 8);
        Arena arena = arena(blocks);
        arena.clicked(blocks.block(0, 66, 5));
        // whether a door is open is in its lower half
        blocks.set(0, 65, 5, Material.WOODEN_DOOR, 1 | 4);
        arena.restore();
        assertArrayEquals(new int[] {idOf(Material.WOODEN_DOOR), 1}, blocks.get(0, 65, 5));
        assertArrayEquals(new int[] {idOf(Material.WOODEN_DOOR), 8}, blocks.get(0, 66, 5));
    }

    @Test
    void aBlockClickedToPlaceAnotherOnItStaysAndTheBlockPlacedGoes() {
        Blocks blocks = new Blocks();
        blocks.set(7, 64, 0, Material.SANDSTONE, 2);
        Arena arena = arena(blocks);
        Block floor = blocks.block(7, 64, 0);
        arena.clicked(floor);
        blocks.set(6, 64, 0, Material.WOOL, 11);
        arena.placed(blocks.block(6, 64, 0), new Before(0, 0).state(blocks.block(6, 64, 0)));
        arena.restore();
        assertArrayEquals(new int[] {idOf(Material.SANDSTONE), 2}, blocks.get(7, 64, 0));
        assertArrayEquals(new int[] {0, 0}, blocks.get(6, 64, 0));
        assertEquals(0, arena.changes());
    }

    @Test
    void aZoneItsPlayersLeftBeforeTheRestoreComesBackAsBuilt() {
        Blocks blocks = new Blocks();
        blocks.set(7, 64, 0, Material.SANDSTONE, 2);
        Arena arena = arena(blocks);
        // the blocks of the events, kept until the restore: Play again on the end screen, the
        // players are gone and the chunks of the zone with them
        Block wool = blocks.unloaded(6, 64, 0);
        blocks.set(6, 64, 0, Material.WOOL, 11);
        arena.placed(wool, new Before(0, 0).state(wool));
        Block floor = blocks.unloaded(7, 64, 0);
        arena.removed(floor);
        blocks.set(7, 64, 0, Material.AIR, 0);
        arena.restore();
        assertArrayEquals(new int[] {0, 0}, blocks.get(6, 64, 0));
        assertArrayEquals(new int[] {idOf(Material.SANDSTONE), 2}, blocks.get(7, 64, 0));
    }

    /** What the place event says was there before: a block state of this id and data. */
    private static final class Before {
        final int id;
        final int data;

        Before(int id, int data) {
            this.id = id;
            this.data = data;
        }

        org.bukkit.block.BlockState state(Block block) {
            return (org.bukkit.block.BlockState) Proxy.newProxyInstance(org.bukkit.block.BlockState.class.getClassLoader(),
                    new Class<?>[] {org.bukkit.block.BlockState.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getTypeId":
                                return id;
                            case "getRawData":
                                return (byte) data;
                            case "getBlock":
                                return block;
                            default:
                                throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }
}
