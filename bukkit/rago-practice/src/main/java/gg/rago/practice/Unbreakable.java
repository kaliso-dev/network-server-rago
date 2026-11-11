package gg.rago.practice;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Nothing wears out in the games: every sword, tool, bow and piece of armour of a player carries
 * the Unbreakable tag (its line hidden). The server cancels their wear already
 * (PlayerItemDamageEvent), but the 1.8 client wears the sword or the tool in its own hand at each
 * hit and each broken block, and shows it, broken even, until the server sends the item again: with
 * the tag, the client does not either. Main thread only.
 */
final class Unbreakable {

    private Unbreakable() {
    }

    /** The items of this player that wear out made unbreakable, those not yet. */
    static void apply(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack made = of(inventory.getItem(slot));
            if (made != null) {
                inventory.setItem(slot, made);
            }
        }
        ItemStack[] armour = inventory.getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armour.length; i++) {
            ItemStack made = of(armour[i]);
            if (made != null) {
                armour[i] = made;
                changed = true;
            }
        }
        if (changed) {
            inventory.setArmorContents(armour);
        }
    }

    /** This item made unbreakable and whole, if it wears out and is not yet; null otherwise. */
    static ItemStack of(ItemStack item) {
        if (item == null || !wears(item.getType().getMaxDurability())) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.spigot().isUnbreakable()) {
            return null;
        }
        meta.spigot().setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        ItemStack made = item.clone();
        made.setItemMeta(meta);
        made.setDurability((short) 0);
        return made;
    }

    /** True for an item of this most durability: one that wears out (blocks and food have none). */
    static boolean wears(short maxDurability) {
        return maxDurability > 0;
    }
}
