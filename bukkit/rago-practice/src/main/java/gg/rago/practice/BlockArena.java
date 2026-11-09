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
import org.bukkit.util.Vector;

/**
 * The block placement arena, open to everyone: one arena where the players join and leave when
 * they like, endless sandstone, the block fight kit. On the owner's arena (block-fight-arena.cartes
 * in config.yml: the green arena in its walls) the players come in anywhere on the grass, and its
 * slime blocks throw whoever stands on them three blocks up and two forward, the way they look:
 * the way out of the black pit in the corner. Without it, a big platform and four islands over
 * the void. Knock the others off or kill them; you come back at once. The blocks placed go away
 * after fifteen seconds, so that the arena never fills up. The kills count in the stats of the mode.
 */
final class BlockArena extends PracticeGame {

    private static final int DECAY_TICKS = 300;
    private static final int HALF = 10;
    private static final int ISLAND = 19;
    /** On the map: the players come in this far at most from the centre, on the floor. */
    private static final int SPREAD = 30;
    /** The throw of a slime block: three blocks up, two forward. */
    static final double LAUNCH_UP = 0.7;
    static final double LAUNCH_FORWARD = 0.25;
    /** Ticks before the same player is thrown again. */
    private static final int LAUNCH_PAUSE = 10;
    /** The id of the slime block. */
    static final int SLIME_ID = 165;
    private static final Color[] COLOURS = {Color.RED, Color.BLUE, Color.LIME, Color.YELLOW, Color.AQUA, Color.WHITE,
        Color.FUCHSIA, Color.ORANGE, Color.GRAY, Color.PURPLE};

    private final Random random = new Random();
    /** The blocks placed, oldest first, with the tick they were placed at. */
    private final Deque<Object[]> decaying = new ArrayDeque<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();
    /** The slime blocks of the map (x, y, z from the centre): they throw the players. */
    private final List<int[]> pads;
    private final Map<UUID, Integer> launched = new HashMap<>();

    BlockArena(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams);
        this.pads = arena.map() != null ? arena.map().copy.find(SLIME_ID) : new ArrayList<>();
    }

    @Override
    void build() {
        if (arena.map() != null) {
            return;
        }
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
        if (arena.map() != null) {
            return mapSpawn();
        }
        double x = random.nextInt(2 * HALF - 3) - HALF + 2 + 0.5;
        double z = random.nextInt(2 * HALF - 3) - HALF + 2 + 0.5;
        return arena.at(x, 1, z, random.nextInt(360) - 180f);
    }

    /** Somewhere on the floor of the map, with room to stand; the centre if no try finds one. */
    private Location mapSpawn() {
        for (int i = 0; i < 30; i++) {
            int x = random.nextInt(2 * SPREAD + 1) - SPREAD;
            int z = random.nextInt(2 * SPREAD + 1) - SPREAD;
            if (arena.block(x, 0, z).getType().isSolid() && arena.block(x, 1, z).isEmpty() && arena.block(x, 2, z).isEmpty()) {
                return arena.at(x + 0.5, 1, z + 0.5, random.nextInt(360) - 180f);
            }
        }
        return arena.at(0.5, 1, 0.5, random.nextInt(360) - 180f);
    }

    /**
     * True if feet at (x, y, z) from the centre stand on this slime block (x, y, z from the
     * centre): the player, 0.6 wide, over its top, or just above it.
     */
    static boolean onPad(double x, double y, double z, int[] pad) {
        double top = pad[1] + 1;
        return y >= top - 0.01 && y <= top + 0.5 && x + 0.3 > pad[0] && x - 0.3 < pad[0] + 1
                && z + 0.3 > pad[2] && z - 0.3 < pad[2] + 1;
    }

    /** The throw of a slime block for a player looking along this yaw: x, y, z. */
    static double[] launch(float yaw) {
        double r = Math.toRadians(yaw);
        return new double[] {-Math.sin(r) * LAUNCH_FORWARD, LAUNCH_UP, Math.cos(r) * LAUNCH_FORWARD};
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
        if (arena.map() != null) {
            return arena.contains(block, 50, 4, 18);
        }
        return arena.contains(block, ISLAND + 8, 12, 14);
    }

    @Override
    void placed(Player player, Block block) {
        decaying.addLast(new Object[] {block, tickCount});
    }

    @Override
    void tick() {
        if (!pads.isEmpty()) {
            launchPads();
        }
        while (!decaying.isEmpty() && tickCount - (Integer) decaying.peekFirst()[1] >= DECAY_TICKS) {
            Block block = (Block) decaying.pollFirst()[0];
            if (arena.isPlaced(block)) {
                arena.restore(block);
            }
        }
    }

    /** Whoever stands on a slime block of the map goes three blocks up and two forward. */
    private void launchPads() {
        for (Player player : online()) {
            Integer last = launched.get(player.getUniqueId());
            if (last != null && tickCount - last < LAUNCH_PAUSE) {
                continue;
            }
            double[] feet = arena.offset(player.getLocation());
            for (int[] pad : pads) {
                if (onPad(feet[0], feet[1], feet[2], pad)) {
                    double[] throw_ = launch(player.getLocation().getYaw());
                    player.setVelocity(new Vector(throw_[0], throw_[1], throw_[2]));
                    player.setFallDistance(0f);
                    sound(player, Sound.SLIME_WALK, 1f);
                    launched.put(player.getUniqueId(), tickCount);
                    break;
                }
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
        launched.remove(id);
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
