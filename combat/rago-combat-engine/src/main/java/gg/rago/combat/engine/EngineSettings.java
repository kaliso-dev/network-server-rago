package gg.rago.combat.engine;

import gg.rago.combat.model.KnockbackProfile;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * An immutable snapshot of the engine's settings. Commands change settings by building a new
 * snapshot, so a hit is always computed with one consistent set of values.
 */
final class EngineSettings {

    private final EngineMode mode;
    private final String defaultProfile;
    private final Map<String, KnockbackProfile> profiles;
    private final Map<String, String> worldProfiles;
    private final boolean recordHits;

    private EngineSettings(EngineMode mode, String defaultProfile, Map<String, KnockbackProfile> profiles,
            Map<String, String> worldProfiles, boolean recordHits) {
        this.mode = mode;
        this.defaultProfile = defaultProfile;
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
        this.worldProfiles = Collections.unmodifiableMap(new LinkedHashMap<>(worldProfiles));
        this.recordHits = recordHits;
    }

    /**
     * Reads config.yml. The built-in {@code vanilla} profile is always available and cannot be
     * redefined.
     *
     * @throws IllegalArgumentException with a message for the server owner if anything is invalid
     */
    static EngineSettings load(ConfigurationSection config) {
        EngineMode mode = EngineMode.parse(config.getString("mode", EngineMode.OVERRIDE.configName()));

        Map<String, KnockbackProfile> profiles = new LinkedHashMap<>();
        profiles.put(KnockbackProfile.VANILLA_NAME, KnockbackProfile.vanilla());
        ConfigurationSection profilesSection = config.getConfigurationSection("profiles");
        if (profilesSection != null) {
            for (String name : profilesSection.getKeys(false)) {
                if (KnockbackProfile.VANILLA_NAME.equals(name)) {
                    throw new IllegalArgumentException("the 'vanilla' profile is built in and cannot be redefined");
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

        return new EngineSettings(mode, defaultProfile, profiles, worldProfiles, config.getBoolean("record-hits", false));
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

    /** The profile that applies to hits in this world. */
    KnockbackProfile profileFor(String world) {
        String name = worldProfiles.get(world);
        return profiles.get(name != null ? name : defaultProfile);
    }

    EngineSettings withMode(EngineMode newMode) {
        return new EngineSettings(newMode, defaultProfile, profiles, worldProfiles, recordHits);
    }

    EngineSettings withDefaultProfile(String name) {
        requireProfile(profiles, name, "profile");
        return new EngineSettings(mode, name, profiles, worldProfiles, recordHits);
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
        return new EngineSettings(mode, defaultProfile, profiles, worlds, recordHits);
    }
}
