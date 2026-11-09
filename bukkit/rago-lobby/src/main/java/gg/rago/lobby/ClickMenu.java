package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * A chest menu whose items each run an action when clicked: the shop, the profile, the friends,
 * the settings. Nothing in it can be taken ({@link LobbyListener} cancels every click). Main thread.
 */
final class ClickMenu implements InventoryHolder {

    /** What a click on an item does. */
    interface Action {
        void click(Player player, boolean right);
    }

    /** The title of a chest: 32 characters at most in 1.8. */
    static final int MAX_TITLE = 32;

    private final Inventory inventory;
    private final Map<Integer, Action> actions = new HashMap<>();

    ClickMenu(int rows, String title) {
        String shown = title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE) : title;
        this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, shown);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Puts an item in the slot; a click on it runs the action (null: nothing). */
    ClickMenu set(int slot, ItemStack item, Action action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return this;
        }
        inventory.setItem(slot, item);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
        return this;
    }

    int size() {
        return inventory.getSize();
    }

    void open(Player player) {
        player.openInventory(inventory);
    }

    void click(Player player, int slot, boolean right) {
        Action action = actions.get(slot);
        if (action != null) {
            action.click(player, right);
        }
    }

    // --- Items ----------------------------------------------------------------------------------

    static ItemStack item(Material material, String name, String... lore) {
        return item(material, 0, name, Arrays.asList(lore));
    }

    static ItemStack item(Material material, int data, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, 1, (short) data);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + name);
            List<String> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(line.isEmpty() || line.charAt(0) == ChatColor.COLOR_CHAR ? line : ChatColor.GRAY + line);
            }
            meta.setLore(lines);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** A player's head. */
    @SuppressWarnings("deprecation") // SkullMeta#setOwner(String) is the 1.8.8 way to show a player's head
    static ItemStack head(String owner, String name, List<String> lore) {
        ItemStack head = item(Material.SKULL_ITEM, 3, name, lore);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwner(owner);
            head.setItemMeta(meta);
        }
        return head;
    }

    /** The "Go back" arrow. */
    static ItemStack back(String to) {
        return item(Material.ARROW, ChatColor.GREEN + "Go back", "To " + to);
    }

    /** A grey pane that fills the borders. */
    static ItemStack pane() {
        return item(Material.STAINED_GLASS_PANE, 7, " ", new ArrayList<String>());
    }
}
