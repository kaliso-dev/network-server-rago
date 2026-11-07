package gg.rago.combat.engine;

import gg.rago.combat.model.MeleeHit;
import gg.rago.combat.model.Vec3;
import java.util.UUID;

/**
 * A melee hit seen in the damage event, waiting for the velocity event that the same attack fires
 * a moment later on the same thread.
 */
final class PendingHit {

    final UUID victimId;
    final long tick;
    final String attackerName;
    final String victimName;
    final int victimEntityId;
    final boolean sprinting;
    final int knockbackEnchantment;
    final boolean victimOnGround;
    final MeleeHit hit;
    /** The victim's motion on the server at the hit; {@link #hit} may hold an older one. */
    final Vec3 liveVictimMotion;
    /** How many ticks old the motion in {@link #hit} is: 0 unless the ping simulation is on. */
    final int simulatedDelayTicks;

    PendingHit(UUID victimId, long tick, String attackerName, String victimName, int victimEntityId,
            boolean sprinting, int knockbackEnchantment, boolean victimOnGround, MeleeHit hit, Vec3 liveVictimMotion,
            int simulatedDelayTicks) {
        this.victimId = victimId;
        this.tick = tick;
        this.attackerName = attackerName;
        this.victimName = victimName;
        this.victimEntityId = victimEntityId;
        this.sprinting = sprinting;
        this.knockbackEnchantment = knockbackEnchantment;
        this.victimOnGround = victimOnGround;
        this.hit = hit;
        this.liveVictimMotion = liveVictimMotion;
        this.simulatedDelayTicks = simulatedDelayTicks;
    }
}
