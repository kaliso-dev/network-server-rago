package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/** The settings of config.yml, checked. Immutable. */
final class BedwarsSettings {

    /**
     * A generator: one item every so many ticks on average, at most so many lying on it. They drop
     * in batches of one of these sizes, drawn at random (the iron of a forge: 2 or 4 at once, as on
     * Hypixel), the next batch waiting as many times the ticks.
     */
    static final class Rate {
        final int ticks;
        final int max;
        final int[] batches;

        Rate(int ticks, int max) {
            this(ticks, max, new int[] {1});
        }

        Rate(int ticks, int max, int[] batches) {
            this.ticks = ticks;
            this.max = max;
            this.batches = batches.clone();
        }
    }

    /** The speed of an island forge, as on Hypixel: its number of iron nodes. */
    enum Speed {
        SLOW(1, "lente"), MEDIUM(2, "moyenne"), FAST(3, "rapide");

        final int nodes;
        final String word;

        Speed(int nodes, String word) {
            this.nodes = nodes;
            this.word = word;
        }

        /** lente, moyenne or rapide (or slow, medium, fast); null for another word. */
        static Speed of(String word) {
            String key = word.trim().toLowerCase(java.util.Locale.ROOT);
            for (Speed speed : values()) {
                if (speed.word.equals(key) || speed.name().toLowerCase(java.util.Locale.ROOT).equals(key)) {
                    return speed;
                }
            }
            return null;
        }
    }

    /**
     * The island forges, as on Hypixel: each iron node drops an ingot every so many seconds; a forge
     * has the nodes of its map's speed, one more on a map of four teams or fewer; the iron comes in
     * batches (2 or 4 ingots at once); gold at its own pace, faster on a map of four teams or fewer;
     * twice as fast at the start of the game.
     */
    static final class Forge {
        final double nodeSeconds;
        final double goldSeconds;
        final double fewTeamsGoldSeconds;
        final int maxIron;
        final int maxGold;
        final int fastStartSeconds;
        final int[] ironBatches;
        final Speed fallback;
        /** By map name in lower case: the speed in solo, then with teams of two or more. */
        final Map<String, Speed[]> maps;
        /** By mode id: what its forges have instead, on every map. */
        final Map<String, ModeForge> modes;

        Forge(double nodeSeconds, double goldSeconds, int maxIron, int maxGold, int fastStartSeconds, Speed fallback,
                Map<String, Speed[]> maps) {
            this(nodeSeconds, goldSeconds, goldSeconds, maxIron, maxGold, fastStartSeconds, new int[] {1}, fallback, maps,
                    Collections.<String, ModeForge>emptyMap());
        }

        Forge(double nodeSeconds, double goldSeconds, double fewTeamsGoldSeconds, int maxIron, int maxGold,
                int fastStartSeconds, int[] ironBatches, Speed fallback, Map<String, Speed[]> maps,
                Map<String, ModeForge> modes) {
            this.nodeSeconds = nodeSeconds;
            this.goldSeconds = goldSeconds;
            this.fewTeamsGoldSeconds = fewTeamsGoldSeconds;
            this.maxIron = maxIron;
            this.maxGold = maxGold;
            this.fastStartSeconds = fastStartSeconds;
            this.ironBatches = ironBatches.clone();
            this.fallback = fallback;
            this.maps = Collections.unmodifiableMap(maps);
            this.modes = Collections.unmodifiableMap(modes);
        }

        /**
         * The forges of a game of this mode: those of forge.modes for it (one speed on every map,
         * their gold), else the maps' own.
         */
        Forge forMode(String modeId) {
            ModeForge mode = modes.get(modeId);
            if (mode == null) {
                return this;
            }
            double gold = mode.goldSeconds > 0 ? mode.goldSeconds : goldSeconds;
            double fewTeamsGold = mode.goldSeconds > 0 ? mode.goldSeconds : fewTeamsGoldSeconds;
            return new Forge(nodeSeconds, gold, fewTeamsGold, maxIron, maxGold, fastStartSeconds, ironBatches,
                    mode.speed != null ? mode.speed : fallback,
                    mode.speed != null ? Collections.<String, Speed[]>emptyMap() : maps, Collections.<String, ModeForge>emptyMap());
        }

        /** The speed of this map's forge for teams of this many players. */
        Speed speed(String map, int teamSize) {
            Speed[] speeds = maps.get(map.toLowerCase(java.util.Locale.ROOT));
            return speeds == null ? fallback : speeds[teamSize <= 1 ? 0 : 1];
        }

        /** The iron of an island of this map, for a game of teams of this many players. */
        Rate iron(String map, int teamSize, int islands) {
            int nodes = speed(map, teamSize).nodes + (islands <= 4 ? 1 : 0);
            return new Rate(Math.max(1, (int) Math.round(nodeSeconds * 20 / nodes)), maxIron, ironBatches);
        }

        /** The gold of an island of a map of this many islands. */
        Rate gold(int islands) {
            double seconds = islands <= 4 ? fewTeamsGoldSeconds : goldSeconds;
            return new Rate(Math.max(1, (int) Math.round(seconds * 20)), maxGold);
        }
    }

    /** What the forges of a mode have on every map: a speed (null: each map's own), its gold (0: the usual). */
    static final class ModeForge {
        final Speed speed;
        final double goldSeconds;

        ModeForge(Speed speed, double goldSeconds) {
            this.speed = speed;
            this.goldSeconds = goldSeconds;
        }
    }

    /**
     * The fireball, as on Hypixel (the values of BedWars1058, the open copy of it): its speed at the
     * throw, in blocks a tick; the push of its blast on the players around it (within radius blocks
     * on each axis), away from the blast and upwards, which replaces the blast's own; its fixed
     * damage, armour or not, in half hearts.
     */
    static final class Fireball {
        final double speed;
        final double radius;
        final double horizontal;
        final double vertical;
        final double self;
        final double enemies;
        final double teammates;

        Fireball(double speed, double radius, double horizontal, double vertical, double self, double enemies,
                double teammates) {
            this.speed = speed;
            this.radius = radius;
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.self = self;
            this.enemies = enemies;
            this.teammates = teammates;
        }

        /**
         * The velocity the blast at (bx, by, bz) gives a player standing at (px, py, pz), as
         * BedWars1058 computes it: away from the blast horizontally, and up; more up when the blast
         * is under the player's feet (a fireball jump).
         */
        double[] push(double bx, double by, double bz, double px, double py, double pz) {
            double dx = bx - px;
            double dy = by - py;
            double dz = bz - pz;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1e-6) {
                dx = 0;
                dy = 1;
                dz = 0;
                length = 1;
            }
            double nx = dx / length;
            double ny = dy / length;
            double nz = dz / length;
            double y = ny < 0 ? ny + 1.5 : ny;
            y = y <= 0.5 ? vertical * 1.5 : y * vertical * 1.5;
            return new double[] {-nx * horizontal, y, -nz * horizontal};
        }

        /** True if a player at this offset from the blast is pushed by it. */
        boolean reaches(double dx, double dy, double dz) {
            return Math.abs(dx) <= radius && Math.abs(dy) <= radius && Math.abs(dz) <= radius;
        }
    }

    /** The kind of game a mode plays (jeux: in config.yml). */
    enum Kind {
        /** A Bed Wars game: beds, forges, shops, until one team is left. */
        CLASSIC("classique"),
        /** Free for all with the mode's kit, no bed, back at once after a death. */
        FFA("ffa"),
        /** Free for all with the forges, diamonds, emeralds and a shop of its own, and defenses ready for top fights. */
        ARENA("arene"),
        /** Invis practice: 2 to 6 players, rounds of 2 minutes, one invisible against the others. */
        INVIS("invis"),
        /** Fast Shop: lists of items to buy as fast as possible, with the player's own quick buy. */
        FAST_SHOP("fast-shop"),
        /**
         * Top Bridge: the fight on the top bridge alone, no bed, no forge; rounds of one life to 5
         * points, or a practice without end in a party's private game.
         */
        TOP_BRIDGE("top-bridge"),
        /**
         * Attack and Defense: a map of one base and the attackers' bridge (attaquants: in its
         * bedwars.yml); two rounds, each side attacks once, as in a game.
         */
        ATTACK_DEFENSE("attaque-defense"),
        /**
         * Clutch Scenario: a real game where one team has lost its bed already, the other keeps it
         * under a ranked defense; iron armour, a stone sword and full forges from the start.
         */
        CLUTCH_SCENARIO("clutch-scenario"),
        /**
         * Bed Fight, as on Minemen: two islands, each bed under the defense of the map, no forge
         * nor shop, the mode's kit at each life; back while the bed stands, the last team wins.
         */
        BED_FIGHT("bed-fight"),
        /**
         * Top Bed Fight: Top Bridge's rounds of one life to 5 points on the bridge a map has on top,
         * the spawns of the map at its ends, in leather armour with the mode's kit.
         */
        TOP_BED_FIGHT("top-bed-fight");

        final String word;

        Kind(String word) {
            this.word = word;
        }

        static Kind of(String word) {
            String key = word.trim().toLowerCase(java.util.Locale.ROOT);
            for (Kind kind : values()) {
                if (kind.word.equals(key)) {
                    return kind;
                }
            }
            return null;
        }

        static String words() {
            List<String> words = new ArrayList<>();
            for (Kind kind : values()) {
                words.add(kind.word);
            }
            return String.join(", ", words);
        }
    }

    /** The modes this plugin plays, each with the names of its maps (empty: every map). */
    private final Map<String, List<String>> modes;
    /** The modes that are not a classic game, with their kind. */
    private final Map<String, Kind> kinds;
    private final int maxGames;
    private final boolean demo;
    private final int respawnSeconds;
    private final Forge forge;
    private final List<Rate> diamond;
    private final List<Rate> emerald;
    /** In seconds of game: tier II and III of the diamonds and the emeralds, the end of the beds, the end. */
    private final int diamondII;
    private final int emeraldII;
    private final int diamondIII;
    private final int emeraldIII;
    private final int bedsGone;
    private final int gameEnd;
    private final Fireball fireball;
    /** The modes whose teams play on neighbouring islands (First Rush), not opposite ones. */
    private final List<String> neighbourModes;
    /** The modes whose games have a top bridge (Top Bridge). */
    private final List<String> topBridgeModes;
    /** The modes whose games keep every forge of the map going, the empty islands' too. */
    private final List<String> allForgeModes;
    /** The kits of Attack and Defense to choose from, each side its own (attaque-defense:). */
    private final List<RoundKit> attackKits;
    private final List<RoundKit> defenseKits;

    private BedwarsSettings(Map<String, List<String>> modes, Map<String, Kind> kinds, int maxGames, boolean demo,
            int respawnSeconds, Forge forge, List<Rate> diamond, List<Rate> emerald, int[] events, Fireball fireball,
            List<String> neighbourModes, List<String> topBridgeModes, List<String> allForgeModes, List<RoundKit> attackKits,
            List<RoundKit> defenseKits) {
        this.modes = Collections.unmodifiableMap(modes);
        this.kinds = Collections.unmodifiableMap(kinds);
        this.maxGames = maxGames;
        this.demo = demo;
        this.respawnSeconds = respawnSeconds;
        this.forge = forge;
        this.diamond = Collections.unmodifiableList(diamond);
        this.emerald = Collections.unmodifiableList(emerald);
        this.diamondII = events[0];
        this.emeraldII = events[1];
        this.diamondIII = events[2];
        this.emeraldIII = events[3];
        this.bedsGone = events[4];
        this.gameEnd = events[5];
        this.fireball = fireball;
        this.neighbourModes = Collections.unmodifiableList(neighbourModes);
        this.topBridgeModes = Collections.unmodifiableList(topBridgeModes);
        this.allForgeModes = Collections.unmodifiableList(allForgeModes);
        this.attackKits = attackKits;
        this.defenseKits = defenseKits;
    }

    /** The word for every map, in the maps of a mode. */
    static final String EVERY_MAP = "toutes";

    /** The settings of config.yml. Throws IllegalArgumentException naming the wrong setting. */
    static BedwarsSettings read(ConfigurationSection config) {
        Map<String, List<String>> modes = modes(config);
        int maxGames = config.getInt("parties-max", 8);
        if (maxGames < 1 || maxGames > 50) {
            throw new IllegalArgumentException("parties-max: from 1 to 50");
        }
        int respawn = config.getInt("reapparition-secondes", 5);
        if (respawn < 0 || respawn > 30) {
            throw new IllegalArgumentException("reapparition-secondes: from 0 to 30");
        }
        Forge forge = forge(config);
        List<Rate> diamond = tiers(config, "generateurs.diamant", new double[] {30, 23, 12}, 4);
        List<Rate> emerald = tiers(config, "generateurs.emeraude", new double[] {65, 50, 35}, 2);
        String[] keys = {"diamant-2", "emeraude-2", "diamant-3", "emeraude-3", "fin-des-lits", "fin-de-partie"};
        double[] defaults = {6, 12, 18, 24, 30, 45};
        int[] events = new int[keys.length];
        int previous = 0;
        for (int i = 0; i < keys.length; i++) {
            String path = "evenements." + keys[i];
            double minutes = config.getDouble(path, defaults[i]);
            events[i] = (int) Math.round(minutes * 60);
            if (events[i] <= previous || minutes > 180) {
                throw new IllegalArgumentException(path + ": in minutes of game, after the event above and 180 at most");
            }
            previous = events[i];
        }
        Fireball fireball = new Fireball(number(config, "boule-de-feu.vitesse", 1.0, 0.1, 5),
                number(config, "boule-de-feu.rayon", 3, 1, 8),
                number(config, "boule-de-feu.recul-horizontal", 1.0, 0, 5),
                number(config, "boule-de-feu.recul-vertical", 0.65, 0, 5),
                damage(config, "boule-de-feu.degats-sur-soi", 2.0),
                damage(config, "boule-de-feu.degats-sur-les-autres", 2.0),
                damage(config, "boule-de-feu.degats-sur-les-coequipiers", 0.0));
        List<String> neighbours = new ArrayList<>();
        List<String> listed = config.contains("iles-voisines") ? config.getStringList("iles-voisines")
                : Arrays.asList("first-rush", "first-rush-duo");
        for (String mode : listed) {
            neighbours.add(modeId(mode, "iles-voisines"));
        }
        List<String> topBridge = new ArrayList<>();
        for (String mode : config.getStringList("top-bridge")) {
            topBridge.add(modeId(mode, "top-bridge"));
        }
        List<String> allForges = new ArrayList<>();
        for (String mode : config.getStringList("forges-partout")) {
            allForges.add(modeId(mode, "forges-partout"));
        }
        List<RoundKit> attackKits = RoundKit.read(config.getList("attaque-defense.kits-attaque"),
                "attaque-defense.kits-attaque", RoundKit.ATTACK);
        List<RoundKit> defenseKits = RoundKit.read(config.getList("attaque-defense.kits-defense"),
                "attaque-defense.kits-defense", RoundKit.DEFENSE);
        return new BedwarsSettings(modes, kinds(config), maxGames, config.getBoolean("carte-demo", true), respawn, forge,
                diamond, emerald, events, fireball, neighbours, topBridge, allForges, attackKits, defenseKits);
    }

    /** jeux: the modes that are not a classic game, each with its kind. */
    private static Map<String, Kind> kinds(ConfigurationSection config) {
        Map<String, Kind> kinds = new LinkedHashMap<>();
        if (!config.contains("jeux")) {
            return kinds;
        }
        if (!config.isConfigurationSection("jeux")) {
            throw new IllegalArgumentException("jeux: each mode with its kind of game, such as ffa: ffa");
        }
        ConfigurationSection section = config.getConfigurationSection("jeux");
        for (String key : section.getKeys(false)) {
            Kind kind = Kind.of(String.valueOf(section.get(key)));
            if (kind == null) {
                throw new IllegalArgumentException("jeux." + key + ": " + Kind.words());
            }
            kinds.put(modeId(key, "jeux"), kind);
        }
        return kinds;
    }

    /**
     * modes: each mode with its maps (a list of names, or toutes), or the list of the first version
     * (each mode on every map).
     */
    private static Map<String, List<String>> modes(ConfigurationSection config) {
        Map<String, List<String>> modes = new LinkedHashMap<>();
        if (!config.isConfigurationSection("modes")) {
            for (String mode : config.getStringList("modes")) {
                modes.put(modeId(mode, "modes"), new ArrayList<String>());
            }
            return modes;
        }
        ConfigurationSection section = config.getConfigurationSection("modes");
        for (String key : section.getKeys(false)) {
            String id = modeId(key, "modes");
            String path = "modes." + key;
            List<String> maps = new ArrayList<>();
            if (section.isList(key)) {
                for (String map : section.getStringList(key)) {
                    String name = map.trim();
                    if (!name.matches("[A-Za-z0-9_-]{1,64}")) {
                        throw new IllegalArgumentException(path + ": \"" + map + "\" is not a map (the name of its world folder)");
                    }
                    maps.add(name);
                }
                if (maps.isEmpty()) {
                    throw new IllegalArgumentException(path + ": at least one map, or " + EVERY_MAP);
                }
            } else if (!EVERY_MAP.equalsIgnoreCase(String.valueOf(section.get(key)).trim())) {
                throw new IllegalArgumentException(path + ": a list of maps, such as [Gelato, Nebuc], or " + EVERY_MAP);
            }
            modes.put(id, maps);
        }
        return modes;
    }

    private static String modeId(String mode, String path) {
        String id = mode.trim();
        if (!id.matches("[a-z0-9-]{1,32}")) {
            throw new IllegalArgumentException(path + ": \"" + mode + "\" is not a mode id of the lobby");
        }
        return id;
    }

    /** forge: the island forges; their speed on each map (lente, moyenne, rapide, or one for solo and one for teams). */
    private static Forge forge(ConfigurationSection config) {
        double nodeSeconds = number(config, "forge.fer-secondes-par-noeud", 2.0, 0.25, 600);
        double goldSeconds = number(config, "forge.or-secondes", 7.5, 0.25, 600);
        double fewTeamsGold = number(config, "forge.or-secondes-4-equipes", 5.0, 0.25, 600);
        int maxIron = (int) number(config, "forge.max-fer", 48, 1, 256);
        int maxGold = (int) number(config, "forge.max-or", 16, 1, 256);
        int fastStart = (int) number(config, "forge.debut-rapide-secondes", 30, 0, 600);
        Speed fallback = speed(config.getString("forge.vitesse-par-defaut", "rapide"), "forge.vitesse-par-defaut");
        Map<String, Speed[]> maps = new LinkedHashMap<>();
        if (!config.contains("forge.vitesses")) {
            maps.put("lighthouse", new Speed[] {Speed.SLOW, Speed.MEDIUM});
            maps.put("lotus", new Speed[] {Speed.SLOW, Speed.MEDIUM});
        } else if (config.isConfigurationSection("forge.vitesses")) {
            ConfigurationSection section = config.getConfigurationSection("forge.vitesses");
            for (String map : section.getKeys(false)) {
                String path = "forge.vitesses." + map;
                Speed[] speeds;
                if (section.isConfigurationSection(map)) {
                    speeds = new Speed[] {speed(section.getString(map + ".solo", ""), path + ".solo"),
                        speed(section.getString(map + ".equipes", ""), path + ".equipes")};
                } else {
                    Speed one = speed(String.valueOf(section.get(map)), path);
                    speeds = new Speed[] {one, one};
                }
                maps.put(map.toLowerCase(java.util.Locale.ROOT), speeds);
            }
        } else {
            throw new IllegalArgumentException("forge.vitesses: each map with its speed, such as Lotus: lente");
        }
        int[] batches = batches(config, "forge.fer-par-lot");
        Map<String, ModeForge> modes = new LinkedHashMap<>();
        if (config.isConfigurationSection("forge.modes")) {
            ConfigurationSection section = config.getConfigurationSection("forge.modes");
            for (String mode : section.getKeys(false)) {
                String path = "forge.modes." + mode;
                if (!section.isConfigurationSection(mode)) {
                    throw new IllegalArgumentException(path + ": vitesse and or-secondes, such as {vitesse: moyenne, or-secondes: 7.5}");
                }
                String word = section.getString(mode + ".vitesse", "");
                Speed speed = word.trim().isEmpty() ? null : speed(word, path + ".vitesse");
                double gold = section.contains(mode + ".or-secondes") ? number(config, path + ".or-secondes", 0, 0.25, 600) : 0;
                modes.put(modeId(mode, path), new ModeForge(speed, gold));
            }
        } else if (config.contains("forge.modes")) {
            throw new IllegalArgumentException("forge.modes: each mode with its forge, such as ranked-1v1: {vitesse: moyenne}");
        }
        return new Forge(nodeSeconds, goldSeconds, fewTeamsGold, maxIron, maxGold, fastStart, batches, fallback, maps, modes);
    }

    /** The sizes of the batches of iron (2 and 4 by default), each from 1 to 16. */
    private static int[] batches(ConfigurationSection config, String path) {
        if (!config.contains(path)) {
            return new int[] {2, 4};
        }
        List<?> list = config.getList(path);
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException(path + ": a list of how many ingots drop at once, such as [2, 4]");
        }
        int[] sizes = new int[list.size()];
        for (int i = 0; i < sizes.length; i++) {
            Object each = list.get(i);
            if (!(each instanceof Number) || ((Number) each).intValue() < 1 || ((Number) each).intValue() > 16) {
                throw new IllegalArgumentException(path + ": each from 1 to 16 ingots");
            }
            sizes[i] = ((Number) each).intValue();
        }
        return sizes;
    }

    private static Speed speed(String word, String path) {
        Speed speed = Speed.of(word);
        if (speed == null) {
            throw new IllegalArgumentException(path + ": lente, moyenne or rapide");
        }
        return speed;
    }

    private static double number(ConfigurationSection config, String path, double fallback, double min, double max) {
        double value = config.getDouble(path, fallback);
        if (value < min || value > max) {
            throw new IllegalArgumentException(path + ": from " + plain(min) + " to " + plain(max));
        }
        return value;
    }

    private static String plain(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static double damage(ConfigurationSection config, String path, double fallback) {
        double damage = config.getDouble(path, fallback);
        if (damage < 0 || damage > 20) {
            throw new IllegalArgumentException(path + ": in half hearts, from 0 to 20");
        }
        return damage;
    }

    private static List<Rate> tiers(ConfigurationSection config, String path, double[] seconds, int max) {
        List<Double> given = config.contains(path + ".secondes") ? config.getDoubleList(path + ".secondes") : null;
        if (given != null && given.size() != 3) {
            throw new IllegalArgumentException(path + ".secondes: three numbers, for tiers I, II and III");
        }
        int most = config.getInt(path + ".max", max);
        if (most < 1 || most > 256) {
            throw new IllegalArgumentException(path + ".max: from 1 to 256");
        }
        List<Rate> tiers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double every = given == null ? seconds[i] : given.get(i);
            if (every < 1 || every > 600) {
                throw new IllegalArgumentException(path + ".secondes: from 1 to 600 each");
            }
            tiers.add(new Rate((int) Math.round(every * 20), most));
        }
        return tiers;
    }

    /** The lobby modes this plugin plays. */
    List<String> modes() {
        return new ArrayList<>(modes.keySet());
    }

    /** The kind of game of this mode: a classic Bed Wars game unless jeux: says otherwise. */
    Kind kind(String modeId) {
        Kind kind = kinds.get(modeId);
        return kind == null ? Kind.CLASSIC : kind;
    }

    /** The names of this mode's maps, in the order given; empty for every map. */
    List<String> mapsOf(String modeId) {
        List<String> maps = modes.get(modeId);
        return maps == null ? Collections.<String>emptyList() : Collections.unmodifiableList(maps);
    }

    /** How many game worlds the server keeps at most, so how many games at once. */
    int maxGames() {
        return maxGames;
    }

    /** True to create the demo map when it does not exist. */
    boolean demo() {
        return demo;
    }

    int respawnSeconds() {
        return respawnSeconds;
    }

    /** The island forges. */
    Forge forge() {
        return forge;
    }

    /** Tier I, II and III. */
    List<Rate> diamond() {
        return diamond;
    }

    List<Rate> emerald() {
        return emerald;
    }

    int diamondII() {
        return diamondII;
    }

    int emeraldII() {
        return emeraldII;
    }

    int diamondIII() {
        return diamondIII;
    }

    int emeraldIII() {
        return emeraldIII;
    }

    int bedsGone() {
        return bedsGone;
    }

    int gameEnd() {
        return gameEnd;
    }

    Fireball fireball() {
        return fireball;
    }

    /** True if the teams of this mode play on neighbouring islands (First Rush), not opposite ones. */
    boolean neighbours(String modeId) {
        return neighbourModes.contains(modeId);
    }

    /** True if the games of this mode keep every forge of the map going, the empty islands' too. */
    boolean allForges(String modeId) {
        return allForgeModes.contains(modeId);
    }

    /** True if the games of this mode have a top bridge over the middle (see {@link TopBridge}). */
    boolean topBridge(String modeId) {
        return topBridgeModes.contains(modeId);
    }

    /** The kits of the attackers (attack: true) or of the defenders of Attack and Defense. */
    List<RoundKit> roundKits(boolean attack) {
        return attack ? attackKits : defenseKits;
    }
}
