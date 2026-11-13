package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Lobby Fight, the fight in the waiting lobby of a Bed Wars map (salle-attente: in its
 * bedwars.yml, see {@link BedwarsMap.WaitingLobby}) with the kit of the FFA: in rounds of one life
 * ({@link LobbyFightGame}, 1v1), or for everyone ({@link FfaGame}, the FFA). What both have: where a
 * player can stand on its floor, the two starts of a duel, the blocks placed only in the lobby, a
 * fall off it is a death. One per game, made on its copy of the map. Main thread only.
 */
final class LobbyFight {

    /** A player this many blocks under the floor has fallen off the lobby: dead, as in the void. */
    static final int FALL_BELOW = 3;
    /** Blocks are placed in the lobby up to this many blocks above its floor. */
    static final int BUILD_ABOVE = 6;
    /** The lobby mode whose kit Lobby Fight plays when the lobby has none for its own mode: the FFA. */
    static final String KIT_MODE = "ffa";
    /** The starts of a duel are at most this far from the middle, in parts of the radius... */
    static final double START_REACH = 0.75;
    /** ...and at least this many blocks from it. */
    static final int START_MIN = 2;

    /** What the copy of the map has at a block: is it solid, is it empty (air). */
    interface Blocks {
        boolean solid(int x, int y, int z);

        boolean empty(int x, int y, int z);
    }

    private final BedwarsMap.WaitingLobby lobby;
    private final World world;
    private final Blocks blocks;
    /** The blocks of the floor where a player can stand (x, z), found the first time they are needed. */
    private List<int[]> spots;
    /** The starts of a duel, found the first time they are needed (see {@link #starts}). */
    private double[][] starts;

    LobbyFight(BedwarsMap.WaitingLobby lobby, World world) {
        this(lobby, world, new Blocks() {
            @Override
            public boolean solid(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getType().isSolid();
            }

            @Override
            public boolean empty(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getType() == Material.AIR;
            }
        });
    }

    LobbyFight(BedwarsMap.WaitingLobby lobby, World world, Blocks blocks) {
        this.lobby = lobby;
        this.world = world;
        this.blocks = blocks;
    }

    BedwarsMap.WaitingLobby lobby() {
        return lobby;
    }

    /** Under this height a player has fallen off the lobby. */
    double fallLine() {
        return lobby.floorY - FALL_BELOW;
    }

    /** Where the dead and those who wait watch: above the middle of the lobby, looking down at it. */
    Location watchPoint() {
        return new Location(world, lobby.centreX, lobby.floorY + 1 + BUILD_ABOVE + 3, lobby.centreZ, 0f, 90f);
    }

    /** True if a player can stand on the floor at (x, z): solid under the feet, the air for the feet and the head. */
    boolean standable(int x, int z) {
        int y = lobby.floorY;
        return lobby.covers(x, z) && blocks.solid(x, y, z) && blocks.empty(x, y + 1, z) && blocks.empty(x, y + 2, z);
    }

    /** The blocks of the floor where a player can stand, away from its edge (a block in from the radius). */
    List<int[]> spots() {
        if (spots == null) {
            spots = new ArrayList<>();
            int reach = (int) Math.ceil(lobby.radius);
            int cx = (int) Math.floor(lobby.centreX);
            int cz = (int) Math.floor(lobby.centreZ);
            for (int x = cx - reach; x <= cx + reach; x++) {
                for (int z = cz - reach; z <= cz + reach; z++) {
                    double dx = x + 0.5 - lobby.centreX;
                    double dz = z + 0.5 - lobby.centreZ;
                    if (dx * dx + dz * dz <= (lobby.radius - 1.5) * (lobby.radius - 1.5) && standable(x, z)) {
                        spots.add(new int[] {x, z});
                    }
                }
            }
        }
        return spots;
    }

    /** A place to stand at random on the floor, looking at the middle; the middle when none is found. */
    Location anywhere(Random random) {
        List<int[]> all = spots();
        if (all.isEmpty()) {
            return middle();
        }
        int[] spot = all.get(random.nextInt(all.size()));
        return facingMiddle(spot[0] + 0.5, spot[1] + 0.5);
    }

    /** The middle of the floor, a player standing on it. */
    Location middle() {
        return new Location(world, lobby.centreX, lobby.floorY + 1, lobby.centreZ);
    }

    private Location facingMiddle(double x, double z) {
        Location at = new Location(world, x, lobby.floorY + 1, z);
        at.setYaw(yaw(lobby.centreX - x, lobby.centreZ - z));
        return at;
    }

    /** The yaw of Minecraft that looks along (dx, dz). */
    static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * Where the two sides of a duel start (x, z, yaw each), face to face: across the middle along x
     * or along z, as far from it as both starts stand on the floor and nothing is between them (the
     * posts of a lobby), at most {@link #START_REACH} of the radius, the first side on the west or
     * the north; without such a line, the two places of the floor farthest apart. Null when the
     * floor has no place to stand.
     */
    double[][] starts() {
        if (starts == null) {
            starts = across();
            if (starts == null) {
                starts = farthestApart();
            }
        }
        return starts;
    }

    /** The starts across the middle along x or z, or null when no line is clear. */
    private double[][] across() {
        int cx = (int) Math.floor(lobby.centreX);
        int cz = (int) Math.floor(lobby.centreZ);
        int far = (int) Math.floor(lobby.radius * START_REACH);
        int[] best = null;
        for (int axis = 0; axis < 2; axis++) {
            for (int d = far; d >= START_MIN; d--) {
                if (best != null && d <= best[1]) {
                    break;
                }
                int ax = axis == 0 ? d : 0;
                int az = axis == 0 ? 0 : d;
                if (standable(cx - ax, cz - az) && standable(cx + ax, cz + az) && clear(cx, cz, axis, d)) {
                    best = new int[] {axis, d};
                    break;
                }
            }
        }
        if (best == null) {
            return null;
        }
        int ax = best[0] == 0 ? best[1] : 0;
        int az = best[0] == 0 ? 0 : best[1];
        return new double[][] {
            {cx - ax + 0.5, cz - az + 0.5, yaw(ax, az)},
            {cx + ax + 0.5, cz + az + 0.5, yaw(-ax, -az)},
        };
    }

    /** The two places of the floor farthest apart, each looking at the other; null without any. */
    private double[][] farthestApart() {
        List<int[]> all = spots();
        if (all.isEmpty()) {
            return null;
        }
        int[] a = all.get(0);
        int[] b = all.get(0);
        long most = -1;
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                long dx = all.get(i)[0] - all.get(j)[0];
                long dz = all.get(i)[1] - all.get(j)[1];
                if (dx * dx + dz * dz > most) {
                    most = dx * dx + dz * dz;
                    a = all.get(i);
                    b = all.get(j);
                }
            }
        }
        return new double[][] {
            {a[0] + 0.5, a[1] + 0.5, yaw(b[0] - a[0], b[1] - a[1])},
            {b[0] + 0.5, b[1] + 0.5, yaw(a[0] - b[0], a[1] - b[1])},
        };
    }

    /** True if nothing solid is in the way at the feet nor at the head, from -d to d along the axis. */
    private boolean clear(int cx, int cz, int axis, int d) {
        int y = lobby.floorY;
        for (int i = -d; i <= d; i++) {
            int x = axis == 0 ? cx + i : cx;
            int z = axis == 0 ? cz : cz + i;
            if (blocks.solid(x, y + 1, z) || blocks.solid(x, y + 2, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The start of the index-th of count players of this side (0 or 1) of a duel: side by side, a
     * block and a half apart across the line of the duel; the middle when the floor has no place
     * to stand.
     */
    Location start(int side, int index, int count) {
        double[][] starts = starts();
        if (starts == null) {
            return middle();
        }
        double[] start = starts[Math.max(0, Math.min(1, side))];
        double across = (index - (count - 1) / 2.0) * 1.5;
        // across the line of the duel: along z when it runs along x, and the other way
        boolean alongX = starts[0][1] == starts[1][1];
        Location at = new Location(world, start[0] + (alongX ? 0 : across), lobby.floorY + 1, start[1] + (alongX ? across : 0));
        at.setYaw((float) start[2]);
        return at;
    }

    /**
     * Why a block cannot go there, null when it can: in the lobby only (within its radius), from
     * its floor up to {@link #BUILD_ABOVE} blocks above it.
     */
    String cannotBuildAt(Block block) {
        return cannotBuildAt(block.getX(), block.getY(), block.getZ());
    }

    String cannotBuildAt(int x, int y, int z) {
        if (y > lobby.floorY + BUILD_ABOVE) {
            return "Build height limit reached!";
        }
        if (y <= lobby.floorY || !lobby.covers(x, z)) {
            return "You can only place blocks in the lobby!";
        }
        return null;
    }
}
