package gg.rago.bedwars;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * The blast of a fireball that breaks blocks. On 1.8 a fireball breaks blocks only where the
 * mobGriefing rule is on, and it is off in the copies of the maps (no mob breaks anything there):
 * so its blast is made again here, as the server makes it, but with its blocks, through the
 * server's own classes found by reflection (v1_8_R3). Which of the blocks go is then decided as for
 * any blast (BedwarsHost.onExplode). If this server has no such method, the fireballs break nothing,
 * as before. Main thread only.
 */
final class FireballBlast {

    private static Method entityHandle;
    private static Method worldHandle;
    private static Method createExplosion;
    private static boolean failed;

    private FireballBlast() {
    }

    /**
     * Makes the blast of this fireball, where it is, with this power, breaking blocks: true if done
     * (the server's own, which breaks none, is then to be cancelled).
     */
    static boolean explode(Entity fireball, float power, boolean fire, Logger logger) {
        if (failed) {
            return false;
        }
        try {
            World world = fireball.getWorld();
            if (entityHandle == null) {
                entityHandle = fireball.getClass().getMethod("getHandle");
                worldHandle = world.getClass().getMethod("getHandle");
            }
            Object entity = entityHandle.invoke(fireball);
            Object level = worldHandle.invoke(world);
            if (createExplosion == null) {
                // createExplosion(Entity source, double x, double y, double z, float power, boolean fire, boolean blocks)
                for (Method method : level.getClass().getMethods()) {
                    Class<?>[] types = method.getParameterTypes();
                    if (method.getName().equals("createExplosion") && types.length == 7 && types[0].isInstance(entity)
                            && types[4] == float.class && types[5] == boolean.class && types[6] == boolean.class) {
                        createExplosion = method;
                    }
                }
                if (createExplosion == null) {
                    throw new NoSuchMethodException("World.createExplosion(Entity, double, double, double, float, boolean, boolean)");
                }
            }
            Location at = fireball.getLocation();
            createExplosion.invoke(level, entity, at.getX(), at.getY(), at.getZ(), power, fire, true);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            logger.log(Level.WARNING, "Cannot make the fireballs break blocks on this server: they break none", e);
            return false;
        }
    }
}
