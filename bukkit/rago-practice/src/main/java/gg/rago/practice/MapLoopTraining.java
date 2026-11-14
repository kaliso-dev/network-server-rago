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
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * A training in a loop on the owner's Bed Wars maps (the pearl practice, the fireball practice):
 * each attempt on the next map of the loop, at another place, in another direction, the kind of
 * situation its level asks for (see {@link MapParts}). The next one comes by itself: nothing to
 * choose. Each good attempt adds to the streak, a miss ends it.
 *
 * <p>The zone holds one part of a map at a time: when the map changes, its blocks go and those of
 * the next part come, written straight into the chunks ({@link FastBlocks}, some 10 ms), the
 * Bukkit way a little at a time when the server does not allow it, the player waiting above.
 */
abstract class MapLoopTraining extends Training {

    /** Where a training of the loop plays, for all its zones: the parts of the maps, and the part each zone holds now. */
    static final class Places {
        final List<MapParts.Part> parts;
        /** By the slot of the zone: the part built there. Main thread only. */
        private final Map<Integer, MapParts.Part> built = new HashMap<>();
        /** The zones being built again. */
        private final Set<Integer> busy = new HashSet<>();
        private FastBlocks fast;
        /** True once the chunks could not be written straight: the Bukkit way from then on. */
        private boolean noFast;

        /** The parts with a situation of the pearl practice, or of the fireball practice; the plugin's own islands without any. */
        Places(List<MapParts.Part> parts, boolean fireball) {
            List<MapParts.Part> kept = new ArrayList<>();
            for (MapParts.Part part : parts) {
                if (part.hasAny(fireball)) {
                    kept.add(part);
                }
            }
            if (kept.isEmpty()) {
                kept.add(MapParts.plain());
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
            logger.log(Level.WARNING, "A practice could not write a map into its zone straight: the Bukkit way from now on"
                    + " (a short lag at each map)", e);
            fast = null;
            noFast = true;
        }
    }

    /** Blocks the zone changes each tick while it is built again the Bukkit way (some 30 microseconds each). */
    static final int BLOCKS_PER_TICK = 1500;
    /** The time of a tick the zone takes at most while its chunks are written straight. */
    private static final long BUILD_NANOS_PER_TICK = 15_000_000L;
    /** How high over the floor of the zone the player waits while it is built again. */
    static final int WAIT_HEIGHT = 90;
    /** A player this far under the lowest place where blocks go has fallen into the void. */
    static final int FALL_UNDER_LIMIT = 12;

    final Places places;
    final Random random = new Random();
    /** The maps of the loop, in an order of this game's own. */
    private final Loop<MapParts.Part> order;
    int turn;
    MapParts.Part part;
    MapParts.Situation situation;
    /** The situation of the next attempt, chosen before its zone is ready. */
    private MapParts.Situation pending;
    private MapParts.Part pendingPart;
    boolean loading;
    private BukkitTask building;
    int streak;

    MapLoopTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, Places places,
            Variant... variants) {
        super(host, mode, arena, teams, variants);
        this.places = places;
        this.order = new Loop<>(places.parts, random);
    }

    /** The kind of situation this level plays for the attempt after so many. */
    abstract MapParts.Kind kindOf(String variantId, int turn);

    /** How many kinds this level plays in turn. */
    abstract int kindsOf(String variantId);

    /** A fall or a miss gives the next attempt by itself: no blaze rod. */
    @Override
    boolean restartUsed() {
        return false;
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

    /**
     * The maps of a loop, each pass through all of them in a new order: the maps do not come back
     * with the same kinds of situations each time (three maps and three kinds of a level would
     * always pair up the same way).
     */
    static final class Loop<T> {
        private final List<T> order;
        private final Random random;
        private int next;

        Loop(List<T> items, Random random) {
            this.order = new ArrayList<>(items);
            this.random = random;
            Collections.shuffle(order, random);
        }

        /**
         * The next one that fits: further in this pass, else in the next pass (all of them, in a
         * new order that does not start with the last one played). Null when none fits.
         */
        T next(Predicate<T> fits, T last) {
            for (int tries = 0; tries < 2 * order.size(); tries++) {
                if (next >= order.size()) {
                    next = 0;
                    Collections.shuffle(order, random);
                    if (order.size() > 1 && order.get(0) == last) {
                        Collections.swap(order, 0, 1 + random.nextInt(order.size() - 1));
                    }
                }
                T each = order.get(next++);
                if (fits.test(each)) {
                    return each;
                }
            }
            return null;
        }

        T first() {
            return order.get(0);
        }
    }

    /**
     * The next situation: the next map of the loop that has one of the kind, not the last one
     * played; when none has it, the next kind of the level (the turns going on from it).
     */
    private void choose() {
        int kinds = Math.max(1, kindsOf(current().id));
        for (int shift = 0; shift < kinds; shift++) {
            MapParts.Kind kind = kindOf(current().id, turn + shift);
            if (pendingPart != null && !pendingPart.of(kind).isEmpty()) {
                // the map chosen already (being built): a situation of the level now chosen on it
                List<MapParts.Situation> found = pendingPart.of(kind);
                pending = found.get(random.nextInt(found.size()));
                turn += shift;
                return;
            }
            MapParts.Part next = order.next(each -> !each.of(kind).isEmpty(), part);
            if (next != null) {
                List<MapParts.Situation> found = next.of(kind);
                if (found.size() > 1) {
                    found.remove(situation);
                }
                pendingPart = next;
                pending = found.get(random.nextInt(found.size()));
                turn += shift;
                return;
            }
        }
        // no map has a kind of the level: any situation of this training on the next map
        boolean fireball = kindOf(current().id, turn).fireball;
        MapParts.Part next = order.next(each -> each.hasAny(fireball), part);
        pendingPart = next != null ? next : order.first();
        for (MapParts.Situation each : pendingPart.situations) {
            if (each.kind.fireball == fireball) {
                pending = each;
                return;
            }
        }
        pending = pendingPart.situations.get(0);
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

    /** The zone is about to change: what the attempt left in it goes first (a pearl in the air, a bot...). */
    void leaving() {
    }

    /**
     * The zone gets the blocks of this part: those of the part it holds go first. Straight into the
     * chunks when the server allows it ({@link FastBlocks}): some 10 ms, in one tick; else the Bukkit
     * way, a little at a time, the player waiting above. Even when the game ends meanwhile, the
     * blocks keep coming, so that the zone holds a whole part for the next game.
     */
    private void rebuild(MapParts.Part next) {
        final int slot = arena.slot();
        final MapParts.Part old = places.built.remove(slot);
        places.busy.add(slot);
        loading = true;
        // the blocks of the last attempt (the player's, the bridges) go before those of the map
        arena.restore();
        leaving();
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
    void waitAbove(Player player) {
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
    void longTitle(Player player, String title, String subtitle, int stay) {
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
        MapParts.Situation now = situation != null ? situation : pending;
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
    boolean ticks() {
        return true;
    }

    /** Waiting while the zone is built again: no tick, no clock. */
    @Override
    boolean paused() {
        return loading || super.paused();
    }

    /** A good attempt: one more in the streak, and the next one in a moment. */
    void succeeded(Player player, String what) {
        streak++;
        player.sendMessage(ChatColor.GREEN + what + " " + ChatColor.GRAY + "Streak: " + ChatColor.WHITE + streak);
        sound(player, org.bukkit.Sound.ORB_PICKUP, 1.2f);
        score(streak, true);
        again(15L);
    }

    /** A miss: the streak ends, the next attempt at once. */
    void missed(String why) {
        String end = streak > 0 ? " The streak of " + streak + " ends." : "";
        streak = 0;
        failed(why + end);
    }

    @Override
    void fell(Player player) {
        if (loading) {
            // fallen from where they wait for the next map
            waitAbove(player);
            return;
        }
        missed(fellWhy());
    }

    /** What a fall into the void says. */
    String fellWhy() {
        return "You fell.";
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Map: " + ChatColor.AQUA + (part == null ? "-" : part.map));
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
    }
}
