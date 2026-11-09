package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The top bridge of a Top Bridge game, as the owner built it on Antenna: high above the middle of
 * the map, a platform of wool 17 blocks wide, each half in the colour of the team on its side and a
 * white block in the middle; and from the front edge of each island, a stairway of wool one block
 * wide that climbs one block every two, from just below the bed up to the platform. On Antenna the
 * platform is 16 blocks above the beds and 79 long. On another map it is as high if it is clear
 * there (nothing of the map in it or just above it), else at the nearest height where it is, and
 * always at least {@value #MIN_HALF_LENGTH} blocks long on each side of the middle. Between two
 * beds not in line (Invasion, Lectus), everything follows the line from one bed to the other.
 * Immutable.
 */
final class TopBridge {

    /** Height of the platform above the beds, as on Antenna (beds at 83, platform at 99). */
    static final int ABOVE_BED = 16;
    /** Blocks on each side of the middle line: 17 wide. */
    static final int HALF_WIDTH = 8;
    /** The platform is at least twice this long. */
    static final int MIN_HALF_LENGTH = 8;
    /** The platform may go up to this many blocks higher than {@link #ABOVE_BED} to clear the map. */
    static final int MAX_RAISE = 14;
    /** Players may build up to this many blocks above the platform. */
    static final int BUILD_ABOVE = 10;
    /** Two beds nearer than this have no room for a bridge. */
    private static final double MIN_DISTANCE = 16;
    private static final double EPSILON = 1e-6;

    /** Whether there is a block at this place of the map. */
    interface Solid {
        boolean at(int x, int y, int z);
    }

    /** A block of the bridge: a wool of a team's colour, or white (team null) in the middle. */
    static final class Piece {
        final int x;
        final int y;
        final int z;
        final TeamColor team;

        Piece(int x, int y, int z, TeamColor team) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.team = team;
        }

        /** The data value of its wool. */
        short woolData() {
            return team == null ? TeamColor.WHITE.woolData() : team.woolData();
        }
    }

    private final int height;
    private final List<Piece> pieces;

    private TopBridge(int height, List<Piece> pieces) {
        this.height = height;
        this.pieces = Collections.unmodifiableList(pieces);
    }

    /** The y of the platform. */
    int height() {
        return height;
    }

    /** Its blocks, where the map has none. */
    List<Piece> pieces() {
        return pieces;
    }

    /**
     * The bridge between these two beds (their block, as in bedwars.yml), or null when the islands
     * are too near for one.
     */
    static TopBridge plan(TeamColor teamA, MapPoint bedA, TeamColor teamB, MapPoint bedB, Solid solid) {
        double ax = Math.floor(bedA.x()) + 0.5;
        double az = Math.floor(bedA.z()) + 0.5;
        double bx = Math.floor(bedB.x()) + 0.5;
        double bz = Math.floor(bedB.z()) + 0.5;
        double distance = Math.hypot(bx - ax, bz - az);
        if (distance < MIN_DISTANCE) {
            return null;
        }
        double ux = (bx - ax) / distance;
        double uz = (bz - az) / distance;
        // From the bed, the stairway starts one block lower, at the first cell past the island.
        int floorA = bedA.blockY() - 1;
        int floorB = bedB.blockY() - 1;
        double startA = edge(ax, az, ux, uz, distance / 2, floorA, solid);
        double startB = edge(bx, bz, -ux, -uz, distance / 2, floorB, solid);
        if (Double.isNaN(startA) || Double.isNaN(startB)) {
            return null;
        }
        // As on Antenna, 16 blocks above the beds; else the nearest height where the platform is
        // long enough and clear of the map (nothing in it or in the 3 blocks above it), else the
        // one with the fewest blocks of the map in the way.
        int wanted = Math.max(bedA.blockY(), bedB.blockY()) + ABOVE_BED;
        int height = Integer.MIN_VALUE;
        long best = Long.MAX_VALUE;
        for (int h = Math.max(floorA, floorB) + 2; h <= Math.min(250, wanted + MAX_RAISE); h++) {
            if (platformLength(distance, startA, floorA, startB, floorB, h) < 2 * MIN_HALF_LENGTH) {
                continue;
            }
            int inTheWay = inTheWay(ax, az, ux, uz, startA + 2 * (h - floorA), distance - startB - 2 * (h - floorB), h, solid);
            long score = (long) Math.min(inTheWay, 1_000_000) * 1000 + Math.abs(h - wanted) * 2 + (h > wanted ? 1 : 0);
            if (score < best) {
                best = score;
                height = h;
            }
        }
        if (height == Integer.MIN_VALUE) {
            return null;
        }
        // where the platform begins and ends, along the line from bed A
        double from = startA + 2 * (height - floorA);
        double to = distance - startB - 2 * (height - floorB);
        boolean straight = Math.abs(ux) < EPSILON || Math.abs(uz) < EPSILON;
        // one block wide in line with the blocks; on a slant, wide enough for the cells to touch
        double stairWidth = straight ? 0.5 : 0.75;
        Map<Long, Piece> pieces = new LinkedHashMap<>();
        int minX = (int) Math.floor(Math.min(ax, bx)) - HALF_WIDTH - 2;
        int maxX = (int) Math.ceil(Math.max(ax, bx)) + HALF_WIDTH + 2;
        int minZ = (int) Math.floor(Math.min(az, bz)) - HALF_WIDTH - 2;
        int maxZ = (int) Math.ceil(Math.max(az, bz)) + HALF_WIDTH + 2;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double cx = x + 0.5 - ax;
                double cz = z + 0.5 - az;
                double along = cx * ux + cz * uz;
                double across = -cx * uz + cz * ux;
                if (along >= from - EPSILON && along <= to + EPSILON && Math.abs(across) <= HALF_WIDTH + EPSILON) {
                    TeamColor colour = along < distance / 2 - EPSILON ? teamA : along > distance / 2 + EPSILON ? teamB
                            : across < -EPSILON ? teamA : across > EPSILON ? teamB : null;
                    add(pieces, x, height, z, colour, solid);
                    // under the first and last rows, the last step of each stairway
                    if (Math.abs(across) <= stairWidth + EPSILON && (along < from + 0.5 || along > to - 0.5)) {
                        add(pieces, x, height - 1, z, along < distance / 2 ? teamA : teamB, solid);
                    }
                } else if (Math.abs(across) <= stairWidth + EPSILON) {
                    if (along >= startA - EPSILON && along < from - EPSILON) {
                        stair(pieces, x, z, along - startA, floorA, teamA, solid);
                    } else if (distance - along >= startB - EPSILON && distance - along < distance - to - EPSILON) {
                        stair(pieces, x, z, distance - along - startB, floorB, teamB, solid);
                    }
                }
            }
        }
        return new TopBridge(height, new ArrayList<>(pieces.values()));
    }

    /** How many cells of the platform at this height have a block of the map in it or in the 3 blocks above. */
    private static int inTheWay(double ax, double az, double ux, double uz, double from, double to, int height, Solid solid) {
        int count = 0;
        double reach = Math.max(Math.abs(from), Math.abs(to)) + HALF_WIDTH + 2;
        for (int x = (int) Math.floor(ax - reach); x <= (int) Math.ceil(ax + reach); x++) {
            for (int z = (int) Math.floor(az - reach); z <= (int) Math.ceil(az + reach); z++) {
                double cx = x + 0.5 - ax;
                double cz = z + 0.5 - az;
                double along = cx * ux + cz * uz;
                double across = -cx * uz + cz * ux;
                if (along < from - EPSILON || along > to + EPSILON || Math.abs(across) > HALF_WIDTH + EPSILON) {
                    continue;
                }
                for (int y = height; y <= height + 3; y++) {
                    if (solid.at(x, y, z)) {
                        count++;
                        break;
                    }
                }
            }
        }
        return count;
    }

    /** The length of the platform at this height (negative when the stairways would cross). */
    static double platformLength(double distance, double startA, int floorA, double startB, int floorB, int height) {
        return distance - startB - 2 * (height - floorB) - (startA + 2 * (height - floorA));
    }

    /**
     * A step of a stairway, this far from its start: one block every two blocks up, with a block
     * under each first half of a step so that it is climbed by jumping.
     */
    private static void stair(Map<Long, Piece> pieces, int x, int z, double from, int floor, TeamColor team, Solid solid) {
        int up = (int) Math.floor(from / 2 + EPSILON);
        add(pieces, x, floor + up, z, team, solid);
        if (from >= 2 - EPSILON && ((int) Math.floor(from + EPSILON)) % 2 == 0) {
            add(pieces, x, floor + up - 1, z, team, solid);
        }
    }

    private static void add(Map<Long, Piece> pieces, int x, int y, int z, TeamColor team, Solid solid) {
        if (y < 1 || y > 255 || solid.at(x, y, z)) {
            return;
        }
        long key = Instance.key(x, y, z);
        if (!pieces.containsKey(key)) {
            pieces.put(key, new Piece(x, y, z, team));
        }
    }

    /**
     * How far from the bed (x, z), along the line towards the other bed, the first cell past the
     * island is: the first of two cells in a row with nothing from 3 blocks under the floor of the
     * stairway to the bed's height. NaN if the island reaches the middle.
     */
    static double edge(double x, double z, double ux, double uz, double limit, int floor, Solid solid) {
        boolean previous = false;
        double first = Double.NaN;
        int lastX = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (double s = 1; s < limit; s += 0.25) {
            int cellX = (int) Math.floor(x + ux * s);
            int cellZ = (int) Math.floor(z + uz * s);
            if (cellX == lastX && cellZ == lastZ) {
                continue;
            }
            lastX = cellX;
            lastZ = cellZ;
            boolean empty = true;
            for (int y = floor - 3; y <= floor + 1 && empty; y++) {
                empty = !solid.at(cellX, y, cellZ);
            }
            if (empty && previous) {
                return first;
            }
            if (empty) {
                first = (cellX + 0.5 - x) * ux + (cellZ + 0.5 - z) * uz;
            }
            previous = empty;
        }
        return Double.NaN;
    }
}
