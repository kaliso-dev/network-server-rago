package gg.rago.practice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.ChatColor;

/**
 * The recap of a duel, told to its players at its end, as the "match details" of the practice
 * servers: for each one, the hits they landed and their best combo (hits in a row without being
 * hit back); then, when the duel had them, the health they had left, the golden apples they ate,
 * the blocks they placed and the beds they broke. Only the hits that went through count (not
 * those in the hit delay). Main thread only.
 */
final class MatchRecap {

    private static final int HITS = 0;
    private static final int COMBO = 1;
    private static final int BEST = 2;
    private static final int APPLES = 3;
    private static final int BLOCKS = 4;
    private static final int BEDS = 5;

    /** Its first line. */
    static final String TITLE = ChatColor.GOLD + "" + ChatColor.BOLD + "Match recap";

    /** Hits landed, the combo running, the best combo, golden apples eaten, blocks placed, beds broken. */
    private final Map<UUID, int[]> counts = new HashMap<>();
    /** The health of each player at the end, in half hearts (0 for one out of the fight). */
    private final Map<UUID, Double> health = new HashMap<>();
    /** True once a hit or a fall hurt someone: the health left is worth telling. */
    private boolean hurt;

    /** A hit of the attacker on the victim: the attacker's combo goes on, the victim's ends. */
    void hit(UUID attacker, UUID victim) {
        int[] mine = of(attacker);
        mine[HITS]++;
        mine[COMBO]++;
        mine[BEST] = Math.max(mine[BEST], mine[COMBO]);
        if (victim != null && !victim.equals(attacker)) {
            of(victim)[COMBO] = 0;
        }
    }

    /** The player ate a golden apple. */
    void ate(UUID player) {
        of(player)[APPLES]++;
    }

    /** The player placed a block. */
    void placed(UUID player) {
        of(player)[BLOCKS]++;
    }

    /** The player broke a bed. */
    void bed(UUID player) {
        of(player)[BEDS]++;
    }

    /** Someone lost health in the duel. */
    void hurt() {
        hurt = true;
    }

    /** The player's health at the end, in half hearts (20 for full health, 0 for one out). */
    void health(UUID player, double halfHearts) {
        health.put(player, Math.max(0, halfHearts));
    }

    int hits(UUID player) {
        return count(player, HITS);
    }

    int bestCombo(UUID player) {
        return count(player, BEST);
    }

    int apples(UUID player) {
        return count(player, APPLES);
    }

    int blocks(UUID player) {
        return count(player, BLOCKS);
    }

    int beds(UUID player) {
        return count(player, BEDS);
    }

    private int count(UUID player, int what) {
        int[] counted = counts.get(player);
        return counted == null ? 0 : counted[what];
    }

    /** No hit, no block, nothing at all: nothing to tell. */
    boolean isEmpty() {
        return counts.isEmpty();
    }

    private int[] of(UUID player) {
        int[] counted = counts.get(player);
        if (counted == null) {
            counted = new int[6];
            counts.put(player, counted);
        }
        return counted;
    }

    /** True if someone has some of this. */
    private boolean any(int what) {
        for (int[] counted : counts.values()) {
            if (counted[what] > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * The player's numbers, for their inventory of the end: the hits and the best combo, then the
     * golden apples, the blocks and the beds when the duel had them.
     */
    List<String> numbers(UUID player) {
        List<String> numbers = new ArrayList<>();
        numbers.add(ChatColor.GRAY + "Hits: " + ChatColor.WHITE + hits(player));
        numbers.add(ChatColor.GRAY + "Best combo: " + ChatColor.WHITE + bestCombo(player));
        if (any(APPLES)) {
            numbers.add(ChatColor.GRAY + "Golden apples eaten: " + ChatColor.WHITE + apples(player));
        }
        if (any(BLOCKS)) {
            numbers.add(ChatColor.GRAY + "Blocks placed: " + ChatColor.WHITE + blocks(player));
        }
        if (any(BEDS)) {
            numbers.add(ChatColor.GRAY + "Beds broken: " + ChatColor.WHITE + beds(player));
        }
        return numbers;
    }

    /** "Alice: 34 hits, best combo 7", after " - ", the name in the colour of their side (its own colour left out). */
    String head(UUID player, String name, ChatColor colour) {
        int hits = hits(player);
        return ChatColor.DARK_GRAY + " - " + colour + ChatColor.stripColor(name) + ChatColor.GRAY + ": " + ChatColor.WHITE + hits
                + ChatColor.GRAY + (hits == 1 ? " hit" : " hits") + ", best combo " + ChatColor.WHITE + bestCombo(player);
    }

    /**
     * The second line of a player, or null when the duel had none of it: the health they had left
     * (once someone was hurt), the golden apples they ate, the blocks they placed, the beds they
     * broke, each when someone in the duel did it.
     */
    String details(UUID player) {
        List<String> parts = new ArrayList<>();
        if (hurt) {
            Double left = health.get(player);
            parts.add(ChatColor.RED + hearts(left == null ? 0 : left) + "❤" + ChatColor.GRAY + " left");
        }
        if (any(APPLES)) {
            parts.add(amount(apples(player), "golden apple", "golden apples"));
        }
        if (any(BLOCKS)) {
            parts.add(amount(blocks(player), "block", "blocks"));
        }
        if (any(BEDS)) {
            parts.add(amount(beds(player), "bed broken", "beds broken"));
        }
        return parts.isEmpty() ? null : "   " + String.join(ChatColor.GRAY + ", ", parts);
    }

    private static String amount(int count, String one, String many) {
        return ChatColor.WHITE + "" + count + ChatColor.GRAY + " " + (count == 1 ? one : many);
    }

    /** Half hearts as hearts, rounded up as the game shows them: 15 is "7.5", 20 is "10", 13.6 is "7". */
    static String hearts(double halfHearts) {
        double hearts = Math.ceil(Math.max(0, halfHearts)) / 2.0;
        return hearts == Math.floor(hearts) ? String.valueOf((int) hearts) : String.format(Locale.ROOT, "%.1f", hearts);
    }

    /**
     * The lines of the recap: the title, then for each player their line and their second one,
     * side by side in their colour ("Alice - 34 hits, best combo 7").
     */
    List<String> lines(List<List<UUID>> sides, Function<UUID, String> names, ChatColor[] colours) {
        List<String> lines = new ArrayList<>();
        lines.add(TITLE);
        for (int side = 0; side < sides.size(); side++) {
            ChatColor colour = colours[side % colours.length];
            for (UUID player : sides.get(side)) {
                lines.add(head(player, names.apply(player), colour));
                String more = details(player);
                if (more != null) {
                    lines.add(more);
                }
            }
        }
        return lines;
    }
}
