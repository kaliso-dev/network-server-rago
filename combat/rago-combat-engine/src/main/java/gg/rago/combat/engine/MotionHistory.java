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

    /** Enough for the longest delay the settings allow, one tick further back. */
    static final int CAPACITY = EngineSettings.MAX_PING_SIMULATION_MS / 50 + 2;

    private static final class Samples {
        final long[] ticks = new long[CAPACITY];
        final Vec3[] motions = new Vec3[CAPACITY];

        Samples() {
            Arrays.fill(ticks, Long.MIN_VALUE);
        }

        /** The sample of {@code tick}, or null if it was never taken or has been overwritten. */
        Vec3 at(long tick) {
            int slot = (int) Math.floorMod(tick, (long) CAPACITY);
            return ticks[slot] == tick ? motions[slot] : null;
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
     * <p>The server changes a player's motion when it handles that player's movement packet (one per
     * client tick), and it handles the packets of a tick in the order they arrived, attacks
     * included, before the scheduler takes the samples. So during a hit, the victim's movement of
     * the current tick may or may not have been handled yet:
     * <ul>
     *   <li>if it has, the current motion is one movement newer than the latest sample, the one of
     *       {@code lastTick}: a delay of {@code n} ticks takes the sample of {@code lastTick - (n - 1)};
     *   <li>if not, the current motion is still the latest sample: a delay of {@code n} ticks takes
     *       the sample of {@code lastTick - n}.
     * </ul>
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
        int delay = Math.min(delayTicks, CAPACITY - 1);
        Vec3 latest = samples.at(lastTick);
        boolean movedThisTick = latest == null || !latest.equals(current);
        Vec3 sample = samples.at(lastTick - delay + (movedThisTick ? 1 : 0));
        return sample != null ? sample : current;
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
