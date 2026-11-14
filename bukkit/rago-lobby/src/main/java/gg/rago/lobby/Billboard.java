package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/**
 * A billboard of the lobby as config.yml gives it (panneaux:): an image of the folder panneaux/
 * on a wall of maps, its size in blocks and where it stands. Immutable. The geometry is here too,
 * pure: which block each map is on, which way the wall faces.
 */
final class Billboard {

    /** Blocks on a side of a billboard at most: 256 maps. */
    static final int MAX_SIZE = 16;
    static final int MAX_BOARDS = 8;
    /** The point that places a billboard in the game: this, then its name. */
    static final String POINT_PREFIX = "panneau-";
    /**
     * The way a wall faces, as the client numbers it (an item frame's data): south (+z), west
     * (-x), north (-z), east (+x). Each {x, z}.
     */
    static final int SOUTH = 0;
    static final int WEST = 1;
    static final int NORTH = 2;
    static final int EAST = 3;
    private static final int[][] FACING = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
    private static final String[] FACE_NAMES = {"sud", "ouest", "nord", "est"};
    /** No face given: towards the spawn. */
    static final int TOWARDS_SPAWN = -1;

    final String name;
    final String image;
    final int columns;
    final int rows;
    /** The block of the middle of its bottom row, or null: only on its point. */
    final int[] block;
    final int face;

    Billboard(String name, String image, int columns, int rows, int[] block, int face) {
        this.name = name;
        this.image = image;
        this.columns = columns;
        this.rows = rows;
        this.block = block == null ? null : block.clone();
        this.face = face;
    }

    /** The point of /lobby point that places it in the game. */
    String point() {
        return POINT_PREFIX + name;
    }

    int maps() {
        return columns * rows;
    }

    /** panneaux: of config.yml. None without it. */
    static List<Billboard> read(ConfigurationSection section) {
        List<Billboard> boards = new ArrayList<>();
        if (section == null) {
            return boards;
        }
        for (String name : section.getKeys(false)) {
            String where = "panneaux." + name;
            if (!LobbyPoints.validName(POINT_PREFIX + name)) {
                throw new IllegalArgumentException(where + ": the name of a billboard is lowercase letters without accents,"
                        + " digits and dashes, " + (32 - POINT_PREFIX.length()) + " characters at most");
            }
            ConfigurationSection board = section.getConfigurationSection(name);
            if (board == null) {
                throw new IllegalArgumentException(where + ": image, largeur, hauteur and lieu, as in the example");
            }
            String image = board.getString("image", "").trim();
            if (!image.matches("[A-Za-z0-9 _.-]{1,64}") || image.startsWith(".") || !image.contains(".")) {
                throw new IllegalArgumentException(where + ".image: the name of an image of the folder panneaux"
                        + " (PNG or JPG), as discord.png");
            }
            int columns = board.getInt("largeur", 0);
            int rows = board.getInt("hauteur", 0);
            if (columns < 1 || columns > MAX_SIZE || rows < 1 || rows > MAX_SIZE) {
                throw new IllegalArgumentException(where + ": largeur and hauteur from 1 to " + MAX_SIZE + " blocks");
            }
            int[] block = null;
            int face = TOWARDS_SPAWN;
            String place = board.getString("lieu", "").trim().toLowerCase(Locale.ROOT);
            if (!place.isEmpty()) {
                String[] parts = place.split("\\s+");
                try {
                    if (parts.length < 3 || parts.length > 4) {
                        throw new NumberFormatException();
                    }
                    block = new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(where + ".lieu: \"x y z\", the block of the middle of its bottom row,"
                            + " and nord, sud, est or ouest after it to choose where it faces (else the spawn)");
                }
                if (parts.length == 4) {
                    face = faceNamed(parts[3]);
                    if (face < 0) {
                        throw new IllegalArgumentException(where + ".lieu: it faces nord, sud, est or ouest, not \""
                                + parts[3] + "\"");
                    }
                }
            }
            boards.add(new Billboard(name, image, columns, rows, block, face));
        }
        if (boards.size() > MAX_BOARDS) {
            throw new IllegalArgumentException("panneaux: " + MAX_BOARDS + " billboards at most");
        }
        return Collections.unmodifiableList(boards);
    }

    private static int faceNamed(String name) {
        for (int i = 0; i < FACE_NAMES.length; i++) {
            if (FACE_NAMES[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    // --- Geometry ------------------------------------------------------------------------------

    /** The way someone looking this way (a yaw of Minecraft) faces: south, west, north or east. */
    static int face(float yaw) {
        return Math.floorMod(Math.round(yaw / 90f), 4);
    }

    /** The way that faces most towards a place, from (x, z). */
    static int faceTowards(double x, double z, double towardsX, double towardsZ) {
        double dx = towardsX - x;
        double dz = towardsZ - z;
        if (Math.abs(dz) >= Math.abs(dx)) {
            return dz >= 0 ? SOUTH : NORTH;
        }
        return dx >= 0 ? EAST : WEST;
    }

    /**
     * The block of each map of a wall facing this way, whose bottom row has its middle on this
     * block (just right of the middle for an even width): {x, y, z}, row by row from the top
     * left as seen from in front, as {@link MapArt#tiles} gives the maps.
     */
    static int[][] blocks(int x, int y, int z, int face, int columns, int rows) {
        // the right of whoever looks at the wall
        int rightX = FACING[face][1];
        int rightZ = -FACING[face][0];
        int[][] blocks = new int[columns * rows][];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int along = column - columns / 2;
                blocks[row * columns + column] = new int[] {x + along * rightX, y + rows - 1 - row, z + along * rightZ};
            }
        }
        return blocks;
    }
}
