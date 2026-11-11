package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The quests (config.yml, quetes:): daily ones and weekly ones, longer and worth more Network XP.
 * Every quest runs by itself: play games, win games, kill players, win ranked games (of any mode,
 * or of one mode). A quest done gives its XP once; the daily ones start again every day, the
 * weekly ones every Monday (the days and weeks of {@link StatsPeriod}). Immutable.
 */
final class Quests {

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

    /** What a quest counts. */
    enum Goal {
        GAMES("parties"), WINS("victoires"), KILLS("kills"), RANKED_WINS("victoires-classees");

        private final String word;

        Goal(String word) {
            this.word = word;
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
        private final String mode;

        Quest(Period period, String id, String name, Goal goal, int target, int xp, String mode) {
            if (target < 1 || xp < 0) {
                throw new IllegalArgumentException("a quest counts to 1 at least");
            }
            this.period = period;
            this.id = id;
            this.name = name;
            this.goal = goal;
            this.target = target;
            this.xp = xp;
            this.mode = mode;
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

        /** The id of the one mode it counts, or null for any mode. */
        String mode() {
            return mode;
        }

        boolean counts(Goal what, String modeId) {
            return goal == what && (mode == null || mode.equals(modeId));
        }
    }

    private final List<Quest> quests;

    Quests(List<Quest> quests) {
        this.quests = Collections.unmodifiableList(new ArrayList<>(quests));
    }

    List<Quest> all() {
        return quests;
    }

    List<Quest> of(Period period) {
        List<Quest> list = new ArrayList<>();
        for (Quest quest : quests) {
            if (quest.period() == period) {
                list.add(quest);
            }
        }
        return list;
    }

    /** How many quests of this period the player has done today (or this week): none without stats. */
    int done(PlayerStats stats, Period period, String day, String week) {
        int done = 0;
        if (stats != null) {
            for (Quest quest : of(period)) {
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

    /**
     * Something the quests count happened (a game, a win, a kill...): every quest it counts moves
     * on. Returns the quests it completed, whose XP is for the caller to give.
     */
    List<Quest> advance(PlayerStats stats, Goal goal, String modeId, int amount, String day, String week) {
        List<Quest> done = new ArrayList<>();
        stats.newQuestPeriod(day, week);
        for (Quest quest : quests) {
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

    /** Four daily quests and four weekly ones, the weekly ones worth much more. */
    static Quests defaults() {
        List<Quest> all = new ArrayList<>();
        all.add(new Quest(Period.DAILY, "jouer", "Play 5 games", Goal.GAMES, 5, 500, null));
        all.add(new Quest(Period.DAILY, "gagner", "Win 3 games", Goal.WINS, 3, 750, null));
        all.add(new Quest(Period.DAILY, "kills", "Get 15 kills", Goal.KILLS, 15, 500, null));
        all.add(new Quest(Period.DAILY, "classe", "Win a ranked game", Goal.RANKED_WINS, 1, 750, null));
        all.add(new Quest(Period.WEEKLY, "jouer", "Play 40 games", Goal.GAMES, 40, 3_000, null));
        all.add(new Quest(Period.WEEKLY, "gagner", "Win 25 games", Goal.WINS, 25, 5_000, null));
        all.add(new Quest(Period.WEEKLY, "kills", "Get 100 kills", Goal.KILLS, 100, 3_000, null));
        all.add(new Quest(Period.WEEKLY, "classe", "Win 10 ranked games", Goal.RANKED_WINS, 10, 5_000, null));
        return new Quests(all);
    }
}
