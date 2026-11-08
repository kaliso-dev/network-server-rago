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
 * a kit; clicking one puts that kit in the player's inventory, as they arranged it last time, with
 * a small chest above it: Save, Reset (the kit's own layout) and Cancel. The player moves the items
 * of their inventory where they want them; closing the chest saves too. The items stay the
 * server's: they can be moved, not split, merged, dropped or put in the chest. The lobby hotbar
 * comes back once the chest is closed. The bottom row of the list copies the player's Bed Wars
 * shop and hotbar manager from Hypixel in one click, and shows them. Main thread only.
 */
final class KitEditor implements Listener {

    static final String TITLE = "Kit editor";
    static final int SIZE = 27;
    static final int INFO = 4;
    static final int SAVE = 11;
    static final int RESET = 13;
    static final int CANCEL = 15;
    /** The list of modes is one chest of six rows at most, the last one for the Hypixel buttons. */
    static final int MAX_MODES = 45;
    /** In the last row of the list: copy the Hypixel shop, and show it. */
    static final int IMPORT_COLUMN = 3;
    static final int SHOP_COLUMN = 5;

    /** Marks our chests: the list of modes (no mode), or the editor of one mode's kit. */
    static final class Holder implements InventoryHolder {
        private final String modeId;
        private final List<String> modes;
        private Inventory inventory;

        Holder(String modeId, List<String> modes) {
            this.modeId = modeId;
            this.modes = modes;
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
            if (display.kit() != null && modes.size() < MAX_MODES) {
                modes.add(display);
            }
        }
        return modes;
    }

    /** True while the player's inventory holds a kit being arranged: the lobby hotbar waits. */
    boolean isEditing(Player player) {
        return editing.containsKey(player.getUniqueId());
    }

    /** The book: the modes whose kit can be arranged, and the Hypixel shop. */
    void openModes(Player player) {
        List<ModeDisplay> modes = modes();
        List<String> ids = new ArrayList<>();
        for (ModeDisplay display : modes) {
            ids.add(display.mode().id());
        }
        Holder holder = new Holder(null, Collections.unmodifiableList(ids));
        int size = 9 * ((modes.size() + 8) / 9 + 1);
        Inventory inventory = Bukkit.createInventory(holder, size, TITLE);
        holder.inventory = inventory;
        HypixelProfile imported = hypixel.get(player.getUniqueId());
        inventory.setItem(size - 9 + IMPORT_COLUMN, hypixel.enabled()
                ? item(Material.EMERALD, 0, ChatColor.GREEN + "Import your Hypixel shop",
                        ChatColor.GRAY + "One click: copies the quick buy",
                        ChatColor.GRAY + "and the hotbar manager of your",
                        ChatColor.GRAY + "Hypixel account, for the Bed Wars",
                        ChatColor.GRAY + "modes and Fast Shop.", "",
                        ChatColor.GRAY + "Another account: " + ChatColor.WHITE + "/hypixel import <name>")
                : item(Material.EMERALD, 0, ChatColor.GRAY + "Import your Hypixel shop",
                        ChatColor.RED + "Not set up on this server yet."));
        inventory.setItem(size - 9 + SHOP_COLUMN, item(Material.CHEST, 0, ChatColor.GOLD + "Your Hypixel shop",
                imported == null ? ChatColor.GRAY + "Nothing imported yet."
                        : ChatColor.GRAY + "The quick buy of " + ChatColor.WHITE + imported.name()
                                + ChatColor.GRAY + ": " + imported.items() + " items.",
                "", ChatColor.YELLOW + "Click to see it"));
        for (int slot = 0; slot < modes.size(); slot++) {
            ModeDisplay display = modes.get(slot);
            boolean arranged = layouts.get(player.getUniqueId(), display.mode().id()) != null;
            inventory.setItem(slot, item(display.icon(), 0, ChatColor.RESET + display.mode().name(),
                    arranged ? ChatColor.GREEN + "Your layout is saved." : ChatColor.GRAY + "The default layout.",
                    "", ChatColor.YELLOW + "Click to arrange this kit"));
        }
        player.openInventory(inventory);
    }

    /** Puts the mode's kit in the player's inventory, as they arranged it, under the editor chest. */
    void edit(Player player, String modeId) {
        ModeDisplay display = settings.display(modeId);
        if (display == null || display.kit() == null || !state.isProtected(player)) {
            return;
        }
        // Before the inventory changes: the lobby hotbar must not come back over the kit.
        editing.put(player.getUniqueId(), modeId);
        fill(player, display.kit().arrange(layouts.get(player.getUniqueId(), modeId)));
        Holder holder = new Holder(modeId, Collections.<String>emptyList());
        String title = "Kit: " + ChatColor.stripColor(display.mode().name());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title.length() > 32 ? title.substring(0, 32) : title);
        holder.inventory = inventory;
        inventory.setItem(INFO, item(Material.BOOK, 0, ChatColor.GOLD + ChatColor.stripColor(display.mode().name()),
                ChatColor.GRAY + "Move the items of your inventory",
                ChatColor.GRAY + "where you want them, then save:",
                ChatColor.GRAY + "your next games of this mode",
                ChatColor.GRAY + "give them this way."));
        inventory.setItem(SAVE, item(Material.WOOL, 5, ChatColor.GREEN + "" + ChatColor.BOLD + "Save",
                ChatColor.GRAY + "Closing this menu saves too."));
        inventory.setItem(RESET, item(Material.WOOL, 4, ChatColor.YELLOW + "Reset",
                ChatColor.GRAY + "Puts every item back", ChatColor.GRAY + "where the kit puts it."));
        inventory.setItem(CANCEL, item(Material.WOOL, 14, ChatColor.RED + "Cancel",
                ChatColor.GRAY + "Keeps your last saved layout."));
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
            if (raw >= 0 && raw < holder.modes.size()) {
                final String modeId = holder.modes.get(raw);
                // Not from inside the click: the next tick opens the editor.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == holder) {
                        edit(player, modeId);
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
                closeLater(player);
            } else if (raw == RESET) {
                ModeDisplay display = settings.display(holder.modeId);
                player.setItemOnCursor(null);
                fill(player, display.kit().arrange(Kit.defaultLayout()));
                player.sendMessage(ChatColor.YELLOW + "The kit is back to its default layout: save to keep it.");
            } else if (raw == CANCEL) {
                editing.remove(player.getUniqueId());
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
        // The lobby hotbar comes back, unless a game started meanwhile (it gives its own items).
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !isEditing(player) && state.isProtected(player)) {
                lobbyHotbar.accept(player);
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
