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
 * What makes the lobby a network, from config.yml: the ranks (rangs), the ELO divisions (elo), the
 * Rago Points (rago-points), the prices of the shop (boutique), the chat rules (chat), the tab
 * list (tab), the server list message (motd), the announcements (annonces) and the links (liens).
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

        static final int MAX_STREAK = 7;

        Points(int win, int loss, int kill, int perLevel, int daily, int dailyStreak) {
            this.win = win;
            this.loss = loss;
            this.kill = kill;
            this.perLevel = perLevel;
            this.daily = daily;
            this.dailyStreak = dailyStreak;
        }

        /** The reward of the n-th day in a row (1 for the first). */
        int dailyReward(int day) {
            return daily + dailyStreak * (Math.max(1, Math.min(MAX_STREAK, day)) - 1);
        }
    }

    static final int MAX_POINTS = 100_000_000;

    private final Ranks ranks;
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

    private ServerSettings(Ranks ranks, EloLadder elo, Points points, Map<String, Cosmetic> cosmetics, long chatCooldownMillis,
            boolean blockRepeats, List<String> tabHeader, List<String> tabFooter, List<String> motd, int announceMinutes,
            List<String> announcements, Map<String, List<String>> links) {
        this.ranks = ranks;
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
    }

    static ServerSettings load(ConfigurationSection config) {
        Ranks ranks = readRanks(config.getConfigurationSection("rangs"));
        EloLadder elo = readElo(config.getConfigurationSection("elo"));
        ConfigurationSection p = config.getConfigurationSection("rago-points");
        Points points = new Points(count(p, "rago-points", "victoire", 20), count(p, "rago-points", "defaite", 5),
                count(p, "rago-points", "kill", 2), count(p, "rago-points", "par-niveau", 10),
                count(p, "rago-points", "quotidien", 100), count(p, "rago-points", "quotidien-serie", 25));
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
        return new ServerSettings(ranks, elo, points, cosmetics, cooldown * 1000L, repeats, header, footer, motd, minutes,
                announcements, links);
    }

    // --- Ranks ----------------------------------------------------------------------------------

    private static Ranks readRanks(ConfigurationSection section) {
        if (section == null) {
            return Ranks.defaults();
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
            if ((prefix + colour).length() > RankDef.MAX_PREFIX) {
                throw new IllegalArgumentException(where + ".prefixe: the prefix and the colour of the name are "
                        + RankDef.MAX_PREFIX + " characters at most, colour codes included");
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
            ranks.add(new RankDef(id, prefix, colour, chat, staff, permissions, parent, price, level, icon, perks));
        }
        return new Ranks(ranks);
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

    /** The lines of /discord, /site, /rules or /store, or null when config.yml has none. */
    List<String> link(String key) {
        return links.get(key);
    }
}
