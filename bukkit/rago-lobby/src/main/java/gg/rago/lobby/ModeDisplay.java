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

    ModeDisplay(GameMode mode, Material icon, List<String> description) {
        this.mode = mode;
        this.icon = icon;
        this.description = Collections.unmodifiableList(description);
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
