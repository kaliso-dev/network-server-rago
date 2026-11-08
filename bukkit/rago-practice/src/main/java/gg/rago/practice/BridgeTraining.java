package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.Locale;
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
 * Timed bridge: from one platform to the other over 31 blocks of void, by technique, each with
 * its own record board. The rules of a technique are checked: a god bridge looks forward and never
 * crouches, a breezily bridge looks back and never crouches, a ninja bridge looks back, a telly
 * bridge places most of its blocks in the air; the speed bridge allows anything. The time runs
 * from the first block to the first step on the finish.
 */
final class BridgeTraining extends Training {

    /** The last block of the start platform, and the first of the finish (x offsets). */
    private static final int START_EDGE = -16;
    private static final int FINISH_EDGE = 16;
    private static final long LIMIT_MS = 60000L;

    private long started;
    private int blocks;
    private int inAir;
    private boolean done;

    BridgeTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams,
                new Variant("speed", "Speed bridge", true, ""),
                new Variant("ninja", "Ninja bridge", true, ""),
                new Variant("breezily", "Breezily bridge", true, ""),
                new Variant("god", "God bridge", true, ""),
                new Variant("telly", "Telly bridge", true, ""));
    }

    @Override
    String describe() {
        switch (current().id) {
            case "ninja":
                return "look back while you bridge (crouch taps allowed).";
            case "breezily":
                return "look back, never crouch: strafe left and right.";
            case "god":
                return "look forward, never crouch.";
            case "telly":
                return "sprint and jump, place most of your blocks in the air.";
            default:
                return "any technique, to the green platform as fast as you can.";
        }
    }

    @Override
    void build() {
        Shapes.island(arena, START_EDGE - 4, -2, START_EDGE, 2, Material.STAINED_CLAY, Shapes.WHITE, Material.STAINED_CLAY,
                Shapes.LIGHT_GRAY);
        Shapes.island(arena, FINISH_EDGE, -2, FINISH_EDGE + 4, 2, Material.STAINED_CLAY, Shapes.LIME, Material.STAINED_CLAY,
                Shapes.GREEN);
        // a line on the finish, to see it from afar
        for (int z = -2; z <= 2; z++) {
            arena.build(FINISH_EDGE, 0, z, Material.GOLD_BLOCK, 0);
        }
    }

    @Override
    Location spawnOf(UUID player) {
        return arena.at(START_EDGE - 1.5, 1, 0.5, -90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 5;
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {stack(Material.SANDSTONE), stack(Material.WOOL)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        started = 0;
        blocks = 0;
        inAir = 0;
        done = false;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        if (done) {
            return false;
        }
        int dx = block.getX() - arena.centreX();
        if (dx <= START_EDGE || dx >= FINISH_EDGE || !near(block, 0, 0, FINISH_EDGE + 4, 2, 2)
                || Math.abs(block.getZ() - arena.centreZ()) > 2) {
            return false;
        }
        String broken = rule(player, false);
        if (broken != null) {
            failed(broken);
            return false;
        }
        return true;
    }

    /** The rule of the technique that the player breaks now, or null. */
    private String rule(Player player, boolean ticking) {
        String id = current().id;
        double x = player.getLocation().getDirection().getX();
        boolean noCrouch = id.equals("breezily") || id.equals("god") || id.equals("telly");
        if (noCrouch && player.isSneaking()) {
            return "You crouched: a " + current().label.toLowerCase() + " never crouches.";
        }
        if (ticking) {
            return null;
        }
        if ((id.equals("ninja") || id.equals("breezily")) && x > -0.2) {
            return "Look back: a " + current().label.toLowerCase() + " places its blocks facing away from the finish.";
        }
        if (id.equals("god") && x < 0.2) {
            return "Look forward: a god bridge faces the finish.";
        }
        return null;
    }

    @Override
    void placed(Player player, Block block) {
        if (started == 0) {
            started = now();
        }
        blocks++;
        if (!onGround(player)) {
            inAir++;
        }
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || done || started == 0) {
            return;
        }
        long elapsed = now() - started;
        String broken = rule(player, true);
        if (broken != null) {
            failed(broken);
            return;
        }
        if (player.getLocation().getX() - arena.centreX() >= FINISH_EDGE && onGround(player)) {
            done = true;
            if (current().id.equals("telly") && inAir * 3 < blocks * 2) {
                failed("Only " + inAir + " of your " + blocks
                        + " blocks were placed in the air: a telly bridge places most of them jumping.");
                return;
            }
            score(elapsed);
            player.sendMessage(ChatColor.GRAY + "" + blocks + " blocks, "
                    + String.format(Locale.ROOT, "%.1f", blocks * 1000.0 / Math.max(1, elapsed)) + " blocks a second.");
            sound(player, Sound.FIREWORK_LAUNCH, 1f);
            again(40L);
            return;
        }
        if (elapsed > LIMIT_MS) {
            failed("A minute without reaching the finish: start again.");
            return;
        }
        refresh();
    }

    @Override
    void trainingLines(List<String> lines) {
        long elapsed = started == 0 ? 0 : now() - started;
        lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + time(elapsed) + " s");
        lines.add(ChatColor.WHITE + "Blocks: " + ChatColor.GREEN + blocks + ChatColor.GRAY + "/" + (FINISH_EDGE - START_EDGE - 1));
        lines.add(ChatColor.GRAY + (started == 0 ? "Place a block to start" : done ? "Finished!" : "Go!"));
    }
}
