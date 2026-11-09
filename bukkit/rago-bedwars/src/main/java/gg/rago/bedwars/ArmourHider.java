package gg.rago.bedwars;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The armour of an invisible player, hidden from the others as on Hypixel, where an invisibility
 * potion makes a player wholly invisible. The server keeps the armour on (its protection counts);
 * only the other players' clients are told there is none. 1.8 has no API for it: the server's own
 * equipment packet, found by reflection, as on any 1.8 server (v1_8_R3). If the server has none of
 * it, the armour shows, and it is said once in the log. Main thread only.
 */
final class ArmourHider {

    private static boolean failed;
    private static Constructor<?> equipment;
    private static Method handle;
    private static Field connection;
    private static Method send;
    private static Method copy;

    private ArmourHider() {
    }

    /**
     * Tells the viewer what this player wears: nothing while hidden, else their armour. The slots of
     * the packet: 1 the boots, 2 the leggings, 3 the chestplate, 4 the helmet, as Bukkit orders them.
     */
    static void show(Player viewer, Player player, boolean hidden, Logger logger) {
        if (!ready(logger)) {
            return;
        }
        try {
            Object to = connection.get(handle.invoke(viewer));
            ItemStack[] armour = player.getInventory().getArmorContents();
            for (int slot = 1; slot <= 4; slot++) {
                Object item = hidden || armour[slot - 1] == null ? null : copy.invoke(null, armour[slot - 1]);
                send.invoke(to, equipment.newInstance(player.getEntityId(), slot, item));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            logger.log(Level.WARNING, "Cannot hide the armour of invisible players any more: it shows", e);
        }
    }

    private static boolean ready(Logger logger) {
        if (failed) {
            return false;
        }
        if (equipment != null) {
            return true;
        }
        try {
            // org.bukkit.craftbukkit.v1_8_R3: its version is that of the server's own classes
            String craft = Bukkit.getServer().getClass().getPackage().getName();
            String nms = "net.minecraft.server." + craft.substring(craft.lastIndexOf('.') + 1) + ".";
            Class<?> item = Class.forName(nms + "ItemStack");
            handle = Class.forName(craft + ".entity.CraftPlayer").getMethod("getHandle");
            connection = Class.forName(nms + "EntityPlayer").getField("playerConnection");
            send = Class.forName(nms + "PlayerConnection").getMethod("sendPacket", Class.forName(nms + "Packet"));
            copy = Class.forName(craft + ".inventory.CraftItemStack").getMethod("asNMSCopy", ItemStack.class);
            equipment = Class.forName(nms + "PacketPlayOutEntityEquipment").getConstructor(int.class, int.class, item);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            logger.log(Level.WARNING, "Cannot hide the armour of invisible players on this server: it shows", e);
            return false;
        }
    }
}
