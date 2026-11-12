package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Shows the Bed Wars settings a player imported from Hypixel: the quick buy as in the Hypixel
 * shop (three rows of seven), and the hotbar manager in the bottom row. Nothing can be taken.
 */
final class HypixelMenu {

    static final String TITLE = "Your Hypixel shop";
    /** The first slot of each row of the quick buy, which starts in the second column. */
    private static final int[] ROWS = {10, 19, 28};
    static final int HOTBAR_ROW = 45;

    /** Marks the chest: every click in it is cancelled. */
    static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private HypixelMenu() {
    }

    static void open(Player player, HypixelProfile profile) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 54, TITLE);
        holder.inventory = inventory;
        inventory.setItem(4, item(Material.EMERALD, 0, ChatColor.GREEN + "Quick buy of " + profile.name(),
                ChatColor.GRAY + "Imported from Hypixel. Your Bed Wars",
                ChatColor.GRAY + "modes and Fast Shop will use it.",
                ChatColor.GRAY + "/hypixel import to copy it again."));
        for (int i = 0; i < HypixelProfile.QUICK_BUY; i++) {
            String id = profile.quickBuy().get(i);
            int slot = ROWS[i / 7] + i % 7;
            inventory.setItem(slot, id == null
                    ? item(Material.STAINED_GLASS_PANE, 14, ChatColor.RED + "Empty slot")
                    : shopItem(id));
        }
        if (profile.hotbar() == null) {
            inventory.setItem(40, item(Material.BARRIER, 0, ChatColor.GRAY + "No hotbar manager on Hypixel"));
        } else {
            inventory.setItem(40, item(Material.BOOK, 0, Brand.ACCENT + "Hotbar manager (below)",
                    ChatColor.GRAY + "Where each kind of item goes", ChatColor.GRAY + "in your hotbar."));
            for (int i = 0; i < HypixelProfile.HOTBAR; i++) {
                String category = profile.hotbar().get(i);
                inventory.setItem(HOTBAR_ROW + i, category == null
                        ? item(Material.STAINED_GLASS_PANE, 7, ChatColor.GRAY + "Slot " + (i + 1) + ": free")
                        : categoryItem(category, i));
            }
        }
        player.openInventory(inventory);
    }

    /** The 1.8 item that shows a Hypixel shop id, named after it; paper for an id it does not know. */
    @SuppressWarnings("deprecation") // ItemStack#getDurability is the 1.8 data value
    static ItemStack shopItem(String id) {
        ItemStack shown = shopMaterial(id);
        return item(shown.getType(), shown.getDurability(), ChatColor.WHITE + pretty(id),
                ChatColor.DARK_GRAY + id);
    }

    /** The material (and data) of a Hypixel shop id: wool, stone_sword, speed_ii_potion_(45_seconds)... */
    static ItemStack shopMaterial(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        if (key.contains("potion")) {
            short data = key.contains("speed") ? (short) 8194 : key.contains("jump") ? (short) 8203
                    : key.contains("invis") ? (short) 8206 : (short) 0;
            return new ItemStack(Material.POTION, 1, data);
        }
        if (key.contains("pickaxe")) {
            return new ItemStack(tier(key, Material.WOOD_PICKAXE, Material.IRON_PICKAXE, Material.GOLD_PICKAXE,
                    Material.DIAMOND_PICKAXE));
        }
        if (key.contains("axe")) {
            return new ItemStack(tier(key, Material.WOOD_AXE, Material.IRON_AXE, Material.GOLD_AXE, Material.DIAMOND_AXE));
        }
        if (key.contains("sword")) {
            return new ItemStack(key.contains("diamond") ? Material.DIAMOND_SWORD : key.contains("iron") ? Material.IRON_SWORD
                    : key.contains("stone") ? Material.STONE_SWORD : Material.WOOD_SWORD);
        }
        if (key.contains("chainmail")) {
            return new ItemStack(Material.CHAINMAIL_BOOTS);
        }
        if (key.contains("boots") || key.contains("armor")) {
            return new ItemStack(key.contains("diamond") ? Material.DIAMOND_BOOTS : Material.IRON_BOOTS);
        }
        String[][] names = {
            {"wool", "WOOL"}, {"clay", "STAINED_CLAY"}, {"glass", "STAINED_GLASS"}, {"end_stone", "ENDER_STONE"},
            {"ladder", "LADDER"}, {"plank", "WOOD"}, {"obsidian", "OBSIDIAN"}, {"stick", "STICK"},
            {"shears", "SHEARS"}, {"arrow", "ARROW"}, {"bow", "BOW"}, {"golden_apple", "GOLDEN_APPLE"},
            {"bedbug", "SNOW_BALL"}, {"fireball", "FIREBALL"}, {"tnt", "TNT"}, {"ender_pearl", "ENDER_PEARL"},
            {"water", "WATER_BUCKET"}, {"egg", "EGG"}, {"milk", "MILK_BUCKET"}, {"sponge", "SPONGE"},
            {"tower", "CHEST"}, {"defender", "MONSTER_EGG"},
        };
        for (String[] name : names) {
            if (key.contains(name[0])) {
                Material material = Material.valueOf(name[1]);
                // The dream defender is an iron golem: its spawn egg.
                return new ItemStack(material, 1, material == Material.MONSTER_EGG ? (short) 99 : (short) 0);
            }
        }
        return new ItemStack(Material.PAPER);
    }

    private static Material tier(String key, Material wood, Material iron, Material gold, Material diamond) {
        return key.contains("diamond") ? diamond : key.contains("gold") ? gold : key.contains("iron") ? iron : wood;
    }

    /** A hotbar manager category: Melee, Blocks, Tools, Ranged, Potions, Utility, Compass. */
    static ItemStack categoryItem(String category, int slot) {
        String key = category.toLowerCase(Locale.ROOT);
        Material material = key.contains("melee") ? Material.GOLD_SWORD : key.contains("block") ? Material.STAINED_CLAY
                : key.contains("tool") ? Material.STONE_PICKAXE : key.contains("ranged") ? Material.BOW
                : key.contains("potion") ? Material.BREWING_STAND_ITEM : key.contains("utility") ? Material.TNT
                : key.contains("compass") || key.contains("tracker") ? Material.COMPASS : Material.PAPER;
        return item(material, 0, Brand.ACCENT + "Slot " + (slot + 1) + ": " + ChatColor.WHITE + pretty(category));
    }

    /** "speed_ii_potion_(45_seconds)" to "Speed II Potion (45 Seconds)". */
    static String pretty(String id) {
        List<String> words = new ArrayList<>();
        for (String word : id.replace('_', ' ').trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            String bare = word.replaceAll("[^A-Za-z]", "");
            if (bare.matches("(?i)[ivx]+") && bare.length() <= 4) {
                words.add(word.toUpperCase(Locale.ROOT));
            } else {
                int letter = 0;
                while (letter < word.length() && !Character.isLetter(word.charAt(letter))) {
                    letter++;
                }
                words.add(letter >= word.length() ? word
                        : word.substring(0, letter) + Character.toUpperCase(word.charAt(letter)) + word.substring(letter + 1));
            }
        }
        return String.join(" ", words);
    }

    private static ItemStack item(Material material, int data, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1, (short) data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}
