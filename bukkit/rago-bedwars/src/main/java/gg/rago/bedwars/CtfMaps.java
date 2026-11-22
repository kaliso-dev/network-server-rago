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
 *   <li><b>CtfCastle</b>, for the CTF of Bed Wars, the « type normal » of 22/11: a large map, castle
 *       against castle, two castles of stone bricks with one middle island between them, the
 *       diamonds and the emerald on it. Each castle has its walls with their walk and their
 *       battlements, four towers, a gate towards the middle, a courtyard with the spawn, the forge,
 *       the shop and the upgrades, and at the back the keep where its flag stands. The players
 *       bridge between the islands, as in Bed Wars.</li>
 * </ul>
 *
 * <p>The flag point of each team is its bed point ({@link BedwarsMap.Team#bed}): the block where
 * the game puts the banner of the flag, on its gold. The plans are built without Bukkit
 * ({@link Plan}), then put in the world. The designs are starting values, to confirm.
 */
final class CtfMaps {

    static final String BRIDGE = "CtfBridge";
    static final String CASTLE = "CtfCastle";
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

    /** CtfCastle: each castle from x 45 (its front, towards the middle) to 75, z -15 to 15. */
    static final int CASTLE_FRONT = 45;
    static final int CASTLE_BACK = 75;
    static final int CASTLE_HALF_Z = 15;
    static final int WALL_TOP = FLOOR + 6;
    static final int TOWER_TOP = FLOOR + 10;
    /** The keep, at the back of the courtyard: from x 64 to 72, z -5 to 5. */
    static final int KEEP_FRONT = 64;
    static final int KEEP_BACK = 72;
    static final int KEEP_HALF_Z = 5;
    static final int CASTLE_FLAG_X = 69;
    static final int CASTLE_SPAWN_X = 56;
    static final int CASTLE_GENERATOR_X = 59;
    /** The middle island, a disc of this radius. */
    static final int MID_RADIUS = 10;

    private CtfMaps() {
    }

    /** True for a map of Capture the Flag: it has no bed, only the modes that name it play on it. */
    static boolean isCtfMap(String world) {
        return BRIDGE.equalsIgnoreCase(world) || CASTLE.equalsIgnoreCase(world);
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

    // --- The castles --------------------------------------------------------------------------------

    /** The plan of CtfCastle. */
    static Plan castlePlan() {
        Plan plan = new Plan();
        for (int side : new int[] {-1, 1}) {
            castle(plan, side, side < 0 ? TeamColor.RED.woolData() : TeamColor.BLUE.woolData());
        }
        middle(plan);
        return plan;
    }

    /**
     * Stone bricks, now and then mossy or cracked (data 1, 2), by place: the same on both castles,
     * mirrored, every time the map is made.
     */
    static int brick(int x, int y, int z) {
        int roll = Math.floorMod(Math.abs(x) * 73_856_093 ^ y * 19_349_663 ^ z * 83_492_791, 10);
        return roll == 0 ? 1 : roll == 1 ? 2 : 0;
    }

    private static void castle(Plan plan, int side, int colour) {
        int front = side * CASTLE_FRONT;
        int back = side * CASTLE_BACK;
        int minX = Math.min(front, back);
        int maxX = Math.max(front, back);
        // the island: grass over dirt and stone, narrower down
        int[] insets = {0, 1, 2, 3, 5, 8};
        for (int i = 0; i < insets.length; i++) {
            int inset = insets[i];
            Material material = i == 0 ? Material.GRASS : i < 3 ? Material.DIRT : Material.STONE;
            plan.fill(minX + inset, FLOOR - i, -CASTLE_HALF_Z + inset, maxX - inset, FLOOR - i, CASTLE_HALF_Z - inset,
                    material, 0);
        }
        // the path of stone bricks from the gate to the keep
        plan.fill(front, FLOOR, -1, side * KEEP_FRONT, FLOOR, 1, Material.SMOOTH_BRICK, 0);
        // the walls, two blocks thick, their walk on top and battlements on the outer row
        for (int x = minX; x <= maxX; x++) {
            for (int z = -CASTLE_HALF_Z; z <= CASTLE_HALF_Z; z++) {
                boolean outer = x == minX || x == maxX || z == -CASTLE_HALF_Z || z == CASTLE_HALF_Z;
                boolean inner = !outer && (x == minX + 1 || x == maxX - 1 || z == -CASTLE_HALF_Z + 1 || z == CASTLE_HALF_Z - 1);
                if (!outer && !inner) {
                    continue;
                }
                for (int y = Y; y <= WALL_TOP; y++) {
                    plan.set(x, y, z, Material.SMOOTH_BRICK, brick(x, y, z));
                }
                if (outer && (x + z) % 2 == 0) {
                    plan.set(x, WALL_TOP + 1, z, Material.SMOOTH_BRICK, 0);
                }
            }
        }
        // the four towers at the corners, 7 by 7, higher, with their battlements and a banner of wool
        for (int tx : new int[] {minX, maxX - 6}) {
            for (int tz : new int[] {-CASTLE_HALF_Z, CASTLE_HALF_Z - 6}) {
                tower(plan, tx, tz, colour);
            }
        }
        // the gate towards the middle, through both rows of the wall: 3 wide, 4 high, bars over it
        plan.clear(front, Y, -1, front + side, FLOOR + 4, 1);
        for (int z = -1; z <= 1; z++) {
            plan.set(front, FLOOR + 5, z, Material.IRON_FENCE);
        }
        // the team's colours hanging on the two front towers, outside, towards the middle
        int outside = front - side;
        for (int z : new int[] {-CASTLE_HALF_Z + 2, CASTLE_HALF_Z - 4}) {
            plan.fill(outside, FLOOR + 5, z, outside, FLOOR + 8, z + 2, Material.WOOL, colour);
        }
        keep(plan, side, colour);
        // lights in the inner row of the walls, on the courtyard side, between the towers: the
        // front wall on each side of the gate, the back one, the two side walls
        for (int z : new int[] {-6, 6}) {
            plan.set(front + side, FLOOR + 3, z, Material.GLOWSTONE);
            plan.set(back - side, FLOOR + 3, z, Material.GLOWSTONE);
        }
        plan.set(back - side, FLOOR + 3, 0, Material.GLOWSTONE);
        for (int x = CASTLE_FRONT + 9; x <= CASTLE_BACK - 9; x += 6) {
            plan.set(side * x, FLOOR + 3, -CASTLE_HALF_Z + 1, Material.GLOWSTONE);
            plan.set(side * x, FLOOR + 3, CASTLE_HALF_Z - 1, Material.GLOWSTONE);
        }
        // the forge on iron, the shop and upgrades on their slabs of the team's wool
        plan.set(side * CASTLE_GENERATOR_X, FLOOR, 0, Material.IRON_BLOCK);
        plan.set(side * CASTLE_SPAWN_X, FLOOR, 6, Material.WOOL, colour);
        plan.set(side * CASTLE_SPAWN_X, FLOOR, -6, Material.WOOL, colour);
    }

    private static void tower(Plan plan, int minX, int minZ, int colour) {
        for (int x = minX; x <= minX + 6; x++) {
            for (int z = minZ; z <= minZ + 6; z++) {
                boolean edge = x == minX || x == minX + 6 || z == minZ || z == minZ + 6;
                for (int y = Y; y <= TOWER_TOP; y++) {
                    plan.set(x, y, z, Material.SMOOTH_BRICK, brick(x, y, z));
                }
                if (edge && (x + z) % 2 == 0) {
                    plan.set(x, TOWER_TOP + 1, z, Material.SMOOTH_BRICK, 0);
                }
            }
        }
        // a lit top, and arrow slits of the team's glass on its faces
        plan.set(minX + 3, TOWER_TOP + 1, minZ + 3, Material.GLOWSTONE);
        for (int y : new int[] {FLOOR + 4, FLOOR + 8}) {
            plan.set(minX + 3, y, minZ, Material.STAINED_GLASS, colour);
            plan.set(minX + 3, y, minZ + 6, Material.STAINED_GLASS, colour);
            plan.set(minX, y, minZ + 3, Material.STAINED_GLASS, colour);
            plan.set(minX + 6, y, minZ + 3, Material.STAINED_GLASS, colour);
        }
    }

    /** The keep at the back of the courtyard, the flag inside, on gold, its door to the courtyard. */
    private static void keep(Plan plan, int side, int colour) {
        int front = side * KEEP_FRONT;
        int back = side * KEEP_BACK;
        int minX = Math.min(front, back);
        int maxX = Math.max(front, back);
        int roof = FLOOR + 8;
        for (int x = minX; x <= maxX; x++) {
            for (int z = -KEEP_HALF_Z; z <= KEEP_HALF_Z; z++) {
                boolean edge = x == minX || x == maxX || z == -KEEP_HALF_Z || z == KEEP_HALF_Z;
                if (edge) {
                    for (int y = Y; y < roof; y++) {
                        plan.set(x, y, z, Material.SMOOTH_BRICK, brick(x, y, z));
                    }
                    if ((x + z) % 2 == 0) {
                        plan.set(x, roof + 1, z, Material.SMOOTH_BRICK, 0);
                    }
                } else {
                    plan.set(x, FLOOR, z, Material.SMOOTH_BRICK, 3);
                }
                plan.set(x, roof, z, edge ? Material.SMOOTH_BRICK : Material.WOOD, edge ? 0 : 1);
            }
        }
        // the door, 3 wide and 3 high, and windows of the team's glass on the sides
        plan.clear(front, Y, -1, front, FLOOR + 3, 1);
        for (int k = KEEP_FRONT + 2; k <= KEEP_BACK - 2; k += 3) {
            plan.set(side * k, FLOOR + 3, -KEEP_HALF_Z, Material.STAINED_GLASS, colour);
            plan.set(side * k, FLOOR + 3, KEEP_HALF_Z, Material.STAINED_GLASS, colour);
        }
        // the light under the roof, the flag on gold, its colour on the wall behind it
        for (int k = KEEP_FRONT + 2; k <= KEEP_BACK - 2; k += 3) {
            plan.set(side * k, roof, -2, Material.GLOWSTONE);
            plan.set(side * k, roof, 2, Material.GLOWSTONE);
        }
        int flagX = side * CASTLE_FLAG_X;
        plan.fill(flagX - 1, FLOOR, -1, flagX + 1, FLOOR, 1, Material.QUARTZ_BLOCK, 1);
        plan.set(flagX, FLOOR, 0, Material.GOLD_BLOCK);
        plan.fill(back - side, FLOOR + 2, -1, back - side, FLOOR + 5, 1, Material.WOOL, colour);
    }

    /** The middle: a disc of grass and stone with ruins, two diamond generators and an emerald one. */
    private static void middle(Plan plan) {
        int[] radii = {MID_RADIUS, MID_RADIUS - 1, MID_RADIUS - 2, MID_RADIUS - 4, MID_RADIUS - 6};
        for (int i = 0; i < radii.length; i++) {
            int r = radii[i];
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z <= r * r + r) {
                        plan.set(x, FLOOR - i, z, i == 0 ? Material.GRASS : i < 2 ? Material.DIRT : Material.STONE, 0);
                    }
                }
            }
        }
        // a ring of stone bricks around the generators
        for (int x = -3; x <= 3; x++) {
            for (int z = -8; z <= 8; z++) {
                if (Math.abs(x) <= 1 && (Math.abs(z) <= 1 || Math.abs(z - 6) <= 1 || Math.abs(z + 6) <= 1)) {
                    plan.set(x, FLOOR, z, Material.SMOOTH_BRICK, 0);
                }
            }
        }
        plan.set(0, FLOOR, 0, Material.EMERALD_BLOCK);
        plan.set(0, FLOOR, 6, Material.DIAMOND_BLOCK);
        plan.set(0, FLOOR, -6, Material.DIAMOND_BLOCK);
        // the ruins: four broken pillars, the two of the south higher (the same for both teams), and a
        // low wall on each side
        for (int[] pillar : new int[][] {{5, 5}, {-5, 5}, {5, -5}, {-5, -5}}) {
            int height = pillar[1] > 0 ? 4 : 2;
            plan.fill(pillar[0], Y, pillar[1], pillar[0], FLOOR + height, pillar[1], Material.SMOOTH_BRICK, 1);
        }
        plan.fill(-1, Y, 9, 1, Y, 9, Material.SMOOTH_BRICK, 2);
        plan.fill(-1, Y, -9, 1, Y, -9, Material.SMOOTH_BRICK, 2);
        plan.set(0, FLOOR + 2, 9, Material.SMOOTH_BRICK, 2);
        plan.set(0, FLOOR + 2, -9, Material.SMOOTH_BRICK, 2);
    }

    /** The points of CtfCastle: each castle's spawn, flag (bed point), forge, shop and upgrades; the middle's generators. */
    static void castleLayout(BedwarsMap map) {
        for (int side : new int[] {-1, 1}) {
            BedwarsMap.Team team = map.team(side < 0 ? TeamColor.RED : TeamColor.BLUE);
            float towardsMiddle = side < 0 ? -90f : 90f;
            team.spawn = new MapPoint(side * CASTLE_SPAWN_X + 0.5, Y, 0.5, towardsMiddle, 0f);
            team.bed = new MapPoint(side * CASTLE_FLAG_X, Y, 0, 0f, 0f);
            team.generator = new MapPoint(side * CASTLE_GENERATOR_X + 0.5, Y, 0.5, 0f, 0f);
            team.shop = new MapPoint(side * CASTLE_SPAWN_X + 0.5, Y, 6.5, 180f, 0f);
            team.upgrades = new MapPoint(side * CASTLE_SPAWN_X + 0.5, Y, -5.5, 0f, 0f);
        }
        map.diamonds().clear();
        map.diamonds().add(new MapPoint(0.5, Y, 6.5, 0f, 0f));
        map.diamonds().add(new MapPoint(0.5, Y, -5.5, 0f, 0f));
        map.emeralds().clear();
        map.emeralds().add(new MapPoint(0.5, Y, 0.5, 0f, 0f));
        map.setSpectators(new MapPoint(0.5, FLOOR + 30, 0.5, 0f, 70f));
        map.setBuildLimit(FLOOR + 22);
        map.setVoidY(FLOOR - 30);
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
