package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The settings the party leader chose for a private game of Bedwars Normal (the owner's answers of
 * 22/11, reglages-prives in config.yml), those the owner asked for first; each is as in a normal
 * game until set:
 * <ul>
 * <li>Speed: Speed I, II or III on every player for the whole game;
 * <li>Max Diamond: every team with all the enchantments of the upgrades from the start, Sharpened
 * Swords and Reinforced Armor IV (the owner: « tout les enchantement debloqué »);
 * <li>Max Emerald: the emerald generators at Tier III from the start, the game as long as usual
 * (the owner: « event au max Emerald 3 mais avec le temps normal d'une game »);
 * <li>Break the Map: every block of the map breaks, but around the spawns and the generators; the
 * copy of the map goes with the game.
 * </ul>
 * Pure logic, for the tests.
 */
final class PrivateRules {

    static final String SPEED = "speed";
    static final String MAX_DIAMOND = "max-diamond";
    static final String MAX_EMERALD = "max-emerald";
    static final String BREAK_MAP = "break-map";

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

    /** The settings, in the order of the menu. */
    static final List<Setting> SETTINGS = Collections.unmodifiableList(Arrays.asList(
            new Setting(SPEED, "Speed", Arrays.asList("None", "Speed I", "Speed II", "Speed III"),
                    "Speed for every player,", "the whole game."),
            new Setting(MAX_DIAMOND, "Max Diamond", OFF_ON,
                    "Every team starts with all the", "enchantments of the upgrades:",
                    "Sharpened Swords and", "Reinforced Armor IV."),
            new Setting(MAX_EMERALD, "Max Emerald", OFF_ON,
                    "The emerald generators start", "at Tier III. The game is as", "long as usual."),
            new Setting(BREAK_MAP, "Break the Map", OFF_ON,
                    "Every block of the map breaks,", "but around the spawns and", "the generators.")));

    /** A normal game. */
    static final PrivateRules NONE = new PrivateRules(0, false, false, false);

    private final int speed;
    private final boolean maxDiamond;
    private final boolean maxEmerald;
    private final boolean breakMap;

    PrivateRules(int speed, boolean maxDiamond, boolean maxEmerald, boolean breakMap) {
        this.speed = Math.max(0, Math.min(3, speed));
        this.maxDiamond = maxDiamond;
        this.maxEmerald = maxEmerald;
        this.breakMap = breakMap;
    }

    /**
     * The rules of the choices the lobby gives (setting id: the index of its choice, 0 for that of a
     * normal game); a setting missing, unknown or out of its choices is as in a normal game.
     */
    static PrivateRules of(Map<String, Integer> chosen) {
        if (chosen == null || chosen.isEmpty()) {
            return NONE;
        }
        return new PrivateRules(choice(chosen, SPEED), choice(chosen, MAX_DIAMOND) == 1, choice(chosen, MAX_EMERALD) == 1,
                choice(chosen, BREAK_MAP) == 1);
    }

    private static int choice(Map<String, Integer> chosen, String id) {
        Integer index = chosen.get(id);
        for (Setting setting : SETTINGS) {
            if (setting.id.equals(id)) {
                return index == null || index < 0 || index >= setting.choices.size() ? 0 : index;
            }
        }
        return 0;
    }

    /** Speed for everyone: 0 none, 1 to 3 Speed I to III. */
    int speed() {
        return speed;
    }

    boolean maxDiamond() {
        return maxDiamond;
    }

    boolean maxEmerald() {
        return maxEmerald;
    }

    boolean breakMap() {
        return breakMap;
    }

    /** True if a setting is not as in a normal game. */
    boolean any() {
        return speed > 0 || maxDiamond || maxEmerald || breakMap;
    }

    /** The settings not as in a normal game, as the players are told at the start: "Speed II", "Max Emerald". */
    List<String> summary() {
        List<String> lines = new ArrayList<>();
        if (speed > 0) {
            lines.add(SETTINGS.get(0).choices.get(speed));
        }
        if (maxDiamond) {
            lines.add(SETTINGS.get(1).name);
        }
        if (maxEmerald) {
            lines.add(SETTINGS.get(2).name);
        }
        if (breakMap) {
            lines.add(SETTINGS.get(3).name);
        }
        return lines;
    }

    /**
     * The times of the six timed events of the game (Diamond II, Emerald II, Diamond III, Emerald
     * III, the end of the beds, the end; -1 for one that does not come) with these rules: with Max
     * Emerald, Emerald II and III do not come, the generators being there already.
     */
    int[] times(int[] times) {
        int[] with = times.clone();
        if (maxEmerald && with.length > 3) {
            with[1] = -1;
            with[3] = -1;
        }
        return with;
    }
}
