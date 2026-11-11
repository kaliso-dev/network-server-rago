package gg.rago.combat.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Simulates how a 1.8.9 client moves after it receives a velocity, when the player presses no key
 * and stands on flat, solid, normal ground (slipperiness 0.6) with nothing around.
 *
 * <p>Once the server has sent a velocity, the victim's own client computes the trajectory: each
 * tick it zeroes motions below 0.005, moves, then applies gravity (0.08), air drag (0.98) and
 * horizontal friction (0.91 in the air, 0.6 × 0.91 on the ground, using the ground state from
 * before the move). This class reproduces that order and arithmetic
 * ({@code EntityLivingBase.onLivingUpdate} and {@code moveEntityWithHeading}) so that recorded
 * trajectories can be compared with the velocity packets that caused them.
 *
 * <p>It does not model player input, walls, blocks with another slipperiness, water, ladders or
 * potion effects. Use it on recordings made in those conditions only.
 */
public final class ClientTrajectory {

    /** Gravity subtracted from the vertical motion each tick. */
    public static final double GRAVITY = 0.08D;
    /** Vertical drag, {@code 0.98F} widened to double as the client does. */
    public static final double VERTICAL_DRAG = 0.9800000190734863D;
    /** Horizontal friction in the air. */
    public static final float AIR_FRICTION = 0.91F;
    /** Slipperiness of ordinary blocks (not ice or slime). */
    public static final float DEFAULT_SLIPPERINESS = 0.6F;
    /** Motions smaller than this are set to zero at the start of each tick. */
    public static final double MOTION_THRESHOLD = 0.005D;

    private ClientTrajectory() {
    }

    /**
     * Runs the simulation.
     *
     * @param velocity     the velocity the client sets, usually {@link VelocityPacket#decode()}
     * @param startOnGround whether the player stands on the ground when the velocity arrives
     * @param maxTicks     number of ticks to simulate; the simulation stops earlier, on the tick the
     *                     player lands, if it left the ground
     */
    public static Trajectory simulate(Vec3 velocity, boolean startOnGround, int maxTicks) {
        Objects.requireNonNull(velocity, "velocity");
        if (maxTicks < 1) {
            throw new IllegalArgumentException("maxTicks must be at least 1, got " + maxTicks);
        }
        List<Tick> ticks = new ArrayList<>();
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        double motionX = velocity.x();
        double motionY = velocity.y();
        double motionZ = velocity.z();
        boolean onGround = startOnGround;
        boolean leftGround = false;

        for (int tick = 1; tick <= maxTicks; tick++) {
            if (Math.abs(motionX) < MOTION_THRESHOLD) {
                motionX = 0.0D;
            }
            if (Math.abs(motionY) < MOTION_THRESHOLD) {
                motionY = 0.0D;
            }
            if (Math.abs(motionZ) < MOTION_THRESHOLD) {
                motionZ = 0.0D;
            }

            float friction = onGround ? DEFAULT_SLIPPERINESS * AIR_FRICTION : AIR_FRICTION;

            // Move. The only obstacle is the ground plane at y = 0.
            x += motionX;
            z += motionZ;
            double wantedY = motionY;
            double movedY = wantedY;
            if (y + movedY < 0.0D) {
                movedY = -y;
            }
            y += movedY;
            boolean collidedVertically = movedY != wantedY;
            onGround = collidedVertically && wantedY < 0.0D;
            if (collidedVertically) {
                motionY = 0.0D;
            }

            motionY -= GRAVITY;
            motionY *= VERTICAL_DRAG;
            motionX *= (double) friction;
            motionZ *= (double) friction;

            ticks.add(new Tick(tick, new Vec3(x, y, z), new Vec3(motionX, motionY, motionZ), onGround));
            if (!onGround) {
                leftGround = true;
            } else if (leftGround) {
                break;
            }
        }
        return new Trajectory(velocity, ticks);
    }

    /** The state of the player at the end of a tick. */
    public static final class Tick {
        private final int number;
        private final Vec3 position;
        private final Vec3 motion;
        private final boolean onGround;

        Tick(int number, Vec3 position, Vec3 motion, boolean onGround) {
            this.number = number;
            this.position = position;
            this.motion = motion;
            this.onGround = onGround;
        }

        /** Tick number, 1 for the first tick after the velocity was received. */
        public int number() {
            return number;
        }

        /** Position relative to where the velocity was received, in blocks. */
        public Vec3 position() {
            return position;
        }

        /** Motion after this tick's gravity and friction, in blocks per tick. */
        public Vec3 motion() {
            return motion;
        }

        public boolean onGround() {
            return onGround;
        }
    }

    /** A simulated trajectory and the measures the knockback reports use. */
    public static final class Trajectory {
        private final Vec3 initialVelocity;
        private final List<Tick> ticks;

        Trajectory(Vec3 initialVelocity, List<Tick> ticks) {
            this.initialVelocity = initialVelocity;
            this.ticks = Collections.unmodifiableList(ticks);
        }

        public Vec3 initialVelocity() {
            return initialVelocity;
        }

        public List<Tick> ticks() {
            return ticks;
        }

        /** Highest height reached above the starting point, in blocks. */
        public double peakHeight() {
            double peak = 0.0D;
            for (Tick tick : ticks) {
                peak = Math.max(peak, tick.position().y());
            }
            return peak;
        }

        /**
         * Number of the tick on which the player is back on the ground after having left it, or
         * -1 if it never left the ground or had not landed within the simulated ticks.
         */
        public int landingTick() {
            boolean left = false;
            for (Tick tick : ticks) {
                if (!tick.onGround()) {
                    left = true;
                } else if (left) {
                    return tick.number();
                }
            }
            return -1;
        }

        /**
         * Horizontal distance from the starting point at landing, in blocks, or {@code NaN} if the
         * player had not landed within the simulated ticks.
         */
        public double horizontalDistanceAtLanding() {
            int landing = landingTick();
            if (landing < 0) {
                return Double.NaN;
            }
            return ticks.get(landing - 1).position().horizontalLength();
        }
    }
}
