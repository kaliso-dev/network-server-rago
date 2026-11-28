package gg.rago.bedwars;

/**
 * The protection of a player who has just respawned (Bed Fight, as on Minemen): for a moment, no
 * enemy can hurt them; it ends at once when they hit an enemy themselves (they engaged a fight).
 * Times in milliseconds (System.currentTimeMillis). Main thread only.
 */
final class SpawnProtection {

    /** Until when it lasts; 0: none. */
    private long until;

    /** A new life: protected for this long from now (0 or less: not protected). */
    void start(long now, long millis) {
        until = millis > 0 ? now + millis : 0L;
    }

    /** The player hits an enemy: the protection ends, whatever the hit does. */
    void engaged() {
        until = 0L;
    }

    /** True while no enemy can hurt the player. */
    boolean on(long now) {
        return now < until;
    }
}
