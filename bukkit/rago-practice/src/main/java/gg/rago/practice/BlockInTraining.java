package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Block in practice, as on the Ultimate Block-In Practice map: the player stands on a ring of glass
 * ten blocks over a bed and its defense (end stone, glass, then wool on one side and wood on the
 * other), drops on it, blocks themselves in and digs down to the bed. The time runs from the first
 * block placed (or broken); the chat then gives how long each layer took, as the map did. The time
 * goes to the records only if the player blocked themselves in: once, the four sides at their feet
 * and at their head closed, one of them a block of theirs (else breaking the bed as fast as
 * possible, without a block, would top the board). The island is the map's own when the server has
 * it (see {@link MapCopy}), else a plain one.
 *
 * <p>The RBW level (the owner's ask of 27/11) plays the same on his map Block In Practice RBW: its
 * red bed with the defense of a game of RBW (end stone, then red glass), copied beside the island
 * ({@link #RBW_X} blocks east of it), a ring of glass ten blocks over it as on the island.
 *
 * <p>As in a game, an enemy comes two seconds after the start (a setting turns it off), at one side
 * of the player (see {@link BlockInEnemy}): it hits them only through that side open, breaks the
 * block they put there in the time a player takes, with the cracks, and has to break again a block
 * put back. The defense itself is never touched.
 */
final class BlockInTraining extends Training {

    /** The island of the training: the copy of the map (or null), how the bed lies, the two starts. */
    static final class Island {
        final MapCopy copy;
        /** The head of the bed, one step from its foot at (0, 1, 0): (-1, 0) on the map. */
        final int hx;
        final int hz;
        /** Each start: x, y, z (the feet, from the foot of the bed on the floor), yaw, pitch. */
        final double[][] starts;

        /** The RBW level's own island, or null: the RBW level plays on this one. */
        final Rbw rbw;

        Island(MapCopy copy, int hx, int hz, double[][] starts) {
            this(copy, hx, hz, starts, null);
        }

        Island(MapCopy copy, int hx, int hz, double[][] starts, Rbw rbw) {
            this.copy = copy;
            this.hx = hx;
            this.hz = hz;
            this.starts = starts;
            this.rbw = rbw;
        }
    }

    /**
     * The island of the RBW level: the owner's map Block In Practice RBW (block-in.rbw), its bed and
     * its defense left out of the copy and put back at each attempt. Everything is in the zone, the
     * foot of its bed at ({@link #RBW_X}, {@link #BED_Y}, 0).
     */
    static final class Rbw {
        final MapCopy copy;
        /** The head of its bed, one step from its foot. */
        final int hx;
        final int hz;
        /** Its defense: x, y, z in the zone, block id, data each. */
        final List<int[]> defense;

        Rbw(MapCopy copy, int hx, int hz, List<int[]> defense) {
            this.copy = copy;
            this.hx = hx;
            this.hz = hz;
            this.defense = defense;
        }
    }

    /** The RBW island stands this far east of the island, in the same zone. */
    static final int RBW_X = 64;
    /** The blocks of a defense: end stone, glass, stained glass, wool, planks, obsidian, clay, hardened clay. */
    static final int[] DEFENSE_IDS = {121, 20, 95, 35, 5, 49, 159, 172};
    /** How far from the bed a block of the RBW map belongs to its defense, and how high over it. */
    static final int DEFENSE_REACH = 3;

    /** The bed's foot: the floor of the zone is the island's ground, the bed on it. */
    static final int BED_Y = 1;
    /** The ring of glass of the plain island, this high over the floor, as on the map. */
    static final int PLATFORM_Y = 10;
    /** A minute after the start, the attempt is over. */
    private static final long LIMIT_MS = 60000L;
    private static final String[] SPLITS = {"Wood/Wool", "Glass", "Endstone", "Bed"};

    private final Island island;
    private final List<List<int[]>> layers;
    /** The blocks of the defense of this round: only their first break counts for a split. */
    private final Set<Block> defense = new HashSet<>();
    private long started;
    /** When each layer was first broken (wood/wool, glass, end stone, bed), 0 until then. */
    private final long[] splits = new long[4];
    private boolean done;
    /** True once the player blocked themselves in this round: only then does the time count. */
    private boolean blockedIn;
    /** The enemy who hits the player and breaks their block-in: on, or off. */
    private final Setting enemy;
    /** The enemy comes this long after the start. */
    static final long ENEMY_AFTER_MS = 2000L;
    private final java.util.Random random = new java.util.Random();
    /** The enemy itself, at one side of the player. */
    private final BlockInEnemy attacker;
    /**
     * Where the player stands, for the enemy: the block of their feet when they were last on the
     * ground (a jump, or the push of a hit, does not take them away from the block it breaks).
     */
    private Block standing;
    /** The number of the cracks of the blocks the enemy breaks. */
    private final int cracksId = BlockCracks.newId();
    /** The four sides around the player, at their feet and head. */
    private static final int[][] SIDES = BlockInEnemy.SIDES;

    /** The defenses to break into, timed. */
    static final Variant[] DEFENSES = {
        new Variant("wood", "3 layers, wood side", true, ""),
        new Variant("wool", "3 layers, wool side", true, ""),
        new Variant("two", "2 layers", true, ""),
        new Variant("rbw", "RBW defense", true, "")};

    BlockInTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, Island island) {
        super(host, mode, arena, teams, DEFENSES);
        this.island = island;
        this.layers = Shapes.defense(0, BED_Y, 0, island.hx, island.hz, 3);
        this.enemy = toggle("Enemy (hits, breaks)", true);
        this.attacker = new BlockInEnemy(random, block -> arena.isPlaced(block) && !defense.contains(block),
                new BlockInEnemy.Hands() {
                    @Override
                    public void hit(int[] side) {
                        Player player = player();
                        if (player != null) {
                            enemyHits(player, side);
                        }
                    }

                    @Override
                    public void cracks(Block block, int stage) {
                        BlockCracks.show(online(), cracksId, block, stage, plugin.getLogger());
                    }

                    @Override
                    public void breaks(Block block) {
                        @SuppressWarnings("deprecation") // the block id of the break effect, as 1.8 takes it
                        int id = block.getTypeId();
                        block.getWorld().playEffect(block.getLocation(), org.bukkit.Effect.STEP_SOUND, id);
                        arena.restore(block);
                    }
                });
    }

    /** The places of the bed and of its three layers: the copy of the map leaves them out. */
    static List<int[]> defensePlaces(int hx, int hz) {
        List<int[]> places = new ArrayList<>();
        places.add(new int[] {0, BED_Y, 0});
        places.add(new int[] {hx, BED_Y, hz});
        for (List<int[]> layer : Shapes.defense(0, BED_Y, 0, hx, hz, 3)) {
            places.addAll(layer);
        }
        return places;
    }

    /**
     * The block of a place of the defense: end stone, then white stained glass, then wool on one
     * side of the bed and oak planks on the other, as on the map.
     */
    static Material layerType(int layer, int[] place, int hx, int hz) {
        if (layer == 0) {
            return Material.ENDER_STONE;
        }
        if (layer == 1) {
            return Material.STAINED_GLASS;
        }
        int side = hz == 0 ? place[2] : -place[0];
        return side < 0 ? Material.WOOL : Material.WOOD;
    }

    private boolean twoLayers() {
        return current().id.equals("two");
    }

    /** True on the RBW level, with its map: the RBW island, its bed and its defense. */
    private boolean rbw() {
        return current().id.equals("rbw") && island.rbw != null;
    }

    /** The x of the foot of the bed of this level, in the zone. */
    private int bedX() {
        return rbw() ? RBW_X : 0;
    }

    /**
     * (x, z) of the island turned for a bed whose head goes (hx, hz), from the island's bed whose head
     * goes west (-1, 0): the ring and the starts of the island fit any bed.
     */
    static int[] turned(int x, int z, int hx, int hz) {
        return new int[] {-hx * x + hz * z, -hz * x - hx * z};
    }

    /** A yaw of the island turned the same way. */
    static float turnedYaw(double yaw, int hx, int hz) {
        double r = Math.toRadians(yaw);
        double dx = -Math.sin(r);
        double dz = Math.cos(r);
        double tx = -hx * dx + hz * dz;
        double tz = -hz * dx - hx * dz;
        return (float) Math.toDegrees(Math.atan2(-tx, tz));
    }

    /** The starts of the RBW level: those of the plain island, turned to its bed, in the zone. */
    static double[][] rbwStarts(int hx, int hz) {
        double[][] plain = {{4.5, 11, 4.5, 135, 60}, {4.5, 11, -3.5, 45, 60}};
        double[][] starts = new double[plain.length][];
        for (int i = 0; i < plain.length; i++) {
            int[] at = turned((int) Math.floor(plain[i][0]), (int) Math.floor(plain[i][2]), hx, hz);
            starts[i] = new double[] {RBW_X + at[0] + 0.5, plain[i][1], at[1] + 0.5, turnedYaw(plain[i][3], hx, hz), plain[i][4]};
        }
        return starts;
    }

    @Override
    String describe() {
        return "drop on the defense, block yourself in and dig to the bed. Timed from your first block; the time"
                + " counts only once you have blocked yourself in.";
    }

    @Override
    void build() {
        if (island.copy != null) {
            island.copy.build(arena);
        } else {
            Shapes.island(arena, -7, -6, 6, 6, Material.SANDSTONE, 0, Material.STONE, 0);
            Shapes.walls(arena, -5, -4, 4, 4, PLATFORM_Y, 1, Material.GLASS, 0);
        }
        if (island.rbw != null) {
            island.rbw.copy.build(arena);
            // the ring of glass of the island, turned to the RBW bed
            int[] a = turned(-5, -4, island.rbw.hx, island.rbw.hz);
            int[] b = turned(4, 4, island.rbw.hx, island.rbw.hz);
            Shapes.walls(arena, RBW_X + Math.min(a[0], b[0]), Math.min(a[1], b[1]), RBW_X + Math.max(a[0], b[0]),
                    Math.max(a[1], b[1]), PLATFORM_Y, 1, Material.GLASS, 0);
        }
    }

    @Override
    Location spawnOf(UUID player) {
        double[] start = rbw() ? rbwStarts(island.rbw.hx, island.rbw.hz)[0]
                : island.starts[current().id.equals("wool") && island.starts.length > 1 ? 1 : 0];
        Location at = arena.at(start[0], start[1], start[2], (float) start[3]);
        at.setPitch((float) start[4]);
        return at;
    }

    @Override
    double fallY() {
        return arena.floorY() - 12;
    }

    @Override
    ItemStack[] kit() {
        ItemStack pickaxe = new ItemStack(Material.GOLD_PICKAXE);
        pickaxe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 3);
        ItemStack axe = new ItemStack(Material.IRON_AXE);
        axe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 2);
        return new ItemStack[] {stack(Material.WOOL), pickaxe, axe, new ItemStack(Material.SHEARS)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        started = 0;
        attacker.reset();
        standing = null;
        done = false;
        blockedIn = false;
        Arrays.fill(splits, 0L);
        defense.clear();
        if (rbw()) {
            // the RBW level: the bed of its map and its defense, as the map has them
            Rbw r = island.rbw;
            Shapes.putBed(arena, RBW_X, BED_Y, 0, r.hx, r.hz);
            defense.add(arena.block(RBW_X, BED_Y, 0));
            defense.add(arena.block(RBW_X + r.hx, BED_Y, r.hz));
            for (int[] block : r.defense) {
                Block at = arena.block(block[0], block[1], block[2]);
                if (at.getType() == Material.AIR) {
                    arena.put(block[0], block[1], block[2], material(block[3]), block[4]);
                    defense.add(at);
                }
            }
            return;
        }
        Shapes.putBed(arena, 0, BED_Y, 0, island.hx, island.hz);
        defense.add(arena.block(0, BED_Y, 0));
        defense.add(arena.block(island.hx, BED_Y, island.hz));
        int count = twoLayers() ? 2 : 3;
        for (int i = 0; i < count; i++) {
            for (int[] place : layers.get(i)) {
                Block block = arena.block(place[0], place[1], place[2]);
                if (block.getType() == Material.AIR) {
                    arena.put(place[0], place[1], place[2], layerType(i, place, island.hx, island.hz), 0);
                    defense.add(block);
                }
            }
        }
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return !done && near(block, bedX(), 0, 8, -BED_Y, PLATFORM_Y + 2);
    }

    @Override
    void placed(Player player, Block block) {
        start();
    }

    private void start() {
        if (started == 0) {
            started = now();
        }
    }

    @Override
    boolean canBreak(Player player, Block block) {
        return !done && arena.isPlaced(block);
    }

    @Override
    void broke(Player player, Block block) {
        if (!defense.remove(block)) {
            return;
        }
        start();
        int split = splitOf(block.getType());
        if (split >= 0 && splits[split] == 0) {
            splits[split] = Math.max(1, now() - started);
        }
        if (block.getType() == Material.BED_BLOCK) {
            bedBroken(player);
        }
    }

    /** Which split a block of the defense ends: 0 wood/wool, 1 glass, 2 end stone, 3 bed. */
    static int splitOf(Material type) {
        switch (type) {
            case WOOL:
            case WOOD:
                return 0;
            case STAINED_GLASS:
            case GLASS:
                return 1;
            case ENDER_STONE:
                return 2;
            case BED_BLOCK:
                return 3;
            default:
                return -1;
        }
    }

    private void bedBroken(Player player) {
        done = true;
        if (!blockedIn) {
            // the bed broken without a block-in: no time for the records
            player.sendMessage(ChatColor.RED + "Not counted: you never blocked yourself in. Close the four sides at your feet"
                    + " and at your head with your blocks, then dig to the bed.");
            title(player, ChatColor.RED + "" + ChatColor.BOLD + "NOT COUNTED", ChatColor.GRAY + "Block yourself in first");
            sound(player, Sound.VILLAGER_NO, 1f);
            tried(me);
            again(60L);
            return;
        }
        long time = splits[3];
        player.sendMessage(ChatColor.GRAY + "---------------------");
        for (int i = twoLayers() ? 1 : 0; i < SPLITS.length; i++) {
            player.sendMessage(ChatColor.YELLOW + pad(SPLITS[i]) + (splits[i] == 0 ? ChatColor.GRAY + "-"
                    : ChatColor.GREEN + time(splits[i]) + " seconds"));
        }
        player.sendMessage(ChatColor.GRAY + "---------------------");
        title(player, ChatColor.GREEN + "" + ChatColor.BOLD + "BED BROKEN!", ChatColor.GRAY + time(time) + " s");
        sound(player, Sound.ENDERDRAGON_GROWL, 1.2f);
        score(time);
        again(60L);
    }

    @SuppressWarnings("deprecation") // Material#getMaterial(int): the 1.8 block ids of the map
    private static Material material(int id) {
        Material type = Material.getMaterial(id);
        return type == null ? Material.ENDER_STONE : type;
    }

    /**
     * The defense of a bed of a map copied with the foot of the bed at (0, BED_Y, 0): its blocks of
     * {@link #DEFENSE_IDS} at most {@link #DEFENSE_REACH} from the bed, from its level up.
     */
    static List<int[]> defenseOf(MapCopy copy, int hx, int hz) {
        List<int[]> found = new ArrayList<>();
        for (int[] block : copy.blocksOf(DEFENSE_IDS)) {
            int x = block[0];
            int y = block[1];
            int z = block[2];
            boolean nearFoot = Math.abs(x) <= DEFENSE_REACH && Math.abs(z) <= DEFENSE_REACH;
            boolean nearHead = Math.abs(x - hx) <= DEFENSE_REACH && Math.abs(z - hz) <= DEFENSE_REACH;
            if ((nearFoot || nearHead) && y >= BED_Y && y <= BED_Y + DEFENSE_REACH) {
                found.add(block);
            }
        }
        return found;
    }

    private static String pad(String name) {
        StringBuilder text = new StringBuilder(name);
        while (text.length() < 11) {
            text.append(' ');
        }
        return text.toString();
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || done) {
            attacker.stop();
            return;
        }
        if (started != 0 && now() - started > LIMIT_MS) {
            attacker.stop();
            failed("A minute and the bed still stands: start again.");
            return;
        }
        if (!blockedIn && started != 0 && blockedIn(player.getLocation().getBlock())) {
            blockedIn = true;
            sound(player, Sound.ORB_PICKUP, 1.6f);
        }
        if (enemy.on() && started != 0 && now() - started >= ENEMY_AFTER_MS) {
            if (standing == null || player.isOnGround()) {
                standing = player.getLocation().getBlock();
            }
            attacker.tick(standing, tickCount);
        } else {
            attacker.stop();
        }
        refresh();
    }

    /**
     * True if the player whose feet are in this block is blocked in: the four sides at their feet
     * and at their head are solid, and one of them at least is a block they placed (not of the
     * defense, that digging down would put around them).
     */
    private boolean blockedIn(Block feet) {
        boolean theirs = false;
        for (int[] side : SIDES) {
            for (int up = 0; up <= 1; up++) {
                Block next = feet.getRelative(side[0], up, side[1]);
                if (!next.getType().isSolid()) {
                    return false;
                }
                theirs |= arena.isPlaced(next) && !defense.contains(next);
            }
        }
        return theirs;
    }

    /** The enemy hits the player from this side: pushed away from it, as by a sword hit from there. */
    private void enemyHits(Player player, int[] side) {
        double push = 0.32 + random.nextDouble() * 0.08;
        player.setVelocity(new org.bukkit.util.Vector(-side[0] * push, 0.3, -side[1] * push));
        player.playEffect(org.bukkit.EntityEffect.HURT);
        sound(player, Sound.HURT_FLESH, 1f);
    }

    @Override
    void trainingLines(List<String> lines) {
        long elapsed = started == 0 ? 0 : done ? splits[3] : now() - started;
        lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + time(elapsed) + " s");
        for (int i = twoLayers() ? 1 : 0; i < SPLITS.length; i++) {
            lines.add(ChatColor.WHITE + SPLITS[i] + ": " + (splits[i] == 0 ? ChatColor.GRAY + "-" : ChatColor.GREEN + time(splits[i])));
        }
    }
}
