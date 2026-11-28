package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The menu of the maps of Block Method 1v1, from the map of the player's hotbar: one item per map
 * of the mode, the one they play on lit; a click on another goes on there. Main thread only.
 */
final class BlockMethodMenu {

    static final String TITLE = "Choose your map";

    private BlockMethodMenu() {
    }

    static final class Holder implements InventoryHolder {
        final BlockMethodGame game;
        final List<String> worlds;
        private Inventory inventory;

        Holder(BlockMethodGame game, List<String> worlds) {
            this.game = game;
            this.worlds = worlds;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** The rows a menu of this many maps needs: one at least, six at most. */
    static int rows(int maps) {
        return Math.max(1, Math.min(6, (maps + 8) / 9));
    }

    static void open(Player player, BlockMethodGame game, List<BedwarsMap> maps, String current) {
        List<String> worlds = new ArrayList<>();
        for (BedwarsMap map : maps) {
            worlds.add(map.world());
        }
        int size = rows(worlds.size()) * 9;
        Holder holder = new Holder(game, worlds);
        Inventory inventory = Bukkit.createInventory(holder, size, TITLE);
        holder.inventory = inventory;
        for (int i = 0; i < Math.min(worlds.size(), size); i++) {
            String world = worlds.get(i);
            boolean lit = world.equalsIgnoreCase(current);
            ItemStack item = new ItemStack(lit ? Material.MAP : Material.EMPTY_MAP);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName((lit ? ChatColor.GREEN : ChatColor.YELLOW) + "" + ChatColor.BOLD + world);
            meta.setLore(Arrays.asList(lit ? ChatColor.GREEN + "You play here" : ChatColor.YELLOW + "Click to play here"));
            item.setItemMeta(meta);
            inventory.setItem(i, item);
        }
        player.openInventory(inventory);
    }

    static void click(Player player, Holder holder, int slot) {
        if (slot < 0 || slot >= holder.worlds.size()) {
            return;
        }
        player.closeInventory();
        holder.game.chooseMap(player, holder.worlds.get(slot));
    }
}
