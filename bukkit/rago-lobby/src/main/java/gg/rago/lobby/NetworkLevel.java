package gg.rago.lobby;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The Network level (config.yml, niveau:): every game gives XP to everyone in it (more for a win),
 * every kill a little more. Everyone starts at level 1; going from level n to n + 1 takes
 * {@code firstLevelXp + extraPerLevel * (n - 1)} XP. The level shows as "[12✫]" in the chat,
 * after the name above the head and in the tab list, in the colour of its tier (every 10 levels by
 * default: grey, white, gold, aqua...). Immutable.
 */
final class NetworkLevel {

    /** Above this, the level stops counting (it would take millions of games). */
    static final int MAX_LEVEL = 10_000;
    /** After the level, as Hypixel shows the Bed Wars stars. */
    static final String STAR = "\u272B";

    static final NetworkLevel DEFAULT = new NetworkLevel(50, 15, 5, 100, 50, defaultColours());

    private final int winXp;
    private final int lossXp;
    private final int killXp;
    private final int firstLevelXp;
    private final int extraPerLevel;
    private final TreeMap<Integer, String> colours;

    NetworkLevel(int winXp, int lossXp, int killXp, int firstLevelXp, int extraPerLevel, Map<Integer, String> colours) {
        if (firstLevelXp < 1 || extraPerLevel < 0 || winXp < 0 || lossXp < 0 || killXp < 0) {
            throw new IllegalArgumentException("invalid XP settings");
        }
        this.winXp = winXp;
        this.lossXp = lossXp;
        this.killXp = killXp;
        this.firstLevelXp = firstLevelXp;
        this.extraPerLevel = extraPerLevel;
        this.colours = new TreeMap<>(colours);
    }

    /** The colour of each tier of 10 levels, from the grey of level 1. */
    private static Map<Integer, String> defaultColours() {
        Map<Integer, String> colours = new TreeMap<>();
        colours.put(1, "\u00a77");
        colours.put(10, "\u00a7f");
        colours.put(20, "\u00a76");
        colours.put(30, "\u00a7b");
        colours.put(40, "\u00a72");
        colours.put(50, "\u00a73");
        colours.put(60, "\u00a74");
        colours.put(70, "\u00a7d");
        colours.put(80, "\u00a79");
        colours.put(90, "\u00a75");
        colours.put(100, "\u00a7e");
        return colours;
    }

    int winXp() {
        return winXp;
    }

    int lossXp() {
        return lossXp;
    }

    int killXp() {
        return killXp;
    }

    /** The XP that takes a player from this level to the next one. */
    int xpToNext(int level) {
        long xp = firstLevelXp + (long) extraPerLevel * (Math.max(1, level) - 1);
        return (int) Math.min(Integer.MAX_VALUE, xp);
    }

    /** The level of a player with this much XP: 1 at 0 XP. */
    int level(long xp) {
        int level = 1;
        long left = xp;
        while (level < MAX_LEVEL && left >= xpToNext(level)) {
            left -= xpToNext(level);
            level++;
        }
        return level;
    }

    /** The XP gained since the current level was reached. */
    long xpIntoLevel(long xp) {
        int level = 1;
        long left = Math.max(0, xp);
        while (level < MAX_LEVEL && left >= xpToNext(level)) {
            left -= xpToNext(level);
            level++;
        }
        return left;
    }

    /** From 0 to just under 1: how far the player is towards the next level, for the XP bar. */
    float progress(long xp) {
        int level = level(xp);
        if (level >= MAX_LEVEL) {
            return 0f;
        }
        float progress = (float) xpIntoLevel(xp) / xpToNext(level);
        return Math.max(0f, Math.min(0.999f, progress));
    }

    /** The colour codes of this level's tier ("§6"), grey below the first tier. */
    String colour(int level) {
        Map.Entry<Integer, String> entry = colours.floorEntry(level);
        return entry == null ? "\u00a77" : entry.getValue();
    }

    /** "§6[12✫]": the level as the chat and the name tags show it. */
    String tag(int level) {
        return colour(level) + "[" + level + STAR + "]";
    }

    /** The first level of the next tier, or 0 in the last one. */
    int nextTier(int level) {
        Integer next = colours.higherKey(level);
        return next == null ? 0 : next;
    }

    /** Every tier, by its first level. */
    SortedMap<Integer, String> colours() {
        return Collections.unmodifiableSortedMap(colours);
    }
}
