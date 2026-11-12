package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The menu of the kits of Attack and Defense, before a round: the kits of the player's side this
 * round (attack or defense) on the middle row, each with what it gives; the one chosen shines.
 * Main thread only.
 */
final class KitMenu {

    static final String ATTACK_TITLE = "Attack kit";
    static final String DEFENSE_TITLE = "Defense kit";
    private static final int SIZE = 27;

    private KitMenu() {
    }

    static final class Holder implements InventoryHolder {
        final AttackDefenseGame game;
        final boolean attack;
        final int count;
        private Inventory inventory;

        Holder(AttackDefenseGame game, boolean attack, int count) {
            this.game = game;
            this.attack = attack;
            this.count = count;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** The slot of the n-th of {@code count} kits on the middle row: one slot apart, centred (11, 13, 15 for three). */
    static int slotOf(int index, int count) {
        int gap = count <= 4 ? 2 : 1;
        return 13 - (count - 1) * gap / 2 + index * gap;
    }

    /** What the menu says of a kit: its words, its items, and whether it is the one. */
    static List<String> lore(RoundKit kit, ShopCatalog catalog, boolean chosen) {
        List<String> lore = new ArrayList<>();
        for (String line : kit.description) {
            lore.add(ChatColor.GRAY + line);
        }
        lore.add("");
        for (RoundKit.Entry entry : kit.entries(catalog)) {
            lore.add(ChatColor.DARK_GRAY + "- " + ChatColor.WHITE + RoundKit.label(entry));
        }
        lore.add("");
        lore.add(chosen ? ChatColor.GREEN + "Your kit" : ChatColor.RED + "Click to choose");
        return lore;
    }

    static void open(Player player, AttackDefenseGame game, boolean attack, List<RoundKit> kits, int chosen,
            ShopCatalog catalog) {
        Holder holder = new Holder(game, attack, kits.size());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, attack ? ATTACK_TITLE : DEFENSE_TITLE);
        holder.inventory = inventory;
        for (int i = 0; i < kits.size(); i++) {
            RoundKit kit = kits.get(i);
            boolean lit = i == chosen;
            ItemStack item = new ItemStack(kit.icon);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName((lit ? ChatColor.GREEN : ChatColor.YELLOW) + "" + ChatColor.BOLD + ChatColor.stripColor(kit.name));
            meta.setLore(lore(kit, catalog, lit));
            if (lit) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_POTION_EFFECTS);
            item.setItemMeta(meta);
            inventory.setItem(slotOf(i, kits.size()), item);
        }
        player.openInventory(inventory);
    }

    static void click(Player player, Holder holder, int slot) {
        for (int i = 0; i < holder.count; i++) {
            if (slotOf(i, holder.count) == slot) {
                player.closeInventory();
                holder.game.chooseKit(player, holder.attack, i);
                return;
            }
        }
    }
}
