package gg.rago.combat.engine;

import gg.rago.combat.model.Vec3;

/** The victim's motion a hit is computed with, and how many ticks old it is (0 = the current one). */
final class DelayedMotion {

    final Vec3 motion;
    final int delayTicks;

    DelayedMotion(Vec3 motion, int delayTicks) {
        this.motion = motion;
        this.delayTicks = delayTicks;
    }
}
