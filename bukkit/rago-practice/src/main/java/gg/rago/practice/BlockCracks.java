package gg.rago.practice;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * The cracks of a block being broken, shown on a placed block as it gets old: 1.8 has no API for
 * them, so the server's own packet (block break animation), found by reflection as on any 1.8
 * server (v1_8_R3). Each block has its own number, the one of the breaker the client knows the
 * cracks by. If the server has none of it, the blocks just go without cracks, and it is said once
 * in the log. Main thread only.
 */
final class BlockCracks {

    /** The stages of the cracks: 0 to 9; any other number takes them off. */
    static final int STAGES = 10;
    /** The numbers of the cracks: negative, so that no entity of the server has one of them. */
    private static int next = -1_000_000;

    private static boolean failed;
    private static Constructor<?> animation;
    private static Constructor<?> position;
    private static Method handle;
    private static Field connection;
    private static Method send;

    private BlockCracks() {
    }

    /** A new number for the cracks of a block. */
    static int newId() {
        next = next <= Integer.MIN_VALUE + 1 ? -1_000_000 : next - 1;
        return next;
    }

    /** The stage of the cracks of a block this old, out of a life of this long: 0 at first, 9 at the end. */
    static int stage(long age, long life) {
        if (life <= 0 || age <= 0) {
            return 0;
        }
        return (int) Math.min(STAGES - 1, age * STAGES / life);
    }

    /** Shows the cracks of this stage (-1: none) on the block to these players. */
    static void show(Iterable<Player> viewers, int id, Block block, int stage, Logger logger) {
        if (!ready(logger)) {
            return;
        }
        try {
            Object packet = animation.newInstance(id, position.newInstance(block.getX(), block.getY(), block.getZ()),
                    stage < 0 ? -1 : stage);
            for (Player viewer : viewers) {
                if (viewer.getWorld().equals(block.getWorld())) {
                    send.invoke(connection.get(handle.invoke(viewer)), packet);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            logger.log(Level.WARNING, "Cannot show the cracks of the blocks any more: they go without", e);
        }
    }

    private static boolean ready(Logger logger) {
        if (failed) {
            return false;
        }
        if (animation != null) {
            return true;
        }
        try {
            // org.bukkit.craftbukkit.v1_8_R3: its version is that of the server's own classes
            String craft = Bukkit.getServer().getClass().getPackage().getName();
            String nms = "net.minecraft.server." + craft.substring(craft.lastIndexOf('.') + 1) + ".";
            Class<?> blockPosition = Class.forName(nms + "BlockPosition");
            position = blockPosition.getConstructor(int.class, int.class, int.class);
            handle = Class.forName(craft + ".entity.CraftPlayer").getMethod("getHandle");
            connection = Class.forName(nms + "EntityPlayer").getField("playerConnection");
            send = Class.forName(nms + "PlayerConnection").getMethod("sendPacket", Class.forName(nms + "Packet"));
            animation = Class.forName(nms + "PacketPlayOutBlockBreakAnimation").getConstructor(int.class, blockPosition, int.class);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            logger.log(Level.WARNING, "Cannot show the cracks of the blocks on this server: they go without", e);
            return false;
        }
    }
}
