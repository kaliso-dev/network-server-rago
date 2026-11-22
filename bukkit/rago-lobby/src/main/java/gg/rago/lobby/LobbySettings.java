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
    /** The texts of the lobby ({@link LobbyText}): none without textes:. */
    private List<LobbyText> texts = Collections.emptyList();
    /** RagoBot (ragobot:). */
    private BotSettings bots;
    /** The block under each NPC that has one in config.yml (pnj: ... bloc:), by point. */
    private Map<String, Pedestal> pedestals = Collections.emptyMap();
    /** The points of the daily reward chest and of the quests NPC. */
    private String dailyPoint = DailyChest.POINT;
    private String questsPoint = QuestsNpc.POINT;
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
    /** One row: the top row of the compass, where they sit side by side. */
    static final int MAX_CATEGORIES = 9;
    /** The lines a click on a product NPC shows in the chat (liens:), at most. */
    static final int MAX_PRODUCT_LINKS = 8;
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
        checkAlso(categories, groups, modes, perCategory);
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
        List<LobbyText> texts = LobbyText.read(config.getConfigurationSection("textes"));
        for (LobbyText text : texts) {
            for (LobbyNpc npc : npcs) {
                if (npc.point().equals(text.point)) {
                    throw new IllegalArgumentException("textes." + text.point + ": the point of an NPC (pnj:), a text needs"
                            + " its own point");
                }
            }
        }
        Map<String, GameMode> modesById = new HashMap<>();
        for (ModeDisplay display : modes) {
            modesById.put(display.mode().id(), display.mode());
        }
        BotSettings bots = BotSettings.read(config.getConfigurationSection("ragobot"), modesById);
        Map<String, Pedestal> pedestals = readPedestals(config.getConfigurationSection("pnj"));
        String dailyPoint = pointName(config, "point-coffre-du-jour", DailyChest.POINT);
        String questsPoint = pointName(config, "point-pnj-quetes", QuestsNpc.POINT);
        try {
            LobbySettings settings = new LobbySettings(world, voidHeight, title, address, defaultRating, window, categories,
                    groups, modes, npcs, levels, hypixelKey, privateRoutes);
            settings.slimeForward = slimeForward;
            settings.slimeUp = slimeUp;
            settings.mineskinKey = mineskinKey;
            settings.welcome = config.getBoolean("accueil", true);
            settings.firstChoices = Collections.unmodifiableList(firstChoices);
            settings.billboards = billboards;
            settings.texts = Collections.unmodifiableList(texts);
            settings.bots = bots;
            settings.pedestals = Collections.unmodifiableMap(pedestals);
            settings.dailyPoint = dailyPoint;
            settings.questsPoint = questsPoint;
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
                if ("selecteur".equals(type)) {
                    if (npc.contains("modes") || npc.contains("categorie")) {
                        throw new IllegalArgumentException(where + ": the leaderboard selector has neither modes nor categorie");
                    }
                    String subtitle = npc.getString("sous-titre", "");
                    npcs.add(LobbyNpc.selector(point, colour(npc.getString("nom", "&b&lLEADERBOARDS")), selectorStats(npc, where),
                            minimumGames(npc, where, "jour", DEFAULT_MINIMUM_DAY), minimumGames(npc, where, "semaine",
                                    DEFAULT_MINIMUM_WEEK))
                            .withSubtitle(subtitle.trim().isEmpty() ? null : colour(subtitle)).withSkin(skin(npc, where)));
                    continue;
                }
                if ("personne".equals(type)) {
                    if (npc.contains("modes") || npc.contains("categorie")) {
                        throw new IllegalArgumentException(where + ": a person has neither modes nor categorie");
                    }
                    String rank = npc.getString("rang", "").trim();
                    npcs.add(LobbyNpc.person(point, colour(npc.getString("nom", point)), rank.isEmpty() ? null : rank)
                            .withSkin(skin(npc, where)));
                    continue;
                }
                if ("produit".equals(type)) {
                    if (npc.contains("modes") || npc.contains("categorie") || npc.contains("rang")) {
                        throw new IllegalArgumentException(where + ": a product has neither modes, categorie nor rang");
                    }
                    List<String> links = new ArrayList<>();
                    for (String line : npc.getStringList("liens")) {
                        if (!line.trim().isEmpty()) {
                            links.add(colour(line));
                        }
                    }
                    if (links.isEmpty() || links.size() > MAX_PRODUCT_LINKS) {
                        throw new IllegalArgumentException(where + ".liens: from 1 to " + MAX_PRODUCT_LINKS
                                + " lines, what a click shows in the chat");
                    }
                    String title = npc.getString("titre", "");
                    String subtitle = npc.getString("sous-titre", "");
                    npcs.add(LobbyNpc.product(point, colour(npc.getString("nom", point)),
                            title.trim().isEmpty() ? null : colour(title), links)
                            .withSubtitle(subtitle.trim().isEmpty() ? null : colour(subtitle)).withSkin(skin(npc, where)));
                    continue;
                }
                if (!"modes".equals(type)) {
                    throw new IllegalArgumentException(where + ".type: \"" + type
                            + "\" is not modes, stats, shop, selecteur, personne or produit");
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
            // skin-joueur: the skin of a Minecraft account, in place of skin:
            for (int i = 0; i < npcs.size(); i++) {
                LobbyNpc read = npcs.get(i);
                String where = "pnj." + read.point();
                String account = section.getConfigurationSection(read.point()).getString("skin-joueur", "").trim();
                if (account.isEmpty()) {
                    continue;
                }
                if (!AccountSkins.validName(account)) {
                    throw new IllegalArgumentException(where + ".skin-joueur: \"" + account
                            + "\" is not a Minecraft name (3 to 16 letters, digits or _)");
                }
                if (read.skin() != null) {
                    throw new IllegalArgumentException(where + ": skin or skin-joueur, not both");
                }
                npcs.set(i, read.withAccount(account));
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
                // a wall of the selector: the stat each player chose there, on the period of the wall
                LobbyNpc chooser = null;
                if (board != null && board.isSet("selecteur")) {
                    String of = board.getString("selecteur", "").trim();
                    for (LobbyNpc npc : npcs) {
                        if (npc.point().equals(of) && npc.kind() == LobbyNpc.Kind.SELECTOR) {
                            chooser = npc;
                        }
                    }
                    if (chooser == null) {
                        throw new IllegalArgumentException(where + ".selecteur: \"" + of + "\" is not an NPC of pnj: of type"
                                + " selecteur");
                    }
                    if (board.isSet("tableaux") || !board.isSet("periode")) {
                        throw new IllegalArgumentException(where + ": a wall of the selector has its periode (jour, semaine or"
                                + " toujours) and no tableaux: it shows the stats of the selector");
                    }
                }
                if (board == null) {
                    // The short form: only the title, over a board of wins.
                    name = boards.getString(point, point);
                    stats.add(Collections.singletonList(BoardStat.WINS));
                } else {
                    name = board.getString("nom", "");
                    // a wall of the selector takes its stats (LobbyNpc#following): nothing to read
                    List<?> entries = chooser != null ? Collections.singletonList(BoardStat.WINS.key()) : board.getList("tableaux");
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
                LobbyNpc wall = LobbyNpc.leaderboards(point, colour(name), stats, podium).withPeriod(period);
                npcs.add(chooser == null ? wall : wall.following(chooser));
            }
        }
        return npcs;
    }

    /** The games a ratio of a day needs on the walls of a selector, unless its config says otherwise (plan 3.1). */
    static final int DEFAULT_MINIMUM_DAY = 5;
    /** The games a ratio of a week needs. */
    static final int DEFAULT_MINIMUM_WEEK = 15;

    /**
     * The stats of a leaderboard selector, in the order of its menu (stats:), by default those of
     * the plan: the win streak, the Network level, the ELO, the wins, the kills, the FKDR, the WLR,
     * then the games played.
     */
    private static List<BoardStat> selectorStats(ConfigurationSection npc, String where) {
        List<BoardStat> stats = new ArrayList<>();
        if (!npc.isSet("stats")) {
            return java.util.Arrays.asList(BoardStat.STREAK, BoardStat.LEVEL, BoardStat.ELO, BoardStat.WINS, BoardStat.KILLS,
                    BoardStat.FKDR, BoardStat.WLR, BoardStat.GAMES);
        }
        for (String key : npc.getStringList("stats")) {
            BoardStat stat = BoardStat.of(key.trim());
            if (stat == null) {
                throw new IllegalArgumentException(where + ".stats: \"" + key + "\" is not " + BoardStat.keys());
            }
            if (!stats.contains(stat)) {
                stats.add(stat);
            }
        }
        if (stats.isEmpty()) {
            throw new IllegalArgumentException(where + ".stats: at least one stat");
        }
        return stats;
    }

    /** minimum-parties.jour (or semaine) of a selector: the games a ratio of the period needs, 0 or more. */
    private static int minimumGames(ConfigurationSection npc, String where, String period, int fallback) {
        String key = "minimum-parties." + period;
        if (!npc.isSet(key)) {
            return fallback;
        }
        int games = npc.getInt(key, -1);
        if (games < 0) {
            throw new IllegalArgumentException(where + "." + key + ": a number of games, 0 or more");
        }
        return games;
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
            boolean party = category.getBoolean("party", false);
            for (ModeCategory before : categories) {
                if (party && before.party()) {
                    throw new IllegalArgumentException(where + ".party: one category of the parties at most, \""
                            + before.id() + "\" is");
                }
            }
            if (party && !category.getStringList("aussi").isEmpty()) {
                throw new IllegalArgumentException(where + ".aussi: not in the category of the parties (party: true), whose"
                        + " modes follow the size of the viewer's party");
            }
            List<String> also = new ArrayList<>();
            for (String other : category.getStringList("aussi")) {
                String key = other.trim().toLowerCase(java.util.Locale.ROOT);
                if (also.contains(key)) {
                    throw new IllegalArgumentException(where + ".aussi: \"" + other + "\" twice");
                }
                also.add(key);
            }
            categories.add(new ModeCategory(id, colour(category.getString("nom", id)),
                    icon(where, category.getString("icone", "CHEST")), description(category),
                    slogan == null || slogan.trim().isEmpty() ? null : colour(slogan), also, party));
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
                if (c.id().equals(category) && c.party()) {
                    throw new IllegalArgumentException(where + ".categorie: \"" + category + "\" is the category of the"
                            + " parties (party: true): it has no modes of its own, it shows those of the others");
                }
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

    /** RagoBot (ragobot:). */
    BotSettings bots() {
        return bots;
    }

    /** The texts of the lobby (textes:), each on its point; empty: none. */
    List<LobbyText> texts() {
        return texts;
    }

    /** The text on this point, or null. */
    LobbyText text(String point) {
        for (LobbyText text : texts) {
            if (text.point.equals(point)) {
                return text;
            }
        }
        return null;
    }

    /** The block the NPC of this point stands on: its bloc:, else polished andesite. */
    Pedestal pedestal(String point) {
        Pedestal pedestal = pedestals.get(point);
        return pedestal == null ? Pedestal.ANDESITE : pedestal;
    }

    /** The point of the daily reward chest (point-coffre-du-jour:, daily without it). */
    String dailyPoint() {
        return dailyPoint;
    }

    /** The point of the quests NPC (point-pnj-quetes:, quests without it). */
    String questsPoint() {
        return questsPoint;
    }

    /** Refuses a person (type: personne) whose rang: is not a rank of rangs:. */
    static void checkRanks(List<LobbyNpc> npcs, Ranks ranks) {
        for (LobbyNpc npc : npcs) {
            if (npc.rank() != null && ranks.get(npc.rank()) == null) {
                throw new IllegalArgumentException("pnj." + npc.point() + ".rang: \"" + npc.rank() + "\" is not a rank of rangs:");
            }
        }
    }

    /** The bloc: of each NPC of pnj: that has one. */
    static Map<String, Pedestal> readPedestals(ConfigurationSection section) {
        Map<String, Pedestal> pedestals = new HashMap<>();
        if (section == null) {
            return pedestals;
        }
        for (String point : section.getKeys(false)) {
            ConfigurationSection npc = section.getConfigurationSection(point);
            if (npc == null || !npc.isSet("bloc")) {
                continue;
            }
            try {
                pedestals.put(point, Pedestal.parse(String.valueOf(npc.get("bloc"))));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("pnj." + point + ".bloc: " + e.getMessage(), e);
            }
        }
        return pedestals;
    }

    /** A point name of config.yml (a name of /lobby point), or its default without the key. */
    private static String pointName(ConfigurationSection config, String key, String fallback) {
        String name = config.getString(key, fallback).trim();
        if (!LobbyPoints.validName(name)) {
            throw new IllegalArgumentException(key + ": \"" + name + "\" is not a point name of /lobby point");
        }
        return name;
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
        for (ModeDisplay display : ownModes(categoryId)) {
            if (display.hidden()) {
                continue;
            }
            if (display.group() == null) {
                entries.add(new Entry(display, null));
            } else if (seen.add(display.group())) {
                entries.add(new Entry(null, group(display.group())));
            }
        }
        // then those of other categories it shows too: a group as one item, a mode alone
        ModeCategory category = categoryId == null ? null : category(categoryId);
        for (String id : category == null ? Collections.<String>emptyList() : category.also()) {
            ModeCategory group = group(id);
            ModeDisplay display = display(id);
            if (group != null) {
                entries.add(new Entry(null, group));
            } else if (display != null && !display.hidden()) {
                entries.add(new Entry(display, null));
            }
        }
        return entries;
    }

    /**
     * aussi: of each category: modes or groups of the modes list, not hidden, of another category;
     * with its own items, a category shows MAX_MODES_PER_CATEGORY at most.
     */
    private static void checkAlso(List<ModeCategory> categories, List<ModeCategory> groups, List<ModeDisplay> modes,
            Map<String, Integer> perCategory) {
        for (ModeCategory category : categories) {
            String where = "categories." + category.id() + ".aussi";
            for (String id : category.also()) {
                boolean isGroup = false;
                for (ModeCategory group : groups) {
                    isGroup |= group.id().equals(id);
                }
                String home = null;
                boolean found = false;
                for (ModeDisplay display : modes) {
                    if (isGroup ? id.equals(display.group()) : display.mode().id().equals(id)) {
                        if (display.hidden() && !isGroup) {
                            throw new IllegalArgumentException(where + ": \"" + id + "\" is hidden from the selector");
                        }
                        found = true;
                        home = display.category();
                    }
                }
                if (!found) {
                    throw new IllegalArgumentException(where + ": \"" + id + "\" is neither a mode nor a group with modes");
                }
                if (category.id().equals(home)) {
                    throw new IllegalArgumentException(where + ": \"" + id + "\" is of this category already");
                }
            }
            int items = perCategory.getOrDefault(category.id(), 0) + category.also().size();
            if (items > MAX_MODES_PER_CATEGORY) {
                throw new IllegalArgumentException(where + ": " + MAX_MODES_PER_CATEGORY + " items at most in a category,"
                        + " its own and those of aussi");
            }
        }
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

    /**
     * The modes of one category (all of them for null), in the order of config.yml, then those of
     * other categories it shows too (aussi:).
     */
    List<ModeDisplay> modesIn(String categoryId) {
        if (categoryId == null) {
            return modes;
        }
        List<ModeDisplay> result = ownModes(categoryId);
        ModeCategory category = category(categoryId);
        for (String id : category == null ? Collections.<String>emptyList() : category.also()) {
            List<ModeDisplay> more = group(id) != null ? modesOfGroup(id) : Collections.singletonList(display(id));
            for (ModeDisplay display : more) {
                if (display != null && !result.contains(display)) {
                    result.add(display);
                }
            }
        }
        return result;
    }

    /** The modes whose category is this one, in the order of config.yml. */
    private List<ModeDisplay> ownModes(String categoryId) {
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
