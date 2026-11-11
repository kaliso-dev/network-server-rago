package gg.rago.practice;

import gg.rago.lobby.api.FireballAim;
import org.bukkit.Location;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * The Bed Wars fireball, with the values of Hypixel (those of BedWars1058, as in RagoBedwars):
 * thrown at speed 1 where the player looks, speeding up by 0.1 each tick (see {@link FireballAim}),
 * it pushes the players up to 3 blocks away from where it lands, and hurts them by 1 heart. The
 * math is pure Java.
 */
final class Fireballs {

    static final double SPEED = 1.0;
    static final double RADIUS = 3.0;
    static final double HORIZONTAL = 1.0;
    static final double VERTICAL = 0.65;
    static final double SELF_DAMAGE = 2.0;
    static final double ENEMY_DAMAGE = 2.0;
    /** Half a second between two fireballs of a player, as on Hypixel. */
    static final long COOLDOWN_MS = 500L;
    /** The blast breaks blocks as a strength 3 explosion would (only placed blocks, see the listener). */
    static final float YIELD = 3f;

    private Fireballs() {
    }

    /** The player throws a fireball from their hand: one less in the stack. */
    static Fireball launch(Player player) {
        ItemStack hand = player.getItemInHand();
        if (hand != null && hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.setItemInHand(null);
        }
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setIsIncendiary(false);
        fireball.setYield(YIELD);
        Vector direction = player.getEyeLocation().getDirection();
        // not Fireball#setDirection: on 1.8 it makes the fireball ten times too fast
        FireballAim.aim(fireball, direction);
        fireball.setVelocity(direction.clone().multiply(SPEED));
        return fireball;
    }

    /** A fireball sent by the game itself, from this point to that one. */
    static Fireball send(Location from, Location to, double speed) {
        Vector direction = to.toVector().subtract(from.toVector());
        if (direction.lengthSquared() < 1e-6) {
            direction = new Vector(0, -1, 0);
        }
        direction.normalize();
        Location start = from.clone();
        start.setDirection(direction);
        Fireball fireball = from.getWorld().spawn(start, org.bukkit.entity.LargeFireball.class);
        fireball.setIsIncendiary(false);
        fireball.setYield(0f);
        if (!FireballAim.aim(fireball, direction)) {
            // without it, a fireball of the world's own would slow down and stop
            fireball.setDirection(direction);
        }
        fireball.setVelocity(direction.clone().multiply(speed));
        return fireball;
    }

    /**
     * The velocity the blast at (bx, by, bz) gives a player standing at (px, py, pz), as
     * BedWars1058 computes it: away from the blast horizontally, and up; more up when the blast is
     * under the player's feet (a fireball jump).
     */
    static double[] push(double bx, double by, double bz, double px, double py, double pz) {
        double dx = bx - px;
        double dy = by - py;
        double dz = bz - pz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-6) {
            dx = 0;
            dy = 1;
            dz = 0;
            length = 1;
        }
        double nx = dx / length;
        double ny = dy / length;
        double nz = dz / length;
        double y = ny < 0 ? ny + 1.5 : ny;
        y = y <= 0.5 ? VERTICAL * 1.5 : y * VERTICAL * 1.5;
        return new double[] {-nx * HORIZONTAL, y, -nz * HORIZONTAL};
    }

    /** How fast a fireball's blast sends a lit TNT away, sideways and up: a few blocks in all. */
    static final double TNT_NUDGE_HORIZONTAL = 0.22;
    static final double TNT_NUDGE_VERTICAL = 0.22;

    /**
     * The velocity a fireball's blast at (bx, by, bz) gives a lit TNT at (tx, ty, tz): away from
     * the blast on the ground, a little up, so that it lands a few blocks away.
     */
    static double[] tntNudge(double bx, double by, double bz, double tx, double ty, double tz) {
        double dx = tx - bx;
        double dz = tz - bz;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-6) {
            return new double[] {0, TNT_NUDGE_VERTICAL, 0};
        }
        return new double[] {dx / length * TNT_NUDGE_HORIZONTAL, TNT_NUDGE_VERTICAL, dz / length * TNT_NUDGE_HORIZONTAL};
    }

    /** The power of a TNT's blast: it reaches twice as many blocks. */
    static final double TNT_POWER = 4.0;
    /** The height of a player's eyes over their feet. */
    private static final double EYES = 1.62;

    /**
     * The velocity a TNT's blast at (bx, by, bz) adds to a player whose feet are at (px, py, pz),
     * as the server computes it for nothing in between: from the blast to their eyes, 1 at the
     * blast down to 0 at {@link #TNT_POWER} x 2 blocks. Null when it does not reach them.
     */
    static double[] tntPush(double bx, double by, double bz, double px, double py, double pz) {
        double reach = TNT_POWER * 2;
        double feet = Math.sqrt((px - bx) * (px - bx) + (py - by) * (py - by) + (pz - bz) * (pz - bz)) / reach;
        if (feet > 1) {
            return null;
        }
        double dx = px - bx;
        double dy = py + EYES - by;
        double dz = pz - bz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-6) {
            return null;
        }
        double strength = 1 - feet;
        return new double[] {dx / length * strength, dy / length * strength, dz / length * strength};
    }

    /** True if a player at this offset from the blast is pushed by it. */
    static boolean reaches(double dx, double dy, double dz) {
        return Math.abs(dx) <= RADIUS && Math.abs(dy) <= RADIUS && Math.abs(dz) <= RADIUS;
    }
}
