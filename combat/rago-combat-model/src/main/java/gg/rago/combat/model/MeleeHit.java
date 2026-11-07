package gg.rago.combat.model;

import java.util.Objects;

/**
 * Everything the server knows about a melee hit at the moment it computes knockback.
 *
 * <p>All values are the server's view: positions from the last movement packets, the attacker's
 * yaw from its last look packet, and the victim's server-side motion. That motion is not the
 * velocity the victim's client is moving with: for a player standing on the ground the server
 * holds {@code motY = -0.0784000015258789} (one tick of gravity), which is why vanilla's first
 * vertical knockback is 0.3608 rather than 0.4.
 */
public final class MeleeHit {

    private final double attackerX;
    private final double attackerZ;
    private final float attackerYaw;
    private final int knockbackLevel;
    private final double victimX;
    private final double victimZ;
    private final Vec3 victimMotion;

    /**
     * @param attackerX      attacker position, x
     * @param attackerZ      attacker position, z
     * @param attackerYaw    attacker yaw in degrees, as the server holds it
     * @param knockbackLevel Knockback enchantment level of the held item, plus one if the
     *                       attacker was sprinting when the attack started
     * @param victimX        victim position, x
     * @param victimZ        victim position, z
     * @param victimMotion   victim server-side motion before the hit, in blocks per tick
     */
    public MeleeHit(double attackerX, double attackerZ, float attackerYaw, int knockbackLevel,
            double victimX, double victimZ, Vec3 victimMotion) {
        if (knockbackLevel < 0) {
            throw new IllegalArgumentException("knockbackLevel must not be negative, got " + knockbackLevel);
        }
        this.attackerX = attackerX;
        this.attackerZ = attackerZ;
        this.attackerYaw = attackerYaw;
        this.knockbackLevel = knockbackLevel;
        this.victimX = victimX;
        this.victimZ = victimZ;
        this.victimMotion = Objects.requireNonNull(victimMotion, "victimMotion");
    }

    public double attackerX() {
        return attackerX;
    }

    public double attackerZ() {
        return attackerZ;
    }

    public float attackerYaw() {
        return attackerYaw;
    }

    public int knockbackLevel() {
        return knockbackLevel;
    }

    public double victimX() {
        return victimX;
    }

    public double victimZ() {
        return victimZ;
    }

    public Vec3 victimMotion() {
        return victimMotion;
    }

    @Override
    public String toString() {
        return "MeleeHit{attacker=(" + attackerX + ", " + attackerZ + "), yaw=" + attackerYaw
                + ", level=" + knockbackLevel + ", victim=(" + victimX + ", " + victimZ
                + "), victimMotion=" + victimMotion + '}';
    }
}
