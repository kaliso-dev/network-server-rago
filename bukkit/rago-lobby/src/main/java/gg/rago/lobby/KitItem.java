package gg.rago.lobby;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** One stack of a kit: "IRON_SWORD", "WOOL 64", "WOOL:14 64" in config.yml. Immutable. */
final class KitItem {

    private final Material material;
    private final short data;
    private final int amount;

    KitItem(Material material, short data, int amount) {
        if (material == null || material == Material.AIR) {
            throw new IllegalArgumentException("no item");
        }
        if (amount < 1 || amount > 64) {
            throw new IllegalArgumentException("from 1 to 64 items in a stack");
        }
        this.material = material;
        this.data = data;
        this.amount = amount;
    }

    /**
     * "IRON_SWORD", "WOOL 64" or "WOOL:14 64" (data 14: red wool); null for an empty slot ("" or
     * "AIR"). Throws {@link IllegalArgumentException} with what is wrong.
     */
    static KitItem parse(String text) {
        String spec = text == null ? "" : text.trim();
        if (spec.isEmpty() || spec.equalsIgnoreCase("AIR")) {
            return null;
        }
        String[] parts = spec.split("\\s+");
        if (parts.length > 2) {
            throw new IllegalArgumentException("\"" + spec + "\": write ITEM, ITEM amount or ITEM:data amount");
        }
        String name = parts[0];
        short data = 0;
        int colon = name.indexOf(':');
        if (colon >= 0) {
            try {
                data = Short.parseShort(name.substring(colon + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("\"" + spec + "\": the data after ':' is a number");
            }
            name = name.substring(0, colon);
        }
        Material material = Material.matchMaterial(name);
        if (material == null || material == Material.AIR) {
            throw new IllegalArgumentException("\"" + name + "\" is not a Minecraft 1.8 item");
        }
        int amount = 1;
        if (parts.length == 2) {
            try {
                amount = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("\"" + spec + "\": the amount is a number");
            }
        }
        if (amount < 1 || amount > 64) {
            throw new IllegalArgumentException("\"" + spec + "\": from 1 to 64 items in a stack");
        }
        return new KitItem(material, data, amount);
    }

    /** The stack in a player's inventory, or null for anything that is not a whole kit stack. */
    @SuppressWarnings("deprecation") // ItemStack#getDurability is the 1.8 data value
    static KitItem of(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() < 1 || stack.getAmount() > 64) {
            return null;
        }
        return new KitItem(stack.getType(), stack.getDurability(), stack.getAmount());
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

    ItemStack toItem() {
        return new ItemStack(material, amount, data);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof KitItem)) {
            return false;
        }
        KitItem item = (KitItem) other;
        return material == item.material && data == item.data && amount == item.amount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(material, data, amount);
    }

    @Override
    public String toString() {
        return material + (data != 0 ? ":" + data : "") + (amount != 1 ? " " + amount : "");
    }
}
