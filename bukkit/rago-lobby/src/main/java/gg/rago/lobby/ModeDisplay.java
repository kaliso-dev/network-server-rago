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

    ModeDisplay(GameMode mode, Material icon, List<String> description, String category) {
        this(mode, icon, description, category, null);
    }

    ModeDisplay(GameMode mode, Material icon, List<String> description, String category, Kit kit) {
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
