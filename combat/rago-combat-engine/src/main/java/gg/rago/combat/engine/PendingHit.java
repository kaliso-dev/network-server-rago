package gg.rago.combat.engine;

import gg.rago.combat.model.MeleeHit;
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

    PendingHit(UUID victimId, long tick, String attackerName, String victimName, int victimEntityId,
            boolean sprinting, int knockbackEnchantment, boolean victimOnGround, MeleeHit hit) {
        this.victimId = victimId;
        this.tick = tick;
        this.attackerName = attackerName;
        this.victimName = victimName;
        this.victimEntityId = victimEntityId;
        this.sprinting = sprinting;
        this.knockbackEnchantment = knockbackEnchantment;
        this.victimOnGround = victimOnGround;
        this.hit = hit;
    }
}
