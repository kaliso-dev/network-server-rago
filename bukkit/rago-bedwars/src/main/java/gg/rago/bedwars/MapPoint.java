package gg.rago.bedwars;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** A place in a map: a position and where it looks. Immutable. */
final class MapPoint {

    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    MapPoint(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /** Where the player stands, rounded to the half block, looking straight (pitch 0). */
    static MapPoint standing(Location location) {
        return new MapPoint(half(location.getX()), Math.floor(location.getY() * 2) / 2, half(location.getZ()),
                Math.round(location.getYaw() / 45f) * 45f % 360f, 0f);
    }

    /** The centre of a block's top: where an item or a generator stands. */
    static MapPoint centreOf(Location location) {
        return new MapPoint(location.getBlockX() + 0.5, location.getBlockY(), location.getBlockZ() + 0.5, 0f, 0f);
    }

    /** A block, by its corner. */
    static MapPoint block(Block block) {
        return new MapPoint(block.getX(), block.getY(), block.getZ(), 0f, 0f);
    }

    /** The middle of the block the coordinate is in. */
    private static double half(double coordinate) {
        return Math.floor(coordinate) + 0.5;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double z() {
        return z;
    }

    float yaw() {
        return yaw;
    }

    float pitch() {
        return pitch;
    }

    int blockX() {
        return (int) Math.floor(x);
    }

    int blockY() {
        return (int) Math.floor(y);
    }

    int blockZ() {
        return (int) Math.floor(z);
    }

    Location in(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    Block blockIn(World world) {
        return world.getBlockAt(blockX(), blockY(), blockZ());
    }

    /** Horizontal distance to a block position, squared. */
    double flatDistanceSquared(int bx, int bz) {
        double dx = bx + 0.5 - x;
        double dz = bz + 0.5 - z;
        return dx * dx + dz * dz;
    }

    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        if (yaw != 0f) {
            map.put("yaw", (double) yaw);
        }
        if (pitch != 0f) {
            map.put("pitch", (double) pitch);
        }
        return map;
    }

    /** The point written as x, y, z (and yaw, pitch): numbers. Throws with the path when it is not. */
    static MapPoint fromMap(Map<?, ?> map, String where) {
        return new MapPoint(number(map, "x", where, true), number(map, "y", where, true), number(map, "z", where, true),
                (float) number(map, "yaw", where, false), (float) number(map, "pitch", where, false));
    }

    private static double number(Map<?, ?> map, String key, String where, boolean required) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null && !required) {
            return 0.0;
        }
        throw new IllegalArgumentException(where + "." + key + ": a number");
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof MapPoint)) {
            return false;
        }
        MapPoint point = (MapPoint) other;
        return x == point.x && y == point.y && z == point.z && yaw == point.yaw && pitch == point.pitch;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(x) * 31 * 31 + Double.hashCode(y) * 31 + Double.hashCode(z);
    }

    /** "12 64 -3". */
    @Override
    public String toString() {
        return blockX() + " " + blockY() + " " + blockZ();
    }
}
