package gg.rago.lobby.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Material;

/**
 * A setting of a private game that the party leader chooses in the lobby before the game (Bedwars
 * Normal: Speed, Max Diamond...), as the game plugin offers it for a mode
 * ({@link MatchHost#privateSettings}): its id, its name and icon in the menu, what it does, and its
 * choices in order, the first that of a normal game. The game gets the choice of each with
 * {@link LobbyService#privateSettings}. Immutable.
 */
public final class PrivateSetting {

    public final String id;
    public final String name;
    public final Material icon;
    /** The choices as the menu shows them ("None", "Speed I"...): the first is that of a normal game. */
    public final List<String> choices;
    /** What it does, line by line. */
    public final List<String> description;

    public PrivateSetting(String id, String name, Material icon, List<String> choices, List<String> description) {
        if (choices.isEmpty()) {
            throw new IllegalArgumentException(id + ": no choice");
        }
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
        this.description = Collections.unmodifiableList(new ArrayList<>(description));
    }
}
