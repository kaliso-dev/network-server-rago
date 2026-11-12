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
    /** Measured on Hypixel (docs/combat/mesures-hypixel.md): 9 ticks, the delay of a config without one. */
    static final int DEFAULT_HIT_DELAY_TICKS = 9;
    /** The profile of a config without active-profile: the knockback of Hypixel. */
    static final String DEFAULT_PROFILE = MeasuredProfiles.HYPIXEL_APPROX_V1_NAME;
    static final int MIN_HIT_DELAY_TICKS = 1;
    static final int MAX_HIT_DELAY_TICKS = 20;
    static final String KEY_HIT_DELAY_TICKS = "hit-delay-ticks";

    private final EngineMode mode;
    private final String defaultProfile;
    private final Map<String, KnockbackProfile> profiles;
    private final Map<String, String> worldProfiles;
    private final boolean recordHits;
    private final int hitDelayTicks;
    private final boolean locked;

    private EngineSettings(EngineMode mode, String defaultProfile, Map<String, KnockbackProfile> profiles,
            Map<String, String> worldProfiles, boolean recordHits, int hitDelayTicks, boolean locked) {
        this.mode = mode;
        this.defaultProfile = defaultProfile;
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
        this.worldProfiles = Collections.unmodifiableMap(new LinkedHashMap<>(worldProfiles));
        this.recordHits = recordHits;
        this.hitDelayTicks = requireHitDelay(hitDelayTicks);
        this.locked = locked;
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

        String defaultProfile = config.getString("active-profile", DEFAULT_PROFILE);
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
                readHitDelay(config), config.getBoolean("locked", true));
    }

    private static int readHitDelay(ConfigurationSection config) {
        Object value = config.get(KEY_HIT_DELAY_TICKS, DEFAULT_HIT_DELAY_TICKS);
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

    /** True when /kb may not change the mode, the profiles or the hit delay (config.yml only). */
    boolean locked() {
        return locked;
    }

    /**
     * The players' damage cooldown that gives this hit delay. A 1.8 hit counts in full once the
     * victim's cooldown counter has fallen to half its maximum or less, and the counter falls by one
     * per tick, so the delay is half the maximum.
     */
    int maximumNoDamageTicks() {
        return hitDelayTicks * 2;
    }

    /** The profile that applies to hits in this world. */
    KnockbackProfile profileFor(String world) {
        String name = worldProfiles.get(world);
        return profiles.get(name != null ? name : defaultProfile);
    }

    EngineSettings withMode(EngineMode newMode) {
        return new EngineSettings(newMode, defaultProfile, profiles, worldProfiles, recordHits, hitDelayTicks, locked);
    }

    EngineSettings withDefaultProfile(String name) {
        requireProfile(profiles, name, "profile");
        return new EngineSettings(mode, name, profiles, worldProfiles, recordHits, hitDelayTicks, locked);
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
        return new EngineSettings(mode, defaultProfile, profiles, worlds, recordHits, hitDelayTicks, locked);
    }

    /** @throws IllegalArgumentException if the delay is out of range */
    EngineSettings withHitDelayTicks(int ticks) {
        return new EngineSettings(mode, defaultProfile, profiles, worldProfiles, recordHits, ticks, locked);
    }
}
