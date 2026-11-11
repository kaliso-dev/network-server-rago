package gg.rago.bedwars;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

/**
 * Something the item shop sells: what it gives, for how much, in which category, and how it is
 * given (a block in the team's colour, a sword that replaces the wooden one, armour kept until the
 * end, a tool with tiers...). Its id is also the word that finds it in a quick buy copied from
 * Hypixel ("wool" in "wool", "pickaxe" in "wooden_pickaxe"). Immutable.
 */
final class ShopItem {

    /** How the item is given. */
    enum Kind {
        /** Wool, clay, glass: in the colour of the buyer's team. */
        TEAM_BLOCK,
        /** Given as it is. */
        ITEM,
        /** Replaces the wooden sword. */
        SWORD,
        /** The leggings and boots of this tier, kept until the end of the game. */
        ARMOR,
        /** The next pickaxe of the buyer; one tier lost at each death. */
        PICKAXE,
        /** The next axe of the buyer; one tier lost at each death. */
        AXE,
        /** Kept until the end of the game. */
        SHEARS,
        /** A potion with an effect of its own (1.8 potions have no 45-second effects). */
        POTION,
        /** A special item of Bed Wars, named in the hotbar, that does its own thing when used (see {@link Utilities}). */
        UTILITY,
        /** Not in this version yet: shown, not sold. */
        COMING_SOON
    }

    /** What the shop takes. */
    enum Currency {
        IRON(Material.IRON_INGOT, "Iron", ChatColor.WHITE),
        GOLD(Material.GOLD_INGOT, "Gold", ChatColor.GOLD),
        DIAMOND(Material.DIAMOND, "Diamond", ChatColor.AQUA),
        EMERALD(Material.EMERALD, "Emerald", ChatColor.DARK_GREEN);

        private final Material material;
        private final String displayName;
        private final ChatColor chat;

        Currency(Material material, String displayName, ChatColor chat) {
            this.material = material;
            this.displayName = displayName;
            this.chat = chat;
        }

        Material material() {
            return material;
        }

        /** "4 Iron", "1 Emerald" (plural "Emeralds", "Diamonds"). */
        String amount(int count) {
            boolean plural = count > 1 && (this == DIAMOND || this == EMERALD);
            return chat + "" + count + " " + displayName + (plural ? "s" : "");
        }

        String displayName() {
            return displayName;
        }
    }

    /** The categories of the shop, and the hotbar manager slot of what they sell. */
    enum Category {
        BLOCKS("Blocks", Material.HARD_CLAY, "Blocks"),
        MELEE("Melee", Material.GOLD_SWORD, "Melee"),
        ARMOR("Armor", Material.CHAINMAIL_BOOTS, null),
        TOOLS("Tools", Material.STONE_PICKAXE, "Tools"),
        RANGED("Ranged", Material.BOW, "Ranged"),
        POTIONS("Potions", Material.BREWING_STAND_ITEM, "Potions"),
        UTILITY("Utility", Material.TNT, "Utility");

        private final String displayName;
        private final Material icon;
        private final String hotbar;

        Category(String displayName, Material icon, String hotbar) {
            this.displayName = displayName;
            this.icon = icon;
            this.hotbar = hotbar;
        }

        String displayName() {
            return displayName;
        }

        Material icon() {
            return icon;
        }

        /** The name of this kind of item in Hypixel's hotbar manager, or null (armour is worn). */
        String hotbar() {
            return hotbar;
        }
    }

    private final String id;
    private final String name;
    private final Category category;
    private final Kind kind;
    private final Material material;
    private final short data;
    private final int amount;
    private final Currency currency;
    private final int price;
    private final int tier;
    private final Enchantment enchantment;
    private final int enchantmentLevel;
    private final Enchantment second;
    private final int secondLevel;
    private final PotionEffectType effect;
    private final int effectLevel;
    private final int effectSeconds;

    private ShopItem(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.category = builder.category;
        this.kind = builder.kind;
        this.material = builder.material;
        this.data = builder.data;
        this.amount = builder.amount;
        this.currency = builder.currency;
        this.price = builder.price;
        this.tier = builder.tier;
        this.enchantment = builder.enchantment;
        this.enchantmentLevel = builder.enchantmentLevel;
        this.second = builder.second;
        this.secondLevel = builder.secondLevel;
        this.effect = builder.effect;
        this.effectLevel = builder.effectLevel;
        this.effectSeconds = builder.effectSeconds;
    }

    static Builder of(String id, String name, Category category, Kind kind, Material material, int amount) {
        return new Builder(id, name, category, kind, material, amount);
    }

    String id() {
        return id;
    }

    String name() {
        return name;
    }

    Category category() {
        return category;
    }

    Kind kind() {
        return kind;
    }

    Material material() {
        return material;
    }

    short data() {
        return data;
    }

    int amount() {
        return amount;
    }

    Currency currency() {
        return currency;
    }

    int price() {
        return price;
    }

    /** The armour tier: 1 chainmail, 2 iron, 3 diamond. */
    int tier() {
        return tier;
    }

    Enchantment enchantment() {
        return enchantment;
    }

    int enchantmentLevel() {
        return enchantmentLevel;
    }

    Enchantment second() {
        return second;
    }

    int secondLevel() {
        return secondLevel;
    }

    PotionEffectType effect() {
        return effect;
    }

    int effectLevel() {
        return effectLevel;
    }

    int effectSeconds() {
        return effectSeconds;
    }

    /** The same item at another price. */
    ShopItem costing(int newPrice, Currency newCurrency) {
        Builder builder = new Builder(id, name, category, kind, material, amount);
        builder.data = data;
        builder.currency = newCurrency;
        builder.price = newPrice;
        builder.tier = tier;
        builder.enchantment = enchantment;
        builder.enchantmentLevel = enchantmentLevel;
        builder.second = second;
        builder.secondLevel = secondLevel;
        builder.effect = effect;
        builder.effectLevel = effectLevel;
        builder.effectSeconds = effectSeconds;
        return builder.build();
    }

    @Override
    public String toString() {
        return id;
    }

    static final class Builder {
        private final String id;
        private final String name;
        private final Category category;
        private final Kind kind;
        private final Material material;
        private final int amount;
        private short data;
        private Currency currency = Currency.IRON;
        private int price;
        private int tier;
        private Enchantment enchantment;
        private int enchantmentLevel;
        private Enchantment second;
        private int secondLevel;
        private PotionEffectType effect;
        private int effectLevel;
        private int effectSeconds;

        private Builder(String id, String name, Category category, Kind kind, Material material, int amount) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.kind = kind;
            this.material = material;
            this.amount = amount;
        }

        Builder costs(int price, Currency currency) {
            this.price = price;
            this.currency = currency;
            return this;
        }

        Builder data(int data) {
            this.data = (short) data;
            return this;
        }

        Builder tier(int tier) {
            this.tier = tier;
            return this;
        }

        Builder enchant(Enchantment enchantment, int level) {
            if (this.enchantment == null) {
                this.enchantment = enchantment;
                this.enchantmentLevel = level;
            } else {
                this.second = enchantment;
                this.secondLevel = level;
            }
            return this;
        }

        Builder effect(PotionEffectType effect, int level, int seconds) {
            this.effect = effect;
            this.effectLevel = level;
            this.effectSeconds = seconds;
            return this;
        }

        ShopItem build() {
            return new ShopItem(this);
        }
    }
}
