package gg.rago.lobby;

import gg.rago.game.GameMode;
import gg.rago.game.ModeCatalog;
import gg.rago.game.RatingWindow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The lobby's config.yml, read and checked once. Immutable. An invalid file throws
 * {@link IllegalArgumentException} with a message for the server owner, in French.
 */
final class LobbySettings {

    private final String world;
    private final int voidHeight;
    private final String title;
    private final String address;
    private final int defaultRating;
    private final RatingWindow window;
    /** What the selector shows: five rows of seven, in a six-row chest. */
    static final int MAX_MODES = 35;

    private final List<ModeDisplay> modes;
    private final ModeCatalog catalog;

    private LobbySettings(String world, int voidHeight, String title, String address, int defaultRating,
            RatingWindow window, List<ModeDisplay> modes) {
        this.world = world;
        this.voidHeight = voidHeight;
        this.title = title;
        this.address = address;
        this.defaultRating = defaultRating;
        this.window = window;
        this.modes = Collections.unmodifiableList(modes);
        List<GameMode> list = new ArrayList<>();
        for (ModeDisplay display : modes) {
            list.add(display.mode());
        }
        this.catalog = new ModeCatalog(list);
    }

    static LobbySettings load(ConfigurationSection config) {
        String world = config.getString("monde", "lobby");
        if (!world.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("monde : « " + world + " » n'est pas un nom de monde valide");
        }
        int voidHeight = config.getInt("hauteur-vide", 0);
        String title = colour(config.getString("titre", "&b&lRAGO PRACTICE"));
        if (ChatColor.stripColor(title).isEmpty() || title.length() > 32) {
            throw new IllegalArgumentException("titre : entre 1 et 32 caractères, codes de couleur compris");
        }
        String address = colour(config.getString("adresse", "&7rago.gg"));
        if (address.length() > 40) {
            throw new IllegalArgumentException("adresse : 40 caractères au plus, codes de couleur compris");
        }

        ConfigurationSection queue = config.getConfigurationSection("file");
        int defaultRating = queue == null ? 1000 : queue.getInt("elo-par-defaut", 1000);
        RatingWindow window;
        try {
            window = queue == null ? RatingWindow.DEFAULT : new RatingWindow(
                    queue.getInt("ecart-elo-initial", 100), queue.getInt("ecart-elo-pas", 50),
                    queue.getInt("ecart-elo-secondes", 10), queue.getInt("ecart-elo-max", 500));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("file : écart d'ELO invalide (" + e.getMessage() + ")", e);
        }

        ConfigurationSection section = config.getConfigurationSection("modes");
        if (section == null || section.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("modes : la liste des modes est vide");
        }
        if (section.getKeys(false).size() > MAX_MODES) {
            throw new IllegalArgumentException("modes : " + MAX_MODES + " modes au plus, le sélecteur n'en affiche pas davantage");
        }
        List<ModeDisplay> modes = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection mode = section.getConfigurationSection(id);
            if (mode == null) {
                throw new IllegalArgumentException("modes." + id + " : ce n'est pas une section");
            }
            modes.add(readMode(id, mode));
        }
        try {
            return new LobbySettings(world, voidHeight, title, address, defaultRating, window, modes);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("modes : " + e.getMessage(), e);
        }
    }

    private static ModeDisplay readMode(String id, ConfigurationSection section) {
        String where = "modes." + id;
        String name = colour(section.getString("nom", id));
        GameMode mode;
        try {
            mode = new GameMode(id, name, section.getInt("equipes", 2), section.getInt("joueurs-par-equipe", 1),
                    section.getBoolean("classe", false));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + " : " + e.getMessage(), e);
        }
        String iconName = section.getString("icone", "PAPER");
        Material icon = Material.matchMaterial(iconName);
        if (icon == null || icon == Material.AIR) {
            throw new IllegalArgumentException(where + ".icone : « " + iconName + " » n'est pas un objet de Minecraft 1.8");
        }
        List<String> description = new ArrayList<>();
        for (String line : section.getStringList("description")) {
            description.add(colour("&7" + line));
        }
        return new ModeDisplay(mode, icon, description);
    }

    static String colour(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /** Name of the lobby world. */
    String world() {
        return world;
    }

    /** Below this height, a player in the lobby goes back to the spawn. */
    int voidHeight() {
        return voidHeight;
    }

    /** Scoreboard title, coloured. */
    String title() {
        return title;
    }

    /** Last line of the scoreboard, coloured. */
    String address() {
        return address;
    }

    /** Rating of every player until the database and rago-ranked exist. */
    int defaultRating() {
        return defaultRating;
    }

    RatingWindow window() {
        return window;
    }

    /** The modes in the order of config.yml, which is the order of the selector. */
    List<ModeDisplay> modes() {
        return modes;
    }

    ModeCatalog catalog() {
        return catalog;
    }

    ModeDisplay display(String modeId) {
        for (ModeDisplay display : modes) {
            if (display.mode().id().equals(modeId)) {
                return display;
            }
        }
        return null;
    }
}
