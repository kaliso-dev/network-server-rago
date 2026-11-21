package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * The kit editor, opened by the book of the lobby hotbar. A first chest lists the modes that give
 * a kit, by category in the order of the selector (Practice, RBW Modes, Duels, Arenas), sorted by
 * name in each: a row for each category, its label first, then its kits (on more rows if it has
 * more than 8, on more pages if they do not fit). Each kit says which other modes play it and
 * whether the player arranged it. Clicking one puts that kit in the player's inventory, as they
 * arranged it last time, with a small chest above it: Save and Cancel (both back to the list),
 * Reset (the kit's own layout). The player moves the items of their inventory where they want
 * them; closing the chest saves too. The items stay the server's: they can be moved, not split,
 * merged, dropped or put in the chest. The lobby hotbar comes back once the chest is closed. The
 * bottom row of the list explains it, copies the player's Bed Wars shop and hotbar manager from
 * Hypixel in one click, and shows them. Main thread only.
 */
final class KitEditor implements Listener {

    static final String TITLE = "Kit editor";
    static final int SIZE = 27;
    static final int INFO = 4;
    static final int SAVE = 11;
    static final int RESET = 13;
    static final int CANCEL = 15;
    /** A page of the list: five rows of kits at most, the last row for the buttons. */
    static final int PAGE_ROWS = 5;
    /** A row of the list: the category's label in the first column, then 8 kits. */
    static final int ROW_KITS = 8;
    /** In the last row of the list: the page before, copy the Hypixel shop, how it works, show the shop, the page after. */
    static final int PREVIOUS_COLUMN = 0;
    static final int IMPORT_COLUMN = 3;
    static final int HELP_COLUMN = 4;
    static final int SHOP_COLUMN = 5;
    static final int NEXT_COLUMN = 8;

    /** A row of the list: the kits of one category (null: of none the selector has), its label on the first. */
    static final class Row {
        final String category;
        final boolean first;
        final List<String> modes;

        Row(String category, boolean first, List<String> modes) {
            this.category = category;
            this.first = first;
            this.modes = Collections.unmodifiableList(new ArrayList<>(modes));
        }
    }

    /** Marks our chests: the list of modes (no mode), or the editor of one mode's kit; the page of the list. */
    static final class Holder implements InventoryHolder {
        private final String modeId;
        /** The kits of the list, by slot. */
        private final Map<Integer, String> modes;
        private final int page;
        private Inventory inventory;

        Holder(String modeId, Map<Integer, String> modes, int page) {
            this.modeId = modeId;
            this.modes = modes;
            this.page = page;
        }

        /** The mode being edited, or null for the list of modes. */
        String modeId() {
            return modeId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Plugin plugin;
    private final LobbySettings settings;
    private final KitLayouts layouts;
    private final LobbyState state;
    private final Consumer<Player> lobbyHotbar;
    private final HypixelProfiles hypixel;
    /** The players arranging a kit, and its mode. */
    private final Map<UUID, String> editing = new HashMap<>();
    /** The players who saved or cancelled: the list comes back, at this page. */
    private final Map<UUID, Integer> backToList = new HashMap<>();
    /** The kits that games make themselves, by player then mode (or mode/role): learnt when a game gives one. */
    private final Map<UUID, Map<String, Kit>> codeKits = new HashMap<>();
    /** The kit a game gave each player last: its mode, or mode/role (the defenders of the invis practice). */
    private final Map<UUID, String> given = new HashMap<>();
    /** An arrangement made in a game counts with this share of the kit's stacks found, at least (a potion drunk...). */
    static final double FOUND_SHARE = 0.75;

    KitEditor(Plugin plugin, LobbySettings settings, KitLayouts layouts, LobbyState state, Consumer<Player> lobbyHotbar,
            HypixelProfiles hypixel) {
        this.plugin = plugin;
        this.hypixel = hypixel;
        this.settings = settings;
        this.layouts = layouts;
        this.state = state;
        this.lobbyHotbar = lobbyHotbar;
    }

    /** The modes that give a kit, in the order of config.yml. */
    List<ModeDisplay> modes() {
        List<ModeDisplay> modes = new ArrayList<>();
        for (ModeDisplay display : settings.modes()) {
            if (display.kit() != null) {
                modes.add(display);
            }
        }
        return modes;
    }

    /**
     * The rows of the list: the kits by category, in the order of the categories (those of no
     * category listed come last), sorted by name in each (ignoring colours and case); a category
     * starts a row, and takes as many rows of {@link #ROW_KITS} as it needs.
     */
    static List<Row> rows(List<String> modes, Map<String, String> categoryOf, Map<String, String> nameOf, List<String> categories) {
        List<String> order = new ArrayList<>(categories);
        order.add(null);
        List<Row> rows = new ArrayList<>();
        for (String category : order) {
            List<String> kits = new ArrayList<>();
            for (String mode : modes) {
                String of = categoryOf.get(mode);
                if (category == null ? of == null || !categories.contains(of) : category.equals(of)) {
                    kits.add(mode);
                }
            }
            kits.sort((a, b) -> sortName(nameOf.get(a), a).compareTo(sortName(nameOf.get(b), b)));
            for (int from = 0; from < kits.size(); from += ROW_KITS) {
                rows.add(new Row(category, from == 0, kits.subList(from, Math.min(kits.size(), from + ROW_KITS))));
            }
        }
        return rows;
    }

    private static String sortName(String name, String mode) {
        String plain = name == null ? mode : ChatColor.stripColor(name);
        return plain.toLowerCase(java.util.Locale.ROOT);
    }

    /** The rows of the list on this page ({@link #PAGE_ROWS} a page). */
    static List<Row> page(List<Row> rows, int page) {
        int from = Math.min(rows.size(), Math.max(0, page) * PAGE_ROWS);
        return rows.subList(from, Math.min(rows.size(), from + PAGE_ROWS));
    }

    /** How many pages the list has (one at least). */
    static int pages(List<Row> rows) {
        return Math.max(1, (rows.size() + PAGE_ROWS - 1) / PAGE_ROWS);
    }

    /** The names of the other modes that play this mode's kit (kit-de:), colours removed. */
    List<String> playedBy(String modeId) {
        List<String> names = new ArrayList<>();
        for (ModeDisplay display : settings.modes()) {
            if (modeId.equals(display.kitFrom())) {
                names.add(ChatColor.stripColor(display.mode().name()));
            }
        }
        return names;
    }

    /** Words in lines of about this many characters, each in this colour. */
    static List<String> wrap(String text, int width, String colour) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(colour + line);
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(colour + line);
        }
        return lines;
    }

    /** True while the player's inventory holds a kit being arranged: the lobby hotbar waits. */
    boolean isEditing(Player player) {
        return editing.containsKey(player.getUniqueId());
    }

    /** The book: the modes whose kit can be arranged, and the Hypixel shop. */
    void openModes(Player player) {
        openModes(player, 0);
    }

    /** The list of kits at this page. */
    void openModes(Player player, int page) {
        List<String> ids = new ArrayList<>();
        Map<String, String> categoryOf = new HashMap<>();
        Map<String, String> nameOf = new HashMap<>();
        for (ModeDisplay display : modes()) {
            ids.add(display.mode().id());
            categoryOf.put(display.mode().id(), display.category());
            nameOf.put(display.mode().id(), display.mode().name());
        }
        Map<String, ModeCategory> categories = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (ModeCategory category : settings.categories()) {
            categories.put(category.id(), category);
            order.add(category.id());
        }
        List<Row> rows = rows(ids, categoryOf, nameOf, order);
        int pages = pages(rows);
        int at = Math.max(0, Math.min(page, pages - 1));
        List<Row> shown = page(rows, at);
        Map<Integer, String> slots = new HashMap<>();
        Holder holder = new Holder(null, slots, at);
        int size = 9 * (shown.size() + 1);
        Inventory inventory = Bukkit.createInventory(holder, size, pages > 1 ? TITLE + " (" + (at + 1) + "/" + pages + ")" : TITLE);
        holder.inventory = inventory;
        for (int row = 0; row < shown.size(); row++) {
            Row each = shown.get(row);
            if (each.first || row == 0) {
                inventory.setItem(row * 9, label(each.category == null ? null : categories.get(each.category), order.isEmpty()));
            }
            for (int i = 0; i < each.modes.size(); i++) {
                String modeId = each.modes.get(i);
                int slot = row * 9 + 1 + i;
                slots.put(slot, modeId);
                inventory.setItem(slot, kitItem(player, settings.display(modeId)));
            }
        }
        int last = size - 9;
        if (at > 0) {
            inventory.setItem(last + PREVIOUS_COLUMN, item(Material.ARROW, 0, ChatColor.YELLOW + "Previous page",
                    ChatColor.GRAY + "Page " + at + " of " + pages));
        }
        if (at < pages - 1) {
            inventory.setItem(last + NEXT_COLUMN, item(Material.ARROW, 0, ChatColor.YELLOW + "Next page",
                    ChatColor.GRAY + "Page " + (at + 2) + " of " + pages));
        }
        HypixelProfile imported = hypixel.get(player.getUniqueId());
        inventory.setItem(last + IMPORT_COLUMN, hypixel.enabled()
                ? item(Material.EMERALD, 0, ChatColor.GREEN + "Import your Hypixel shop",
                        ChatColor.GRAY + "One click: copies the quick buy",
                        ChatColor.GRAY + "and the hotbar manager of your",
                        ChatColor.GRAY + "Hypixel account, for the Bed Wars",
                        ChatColor.GRAY + "modes and Fast Shop.", "",
                        ChatColor.GRAY + "Another account: " + ChatColor.WHITE + "/hypixel import <name>")
                : item(Material.EMERALD, 0, ChatColor.GRAY + "Import your Hypixel shop",
                        ChatColor.RED + "Not set up on this server yet."));
        inventory.setItem(last + HELP_COLUMN, item(Material.BOOK, 0, ChatColor.GOLD + "How it works",
                ChatColor.GRAY + "Click a kit: its items come into",
                ChatColor.GRAY + "your inventory. Move them where",
                ChatColor.GRAY + "you want them, then " + ChatColor.GREEN + "Save" + ChatColor.GRAY + ":",
                ChatColor.GRAY + "your next games give them this way.", "",
                ChatColor.GRAY + "In a game too: move the items of",
                ChatColor.GRAY + "your kit, close your inventory,",
                ChatColor.GRAY + "and the new layout is kept."));
        inventory.setItem(last + SHOP_COLUMN, item(Material.CHEST, 0, ChatColor.GOLD + "Your Hypixel shop",
                imported == null ? ChatColor.GRAY + "Nothing imported yet."
                        : ChatColor.GRAY + "The quick buy of " + ChatColor.WHITE + imported.name()
                                + ChatColor.GRAY + ": " + imported.items() + " items.",
                "", Brand.ACCENT + "Click to see it"));
        player.openInventory(inventory);
    }

    /**
     * The label of a row: its category's icon and name; the modes of no category: "Other kits", or
     * "Kits" when the selector has no categories.
     */
    private static ItemStack label(ModeCategory category, boolean noCategories) {
        if (category == null) {
            return item(Material.CHEST, 0, ChatColor.WHITE + "" + ChatColor.BOLD + (noCategories ? "Kits" : "Other kits"));
        }
        String slogan = category.slogan();
        return slogan == null ? item(category.icon(), 0, category.name())
                : item(category.icon(), 0, category.name(), slogan);
    }

    /** A kit of the list: the mode, the other modes that play its kit, the player's layout or not. */
    private ItemStack kitItem(Player player, ModeDisplay display) {
        List<String> lore = new ArrayList<>();
        List<String> others = playedBy(display.mode().id());
        if (!others.isEmpty()) {
            lore.addAll(wrap("Also for: " + String.join(", ", others) + ".", 32, ChatColor.GRAY.toString()));
            lore.add("");
        }
        boolean arranged = layouts.get(player.getUniqueId(), display.mode().id()) != null;
        lore.add(arranged ? ChatColor.GREEN + "Your layout is saved." : ChatColor.GRAY + "The default layout.");
        lore.add("");
        lore.add(Brand.ACCENT + "Click to arrange this kit");
        return item(display.icon(), 0, ChatColor.RESET + display.mode().name(), lore.toArray(new String[0]));
    }

    /** Puts the mode's kit in the player's inventory, as they arranged it, under the editor chest. */
    void edit(Player player, String modeId) {
        edit(player, modeId, 0);
    }

    /** The same, from this page of the list: Save and Cancel go back to it. */
    void edit(Player player, String modeId, int page) {
        ModeDisplay display = settings.display(modeId);
        if (display == null || display.kit() == null || !state.isProtected(player)) {
            return;
        }
        // Before the inventory changes: the lobby hotbar must not come back over the kit.
        editing.put(player.getUniqueId(), modeId);
        fill(player, display.kit().arrange(layouts.get(player.getUniqueId(), modeId)));
        Holder holder = new Holder(modeId, Collections.<Integer, String>emptyMap(), page);
        String title = "Kit: " + ChatColor.stripColor(display.mode().name());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title.length() > 32 ? title.substring(0, 32) : title);
        holder.inventory = inventory;
        inventory.setItem(INFO, item(Material.BOOK, 0, ChatColor.GOLD + ChatColor.stripColor(display.mode().name()),
                ChatColor.GRAY + "Move the items of your inventory",
                ChatColor.GRAY + "where you want them, then save:",
                ChatColor.GRAY + "your next games of this mode",
                ChatColor.GRAY + "give them this way."));
        inventory.setItem(SAVE, item(Material.WOOL, 5, ChatColor.GREEN + "" + ChatColor.BOLD + "Save",
                ChatColor.GRAY + "Back to the list of kits.",
                ChatColor.GRAY + "Closing this menu saves too."));
        inventory.setItem(RESET, item(Material.WOOL, 4, ChatColor.WHITE + "Reset",
                ChatColor.GRAY + "Puts every item back", ChatColor.GRAY + "where the kit puts it."));
        inventory.setItem(CANCEL, item(Material.WOOL, 14, ChatColor.RED + "Cancel",
                ChatColor.GRAY + "Keeps your last saved layout,",
                ChatColor.GRAY + "back to the list of kits."));
        player.openInventory(inventory);
    }

    /** A click while one of our chests is open (the lobby listener sends it here). */
    void click(InventoryClickEvent event, Holder holder) {
        if (!(event.getWhoClicked() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        final Player player = (Player) event.getWhoClicked();
        int raw = event.getRawSlot();
        int top = event.getView().getTopInventory().getSize();
        if (holder.modeId == null) {
            event.setCancelled(true);
            if (raw == top - 9 + IMPORT_COLUMN) {
                closeLater(player);
                hypixel.importFor(player, null);
                return;
            }
            if (raw == top - 9 + SHOP_COLUMN) {
                final HypixelProfile imported = hypixel.get(player.getUniqueId());
                if (imported == null) {
                    player.sendMessage(ChatColor.GRAY + "You have not imported your Hypixel shop yet: click the"
                            + " emerald, or /hypixel import <name>.");
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == holder) {
                        HypixelMenu.open(player, imported);
                    }
                });
                return;
            }
            final int turn = raw == top - 9 + PREVIOUS_COLUMN ? -1 : raw == top - 9 + NEXT_COLUMN ? 1 : 0;
            if (turn != 0 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == holder) {
                        openModes(player, holder.page + turn);
                    }
                });
                return;
            }
            final String modeId = holder.modes.get(raw);
            if (modeId != null) {
                // Not from inside the click: the next tick opens the editor.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == holder) {
                        edit(player, modeId, holder.page);
                    }
                });
            }
            return;
        }
        if (!holder.modeId.equals(editing.get(player.getUniqueId()))) {
            event.setCancelled(true);
            return;
        }
        if (raw >= 0 && raw < top) {
            event.setCancelled(true);
            if (raw == SAVE) {
                backToList.put(player.getUniqueId(), holder.page);
                closeLater(player);
            } else if (raw == RESET) {
                ModeDisplay display = settings.display(holder.modeId);
                player.setItemOnCursor(null);
                fill(player, display.kit().arrange(Kit.defaultLayout()));
                player.sendMessage(ChatColor.GRAY + "The kit is back to its default layout: save to keep it.");
            } else if (raw == CANCEL) {
                editing.remove(player.getUniqueId());
                backToList.put(player.getUniqueId(), holder.page);
                player.sendMessage(ChatColor.GRAY + "Nothing changed: your kit stays as it was.");
                closeLater(player);
            }
            return;
        }
        if (raw < 0 || !movesOneStack(event)) {
            event.setCancelled(true);
        }
    }

    /**
     * Moves that keep every stack whole, in the player's inventory: picking a stack up, putting it
     * down in an empty slot, swapping it with another, or with a hotbar slot (a number key).
     */
    static boolean movesOneStack(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        boolean emptySlot = current == null || current.getType() == Material.AIR;
        switch (event.getAction()) {
            case PICKUP_ALL:
            case SWAP_WITH_CURSOR:
            case HOTBAR_SWAP:
            case NOTHING:
                return true;
            case PICKUP_HALF:
                // A right click on a single item takes all of it.
                return !emptySlot && current.getAmount() == 1;
            case PLACE_ALL:
                return emptySlot;
            case PLACE_ONE:
                return emptySlot && event.getCursor() != null && event.getCursor().getAmount() == 1;
            default:
                return false;
        }
    }

    /**
     * A drag while one of our chests is open. The client often sends a short click as a drag over
     * one slot: that one is let through when it puts the whole stack in an empty slot of the
     * inventory.
     */
    void drag(InventoryDragEvent event, Holder holder) {
        boolean whole = false;
        if (holder.modeId != null && event.getWhoClicked() instanceof Player
                && holder.modeId.equals(editing.get(event.getWhoClicked().getUniqueId()))
                && event.getRawSlots().size() == 1 && event.getType() == DragType.EVEN) {
            int raw = event.getRawSlots().iterator().next();
            ItemStack there = event.getView().getItem(raw);
            ItemStack after = event.getCursor();
            whole = raw >= event.getView().getTopInventory().getSize()
                    && (there == null || there.getType() == Material.AIR)
                    && (after == null || after.getType() == Material.AIR || after.getAmount() == 0);
        }
        if (!whole) {
            event.setCancelled(true);
        }
    }

    /** A game gave this player this kit, that it makes itself: their arrangement of it can be kept. */
    void learn(UUID player, String modeId, Kit kit) {
        codeKits.computeIfAbsent(player, id -> new HashMap<>()).put(modeId, kit);
        gave(player, modeId);
    }

    /** A game gave this player the kit of this mode (or mode/role). */
    void gave(UUID player, String modeId) {
        given.put(player, modeId);
    }

    /** The mode's kit: the one of config.yml, else the one its game gave this player last; null if none. */
    private Kit kitOf(UUID player, String modeId) {
        ModeDisplay display = settings.display(modeId);
        if (display != null && display.kit() != null) {
            return display.kit();
        }
        Map<String, Kit> learnt = codeKits.get(player);
        return learnt == null ? null : learnt.get(modeId);
    }

    /** The kit a player in a game of this mode holds: the last one it gave them (a role's), else the mode's. */
    static String kitKey(String gameMode, String lastGiven) {
        return lastGiven != null && (lastGiven.equals(gameMode) || lastGiven.startsWith(gameMode + "/")) ? lastGiven : gameMode;
    }

    /**
     * Where the player put the kit's items in a game, if it is their kit: {@value #FOUND_SHARE} of
     * its stacks or more found (a stack may hold fewer items: blocks placed); an item no longer there
     * (a potion drunk) keeps where it went before. Null for an inventory that is not this kit.
     */
    static int[] arrangedInGame(Kit kit, KitItem[] found, int[] saved) {
        int[] layout = kit.layoutOf(found, true);
        int stacks = 0;
        int there = 0;
        for (int slot = 0; slot < Kit.SLOTS; slot++) {
            if (kit.item(slot) != null) {
                stacks++;
                if (layout[slot] >= 0) {
                    there++;
                }
            }
        }
        if (stacks == 0 || there < Math.max(1, Math.ceil(FOUND_SHARE * stacks))) {
            return null;
        }
        boolean[] taken = new boolean[Kit.SLOTS];
        for (int target : layout) {
            if (target >= 0) {
                taken[target] = true;
            }
        }
        for (int slot = 0; slot < Kit.SLOTS; slot++) {
            if (kit.item(slot) != null && layout[slot] < 0) {
                int before = saved != null && slot < saved.length ? saved[slot] : -1;
                int keep = before >= 0 ? before : slot;
                if (!taken[keep]) {
                    layout[slot] = keep;
                    taken[keep] = true;
                }
            }
        }
        return layout;
    }

    /**
     * A player in a game closed their inventory: where they put the items of the kit the game gave
     * them (the mode's, or a role's: the defenders of the invis practice) is kept, even with a few
     * blocks placed or a potion drunk, whenever it changed. Nothing for an inventory that is not
     * that kit (another role's items).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onArranged(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        UUID id = player.getUniqueId();
        String gameMode = state.gameOf(id);
        if (gameMode == null || editing.containsKey(id)) {
            return;
        }
        // the sizes of one game keep one layout, the one of the mode holding their kit (kit-de:)
        String base = settings.kitMode(gameMode);
        String modeId = kitKey(base, given.get(id));
        Kit kit = kitOf(id, modeId);
        if (kit == null) {
            return;
        }
        KitItem[] found = new KitItem[Kit.SLOTS];
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < Kit.SLOTS; slot++) {
            found[slot] = KitItem.of(inventory.getItem(slot));
        }
        int[] saved = layouts.get(id, modeId);
        int[] layout = arrangedInGame(kit, found, saved);
        if (layout == null || Arrays.equals(moves(layout), saved == null ? moves(Kit.defaultLayout()) : moves(saved))) {
            return;
        }
        layouts.set(id, modeId, layout);
        ModeDisplay display = settings.display(base);
        String name = display == null ? base : ChatColor.stripColor(display.mode().name());
        String role = modeId.equals(base) ? "" : " (" + modeId.substring(base.length() + 1) + ")";
        player.sendMessage(ChatColor.GREEN + "Kit layout saved for " + name + role + ": your next games give it this way.");
    }

    /** The layout's moves only: an item in its own slot counts as not moved. */
    static int[] moves(int[] layout) {
        int[] moves = new int[Kit.SLOTS];
        for (int slot = 0; slot < Kit.SLOTS; slot++) {
            int target = slot < layout.length ? layout[slot] : -1;
            moves[slot] = target == slot ? -1 : target;
        }
        return moves;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Holder) || ((Holder) holder).modeId == null || !(event.getPlayer() instanceof Player)) {
            return;
        }
        final Player player = (Player) event.getPlayer();
        String modeId = editing.remove(player.getUniqueId());
        if (modeId != null) {
            save(player, modeId);
        }
        player.setItemOnCursor(null);
        final Integer page = backToList.remove(player.getUniqueId());
        // The lobby hotbar comes back, unless a game started meanwhile (it gives its own items); after
        // Save or Cancel, the list of kits too.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !isEditing(player) && state.isProtected(player)) {
                lobbyHotbar.accept(player);
                if (page != null) {
                    openModes(player, page);
                }
            }
        });
    }

    /** Before the plugin stops: the kits being arranged are saved. */
    void closeAll() {
        for (Map.Entry<UUID, String> entry : new ArrayList<>(editing.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            editing.remove(entry.getKey());
            if (player != null) {
                save(player, entry.getValue());
                player.setItemOnCursor(null);
                player.closeInventory();
            }
        }
    }

    void forget(UUID player) {
        editing.remove(player);
        backToList.remove(player);
        given.remove(player);
        codeKits.remove(player);
    }

    /** Where the player put each item of the kit, kept for their next games. */
    private void save(Player player, String modeId) {
        ModeDisplay display = settings.display(modeId);
        if (display == null || display.kit() == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        KitItem[] edited = new KitItem[Kit.SLOTS];
        for (int slot = 0; slot < Kit.SLOTS; slot++) {
            edited[slot] = KitItem.of(inventory.getItem(slot));
        }
        KitItem held = KitItem.of(player.getItemOnCursor());
        if (held != null) {
            // Still on the cursor: it goes to the first free slot, as the inventory would take it back.
            int free = Arrays.asList(edited).indexOf(null);
            if (free >= 0) {
                edited[free] = held;
            }
        }
        layouts.set(player.getUniqueId(), modeId, display.kit().layoutOf(edited));
        player.sendMessage(ChatColor.GREEN + "Kit saved for " + ChatColor.stripColor(display.mode().name())
                + ": your next games give it this way.");
    }

    private void closeLater(final Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                player.closeInventory();
            }
        });
    }

    private static void fill(Player player, KitItem[] items) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        for (int slot = 0; slot < Kit.SLOTS; slot++) {
            inventory.setItem(slot, items[slot] == null ? null : items[slot].toItem());
        }
        player.updateInventory();
    }

    private static ItemStack item(Material material, int data, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1, (short) data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}
