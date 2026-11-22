package gg.rago.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who fights whom in a Red Rover (plan 6.5, solution 1): the players in 1v1, in turn, the others
 * waiting. After each 1v1, the one who stays (the winner, or the loser, as the settings say) fights
 * the one who waited longest, and the other one waits. Two ways to win: the first to a number of
 * 1v1 won (points), or the last one in, a lost 1v1 being out (elimination: the winner stays).
 * No Bukkit here: the players are whatever T is (their UUID in the game). Shared by the Red Rovers
 * of RagoPractice (Sumo, Block Edit, Block Placement) and of RagoBedwars (Top Bridge), with their
 * settings ({@link Settings}).
 */
public final class RedRoverRotation<T> {

    /** How the game is won. */
    public enum Variant {
        /** The first to a number of 1v1 won wins the game. */
        POINTS,
        /** The loser of a 1v1 is out; the last one in wins the game. */
        ELIMINATION
    }

    /** Who stays on for the next 1v1 (with points; in an elimination, the winner, since the loser is out). */
    public enum Stays { WINNER, LOSER }

    /**
     * The settings of every Red Rover: red-rover in the config.yml of RagoPractice (variante,
     * reste, points-pour-gagner, secondes-par-1v1), which RagoBedwars reads too.
     */
    public static final class Settings {
        /** The settings when those of config.yml are wrong or missing: the bundled ones. */
        public static final Settings DEFAULTS = new Settings(Variant.POINTS, Stays.WINNER, 3, 90);

        public final Variant variant;
        public final Stays stays;
        /** With points: the 1v1 won to win the game. */
        public final int target;
        /** A 1v1 ends after this long (0: never): the one who landed more hits wins it. */
        public final int roundSeconds;

        public Settings(Variant variant, Stays stays, int target, int roundSeconds) {
            this.variant = variant;
            this.stays = stays;
            this.target = target;
            this.roundSeconds = roundSeconds;
        }

        /** The settings of these words and numbers of config.yml, or null if one is wrong (see {@link #check}). */
        public static Settings of(String variant, String stays, int target, int roundSeconds) {
            return check(variant, stays, target, roundSeconds) != null ? null
                    : new Settings(variantOf(variant), staysOf(stays), target, roundSeconds);
        }

        /** The variant of its word in config.yml (points, elimination), or null. */
        public static Variant variantOf(String word) {
            if ("points".equalsIgnoreCase(word)) {
                return Variant.POINTS;
            }
            if ("elimination".equalsIgnoreCase(word)) {
                return Variant.ELIMINATION;
            }
            return null;
        }

        /** Who stays on of its word in config.yml (gagnant, perdant), or null. */
        public static Stays staysOf(String word) {
            if ("gagnant".equalsIgnoreCase(word)) {
                return Stays.WINNER;
            }
            if ("perdant".equalsIgnoreCase(word)) {
                return Stays.LOSER;
            }
            return null;
        }

        /** What is wrong with these values, or null. */
        public static String check(String variant, String stays, int target, int roundSeconds) {
            if (variantOf(variant) == null) {
                return "variante must be points or elimination";
            }
            if (staysOf(stays) == null) {
                return "reste must be gagnant or perdant";
            }
            if (target < 1 || target > 10) {
                return "points-pour-gagner must be from 1 to 10";
            }
            if (roundSeconds < 0 || roundSeconds > 600) {
                return "secondes-par-1v1 must be from 0 (no limit) to 600";
            }
            return null;
        }

        /** What the game is won by, for the players: "First to 3" or "Elimination". */
        public String goal() {
            return variant == Variant.POINTS ? "First to " + target : "Elimination";
        }
    }

    private final Variant variant;
    private final Stays stays;
    private final int target;
    /** The two who fight now. */
    private final List<T> fight = new ArrayList<>();
    /** The ones who wait, the next to fight first. */
    private final List<T> waiting = new ArrayList<>();
    /** The 1v1 won by each player, in the order of the start. */
    private final Map<T, Integer> wins = new LinkedHashMap<>();
    /** The players still in the game: not out (elimination), not gone. */
    private final List<T> in = new ArrayList<>();
    /** The ones out (a 1v1 lost in an elimination) or gone, in the order they went out. */
    private final List<T> out = new ArrayList<>();

    /** Two players at least, in the order they come in: the first two fight first. */
    public RedRoverRotation(List<T> order, Variant variant, Stays stays, int target) {
        if (order.size() < 2) {
            throw new IllegalArgumentException("a Red Rover needs two players at least");
        }
        this.variant = variant;
        this.stays = stays;
        this.target = Math.max(1, target);
        for (T player : order) {
            wins.put(player, 0);
            in.add(player);
        }
        fight.add(order.get(0));
        fight.add(order.get(1));
        waiting.addAll(order.subList(2, order.size()));
    }

    public Variant variant() {
        return variant;
    }

    public int target() {
        return target;
    }

    /** The two who fight now (empty once the game is won). */
    public List<T> fight() {
        return Collections.unmodifiableList(fight);
    }

    /** The ones who wait, the next to fight first. */
    public List<T> waiting() {
        return Collections.unmodifiableList(waiting);
    }

    public boolean fights(T player) {
        return fight.contains(player);
    }

    /** True while the player is in the game (not out, not gone). */
    public boolean isIn(T player) {
        return in.contains(player);
    }

    /** The players still in. */
    public List<T> players() {
        return Collections.unmodifiableList(in);
    }

    /** The 1v1 this player won. */
    public int wins(T player) {
        Integer won = wins.get(player);
        return won == null ? 0 : won;
    }

    /** Everyone of the start, gone or not, with their 1v1 won. */
    public Map<T, Integer> scores() {
        return Collections.unmodifiableMap(wins);
    }

    /**
     * The best of the game, at most count of them (the top 3 of its end, the owner's ask of 22/11):
     * the winner of the game first (null: none), then by 1v1 won; between as many, one still in
     * before one out, the last out before those out earlier, else in the order of the start.
     */
    public List<T> top(T champion, int count) {
        List<T> ranked = new ArrayList<>(wins.keySet());
        ranked.sort((a, b) -> {
            if (a.equals(champion) != b.equals(champion)) {
                return a.equals(champion) ? -1 : 1;
            }
            if (wins(a) != wins(b)) {
                return wins(b) - wins(a);
            }
            // the sort keeps the order of the start between equals
            return Integer.compare(outRank(b), outRank(a));
        });
        return new ArrayList<>(ranked.subList(0, Math.min(Math.max(count, 0), ranked.size())));
    }

    /** Higher for one still in than for one out, and for one out later than for one out earlier. */
    private int outRank(T player) {
        int index = out.indexOf(player);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    /**
     * This fighter won the 1v1: the winner of the game if that makes one, else null and the next
     * 1v1 is set.
     */
    public T won(T winner) {
        if (!fight.contains(winner)) {
            throw new IllegalArgumentException(winner + " does not fight now");
        }
        T loser = fight.get(0).equals(winner) ? fight.get(1) : fight.get(0);
        wins.put(winner, wins(winner) + 1);
        fight.clear();
        if (variant == Variant.POINTS) {
            if (wins(winner) >= target) {
                return winner;
            }
            T stayer = stays == Stays.WINNER ? winner : loser;
            waiting.add(stayer == winner ? loser : winner);
            fight.add(stayer);
            fight.add(waiting.remove(0));
            return null;
        }
        in.remove(loser);
        out.add(loser);
        if (in.size() == 1) {
            return winner;
        }
        fight.add(winner);
        fight.add(waiting.remove(0));
        return null;
    }

    /**
     * This player left the game: out of it, their 1v1 won kept on the board. The last one in if one
     * is left (the winner), else null; a fighter who left is replaced by the next who waits, the
     * other fighter staying on.
     */
    public T left(T player) {
        if (!in.remove(player)) {
            return null;
        }
        out.add(player);
        waiting.remove(player);
        boolean fought = fight.remove(player);
        if (in.size() == 1) {
            fight.clear();
            return in.get(0);
        }
        if (fought && !waiting.isEmpty()) {
            fight.add(waiting.remove(0));
        }
        return null;
    }
}
