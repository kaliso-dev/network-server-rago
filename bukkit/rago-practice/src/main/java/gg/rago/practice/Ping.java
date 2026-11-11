package gg.rago.practice;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/** The ping of a player (the 1.8 API has none: the server's own field), for the sidebars. */
final class Ping {

    private static Method getHandle;
    private static Field ping;
    private static boolean broken;

    private Ping() {
    }

    /** The player's ping in milliseconds, -1 if unknown. */
    static int of(Player player) {
        if (broken) {
            return -1;
        }
        try {
            if (getHandle == null) {
                getHandle = player.getClass().getMethod("getHandle");
            }
            Object handle = getHandle.invoke(player);
            if (ping == null) {
                ping = handle.getClass().getField("ping");
            }
            return ping.getInt(handle);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            return -1;
        }
    }

    /** "42 ms" in green, yellow or red. */
    static String text(Player player) {
        int ms = player == null ? -1 : of(player);
        if (ms < 0) {
            return ChatColor.GRAY + "?";
        }
        ChatColor colour = ms < 80 ? ChatColor.GREEN : ms < 150 ? ChatColor.YELLOW : ChatColor.RED;
        return colour + "" + ms + " ms";
    }
}
