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
 * Extend practice, the jumps of Flarefin's Clutch Training: a gap too long to jump, between two
 * pads of quartz and wool. The player runs, jumps and, in the air, extends their wool out of the
 * edge to land on it, then reaches the other pad: a double extend (5 blocks up 1, 6 flat) with two
 * blocks at most, a triple extend (6 up 1, 7 flat) or the jumps down (7 down 1 or 2) with three.
 * Each pad reached adds one to the streak; the void ends it.
 */
final class ExtendTraining extends Training {

    /** One jump of the map: the gap between the pads, how much higher the far one is, the blocks allowed. */
    static final class Jump {
        final String id;
        final String label;
        final int gap;
        final int rise;
        final int blocks;

        Jump(String id, String label, int gap, int rise, int blocks) {
            this.id = id;
            this.label = label;
            this.gap = gap;
            this.rise = rise;
            this.blocks = blocks;
        }
    }

    /** The jumps, as the signs of the map name them (the gap, then + or - the height). */
    static final Jump[] JUMPS = {
        new Jump("double-5-1", "Double extend 5+1", 5, 1, 2),
        new Jump("double-6-0", "Double extend 6+0", 6, 0, 2),
        new Jump("triple-6-1", "Triple extend 6+1", 6, 1, 3),
        new Jump("triple-7-0", "Triple extend 7+0", 7, 0, 3),
        new Jump("down-7-1", "Extend 7-1", 7, -1, 3),
        new Jump("down-7-2", "Extend 7-2", 7, -2, 3),
    };
    /** The jumps stand side by side, this far apart on x. */
    static final int SPACING = 12;
    /** A pad is 5 by 5; the start pad runs from z 0 to z 4, its top at y 0. */
    static final int PAD = 5;

    private boolean left;
    private boolean done;
    private long started;
    private int placedCount;
    private int streak;

    ExtendTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams, variants());
    }

    private static Variant[] variants() {
        Variant[] variants = new Variant[JUMPS.length];
        for (int i = 0; i < JUMPS.length; i++) {
            variants[i] = new Variant(JUMPS[i].id, JUMPS[i].label, false, "jumps");
        }
        return variants;
    }

    private Jump jump() {
        return JUMPS[variant];
    }

    /** The x of the middle of a jump's pads. */
    static int jumpX(int index) {
        return SPACING * index - SPACING * (JUMPS.length - 1) / 2;
    }

    /** The first row of the far pad: the start pad, then the gap. */
    static int farZ(Jump jump) {
        return PAD + jump.gap;
    }

    @Override
    String describe() {
        Jump jump = jump();
        return "jump the " + jump.gap + " blocks" + (jump.rise > 0 ? " up " + jump.rise : jump.rise < 0 ? " down " + -jump.rise : "")
                + ": extend your wool in the air, " + jump.blocks + " blocks at most.";
    }

    @Override
    void build() {
        for (int i = 0; i < JUMPS.length; i++) {
            int x = jumpX(i);
            pad(arena, x, 0, 0);
            pad(arena, x, farZ(JUMPS[i]), JUMPS[i].rise);
            backPanel(arena, x);
        }
    }

    /**
     * A pad of the map, its top at y top: a ring of quartz around light gray wool, under it white
     * stained glass with a lamp in the middle, lit by a block of redstone under the lamp.
     */
    static void pad(Arena arena, int x, int z, int top) {
        arena.fill(x - 2, top - 2, z, x + 2, top - 2, z + PAD - 1, Material.QUARTZ_BLOCK, 0);
        arena.build(x, top - 2, z + 2, Material.REDSTONE_BLOCK, 0);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = 0; dz < PAD; dz++) {
                boolean edgeX = Math.abs(dx) == 2;
                boolean edgeZ = dz == 0 || dz == PAD - 1;
                if (edgeX && edgeZ) {
                    arena.build(x + dx, top - 1, z + dz, Material.QUARTZ_BLOCK, 0);
                } else if (edgeX || edgeZ) {
                    arena.build(x + dx, top - 1, z + dz, Material.STAINED_GLASS, Shapes.WHITE);
                }
            }
        }
        arena.build(x, top - 1, z + 2, Material.REDSTONE_LAMP_ON, 0);
        arena.fill(x - 2, top, z, x + 2, top, z + PAD - 1, Material.QUARTZ_BLOCK, 0);
        arena.fill(x - 1, top, z + 1, x + 1, top, z + PAD - 2, Material.WOOL, Shapes.LIGHT_GRAY);
    }

    /** The quartz wall behind the start pad, as on the map (where its "Back" button was). */
    static void backPanel(Arena arena, int x) {
        arena.fill(x - 2, 1, -1, x + 2, 1, -1, Material.QUARTZ_BLOCK, 0);
        for (int dx = -2; dx <= 2; dx++) {
            arena.build(x + dx, 2, -1, Math.abs(dx) == 1 ? Material.STAINED_GLASS_PANE : Material.QUARTZ_BLOCK, 0);
        }
        arena.fill(x - 1, 3, -1, x + 1, 3, -1, Material.QUARTZ_BLOCK, 0);
        arena.build(x - 2, 3, -1, Material.QUARTZ_STAIRS, 0);
        arena.build(x + 2, 3, -1, Material.QUARTZ_STAIRS, 1);
    }

    @Override
    Location spawnOf(UUID player) {
        return arena.at(jumpX(variant) + 0.5, 1, 1.5, 0f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 9;
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {stack(Material.WOOL)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        left = false;
        done = false;
        started = 0;
        placedCount = 0;
    }

    /** The blocks go in the gap only, and no more than the jump allows. */
    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        Jump jump = jump();
        if (done || placedCount >= jump.blocks) {
            if (!done) {
                player.sendMessage(ChatColor.RED + "" + jump.blocks + " blocks at most on this jump.");
            }
            return false;
        }
        int x = block.getX() - arena.centreX() - jumpX(variant);
        int y = block.getY() - arena.floorY();
        int z = block.getZ() - arena.centreZ();
        return Math.abs(x) <= 3 && z >= PAD && z < farZ(jump) && y >= Math.min(0, jump.rise) - 4 && y <= Math.max(0, jump.rise) + 2;
    }

    @Override
    void placed(Player player, Block block) {
        placedCount++;
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || done) {
            return;
        }
        double[] at = arena.offset(player.getLocation());
        if (!left && (at[2] > PAD + 0.3 || at[1] < 0.5)) {
            left = true;
            started = now();
        }
        if (left && onGround(player) && onFarPad(player)) {
            reached(player);
            return;
        }
        refresh();
    }

    /** True if the player stands on the top of the far pad. */
    private boolean onFarPad(Player player) {
        Jump jump = jump();
        Block under = player.getLocation().subtract(0, 0.1, 0).getBlock();
        int x = under.getX() - arena.centreX() - jumpX(variant);
        int z = under.getZ() - arena.centreZ() - farZ(jump);
        return under.getY() - arena.floorY() == jump.rise && Math.abs(x) <= 2 && z >= 0 && z < PAD && !arena.isPlaced(under);
    }

    private void reached(Player player) {
        done = true;
        streak++;
        long time = now() - started;
        player.sendMessage(ChatColor.YELLOW + "You reached the end in " + ChatColor.AQUA + time(time) + " s" + ChatColor.YELLOW
                + " using " + ChatColor.AQUA + placedCount + ChatColor.YELLOW + (placedCount == 1 ? " block!" : " blocks!")
                + ChatColor.GRAY + " Streak: " + ChatColor.WHITE + streak);
        title(player, ChatColor.GREEN + "" + ChatColor.BOLD + "EXTEND!", ChatColor.GRAY + "" + placedCount + " blocks, " + time(time) + " s");
        sound(player, Sound.FIREWORK_LAUNCH, 1f);
        score(streak, true);
        again(25L);
    }

    @Override
    void fell(Player player) {
        String why = streak > 0 ? "You fell after " + streak + (streak == 1 ? " jump" : " jumps") + ": the streak starts again."
                : "You fell.";
        streak = 0;
        failed(why);
    }

    @Override
    void trainingLines(List<String> lines) {
        Jump jump = jump();
        long elapsed = started == 0 ? 0 : (done ? 0 : now() - started);
        lines.add(ChatColor.WHITE + "Blocks: " + ChatColor.GREEN + placedCount + ChatColor.GRAY + "/" + jump.blocks);
        lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + time(elapsed) + " s");
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
    }
}
