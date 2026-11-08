package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * A Bed Wars map: a world of the server's world folder, with the points of the game kept in
 * {@value #FILE_NAME} inside the world's own folder, placed in game with /bw. Each team has a
 * spawn, a bed, a generator (iron and gold) and a shop, and may have a team upgrades NPC; the map
 * has diamond and emerald generators, and a spectator point. A map is played once it has two
 * complete teams or more and is enabled. Main thread only.
 */
final class BedwarsMap {

    static final String FILE_NAME = "bedwars.yml";
    static final int MIN_TEAMS = 2;
    /** Without hauteur-max, players build up to this many blocks above the highest spawn. */
    static final int AUTO_BUILD_ABOVE = 25;
    /** Without hauteur-vide, players die this many blocks below the lowest spawn. */
    static final int AUTO_VOID_BELOW = 30;

    /** The points of one team. Each is null until placed. */
    static final class Team {
        MapPoint spawn;
        MapPoint bed;
        MapPoint generator;
        MapPoint shop;
        MapPoint upgrades;

        /** What the team still needs to be played: "spawn", "bed"... */
        List<String> missing() {
            List<String> missing = new ArrayList<>();
            if (spawn == null) {
                missing.add("spawn");
            }
            if (bed == null) {
                missing.add("bed");
            }
            if (generator == null) {
                missing.add("generator");
            }
            if (shop == null) {
                missing.add("shop");
            }
            return missing;
        }
    }

    private final String world;
    private boolean enabled;
    /** 0: automatic. */
    private int buildLimit;
    /** Integer.MIN_VALUE: automatic. */
    private int voidY = Integer.MIN_VALUE;
    private final Map<TeamColor, Team> teams = new EnumMap<>(TeamColor.class);
    private final List<MapPoint> diamonds = new ArrayList<>();
    private final List<MapPoint> emeralds = new ArrayList<>();
    private MapPoint spectators;

    BedwarsMap(String world) {
        this.world = world;
    }

    /** The name of the map's world (its folder). */
    String world() {
        return world;
    }

    boolean enabled() {
        return enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** The team's points, created empty if the map had no such team. */
    Team team(TeamColor color) {
        return teams.computeIfAbsent(color, c -> new Team());
    }

    /** The teams of the map, in the order of {@link TeamColor}. */
    Map<TeamColor, Team> teams() {
        return Collections.unmodifiableMap(teams);
    }

    boolean removeTeam(TeamColor color) {
        return teams.remove(color) != null;
    }

    /** The teams that have everything a game needs, in the order of {@link TeamColor}. */
    List<TeamColor> completeTeams() {
        List<TeamColor> complete = new ArrayList<>();
        for (Map.Entry<TeamColor, Team> entry : teams.entrySet()) {
            if (entry.getValue().missing().isEmpty()) {
                complete.add(entry.getKey());
            }
        }
        return complete;
    }

    /** What the map still needs to be played, for /bw info: empty when it can be. */
    List<String> missing() {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<TeamColor, Team> entry : teams.entrySet()) {
            List<String> team = entry.getValue().missing();
            if (!team.isEmpty()) {
                missing.add(entry.getKey().id() + ": " + String.join(", ", team));
            }
        }
        if (completeTeams().size() < MIN_TEAMS) {
            missing.add("at least " + MIN_TEAMS + " complete teams (spawn, bed, generator and shop each)");
        }
        return missing;
    }

    /** True when it is enabled and has two complete teams or more. */
    boolean playable() {
        return enabled && completeTeams().size() >= MIN_TEAMS;
    }

    List<MapPoint> diamonds() {
        return diamonds;
    }

    List<MapPoint> emeralds() {
        return emeralds;
    }

    MapPoint spectators() {
        return spectators;
    }

    void setSpectators(MapPoint spectators) {
        this.spectators = spectators;
    }

    /** Where the dead and the eliminated look from: the spectator point, else above the middle of the spawns. */
    MapPoint spectatorPoint() {
        if (spectators != null) {
            return spectators;
        }
        double x = 0;
        double z = 0;
        double y = 0;
        int count = 0;
        for (Team team : teams.values()) {
            if (team.spawn != null) {
                x += team.spawn.x();
                y = Math.max(y, team.spawn.y());
                z += team.spawn.z();
                count++;
            }
        }
        return count == 0 ? new MapPoint(0.5, 100, 0.5, 0f, 90f)
                : new MapPoint(x / count, y + 15, z / count, 0f, 90f);
    }

    /** The highest block players may place: hauteur-max, else 25 above the highest spawn. */
    int buildLimit() {
        if (buildLimit > 0) {
            return buildLimit;
        }
        int highest = 0;
        for (Team team : teams.values()) {
            if (team.spawn != null) {
                highest = Math.max(highest, team.spawn.blockY());
            }
        }
        return Math.min(255, highest + AUTO_BUILD_ABOVE);
    }

    void setBuildLimit(int buildLimit) {
        this.buildLimit = buildLimit;
    }

    /** Below this height players die: hauteur-vide, else 30 below the lowest spawn. */
    int voidY() {
        if (voidY != Integer.MIN_VALUE) {
            return voidY;
        }
        int lowest = 255;
        for (Team team : teams.values()) {
            if (team.spawn != null) {
                lowest = Math.min(lowest, team.spawn.blockY());
            }
        }
        return lowest - AUTO_VOID_BELOW;
    }

    void setVoidY(int voidY) {
        this.voidY = voidY;
    }

    boolean hasBuildLimit() {
        return buildLimit > 0;
    }

    boolean hasVoidY() {
        return voidY != Integer.MIN_VALUE;
    }

    /** A copy, for a game: what the map was when the game's world was copied. */
    BedwarsMap copy() {
        try {
            return fromYaml(world, toYaml());
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException("a map that does not read back: " + e.getMessage(), e);
        }
    }

    String toYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("The Bed Wars points of this map, placed with /bw (RagoBedwars). /bw info "
                + world + " says what is missing.");
        yaml.set("actif", enabled);
        if (buildLimit > 0) {
            yaml.set("hauteur-max", buildLimit);
        }
        if (voidY != Integer.MIN_VALUE) {
            yaml.set("hauteur-vide", voidY);
        }
        for (Map.Entry<TeamColor, Team> entry : teams.entrySet()) {
            String path = "equipes." + entry.getKey().id();
            Team team = entry.getValue();
            yaml.createSection(path);
            write(yaml, path + ".spawn", team.spawn);
            write(yaml, path + ".lit", team.bed);
            write(yaml, path + ".generateur", team.generator);
            write(yaml, path + ".shop", team.shop);
            write(yaml, path + ".ameliorations", team.upgrades);
        }
        yaml.set("diamants", maps(diamonds));
        yaml.set("emeraudes", maps(emeralds));
        write(yaml, "spectateurs", spectators);
        return yaml.saveToString();
    }

    private static void write(YamlConfiguration yaml, String path, MapPoint point) {
        if (point != null) {
            yaml.createSection(path, point.toMap());
        }
    }

    private static List<Map<String, Object>> maps(List<MapPoint> points) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (MapPoint point : points) {
            maps.add(point.toMap());
        }
        return maps;
    }

    /**
     * The map of this world from its {@value #FILE_NAME}. Throws InvalidConfigurationException for
     * a file that is not YAML, IllegalArgumentException with the path of a wrong value.
     */
    static BedwarsMap fromYaml(String world, String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        BedwarsMap map = new BedwarsMap(world);
        map.enabled = yaml.getBoolean("actif", false);
        if (yaml.contains("hauteur-max")) {
            int limit = yaml.getInt("hauteur-max");
            if (limit < 1 || limit > 255) {
                throw new IllegalArgumentException("hauteur-max: from 1 to 255");
            }
            map.buildLimit = limit;
        }
        if (yaml.contains("hauteur-vide")) {
            map.voidY = yaml.getInt("hauteur-vide");
        }
        ConfigurationSection teams = yaml.getConfigurationSection("equipes");
        if (teams != null) {
            for (String id : teams.getKeys(false)) {
                TeamColor color = TeamColor.byId(id);
                if (color == null) {
                    throw new IllegalArgumentException("equipes." + id + ": not a team (" + TeamColor.ids() + ")");
                }
                ConfigurationSection section = teams.getConfigurationSection(id);
                Team team = map.team(color);
                String where = "equipes." + id;
                team.spawn = point(section, "spawn", where);
                team.bed = point(section, "lit", where);
                team.generator = point(section, "generateur", where);
                team.shop = point(section, "shop", where);
                team.upgrades = point(section, "ameliorations", where);
            }
        }
        map.diamonds.addAll(points(yaml.getMapList("diamants"), "diamants"));
        map.emeralds.addAll(points(yaml.getMapList("emeraudes"), "emeraudes"));
        map.spectators = point(yaml, "spectateurs", null);
        return map;
    }

    private static MapPoint point(ConfigurationSection parent, String key, String where) {
        String path = where == null ? key : where + "." + key;
        if (parent == null || !parent.contains(key)) {
            return null;
        }
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            throw new IllegalArgumentException(path + ": x, y and z");
        }
        return MapPoint.fromMap(section.getValues(false), path);
    }

    private static List<MapPoint> points(List<Map<?, ?>> maps, String where) {
        List<MapPoint> points = new ArrayList<>();
        for (int i = 0; i < maps.size(); i++) {
            points.add(MapPoint.fromMap(maps.get(i), where + "[" + i + "]"));
        }
        return points;
    }
}
