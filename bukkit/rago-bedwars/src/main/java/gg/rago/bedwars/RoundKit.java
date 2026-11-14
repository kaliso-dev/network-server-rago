package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * A kit a player chooses before a round of Attack and Defense or of the Bed Break Simulator: its
 * name, its icon, a few words, and its items, those of the item shop by their id, with their
 * amount ("wool 64", "fireball 3", golden_pickaxe). Three for the attackers and three for the
 * defenders of Attack and Defense by default (attaque-defense: in config.yml), three for the
 * attacker of the Bed Break Simulator (bed-break:). Immutable.
 */
final class RoundKit {

    /** The most kits of a side: one row of the menu. */
    static final int MAX = 7;
    private static final int MAX_NAME = 24;
    private static final int MAX_LINES = 4;

    /** The attackers' kits: one life, iron armour, the kit once. */
    static final List<RoundKit> ATTACK = Collections.unmodifiableList(Arrays.asList(
            new RoundKit("Rusher", Material.FIREBALL, Arrays.asList("Fireballs to clear the bridge", "and throw the defenders off;",
                    "the Golden Pickaxe digs fast."), Arrays.asList("stone_sword", "wool 64", "fireball 3", "golden_pickaxe",
                    "shears")),
            new RoundKit("Breaker", Material.TNT, Arrays.asList("TNT blows up the wool and the", "end stone of the defense; the",
                    "Diamond Pickaxe does the rest."), Arrays.asList("stone_sword", "wool 32", "tnt 2", "diamond_pickaxe",
                    "shears")),
            new RoundKit("Ninja", Material.ENDER_PEARL, Arrays.asList("Invisible for 30 seconds, then", "a pearl to land on the bed."),
                    Arrays.asList("stone_sword", "wool 32", "invisibility", "ender_pearl", "iron_pickaxe", "shears"))));

    /** The defenders' kits: given at their first life of the round, then they come back as in a game. */
    static final List<RoundKit> DEFENSE = Collections.unmodifiableList(Arrays.asList(
            new RoundKit("Guardian", Material.STICK, Arrays.asList("A stone sword, and the stick", "to knock them off at the door."),
                    Arrays.asList("stone_sword", "stick", "wool 16", "golden_apple")),
            new RoundKit("Archer", Material.BOW, Arrays.asList("Shoot them off the bridge", "before they land."),
                    Arrays.asList("bow", "arrow 24", "wool 16")),
            new RoundKit("Builder", Material.ENDER_STONE, Arrays.asList("End stone and wool to build", "the defense up again as they",
                    "break it; shears for good."), Arrays.asList("end_stone 24", "wool 32", "shears"))));

    /**
     * The attacker's kits of the Bed Break Simulator, the owner's three: the classic one (a tier III
     * pickaxe, two stacks of blocks), the fireball one (a fireball, a tier II pickaxe, two stacks),
     * the fighter (a tier I pickaxe, a golden apple, ladders, the knockback stick); each with a stone
     * sword.
     */
    static final List<RoundKit> BED_BREAK = Collections.unmodifiableList(Arrays.asList(
            new RoundKit("Classic", Material.GOLD_PICKAXE, Arrays.asList("The Golden Pickaxe (tier III)", "digs the end stone fast:",
                    "two stacks to bridge in."), Arrays.asList("stone_sword", "golden_pickaxe", "wool 64", "wool 64")),
            new RoundKit("Fireball", Material.FIREBALL, Arrays.asList("A fireball to blow the wool and", "knock a defender off, the",
                    "Iron Pickaxe (tier II), two stacks."), Arrays.asList("stone_sword", "fireball", "iron_pickaxe", "wool 64",
                    "wool 64")),
            new RoundKit("Fighter", Material.STICK, Arrays.asList("Knock them off with the stick,", "a golden apple to last, ladders",
                    "to climb; the Wooden Pickaxe."), Arrays.asList("stone_sword", "wooden_pickaxe", "golden_apple", "ladder 8",
                    "stick"))));

    final String name;
    final Material icon;
    final List<String> description;
    /** "id" or "id amount", each a valid item of the shop. */
    final List<String> items;

    RoundKit(String name, Material icon, List<String> description, List<String> items) {
        this.name = name;
        this.icon = icon;
        this.description = Collections.unmodifiableList(new ArrayList<>(description));
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    /** One item of a kit: what the shop sells, and how many. */
    static final class Entry {
        final ShopItem item;
        final int amount;

        Entry(ShopItem item, int amount) {
            this.item = item;
            this.amount = amount;
        }
    }

    /** The items of this kit in the shop. */
    List<Entry> entries(ShopCatalog catalog) {
        List<Entry> entries = new ArrayList<>();
        for (String item : items) {
            entries.add(entry(catalog, item, "kit " + name));
        }
        return entries;
    }

    /** "wool 64": the wool of the shop, 64 of it. Throws IllegalArgumentException naming the entry. */
    static Entry entry(ShopCatalog catalog, String text, String where) {
        String[] parts = text.trim().split("\\s+");
        String id = parts[0].toLowerCase(Locale.ROOT);
        ShopItem item = parts.length > 2 ? null : InvisGame.catalogItem(catalog, id);
        if (item == null) {
            throw new IllegalArgumentException(where + ": \"" + text + "\" is not an item of the shop with its amount"
                    + " (wool 64, fireball 3, golden_pickaxe...)");
        }
        int amount = 1;
        if (parts.length == 2) {
            try {
                amount = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                amount = 0;
            }
            if (amount < 1 || amount > 64) {
                throw new IllegalArgumentException(where + ": \"" + text + "\", the amount goes from 1 to 64");
            }
        }
        return new Entry(item, amount);
    }

    /** "Wool x64", "Golden Pickaxe": the item as the menu lists it. */
    static String label(Entry entry) {
        return entry.item.name() + (entry.amount > 1 ? " x" + entry.amount : "");
    }

    /**
     * The kits of a side in config.yml (a list of nom, icone, description, objets), or these
     * defaults when there is none. Throws IllegalArgumentException naming the wrong setting.
     */
    static List<RoundKit> read(List<?> raw, String where, List<RoundKit> defaults) {
        if (raw == null || raw.isEmpty()) {
            return defaults;
        }
        if (raw.size() > MAX) {
            throw new IllegalArgumentException(where + ": " + MAX + " kits at most");
        }
        ShopCatalog catalog = ShopCatalog.standard();
        List<RoundKit> kits = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String at = where + "[" + (i + 1) + "]";
            if (!(raw.get(i) instanceof Map)) {
                throw new IllegalArgumentException(at + ": a kit, with nom, icone, description and objets");
            }
            Map<?, ?> map = (Map<?, ?>) raw.get(i);
            Object name = map.get("nom");
            if (name == null || String.valueOf(name).trim().isEmpty() || String.valueOf(name).length() > MAX_NAME) {
                throw new IllegalArgumentException(at + ".nom: the name of the kit, " + MAX_NAME + " letters at most");
            }
            Material icon = map.get("icone") == null ? null : Material.matchMaterial(String.valueOf(map.get("icone")));
            if (icon == null || icon == Material.AIR) {
                throw new IllegalArgumentException(at + ".icone: a block or an item (FIREBALL, TNT, BOW...)");
            }
            List<String> lines = new ArrayList<>();
            Object description = map.get("description");
            if (description instanceof List) {
                for (Object line : (List<?>) description) {
                    lines.add(ChatColor.translateAlternateColorCodes('&', String.valueOf(line)));
                }
            } else if (description != null) {
                lines.add(ChatColor.translateAlternateColorCodes('&', String.valueOf(description)));
            }
            if (lines.size() > MAX_LINES) {
                throw new IllegalArgumentException(at + ".description: " + MAX_LINES + " lines at most");
            }
            Object objects = map.get("objets");
            if (!(objects instanceof List) || ((List<?>) objects).isEmpty()) {
                throw new IllegalArgumentException(at + ".objets: the items of the kit (stone_sword, \"wool 64\"...)");
            }
            List<String> items = new ArrayList<>();
            for (Object object : (List<?>) objects) {
                entry(catalog, String.valueOf(object), at + ".objets");
                items.add(String.valueOf(object).trim());
            }
            kits.add(new RoundKit(ChatColor.translateAlternateColorCodes('&', String.valueOf(name).trim()), icon, lines, items));
        }
        return Collections.unmodifiableList(kits);
    }
}
