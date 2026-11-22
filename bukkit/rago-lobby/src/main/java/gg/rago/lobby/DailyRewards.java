package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

/**
 * What the Daily Chest gives (config.yml, coffre-du-jour:): a list of rewards, each with its rarity,
 * what it gives (Rago Points, Network XP, or a cosmetic of the shop the player does not own yet) and
 * its chance. A draw picks one by its chance, then its amount between its min and max. A cosmetic is
 * never one the player owns, nor one of PREMIUM: with none left, the largest Rago Points of the
 * list instead. Immutable.
 */
final class DailyRewards {

    /** The rarities, from the most frequent: their colour, the pane of the chest and their show. */
    enum Rarity {
        COMMON("commun", "Common", ChatColor.WHITE, 0),
        RARE("rare", "Rare", ChatColor.BLUE, 11),
        EPIC("epique", "Epic", ChatColor.DARK_PURPLE, 10),
        LEGENDARY("legendaire", "Legendary", ChatColor.GOLD, 1);

        private final String word;
        private final String title;
        private final ChatColor colour;
        private final int pane;

        Rarity(String word, String title, ChatColor colour, int pane) {
            this.word = word;
            this.title = title;
            this.colour = colour;
            this.pane = pane;
        }

        /** Its word in config.yml. */
        String word() {
            return word;
        }

        String title() {
            return title;
        }

        ChatColor colour() {
            return colour;
        }

        /** The colour of the glass panes around the reward (the data of a stained glass pane). */
        int pane() {
            return pane;
        }

        /** "RARE" in its colour, bold. */
        String tag() {
            return colour + "" + ChatColor.BOLD + title.toUpperCase(Locale.ROOT);
        }

        static Rarity of(String word) {
            for (Rarity rarity : values()) {
                if (rarity.word.equals(word)) {
                    return rarity;
                }
            }
            return null;
        }

        static String words() {
            List<String> words = new ArrayList<>();
            for (Rarity rarity : values()) {
                words.add(rarity.word);
            }
            return String.join(", ", words);
        }
    }

    /** What a reward gives. */
    enum Kind {
        POINTS("rago-points"),
        XP("xp"),
        COSMETIC("cosmetique");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        /** Its key in a reward of config.yml. */
        String key() {
            return key;
        }
    }

    /** A line of the list: a rarity, what it gives, from min to max, and its chance. */
    static final class Entry {
        final String id;
        final Rarity rarity;
        final Kind kind;
        final int min;
        final int max;
        final double chance;

        Entry(String id, Rarity rarity, Kind kind, int min, int max, double chance) {
            this.id = id;
            this.rarity = rarity;
            this.kind = kind;
            this.min = min;
            this.max = max;
            this.chance = chance;
        }
    }

    /** What one draw gave. */
    static final class Reward {
        final Rarity rarity;
        final Kind kind;
        /** The Rago Points or the Network XP (0 for a cosmetic). */
        final int amount;
        /** The cosmetic (null for points or XP). */
        final Cosmetic cosmetic;
        /** True if a cosmetic was drawn but the player owns them all: Rago Points instead. */
        final boolean instead;

        Reward(Rarity rarity, Kind kind, int amount, Cosmetic cosmetic, boolean instead) {
            this.rarity = rarity;
            this.kind = kind;
            this.amount = amount;
            this.cosmetic = cosmetic;
            this.instead = instead;
        }

        /** "+250 Rago Points", "+1,250 Network XP", "Flames (projectile trail)". */
        String label() {
            switch (kind) {
                case POINTS:
                    return ChatColor.GOLD + "+" + number(amount) + " Rago Points";
                case XP:
                    return Brand.XP + "+" + number(amount) + " Network XP";
                default:
                    String type = cosmetic.type().title().toLowerCase(Locale.ROOT);
                    return rarity.colour() + cosmetic.name() + ChatColor.GRAY + " (" + type.substring(0, type.length() - 1) + ")";
            }
        }
    }

    /** The largest amount of a reward. */
    static final int MAX_AMOUNT = 1_000_000;

    private final List<Entry> entries;
    private final double total;

    DailyRewards(List<Entry> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("coffre-du-jour.recompenses: one reward at least");
        }
        boolean pointsOrXp = false;
        double sum = 0;
        for (Entry entry : entries) {
            sum += entry.chance;
            pointsOrXp |= entry.kind != Kind.COSMETIC;
        }
        if (!pointsOrXp) {
            throw new IllegalArgumentException("coffre-du-jour.recompenses: one reward of rago-points or xp at least,"
                    + " for the players who own every cosmetic");
        }
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.total = sum;
    }

    List<Entry> entries() {
        return entries;
    }

    /**
     * The plugin's own: Rago Points from 100 to 500, Network XP from 100 to 2,000, a cosmetic, from
     * the most frequent to the rarest (Common 55%, Rare 28%, Epic 12%, Legendary 5%). The bundled
     * config.yml says the same.
     */
    static DailyRewards defaults() {
        List<Entry> list = new ArrayList<>();
        list.add(new Entry("points-commun", Rarity.COMMON, Kind.POINTS, 100, 200, 30));
        list.add(new Entry("xp-commun", Rarity.COMMON, Kind.XP, 100, 500, 25));
        list.add(new Entry("points-rare", Rarity.RARE, Kind.POINTS, 200, 350, 15));
        list.add(new Entry("xp-rare", Rarity.RARE, Kind.XP, 500, 1000, 13));
        list.add(new Entry("points-epique", Rarity.EPIC, Kind.POINTS, 350, 500, 6));
        list.add(new Entry("xp-epique", Rarity.EPIC, Kind.XP, 1000, 2000, 6));
        list.add(new Entry("cosmetique", Rarity.LEGENDARY, Kind.COSMETIC, 0, 0, 5));
        return new DailyRewards(list);
    }

    /**
     * coffre-du-jour: its recompenses, by name: rarete (commun, rare, epique, legendaire), one of
     * rago-points or xp (an amount, or min-max: "100-200") or cosmetique: true, and chance (in %, of
     * the total of the list). Left out: the plugin's own.
     */
    static DailyRewards read(ConfigurationSection section) {
        ConfigurationSection rewards = section == null ? null : section.getConfigurationSection("recompenses");
        if (rewards == null) {
            if (section != null && section.isSet("recompenses")) {
                throw new IllegalArgumentException("coffre-du-jour.recompenses: a section of rewards, by name");
            }
            return defaults();
        }
        List<Entry> list = new ArrayList<>();
        for (String id : rewards.getKeys(false)) {
            String where = "coffre-du-jour.recompenses." + id;
            ConfigurationSection one = rewards.getConfigurationSection(id);
            if (one == null || !id.matches("[a-z0-9-]{1,32}")) {
                throw new IllegalArgumentException(where + ": a section, its name 1 to 32 lowercase letters, digits or -");
            }
            Rarity rarity = Rarity.of(one.getString("rarete", ""));
            if (rarity == null) {
                throw new IllegalArgumentException(where + ".rarete: " + Rarity.words());
            }
            Kind kind = null;
            for (Kind each : Kind.values()) {
                if (one.isSet(each.key())) {
                    if (kind != null) {
                        throw new IllegalArgumentException(where + ": one of rago-points, xp or cosmetique, not two");
                    }
                    kind = each;
                }
            }
            if (kind == null) {
                throw new IllegalArgumentException(where + ": rago-points, xp or cosmetique");
            }
            int min = 0;
            int max = 0;
            if (kind == Kind.COSMETIC) {
                if (!one.isBoolean(kind.key()) || !one.getBoolean(kind.key())) {
                    throw new IllegalArgumentException(where + ".cosmetique: true (a cosmetic of the shop the player does not own)");
                }
            } else {
                int[] range = range(String.valueOf(one.get(kind.key())), where + "." + kind.key());
                min = range[0];
                max = range[1];
            }
            if (!one.isInt("chance") && !one.isDouble("chance")) {
                throw new IllegalArgumentException(where + ".chance: a number, in %");
            }
            double chance = one.getDouble("chance");
            if (!(chance > 0) || chance > 100) {
                throw new IllegalArgumentException(where + ".chance: more than 0, 100 at most (in %)");
            }
            list.add(new Entry(id, rarity, kind, min, max, chance));
        }
        return new DailyRewards(list);
    }

    /** "250" or "100-200": the smallest and the largest amount. */
    static int[] range(String text, String where) {
        String[] parts = text.trim().split("\\s*-\\s*", -1);
        try {
            if (parts.length == 1 || parts.length == 2) {
                int min = Integer.parseInt(parts[0]);
                int max = Integer.parseInt(parts[parts.length - 1]);
                if (min >= 1 && max >= min && max <= MAX_AMOUNT) {
                    return new int[] {min, max};
                }
            }
        } catch (NumberFormatException e) {
            // told below
        }
        throw new IllegalArgumentException(where + ": an amount (250) or from min to max (100-200), from 1 to " + MAX_AMOUNT
                + ", not \"" + text + "\"");
    }

    /** One draw: a reward by its chance, its amount, or one of these cosmetics (those the player does not own). */
    Reward draw(Random random, List<Cosmetic> unowned) {
        double at = random.nextDouble() * total;
        Entry picked = entries.get(entries.size() - 1);
        for (Entry entry : entries) {
            at -= entry.chance;
            if (at < 0) {
                picked = entry;
                break;
            }
        }
        return of(picked, random, unowned);
    }

    /** The reward of this line of the list. */
    Reward of(Entry entry, Random random, List<Cosmetic> unowned) {
        if (entry.kind == Kind.COSMETIC) {
            if (unowned.isEmpty()) {
                Entry best = instead();
                return new Reward(entry.rarity, best.kind, best.max, null, true);
            }
            return new Reward(entry.rarity, Kind.COSMETIC, 0, unowned.get(random.nextInt(unowned.size())), false);
        }
        int amount = entry.min + (entry.max > entry.min ? random.nextInt(entry.max - entry.min + 1) : 0);
        return new Reward(entry.rarity, entry.kind, amount, null, false);
    }

    /** What a player who owns every cosmetic gets instead: the largest Rago Points of the list, else the largest XP. */
    Entry instead() {
        Entry best = null;
        for (Entry entry : entries) {
            if (entry.kind == Kind.COSMETIC) {
                continue;
            }
            if (best == null || entry.kind == Kind.POINTS && (best.kind != Kind.POINTS || entry.max > best.max)
                    || entry.kind == Kind.XP && best.kind == Kind.XP && entry.max > best.max) {
                best = entry;
            }
        }
        return best;
    }

    /** The chance of each rarity of the list, in %, in their order (those the list has). */
    Map<Rarity, Double> odds() {
        Map<Rarity, Double> odds = new EnumMap<>(Rarity.class);
        for (Entry entry : entries) {
            Double sum = odds.get(entry.rarity);
            odds.put(entry.rarity, (sum == null ? 0 : sum) + entry.chance * 100 / total);
        }
        return odds;
    }

    /** "Common 55%", "Legendary 2.5%": the odds as the chest shows them. */
    List<String> oddsLines() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Rarity, Double> rarity : odds().entrySet()) {
            lines.add(rarity.getKey().colour() + rarity.getKey().title() + ChatColor.GRAY + " " + percent(rarity.getValue()));
        }
        return lines;
    }

    /** 55 is "55%", 2.5 "2.5%", 33.333 "33.3%". */
    static String percent(double value) {
        double tenths = Math.round(value * 10) / 10.0;
        return (tenths == Math.rint(tenths) ? String.valueOf((long) tenths) : String.valueOf(tenths)) + "%";
    }

    static String number(int value) {
        return String.format(Locale.US, "%,d", value);
    }

    // --- The spin of the chest ------------------------------------------------------------------

    /** Steps of the spin before it stops. */
    static final int SHIFTS = 32;
    /** Ticks added to the last step: the spin slows down from 1 tick a step to 1 + this. */
    static final int SLOW = 10;

    /**
     * The ticks before each step of the spin: 1 at first, then longer and longer (a cubic ease),
     * up to 1 + {@link #SLOW} for the last one. About 6 seconds in all.
     */
    static int[] delays(int shifts) {
        int[] delays = new int[shifts];
        for (int i = 0; i < shifts; i++) {
            double t = shifts == 1 ? 1 : (double) i / (shifts - 1);
            delays[i] = 1 + (int) Math.round(SLOW * t * t * t);
        }
        return delays;
    }
}
