package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.Collections;
import java.util.List;
import org.bukkit.Material;

/** A mode of the catalogue with how the selector shows it. Immutable. */
final class ModeDisplay {

    private final GameMode mode;
    private final Material icon;
    private final List<String> description;
    private final String category;
    private final Kit kit;
    private final String group;
    private final boolean hidden;
    private final boolean startHere;
    private final String kitFrom;
    private final String format;

    ModeDisplay(GameMode mode, Material icon, List<String> description, String category) {
        this(mode, icon, description, category, null);
    }

    ModeDisplay(GameMode mode, Material icon, List<String> description, String category, Kit kit) {
        this(mode, icon, description, category, kit, null, false);
    }

    ModeDisplay(GameMode mode, Material icon, List<String> description, String category, Kit kit, String group,
            boolean hidden) {
        this(mode, icon, description, category, kit, group, hidden, false);
    }

    ModeDisplay(GameMode mode, Material icon, List<String> description, String category, Kit kit, String group,
            boolean hidden, boolean startHere) {
        this(mode, icon, description, category, kit, group, hidden, startHere, null, null);
    }

    ModeDisplay(GameMode mode, Material icon, List<String> description, String category, Kit kit, String group,
            boolean hidden, boolean startHere, String kitFrom, String format) {
        this.kitFrom = kitFrom;
        this.format = format;
        this.startHere = startHere;
        this.group = group;
        this.hidden = hidden;
        this.mode = mode;
        this.icon = icon;
        this.description = Collections.unmodifiableList(description);
        this.category = category;
        this.kit = kit;
    }

    /** The items the mode gives, which players arrange in the kit editor; null when it gives none. */
    Kit kit() {
        return kit;
    }

    /**
     * The mode whose kit this one plays, with the layouts the players keep of it (kit-de:): one kit
     * for the sizes of one game, arranged once in the kit editor. Null: its own kit, if any.
     */
    String kitFrom() {
        return kitFrom;
    }

    /** How the menus show its size: its own format: ("1v3"), else the mode's ("2v2", "solo"). */
    String format() {
        return format != null ? format : mode.format();
    }

    /**
     * True for a mode not in the selector (cache: true): played only through a private game or an
     * event, or holding the kit of other modes (kit-de:).
     */
    boolean hidden() {
        return hidden;
    }

    /** True for the first mode of a new player (commencer-ici: true): "Start here" in the menus. */
    boolean startHere() {
        return startHere;
    }

    /** The id of the mode's group (the sizes of one game, one item of its category), or null. */
    String group() {
        return group;
    }

    /** The id of the mode's category, or null when the selector has no categories. */
    String category() {
        return category;
    }

    GameMode mode() {
        return mode;
    }

    Material icon() {
        return icon;
    }

    /** Lines already coloured (the {@code &} codes are translated). */
    List<String> description() {
        return description;
    }
}
