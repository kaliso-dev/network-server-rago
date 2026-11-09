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
    KIT_EDITOR(1, Material.BOOK, 0, ChatColor.GOLD + "Kit editor " + ChatColor.GRAY + "(right click)",
            "Arrange the items of a mode's", "kit as you like: your next", "games give it this way."),
    FIREBALL(2, Material.FIREBALL, 0, ChatColor.GOLD + "Fireball " + ChatColor.GRAY + "(right click)",
            "Throw it where you look: its blast", "pushes the players around it.", "Under your feet: a fireball jump."),
    TELEPORT(3, Material.BLAZE_ROD, 0, ChatColor.YELLOW + "Teleport stick " + ChatColor.GRAY + "(right click)",
            "Takes you onto the block you", "look at, 20 blocks away at most,", "or 20 blocks ahead in the air.",
            "Not through the walls!", "", "Left click: another gadget"),
    // The other gadgets take the place of the teleport stick (slot 3), chosen in the gadget menu.
    GRAPPLE(3, Material.FISHING_ROD, 0, ChatColor.YELLOW + "Grappling hook " + ChatColor.GRAY + "(right click)",
            "Hook the block you look at,", "32 blocks away at most, and fly", "to it.", "", "Left click: another gadget"),
    PAINTBALL(3, Material.SNOW_BALL, 0, ChatColor.YELLOW + "Paintball gun " + ChatColor.GRAY + "(right click)",
            "Paint the lobby: each ball", "colours the blocks it hits", "for a few seconds.", "", "Left click: another gadget"),
    FIREWORKS(3, Material.FIREWORK, 0, ChatColor.YELLOW + "Firework launcher " + ChatColor.GRAY + "(right click)",
            "A firework of random colours,", "every three seconds.", "", "Left click: another gadget"),
    POPPER(3, Material.CAKE, 0, ChatColor.YELLOW + "Party popper " + ChatColor.GRAY + "(right click)",
            "Cookies, sweets and music", "for everyone around.", "", "Left click: another gadget"),
    LEAP(3, Material.FEATHER, 0, ChatColor.YELLOW + "Leap feather " + ChatColor.GRAY + "(right click)",
            "A big leap where you look.", "", "Left click: another gadget"),
    LEAVE_QUEUE(4, Material.INK_SACK, 1, ChatColor.RED + "Leave the queue " + ChatColor.GRAY + "(right click)",
            "You leave the queue."),
    LEVEL(5, Material.BREWING_STAND_ITEM, 0, ChatColor.AQUA + "Network Level " + ChatColor.GRAY + "(right click)",
            "Your level, its tier, the rewards", "of every 5 levels, and the daily", "and weekly quests."),
    SHOP(6, Material.EMERALD, 0, ChatColor.GREEN + "Rago Shop " + ChatColor.GRAY + "(right click)",
            "Ranks and cosmetics, bought", "with the Rago Points of", "your games."),
    PROFILE(7, Material.SKULL_ITEM, 3, ChatColor.GREEN + "My profile " + ChatColor.GRAY + "(right click)",
            "Your level, rank and divisions,", "your stats, friends, settings", "and daily reward."),
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

    /** True for the gadgets of slot 3: the teleport stick and the others of the gadget menu. */
    boolean gadget() {
        return slot == 3;
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
