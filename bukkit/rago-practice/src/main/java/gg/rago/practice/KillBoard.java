package gg.rago.practice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

/**
 * The board of the players with the most kills ever in a mode, floating in the air of the bridge
 * fight, where everyone sees it: a title, then the ten first, each with their rank and their
 * kills (as the board of the FFA of Bed Wars). Lines of invisible armour stands that nothing can
 * touch; a line is sent again only when it changes. Main thread only.
 */
final class KillBoard {

    /** The players on the board. */
    static final int ROWS = 10;
    /** Space between two lines, in blocks. */
    static final double LINE = 0.27;
    /** The colour of the place of the first three, then of the others. */
    private static final ChatColor[] PLACES = {ChatColor.GOLD, ChatColor.WHITE, ChatColor.RED, ChatColor.GRAY};
    /** Names of 1.8 entities are 64 characters at most. */
    private static final int MAX_LINE = 64;

    private final List<ArmorStand> stands = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();

    /** The board, its top line here. */
    KillBoard(Location top) {
        top.getChunk().load();
        for (int i = 0; i < ROWS + 2; i++) {
            ArmorStand stand = top.getWorld().spawn(top.clone().subtract(0, i * LINE, 0), ArmorStand.class);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setBasePlate(false);
            try {
                // a marker has no box: the hits and clicks go through it
                ArmorStand.class.getMethod("setMarker", boolean.class).invoke(stand, true);
            } catch (ReflectiveOperationException e) {
                // an older server: a box, high in the air
            }
            stand.setCustomName(" ");
            stand.setCustomNameVisible(false);
            stands.add(stand);
            shown.add("");
        }
    }

    /** These lines, top to bottom: the stands under the last one show nothing. */
    void show(List<String> lines) {
        for (int i = 0; i < stands.size(); i++) {
            String text = i < lines.size() ? lines.get(i) : "";
            if (text.length() > MAX_LINE) {
                text = text.substring(0, MAX_LINE);
            }
            if (text.equals(shown.get(i))) {
                continue;
            }
            ArmorStand stand = stands.get(i);
            stand.setCustomName(text.isEmpty() ? " " : text);
            stand.setCustomNameVisible(!text.isEmpty());
            shown.set(i, text);
        }
    }

    /** The colour of this place (from 0): gold, white, red, then gray. */
    static ChatColor place(int i) {
        return PLACES[Math.min(i, PLACES.length - 1)];
    }

    void remove() {
        for (ArmorStand stand : stands) {
            stand.remove();
        }
        stands.clear();
        shown.clear();
    }

    /**
     * The lines of the board: "TOP KILLS", "Lifetime", then "1. [PREMIUM+] Sofiene - 245" for each
     * of the first (their name as the lobby gives it, with their rank), or "Nobody yet".
     */
    static List<String> lines(List<Map.Entry<String, Integer>> top) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "TOP KILLS");
        lines.add(ChatColor.GRAY + "Lifetime");
        if (top.isEmpty()) {
            lines.add(ChatColor.GRAY + "Nobody yet");
        }
        for (int i = 0; i < Math.min(ROWS, top.size()); i++) {
            Map.Entry<String, Integer> row = top.get(i);
            lines.add(PLACES[Math.min(i, PLACES.length - 1)] + "" + ChatColor.BOLD + (i + 1) + ". " + ChatColor.RESET
                    + row.getKey() + ChatColor.DARK_GRAY + " - " + ChatColor.YELLOW + row.getValue());
        }
        return lines;
    }

    /**
     * The lines of the players with the most kills in the game, for a sidebar: "Top kills", then
     * "1. Sofiene 12" for each of the first with a kill (16 letters of their name at most), or
     * "Nobody yet". Ties keep the order given.
     */
    static List<String> topLines(List<Map.Entry<String, Integer>> players, int rows) {
        List<Map.Entry<String, Integer>> top = new ArrayList<>();
        for (Map.Entry<String, Integer> player : players) {
            if (player.getValue() > 0) {
                top.add(player);
            }
        }
        java.util.Collections.sort(top, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Top kills");
        if (top.isEmpty()) {
            lines.add(ChatColor.GRAY + "Nobody yet");
        }
        for (int i = 0; i < Math.min(rows, top.size()); i++) {
            String name = top.get(i).getKey();
            lines.add(PLACES[Math.min(i, PLACES.length - 1)] + "" + (i + 1) + ". " + ChatColor.WHITE
                    + (name.length() > 16 ? name.substring(0, 16) : name) + ChatColor.GRAY + " " + top.get(i).getValue());
        }
        return lines;
    }
}
