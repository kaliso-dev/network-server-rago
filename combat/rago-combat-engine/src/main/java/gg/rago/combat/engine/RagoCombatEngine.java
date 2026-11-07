package gg.rago.combat.engine;

import gg.rago.combat.model.Vec3;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * RagoCombatEngine: applies a measurable, configurable knockback profile to melee hits between
 * players on a 1.8.8 server. See the module README for the behaviour and the configuration.
 */
public final class RagoCombatEngine extends JavaPlugin {

    private volatile EngineSettings settings;
    private HitRecorder recorder;
    private final Set<UUID> debugViewers = new HashSet<>();
    private long tick;
    private final MotionHistory motionHistory = new MotionHistory();
    /** Set once if the server does not expose the players' ping: they then count as 0 ms. */
    private boolean pingUnavailable;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            settings = EngineSettings.load(getConfig());
        } catch (IllegalArgumentException e) {
            // Never run a competitive server on half-read settings: stop here, loudly.
            getLogger().log(Level.SEVERE, "config.yml is invalid, RagoCombatEngine is disabled: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        applyRecorderSetting();

        getServer().getScheduler().runTaskTimer(this, this::endOfTick, 0L, 1L);
        getServer().getPluginManager().registerEvents(new KnockbackListener(this), this);
        getServer().getPluginManager().registerEvents(new HitDelayListener(this), this);
        applyHitDelay();
        PluginCommand command = getCommand("kb");
        KbCommand executor = new KbCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getLogger().info("Mode " + settings.mode().configName() + ", default profile " + settings.defaultProfile()
                + ", profiles " + settings.profiles().keySet() + ", hit delay " + settings.hitDelayTicks() + " ticks"
                + ", ping simulation " + settings.pingSimulationMs() + " ms");
    }

    @Override
    public void onDisable() {
        if (recorder != null) {
            recorder.close();
            recorder = null;
        }
        debugViewers.clear();
        motionHistory.clear();
    }

    /**
     * Runs once per tick, after the players' packets of the tick: counts the tick and, while the
     * ping simulation is on, records every player's motion.
     */
    private void endOfTick() {
        tick++;
        if (settings.pingSimulationMs() == 0) {
            if (!motionHistory.isEmpty()) {
                motionHistory.clear();
            }
            return;
        }
        for (Player player : getServer().getOnlinePlayers()) {
            Vector motion = player.getVelocity();
            motionHistory.record(player.getUniqueId(), tick, new Vec3(motion.getX(), motion.getY(), motion.getZ()));
        }
    }

    /**
     * The victim's motion to compute a hit with: the current one, or with the ping simulation on,
     * the one the victim had as many ticks before as its ping falls short of the simulated ping.
     *
     * @return the motion and the delay used, in ticks
     */
    DelayedMotion motionForHit(Player victim, Vec3 current) {
        int delay = settings.simulatedDelayTicks(settings.pingSimulationMs() == 0 ? 0 : pingOf(victim));
        Vec3 motion = motionHistory.delayed(victim.getUniqueId(), tick, delay, current);
        // A missing sample gives back the current motion itself: then no delay was applied.
        return new DelayedMotion(motion, motion == current ? 0 : delay);
    }

    void forgetMotion(UUID player) {
        motionHistory.forget(player);
    }

    /**
     * The player's ping as the server measures it (the keep-alive round trip, smoothed). The
     * Bukkit API of 1.8 has no getter for it, so it is read from the server's player object.
     */
    int pingOf(Player player) {
        if (pingUnavailable) {
            return 0;
        }
        try {
            Method getHandle = player.getClass().getMethod("getHandle");
            Object handle = getHandle.invoke(player);
            Field ping = handle.getClass().getField("ping");
            return ping.getInt(handle);
        } catch (ReflectiveOperationException | RuntimeException e) {
            pingUnavailable = true;
            getLogger().warning("The players' ping cannot be read on this server (" + e
                    + "): the ping simulation counts every player as 0 ms.");
            return 0;
        }
    }

    /** Server ticks since the plugin was enabled. Main thread only. */
    long currentTick() {
        return tick;
    }

    EngineSettings settings() {
        return settings;
    }

    void updateSettings(EngineSettings newSettings) {
        settings = newSettings;
        applyHitDelay();
    }

    /**
     * Reloads config.yml. If it is invalid, the current settings stay in place.
     *
     * @throws IllegalArgumentException if the file is invalid
     */
    void reloadSettings() {
        reloadConfig();
        settings = EngineSettings.load(getConfig());
        applyRecorderSetting();
        applyHitDelay();
    }

    /** Gives every online player the damage cooldown of the current hit delay. Main thread only. */
    void applyHitDelay() {
        for (Player player : getServer().getOnlinePlayers()) {
            applyHitDelay(player);
        }
    }

    void applyHitDelay(Player player) {
        int ticks = settings.maximumNoDamageTicks();
        if (player.getMaximumNoDamageTicks() != ticks) {
            player.setMaximumNoDamageTicks(ticks);
        }
    }

    private void applyRecorderSetting() {
        if (settings.recordHits() && recorder == null) {
            recorder = new HitRecorder(getDataFolder().toPath().resolve("hits"), getLogger());
        } else if (!settings.recordHits() && recorder != null) {
            recorder.close();
            recorder = null;
        }
    }

    long droppedRecords() {
        return recorder == null ? 0L : recorder.droppedCount();
    }

    boolean toggleDebug(UUID viewer) {
        if (debugViewers.remove(viewer)) {
            return false;
        }
        debugViewers.add(viewer);
        return true;
    }

    /** Records a staff change in the server log, where the audit trail lives until RagoCore exists. */
    void audit(String actor, String change) {
        getLogger().info("[audit] " + actor + ": " + change);
    }

    void report(HitRecord record) {
        if (recorder != null) {
            recorder.record(record);
        }
        if (debugViewers.isEmpty()) {
            return;
        }
        String message = ChatColor.DARK_GRAY + "[KB] " + ChatColor.WHITE + record.attacker + ChatColor.GRAY + " -> "
                + ChatColor.WHITE + record.victim + ChatColor.GRAY
                + " | niv. " + record.hit.knockbackLevel() + (record.sprinting ? " (sprint)" : "")
                + " | " + record.profile.name() + (record.applied ? "" : " (observe)")
                + (record.simulatedDelayTicks == 0 ? "" : " | ping simulé, mouvement d'il y a "
                        + record.simulatedDelayTicks + " ticks")
                + " | paquet " + ChatColor.WHITE + record.packet;
        for (UUID viewer : debugViewers) {
            Player player = getServer().getPlayer(viewer);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }
}
