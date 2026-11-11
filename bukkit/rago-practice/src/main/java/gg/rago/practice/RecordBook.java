package gg.rago.practice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The personal bests of the trainings, board by board ("clutch.single", "bridge.ninja"...): the
 * best score of each player and the best of all, a time (lower is better) or a count (higher is
 * better). Pure Java, main thread.
 */
final class RecordBook {

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

    private boolean lower(String board) {
        Boolean lower = lowerIsBetter.get(board);
        return lower != null && lower;
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
