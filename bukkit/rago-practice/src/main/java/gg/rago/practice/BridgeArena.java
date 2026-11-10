package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * The bridge fight, a free for all open to everyone like the block placement arena, in Ranked
 * Bedwars gear (iron armour, stone sword, golden apples) with real damage, and nobody places
 * blocks. On the owner's map (bridge-fight.cartes in config.yml: the lowest floor of "Bridge Fight
 * Plateform") its platforms of wool or stained clay, each of two colours as a top fight platform,
 * over the void, and in the row between them the little islands of the arena, each a booth with its
 * sea lantern on the floor: a click on a portal takes the player from their island to the next one
 * the way they look (from the middle one to the left or the right one, and back), on its sea
 * lantern. A player comes in, and comes
 * back after a death or a fall, in the booth nearest to their platform (the little white island of
 * quartz), on its sea lantern in the middle of the diamond of quartz, facing the platform. Without
 * it, four platforms side by side: a
 * bridge leads from a pad at each end to the tip of its platform, and a walkway along each end
 * links the pads, so a player walks from one platform to another, and comes back at an end of the
 * platform they were on. Out of a fight, the compass takes them to any platform. The kills count in the stats of the mode. The 1v1 with points is
 * {@link BridgeDuel}.
 */
final class BridgeArena extends PracticeGame {

    /**
     * A platform: its name, its two colours (wool data, armour), its middle (x, z from the centre of
     * the arena), what it is made of, and where a player comes in at each end (x, y of the feet, z,
     * yaw), the first end in the half of the first colour.
     */
    static final class Platform {
        final String name;
        final ChatColor chat;
        final int first;
        final int second;
        final Color firstArmour;
        final Color secondArmour;
        final int x;
        final int z;
        final Material block;
        final double[][] ends;
        /** How far across a player may come in, at random, on each side of an end (the pads: 1). */
        final int spread;

        /** A platform of the plugin's own, across the row at this z, its pads at both ends. */
        Platform(String name, ChatColor chat, int first, Color firstArmour, int second, Color secondArmour, int z) {
            this(name, chat, first, firstArmour, second, secondArmour, 0, z, Material.WOOL,
                    new double[][] {{-PAD + 0.5, 1, z + 0.5, -90f}, {PAD + 0.5, 1, z + 0.5, 90f}}, 1);
        }

        Platform(String name, ChatColor chat, int first, Color firstArmour, int second, Color secondArmour, int x, int z,
                Material block, double[][] ends, int spread) {
            this.name = name;
            this.chat = chat;
            this.first = first;
            this.firstArmour = firstArmour;
            this.second = second;
            this.secondArmour = secondArmour;
            this.x = x;
            this.z = z;
            this.block = block;
            this.ends = ends;
            this.spread = spread;
        }

        /** The wool colour of this place of the platform: the first colour on the west half. */
        int colourAt(int x, int z) {
            return x < 0 || (x == 0 && z < 0) ? first : second;
        }

        Platform named(String other) {
            return new Platform(other, chat, first, firstArmour, second, secondArmour, x, z, block, ends, spread);
        }
    }

    /** The platforms, the one a new player goes to on a tie first: across the row, as in the picture. */
    static final Platform[] PLATFORMS = {
        new Platform("Blue & Yellow", ChatColor.BLUE, Shapes.BLUE, Color.BLUE, Shapes.YELLOW, Color.YELLOW, 10),
        new Platform("Red & White", ChatColor.RED, Shapes.RED, Color.RED, Shapes.WHITE, Color.WHITE, 30),
        new Platform("Orange & Black", ChatColor.GOLD, Shapes.ORANGE, Color.ORANGE, Shapes.BLACK, Color.BLACK, -10),
        new Platform("Green & Lime", ChatColor.DARK_GREEN, Shapes.GREEN, Color.GREEN, Shapes.LIME, Color.LIME, -30),
    };
    /** The tips of the platforms are this far from their centre, along the row. */
    static final int HALF_LENGTH = 15;
    /** The centre of the spawn pads, this far from the centre of their platform, a short bridge away. */
    static final int PAD = HALF_LENGTH + 5;
    /** The walkways along the row go this far past the outer platforms. */
    static final int WALK = 1;
    /** How often the platform of each player follows where they are, in ticks. */
    private static final int FOLLOW_TICKS = 10;
    /** Farther than this from the middle of every platform, a player's platform stays as it was. */
    static final double FOLLOW_REACH = PAD + 4;
    /** A player who just came back can't be hurt for this long, unless they hit first. */
    private static final long PROTECTION_MS = 3000L;
    static final String MENU_ITEM = ChatColor.GREEN + "Choose a platform " + ChatColor.GRAY + "(right click)";
    static final String MENU_TITLE = "Bridge fight: platforms";

    /** The barrier, the sea lantern, wool, stained clay: their block ids. */
    static final int BARRIER_ID = 166;
    static final int SEA_LANTERN_ID = 169;
    static final int WOOL_ID = 35;
    static final int CLAY_ID = 159;
    /** On a map, a floor of wool or clay of fewer cells is not a platform (a decoration). */
    static final int MIN_CELLS = 40;
    /** At most this many platforms on a map: one row of the menu. */
    static final int MAX_PLATFORMS = 9;
    /** On a map, a player comes in this many blocks in from a tip, facing the middle. */
    static final int END_IN = 2;
    /** A portal: the booth around its sea lantern, this far from it on x and z, this high from the floor. */
    static final int PORTAL_HALF = 3;
    static final int PORTAL_HEIGHT = 9;
    /** How far a player reaches a portal with a click. */
    static final double PORTAL_REACH = 5;
    /** The islands of one floor: their sea lanterns this many blocks apart in height at most. */
    static final int SAME_FLOOR = 4;
    /** A booth farther than this from the tips of a platform is not where its players come in. */
    static final double BOOTH_REACH = 16;
    /** One portal click a second at most. */
    private static final long PORTAL_PAUSE_MS = 1000L;

    /** The names and chat colours of the wool colours, by data. */
    static final String[] COLOUR_NAMES = {"White", "Orange", "Magenta", "Light Blue", "Yellow", "Lime", "Pink", "Gray",
        "Light Gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black"};
    static final ChatColor[] CHAT_COLOURS = {ChatColor.WHITE, ChatColor.GOLD, ChatColor.LIGHT_PURPLE, ChatColor.AQUA,
        ChatColor.YELLOW, ChatColor.GREEN, ChatColor.LIGHT_PURPLE, ChatColor.DARK_GRAY, ChatColor.GRAY, ChatColor.DARK_AQUA,
        ChatColor.DARK_PURPLE, ChatColor.BLUE, ChatColor.GOLD, ChatColor.DARK_GREEN, ChatColor.RED, ChatColor.DARK_GRAY};

    private final Random random = new Random();
    /** The platforms of this arena: the map's, or the plugin's own. */
    private final List<Platform> platforms;
    /** The portals of the map (x, y of the floor, z from the centre): none on the plugin's own platforms. */
    private final List<int[]> portals;
    private final Map<UUID, Integer> platformOf = new HashMap<>();
    /** The end of their platform each player came back at, by its number. */
    private final Map<UUID, Integer> endOf = new HashMap<>();
    /** The end a player comes in at next (through a portal), instead of one at random. */
    private final Map<UUID, Integer> nextEnd = new HashMap<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final Map<UUID, Long> portalPause = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();

    BridgeArena(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams);
        List<Platform> onMap = arena.map() != null ? platformsOf(arena.map().copy) : Collections.<Platform>emptyList();
        this.platforms = onMap.isEmpty() ? Arrays.asList(PLATFORMS) : onMap;
        this.portals = onMap.isEmpty() ? Collections.<int[]>emptyList() : portalsOf(arena.map().copy);
    }

    /** Half the width of a platform at this place along it, as a top fight platform; -1 past its tips. */
    static int halfWidth(int x) {
        int along = Math.abs(x);
        return along <= 7 ? 4 : along <= 10 ? 3 : along <= 12 ? 2 : along <= 14 ? 1 : along == HALF_LENGTH ? 0 : -1;
    }

    /** The platform with the fewest players, the first one on a tie. */
    static int leastCrowded(int[] players) {
        int best = 0;
        for (int i = 1; i < players.length; i++) {
            if (players[i] < players[best]) {
                best = i;
            }
        }
        return best;
    }

    /** The platform whose middle is nearest to this place (x, z from the centre), the first on a tie. */
    static int nearest(List<Platform> platforms, double x, double z) {
        int best = 0;
        for (int i = 1; i < platforms.size(); i++) {
            if (distance(platforms.get(i), x, z) < distance(platforms.get(best), x, z)) {
                best = i;
            }
        }
        return best;
    }

    /** How far this place (x, z from the centre) is from the middle of the platform, horizontally. */
    static double distance(Platform platform, double x, double z) {
        return Math.hypot(x - (platform.x + 0.5), z - (platform.z + 0.5));
    }

    /** Where the platforms go in the menu: one row, centred, a slot apart when there is room. */
    static int[] menuSlots(int count) {
        int[] slots = new int[count];
        boolean apart = 2 * count - 1 <= 9;
        int first = apart ? (9 - (2 * count - 1)) / 2 : Math.max(0, (9 - count) / 2);
        for (int i = 0; i < count; i++) {
            slots[i] = first + (apart ? 2 * i : i);
        }
        return slots;
    }

    // --- The owner's map ------------------------------------------------------------------------

    /**
     * The platforms of a map: the floors of wool or stained clay at the height of its centre (y 0),
     * {@value #MIN_CELLS} cells or more, each in one piece; the biggest {@value #MAX_PLATFORMS} at
     * most, from west to east, then north to south. Each is of the colour most of its first half
     * is (north, or west, along its length) and of the colour most of the other half is (if the
     * same: the next colour most of it is), and named after them, with where it is when two have
     * the same name. Its ends are {@value #END_IN} blocks in from its tips, facing its middle.
     */
    static List<Platform> platformsOf(MapCopy copy) {
        Map<Long, int[]> cells = new LinkedHashMap<>();
        for (int[] block : copy.blocksOf(WOOL_ID, CLAY_ID)) {
            if (block[1] == 0) {
                cells.put(key(block[0], block[2]), block);
            }
        }
        List<List<int[]>> pieces = new ArrayList<>();
        Map<Long, Boolean> seen = new HashMap<>();
        for (Map.Entry<Long, int[]> cell : cells.entrySet()) {
            if (seen.containsKey(cell.getKey())) {
                continue;
            }
            List<int[]> piece = new ArrayList<>();
            Deque<int[]> next = new ArrayDeque<>();
            next.add(cell.getValue());
            seen.put(cell.getKey(), true);
            while (!next.isEmpty()) {
                int[] at = next.poll();
                piece.add(at);
                for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    long near = key(at[0] + step[0], at[2] + step[1]);
                    int[] neighbour = cells.get(near);
                    if (neighbour != null && !seen.containsKey(near)) {
                        seen.put(near, true);
                        next.add(neighbour);
                    }
                }
            }
            if (piece.size() >= MIN_CELLS) {
                pieces.add(piece);
            }
        }
        pieces.sort(Comparator.comparingInt((List<int[]> piece) -> -piece.size()));
        List<Platform> found = new ArrayList<>();
        for (List<int[]> piece : pieces.subList(0, Math.min(MAX_PLATFORMS, pieces.size()))) {
            found.add(platform(piece, copy));
        }
        found.sort(Comparator.comparingInt((Platform platform) -> platform.x).thenComparingInt(platform -> platform.z));
        return withPlaces(found);
    }

    /** One platform of a map, from its cells (x, y, z, id, data each). */
    private static Platform platform(List<int[]> cells, MapCopy copy) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int[] ids = new int[2];
        for (int[] cell : cells) {
            minX = Math.min(minX, cell[0]);
            maxX = Math.max(maxX, cell[0]);
            minZ = Math.min(minZ, cell[2]);
            maxZ = Math.max(maxZ, cell[2]);
            ids[cell[3] == WOOL_ID ? 0 : 1]++;
        }
        // along z when it is at least as long that way
        boolean alongZ = maxZ - minZ >= maxX - minX;
        int from = alongZ ? minZ : minX;
        int to = alongZ ? maxZ : maxX;
        int middleX = Math.floorDiv(minX + maxX, 2);
        int middleZ = Math.floorDiv(minZ + maxZ, 2);
        int[] firstHalf = new int[16];
        int[] secondHalf = new int[16];
        int[] all = new int[16];
        for (int[] cell : cells) {
            int along = alongZ ? cell[2] : cell[0];
            int colour = cell[4] & 15;
            all[colour]++;
            if (2 * along < from + to) {
                firstHalf[colour]++;
            } else if (2 * along > from + to) {
                secondHalf[colour]++;
            }
        }
        int first = most(firstHalf, -1);
        int second = most(secondHalf, -1);
        if (second == first) {
            int other = most(all, first);
            second = all[other] > 0 ? other : first;
        }
        String name = first == second ? COLOUR_NAMES[first] : COLOUR_NAMES[first] + " & " + COLOUR_NAMES[second];
        double[][] ends = {end(cells, copy, alongZ, from, 1, middleX, middleZ), end(cells, copy, alongZ, to, -1, middleX, middleZ)};
        return new Platform(name, CHAT_COLOURS[first], first, armour(first), second, armour(second), middleX, middleZ,
                ids[0] >= ids[1] ? Material.WOOL : Material.STAINED_CLAY, ends, 0);
    }

    /** The colour most counted, other than this one; the first on a tie. */
    private static int most(int[] counts, int except) {
        int best = except == 0 ? 1 : 0;
        for (int colour = 0; colour < counts.length; colour++) {
            if (colour != except && counts[colour] > counts[best]) {
                best = colour;
            }
        }
        return best;
    }

    /**
     * Where a player comes in at the end of a platform at this tip: the cell {@value #END_IN} rows in
     * (or the first row in with a cell to stand on), the nearest to the middle line, facing inwards.
     */
    private static double[] end(List<int[]> cells, MapCopy copy, boolean alongZ, int tip, int inwards, int middleX, int middleZ) {
        float yaw = alongZ ? (inwards > 0 ? 0f : 180f) : (inwards > 0 ? -90f : 90f);
        int[] best = null;
        for (int in = END_IN; best == null && in >= 0; in--) {
            int row = tip + inwards * in;
            for (int[] cell : cells) {
                int along = alongZ ? cell[2] : cell[0];
                if (along != row || copy.blockAt(cell[0], 1, cell[2]) != 0 || copy.blockAt(cell[0], 2, cell[2]) != 0) {
                    continue;
                }
                int across = alongZ ? Math.abs(cell[0] - middleX) : Math.abs(cell[2] - middleZ);
                if (best == null || across < (alongZ ? Math.abs(best[0] - middleX) : Math.abs(best[2] - middleZ))) {
                    best = cell;
                }
            }
        }
        if (best == null) {
            return new double[] {middleX + 0.5, 1, middleZ + 0.5, yaw};
        }
        return new double[] {best[0] + 0.5, 1, best[2] + 0.5, yaw};
    }

    @SuppressWarnings("deprecation") // DyeColor#getByWoolData: the 1.8 wool data
    static Color armour(int woolData) {
        return DyeColor.getByWoolData((byte) woolData).getColor();
    }

    /** Two platforms of the same name get where they are: north and south, or west and east; more get a number. */
    static List<Platform> withPlaces(List<Platform> platforms) {
        Map<String, List<Integer>> byName = new LinkedHashMap<>();
        for (int i = 0; i < platforms.size(); i++) {
            byName.computeIfAbsent(platforms.get(i).name, name -> new ArrayList<>()).add(i);
        }
        Platform[] named = platforms.toArray(new Platform[0]);
        for (List<Integer> same : byName.values()) {
            if (same.size() == 2) {
                Platform a = named[same.get(0)];
                Platform b = named[same.get(1)];
                boolean northSouth = Math.abs(a.z - b.z) >= Math.abs(a.x - b.x);
                boolean aFirst = northSouth ? a.z <= b.z : a.x <= b.x;
                String low = northSouth ? " (north)" : " (west)";
                String high = northSouth ? " (south)" : " (east)";
                named[same.get(0)] = a.named(a.name + (aFirst ? low : high));
                named[same.get(1)] = b.named(b.name + (aFirst ? high : low));
            } else if (same.size() > 2) {
                for (int n = 0; n < same.size(); n++) {
                    named[same.get(n)] = named[same.get(n)].named(named[same.get(n)].name + " " + (n + 1));
                }
            }
        }
        return new ArrayList<>(Arrays.asList(named));
    }

    /** The portals of a map: one for each column of sea lanterns, at its lowest one (x, y, z from the centre). */
    static List<int[]> portalsOf(MapCopy copy) {
        Map<Long, int[]> columns = new LinkedHashMap<>();
        for (int[] block : copy.blocksOf(SEA_LANTERN_ID)) {
            long column = key(block[0], block[2]);
            int[] there = columns.get(column);
            if (there == null || block[1] < there[1]) {
                columns.put(column, new int[] {block[0], block[1], block[2]});
            }
        }
        return new ArrayList<>(columns.values());
    }

    /**
     * The island a portal takes a player to from this one (its number in the portals), looking this
     * way: the nearest other island of the floor ahead of them (from the middle one, the left or the
     * right one); with none ahead, the nearest one (from an outer island, the middle one). -1 when
     * the island is alone on its floor.
     */
    static int nextIsland(List<int[]> portals, int from, float yaw) {
        double r = Math.toRadians(yaw);
        double wayX = -Math.sin(r);
        double wayZ = Math.cos(r);
        int[] here = portals.get(from);
        int ahead = -1;
        int nearest = -1;
        double aheadDistance = Double.MAX_VALUE;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < portals.size(); i++) {
            int[] there = portals.get(i);
            if (i == from || Math.abs(there[1] - here[1]) > SAME_FLOOR) {
                continue;
            }
            double dx = there[0] - here[0];
            double dz = there[2] - here[2];
            double distance = Math.hypot(dx, dz);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = i;
            }
            if (dx * wayX + dz * wayZ > 0 && distance < aheadDistance) {
                aheadDistance = distance;
                ahead = i;
            }
        }
        return ahead >= 0 ? ahead : nearest;
    }

    /** The booth where the players of a platform come in: the one nearest to a tip of it, within {@value #BOOTH_REACH} blocks; -1 if none. */
    static int boothOf(Platform platform, List<int[]> portals) {
        int best = -1;
        double nearest = BOOTH_REACH;
        for (int i = 0; i < portals.size(); i++) {
            double distance = endDistance(platform.ends[nearestEnd(platform, portals.get(i))], portals.get(i));
            if (distance <= nearest) {
                nearest = distance;
                best = i;
            }
        }
        return best;
    }

    /** The yaw of a player at (x, z) looking at (toX, toZ): 0 to the south (+z), 90 to the west. */
    static float yawTo(double x, double z, double toX, double toZ) {
        return (float) Math.toDegrees(Math.atan2(-(toX - x), toZ - z));
    }

    /** True if this point (x, y, z from the centre) is in the booth of a portal. */
    static boolean inBooth(List<int[]> portals, double x, double y, double z) {
        for (int[] portal : portals) {
            double[][] box = booth(portal);
            if (x >= box[0][0] && x < box[1][0] && y >= box[0][1] && y < box[1][1] && z >= box[0][2] && z < box[1][2]) {
                return true;
            }
        }
        return false;
    }

    /** The end of the platform nearest to the portal. */
    static int nearestEnd(Platform platform, int[] portal) {
        int best = 0;
        for (int i = 1; i < platform.ends.length; i++) {
            if (endDistance(platform.ends[i], portal) < endDistance(platform.ends[best], portal)) {
                best = i;
            }
        }
        return best;
    }

    private static double endDistance(double[] end, int[] portal) {
        return Math.hypot(end[0] - (portal[0] + 0.5), end[2] - (portal[2] + 0.5));
    }

    /** The corners of the booth of a portal: x, y, z from the centre, the least and the most. */
    static double[][] booth(int[] portal) {
        return new double[][] {{portal[0] - PORTAL_HALF, portal[1], portal[2] - PORTAL_HALF},
            {portal[0] + PORTAL_HALF + 1, portal[1] + PORTAL_HEIGHT, portal[2] + PORTAL_HALF + 1}};
    }

    /** True if the line of sight from the eye, this way (of length 1), meets the box within this reach. */
    static boolean aims(double[] eye, double[] way, double[] min, double[] max, double reach) {
        double near = 0;
        double far = reach;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(way[i]) < 1e-9) {
                if (eye[i] < min[i] || eye[i] > max[i]) {
                    return false;
                }
                continue;
            }
            double t1 = (min[i] - eye[i]) / way[i];
            double t2 = (max[i] - eye[i]) / way[i];
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
            if (near > far) {
                return false;
            }
        }
        return true;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    // --- The game -------------------------------------------------------------------------------

    @Override
    void build() {
        int from = Integer.MAX_VALUE;
        int to = Integer.MIN_VALUE;
        for (Platform platform : PLATFORMS) {
            buildPlatform(arena, platform.z, platform);
            from = Math.min(from, platform.z - 1 - WALK);
            to = Math.max(to, platform.z + 1 + WALK);
        }
        // the walkways along the row, one at each end through the pads: every platform on foot
        for (int end = -1; end <= 1; end += 2) {
            for (int z = from; z <= to; z++) {
                if (!onPad(z)) {
                    arena.build(end * PAD, 0, z, Material.WOOL, Shapes.LIGHT_GRAY);
                }
            }
        }
    }

    /** True if this place across the row is on the pads of a platform. */
    static boolean onPad(int z) {
        for (Platform platform : PLATFORMS) {
            if (Math.abs(z - platform.z) <= 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * A platform centred this far across the row, the pads at its ends and the bridges from the
     * pads to its tips, each end of the colour of its half.
     */
    static void buildPlatform(Arena arena, int z0, Platform platform) {
        for (int x = -HALF_LENGTH; x <= HALF_LENGTH; x++) {
            for (int z = -halfWidth(x); z <= halfWidth(x); z++) {
                arena.build(x, 0, z0 + z, Material.WOOL, platform.colourAt(x, z));
            }
        }
        for (int end = -1; end <= 1; end += 2) {
            int colour = end < 0 ? platform.first : platform.second;
            Shapes.floor(arena, end * PAD - 1, z0 - 1, end * PAD + 1, z0 + 1, 0, Material.WOOL, colour);
            for (int along = HALF_LENGTH + 1; along < PAD - 1; along++) {
                arena.build(end * along, 0, z0, Material.WOOL, colour);
            }
        }
    }

    private int[] crowd() {
        int[] players = new int[platforms.size()];
        for (Player player : online()) {
            Integer platform = platformOf.get(player.getUniqueId());
            if (platform != null) {
                players[platform]++;
            }
        }
        return players;
    }

    private Platform platformOf(UUID player) {
        Integer platform = platformOf.get(player);
        if (platform == null) {
            platform = leastCrowded(crowd());
            platformOf.put(player, platform);
        }
        return platforms.get(platform);
    }

    @Override
    Location spawnOf(UUID player) {
        Platform platform = platformOf(player);
        Integer chosen = nextEnd.remove(player);
        int booth = chosen == null ? boothOf(platform, portals) : -1;
        if (booth >= 0) {
            // in the booth, on the sea lantern, facing the platform
            int[] portal = portals.get(booth);
            int end = nearestEnd(platform, portal);
            endOf.put(player, end);
            protectedUntil.put(player, System.currentTimeMillis() + PROTECTION_MS);
            double[] tip = platform.ends[end];
            return arena.at(portal[0] + 0.5, portal[1] + 1, portal[2] + 0.5, yawTo(portal[0] + 0.5, portal[2] + 0.5, tip[0], tip[2]));
        }
        int end = chosen != null ? chosen : random.nextInt(platform.ends.length);
        endOf.put(player, end);
        protectedUntil.put(player, System.currentTimeMillis() + PROTECTION_MS);
        double[] at = platform.ends[end];
        double z = at[2] + (platform.spread > 0 ? random.nextInt(2 * platform.spread + 1) - platform.spread : 0);
        // facing the platform
        return arena.at(at[0], at[1], z, (float) at[3]);
    }

    @Override
    int countdownSeconds() {
        return 0;
    }

    @Override
    boolean freezes() {
        return false;
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    boolean counts() {
        return false;
    }

    @Override
    double fallY() {
        return arena.floorY() - 12;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.STONE_SWORD), new ItemStack(Material.GOLDEN_APPLE, 2)};
    }

    @Override
    void armour(Player player) {
        Platform platform = platformOf(player.getUniqueId());
        ironArmour(player, endOf.getOrDefault(player.getUniqueId(), 0) == 0 ? platform.firstArmour : platform.secondArmour);
    }

    @Override
    void afterKit(Player player) {
        PlayerInventory inventory = player.getInventory();
        Platform platform = platformOf(player.getUniqueId());
        ItemStack menu = named(Material.COMPASS, 1, 0, MENU_ITEM, ChatColor.GRAY + "Now: " + platform.chat + platform.name);
        ItemStack there = inventory.getItem(8);
        inventory.setItem(8, menu);
        if (there != null && there.getType() != Material.AIR) {
            inventory.addItem(there);
        }
    }

    @Override
    void interact(Player player, PlayerInteractEvent event) {
        Action action = event.getAction();
        if (isNamed(event.getItem(), Material.COMPASS, MENU_ITEM)) {
            event.setCancelled(true);
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                openMenu(player);
            }
            return;
        }
        int portal = -1;
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            portal = aimedPortal(player);
        } else if (action == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            portal = portalOf(event.getClickedBlock());
        }
        if (portal >= 0) {
            event.setCancelled(true);
            takePortal(player, portal);
        }
    }

    /** The portal the player looks at, within reach; -1 if none. */
    private int aimedPortal(Player player) {
        Location eye = player.getEyeLocation();
        double[] from = arena.offset(eye);
        org.bukkit.util.Vector way = eye.getDirection();
        double[] direction = {way.getX(), way.getY(), way.getZ()};
        for (int i = 0; i < portals.size(); i++) {
            double[][] box = booth(portals.get(i));
            if (aims(from, direction, box[0], box[1], PORTAL_REACH)) {
                return i;
            }
        }
        return -1;
    }

    /** The portal whose booth holds this block; -1 if none. */
    private int portalOf(Block block) {
        double[] at = arena.offset(block.getLocation());
        for (int i = 0; i < portals.size(); i++) {
            double[][] box = booth(portals.get(i));
            if (at[0] >= box[0][0] && at[0] < box[1][0] && at[1] >= box[0][1] && at[1] < box[1][1] && at[2] >= box[0][2]
                    && at[2] < box[1][2]) {
                return i;
            }
        }
        return -1;
    }

    /** Through the portal to the next island of the arena, the way the player looks, on its sea lantern. */
    private void takePortal(Player player, int portal) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (portalPause.getOrDefault(id, 0L) > now) {
            return;
        }
        portalPause.put(id, now + PORTAL_PAUSE_MS);
        if (killerOf(id) != null) {
            player.sendMessage(ChatColor.RED + "You can't take a portal in the middle of a fight.");
            return;
        }
        float yaw = player.getLocation().getYaw();
        int target = nextIsland(portals, portal, yaw);
        if (target < 0) {
            openMenu(player);
            return;
        }
        int[] island = portals.get(target);
        player.setFallDistance(0f);
        player.teleport(arena.at(island[0] + 0.5, island[1] + 1, island[2] + 0.5, yaw));
        sound(player, Sound.ENDERMAN_TELEPORT, 1.2f);
    }

    private void openMenu(Player player) {
        int[] crowd = crowd();
        int[] slots = menuSlots(platforms.size());
        Integer mine = platformOf.get(player.getUniqueId());
        ItemStack[] items = new ItemStack[9];
        for (int i = 0; i < platforms.size(); i++) {
            Platform platform = platforms.get(i);
            String count = ChatColor.GRAY + "Players: " + ChatColor.WHITE + crowd[i];
            String what = mine != null && mine == i ? ChatColor.GREEN + "You fight here." : ChatColor.YELLOW + "Click to go there.";
            items[slots[i]] = named(platform.block, Math.max(1, crowd[i]), platform.first, platform.chat + platform.name,
                    count, what);
        }
        player.openInventory(new GameMenu(this, MENU_TITLE, items).getInventory());
    }

    @Override
    void menuClick(Player player, int slot) {
        int[] slots = menuSlots(platforms.size());
        int chosen = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                chosen = i;
            }
        }
        if (chosen < 0) {
            return;
        }
        player.closeInventory();
        UUID id = player.getUniqueId();
        Integer mine = platformOf.get(id);
        if (mine != null && mine == chosen) {
            player.sendMessage(ChatColor.GRAY + "You already fight on this platform.");
            return;
        }
        if (killerOf(id) != null) {
            player.sendMessage(ChatColor.RED + "You can't change platform in the middle of a fight.");
            return;
        }
        go(player, chosen, -1);
    }

    /** The player goes to this platform, at this end of it (-1: one at random). */
    private void go(Player player, int platform, int end) {
        UUID id = player.getUniqueId();
        Integer was = platformOf.put(id, platform);
        if (was == null || was != platform) {
            streaks.remove(id);
        }
        if (end >= 0) {
            nextEnd.put(id, end);
        }
        Platform there = platforms.get(platform);
        player.sendMessage(ChatColor.GRAY + "You go to the " + there.chat + there.name + ChatColor.GRAY + " platform.");
        placeAtStart(player);
        sidebars();
    }

    @Override
    boolean refills() {
        return false;
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        long now = System.currentTimeMillis();
        if (attacker != null) {
            // hitting first ends one's own protection
            protectedUntil.remove(attacker.getUniqueId());
        }
        return protectedUntil.getOrDefault(victim.getUniqueId(), 0L) <= now;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        player.sendMessage(ChatColor.RED + (portals.isEmpty() ? "No blocks in the bridge fight: walk the bridges."
                : "No blocks in the bridge fight: take the portals."));
        return false;
    }

    /** The platform of each player follows where they are: they come back on it. */
    @Override
    void tick() {
        if (tickCount % FOLLOW_TICKS != 0) {
            return;
        }
        boolean moved = false;
        for (Player player : online()) {
            double[] at = arena.offset(player.getLocation());
            int platform = nearest(platforms, at[0], at[2]);
            if (at[1] < -1 || distance(platforms.get(platform), at[0], at[2]) > FOLLOW_REACH) {
                continue;
            }
            if (inBooth(portals, at[0], at[1], at[2])) {
                // a booth is between two platforms: the player's stays the one they came for
                continue;
            }
            UUID id = player.getUniqueId();
            Integer was = platformOf.put(id, platform);
            if (was == null || was != platform) {
                moved = true;
                if (was != null) {
                    streaks.remove(id);
                    player.sendMessage(ChatColor.GRAY + "Now on the " + platforms.get(platform).chat + platforms.get(platform).name
                            + ChatColor.GRAY + " platform.");
                }
            }
        }
        if (moved) {
            sidebars();
        }
    }

    @Override
    void intro(Player player) {
        if (portals.isEmpty()) {
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Bridge fight" + ChatColor.GRAY
                    + " - four platforms, everyone in one arena, real damage, no blocks. Walk the bridges from"
                    + " one platform to another: you come back on the pads of the one you were on.");
        } else {
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Bridge fight" + ChatColor.GRAY + " - " + platforms.size()
                    + " platforms, everyone in one arena, real damage, no blocks. Click a portal to go to the next island"
                    + " of the arena, the way you look: you come back in the quartz booth next to your platform.");
        }
        player.sendMessage(ChatColor.GRAY + "The compass takes you to a platform (not in a fight). /spawn takes you back to the lobby.");
        for (Player other : online()) {
            if (!other.equals(player)) {
                other.sendMessage(ChatColor.GRAY + player.getDisplayName() + " joined the bridge fight.");
            }
        }
    }

    @Override
    void fell(Player player) {
        out(player);
    }

    @Override
    void lethal(Player victim, Player killer) {
        out(victim);
    }

    /** The player is out (a fall, a death): the kill to whoever hit them last, and back on their platform. */
    private void out(Player victim) {
        UUID by = killerOf(victim.getUniqueId());
        Player killer = by == null ? null : Bukkit.getPlayer(by);
        if (killer != null && host.gameOf(by) == this && !killer.equals(victim)) {
            plugin.lobby().recordKill(mode.id(), killer.getUniqueId(), victim.getUniqueId());
            kills.merge(by, 1, Integer::sum);
            int streak = streaks.merge(by, 1, Integer::sum);
            broadcast(ChatColor.RED + victim.getDisplayName() + ChatColor.GRAY + " was killed by " + ChatColor.GREEN
                    + killer.getDisplayName() + (streak >= 3 ? ChatColor.GOLD + " (" + streak + " in a row)" : ""), null);
            sound(killer, Sound.ORB_PICKUP, 1.2f);
        } else {
            victim.sendMessage(ChatColor.GRAY + "You fell.");
        }
        streaks.remove(victim.getUniqueId());
        forgetHits(victim.getUniqueId());
        placeAtStart(victim);
        sidebars();
    }

    @Override
    void leave(Player player, boolean toLobby) {
        UUID id = player.getUniqueId();
        host.release(id);
        streaks.remove(id);
        platformOf.remove(id);
        endOf.remove(id);
        nextEnd.remove(id);
        protectedUntil.remove(id);
        portalPause.remove(id);
        if (phase != Phase.ENDED) {
            if (online().isEmpty()) {
                finish(-1, null);
            } else {
                broadcast(ChatColor.GRAY + player.getDisplayName() + " left the bridge fight.", null);
                sidebars();
            }
        }
        if (toLobby) {
            plugin.lobby().sendToLobby(player);
        }
    }

    @Override
    List<String> sidebar(UUID viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.AQUA + "Bridge fight");
        lines.add("");
        Platform platform = platformOf(viewer);
        lines.add(Duel.cut(ChatColor.WHITE + "Platform: " + platform.chat + platform.name));
        lines.add(ChatColor.WHITE + "On it: " + ChatColor.GREEN + crowd()[platformOf.get(viewer)]
                + ChatColor.GRAY + " / " + online().size());
        lines.add(ChatColor.WHITE + "Your kills: " + ChatColor.GREEN + kills.getOrDefault(viewer, 0));
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streaks.getOrDefault(viewer, 0));
        UUID top = null;
        int most = 0;
        for (Player player : online()) {
            int count = kills.getOrDefault(player.getUniqueId(), 0);
            if (count > most) {
                most = count;
                top = player.getUniqueId();
            }
        }
        if (top != null) {
            lines.add("");
            lines.add(Duel.cut(ChatColor.WHITE + "Top: " + ChatColor.GOLD + nameOf(top) + ChatColor.GRAY + " " + most + " kills"));
        }
        lines.add("");
        lines.add(ChatColor.WHITE + "Ping: " + Ping.text(Bukkit.getPlayer(viewer)));
        return lines;
    }
}
