package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Aim trainer: a target the size of a player moves in front of the player as a player would in a
 * fight, and every click that lands on it counts. It strafes left and right, changing direction
 * at random; it strafes and jumps; or it circles around the player. The thirty seconds start with
 * the first hit; the score is the hits, and the accuracy (hits out of clicks) is shown.
 */
final class AimTrainer extends Training {

    private static final int DURATION_TICKS = 600;
    private static final double STRAFE_SPEED = 0.22;
    private static final double CIRCLE_RADIUS = 2.8;

    private final Random random = new Random();
    private ArmorStand target;
    private int hits;
    private int clicks;
    private int startTick = -1;
    private boolean done;
    /** The target's place: its sideways offset and distance (strafe), or its angle (circle). */
    private double side;
    private double distance = 3;
    private double angle;
    private int direction = 1;
    private int turnIn;
    private double jumpY;
    private double jumpSpeed;
    private int jumpIn;

    AimTrainer(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams,
                new Variant("strafe", "Strafing target", false, "hits"),
                new Variant("jump", "Strafing and jumping", false, "hits"),
                new Variant("circle", "Circling target", false, "hits"));
    }

    @Override
    String describe() {
        switch (current().id) {
            case "jump":
                return "the target strafes and jumps: keep your crosshair on it.";
            case "circle":
                return "the target circles around you: follow it.";
            default:
                return "the target strafes left and right: hit it as often as you can in 30 seconds.";
        }
    }

    @Override
    void build() {
        Shapes.floor(arena, -8, -8, 8, 8, 0, Material.STAINED_CLAY, Shapes.WHITE);
        Shapes.walls(arena, -8, -8, 8, 8, 1, 3, Material.BARRIER, 0);
    }

    @Override
    Location spawnOf(UUID player) {
        return arena.at(-2.5, 1, 0.5, -90f);
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {new ItemStack(Material.DIAMOND_SWORD)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return false;
    }

    @Override
    void roundReset() {
        hits = 0;
        clicks = 0;
        startTick = -1;
        done = false;
        side = 0;
        distance = 3;
        angle = 0;
        direction = 1;
        turnIn = 10;
        jumpY = 0;
        jumpSpeed = 0;
        jumpIn = 20;
    }

    @Override
    void onFight() {
        if (target == null || !target.isValid()) {
            target = arena.world().spawn(place(), ArmorStand.class);
            target.setGravity(false);
            target.setBasePlate(false);
            target.setArms(true);
            target.setCustomName(ChatColor.RED + "Target");
            target.setCustomNameVisible(true);
            target.setHelmet(leatherPiece(Material.LEATHER_HELMET, Color.RED));
            target.setChestplate(leatherPiece(Material.LEATHER_CHESTPLATE, Color.RED));
            target.setLeggings(leatherPiece(Material.LEATHER_LEGGINGS, Color.RED));
            target.setBoots(leatherPiece(Material.LEATHER_BOOTS, Color.RED));
            target.setItemInHand(new ItemStack(Material.IRON_SWORD));
            host.own(target, this);
        } else {
            target.teleport(place());
        }
    }

    @Override
    void ended() {
        if (target != null) {
            host.disown(target);
            target.remove();
            target = null;
        }
    }

    /** Where the target stands now, looking at the player. */
    private Location place() {
        Player player = player();
        Location centre = player == null ? spawnOf(me) : player.getLocation();
        Location at;
        if (current().id.equals("circle")) {
            at = centre.clone().add(Math.cos(angle) * CIRCLE_RADIUS, 0, Math.sin(angle) * CIRCLE_RADIUS);
            at.setY(arena.floorY() + 1);
        } else {
            at = arena.at(-2.5 + distance, 1, 0.5 + side);
        }
        at.add(0, jumpY, 0);
        // inside the walls
        double[] offset = arena.offset(at);
        at.setX(arena.centreX() + Math.max(-7.2, Math.min(7.2, offset[0])));
        at.setZ(arena.centreZ() + Math.max(-7.2, Math.min(7.2, offset[2])));
        double dx = centre.getX() - at.getX();
        double dz = centre.getZ() - at.getZ();
        at.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        at.setPitch(0f);
        return at;
    }

    @Override
    void tick() {
        if (target == null || !target.isValid()) {
            return;
        }
        move();
        target.teleport(place());
        if (startTick >= 0 && !done && tickCount - startTick >= DURATION_TICKS) {
            done = true;
            Player player = player();
            score(hits);
            if (player != null) {
                player.sendMessage(ChatColor.GRAY + "Accuracy: " + ChatColor.WHITE + accuracy() + ChatColor.GRAY + " (" + hits
                        + " hits out of " + clicks + " clicks).");
            }
            again(60L);
        }
        if (startTick >= 0 && !done) {
            refresh();
        }
    }

    /** One tick of the target's moves: strafes that change direction at random, jumps. */
    private void move() {
        if (--turnIn <= 0) {
            direction = random.nextInt(3) == 0 ? direction : -direction;
            turnIn = 6 + random.nextInt(16);
        }
        if (current().id.equals("circle")) {
            angle += direction * (0.07 + random.nextDouble() * 0.03);
        } else {
            side += direction * STRAFE_SPEED;
            if (Math.abs(side) > 3) {
                side = Math.signum(side) * 3;
                direction = -direction;
            }
            distance += (random.nextDouble() - 0.5) * 0.12;
            distance = Math.max(2.2, Math.min(3.8, distance));
        }
        if (current().id.equals("jump")) {
            if (jumpY <= 0 && --jumpIn <= 0) {
                jumpSpeed = 0.42;
                jumpIn = 12 + random.nextInt(20);
            }
            if (jumpSpeed != 0 || jumpY > 0) {
                jumpY += jumpSpeed;
                jumpSpeed = (jumpSpeed - 0.08) * 0.98;
                if (jumpY <= 0) {
                    jumpY = 0;
                    jumpSpeed = 0;
                }
            }
        }
    }

    @Override
    void hitEntity(Player attacker, Entity entity, EntityDamageEvent event) {
        event.setCancelled(true);
        if (entity != target || done) {
            return;
        }
        if (startTick < 0) {
            startTick = tickCount;
            clicks = 1;
            attacker.sendMessage(ChatColor.GREEN + "Go! 30 seconds.");
        }
        hits++;
        sound(attacker, Sound.SUCCESSFUL_HIT, 1.6f);
    }

    @Override
    void swung(Player player) {
        if (startTick >= 0 && !done) {
            clicks++;
        }
    }

    private String accuracy() {
        return clicks == 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", 100.0 * Math.min(hits, clicks) / clicks);
    }

    @Override
    void trainingLines(List<String> lines) {
        int left = startTick < 0 ? DURATION_TICKS : Math.max(0, DURATION_TICKS - (tickCount - startTick));
        lines.add(ChatColor.WHITE + "Time left: " + ChatColor.GREEN + clock((left + 19) / 20));
        lines.add(ChatColor.WHITE + "Hits: " + ChatColor.GREEN + hits);
        lines.add(ChatColor.WHITE + "Accuracy: " + ChatColor.GREEN + accuracy());
        if (startTick < 0) {
            lines.add(ChatColor.GRAY + "Hit the target to start");
        }
    }
}
