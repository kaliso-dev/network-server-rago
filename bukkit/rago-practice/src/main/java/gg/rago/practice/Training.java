package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * A solo training: one player, attempts one after the other (each on the arena as built), a
 * personal best and the server's best for each of its variants, kept in records.yml. Two items
 * at the end of the hotbar: the variant (when there are several) and a restart. No damage, no
 * stats: the player leaves with /spawn.
 */
abstract class Training extends PracticeGame {

    /** One way to play the training, with its own record board. */
    static final class Variant {
        final String id;
        final String label;
        /** True when the score is a time in milliseconds (lower is better); else a count. */
        final boolean timed;
        /** The unit of a count ("clutches", "hits"...). */
        final String unit;

        Variant(String id, String label, boolean timed, String unit) {
            this.id = id;
            this.label = label;
            this.timed = timed;
            this.unit = unit;
        }
    }

    static final String MODE_ITEM = ChatColor.GREEN + "Change mode" + ChatColor.GRAY + " (right click)";
    static final String RESTART_ITEM = ChatColor.YELLOW + "Restart" + ChatColor.GRAY + " (right click)";
    static final int MODE_SLOT = 7;
    static final int RESTART_SLOT = 8;

    final List<Variant> variants = new ArrayList<>();
    int variant;
    final UUID me;
    private final String myName;
    /** The last score of the player, for the sidebar. */
    String last = "";

    Training(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, Variant... variants) {
        super(host, mode, arena, teams);
        for (Variant v : variants) {
            this.variants.add(v);
            host.records().book().define(mode.id() + "." + v.id, v.timed);
        }
        this.me = teams.get(0).get(0).getUniqueId();
        this.myName = teams.get(0).get(0).getName();
    }

    Variant current() {
        return variants.get(variant);
    }

    String board() {
        return mode.id() + "." + current().id;
    }

    Player player() {
        Player player = Bukkit.getPlayer(me);
        return player != null && host.gameOf(me) == this ? player : null;
    }

    @Override
    int countdownSeconds() {
        return 0;
    }

    @Override
    boolean counts() {
        return false;
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        return false;
    }

    @Override
    void blasted(Player player, Player thrower, double damage) {
        // a training's fireballs push, they do not hurt
    }

    /** The kit of the training, without its two items. */
    abstract ItemStack[] kit();

    @Override
    ItemStack[] defaultKit(Player player) {
        return kit();
    }

    @Override
    void afterKit(Player player) {
        if (variants.size() > 1) {
            player.getInventory().setItem(MODE_SLOT, named(Material.NETHER_STAR, 1, 0, MODE_ITEM,
                    ChatColor.GRAY + "Now: " + ChatColor.WHITE + current().label));
        }
        player.getInventory().setItem(RESTART_SLOT, named(Material.BLAZE_ROD, 1, 0, RESTART_ITEM));
    }

    @Override
    void interact(Player player, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            use(player, event);
            return;
        }
        ItemStack item = event.getItem();
        if (isNamed(item, Material.NETHER_STAR, MODE_ITEM)) {
            event.setCancelled(true);
            variant = (variant + 1) % variants.size();
            player.sendMessage(ChatColor.GREEN + "Mode: " + ChatColor.WHITE + current().label + ChatColor.GRAY + " - " + describe());
            sound(player, Sound.CLICK, 1f);
            startRound();
            return;
        }
        if (isNamed(item, Material.BLAZE_ROD, RESTART_ITEM)) {
            event.setCancelled(true);
            sound(player, Sound.CLICK, 1.4f);
            startRound();
            return;
        }
        use(player, event);
    }

    /** A click that is not one of the two items (a fireball, a pearl...). */
    void use(Player player, PlayerInteractEvent event) {
    }

    /** What the current variant asks, in one sentence. */
    abstract String describe();

    @Override
    void intro(Player player) {
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + ChatColor.stripColor(mode.name()) + ChatColor.GRAY
                + " - " + ChatColor.WHITE + current().label + ChatColor.GRAY + ": " + describe());
        player.sendMessage(ChatColor.GRAY + "The star changes the mode, the blaze rod restarts, /spawn takes you back to the lobby.");
    }

    /** The text of a score: "4.512 s" or "12 clutches". */
    String format(long value) {
        Variant v = current();
        return v.timed ? time(value) + " s" : value + (v.unit.isEmpty() ? "" : " " + v.unit);
    }

    /** A new score of this attempt: the player hears whether it is a best. */
    void score(long value) {
        score(value, false);
    }

    /** A new score; quiet: a message only for a new best (a streak that goes on). */
    void score(long value, boolean quiet) {
        Player player = player();
        RecordBook.Result result = host.records().submit(board(), me, myName, value);
        last = format(value);
        if (player == null) {
            return;
        }
        if (result.boardBest && result.previous != null) {
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "New server record! " + ChatColor.WHITE + format(value));
            sound(player, Sound.LEVEL_UP, 1.2f);
        } else if (result.personalBest && result.previous != null) {
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "New personal best! " + ChatColor.WHITE + format(value)
                    + ChatColor.GRAY + " (before: " + format(result.previous) + ")");
            sound(player, Sound.LEVEL_UP, 1f);
        } else if (quiet) {
            return;
        } else if (result.previous == null) {
            player.sendMessage(ChatColor.GREEN + "First score: " + ChatColor.WHITE + format(value));
            sound(player, Sound.ORB_PICKUP, 1f);
        } else {
            player.sendMessage(ChatColor.GREEN + format(value) + ChatColor.GRAY + " (your best: " + format(result.previous) + ")");
            sound(player, Sound.ORB_PICKUP, 1f);
        }
    }

    @Override
    List<String> sidebar(UUID viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(Duel.cut(mode.name()));
        lines.add(Duel.cut(ChatColor.WHITE + "Mode: " + ChatColor.GREEN + current().label));
        lines.add("");
        trainingLines(lines);
        lines.add("");
        Long best = host.records().book().best(board(), me);
        lines.add(ChatColor.WHITE + "Your best: " + ChatColor.GREEN + (best == null ? ChatColor.GRAY + "none" : format(best)));
        if (!last.isEmpty()) {
            lines.add(ChatColor.WHITE + "Last: " + ChatColor.YELLOW + last);
        }
        RecordBook.Entry top = host.records().book().top(board());
        if (top != null) {
            lines.add(Duel.cut(ChatColor.WHITE + "Top: " + ChatColor.GOLD + top.name + ChatColor.GRAY + " " + format(top.value)));
            int rank = host.records().book().rank(board(), me);
            if (rank > 0) {
                lines.add(ChatColor.WHITE + "Your rank: " + ChatColor.AQUA + "#" + rank);
            }
        }
        return lines;
    }

    /** The lines of the attempt in the sidebar (the time so far, the streak...). */
    abstract void trainingLines(List<String> lines);

    /** The player goes back to the start, the arena as built, in a moment. */
    void again(long ticks) {
        final int roundNow = round;
        later(ticks, () -> {
            if (round == roundNow) {
                startRound();
            }
        });
    }

    @Override
    void fell(Player player) {
        failed("You fell.");
    }

    /** The attempt failed: the player starts again at once. */
    void failed(String why) {
        Player player = player();
        if (player != null) {
            player.sendMessage(ChatColor.RED + why);
            sound(player, Sound.VILLAGER_NO, 1f);
        }
        startRound();
    }

    /** The 64 blocks of a stack that never runs out, in a training. */
    static ItemStack stack(Material type) {
        return new ItemStack(type, 64);
    }

    static ItemStack stack(Material type, int data) {
        return new ItemStack(type, 64, (short) data);
    }

    @Override
    boolean refills() {
        return true;
    }

    /** The time now, in milliseconds, for the timers. */
    static long now() {
        return System.nanoTime() / 1000000L;
    }

    /** The sidebar again, five times a second: a timer that runs shows it. Called from tick(). */
    void refresh() {
        if (tickCount % 4 == 0) {
            sidebars();
        }
    }

    /**
     * True if this block is in the box around (cx, cz) of the arena: this far on x and z, from
     * below to above the floor.
     */
    boolean near(Block block, int cx, int cz, int radius, int below, int above) {
        int dx = block.getX() - arena.centreX() - cx;
        int dz = block.getZ() - arena.centreZ() - cz;
        int dy = block.getY() - arena.floorY();
        return block.getWorld().equals(arena.world()) && Math.abs(dx) <= radius && Math.abs(dz) <= radius
                && dy >= -below && dy <= above;
    }
}
