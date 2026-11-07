package gg.rago.combat.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The parameters of a melee knockback formula with the same structure as Minecraft 1.8.
 *
 * <p>The six parameters are those of the 1.8.8 server code:
 * <ul>
 *   <li>{@code friction}: the victim's current server-side motion is divided by this value before
 *       knockback is added (vanilla 2.0);</li>
 *   <li>{@code horizontal}: horizontal push away from the attacker (vanilla {@code 0.4F});</li>
 *   <li>{@code vertical}: vertical push (vanilla {@code 0.4F});</li>
 *   <li>{@code verticalLimit}: cap on the vertical motion after that push (vanilla
 *       {@code 0.4000000059604645});</li>
 *   <li>{@code extraHorizontal}: extra push per knockback level, in the direction the attacker
 *       looks; a sprinting attacker adds one level, each Knockback enchantment level another
 *       (vanilla {@code 0.5F});</li>
 *   <li>{@code extraVertical}: extra vertical push when the level is above zero (vanilla 0.1).</li>
 * </ul>
 *
 * <p>{@code extraHorizontal} is stored as a {@code float} because the server multiplies it in
 * {@code float} arithmetic; this keeps the {@link #vanilla()} profile exact.
 *
 * <p>Instances are immutable. Hypixel's values are not public: a profile meant to approach them
 * must come from measurements (docs/combat/knockback-1.8.8.md), never from guesses.
 */
public final class KnockbackProfile {

    /** Name reserved for the built-in vanilla profile. */
    public static final String VANILLA_NAME = "vanilla";

    public static final String KEY_FRICTION = "friction";
    public static final String KEY_HORIZONTAL = "horizontal";
    public static final String KEY_VERTICAL = "vertical";
    public static final String KEY_VERTICAL_LIMIT = "vertical-limit";
    public static final String KEY_EXTRA_HORIZONTAL = "extra-horizontal";
    public static final String KEY_EXTRA_VERTICAL = "extra-vertical";

    /** The configuration keys of a profile, in the order they are documented. */
    public static final Set<String> KEYS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            KEY_FRICTION, KEY_HORIZONTAL, KEY_VERTICAL, KEY_VERTICAL_LIMIT,
            KEY_EXTRA_HORIZONTAL, KEY_EXTRA_VERTICAL)));

    /**
     * Largest velocity a 1.8 velocity packet can carry, in blocks per tick. Larger parameters
     * could only produce clamped packets, so they are refused.
     */
    public static final double MAX_COMPONENT = VelocityPacket.MAX_COMPONENT;

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

    private static final KnockbackProfile VANILLA = new KnockbackProfile(
            VANILLA_NAME,
            2.0D,
            (double) 0.4F,
            (double) 0.4F,
            0.4000000059604645D,
            0.5F,
            0.1D);

    private final String name;
    private final double friction;
    private final double horizontal;
    private final double vertical;
    private final double verticalLimit;
    private final float extraHorizontal;
    private final double extraVertical;

    private KnockbackProfile(String name, double friction, double horizontal, double vertical,
            double verticalLimit, float extraHorizontal, double extraVertical) {
        this.name = name;
        this.friction = friction;
        this.horizontal = horizontal;
        this.vertical = vertical;
        this.verticalLimit = verticalLimit;
        this.extraHorizontal = extraHorizontal;
        this.extraVertical = extraVertical;
    }

    /** The exact Minecraft 1.8.8 melee knockback. */
    public static KnockbackProfile vanilla() {
        return VANILLA;
    }

    /**
     * Creates a profile after validating every value.
     *
     * @throws IllegalArgumentException if the name or a value is invalid
     */
    public static KnockbackProfile of(String name, double friction, double horizontal, double vertical,
            double verticalLimit, double extraHorizontal, double extraVertical) {
        validateName(name);
        if (!Double.isFinite(friction) || friction <= 0.0D || friction > 100.0D) {
            throw new IllegalArgumentException(KEY_FRICTION + " must be above 0 and at most 100, got " + friction);
        }
        requireComponent(KEY_HORIZONTAL, horizontal);
        requireComponent(KEY_VERTICAL, vertical);
        requireComponent(KEY_VERTICAL_LIMIT, verticalLimit);
        requireComponent(KEY_EXTRA_HORIZONTAL, extraHorizontal);
        requireComponent(KEY_EXTRA_VERTICAL, extraVertical);
        return new KnockbackProfile(name, friction, horizontal, vertical, verticalLimit,
                (float) extraHorizontal, extraVertical);
    }

    /**
     * Reads a profile from configuration values, for example a YAML section. Every key of
     * {@link #KEYS} is required and no other key is accepted, so that a typo cannot silently fall
     * back to a default value.
     *
     * @throws IllegalArgumentException if a key is missing, unknown or has an invalid value
     */
    public static KnockbackProfile fromMap(String name, Map<String, ?> values) {
        Objects.requireNonNull(values, "values");
        for (String key : values.keySet()) {
            if (!KEYS.contains(key)) {
                throw new IllegalArgumentException("profile '" + name + "': unknown key '" + key
                        + "', expected " + KEYS);
            }
        }
        try {
            return of(name,
                    number(values, KEY_FRICTION),
                    number(values, KEY_HORIZONTAL),
                    number(values, KEY_VERTICAL),
                    number(values, KEY_VERTICAL_LIMIT),
                    number(values, KEY_EXTRA_HORIZONTAL),
                    number(values, KEY_EXTRA_VERTICAL));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("profile '" + name + "': " + e.getMessage(), e);
        }
    }

    private static double number(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing key '" + key + "'");
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("'" + key + "' must be a number, got '" + value + "'");
        }
        return ((Number) value).doubleValue();
    }

    private static void validateName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid profile name '" + name
                    + "': use 1 to 32 lowercase letters, digits, '-' or '_'");
        }
    }

    private static void requireComponent(String key, double value) {
        if (!Double.isFinite(value) || Math.abs(value) > MAX_COMPONENT) {
            throw new IllegalArgumentException(key + " must be between -" + MAX_COMPONENT + " and "
                    + MAX_COMPONENT + ", got " + value);
        }
    }

    public String name() {
        return name;
    }

    public double friction() {
        return friction;
    }

    public double horizontal() {
        return horizontal;
    }

    public double vertical() {
        return vertical;
    }

    public double verticalLimit() {
        return verticalLimit;
    }

    public float extraHorizontal() {
        return extraHorizontal;
    }

    public double extraVertical() {
        return extraVertical;
    }

    /** True if both profiles produce the same knockback, whatever their names. */
    public boolean sameValuesAs(KnockbackProfile other) {
        return Double.compare(friction, other.friction) == 0
                && Double.compare(horizontal, other.horizontal) == 0
                && Double.compare(vertical, other.vertical) == 0
                && Double.compare(verticalLimit, other.verticalLimit) == 0
                && Float.compare(extraHorizontal, other.extraHorizontal) == 0
                && Double.compare(extraVertical, other.extraVertical) == 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof KnockbackProfile)) {
            return false;
        }
        KnockbackProfile profile = (KnockbackProfile) other;
        return name.equals(profile.name) && sameValuesAs(profile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, friction, horizontal, vertical, verticalLimit, extraHorizontal, extraVertical);
    }

    @Override
    public String toString() {
        return name + "{friction=" + friction
                + ", horizontal=" + horizontal
                + ", vertical=" + vertical
                + ", vertical-limit=" + verticalLimit
                + ", extra-horizontal=" + extraHorizontal
                + ", extra-vertical=" + extraVertical + '}';
    }
}
