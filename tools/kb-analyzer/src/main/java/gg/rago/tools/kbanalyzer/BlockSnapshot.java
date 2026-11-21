package gg.rago.tools.kbanalyzer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A blocks line: every block that is not air in a cube around an explosion, as the client saw
 * them just before it applied the explosion.
 */
public final class BlockSnapshot {

    public final long ms;
    /** The explosion's centre and strength, as in its packet. */
    public final double x;
    public final double y;
    public final double z;
    public final double strength;
    /** Half the side of the cube, in blocks. */
    public final int reach;
    private final Map<Long, int[]> blocks;

    public BlockSnapshot(long ms, double x, double y, double z, double strength, int reach, Map<Long, int[]> blocks) {
        this.ms = ms;
        this.x = x;
        this.y = y;
        this.z = z;
        this.strength = strength;
        this.reach = reach;
        this.blocks = blocks;
    }

    static BlockSnapshot fromJson(JsonObject json) {
        Map<Long, int[]> blocks = new HashMap<>();
        JsonArray array = json.has("blocks") && json.get("blocks").isJsonArray()
                ? json.getAsJsonArray("blocks") : new JsonArray();
        for (JsonElement element : array) {
            JsonArray block = element.getAsJsonArray();
            int x = block.get(0).getAsInt();
            int y = block.get(1).getAsInt();
            int z = block.get(2).getAsInt();
            blocks.put(key(x, y, z), new int[] {block.get(3).getAsInt(), block.get(4).getAsInt()});
        }
        double reach = Json.number(json, "reach");
        return new BlockSnapshot(json.get("ms").getAsLong(), Json.number(json, "x"), Json.number(json, "y"),
                Json.number(json, "z"), Json.number(json, "strength"), Double.isNaN(reach) ? 0 : (int) reach, blocks);
    }

    /** The block at the position as {@code [id, metadata]}, or null for air or outside the cube. */
    public int[] at(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    /** Every block of the snapshot, by position key ({@link #key}). */
    public Map<Long, int[]> blocks() {
        return Collections.unmodifiableMap(blocks);
    }

    /** True when the position is inside the cube that was read. */
    public boolean covers(int bx, int by, int bz) {
        return Math.abs(bx - (int) Math.floor(x)) <= reach && Math.abs(by - (int) Math.floor(y)) <= reach
                && Math.abs(bz - (int) Math.floor(z)) <= reach;
    }

    /** True when this snapshot was read for the explosion with this centre. */
    public boolean isFor(double ex, double ey, double ez) {
        return Math.abs(ex - x) < 1e-3 && Math.abs(ey - y) < 1e-3 && Math.abs(ez - z) < 1e-3;
    }

    public static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) y & 0xFFF) << 26 | (long) z & 0x3FFFFFF;
    }

    public static int[] position(long key) {
        return new int[] {(int) (key >> 38), (int) (key << 26 >> 52), (int) (key << 38 >> 38)};
    }
}
