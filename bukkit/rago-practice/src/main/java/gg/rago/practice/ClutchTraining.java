package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Block clutch: the player stands on a one-block bridge over the void and takes a hit from the
 * side, as in a fight on a bridge; they place their wool to save themselves. Single clutch (one
 * hit), double clutch (a second hit in the air), random knockback (any way, any strength) or a
 * long knockback to extend out of. Each clutch saved adds one to the streak; a fall ends it.
 */
final class ClutchTraining extends Training {

    /** Ticks on the ground after the last hit that make a clutch saved. */
    private static final int SAVED_TICKS = 16;

    private final Random random = new Random();
    /** Ticks before the next hit; -1 once the hits of the attempt are given. */
    private int nextHit;
    private int hitsLeft;
    private int lastHitTick;
    private int groundTicks;
    private int placedSinceHit;
    private int streak;
    private Vector way;

    ClutchTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams,
                new Variant("single", "Single clutch", false, "clutches"),
                new Variant("double", "Double clutch", false, "clutches"),
                new Variant("random", "Random knockback", false, "clutches"),
                new Variant("extend", "Long knockback", false, "clutches"));
    }

    @Override
    String describe() {
        switch (current().id) {
            case "double":
                return "two hits in a row, clutch twice.";
            case "random":
                return "a hit from any side, of any strength.";
            case "extend":
                return "a long knockback: extend your blocks to catch yourself.";
            default:
                return "a hit from the side: place a block under you.";
        }
    }

    @Override
    void build() {
        Shapes.floor(arena, -3, 0, 3, 0, 0, Material.SANDSTONE, 0);
        arena.build(-4, 0, 0, Material.STAINED_CLAY, Shapes.GREEN);
        arena.build(4, 0, 0, Material.STAINED_CLAY, Shapes.GREEN);
    }

    @Override
    Location spawnOf(UUID player) {
        return arena.at(0.5, 1, 0.5, -90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 7;
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {stack(Material.WOOL), stack(Material.WOOL)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void onFight() {
        nextHit = 25 + random.nextInt(35);
        hitsLeft = current().id.equals("double") ? 2 : 1;
        groundTicks = 0;
        placedSinceHit = 0;
        lastHitTick = 0;
        double side = random.nextBoolean() ? 1 : -1;
        if (current().id.equals("random")) {
            double angle = random.nextDouble() * Math.PI * 2;
            way = new Vector(Math.cos(angle), 0, Math.sin(angle));
        } else {
            way = new Vector(0, 0, side);
        }
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null) {
            return;
        }
        if (hitsLeft > 0) {
            if (--nextHit <= 0) {
                hit(player);
                hitsLeft--;
                lastHitTick = tickCount;
                nextHit = 10;
            }
            return;
        }
        if (tickCount - lastHitTick < 8) {
            return;
        }
        if (onGround(player)) {
            groundTicks++;
        } else {
            groundTicks = 0;
        }
        if (groundTicks >= SAVED_TICKS) {
            if (placedSinceHit == 0) {
                player.sendMessage(ChatColor.GRAY + "No clutch needed: next hit.");
                startRound();
                return;
            }
            streak++;
            player.sendMessage(ChatColor.GREEN + "Clutch! " + ChatColor.GRAY + "Streak: " + ChatColor.WHITE + streak);
            sound(player, Sound.ORB_PICKUP, 1.2f);
            score(streak, true);
            hitsLeft = -1;
            groundTicks = Integer.MIN_VALUE;
            again(12L);
        }
    }

    /** A hit as a player would give it, away from the bridge. */
    private void hit(Player player) {
        double horizontal;
        double vertical;
        switch (current().id) {
            case "random":
                horizontal = 0.32 + random.nextDouble() * 0.3;
                vertical = 0.3 + random.nextDouble() * 0.15;
                break;
            case "extend":
                horizontal = 0.95;
                vertical = 0.42;
                break;
            default:
                horizontal = 0.42;
                vertical = 0.36;
        }
        player.setVelocity(way.clone().multiply(horizontal).setY(vertical));
        player.playEffect(EntityEffect.HURT);
        sound(player, Sound.HURT_FLESH, 1f);
        placedSinceHit = 0;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return arena.contains(block, 24, 10, 6);
    }

    @Override
    void placed(Player player, Block block) {
        placedSinceHit++;
    }

    @Override
    void fell(Player player) {
        String why = streak > 0 ? "You fell after " + streak + (streak == 1 ? " clutch" : " clutches") + ": the streak starts again."
                : "You fell.";
        streak = 0;
        failed(why);
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
        lines.add(ChatColor.GRAY + (hitsLeft > 0 ? "Get ready..." : "Clutch!"));
    }
}
