package gg.rago.lobby;

import java.util.Collections;
import java.util.List;
import org.bukkit.Material;

/**
 * A rank of config.yml (rangs:): its prefix before the name ("§c[ADMIN] "), the colour of the name
 * and of the messages, its permissions (with those of the rank it inherits), and for a rank of the
 * shop its price in Rago Points and the Network level it asks. A staff rank is given with
 * /rank set, never sold. Immutable.
 */
final class RankDef {

    /** A 1.8 team prefix is 16 characters: the rank's prefix and the colour of the name fit in it. */
    static final int MAX_PREFIX = 16;

    private final String id;
    private final String prefix;
    private final String nameColour;
    private final String chatColour;
    private final boolean staff;
    private final List<String> permissions;
    private final String parent;
    private final int price;
    private final int level;
    private final Material icon;
    private final List<String> perks;

    RankDef(String id, String prefix, String nameColour, String chatColour, boolean staff, List<String> permissions,
            String parent, int price, int level, Material icon, List<String> perks) {
        this.id = id;
        this.prefix = prefix;
        this.nameColour = nameColour;
        this.chatColour = chatColour;
        this.staff = staff;
        this.permissions = Collections.unmodifiableList(permissions);
        this.parent = parent;
        this.price = price;
        this.level = level;
        this.icon = icon;
        this.perks = Collections.unmodifiableList(perks);
    }

    String id() {
        return id;
    }

    /** "§c[ADMIN] ": before the name, in the chat, above the head and in the tab list; "" for none. */
    String prefix() {
        return prefix;
    }

    /** The colour codes of the name. */
    String nameColour() {
        return nameColour;
    }

    /** The colour codes of the player's messages in the chat. */
    String chatColour() {
        return chatColour;
    }

    boolean staff() {
        return staff;
    }

    /** The permissions of this rank alone (see {@link Ranks#permissions}). */
    List<String> permissions() {
        return permissions;
    }

    /** The rank whose permissions this one also has, or null. */
    String parent() {
        return parent;
    }

    /** The price in Rago Points, 0 for a rank the shop does not sell. */
    int price() {
        return price;
    }

    boolean sold() {
        return price > 0 && !staff;
    }

    /** The Network level the shop asks before selling it. */
    int level() {
        return level;
    }

    Material icon() {
        return icon;
    }

    /** What the rank gives, for the shop. */
    List<String> perks() {
        return perks;
    }

    /** The prefix and the colour of the name, as a team prefix shows them. */
    String tagPrefix() {
        return prefix + nameColour;
    }

    /** The name of the rank without its brackets and colours: "ADMIN", or the id when it has no prefix. */
    String displayName() {
        String plain = org.bukkit.ChatColor.stripColor(prefix).trim();
        if (plain.startsWith("[") && plain.endsWith("]") && plain.length() > 2) {
            plain = plain.substring(1, plain.length() - 1);
        }
        return plain.isEmpty() ? id : plain;
    }
}
