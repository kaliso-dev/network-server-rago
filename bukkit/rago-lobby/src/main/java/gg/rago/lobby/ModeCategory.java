package gg.rago.lobby;

import java.util.Collections;
import java.util.List;
import org.bukkit.Material;

/** A group of modes in the selector, matching a zone of the lobby. Immutable. */
final class ModeCategory {

    private final String id;
    private final String name;
    private final Material icon;
    private final List<String> description;

    ModeCategory(String id, String name, Material icon, List<String> description) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.description = Collections.unmodifiableList(description);
    }

    String id() {
        return id;
    }

    /** Coloured. */
    String name() {
        return name;
    }

    Material icon() {
        return icon;
    }

    /** Lines already coloured. */
    List<String> description() {
        return description;
    }
}
