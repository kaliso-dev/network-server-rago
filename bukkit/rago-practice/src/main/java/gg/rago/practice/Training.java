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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * A solo training: one player, attempts one after the other (each on the arena as built), a
 * personal best and the server's best for each of its variants, kept in records.yml. At the end
 * of the hotbar, the controls: the bed goes back to the lobby, the compass to another training,
 * the comparator opens the settings (the mode, each setting with - and +, back to default, the
 * presets), the blaze rod restarts. No damage, no stats.
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
     * A setting of the training the player changes in the settings menu: a whole number from min to
     * max, or on and off (one item flips it).
     */
    static final class Setting {
        final String label;
        final int min;
        final int max;
        final boolean toggle;
        /** Its value by default, that "Reset to default" (or shift and a right click on the blaze rod) gives back. */
        final int initial;
        int value;

        Setting(String label, int min, int max, int value, boolean toggle) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.value = value;
            this.initial = value;
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

    /** A choice of every setting at once, in the settings menu ("Hypixel", "Hard"...). */
    static final class Preset {
        final String name;
        final Material icon;
        final String description;
        /** A value for each setting of the training, in their order. */
        final int[] values;

        Preset(String name, Material icon, String description, int[] values) {
            this.name = name;
            this.icon = icon;
            this.description = description;
            this.values = values;
        }
    }

    static final String RESTART_ITEM = ChatColor.YELLOW + "Restart" + ChatColor.GRAY + " (right click)";
    static final String SETTINGS_ITEM = ChatColor.AQUA + "Settings" + ChatColor.GRAY + " (right click)";
    static final String TRAININGS_ITEM = ChatColor.GREEN + "Trainings" + ChatColor.GRAY + " (right click)";
    static final String LEAVE_ITEM = ChatColor.RED + "Leave" + ChatColor.GRAY + " (right click)";
    /** The controls, at the end of the hotbar; the kit keeps the slots before them. */
    static final int LEAVE_SLOT = 5;
    static final int TRAININGS_SLOT = 6;
    static final int SETTINGS_SLOT = 7;
    static final int RESTART_SLOT = 8;
    /** The settings menu: a dye for an on and off setting (green on, gray off), panes to lower (red) and raise (green). */
    static final int GREEN_DYE = 10;
    static final int GRAY_DYE = 8;
    static final int RED_PANE = 14;
    static final int GREEN_PANE = 5;

    final List<Variant> variants = new ArrayList<>();
    int variant;
    /** The settings of the training, in the settings menu under the mode. */
    final List<Setting> settings = new ArrayList<>();
    /** The presets of the settings menu, if the training has some. */
    final List<Preset> presets = new ArrayList<>();
    final UUID me;
    private final String myName;
    /** The last score of the player, for the sidebar. */
    String last = "";
    /** True when [PLAY] of the challenge of the day chose the variant: the player's last one does not replace it. */
    private boolean challengeVariant;
    /** True once the player's last variant and settings are given back. */
    private boolean restored;
    /**
     * True when the mode or a setting changed in the settings menu: the attempt starts again when
     * the menu closes (a new start teleports the player, which would close the menu at each click).
     */
    private boolean restartOnClose;

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
                this.challengeVariant = true;
            }
        }
    }

    /**
     * The player's last variant (unless the challenge of the day chose it) and settings in this
     * training, given back once, before the first attempt: the subclasses have made their settings.
     */
    private void restore() {
        if (restored) {
            return;
        }
        restored = true;
        TrainingPrefs prefs = host.prefs();
        TrainingPrefs.Choice choice = prefs == null ? null : prefs.choice(me, mode.id());
        if (choice == null) {
            return;
        }
        for (int i = 0; !challengeVariant && choice.variant != null && i < variants.size(); i++) {
            if (variants.get(i).id.equals(choice.variant)) {
                variant = i;
            }
        }
        for (Setting setting : settings) {
            Integer value = choice.settings.get(TrainingPrefs.key(setting.label));
            if (value != null) {
                setting.value = Math.max(setting.min, Math.min(setting.max, value));
            }
        }
    }

    /**
     * /challenge play in this training (the challenge of the day in its Trainings menu): the
     * variant of the challenge, and a new attempt with it. The player's own choice stays for the
     * next time, as with [PLAY] from the lobby.
     */
    void playVariant(Player player, String variantId) {
        for (int i = 0; i < variants.size(); i++) {
            if (variants.get(i).id.equals(variantId)) {
                variant = i;
                challengeVariant = true;
                restartOnClose = false;
                player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Daily challenge" + ChatColor.GRAY + " - " + ChatColor.WHITE
                        + current().label + ChatColor.GRAY + ": " + describe());
                settingsChanged();
                return;
            }
        }
    }

    /** Keeps the player's variant and settings for the next time they play this training. */
    private void remember() {
        TrainingPrefs prefs = host.prefs();
        if (prefs != null) {
            prefs.remember(me, mode.id(), current().id, settings);
        }
    }

    /** Every setting back to its value by default: true if one changed. */
    boolean defaults() {
        boolean changed = false;
        for (Setting setting : settings) {
            if (setting.value != setting.initial) {
                setting.value = setting.initial;
                changed = true;
            }
        }
        return changed;
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

    /**
     * A preset of the settings menu: a value for each setting of the training, in the order they
     * were made (0 and 1 for on and off).
     */
    void preset(String name, Material icon, String description, int... values) {
        presets.add(new Preset(name, icon, description, values));
    }

    /** True when the training has something to set: several modes, or settings. */
    boolean hasSettings() {
        return variants.size() > 1 || !settings.isEmpty();
    }

    /**
     * The mode or the settings changed in the settings menu, which is closed now: by default, the
     * attempt starts again with them.
     */
    void settingsChanged() {
        startRound();
    }

    @Override
    void afterKit(Player player) {
        controls(player);
    }

    /**
     * The controls at the end of the hotbar: the bed (back to the lobby), the compass (another
     * training), the comparator (the settings, when there are some) and the blaze rod (restart).
     */
    void controls(Player player) {
        PlayerInventory inventory = player.getInventory();
        // an item of the kit the player keeps where the controls go moves out of their way
        for (int at = LEAVE_SLOT; at <= RESTART_SLOT; at++) {
            ItemStack there = inventory.getItem(at);
            if (there == null || there.getType() == Material.AIR || isControl(there) || !reserved(at)) {
                continue;
            }
            int free = freeSlot(inventory);
            if (free >= 0) {
                inventory.setItem(free, there);
                inventory.setItem(at, null);
            }
        }
        inventory.setItem(LEAVE_SLOT, named(Material.BED, 1, 0, LEAVE_ITEM, ChatColor.GRAY + "Back to the lobby."));
        inventory.setItem(TRAININGS_SLOT, named(Material.COMPASS, 1, 0, TRAININGS_ITEM,
                ChatColor.GRAY + "Another training, straight from here."));
        if (hasSettings()) {
            List<String> lore = new ArrayList<>();
            if (variants.size() > 1) {
                lore.add(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + current().label);
            }
            for (Setting setting : settings) {
                lore.add(ChatColor.GRAY + setting.label + ": " + setting.text());
            }
            inventory.setItem(SETTINGS_SLOT, named(Material.REDSTONE_COMPARATOR, 1, 0, SETTINGS_ITEM, lore.toArray(new String[0])));
        } else if (isControl(inventory.getItem(SETTINGS_SLOT))) {
            inventory.setItem(SETTINGS_SLOT, null);
        }
        inventory.setItem(RESTART_SLOT, settings.isEmpty() ? named(Material.BLAZE_ROD, 1, 0, RESTART_ITEM)
                : named(Material.BLAZE_ROD, 1, 0, RESTART_ITEM, ChatColor.GRAY + "Shift + right click: default settings"));
    }

    /** True for a slot of the controls: the settings' only when the training has some. */
    private boolean reserved(int slot) {
        return slot >= LEAVE_SLOT && slot <= RESTART_SLOT && (slot != SETTINGS_SLOT || hasSettings());
    }

    /** One of the controls of the hotbar. */
    static boolean isControl(ItemStack item) {
        return isNamed(item, Material.BLAZE_ROD, RESTART_ITEM) || isNamed(item, Material.REDSTONE_COMPARATOR, SETTINGS_ITEM)
                || isNamed(item, Material.COMPASS, TRAININGS_ITEM) || isNamed(item, Material.BED, LEAVE_ITEM);
    }

    /** The first empty slot of the hotbar out of the controls, else of the inventory; -1 if none. */
    private int freeSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < 36; slot++) {
            if (reserved(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                return slot;
            }
        }
        return -1;
    }

    // --- The settings menu ----------------------------------------------------------------------

    /** The settings menu: its title, cut to the 32 letters of a chest's. */
    String settingsTitle() {
        String title = "Settings: " + ChatColor.stripColor(mode.name());
        return title.length() > 32 ? title.substring(0, 32) : title;
    }

    /** The row of the modes (-1 without one), of the first setting, of the buttons at the bottom. */
    private int modeRow() {
        return variants.size() > 1 ? 0 : -1;
    }

    private int firstSettingRow() {
        return modeRow() + 1;
    }

    private int bottomRow() {
        return firstSettingRow() + settings.size();
    }

    /** The column of the first mode of the row: the modes are in the middle. */
    private int firstModeColumn() {
        return Math.max(0, (9 - variants.size()) / 2);
    }

    /** The columns of the presets in the bottom row, in the middle. */
    private int presetColumn(int index) {
        int count = Math.min(presets.size(), 3);
        return 4 - (count - 1) + index * 2;
    }

    static final int RESET_COLUMN = 0;
    static final int CLOSE_COLUMN = 8;
    static final int LOWER_COLUMN = 3;
    static final int VALUE_COLUMN = 4;
    static final int RAISE_COLUMN = 5;

    /** Opens the settings menu: the mode, each setting with - and +, back to default, the presets. */
    void openSettings(Player player) {
        player.openInventory(new GameMenu(this, settingsTitle(), settingsItems()).getInventory());
    }

    /** The items of the settings menu, as they are now. */
    ItemStack[] settingsItems() {
        ItemStack[] items = new ItemStack[(bottomRow() + 1) * 9];
        if (modeRow() >= 0) {
            for (int i = 0; i < variants.size() && i < 9; i++) {
                items[modeRow() * 9 + firstModeColumn() + i] = modeItem(i);
            }
        }
        for (int i = 0; i < settings.size(); i++) {
            Setting setting = settings.get(i);
            int row = (firstSettingRow() + i) * 9;
            String byDefault = ChatColor.GRAY + "Default: " + (setting.toggle ? (setting.initial != 0 ? "On" : "Off")
                    : ChatColor.WHITE + "" + setting.initial);
            if (setting.toggle) {
                items[row + VALUE_COLUMN] = named(Material.INK_SACK, 1, setting.on() ? GREEN_DYE : GRAY_DYE,
                        ChatColor.WHITE + setting.label + ": " + setting.text(), byDefault, ChatColor.YELLOW + "Click: on or off");
                continue;
            }
            int amount = Math.max(1, Math.min(64, setting.value));
            items[row + LOWER_COLUMN] = named(Material.STAINED_GLASS_PANE, 1, RED_PANE, ChatColor.RED + "- " + setting.label,
                    ChatColor.YELLOW + "Click: lower");
            items[row + VALUE_COLUMN] = named(Material.PAPER, amount, 0, ChatColor.WHITE + setting.label + ": " + setting.text(),
                    byDefault);
            items[row + RAISE_COLUMN] = named(Material.STAINED_GLASS_PANE, 1, GREEN_PANE, ChatColor.GREEN + "+ " + setting.label,
                    ChatColor.YELLOW + "Click: raise");
        }
        int bottom = bottomRow() * 9;
        if (!settings.isEmpty()) {
            items[bottom + RESET_COLUMN] = named(Material.REDSTONE, 1, 0, ChatColor.YELLOW + "Reset to default",
                    ChatColor.GRAY + "Every setting as it is by default.");
        }
        for (int i = 0; i < presets.size() && i < 3; i++) {
            Preset preset = presets.get(i);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + preset.description);
            lore.add(matches(preset) ? ChatColor.GREEN + "Your settings now" : ChatColor.YELLOW + "Click: use it");
            items[bottom + presetColumn(i)] = named(preset.icon, 1, 0, ChatColor.GOLD + "Preset: " + preset.name,
                    lore.toArray(new String[0]));
        }
        items[bottom + CLOSE_COLUMN] = named(Material.BARRIER, 1, 0, ChatColor.RED + "Close");
        return items;
    }

    /** The item of a mode of the training in the menu: its best, the one played now. */
    private ItemStack modeItem(int index) {
        Variant v = variants.get(index);
        List<String> lore = new ArrayList<>();
        Long best = host.records().book().best(mode.id() + "." + v.id, me);
        lore.add(ChatColor.GRAY + "Your best: " + (best == null ? "none yet" : ChatColor.WHITE + format(v, best)));
        if (host.challenges() != null && host.challenges().isToday(mode.id() + "." + v.id)) {
            lore.add(ChatColor.GOLD + "\u2726 Challenge of the day");
        }
        lore.add(index == variant ? ChatColor.GREEN + "Playing now" : ChatColor.YELLOW + "Click: play this mode");
        ItemStack item = named(index == variant ? Material.EMPTY_MAP : Material.PAPER, index + 1, 0,
                (index == variant ? ChatColor.GREEN : ChatColor.WHITE) + v.label, lore.toArray(new String[0]));
        return item;
    }

    /** True if every setting has the value of this preset. */
    private boolean matches(Preset preset) {
        for (int i = 0; i < settings.size() && i < preset.values.length; i++) {
            if (settings.get(i).value != preset.values[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    void menuClosed(Player player) {
        if (restartOnClose) {
            restartOnClose = false;
            settingsChanged();
        }
    }

    @Override
    void menuClick(Player player, int slot) {
        int row = slot / 9;
        int column = slot % 9;
        if (row == modeRow()) {
            int index = column - firstModeColumn();
            if (index >= 0 && index < variants.size() && index != variant) {
                variant = index;
                remember();
                changed(player, Sound.CLICK, 1.2f);
            }
            return;
        }
        int index = row - firstSettingRow();
        if (index >= 0 && index < settings.size()) {
            Setting setting = settings.get(index);
            int by = setting.toggle ? (column == VALUE_COLUMN ? 1 : 0) : column == RAISE_COLUMN ? 1 : column == LOWER_COLUMN ? -1 : 0;
            if (by == 0) {
                return;
            }
            if (!setting.step(by)) {
                sound(player, Sound.VILLAGER_NO, 1f);
                return;
            }
            remember();
            changed(player, Sound.CLICK, by > 0 ? 1.2f : 0.9f);
            return;
        }
        if (row != bottomRow()) {
            return;
        }
        if (column == CLOSE_COLUMN) {
            player.closeInventory();
        } else if (column == RESET_COLUMN && !settings.isEmpty()) {
            if (!defaults()) {
                sound(player, Sound.VILLAGER_NO, 1f);
                return;
            }
            remember();
            player.sendMessage(ChatColor.GREEN + "Settings back to default.");
            changed(player, Sound.CLICK, 1.4f);
        } else {
            for (int i = 0; i < presets.size() && i < 3; i++) {
                if (column == presetColumn(i)) {
                    use(player, presets.get(i));
                }
            }
        }
    }

    /** The settings take the values of this preset. */
    private void use(Player player, Preset preset) {
        if (matches(preset)) {
            sound(player, Sound.VILLAGER_NO, 1f);
            return;
        }
        for (int i = 0; i < settings.size() && i < preset.values.length; i++) {
            Setting setting = settings.get(i);
            setting.value = Math.max(setting.min, Math.min(setting.max, preset.values[i]));
        }
        remember();
        player.sendMessage(ChatColor.GREEN + "Preset: " + ChatColor.WHITE + preset.name + ChatColor.GRAY + " - " + preset.description);
        changed(player, Sound.CLICK, 1.4f);
    }

    /**
     * A change in the settings menu: the menu and the comparator show it, a click is heard, and the
     * attempt starts again with it when the menu closes.
     */
    private void changed(Player player, Sound sound, float pitch) {
        restartOnClose = true;
        sound(player, sound, pitch);
        controls(player);
        player.updateInventory();
        Inventory open = player.getOpenInventory().getTopInventory();
        if (open != null && open.getHolder() instanceof GameMenu && ((GameMenu) open.getHolder()).game == this) {
            ItemStack[] items = settingsItems();
            if (items.length == open.getSize()) {
                open.setContents(items);
            }
        }
    }

    @Override
    void interact(Player player, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            use(player, event);
            return;
        }
        ItemStack item = event.getItem();
        if (isNamed(item, Material.REDSTONE_COMPARATOR, SETTINGS_ITEM)) {
            event.setCancelled(true);
            openSettings(player);
            return;
        }
        if (isNamed(item, Material.COMPASS, TRAININGS_ITEM)) {
            event.setCancelled(true);
            if (!plugin.lobby().openModesOf(player, mode.id())) {
                player.sendMessage(ChatColor.GRAY + "Back to the lobby to choose another training: /spawn");
            }
            return;
        }
        if (isNamed(item, Material.BED, LEAVE_ITEM)) {
            event.setCancelled(true);
            // as /spawn, at the next tick, out of the event
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && host.gameOf(me) == this) {
                    player.performCommand("spawn");
                }
            });
            return;
        }
        if (isNamed(item, Material.BLAZE_ROD, RESTART_ITEM)) {
            event.setCancelled(true);
            if (player.isSneaking() && !settings.isEmpty()) {
                // shift and a right click: every setting as it is by default
                boolean changed = defaults();
                remember();
                player.sendMessage(changed ? ChatColor.GREEN + "Settings back to default." : ChatColor.GRAY + "The settings are already the default ones.");
                controls(player);
                player.updateInventory();
            }
            sound(player, Sound.CLICK, 1.4f);
            startRound();
            return;
        }
        use(player, event);
    }

    /** A click that is not one of the controls (a fireball, a pearl...). */
    void use(Player player, PlayerInteractEvent event) {
    }

    /** What the current variant asks, in one sentence. */
    abstract String describe();

    @Override
    void intro(Player player) {
        restore();
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + ChatColor.stripColor(mode.name()) + ChatColor.GRAY
                + " - " + ChatColor.WHITE + current().label + ChatColor.GRAY + ": " + describe());
        if (hasSettings()) {
            player.sendMessage(ChatColor.GRAY + "Hotbar: the " + ChatColor.AQUA + "comparator" + ChatColor.GRAY
                    + " opens the settings (the mode, the settings, the presets), the " + ChatColor.YELLOW + "blaze rod"
                    + ChatColor.GRAY + " restarts, the " + ChatColor.GREEN + "compass" + ChatColor.GRAY + " takes you to another"
                    + " training, the " + ChatColor.RED + "bed" + ChatColor.GRAY + " back to the lobby. Your last mode and"
                    + " settings come back next time.");
        } else {
            player.sendMessage(ChatColor.GRAY + "Hotbar: the " + ChatColor.YELLOW + "blaze rod" + ChatColor.GRAY + " restarts, the "
                    + ChatColor.GREEN + "compass" + ChatColor.GRAY + " takes you to another training, the " + ChatColor.RED + "bed"
                    + ChatColor.GRAY + " back to the lobby.");
        }
    }

    /** The text of a score: "4.512 s" or "12 clutches". */
    String format(long value) {
        return format(current(), value);
    }

    /** The text of a score of this variant. */
    static String format(Variant v, long value) {
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
            // a streak that goes on: nothing to say
        } else if (result.previous == null) {
            player.sendMessage(ChatColor.GREEN + "First score: " + ChatColor.WHITE + format(value));
            sound(player, Sound.ORB_PICKUP, 1f);
        } else {
            player.sendMessage(ChatColor.GREEN + format(value) + ChatColor.GRAY + " (your best: " + format(result.previous) + ")");
            sound(player, Sound.ORB_PICKUP, 1f);
        }
        // the quests of a new player, after the score: a training played, a record beaten
        host.plugin().lobby().trainingScored(player, mode.id(), result.personalBest && result.previous != null);
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
        return board().equals(board) || shownBoard().equals(board);
    }

    /** The board of the top 3 of the sidebar and of the player's line: by default, the training's own. */
    String shownBoard() {
        return board();
    }

    /** A score of the shown board as the sidebar writes it. */
    String shownText(long value) {
        return format(value);
    }

    /** Someone else has a new best on a board this training shows. */
    void bestOn(String board) {
        sidebars();
    }

    /** The colours of the top 3 of the sidebar: gold, silver, bronze. */
    private static final ChatColor[] PODIUM = {ChatColor.GOLD, ChatColor.WHITE, ChatColor.RED};

    /** False for a training whose sidebar shows no ranking: no top 3, no best nor place of the player. */
    boolean showsRanking() {
        return true;
    }

    /**
     * The sidebar: the mode, the lines of the attempt, the top 3 of the server, a blank line, the
     * player's best and place, the last score and the ping (in one line, so that the lobby adds
     * none: a game gives 12 lines). Without the ranking: the mode, the attempt and the ping.
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
        Player player = Bukkit.getPlayer(viewer);
        String ping = ChatColor.WHITE + "Ping: " + (player == null ? ChatColor.GRAY + "-" : Ping.text(player));
        if (!showsRanking()) {
            lines.add(Duel.cut(ping));
            return fitHead(lines);
        }
        String shown = shownBoard();
        List<RecordBook.Entry> ranked = host.records().book().ranked(shown);
        for (int i = 0; i < 3 && i < ranked.size(); i++) {
            RecordBook.Entry entry = ranked.get(i);
            lines.add(Duel.cut(PODIUM[i] + "#" + (i + 1) + " " + (entry.player.equals(me) ? ChatColor.GREEN : ChatColor.WHITE)
                    + entry.name + " " + ChatColor.GRAY + shownText(entry.value)));
        }
        if (ranked.isEmpty()) {
            lines.add(ChatColor.GRAY + "No record yet: be the first!");
        }
        // room between the third and the player's best
        lines.add("");
        Long best = host.records().book().best(shown, me);
        int rank = host.records().book().rank(shown, me);
        lines.add(Duel.cut(ChatColor.WHITE + "You: " + (best == null ? ChatColor.GRAY + "no score" : ChatColor.GREEN + shownText(best)
                + (rank > 0 ? ChatColor.GRAY + " (#" + rank + ")" : ""))));
        lines.add(Duel.cut((last.isEmpty() ? "" : ChatColor.WHITE + "Last: " + ChatColor.YELLOW + last + ChatColor.DARK_GRAY + " | ")
                + ping));
        return fit(lines, ranked.size());
    }

    /** The most lines a game gives the sidebar of the lobby. */
    static final int MAX_LINES = 12;

    /**
     * Sidebar lines cut to {@link #MAX_LINES} when the attempt takes many: first the blank line
     * under the mode goes, then the "Mode:" line, then the third and the second of the top, then
     * the blank line under the top, so that the player's best and the ping always show.
     */
    static List<String> fit(List<String> lines, int topShown) {
        List<String> fitted = fitHead(lines);
        int top = Math.min(3, topShown);
        // the end: the top, a blank line, the best, the ping
        while (fitted.size() > MAX_LINES && top > 1) {
            fitted.remove(fitted.size() - 4);
            top--;
        }
        if (fitted.size() > MAX_LINES) {
            fitted.remove(fitted.size() - 3);
        }
        return fitted;
    }

    /** Sidebar lines without the blank line under the mode, then without the "Mode:" line, while too many. */
    static List<String> fitHead(List<String> lines) {
        List<String> fitted = new ArrayList<>(lines);
        if (fitted.size() > MAX_LINES) {
            fitted.remove(2);
        }
        if (fitted.size() > MAX_LINES) {
            fitted.remove(1);
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
