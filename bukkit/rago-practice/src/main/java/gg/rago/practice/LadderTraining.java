package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Ladder practice: up a wall of end stone 12 blocks high with ladders, placed and climbed as fast
 * as possible; or the block ladder, a tower of wool under the feet, 12 blocks up. The time runs
 * from the first ladder or block to the first step on the top.
 */
final class LadderTraining extends Training {

    private static final int HEIGHT = 12;
    private static final long LIMIT_MS = 30000L;
    /** The x of each station: the wall, the tower. */
    private static final int[] STATIONS = {-12, 12};
    /** The x of the wall, from its station: its face looks at the spawn. */
    private static final int WALL = 2;

    private long started;
    private int placedCount;
    private boolean done;

    LadderTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams,
                new Variant("wall", "Ladder wall", true, ""),
                new Variant("tower", "Block ladder (tower)", true, ""));
    }

    @Override
    String describe() {
        return current().id.equals("tower")
                ? "tower up 12 blocks: jump and place your wool under your feet, up to the gold line."
                : "place your ladders on the wall and climb to its top (12 blocks).";
    }

    private int station() {
        return STATIONS[variant];
    }

    @Override
    void build() {
        int w = STATIONS[0];
        Shapes.island(arena, w - 5, -4, w + 5, 4, Material.WOOD, 0, Material.LOG, 0);
        arena.fill(w + WALL, 1, -2, w + WALL, HEIGHT - 1, 2, Material.ENDER_STONE, 0);
        arena.fill(w + WALL, HEIGHT, -2, w + WALL, HEIGHT, 2, Material.GOLD_BLOCK, 0);
        int t = STATIONS[1];
        Shapes.island(arena, t - 5, -4, t + 5, 4, Material.WOOD, 0, Material.LOG, 0);
        // the height to reach, beside the tower
        arena.fill(t + 3, 1, 3, t + 3, HEIGHT, 3, Material.GLASS, 0);
        arena.build(t + 3, HEIGHT + 1, 3, Material.GOLD_BLOCK, 0);
    }

    @Override
    Location spawnOf(UUID player) {
        return arena.at(station() + 0.5, 1, 0.5, -90f);
    }

    @Override
    ItemStack[] kit() {
        return current().id.equals("tower") ? new ItemStack[] {stack(Material.WOOL)} : new ItemStack[] {stack(Material.LADDER)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        started = 0;
        placedCount = 0;
        done = false;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return !done && near(block, station(), 0, 4, 0, HEIGHT + 2);
    }

    @Override
    void placed(Player player, Block block) {
        if (started == 0) {
            started = now();
        }
        placedCount++;
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || done || started == 0) {
            return;
        }
        long elapsed = now() - started;
        if (player.getLocation().getY() >= arena.floorY() + HEIGHT + 1 && onGround(player)) {
            done = true;
            score(elapsed);
            player.sendMessage(ChatColor.GRAY + "At the top with " + placedCount
                    + (current().id.equals("tower") ? " blocks." : " ladders."));
            sound(player, Sound.FIREWORK_LAUNCH, 1f);
            again(40L);
            return;
        }
        if (elapsed > LIMIT_MS) {
            failed("Thirty seconds and you are not at the top: start again.");
            return;
        }
        refresh();
    }

    @Override
    void trainingLines(List<String> lines) {
        long elapsed = started == 0 ? 0 : now() - started;
        Player player = player();
        int height = player == null ? 0 : Math.max(0, (int) Math.floor(player.getLocation().getY()) - arena.floorY() - 1);
        lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + time(elapsed) + " s");
        lines.add(ChatColor.WHITE + "Height: " + ChatColor.GREEN + Math.min(height, HEIGHT) + ChatColor.GRAY + "/" + HEIGHT);
        lines.add(ChatColor.GRAY + (started == 0 ? "Place to start" : done ? "At the top!" : "Climb!"));
    }
}
