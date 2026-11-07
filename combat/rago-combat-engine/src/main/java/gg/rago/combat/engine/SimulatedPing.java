package gg.rago.combat.engine;

import gg.rago.combat.model.Vec3;

/** What the ping simulation changes for one hit. Without it: the current motion and no waits. */
final class SimulatedPing {

    /** The victim's motion the hit is computed with. */
    final Vec3 motion;
    /** How many ticks old {@link #motion} is: 0 for the current one. */
    final int motionDelayTicks;
    /** How long the knockback packet waits before it is sent to the victim, in milliseconds. */
    final int knockbackDelayMs;

    SimulatedPing(Vec3 motion, int motionDelayTicks, int knockbackDelayMs) {
        this.motion = motion;
        this.motionDelayTicks = motionDelayTicks;
        this.knockbackDelayMs = knockbackDelayMs;
    }
}
