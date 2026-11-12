package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * "Getting started", the quests of a new player, once each: play a training, beat one of your
 * records, play a duel, import your Hypixel quick buy, find our Discord. Each gives Rago Points,
 * and all of them a reward (a cosmetic of the shop or a style): quetes.debut in config.yml. Their
 * progress is in the player's profile ("s:entrainement"), never reset. A step this server cannot
 * offer (no Hypixel key, no Discord link) is left out. Immutable.
 */
final class GettingStarted {

    /** The steps, in the order a new player meets them. */
    enum Step {
        TRAINING("entrainement", "Play a training", "Score once in a training:", "the block clutch to start."),
        RECORD("record", "Beat one of your records", "A new personal best in a", "training."),
        DUEL("duel", "Play a duel", "A 1v1 to the end: the Duels", "menu, or /duel a friend."),
        QUICK_BUY("quick-buy", "Import your Hypixel quick buy", "/hypixel import: your Bed Wars", "shop, as on Hypixel."),
        DISCORD("discord", "Find us on Discord", "/discord: the link of our", "Discord.");

        private final String id;
        private final String title;
        private final String[] how;

        Step(String id, String title, String... how) {
            this.id = id;
            this.title = title;
            this.how = how;
        }

        /** Its name in config.yml (quetes.debut.points). */
        String id() {
            return id;
        }

        String title() {
            return title;
        }

        /** How to do it, in two lines. */
        List<String> how() {
            List<String> lines = new ArrayList<>();
            Collections.addAll(lines, how);
            return lines;
        }

        /** "s:entrainement": its progress in the player's profile. */
        String key() {
            return "s:" + id;
        }

        static Step of(String id) {
            for (Step step : values()) {
                if (step.id.equals(id)) {
                    return step;
                }
            }
            return null;
        }
    }

    /** Off: no quest of a new player. */
    static final GettingStarted OFF = new GettingStarted(false, new EnumMap<Step, Integer>(Step.class), null);

    private final boolean on;
    private final Map<Step, Integer> points;
    private final String reward;

    /** In the profile once the reward of all the steps is given: never twice, even if a step comes later. */
    static final String REWARDED = "s:recompense";

    GettingStarted(boolean on, Map<Step, Integer> points, String reward) {
        Map<Step, Integer> copy = new EnumMap<>(Step.class);
        copy.putAll(points);
        this.on = on;
        this.points = Collections.unmodifiableMap(copy);
        this.reward = reward == null || reward.trim().isEmpty() ? null : reward.trim();
    }

    /** 100 to 150 Rago Points a step, 600 in all, and the Emerald sparks trail at the end. */
    static GettingStarted defaults() {
        Map<Step, Integer> points = new EnumMap<>(Step.class);
        points.put(Step.TRAINING, 100);
        points.put(Step.RECORD, 150);
        points.put(Step.DUEL, 150);
        points.put(Step.QUICK_BUY, 100);
        points.put(Step.DISCORD, 100);
        return new GettingStarted(true, points, "trail-emeralds");
    }

    boolean on() {
        return on;
    }

    /** The Rago Points of a step (0 if none). */
    int points(Step step) {
        Integer value = points.get(step);
        return value == null ? 0 : value;
    }

    /** What all the steps give: a cosmetic or a style id, or null. */
    String reward() {
        return reward;
    }

    /** The steps of this server: the Hypixel import needs its API key, the Discord its link. */
    List<Step> steps(boolean hypixel, boolean discord) {
        List<Step> steps = new ArrayList<>();
        if (!on) {
            return steps;
        }
        for (Step step : Step.values()) {
            if (step == Step.QUICK_BUY && !hypixel || step == Step.DISCORD && !discord) {
                continue;
            }
            steps.add(step);
        }
        return steps;
    }

    static boolean done(PlayerStats stats, Step step) {
        return stats != null && stats.questProgress(step.key()) > 0;
    }

    /** How many of these steps the player has done. */
    static int done(PlayerStats stats, List<Step> steps) {
        int done = 0;
        for (Step step : steps) {
            if (done(stats, step)) {
                done++;
            }
        }
        return done;
    }

    /** The first of these steps the player has not done, or null when they are all done. */
    static Step next(PlayerStats stats, List<Step> steps) {
        for (Step step : steps) {
            if (!done(stats, step)) {
                return step;
            }
        }
        return null;
    }

    /**
     * The step is done: true the first time (its points and the next one for the caller), false
     * if it was done already.
     */
    static boolean complete(PlayerStats stats, Step step) {
        if (done(stats, step)) {
            return false;
        }
        stats.setQuestProgress(step.key(), 1);
        return true;
    }

    /**
     * All these steps are done and their reward not given yet: true once (marked in the profile),
     * the reward then for the caller.
     */
    static boolean finish(PlayerStats stats, List<Step> steps) {
        if (steps.isEmpty() || next(stats, steps) != null || stats.questProgress(REWARDED) > 0) {
            return false;
        }
        stats.setQuestProgress(REWARDED, 1);
        return true;
    }

    /** The Rago Points of all these steps together. */
    int totalPoints(List<Step> steps) {
        int total = 0;
        for (Step step : steps) {
            total += points(step);
        }
        return total;
    }
}
