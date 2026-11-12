package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * A solo training: one player, attempts one after the other (each on the arena as built), a
 * personal best and the server's best for each of its variants, kept in records.yml. At the end
 * of the hotbar, the controls: the green dye goes to the next level (when there are several), the
 * bed back to the lobby, the compass to another training, the comparator opens the settings (the
 * mode, each setting with - and +, back to default, the presets), the blaze rod restarts. The
 * player puts the items and the controls where they like in their inventory: the lobby keeps the
 * kit's, reglages.yml the controls', for every training. The training waits (no hit, no new
 * attempt) while a menu is open or the player arranges their inventory. No damage, no stats.
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

    /** The controls of the hotbar: their key in reglages.yml and their slot by default. */
    enum Control {
        NEXT("suivant", 4),
        LEAVE("quitter", 5),
        TRAININGS("trainings", 6),
        SETTINGS("reglages", 7),
        RESTART("recommencer", 8);

        final String key;
        final int slot;

        Control(String key, int slot) {
            this.key = key;
            this.slot = slot;
        }
    }

    static final String NEXT_ITEM = ChatColor.GREEN + "Next level" + ChatColor.GRAY + " (right click)";
    static final String RESTART_ITEM = ChatColor.YELLOW + "Restart" + ChatColor.GRAY + " (right click)";
    static final String SETTINGS_ITEM = ChatColor.AQUA + "Settings" + ChatColor.GRAY + " (right click)";
    static final String TRAININGS_ITEM = ChatColor.GREEN + "Trainings" + ChatColor.GRAY + " (right click)";
    static final String LEAVE_ITEM = ChatColor.RED + "Leave" + ChatColor.GRAY + " (right click)";
    /** The slots of an inventory the kit and the controls go to: the hotbar (0 to 8), then the rest. */
    static final int SLOTS = 36;
    /**
     * After a click in their own inventory, the training waits this long for the player to close it
     * (the client says nothing when it opens): no hit, no new attempt that would give the kit back.
     */
    static final long ARRANGING_MS = 15000L;
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
    /** The board clicked in /records ("Click to play it"), played once the settings are given back; or null. */
    private String wantedBoard;
    /** True once the player's last variant and settings are given back. */
    private boolean restored;
    /**
     * True when the mode or a setting changed in the settings menu: the attempt starts again when
     * the menu closes (a new start teleports the player, which would close the menu at each click).
     */
    private boolean restartOnClose;
    /** Until when the player arranges their inventory (a click in it), in milliseconds; 0 when closed. */
    private long arrangingUntil;

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
        // a board clicked in /records: its variant and settings, once the subclass has made them
        RecordsMenu records = host.plugin().recordsMenu();
        this.wantedBoard = records == null ? null : records.takeBoard(me, mode.id());
    }

    /**
     * The player's last variant (unless the challenge of the day chose it) and settings in this
     * training, given back once, before the first attempt: the subclasses have made their settings.
     * Then those of the challenge of the day, or of the board clicked in /records.
     */
    private void restore() {
        if (restored) {
            return;
        }
        restored = true;
        TrainingPrefs prefs = host.prefs();
        TrainingPrefs.Choice choice = prefs == null ? null : prefs.choice(me, mode.id());
        if (choice != null) {
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
        if (challengeVariant) {
            forChallenge();
        } else if (wantedBoard != null && chooseBoard(wantedBoard)) {
            // the player's own choice: it comes back next time
            remember();
        }
        wantedBoard = null;
    }

    /** The challenge of the day chose the variant: the settings it is played with (the player's own, by default). */
    void forChallenge() {
    }

    /**
     * The variant (and the settings) of this board of the records ("clutch.island"): true if it
     * is one of this training's, now the one played.
     */
    boolean chooseBoard(String board) {
        for (int i = 0; i < variants.size(); i++) {
            if (boardOf(variants.get(i)).equals(board)) {
                variant = i;
                return true;
            }
        }
        return false;
    }

    /**
     * A board of /records clicked in this training: its variant and settings, kept for the next
     * time, and a new attempt with it.
     */
    void playBoard(Player player, RecordCatalog.Board board) {
        if (!chooseBoard(board.id)) {
            return;
        }
        challengeVariant = false;
        restartOnClose = false;
        remember();
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + ChatColor.stripColor(mode.name()) + ChatColor.GRAY + " - "
                + ChatColor.WHITE + board.label + ChatColor.GRAY + ": " + describe());
        settingsChanged();
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
                forChallenge();
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
        return boardOf(current());
    }

    /** The board of the records of this variant, with the settings played now: "clutch.island". */
    String boardOf(Variant v) {
        return mode.id() + "." + v.id;
    }

    /** The board of the challenge of the day this attempt plays for: by default, the training's own. */
    String challengeBoard() {
        return board();
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

    /** The controls this training has: the green dye with several levels, the comparator with settings. */
    Set<Control> present() {
        Set<Control> present = EnumSet.of(Control.LEAVE, Control.TRAININGS, Control.RESTART);
        if (variants.size() > 1) {
            present.add(Control.NEXT);
        }
        if (hasSettings()) {
            present.add(Control.SETTINGS);
        }
        return present;
    }

    /** Where each control of this training goes for this player: where they put it, else its own slot. */
    Map<Control, Integer> controlSlots() {
        TrainingPrefs prefs = host.prefs();
        return placement(prefs == null ? null : prefs.controls(me), present());
    }

    /**
     * Where each control goes: the slot the player gave it (saved: by key, from 0 to 35), else its
     * own; one taken by another control gives the first free one of the hotbar from the end, then of
     * the inventory.
     */
    static Map<Control, Integer> placement(Map<String, Integer> saved, Set<Control> present) {
        Map<Control, Integer> at = new EnumMap<>(Control.class);
        boolean[] taken = new boolean[SLOTS];
        for (Control control : present) {
            Integer slot = saved == null ? null : saved.get(control.key);
            if (slot != null && slot >= 0 && slot < SLOTS && !taken[slot]) {
                at.put(control, slot);
                taken[slot] = true;
            }
        }
        for (Control control : present) {
            if (at.containsKey(control)) {
                continue;
            }
            int slot = control.slot;
            for (int i = 8; taken[slot] && i >= 0; i--) {
                slot = i;
            }
            for (int i = 9; taken[slot] && i < SLOTS; i++) {
                slot = i;
            }
            at.put(control, slot);
            taken[slot] = true;
        }
        return at;
    }

    /**
     * The controls in the inventory, where the player put them: the green dye (the next level), the
     * bed (back to the lobby), the compass (another training), the comparator (the settings, when
     * there are some) and the blaze rod (restart). An item of the kit where a control goes moves out
     * of its way.
     */
    void controls(Player player) {
        PlayerInventory inventory = player.getInventory();
        Map<Control, Integer> at = controlSlots();
        // no control left elsewhere: each goes back where it belongs
        for (int slot = 0; slot < SLOTS; slot++) {
            if (isControl(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
        for (int slot : at.values()) {
            ItemStack there = inventory.getItem(slot);
            if (there == null || there.getType() == Material.AIR) {
                continue;
            }
            int free = freeSlot(inventory, at);
            if (free >= 0) {
                inventory.setItem(free, there);
            }
            inventory.setItem(slot, null);
        }
        for (Map.Entry<Control, Integer> control : at.entrySet()) {
            inventory.setItem(control.getValue(), item(control.getKey()));
        }
    }

    /** The item of a control, as it is now (the level on the dye, the settings on the comparator). */
    ItemStack item(Control control) {
        switch (control) {
            case NEXT:
                return named(Material.INK_SACK, variant + 1, GREEN_DYE, NEXT_ITEM, ChatColor.GRAY + "Now: " + ChatColor.WHITE
                        + current().label, ChatColor.GRAY + "Next: " + ChatColor.WHITE + variants.get((variant + 1) % variants.size()).label,
                        ChatColor.GRAY + "Shift + right click: the one before");
            case LEAVE:
                return named(Material.BED, 1, 0, LEAVE_ITEM, ChatColor.GRAY + "Back to the lobby.");
            case TRAININGS:
                return named(Material.COMPASS, 1, 0, TRAININGS_ITEM, ChatColor.GRAY + "Another training, straight from here.");
            case SETTINGS:
                List<String> lore = new ArrayList<>();
                if (variants.size() > 1) {
                    lore.add(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + current().label);
                }
                for (Setting setting : settings) {
                    lore.add(ChatColor.GRAY + setting.label + ": " + setting.text());
                }
                return named(Material.REDSTONE_COMPARATOR, 1, 0, SETTINGS_ITEM, lore.toArray(new String[0]));
            default:
                return settings.isEmpty() ? named(Material.BLAZE_ROD, 1, 0, RESTART_ITEM)
                        : named(Material.BLAZE_ROD, 1, 0, RESTART_ITEM, ChatColor.GRAY + "Shift + right click: default settings");
        }
    }

    /** The control this item is, or null. */
    static Control controlOf(ItemStack item) {
        if (isNamed(item, Material.INK_SACK, NEXT_ITEM)) {
            return Control.NEXT;
        }
        if (isNamed(item, Material.BED, LEAVE_ITEM)) {
            return Control.LEAVE;
        }
        if (isNamed(item, Material.COMPASS, TRAININGS_ITEM)) {
            return Control.TRAININGS;
        }
        if (isNamed(item, Material.REDSTONE_COMPARATOR, SETTINGS_ITEM)) {
            return Control.SETTINGS;
        }
        return isNamed(item, Material.BLAZE_ROD, RESTART_ITEM) ? Control.RESTART : null;
    }

    /** One of the controls of the hotbar. */
    static boolean isControl(ItemStack item) {
        return controlOf(item) != null;
    }

    /** The first empty slot of the hotbar out of the controls, else of the inventory; -1 if none. */
    private static int freeSlot(PlayerInventory inventory, Map<Control, Integer> controls) {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (controls.containsValue(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Where the player put the controls in this inventory: each one once, every one there; null
     * otherwise (one on the cursor, an inventory not yet given).
     */
    static Map<Control, Integer> found(ItemStack[] contents, Set<Control> present) {
        Map<Control, Integer> found = new EnumMap<>(Control.class);
        for (int slot = 0; slot < SLOTS && slot < contents.length; slot++) {
            Control control = controlOf(contents[slot]);
            if (control == null) {
                continue;
            }
            if (found.put(control, slot) != null) {
                return null;
            }
        }
        return found.keySet().equals(present) ? found : null;
    }

    // --- The player arranges their inventory -------------------------------------------------------

    /** A click in the player's own inventory: the training waits until they close it. */
    void arranging() {
        arrangingUntil = System.currentTimeMillis() + ARRANGING_MS;
    }

    /**
     * The player closed their own inventory: where they put the controls is kept for every training
     * (the lobby keeps the kit's items), and the training goes on.
     */
    void inventoryClosed(Player player) {
        arrangingUntil = 0;
        Map<Control, Integer> found = found(player.getInventory().getContents(), present());
        TrainingPrefs prefs = host.prefs();
        if (found == null || prefs == null || found.equals(controlSlots())) {
            return;
        }
        Map<String, Integer> keys = new LinkedHashMap<>();
        for (Map.Entry<Control, Integer> control : found.entrySet()) {
            keys.put(control.getKey().key, control.getValue());
        }
        prefs.rememberControls(me, keys);
        player.sendMessage(ChatColor.GREEN + "Hotbar saved: the controls stay there in every training.");
    }

    /**
     * True while the training waits: a menu is open (the settings, the trainings of the compass) or
     * the player arranges their inventory. No hit, no new attempt: one would close the menu.
     */
    @Override
    boolean paused() {
        Player player = player();
        if (player == null) {
            return false;
        }
        InventoryType open = player.getOpenInventory().getType();
        return open != InventoryType.CRAFTING && open != InventoryType.CREATIVE || System.currentTimeMillis() < arrangingUntil;
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

    /**
     * A new attempt while the settings are open (a fall that began before they opened): the
     * teleport to the start closes them, so they open again at once.
     */
    @Override
    void startRound() {
        Player player = player();
        boolean settingsOpen = player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof GameMenu
                && ((GameMenu) player.getOpenInventory().getTopInventory().getHolder()).game == this;
        super.startRound();
        if (settingsOpen) {
            later(2L, () -> {
                Player again = player();
                if (again != null && again.getOpenInventory().getType() == InventoryType.CRAFTING) {
                    openSettings(again);
                }
            });
        }
    }

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
                        ChatColor.WHITE + setting.label + ": " + setting.text(), byDefault, ChatColor.RED + "Click: on or off");
                continue;
            }
            int amount = Math.max(1, Math.min(64, setting.value));
            items[row + LOWER_COLUMN] = named(Material.STAINED_GLASS_PANE, 1, RED_PANE, ChatColor.RED + "- " + setting.label,
                    ChatColor.RED + "Click: lower");
            items[row + VALUE_COLUMN] = named(Material.PAPER, amount, 0, ChatColor.WHITE + setting.label + ": " + setting.text(),
                    byDefault);
            items[row + RAISE_COLUMN] = named(Material.STAINED_GLASS_PANE, 1, GREEN_PANE, ChatColor.GREEN + "+ " + setting.label,
                    ChatColor.RED + "Click: raise");
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
            lore.add(matches(preset) ? ChatColor.GREEN + "Your settings now" : ChatColor.RED + "Click: use it");
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
        Long best = host.records().book().best(boardOf(v), me);
        lore.add(ChatColor.GRAY + "Your best: " + (best == null ? "none yet" : ChatColor.WHITE + format(v, best)));
        if (host.challenges() != null && host.challenges().isToday(mode.id() + "." + v.id)) {
            lore.add(ChatColor.GOLD + "\u2726 Challenge of the day");
        }
        lore.add(index == variant ? ChatColor.GREEN + "Playing now" : ChatColor.RED + "Click: play this mode");
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
        if (isNamed(item, Material.INK_SACK, NEXT_ITEM)) {
            event.setCancelled(true);
            nextLevel(player, player.isSneaking() ? -1 : 1);
            return;
        }
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

    /**
     * The green dye: the next level (by: 1), or the one before (-1, shift and a right click), the
     * first after the last; kept for the next time, and a new attempt with it.
     */
    void nextLevel(Player player, int by) {
        if (variants.size() < 2) {
            return;
        }
        variant = ((variant + by) % variants.size() + variants.size()) % variants.size();
        challengeVariant = false;
        restartOnClose = false;
        remember();
        sound(player, Sound.CLICK, by > 0 ? 1.4f : 0.9f);
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + ChatColor.stripColor(mode.name()) + ChatColor.GRAY + " - "
                + ChatColor.WHITE + current().label + ChatColor.GRAY + ": " + describe());
        settingsChanged();
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
        String next = variants.size() > 1 ? "the " + ChatColor.GREEN + "green dye" + ChatColor.GRAY + " goes to the next level, " : "";
        if (hasSettings()) {
            player.sendMessage(ChatColor.GRAY + "Hotbar: " + next + "the " + ChatColor.AQUA + "comparator" + ChatColor.GRAY
                    + " opens the settings (the mode, the settings, the presets), the " + ChatColor.YELLOW + "blaze rod"
                    + ChatColor.GRAY + " restarts, the " + ChatColor.GREEN + "compass" + ChatColor.GRAY + " takes you to another"
                    + " training, the " + ChatColor.RED + "bed" + ChatColor.GRAY + " back to the lobby. Your last mode and"
                    + " settings come back next time; arrange your inventory as you like, it is kept.");
        } else {
            player.sendMessage(ChatColor.GRAY + "Hotbar: " + next + "the " + ChatColor.YELLOW + "blaze rod" + ChatColor.GRAY + " restarts, the "
                    + ChatColor.GREEN + "compass" + ChatColor.GRAY + " takes you to another training, the " + ChatColor.RED + "bed"
                    + ChatColor.GRAY + " back to the lobby. Arrange your inventory as you like: it is kept.");
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
            host.challenges().scored(me, myName, challengeBoard(), value);
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
                + (host.challenges() != null && host.challenges().isToday(challengeBoard()) ? ChatColor.GOLD + " \u2726 Daily" : "")));
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
            if (round != roundNow) {
                return;
            }
            if (paused()) {
                // a menu open, an inventory arranged: the new attempt waits for them
                again(10L);
                return;
            }
            startRound();
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
