package gg.rago.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The bed defenses the owner built, as templates that the games and the trainings build around a
 * bed. A template is one picture per height, from the bed's up; each picture has a row per place
 * along the bed (the foot's row, then the head's: both marked B in the first picture) and a column
 * per place across it. E is end stone, W wool (of the team's colour), '.' nothing.
 */
public final class BedDefense {

    /** End stone. */
    public static final char END_STONE = 'E';
    /** Wool, of the team's colour. */
    public static final char WOOL = 'W';
    private static final char BED = 'B';

    /**
     * The duo defense of the owner's map "Defense Claude" (12 end stone, 23 wool): end stone around
     * the bed and on it, one more block of it past the head, then wool over all of it.
     */
    public static final List<String[]> DUO = Collections.unmodifiableList(java.util.Arrays.asList(
            new String[] {
                "..W..",
                ".WEW.",
                "WEBEW",
                "WEBEW",
                "WEEEW",
                ".WEW.",
                "..W.."},
            new String[] {
                ".....",
                "..W..",
                ".WEW.",
                ".WEW.",
                ".WEW.",
                "..W..",
                "....."},
            new String[] {
                ".....",
                ".....",
                "..W..",
                "..W..",
                "..W..",
                ".....",
                "....."}));

    private BedDefense() {
    }

    /**
     * Each block of a template around a bed whose foot is at (0, 0, 0) and head one block towards
     * (hx, 0, hz): {x, y, z, block}, the block {@link #END_STONE} or {@link #WOOL}, y from 0 (the
     * bed's height) up.
     */
    public static List<int[]> places(List<String[]> template, int hx, int hz) {
        String[] first = template.get(0);
        int footRow = -1;
        int centre = -1;
        for (int row = 0; row < first.length && footRow < 0; row++) {
            centre = first[row].indexOf(BED);
            if (centre >= 0) {
                footRow = row;
            }
        }
        if (footRow < 0) {
            throw new IllegalArgumentException("a template with no bed");
        }
        List<int[]> places = new ArrayList<>();
        for (int y = 0; y < template.size(); y++) {
            String[] picture = template.get(y);
            for (int row = 0; row < picture.length; row++) {
                for (int column = 0; column < picture[row].length(); column++) {
                    char block = picture[row].charAt(column);
                    if (block != END_STONE && block != WOOL) {
                        continue;
                    }
                    int along = row - footRow;
                    int across = column - centre;
                    places.add(new int[] {hx * along - hz * across, y, hz * along + hx * across, block});
                }
            }
        }
        return places;
    }
}
