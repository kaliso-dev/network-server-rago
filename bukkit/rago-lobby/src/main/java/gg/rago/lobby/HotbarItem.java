package gg.rago.lobby;

import java.util.Arrays;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The items of the lobby hotbar. 1.8.8 has no persistent data on items, so an item is recognised by
 * its material and its exact name, which players cannot give to an item in the lobby (no anvil, no
 * creative mode outside the build mode).
 */
enum HotbarItem {

    SELECTOR(0, Material.COMPASS, 0, ChatColor.GREEN + "Game modes " + ChatColor.GRAY + "(right click)",
            "Pick a mode and join", "its queue."),
    LEAVE_QUEUE(4, Material.INK_SACK, 1, ChatColor.RED + "Leave the queue " + ChatColor.GRAY + "(right click)",
            "You leave the queue."),
    HIDE_PLAYERS(8, Material.INK_SACK, 10, ChatColor.GREEN + "Players: shown " + ChatColor.GRAY + "(right click)",
            "Right click to hide", "the other players."),
    SHOW_PLAYERS(8, Material.INK_SACK, 8, ChatColor.GRAY + "Players: hidden " + ChatColor.GRAY + "(right click)",
            "Right click to show", "the other players again.");

    private final int slot;
    private final Material material;
    private final short data;
    private final String name;
    private final String[] lore;

    HotbarItem(int slot, Material material, int data, String name, String... lore) {
        this.slot = slot;
        this.material = material;
        this.data = (short) data;
        this.name = name;
        this.lore = lore;
    }

    int slot() {
        return slot;
    }

    ItemStack create() {
        ItemStack item = new ItemStack(material, 1, data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        String[] lines = new String[lore.length];
        for (int i = 0; i < lore.length; i++) {
            lines[i] = ChatColor.GRAY + lore[i];
        }
        meta.setLore(Arrays.asList(lines));
        item.setItemMeta(meta);
        return item;
    }

    /** The lobby item this is, or null. */
    static HotbarItem of(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return null;
        }
        String displayName = item.getItemMeta().getDisplayName();
        for (HotbarItem hotbar : values()) {
            if (hotbar.material == item.getType() && hotbar.name.equals(displayName)) {
                return hotbar;
            }
        }
        return null;
    }
}
