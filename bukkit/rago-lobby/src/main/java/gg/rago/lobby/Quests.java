package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The quests (config.yml, quetes:): daily ones and weekly ones, longer and worth more Network XP.
 * Every quest runs by itself: in the games (play, win, kill, win ranked games) or in the trainings
 * (succeed, beat a record, play the daily challenge, train for some minutes), of any mode or of
 * some modes. Each day, a few daily quests are drawn from the list (the same for everyone, one of
 * the games and one of the trainings at least); every weekly quest runs all week. A quest done
 * gives its XP once; the daily ones start again every day, the weekly ones every Monday (the days
 * and weeks of {@link StatsPeriod}). Immutable.
 */
final class Quests {

    /** The daily quests drawn each day, by default. */
    static final int PER_DAY = 3;
    /** The quests of a period that run at once, at most: the menu shows them in one row. */
    static final int MAX_RUNNING = 5;

    /** How long a quest lasts. */
    enum Period {
        DAILY("quotidiennes", "d", "Daily quests", StatsPeriod.DAY),
        WEEKLY("hebdomadaires", "w", "Weekly quests", StatsPeriod.WEEK);

        private final String section;
        private final String prefix;
        private final String title;
        private final StatsPeriod stats;

        Period(String section, String prefix, String title, StatsPeriod stats) {
            this.section = section;
            this.prefix = prefix;
            this.title = title;
            this.stats = stats;
        }

        /** quetes.quotidiennes, quetes.hebdomadaires. */
        String section() {
            return section;
        }

        String title() {
            return title;
        }

        /** "Resets in 5h 12m". */
        String resetText(long millis) {
            return stats.resetText(millis);
        }
    }

    /** What a quest counts: in the games, or in the trainings. */
    enum Goal {
        GAMES("parties", false, null),
        WINS("victoires", false, null),
        KILLS("kills", false, null),
        RANKED_WINS("victoires-classees", false, null),
        SUCCESSES("reussites", true, "Each success in a training counts."),
        RECORDS("records", true, "A new personal best in a training."),
        CHALLENGE("defi", true, "A score on the challenge of the day: /challenge."),
        MINUTES("minutes", true, "Each minute of training counts, not idle.");

        private final String word;
        private final boolean training;
        private final String hint;

        Goal(String word, boolean training, String hint) {
            this.word = word;
            this.training = training;
            this.hint = hint;
        }

        /** True for what the trainings count, false for the games. */
        boolean training() {
            return training;
        }

        /** How a training quest moves on, for its lore; null for the games. */
        String hint() {
            return hint;
        }

        /** "parties, victoires, ... or minutes": the words of config.yml. */
        static String words() {
            StringBuilder words = new StringBuilder();
            Goal[] all = values();
            for (int i = 0; i < all.length; i++) {
                words.append(i == 0 ? "" : i == all.length - 1 ? " or " : ", ").append(all[i].word);
            }
            return words.toString();
        }

        static Goal of(String word) {
            for (Goal goal : values()) {
                if (goal.word.equals(word)) {
                    return goal;
                }
            }
            return null;
        }
    }

    /** One quest. */
    static final class Quest {
        private final Period period;
        private final String id;
        private final String name;
        private final Goal goal;
        private final int target;
        private final int xp;
        private final List<String> modes;

        /** modes: the ids of the modes it counts, null or none for any mode. */
        Quest(Period period, String id, String name, Goal goal, int target, int xp, List<String> modes) {
            if (target < 1 || xp < 0) {
                throw new IllegalArgumentException("a quest counts to 1 at least");
            }
            this.period = period;
            this.id = id;
            this.name = name;
            this.goal = goal;
            this.target = target;
            this.xp = xp;
            this.modes = modes == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<>(modes));
        }

        Period period() {
            return period;
        }

        String id() {
            return id;
        }

        /** "d:gagner": the key of the quest's progress in the player's profile. */
        String key() {
            return period.prefix + ":" + id;
        }

        String name() {
            return name;
        }

        Goal goal() {
            return goal;
        }

        int target() {
            return target;
        }

        int xp() {
            return xp;
        }

        /** The ids of the modes it counts, or none for any mode. */
        List<String> modes() {
            return modes;
        }

        boolean counts(Goal what, String modeId) {
            return goal == what && (modes.isEmpty() || modes.contains(modeId));
        }
    }

    private final List<Quest> quests;
    private final int perDay;

    /** Every quest runs, none is drawn. */
    Quests(List<Quest> quests) {
        this(quests, 0);
    }

    /** perDay: the daily quests drawn each day (0: all of them). */
    Quests(List<Quest> quests, int perDay) {
        if (perDay < 0) {
            throw new IllegalArgumentException("no fewer than 0 quests a day");
        }
        this.quests = Collections.unmodifiableList(new ArrayList<>(quests));
        this.perDay = perDay;
    }

    List<Quest> all() {
        return quests;
    }

    /** Every quest of this period in the list: for the daily ones, those the draw picks from. */
    List<Quest> of(Period period) {
        List<Quest> list = new ArrayList<>();
        for (Quest quest : quests) {
            if (quest.period() == period) {
                list.add(quest);
            }
        }
        return list;
    }

    /** The daily quests drawn each day, 0 for all of them. */
    int perDay() {
        return perDay;
    }

    /**
     * The quests of this period on this day and week: the daily ones drawn for the day (all of
     * them when the draw would take as many), every weekly one. In the order of the list.
     */
    List<Quest> active(Period period, String day, String week) {
        List<Quest> pool = of(period);
        if (period != Period.DAILY || perDay == 0 || perDay >= pool.size()) {
            return pool;
        }
        return draw(pool, perDay, day);
    }

    /**
     * count quests of the pool, the same for the same key (a day): a quest of the games and one
     * of the trainings at least when the pool has both, in the order of the pool.
     */
    static List<Quest> draw(List<Quest> pool, int count, String key) {
        List<Quest> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, new Random(mix(key.hashCode())));
        List<Quest> chosen = new ArrayList<>(shuffled.subList(0, Math.min(count, shuffled.size())));
        if (chosen.size() >= 2) {
            for (boolean training : Arrays.asList(true, false)) {
                if (kindIn(chosen, training) != null) {
                    continue;
                }
                Quest missing = null;
                for (Quest quest : shuffled) {
                    if (quest.goal().training() == training && !chosen.contains(quest)) {
                        missing = quest;
                        break;
                    }
                }
                if (missing != null) {
                    // in place of the last one drawn, of the other kind (it keeps at least one)
                    chosen.set(chosen.size() - 1, missing);
                }
            }
        }
        chosen.sort(Comparator.comparingInt(pool::indexOf));
        return chosen;
    }

    private static Quest kindIn(List<Quest> quests, boolean training) {
        for (Quest quest : quests) {
            if (quest.goal().training() == training) {
                return quest;
            }
        }
        return null;
    }

    /** A seed spread over the whole range, so that two days in a row draw unlike quests. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /** How many quests of this period the player has done today (or this week): none without stats. */
    int done(PlayerStats stats, Period period, String day, String week) {
        int done = 0;
        if (stats != null) {
            for (Quest quest : active(period, day, week)) {
                if (progress(stats, quest, day, week) >= quest.target()) {
                    done++;
                }
            }
        }
        return done;
    }

    /** Where the player is in this quest today (or this week), from 0 to its target. */
    static int progress(PlayerStats stats, Quest quest, String day, String week) {
        stats.newQuestPeriod(day, week);
        return Math.min(quest.target(), stats.questProgress(quest.key()));
    }

    /** The quests running on this day and week: the daily ones drawn, then the weekly ones. */
    List<Quest> active(String day, String week) {
        List<Quest> list = new ArrayList<>(active(Period.DAILY, day, week));
        list.addAll(active(Period.WEEKLY, day, week));
        return list;
    }

    /** True if this would move on a quest of the player now (one that runs and is not done yet). */
    boolean wants(PlayerStats stats, Goal goal, String modeId, String day, String week) {
        stats.newQuestPeriod(day, week);
        for (Quest quest : active(day, week)) {
            if (quest.counts(goal, modeId) && stats.questProgress(quest.key()) < quest.target()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Something the quests count happened (a game, a win, a kill, a success in a training...):
     * every quest that runs now and counts it moves on. Returns the quests it completed, whose XP
     * is for the caller to give.
     */
    List<Quest> advance(PlayerStats stats, Goal goal, String modeId, int amount, String day, String week) {
        List<Quest> done = new ArrayList<>();
        stats.newQuestPeriod(day, week);
        for (Quest quest : active(day, week)) {
            if (!quest.counts(goal, modeId)) {
                continue;
            }
            int before = stats.questProgress(quest.key());
            if (before >= quest.target()) {
                continue;
            }
            int after = (int) Math.min(quest.target(), (long) before + Math.max(0, amount));
            stats.setQuestProgress(quest.key(), after);
            if (after >= quest.target()) {
                done.add(quest);
            }
        }
        return done;
    }

    /**
     * Eight daily quests, four of the games and four of the trainings, three of them drawn each
     * day; five weekly ones, worth much more, one of them of the trainings.
     */
    static Quests defaults() {
        List<Quest> all = new ArrayList<>();
        all.add(new Quest(Period.DAILY, "jouer", "Play 5 games", Goal.GAMES, 5, 500, null));
        all.add(new Quest(Period.DAILY, "gagner", "Win 3 games", Goal.WINS, 3, 750, null));
        all.add(new Quest(Period.DAILY, "kills", "Get 15 kills", Goal.KILLS, 15, 500, null));
        all.add(new Quest(Period.DAILY, "classe", "Win a ranked game", Goal.RANKED_WINS, 1, 750, null));
        all.add(new Quest(Period.DAILY, "clutchs", "Land 25 clutches", Goal.SUCCESSES, 25, 600, CLUTCHES));
        all.add(new Quest(Period.DAILY, "record", "Beat a personal best", Goal.RECORDS, 1, 750, null));
        all.add(new Quest(Period.DAILY, "defi", "Play the daily challenge", Goal.CHALLENGE, 1, 500, null));
        all.add(new Quest(Period.DAILY, "entrainement", "Train for 15 minutes", Goal.MINUTES, 15, 600, null));
        all.add(new Quest(Period.WEEKLY, "jouer", "Play 40 games", Goal.GAMES, 40, 3_000, null));
        all.add(new Quest(Period.WEEKLY, "gagner", "Win 25 games", Goal.WINS, 25, 5_000, null));
        all.add(new Quest(Period.WEEKLY, "kills", "Get 100 kills", Goal.KILLS, 100, 3_000, null));
        all.add(new Quest(Period.WEEKLY, "classe", "Win 10 ranked games", Goal.RANKED_WINS, 10, 5_000, null));
        all.add(new Quest(Period.WEEKLY, "entrainement", "Train for 2 hours", Goal.MINUTES, 120, 4_000, null));
        return new Quests(all, PER_DAY);
    }

    /** The two block clutches: the island one and the top fight one. */
    private static final List<String> CLUTCHES = Arrays.asList("clutch", "clutch-top-fight");
}
