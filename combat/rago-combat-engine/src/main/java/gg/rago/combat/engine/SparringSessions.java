package gg.rago.combat.engine;

import gg.rago.combat.model.KnockbackCalculator;
import gg.rago.combat.model.KnockbackProfile;
import gg.rago.combat.model.MeleeHit;
import gg.rago.combat.model.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Runs {@code /kb sparring}: every few ticks, a hit from an invisible attacker standing in front of
 * the player (see {@link Sparring}), computed with the world's profile like a real hit, with the
 * hurt animation and sound. Main thread only.
 *
 * <p>After a real hit, the server sends the velocity packet and then puts the victim's previous
 * motion back, so that the knockback does not count twice in the next hit. A plugin cannot send a
 * velocity without changing the motion, so this class does the same in two steps: it sets the
 * knockback (the packet goes out with the next update of the player), then, on the next tick, puts
 * the previous motion back and cancels the velocity event of that restore, so that no second
 * packet is sent. The server's motion therefore carries the knockback one tick longer than after
 * a real hit; the packet the player receives is exactly the profile's.
 */
final class SparringSessions implements Listener {

    static final String ATTACKER_NAME = "(sparring)";

    private final RagoCombatEngine plugin;
    private final KnockbackCalculator calculator = new KnockbackCalculator();
    private final Map<UUID, Session> sessions = new HashMap<>();

    private static final class Session {
        final Sparring.Pattern pattern;
        final int intervalTicks;
        int hits;
        BukkitTask task;
        /** The knockback just set, until its velocity event has fired. */
        Vector sentKnockback;
        /** The motion before that knockback, to put back once the packet is out. */
        Vector motionBefore;
        /** The motion being put back, whose velocity event is cancelled. */
        Vector restoring;

        Session(Sparring.Pattern pattern, int intervalTicks) {
            this.pattern = pattern;
            this.intervalTicks = intervalTicks;
        }
    }

    SparringSessions(RagoCombatEngine plugin) {
        this.plugin = plugin;
    }

    /** Starts (or restarts) sparring for a player. */
    void start(Player player, Sparring.Pattern pattern, int intervalTicks) {
        UUID id = player.getUniqueId();
        stop(id);
        Session session = new Session(pattern, intervalTicks);
        session.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> hit(id, session),
                intervalTicks, intervalTicks);
        sessions.put(id, session);
    }

    /** Stops sparring for a player; returns false if it was not running. */
    boolean stop(UUID player) {
        Session session = sessions.remove(player);
        if (session == null) {
            return false;
        }
        session.task.cancel();
        return true;
    }

    void stopAll() {
        for (Session session : sessions.values()) {
            session.task.cancel();
        }
        sessions.clear();
    }

    boolean isRunning(UUID player) {
        return sessions.containsKey(player);
    }

    private void hit(UUID id, Session session) {
        Player victim = plugin.getServer().getPlayer(id);
        if (victim == null) {
            stop(id);
            return;
        }
        if (victim.isDead()) {
            return;
        }
        boolean sprint = session.pattern.sprints(session.hits++);
        Location at = victim.getLocation();
        Vector before = victim.getVelocity();
        MeleeHit hit = Sparring.hit(at.getX(), at.getZ(), at.getYaw(),
                new Vec3(before.getX(), before.getY(), before.getZ()), sprint ? 1 : 0);
        EngineSettings settings = plugin.settings();
        String world = victim.getWorld().getName();
        KnockbackProfile profile = settings.profileFor(world);
        Vec3 velocity = calculator.compute(profile, hit);

        Vector knockback = new Vector(velocity.x(), velocity.y(), velocity.z());
        session.sentKnockback = knockback.clone();
        session.motionBefore = before;
        session.restoring = null;
        victim.playEffect(EntityEffect.HURT);
        victim.setVelocity(knockback);

        PendingHit pending = new PendingHit(id, plugin.currentTick(), ATTACKER_NAME, victim.getName(),
                victim.getEntityId(), sprint, 0, reportedOnGround(victim), hit);
        plugin.report(new HitRecord(System.currentTimeMillis(), pending.tick, settings.mode(), profile, world,
                pending, velocity, true));
    }

    /** Runs before the knockback listener, which ignores cancelled events. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.restoring != null && session.restoring.equals(event.getVelocity())) {
            // Putting the previous motion back must not send a second velocity packet.
            session.restoring = null;
            event.setCancelled(true);
            return;
        }
        if (session.sentKnockback != null && session.sentKnockback.equals(event.getVelocity())) {
            // The knockback packet goes out right after this event: put the motion back next tick.
            Vector before = session.motionBefore;
            session.sentKnockback = null;
            session.motionBefore = null;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (sessions.get(player.getUniqueId()) == session && player.isOnline()) {
                    session.restoring = before.clone();
                    player.setVelocity(before);
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId());
    }

    /** See {@code KnockbackListener}: recorded for analysis, never used to compute knockback. */
    @SuppressWarnings("deprecation")
    private static boolean reportedOnGround(Player victim) {
        return victim.isOnGround();
    }
}
