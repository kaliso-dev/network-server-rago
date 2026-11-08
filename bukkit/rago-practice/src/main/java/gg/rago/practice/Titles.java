package gg.rago.practice;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * Title timings. The 1.8.8 API sends a title with the client's default timings (half a second to
 * appear, three and a half seconds on screen), too slow for a countdown, and has no call to change
 * them: this sends the server's own timings packet. If the server's classes are not the expected
 * ones, the titles keep the default timings and a warning is logged once. Main thread only.
 */
final class Titles {

    private static boolean broken;

    private Titles() {
    }

    /** Sets how long the next titles take to appear, stay and fade out, in ticks. */
    static void times(Logger logger, Player player, int fadeIn, int stay, int fadeOut) {
        if (broken) {
            return;
        }
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = handle.getClass().getField("playerConnection").get(handle);
            String server = handle.getClass().getPackage().getName();
            Constructor<?> timings = Class.forName(server + ".PacketPlayOutTitle")
                    .getConstructor(int.class, int.class, int.class);
            Method send = connection.getClass().getMethod("sendPacket", Class.forName(server + ".Packet"));
            send.invoke(connection, timings.newInstance(fadeIn, stay, fadeOut));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            logger.warning("Titres : les durées ne peuvent pas être réglées sur ce serveur ("
                    + e.getClass().getSimpleName() + "), les titres gardent les durées par défaut.");
        }
    }
}
