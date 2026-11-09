package gg.rago.bedwars;

import gg.rago.bedwars.ShopItem.Category;
import gg.rago.bedwars.ShopItem.Currency;
import gg.rago.bedwars.ShopItem.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

/**
 * What the item shop sells, by category, at the usual Bed Wars prices of a solo or doubles game.
 * The pickaxe and the axe are sold tier after tier: the shop shows the next one. Immutable.
 */
final class ShopCatalog {

    /** The quick buy of a player who imported none: three rows of seven, null for an empty slot. */
    static final List<String> DEFAULT_QUICK_BUY = Collections.unmodifiableList(Arrays.asList(
            "wool", "stone_sword", "chainmail", "pickaxe", "bow", "speed", "tnt",
            "plank", "iron_sword", "iron_boots", "shears", "arrow", "jump", "water",
            "end_stone", "golden_apple", "fireball", "ender_pearl", "axe", "glass", null));

    private final List<ShopItem> items;
    private final List<ShopItem> pickaxes;
    private final List<ShopItem> axes;

    private ShopCatalog(List<ShopItem> items, List<ShopItem> pickaxes, List<ShopItem> axes) {
        this.items = Collections.unmodifiableList(items);
        this.pickaxes = Collections.unmodifiableList(pickaxes);
        this.axes = Collections.unmodifiableList(axes);
    }

    static ShopCatalog standard() {
        List<ShopItem> items = new ArrayList<>();
        // Blocks
        items.add(ShopItem.of("wool", "Wool", Category.BLOCKS, Kind.TEAM_BLOCK, Material.WOOL, 16)
                .costs(4, Currency.IRON).build());
        items.add(ShopItem.of("clay", "Hardened Clay", Category.BLOCKS, Kind.TEAM_BLOCK, Material.STAINED_CLAY, 16)
                .costs(12, Currency.IRON).build());
        items.add(ShopItem.of("glass", "Blast-Proof Glass", Category.BLOCKS, Kind.TEAM_BLOCK, Material.STAINED_GLASS, 4)
                .costs(12, Currency.IRON).build());
        items.add(ShopItem.of("end_stone", "End Stone", Category.BLOCKS, Kind.ITEM, Material.ENDER_STONE, 12)
                .costs(24, Currency.IRON).build());
        items.add(ShopItem.of("ladder", "Ladder", Category.BLOCKS, Kind.ITEM, Material.LADDER, 8)
                .costs(4, Currency.IRON).build());
        items.add(ShopItem.of("plank", "Oak Wood Planks", Category.BLOCKS, Kind.ITEM, Material.WOOD, 16)
                .costs(4, Currency.GOLD).build());
        items.add(ShopItem.of("obsidian", "Obsidian", Category.BLOCKS, Kind.ITEM, Material.OBSIDIAN, 4)
                .costs(4, Currency.EMERALD).build());
        // Melee
        items.add(ShopItem.of("stone_sword", "Stone Sword", Category.MELEE, Kind.SWORD, Material.STONE_SWORD, 1)
                .costs(10, Currency.IRON).build());
        items.add(ShopItem.of("iron_sword", "Iron Sword", Category.MELEE, Kind.SWORD, Material.IRON_SWORD, 1)
                .costs(7, Currency.GOLD).build());
        items.add(ShopItem.of("diamond_sword", "Diamond Sword", Category.MELEE, Kind.SWORD, Material.DIAMOND_SWORD, 1)
                .costs(4, Currency.EMERALD).build());
        items.add(ShopItem.of("stick", "Stick (Knockback I)", Category.MELEE, Kind.ITEM, Material.STICK, 1)
                .costs(5, Currency.GOLD).enchant(Enchantment.KNOCKBACK, 1).build());
        // Armor
        items.add(ShopItem.of("chainmail", "Permanent Chainmail Armor", Category.ARMOR, Kind.ARMOR,
                Material.CHAINMAIL_BOOTS, 1).costs(24, Currency.IRON).tier(1).build());
        items.add(ShopItem.of("iron_boots", "Permanent Iron Armor", Category.ARMOR, Kind.ARMOR,
                Material.IRON_BOOTS, 1).costs(12, Currency.GOLD).tier(2).build());
        items.add(ShopItem.of("diamond_boots", "Permanent Diamond Armor", Category.ARMOR, Kind.ARMOR,
                Material.DIAMOND_BOOTS, 1).costs(6, Currency.EMERALD).tier(3).build());
        // Tools: the pickaxe and the axe are shown by their next tier, below.
        items.add(ShopItem.of("shears", "Permanent Shears", Category.TOOLS, Kind.SHEARS, Material.SHEARS, 1)
                .costs(20, Currency.IRON).build());
        List<ShopItem> pickaxes = Arrays.asList(
                tool("pickaxe", "Wooden Pickaxe", Kind.PICKAXE, Material.WOOD_PICKAXE, 10, Currency.IRON, 1, 0),
                tool("pickaxe", "Iron Pickaxe", Kind.PICKAXE, Material.IRON_PICKAXE, 10, Currency.IRON, 2, 1),
                tool("pickaxe", "Golden Pickaxe", Kind.PICKAXE, Material.GOLD_PICKAXE, 3, Currency.GOLD, 3, 2),
                tool("pickaxe", "Diamond Pickaxe", Kind.PICKAXE, Material.DIAMOND_PICKAXE, 6, Currency.GOLD, 3, 3));
        List<ShopItem> axes = Arrays.asList(
                tool("axe", "Wooden Axe", Kind.AXE, Material.WOOD_AXE, 10, Currency.IRON, 1, 0),
                tool("axe", "Stone Axe", Kind.AXE, Material.STONE_AXE, 10, Currency.IRON, 1, 1),
                tool("axe", "Iron Axe", Kind.AXE, Material.IRON_AXE, 3, Currency.GOLD, 2, 2),
                tool("axe", "Diamond Axe", Kind.AXE, Material.DIAMOND_AXE, 6, Currency.GOLD, 3, 3));
        items.add(pickaxes.get(0));
        items.add(axes.get(0));
        // Ranged
        items.add(ShopItem.of("arrow", "Arrow", Category.RANGED, Kind.ITEM, Material.ARROW, 8)
                .costs(2, Currency.GOLD).build());
        items.add(ShopItem.of("bow", "Bow", Category.RANGED, Kind.ITEM, Material.BOW, 1)
                .costs(12, Currency.GOLD).build());
        items.add(ShopItem.of("bow_(power_i)", "Bow (Power I)", Category.RANGED, Kind.ITEM, Material.BOW, 1)
                .costs(20, Currency.GOLD).enchant(Enchantment.ARROW_DAMAGE, 1).build());
        items.add(ShopItem.of("bow_(power_i__punch_i)", "Bow (Power I, Punch I)", Category.RANGED, Kind.ITEM,
                Material.BOW, 1).costs(6, Currency.EMERALD).enchant(Enchantment.ARROW_DAMAGE, 1)
                .enchant(Enchantment.ARROW_KNOCKBACK, 1).build());
        // Potions: the data value only gives the colour of the bottle.
        items.add(ShopItem.of("speed", "Speed II Potion (45 seconds)", Category.POTIONS, Kind.POTION, Material.POTION, 1)
                .data(8194).costs(1, Currency.EMERALD).effect(PotionEffectType.SPEED, 2, 45).build());
        items.add(ShopItem.of("jump", "Jump V Potion (45 seconds)", Category.POTIONS, Kind.POTION, Material.POTION, 1)
                .data(8203).costs(1, Currency.EMERALD).effect(PotionEffectType.JUMP, 5, 45).build());
        items.add(ShopItem.of("invisibility", "Invisibility Potion (30 seconds)", Category.POTIONS, Kind.POTION,
                Material.POTION, 1).data(8206).costs(2, Currency.EMERALD).effect(PotionEffectType.INVISIBILITY, 1, 30)
                .build());
        // Utility
        items.add(ShopItem.of("golden_apple", "Golden Apple", Category.UTILITY, Kind.ITEM, Material.GOLDEN_APPLE, 1)
                .costs(3, Currency.GOLD).build());
        items.add(ShopItem.of("fireball", "Fireball", Category.UTILITY, Kind.ITEM, Material.FIREBALL, 1)
                .costs(40, Currency.IRON).build());
        items.add(ShopItem.of("tnt", "TNT", Category.UTILITY, Kind.ITEM, Material.TNT, 1)
                .costs(4, Currency.GOLD).build());
        items.add(ShopItem.of("ender_pearl", "Ender Pearl", Category.UTILITY, Kind.ITEM, Material.ENDER_PEARL, 1)
                .costs(4, Currency.EMERALD).build());
        items.add(ShopItem.of("water", "Water Bucket", Category.UTILITY, Kind.ITEM, Material.WATER_BUCKET, 1)
                .costs(3, Currency.GOLD).build());
        items.add(ShopItem.of("sponge", "Sponge", Category.UTILITY, Kind.ITEM, Material.SPONGE, 4)
                .costs(3, Currency.GOLD).build());
        items.add(ShopItem.of("bedbug", "Bedbug", Category.UTILITY, Kind.COMING_SOON, Material.SNOW_BALL, 1)
                .costs(24, Currency.IRON).build());
        items.add(ShopItem.of("dream_defender", "Dream Defender", Category.UTILITY, Kind.COMING_SOON,
                Material.MONSTER_EGG, 1).data(99).costs(120, Currency.IRON).build());
        items.add(ShopItem.of("bridge_egg", "Bridge Egg", Category.UTILITY, Kind.COMING_SOON, Material.EGG, 1)
                .costs(1, Currency.EMERALD).build());
        items.add(ShopItem.of("milk", "Magic Milk", Category.UTILITY, Kind.COMING_SOON, Material.MILK_BUCKET, 1)
                .costs(4, Currency.GOLD).build());
        items.add(ShopItem.of("tower", "Compact Pop-up Tower", Category.UTILITY, Kind.COMING_SOON, Material.CHEST, 1)
                .costs(24, Currency.IRON).build());
        return new ShopCatalog(items, pickaxes, axes);
    }

    /**
     * The shop of the arena (Free FFA): the usual items without the ones still to come, the
     * diamond armour and sword, the obsidian cheaper, and what the diamonds buy, since the arena
     * has no team upgrades: a sword with Sharpness, a Power II bow, Regeneration and Strength
     * potions, an enchanted golden apple.
     */
    static ShopCatalog arena() {
        ShopCatalog standard = standard();
        List<ShopItem> items = new ArrayList<>();
        for (ShopItem item : standard.items) {
            if (item.kind() == Kind.COMING_SOON) {
                continue;
            }
            switch (item.id()) {
                case "obsidian":
                    items.add(item.costing(2, Currency.EMERALD));
                    break;
                case "diamond_sword":
                    items.add(item.costing(3, Currency.EMERALD));
                    items.add(ShopItem.of("sharp_sword", "Diamond Sword (Sharpness I)", Category.MELEE, Kind.SWORD,
                            Material.DIAMOND_SWORD, 1).costs(4, Currency.DIAMOND).enchant(Enchantment.DAMAGE_ALL, 1).build());
                    break;
                case "diamond_boots":
                    items.add(item.costing(4, Currency.EMERALD));
                    break;
                case "bow_(power_i__punch_i)":
                    items.add(item);
                    items.add(ShopItem.of("power_bow", "Bow (Power II)", Category.RANGED, Kind.ITEM, Material.BOW, 1)
                            .costs(3, Currency.DIAMOND).enchant(Enchantment.ARROW_DAMAGE, 2).build());
                    break;
                case "invisibility":
                    items.add(item);
                    items.add(ShopItem.of("regeneration", "Regeneration II Potion (10 seconds)", Category.POTIONS,
                            Kind.POTION, Material.POTION, 1).data(8193).costs(2, Currency.DIAMOND)
                            .effect(PotionEffectType.REGENERATION, 2, 10).build());
                    items.add(ShopItem.of("strength", "Strength I Potion (15 seconds)", Category.POTIONS, Kind.POTION,
                            Material.POTION, 1).data(8201).costs(3, Currency.DIAMOND)
                            .effect(PotionEffectType.INCREASE_DAMAGE, 1, 15).build());
                    break;
                case "golden_apple":
                    items.add(item);
                    items.add(ShopItem.of("enchanted_apple", "Enchanted Golden Apple", Category.UTILITY, Kind.ITEM,
                            Material.GOLDEN_APPLE, 1).data(1).costs(6, Currency.DIAMOND).build());
                    break;
                default:
                    items.add(item);
                    break;
            }
        }
        return new ShopCatalog(items, standard.pickaxes, standard.axes);
    }

    /** A pickaxe or axe tier: tier is its place in the list (0 wooden), level its Efficiency. */
    private static ShopItem tool(String id, String name, Kind kind, Material material, int price, Currency currency,
            int efficiency, int tier) {
        return ShopItem.of(id, name, Category.TOOLS, kind, material, 1).costs(price, currency).tier(tier)
                .enchant(Enchantment.DIG_SPEED, efficiency).build();
    }

    /** What the category sells, in the order of the shop. */
    List<ShopItem> in(Category category) {
        List<ShopItem> sold = new ArrayList<>();
        for (ShopItem item : items) {
            if (item.category() == category) {
                sold.add(item);
            }
        }
        return sold;
    }

    List<ShopItem> all() {
        return items;
    }

    /** The item with this id, or null. */
    ShopItem byId(String id) {
        for (ShopItem item : items) {
            if (item.id().equals(id)) {
                return item;
            }
        }
        return null;
    }

    /** Pickaxes from wooden to diamond. */
    List<ShopItem> pickaxes() {
        return pickaxes;
    }

    /** Axes from wooden to diamond. */
    List<ShopItem> axes() {
        return axes;
    }

    /**
     * The item of a Hypixel shop id ("wool", "stone_sword", "speed_ii_potion_(45_seconds)"): the
     * one whose id is the longest found in it, so that "wooden_pickaxe" is the pickaxe and not the
     * axe. Null for an id nothing matches.
     */
    ShopItem forHypixel(String hypixelId) {
        if (hypixelId == null) {
            return null;
        }
        String key = hypixelId.toLowerCase(Locale.ROOT);
        ShopItem best = null;
        for (ShopItem item : items) {
            if (key.contains(item.id()) && (best == null || item.id().length() > best.id().length())) {
                best = item;
            }
        }
        return best;
    }

    /**
     * The 21 slots of a player's quick buy: from the Hypixel ids they imported, else the default
     * one. Null for an empty slot or an id the shop does not know.
     */
    List<ShopItem> quickBuy(List<String> hypixel) {
        List<String> ids = hypixel == null ? DEFAULT_QUICK_BUY : hypixel;
        List<ShopItem> slots = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            String id = i < ids.size() ? ids.get(i) : null;
            slots.add(hypixel == null ? (id == null ? null : byId(id)) : forHypixel(id));
        }
        return slots;
    }
}
