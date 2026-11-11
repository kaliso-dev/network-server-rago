package gg.rago.lobby.api;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * The end of a game, as on Hypixel: a few seconds to see who won (the winners' victory effect)
 * before everyone goes back to the lobby, with the Play again paper and the Leave bed in the
 * hotbar, and the same two buttons in the chat for a spectator, who cannot use items. Both work at
 * once: /playagain queues the same mode again (the lobby's command), /spawn goes back to the lobby.
 * The game plugins call it; main thread only.
 */
public final class EndOfGame {

    /** The Play again paper, in the eighth slot. */
    public static final int PLAY_AGAIN_SLOT = 7;
    /** The Leave bed, in the last slot. */
    public static final int LEAVE_SLOT = 8;
    static final String PLAY_AGAIN = ChatColor.GREEN + "" + ChatColor.BOLD + "Play again" + ChatColor.GRAY + " (right click)";
    static final String LEAVE = ChatColor.RED + "" + ChatColor.BOLD + "Leave" + ChatColor.GRAY + " (right click)";

    private EndOfGame() {
    }

    /**
     * The game is over for this player: the two items in an empty hotbar, the paper in their hand
     * (not for a spectator, who has no hotbar of their own), and the two buttons in the chat.
     */
    public static void give(Player player) {
        if (player.getGameMode() != GameMode.SPECTATOR) {
            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            inventory.setItem(PLAY_AGAIN_SLOT, item(Material.PAPER, 0, PLAY_AGAIN, "Queue the same mode again."));
            inventory.setItem(LEAVE_SLOT, item(Material.BED, 0, LEAVE, "Back to the lobby."));
            inventory.setHeldItemSlot(PLAY_AGAIN_SLOT);
            player.updateInventory();
        }
        buttons(player);
    }

    /** [PLAY AGAIN] and [LEAVE] in the chat: a click runs /playagain or /spawn. */
    public static void buttons(Player player) {
        // clickable through the vanilla /tellraw, as the lobby's buttons: a name is letters, digits and _
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + json());
    }

    /** The JSON of the two buttons. */
    static String json() {
        return "[\"\",{\"text\":\"[PLAY AGAIN]\",\"color\":\"green\",\"bold\":true,"
                + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/playagain\"},"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Queue the same mode again\"}},"
                + "{\"text\":\"   \"},"
                + "{\"text\":\"[LEAVE]\",\"color\":\"red\",\"bold\":true,"
                + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/spawn\"},"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Back to the lobby\"}}]";
    }

    /**
     * A right click with this item: true if it is the Play again paper or the Leave bed, whose
     * command then runs at the next tick, out of the event (the game lets the player go).
     */
    public static boolean clicked(Plugin plugin, final Player player, ItemStack item) {
        final String command = commandOf(item);
        if (command == null) {
            return false;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.performCommand(command);
            }
        });
        return true;
    }

    /** The command of this item: "playagain", "spawn", or null for another item. */
    static String commandOf(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return null;
        }
        String name = item.getItemMeta().getDisplayName();
        if (item.getType() == Material.PAPER && PLAY_AGAIN.equals(name)) {
            return "playagain";
        }
        if (item.getType() == Material.BED && LEAVE.equals(name)) {
            return "spawn";
        }
        return null;
    }

    private static ItemStack item(Material type, int data, String name, String lore) {
        ItemStack item = new ItemStack(type, 1, (short) data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(java.util.Collections.singletonList(ChatColor.GRAY + lore));
        item.setItemMeta(meta);
        return item;
    }
}
