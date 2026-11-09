package gg.rago.bedwars;

import gg.rago.bedwars.BedwarsGame.Gamer;
import gg.rago.bedwars.ShopItem.Currency;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The menu of the upgrades NPC, laid out as on Hypixel: the team upgrades on the left, the traps on
 * the right, a row of glass, then the three places of the trap queue. A click buys the next tier of
 * an upgrade, or queues a trap; the names are green when the player can pay, red when not, and the
 * tiers the team has are green. Main thread only.
 */
final class UpgradeMenu {

    static final String TITLE = "Upgrades & Traps";
    static final int SIZE = 45;
    /** Where the upgrades and the traps are, in the order of their enums; then the queue. */
    static final int[] UPGRADE_SLOTS = {10, 11, 12, 19, 20};
    static final int[] TRAP_SLOTS = {14, 15, 16, 23};
    static final int[] QUEUE_SLOTS = {39, 40, 41};
    private static final String[] ORDINALS = {"first", "second", "third"};
    private static final short GRAY = 7;
    private static final short LIGHT_GRAY = 8;

    /** Marks the menu's inventory. */
    static final class Holder implements InventoryHolder {
        final BedwarsGame game;
        private Inventory inventory;

        Holder(BedwarsGame game) {
            this.game = game;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private UpgradeMenu() {
    }

    static void open(Player player, BedwarsGame game) {
        Gamer gamer = game.gamer(player.getUniqueId());
        if (gamer == null) {
            return;
        }
        Holder holder = new Holder(game);
        holder.inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        fill(holder, gamer, player);
        player.openInventory(holder.inventory);
    }

    /** A click in the menu: an upgrade or a trap bought. */
    static void click(Player player, Holder holder, int slot) {
        Gamer gamer = holder.game.gamer(player.getUniqueId());
        if (gamer == null) {
            return;
        }
        Upgrades.Kind kind = upgradeAt(slot);
        Upgrades.Trap trap = trapAt(slot);
        boolean bought = kind != null ? holder.game.buyUpgrade(player, kind)
                : trap != null && holder.game.buyTrap(player, trap);
        if (bought) {
            fill(holder, gamer, player);
        }
    }

    static Upgrades.Kind upgradeAt(int slot) {
        for (int i = 0; i < UPGRADE_SLOTS.length; i++) {
            if (UPGRADE_SLOTS[i] == slot) {
                return Upgrades.Kind.values()[i];
            }
        }
        return null;
    }

    static Upgrades.Trap trapAt(int slot) {
        for (int i = 0; i < TRAP_SLOTS.length; i++) {
            if (TRAP_SLOTS[i] == slot) {
                return Upgrades.Trap.values()[i];
            }
        }
        return null;
    }

    static void fill(Holder holder, Gamer gamer, Player player) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        Upgrades upgrades = gamer.team.upgrades;
        int diamonds = BedwarsGame.count(player, Currency.DIAMOND);
        int teamSize = holder.game.teamSize();
        for (Upgrades.Kind kind : Upgrades.Kind.values()) {
            inventory.setItem(UPGRADE_SLOTS[kind.ordinal()], upgrade(kind, upgrades.tier(kind), teamSize, diamonds));
        }
        for (Upgrades.Trap trap : Upgrades.Trap.values()) {
            inventory.setItem(TRAP_SLOTS[trap.ordinal()], trap(trap, upgrades.traps.size(), diamonds));
        }
        for (int slot = 27; slot < 36; slot++) {
            inventory.setItem(slot, icon(Material.STAINED_GLASS_PANE, GRAY, ChatColor.DARK_GRAY + "⬆ " + ChatColor.GRAY
                    + "Purchasable", Arrays.asList(ChatColor.DARK_GRAY + "⬇ " + ChatColor.GRAY + "Traps Queue"), false));
        }
        for (int i = 0; i < QUEUE_SLOTS.length; i++) {
            inventory.setItem(QUEUE_SLOTS[i], queued(i, upgrades.traps));
        }
    }

    /** An upgrade as the menu shows it: its next tier, every tier and its price, what a click does. */
    static ItemStack upgrade(Upgrades.Kind kind, int tier, int teamSize, int diamonds) {
        boolean unlocked = tier >= kind.tiers();
        int next = Math.min(tier + 1, kind.tiers());
        int price = kind.price(next, teamSize);
        List<String> lore = lines(kind.description);
        lore.add("");
        if (kind.tiers() == 1) {
            lore.add(ChatColor.GRAY + "Cost: " + Currency.DIAMOND.amount(price));
        } else {
            for (int t = 1; t <= kind.tiers(); t++) {
                lore.add((t <= tier ? ChatColor.GREEN : ChatColor.GRAY) + "Tier " + t + ": " + kind.tierEffect(t) + ", "
                        + Currency.DIAMOND.amount(kind.price(t, teamSize)));
            }
        }
        lore.add("");
        ChatColor colour;
        if (unlocked) {
            colour = ChatColor.GREEN;
            lore.add(ChatColor.GREEN + "UNLOCKED");
        } else if (diamonds >= price) {
            colour = ChatColor.GREEN;
            lore.add(ChatColor.YELLOW + "Click to purchase!");
        } else {
            colour = ChatColor.RED;
            lore.add(ChatColor.RED + "You don't have enough Diamonds!");
        }
        return icon(kind.icon, (short) 0, colour + kind.tierName(next), lore, tier > 0);
    }

    /** A trap as the menu shows it: what it does, and the price of the next trap in the queue. */
    static ItemStack trap(Upgrades.Trap trap, int queued, int diamonds) {
        int price = Upgrades.trapPrice(queued);
        List<String> lore = lines(trap.description);
        lore.add("");
        lore.add(ChatColor.GRAY + "Cost: " + Currency.DIAMOND.amount(price));
        lore.add("");
        ChatColor colour;
        if (queued >= Upgrades.MAX_TRAPS) {
            colour = ChatColor.RED;
            lore.add(ChatColor.RED + "Trap queue full!");
        } else if (diamonds >= price) {
            colour = ChatColor.GREEN;
            lore.add(ChatColor.YELLOW + "Click to purchase!");
        } else {
            colour = ChatColor.RED;
            lore.add(ChatColor.RED + "You don't have enough Diamonds!");
        }
        return icon(trap.icon, (short) 0, colour + trap.displayName, lore, false);
    }

    /** A place of the trap queue: the trap waiting there, or none and the price of the next. */
    static ItemStack queued(int index, List<Upgrades.Trap> traps) {
        String who = ChatColor.GRAY + "The " + ORDINALS[index] + " enemy to walk into";
        if (index < traps.size()) {
            Upgrades.Trap trap = traps.get(index);
            List<String> lore = lines(trap.description);
            lore.add("");
            lore.add(who);
            lore.add(ChatColor.GRAY + "your base will trigger this trap!");
            return icon(trap.icon, (short) 0, ChatColor.GREEN + "Trap #" + (index + 1) + ": " + trap.displayName, lore,
                    false);
        }
        List<String> lore = new ArrayList<>(Arrays.asList(who, ChatColor.GRAY + "your base will trigger this",
                ChatColor.GRAY + "trap!", "", ChatColor.GRAY + "Purchasing a trap will queue", ChatColor.GRAY
                        + "it here. Its cost will scale", ChatColor.GRAY + "based on the number of traps",
                ChatColor.GRAY + "queued.", "", ChatColor.GRAY + "Next trap: "
                        + Currency.DIAMOND.amount(Upgrades.trapPrice(traps.size()))));
        return icon(Material.STAINED_GLASS, LIGHT_GRAY, ChatColor.RED + "Trap #" + (index + 1) + ": No Trap!", lore,
                false);
    }

    private static List<String> lines(String[] description) {
        List<String> lines = new ArrayList<>();
        for (String line : description) {
            lines.add(ChatColor.GRAY + line);
        }
        return lines;
    }

    private static ItemStack icon(Material material, short data, String name, List<String> lore, boolean glowing) {
        ItemStack stack = new ItemStack(material, 1, data);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        if (glowing) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_POTION_EFFECTS);
        stack.setItemMeta(meta);
        return stack;
    }
}
