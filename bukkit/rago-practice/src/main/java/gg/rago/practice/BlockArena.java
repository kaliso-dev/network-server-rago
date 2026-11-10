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
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * The block placement arena, open to everyone: one arena where the players join and leave when
 * they like, endless sandstone, the block fight kit. On the owner's arena (block-fight-arena.cartes
 * in config.yml: the green arena in its walls) the players come in anywhere on the grass, and its
 * slime blocks throw whoever stands on them three blocks up and two forward, the way they look:
 * the way out of the black pit in the corner. Without it, a big platform and four islands over
 * the void. The kit: a wooden sword, two stacks of wool of the player's colour (they never run out)
 * and shears. Knock the others off or kill them; you come back at once. The blocks placed crack
 * more and more, and break after fifteen seconds, so that the arena never fills up. The kills
 * count in the stats of the mode.
 *
 * <p>Arena Free (free-ffa) is the same arena for a free for all fight: the Ranked Bedwars kit
 * (iron sword, wool, shears, golden apples, fireballs, a pearl) and iron armour, back at each life,
 * and a kill heals the killer.
 */
final class BlockArena extends PracticeGame {

    /** The life of a block placed: fifteen seconds. */
    static final int DECAY_TICKS = 300;
    /** How often the cracks of the blocks are brought up to date. */
    private static final int CRACK_TICKS = 5;
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
    /** The wool of each of those colours. */
    static final int[] WOOLS = {Shapes.RED, Shapes.BLUE, Shapes.LIME, Shapes.YELLOW, 3, Shapes.WHITE, 2, Shapes.ORANGE, 7, 10};

    /** A block placed: where, when, and the number of its cracks with their stage now. */
    private static final class Placed {
        final Block block;
        final int tick;
        final int id = BlockCracks.newId();
        int stage = -1;

        Placed(Block block, int tick) {
            this.block = block;
            this.tick = tick;
        }
    }

    private final Random random = new Random();
    /** The blocks placed, oldest first. */
    private final Deque<Placed> decaying = new ArrayDeque<>();
    /** The last placing of each block: an older one there no longer counts. */
    private final Map<Block, Placed> latest = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();
    /** The slime blocks of the map (x, y, z from the centre): they throw the players. */
    private final List<int[]> pads;
    private final Map<UUID, Integer> launched = new HashMap<>();
    /** True for Arena Free: the fight kit, iron armour, a kill heals. */
    private final boolean free;
    private final Map<UUID, Long> lastFireball = new HashMap<>();

    BlockArena(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, boolean free) {
        super(host, mode, arena, teams);
        this.free = free;
        this.pads = arena.map() != null ? arena.map().copy.find(SLIME_ID) : new ArrayList<>();
    }

    /** The name of the arena in its messages and sidebar. */
    private String title() {
        return free ? "Arena Free" : "Block placement arena";
    }

    /** The kit of Arena Free when the player has not arranged their own: the Ranked Bedwars one. */
    static ItemStack[] freeKit() {
        return new ItemStack[] {new ItemStack(Material.IRON_SWORD), new ItemStack(Material.WOOL, 64),
            new ItemStack(Material.WOOL, 64), new ItemStack(Material.SHEARS), new ItemStack(Material.GOLDEN_APPLE, 3),
            new ItemStack(Material.FIREBALL, 4), new ItemStack(Material.ENDER_PEARL)};
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
        if (free) {
            return freeKit();
        }
        return new ItemStack[] {new ItemStack(Material.WOOD_SWORD), new ItemStack(Material.WOOL, 64),
            new ItemStack(Material.WOOL, 64), new ItemStack(Material.SHEARS)};
    }

    /** The wool of the kit in the player's colour, wherever they put it. */
    @Override
    void afterKit(Player player) {
        int colour = WOOLS[Math.max(0, sideOf(player.getUniqueId())) % WOOLS.length];
        ItemStack[] items = player.getInventory().getContents();
        for (ItemStack item : items) {
            if (item != null && item.getType() == Material.WOOL) {
                item.setDurability((short) colour);
            }
        }
        player.getInventory().setContents(items);
    }

    @Override
    void armour(Player player) {
        int index = Math.max(0, sideOf(player.getUniqueId()));
        if (free) {
            ironArmour(player, COLOURS[index % COLOURS.length]);
        } else {
            leather(player, COLOURS[index % COLOURS.length]);
        }
    }

    /** In Arena Free, a fireball is thrown from the hand, one every half second. */
    @Override
    void interact(Player player, PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!free || item == null || item.getType() != Material.FIREBALL
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = lastFireball.get(player.getUniqueId());
        if (phase != Phase.PLAYING || (last != null && now - last < Fireballs.COOLDOWN_MS)) {
            return;
        }
        lastFireball.put(player.getUniqueId(), now);
        Fireballs.launch(player);
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
        Placed placed = new Placed(block, tickCount);
        Placed before = latest.put(block, placed);
        if (before != null) {
            // its cracks go with it
            BlockCracks.show(online(), before.id, block, -1, plugin.getLogger());
        }
        decaying.addLast(placed);
    }

    @Override
    void tick() {
        if (!pads.isEmpty()) {
            launchPads();
        }
        while (!decaying.isEmpty() && tickCount - decaying.peekFirst().tick >= DECAY_TICKS) {
            Placed placed = decaying.pollFirst();
            if (latest.get(placed.block) != placed) {
                continue;
            }
            latest.remove(placed.block);
            BlockCracks.show(online(), placed.id, placed.block, -1, plugin.getLogger());
            if (arena.isPlaced(placed.block)) {
                Block block = placed.block;
                breakEffect(block);
                arena.restore(block);
            }
        }
        if (tickCount % CRACK_TICKS == 0) {
            cracks();
        }
    }

    /** The sound and the bits of a broken block, in its colour: the effect of the server (id + data * 4096). */
    @SuppressWarnings("deprecation") // Block#getTypeId and getData: the 1.8 block id and data of the effect
    private static void breakEffect(Block block) {
        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getTypeId() | (block.getData() & 15) << 12);
    }

    /** The cracks of the blocks placed, deeper as they get old; a block broken before its time loses them. */
    private void cracks() {
        List<Player> viewers = online();
        java.util.Iterator<Placed> all = decaying.iterator();
        while (all.hasNext()) {
            Placed placed = all.next();
            if (latest.get(placed.block) != placed) {
                continue;
            }
            if (!arena.isPlaced(placed.block) || placed.block.getType() == Material.AIR) {
                BlockCracks.show(viewers, placed.id, placed.block, -1, plugin.getLogger());
                latest.remove(placed.block);
                all.remove();
                continue;
            }
            int stage = BlockCracks.stage(tickCount - placed.tick, DECAY_TICKS);
            if (stage != placed.stage) {
                placed.stage = stage;
                BlockCracks.show(viewers, placed.id, placed.block, stage, plugin.getLogger());
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
        if (free) {
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "Arena Free" + ChatColor.GRAY
                    + " - everyone in one arena, the Ranked Bedwars kit: a kill heals you. The slime in the black pit throws"
                    + " you out of it; your blocks go away after 15 seconds.");
        } else {
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Block placement arena" + ChatColor.GRAY
                    + " - everyone in one arena: knock them off, build, clutch. Your blocks go away after 15 seconds.");
        }
        player.sendMessage(ChatColor.GRAY + "/spawn takes you back to the lobby.");
        broadcastJoin(player);
    }

    private void broadcastJoin(Player player) {
        for (Player other : online()) {
            if (!other.equals(player)) {
                other.sendMessage(ChatColor.GRAY + player.getDisplayName() + " joined the arena.");
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
            broadcast(ChatColor.RED + victim.getDisplayName() + ChatColor.GRAY + (free ? " was killed by " : " was knocked off by ")
                    + ChatColor.GREEN + killer.getDisplayName() + (streak >= 3 ? ChatColor.GOLD + " (" + streak + " in a row)" : ""),
                    null);
            sound(killer, Sound.ORB_PICKUP, 1.2f);
            if (free) {
                killer.setHealth(killer.getMaxHealth());
            }
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
        lastFireball.remove(id);
        if (phase != Phase.ENDED) {
            if (online().isEmpty()) {
                finish(-1, null);
            } else {
                broadcast(ChatColor.GRAY + player.getDisplayName() + " left the arena.", null);
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
        lines.add((free ? ChatColor.GREEN : ChatColor.AQUA) + title());
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
