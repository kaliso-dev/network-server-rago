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
import org.bukkit.inventory.PlayerInventory;

/**
 * A solo training: one player, attempts one after the other (each on the arena as built), a
 * personal best and the server's best for each of its variants, kept in records.yml. At the end
 * of the hotbar, a bar of controls: a red dye lowers and a green dye raises, the mode (when there
 * are several) and each setting of the training, the number on the dyes giving where it is now;
 * a blaze rod restarts. No damage, no stats: the player leaves with /spawn.
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

    /**
     * A setting of the training the player changes from the hotbar: a whole number from min to max,
     * or on and off (one item flips it).
     */
    static final class Setting {
        final String label;
        final int min;
        final int max;
        final boolean toggle;
        int value;

        Setting(String label, int min, int max, int value, boolean toggle) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.value = value;
            this.toggle = toggle;
        }

        boolean on() {
            return value != 0;
        }

        /** The value as the player reads it: "3/5" with a bar, or On / Off. */
        String text() {
            if (toggle) {
                return on() ? ChatColor.GREEN + "On" : ChatColor.RED + "Off";
            }
            StringBuilder bar = new StringBuilder();
            for (int i = min; i <= max && max - min <= 10; i++) {
                bar.append(i <= value ? ChatColor.GREEN : ChatColor.DARK_GRAY).append('|');
            }
            return bar + " " + ChatColor.WHITE + value + ChatColor.GRAY + "/" + max;
        }

        /** One step lower (-1) or higher (+1), within min and max; true if it changed. */
        boolean step(int by) {
            int next = toggle ? (on() ? 0 : 1) : Math.max(min, Math.min(max, value + by));
            if (next == value) {
                return false;
            }
            value = next;
            return true;
        }
    }

    static final String RESTART_ITEM = ChatColor.YELLOW + "Restart" + ChatColor.GRAY + " (right click)";
    static final String PREVIOUS_MODE = ChatColor.RED + "\u25C0 Previous mode";
    static final String NEXT_MODE = ChatColor.GREEN + "Next mode \u25B6";
    static final String LOWER = ChatColor.RED + "- ";
    static final String RAISE = ChatColor.GREEN + "+ ";
    static final String SWITCH = ChatColor.YELLOW + "";
    static final int RESTART_SLOT = 8;
    /** The dyes: red lowers, green raises, gray is a setting that is off. */
    static final int RED_DYE = 1;
    static final int GREEN_DYE = 10;
    static final int GRAY_DYE = 8;

    final List<Variant> variants = new ArrayList<>();
    int variant;
    /** The settings of the training, in the hotbar after the mode. */
    final List<Setting> settings = new ArrayList<>();
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
        // [PLAY] of the challenge of the day: its variant first
        String wanted = host.challenges() == null ? null : host.challenges().takeVariant(me, mode.id());
        for (int i = 0; wanted != null && i < this.variants.size(); i++) {
            if (this.variants.get(i).id.equals(wanted)) {
                this.variant = i;
            }
        }
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

    /** A setting of this training, from lowest to highest, starting at value. */
    Setting setting(String label, int min, int max, int value) {
        Setting setting = new Setting(label, min, max, value, false);
        settings.add(setting);
        return setting;
    }

    /** An on and off setting of this training. */
    Setting toggle(String label, boolean on) {
        Setting setting = new Setting(label, 0, 1, on ? 1 : 0, true);
        settings.add(setting);
        return setting;
    }

    /** A setting was changed from the hotbar: by default, the attempt starts again with it. */
    void settingChanged(Setting setting) {
        startRound();
    }

    @Override
    void afterKit(Player player) {
        controls(player);
    }

    /**
     * The bar of controls, from the right of the hotbar: the restart, the mode (previous, next),
     * then each setting (lower, raise; or one item for on and off). The number on a dye is where
     * the mode or the setting is now.
     */
    void controls(Player player) {
        PlayerInventory inventory = player.getInventory();
        // an item of the kit the player keeps where the controls go moves out of their way
        int needed = 1 + (variants.size() > 1 ? 2 : 0);
        for (Setting setting : settings) {
            needed += setting.toggle ? 1 : 2;
        }
        int lowest = Math.max(0, RESTART_SLOT - needed + 1);
        for (int at = lowest; at <= RESTART_SLOT; at++) {
            ItemStack there = inventory.getItem(at);
            if (there == null || there.getType() == Material.AIR || isControl(there)) {
                continue;
            }
            int free = freeSlotOutside(inventory, lowest);
            if (free >= 0) {
                inventory.setItem(free, there);
                inventory.setItem(at, null);
            }
        }
        int slot = RESTART_SLOT;
        inventory.setItem(slot--, named(Material.BLAZE_ROD, 1, 0, RESTART_ITEM));
        if (variants.size() > 1) {
            String now = ChatColor.GRAY + "Now: " + ChatColor.WHITE + current().label + ChatColor.GRAY + " (" + (variant + 1)
                    + "/" + variants.size() + ")";
            inventory.setItem(slot--, named(Material.INK_SACK, variant + 1, GREEN_DYE, NEXT_MODE, now, ChatColor.GRAY + "Right click"));
            inventory.setItem(slot--, named(Material.INK_SACK, variant + 1, RED_DYE, PREVIOUS_MODE, now, ChatColor.GRAY + "Right click"));
        }
        for (Setting setting : settings) {
            if (slot < 0) {
                break;
            }
            String now = ChatColor.GRAY + "Now: " + setting.text();
            if (setting.toggle) {
                inventory.setItem(slot--, named(Material.INK_SACK, 1, setting.on() ? GREEN_DYE : GRAY_DYE,
                        SWITCH + setting.label + ": " + setting.text(), ChatColor.GRAY + "Right click: on or off"));
                continue;
            }
            int amount = Math.max(1, Math.min(64, setting.value));
            inventory.setItem(slot--, named(Material.INK_SACK, amount, GREEN_DYE, RAISE + setting.label, now,
                    ChatColor.GRAY + "Right click: raise"));
            if (slot >= 0) {
                inventory.setItem(slot--, named(Material.INK_SACK, amount, RED_DYE, LOWER + setting.label, now,
                        ChatColor.GRAY + "Right click: lower"));
            }
        }
    }

    /** The restart or a dye of the bar of controls. */
    static boolean isControl(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        return item.getType() == Material.INK_SACK
                || item.getType() == Material.BLAZE_ROD && RESTART_ITEM.equals(item.getItemMeta().getDisplayName());
    }

    /** The first empty slot of the hotbar left of the controls, else of the inventory; -1 if none. */
    private static int freeSlotOutside(PlayerInventory inventory, int lowest) {
        for (int slot = 0; slot < 36; slot++) {
            if (slot >= lowest && slot <= RESTART_SLOT) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                return slot;
            }
        }
        return -1;
    }

    /** If this item is a control (the mode, a setting), applies it and returns true. */
    private boolean control(Player player, ItemStack item) {
        if (item == null || item.getType() != Material.INK_SACK || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        String name = item.getItemMeta().getDisplayName();
        if (name.equals(PREVIOUS_MODE) || name.equals(NEXT_MODE)) {
            int by = name.equals(NEXT_MODE) ? 1 : -1;
            variant = (variant + by + variants.size()) % variants.size();
            player.sendMessage(ChatColor.GREEN + "Mode: " + ChatColor.WHITE + current().label + ChatColor.GRAY + " - " + describe());
            sound(player, Sound.CLICK, by > 0 ? 1.2f : 0.9f);
            startRound();
            return true;
        }
        for (Setting setting : settings) {
            int by = name.equals(RAISE + setting.label) ? 1 : name.equals(LOWER + setting.label) ? -1
                    : name.startsWith(SWITCH + setting.label + ": ") && setting.toggle ? 1 : 0;
            if (by == 0) {
                continue;
            }
            if (!setting.step(by)) {
                player.sendMessage(ChatColor.GRAY + setting.label + " is already at its " + (by > 0 ? "highest" : "lowest") + ".");
                sound(player, Sound.VILLAGER_NO, 1f);
                return true;
            }
            player.sendMessage(ChatColor.GREEN + setting.label + ": " + setting.text());
            sound(player, Sound.CLICK, by > 0 ? 1.2f : 0.9f);
            controls(player);
            player.updateInventory();
            settingChanged(setting);
            return true;
        }
        return false;
    }

    @Override
    void interact(Player player, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            use(player, event);
            return;
        }
        ItemStack item = event.getItem();
        if (control(player, item)) {
            event.setCancelled(true);
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
        if (variants.size() > 1 || !settings.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Hotbar: " + ChatColor.RED + "red dye" + ChatColor.GRAY + " lowers, "
                    + ChatColor.GREEN + "green dye" + ChatColor.GRAY + " raises (the mode, the settings: the number on the dye is where"
                    + " it is), " + ChatColor.YELLOW + "blaze rod" + ChatColor.GRAY + " restarts. /spawn: back to the lobby.");
        } else {
            player.sendMessage(ChatColor.GRAY + "The blaze rod restarts, /spawn takes you back to the lobby.");
        }
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

    /**
     * A new score; quiet: a message only for a new best (a streak that goes on). It also counts for
     * the challenge of the day when this is its training.
     */
    void score(long value, boolean quiet) {
        record(value, quiet);
        if (host.challenges() != null) {
            host.challenges().scored(me, myName, board(), value);
        }
    }

    private void record(long value, boolean quiet) {
        Player player = player();
        succeeded(me);
        RecordBook.Result result = host.records().submit(board(), me, myName, value);
        last = format(value);
        if (result.personalBest) {
            othersOn(board());
        }
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

    /**
     * A score of the player on another board than the training's own (the biggest clutch...),
     * nothing said; the other trainings of the board see the new best at once.
     */
    RecordBook.Result submitTo(String board, long value) {
        RecordBook.Result result = host.records().submit(board, me, myName, value);
        if (result.personalBest) {
            othersOn(board);
        }
        return result;
    }

    /**
     * Someone has a new best on this board: every other training that shows it (its top 3 in the
     * sidebar, a board on the island) shows it again at once, so that everyone sees the same top.
     */
    private void othersOn(String board) {
        for (PracticeGame game : host.allGames()) {
            if (game != this && game instanceof Training && ((Training) game).shows(board)) {
                ((Training) game).bestOn(board);
            }
        }
    }

    /** True if this training shows this board: by default, its own. */
    boolean shows(String board) {
        return board().equals(board);
    }

    /** Someone else has a new best on a board this training shows. */
    void bestOn(String board) {
        sidebars();
    }

    /** The colours of the top 3 of the sidebar: gold, silver, bronze. */
    private static final ChatColor[] PODIUM = {ChatColor.GOLD, ChatColor.WHITE, ChatColor.RED};

    /**
     * The sidebar: the mode, the lines of the attempt, the top 3 of the server, the player's best
     * and place, the last score and the ping (in one line, so that the lobby adds none: a game
     * gives 12 lines).
     */
    @Override
    List<String> sidebar(UUID viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(Duel.cut(mode.name()));
        lines.add(Duel.cut(ChatColor.WHITE + "Mode: " + ChatColor.GREEN + current().label
                + (host.challenges() != null && host.challenges().isToday(board()) ? ChatColor.GOLD + " \u2726 Daily" : "")));
        lines.add("");
        trainingLines(lines);
        lines.add("");
        List<RecordBook.Entry> ranked = host.records().book().ranked(board());
        for (int i = 0; i < 3 && i < ranked.size(); i++) {
            RecordBook.Entry entry = ranked.get(i);
            lines.add(Duel.cut(PODIUM[i] + "#" + (i + 1) + " " + (entry.player.equals(me) ? ChatColor.GREEN : ChatColor.WHITE)
                    + entry.name + " " + ChatColor.GRAY + format(entry.value)));
        }
        if (ranked.isEmpty()) {
            lines.add(ChatColor.GRAY + "No record yet: be the first!");
        }
        Long best = host.records().book().best(board(), me);
        int rank = host.records().book().rank(board(), me);
        lines.add(Duel.cut(ChatColor.WHITE + "You: " + (best == null ? ChatColor.GRAY + "no score" : ChatColor.GREEN + format(best)
                + (rank > 0 ? ChatColor.GRAY + " (#" + rank + ")" : ""))));
        Player player = Bukkit.getPlayer(viewer);
        lines.add(Duel.cut((last.isEmpty() ? "" : ChatColor.WHITE + "Last: " + ChatColor.YELLOW + last + ChatColor.DARK_GRAY + " | ")
                + ChatColor.WHITE + "Ping: " + (player == null ? ChatColor.GRAY + "-" : Ping.text(player))));
        return fit(lines, ranked.size());
    }

    /** The most lines a game gives the sidebar of the lobby. */
    static final int MAX_LINES = 12;

    /**
     * Sidebar lines cut to {@link #MAX_LINES} when the attempt takes many: first the blank line
     * under the mode goes, then the "Mode:" line, then the third and the second of the top, so
     * that the player's best and the ping always show.
     */
    static List<String> fit(List<String> lines, int topShown) {
        List<String> fitted = new ArrayList<>(lines);
        if (fitted.size() > MAX_LINES) {
            fitted.remove(2);
        }
        if (fitted.size() > MAX_LINES) {
            fitted.remove(1);
        }
        int top = Math.min(3, topShown);
        while (fitted.size() > MAX_LINES && top > 1) {
            fitted.remove(fitted.size() - 3);
            top--;
        }
        return fitted;
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
        tried(me);
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
