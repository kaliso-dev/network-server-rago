package gg.rago.lobby;

import java.util.Collections;
import java.util.List;
import org.bukkit.Material;

/**
 * A rank of config.yml (rangs:): its prefix before the name ("§c[ADMIN] "), the colour of the name
 * and of the messages, its permissions (with those of the rank it inherits), and for a rank of the
 * shop its price in Rago Points and the Network level it asks. A rank can also boost the Network
 * XP of its players, give them a style of name and a badge they wear by default ({@link NameStyle}),
 * and be sold on the web store instead of the shop (the PREMIUM rank). A staff rank is given with
 * /rank set, never sold. Immutable.
 */
final class RankDef {

    /** A 1.8 team prefix is 16 characters: the rank's prefix and the colour of the name fit in it. */
    static final int MAX_PREFIX = 16;
    /**
     * In a prefix, the "+" a player colours (/style): "§d[PREMIUM{+}] " shows "§d[PREMIUM§f+§d] ",
     * white until they wear a colour of +. The colour before it comes back after it.
     */
    static final String PLUS = "{+}";
    /** The colour of the + of a player who wears none. */
    static final String DEFAULT_PLUS = "\u00a7f";

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
    private final double xpBoost;
    private final String style;
    private final String badge;
    private final boolean webStore;
    /** What it costs on the web store, as shown in the game ("$4.99"), or null. */
    private final String webPrice;
    /** Its place in the queues: the higher first (PREMIUM+ 2, VIP 1), 0 for none. */
    private final int queuePriority;

    RankDef(String id, String prefix, String nameColour, String chatColour, boolean staff, List<String> permissions,
            String parent, int price, int level, Material icon, List<String> perks) {
        this(id, prefix, nameColour, chatColour, staff, permissions, parent, price, level, icon, perks, 1.0, null, null, false);
    }

    RankDef(String id, String prefix, String nameColour, String chatColour, boolean staff, List<String> permissions,
            String parent, int price, int level, Material icon, List<String> perks, double xpBoost, String style,
            String badge, boolean webStore) {
        this(id, prefix, nameColour, chatColour, staff, permissions, parent, price, level, icon, perks, xpBoost, style, badge,
                webStore, null);
    }

    RankDef(String id, String prefix, String nameColour, String chatColour, boolean staff, List<String> permissions,
            String parent, int price, int level, Material icon, List<String> perks, double xpBoost, String style,
            String badge, boolean webStore, String webPrice) {
        this(id, prefix, nameColour, chatColour, staff, permissions, parent, price, level, icon, perks, xpBoost, style, badge,
                webStore, webPrice, 0);
    }

    RankDef(String id, String prefix, String nameColour, String chatColour, boolean staff, List<String> permissions,
            String parent, int price, int level, Material icon, List<String> perks, double xpBoost, String style,
            String badge, boolean webStore, String webPrice, int queuePriority) {
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
        this.xpBoost = xpBoost;
        this.style = style;
        this.badge = badge;
        this.webStore = webStore;
        this.webPrice = webPrice;
        this.queuePriority = queuePriority;
    }

    /** Its place in the queues: the higher first, 0 for none. */
    int queuePriority() {
        return queuePriority;
    }

    /** Its price on the web store, as shown ("$4.99"), or null when the game does not show one. */
    String webPrice() {
        return webPrice;
    }

    String id() {
        return id;
    }

    /** "§c[ADMIN] ": before the name, in the chat, above the head and in the tab list; "" for none. */
    String prefix() {
        return prefix(DEFAULT_PLUS);
    }

    /** The prefix with its + (if it has one) in these colour codes. */
    String prefix(String plusColour) {
        return render(prefix, plusColour);
    }

    /** True if the prefix has a + the player colours. */
    boolean hasPlus() {
        return prefix.contains(PLUS);
    }

    /** The prefix with its + in this colour, the colour before the + again after it. */
    static String render(String prefix, String plusColour) {
        int at = prefix.indexOf(PLUS);
        if (at < 0) {
            return prefix;
        }
        String before = prefix.substring(0, at);
        String after = prefix.substring(at + PLUS.length());
        return before + plusColour + "+" + org.bukkit.ChatColor.getLastColors(before) + render(after, plusColour);
    }

    /**
     * The prefix above the head, with the colour of the name after it, in a team prefix of
     * {@link #MAX_PREFIX} characters: as in the chat if it fits, else without its brackets
     * ("§dPREMIUM§f+ §d"), else cut.
     */
    String headPrefix(String plusColour, String nameColour) {
        return head(prefix(plusColour), nameColour);
    }

    static String head(String prefix, String nameColour) {
        String full = prefix + nameColour;
        if (full.length() <= MAX_PREFIX) {
            return full;
        }
        // Without its brackets, and without the colours that only come before the space.
        String compact = prefix.replace("[", "").replace("]", "").replaceAll("(\u00a7.)+(\\s*)$", "$2") + nameColour;
        if (compact.length() <= MAX_PREFIX) {
            return compact;
        }
        String cut = compact.substring(0, MAX_PREFIX);
        return cut.endsWith("\u00a7") ? cut.substring(0, MAX_PREFIX - 1) : cut;
    }

    /** True if this prefix and colour of name fit above the head ({@link #head}) without being cut. */
    static boolean fitsHead(String prefix, String nameColour) {
        String head = head(render(prefix, DEFAULT_PLUS), nameColour);
        return head.endsWith(nameColour) && head.length() <= MAX_PREFIX
                && org.bukkit.ChatColor.stripColor(head).replace("[", "").replace("]", "").equals(
                org.bukkit.ChatColor.stripColor(render(prefix, DEFAULT_PLUS)).replace("[", "").replace("]", ""));
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

    /** What the Network XP of its players is multiplied by: 1 for none, 1.5 for half more. */
    double xpBoost() {
        return xpBoost;
    }

    /** The id of the colour of name its players wear until they choose another ("rose"), or null. */
    String style() {
        return style;
    }

    /** The id of the badge its players wear until they choose another ("premium"), or null. */
    String badge() {
        return badge;
    }

    /** True for a rank sold on the web store (the shop shows where), not for Rago Points. */
    boolean webStore() {
        return webStore;
    }

    /** The prefix and the colour of the name, as a team prefix shows them. */
    String tagPrefix() {
        return headPrefix(DEFAULT_PLUS, nameColour);
    }

    /** The name of the rank without its brackets and colours: "ADMIN", or the id when it has no prefix. */
    String displayName() {
        String plain = org.bukkit.ChatColor.stripColor(prefix()).trim();
        if (plain.startsWith("[") && plain.endsWith("]") && plain.length() > 2) {
            plain = plain.substring(1, plain.length() - 1);
        }
        return plain.isEmpty() ? id : plain;
    }
}
