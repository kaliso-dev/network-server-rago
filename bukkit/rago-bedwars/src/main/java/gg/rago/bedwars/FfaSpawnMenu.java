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
 * The menu of the FFA where a player chooses where they spawn: each spawn of the map (apparitions:
 * in its bedwars.yml: "Top bridge", "Low", "Mid"...), then Random, among all of them, the bases and
 * the diamonds. One row; the choice they have is lit. Main thread only.
 */
final class FfaSpawnMenu {

    static final String TITLE = "Choose your spawn";
    /** The icons of the spawns, in their order (a ladder for the first: the top bridge). */
    private static final List<Material> ICONS = Arrays.asList(Material.LADDER, Material.GRASS, Material.EMERALD,
            Material.QUARTZ_BLOCK, Material.GOLD_BLOCK, Material.DIAMOND_BLOCK, Material.ENDER_STONE, Material.OBSIDIAN);

    private FfaSpawnMenu() {
    }

    static final class Holder implements InventoryHolder {
        final FfaGame game;
        final List<String> names;
        private Inventory inventory;

        Holder(FfaGame game, List<String> names) {
            this.game = game;
            this.names = names;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** The slot of the n-th of {@code count} items: side by side, centred in the row. */
    static int slotOf(int index, int count) {
        return (9 - count) / 2 + index;
    }

    static void open(Player player, FfaGame game, List<String> names, String chosen) {
        List<String> all = new ArrayList<>(names);
        all.add(null);
        Holder holder = new Holder(game, all);
        Inventory inventory = Bukkit.createInventory(holder, 9, TITLE);
        holder.inventory = inventory;
        for (int i = 0; i < all.size(); i++) {
            String name = all.get(i);
            boolean lit = name == null ? chosen == null : name.equals(chosen);
            Material icon = name == null ? Material.ENDER_PEARL : ICONS.get(Math.min(i, ICONS.size() - 1));
            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName((lit ? ChatColor.GREEN : ChatColor.YELLOW) + "" + ChatColor.BOLD + (name == null ? "Random" : name));
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + (name == null ? "Anywhere: a base, a diamond," : "You spawn here,"),
                    ChatColor.GRAY + (name == null ? "the middle or the top bridge." : "away from the others."),
                    "",
                    lit ? ChatColor.GREEN + "Your spawn" : ChatColor.YELLOW + "Click to spawn here"));
            item.setItemMeta(meta);
            inventory.setItem(slotOf(i, all.size()), item);
        }
        player.openInventory(inventory);
    }

    static void click(Player player, Holder holder, int slot) {
        for (int i = 0; i < holder.names.size(); i++) {
            if (slotOf(i, holder.names.size()) == slot) {
                player.closeInventory();
                holder.game.choose(player, holder.names.get(i));
                return;
            }
        }
    }
}
