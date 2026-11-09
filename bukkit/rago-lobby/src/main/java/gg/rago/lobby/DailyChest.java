package gg.rago.lobby;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * The daily reward chest of the spawn, as on Hypixel: a chest on the point "daily" (/lobby point
 * daily, where the chest goes), with lines above it that tell each player whether their reward
 * waits, and their streak. A click on it claims the reward (the same as /daily). Without the
 * point, there is no chest: /daily and the profile still give the reward. Main thread only.
 */
final class DailyChest {

    static final String POINT = "daily";
    static final int LINES = 3;

    private final Profiles profiles;
    private Block chest;

    DailyChest(Profiles profiles) {
        this.profiles = profiles;
    }

    /** Puts the chest and its lines on the point, after the NPCs (which clear every hologram). */
    void place(LobbyPoints points, World world, FakeEntities fakes) {
        SpawnPoint point = points.get(POINT);
        Block at = point == null ? null : point.in(world).getBlock();
        if (chest != null && !chest.equals(at) && chest.getType() == Material.CHEST) {
            chest.setType(Material.AIR);
        }
        chest = at;
        if (at == null) {
            return;
        }
        if (at.getType() != Material.CHEST) {
            at.setType(Material.CHEST);
        }
        Location bottom = at.getLocation().add(0.5, 1.25, 0.5);
        fakes.add(new FakeHologram(bottom, LINES, viewer -> lines(viewer), (player, left) -> claim(player)));
    }

    /** True if the block is the daily chest. */
    boolean isChest(Block block) {
        return chest != null && chest.equals(block);
    }

    List<String> lines(Player viewer) {
        long now = System.currentTimeMillis();
        PlayerStats profile = profiles.of(viewer);
        boolean ready = profiles.dailyReady(viewer.getUniqueId());
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.AQUA + "" + ChatColor.BOLD + "DAILY REWARD");
        lines.add(ready ? ChatColor.YELLOW + "Click to claim!"
                : ChatColor.GRAY + StatsPeriod.DAY.resetText(now).replace("Resets in", "Next one in"));
        int streak = profile.dailyStreak();
        lines.add(streak > 0 ? ChatColor.GRAY + "Streak: " + ChatColor.GREEN + streak + (streak == 1 ? " day" : " days") : "");
        return lines;
    }

    /** A click on the chest: the reward, with a burst over the chest. */
    void claim(Player player) {
        boolean ready = profiles.dailyReady(player.getUniqueId());
        profiles.claimDaily(player);
        if (ready && chest != null) {
            Location top = chest.getLocation().add(0.5, 1.0, 0.5);
            top.getWorld().playSound(top, Sound.CHEST_OPEN, 1f, 1f);
            top.getWorld().spigot().playEffect(top, Effect.HAPPY_VILLAGER, 0, 0, 0.5f, 0.5f, 0.5f, 0f, 25, 32);
            top.getWorld().spigot().playEffect(top, Effect.FIREWORKS_SPARK, 0, 0, 0.3f, 0.6f, 0.3f, 0.1f, 20, 32);
        }
    }
}
