package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

/**
 * The Fireball Fight of FUN PRACTICE (plan 6.1): three players at once on a big round platform over
 * the void (the owner's ask of 22/11), each for themselves; knock the others off with fireballs
 * that push harder than in Bed Wars, and get out of a bad spot with the double jump (jump twice in
 * the air: the leap of the lobby's leap feather, once every few seconds). Wool that never runs out,
 * each block placed gone after a few seconds. No hits, no damage: only the push of the fireballs.
 * The last one on the platform wins the round. Not the Fireball fight of the Duels ({@link
 * FireballFight}, with beds). Its settings: fireball-knockback in config.yml.
 */
final class FireballKnockback extends Duel {

    /** The settings of the mode (fireball-knockback in config.yml). */
    static final class Settings {
        /** Radius of the plugin's platform, in blocks. */
        final int radius;
        /** How many times the push of a Bed Wars fireball the others get, across and up. */
        final double across;
        final double up;
        /** Fireballs of each round; 0: as many as the player likes (one every half second). */
        final int fireballs;
        /** Seconds between two double jumps of a player. */
        final int gustSeconds;
        /** Seconds a block placed stays. */
        final int blockSeconds;

        Settings(int radius, double across, double up, int fireballs, int gustSeconds, int blockSeconds) {
            this.radius = radius;
            this.across = across;
            this.up = up;
            this.fireballs = fireballs;
            this.gustSeconds = gustSeconds;
            this.blockSeconds = blockSeconds;
        }

        /** What is wrong with these values, or null. */
        static String check(int radius, double across, double up, int fireballs, int gustSeconds, int blockSeconds) {
            if (radius < 5 || radius > 30) {
                return "rayon must be from 5 to 30";
            }
            if (across < 0.5 || across > 5 || up < 0.5 || up > 5) {
                return "recul-horizontal and recul-vertical must be from 0.5 to 5";
            }
            if (fireballs < 0 || fireballs > 64) {
                return "fireballs must be from 0 (no limit) to 64";
            }
            if (gustSeconds < 0 || gustSeconds > 60) {
                return "souffle-secondes must be from 0 to 60";
            }
            if (blockSeconds < 1 || blockSeconds > 30) {
                return "blocs-secondes must be from 1 to 30";
            }
            return null;
        }
    }

    /** Below the platform by this much, a player has fallen off (as in the sumo). */
    static final double FALL_DEPTH = SumoMatch.FALL_DEPTH;
    /** The names of the items, in the kit as the player arranged it. */
    static final String FIREBALL_NAME = ChatColor.GOLD + "Fireball";
    static final String GUST_NAME = ChatColor.AQUA + "Wind gust";
    /** The three sides: their colour in the chat, their name and the colour of their wool. */
    static final ChatColor[] SIDE_CHAT = {ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN};
    static final String[] SIDE_NAMES = {"Red", "Blue", "Green"};
    static final int[] SIDE_WOOL = {Shapes.RED, Shapes.BLUE, Shapes.LIME};
    /** How often the cracks of the blocks placed are brought up to date. */
    private static final int CRACK_TICKS = 5;

    /** A block placed: where, when (the tick of the round), the number of its cracks and their stage now. */
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

    private final Settings settings;
    /** The rounds won by each side. */
    private final int[] scores;
    private final Map<UUID, Long> lastFireball = new HashMap<>();
    /** When each player may double jump again (System.currentTimeMillis). */
    private final Map<UUID, Long> gustReady = new HashMap<>();
    /** The blocks placed, oldest first, and the last placing of each block (an older one there no longer counts). */
    private final Deque<Placed> decaying = new ArrayDeque<>();
    private final Map<Block, Placed> latest = new HashMap<>();

    /** Teams: a player a side ({@link RedRover#alone}), three of them. */
    FireballKnockback(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target, Settings settings) {
        super(host, mode, arena, teams, target);
        this.settings = settings;
        this.scores = new int[sides.size()];
    }

    @Override
    String title() {
        return "Fireball Fight";
    }

    /** The colour of a side in the chat. */
    static ChatColor chatOf(int side) {
        return SIDE_CHAT[Math.max(0, Math.min(SIDE_CHAT.length - 1, side))];
    }

    /** The name of a side ("Red"). */
    static String nameOfSide(int side) {
        return SIDE_NAMES[Math.max(0, Math.min(SIDE_NAMES.length - 1, side))];
    }

    @Override
    ChatColor sideColour(int side) {
        return chatOf(side);
    }

    @Override
    void intro(Player player) {
        int side = Math.max(0, sideOf(player.getUniqueId()));
        // the name of each player in the colour of their side, above their head and in the tab list
        plugin.lobby().setGameNameTag(player, chatOf(side).toString());
        List<String> others = new ArrayList<>();
        for (int s = 0; s < sides.size(); s++) {
            if (s != side) {
                others.add(chatOf(s) + namesOf(s) + ChatColor.GREEN);
            }
        }
        player.sendMessage(ChatColor.GREEN + title() + " against " + joined(others) + ": first to " + goal() + " wins.");
        player.sendMessage(ChatColor.GRAY + "Knock them off with your fireballs (right click); jump twice to double jump."
                + " Your wool never runs out, but each block goes after " + settings.blockSeconds
                + (settings.blockSeconds == 1 ? " second" : " seconds") + ". No hits, no damage: the last one on the platform"
                + " wins the round.");
    }

    /** "A", "A and B", "A, B and C". */
    static String joined(List<String> names) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                text.append(i == names.size() - 1 ? " and " : ", ");
            }
            text.append(names.get(i));
        }
        return text.toString();
    }

    @Override
    boolean colouredBlocks() {
        return false;
    }

    @Override
    void build() {
        if (arena.map() != null) {
            return;
        }
        Shapes.disc(arena, 0, 0, 0, settings.radius, Material.QUARTZ_BLOCK, 0);
    }

    @Override
    void roundReset() {
        super.roundReset();
        lastFireball.clear();
        gustReady.clear();
        // the arena is as it was: the blocks placed went with it, their cracks too
        for (Placed placed : latest.values()) {
            BlockCracks.show(online(), placed.id, placed.block, -1, plugin.getLogger());
        }
        decaying.clear();
        latest.clear();
    }

    /** How far from the middle each side starts on the plugin's platform. */
    static double startDistance(int radius) {
        return Math.max(3, radius / 2);
    }

    /**
     * Where this side of so many starts on the plugin's platform, this far from the middle, facing
     * it: x and z from the middle, and the yaw. The first side west of the middle, the others
     * around the platform at equal angles (with two sides, the second east of it).
     */
    static double[] start(int side, int count, double distance) {
        double angle = Math.PI + 2 * Math.PI * side / Math.max(1, count);
        double x = Math.cos(angle) * distance;
        double z = Math.sin(angle) * distance;
        return new double[] {x, z, Math.toDegrees(Math.atan2(x, -z))};
    }

    @Override
    Location spawnOf(UUID player) {
        int side = Math.max(0, sideOf(player));
        if (arena.map() != null && arena.map().starts.length >= sides.size()) {
            Location start = arena.mapStart(side);
            if (start != null) {
                return start;
            }
        }
        double[] start = start(side, sides.size(), startDistance(settings.radius));
        return arena.at(0.5 + start[0], 1, 0.5 + start[1], (float) start[2]);
    }

    /** At the start, the double jump: the player may fly, and a flight becomes the jump ({@link #toggledFlight}). */
    @Override
    void placeAtStart(Player player) {
        super.placeAtStart(player);
        player.setAllowFlight(true);
    }

    @Override
    double fallY() {
        return arena.floorY() - FALL_DEPTH;
    }

    @Override
    Location viewPoint() {
        Location view = arena.at(0.5, 10, -(settings.radius + 8) + 0.5, 0f);
        view.setPitch(35f);
        return view;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.FIREBALL, fireballsOf(settings.fireballs)), new ItemStack(Material.WOOL, 64)};
    }

    /** The fireballs of a round in the kit: the limit, or a full stack when there is none. */
    static int fireballsOf(int limit) {
        return limit <= 0 ? 64 : limit;
    }

    /**
     * The fireballs named, as many as the settings say; the wool in the colour of the player's side
     * (a feather of an older kit stays the wind gust, as the double jump).
     */
    @Override
    void afterKit(Player player) {
        ItemStack[] items = player.getInventory().getContents();
        for (ItemStack item : items) {
            if (item == null) {
                continue;
            }
            if (item.getType() == Material.FIREBALL) {
                item.setAmount(fireballsOf(settings.fireballs));
                rename(item, FIREBALL_NAME);
            } else if (item.getType() == Material.FEATHER) {
                rename(item, GUST_NAME);
            }
        }
        player.getInventory().setContents(items);
        recolour(player.getInventory(), SIDE_WOOL[Math.max(0, Math.min(SIDE_WOOL.length - 1, sideOf(player.getUniqueId())))]);
        player.setLevel(0);
        player.setExp(1f);
    }

    private static void rename(ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
    }

    /** No hits (the owner's ask of 22/11) and nothing else hurts (no fall damage): only the fireballs push. */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        return false;
    }

    /** The fireballs only push (their hit still counts for the point): no damage. */
    @Override
    void blasted(Player player, Player thrower, double damage) {
    }

    /** The others are pushed harder than in Bed Wars; the thrower's own fireball jump stays as it is. */
    @Override
    double[] fireballPush(Player player, Player thrower, double[] push) {
        if (thrower == null || thrower.equals(player)) {
            return push;
        }
        return scaled(push, settings.across, settings.up);
    }

    /** The push, this many times stronger across (x and z) and up (y). */
    static double[] scaled(double[] push, double across, double up) {
        return new double[] {push[0] * across, push[1] * up, push[2] * across};
    }

    // --- The blocks -----------------------------------------------------------------------------

    /** The wool never runs out: the stack in the hand is full again after each block. */
    @Override
    boolean refills() {
        return true;
    }

    /** On the platform and around it, not too far up nor down. */
    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return arena.contains(block, settings.radius + 8, 12, 14);
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

    /** The ticks a block placed stays. */
    static int lifeTicks(int blockSeconds) {
        return blockSeconds * 20;
    }

    /** The blocks placed whose time is up go; the others crack more and more. */
    private void decay() {
        int life = lifeTicks(settings.blockSeconds);
        while (!decaying.isEmpty() && tickCount - decaying.peekFirst().tick >= life) {
            Placed placed = decaying.pollFirst();
            if (latest.get(placed.block) != placed) {
                continue;
            }
            latest.remove(placed.block);
            BlockCracks.show(online(), placed.id, placed.block, -1, plugin.getLogger());
            if (arena.isPlaced(placed.block)) {
                arena.restore(placed.block);
            }
        }
        if (tickCount % CRACK_TICKS != 0) {
            return;
        }
        List<Player> viewers = online();
        Iterator<Placed> all = decaying.iterator();
        while (all.hasNext()) {
            Placed placed = all.next();
            if (latest.get(placed.block) != placed) {
                continue;
            }
            if (!arena.isPlaced(placed.block) || placed.block.getType() == Material.AIR) {
                // blown up or broken before its time
                BlockCracks.show(viewers, placed.id, placed.block, -1, plugin.getLogger());
                latest.remove(placed.block);
                all.remove();
                continue;
            }
            int stage = BlockCracks.stage(tickCount - placed.tick, life);
            if (stage != placed.stage) {
                placed.stage = stage;
                BlockCracks.show(viewers, placed.id, placed.block, stage, plugin.getLogger());
            }
        }
    }

    // --- The double jump ------------------------------------------------------------------------

    @Override
    boolean ticks() {
        return true;
    }

    /** The bar of experience shows when the double jump is ready again; the blocks placed decay. */
    @Override
    void tick() {
        long now = System.currentTimeMillis();
        for (Player player : online()) {
            Long ready = gustReady.get(player.getUniqueId());
            player.setExp(gustBar(ready == null ? 0 : ready - now, settings.gustSeconds));
        }
        decay();
    }

    /** The bar of experience (0 to 1) when the double jump is ready again in this many milliseconds. */
    static float gustBar(long leftMs, int gustSeconds) {
        if (leftMs <= 0 || gustSeconds <= 0) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, 1f - leftMs / (gustSeconds * 1000f)));
    }

    /** Jump pressed twice in the air: no flight, the double jump (when it is ready, in the fight). */
    @Override
    void toggledFlight(Player player, PlayerToggleFlightEvent event) {
        if (!event.isFlying()) {
            return;
        }
        event.setCancelled(true);
        player.setFlying(false);
        if (!out.contains(player.getUniqueId())) {
            gust(player);
        }
    }

    @Override
    void interact(Player player, PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        if (item.getType() == Material.FIREBALL) {
            event.setCancelled(true);
            throwFireball(player);
        } else if (item.getType() == Material.FEATHER) {
            event.setCancelled(true);
            gust(player);
        }
    }

    private void throwFireball(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastFireball.get(player.getUniqueId());
        if (phase != Phase.PLAYING || (last != null && now - last < Fireballs.COOLDOWN_MS)) {
            return;
        }
        lastFireball.put(player.getUniqueId(), now);
        int before = player.getItemInHand().getAmount();
        Fireball fireball = Fireballs.launch(player);
        if (fireball != null && settings.fireballs <= 0) {
            // no limit: the stack stays full
            ItemStack hand = new ItemStack(Material.FIREBALL, Math.max(1, before));
            rename(hand, FIREBALL_NAME);
            player.setItemInHand(hand);
        }
    }

    /** The double jump: the leap of the lobby's leap feather, once every few seconds. */
    private void gust(Player player) {
        if (phase != Phase.PLAYING) {
            return;
        }
        long now = System.currentTimeMillis();
        Long ready = gustReady.get(player.getUniqueId());
        if (ready != null && now < ready) {
            long left = (ready - now + 999) / 1000;
            player.sendMessage(ChatColor.GRAY + "Double jump ready in " + left + (left == 1 ? " second." : " seconds."));
            return;
        }
        gustReady.put(player.getUniqueId(), now + settings.gustSeconds * 1000L);
        double[] leap = leap(player.getEyeLocation().getDirection());
        player.setVelocity(new Vector(leap[0], leap[1], leap[2]));
        player.setFallDistance(0f);
        player.getWorld().playSound(player.getLocation(), Sound.BAT_TAKEOFF, 1f, 1f);
        player.getWorld().spigot().playEffect(player.getLocation(), Effect.CLOUD, 0, 0, 0.3f, 0.1f, 0.3f, 0.05f, 15, 48);
    }

    /**
     * The velocity of the double jump for a player looking this way: that of the lobby's leap
     * feather (LobbyGadgets#leap), 1.6 forward across, and up at least 0.7, more when looking up.
     */
    static double[] leap(Vector look) {
        double x = look.getX();
        double z = look.getZ();
        double across = Math.sqrt(x * x + z * z);
        if (across > 1e-6) {
            x = x / across * 1.6;
            z = z / across * 1.6;
        } else {
            x = 0;
            z = 0;
        }
        return new double[] {x, Math.max(0.7, look.getY() * 1.4 + 0.5), z};
    }

    // --- The rounds, each for themselves --------------------------------------------------------

    @Override
    void deathMessage(Player player, UUID by, boolean fell) {
        String victim = chatOf(sideOf(player.getUniqueId())) + player.getDisplayName() + ChatColor.GRAY;
        String text;
        if (by == null) {
            text = victim + (fell ? " fell into the void." : " died.");
        } else {
            text = victim + (fell ? " was knocked into the void by " : " was killed by ") + chatOf(sideOf(by)) + nameOf(by)
                    + ChatColor.GRAY + ".";
        }
        broadcast(text, null);
    }

    /** The player is out of this round (the kill to the last one who pushed them): once one side is left, its point. */
    @Override
    void out(Player player, boolean fell) {
        UUID id = player.getUniqueId();
        int side = sideOf(id);
        if (side < 0 || phase != Phase.PLAYING || out.contains(id)) {
            return;
        }
        UUID by = killerOf(id);
        deathMessage(player, by, fell);
        if (counts()) {
            // by null: a death nobody gets the kill of (the void, a fall), a death all the same
            plugin.lobby().recordKill(mode.id(), by, id);
        }
        out.add(id);
        if (sidesLeft().size() > 1) {
            spectate(player);
            return;
        }
        roundOverIfOneLeft();
    }

    /** The sides still in the round. */
    private List<Integer> sidesLeft() {
        List<Integer> left = new ArrayList<>();
        for (int side = 0; side < sides.size(); side++) {
            if (alive(side)) {
                left.add(side);
            }
        }
        return left;
    }

    /** One side left in the round: its point; none (all gone at once): the round again. */
    private void roundOverIfOneLeft() {
        List<Integer> left = sidesLeft();
        if (left.size() == 1) {
            point(left.get(0));
        } else if (left.isEmpty() && phase == Phase.PLAYING && !online().isEmpty()) {
            startRound();
        }
    }

    @Override
    void point(int side) {
        if (phase != Phase.PLAYING) {
            return;
        }
        scores[side]++;
        replayChat(chatOf(side) + "Point for " + nameOfSide(side) + ChatColor.GRAY + " " + scoreLine(scores, -1));
        for (Player player : online()) {
            int mine = sideOf(player.getUniqueId());
            player.sendMessage((mine == side ? ChatColor.GREEN + "Point for you" : chatOf(side) + "Point for " + nameOf(sides.get(side).get(0)))
                    + ChatColor.GRAY + " " + scoreLine(scores, mine));
            pointSound(player, mine == side);
        }
        if (scores[side] >= target) {
            finish(side, null);
        } else {
            startRound();
        }
    }

    /** The rounds won, "(1 - 0 - 0)": this side's first (-1: in the order of the sides). */
    static String scoreLine(int[] scores, int first) {
        StringBuilder text = new StringBuilder("(");
        if (first >= 0 && first < scores.length) {
            text.append(scores[first]);
        }
        for (int side = 0; side < scores.length; side++) {
            if (side == first) {
                continue;
            }
            if (text.length() > 1) {
                text.append(" - ");
            }
            text.append(scores[side]);
        }
        return text.append(')').toString();
    }

    /** A player who left: if they were the last but one in the round, the last one's point. */
    @Override
    void left(Player player) {
        if (phase == Phase.PLAYING) {
            roundOverIfOneLeft();
        }
    }

    @Override
    String result(int winner) {
        return scoreLine(scores, winner);
    }

    @Override
    List<String> sidebar(UUID viewer) {
        int mine = sideOf(viewer);
        List<String> lines = new ArrayList<>();
        lines.add(cut(mode.name()));
        lines.add("");
        for (int side = 0; side < sides.size(); side++) {
            String who = sides.get(side).isEmpty() ? nameOfSide(side) : nameOf(sides.get(side).get(0));
            String state = out.containsAll(sides.get(side)) ? ChatColor.GRAY + " ✘" : "";
            lines.add(cut(chatOf(side) + who + ": " + ChatColor.WHITE + scores[side] + state + (side == mine ? ChatColor.GRAY + " (you)" : "")));
        }
        lines.add(ChatColor.GRAY + "First to " + goal());
        lines.add("");
        lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + clock(seconds) + ChatColor.GRAY + "  Round " + round);
        lines.add("");
        Player me = Bukkit.getPlayer(viewer);
        lines.add(ChatColor.WHITE + "Your ping: " + Ping.text(me));
        return lines;
    }
}
