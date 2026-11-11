package gg.rago.practice;

import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * The station of the block edit, as on the owner's Block Edit Practice map: a glass case, a wall
 * of wool five wide and three high across it, the player on one side and their enemy on the
 * other. The wall stands at x = 0 of the arena; the player's room is the five blocks west of it.
 * Against the bot, the bot's room behind the wall is two blocks deep and of wood; in a duel, the
 * other player's room is the five blocks east of it, as the first one.
 */
final class EditStation {

    /** The wall: x = 0, from this far on each side of z = 0, from just above the floor this high. */
    static final int WALL_HALF = 2;
    static final int WALL_HEIGHT = 3;
    /** The rooms: the player's goes this far west of the wall, the other one this far east. */
    static final int ROOM = 5;
    static final int BOT_ROOM = 2;

    private EditStation() {
    }

    /** True if this place of the arena (relative to its centre and floor) is a block of the wall. */
    static boolean isWall(int dx, int dy, int dz) {
        return dx == 0 && dy >= 1 && dy <= WALL_HEIGHT && Math.abs(dz) <= WALL_HALF;
    }

    /** The farthest x east of the station's inside: the bot's room, or a second player's room. */
    static int east(boolean duel) {
        return duel ? ROOM : BOT_ROOM;
    }

    /** True if the block is part of the wall of this arena. */
    static boolean isWall(Arena arena, Block block) {
        return block.getWorld().equals(arena.world()) && isWall(block.getX() - arena.centreX(), block.getY() - arena.floorY(),
                block.getZ() - arena.centreZ());
    }

    /** How many blocks of the wall are missing; low: only the ones a player can be hit through. */
    static int holes(Arena arena, boolean low) {
        int holes = 0;
        for (int dy = 1; dy <= (low ? 2 : WALL_HEIGHT); dy++) {
            for (int dz = -WALL_HALF; dz <= WALL_HALF; dz++) {
                if (arena.world().getBlockAt(arena.centreX(), arena.floorY() + dy, arena.centreZ() + dz).getType() != Material.WOOL) {
                    holes++;
                }
            }
        }
        return holes;
    }

    /** Builds the station: the glass case, the floor, the wall of this colour, the bot's room of wood. */
    static void build(Arena arena, boolean duel, int wool) {
        int west = -ROOM - 1;
        int east = east(duel) + 1;
        int side = WALL_HALF + 1;
        // the glass case, one block around the inside
        for (int x = west; x <= east; x++) {
            for (int y = 0; y <= WALL_HEIGHT + 2; y++) {
                for (int z = -side; z <= side; z++) {
                    if (x == west || x == east || Math.abs(z) == side || y == 0 || y == WALL_HEIGHT + 2) {
                        arena.build(x, y, z, Material.GLASS);
                    }
                }
            }
        }
        Shapes.floor(arena, west + 1, -WALL_HALF, east - 1, WALL_HALF, 0, Material.GRASS, 0);
        for (int z = -WALL_HALF; z <= WALL_HALF; z++) {
            arena.build(0, 0, z, Material.DIRT);
            // the beam over the wall
            arena.build(0, WALL_HEIGHT + 1, z, Material.LOG);
            for (int y = 1; y <= WALL_HEIGHT; y++) {
                arena.build(0, y, z, Material.WOOL, wool);
            }
        }
        if (duel) {
            return;
        }
        // the bot's room: wood all around it, and its roof
        for (int x = 0; x <= east; x++) {
            for (int y = 1; y <= WALL_HEIGHT; y++) {
                Material wood = x == 0 || x == east ? Material.LOG : Material.WOOD;
                arena.build(x, y, -side, wood);
                arena.build(x, y, side, wood);
            }
            for (int z = -WALL_HALF; z <= WALL_HALF; z++) {
                if (x == east) {
                    for (int y = 1; y <= WALL_HEIGHT; y++) {
                        arena.build(x, y, z, Material.WOOD);
                    }
                }
                if (x > 0) {
                    arena.build(x, WALL_HEIGHT + 1, z, Material.WOOD);
                }
            }
        }
    }
}
