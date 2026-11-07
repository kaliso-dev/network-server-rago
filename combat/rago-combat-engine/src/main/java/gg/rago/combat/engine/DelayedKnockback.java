package gg.rago.combat.engine;

import gg.rago.combat.model.Vec3;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * Sends a victim's knockback packet a few milliseconds late, for the ping simulation.
 *
 * <p>The caller cancels the victim's velocity event, so the server sends nothing. This class then
 * does what the server does right after sending a knockback (puts back the victim's motion of
 * before the hit and clears its "velocity changed" flag, so that the entity tracker sends nothing
 * either), and sends the same velocity packet later from its own thread. The Bukkit API of 1.8 can
 * do neither, so both go through the server's own classes, found by reflection once. If they cannot
 * be found, {@link #isAvailable} turns false and hits keep their normal, immediate knockback.
 */
final class DelayedKnockback {

    private final Logger logger;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RagoCombatEngine delayed knockback");
        thread.setDaemon(true);
        return thread;
    });

    /** Main thread only. */
    private boolean resolved;
    private boolean unavailable;
    private Method getHandle;
    private Field motX;
    private Field motY;
    private Field motZ;
    private Field velocityChanged;
    private Field playerConnection;
    private Method sendPacket;
    private Constructor<?> velocityPacket;

    DelayedKnockback(Logger logger) {
        this.logger = logger;
    }

    /** Whether knockback can be delayed on this server. Main thread only. */
    boolean isAvailable(Player sample) {
        if (!resolved) {
            try {
                getHandle = sample.getClass().getMethod("getHandle");
                resolve(getHandle.invoke(sample).getClass());
            } catch (ReflectiveOperationException | RuntimeException e) {
                resolved = true;
                disable(e);
            }
        }
        return !unavailable;
    }

    /**
     * Puts back the victim's motion of before the hit now, and sends {@code velocity} to the victim
     * after {@code delayMs}. Main thread only, from the velocity event that the caller then cancels.
     *
     * @return false if it could not be done: the caller must then not cancel the event
     */
    boolean sendLater(Player victim, Vec3 motionBefore, Vec3 velocity, int delayMs) {
        if (!isAvailable(victim)) {
            return false;
        }
        try {
            return sendLater(getHandle.invoke(victim), victim.getEntityId(), motionBefore, velocity, delayMs);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable(e);
            return false;
        }
    }

    /** The same, from the server's own player object: what the tests can build. */
    boolean sendLater(Object handle, int entityId, Vec3 motionBefore, Vec3 velocity, int delayMs) {
        if (!resolved) {
            resolve(handle.getClass());
        }
        if (unavailable) {
            return false;
        }
        try {
            Object packet = velocityPacket.newInstance(entityId, velocity.x(), velocity.y(), velocity.z());
            Object connection = playerConnection.get(handle);
            motX.setDouble(handle, motionBefore.x());
            motY.setDouble(handle, motionBefore.y());
            motZ.setDouble(handle, motionBefore.z());
            velocityChanged.setBoolean(handle, false);
            executor.schedule(() -> send(connection, packet), delayMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable(e);
            return false;
        }
    }

    void shutdown() {
        executor.shutdownNow();
    }

    /** Runs on the executor thread: the server's connection hands the packet to its network thread. */
    private void send(Object connection, Object packet) {
        try {
            sendPacket.invoke(connection, packet);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.warning("A delayed knockback could not be sent: " + e);
        }
    }

    /** Finds the fields and the packet class from the server's player class. */
    private void resolve(Class<?> entityPlayer) {
        resolved = true;
        try {
            motX = entityPlayer.getField("motX");
            motY = entityPlayer.getField("motY");
            motZ = entityPlayer.getField("motZ");
            velocityChanged = entityPlayer.getField("velocityChanged");
            playerConnection = entityPlayer.getField("playerConnection");
            Class<?> connectionType = playerConnection.getType();
            String serverPackage = connectionType.getName().substring(0, connectionType.getName().lastIndexOf('.'));
            ClassLoader loader = connectionType.getClassLoader();
            Class<?> packetType = Class.forName(serverPackage + ".Packet", false, loader);
            velocityPacket = Class.forName(serverPackage + ".PacketPlayOutEntityVelocity", false, loader)
                    .getConstructor(int.class, double.class, double.class, double.class);
            sendPacket = connectionType.getMethod("sendPacket", packetType);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable(e);
        }
    }

    private void disable(Exception cause) {
        if (!unavailable) {
            unavailable = true;
            logger.warning("Knockback cannot be delayed on this server (" + cause
                    + "): the ping simulation only uses the older motion.");
        }
    }
}
