package gg.rago.lobby;

import gg.rago.game.GameMode;
import gg.rago.game.ModeCatalog;
import gg.rago.game.RatingWindow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The lobby's config.yml, read and checked once. Immutable. An invalid file throws
 * {@link IllegalArgumentException} with a message for the server owner.
 */
final class LobbySettings {

    private final String world;
    private final int voidHeight;
    private final String title;
    private final String address;
    private final int defaultRating;
    private final RatingWindow window;
    /** What the selector shows without categories: five rows of seven, in a six-row chest. */
    static final int MAX_MODES = 35;
    /** What a category's menu shows: four rows of seven, the last row keeping the back button. */
    static final int MAX_MODES_PER_CATEGORY = 28;
    /** One row of the category menu. */
    static final int MAX_CATEGORIES = 7;
    /** Leaderboards side by side on one point. */
    static final int MAX_BOARDS = 5;
    /** A rank and a space fit in the 16 characters of a 1.8 team prefix. */
    static final int MAX_RANK = 15;
    static final int MAX_XP = 1_000_000;

    private final List<ModeDisplay> modes;
    private final List<ModeCategory> categories;
    private final List<LobbyNpc> npcs;
    private final ModeCatalog catalog;
    private final NetworkLevel levels;

    private LobbySettings(String world, int voidHeight, String title, String address, int defaultRating,
            RatingWindow window, List<ModeCategory> categories, List<ModeDisplay> modes, List<LobbyNpc> npcs,
            NetworkLevel levels) {
        this.levels = levels;
        this.categories = Collections.unmodifiableList(categories);
        this.npcs = Collections.unmodifiableList(npcs);
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
            throw new IllegalArgumentException("monde: \"" + world + "\" is not a valid world name");
        }
        int voidHeight = config.getInt("hauteur-vide", 0);
        String title = colour(config.getString("titre", "&b&lRAGO PRACTICE"));
        if (ChatColor.stripColor(title).isEmpty() || title.length() > 32) {
            throw new IllegalArgumentException("titre: from 1 to 32 characters, colour codes included");
        }
        String address = colour(config.getString("adresse", "&7rago.gg"));
        if (address.length() > 40) {
            throw new IllegalArgumentException("adresse: 40 characters at most, colour codes included");
        }

        ConfigurationSection queue = config.getConfigurationSection("file");
        int defaultRating = queue == null ? 1000 : queue.getInt("elo-par-defaut", 1000);
        RatingWindow window;
        try {
            window = queue == null ? RatingWindow.DEFAULT : new RatingWindow(
                    queue.getInt("ecart-elo-initial", 100), queue.getInt("ecart-elo-pas", 50),
                    queue.getInt("ecart-elo-secondes", 10), queue.getInt("ecart-elo-max", 500));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("file: invalid ELO range (" + e.getMessage() + ")", e);
        }

        ConfigurationSection section = config.getConfigurationSection("modes");
        if (section == null || section.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("modes: the list of modes is empty");
        }
        List<ModeCategory> categories = readCategories(config.getConfigurationSection("categories"));
        if (categories.isEmpty() && section.getKeys(false).size() > MAX_MODES) {
            throw new IllegalArgumentException("modes: " + MAX_MODES + " modes at most without categories, the selector"
                    + " shows no more");
        }
        Map<String, Integer> perCategory = new HashMap<>();
        List<ModeDisplay> modes = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection mode = section.getConfigurationSection(id);
            if (mode == null) {
                throw new IllegalArgumentException("modes." + id + ": not a section");
            }
            ModeDisplay display = readMode(id, mode, categories);
            if (display.category() != null) {
                int count = perCategory.merge(display.category(), 1, Integer::sum);
                if (count > MAX_MODES_PER_CATEGORY) {
                    throw new IllegalArgumentException("modes." + id + ".categorie: " + MAX_MODES_PER_CATEGORY
                            + " modes at most in a category");
                }
            }
            modes.add(display);
        }
        List<LobbyNpc> npcs = readNpcs(config, modes, categories);
        NetworkLevel levels = readLevels(config.getConfigurationSection("niveau"));
        try {
            return new LobbySettings(world, voidHeight, title, address, defaultRating, window, categories, modes, npcs,
                    levels);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("modes: " + e.getMessage(), e);
        }
    }

    private static List<LobbyNpc> readNpcs(ConfigurationSection config, List<ModeDisplay> modes,
            List<ModeCategory> categories) {
        List<LobbyNpc> npcs = new ArrayList<>();
        java.util.Set<String> points = new java.util.HashSet<>();
        ConfigurationSection section = config.getConfigurationSection("pnj");
        if (section != null) {
            for (String point : section.getKeys(false)) {
                String where = "pnj." + point;
                ConfigurationSection npc = section.getConfigurationSection(point);
                if (npc == null) {
                    throw new IllegalArgumentException(where + ": not a section");
                }
                if (!LobbyPoints.validName(point)) {
                    throw new IllegalArgumentException(where + ": not a valid point name");
                }
                points.add(point);
                String type = npc.getString("type", "modes");
                if ("stats".equals(type)) {
                    if (npc.contains("modes") || npc.contains("categorie")) {
                        throw new IllegalArgumentException(where + ": a stats NPC has neither modes nor categorie");
                    }
                    npcs.add(LobbyNpc.stats(point, colour(npc.getString("nom", "&6&lYour profile"))));
                    continue;
                }
                if (!"modes".equals(type)) {
                    throw new IllegalArgumentException(where + ".type: \"" + type + "\" is not modes or stats");
                }
                String category = npc.getString("categorie");
                List<String> ids = new ArrayList<>(npc.getStringList("modes"));
                if (category != null) {
                    boolean known = false;
                    for (ModeCategory c : categories) {
                        known |= c.id().equals(category);
                    }
                    if (!known || !ids.isEmpty()) {
                        throw new IllegalArgumentException(where + ".categorie: \"" + category
                                + "\" is not one of the categories, or the NPC has modes too");
                    }
                    for (ModeDisplay display : modes) {
                        if (category.equals(display.category())) {
                            ids.add(display.mode().id());
                        }
                    }
                } else if (ids.isEmpty()) {
                    throw new IllegalArgumentException(where + ".modes: at least one mode, or a categorie");
                }
                for (String id : ids) {
                    boolean known = false;
                    for (ModeDisplay display : modes) {
                        known |= display.mode().id().equals(id);
                    }
                    if (!known) {
                        throw new IllegalArgumentException(where + ".modes: \"" + id + "\" is not one of the modes");
                    }
                }
                npcs.add(LobbyNpc.modes(point, colour(npc.getString("nom", point)), ids, category));
            }
        }
        ConfigurationSection boards = config.getConfigurationSection("classements");
        if (boards != null) {
            for (String point : boards.getKeys(false)) {
                String where = "classements." + point;
                if (!LobbyPoints.validName(point) || !points.add(point)) {
                    throw new IllegalArgumentException(where + ": invalid point name, or a point an NPC already has");
                }
                List<List<BoardStat>> stats = new ArrayList<>();
                String name;
                ConfigurationSection board = boards.getConfigurationSection(point);
                if (board == null) {
                    // The short form: only the title, over a board of wins.
                    name = boards.getString(point, point);
                    stats.add(Collections.singletonList(BoardStat.WINS));
                } else {
                    name = board.getString("nom", "");
                    List<?> entries = board.getList("tableaux");
                    for (Object entry : entries == null ? Collections.emptyList() : entries) {
                        // A list in the list: one board whose right click goes from one stat to the next.
                        List<?> keys = entry instanceof List ? (List<?>) entry : Collections.singletonList(entry);
                        List<BoardStat> variables = new ArrayList<>();
                        for (Object key : keys) {
                            BoardStat stat = BoardStat.of(String.valueOf(key));
                            if (stat == null) {
                                throw new IllegalArgumentException(where + ".tableaux: \"" + key + "\" is not "
                                        + BoardStat.keys());
                            }
                            if (!variables.contains(stat)) {
                                variables.add(stat);
                            }
                        }
                        if (variables.isEmpty()) {
                            throw new IllegalArgumentException(where + ".tableaux: an empty board");
                        }
                        stats.add(variables);
                    }
                    if (stats.isEmpty() || stats.size() > MAX_BOARDS) {
                        throw new IllegalArgumentException(where + ".tableaux: from 1 to " + MAX_BOARDS + " boards");
                    }
                }
                npcs.add(LobbyNpc.leaderboards(point, colour(name), stats));
            }
        }
        return npcs;
    }

    /** niveau: the XP of a game and of a kill, the XP of each level, and the ranks. */
    private static NetworkLevel readLevels(ConfigurationSection section) {
        if (section == null) {
            return NetworkLevel.DEFAULT;
        }
        int win = xp(section, "xp-victoire", NetworkLevel.DEFAULT.winXp(), 0);
        int loss = xp(section, "xp-defaite", NetworkLevel.DEFAULT.lossXp(), 0);
        int kill = xp(section, "xp-kill", NetworkLevel.DEFAULT.killXp(), 0);
        int first = xp(section, "xp-niveau-2", NetworkLevel.DEFAULT.xpToNext(1), 1);
        int extra = xp(section, "xp-en-plus-par-niveau", NetworkLevel.DEFAULT.xpToNext(2) - NetworkLevel.DEFAULT.xpToNext(1), 0);
        Map<Integer, String> ranks = new HashMap<>();
        ConfigurationSection list = section.getConfigurationSection("rangs");
        if (list == null && !section.contains("rangs")) {
            ranks.putAll(NetworkLevel.DEFAULT.ranks());
        } else if (list != null) {
            for (String key : list.getKeys(false)) {
                int level;
                try {
                    level = Integer.parseInt(key.trim());
                } catch (NumberFormatException e) {
                    level = 0;
                }
                if (level < 2 || level > NetworkLevel.MAX_LEVEL) {
                    throw new IllegalArgumentException("niveau.rangs." + key + ": the key is the level of the rank, from 2 to "
                            + NetworkLevel.MAX_LEVEL);
                }
                String rank = colour(list.getString(key, ""));
                if (ChatColor.stripColor(rank).trim().isEmpty() || rank.length() > MAX_RANK) {
                    throw new IllegalArgumentException("niveau.rangs." + key + ": from 1 to " + MAX_RANK
                            + " characters, colour codes included");
                }
                ranks.put(level, rank);
            }
        }
        return new NetworkLevel(win, loss, kill, first, extra, ranks);
    }

    private static int xp(ConfigurationSection section, String key, int fallback, int min) {
        int value = section.getInt(key, fallback);
        if (value < min || value > MAX_XP) {
            throw new IllegalArgumentException("niveau." + key + ": from " + min + " to " + MAX_XP);
        }
        return value;
    }

    private static List<ModeCategory> readCategories(ConfigurationSection section) {
        List<ModeCategory> categories = new ArrayList<>();
        if (section == null) {
            return categories;
        }
        if (section.getKeys(false).size() > MAX_CATEGORIES) {
            throw new IllegalArgumentException("categories: " + MAX_CATEGORIES + " categories at most");
        }
        for (String id : section.getKeys(false)) {
            String where = "categories." + id;
            ConfigurationSection category = section.getConfigurationSection(id);
            if (category == null) {
                throw new IllegalArgumentException(where + ": not a section");
            }
            if (!id.matches("[a-z0-9][a-z0-9-]{0,31}")) {
                throw new IllegalArgumentException(where + ": lowercase letters without accents, digits and dashes only");
            }
            categories.add(new ModeCategory(id, colour(category.getString("nom", id)),
                    icon(where, category.getString("icone", "CHEST")), description(category)));
        }
        return categories;
    }

    private static Material icon(String where, String iconName) {
        Material icon = Material.matchMaterial(iconName);
        if (icon == null || icon == Material.AIR) {
            throw new IllegalArgumentException(where + ".icone: \"" + iconName + "\" is not a Minecraft 1.8 item");
        }
        return icon;
    }

    private static List<String> description(ConfigurationSection section) {
        List<String> description = new ArrayList<>();
        for (String line : section.getStringList("description")) {
            description.add(colour("&7" + line));
        }
        return description;
    }

    private static ModeDisplay readMode(String id, ConfigurationSection section, List<ModeCategory> categories) {
        String where = "modes." + id;
        String name = colour(section.getString("nom", id));
        GameMode mode;
        try {
            mode = new GameMode(id, name, section.getInt("equipes", 2), section.getInt("joueurs-par-equipe", 1),
                    section.getBoolean("classe", false));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + ": " + e.getMessage(), e);
        }
        Material icon = icon(where, section.getString("icone", "PAPER"));
        String category = null;
        if (!categories.isEmpty()) {
            category = section.getString("categorie");
            boolean known = false;
            for (ModeCategory c : categories) {
                known |= c.id().equals(category);
            }
            if (!known) {
                throw new IllegalArgumentException(where + ".categorie: \"" + category
                        + "\" is not one of the categories (categories: at the top of the file)");
            }
        }
        return new ModeDisplay(mode, icon, description(section), category, readKit(where + ".kit", section));
    }

    /**
     * kit: the items of the mode, by slot (0 to 8 the hotbar, 9 to 35 the inventory). Either a list,
     * whose first item goes in slot 0 ("" or AIR for an empty slot), or slot: item lines. Null when
     * the mode gives no items.
     */
    private static Kit readKit(String where, ConfigurationSection section) {
        if (!section.contains("kit")) {
            return null;
        }
        List<KitItem> items = new ArrayList<>();
        ConfigurationSection bySlot = section.getConfigurationSection("kit");
        if (bySlot != null) {
            for (int i = 0; i < Kit.SLOTS; i++) {
                items.add(null);
            }
            for (String key : bySlot.getKeys(false)) {
                int slot;
                try {
                    slot = Integer.parseInt(key.trim());
                } catch (NumberFormatException e) {
                    slot = -1;
                }
                if (slot < 0 || slot >= Kit.SLOTS) {
                    throw new IllegalArgumentException(where + "." + key + ": the key is a slot, from 0 to "
                            + (Kit.SLOTS - 1) + " (0 to 8: the hotbar)");
                }
                items.set(slot, kitItem(where + "." + key, bySlot.getString(key)));
            }
        } else {
            List<?> list = section.getList("kit");
            if (list == null) {
                throw new IllegalArgumentException(where + ": a list of items, or slot: item lines");
            }
            if (list.size() > Kit.SLOTS) {
                throw new IllegalArgumentException(where + ": " + Kit.SLOTS + " items at most, the slots of an inventory");
            }
            for (int i = 0; i < list.size(); i++) {
                Object entry = list.get(i);
                items.add(kitItem(where + "[" + i + "]", entry == null ? null : String.valueOf(entry)));
            }
        }
        Kit kit = new Kit(items);
        return kit.isEmpty() ? null : kit;
    }

    private static KitItem kitItem(String where, String text) {
        try {
            return KitItem.parse(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + ": " + e.getMessage(), e);
        }
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

    /** The XP, the levels and the ranks of the Network level. */
    NetworkLevel levels() {
        return levels;
    }

    /** The modes in the order of config.yml, which is the order of the selector. */
    List<ModeDisplay> modes() {
        return modes;
    }

    /** The categories in the order of config.yml; empty when the selector shows every mode at once. */
    List<ModeCategory> categories() {
        return categories;
    }

    /** The NPCs and leaderboards of the lobby, by point. */
    List<LobbyNpc> npcs() {
        return npcs;
    }

    LobbyNpc npc(String point) {
        for (LobbyNpc npc : npcs) {
            if (npc.point().equals(point)) {
                return npc;
            }
        }
        return null;
    }

    ModeCategory category(String id) {
        for (ModeCategory category : categories) {
            if (category.id().equals(id)) {
                return category;
            }
        }
        return null;
    }

    /** The modes of one category (all of them for null), in the order of config.yml. */
    List<ModeDisplay> modesIn(String categoryId) {
        if (categoryId == null) {
            return modes;
        }
        List<ModeDisplay> result = new ArrayList<>();
        for (ModeDisplay display : modes) {
            if (categoryId.equals(display.category())) {
                result.add(display);
            }
        }
        return result;
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
