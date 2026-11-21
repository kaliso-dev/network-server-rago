package gg.rago.lobby;

import java.util.Collections;
import java.util.List;
import org.bukkit.Material;

/**
 * A category of the selector, matching a zone of the lobby; or a group of a category (groupes:
 * in config.yml), the sizes of one game shown as one item. A category may have a slogan: what a
 * player does there, in a few words ("Train alone"), shown under its name in the compass, under
 * its NPC and in the welcome of the lobby. Immutable.
 */
final class ModeCategory {

    private final String id;
    private final String name;
    private final Material icon;
    private final List<String> description;
    private final String slogan;
    /** The modes and groups of other categories it shows too (aussi: in config.yml), in that order. */
    private final List<String> also;

    ModeCategory(String id, String name, Material icon, List<String> description) {
        this(id, name, icon, description, null);
    }

    ModeCategory(String id, String name, Material icon, List<String> description, String slogan) {
        this(id, name, icon, description, slogan, Collections.<String>emptyList());
    }

    ModeCategory(String id, String name, Material icon, List<String> description, String slogan, List<String> also) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.description = Collections.unmodifiableList(description);
        this.slogan = slogan;
        this.also = Collections.unmodifiableList(new java.util.ArrayList<>(also));
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

    /** What a player does there, coloured, or null. */
    String slogan() {
        return slogan;
    }

    /**
     * The ids of the modes and groups of other categories that this one shows too, as the
     * category of the games for three players shows Red Rover of the Duels: the same modes and
     * queues, one more way to them. Empty for most.
     */
    List<String> also() {
        return also;
    }
}
