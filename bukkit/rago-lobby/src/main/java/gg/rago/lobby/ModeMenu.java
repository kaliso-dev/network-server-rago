package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The mode selector: a chest menu with one item per mode, seven per row, framed by an empty row
 * above and below. Clicking a playable mode joins its queue; clicking the mode one waits for
 * leaves it. Open menus are refreshed every second, for the queue counts.
 */
final class ModeMenu {

    static final String TITLE = "Modes de jeu";
    private static final int PER_ROW = 7;

    /** Marks our inventories: a click in one of them is a click in the selector. */
    static final class Holder implements InventoryHolder {
        private Inventory inventory;

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

    void open(Player player) {
        int modes = settings.modes().size();
        int rows = Math.min(6, 2 + (modes + PER_ROW - 1) / PER_ROW);
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, TITLE);
        holder.inventory = inventory;
        fill(inventory, player);
        player.openInventory(inventory);
    }

    /** The slot of the n-th mode: rows 1 and after, columns 1 to 7. */
    static int slotOf(int index) {
        return (1 + index / PER_ROW) * 9 + 1 + index % PER_ROW;
    }

    /** The mode shown in this slot, or null. */
    GameMode modeAt(int slot) {
        List<ModeDisplay> modes = settings.modes();
        for (int i = 0; i < modes.size(); i++) {
            if (slotOf(i) == slot) {
                return modes.get(i).mode();
            }
        }
        return null;
    }

    /** Clicked in our menu: join or leave the mode's queue. */
    void click(Player player, int slot) {
        GameMode mode = modeAt(slot);
        if (mode == null) {
            return;
        }
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
        List<ModeDisplay> modes = settings.modes();
        GameMode current = viewer instanceof Player ? queues.queueOf((Player) viewer) : null;
        for (int i = 0; i < modes.size() && slotOf(i) < inventory.getSize(); i++) {
            inventory.setItem(slotOf(i), icon(modes.get(i), modes.get(i).mode().equals(current)));
        }
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
