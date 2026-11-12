package gg.rago.lobby.api;

import org.bukkit.World;

/**
 * What a replay records and where it is shown again: the mode and the map, the world of the game
 * and the part of it that belongs to the game, and how to build the map again for the replay.
 *
 * <p>Two kinds of stage: the blocks of the arena themselves, kept in the replay (a practice duel:
 * the replay is shown in the lobby's own replay world), or a world a game plugin makes again from
 * the map (a Bed Wars game: {@link ReplayStages}, the positions those of the game's world).
 */
public final class ReplaySetup {

    /** The stage whose blocks the replay holds. */
    public static final String BLOCKS = "blocks";

    private final String modeId;
    private final String modeName;
    private final String mapName;
    private final World world;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final boolean bounded;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final String stageKind;
    private final String stageKey;
    private final int[] blocks;

    private ReplaySetup(String modeId, String modeName, String mapName, World world, int originX, int originY, int originZ,
            boolean bounded, int minX, int maxX, int minZ, int maxZ, String stageKind, String stageKey, int[] blocks) {
        this.modeId = modeId;
        this.modeName = modeName;
        this.mapName = mapName;
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.bounded = bounded;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.stageKind = stageKind;
        this.stageKey = stageKey;
        this.blocks = blocks;
    }

    /**
     * A game in a zone of a shared world: everything between minX and maxX, minZ and maxZ
     * (inclusive) is its own. The arena's blocks are given as they are at the start, five numbers
     * a block from the origin (x, y, z, id, data), and are kept in the replay; it is read later,
     * away from the main thread: the array must not change.
     */
    public static ReplaySetup ofBlocks(String modeId, String modeName, String mapName, World world, int originX,
            int originY, int originZ, int minX, int maxX, int minZ, int maxZ, int[] blocks) {
        if (blocks == null || blocks.length % 5 != 0) {
            throw new IllegalArgumentException("five numbers a block: x, y, z, id, data");
        }
        return new ReplaySetup(modeId, modeName, mapName, world, originX, originY, originZ, true, minX, maxX, minZ, maxZ,
                BLOCKS, "", blocks);
    }

    /**
     * A game alone in its world (a copy of a map): the whole world is its own, and the plugin that
     * registered this kind of stage makes the world again for the replay from the key (the map).
     */
    public static ReplaySetup ofStage(String modeId, String modeName, String mapName, World world, String kind, String key) {
        if (BLOCKS.equals(kind)) {
            throw new IllegalArgumentException(BLOCKS + " is the stage of ofBlocks");
        }
        return new ReplaySetup(modeId, modeName, mapName, world, 0, 0, 0, false, 0, 0, 0, 0, kind, key, new int[0]);
    }

    public String modeId() {
        return modeId;
    }

    public String modeName() {
        return modeName;
    }

    public String mapName() {
        return mapName;
    }

    public World world() {
        return world;
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    public int originZ() {
        return originZ;
    }

    /** True if only a zone of the world is the game's (see {@link #contains}). */
    public boolean bounded() {
        return bounded;
    }

    /** True if this block column is the game's. */
    public boolean contains(int x, int z) {
        return !bounded || (x >= minX && x <= maxX && z >= minZ && z <= maxZ);
    }

    public String stageKind() {
        return stageKind;
    }

    public String stageKey() {
        return stageKey;
    }

    /** The arena's blocks from the origin, five numbers a block: x, y, z, id, data (not to be changed). */
    public int[] blocks() {
        return blocks;
    }
}
