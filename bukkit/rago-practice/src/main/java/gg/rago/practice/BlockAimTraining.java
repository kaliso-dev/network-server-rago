package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Block aim (plan, step 6.4, the placement on a target of its proposal): an island with its bed
 * and a short bridge out of it. A face lights up (green dust on it): under the feet at the end of
 * the bridge, on the side of the bridge, or on the bed to cover (shift held, as on Hypixel), and
 * the player puts a block against it as fast as they can. The block goes again at once and the
 * next face lights up, not far from the one before. As with the heights of Ladder practice, the
 * levels give less and less time to each target; each target put in time adds one to the streak
 * (the record of the level), one not put in time or a fall ends it. The time to each target and
 * the blocks put elsewhere are counted and shown. The first target of an attempt waits: it starts
 * the streak when the player is ready.
 *
 * <p>The scene, the faces, the times of the levels and the rules here (a block put elsewhere is
 * counted, it does not end the streak) are starting values, to confirm with the owner.
 */
final class BlockAimTraining extends Training {

    /** A block put stays this long before it goes (ticks): long enough to be seen. */
    static final long GONE_TICKS = 4L;
    /** The dust of the lit face comes again this often (ticks), and shows this far (blocks). */
    private static final int DUST_TICKS = 4;
    private static final int DUST_RADIUS = 32;
    /** A gap longer than this between two ticks (a menu, the inventory) is a pause: it does not count for the target. */
    private static final long GAP_MS = 250L;

    private final Random random = new Random();
    private BlockAim.Target target;
    /** False while the first target of the attempt waits for the player. */
    private boolean started;
    private boolean done;
    private long litAt;
    private long lastTickAt;
    private BlockAim.Streak streak = new BlockAim.Streak();

    BlockAimTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams, variants());
    }

    static Variant[] variants() {
        Variant[] variants = new Variant[BlockAim.LEVEL_MS.length];
        for (int i = 0; i < variants.length; i++) {
            variants[i] = new Variant("level" + (i + 1), "Level " + (i + 1) + " (" + BlockAim.levelSeconds(i) + " s)", false,
                    "targets");
        }
        return variants;
    }

    /** A miss or a fall gives the next attempt by itself: no blaze rod. */
    @Override
    boolean restartUsed() {
        return false;
    }

    @Override
    String describe() {
        return "a face lights up, under your feet at the end of the bridge, on the side of the bridge or on the bed"
                + " (shift held there): put a block on it within " + BlockAim.levelSeconds(variant) + " s.";
    }

    @Override
    void build() {
        Shapes.island(arena, BlockAim.ISLAND_MIN_X, -BlockAim.ISLAND_HALF, BlockAim.ISLAND_MAX_X, BlockAim.ISLAND_HALF,
                Material.WOOD, 0, Material.LOG, 0);
        arena.fill(0, 0, 0, BlockAim.BRIDGE_LENGTH - 1, 0, 0, Material.WOOL, Shapes.BLUE);
        Shapes.bed(arena, BlockAim.BED_FOOT_X, BlockAim.BED_Y, 0, 1, 0);
    }

    /** At the start of the bridge, facing the bed. */
    @Override
    Location spawnOf(UUID player) {
        return arena.at(0.5, 1, 0.5, 90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 5;
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {stack(Material.WOOL, Shapes.BLUE)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        target = BlockAim.first(random);
        started = false;
        done = false;
        litAt = now();
        lastTickAt = 0;
        streak = new BlockAim.Streak();
    }

    /** A block anywhere near the scene: on the target it counts, elsewhere too (it goes again either way). */
    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return !done && near(block, -1, 0, 8, 3, 4);
    }

    @Override
    void placed(Player player, Block block) {
        later(GONE_TICKS, () -> arena.restore(block));
        if (done) {
            return;
        }
        int x = block.getX() - arena.centreX();
        int y = block.getY() - arena.floorY();
        int z = block.getZ() - arena.centreZ();
        if (!target.hitBy(x, y, z)) {
            if (started) {
                streak.elsewhere();
                sound(player, Sound.NOTE_BASS, 0.7f);
            }
            sidebars();
            return;
        }
        if (!started) {
            started = true;
            title(player, ChatColor.GREEN + "" + ChatColor.BOLD + "GO!", ChatColor.WHITE + BlockAim.levelSeconds(variant)
                    + " s for each target");
            sound(player, Sound.NOTE_PLING, 1.5f);
        } else {
            streak.hit(now() - litAt);
            sound(player, Sound.ORB_PICKUP, 1.2f);
            score(streak.hits, true);
        }
        light(BlockAim.next(random, target));
    }

    /** The next face lights up, its time starting now. */
    private void light(BlockAim.Target next) {
        target = next;
        litAt = now();
        dust();
        sidebars();
    }

    /** The green dust on the lit face. */
    private void dust() {
        World world = arena.world();
        for (double[] point : target.points()) {
            Location at = arena.at(point[0], point[1], point[2]);
            // a count of 0: one dust exactly there, the offsets give its colour (a red of 0 would be red)
            world.spigot().playEffect(at, Effect.COLOURED_DUST, 0, 0, 0.001f, 1f, 0.001f, 1f, 0, DUST_RADIUS);
        }
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || done) {
            return;
        }
        long time = now();
        if (lastTickAt > 0 && time - lastTickAt > GAP_MS) {
            // a menu or the inventory paused the game: the time of the pause does not count
            litAt += time - lastTickAt;
        }
        lastTickAt = time;
        if (tickCount % DUST_TICKS == 0) {
            dust();
        }
        if (started && time - litAt > BlockAim.LEVEL_MS[variant]) {
            lost("Too slow: " + BlockAim.levelSeconds(variant) + " s went by (the face was " + BlockAim.PLACES[target.place] + ").");
            return;
        }
        refresh();
    }

    @Override
    void fell(Player player) {
        lost("You fell.");
    }

    private void lost(String why) {
        if (done) {
            return;
        }
        done = true;
        if (started) {
            why += " Streak: " + streak.summary() + ". It starts again.";
        }
        failed(why);
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak.hits + (streak.hits == 0 ? ""
                : ChatColor.GRAY + " | avg " + ChatColor.WHITE + BlockAim.seconds(streak.average()) + " s"));
        lines.add(ChatColor.WHITE + "Elsewhere: " + ChatColor.GREEN + streak.elsewhere);
        String where = ChatColor.GRAY + BlockAim.SHORT[target == null ? 0 : target.place] + ": ";
        if (!started) {
            lines.add(where + ChatColor.GREEN + "place to start");
        } else {
            long left = Math.max(0, BlockAim.LEVEL_MS[variant] - (now() - litAt));
            lines.add(where + ChatColor.YELLOW + BlockAim.seconds(left) + " s");
        }
    }
}
