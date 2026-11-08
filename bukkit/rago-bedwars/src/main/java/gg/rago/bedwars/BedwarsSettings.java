package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/** The settings of config.yml, checked. Immutable. */
final class BedwarsSettings {

    /** A generator: one item every so many ticks, at most so many lying on it. */
    static final class Rate {
        final int ticks;
        final int max;

        Rate(int ticks, int max) {
            this.ticks = ticks;
            this.max = max;
        }
    }

    /** The modes this plugin plays, each with the names of its maps (empty: every map). */
    private final Map<String, List<String>> modes;
    private final int maxGames;
    private final boolean demo;
    private final int respawnSeconds;
    private final Rate iron;
    private final Rate gold;
    private final List<Rate> diamond;
    private final List<Rate> emerald;
    /** In seconds of game: tier II and III of the diamonds and the emeralds, the end of the beds, the end. */
    private final int diamondII;
    private final int emeraldII;
    private final int diamondIII;
    private final int emeraldIII;
    private final int bedsGone;
    private final int gameEnd;
    /** The damage of a fireball's blast, before the armour: on its thrower, on the other players. */
    private final double fireballSelf;
    private final double fireballOthers;
    /** The modes of one team: the layers of the defense around the bed they rush, and their time limit. */
    private final List<Material> rushDefense;
    private final int rushSeconds;

    private BedwarsSettings(Map<String, List<String>> modes, int maxGames, boolean demo, int respawnSeconds, Rate iron,
            Rate gold, List<Rate> diamond, List<Rate> emerald, int[] events, double[] fireball, List<Material> rushDefense,
            int rushSeconds) {
        this.modes = Collections.unmodifiableMap(modes);
        this.maxGames = maxGames;
        this.demo = demo;
        this.respawnSeconds = respawnSeconds;
        this.iron = iron;
        this.gold = gold;
        this.diamond = Collections.unmodifiableList(diamond);
        this.emerald = Collections.unmodifiableList(emerald);
        this.diamondII = events[0];
        this.emeraldII = events[1];
        this.diamondIII = events[2];
        this.emeraldIII = events[3];
        this.bedsGone = events[4];
        this.gameEnd = events[5];
        this.fireballSelf = fireball[0];
        this.fireballOthers = fireball[1];
        this.rushDefense = Collections.unmodifiableList(rushDefense);
        this.rushSeconds = rushSeconds;
    }

    /** The word for every map, in the maps of a mode. */
    static final String EVERY_MAP = "toutes";
    /** At most this many layers of defense around the bed of a rush. */
    static final int MAX_DEFENSE = 4;

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
        Rate iron = rate(config, "generateurs.fer", 1.0, 48);
        Rate gold = rate(config, "generateurs.or", 6.0, 16);
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
        double[] fireball = {damage(config, "boule-de-feu.degats-sur-soi", 2.0),
            damage(config, "boule-de-feu.degats-sur-les-autres", 3.0)};
        List<Material> defense = new ArrayList<>();
        List<String> layers = config.contains("premier-rush.defense") ? config.getStringList("premier-rush.defense")
                : Collections.singletonList("WOOL");
        for (String name : layers) {
            Material block = block(name);
            if (block == null || !block.isBlock() || !block.isSolid()) {
                throw new IllegalArgumentException("premier-rush.defense: \"" + name + "\" is not a solid block, "
                        + "such as WOOL or END_STONE");
            }
            defense.add(block);
        }
        if (defense.size() > MAX_DEFENSE) {
            throw new IllegalArgumentException("premier-rush.defense: " + MAX_DEFENSE + " layers at most");
        }
        double rushMinutes = config.getDouble("premier-rush.minutes", 5);
        if (rushMinutes < 0.5 || rushMinutes > 60) {
            throw new IllegalArgumentException("premier-rush.minutes: from 0.5 to 60");
        }
        return new BedwarsSettings(modes, maxGames, config.getBoolean("carte-demo", true), respawn, iron, gold, diamond,
                emerald, events, fireball, defense, (int) Math.round(rushMinutes * 60));
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

    /** A block by its 1.8 name, or by the names players know: END_STONE (ENDER_STONE in 1.8), PLANKS (WOOD). */
    static Material block(String name) {
        String key = name.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        if (key.equals("END_STONE") || key.equals("ENDSTONE")) {
            return Material.ENDER_STONE;
        }
        if (key.equals("PLANKS")) {
            return Material.WOOD;
        }
        return Material.matchMaterial(key);
    }

    private static String modeId(String mode, String path) {
        String id = mode.trim();
        if (!id.matches("[a-z0-9-]{1,32}")) {
            throw new IllegalArgumentException(path + ": \"" + mode + "\" is not a mode id of the lobby");
        }
        return id;
    }

    private static double damage(ConfigurationSection config, String path, double fallback) {
        double damage = config.getDouble(path, fallback);
        if (damage < 0 || damage > 20) {
            throw new IllegalArgumentException(path + ": in half hearts, from 0 to 20");
        }
        return damage;
    }

    private static Rate rate(ConfigurationSection config, String path, double seconds, int max) {
        double every = config.getDouble(path + ".secondes", seconds);
        int most = config.getInt(path + ".max", max);
        if (every < 0.25 || every > 600) {
            throw new IllegalArgumentException(path + ".secondes: from 0.25 to 600");
        }
        if (most < 1 || most > 256) {
            throw new IllegalArgumentException(path + ".max: from 1 to 256");
        }
        return new Rate((int) Math.round(every * 20), most);
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

    Rate iron() {
        return iron;
    }

    Rate gold() {
        return gold;
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

    /** The damage of a fireball's blast on its thrower, in half hearts before the armour. */
    double fireballSelf() {
        return fireballSelf;
    }

    /** The damage of a fireball on the other players, in half hearts before the armour. */
    double fireballOthers() {
        return fireballOthers;
    }

    /** The layers of blocks around the bed a team of one rushes, from the bed outwards (WOOL: the island's colour). */
    List<Material> rushDefense() {
        return rushDefense;
    }

    /** The time limit of a rush, in seconds. */
    int rushSeconds() {
        return rushSeconds;
    }
}
