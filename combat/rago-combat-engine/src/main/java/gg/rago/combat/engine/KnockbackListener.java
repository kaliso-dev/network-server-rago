package gg.rago.combat.engine;

import gg.rago.combat.model.HitRegistration;
import gg.rago.combat.model.KnockbackCalculator;
import gg.rago.combat.model.KnockbackProfile;
import gg.rago.combat.model.MeleeHit;
import gg.rago.combat.model.Vec3;
import org.bukkit.Location;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Replaces the velocity of player-against-player melee hits.
 *
 * <p>On a 1.8.8 server, one attack runs these steps on the main thread, in this order
 * ({@code EntityHuman.attack}):
 * <ol>
 *   <li>the damage event fires, before anything has moved: the attacker still has its sprint
 *       state and the victim its server-side motion;</li>
 *   <li>the server computes its own knockback and the sprint extra, and stops the attacker's
 *       sprint;</li>
 *   <li>if the victim is a player, {@link PlayerVelocityEvent} fires and the velocity packet is
 *       sent right after it; the server then restores the victim's previous motion.</li>
 * </ol>
 * The listener reads the inputs in step 1, computes the velocity with
 * {@link KnockbackCalculator}, and sets it in step 3. Only a velocity event that belongs to the
 * hit captured in the same tick, for the same victim, is changed: velocity from explosions,
 * projectiles or other plugins is never touched.
 */
final class KnockbackListener implements Listener {

    private final RagoCombatEngine plugin;
    private final KnockbackCalculator calculator = new KnockbackCalculator();

    /** Main thread only. */
    private PendingHit pending;

    KnockbackListener(RagoCombatEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        pending = null;
        if (event.getCause() != DamageCause.ENTITY_ATTACK
                || !(event.getEntity() instanceof Player)
                || !(event.getDamager() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();
        if (!HitRegistration.isFullHit(victim.getNoDamageTicks(), victim.getMaximumNoDamageTicks())) {
            // A damage top-up during the cooldown: vanilla sends no velocity for it.
            return;
        }

        Location attackerLocation = attacker.getLocation();
        Location victimLocation = victim.getLocation();
        Vector motion = victim.getVelocity();
        Vec3 liveMotion = new Vec3(motion.getX(), motion.getY(), motion.getZ());
        DelayedMotion used = plugin.motionForHit(victim, liveMotion);
        boolean sprinting = attacker.isSprinting();
        int enchantment = knockbackEnchantment(attacker);
        MeleeHit hit = new MeleeHit(
                attackerLocation.getX(), attackerLocation.getZ(), attackerLocation.getYaw(),
                enchantment + (sprinting ? 1 : 0),
                victimLocation.getX(), victimLocation.getZ(),
                used.motion);
        pending = new PendingHit(victim.getUniqueId(), plugin.currentTick(), attacker.getName(), victim.getName(),
                victim.getEntityId(), sprinting, enchantment, reportedOnGround(victim), hit, liveMotion,
                used.delayTicks);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        PendingHit hit = pending;
        pending = null;
        Player victim = event.getPlayer();
        if (hit == null || hit.tick != plugin.currentTick() || !hit.victimId.equals(victim.getUniqueId())) {
            return;
        }

        EngineSettings settings = plugin.settings();
        String world = victim.getWorld().getName();
        KnockbackProfile profile = settings.profileFor(world);
        Vec3 velocity = calculator.compute(profile, hit.hit);
        boolean applied = settings.mode() == EngineMode.OVERRIDE;
        if (applied) {
            Vector bukkitVelocity = new Vector(velocity.x(), velocity.y(), velocity.z());
            event.setVelocity(bukkitVelocity);
            // The server only copies the event's vector onto the player when it differs from the
            // vector the event started with, and which vector that is has varied between 1.8
            // forks. Setting it on the player as well makes the packet carry our velocity either
            // way; the server restores the previous motion right after sending it.
            victim.setVelocity(bukkitVelocity);
        }
        plugin.report(new HitRecord(System.currentTimeMillis(), hit.tick, settings.mode(), profile, world,
                hit, velocity, applied, settings.pingSimulationMs()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.forgetMotion(event.getPlayer().getUniqueId());
    }

    /**
     * The ground state the victim's client last reported. Bukkit deprecates it because a client
     * can lie about it, but it is exactly what the server's own physics used, so it is recorded
     * for analysis only and never used to compute knockback.
     */
    @SuppressWarnings("deprecation")
    private static boolean reportedOnGround(Player victim) {
        return victim.isOnGround();
    }

    private static int knockbackEnchantment(Player attacker) {
        ItemStack item = attacker.getItemInHand();
        return item == null ? 0 : item.getEnchantmentLevel(Enchantment.KNOCKBACK);
    }
}
