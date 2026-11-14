package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.ChatColor;

/**
 * The titles of the modes, earned with wins ("Sumo Gold III"): each title counts the wins of its
 * modes together (Bed Fight: its 1v1, its 2v2 and the top bed fight), and climbs steps (Bronze to
 * Mythic) of five levels each (I to V): the first level of a step at its first wins, then one
 * every few more wins. A player wears one in the chat and their profile, and above their name if
 * they choose it: their best by default, another of theirs, or none (/titles). Nothing to pay.
 * The ranked modes have their divisions of ELO instead (a "Ranked Gold" of wins would read as the
 * Gold division). Immutable.
 */
final class Titles {

    /** The levels of a step: I to V. */
    static final int LEVELS = 5;
    /** The steps at most, and the titles at most (the menu shows four rows of seven). */
    static final int MAX_STEPS = 12;
    static final int MAX_FAMILIES = 28;
    /** The choice of wearing no title. */
    static final String NONE = "aucun";
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    /** A step of the titles: "Gold" in its colour, the wins of its level I and between two levels. */
    static final class Step {
        final String name;
        final String colour;
        final int first;
        final int every;

        Step(String name, String colour, int first, int every) {
            this.name = name;
            this.colour = colour;
            this.first = first;
            this.every = every;
        }

        /** The wins of the level (1 to 5). */
        int wins(int level) {
            return first + (level - 1) * every;
        }
    }

    /** A title of the modes: its key ("bed-fight"), its name ("Bed Fight") and the modes it counts. */
    static final class Family {
        final String key;
        final String name;
        final List<String> modes;

        Family(String key, String name, List<String> modes) {
            this.key = key;
            this.name = name;
            this.modes = Collections.unmodifiableList(new ArrayList<>(modes));
        }
    }

    /** A title a player has: its family, its step and level, and the wins it counts. */
    static final class Title {
        final Family family;
        final Step step;
        final int level;
        final int wins;
        /** The place of its step and level among all of them, from 0: the higher, the better. */
        final int order;

        Title(Family family, Step step, int level, int wins, int order) {
            this.family = family;
            this.step = step;
            this.level = level;
            this.wins = wins;
            this.order = order;
        }

        /** "§eSumo Gold III". */
        String text() {
            return step.colour + family.name + " " + step.name + " " + roman(level);
        }

        /** "§8[§eSumo Gold III§8] ": before the rank in the chat. */
        String tag() {
            return ChatColor.DARK_GRAY + "[" + text() + ChatColor.DARK_GRAY + "] ";
        }

        /** True if it is higher than other: its step and level, then more wins. */
        boolean above(Title other) {
            return other == null || order > other.order || order == other.order && wins > other.wins;
        }
    }

    private final boolean on;
    private final List<Step> steps;
    private final List<Family> families;

    Titles(boolean on, List<Step> steps, List<Family> families) {
        if (steps.isEmpty() || steps.size() > MAX_STEPS) {
            throw new IllegalArgumentException("from 1 to " + MAX_STEPS + " steps");
        }
        if (families.size() > MAX_FAMILIES) {
            throw new IllegalArgumentException(MAX_FAMILIES + " titles at most");
        }
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (step.first < 1 || step.every < 1) {
                throw new IllegalArgumentException(step.name + ": its first wins and the wins between two levels from 1");
            }
            if (i > 0 && step.first <= steps.get(i - 1).wins(LEVELS)) {
                throw new IllegalArgumentException(step.name + ": its first wins after those of " + steps.get(i - 1).name + " V ("
                        + steps.get(i - 1).wins(LEVELS) + ")");
            }
        }
        this.on = on;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.families = Collections.unmodifiableList(new ArrayList<>(families));
    }

    /** The titles of the plugin: eight steps, and the modes of the bundled config.yml (not the ranked ones). */
    static Titles defaults() {
        List<Step> steps = Arrays.asList(
                new Step("Bronze", ChatColor.GOLD.toString(), 10, 10),
                new Step("Silver", ChatColor.GRAY.toString(), 75, 25),
                new Step("Gold", ChatColor.YELLOW.toString(), 200, 50),
                new Step("Platinum", ChatColor.DARK_AQUA.toString(), 500, 100),
                new Step("Diamond", ChatColor.AQUA.toString(), 1000, 200),
                new Step("Master", ChatColor.DARK_GREEN.toString(), 2000, 500),
                new Step("Legend", ChatColor.DARK_PURPLE.toString(), 5000, 1000),
                new Step("Mythic", ChatColor.LIGHT_PURPLE.toString(), 10000, 2500));
        List<Family> families = Arrays.asList(
                new Family("sky-rise", "Sky Rise", Arrays.asList("skyrise-1v1", "skyrise-2v2", "skyrise-resources-1v1",
                        "skyrise-resources-2v2")),
                new Family("first-rush", "First Rush", Arrays.asList("first-rush", "first-rush-duo", "first-rush-full",
                        "first-rush-full-duo")),
                new Family("bed-fight", "Bed Fight", Arrays.asList("bed-fight-1v1", "bed-fight-2v2", "top-bed-fight-1v1")),
                new Family("top-bridge", "Top Bridge", Arrays.asList("top-bridge-1v1", "top-bridge-2v2", "top-bridge-ffa")),
                new Family("clutch-scenario", "Clutch Scenario", Arrays.asList("clutch-scenario-1v1", "clutch-scenario-2v2",
                        "clutch-scenario-3v3", "clutch-scenario-4v4")),
                new Family("attack-defense", "Attack Defense", Arrays.asList("attack-defense-1v1", "attack-defense-2v2")),
                new Family("invis", "Invis", Arrays.asList("invis-practice", "invis-practice-1v1", "invis-practice-1v2",
                        "invis-practice-1v3", "invis-practice-1v4")),
                new Family("bed-break", "Bed Break", Arrays.asList("bed-break-simulator", "bed-break-simulator-1v1",
                        "bed-break-simulator-1v2", "bed-break-simulator-1v3", "bed-break-simulator-1v4")),
                new Family("sumo", "Sumo", Arrays.asList("sumo")),
                new Family("boxing", "Boxing", Arrays.asList("boxing")),
                new Family("kb-displace", "KB Displace", Arrays.asList("kb-displace")),
                new Family("block-placement", "Block Placement", Arrays.asList("block-fight-1v1", "block-fight-2v2")),
                new Family("bridge-fight", "Bridge Fight", Arrays.asList("bridge-fight-1v1", "bridge-fight-2v2")),
                new Family("block-edit", "Block Edit", Arrays.asList("block-edit-duel")),
                new Family("fireball", "Fireball Fight", Arrays.asList("fireball")),
                new Family("bow", "Bow Duel", Arrays.asList("bow-duel")));
        return new Titles(true, steps, families);
    }

    /** No titles: nobody wears one. */
    static Titles off() {
        Titles defaults = defaults();
        return new Titles(false, defaults.steps, defaults.families);
    }

    boolean on() {
        return on;
    }

    List<Step> steps() {
        return steps;
    }

    List<Family> families() {
        return on ? families : Collections.<Family>emptyList();
    }

    /** The title of this key, or null. */
    Family family(String key) {
        for (Family family : families()) {
            if (family.key.equals(key)) {
                return family;
            }
        }
        return null;
    }

    /** The titles that count the wins of this mode. */
    List<Family> familiesOf(String modeId) {
        List<Family> of = new ArrayList<>();
        for (Family family : families()) {
            if (family.modes.contains(modeId)) {
                of.add(family);
            }
        }
        return of;
    }

    static String roman(int level) {
        return ROMAN[Math.max(1, Math.min(LEVELS, level)) - 1];
    }

    /** The title of these wins, or null under the first. */
    Title of(Family family, int wins) {
        Title title = null;
        for (int s = 0; s < steps.size(); s++) {
            Step step = steps.get(s);
            for (int level = 1; level <= LEVELS; level++) {
                if (wins >= step.wins(level)) {
                    title = new Title(family, step, level, wins, s * LEVELS + level - 1);
                }
            }
        }
        return title;
    }

    /** The wins of the next title after these wins, or -1 at the top. */
    int nextWins(int wins) {
        for (Step step : steps) {
            for (int level = 1; level <= LEVELS; level++) {
                if (step.wins(level) > wins) {
                    return step.wins(level);
                }
            }
        }
        return -1;
    }

    /**
     * How far these wins are from the title they have to the next one, from 0 to 1 (1 at the top):
     * the bar of the menu.
     */
    float progress(int wins) {
        int next = nextWins(wins);
        if (next < 0) {
            return 1f;
        }
        int from = 0;
        for (Step step : steps) {
            for (int level = 1; level <= LEVELS; level++) {
                if (step.wins(level) <= wins) {
                    from = step.wins(level);
                }
            }
        }
        return Math.max(0f, Math.min(1f, (wins - from) / (float) (next - from)));
    }

    /** "Gold IV" for the title of these wins: the name of a step and its level, or null. */
    String rankAt(int wins) {
        for (Step step : steps) {
            for (int level = 1; level <= LEVELS; level++) {
                if (step.wins(level) == wins) {
                    return step.colour + step.name + " " + roman(level);
                }
            }
        }
        return null;
    }

    /** The wins of the player in the modes of a title. */
    static int wins(PlayerStats player, Family family) {
        int wins = 0;
        for (String mode : family.modes) {
            ModeStats stats = player == null ? null : player.mode(mode);
            wins += stats == null ? 0 : stats.wins;
        }
        return wins;
    }

    /** The player's title of this family, or null. */
    Title title(PlayerStats player, Family family) {
        return of(family, wins(player, family));
    }

    /** Every title of the player, in the order of the families. */
    List<Title> all(PlayerStats player) {
        List<Title> all = new ArrayList<>();
        for (Family family : families()) {
            Title title = title(player, family);
            if (title != null) {
                all.add(title);
            }
        }
        return all;
    }

    /** The highest title of the player, or null. */
    Title best(PlayerStats player) {
        Title best = null;
        for (Title title : all(player)) {
            if (title.above(best)) {
                best = title;
            }
        }
        return best;
    }

    /**
     * The title the player wears: the one they chose (a key of a family), none ({@link #NONE}), or
     * by default (null, or a title they no longer have) their best.
     */
    Title worn(PlayerStats player) {
        if (!on || player == null) {
            return null;
        }
        String choice = player.titleChoice();
        if (NONE.equals(choice)) {
            return null;
        }
        Family chosen = choice == null ? null : family(choice);
        Title title = chosen == null ? null : title(player, chosen);
        return title != null ? title : best(player);
    }
}
