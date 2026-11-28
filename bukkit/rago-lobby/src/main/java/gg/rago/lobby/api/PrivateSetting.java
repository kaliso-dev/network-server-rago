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
 *
 * <p>A setting of items to tick ({@link #toggles}, One Hit One Kill: which weapons kill in one hit)
 * has its items in {@link #choices}, each with its icon ({@link #icons}); its choice is then the
 * items ticked, one bit each (bit i for the item i), 0 for none (a normal game).
 */
public final class PrivateSetting {

    /** The most items a setting to tick has: one bit each in its choice. */
    public static final int MAX_TOGGLES = 30;

    public final String id;
    public final String name;
    public final Material icon;
    /** The choices as the menu shows them ("None", "Speed I"...): the first is that of a normal game. */
    public final List<String> choices;
    /** What it does, line by line. */
    public final List<String> description;
    /** True for items to tick, each on or off, in {@link #choices}; false for one choice of them. */
    public final boolean toggles;
    /** The icon of each item to tick, in the order of {@link #choices}; empty for one choice. */
    public final List<Material> icons;

    public PrivateSetting(String id, String name, Material icon, List<String> choices, List<String> description) {
        this(id, name, icon, choices, description, false, Collections.<Material>emptyList());
    }

    private PrivateSetting(String id, String name, Material icon, List<String> choices, List<String> description,
            boolean toggles, List<Material> icons) {
        if (choices.isEmpty()) {
            throw new IllegalArgumentException(id + ": no choice");
        }
        if (toggles && (choices.size() > MAX_TOGGLES || icons.size() != choices.size())) {
            throw new IllegalArgumentException(id + ": 1 to " + MAX_TOGGLES + " items, an icon each");
        }
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
        this.description = Collections.unmodifiableList(new ArrayList<>(description));
        this.toggles = toggles;
        this.icons = Collections.unmodifiableList(new ArrayList<>(icons));
    }

    /** A setting of items to tick, each on or off (bit i of its choice for the item i), none by default. */
    public static PrivateSetting toggles(String id, String name, Material icon, List<String> items, List<Material> icons,
            List<String> description) {
        return new PrivateSetting(id, name, icon, items, description, true, icons);
    }

    /** True if this choice is one of this setting: an index of {@link #choices}, or for items to tick, their bits. */
    public boolean valid(int choice) {
        return choice >= 0 && choice < (toggles ? 1 << choices.size() : choices.size());
    }

    /**
     * What this choice is, as the menu and the party are told: the choice itself ("Speed II"), or
     * for items to tick those ticked ("Wooden Sword, Bow"), "None" if none.
     */
    public String describe(int choice) {
        if (!valid(choice)) {
            choice = 0;
        }
        if (!toggles) {
            return choices.get(choice);
        }
        List<String> ticked = new ArrayList<>();
        for (int i = 0; i < choices.size(); i++) {
            if ((choice & 1 << i) != 0) {
                ticked.add(choices.get(i));
            }
        }
        return ticked.isEmpty() ? "None" : String.join(", ", ticked);
    }
}
