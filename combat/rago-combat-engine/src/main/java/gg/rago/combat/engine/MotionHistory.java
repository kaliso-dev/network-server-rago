package gg.rago.combat.engine;

import gg.rago.combat.model.Vec3;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The server-side motion of each player over the last ticks, for the ping simulation.
 *
 * <p>After a hit, the server keeps the victim's own motion and only changes it once the victim's
 * client, having received the knockback, sends its new movements. The higher the ping, the later
 * that happens, so the next hit of a combo finds an older motion. Taking the motion of a few ticks
 * before reproduces this for a victim with less ping (docs/combat/mesures-hypixel.md).
 *
 * <p>One sample per player and per tick, taken once all of that tick's packets are handled. Main
 * thread only.
 */
final class MotionHistory {

    /** Enough for the longest delay the settings allow. */
    static final int CAPACITY = EngineSettings.MAX_PING_SIMULATION_MS / 50 + 1;

    private static final class Samples {
        final long[] ticks = new long[CAPACITY];
        final Vec3[] motions = new Vec3[CAPACITY];

        Samples() {
            Arrays.fill(ticks, Long.MIN_VALUE);
        }
    }

    private final Map<UUID, Samples> players = new HashMap<>();

    /** Records a player's motion at the end of {@code tick}. */
    void record(UUID player, long tick, Vec3 motion) {
        Samples samples = players.computeIfAbsent(player, id -> new Samples());
        int slot = (int) Math.floorMod(tick, (long) CAPACITY);
        samples.ticks[slot] = tick;
        samples.motions[slot] = motion;
    }

    /**
     * The motion to use for a hit, as if the server had received the victim's movements
     * {@code delayTicks} ticks later.
     *
     * <p>A server tick handles the players' packets (moves and attacks) before the scheduler runs, and
     * the samples are taken by the scheduler. So during a hit, {@code lastTick}, the tick of the
     * latest sample, ended one tick before the current motion: a delay of one tick takes that
     * sample, a delay of {@code n} ticks the one taken {@code n - 1} ticks before it.
     *
     * @return {@code current} when the delay is 0 or the sample is missing (a player who just
     *     joined, or a simulation just turned on), else the sample
     */
    Vec3 delayed(UUID player, long lastTick, int delayTicks, Vec3 current) {
        if (delayTicks <= 0) {
            return current;
        }
        Samples samples = players.get(player);
        if (samples == null) {
            return current;
        }
        long wanted = lastTick - (Math.min(delayTicks, CAPACITY) - 1);
        int slot = (int) Math.floorMod(wanted, (long) CAPACITY);
        return samples.ticks[slot] == wanted ? samples.motions[slot] : current;
    }

    void forget(UUID player) {
        players.remove(player);
    }

    void clear() {
        players.clear();
    }

    boolean isEmpty() {
        return players.isEmpty();
    }
}
