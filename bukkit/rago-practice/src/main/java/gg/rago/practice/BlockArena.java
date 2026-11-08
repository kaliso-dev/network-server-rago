package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The block placement arena, open to everyone: one arena where the players join and leave when
 * they like, a big platform and four islands over the void, endless sandstone, the block fight
 * kit. Knock the others off or kill them; you come back at once somewhere on the platform. The
 * blocks placed go away after fifteen seconds, so that the arena never fills up. The kills count
 * in the stats of the mode.
 */
final class BlockArena extends PracticeGame {

    private static final int DECAY_TICKS = 300;
    private static final int HALF = 10;
    private static final int ISLAND = 19;
    private static final Color[] COLOURS = {Color.RED, Color.BLUE, Color.LIME, Color.YELLOW, Color.AQUA, Color.WHITE,
        Color.FUCHSIA, Color.ORANGE, Color.GRAY, Color.PURPLE};

    private final Random random = new Random();
    /** The blocks placed, oldest first, with the tick they were placed at. */
    private final Deque<Object[]> decaying = new ArrayDeque<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();

    BlockArena(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams);
    }

    @Override
    void build() {
        Shapes.island(arena, -HALF, -HALF, HALF, HALF, Material.SANDSTONE, 2, Material.SANDSTONE, 0);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                Shapes.island(arena, sx * ISLAND - 2, sz * ISLAND - 2, sx * ISLAND + 2, sz * ISLAND + 2, Material.STAINED_CLAY,
                        sx < 0 ? (sz < 0 ? Shapes.RED : Shapes.BLUE) : (sz < 0 ? Shapes.LIME : Shapes.YELLOW),
                        Material.STAINED_CLAY, Shapes.GRAY);
            }
        }
    }

    @Override
    Location spawnOf(UUID player) {
        double x = random.nextInt(2 * HALF - 3) - HALF + 2 + 0.5;
        double z = random.nextInt(2 * HALF - 3) - HALF + 2 + 0.5;
        return arena.at(x, 1, z, random.nextInt(360) - 180f);
    }

    @Override
    int countdownSeconds() {
        return 0;
    }

    @Override
    boolean freezes() {
        return false;
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    boolean counts() {
        return false;
    }

    @Override
    double fallY() {
        return arena.floorY() - 10;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.WOOD_SWORD), new ItemStack(Material.SANDSTONE, 64),
            new ItemStack(Material.SANDSTONE, 64)};
    }

    @Override
    void armour(Player player) {
        int index = Math.max(0, sideOf(player.getUniqueId()));
        leather(player, COLOURS[index % COLOURS.length]);
    }

    @Override
    boolean refills() {
        return true;
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        return event.getCause() != EntityDamageEvent.DamageCause.FALL;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return arena.contains(block, ISLAND + 8, 12, 14);
    }

    @Override
    void placed(Player player, Block block) {
        decaying.addLast(new Object[] {block, tickCount});
    }

    @Override
    void tick() {
        while (!decaying.isEmpty() && tickCount - (Integer) decaying.peekFirst()[1] >= DECAY_TICKS) {
            Block block = (Block) decaying.pollFirst()[0];
            if (arena.isPlaced(block)) {
                arena.restore(block);
            }
        }
    }

    @Override
    void intro(Player player) {
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Block placement arena" + ChatColor.GRAY
                + " - everyone in one arena: knock them off, build, clutch. Your blocks go away after 15 seconds.");
        player.sendMessage(ChatColor.GRAY + "/spawn takes you back to the lobby.");
        broadcastJoin(player);
    }

    private void broadcastJoin(Player player) {
        for (Player other : online()) {
            if (!other.equals(player)) {
                other.sendMessage(ChatColor.GRAY + player.getName() + " joined the arena.");
            }
        }
    }

    @Override
    void fell(Player player) {
        out(player);
    }

    @Override
    void lethal(Player victim, Player killer) {
        out(victim);
    }

    /** The player is out (a fall, a death): the kill to whoever hit them last, and back at once. */
    private void out(Player victim) {
        UUID by = killerOf(victim.getUniqueId());
        Player killer = by == null ? null : Bukkit.getPlayer(by);
        if (killer != null && host.gameOf(by) == this && !killer.equals(victim)) {
            plugin.lobby().recordKill(mode.id(), killer.getUniqueId(), victim.getUniqueId());
            kills.merge(by, 1, Integer::sum);
            int streak = streaks.merge(by, 1, Integer::sum);
            broadcast(ChatColor.RED + victim.getName() + ChatColor.GRAY + " was knocked off by " + ChatColor.GREEN
                    + killer.getName() + (streak >= 3 ? ChatColor.GOLD + " (" + streak + " in a row)" : ""), null);
            sound(killer, Sound.ORB_PICKUP, 1.2f);
        } else {
            victim.sendMessage(ChatColor.GRAY + "You fell.");
        }
        streaks.remove(victim.getUniqueId());
        forgetHits(victim.getUniqueId());
        placeAtStart(victim);
        sidebars();
    }

    @Override
    void leave(Player player, boolean toLobby) {
        UUID id = player.getUniqueId();
        host.release(id);
        streaks.remove(id);
        if (phase != Phase.ENDED) {
            if (online().isEmpty()) {
                finish(-1, null);
            } else {
                broadcast(ChatColor.GRAY + player.getName() + " left the arena.", null);
                sidebars();
            }
        }
        if (toLobby) {
            plugin.lobby().sendToLobby(player);
        }
    }

    @Override
    List<String> sidebar(UUID viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.AQUA + "Block placement arena");
        lines.add("");
        lines.add(ChatColor.WHITE + "Players: " + ChatColor.GREEN + online().size());
        lines.add(ChatColor.WHITE + "Your kills: " + ChatColor.GREEN + kills.getOrDefault(viewer, 0));
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streaks.getOrDefault(viewer, 0));
        UUID top = null;
        int most = 0;
        for (Player player : online()) {
            int count = kills.getOrDefault(player.getUniqueId(), 0);
            if (count > most) {
                most = count;
                top = player.getUniqueId();
            }
        }
        if (top != null) {
            lines.add("");
            lines.add(Duel.cut(ChatColor.WHITE + "Top: " + ChatColor.GOLD + nameOf(top) + ChatColor.GRAY + " " + most + " kills"));
        }
        lines.add("");
        lines.add(ChatColor.WHITE + "Ping: " + Ping.text(Bukkit.getPlayer(viewer)));
        return lines;
    }
}
