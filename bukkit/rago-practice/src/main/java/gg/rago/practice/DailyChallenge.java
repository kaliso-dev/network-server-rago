package gg.rago.practice;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The challenge of the day: one training for everyone each day (Paris time), in turn from a list
 * of record boards ("block-in.wood", "clutch.island"...). The best score of each player today makes
 * the day's top; when the day turns, the three best get their Network XP. Each player's first score
 * of the day gives the participation XP, more for each day in a row they played it. Pure Java, main
 * thread.
 */
final class DailyChallenge {

    static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    /** A training a challenge can be: its mode, the name the players read, the variant. */
    static final class Board {
        final String modeId;
        final String title;
        final Training.Variant variant;

        Board(String modeId, String title, Training.Variant variant) {
            this.modeId = modeId;
            this.title = title;
            this.variant = variant;
        }

        /** "block-in.wood": the board of records.yml. */
        String id() {
            return modeId + "." + variant.id;
        }

        /** "Block in practice: 3 layers, wood side". */
        String label() {
            return title + ": " + variant.label;
        }

        /** "4.512 s" or "12 clutches". */
        String format(long value) {
            return variant.timed ? Training.time(value) + " s" : value + (variant.unit.isEmpty() ? "" : " " + variant.unit);
        }

        /** True if a is a better score than b on this board. */
        boolean better(long a, long b) {
            return variant.timed ? a < b : a > b;
        }
    }

    /** The trainings a challenge can be, by board. */
    static final Map<String, Board> CATALOG;

    static {
        Map<String, Board> catalog = new HashMap<>();
        add(catalog, "block-in", "Block in practice", BlockInTraining.DEFENSES);
        add(catalog, "clutch", "Block clutch", ClutchTraining.ISLAND);
        add(catalog, "clutch-top-fight", "Block clutch", ClutchTraining.TOP);
        add(catalog, "aim-trainer", "Aim trainer", AimTrainer.TARGETS);
        add(catalog, "fast-defense", "Fast defense", FastDefenseTraining.DEFENSES);
        add(catalog, "ladder-practice", "Ladder practice", LadderTraining.variants());
        add(catalog, "combo-bot", "Combo bot", ComboBot.variants());
        add(catalog, "extend-practice", "Extend practice", ExtendTraining.variants());
        add(catalog, "kb-displace-bot", "KB displace against a bot", KbDisplaceBot.STILL);
        add(catalog, "block-edit", "Block edit", BlockEditTraining.BOT);
        // a distance of the timed bridge, straight and flat
        add(catalog, "bridge-chrono", "Timed bridge", BridgeTraining.VARIANTS);
        CATALOG = Collections.unmodifiableMap(catalog);
    }

    private static void add(Map<String, Board> catalog, String modeId, String title, Training.Variant... variants) {
        for (Training.Variant variant : variants) {
            Board board = new Board(modeId, title, variant);
            catalog.put(board.id(), board);
        }
    }

    /** The settings of defi-du-jour: in config.yml. */
    static final class Settings {
        static final List<String> DEFAULT_BOARDS = Collections.unmodifiableList(java.util.Arrays.asList(
                "block-in.wood", "clutch.island", "aim-trainer.strafe", "fast-defense.ranked", "ladder-practice.random",
                "combo-bot.hard", "extend-practice.chain", "clutch-top-fight.top", "block-edit.bot", "block-in.two",
                "aim-trainer.circle", "kb-displace-bot.still"));
        /** Without the section: on, every training of the list, 100 XP a day (+25 a day in a row), 750, 500 and 300 XP. */
        static final Settings DEFAULT = new Settings(true, DEFAULT_BOARDS, 100, 25, 7, java.util.Arrays.asList(750, 500, 300));
        /** No challenge (a wrong section). */
        static final Settings OFF = new Settings(false, DEFAULT_BOARDS, 100, 25, 7, java.util.Arrays.asList(750, 500, 300));

        final boolean on;
        final List<String> boards;
        /** The XP of a player's first score of the day. */
        final int participationXp;
        /** The XP more for each day in a row after the first. */
        final int streakXp;
        /** The days in a row that count for the XP, at most. */
        final int streakCap;
        /** The XP of the first, the second and the third of the day. */
        final List<Integer> podiumXp;

        Settings(boolean on, List<String> boards, int participationXp, int streakXp, int streakCap, List<Integer> podiumXp) {
            this.on = on;
            this.boards = Collections.unmodifiableList(new ArrayList<>(boards));
            this.participationXp = participationXp;
            this.streakXp = streakXp;
            this.streakCap = streakCap;
            this.podiumXp = Collections.unmodifiableList(new ArrayList<>(podiumXp));
        }

        /** The XP of a first score of the day on the days-th day in a row. */
        int participation(int days) {
            return participationXp + streakXp * Math.max(0, Math.min(days, streakCap) - 1);
        }

        /** The XP of the place (from 1) at the end of the day; 0 off the podium. */
        int podium(int place) {
            return place >= 1 && place <= podiumXp.size() ? podiumXp.get(place - 1) : 0;
        }

        /** The section of config.yml (null: none, the default challenge). Call check first. */
        static Settings of(ConfigurationSection section) {
            if (section == null) {
                return DEFAULT;
            }
            List<String> boards = section.isList("epreuves") ? section.getStringList("epreuves") : DEFAULT_BOARDS;
            List<Integer> podium = section.isList("xp-podium") ? section.getIntegerList("xp-podium")
                    : java.util.Arrays.asList(750, 500, 300);
            return new Settings(section.getBoolean("actif", true), boards, section.getInt("xp-participation", 100),
                    section.getInt("xp-par-jour-de-suite", 25), section.getInt("jours-de-suite-max", 7), podium);
        }

        /** What is wrong with the section, or null. */
        static String check(ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            if (section.isList("epreuves")) {
                List<String> boards = section.getStringList("epreuves");
                if (boards.isEmpty()) {
                    return "defi-du-jour.epreuves: at least one training";
                }
                for (String board : boards) {
                    if (!CATALOG.containsKey(board)) {
                        List<String> known = new ArrayList<>(CATALOG.keySet());
                        Collections.sort(known);
                        return "defi-du-jour.epreuves: \"" + board + "\" is not a training of the challenge (" + String.join(", ", known)
                                + ")";
                    }
                }
            }
            for (String key : new String[] {"xp-participation", "xp-par-jour-de-suite"}) {
                int value = section.getInt(key, 0);
                if (value < 0 || value > 10000) {
                    return "defi-du-jour." + key + ": from 0 to 10000";
                }
            }
            int cap = section.getInt("jours-de-suite-max", 7);
            if (cap < 1 || cap > 365) {
                return "defi-du-jour.jours-de-suite-max: from 1 to 365";
            }
            if (section.isList("xp-podium")) {
                List<Integer> podium = section.getIntegerList("xp-podium");
                if (podium.size() > 10) {
                    return "defi-du-jour.xp-podium: 10 places at most";
                }
                for (int xp : podium) {
                    if (xp < 0 || xp > 100000) {
                        return "defi-du-jour.xp-podium: from 0 to 100000 XP a place";
                    }
                }
            }
            return null;
        }
    }

    /** A player's best of the day. */
    static final class Score {
        final UUID player;
        final String name;
        final long value;

        Score(UUID player, String name, long value) {
            this.player = player;
            this.name = name;
            this.value = value;
        }
    }

    /** A player's days in a row. */
    static final class Streak {
        final String name;
        final int days;
        /** The last day they played the challenge ("2026-11-11"). */
        final String last;

        Streak(String name, int days, String last) {
            this.name = name;
            this.days = days;
            this.last = last;
        }
    }

    /** What a score did to the day's challenge. */
    static final class Result {
        /** True for the player's first score of the day. */
        final boolean first;
        /** True when it beats their best of the day (or is their first). */
        final boolean best;
        /** Their place in the day's top now, from 1. */
        final int place;
        /** Their days in a row, this one counted. */
        final int days;
        /** The participation XP to give now (0 after the first score). */
        final int xp;

        Result(boolean first, boolean best, int place, int days, int xp) {
            this.first = first;
            this.best = best;
            this.place = place;
            this.days = days;
            this.xp = xp;
        }
    }

    /** A day that ended: its challenge and its top. */
    static final class Ended {
        final String day;
        final Board board;
        final List<Score> top;

        Ended(String day, Board board, List<Score> top) {
            this.day = day;
            this.board = board;
            this.top = top;
        }
    }

    private final Settings settings;
    private String day;
    private Board board;
    private final Map<UUID, Score> scores = new HashMap<>();
    private final Map<UUID, Streak> streaks = new HashMap<>();

    DailyChallenge(Settings settings, long now) {
        this.settings = settings;
        this.day = dayKey(now);
        this.board = boardOf(day);
    }

    Settings settings() {
        return settings;
    }

    /** "2026-11-11": the day in Paris at this time. */
    static String dayKey(long now) {
        return Instant.ofEpochMilli(now).atZone(PARIS).toLocalDate().toString();
    }

    /** The milliseconds from now to the next midnight in Paris. */
    static long millisLeft(long now) {
        ZonedDateTime time = Instant.ofEpochMilli(now).atZone(PARIS);
        ZonedDateTime midnight = time.toLocalDate().plusDays(1).atStartOfDay(PARIS);
        return midnight.toInstant().toEpochMilli() - now;
    }

    /** The challenge of that day: the boards in turn, the same all day long, restart or not. */
    Board boardOf(String day) {
        long index = Math.floorMod(LocalDate.parse(day).toEpochDay(), (long) settings.boards.size());
        return CATALOG.get(settings.boards.get((int) index));
    }

    String day() {
        return day;
    }

    Board board() {
        return board;
    }

    /**
     * The day turned (null when it did not): the top of the day that ended, and the new day starts
     * with its own challenge and no score.
     */
    Ended roll(long now) {
        String today = dayKey(now);
        if (today.equals(day)) {
            return null;
        }
        Ended ended = new Ended(day, board, ranked());
        day = today;
        board = boardOf(today);
        scores.clear();
        return ended;
    }

    /**
     * A score on a training's board: kept when the board is today's challenge (null otherwise),
     * the player's days in a row going on with their first score of the day. Call roll first.
     */
    Result submit(String boardId, UUID player, String name, long value) {
        if (!settings.on || !board.id().equals(boardId)) {
            return null;
        }
        Score mine = scores.get(player);
        boolean first = mine == null;
        boolean best = first || board.better(value, mine.value);
        if (best) {
            scores.put(player, new Score(player, name, value));
        }
        int days;
        if (first) {
            Streak streak = streaks.get(player);
            String yesterday = LocalDate.parse(day).minusDays(1).toString();
            days = streak != null && streak.last.equals(yesterday) ? streak.days + 1
                    : streak != null && streak.last.equals(day) ? streak.days : 1;
            streaks.put(player, new Streak(name, days, day));
        } else {
            days = streaks.get(player) == null ? 1 : streaks.get(player).days;
        }
        return new Result(first, best, place(player), days, first ? settings.participation(days) : 0);
    }

    /** The day's bests, the best first (equal scores: the first to make it). */
    List<Score> ranked() {
        List<Score> ranked = new ArrayList<>(scores.values());
        ranked.sort((a, b) -> {
            if (a.value == b.value) {
                return a.name.compareToIgnoreCase(b.name);
            }
            return board.better(a.value, b.value) ? -1 : 1;
        });
        return ranked;
    }

    /** The player's place today, from 1; 0 without a score. */
    int place(UUID player) {
        List<Score> ranked = ranked();
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).player.equals(player)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** The player's best of the day, or null. */
    Long best(UUID player) {
        Score score = scores.get(player);
        return score == null ? null : score.value;
    }

    /** The player's days in a row, still alive: they played today or yesterday; else 0. */
    int days(UUID player) {
        Streak streak = streaks.get(player);
        if (streak == null) {
            return 0;
        }
        String yesterday = LocalDate.parse(day).minusDays(1).toString();
        return streak.last.equals(day) || streak.last.equals(yesterday) ? streak.days : 0;
    }

    /** True if the player has a score today. */
    boolean played(UUID player) {
        return scores.containsKey(player);
    }

    // --- File -----------------------------------------------------------------------------------

    /** The challenge as defi.yml keeps it. */
    String toYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("jour", day);
        yaml.set("epreuve", board.id());
        for (Score score : scores.values()) {
            yaml.set("scores." + score.player + ".nom", score.name);
            yaml.set("scores." + score.player + ".score", score.value);
        }
        for (Map.Entry<UUID, Streak> entry : streaks.entrySet()) {
            String path = "series." + entry.getKey();
            yaml.set(path + ".nom", entry.getValue().name);
            yaml.set(path + ".jours", entry.getValue().days);
            yaml.set(path + ".dernier", entry.getValue().last);
        }
        return "# The challenge of the day (written by RagoPractice).\n" + yaml.saveToString();
    }

    /**
     * Puts back what defi.yml kept: the days in a row, and the scores when the file is of the day
     * and of the same challenge. Returns the day that ended while the server was off (its top to
     * reward), or null.
     */
    Ended load(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        ConfigurationSection series = yaml.getConfigurationSection("series");
        if (series != null) {
            for (String key : series.getKeys(false)) {
                try {
                    UUID player = UUID.fromString(key);
                    String last = series.getString(key + ".dernier", "");
                    LocalDate.parse(last);
                    streaks.put(player, new Streak(series.getString(key + ".nom", "?"), Math.max(1, series.getInt(key + ".jours", 1)),
                            last));
                } catch (RuntimeException e) {
                    // an unreadable line is left out
                }
            }
        }
        String fileDay = yaml.getString("jour", "");
        Board fileBoard = CATALOG.get(yaml.getString("epreuve", ""));
        ConfigurationSection saved = yaml.getConfigurationSection("scores");
        List<Score> read = new ArrayList<>();
        if (saved != null && fileBoard != null) {
            for (String key : saved.getKeys(false)) {
                try {
                    read.add(new Score(UUID.fromString(key), saved.getString(key + ".nom", "?"), saved.getLong(key + ".score")));
                } catch (IllegalArgumentException e) {
                    // an unreadable line is left out
                }
            }
        }
        if (fileDay.equals(day) && fileBoard != null && fileBoard.id().equals(board.id())) {
            for (Score score : read) {
                scores.put(score.player, score);
            }
            return null;
        }
        if (fileBoard == null || read.isEmpty() || fileDay.compareTo(day) >= 0) {
            return null;
        }
        // the day of the file ended while the server was off: its top still gets its XP
        Board today = board;
        board = fileBoard;
        for (Score score : read) {
            scores.put(score.player, score);
        }
        Ended ended = new Ended(fileDay, fileBoard, ranked());
        scores.clear();
        board = today;
        return ended;
    }
}
