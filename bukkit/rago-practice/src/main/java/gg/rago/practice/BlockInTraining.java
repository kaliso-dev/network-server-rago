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
 * block placed (or broken); the chat then gives how long each layer took, as the map did. The
 * island is the map's own when the server has it (see {@link MapCopy}), else a plain one.
 *
 * <p>As in a game, an enemy comes two seconds after the start (a setting turns it off): about once a
 * second, a player with a side open at their feet or head is hit from it, and a player blocked in
 * sees one of the blocks they placed around them broken. The defense itself is never touched.
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

        Island(MapCopy copy, int hx, int hz, double[][] starts) {
            this.copy = copy;
            this.hx = hx;
            this.hz = hz;
            this.starts = starts;
        }
    }

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
    /** The enemy who hits the player and breaks their block-in. */
    private final Setting enemy;
    /** The enemy comes this long after the start, then acts every ACT_MS or so. */
    static final long ENEMY_AFTER_MS = 2000L;
    private static final long ACT_MS = 1100L;
    private long nextAct;
    private final java.util.Random random = new java.util.Random();
    /** The four sides around the player, at their feet and head. */
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    BlockInTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, Island island) {
        super(host, mode, arena, teams,
                new Variant("wood", "3 layers, wood side", true, ""),
                new Variant("wool", "3 layers, wool side", true, ""),
                new Variant("two", "2 layers", true, ""));
        this.island = island;
        this.layers = Shapes.defense(0, BED_Y, 0, island.hx, island.hz, 3);
        this.enemy = toggle("Enemy (hits, breaks)", true);
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

    @Override
    String describe() {
        return "drop on the defense, block yourself in and dig to the bed. Timed from your first block.";
    }

    @Override
    void build() {
        if (island.copy != null) {
            island.copy.build(arena);
            return;
        }
        Shapes.island(arena, -7, -6, 6, 6, Material.SANDSTONE, 0, Material.STONE, 0);
        Shapes.walls(arena, -5, -4, 4, 4, PLATFORM_Y, 1, Material.GLASS, 0);
    }

    @Override
    Location spawnOf(UUID player) {
        double[] start = island.starts[current().id.equals("wool") && island.starts.length > 1 ? 1 : 0];
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
        nextAct = 0;
        done = false;
        Arrays.fill(splits, 0L);
        defense.clear();
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
        return !done && near(block, 0, 0, 8, -BED_Y, PLATFORM_Y + 2);
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
            return;
        }
        if (started != 0 && now() - started > LIMIT_MS) {
            failed("A minute and the bed still stands: start again.");
            return;
        }
        if (enemy.on() && started != 0 && now() - started >= ENEMY_AFTER_MS && now() >= nextAct) {
            nextAct = now() + ACT_MS - 300 + random.nextInt(600);
            enemyActs(player);
        }
        refresh();
    }

    /**
     * The enemy's turn: a hit through a side left open at the feet or head, else one of the
     * blocks the player placed around them goes.
     */
    private void enemyActs(Player player) {
        Block feet = player.getLocation().getBlock();
        List<int[]> open = new ArrayList<>();
        List<Block> theirs = new ArrayList<>();
        for (int[] side : SIDES) {
            for (int up = 0; up <= 1; up++) {
                Block next = feet.getRelative(side[0], up, side[1]);
                if (!next.getType().isSolid()) {
                    open.add(side);
                } else if (arena.isPlaced(next) && !defense.contains(next)) {
                    theirs.add(next);
                }
            }
        }
        if (!open.isEmpty()) {
            int[] side = open.get(random.nextInt(open.size()));
            // pushed away from the open side, as by a sword hit from there
            double push = 0.32 + random.nextDouble() * 0.08;
            player.setVelocity(new org.bukkit.util.Vector(-side[0] * push, 0.3, -side[1] * push));
            player.playEffect(org.bukkit.EntityEffect.HURT);
            sound(player, Sound.HURT_FLESH, 1f);
            return;
        }
        if (theirs.isEmpty()) {
            return;
        }
        Block target = theirs.get(random.nextInt(theirs.size()));
        @SuppressWarnings("deprecation") // the block id of the break effect, as 1.8 takes it
        int id = target.getTypeId();
        target.getWorld().playEffect(target.getLocation(), org.bukkit.Effect.STEP_SOUND, id);
        arena.restore(target);
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
