package gg.rago.combat.engine;

import gg.rago.combat.model.KnockbackProfile;
import gg.rago.combat.model.MeasuredProfiles;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * An immutable snapshot of the engine's settings. Commands change settings by building a new
 * snapshot, so a hit is always computed with one consistent set of values.
 */
final class EngineSettings {

    /** Vanilla 1.8.8: a new hit counts 10 ticks after the previous one (maxNoDamageTicks 20). */
    static final int VANILLA_HIT_DELAY_TICKS = 10;
    static final int MIN_HIT_DELAY_TICKS = 1;
    static final int MAX_HIT_DELAY_TICKS = 20;
    static final String KEY_HIT_DELAY_TICKS = "hit-delay-ticks";
    /** Off by default: the knockback uses the victim's motion as the server has it now. */
    static final int MIN_PING_SIMULATION_MS = 0;
    static final int MAX_PING_SIMULATION_MS = 500;
    static final String KEY_PING_SIMULATION_MS = "ping-simulation-ms";

    private final EngineMode mode;
    private final String defaultProfile;
    private final Map<String, KnockbackProfile> profiles;
    private final Map<String, String> worldProfiles;
    private final boolean recordHits;
    private final int hitDelayTicks;
    private final int pingSimulationMs;

    private EngineSettings(EngineMode mode, String defaultProfile, Map<String, KnockbackProfile> profiles,
            Map<String, String> worldProfiles, boolean recordHits, int hitDelayTicks, int pingSimulationMs) {
        this.mode = mode;
        this.defaultProfile = defaultProfile;
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
        this.worldProfiles = Collections.unmodifiableMap(new LinkedHashMap<>(worldProfiles));
        this.recordHits = recordHits;
        this.hitDelayTicks = requireHitDelay(hitDelayTicks);
        this.pingSimulationMs = requirePingSimulation(pingSimulationMs);
    }

    /**
     * Reads config.yml. The built-in profiles ({@code vanilla} and the measured profiles of
     * {@link MeasuredProfiles}) are always available and cannot be redefined.
     *
     * @throws IllegalArgumentException with a message for the server owner if anything is invalid
     */
    static EngineSettings load(ConfigurationSection config) {
        EngineMode mode = EngineMode.parse(config.getString("mode", EngineMode.OVERRIDE.configName()));

        Map<String, KnockbackProfile> profiles = new LinkedHashMap<>();
        profiles.put(KnockbackProfile.VANILLA_NAME, KnockbackProfile.vanilla());
        for (KnockbackProfile measured : MeasuredProfiles.all()) {
            profiles.put(measured.name(), measured);
        }
        ConfigurationSection profilesSection = config.getConfigurationSection("profiles");
        if (profilesSection != null) {
            for (String name : profilesSection.getKeys(false)) {
                if (profiles.containsKey(name)) {
                    throw new IllegalArgumentException("the '" + name + "' profile is built in and cannot be redefined");
                }
                ConfigurationSection values = profilesSection.getConfigurationSection(name);
                if (values == null) {
                    throw new IllegalArgumentException("profile '" + name + "' must be a section with "
                            + KnockbackProfile.KEYS);
                }
                profiles.put(name, KnockbackProfile.fromMap(name, values.getValues(false)));
            }
        }

        String defaultProfile = config.getString("active-profile", KnockbackProfile.VANILLA_NAME);
        requireProfile(profiles, defaultProfile, "active-profile");

        Map<String, String> worldProfiles = new LinkedHashMap<>();
        ConfigurationSection worldsSection = config.getConfigurationSection("world-profiles");
        if (worldsSection != null) {
            for (String world : worldsSection.getKeys(false)) {
                String profile = worldsSection.getString(world);
                requireProfile(profiles, profile, "world-profiles." + world);
                worldProfiles.put(world, profile);
            }
        }

        return new EngineSettings(mode, defaultProfile, profiles, worldProfiles, config.getBoolean("record-hits", false),
                readHitDelay(config), readPingSimulation(config));
    }

    private static int readHitDelay(ConfigurationSection config) {
        Object value = config.get(KEY_HIT_DELAY_TICKS, VANILLA_HIT_DELAY_TICKS);
        if (!(value instanceof Integer)) {
            throw new IllegalArgumentException(KEY_HIT_DELAY_TICKS + " must be a whole number of ticks, got " + value);
        }
        return requireHitDelay((Integer) value);
    }

    private static int requireHitDelay(int ticks) {
        if (ticks < MIN_HIT_DELAY_TICKS || ticks > MAX_HIT_DELAY_TICKS) {
            throw new IllegalArgumentException(KEY_HIT_DELAY_TICKS + " must be between " + MIN_HIT_DELAY_TICKS + " and "
                    + MAX_HIT_DELAY_TICKS + ", got " + ticks);
        }
        return ticks;
    }

    private static int readPingSimulation(ConfigurationSection config) {
        Object value = config.get(KEY_PING_SIMULATION_MS, MIN_PING_SIMULATION_MS);
        if (!(value instanceof Integer)) {
            throw new IllegalArgumentException(KEY_PING_SIMULATION_MS + " must be a whole number of milliseconds, got "
                    + value);
        }
        return requirePingSimulation((Integer) value);
    }

    private static int requirePingSimulation(int millis) {
        if (millis < MIN_PING_SIMULATION_MS || millis > MAX_PING_SIMULATION_MS) {
            throw new IllegalArgumentException(KEY_PING_SIMULATION_MS + " must be between " + MIN_PING_SIMULATION_MS
                    + " and " + MAX_PING_SIMULATION_MS + ", got " + millis);
        }
        return millis;
    }

    private static void requireProfile(Map<String, KnockbackProfile> profiles, String name, String where) {
        if (name == null || !profiles.containsKey(name)) {
            throw new IllegalArgumentException(where + ": unknown profile '" + name + "', known profiles: "
                    + profiles.keySet());
        }
    }

    EngineMode mode() {
        return mode;
    }

    String defaultProfile() {
        return defaultProfile;
    }

    Map<String, KnockbackProfile> profiles() {
        return profiles;
    }

    Map<String, String> worldProfiles() {
        return worldProfiles;
    }

    boolean recordHits() {
        return recordHits;
    }

    /** Ticks after a hit before the next one counts in full. */
    int hitDelayTicks() {
        return hitDelayTicks;
    }

    /**
     * The players' damage cooldown that gives this hit delay. A 1.8 hit counts in full once the
     * victim's cooldown counter has fallen to half its maximum or less, and the counter falls by one
     * per tick, so the delay is half the maximum.
     */
    int maximumNoDamageTicks() {
        return hitDelayTicks * 2;
    }

    /**
     * The ping, in milliseconds, that the knockback is computed for: a victim with less ping is hit
     * as if the server still had its motion of a few ticks before. 0 turns the simulation off.
     */
    int pingSimulationMs() {
        return pingSimulationMs;
    }

    /**
     * How many ticks back to take the victim's motion from, for a victim whose real ping is
     * {@code realPingMs}: the missing ping, rounded to whole ticks of 50 ms, or 0.
     */
    int simulatedDelayTicks(int realPingMs) {
        if (pingSimulationMs == 0 || realPingMs >= pingSimulationMs) {
            return 0;
        }
        return Math.round((pingSimulationMs - Math.max(0, realPingMs)) / 50.0F);
    }

    /** The profile that applies to hits in this world. */
    KnockbackProfile profileFor(String world) {
        String name = worldProfiles.get(world);
        return profiles.get(name != null ? name : defaultProfile);
    }

    EngineSettings withMode(EngineMode newMode) {
        return new EngineSettings(newMode, defaultProfile, profiles, worldProfiles, recordHits, hitDelayTicks,
                pingSimulationMs);
    }

    EngineSettings withDefaultProfile(String name) {
        requireProfile(profiles, name, "profile");
        return new EngineSettings(mode, name, profiles, worldProfiles, recordHits, hitDelayTicks, pingSimulationMs);
    }

    /** @param name a profile name, or {@code null} to make the world use the default profile */
    EngineSettings withWorldProfile(String world, String name) {
        Map<String, String> worlds = new LinkedHashMap<>(worldProfiles);
        if (name == null) {
            worlds.remove(world);
        } else {
            requireProfile(profiles, name, "world " + world);
            worlds.put(world, name);
        }
        return new EngineSettings(mode, defaultProfile, profiles, worlds, recordHits, hitDelayTicks, pingSimulationMs);
    }

    /** @throws IllegalArgumentException if the delay is out of range */
    EngineSettings withHitDelayTicks(int ticks) {
        return new EngineSettings(mode, defaultProfile, profiles, worldProfiles, recordHits, ticks, pingSimulationMs);
    }

    /** @throws IllegalArgumentException if the ping is out of range */
    EngineSettings withPingSimulationMs(int millis) {
        return new EngineSettings(mode, defaultProfile, profiles, worldProfiles, recordHits, hitDelayTicks, millis);
    }
}
