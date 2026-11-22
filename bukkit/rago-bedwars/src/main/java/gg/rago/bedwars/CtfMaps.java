package gg.rago.bedwars;

import gg.rago.lobby.api.VoidGenerator;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/**
 * The maps of Capture the Flag, made by the plugin the first time a mode plays them (as the demo
 * map), red in the west and blue in the east, over the void:
 *
 * <ul>
 *   <li><b>CtfBridge</b>, for the CTF of The Bridge: two bases of quartz face to face, joined by a
 *       bridge of stained clay one block wide and 41 long (red on the red half, blue on the blue
 *       one, white in the middle) that the players break and build on, in 9 blocks of height, as
 *       in The Bridge of Hypixel. Each base has its flag at the back, on gold, in front of a wall
 *       with windows of its colour.</li>
 * </ul>
 *
 * <p>The flag point of each team is its bed point ({@link BedwarsMap.Team#bed}): the block where
 * the game puts the banner of the flag, on its gold. The plans are built without Bukkit
 * ({@link Plan}), then put in the world. The design is a starting value, to confirm.
 */
final class CtfMaps {

    static final String BRIDGE = "CtfBridge";
    /** The floor of the maps: players stand one block above. */
    static final int FLOOR = 64;
    static final int Y = FLOOR + 1;

    /**
     * CtfBridge: the bridge from x -{@value #BRIDGE_HALF} to {@value #BRIDGE_HALF} at z 0, one block
     * wide and 41 long, as the bridge of Hypixel's The Bridge.
     */
    static final int BRIDGE_HALF = 20;
    /** Its bases, from x 21 to 37 (each side), z -9 to 9. */
    static final int BASE_FRONT = 21;
    static final int BASE_BACK = 37;
    static final int BASE_HALF_Z = 9;
    /** Its flags and spawns, from the middle. */
    static final int BRIDGE_FLAG_X = 34;
    static final int BRIDGE_SPAWN_X = 25;
    /**
     * Where its players build: from 4 blocks under the bridge to 4 over it, the 9 blocks of height of
     * The Bridge.
     */
    static final int BRIDGE_BUILD = 4;

    private CtfMaps() {
    }

    /** True for a map of Capture the Flag: it has no bed, only the modes that name it play on it. */
    static boolean isCtfMap(String world) {
        return BRIDGE.equalsIgnoreCase(world);
    }

    /** One block of a plan: a material and its data. */
    static final class Piece {
        final Material material;
        final int data;

        Piece(Material material, int data) {
            this.material = material;
            this.data = data;
        }
    }

    /** The blocks of a map, the last set at a place winning. */
    static final class Plan {
        final Map<Long, Piece> blocks = new LinkedHashMap<>();

        void set(int x, int y, int z, Material material, int data) {
            blocks.put(Instance.key(x, y, z), new Piece(material, data));
        }

        void set(int x, int y, int z, Material material) {
            set(x, y, z, material, 0);
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material material, int data) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        set(x, y, z, material, data);
                    }
                }
            }
        }

        void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        blocks.remove(Instance.key(x, y, z));
                    }
                }
            }
        }

        /** What is at this place, or null (air). */
        Piece at(int x, int y, int z) {
            return blocks.get(Instance.key(x, y, z));
        }

        boolean solid(int x, int y, int z) {
            Piece piece = at(x, y, z);
            return piece != null && piece.material.isSolid();
        }
    }

    // --- The Bridge ----------------------------------------------------------------------------------

    /** The plan of CtfBridge. */
    static Plan bridgePlan() {
        Plan plan = new Plan();
        for (int side : new int[] {-1, 1}) {
            bridgeBase(plan, side, side < 0 ? TeamColor.RED.woolData() : TeamColor.BLUE.woolData());
        }
        for (int x = -BRIDGE_HALF; x <= BRIDGE_HALF; x++) {
            int colour = x < 0 ? TeamColor.RED.woolData() : x > 0 ? TeamColor.BLUE.woolData() : 0;
            plan.set(x, FLOOR, 0, Material.STAINED_CLAY, colour);
        }
        return plan;
    }

    /** A base of The Bridge, on this side (-1 west, 1 east), in this colour. */
    private static void bridgeBase(Plan plan, int side, int colour) {
        int front = side * BASE_FRONT;
        int back = side * BASE_BACK;
        // the island under it, narrower at each layer down
        int[] insets = {0, 1, 2, 4, 6};
        Material[] layers = {Material.QUARTZ_BLOCK, Material.STONE, Material.COBBLESTONE, Material.STONE, Material.STONE};
        int[] layerData = {0, 0, 0, 5, 5};
        for (int i = 0; i < insets.length; i++) {
            int inset = insets[i];
            plan.fill(front + side * inset, FLOOR - i, -BASE_HALF_Z + inset, back - side * inset, FLOOR - i,
                    BASE_HALF_Z - inset, layers[i], layerData[i]);
        }
        // a border of its colour, and the path from the bridge to the flag
        for (int x = Math.min(front, back); x <= Math.max(front, back); x++) {
            plan.set(x, FLOOR, -BASE_HALF_Z, Material.WOOL, colour);
            plan.set(x, FLOOR, BASE_HALF_Z, Material.WOOL, colour);
        }
        plan.fill(back, FLOOR, -BASE_HALF_Z, back, FLOOR, BASE_HALF_Z, Material.WOOL, colour);
        plan.fill(front, FLOOR, -1, side * (BRIDGE_FLAG_X - 2), FLOOR, 1, Material.WOOL, colour);
        // the flag on gold, a ring of chiselled quartz around it
        int flagX = side * BRIDGE_FLAG_X;
        plan.fill(flagX - 1, FLOOR, -1, flagX + 1, FLOOR, 1, Material.QUARTZ_BLOCK, 1);
        plan.set(flagX, FLOOR, 0, Material.GOLD_BLOCK);
        // the wall at the back: quartz, windows of the team's glass, sea lanterns on top
        int wallX = side * (BASE_BACK - 1);
        plan.fill(wallX, Y, -BASE_HALF_Z + 1, wallX, FLOOR + 4, BASE_HALF_Z - 1, Material.QUARTZ_BLOCK, 0);
        for (int z = -BASE_HALF_Z + 3; z <= BASE_HALF_Z - 3; z += 3) {
            plan.fill(wallX, FLOOR + 2, z, wallX, FLOOR + 3, z, Material.STAINED_GLASS, colour);
        }
        for (int z = -BASE_HALF_Z + 1; z <= BASE_HALF_Z - 1; z += 2) {
            plan.set(wallX, FLOOR + 5, z, Material.QUARTZ_BLOCK, 2);
        }
        // pillars at the front corners, lit
        for (int z : new int[] {-BASE_HALF_Z + 1, BASE_HALF_Z - 1}) {
            int x = side * (BASE_FRONT + 1);
            plan.fill(x, Y, z, x, FLOOR + 3, z, Material.QUARTZ_BLOCK, 2);
            plan.set(x, FLOOR + 4, z, Material.SEA_LANTERN);
        }
    }

    /** The points of CtfBridge: the spawns, the flags (bed points), the rest on the spawns (not used). */
    static void bridgeLayout(BedwarsMap map) {
        for (int side : new int[] {-1, 1}) {
            BedwarsMap.Team team = map.team(side < 0 ? TeamColor.RED : TeamColor.BLUE);
            float yaw = side < 0 ? -90f : 90f;
            MapPoint spawn = new MapPoint(side * BRIDGE_SPAWN_X + 0.5, Y, 0.5, yaw, 0f);
            team.spawn = spawn;
            team.bed = new MapPoint(side * BRIDGE_FLAG_X, Y, 0, 0f, 0f);
            team.generator = spawn;
            team.shop = spawn;
            team.upgrades = null;
        }
        map.diamonds().clear();
        map.emeralds().clear();
        map.setSpectators(new MapPoint(0.5, FLOOR + 16, 0.5, 0f, 60f));
        map.setBuildLimit(FLOOR + BRIDGE_BUILD);
        map.setFloorY(FLOOR - BRIDGE_BUILD);
        map.setVoidY(FLOOR - 15);
        map.setEnabled(true);
    }

    // --- In the world ----------------------------------------------------------------------------------

    /**
     * Builds the world of this map and returns true if its folder does not exist yet; the world is
     * saved and unloaded (game copies are made from its files).
     */
    static boolean buildIfMissing(String name, Supplier<Plan> plan) {
        if (new File(Bukkit.getWorldContainer(), name).exists()) {
            return false;
        }
        World world = new WorldCreator(name).generator(new VoidGenerator()).generateStructures(false).createWorld();
        if (world == null) {
            return false;
        }
        world.setSpawnLocation(0, Y + 15, 0);
        build(world, plan.get());
        world.save();
        Bukkit.unloadWorld(world, true);
        return true;
    }

    @SuppressWarnings("deprecation") // Block#setTypeIdAndData: the 1.8 way to set a block with its data
    static void build(World world, Plan plan) {
        for (Map.Entry<Long, Piece> entry : plan.blocks.entrySet()) {
            long key = entry.getKey();
            Piece piece = entry.getValue();
            world.getBlockAt(Instance.xOf(key), Instance.yOf(key), Instance.zOf(key))
                    .setTypeIdAndData(piece.material.getId(), (byte) piece.data, false);
        }
    }
}
