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
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Pearl practice, in real Bed Wars situations, with one pearl a try: to an island that is
 * somewhere else each time (higher, lower, to the side), to an island far away (as to the middle),
 * or back onto your island after a hit that sends you into the void. Each good pearl adds one to
 * the streak; a miss ends it.
 */
final class PearlTraining extends Training {

    /** A pearl in the air this long has fallen into the void. */
    private static final int LOST_TICKS = 140;
    private static final int TARGET_HALF = 2;

    private final Random random = new Random();
    private int streak;
    /** The centre of the target island (offsets), and its top. */
    private int tx;
    private int ty;
    private int tz;
    private int thrownTick = -1;
    private int hitIn;
    private boolean saved;

    PearlTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams,
                new Variant("island", "To an island", false, "pearls"),
                new Variant("far", "Long pearl", false, "pearls"),
                new Variant("save", "Pearl to save yourself", false, "saves"));
    }

    @Override
    String describe() {
        switch (current().id) {
            case "far":
                return "pearl onto the island far away, as to the middle.";
            case "save":
                return "a hit sends you into the void: pearl back onto your island.";
            default:
                return "pearl onto the green island, somewhere else each time.";
        }
    }

    private boolean saving() {
        return current().id.equals("save");
    }

    @Override
    void build() {
        Shapes.island(arena, -3, -3, 3, 3, Material.WOOD, 0, Material.LOG, 0);
    }

    @Override
    Location spawnOf(UUID player) {
        // to save yourself: on the edge, back to the void
        return saving() ? arena.at(2.5, 1, 0.5, 90f) : arena.at(0.5, 1, 0.5, -90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - (saving() ? 25 : 15);
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {new ItemStack(Material.ENDER_PEARL, 1)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        thrownTick = -1;
        saved = false;
        hitIn = 20 + random.nextInt(30);
        if (saving()) {
            return;
        }
        boolean far = current().id.equals("far");
        double distance = far ? 34 + random.nextInt(10) : 14 + random.nextInt(14);
        double angle = Math.toRadians(random.nextInt(100) - 50);
        tx = (int) Math.round(Math.cos(angle) * distance);
        tz = (int) Math.round(Math.sin(angle) * distance);
        ty = far ? random.nextInt(5) - 1 : random.nextInt(9) - 3;
        for (int x = tx - TARGET_HALF; x <= tx + TARGET_HALF; x++) {
            for (int z = tz - TARGET_HALF; z <= tz + TARGET_HALF; z++) {
                arena.put(x, ty, z, Material.STAINED_CLAY, Shapes.LIME);
            }
        }
        arena.put(tx, ty, tz, Material.GOLD_BLOCK, 0);
        arena.put(tx, ty - 1, tz, Material.STAINED_CLAY, Shapes.GREEN);
    }

    @Override
    void onFight() {
        Player player = player();
        if (player != null && !saving()) {
            // look at the target
            Location at = player.getLocation();
            Vector to = arena.at(tx + 0.5, ty + 1, tz + 0.5).toVector().subtract(at.toVector());
            at.setDirection(to);
            at.setPitch(0f);
            player.teleport(at);
        }
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null) {
            return;
        }
        if (saving() && hitIn > 0 && --hitIn == 0) {
            // the hit that sends you off, as a player would give it
            double side = random.nextDouble() * 0.4 - 0.2;
            player.setVelocity(new Vector(1.05 + random.nextDouble() * 0.2, 0.42, side));
            player.playEffect(EntityEffect.HURT);
            sound(player, Sound.HURT_FLESH, 1f);
        }
        if (thrownTick >= 0 && tickCount - thrownTick > LOST_TICKS) {
            missed("Your pearl fell into the void.");
        }
    }

    @Override
    void launched(Player player, Projectile projectile, ProjectileLaunchEvent event) {
        if (projectile instanceof EnderPearl) {
            thrownTick = tickCount;
        }
    }

    @Override
    void pearled(Player player, PlayerTeleportEvent event) {
        thrownTick = -1;
        Location to = event.getTo();
        double[] at = arena.offset(to);
        if (saving()) {
            if (Math.abs(at[0]) <= 3.7 && Math.abs(at[2]) <= 3.7 && at[1] >= 0.5 && at[1] <= 4) {
                saved = true;
                success(player, "Saved!");
            } else {
                event.setCancelled(true);
                missed("Your pearl missed the island.");
            }
            return;
        }
        double dx = at[0] - (tx + 0.5);
        double dz = at[2] - (tz + 0.5);
        if (Math.abs(dx) <= TARGET_HALF + 0.8 && Math.abs(dz) <= TARGET_HALF + 0.8 && at[1] >= ty + 0.5 && at[1] <= ty + 4) {
            success(player, "On the island!");
        } else {
            event.setCancelled(true);
            missed("Missed by " + Math.round(Math.sqrt(dx * dx + dz * dz)) + " blocks.");
        }
    }

    private void success(Player player, String what) {
        streak++;
        player.sendMessage(ChatColor.GREEN + what + " " + ChatColor.GRAY + "Streak: " + ChatColor.WHITE + streak);
        sound(player, Sound.ORB_PICKUP, 1.2f);
        score(streak, true);
        hitIn = 0;
        again(saving() ? 25L : 15L);
    }

    private void missed(String why) {
        String end = streak > 0 ? " The streak of " + streak + " ends." : "";
        streak = 0;
        failed(why + end);
    }

    @Override
    void fell(Player player) {
        if (saved) {
            return;
        }
        missed(saving() ? "You fell: throw your pearl back at your island as soon as you are hit." : "You fell.");
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
        if (!saving()) {
            lines.add(ChatColor.WHITE + "Distance: " + ChatColor.GREEN + Math.round(Math.sqrt(tx * tx + tz * tz)) + " blocks");
        }
    }
}
