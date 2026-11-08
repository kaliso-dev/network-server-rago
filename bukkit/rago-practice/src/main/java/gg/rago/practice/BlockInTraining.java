package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Block in: the player shuts themselves in as fast as they can, as in a game when a rusher lands
 * on their island: a block on each of the four sides at the feet and at the head, and one over the
 * head. On flat ground, on top of a bed defense (the sides sit on its slope), or on one block over
 * the void, where each side needs a block under it first. The time runs from the first block.
 */
final class BlockInTraining extends Training {

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    /** Fifteen seconds after the first block, the attempt is over. */
    private static final long LIMIT_MS = 15000L;
    /** The x of each station: the ground, the defense, the void. */
    private static final int[] STATIONS = {-30, 0, 30};

    private long started;
    private int blocks;
    private boolean done;

    BlockInTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams,
                new Variant("ground", "On the ground", true, ""),
                new Variant("defense", "On a bed defense", true, ""),
                new Variant("void", "Over the void", true, ""));
    }

    @Override
    String describe() {
        switch (current().id) {
            case "defense":
                return "on top of your bed defense, block yourself in: four sides, feet and head, and the roof.";
            case "void":
                return "on one block over the void: each side needs a block under it first.";
            default:
                return "block yourself in: the four sides at your feet and head, then the roof. Timed from your first block.";
        }
    }

    private int station() {
        return STATIONS[variant];
    }

    @Override
    void build() {
        // the ground: an island
        Shapes.island(arena, STATIONS[0] - 5, -5, STATIONS[0] + 5, 5, Material.WOOD, 0, Material.LOG, 0);
        // the defense: an island, a bed and two layers (end stone, wool) to stand on
        int d = STATIONS[1];
        Shapes.island(arena, d - 6, -5, d + 7, 5, Material.WOOD, 0, Material.LOG, 0);
        Shapes.bed(arena, d, 1, 0, 1, 0);
        Shapes.buildDefense(arena, Shapes.defense(d, 1, 0, 1, 0, 2),
                new Material[] {Material.ENDER_STONE, Material.WOOL}, new int[] {0, Shapes.WHITE});
        // the void: one block
        arena.build(STATIONS[2], 0, 0, Material.ENDER_STONE, 0);
    }

    @Override
    Location spawnOf(UUID player) {
        if (current().id.equals("defense")) {
            // on top of the defense, over the foot of the bed
            return arena.at(station() + 0.5, 4, 0.5, -90f);
        }
        return arena.at(station() + 0.5, 1, 0.5, -90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 6;
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {stack(Material.WOOL), stack(Material.ENDER_STONE)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        started = 0;
        blocks = 0;
        done = false;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return !done && near(block, station(), 0, 7, 4, 10);
    }

    @Override
    void placed(Player player, Block block) {
        blocks++;
        if (started == 0) {
            started = now();
        }
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || done || started == 0) {
            return;
        }
        long elapsed = now() - started;
        if (enclosed(player)) {
            done = true;
            score(elapsed);
            player.sendMessage(ChatColor.GRAY + "Blocked in with " + blocks + " blocks.");
            again(30L);
            return;
        }
        if (elapsed > LIMIT_MS) {
            failed("Too slow: 15 seconds and you are still open.");
            return;
        }
        refresh();
    }

    /** The four sides at the feet and at the head are blocks, and the block over the head. */
    static boolean enclosed(Player player) {
        Block feet = player.getLocation().getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        if (solid(feet) || solid(head)) {
            return false;
        }
        for (BlockFace side : SIDES) {
            if (!solid(feet.getRelative(side)) || !solid(head.getRelative(side))) {
                return false;
            }
        }
        return solid(head.getRelative(BlockFace.UP));
    }

    private static boolean solid(Block block) {
        return block.getType().isSolid();
    }

    @Override
    void trainingLines(List<String> lines) {
        long elapsed = started == 0 ? 0 : now() - started;
        lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + time(elapsed) + " s");
        lines.add(ChatColor.WHITE + "Blocks: " + ChatColor.GREEN + blocks);
        lines.add(ChatColor.GRAY + (started == 0 ? "Place a block to start" : done ? "Blocked in!" : "Close it!"));
    }

    @Override
    void intro(Player player) {
        super.intro(player);
        sound(player, Sound.NOTE_PLING, 1f);
    }
}
