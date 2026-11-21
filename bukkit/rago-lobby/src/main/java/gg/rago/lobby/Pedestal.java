package gg.rago.lobby;

import org.bukkit.Material;

/**
 * The block a figure of the lobby stands on, as on Hypixel: polished andesite, or the block its
 * NPC has in config.yml (pnj: ... bloc:), quartz under the NPCs of the modes, iron under the stats
 * NPC, emerald under the Shop (plan 5.3). Immutable.
 */
final class Pedestal {

    /** Polished andesite (a stone of data 6 in 1.8): the block of an NPC without bloc:. */
    static final Pedestal ANDESITE = new Pedestal(Material.STONE, (byte) 6, false);

    private final Material material;
    private final byte data;
    private final boolean chosen;

    private Pedestal(Material material, byte data, boolean chosen) {
        this.material = material;
        this.data = data;
        this.chosen = chosen;
    }

    /**
     * The block of bloc: a name or an id, with its data after a colon ("QUARTZ_BLOCK", "STONE:6",
     * "159:14" for red stained clay); a solid block.
     */
    static Pedestal parse(String text) {
        String[] parts = text.trim().split(":", -1);
        if (parts.length > 2 || parts[0].trim().isEmpty()) {
            throw new IllegalArgumentException("\"" + text + "\" is not a block (a name, or name:data)");
        }
        Material material = Material.matchMaterial(parts[0].trim());
        if (material == null || !material.isBlock() || !material.isSolid()) {
            throw new IllegalArgumentException("\"" + text + "\" is not a solid block");
        }
        int data = 0;
        if (parts.length == 2) {
            try {
                data = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("\"" + text + "\": its data is a number from 0 to 15", e);
            }
            if (data < 0 || data > 15) {
                throw new IllegalArgumentException("\"" + text + "\": its data is a number from 0 to 15");
            }
        }
        return new Pedestal(material, (byte) data, true);
    }

    Material material() {
        return material;
    }

    byte data() {
        return data;
    }

    /**
     * True if a block of this type and data already is this pedestal. Only for a block chosen in
     * config.yml: one the builder put under the point is kept, the NPC stands on it.
     */
    boolean builtUnder(Material type, byte blockData) {
        return chosen && type == material && blockData == data;
    }

    @Override
    public String toString() {
        return material + (data == 0 ? "" : ":" + data);
    }
}
