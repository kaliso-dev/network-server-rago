package gg.rago.lobby;

import java.util.Arrays;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The items of the lobby hotbar: the selector, the kit editor, a free slot (2), the gadget, the
 * fireball, a free slot (5), the leave or play again item (6, just before the shop), the shop and
 * the profile (which also opens the Network level and its quests). 1.8.8 has no persistent data on items, so an item is recognised by
 * its material and its exact name, which players cannot give to an item in the lobby (no anvil, no
 * creative mode outside the build mode).
 */
enum HotbarItem {

    SELECTOR(0, Material.COMPASS, 0, ChatColor.GREEN + "Game modes " + ChatColor.GRAY + "(right click)",
            "Pick a mode and join", "its queue."),
    KIT_EDITOR(1, Material.BOOK, 0, ChatColor.GOLD + "Kit editor " + ChatColor.GRAY + "(right click)",
            "Arrange the items of a mode's", "kit as you like: your next", "games give it this way."),
    FIREBALL(4, Material.FIREBALL, 0, ChatColor.GOLD + "Fireball " + ChatColor.GRAY + "(right click)",
            "Throw it where you look: its blast", "pushes the players around it.", "Under your feet: a fireball jump."),
    TELEPORT(3, Material.BLAZE_ROD, 0, Brand.ACCENT + "Teleport stick " + ChatColor.GRAY + "(right click)",
            "Takes you onto the block you", "look at, 20 blocks away at most,", "or 20 blocks ahead in the air.",
            "Not through the walls!", "", "Left click: another gadget"),
    // The other gadgets take the place of the teleport stick (slot 3), chosen in the gadget menu.
    GRAPPLE(3, Material.FISHING_ROD, 0, Brand.ACCENT + "Grappling hook " + ChatColor.GRAY + "(right click)",
            "Hook the block you look at,", "32 blocks away at most, and fly", "to it.", "", "Left click: another gadget"),
    // Horse armour, as on Hypixel: the 1.8 client takes a snowball out of the hand when it throws it.
    PAINTBALL(3, Material.DIAMOND_BARDING, 0, Brand.ACCENT + "Paintball gun " + ChatColor.GRAY + "(right click)",
            "Paint the lobby: each ball", "colours the blocks it hits", "for a few seconds. Never runs out.", "",
            "Left click: another gadget"),
    FIREWORKS(3, Material.FIREWORK, 0, Brand.ACCENT + "Firework launcher " + ChatColor.GRAY + "(right click)",
            "A firework of random colours,", "twice a second.", "", "Left click: another gadget"),
    POPPER(3, Material.CAKE, 0, Brand.ACCENT + "Party popper " + ChatColor.GRAY + "(right click)",
            "Cookies, sweets and music", "for everyone around.", "", "Left click: another gadget"),
    LEAP(3, Material.FEATHER, 0, Brand.ACCENT + "Leap feather " + ChatColor.GRAY + "(right click)",
            "A big leap where you look.", "", "Left click: another gadget"),
    // The gadgets of PREMIUM (rago.perk.gadgets).
    BAT_BLASTER(3, Material.GOLD_BARDING, 0, ChatColor.LIGHT_PURPLE + "Bat blaster " + ChatColor.GRAY + "(right click)",
            "A swarm of bats flies out", "where you look.", "", ChatColor.LIGHT_PURPLE + "PREMIUM gadget", "",
            "Left click: another gadget"),
    EXPLOSIVE_SHEEP(3, Material.SHEARS, 0, ChatColor.LIGHT_PURPLE + "Explosive sheep " + ChatColor.GRAY + "(right click)",
            "A sheep of every colour that", "ticks, then bursts into wool.", "", ChatColor.LIGHT_PURPLE + "PREMIUM gadget", "",
            "Left click: another gadget"),
    DISCO_BALL(3, Material.GOLD_RECORD, 0, ChatColor.LIGHT_PURPLE + "Disco ball " + ChatColor.GRAY + "(right click)",
            "Ten seconds of party: a disco", "ball above you, a dance floor", "of every colour and music.", "",
            ChatColor.LIGHT_PURPLE + "PREMIUM gadget", "", "Left click: another gadget"),
    LEAVE_QUEUE(6, Material.INK_SACK, 1, ChatColor.RED + "Leave the queue " + ChatColor.GRAY + "(right click)",
            "You leave the queue."),
    // After a game, in the slot of the leave item while out of a queue: the same mode again.
    PLAY_AGAIN(6, Material.PAPER, 0, ChatColor.GREEN + "Play again " + ChatColor.GRAY + "(right click)",
            "Queue the mode of your", "last game again."),
    SHOP(7, Material.EMERALD, 0, ChatColor.GREEN + "Rago Shop " + ChatColor.GRAY + "(right click)",
            "Ranks and cosmetics, bought", "with the Rago Points of", "your games."),
    PROFILE(8, Material.SKULL_ITEM, 3, ChatColor.GREEN + "My profile " + ChatColor.GRAY + "(right click)",
            "Your level, rank and divisions,", "your stats, friends, settings,", "daily reward, Network level", "and quests.");

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
