package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The flags of a Capture the Flag game and its score, without Bukkit: each team has its flag, at
 * its base, carried by an enemy, or dropped where its carrier died (back at its base when one of
 * its team touches it, or after {@link #returnSeconds} seconds). A carrier scores at their own
 * flag, while it is at its base; the first team to {@link #captures} captures wins, else the one
 * with the most at the end of the time (a draw when two have as many).
 *
 * <p>The rules here (a dropped flag that waits, a capture only with one's own flag at home) are
 * the classic ones of the proposal of step 7; the numbers are starting values, to confirm (ctf: in
 * config.yml).
 */
final class CaptureFlags {

    /** A dropped flag goes back to its base after this long, by default (seconds; ctf.retour-drapeau-secondes). */
    static final int RETURN_SECONDS = 30;
    /** How near a flag a player touches it: across, and up or down (blocks). */
    static final double TOUCH = 1.5;
    static final double TOUCH_HEIGHT = 2.0;

    enum State { HOME, CARRIED, DROPPED }

    /** What a touch did. */
    enum Touch { NOTHING, TAKEN, RETURNED, CAPTURED, NOT_HOME }

    /** The flag of one team. */
    static final class Flag {
        final int team;
        State state = State.HOME;
        /** Who carries it, while carried. */
        UUID carrier;
        /** Where it lies, while dropped, and since when (the second of the game). */
        double x;
        double y;
        double z;
        int droppedAt;

        Flag(int team) {
            this.team = team;
        }
    }

    private final Flag[] flags;
    private final int[] score;
    final int captures;
    /** The seconds a dropped flag waits before it goes back to its base. */
    final int returnSeconds;

    CaptureFlags(int teams, int captures, int returnSeconds) {
        flags = new Flag[teams];
        for (int i = 0; i < teams; i++) {
            flags[i] = new Flag(i);
        }
        score = new int[teams];
        this.captures = captures;
        this.returnSeconds = returnSeconds;
    }

    Flag flag(int team) {
        return flags[team];
    }

    int teams() {
        return flags.length;
    }

    int score(int team) {
        return score[team];
    }

    /** The team whose flag this player carries, or -1. */
    int carriedBy(UUID player) {
        for (Flag flag : flags) {
            if (flag.state == State.CARRIED && flag.carrier.equals(player)) {
                return flag.team;
            }
        }
        return -1;
    }

    /** True if this offset (from a player to a flag) is near enough to touch it. */
    static boolean touches(double dx, double dy, double dz) {
        return dx * dx + dz * dz <= TOUCH * TOUCH && Math.abs(dy) <= TOUCH_HEIGHT;
    }

    /**
     * A player of this team touches the flag of that one: an enemy takes it (at its base or
     * dropped) when they carry none; one of its team brings it back when dropped, or, carrying an
     * enemy flag, scores at it while it is home (or is told it is not).
     */
    Touch touch(int playerTeam, UUID player, int flagTeam) {
        Flag flag = flags[flagTeam];
        int carried = carriedBy(player);
        if (playerTeam != flagTeam) {
            if (carried < 0 && flag.state != State.CARRIED) {
                flag.state = State.CARRIED;
                flag.carrier = player;
                return Touch.TAKEN;
            }
            return Touch.NOTHING;
        }
        if (flag.state == State.DROPPED) {
            home(flagTeam);
            return Touch.RETURNED;
        }
        if (carried < 0) {
            return Touch.NOTHING;
        }
        if (flag.state != State.HOME) {
            return Touch.NOT_HOME;
        }
        score[playerTeam]++;
        home(carried);
        return Touch.CAPTURED;
    }

    /** The carrier of this flag died there, at this second of the game: it lies there. */
    void drop(int flagTeam, double x, double y, double z, int second) {
        Flag flag = flags[flagTeam];
        flag.state = State.DROPPED;
        flag.carrier = null;
        flag.x = x;
        flag.y = y;
        flag.z = z;
        flag.droppedAt = second;
    }

    /** The flag is back at its base. */
    void home(int flagTeam) {
        Flag flag = flags[flagTeam];
        flag.state = State.HOME;
        flag.carrier = null;
    }

    /** The dropped flags that have waited long enough at this second: they go back to their base. */
    List<Integer> expired(int second) {
        List<Integer> back = new ArrayList<>();
        for (Flag flag : flags) {
            if (flag.state == State.DROPPED && second - flag.droppedAt >= returnSeconds) {
                back.add(flag.team);
            }
        }
        return back;
    }

    /** The seconds before a dropped flag goes back (0 when it is not dropped). */
    int secondsLeft(int flagTeam, int second) {
        Flag flag = flags[flagTeam];
        return flag.state == State.DROPPED ? Math.max(0, returnSeconds - (second - flag.droppedAt)) : 0;
    }

    /** The team that has its captures to win, or -1. */
    int winner() {
        for (int i = 0; i < score.length; i++) {
            if (score[i] >= captures) {
                return i;
            }
        }
        return -1;
    }

    /** At the end of the time: the team with the most captures, -1 for a draw (two with as many). */
    int leader() {
        int best = -1;
        boolean tie = false;
        for (int i = 0; i < score.length; i++) {
            if (best < 0 || score[i] > score[best]) {
                best = i;
                tie = false;
            } else if (score[i] == score[best]) {
                tie = true;
            }
        }
        return tie ? -1 : best;
    }

    /** "Safe", "Taken", "Dropped 24s": the flag of this team on the sidebar. */
    String stateLine(int flagTeam, int second) {
        switch (flags[flagTeam].state) {
            case CARRIED:
                return "Taken";
            case DROPPED:
                return "Dropped " + secondsLeft(flagTeam, second) + "s";
            default:
                return "Safe";
        }
    }
}
