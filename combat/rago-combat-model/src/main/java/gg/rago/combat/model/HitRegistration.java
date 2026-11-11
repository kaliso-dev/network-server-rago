package gg.rago.combat.model;

/**
 * Rules of the 1.8 damage cooldown, from {@code EntityLiving.damageEntity}.
 *
 * <p>After a hit that counts, a player is protected for {@code maxNoDamageTicks} ticks (20 by
 * default). During the second half of that window ({@code noDamageTicks > max / 2}), a new hit
 * is only accepted if it deals more damage than the previous one, and then only the difference is
 * dealt: no knockback is computed and no velocity packet is sent. The melee extra push is still
 * added to the server-side motion, which only shows in the friction term of the next hit.
 */
public final class HitRegistration {

    private HitRegistration() {
    }

    /**
     * True if a hit landing now is a full hit (damage, knockback and velocity packet), false if it
     * falls in the cooldown window where only a stronger hit tops up the damage.
     *
     * @param noDamageTicks    the victim's current cooldown counter
     * @param maxNoDamageTicks the victim's cooldown length
     */
    public static boolean isFullHit(int noDamageTicks, int maxNoDamageTicks) {
        return !((float) noDamageTicks > (float) maxNoDamageTicks / 2.0F);
    }
}
