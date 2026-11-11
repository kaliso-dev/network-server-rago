package gg.rago.practice;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

/**
 * The shapes the practice arenas are built from: floors, discs, beds and the layers of a bed
 * defense. Offsets are from the centre of the arena; y 0 is its floor.
 */
final class Shapes {

    /** Wool, clay and glass colours (data values). */
    static final int WHITE = 0;
    static final int ORANGE = 1;
    static final int LIGHT_BLUE = 3;
    static final int YELLOW = 4;
    static final int LIME = 5;
    static final int GRAY = 7;
    static final int LIGHT_GRAY = 8;
    static final int CYAN = 9;
    static final int BLUE = 11;
    static final int GREEN = 13;
    static final int RED = 14;
    static final int BLACK = 15;

    private Shapes() {
    }

    /** A flat rectangle of blocks at this height. */
    static void floor(Arena arena, int x1, int z1, int x2, int z2, int dy, Material type, int data) {
        arena.fill(x1, dy, z1, x2, dy, z2, type, data);
    }

    /** A round floor of this radius around (cx, cz). */
    static void disc(Arena arena, int cx, int cz, int dy, int radius, Material type, int data) {
        int r2 = radius * radius + radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= r2) {
                    arena.build(cx + dx, dy, cz + dz, type, data);
                }
            }
        }
    }

    /** The walls of a rectangle, this high above the floor at dy. */
    static void walls(Arena arena, int x1, int z1, int x2, int z2, int dy, int height, Material type, int data) {
        for (int y = dy; y < dy + height; y++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                arena.build(x, y, z1, type, data);
                arena.build(x, y, z2, type, data);
            }
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                arena.build(x1, y, z, type, data);
                arena.build(x2, y, z, type, data);
            }
        }
    }

    /**
     * A bed with its foot at (x, dy, z) and its head one block along (hx, hz), a unit step: (0, 1)
     * south, (-1, 0) west, (0, -1) north, (1, 0) east.
     */
    static void bed(Arena arena, int x, int dy, int z, int hx, int hz) {
        int direction = hz > 0 ? 0 : hx < 0 ? 1 : hz < 0 ? 2 : 3;
        arena.build(x, dy, z, Material.BED_BLOCK, direction);
        arena.build(x + hx, dy, z + hz, Material.BED_BLOCK, direction | 8);
    }

    /**
     * The places of each layer of a defense around a bed whose foot is at (x, dy, z) and head at
     * (x + hx, dy, z + hz): layer 1 touches the bed, layer 2 covers layer 1, and so on, as a player
     * builds it (each place at that many steps from the bed, never below it). Offsets {x, y, z}.
     */
    static List<List<int[]>> defense(int x, int dy, int z, int hx, int hz, int layers) {
        List<List<int[]>> result = new ArrayList<>();
        for (int layer = 1; layer <= layers; layer++) {
            List<int[]> places = new ArrayList<>();
            for (int ox = -layer - 1; ox <= layer + 1; ox++) {
                for (int oz = -layer - 1; oz <= layer + 1; oz++) {
                    for (int oy = 0; oy <= layer; oy++) {
                        int px = x + ox;
                        int pz = z + oz;
                        int py = dy + oy;
                        int foot = Math.abs(px - x) + Math.abs(py - dy) + Math.abs(pz - z);
                        int head = Math.abs(px - x - hx) + Math.abs(py - dy) + Math.abs(pz - z - hz);
                        if (Math.min(foot, head) == layer) {
                            places.add(new int[] {px, py, pz});
                        }
                    }
                }
            }
            result.add(places);
        }
        return result;
    }

    /** Builds the layers of a defense, each of its own block. */
    static void buildDefense(Arena arena, List<List<int[]>> layers, Material[] types, int[] datas) {
        for (int i = 0; i < layers.size(); i++) {
            for (int[] place : layers.get(i)) {
                arena.build(place[0], place[1], place[2], types[i], datas[i]);
            }
        }
    }

    /** A bed of this round only: it can be broken, and is back at the next round. */
    static void putBed(Arena arena, int x, int dy, int z, int hx, int hz) {
        int direction = hz > 0 ? 0 : hx < 0 ? 1 : hz < 0 ? 2 : 3;
        arena.put(x, dy, z, Material.BED_BLOCK, direction);
        arena.put(x + hx, dy, z + hz, Material.BED_BLOCK, direction | 8);
    }

    /** The layers of a defense of this round only: they can be broken and blown up. */
    static void putDefense(Arena arena, List<List<int[]>> layers, Material[] types, int[] datas) {
        for (int i = 0; i < layers.size(); i++) {
            for (int[] place : layers.get(i)) {
                if (arena.block(place[0], place[1], place[2]).getType() == Material.AIR) {
                    arena.put(place[0], place[1], place[2], types[i], datas[i]);
                }
            }
        }
    }

    /** A floating island: this rectangle on top, two smaller ones under it. */
    static void island(Arena arena, int x1, int z1, int x2, int z2, Material top, int topData, Material under, int underData) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        floor(arena, minX, minZ, maxX, maxZ, 0, top, topData);
        for (int depth = 1; depth <= 2; depth++) {
            if (maxX - minX - 2 * depth < 0 || maxZ - minZ - 2 * depth < 0) {
                break;
            }
            floor(arena, minX + depth, minZ + depth, maxX - depth, maxZ - depth, -depth, under, underData);
        }
    }
}
