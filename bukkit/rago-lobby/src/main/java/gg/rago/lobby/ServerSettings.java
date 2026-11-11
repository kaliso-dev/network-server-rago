package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * What makes the lobby a network, from config.yml: the ranks (rangs), the colours of name and the
 * badges (styles), the ELO divisions (elo), the
 * Rago Points (rago-points), the prices of the shop (boutique), the chat rules (chat), the tab
 * list (tab), the server list message (motd), the announcements (annonces), the links (liens), the
 * rewards of the Network levels (niveau.recompenses) and the quests (quetes).
 * Every section is optional: a config.yml of an earlier version gets the defaults. Immutable.
 */
final class ServerSettings {

    /** The Rago Points a player earns. */
    static final class Points {
        final int win;
        final int loss;
        final int kill;
        /** A new level gives the level times this. */
        final int perLevel;
        /** The daily reward, and what each day of a streak adds to it (up to {@link #MAX_STREAK} days). */
        final int daily;
        final int dailyStreak;
        /**
         * The trainings, the practice, the aim trainer and the events: one point for each xpPerPoint
         * Network XP they give (before the boosts), at least one; 0 gives none.
         */
        final int xpPerPoint;

        static final int MAX_STREAK = 7;

        Points(int win, int loss, int kill, int perLevel, int daily, int dailyStreak) {
            this(win, loss, kill, perLevel, daily, dailyStreak, 3);
        }

        Points(int win, int loss, int kill, int perLevel, int daily, int dailyStreak, int xpPerPoint) {
            this.win = win;
            this.loss = loss;
            this.kill = kill;
            this.perLevel = perLevel;
            this.daily = daily;
            this.dailyStreak = dailyStreak;
            this.xpPerPoint = xpPerPoint;
        }

        /** The points of a training that gave this Network XP: 10 XP give 3 (one for 3 XP), 1 XP gives 1. */
        int forXp(int xp) {
            if (xpPerPoint <= 0 || xp <= 0) {
                return 0;
            }
            return Math.max(1, xp / xpPerPoint);
        }

        /** The reward of the n-th day in a row (1 for the first). */
        int dailyReward(int day) {
            return daily + dailyStreak * (Math.max(1, Math.min(MAX_STREAK, day)) - 1);
        }
    }

    static final int MAX_POINTS = 100_000_000;

    private final Ranks ranks;
    private final Map<String, NameStyle> styles;
    private final EloLadder elo;
    private final Points points;
    private final Map<String, Cosmetic> cosmetics;
    private final long chatCooldownMillis;
    private final boolean blockRepeats;
    private final List<String> tabHeader;
    private final List<String> tabFooter;
    private final List<String> motd;
    private final int announceMinutes;
    private final List<String> announcements;
    private final Map<String, List<String>> links;
    private final LevelRewards levelRewards;
    private final Quests quests;
    private final StarterPack starterPack;
    private final Season season;

    private ServerSettings(Ranks ranks, Map<String, NameStyle> styles, EloLadder elo, Points points, Map<String, Cosmetic> cosmetics, long chatCooldownMillis,
            boolean blockRepeats, List<String> tabHeader, List<String> tabFooter, List<String> motd, int announceMinutes,
            List<String> announcements, Map<String, List<String>> links, LevelRewards levelRewards, Quests quests,
            StarterPack starterPack, Season season) {
        this.ranks = ranks;
        this.styles = Collections.unmodifiableMap(styles);
        this.elo = elo;
        this.points = points;
        this.cosmetics = Collections.unmodifiableMap(cosmetics);
        this.chatCooldownMillis = chatCooldownMillis;
        this.blockRepeats = blockRepeats;
        this.tabHeader = Collections.unmodifiableList(tabHeader);
        this.tabFooter = Collections.unmodifiableList(tabFooter);
        this.motd = Collections.unmodifiableList(motd);
        this.announceMinutes = announceMinutes;
        this.announcements = Collections.unmodifiableList(announcements);
        this.links = Collections.unmodifiableMap(links);
        this.levelRewards = levelRewards;
        this.quests = quests;
        this.starterPack = starterPack;
        this.season = season;
    }

    static ServerSettings load(ConfigurationSection config) {
        Map<String, NameStyle> styles = readStyles(config.getConfigurationSection("styles"));
        Ranks ranks = readRanks(config.getConfigurationSection("rangs"), styles);
        EloLadder elo = readElo(config.getConfigurationSection("elo"));
        ConfigurationSection p = config.getConfigurationSection("rago-points");
        Points points = new Points(count(p, "rago-points", "victoire", 20), count(p, "rago-points", "defaite", 5),
                count(p, "rago-points", "kill", 2), count(p, "rago-points", "par-niveau", 10),
                count(p, "rago-points", "quotidien", 100), count(p, "rago-points", "quotidien-serie", 25),
                count(p, "rago-points", "xp-par-point", 3));
        Map<String, Cosmetic> cosmetics = new LinkedHashMap<>();
        ConfigurationSection shop = config.getConfigurationSection("boutique");
        for (Cosmetic cosmetic : Cosmetic.catalogue().values()) {
            ConfigurationSection own = shop == null ? null : shop.getConfigurationSection(cosmetic.id());
            if (own == null) {
                cosmetics.put(cosmetic.id(), cosmetic);
            } else {
                String where = "boutique." + cosmetic.id();
                cosmetics.put(cosmetic.id(), cosmetic.priced(count(own, where, "prix", cosmetic.price()),
                        level(own, where, cosmetic.level())));
            }
        }
        if (shop != null) {
            for (String key : shop.getKeys(false)) {
                if (!cosmetics.containsKey(key)) {
                    throw new IllegalArgumentException("boutique." + key + ": no such cosmetic (the cosmetics: "
                            + String.join(", ", cosmetics.keySet()) + ")");
                }
            }
        }
        ConfigurationSection chat = config.getConfigurationSection("chat");
        int cooldown = chat == null ? 2 : chat.getInt("delai-secondes", 2);
        if (cooldown < 0 || cooldown > 60) {
            throw new IllegalArgumentException("chat.delai-secondes: from 0 to 60");
        }
        boolean repeats = chat == null || chat.getBoolean("bloquer-repetition", true);
        List<String> header = lines(config, "tab.haut", defaultHeader(), 4);
        List<String> footer = lines(config, "tab.bas", defaultFooter(), 4);
        List<String> motd = lines(config, "motd", defaultMotd(), 2);
        int minutes = config.getInt("annonces.minutes", 5);
        if (minutes < 1 || minutes > 120) {
            throw new IllegalArgumentException("annonces.minutes: from 1 to 120");
        }
        List<String> announcements = lines(config, "annonces.messages", defaultAnnouncements(), 30);
        Map<String, List<String>> links = new LinkedHashMap<>();
        ConfigurationSection l = config.getConfigurationSection("liens");
        if (l == null) {
            links.put("discord", Collections.singletonList(ChatColor.BLUE + "Our Discord: " + ChatColor.WHITE + "discord.gg/ragooptimizer"));
            links.put("regles", defaultRules());
        } else {
            for (String key : l.getKeys(false)) {
                if (!key.matches("discord|site|regles|boutique")) {
                    throw new IllegalArgumentException("liens." + key + ": discord, site, regles or boutique");
                }
                links.put(key, lines(l, key, Collections.<String>emptyList(), 15));
            }
        }
        LevelRewards rewards = readRewards(config.getConfigurationSection("niveau.recompenses"), cosmetics, styles);
        Quests quests = readQuests(config.getConfigurationSection("quetes"));
        StarterPack pack = StarterPack.read(config.getConfigurationSection("pack-depart"), cosmetics, ranks);
        Season season = Season.read(config.getConfigurationSection("saison"), elo);
        return new ServerSettings(ranks, styles, elo, points, cosmetics, cooldown * 1000L, repeats, header, footer, motd,
                minutes, announcements, links, rewards, quests, pack, season);
    }

    // --- Level rewards and quests ---------------------------------------------------------------

    /**
     * niveau.recompenses: by level, the Rago Points (points) and the cosmetics and styles
     * (objets: "trail-clouds", "couleur-or") it gives. Left out: the plugin's own.
     */
    private static LevelRewards readRewards(ConfigurationSection section, Map<String, Cosmetic> cosmetics,
            Map<String, NameStyle> styles) {
        LevelRewards rewards = section == null ? LevelRewards.defaults() : null;
        if (rewards == null) {
            List<LevelRewards.Reward> list = new ArrayList<>();
            for (String key : section.getKeys(false)) {
                String where = "niveau.recompenses." + key;
                int level;
                try {
                    level = Integer.parseInt(key.trim());
                } catch (NumberFormatException e) {
                    level = 0;
                }
                if (level < 2 || level > NetworkLevel.MAX_LEVEL) {
                    throw new IllegalArgumentException(where + ": the key is a level, from 2 to " + NetworkLevel.MAX_LEVEL);
                }
                ConfigurationSection one = section.getConfigurationSection(key);
                if (one == null) {
                    throw new IllegalArgumentException(where + ": a section with points and objets");
                }
                list.add(new LevelRewards.Reward(level, count(one, where, "points", 0), one.getStringList("objets")));
            }
            rewards = new LevelRewards(list);
        }
        for (LevelRewards.Reward reward : rewards.all()) {
            for (String item : reward.items()) {
                if (!cosmetics.containsKey(item) && !styles.containsKey(item)) {
                    throw new IllegalArgumentException("niveau.recompenses." + reward.level() + ".objets: no cosmetic or style "
                            + item + " (a cosmetic of the shop, such as trail-clouds, or couleur-<id>, badge-<id>)");
                }
            }
        }
        return rewards;
    }

    /**
     * quetes: the daily (quotidiennes) and weekly (hebdomadaires) quests: nom, type (parties,
     * victoires, kills, victoires-classees), objectif, xp, and mode to count one mode only.
     */
    private static Quests readQuests(ConfigurationSection section) {
        if (section == null) {
            return Quests.defaults();
        }
        List<Quests.Quest> list = new ArrayList<>();
        for (Quests.Period period : Quests.Period.values()) {
            ConfigurationSection quests = section.getConfigurationSection(period.section());
            if (quests == null) {
                list.addAll(Quests.defaults().of(period));
                continue;
            }
            for (String id : quests.getKeys(false)) {
                String where = "quetes." + period.section() + "." + id;
                ConfigurationSection one = quests.getConfigurationSection(id);
                if (one == null || !id.matches("[a-z0-9-]{1,16}")) {
                    throw new IllegalArgumentException(where + ": a section, its name 1 to 16 lowercase letters, digits or -");
                }
                Quests.Goal goal = Quests.Goal.of(one.getString("type", ""));
                if (goal == null) {
                    throw new IllegalArgumentException(where + ".type: parties, victoires, kills or victoires-classees");
                }
                int target = count(one, where, "objectif", 0);
                if (target < 1) {
                    throw new IllegalArgumentException(where + ".objectif: 1 at least");
                }
                String name = LobbySettings.colour(one.getString("nom", ""));
                if (name.isEmpty() || name.length() > 32) {
                    throw new IllegalArgumentException(where + ".nom: 1 to 32 characters");
                }
                list.add(new Quests.Quest(period, id, name, goal, target, count(one, where, "xp", 0),
                        one.getString("mode", null)));
            }
        }
        return new Quests(list);
    }

    // --- Ranks ----------------------------------------------------------------------------------

    private static Ranks readRanks(ConfigurationSection section, Map<String, NameStyle> styles) {
        if (section == null) {
            Ranks ranks = Ranks.defaults();
            for (RankDef rank : ranks.all()) {
                checkStyles("rangs." + rank.id(), rank.style(), rank.badge(), styles);
            }
            return ranks;
        }
        List<RankDef> ranks = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            String where = "rangs." + id;
            if (!id.matches("[a-z0-9_-]{1,16}")) {
                throw new IllegalArgumentException(where + ": the name of a rank is 1 to 16 lowercase letters, digits, - or _");
            }
            ConfigurationSection rank = section.getConfigurationSection(id);
            if (rank == null) {
                throw new IllegalArgumentException(where + ": not a section");
            }
            String prefix = LobbySettings.colour(rank.getString("prefixe", ""));
            String colour = LobbySettings.colour(rank.getString("couleur", "&7"));
            if (!LobbySettings.isColourCodes(colour)) {
                throw new IllegalArgumentException(where + ".couleur: one or two colour codes, such as &c or &c&l");
            }
            if (!RankDef.fitsHead(prefix, colour)) {
                throw new IllegalArgumentException(where + ".prefixe: the prefix and the colour of the name are "
                        + RankDef.MAX_PREFIX + " characters at most, colour codes included (without the brackets if"
                        + " needed: above the head, [PREMIUM+] shows as PREMIUM+)");
            }
            String chat = LobbySettings.colour(rank.getString("chat", "&f"));
            if (!LobbySettings.isColourCodes(chat)) {
                throw new IllegalArgumentException(where + ".chat: one or two colour codes, such as &f");
            }
            boolean staff = rank.getBoolean("staff", false);
            List<String> permissions = new ArrayList<>();
            for (String permission : rank.getStringList("permissions")) {
                if (!permission.matches("[A-Za-z0-9_.*-]{1,64}")) {
                    throw new IllegalArgumentException(where + ".permissions: \"" + permission + "\" is not a permission");
                }
                permissions.add(permission);
            }
            String parent = rank.getString("herite", null);
            int price = count(rank, where, "prix", 0);
            if (staff && price > 0) {
                throw new IllegalArgumentException(where + ".prix: a staff rank is not sold");
            }
            int level = level(rank, where, 0);
            Material icon = Material.matchMaterial(rank.getString("icone", "NAME_TAG"));
            if (icon == null) {
                throw new IllegalArgumentException(where + ".icone: unknown material");
            }
            List<String> perks = new ArrayList<>();
            for (String perk : rank.getStringList("avantages")) {
                perks.add(LobbySettings.colour(perk));
            }
            double boost = rank.getDouble("boost-xp", 1.0);
            if (boost < 1.0 || boost > 5.0) {
                throw new IllegalArgumentException(where + ".boost-xp: from 1 (none) to 5, such as 1.5 for half more XP");
            }
            String style = rank.getString("style", null);
            String badge = rank.getString("badge", null);
            checkStyles(where, style, badge, styles);
            boolean webStore = rank.getBoolean("boutique-en-ligne", false);
            if (staff && webStore) {
                throw new IllegalArgumentException(where + ".boutique-en-ligne: a staff rank is not sold");
            }
            String webPrice = rank.getString("prix-en-ligne", null);
            if (webPrice != null && (!webStore || webPrice.trim().isEmpty() || webPrice.length() > 16)) {
                throw new IllegalArgumentException(where + ".prix-en-ligne: the price shown for a rank of the web store"
                        + " (boutique-en-ligne: true), 16 characters at most, such as \"$3.99\"");
            }
            int priority = rank.getInt("priorite-file", 0);
            if (priority < 0 || priority > 9) {
                throw new IllegalArgumentException(where + ".priorite-file: from 0 (none) to 9, the higher first in the queues");
            }
            ranks.add(new RankDef(id, prefix, colour, chat, staff, permissions, parent, price, level, icon, perks, boost,
                    style, badge, webStore, webPrice == null ? null : webPrice.trim(), priority));
        }
        return new Ranks(ranks);
    }

    private static void checkStyles(String where, String style, String badge, Map<String, NameStyle> styles) {
        if (style != null && !styles.containsKey(NameStyle.key(NameStyle.Kind.COLOUR, style))) {
            throw new IllegalArgumentException(where + ".style: no colour " + style + " in styles.couleurs");
        }
        if (badge != null && !styles.containsKey(NameStyle.key(NameStyle.Kind.BADGE, badge))) {
            throw new IllegalArgumentException(where + ".badge: no badge " + badge + " in styles.badges");
        }
    }

    // --- Styles ---------------------------------------------------------------------------------

    /**
     * styles: the colours of name (couleurs: nom, degrade, gras, obtention), the badges (badges:
     * nom, texte, obtention) and the colours of the + of [PREMIUM+] (plus: nom, couleur, obtention).
     * A list left out is the plugin's own.
     */
    private static Map<String, NameStyle> readStyles(ConfigurationSection section) {
        Map<String, NameStyle> catalogue = NameStyle.catalogue();
        Map<String, NameStyle> styles = new LinkedHashMap<>();
        for (NameStyle.Kind kind : NameStyle.Kind.values()) {
            ConfigurationSection list = section == null ? null : section.getConfigurationSection(kind.section());
            if (list == null) {
                for (NameStyle style : catalogue.values()) {
                    if (style.kind() == kind) {
                        styles.put(style.key(), style);
                    }
                }
                continue;
            }
            for (String id : list.getKeys(false)) {
                String where = "styles." + kind.section() + "." + id;
                ConfigurationSection one = list.getConfigurationSection(id);
                if (one == null || !id.matches("[a-z0-9-]{1,16}")) {
                    throw new IllegalArgumentException(where + ": a section, its name 1 to 16 lowercase letters, digits or -");
                }
                String name = one.getString("nom", id);
                if (name.isEmpty() || name.length() > 24) {
                    throw new IllegalArgumentException(where + ".nom: 1 to 24 characters");
                }
                NameStyle.Obtain obtain = NameStyle.Obtain.of(one.getString("obtention", "achat"));
                if (obtain == null) {
                    throw new IllegalArgumentException(where + ".obtention: premium, achat, niveau or points");
                }
                int price = one.getInt("prix", 0);
                if (obtain == NameStyle.Obtain.POINTS && (price < 1 || price > 1_000_000)) {
                    throw new IllegalArgumentException(where + ".prix: the price in Rago Points (obtention: points), 1 to 1000000");
                }
                String text;
                if (kind == NameStyle.Kind.PLUS) {
                    text = one.getString("couleur", "").toLowerCase(java.util.Locale.ROOT).replace("&", "");
                    if (!text.matches("[0-9a-f]")) {
                        throw new IllegalArgumentException(where + ".couleur: the colour of the +, one colour character"
                                + " (0 to 9, a to f), such as 0 for black");
                    }
                } else if (kind == NameStyle.Kind.COLOUR) {
                    text = one.getString("degrade", "").toLowerCase(java.util.Locale.ROOT).replace("&", "");
                    if (!NameStyle.isStops(text)) {
                        throw new IllegalArgumentException(where + ".degrade: the colours from the first letter to the last,"
                                + " 1 to 16 colour characters (0 to 9, a to f), such as 5ddfd");
                    }
                } else {
                    text = LobbySettings.colour(one.getString("texte", ""));
                    if (ChatColor.stripColor(text).trim().isEmpty() || text.length() > NameStyle.MAX_BADGE) {
                        throw new IllegalArgumentException(where + ".texte: the badge, " + NameStyle.MAX_BADGE
                                + " characters at most, colour codes included");
                    }
                }
                NameStyle style = new NameStyle(kind, id, name, text, one.getBoolean("gras", false), obtain,
                        one.getBoolean("anime", true), obtain == NameStyle.Obtain.POINTS ? price : 0);
                styles.put(style.key(), style);
            }
        }
        return styles;
    }

    // --- ELO ------------------------------------------------------------------------------------

    private static EloLadder readElo(ConfigurationSection section) {
        if (section == null) {
            return EloLadder.DEFAULT;
        }
        int bonus = count(section, "elo", "bonus-mvp", EloLadder.DEFAULT.mvpBonus());
        ConfigurationSection list = section.getConfigurationSection("divisions");
        if (list == null) {
            return new EloLadder(EloLadder.DEFAULT.divisions(), bonus);
        }
        List<EloLadder.Division> divisions = new ArrayList<>();
        for (String name : list.getKeys(false)) {
            String where = "elo.divisions." + name;
            ConfigurationSection division = list.getConfigurationSection(name);
            if (division == null || name.length() > 12) {
                throw new IllegalArgumentException(where + ": a section, the name 12 characters at most");
            }
            String colour = LobbySettings.colour(division.getString("couleur", "&7"));
            if (!LobbySettings.isColourCodes(colour)) {
                throw new IllegalArgumentException(where + ".couleur: one or two colour codes");
            }
            divisions.add(new EloLadder.Division(name, colour, count(division, where, "elo", 0),
                    count(division, where, "victoire", 20), count(division, where, "defaite", 20)));
        }
        try {
            return new EloLadder(divisions, bonus);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("elo.divisions: " + e.getMessage(), e);
        }
    }

    // --- Helpers --------------------------------------------------------------------------------

    private static int count(ConfigurationSection section, String where, String key, int fallback) {
        if (section == null) {
            return fallback;
        }
        int value = section.getInt(key, fallback);
        if (value < 0 || value > MAX_POINTS) {
            throw new IllegalArgumentException(where + "." + key + ": from 0 to " + MAX_POINTS);
        }
        return value;
    }

    private static int level(ConfigurationSection section, String where, int fallback) {
        int value = section.getInt("niveau", fallback);
        if (value < 0 || value > NetworkLevel.MAX_LEVEL) {
            throw new IllegalArgumentException(where + ".niveau: from 0 to " + NetworkLevel.MAX_LEVEL);
        }
        return value;
    }

    /** A list of lines (or one line) of config.yml, coloured; the fallback when missing. */
    private static List<String> lines(ConfigurationSection config, String path, List<String> fallback, int max) {
        if (!config.contains(path)) {
            return fallback;
        }
        List<String> lines = new ArrayList<>();
        if (config.isList(path)) {
            for (String line : config.getStringList(path)) {
                lines.add(LobbySettings.colour(line));
            }
        } else {
            lines.add(LobbySettings.colour(config.getString(path, "")));
        }
        if (lines.size() > max) {
            throw new IllegalArgumentException(path + ": " + max + " lines at most");
        }
        return lines;
    }

    private static List<String> defaultHeader() {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.AQUA + "" + ChatColor.BOLD + "RAGO " + ChatColor.WHITE + ChatColor.BOLD + "PRACTICE");
        lines.add(ChatColor.GRAY + "{joueurs} players online");
        return lines;
    }

    private static List<String> defaultFooter() {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GRAY + "Your ping: {ping} ms");
        lines.add(ChatColor.AQUA + "rago.gg");
        return lines;
    }

    private static List<String> defaultMotd() {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.AQUA + "" + ChatColor.BOLD + "RAGO PRACTICE " + ChatColor.GRAY + "- " + ChatColor.WHITE
                + "1.8 PvP & Ranked Bedwars");
        lines.add(ChatColor.YELLOW + "Ranks, shop and ranked ELO divisions!");
        return lines;
    }

    private static List<String> defaultAnnouncements() {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.AQUA + "Earn Rago Points in every game and spend them in the shop (/shop).");
        lines.add(ChatColor.AQUA + "Claim your daily reward with /daily: a few days in a row give more.");
        lines.add(ChatColor.AQUA + "Add your friends with /friend add <name>, and play with them in a /party.");
        lines.add(ChatColor.AQUA + "Climb the ranked divisions, from Coal to Obsidian: /top elo.");
        return lines;
    }

    private static List<String> defaultRules() {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Rules");
        lines.add(ChatColor.GRAY + "1. No cheats, macros or unfair mods.");
        lines.add(ChatColor.GRAY + "2. No insults, spam or advertising.");
        lines.add(ChatColor.GRAY + "3. No teaming in solo modes, no boosting.");
        lines.add(ChatColor.GRAY + "4. Respect the staff: their word is final.");
        return lines;
    }

    // --- Getters --------------------------------------------------------------------------------

    Ranks ranks() {
        return ranks;
    }

    LevelRewards levelRewards() {
        return levelRewards;
    }

    Quests quests() {
        return quests;
    }

    /** Every colour of name and badge, by key ("couleur-rose", "badge-rago"), the colours first. */
    Map<String, NameStyle> styles() {
        return styles;
    }

    /** The colour of name or the badge with this id, or null. */
    NameStyle style(NameStyle.Kind kind, String id) {
        return id == null ? null : styles.get(NameStyle.key(kind, id));
    }

    List<NameStyle> styles(NameStyle.Kind kind) {
        List<NameStyle> list = new ArrayList<>();
        for (NameStyle style : styles.values()) {
            if (style.kind() == kind) {
                list.add(style);
            }
        }
        return list;
    }

    /** The ranked season now (saison: in config.yml). */
    Season season() {
        return season;
    }

    EloLadder elo() {
        return elo;
    }

    Points points() {
        return points;
    }

    /** Every cosmetic of the shop, by id, in its order. */
    Map<String, Cosmetic> cosmetics() {
        return cosmetics;
    }

    List<Cosmetic> cosmetics(Cosmetic.Type type) {
        List<Cosmetic> list = new ArrayList<>();
        for (Cosmetic cosmetic : cosmetics.values()) {
            if (cosmetic.type() == type) {
                list.add(cosmetic);
            }
        }
        return list;
    }

    long chatCooldownMillis() {
        return chatCooldownMillis;
    }

    boolean blockRepeats() {
        return blockRepeats;
    }

    List<String> tabHeader() {
        return tabHeader;
    }

    List<String> tabFooter() {
        return tabFooter;
    }

    List<String> motd() {
        return motd;
    }

    int announceMinutes() {
        return announceMinutes;
    }

    List<String> announcements() {
        return announcements;
    }

    /** The starter pack of the web store (pack-depart). */
    StarterPack starterPack() {
        return starterPack;
    }

    /** The lines of /discord, /site, /rules or /store, or null when config.yml has none. */
    List<String> link(String key) {
        return links.get(key);
    }
}
