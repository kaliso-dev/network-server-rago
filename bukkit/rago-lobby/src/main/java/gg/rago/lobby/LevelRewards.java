package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The rewards of the Network levels (config.yml, niveau.recompenses): one every 5 levels up to 100
 * by default, Rago Points and things to keep: a cosmetic of the shop (by its id, "trail-clouds"),
 * a colour of name or a badge (by its key, "couleur-or", "badge-centurion"). A player gets the
 * rewards of every level they reach, those they passed before the rewards existed too. Immutable.
 */
final class LevelRewards {

    /** What one level gives. */
    static final class Reward {
        private final int level;
        private final int points;
        private final List<String> items;

        Reward(int level, int points, List<String> items) {
            this.level = level;
            this.points = points;
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
        }

        int level() {
            return level;
        }

        int points() {
            return points;
        }

        /** The ids of the cosmetics and the keys of the styles. */
        List<String> items() {
            return items;
        }
    }

    private final TreeMap<Integer, Reward> rewards = new TreeMap<>();

    LevelRewards(List<Reward> rewards) {
        for (Reward reward : rewards) {
            if (this.rewards.put(reward.level(), reward) != null) {
                throw new IllegalArgumentException("two rewards for level " + reward.level());
            }
        }
    }

    /** Every reward, the lowest level first. */
    List<Reward> all() {
        return new ArrayList<>(rewards.values());
    }

    /** The reward of this level, or null. */
    Reward at(int level) {
        return rewards.get(level);
    }

    /** The rewards above the level {@code from}, up to {@code to} included. */
    List<Reward> between(int from, int to) {
        if (to <= from) {
            return Collections.emptyList();
        }
        return new ArrayList<>(rewards.subMap(from, false, to, true).values());
    }

    /** The first reward above this level, or null when none is left. */
    Reward next(int level) {
        Map.Entry<Integer, Reward> next = rewards.higherEntry(level);
        return next == null ? null : next.getValue();
    }

    /** The level whose reward holds this cosmetic or style, or 0. */
    int levelOf(String item) {
        for (Reward reward : rewards.values()) {
            if (reward.items().contains(item)) {
                return reward.level();
            }
        }
        return 0;
    }

    /** One reward every 5 levels, up to the level 100. */
    static LevelRewards defaults() {
        List<Reward> all = new ArrayList<>();
        all.add(items(5, 500, "plus-jaune"));
        all.add(items(10, 0, "trail-clouds"));
        all.add(items(15, 1_000, "plus-vert"));
        all.add(items(20, 0, "kill-hearts"));
        all.add(items(25, 0, "couleur-aqua"));
        all.add(items(30, 2_000, "plus-aqua"));
        all.add(items(35, 0, "win-fireworks"));
        all.add(items(40, 0, "trail-notes"));
        all.add(items(45, 3_000, "plus-rouge"));
        all.add(items(50, 5_000, "couleur-or"));
        all.add(items(55, 0, "kill-lightning"));
        all.add(items(60, 0, "trail-magic"));
        all.add(items(65, 4_000, "plus-or"));
        all.add(items(70, 0, "win-flames"));
        all.add(items(75, 0, "kill-firework"));
        all.add(items(80, 5_000, "trail-ender"));
        all.add(items(85, 0, "win-party", "plus-bleu"));
        all.add(items(90, 0, "kill-explosion"));
        all.add(items(95, 0, "trail-redstone"));
        all.add(items(100, 10_000, "couleur-legende", "badge-centurion"));
        return new LevelRewards(all);
    }

    private static Reward items(int level, int points, String... items) {
        return new Reward(level, points, Arrays.asList(items));
    }
}
