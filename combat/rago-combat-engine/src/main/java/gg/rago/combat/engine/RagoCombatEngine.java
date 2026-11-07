package gg.rago.combat.engine;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RagoCombatEngine: applies a measurable, configurable knockback profile to melee hits between
 * players on a 1.8.8 server. See the module README for the behaviour and the configuration.
 */
public final class RagoCombatEngine extends JavaPlugin {

    private volatile EngineSettings settings;
    private HitRecorder recorder;
    private final Set<UUID> debugViewers = new HashSet<>();
    private long tick;

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

        getServer().getScheduler().runTaskTimer(this, () -> tick++, 0L, 1L);
        getServer().getPluginManager().registerEvents(new KnockbackListener(this), this);
        getServer().getPluginManager().registerEvents(new HitDelayListener(this), this);
        applyHitDelay();
        PluginCommand command = getCommand("kb");
        KbCommand executor = new KbCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getLogger().info("Mode " + settings.mode().configName() + ", default profile " + settings.defaultProfile()
                + ", profiles " + settings.profiles().keySet() + ", hit delay " + settings.hitDelayTicks() + " ticks");
    }

    @Override
    public void onDisable() {
        if (recorder != null) {
            recorder.close();
            recorder = null;
        }
        debugViewers.clear();
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
                + " | paquet " + ChatColor.WHITE + record.packet;
        for (UUID viewer : debugViewers) {
            Player player = getServer().getPlayer(viewer);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }
}
