package gg.rago.lobby;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import org.bukkit.World;

/**
 * Hears every block that changes in a world, whoever changes it (a player, an explosion, flowing
 * water, a plugin): a listener of the 1.8 world itself (its IWorldAccess, through which the server
 * sends the changes to the players), added by reflection, Bukkit having no event for the blocks a
 * plugin sets. The listener gets x, y and z of each change, on the thread that made it (the main
 * one); it must be quick.
 */
final class ReplayHook {

    interface Listener {
        void changed(int x, int y, int z);
    }

    private final Object access;
    private final List<?> accesses;

    private ReplayHook(Object access, List<?> accesses) {
        this.access = access;
        this.accesses = accesses;
    }

    /** Listens to this world (a CraftWorld of 1.8). Throws if the server is not one. */
    static ReplayHook install(World world, final Listener listener) throws ReflectiveOperationException {
        Object handle = world.getClass().getMethod("getHandle").invoke(world);
        String server = handle.getClass().getName();
        String nms = server.substring(0, server.lastIndexOf('.'));
        ClassLoader loader = handle.getClass().getClassLoader();
        Class<?> accessClass = Class.forName(nms + ".IWorldAccess", false, loader);
        final Class<?> positionClass = Class.forName(nms + ".BlockPosition", false, loader);
        Class<?> base = Class.forName(nms + ".BaseBlockPosition", false, loader);
        final Method getX = base.getMethod("getX");
        final Method getY = base.getMethod("getY");
        final Method getZ = base.getMethod("getZ");
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "RagoLobby replays";
                    default:
                        return null;
                }
            }
            // a(BlockPosition): a block changed (b(BlockPosition) is its light, the others sounds and effects)
            if (args != null && args.length == 1 && "a".equals(method.getName()) && positionClass.isInstance(args[0])) {
                listener.changed((Integer) getX.invoke(args[0]), (Integer) getY.invoke(args[0]), (Integer) getZ.invoke(args[0]));
            }
            return null;
        };
        Object access = Proxy.newProxyInstance(loader, new Class<?>[] {accessClass}, handler);
        handle.getClass().getMethod("addIWorldAccess", accessClass).invoke(handle, access);
        // the list it went into, to take it out again
        for (Class<?> type = handle.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object list = field.get(handle);
                if (list instanceof List && ((List<?>) list).contains(access)) {
                    return new ReplayHook(access, (List<?>) list);
                }
            }
        }
        throw new NoSuchFieldException("the list of the world's listeners");
    }

    /** No more listening. */
    void remove() {
        accesses.remove(access);
    }
}
