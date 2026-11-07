package gg.rago.combat.engine;

import java.util.Locale;

/** What the engine does with the velocity it computes for a melee hit. */
public enum EngineMode {

    /** The computed velocity replaces the server's own knockback. */
    OVERRIDE,

    /** The engine computes and records, but the server's own knockback is sent unchanged. */
    OBSERVE;

    /** Name used in the configuration and in commands. */
    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @throws IllegalArgumentException if the text is not a mode
     */
    public static EngineMode parse(String text) {
        if (text != null) {
            for (EngineMode mode : values()) {
                if (mode.configName().equals(text.trim().toLowerCase(Locale.ROOT))) {
                    return mode;
                }
            }
        }
        throw new IllegalArgumentException("unknown mode '" + text + "', expected override or observe");
    }
}
