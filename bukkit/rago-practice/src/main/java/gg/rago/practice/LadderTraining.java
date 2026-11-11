package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Ladder practice, the block-ladder clutch of lavy's map: like a water bucket from high up, but with
 * a block and a ladder. The player jumps from a platform 4 to 24 blocks above a small pad of stone
 * and, while falling, puts a block of wool on the pad and a ladder on its side, then falls into
 * the ladder: no fall damage. Each clutch adds one to the streak; touching the stone, a fall that
 * hurts or the void end it. From high up the fall is fast: the ladder put in the last tick before
 * the landing reaches the server just after it, so the landing is judged a few ticks later, with
 * the ladder there.
 */
final class LadderTraining extends Training {

    /** The heights of the platforms above the pad, as on the map's signs. */
    static final int[] HEIGHTS = {4, 8, 12, 16, 20, 24};
    /** Platforms this far apart on x, the first one at FIRST_X. */
    static final int SPACING = 6;
    static final int FIRST_X = -15;
    /** The platform runs from z 0 to z 2, out of a wall of barriers at WALL_Z. */
    static final int PLATFORM_LENGTH = 3;
    static final int WALL_Z = -2;
    /** The first row of the pad of the lowest platform: the next pads are one block further each. */
    static final int PAD_Z = 7;
    /**
     * Ticks between the landing and its judgement: a block or a ladder put just before the landing
     * may reach the server after it (one or two ticks of ping), and a ladder still counts then.
     */
    static final int GRACE_TICKS = 5;

    private final Random random = new Random();
    /** The platform of this attempt (the random mode draws one each time). */
    private int station;
    private boolean jumped;
    private boolean done;
    private int streak;
    private int blocks;
    /** The tick of the landing out of a ladder, waiting for its judgement; -1 before. */
    private int landedTick = -1;

    LadderTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams, variants());
    }

    static Variant[] variants() {
        Variant[] variants = new Variant[HEIGHTS.length + 1];
        for (int i = 0; i < HEIGHTS.length; i++) {
            variants[i] = new Variant("h" + HEIGHTS[i], HEIGHTS[i] + " blocks", false, "clutches");
        }
        variants[HEIGHTS.length] = new Variant("random", "Random height", false, "clutches");
        return variants;
    }

    /** The x of a platform (and of the middle of its pad). */
    static int platformX(int station) {
        return FIRST_X + SPACING * station;
    }

    /** The first row of a pad: the higher the platform, the further its pad. */
    static int padZ(int station) {
        return PAD_Z + station;
    }

    /** The y of the top of a platform: the player stands one above, this high above the pad. */
    static int platformY(int station) {
        return HEIGHTS[station] - 1;
    }

    @Override
    String describe() {
        return "jump, put a block of wool on the stone and a ladder on its side, and fall into the ladder.";
    }

    @Override
    void build() {
        int last = HEIGHTS.length - 1;
        arena.fill(platformX(0) - 3, 1, WALL_Z, platformX(last) + 3, platformY(last) + 10, WALL_Z, Material.BARRIER, 0);
        for (int i = 0; i < HEIGHTS.length; i++) {
            int x = platformX(i);
            arena.fill(x, platformY(i), 0, x, platformY(i), PLATFORM_LENGTH - 1, Material.QUARTZ_BLOCK, 0);
            arena.fill(x - 1, 0, padZ(i), x + 1, 0, padZ(i) + 2, Material.DOUBLE_STEP, 0);
        }
    }

    @Override
    Location spawnOf(UUID player) {
        return arena.at(platformX(station) + 0.5, platformY(station) + 1, 0.5, 0f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 6;
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {stack(Material.WOOL), stack(Material.LADDER)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        station = current().id.equals("random") ? random.nextInt(HEIGHTS.length) : variant;
        jumped = false;
        done = false;
        blocks = 0;
        landedTick = -1;
    }

    /** True if this block is the stone of the pad under the platform of this attempt. */
    private boolean isPad(Block block) {
        int x = block.getX() - arena.centreX() - platformX(station);
        int z = block.getZ() - arena.centreZ() - padZ(station);
        return block.getWorld().equals(arena.world()) && block.getY() == arena.floorY()
                && Math.abs(x) <= 1 && z >= 0 && z <= 2 && block.getType() == Material.DOUBLE_STEP;
    }

    /** As on the map: the wool goes on the top of the pad, the ladder on that wool. */
    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        if (done || !jumped) {
            return false;
        }
        Block against = event.getBlockAgainst();
        if (block.getType() == Material.WOOL) {
            return isPad(against) && block.getRelative(BlockFace.DOWN).equals(against);
        }
        return block.getType() == Material.LADDER && against.getType() == Material.WOOL && arena.isPlaced(against);
    }

    @Override
    void placed(Player player, Block block) {
        blocks++;
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && jumped && !done) {
            touchedDown(victim);
        }
        return false;
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || done) {
            return;
        }
        Location at = player.getLocation();
        if (!jumped) {
            if (at.getY() < arena.floorY() + platformY(station) + 0.5) {
                jumped = true;
            }
            return;
        }
        if (inLadder(player)) {
            clutched(player);
        } else if (landedTick >= 0 ? tickCount - landedTick >= GRACE_TICKS : onGround(player)) {
            touchedDown(player);
        }
        refresh();
    }

    /** True if the player's feet are in a ladder they put. */
    private boolean inLadder(Player player) {
        Block feet = player.getLocation().getBlock();
        return feet.getType() == Material.LADDER && arena.isPlaced(feet);
    }

    /**
     * The player is on the ground (or hurt by a fall): in their ladder, a clutch; else the landing
     * is judged after the grace ticks, when a late ladder had the time to come.
     */
    private void touchedDown(Player player) {
        if (done) {
            return;
        }
        if (inLadder(player)) {
            clutched(player);
            return;
        }
        if (landedTick < 0) {
            landedTick = tickCount;
            return;
        }
        if (tickCount - landedTick >= GRACE_TICKS) {
            landed(player);
        }
    }

    private void clutched(Player player) {
        done = true;
        streak++;
        player.sendMessage(ChatColor.GREEN + "Clutch! " + ChatColor.GRAY + "Streak: " + ChatColor.WHITE + streak);
        title(player, ChatColor.GREEN + "" + ChatColor.BOLD + "CLUTCH!", ChatColor.GRAY + "from " + HEIGHTS[station] + " blocks");
        sound(player, Sound.ORB_PICKUP, 1.2f);
        score(streak, true);
        again(20L);
    }

    /** The fall ended out of a ladder: on the stone, on the wool, or with a fall that hurt. */
    private void landed(Player player) {
        if (done) {
            return;
        }
        Block under = player.getLocation().subtract(0, 0.1, 0).getBlock();
        String why;
        if (under.getType() == Material.DOUBLE_STEP) {
            why = "You touched the stone!";
        } else if (under.getType() == Material.WOOL) {
            why = "You landed on your wool: put the ladder on its side and fall into the ladder.";
        } else {
            why = "No ladder caught you.";
        }
        lost(why);
    }

    @Override
    void fell(Player player) {
        lost("You fell.");
    }

    private void lost(String why) {
        done = true;
        if (streak > 0) {
            why += " Streak: " + streak + ", it starts again.";
        }
        streak = 0;
        failed(why);
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Height: " + ChatColor.GREEN + HEIGHTS[station] + " blocks");
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
        lines.add(ChatColor.GRAY + (jumped ? (blocks == 0 ? "Wool on the stone!" : "Ladder on the wool!") : "Jump!"));
    }
}
