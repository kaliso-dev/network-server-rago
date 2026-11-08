package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The mode selector, a chest menu. With categories in config.yml, the compass first shows one item
 * per category (a zone of the lobby); clicking one opens that category's modes, with a back
 * button in the bottom row. Without categories, every mode is shown at once. Modes are seven per
 * row, framed by an empty row above and below. Clicking a playable mode joins its queue; clicking
 * the mode one waits for leaves it. Open menus are refreshed every second, for the queue counts.
 */
final class ModeMenu {

    static final String TITLE = "Modes de jeu";
    private static final int PER_ROW = 7;

    /**
     * Marks our inventories: a click in one of them is a click in the selector. The view is the
     * category menu (CATEGORIES), one category's modes (its id), an NPC's modes (NPC + its
     * point), or every mode (null).
     */
    static final class Holder implements InventoryHolder {
        static final String CATEGORIES = "";
        static final String NPC = "pnj:";
        private Inventory inventory;
        private final String view;

        Holder(String view) {
            this.view = view;
        }

        String view() {
            return view;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final LobbySettings settings;
    private final QueueController queues;

    ModeMenu(LobbySettings settings, QueueController queues) {
        this.settings = settings;
        this.queues = queues;
    }

    /** The compass: the categories if there are any, else every mode. */
    void open(Player player) {
        if (settings.categories().isEmpty()) {
            show(player, null, 2 + rowsFor(settings.modes().size()), TITLE);
        } else {
            show(player, Holder.CATEGORIES, 3, TITLE);
        }
    }

    /** One category's modes, from the category menu or later from a lobby NPC. False if unknown. */
    boolean openCategory(Player player, String categoryId) {
        ModeCategory category = settings.category(categoryId);
        if (category == null) {
            return false;
        }
        String title = ChatColor.stripColor(category.name());
        show(player, categoryId, 3 + rowsFor(settings.modesIn(categoryId).size()),
                title.length() > 32 ? title.substring(0, 32) : title);
        return true;
    }

    /** The modes of a lobby NPC that has several. */
    void openNpc(Player player, LobbyNpc npc) {
        String title = ChatColor.stripColor(npc.name());
        show(player, Holder.NPC + npc.point(), 2 + rowsFor(npc.modeIds().size()),
                title.length() > 32 ? title.substring(0, 32) : title);
    }

    /** The modes a view shows. */
    private List<ModeDisplay> modesFor(String view) {
        if (view != null && view.startsWith(Holder.NPC)) {
            List<ModeDisplay> result = new ArrayList<>();
            LobbyNpc npc = settings.npc(view.substring(Holder.NPC.length()));
            if (npc != null) {
                for (String id : npc.modeIds()) {
                    result.add(settings.display(id));
                }
            }
            return result;
        }
        return settings.modesIn(view);
    }

    /** Whether a view has the back button: only a category's modes. */
    private static boolean hasBack(String view) {
        return view != null && !view.startsWith(Holder.NPC);
    }

    private static int rowsFor(int modes) {
        return Math.max(1, (modes + PER_ROW - 1) / PER_ROW);
    }

    private void show(Player player, String view, int rows, String title) {
        Holder holder = new Holder(view);
        Inventory inventory = Bukkit.createInventory(holder, Math.min(6, rows) * 9, title);
        holder.inventory = inventory;
        fill(inventory, player);
        player.openInventory(inventory);
    }

    /** The slot of the n-th mode: rows 1 and after, columns 1 to 7. */
    static int slotOf(int index) {
        return (1 + index / PER_ROW) * 9 + 1 + index % PER_ROW;
    }

    /** The slot of the n-th of {@code count} categories, centred in the middle row. */
    static int categorySlot(int index, int count) {
        return 9 + (9 - count) / 2 + index;
    }

    /** The back button: middle of the bottom row. */
    static int backSlot(int size) {
        return size - 5;
    }

    /** Clicked in our menu: open a category, go back, or join or leave a mode's queue. */
    void click(Player player, Holder holder, int slot) {
        String view = holder.view();
        if (Holder.CATEGORIES.equals(view)) {
            List<ModeCategory> categories = settings.categories();
            for (int i = 0; i < categories.size(); i++) {
                if (categorySlot(i, categories.size()) == slot) {
                    openCategory(player, categories.get(i).id());
                    return;
                }
            }
            return;
        }
        if (hasBack(view) && slot == backSlot(holder.getInventory().getSize())) {
            open(player);
            return;
        }
        List<ModeDisplay> modes = modesFor(view);
        GameMode mode = null;
        for (int i = 0; i < modes.size(); i++) {
            if (slotOf(i) == slot) {
                mode = modes.get(i).mode();
            }
        }
        if (mode == null) {
            return;
        }
        toggle(player, mode);
    }

    /** Joins the mode's queue, or leaves it if the player already waits for it. */
    void toggle(Player player, GameMode mode) {
        GameMode current = queues.queueOf(player);
        if (mode.equals(current)) {
            queues.leave(player, true);
        } else {
            queues.join(player, mode);
        }
        if (queues.queueOf(player) != null) {
            player.closeInventory();
        }
    }

    /** Refreshes the counts of every open selector. */
    void refreshOpen() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof Holder) {
                fill(top, player);
            }
        }
    }

    private void fill(Inventory inventory, HumanEntity viewer) {
        String view = ((Holder) inventory.getHolder()).view();
        GameMode current = viewer instanceof Player ? queues.queueOf((Player) viewer) : null;
        if (Holder.CATEGORIES.equals(view)) {
            List<ModeCategory> categories = settings.categories();
            for (int i = 0; i < categories.size(); i++) {
                inventory.setItem(categorySlot(i, categories.size()), icon(categories.get(i), current));
            }
            return;
        }
        List<ModeDisplay> modes = modesFor(view);
        int last = hasBack(view) ? backSlot(inventory.getSize()) - 4 : inventory.getSize();
        for (int i = 0; i < modes.size() && slotOf(i) < last; i++) {
            inventory.setItem(slotOf(i), icon(modes.get(i), modes.get(i).mode().equals(current)));
        }
        if (hasBack(view)) {
            ItemStack back = new ItemStack(Material.ARROW);
            ItemMeta meta = back.getItemMeta();
            meta.setDisplayName(ChatColor.GRAY + "Retour aux catégories");
            back.setItemMeta(meta);
            inventory.setItem(backSlot(inventory.getSize()), back);
        }
    }

    private ItemStack icon(ModeCategory category, GameMode current) {
        ItemStack item = new ItemStack(category.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(category.name());
        List<String> lore = new ArrayList<>(category.description());
        List<ModeDisplay> modes = settings.modesIn(category.id());
        int waiting = 0;
        boolean here = false;
        for (ModeDisplay display : modes) {
            if (queues.isPlayable(display.mode().id())) {
                waiting += queues.waiting(display.mode().id());
            }
            here |= display.mode().equals(current);
        }
        lore.add("");
        lore.add(ChatColor.GRAY + "" + modes.size() + " modes, " + ChatColor.WHITE + waiting + ChatColor.GRAY + " en file");
        if (here) {
            lore.add(ChatColor.YELLOW + "Tu es dans une file de cette catégorie");
        }
        lore.add(ChatColor.GREEN + "Clic pour voir les modes");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack icon(ModeDisplay display, boolean waitingHere) {
        GameMode mode = display.mode();
        ItemStack item = new ItemStack(display.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(mode.name());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + mode.format() + (mode.ranked() ? " · classé" : ""));
        lore.add("");
        lore.addAll(display.description());
        lore.add("");
        if (!queues.isPlayable(mode.id())) {
            lore.add(ChatColor.GRAY + "Bientôt disponible");
        } else {
            lore.add(ChatColor.GRAY + "En file : " + ChatColor.WHITE + queues.waiting(mode.id()));
            lore.add(waitingHere ? ChatColor.YELLOW + "Tu es dans cette file : clic pour la quitter"
                    : ChatColor.GREEN + "Clic pour rejoindre la file");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
