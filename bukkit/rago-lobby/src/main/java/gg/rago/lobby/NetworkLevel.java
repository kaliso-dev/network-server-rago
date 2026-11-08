package gg.rago.lobby;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The Network level (config.yml, niveau:): every game gives XP to everyone in it (more for a win),
 * every kill a little more. Everyone starts at level 1; going from level n to n + 1 takes
 * {@code firstLevelXp + extraPerLevel * (n - 1)} XP. Reaching some levels gives a rank, shown in
 * the chat, the tab list, above the head and in the sidebar. Immutable.
 */
final class NetworkLevel {

    /** Above this, the level stops counting (it would take millions of games). */
    static final int MAX_LEVEL = 10_000;

    static final NetworkLevel DEFAULT = new NetworkLevel(50, 15, 5, 100, 50, defaultRanks());

    private final int winXp;
    private final int lossXp;
    private final int killXp;
    private final int firstLevelXp;
    private final int extraPerLevel;
    private final TreeMap<Integer, String> ranks;

    NetworkLevel(int winXp, int lossXp, int killXp, int firstLevelXp, int extraPerLevel, Map<Integer, String> ranks) {
        if (firstLevelXp < 1 || extraPerLevel < 0 || winXp < 0 || lossXp < 0 || killXp < 0) {
            throw new IllegalArgumentException("invalid XP settings");
        }
        this.winXp = winXp;
        this.lossXp = lossXp;
        this.killXp = killXp;
        this.firstLevelXp = firstLevelXp;
        this.extraPerLevel = extraPerLevel;
        this.ranks = new TreeMap<>(ranks);
    }

    private static Map<Integer, String> defaultRanks() {
        Map<Integer, String> ranks = new TreeMap<>();
        ranks.put(5, "§f[Iron]");
        ranks.put(10, "§6[Gold]");
        ranks.put(20, "§b[Diamond]");
        ranks.put(30, "§2[Emerald]");
        ranks.put(50, "§3[Sapphire]");
        ranks.put(75, "§4[Ruby]");
        ranks.put(100, "§d[Crystal]");
        return ranks;
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

    /** The rank of this level (with its colours), or null below the first rank. */
    String rank(int level) {
        Map.Entry<Integer, String> entry = ranks.floorEntry(level);
        return entry == null ? null : entry.getValue();
    }

    /** The level that gives this player's rank, or 0 without a rank: the same for the same rank. */
    int rankLevel(int level) {
        Integer key = ranks.floorKey(level);
        return key == null ? 0 : key;
    }

    /** The first rank above this level, or null when the player has the last one. */
    Map.Entry<Integer, String> nextRank(int level) {
        return ranks.higherEntry(level);
    }

    /** Every rank, by the level that gives it. */
    SortedMap<Integer, String> ranks() {
        return Collections.unmodifiableSortedMap(ranks);
    }
}
