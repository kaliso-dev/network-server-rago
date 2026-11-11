package gg.rago.practice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.ChatColor;

/**
 * The recap of a duel, told to its players at its end: for each one, the hits they landed and
 * their best combo (hits in a row without being hit back), as the "match details" of the practice
 * servers. Only the hits that went through count (not those in the hit delay). Main thread only.
 */
final class MatchRecap {

    /** Hits landed, the combo running, the best combo. */
    private final Map<UUID, int[]> counts = new HashMap<>();

    /** A hit of the attacker on the victim: the attacker's combo goes on, the victim's ends. */
    void hit(UUID attacker, UUID victim) {
        int[] mine = of(attacker);
        mine[0]++;
        mine[1]++;
        mine[2] = Math.max(mine[2], mine[1]);
        if (victim != null && !victim.equals(attacker)) {
            of(victim)[1] = 0;
        }
    }

    int hits(UUID player) {
        int[] counted = counts.get(player);
        return counted == null ? 0 : counted[0];
    }

    int bestCombo(UUID player) {
        int[] counted = counts.get(player);
        return counted == null ? 0 : counted[2];
    }

    /** No hit at all: nothing to tell. */
    boolean isEmpty() {
        return counts.isEmpty();
    }

    private int[] of(UUID player) {
        int[] counted = counts.get(player);
        if (counted == null) {
            counted = new int[3];
            counts.put(player, counted);
        }
        return counted;
    }

    /**
     * The lines of the recap: a title, then a line per player, side by side in their colour
     * ("Alice - 34 hits, best combo 7").
     */
    List<String> lines(List<List<UUID>> sides, Function<UUID, String> names, ChatColor[] colours) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Match recap");
        for (int side = 0; side < sides.size(); side++) {
            ChatColor colour = colours[side % colours.length];
            for (UUID player : sides.get(side)) {
                int hits = hits(player);
                lines.add(ChatColor.DARK_GRAY + " - " + colour + ChatColor.stripColor(names.apply(player)) + ChatColor.GRAY + ": "
                        + ChatColor.WHITE + hits + ChatColor.GRAY + (hits == 1 ? " hit" : " hits") + ", best combo "
                        + ChatColor.WHITE + bestCombo(player));
            }
        }
        return lines;
    }
}
