package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    private final List<String> modes;
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

    private BedwarsSettings(List<String> modes, int maxGames, boolean demo, int respawnSeconds, Rate iron, Rate gold,
            List<Rate> diamond, List<Rate> emerald, int[] events) {
        this.modes = Collections.unmodifiableList(modes);
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
    }

    /** The settings of config.yml. Throws IllegalArgumentException naming the wrong setting. */
    static BedwarsSettings read(ConfigurationSection config) {
        List<String> modes = new ArrayList<>();
        for (String mode : config.getStringList("modes")) {
            String id = mode.trim();
            if (!id.matches("[a-z0-9-]{1,32}")) {
                throw new IllegalArgumentException("modes: \"" + mode + "\" is not a mode id of the lobby");
            }
            modes.add(id);
        }
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
        return new BedwarsSettings(modes, maxGames, config.getBoolean("carte-demo", true), respawn, iron, gold, diamond,
                emerald, events);
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
        return modes;
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
}
