package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The settings the party leader chose for a private game of Bedwars Normal (reglages-prives in
 * config.yml); each is as in a normal game until set. First those the owner asked for (22/11):
 * <ul>
 * <li>Speed: Speed I, II or III on every player for the whole game;
 * <li>Max Diamond: every team with all the enchantments of the upgrades from the start, Sharpened
 * Swords and Reinforced Armor IV (the owner: « tout les enchantement debloqué »);
 * <li>Max Emerald: the emerald generators at Tier III from the start, the game as long as usual
 * (the owner: « event au max Emerald 3 mais avec le temps normal d'une game »);
 * <li>Break the Map: every block of the map breaks, but around the spawns and the generators; the
 * copy of the map goes with the game.
 * </ul>
 * Then those of Hypixel's private games (« comme sur hypixel »), as its menu tells them:
 * <ul>
 * <li>Health Buff: Normal, Double or Triple Health (10, 20 or 30 hearts);
 * <li>Low Gravity: Jump Boost III on every player for the whole game;
 * <li>Respawn Time: 1 or 10 seconds instead of that of a normal game;
 * <li>Event Times: the timed events at 0.5x (slower), 2x (faster) or 4x (fastest): the tiers of
 * the generators, the end of the beds and the end of the game;
 * <li>Bed Instabreak: the bed of another team breaks with a single punch.
 * </ul>
 * Pure logic, for the tests.
 */
final class PrivateRules {

    static final String SPEED = "speed";
    static final String MAX_DIAMOND = "max-diamond";
    static final String MAX_EMERALD = "max-emerald";
    static final String BREAK_MAP = "break-map";
    static final String HEALTH_BUFF = "health-buff";
    static final String LOW_GRAVITY = "low-gravity";
    static final String RESPAWN_TIME = "respawn-time";
    static final String EVENT_TIMES = "event-times";
    static final String BED_INSTABREAK = "bed-instabreak";

    /** A setting as the lobby's menu shows it: its choices in order, the first that of a normal game. */
    static final class Setting {
        final String id;
        final String name;
        final List<String> choices;
        final List<String> description;

        Setting(String id, String name, List<String> choices, String... description) {
            this.id = id;
            this.name = name;
            this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
            this.description = Collections.unmodifiableList(Arrays.asList(description));
        }
    }

    private static final List<String> OFF_ON = Arrays.asList("Off", "On");

    /** The settings, in the order of the menu: the owner's first, then Hypixel's. */
    static final List<Setting> SETTINGS = Collections.unmodifiableList(Arrays.asList(
            new Setting(SPEED, "Speed", Arrays.asList("None", "Speed I", "Speed II", "Speed III"),
                    "Speed for every player,", "the whole game."),
            new Setting(MAX_DIAMOND, "Max Diamond", OFF_ON,
                    "Every team starts with all the", "enchantments of the upgrades:",
                    "Sharpened Swords and", "Reinforced Armor IV."),
            new Setting(MAX_EMERALD, "Max Emerald", OFF_ON,
                    "The emerald generators start", "at Tier III. The game is as", "long as usual."),
            new Setting(BREAK_MAP, "Break the Map", OFF_ON,
                    "Every block of the map breaks,", "but around the spawns and", "the generators."),
            new Setting(HEALTH_BUFF, "Health Buff", Arrays.asList("Normal Health", "Double Health", "Triple Health"),
                    "The health of every player:", "10, 20 or 30 hearts."),
            new Setting(LOW_GRAVITY, "Low Gravity", OFF_ON,
                    "Jump Boost III for every", "player, the whole game."),
            new Setting(RESPAWN_TIME, "Respawn Time", Arrays.asList("Normal", "1 second", "10 seconds"),
                    "How long a player waits", "before they respawn."),
            new Setting(EVENT_TIMES, "Event Times", Arrays.asList("1x - Normal", "0.5x - Slower", "2x - Faster", "4x - Fastest"),
                    "How soon the events come:", "the tiers of the generators,", "the end of the beds and the", "end of the game."),
            new Setting(BED_INSTABREAK, "Bed Instabreak", OFF_ON,
                    "The beds break with a", "single punch.")));

    /** The pace of each choice of Event Times: {how fast, how slow}; an event at t comes at t * slow / fast. */
    private static final int[][] PACES = {{1, 1}, {1, 2}, {2, 1}, {4, 1}};

    /** A normal game. */
    static final PrivateRules NONE = new PrivateRules(new int[SETTINGS.size()]);

    /** The choice of each setting, in the order of {@link #SETTINGS}: 0 for that of a normal game. */
    private final int[] chosen;

    private PrivateRules(int[] chosen) {
        this.chosen = chosen;
    }

    /**
     * The rules of the choices the lobby gives (setting id: the index of its choice, 0 for that of a
     * normal game); a setting missing, unknown or out of its choices is as in a normal game.
     */
    static PrivateRules of(Map<String, Integer> chosen) {
        if (chosen == null || chosen.isEmpty()) {
            return NONE;
        }
        int[] at = new int[SETTINGS.size()];
        for (int i = 0; i < at.length; i++) {
            Setting setting = SETTINGS.get(i);
            Integer index = chosen.get(setting.id);
            at[i] = index == null || index < 0 || index >= setting.choices.size() ? 0 : index;
        }
        return new PrivateRules(at);
    }

    private int choice(String id) {
        for (int i = 0; i < SETTINGS.size(); i++) {
            if (SETTINGS.get(i).id.equals(id)) {
                return chosen[i];
            }
        }
        return 0;
    }

    /** Speed for everyone: 0 none, 1 to 3 Speed I to III. */
    int speed() {
        return choice(SPEED);
    }

    boolean maxDiamond() {
        return choice(MAX_DIAMOND) == 1;
    }

    boolean maxEmerald() {
        return choice(MAX_EMERALD) == 1;
    }

    boolean breakMap() {
        return choice(BREAK_MAP) == 1;
    }

    /**
     * Health Buff: the amplifier of the Health Boost on every player (each level 2 hearts more), 4
     * for Double Health (20 hearts), 9 for Triple Health (30 hearts); -1 for none.
     */
    int healthBoost() {
        int times = choice(HEALTH_BUFF) + 1;
        return times == 1 ? -1 : 5 * (times - 1) - 1;
    }

    /** Low Gravity: Jump Boost III on everyone. */
    boolean lowGravity() {
        return choice(LOW_GRAVITY) == 1;
    }

    /** The seconds before a dead player comes back, that of a normal game being this one. */
    int respawnSeconds(int normal) {
        switch (choice(RESPAWN_TIME)) {
            case 1:
                return 1;
            case 2:
                return 10;
            default:
                return normal;
        }
    }

    boolean bedInstabreak() {
        return choice(BED_INSTABREAK) == 1;
    }

    /** True if a setting is not as in a normal game. */
    boolean any() {
        for (int choice : chosen) {
            if (choice != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * The settings not as in a normal game, as the players are told at the start: the choice of
     * those it names ("Speed II", "Double Health"), the name of those that are on ("Max Emerald"),
     * else both ("Respawn Time: 1 second").
     */
    List<String> summary() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < SETTINGS.size(); i++) {
            if (chosen[i] == 0) {
                continue;
            }
            Setting setting = SETTINGS.get(i);
            String choice = setting.choices.get(chosen[i]);
            if (setting.choices.equals(OFF_ON)) {
                lines.add(setting.name);
            } else if (setting.id.equals(SPEED) || setting.id.equals(HEALTH_BUFF)) {
                lines.add(choice);
            } else {
                lines.add(setting.name + ": " + choice);
            }
        }
        return lines;
    }

    /**
     * The times of the six timed events of the game (Diamond II, Emerald II, Diamond III, Emerald
     * III, the end of the beds, the end; -1 for one that does not come) with these rules: Event Times
     * brings each sooner or later (at 2x, an event of 30 minutes comes at 15); with Max Emerald,
     * Emerald II and III do not come, the generators being there already.
     */
    int[] times(int[] times) {
        int[] with = times.clone();
        int[] pace = PACES[choice(EVENT_TIMES)];
        for (int i = 0; i < with.length; i++) {
            if (with[i] >= 0) {
                with[i] = Math.max(1, with[i] * pace[1] / pace[0]);
            }
        }
        if (maxEmerald() && with.length > 3) {
            with[1] = -1;
            with[3] = -1;
        }
        return with;
    }
}
