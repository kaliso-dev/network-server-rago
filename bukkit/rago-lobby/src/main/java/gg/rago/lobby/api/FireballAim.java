package gg.rago.lobby.api;

import java.lang.reflect.Field;
import org.bukkit.entity.Fireball;
import org.bukkit.util.Vector;

/**
 * Aims a fireball as the game does, for the plugins that throw them. A 1.8 fireball speeds up each
 * tick by its "direction", 0.1 block a tick in vanilla; Bukkit's Fireball#setDirection forgets the
 * 0.1 on 1.8 and gives it 1, so the fireball goes ten times faster than on Hypixel. This sets the
 * direction itself, at 0.1, as Hypixel's copies do (BedWars1058 writes the same fields). Main
 * thread only.
 */
public final class FireballAim {

    /** How much the fireball speeds up each tick, in blocks a tick, as in vanilla. */
    public static final double ACCELERATION = 0.1;

    private static Field[] fields;
    private static boolean unavailable;

    private FireballAim() {
    }

    /**
     * Makes the fireball speed up along this direction by {@link #ACCELERATION} each tick. Returns
     * false when the server does not let it be done (then the fireball keeps the direction it was
     * thrown with).
     */
    public static boolean aim(Fireball fireball, Vector direction) {
        double[] acceleration = acceleration(direction.getX(), direction.getY(), direction.getZ());
        Field[] dir = fields(fireball);
        if (dir == null) {
            return false;
        }
        try {
            Object handle = fireball.getClass().getMethod("getHandle").invoke(fireball);
            for (int i = 0; i < 3; i++) {
                dir[i].setDouble(handle, acceleration[i]);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            unavailable = true;
            return false;
        }
    }

    /** The direction made 0.1 long (straight down if it has no length). Pure math. */
    public static double[] acceleration(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-9) {
            return new double[] {0, -ACCELERATION, 0};
        }
        return new double[] {x / length * ACCELERATION, y / length * ACCELERATION, z / length * ACCELERATION};
    }

    /** The dirX, dirY and dirZ fields of the server's fireball, found once; null if there are none. */
    private static Field[] fields(Fireball fireball) {
        if (fields != null || unavailable) {
            return fields;
        }
        try {
            Object handle = fireball.getClass().getMethod("getHandle").invoke(fireball);
            Field[] found = new Field[3];
            String[] names = {"dirX", "dirY", "dirZ"};
            for (int i = 0; i < 3; i++) {
                found[i] = field(handle.getClass(), names[i]);
                found[i].setAccessible(true);
            }
            fields = found;
        } catch (ReflectiveOperationException | RuntimeException e) {
            unavailable = true;
        }
        return fields;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> each = type; each != null; each = each.getSuperclass()) {
            try {
                return each.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                // in a superclass
            }
        }
        throw new NoSuchFieldException(name);
    }
}
