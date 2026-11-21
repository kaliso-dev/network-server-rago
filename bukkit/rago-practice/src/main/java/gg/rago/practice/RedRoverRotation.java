package gg.rago.practice;

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
 * No Bukkit here: the players are whatever T is (their UUID in the game).
 */
final class RedRoverRotation<T> {

    /** How the game is won. */
    enum Variant {
        /** The first to a number of 1v1 won wins the game. */
        POINTS,
        /** The loser of a 1v1 is out; the last one in wins the game. */
        ELIMINATION
    }

    /** Who stays on for the next 1v1 (with points; in an elimination, the winner, since the loser is out). */
    enum Stays { WINNER, LOSER }

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

    /** Two players at least, in the order they come in: the first two fight first. */
    RedRoverRotation(List<T> order, Variant variant, Stays stays, int target) {
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

    Variant variant() {
        return variant;
    }

    int target() {
        return target;
    }

    /** The two who fight now (empty once the game is won). */
    List<T> fight() {
        return Collections.unmodifiableList(fight);
    }

    /** The ones who wait, the next to fight first. */
    List<T> waiting() {
        return Collections.unmodifiableList(waiting);
    }

    boolean fights(T player) {
        return fight.contains(player);
    }

    /** True while the player is in the game (not out, not gone). */
    boolean isIn(T player) {
        return in.contains(player);
    }

    /** The players still in. */
    List<T> players() {
        return Collections.unmodifiableList(in);
    }

    /** The 1v1 this player won. */
    int wins(T player) {
        Integer won = wins.get(player);
        return won == null ? 0 : won;
    }

    /** Everyone of the start, gone or not, with their 1v1 won. */
    Map<T, Integer> scores() {
        return Collections.unmodifiableMap(wins);
    }

    /**
     * This fighter won the 1v1: the winner of the game if that makes one, else null and the next
     * 1v1 is set.
     */
    T won(T winner) {
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
    T left(T player) {
        if (!in.remove(player)) {
            return null;
        }
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
