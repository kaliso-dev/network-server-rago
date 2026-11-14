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
    /** The throw of the slime blocks of the lobby: forward and up, in blocks per tick. */
    private double slimeForward = 3.5;
    private double slimeUp = 1.3;
    private String mineskinKey = "";
    /** The welcome of the lobby at each arrival ({@link LobbyWelcome}). */
    private boolean welcome = true;
    /** The menu of the first arrival ({@link FirstJoinMenu}): empty for none. */
    private List<FirstChoice> firstChoices = Collections.emptyList();
    /** The billboards of the lobby ({@link Billboards}): none without panneaux:. */
    private List<Billboard> billboards = Collections.emptyList();
    private final String title;
    private final String address;
    private final int defaultRating;
    private final RatingWindow window;
    /** What the selector shows without categories: five rows of seven, in a six-row chest. */
    static final int MAX_MODES = 35;
    /**
     * What a category's menu shows: four rows of seven, the last row keeping the back button. The
     * modes of a group are one item there.
     */
    static final int MAX_MODES_PER_CATEGORY = 28;
    /** One row of the category menu. */
    static final int MAX_CATEGORIES = 7;
    /** The slogan of a category, without its colours: one line above an NPC. */
    static final int MAX_SLOGAN = 40;
    /** Leaderboards side by side on one point. */
    static final int MAX_BOARDS = 5;
    /** Steps of the podium of a leaderboard wall. */
    static final int MAX_PODIUM = 5;
    static final int MAX_XP = 1_000_000;

    private final List<ModeDisplay> modes;
    private final List<ModeCategory> categories;
    private final List<ModeCategory> groups;
    private final List<LobbyNpc> npcs;
    private final ModeCatalog catalog;
    private final NetworkLevel levels;
    private final String hypixelKey;
    private final Map<String, List<GameMode>> privateRoutes;

    private LobbySettings(String world, int voidHeight, String title, String address, int defaultRating,
            RatingWindow window, List<ModeCategory> categories, List<ModeCategory> groups, List<ModeDisplay> modes,
            List<LobbyNpc> npcs, NetworkLevel levels, String hypixelKey, Map<String, List<GameMode>> privateRoutes) {
        this.groups = Collections.unmodifiableList(groups);
        this.levels = levels;
        this.hypixelKey = hypixelKey;
        this.privateRoutes = Collections.unmodifiableMap(privateRoutes);
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
        String title = colour(config.getString("titre", "&c&lRAGO PRACTICE"));
        if (ChatColor.stripColor(title).isEmpty() || title.length() > 32) {
            throw new IllegalArgumentException("titre: from 1 to 32 characters, colour codes included");
        }
        String address = colour(config.getString("adresse", "&crago.gg"));
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
        List<ModeCategory> groups = readGroups(config.getConfigurationSection("groupes"));
        Map<String, Integer> perCategory = new HashMap<>();
        Map<String, String> groupCategory = new HashMap<>();
        java.util.Set<String> groupsSeen = new java.util.HashSet<>();
        List<ModeDisplay> modes = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection mode = section.getConfigurationSection(id);
            if (mode == null) {
                throw new IllegalArgumentException("modes." + id + ": not a section");
            }
            ModeDisplay display = readMode(id, mode, categories, groups);
            if (display.group() != null) {
                String before = groupCategory.putIfAbsent(display.group(), String.valueOf(display.category()));
                if (before != null && !before.equals(String.valueOf(display.category()))) {
                    throw new IllegalArgumentException("modes." + id + ".groupe: the modes of the group \"" + display.group()
                            + "\" are all of one category");
                }
            }
            // one item in the category's menu: the mode, or its group the first time
            if (display.category() != null && (display.group() == null || groupsSeen.add(display.group()))) {
                int count = perCategory.merge(display.category(), 1, Integer::sum);
                if (count > MAX_MODES_PER_CATEGORY) {
                    throw new IllegalArgumentException("modes." + id + ".categorie: " + MAX_MODES_PER_CATEGORY
                            + " items at most in a category (the modes of a group are one)");
                }
            }
            modes.add(display);
        }
        checkKitFrom(modes);
        List<LobbyNpc> npcs = readNpcs(config, modes, categories);
        NetworkLevel levels = readLevels(config.getConfigurationSection("niveau"));
        String hypixelKey = config.getString("hypixel.cle-api", "").trim();
        if (!hypixelKey.isEmpty() && !hypixelKey.matches("[0-9a-fA-F-]{32,36}")) {
            throw new IllegalArgumentException("hypixel.cle-api: not a Hypixel API key (32 hexadecimal digits"
                    + " with dashes, from developer.hypixel.net), or \"\" for no import");
        }
        Map<String, List<GameMode>> privateRoutes = readPrivateRoutes(config.getConfigurationSection("parties-privees"), modes);
        String mineskinKey = config.getString("cle-mineskin", "").trim();
        if (!mineskinKey.isEmpty() && !mineskinKey.matches("[A-Za-z0-9_.-]{8,200}")) {
            throw new IllegalArgumentException("cle-mineskin: not a MineSkin API key (account.mineskin.org/keys),"
                    + " or \"\" to sign the skins of the NPCs without one");
        }
        double slimeForward = config.getDouble("slime.avant", 3.5);
        double slimeUp = config.getDouble("slime.haut", 1.3);
        if (slimeForward < 0 || slimeForward > 4 || slimeUp < 0 || slimeUp > 4) {
            throw new IllegalArgumentException("slime: avant and haut from 0 to 4");
        }
        List<FirstChoice> firstChoices = readFirstChoices(config.getConfigurationSection("premiere-venue"), modes, categories);
        List<Billboard> billboards = Billboard.read(config.getConfigurationSection("panneaux"));
        try {
            LobbySettings settings = new LobbySettings(world, voidHeight, title, address, defaultRating, window, categories,
                    groups, modes, npcs, levels, hypixelKey, privateRoutes);
            settings.slimeForward = slimeForward;
            settings.slimeUp = slimeUp;
            settings.mineskinKey = mineskinKey;
            settings.welcome = config.getBoolean("accueil", true);
            settings.firstChoices = Collections.unmodifiableList(firstChoices);
            settings.billboards = billboards;
            return settings;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("modes: " + e.getMessage(), e);
        }
    }

    /**
     * The private games whose size follows the party: for each such mode, the modes it plays, from
     * the smallest. Each is a mode of the list with two teams or more.
     */
    private static Map<String, List<GameMode>> readPrivateRoutes(ConfigurationSection section, List<ModeDisplay> modes) {
        Map<String, List<GameMode>> routes = new HashMap<>();
        if (section == null) {
            return routes;
        }
        Map<String, GameMode> byId = new HashMap<>();
        for (ModeDisplay display : modes) {
            byId.put(display.mode().id(), display.mode());
        }
        for (String id : section.getKeys(false)) {
            if (!byId.containsKey(id)) {
                throw new IllegalArgumentException("parties-privees." + id + ": no mode " + id + " in modes");
            }
            List<GameMode> targets = new ArrayList<>();
            for (String target : section.getStringList(id)) {
                GameMode mode = byId.get(target);
                if (mode == null) {
                    throw new IllegalArgumentException("parties-privees." + id + ": no mode " + target + " in modes");
                }
                if (mode.teams() < 2) {
                    throw new IllegalArgumentException("parties-privees." + id + ": " + target
                            + " has one team, a private game needs two at least");
                }
                if (target.equals(id)) {
                    throw new IllegalArgumentException("parties-privees." + id + ": a mode cannot play itself");
                }
                targets.add(mode);
            }
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("parties-privees." + id + ": a list of modes, as [ranked-1v1, ranked-2v2]");
            }
            targets.sort((a, b) -> Integer.compare(a.playersPerMatch(), b.playersPerMatch()));
            routes.put(id, targets);
        }
        return routes;
    }

    /** The private game modes whose size follows the party, with the modes each plays, smallest first. */
    Map<String, List<GameMode>> privateRoutes() {
        return privateRoutes;
    }

    /** The skin of an NPC (skin: id), or null for Steve. */
    private static String skin(ConfigurationSection npc, String where) {
        String id = npc.getString("skin", "").trim();
        if (id.isEmpty()) {
            return null;
        }
        if (!NpcSkins.validId(id)) {
            throw new IllegalArgumentException(where + ".skin: \"" + id + "\" is not a skin name (lowercase letters,"
                    + " digits and dashes: skins/<name>.png)");
        }
        return id;
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
                    npcs.add(LobbyNpc.stats(point, colour(npc.getString("nom", "&6&lYour profile"))).withSkin(skin(npc, where)));
                    continue;
                }
                if ("shop".equals(type)) {
                    if (npc.contains("modes") || npc.contains("categorie")) {
                        throw new IllegalArgumentException(where + ": the Shop NPC has neither modes nor categorie");
                    }
                    npcs.add(LobbyNpc.shop(point, colour(npc.getString("nom", "&a&lSHOP")),
                            colour(npc.getString("sous-titre", "&cStarter Pack &7· &bVIP"))).withSkin(skin(npc, where)));
                    continue;
                }
                if (!"modes".equals(type)) {
                    throw new IllegalArgumentException(where + ".type: \"" + type + "\" is not modes, stats or shop");
                }
                String category = npc.getString("categorie");
                List<String> ids = new ArrayList<>(npc.getStringList("modes"));
                // The line under its name: its own sous-titre, else the slogan of its category.
                String subtitle = npc.getString("sous-titre", null);
                if (category != null) {
                    boolean known = false;
                    for (ModeCategory c : categories) {
                        known |= c.id().equals(category);
                        if (c.id().equals(category) && subtitle == null) {
                            subtitle = c.slogan();
                        }
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
                npcs.add(LobbyNpc.modes(point, colour(npc.getString("nom", point)), ids, category)
                        .withSubtitle(subtitle == null || subtitle.trim().isEmpty() ? null : colour(subtitle)).withSkin(skin(npc, where)));
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
                List<String> podium = new ArrayList<>();
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
                    for (String step : board.getStringList("podium")) {
                        if (LobbyNpc.coordinates(step) == null && !LobbyPoints.validName(step)) {
                            throw new IllegalArgumentException(where + ".podium: \"" + step + "\" is neither a point name"
                                    + " nor \"x y z\"");
                        }
                        podium.add(step.trim());
                    }
                    if (podium.size() > MAX_PODIUM) {
                        throw new IllegalArgumentException(where + ".podium: at most " + MAX_PODIUM + " steps");
                    }
                }
                StatsPeriod period = null;
                if (board != null && board.isSet("periode")) {
                    period = period(board.getString("periode", ""));
                    if (period == null) {
                        throw new IllegalArgumentException(where + ".periode: jour, semaine or toujours (left out: each player"
                                + " chooses it with a click)");
                    }
                }
                npcs.add(LobbyNpc.leaderboards(point, colour(name), stats, podium).withPeriod(period));
            }
        }
        return npcs;
    }

    /** The period of a wall of config.yml: jour, semaine or toujours; null for another word. */
    static StatsPeriod period(String word) {
        switch (word.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "jour":
                return StatsPeriod.DAY;
            case "semaine":
                return StatsPeriod.WEEK;
            case "toujours":
                return StatsPeriod.ALL;
            default:
                return null;
        }
    }

    /**
     * niveau: the XP of a game and of a kill, the XP of each level, and the colour of each tier of
     * levels (couleurs: the first level of the tier, up to 100, then its colour codes), and of each
     * prestige (prestiges: 200, 300... up to 1000, its couleur; left out: the plugin's own, {} for
     * none). The ranks of the first version (rangs) gave way to the ranks of the shop: they are
     * ignored.
     */
    private static NetworkLevel readLevels(ConfigurationSection section) {
        if (section == null) {
            return NetworkLevel.DEFAULT;
        }
        int win = xp(section, "xp-victoire", NetworkLevel.DEFAULT.winXp(), 0);
        int loss = xp(section, "xp-defaite", NetworkLevel.DEFAULT.lossXp(), 0);
        int kill = xp(section, "xp-kill", NetworkLevel.DEFAULT.killXp(), 0);
        int first = xp(section, "xp-niveau-2", NetworkLevel.DEFAULT.xpToNext(1), 1);
        int extra = xp(section, "xp-en-plus-par-niveau", NetworkLevel.DEFAULT.xpToNext(2) - NetworkLevel.DEFAULT.xpToNext(1), 0);
        Map<Integer, String> colours = new HashMap<>();
        ConfigurationSection list = section.getConfigurationSection("couleurs");
        if (list == null) {
            colours.putAll(NetworkLevel.DEFAULT.colours().headMap(NetworkLevel.PRESTIGE + 1));
        } else {
            for (String key : list.getKeys(false)) {
                int level;
                try {
                    level = Integer.parseInt(key.trim());
                } catch (NumberFormatException e) {
                    level = 0;
                }
                if (level < 1 || level > NetworkLevel.PRESTIGE) {
                    throw new IllegalArgumentException("niveau.couleurs." + key + ": the key is the first level of the tier,"
                            + " from 1 to " + NetworkLevel.PRESTIGE + " (after it: niveau.prestiges)");
                }
                colours.put(level, levelColour(list.getString(key, ""), "niveau.couleurs." + key));
            }
        }
        if (section.isSet("prestiges") && !section.isConfigurationSection("prestiges")) {
            throw new IllegalArgumentException("niveau.prestiges: a section, one prestige by level: 200: {couleur: \"&e&f\","
                    + " points: 20000}");
        }
        ConfigurationSection prestiges = section.getConfigurationSection("prestiges");
        if (prestiges == null) {
            colours.putAll(NetworkLevel.DEFAULT.colours().tailMap(NetworkLevel.PRESTIGE + 1));
        } else {
            for (String key : prestiges.getKeys(false)) {
                String where = "niveau.prestiges." + key;
                int level = NetworkLevel.prestigeLevel(key, where);
                ConfigurationSection one = prestiges.getConfigurationSection(key);
                if (one == null) {
                    throw new IllegalArgumentException(where + ": a section with couleur, points and objets");
                }
                if (one.isSet("couleur")) {
                    colours.put(level, levelColour(one.getString("couleur", ""), where + ".couleur"));
                }
            }
        }
        return new NetworkLevel(win, loss, kill, first, extra, colours);
    }

    /**
     * config.yml as it is written, without the bundled one behind it: a section left out is then the
     * plugin's own, as in the tests. With the bundled one as its defaults (what Bukkit's getConfig
     * gives), Bukkit makes a section left out an empty one: no step of the titles (the lobby would
     * not start), no choice for the first arrival.
     */
    static void asWritten(org.bukkit.configuration.Configuration config) {
        config.setDefaults(new org.bukkit.configuration.MemoryConfiguration());
    }

    /** The colour codes of a tier of levels: one or two colours, or a rainbow. */
    private static String levelColour(String text, String where) {
        String colour = colour(text);
        if (!isColourCodes(colour) && !isRainbow(colour)) {
            throw new IllegalArgumentException(where + ": a colour code (&6, &6&l), two for the brackets and the number (&e&f),"
                    + " or up to 8 colours for a rainbow, such as &c&6&e&a&b&d");
        }
        return colour;
    }

    /** True for 2 to 8 colours, with a format or two: a rainbow ("§c§6§e§a§b§d"). */
    static boolean isRainbow(String text) {
        return text.matches("(\u00a7[0-9a-fk-or]){2,10}") && NetworkLevel.colourCodes(text).size() >= 2
                && NetworkLevel.colourCodes(text).size() <= 8;
    }

    /** True for one or two colour or format codes, and nothing else ("§6", "§6§l"). */
    static boolean isColourCodes(String text) {
        return text.matches("(\u00a7[0-9a-fk-or]){1,2}");
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
            String slogan = category.getString("slogan", null);
            if (slogan != null && ChatColor.stripColor(colour(slogan)).length() > MAX_SLOGAN) {
                throw new IllegalArgumentException(where + ".slogan: " + MAX_SLOGAN + " characters at most (it shows under"
                        + " the NPC of the category)");
            }
            categories.add(new ModeCategory(id, colour(category.getString("nom", id)),
                    icon(where, category.getString("icone", "CHEST")), description(category),
                    slogan == null || slogan.trim().isEmpty() ? null : colour(slogan)));
        }
        return categories;
    }

    /**
     * premiere-venue: the menu of the first arrival, "What do you want to do?": per choice, nom,
     * icone, description, and mode (its queue: a training starts at once) or categorie (its menu).
     * Left out: the plugin's own, without those whose mode or category this server lacks; {}: none.
     */
    static List<FirstChoice> readFirstChoices(ConfigurationSection section, List<ModeDisplay> modes,
            List<ModeCategory> categories) {
        List<FirstChoice> choices = new ArrayList<>();
        if (section == null) {
            for (FirstChoice choice : FirstChoice.defaults()) {
                ModeDisplay mode = choice.modeId() == null ? null : find(modes, choice.modeId());
                if (choice.categoryId() != null ? hasCategory(categories, choice.categoryId()) : mode != null && !mode.hidden()) {
                    choices.add(choice);
                }
            }
            return choices;
        }
        if (section.getKeys(false).size() > FirstChoice.MAX) {
            throw new IllegalArgumentException("premiere-venue: " + FirstChoice.MAX + " choices at most");
        }
        for (String id : section.getKeys(false)) {
            String where = "premiere-venue." + id;
            ConfigurationSection choice = section.getConfigurationSection(id);
            if (choice == null) {
                throw new IllegalArgumentException(where + ": not a section");
            }
            if (!id.matches("[a-z0-9][a-z0-9-]{0,31}")) {
                throw new IllegalArgumentException(where + ": lowercase letters without accents, digits and dashes only");
            }
            String name = colour(choice.getString("nom", ""));
            if (ChatColor.stripColor(name).trim().isEmpty() || ChatColor.stripColor(name).length() > 32) {
                throw new IllegalArgumentException(where + ".nom: 1 to 32 characters");
            }
            String modeId = choice.getString("mode", null);
            String categoryId = choice.getString("categorie", null);
            if ((modeId == null) == (categoryId == null)) {
                throw new IllegalArgumentException(where + ": mode (a mode to play) or categorie (a category to open), one of them");
            }
            if (modeId != null) {
                ModeDisplay mode = find(modes, modeId);
                if (mode == null || mode.hidden()) {
                    throw new IllegalArgumentException(where + ".mode: \"" + modeId + "\" is not a mode of the compass");
                }
            } else if (!hasCategory(categories, categoryId)) {
                throw new IllegalArgumentException(where + ".categorie: \"" + categoryId + "\" is not one of the categories");
            }
            choices.add(new FirstChoice(id, name, icon(where, choice.getString("icone", "CHEST")), description(choice), modeId,
                    categoryId));
        }
        return choices;
    }

    private static ModeDisplay find(List<ModeDisplay> modes, String id) {
        for (ModeDisplay display : modes) {
            if (display.mode().id().equals(id)) {
                return display;
            }
        }
        return null;
    }

    private static boolean hasCategory(List<ModeCategory> categories, String id) {
        for (ModeCategory category : categories) {
            if (category.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** groupes: the sizes of one game, one item in their category (nom, icone, description). */
    private static List<ModeCategory> readGroups(ConfigurationSection section) {
        List<ModeCategory> groups = new ArrayList<>();
        if (section == null) {
            return groups;
        }
        for (String id : section.getKeys(false)) {
            String where = "groupes." + id;
            ConfigurationSection group = section.getConfigurationSection(id);
            if (group == null) {
                throw new IllegalArgumentException(where + ": not a section");
            }
            if (!id.matches("[a-z0-9][a-z0-9-]{0,31}")) {
                throw new IllegalArgumentException(where + ": lowercase letters without accents, digits and dashes only");
            }
            groups.add(new ModeCategory(id, colour(group.getString("nom", id)),
                    icon(where, group.getString("icone", "CHEST")), description(group)));
        }
        return groups;
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

    private static ModeDisplay readMode(String id, ConfigurationSection section, List<ModeCategory> categories,
            List<ModeCategory> groups) {
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
        String group = section.getString("groupe");
        if (group != null) {
            boolean known = false;
            for (ModeCategory g : groups) {
                known |= g.id().equals(group);
            }
            if (!known) {
                throw new IllegalArgumentException(where + ".groupe: \"" + group + "\" is not one of the groupes");
            }
        }
        Kit kit = readKit(where + ".kit", section);
        String kitFrom = section.getString("kit-de", null);
        if (kitFrom != null && kit != null) {
            throw new IllegalArgumentException(where + ": kit or kit-de (the mode whose kit it plays), not both");
        }
        String format = section.getString("format", null);
        if (format != null && (format.trim().isEmpty() || format.length() > 16)) {
            throw new IllegalArgumentException(where + ".format: 1 to 16 characters, as 1v3");
        }
        return new ModeDisplay(mode, icon, description(section), category, kit, group,
                section.getBoolean("cache", false), section.getBoolean("commencer-ici", false), kitFrom, format);
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

    /** How hard a slime block of the lobby throws forward (0: not at all). */
    /** True to greet each arrival with the ways to play (accueil: in config.yml). */
    boolean welcome() {
        return welcome;
    }

    /** The choices of the menu of the first arrival (premiere-venue:); empty: no menu. */
    List<FirstChoice> firstChoices() {
        return firstChoices;
    }

    /** The billboards of the lobby (panneaux:); empty: none. */
    List<Billboard> billboards() {
        return billboards;
    }

    double slimeForward() {
        return slimeForward;
    }

    /** How hard a slime block of the lobby throws up. */
    double slimeUp() {
        return slimeUp;
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

    /** The server's Hypixel API key, for players to import their Bed Wars settings; "" for none. */
    String hypixelKey() {
        return hypixelKey;
    }

    /** The MineSkin API key that signs the skins of the NPCs, or "" to ask without one. */
    String mineskinKey() {
        return mineskinKey;
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

    /** A group of modes by id, or null. */
    ModeCategory group(String id) {
        for (ModeCategory group : groups) {
            if (group.id().equals(id)) {
                return group;
            }
        }
        return null;
    }

    /** The modes of one group, in the order of config.yml. */
    List<ModeDisplay> modesOfGroup(String groupId) {
        List<ModeDisplay> result = new ArrayList<>();
        for (ModeDisplay display : modes) {
            if (groupId.equals(display.group()) && !display.hidden()) {
                result.add(display);
            }
        }
        return result;
    }

    /** An item of a category's menu: a mode, or a group of modes. */
    static final class Entry {
        final ModeDisplay mode;
        final ModeCategory group;

        Entry(ModeDisplay mode, ModeCategory group) {
            this.mode = mode;
            this.group = group;
        }
    }

    /**
     * The items of a category (of every mode for null): each mode, but the modes of one group as
     * one item, where the first of them is in config.yml.
     */
    List<Entry> entriesIn(String categoryId) {
        List<Entry> entries = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ModeDisplay display : modesIn(categoryId)) {
            if (display.hidden()) {
                continue;
            }
            if (display.group() == null) {
                entries.add(new Entry(display, null));
            } else if (seen.add(display.group())) {
                entries.add(new Entry(null, group(display.group())));
            }
        }
        return entries;
    }

    /**
     * The items of a lobby NPC's menu: its modes (not the hidden ones), but two or more of one group as one item, where
     * the first of them is (Top Bridge 1v1, 2v2 and FFA: one ladder), unless that would leave a
     * single item (the Ranked Bedwars NPC shows its four sizes at once).
     */
    List<Entry> entriesOf(List<String> modeIds) {
        Map<String, Integer> perGroup = new HashMap<>();
        for (String id : modeIds) {
            ModeDisplay display = display(id);
            if (display != null && !display.hidden() && display.group() != null) {
                perGroup.merge(display.group(), 1, Integer::sum);
            }
        }
        List<Entry> entries = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String id : modeIds) {
            ModeDisplay display = display(id);
            if (display == null || display.hidden()) {
                continue;
            }
            String group = display.group();
            if (group == null || perGroup.get(group) < 2 || group(group) == null) {
                entries.add(new Entry(display, null));
            } else if (seen.add(group)) {
                entries.add(new Entry(null, group(group)));
            }
        }
        if (entries.size() == 1 && entries.get(0).group != null) {
            entries.clear();
            for (String id : modeIds) {
                if (display(id) != null && !display(id).hidden()) {
                    entries.add(new Entry(display(id), null));
                }
            }
        }
        return entries;
    }

    /** kit-de: names a mode of the list with a kit of its own. */
    static void checkKitFrom(List<ModeDisplay> modes) {
        for (ModeDisplay display : modes) {
            String from = display.kitFrom();
            if (from == null) {
                continue;
            }
            ModeDisplay holder = null;
            for (ModeDisplay other : modes) {
                if (other.mode().id().equals(from)) {
                    holder = other;
                }
            }
            if (holder == null || holder.kit() == null) {
                throw new IllegalArgumentException("modes." + display.mode().id() + ".kit-de: \"" + from
                        + "\" is not a mode with a kit of its own");
            }
        }
    }

    /**
     * Where the kit of a game of this mode, and the layouts the players keep of it, are: the mode
     * named by its kit-de:, else the mode itself; for the kit of a role ("invis-practice-1v3/defense"),
     * that mode's role ("invis-practice/defense").
     */
    String kitMode(String modeId) {
        if (modeId == null) {
            return null;
        }
        int slash = modeId.indexOf('/');
        ModeDisplay display = display(slash < 0 ? modeId : modeId.substring(0, slash));
        if (display == null || display.kitFrom() == null) {
            return modeId;
        }
        return display.kitFrom() + (slash < 0 ? "" : modeId.substring(slash));
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
