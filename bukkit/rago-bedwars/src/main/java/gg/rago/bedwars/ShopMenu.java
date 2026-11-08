package gg.rago.bedwars;

import gg.rago.bedwars.BedwarsGame.Gamer;
import gg.rago.bedwars.ShopItem.Category;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The item shop, laid out as on Hypixel: the Quick Buy and the categories along the top row, a row
 * of glass under them (green under the one open), then three rows of seven items. The Quick Buy is
 * the player's own, copied from Hypixel, else the usual one. A click on an item buys it; the
 * names are green when the player can pay, red when not. Main thread only.
 */
final class ShopMenu {

    static final String TITLE = "Item Shop";
    static final int SIZE = 54;
    /** The 21 item slots: three rows of seven, as on Hypixel. */
    static final int[] ITEM_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final short GRAY = 7;
    private static final short GREEN = 5;
    private static final short RED = 14;

    /** Marks the shop's inventory, with what each slot sells. */
    static final class Holder implements InventoryHolder {
        final BedwarsGame game;
        /** Null for the Quick Buy. */
        Category category;
        final ShopItem[] items = new ShopItem[SIZE];
        private Inventory inventory;

        Holder(BedwarsGame game, Category category) {
            this.game = game;
            this.category = category;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private ShopMenu() {
    }

    /** Opens the shop on this category (null for the Quick Buy). */
    static void open(Player player, BedwarsGame game, Category category) {
        Gamer gamer = game.gamer(player.getUniqueId());
        if (gamer == null) {
            return;
        }
        Holder holder = new Holder(game, category);
        holder.inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        fill(holder, gamer, player);
        player.openInventory(holder.inventory);
    }

    /** A click in the shop: another category, or an item bought. */
    static void click(Player player, Holder holder, int slot) {
        Gamer gamer = holder.game.gamer(player.getUniqueId());
        if (gamer == null) {
            player.closeInventory();
            return;
        }
        if (slot >= 0 && slot <= Category.values().length) {
            holder.category = slot == 0 ? null : Category.values()[slot - 1];
            fill(holder, gamer, player);
            player.playSound(player.getLocation(), org.bukkit.Sound.CLICK, 1f, 1f);
            return;
        }
        ShopItem item = holder.items[slot];
        if (item != null && holder.game.buy(player, item)) {
            fill(holder, gamer, player);
        }
    }

    /** What the shop shows: the top row, the glass, and the items of the category. */
    static void fill(Holder holder, Gamer gamer, Player player) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        Arrays.fill(holder.items, null);
        inventory.setItem(0, icon(Material.NETHER_STAR, (short) 0, ChatColor.GREEN + "Quick Buy",
                ChatColor.YELLOW + "Click to view!"));
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            inventory.setItem(i + 1, icon(categories[i].icon(), (short) 0, ChatColor.GREEN + categories[i].displayName(),
                    ChatColor.YELLOW + "Click to view!"));
        }
        int open = holder.category == null ? 0 : holder.category.ordinal() + 1;
        for (int i = 0; i < 9; i++) {
            inventory.setItem(9 + i, icon(Material.STAINED_GLASS_PANE, i == open ? GREEN : GRAY,
                    ChatColor.DARK_GRAY + "↑ " + ChatColor.GRAY + "Categories",
                    ChatColor.DARK_GRAY + "↓ " + ChatColor.GRAY + (holder.category == null ? "Quick Buy"
                            : holder.category.displayName())));
        }
        List<ShopItem> shown = holder.category == null ? gamer.quickBuy : holder.game.catalog().in(holder.category);
        for (int i = 0; i < ITEM_SLOTS.length && i < shown.size(); i++) {
            ShopItem item = shown.get(i);
            int slot = ITEM_SLOTS[i];
            if (item == null) {
                if (holder.category == null) {
                    inventory.setItem(slot, icon(Material.STAINED_GLASS_PANE, RED, ChatColor.RED + "Empty slot!",
                            ChatColor.GRAY + "Set your Quick Buy on Hypixel,", ChatColor.GRAY + "then copy it in the lobby:",
                            ChatColor.GRAY + "/hypixel import"));
                }
                continue;
            }
            holder.items[slot] = item;
            inventory.setItem(slot, display(holder.game, gamer, player, item));
        }
    }

    /** An item as the shop shows it: its name, its cost, and whether the player can buy it. */
    static ItemStack display(BedwarsGame game, Gamer gamer, Player player, ShopItem sold) {
        ShopItem item = game.shown(gamer, sold);
        ItemStack stack = game.stack(item, gamer);
        String refused = game.cannotBuy(gamer, item);
        boolean affordable = BedwarsGame.count(player, item.currency()) >= item.price();
        ItemMeta meta = stack.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Cost: " + item.currency().amount(item.price()));
        if (item.amount() > 1) {
            lore.add(ChatColor.GRAY + "Amount: " + ChatColor.WHITE + item.amount());
        }
        lore.add("");
        ChatColor colour;
        if (item.kind() == ShopItem.Kind.COMING_SOON) {
            colour = ChatColor.GRAY;
            lore.add(ChatColor.GRAY + "Coming soon!");
        } else if (refused != null) {
            colour = ChatColor.GREEN;
            lore.add(ChatColor.GREEN + refused);
        } else if (affordable) {
            colour = ChatColor.GREEN;
            lore.add(ChatColor.YELLOW + "Click to purchase!");
        } else {
            colour = ChatColor.RED;
            lore.add(ChatColor.RED + "You don't have enough " + item.currency().displayName() + "!");
        }
        meta.setDisplayName(colour + item.name());
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_UNBREAKABLE);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack icon(Material material, short data, String name, String... lore) {
        ItemStack stack = new ItemStack(material, 1, data);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_POTION_EFFECTS);
        stack.setItemMeta(meta);
        return stack;
    }
}
