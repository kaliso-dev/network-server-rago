package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Pearl practice on the owner's Bed Wars maps, in a loop: each attempt on the next map (each
 * server start takes the side of one team of each map), at another place and in another direction,
 * the kinds of situation in turn (see {@link PearlMaps}): a pearl from one island onto another, a
 * hit that throws the player off an island and a pearl back, and above all the pearl block clutch,
 * where the player is hit off their bridge a few blocks under an island, pearls to its side and
 * places blocks under themselves against the wall. One pearl and a stack of wool a try; no block
 * goes under the lower limit of the part, as under the islands on Hypixel. The try counts once
 * the player stands on the island aimed at (or on their blocks against it); each one adds to the
 * streak, a miss ends it. The next one comes by itself: nothing to choose. The levels Pearls,
 * Saves and Clutches play one kind only, still map after map.
 *
 * <p>The zone holds one part at a time: when the map changes, its blocks go and those of the next
 * part come, written straight into the chunks ({@link FastBlocks}, some 10 ms). Without any of the
 * maps, the plugin's own islands ({@link PearlMaps#plain}).
 */
final class PearlTraining extends Training {

    /** Where the pearl practice plays, for all its zones: the parts of the maps, and the part each zone holds now. */
    static final class Places {
        final List<PearlMaps.Part> parts;
        /** By the slot of the zone: the part built there. Main thread only. */
        private final Map<Integer, PearlMaps.Part> built = new HashMap<>();
        /** The zones being built again. */
        private final Set<Integer> busy = new HashSet<>();
        private FastBlocks fast;
        /** True once the chunks could not be written straight: the Bukkit way from then on. */
        private boolean noFast;

        Places(List<PearlMaps.Part> parts) {
            List<PearlMaps.Part> kept = new ArrayList<>(parts);
            if (kept.isEmpty()) {
                kept.add(PearlMaps.plain());
            }
            this.parts = Collections.unmodifiableList(kept);
        }

        /** The writer of the chunks of the zones, or null to place the blocks the Bukkit way. */
        FastBlocks fast(org.bukkit.World world, Logger logger) {
            if (fast == null && !noFast) {
                fast = FastBlocks.of(world, logger);
                noFast = fast == null;
            }
            return fast;
        }

        void fastFailed(Logger logger, Exception e) {
            logger.log(Level.WARNING, "The pearl practice could not write a map into its zone straight: the Bukkit way"
                    + " from now on (a short lag at each map)", e);
            fast = null;
            noFast = true;
        }
    }

    /** The kinds of the loop, in turn: the clutch every other time (it is what to train above all). */
    static final PearlMaps.Kind[] LOOP = {PearlMaps.Kind.PEARL, PearlMaps.Kind.CLUTCH, PearlMaps.Kind.SAVE, PearlMaps.Kind.CLUTCH};
    /** Ticks standing still on the island aimed at that make a good try. */
    private static final int STAND_TICKS = 10;
    /** A pearl in the air this long has fallen into the void. */
    private static final int LOST_TICKS = 140;
    /** A pearl falling this far under the lowest height of the blocks has missed, without waiting longer. */
    private static final int LOST_BELOW_LIMIT = 3;
    /** Blocks the zone changes each tick while it is built again the Bukkit way (some 30 microseconds each). */
    static final int BLOCKS_PER_TICK = 1500;
    /** The time of a tick the zone takes at most while its chunks are written straight. */
    private static final long BUILD_NANOS_PER_TICK = 15_000_000L;
    /** How high over the floor of the zone the player waits while it is built again. */
    private static final int WAIT_HEIGHT = 90;
    /** Columns around the island aimed at where a player still stands on it (on their own blocks against its side). */
    private static final int REACH = 2;
    /** A player this far under the lowest place where blocks go has fallen into the void. */
    private static final int FALL_UNDER_LIMIT = 12;

    private final Places places;
    private final Random random = new Random();
    /** The maps of the loop, in an order of this game's own. */
    private final List<PearlMaps.Part> order;
    private int nextPart;
    private int turn;
    private PearlMaps.Part part;
    private PearlMaps.Situation situation;
    /** The situation of the next attempt, chosen before its zone is ready. */
    private PearlMaps.Situation pending;
    private PearlMaps.Part pendingPart;
    private boolean loading;
    private BukkitTask building;
    private int streak;
    private int thrownTick = -1;
    /** The pearl in the air, or null. */
    private Projectile pearl;
    private boolean landed;
    private boolean placedAny;
    /** Ticks before the hit; 0: no hit (any more). */
    private int hitIn;
    private int standing;
    private long toldAt;

    PearlTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, Places places) {
        super(host, mode, arena, teams, variants());
        this.places = places;
        this.order = new ArrayList<>(places.parts);
        Collections.shuffle(order, random);
    }

    static Variant[] variants() {
        return new Variant[] {
            new Variant("loop", "Loop", false, "in a row"),
            new Variant("pearls", "Pearls", false, "pearls"),
            new Variant("saves", "Saves", false, "saves"),
            new Variant("clutches", "Pearl clutches", false, "clutches"),
        };
    }

    /** The kind of this level for the attempt after so many (null: the loop's turn). */
    static PearlMaps.Kind kindOf(String variantId, int turn) {
        switch (variantId) {
            case "pearls":
                return PearlMaps.Kind.PEARL;
            case "saves":
                return PearlMaps.Kind.SAVE;
            case "clutches":
                return PearlMaps.Kind.CLUTCH;
            default:
                return LOOP[Math.floorMod(turn, LOOP.length)];
        }
    }

    /** True if no block may be placed at this height of the part (from its origin), as under the islands on Hypixel. */
    static boolean belowLimit(int y, int limitY) {
        return y < limitY;
    }

    /** A fall or a miss gives the next attempt by itself: no blaze rod. */
    @Override
    boolean restartUsed() {
        return false;
    }

    @Override
    String describe() {
        switch (current().id) {
            case "pearls":
                return "pearl from one island onto another, a new map each time.";
            case "saves":
                return "a hit throws you off the island: pearl back onto it, a new map each time.";
            case "clutches":
                return "hit off your bridge under an island: pearl to its side, then place blocks under you at once.";
            default:
                return "a new map each time: pearl onto an island, pearl back after a hit, and pearl block clutches"
                        + " against the side of an island.";
        }
    }

    /** The settings changed (the level): the next attempt takes it, at once (on the map coming, if it has one). */
    @Override
    void settingsChanged() {
        pending = null;
        streak = 0;
        super.settingsChanged();
    }

    /** The zone is built with the part of each attempt (see {@link #startRound}). */
    @Override
    void build() {
    }

    // --- The next attempt -----------------------------------------------------------------------

    /** The next situation: the next map of the loop that has one of the kind, not the last one played. */
    private void choose() {
        PearlMaps.Kind kind = kindOf(current().id, turn);
        if (pendingPart != null && !pendingPart.of(kind).isEmpty()) {
            // the map chosen already (being built): a situation of the level now chosen on it
            List<PearlMaps.Situation> found = pendingPart.of(kind);
            pending = found.get(random.nextInt(found.size()));
            return;
        }
        for (int tries = 0; tries < order.size(); tries++) {
            PearlMaps.Part next = order.get(nextPart % order.size());
            nextPart++;
            List<PearlMaps.Situation> found = next.of(kind);
            if (found.isEmpty()) {
                continue;
            }
            if (found.size() > 1) {
                found.remove(situation);
            }
            pendingPart = next;
            pending = found.get(random.nextInt(found.size()));
            return;
        }
        // no map has this kind: any situation of the next map
        PearlMaps.Part next = order.get(nextPart++ % order.size());
        pendingPart = next;
        pending = next.situations.get(random.nextInt(next.situations.size()));
    }

    /**
     * A new attempt: the next situation; when its map is not the one of the zone, the zone is
     * built again first, the player waiting above it.
     */
    @Override
    void startRound() {
        if (loading) {
            return;
        }
        if (pending == null) {
            choose();
        }
        int slot = arena.slot();
        if (places.busy.contains(slot)) {
            later(5L, this::startRound);
            return;
        }
        if (places.built.get(slot) != pendingPart) {
            rebuild(pendingPart);
            return;
        }
        part = pendingPart;
        situation = pending;
        pending = null;
        pendingPart = null;
        turn++;
        super.startRound();
        Player player = player();
        if (player != null) {
            longTitle(player, ChatColor.AQUA + part.map, ChatColor.WHITE + situation.title, 50);
            player.sendMessage(ChatColor.AQUA + part.map + ChatColor.GRAY + ": " + ChatColor.WHITE + situation.what);
        }
    }

    /**
     * The zone gets the blocks of this part: those of the part it holds go first. Straight into the
     * chunks when the server allows it ({@link FastBlocks}): some 10 ms, in one tick; else the Bukkit
     * way, a little at a time, the player waiting above. Even when the game ends meanwhile, the
     * blocks keep coming, so that the zone holds a whole part for the next game.
     */
    private void rebuild(PearlMaps.Part next) {
        final int slot = arena.slot();
        final PearlMaps.Part old = places.built.remove(slot);
        places.busy.add(slot);
        loading = true;
        // the blocks of the last attempt (the player's, the bridge of a clutch) go before those of the map
        arena.restore();
        dropPearl();
        final int[] gone = old == null ? new int[0] : old.copy.blocks();
        final int[] coming = next.copy.blocks();
        final FastBlocks fast = places.fast(arena.world(), plugin.getLogger());
        Player player = player();
        if (player != null && fast == null) {
            // the slow way: the player waits above
            waitAbove(player);
            longTitle(player, ChatColor.AQUA + next.map, ChatColor.GRAY + "Next situation...", 60);
        }
        final List<FastBlocks.Chunk> chunks = fast == null ? null
                : FastBlocks.byChunk(gone, coming, arena.centreX(), arena.floorY(), arena.centreZ());
        building = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private boolean slow = fast == null;
            private int chunk;
            private int cleared;
            private int placed;

            @Override
            public void run() {
                if (!slow) {
                    long until = System.nanoTime() + BUILD_NANOS_PER_TICK;
                    try {
                        while (chunk < chunks.size() && System.nanoTime() < until) {
                            chunks.get(chunk++).write(fast);
                        }
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        // the whole part again, the Bukkit way
                        places.fastFailed(plugin.getLogger(), e);
                        slow = true;
                    }
                    if (!slow && chunk < chunks.size()) {
                        return;
                    }
                }
                if (slow && !slowStep()) {
                    return;
                }
                cancel(building);
                building = null;
                places.built.put(slot, next);
                places.busy.remove(slot);
                // the chunks go to the player first (at the end of this tick), then the attempt
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    loading = false;
                    if (phase != Phase.ENDED) {
                        startRound();
                    }
                }, 2L);
            }

            /** {@link #BLOCKS_PER_TICK} blocks the Bukkit way; true once all are done. */
            private boolean slowStep() {
                int left = BLOCKS_PER_TICK;
                while (left > 0 && cleared < gone.length) {
                    arena.build(gone[cleared], gone[cleared + 1], gone[cleared + 2], Material.AIR, 0);
                    cleared += 5;
                    left--;
                }
                while (left > 0 && placed < coming.length) {
                    place(coming, placed);
                    placed += 5;
                    left--;
                }
                return cleared >= gone.length && placed >= coming.length;
            }
        }, 1L, 1L);
    }

    /** The player waits high over the zone, flying, while it is built again. */
    private void waitAbove(Player player) {
        Location above = arena.at(0.5, WAIT_HEIGHT, 0.5, 0f);
        above.setPitch(60f);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.teleport(above);
        player.setFallDistance(0f);
    }

    /** A title that stays this many ticks: long enough to read what to do. */
    @SuppressWarnings("deprecation") // Player#sendTitle
    private void longTitle(Player player, String title, String subtitle, int stay) {
        Titles.times(plugin.getLogger(), player, 0, stay, 10);
        player.sendTitle(title, subtitle);
    }

    @SuppressWarnings("deprecation") // Material#getMaterial(int): the 1.8 block ids of the copy
    private void place(int[] blocks, int i) {
        Material type = Material.getMaterial(blocks[i + 3]);
        if (type != null) {
            arena.build(blocks[i], blocks[i + 1], blocks[i + 2], type, blocks[i + 4]);
        }
    }

    // --- The attempt ----------------------------------------------------------------------------

    /**
     * The countdown of the first attempt only (the player comes from the lobby); then each attempt
     * starts at once, its map and what to do in the title.
     */
    @Override
    int countdownSeconds() {
        return round <= 1 ? 3 : 0;
    }

    @Override
    Location spawnOf(UUID player) {
        if (loading) {
            return arena.at(0.5, WAIT_HEIGHT, 0.5, 0f);
        }
        PearlMaps.Situation now = situation != null ? situation : pending;
        if (now == null) {
            return arena.at(0.5, 1, 0.5, 0f);
        }
        double[] start = now.start;
        return arena.at(start[0], start[1], start[2], (float) start[3]);
    }

    /** Far under the lowest place where blocks go: the void. */
    @Override
    double fallY() {
        return arena.floorY() + (part == null ? -50 : part.limitY - FALL_UNDER_LIMIT);
    }

    @Override
    ItemStack[] kit() {
        int wool = part == null ? Shapes.RED : part.wool;
        return new ItemStack[] {new ItemStack(Material.ENDER_PEARL, 1), new ItemStack(Material.WOOL, 64, (short) wool)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    /** Waiting while the zone is built again: no tick, no clock. */
    @Override
    boolean paused() {
        return loading || super.paused();
    }

    @Override
    void roundReset() {
        dropPearl();
        thrownTick = -1;
        landed = false;
        placedAny = false;
        standing = 0;
        hitIn = situation != null && situation.push != null ? 20 + random.nextInt(30) : 0;
        if (situation != null && situation.platform != null) {
            int[] at = situation.platform;
            arena.put(at[0], at[1], at[2], Material.WOOL, part.wool);
        }
    }

    /** Where the player is, from the origin of the part. */
    private double[] here(Location at) {
        return arena.offset(at);
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || situation == null || loading) {
            return;
        }
        if (thrownTick < 0 && tickCount % 10 == 0 && !player.getInventory().contains(Material.ENDER_PEARL)) {
            // no pearl thrown yet in this attempt, and none in the inventory (dropped...): one comes back
            player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
        }
        double[] at = here(player.getLocation());
        if (hitIn > 0) {
            if (at[1] < situation.start[1] - 1.5) {
                // off their place before the hit: they are falling already
                hitIn = 0;
            } else if (--hitIn == 0) {
                // the hit that throws them off, as a sprint hit with a knockback stick would
                double[] push = situation.push;
                double side = random.nextDouble() * 0.3 - 0.15;
                boolean save = situation.kind == PearlMaps.Kind.SAVE;
                double strength = save ? 1.1 + random.nextDouble() * 0.25 : 0.75 + random.nextDouble() * 0.2;
                player.setVelocity(new Vector(push[0] * strength - push[1] * side, save ? 0.45 : 0.32,
                        push[1] * strength + push[0] * side));
                player.playEffect(EntityEffect.HURT);
                sound(player, Sound.HURT_FLESH, 1f);
            }
        }
        if (thrownTick >= 0 && !landed && (tickCount - thrownTick > LOST_TICKS || lost(pearl))) {
            missed("Your pearl fell into the void.");
            return;
        }
        boolean tried = landed || (situation.push != null && hitIn == 0 && (thrownTick >= 0 || placedAny));
        if (!tried) {
            return;
        }
        if (!onGround(player)) {
            standing = 0;
            return;
        }
        if (++standing < STAND_TICKS) {
            return;
        }
        if (part.near(situation.target, at[0], at[2], REACH) && at[1] >= situation.targetFloor - 12
                && at[1] <= situation.targetFloor + 8) {
            success(player);
        } else {
            missed("Not on the island you aimed at.");
        }
    }

    @Override
    void launched(Player player, Projectile projectile, ProjectileLaunchEvent event) {
        if (!(projectile instanceof EnderPearl)) {
            return;
        }
        if (hitIn > 0 || loading) {
            event.setCancelled(true);
            player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
            player.sendMessage(ChatColor.GRAY + "Wait for the hit.");
            return;
        }
        thrownTick = tickCount;
        pearl = projectile;
    }

    /**
     * True if this pearl can no longer land anywhere to stand on: gone without landing, or falling
     * already a few blocks under the lowest height where blocks go (nothing to save the player there).
     */
    private boolean lost(Projectile thrown) {
        if (thrown == null) {
            return false;
        }
        if (thrown.isDead()) {
            return true;
        }
        return thrown.getVelocity().getY() < 0 && here(thrown.getLocation())[1] < part.limitY - LOST_BELOW_LIMIT;
    }

    /** A pearl of an attempt that ended (or none thrown in this one): it does not take the player anywhere. */
    @Override
    void pearled(Player player, PlayerTeleportEvent event) {
        if (loading || thrownTick < 0) {
            event.setCancelled(true);
            return;
        }
        landed = true;
        standing = 0;
    }

    /** The pearl of the attempt still in the air goes: it would land during the next one. */
    private void dropPearl() {
        if (pearl != null && !pearl.isDead()) {
            pearl.remove();
        }
        pearl = null;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        if (situation == null || loading || hitIn > 0) {
            return false;
        }
        if (thrownTick < 0) {
            // the pearl first: the blocks save a pearl that lands short, or against a wall
            tell(player, ChatColor.RED + "Pearl first, then the blocks.");
            return false;
        }
        int y = block.getY() - arena.floorY();
        if (belowLimit(y, part.limitY)) {
            tell(player, ChatColor.RED + "You can't place blocks this low!");
            return false;
        }
        int x = block.getX() - arena.centreX();
        int z = block.getZ() - arena.centreZ();
        return Math.abs(x) < 100 && Math.abs(z) < 100 && y <= situation.targetFloor + 15;
    }

    @Override
    void placed(Player player, Block block) {
        placedAny = true;
    }

    /** A message of a refused block, once a second at most. */
    private void tell(Player player, String message) {
        if (now() - toldAt > 1000) {
            toldAt = now();
            player.sendMessage(message);
        }
    }

    private void success(Player player) {
        streak++;
        String what = situation.kind == PearlMaps.Kind.CLUTCH ? "Clutched!" : situation.kind == PearlMaps.Kind.SAVE ? "Saved!"
                : "On the island!";
        player.sendMessage(ChatColor.GREEN + what + " " + ChatColor.GRAY + "Streak: " + ChatColor.WHITE + streak);
        sound(player, Sound.ORB_PICKUP, 1.2f);
        score(streak, true);
        thrownTick = -1;
        pearl = null;
        landed = false;
        placedAny = false;
        standing = Integer.MIN_VALUE;
        again(15L);
    }

    private void missed(String why) {
        String end = streak > 0 ? " The streak of " + streak + " ends." : "";
        streak = 0;
        standing = Integer.MIN_VALUE;
        failed(why + end);
    }

    @Override
    void fell(Player player) {
        if (loading) {
            // fallen from where they wait for the next map
            waitAbove(player);
            return;
        }
        missed(situation != null && situation.push != null ? "You fell: pearl as soon as you are hit." : "You fell.");
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Map: " + ChatColor.AQUA + (part == null ? "-" : part.map));
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
        if (part != null) {
            lines.add(ChatColor.WHITE + "No block under " + ChatColor.RED + "y " + (arena.floorY() + part.limitY));
        }
    }
}
