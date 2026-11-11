package gg.rago.tools.kbanalyzer;

import gg.rago.combat.model.VelocityPacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What one recording says about its player's fight: the velocity packets the player received, the
 * hurt animations it saw, and the attacks, sprint changes and looks it sent. Times are the
 * recording client's clock, in milliseconds.
 */
public final class PlayerView {

    /** A velocity packet for the player and the animation that came with it. */
    public static final class Velocity {
        public final long ms;
        public final VelocityPacket packet;
        /** The player's ground state in its last tick before the packet. */
        public final boolean ground;
        /** A hurt animation for the player came within {@link #HURT_WINDOW_MILLIS} of the packet. */
        public final boolean hurt;

        Velocity(long ms, VelocityPacket packet, boolean ground, boolean hurt) {
            this.ms = ms;
            this.packet = packet;
            this.ground = ground;
            this.hurt = hurt;
        }

        /** Horizontal length in blocks per tick. */
        public double horizontal() {
            return Math.hypot(packet.x(), packet.z()) / VelocityPacket.UNITS_PER_BLOCK;
        }
    }

    /** A melee hit sends the hurt animation and the velocity packet in the same server tick. */
    static final long HURT_WINDOW_MILLIS = 100L;

    public final Recording recording;
    public final String player;
    public final String server;
    /** The player's entity id in most tick lines. */
    public final int entityId;
    public final long durationMillis;
    public final List<Velocity> velocities = new ArrayList<>();
    /** Times of the hurt animations seen, by entity id, sorted. */
    public final Map<Integer, List<Long>> hurts = new HashMap<>();
    /** Times of the attacks sent, by target entity id, sorted. */
    public final Map<Integer, List<Long>> attacks = new HashMap<>();
    /** Sent sprint changes: time and true for a start, in the order sent. */
    public final List<long[]> sprintChanges = new ArrayList<>();
    /** Sent looks: time and yaw, in the order sent. */
    public final List<double[]> looks = new ArrayList<>();
    /** Packets that could not be decoded. */
    public final List<String> problems = new ArrayList<>();

    private final long[] tickMillis;

    public PlayerView(Recording recording) {
        this.recording = recording;
        this.player = recording.session == null ? null : Json.text(recording.session, "player");
        this.server = recording.session == null ? null : Json.text(recording.session, "server");
        List<Tick> ticks = recording.ticks;
        this.tickMillis = new long[ticks.size()];
        Map<Integer, Integer> entityCounts = new HashMap<>();
        for (int i = 0; i < ticks.size(); i++) {
            tickMillis[i] = ticks.get(i).ms;
            entityCounts.merge(ticks.get(i).entityId, 1, Integer::sum);
        }
        this.entityId = entityCounts.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(-1);
        this.durationMillis = ticks.size() < 2 ? 0L : ticks.get(ticks.size() - 1).ms - ticks.get(0).ms;

        List<Packet> packets = recording.packetsByTime();
        List<Long> ownVelocityTimes = new ArrayList<>();
        List<VelocityPacket> ownVelocities = new ArrayList<>();
        for (Packet packet : packets) {
            try {
                read(packet, ownVelocityTimes, ownVelocities);
            } catch (IllegalArgumentException e) {
                problems.add("packet " + packet.id + " at " + packet.ms + ": " + e.getMessage());
            }
        }
        for (List<Long> times : hurts.values()) {
            Collections.sort(times);
        }
        for (List<Long> times : attacks.values()) {
            Collections.sort(times);
        }
        List<Long> ownHurts = hurts.getOrDefault(entityId, Collections.<Long>emptyList());
        for (int i = 0; i < ownVelocityTimes.size(); i++) {
            long ms = ownVelocityTimes.get(i);
            Tick before = tickBefore(ms);
            velocities.add(new Velocity(ms, ownVelocities.get(i), before != null && before.ground,
                    Times.nearest(ownHurts, ms, HURT_WINDOW_MILLIS) != null));
        }
    }

    private void read(Packet packet, List<Long> ownVelocityTimes, List<VelocityPacket> ownVelocities) {
        if (packet.is(true, Packet.IN_ENTITY_VELOCITY)) {
            if (packet.velocityEntity() == playerEntityAt(packet.ms)) {
                ownVelocityTimes.add(packet.ms);
                ownVelocities.add(packet.velocity());
            }
        } else if (packet.is(true, Packet.IN_ENTITY_STATUS)) {
            if (packet.status() == Packet.STATUS_HURT) {
                hurts.computeIfAbsent(packet.statusEntity(), k -> new ArrayList<>()).add(packet.ms);
            }
        } else if (packet.is(false, Packet.OUT_USE_ENTITY)) {
            if (packet.useEntityAction() == Packet.USE_ATTACK) {
                attacks.computeIfAbsent(packet.useEntityTarget(), k -> new ArrayList<>()).add(packet.ms);
            }
        } else if (packet.is(false, Packet.OUT_ENTITY_ACTION)) {
            int action = packet.entityAction();
            if (action == Packet.ACTION_START_SPRINTING || action == Packet.ACTION_STOP_SPRINTING) {
                sprintChanges.add(new long[] {packet.ms, action == Packet.ACTION_START_SPRINTING ? 1L : 0L});
            }
        } else if (packet.isSentLook()) {
            looks.add(new double[] {packet.ms, packet.lookYaw()});
        }
    }

    /**
     * The player's entity id when a packet arrived: the client handles a packet in its next tick,
     * so the tick line at or after the packet tells. The id changes on Hypixel with the server.
     */
    int playerEntityAt(long ms) {
        int index = firstTickAtOrAfter(ms);
        if (index < tickMillis.length) {
            return recording.ticks.get(index).entityId;
        }
        return tickMillis.length == 0 ? -1 : recording.ticks.get(tickMillis.length - 1).entityId;
    }

    /** The last tick line before the time, or null. */
    public Tick tickBefore(long ms) {
        int index = firstTickAtOrAfter(ms) - 1;
        return index >= 0 ? recording.ticks.get(index) : null;
    }

    private int firstTickAtOrAfter(long ms) {
        int low = 0;
        int high = tickMillis.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (tickMillis[middle] < ms) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    /** The entity the player attacked most, or -1 if it attacked nobody. */
    public int opponent() {
        int best = -1;
        int count = 0;
        for (Map.Entry<Integer, List<Long>> entry : attacks.entrySet()) {
            if (entry.getValue().size() > count) {
                best = entry.getKey();
                count = entry.getValue().size();
            }
        }
        return best;
    }

    /** The last sent look before the time, or NaN. */
    public double yawBefore(long ms) {
        double yaw = Double.NaN;
        for (double[] look : looks) {
            if (look[0] >= ms) {
                break;
            }
            yaw = look[1];
        }
        return yaw;
    }

    public double ticksPerSecond() {
        return durationMillis <= 0 ? Double.NaN : (recording.ticks.size() - 1) * 1000.0 / durationMillis;
    }
}
