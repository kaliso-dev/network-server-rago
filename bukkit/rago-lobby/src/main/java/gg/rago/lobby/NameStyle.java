package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.ChatColor;

/**
 * A style of a player's name: a colour of the name, one colour or a gradient along its letters
 * (made of the 16 colours of 1.8), or a badge, a short word after the name ("§c§lRAGO"). A player
 * has a style in one of four ways: with the PREMIUM rank, bought on the store (the store, or the
 * staff, gives it with /style give), bought with Rago Points in /style, or as the reward of a
 * Network level. They wear one colour and one badge at a time. A gradient is animated (its colours
 * run along the name, in the tab list, above the head and from one message to the next) unless
 * it is a fixed one. The styles come from config.yml (styles:), else the plugin's own. Immutable.
 */
final class NameStyle {

    enum Kind {
        COLOUR("couleur", "couleurs", "Name colours"),
        BADGE("badge", "badges", "Badges");

        private final String id;
        private final String section;
        private final String title;

        Kind(String id, String section, String title) {
            this.id = id;
            this.section = section;
            this.title = title;
        }

        /** "couleur" or "badge": the first word of the key of a style the player owns. */
        String id() {
            return id;
        }

        /** The section of config.yml (styles.couleurs, styles.badges). */
        String section() {
            return section;
        }

        String title() {
            return title;
        }

        static Kind of(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id) || kind.section.equals(id)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** How a player gets the style. */
    enum Obtain {
        /** With the PREMIUM rank (or a rank that inherits it). */
        PREMIUM("premium"),
        /** Bought on the store: given with /style give. */
        STORE("achat"),
        /** A reward of a Network level (niveau.recompenses). */
        LEVEL("niveau"),
        /** Bought with Rago Points in /style (prix). */
        POINTS("points");

        private final String word;

        Obtain(String word) {
            this.word = word;
        }

        static Obtain of(String word) {
            for (Obtain obtain : values()) {
                if (obtain.word.equals(word)) {
                    return obtain;
                }
            }
            return null;
        }
    }

    /** A badge after a name shows in a team suffix of 16 characters, after a space. */
    static final int MAX_BADGE = 15;

    private final Kind kind;
    private final String id;
    private final String name;
    /** A colour: its colour characters, first letter to last ("5ddfd"); a badge: its text, coloured. */
    private final String text;
    private final boolean bold;
    private final Obtain obtain;
    private final boolean animated;
    private final int price;

    NameStyle(Kind kind, String id, String name, String text, boolean bold, Obtain obtain) {
        this(kind, id, name, text, bold, obtain, kind == Kind.COLOUR && text.length() > 1, 0);
    }

    NameStyle(Kind kind, String id, String name, String text, boolean bold, Obtain obtain, boolean animated, int price) {
        this.animated = animated && kind == Kind.COLOUR && text.length() > 1;
        this.price = price;
        this.kind = kind;
        this.id = id;
        this.name = name;
        this.text = text;
        this.bold = bold;
        this.obtain = obtain;
    }

    Kind kind() {
        return kind;
    }

    String id() {
        return id;
    }

    /** "couleur-rose", "badge-rago": the key of the style among what a player owns. */
    String key() {
        return key(kind, id);
    }

    static String key(Kind kind, String id) {
        return kind.id() + "-" + id;
    }

    String name() {
        return name;
    }

    Obtain obtain() {
        return obtain;
    }

    /** True for a gradient whose colours run along the name. */
    boolean animated() {
        return animated;
    }

    /** The price in Rago Points of a style bought in /style (POINTS), else 0. */
    int price() {
        return price;
    }

    /** The colour characters of a colour, or the coloured text of a badge. */
    String text() {
        return text;
    }

    /** A name in this colour (a colour), or the badge itself (a badge). */
    String apply(String plain) {
        return apply(plain, 0);
    }

    /** A name in this colour at this step of its animation (a fixed colour: always the same). */
    String apply(String plain, int phase) {
        return kind == Kind.BADGE ? text : animated ? gradient(plain, text, bold, phase) : gradient(plain, text, bold);
    }

    /** The badge, or how the name "Rago" looks in this colour: for the menus. */
    String sample() {
        return kind == Kind.BADGE ? text : gradient(name, text, bold);
    }

    /** The colour code in the middle of a colour: what shows above the head, where one colour fits. */
    String mainColour() {
        return mainColour(0);
    }

    /** The colour above the head at this step of the animation: it runs through the gradient. */
    String mainColour(int phase) {
        if (kind == Kind.BADGE || text.isEmpty()) {
            return ChatColor.GRAY.toString();
        }
        if (!animated) {
            return "§" + text.charAt(text.length() / 2) + (bold ? "§l" : "");
        }
        String cycle = cycle(text);
        return "§" + cycle.charAt(Math.floorMod(text.length() / 2 + phase, cycle.length())) + (bold ? "§l" : "");
    }

    /** The colours an animated gradient runs through: there and back ("4c6e" gives "4c6e6c"). */
    static String cycle(String stops) {
        StringBuilder cycle = new StringBuilder(stops);
        for (int i = stops.length() - 2; i >= 1; i--) {
            cycle.append(stops.charAt(i));
        }
        return cycle.toString();
    }

    /**
     * The gradient moved on by {@code phase} steps: each letter takes the colour of its place along
     * the stops, so many colours further in the cycle there and back. Phase 0 is the gradient itself.
     */
    static String gradient(String text, String stops, boolean bold, int phase) {
        if (text.isEmpty() || stops.length() < 2) {
            return gradient(text, stops, bold);
        }
        String cycle = cycle(stops);
        StringBuilder colours = new StringBuilder();
        int letters = text.length();
        for (int i = 0; i < letters; i++) {
            int stop = letters == 1 ? 0 : (int) Math.round(i * (stops.length() - 1) / (double) (letters - 1));
            colours.append(cycle.charAt(Math.floorMod(stop + phase, cycle.length())));
        }
        StringBuilder out = new StringBuilder();
        char current = 0;
        for (int i = 0; i < letters; i++) {
            char colour = colours.charAt(i);
            if (colour != current) {
                out.append('§').append(colour);
                if (bold) {
                    out.append("§l");
                }
                current = colour;
            }
            out.append(text.charAt(i));
        }
        return out.toString();
    }

    /**
     * The text with its letters coloured from the first colour to the last: each letter takes the
     * colour at its place along the stops. A colour code is written only where the colour changes
     * (and again before each letter when bold, since a colour ends the bold).
     */
    static String gradient(String text, String stops, boolean bold) {
        if (text.isEmpty() || stops.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        char current = 0;
        int letters = text.length();
        for (int i = 0; i < letters; i++) {
            int stop = stops.length() == 1 || letters == 1 ? 0
                    : (int) Math.round(i * (stops.length() - 1) / (double) (letters - 1));
            char colour = stops.charAt(stop);
            if (colour != current) {
                out.append('§').append(colour);
                if (bold) {
                    out.append("§l");
                }
                current = colour;
            }
            out.append(text.charAt(i));
        }
        return out.toString();
    }

    /** True if these are colour characters (0 to 9, a to f), 1 to 16 of them. */
    static boolean isStops(String stops) {
        return stops != null && stops.matches("[0-9a-f]{1,16}");
    }

    /** The plugin's own styles, by key: the colours, then the badges. */
    static Map<String, NameStyle> catalogue() {
        List<NameStyle> all = new ArrayList<>();
        all.add(new NameStyle(Kind.COLOUR, "rose", "Pink", "5ddfd", false, Obtain.PREMIUM));
        all.add(new NameStyle(Kind.COLOUR, "feu", "Fire", "4c6e", false, Obtain.STORE));
        all.add(new NameStyle(Kind.COLOUR, "ocean", "Ocean", "19b3", false, Obtain.STORE));
        all.add(new NameStyle(Kind.COLOUR, "foret", "Forest", "2ae", false, Obtain.STORE));
        all.add(new NameStyle(Kind.COLOUR, "coucher", "Sunset", "5dc6e", false, Obtain.STORE));
        all.add(new NameStyle(Kind.COLOUR, "glace", "Ice", "fb3b", false, Obtain.STORE));
        all.add(new NameStyle(Kind.COLOUR, "galaxie", "Galaxy", "159d", false, Obtain.STORE));
        all.add(new NameStyle(Kind.COLOUR, "arc-en-ciel", "Rainbow", "c6eab9d", false, Obtain.STORE));
        all.add(new NameStyle(Kind.COLOUR, "ombre", "Shadow", "0878", false, Obtain.STORE));
        all.add(new NameStyle(Kind.COLOUR, "aqua", "Aqua", "b", false, Obtain.LEVEL));
        all.add(new NameStyle(Kind.COLOUR, "or", "Gold", "6", false, Obtain.LEVEL));
        all.add(new NameStyle(Kind.COLOUR, "legende", "Legend", "6eef6", true, Obtain.LEVEL));
        // The fixed colours, bought with Rago Points in /style.
        all.add(new NameStyle(Kind.COLOUR, "rouge", "Red", "c", false, Obtain.POINTS, false, 3000));
        all.add(new NameStyle(Kind.COLOUR, "vert", "Green", "a", false, Obtain.POINTS, false, 3000));
        all.add(new NameStyle(Kind.COLOUR, "bleu", "Blue", "9", false, Obtain.POINTS, false, 3000));
        all.add(new NameStyle(Kind.COLOUR, "jaune", "Yellow", "e", false, Obtain.POINTS, false, 3000));
        all.add(new NameStyle(Kind.COLOUR, "violet", "Purple", "5", false, Obtain.POINTS, false, 3000));
        all.add(new NameStyle(Kind.COLOUR, "blanc", "White", "f", false, Obtain.POINTS, false, 3000));
        all.add(new NameStyle(Kind.COLOUR, "cerise", "Cherry", "c4", false, Obtain.POINTS, false, 6000));
        all.add(new NameStyle(Kind.COLOUR, "menthe", "Mint", "ab", false, Obtain.POINTS, false, 6000));
        all.add(new NameStyle(Kind.BADGE, "premium", "Premium", "§d§l✦", false, Obtain.PREMIUM));
        all.add(new NameStyle(Kind.BADGE, "rago", "RAGO", "§c§lRAGO", false, Obtain.STORE));
        all.add(new NameStyle(Kind.BADGE, "better", "BETTER", "§6§lBETTER", false, Obtain.STORE));
        all.add(new NameStyle(Kind.BADGE, "pvp", "PVP", "§4§lPVP", false, Obtain.STORE));
        all.add(new NameStyle(Kind.BADGE, "yt", "YT", "§c§lYT", false, Obtain.STORE));
        all.add(new NameStyle(Kind.BADGE, "centurion", "Centurion", "§e§l✫100", false, Obtain.LEVEL));
        Map<String, NameStyle> byKey = new LinkedHashMap<>();
        for (NameStyle style : all) {
            byKey.put(style.key(), style);
        }
        return Collections.unmodifiableMap(byKey);
    }

    /** "couleur rose", "badge rago": for the logs. */
    @Override
    public String toString() {
        return kind.id() + " " + id.toLowerCase(Locale.ROOT);
    }
}
