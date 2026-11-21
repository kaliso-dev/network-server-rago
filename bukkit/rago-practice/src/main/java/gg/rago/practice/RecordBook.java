package gg.rago.practice;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The personal bests of the trainings, board by board ("clutch.single", "bridge.ninja"...): the
 * best score of each player and the best of all, a time (lower is better) or a count (higher is
 * better). The boards of records are lifetime ones, never emptied; each has its boards of the weeks
 * ({@link #weekly}), the bests of a week from Monday, kept {@value #WEEKS_KEPT} weeks after it.
 * Pure Java, main thread.
 */
final class RecordBook {

    /** The boards of the weeks: "weekly.2026w47.clutch.island", the bests of that week on "clutch.island". */
    static final String WEEKLY = "weekly.";
    /**
     * The weeks kept after the one going on: five, so that the records of a week stay to be seen
     * at least a month after it, 35 days for its last day.
     */
    static final int WEEKS_KEPT = 5;

    /** A player's best on a board. */
    static final class Entry {
        final UUID player;
        final String name;
        final long value;

        Entry(UUID player, String name, long value) {
            this.player = player;
            this.name = name;
            this.value = value;
        }
    }

    /** What a new score did. */
    static final class Result {
        /** The player's best before, or null for a first score. */
        final Long previous;
        /** True if the score beats the player's best (or is their first). */
        final boolean personalBest;
        /** True if the score beats everyone's best on the board. */
        final boolean boardBest;

        Result(Long previous, boolean personalBest, boolean boardBest) {
            this.previous = previous;
            this.personalBest = personalBest;
            this.boardBest = boardBest;
        }
    }

    private final Map<String, Map<UUID, Entry>> boards = new LinkedHashMap<>();
    private final Map<String, Boolean> lowerIsBetter = new HashMap<>();

    /** Declares how a board ranks its scores: true for times. */
    void define(String board, boolean lower) {
        lowerIsBetter.put(board, lower);
    }

    /** True for a board of times; a board of a week ranks as its board. */
    private boolean lower(String board) {
        Boolean lower = lowerIsBetter.get(board);
        if (lower == null && weeklyBase(board) != null) {
            lower = lowerIsBetter.get(weeklyBase(board));
        }
        return lower != null && lower;
    }

    /** The board of this week ("2026w47") of this board of records: "weekly.2026w47.clutch.island". */
    static String weekly(String board, String week) {
        return WEEKLY + week + "." + board;
    }

    /** The board of records of a board of a week ("clutch.island"), or null when it is not one. */
    static String weeklyBase(String board) {
        int dot = board.startsWith(WEEKLY) ? board.indexOf('.', WEEKLY.length()) : -1;
        return dot < 0 || dot == board.length() - 1 ? null : board.substring(dot + 1);
    }

    /** The week of a board of a week ("2026w47"), or null when it is not one. */
    static String weekOf(String board) {
        int dot = board.startsWith(WEEKLY) ? board.indexOf('.', WEEKLY.length()) : -1;
        return dot <= WEEKLY.length() ? null : board.substring(WEEKLY.length(), dot);
    }

    /** The week of this day, from Monday: "2026w47" (as the weeks of the biggest clutches). */
    static String week(LocalDate day) {
        return day.get(WeekFields.ISO.weekBasedYear()) + "w" + day.get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    /** The weeks whose boards are kept on this day: this one first, then the {@value #WEEKS_KEPT} before. */
    static List<String> weeksKept(LocalDate today) {
        List<String> weeks = new ArrayList<>();
        for (int back = 0; back <= WEEKS_KEPT; back++) {
            weeks.add(week(today.minusWeeks(back)));
        }
        return weeks;
    }

    private boolean better(String board, long value, long than) {
        return lower(board) ? value < than : value > than;
    }

    /** Puts a best back, as read from the file (no checks). */
    void load(String board, UUID player, String name, long value) {
        boards.computeIfAbsent(board, b -> new HashMap<>()).put(player, new Entry(player, name, value));
    }

    /** A new score of the player on the board. */
    Result submit(String board, UUID player, String name, long value) {
        Map<UUID, Entry> entries = boards.computeIfAbsent(board, b -> new HashMap<>());
        Entry top = top(board);
        Entry mine = entries.get(player);
        boolean personal = mine == null || better(board, value, mine.value);
        boolean overall = top == null || better(board, value, top.value);
        if (personal) {
            entries.put(player, new Entry(player, name, value));
        } else if (!mine.name.equals(name)) {
            entries.put(player, new Entry(player, name, mine.value));
        }
        return new Result(mine == null ? null : mine.value, personal, overall);
    }

    /**
     * Every board under this one ("clutch.island.kb3.hits1" under "clutch.island") goes into it:
     * each player keeps their best of all, and the boards under it are gone. Returns how many
     * boards went in.
     */
    int fold(String board) {
        String under = board + ".";
        List<String> folded = new ArrayList<>();
        for (String other : boards.keySet()) {
            if (other.startsWith(under)) {
                folded.add(other);
            }
        }
        for (String other : folded) {
            for (Entry entry : boards.remove(other).values()) {
                submit(board, entry.player, entry.name, entry.value);
            }
            lowerIsBetter.remove(other);
        }
        return folded.size();
    }

    /** The board from goes into the board into (each player keeping their best), and is gone. */
    void merge(String from, String into) {
        Map<UUID, Entry> entries = boards.remove(from);
        if (entries == null) {
            return;
        }
        lowerIsBetter.remove(from);
        for (Entry entry : entries.values()) {
            submit(into, entry.player, entry.name, entry.value);
        }
    }

    /** Every board whose id starts with this, but those kept, is gone (the weeks before...). Returns how many. */
    int dropAllBut(String prefix, String... keep) {
        List<String> kept = Arrays.asList(keep);
        List<String> gone = new ArrayList<>();
        for (String board : boards.keySet()) {
            if (board.startsWith(prefix) && !kept.contains(board)) {
                gone.add(board);
            }
        }
        for (String board : gone) {
            boards.remove(board);
            lowerIsBetter.remove(board);
        }
        return gone.size();
    }

    /**
     * Every board of a week but those of these weeks is gone: only boards of the weeks, never one
     * of records. Returns how many.
     */
    int dropWeeksBut(Collection<String> weeks) {
        List<String> gone = new ArrayList<>();
        for (String board : boards.keySet()) {
            String week = weekOf(board);
            if (week != null && !weeks.contains(week)) {
                gone.add(board);
            }
        }
        for (String board : gone) {
            boards.remove(board);
            lowerIsBetter.remove(board);
        }
        return gone.size();
    }

    /** The boards of the weeks of every board whose id starts with this are gone. Returns how many. */
    int dropWeeksOf(String prefix) {
        List<String> gone = new ArrayList<>();
        for (String board : boards.keySet()) {
            String base = weeklyBase(board);
            if (base != null && base.startsWith(prefix)) {
                gone.add(board);
            }
        }
        for (String board : gone) {
            boards.remove(board);
            lowerIsBetter.remove(board);
        }
        return gone.size();
    }

    /** How many players have a score on the board. */
    int count(String board) {
        Map<UUID, Entry> entries = boards.get(board);
        return entries == null ? 0 : entries.size();
    }

    /** The player's best on the board, or null. */
    Long best(String board, UUID player) {
        Map<UUID, Entry> entries = boards.get(board);
        Entry entry = entries == null ? null : entries.get(player);
        return entry == null ? null : entry.value;
    }

    /** The best of all on the board, or null when nobody has a score. */
    Entry top(String board) {
        List<Entry> ranked = ranked(board);
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    /** The bests on the board, the best first. */
    List<Entry> ranked(String board) {
        Map<UUID, Entry> entries = boards.get(board);
        if (entries == null) {
            return Collections.emptyList();
        }
        List<Entry> ranked = new ArrayList<>(entries.values());
        boolean lower = lower(board);
        ranked.sort((a, b) -> {
            int order = Long.compare(a.value, b.value);
            if (order == 0) {
                return a.name.compareToIgnoreCase(b.name);
            }
            return lower ? order : -order;
        });
        return ranked;
    }

    /** The player's place on the board, from 1; 0 without a score. */
    int rank(String board, UUID player) {
        List<Entry> ranked = ranked(board);
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).player.equals(player)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Every board, for the file. */
    Map<String, Map<UUID, Entry>> boards() {
        return Collections.unmodifiableMap(boards);
    }
}
