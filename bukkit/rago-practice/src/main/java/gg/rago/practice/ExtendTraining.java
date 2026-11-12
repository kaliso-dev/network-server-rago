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
 * Each pad reached adds one to the streak; the void ends it. The Chain mode lines up ten pads with
 * every kind of jump between them: the player runs from one to the next with no stop and no
 * teleport, against the clock. Ladders come with the wool (a setting takes them out): they do not
 * count as blocks of the jump, as a ladder on the side of a pad catches a jump that falls short.
 */
final class ExtendTraining extends Training {

    /** Ticks from the far pad back to the start: just the time to see the title, then again. */
    static final long AFTER_SUCCESS = 8L;
    /** Ticks from the end of the chain back to the start. */
    static final long AFTER_CHAIN = 15L;

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
    /** The jumps of the chain, one after the other: every kind, the ten pads ending near the start height. */
    static final int[] CHAIN = {0, 1, 2, 3, 4, 0, 2, 5, 1};
    /** The variant of the chain: after the jumps. */
    static final int CHAIN_VARIANT = JUMPS.length;
    /** The chain runs along z on the right of the jumps, from this z. */
    static final int CHAIN_START_Z = -50;

    private final Setting ladders;
    private boolean left;
    private boolean done;
    private long started;
    private int placedCount;
    private int streak;
    /** In the chain, the pad the player stands on or last reached (0: the start). */
    private int pad;

    ExtendTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams, variants());
        this.ladders = toggle("Ladders", true);
    }

    static Variant[] variants() {
        Variant[] variants = new Variant[JUMPS.length + 1];
        for (int i = 0; i < JUMPS.length; i++) {
            variants[i] = new Variant(JUMPS[i].id, JUMPS[i].label, false, "jumps");
        }
        variants[CHAIN_VARIANT] = new Variant("chain", "Chain: " + (CHAIN.length + 1) + " pads, no stop", true, "");
        return variants;
    }

    private boolean chain() {
        return variant == CHAIN_VARIANT;
    }

    /** The jump the player is on: the one of the variant, or the next gap of the chain. */
    private Jump jump() {
        return chain() ? JUMPS[CHAIN[Math.min(pad, CHAIN.length - 1)]] : JUMPS[variant];
    }

    /** The x of the middle of the chain's pads. */
    static int chainX() {
        return jumpX(JUMPS.length);
    }

    /** The first row (z) of each pad of the chain. */
    static int[] chainZ() {
        int[] z = new int[CHAIN.length + 1];
        z[0] = CHAIN_START_Z;
        for (int i = 0; i < CHAIN.length; i++) {
            z[i + 1] = z[i] + PAD + JUMPS[CHAIN[i]].gap;
        }
        return z;
    }

    /** The height of the top of each pad of the chain. */
    static int[] chainTop() {
        int[] top = new int[CHAIN.length + 1];
        for (int i = 0; i < CHAIN.length; i++) {
            top[i + 1] = top[i] + JUMPS[CHAIN[i]].rise;
        }
        return top;
    }

    /** The x of the middle of a jump's pads. */
    static int jumpX(int index) {
        return SPACING * index - SPACING * (JUMPS.length - 1) / 2;
    }

    /** The first row of the far pad: the start pad, then the gap. */
    static int farZ(Jump jump) {
        return PAD + jump.gap;
    }

    /** A fall gives the next jump by itself (no blaze rod); the chain runs against the clock. */
    @Override
    boolean restartUsed() {
        return current().timed;
    }

    @Override
    String describe() {
        if (chain()) {
            return "run the " + (CHAIN.length + 1) + " pads one after the other with no stop: extend each gap, the blocks"
                    + " of its jump at most.";
        }
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
        int[] z = chainZ();
        int[] top = chainTop();
        for (int i = 0; i < z.length; i++) {
            pad(arena, chainX(), z[i], top[i]);
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
        if (chain()) {
            return arena.at(chainX() + 0.5, chainTop()[0] + 1, CHAIN_START_Z + 1.5, 0f);
        }
        return arena.at(jumpX(variant) + 0.5, 1, 1.5, 0f);
    }

    @Override
    double fallY() {
        int lowest = 0;
        for (int top : chainTop()) {
            lowest = Math.min(lowest, top);
        }
        return arena.floorY() + (chain() ? lowest : 0) - 9;
    }

    @Override
    ItemStack[] kit() {
        if (ladders.on()) {
            return new ItemStack[] {stack(Material.WOOL), stack(Material.LADDER)};
        }
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
        pad = 0;
    }

    /** The blocks go in the gap only, and no more than the jump allows; ladders do not count. */
    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        Jump jump = jump();
        boolean ladder = block.getType() == Material.LADDER;
        if (done || !ladder && placedCount >= jump.blocks) {
            if (!done) {
                player.sendMessage(ChatColor.RED + "" + jump.blocks + " blocks at most on this jump.");
            }
            return false;
        }
        int x = block.getX() - arena.centreX() - (chain() ? chainX() : jumpX(variant));
        int y = block.getY() - arena.floorY();
        int z = block.getZ() - arena.centreZ();
        int from = 0;
        int bottom = 0;
        if (chain()) {
            from = chainZ()[pad];
            bottom = chainTop()[pad];
            if (pad >= CHAIN.length) {
                return false;
            }
        }
        return inGap(jump, x, y - bottom, z - from);
    }

    /**
     * True if a block at (x, y, z), from the start of the near pad and its top, is in the gap of
     * this jump, where the blocks of an extend go.
     */
    static boolean inGap(Jump jump, int x, int y, int z) {
        return Math.abs(x) <= 3 && z >= PAD && z < farZ(jump) && y >= Math.min(0, jump.rise) - 4 && y <= Math.max(0, jump.rise) + 2;
    }

    @Override
    void placed(Player player, Block block) {
        if (block.getType() != Material.LADDER) {
            placedCount++;
        }
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || done) {
            return;
        }
        double[] at = arena.offset(player.getLocation());
        int startZ = chain() ? CHAIN_START_Z : 0;
        int startTop = chain() ? chainTop()[0] : 0;
        if (!left && (at[2] > startZ + PAD + 0.3 || at[1] < startTop + 0.5)) {
            left = true;
            started = now();
        }
        if (chain()) {
            int on = left && onGround(player) ? chainPadUnder(player) : -1;
            if (on > pad) {
                nextPad(player, on);
            }
            refresh();
            return;
        }
        if (left && onGround(player) && onFarPad(player)) {
            reached(player);
            return;
        }
        refresh();
    }

    /** The pad of the chain whose top the player stands on (a block of the map, not one placed), or -1. */
    private int chainPadUnder(Player player) {
        Block under = player.getLocation().subtract(0, 0.1, 0).getBlock();
        if (arena.isPlaced(under)) {
            return -1;
        }
        return chainPadAt(under.getX() - arena.centreX() - chainX(), under.getY() - arena.floorY(), under.getZ() - arena.centreZ());
    }

    /** The pad of the chain whose top is the block (x, y, z) of the arena, or -1. */
    static int chainPadAt(int x, int y, int z) {
        if (Math.abs(x) > 2) {
            return -1;
        }
        int[] zs = chainZ();
        int[] tops = chainTop();
        for (int i = 0; i < zs.length; i++) {
            if (y == tops[i] && z >= zs[i] && z < zs[i] + PAD) {
                return i;
            }
        }
        return -1;
    }

    /** The player reached the next pad of the chain: on to the next gap at once, or the end. */
    private void nextPad(Player player, int reached) {
        pad = reached;
        placedCount = 0;
        if (pad < CHAIN.length) {
            sound(player, Sound.ORB_PICKUP, 1f + 0.05f * pad);
            return;
        }
        done = true;
        long time = now() - started;
        player.sendMessage(ChatColor.YELLOW + "You ran the " + (CHAIN.length + 1) + " pads in " + ChatColor.AQUA + time(time) + " s"
                + ChatColor.YELLOW + "!");
        title(player, ChatColor.GREEN + "" + ChatColor.BOLD + "CHAIN!", ChatColor.GRAY + time(time) + " s");
        sound(player, Sound.FIREWORK_LAUNCH, 1f);
        score(time);
        again(AFTER_CHAIN);
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
        again(AFTER_SUCCESS);
    }

    @Override
    void fell(Player player) {
        if (chain()) {
            failed(pad > 0 ? "You fell after " + pad + (pad == 1 ? " pad." : " pads.") : "You fell.");
            return;
        }
        String why = streak > 0 ? "You fell after " + streak + (streak == 1 ? " jump" : " jumps") + ": the streak starts again."
                : "You fell.";
        streak = 0;
        failed(why);
    }

    /** The sidebar keeps to the jump: no ranking. */
    @Override
    boolean showsRanking() {
        return false;
    }

    @Override
    void trainingLines(List<String> lines) {
        Jump jump = jump();
        long elapsed = started == 0 ? 0 : (done ? 0 : now() - started);
        if (chain()) {
            lines.add(ChatColor.WHITE + "Pad: " + ChatColor.GREEN + (pad + 1) + ChatColor.GRAY + "/" + (CHAIN.length + 1));
            if (pad < CHAIN.length) {
                lines.add(Duel.cut(ChatColor.WHITE + "Next: " + ChatColor.YELLOW + jump.label));
            }
        }
        lines.add(ChatColor.WHITE + "Blocks: " + ChatColor.GREEN + placedCount + ChatColor.GRAY + "/" + jump.blocks);
        lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + time(elapsed) + " s");
        if (!chain()) {
            lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
        }
    }
}
