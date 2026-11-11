package gg.rago.practice;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * A chest menu a game opens for one of its players (the platforms of the bridge fight...): its
 * items can't be taken, and a click on one goes to {@link PracticeGame#menuClick}.
 */
final class GameMenu implements InventoryHolder {

    final PracticeGame game;
    private final Inventory inventory;

    GameMenu(PracticeGame game, String title, ItemStack[] items) {
        this.game = game;
        this.inventory = Bukkit.createInventory(this, 9 * Math.max(1, (items.length + 8) / 9), title);
        for (int slot = 0; slot < items.length; slot++) {
            if (items[slot] != null) {
                inventory.setItem(slot, items[slot]);
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
