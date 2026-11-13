package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * A Bed Wars map: a world of the server's world folder, with the points of the game kept in
 * {@value #FILE_NAME} inside the world's own folder, placed in game with /bw. Each team has a
 * spawn, a bed, a generator (iron and gold) and a shop, and may have a team upgrades NPC; the map
 * has diamond and emerald generators, and a spectator point. A map of attack and defense
 * (attaquants:) has one complete team, the defenders, and the starts of the attackers, a team with
 * no island. A map is played once it has two sides or more (complete teams, and the attackers) and
 * is enabled. A map may have its waiting lobby (salle-attente:), where Lobby Fight is played. Main
 * thread only.
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
    /** The lowest block players may place (hauteur-min); Integer.MIN_VALUE: down to the void. */
    private int floorY = Integer.MIN_VALUE;
    private final Map<TeamColor, Team> teams = new EnumMap<>(TeamColor.class);
    private final List<MapPoint> diamonds = new ArrayList<>();
    private final List<MapPoint> emeralds = new ArrayList<>();
    private MapPoint spectators;
    private final List<Removal> removals = new ArrayList<>();
    /** The attackers of an attack and defense map: their colour (null: none) and their starts. */
    private TeamColor attackerColor;
    private final List<MapPoint> attackerStarts = new ArrayList<>();
    /**
     * The spawns a player of the FFA may choose from (apparitions:), in their order: a name ("Top
     * bridge", "Low", "Mid"...) and its points. Empty: no choice, the islands.
     */
    private final Map<String, List<MapPoint>> spawnChoices = new java.util.LinkedHashMap<>();
    /** The most spawns to choose from: one row of the menu, Random kept. */
    static final int MAX_SPAWN_CHOICES = 8;
    /**
     * Where the boards of the most kills float in the FFA (classement:, a point or a list of points,
     * the top line of each); none: one above the middle.
     */
    private final List<MapPoint> killBoards = new ArrayList<>();
    /** Where blocks may be placed (iles:); none: everywhere under the build limit. */
    private final List<Island> islands = new ArrayList<>();
    /** The waiting lobby (salle-attente:), where Lobby Fight is played; null: none. */
    private WaitingLobby waitingLobby;

    /**
     * The waiting lobby of a map (salle-attente:), the platform high over it where the players wait
     * for a game to start, where Lobby Fight is played: the height of its floor (the players stand
     * one block above), the middle of its floor and how far the floor goes from there. Archway FFA
     * has one: its glass floor at y 117, 11 blocks around the middle. Set with /bw lobbyfight, from
     * its floor. Immutable.
     */
    static final class WaitingLobby {
        /** The farthest a waiting lobby goes from its middle. */
        static final double MAX_RADIUS = 48;

        final double centreX;
        final double centreZ;
        final int floorY;
        final double radius;

        WaitingLobby(double centreX, double centreZ, int floorY, double radius) {
            this.centreX = centreX;
            this.centreZ = centreZ;
            this.floorY = floorY;
            this.radius = radius;
        }

        /**
         * The lobby whose floor is these blocks (x, z, all at this height): its middle is the
         * middle of the box around them, its radius as far as the middle of the farthest of them.
         * Null for no block.
         */
        static WaitingLobby around(List<int[]> floor, int floorY) {
            if (floor.isEmpty()) {
                return null;
            }
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (int[] block : floor) {
                minX = Math.min(minX, block[0]);
                maxX = Math.max(maxX, block[0]);
                minZ = Math.min(minZ, block[1]);
                maxZ = Math.max(maxZ, block[1]);
            }
            double centreX = (minX + maxX + 1) / 2.0;
            double centreZ = (minZ + maxZ + 1) / 2.0;
            double farthest = 0;
            for (int[] block : floor) {
                farthest = Math.max(farthest, Math.hypot(block[0] + 0.5 - centreX, block[1] + 0.5 - centreZ));
            }
            // to the half block above, so that the farthest blocks are in
            return new WaitingLobby(centreX, centreZ, floorY, Math.max(0.5, Math.ceil(farthest * 2 - 1e-9) / 2));
        }

        /** True if the column of this block is in the lobby: its middle within the radius. */
        boolean covers(int x, int z) {
            double dx = x + 0.5 - centreX;
            double dz = z + 0.5 - centreZ;
            return dx * dx + dz * dz <= radius * radius + 1e-9;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            Map<String, Object> centre = new java.util.LinkedHashMap<>();
            centre.put("x", centreX);
            centre.put("z", centreZ);
            map.put("centre", centre);
            map.put("y", floorY);
            map.put("rayon", radius);
            return map;
        }

        static WaitingLobby fromSection(ConfigurationSection section, String where) {
            ConfigurationSection centre = section.getConfigurationSection("centre");
            if (centre == null || !(centre.get("x") instanceof Number) || !(centre.get("z") instanceof Number)) {
                throw new IllegalArgumentException(where + ".centre: x and z");
            }
            Object y = section.get("y");
            if (!(y instanceof Number) || ((Number) y).intValue() < 0 || ((Number) y).intValue() > 254) {
                throw new IllegalArgumentException(where + ".y: the height of the floor, from 0 to 254");
            }
            Object radius = section.get("rayon");
            if (!(radius instanceof Number) || ((Number) radius).doubleValue() <= 0
                    || ((Number) radius).doubleValue() > MAX_RADIUS) {
                throw new IllegalArgumentException(where + ".rayon: a number of blocks, more than 0, " + (int) MAX_RADIUS
                        + " at most");
            }
            return new WaitingLobby(((Number) centre.get("x")).doubleValue(), ((Number) centre.get("z")).doubleValue(),
                    ((Number) y).intValue(), ((Number) radius).doubleValue());
        }

        @Override
        public String toString() {
            return "floor at y " + floorY + ", " + fmt(radius) + " blocks around " + fmt(centreX) + " " + fmt(centreZ);
        }

        private static String fmt(double value) {
            return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
        }
    }

    /**
     * An island where the players may place blocks (iles:), when the map lists them: blocks go on
     * the islands only, never on the bridges between them. A box (de and a, two corners, as in
     * retirer:) or a disc (centre: x and z, rayon, from y bas to y haut, both kept). Archway FFA
     * has them: its middle (a disc that stops under the top bridge), its bases and its diamond
     * islands. Immutable.
     */
    static final class Island {
        /** A disc: its centre and radius (a column is in it when its middle is); a box: NaN. */
        final double centreX;
        final double centreZ;
        final double radius;
        final int minX;
        final int minZ;
        final int maxX;
        final int maxZ;
        final int minY;
        final int maxY;

        private Island(double centreX, double centreZ, double radius, int minX, int minZ, int maxX, int maxZ,
                int minY, int maxY) {
            this.centreX = centreX;
            this.centreZ = centreZ;
            this.radius = radius;
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
            this.minY = Math.max(0, Math.min(minY, maxY));
            this.maxY = Math.min(255, Math.max(minY, maxY));
        }

        static Island box(int x1, int y1, int z1, int x2, int y2, int z2) {
            return new Island(Double.NaN, Double.NaN, Double.NaN, Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2),
                    Math.max(z1, z2), y1, y2);
        }

        static Island disc(double centreX, double centreZ, double radius, int bottom, int top) {
            return new Island(centreX, centreZ, radius, (int) Math.floor(centreX - radius),
                    (int) Math.floor(centreZ - radius), (int) Math.floor(centreX + radius),
                    (int) Math.floor(centreZ + radius), bottom, top);
        }

        boolean disc() {
            return !Double.isNaN(radius);
        }

        /** True if the column of this block is on the island, whatever its height. */
        boolean covers(int x, int z) {
            if (x < minX || x > maxX || z < minZ || z > maxZ) {
                return false;
            }
            if (!disc()) {
                return true;
            }
            double dx = x + 0.5 - centreX;
            double dz = z + 0.5 - centreZ;
            return dx * dx + dz * dz <= radius * radius;
        }

        boolean holds(int y) {
            return y >= minY && y <= maxY;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            if (disc()) {
                Map<String, Object> centre = new java.util.LinkedHashMap<>();
                centre.put("x", centreX);
                centre.put("z", centreZ);
                map.put("centre", centre);
                map.put("rayon", radius);
                map.put("bas", minY);
                map.put("haut", maxY);
            } else {
                map.put("de", Removal.corner(minX, minY, minZ));
                map.put("a", Removal.corner(maxX, maxY, maxZ));
            }
            return map;
        }

        static Island fromMap(Map<?, ?> map, String where) {
            if (!map.containsKey("centre")) {
                int[] from = Removal.corner(map.get("de"), where + ".de");
                int[] to = Removal.corner(map.get("a"), where + ".a");
                return box(from[0], from[1], from[2], to[0], to[1], to[2]);
            }
            Object centre = map.get("centre");
            if (!(centre instanceof Map) || !(((Map<?, ?>) centre).get("x") instanceof Number)
                    || !(((Map<?, ?>) centre).get("z") instanceof Number)) {
                throw new IllegalArgumentException(where + ".centre: x and z");
            }
            Object radius = map.get("rayon");
            if (!(radius instanceof Number) || ((Number) radius).doubleValue() <= 0
                    || ((Number) radius).doubleValue() > 512) {
                throw new IllegalArgumentException(where + ".rayon: a number of blocks, more than 0");
            }
            int[] heights = new int[2];
            String[] keys = {"bas", "haut"};
            int[] defaults = {0, 255};
            for (int i = 0; i < 2; i++) {
                Object value = map.get(keys[i]);
                if (value != null && !(value instanceof Number)) {
                    throw new IllegalArgumentException(where + "." + keys[i] + ": a height, from 0 to 255");
                }
                heights[i] = value == null ? defaults[i] : ((Number) value).intValue();
            }
            return disc(((Number) ((Map<?, ?>) centre).get("x")).doubleValue(),
                    ((Number) ((Map<?, ?>) centre).get("z")).doubleValue(), ((Number) radius).doubleValue(), heights[0],
                    heights[1]);
        }
    }

    /**
     * A box of the map taken out of each game's copy when it is made (retirer:): all its blocks, or
     * only those of some materials. Antenna has one: the owner's top bridge, which the ranked games
     * leave out (the Top Bridge mode builds its own on every map). Immutable.
     */
    static final class Removal {
        final int minX;
        final int minY;
        final int minZ;
        final int maxX;
        final int maxY;
        final int maxZ;
        /** Empty: every block. */
        final List<Material> materials;

        Removal(int x1, int y1, int z1, int x2, int y2, int z2, List<Material> materials) {
            this.minX = Math.min(x1, x2);
            this.minY = Math.max(0, Math.min(y1, y2));
            this.minZ = Math.min(z1, z2);
            this.maxX = Math.max(x1, x2);
            this.maxY = Math.min(255, Math.max(y1, y2));
            this.maxZ = Math.max(z1, z2);
            this.materials = Collections.unmodifiableList(new ArrayList<>(materials));
        }

        boolean takes(Material type) {
            return type != Material.AIR && (materials.isEmpty() || materials.contains(type));
        }

        /** Takes the blocks out of this world; returns how many. */
        int apply(org.bukkit.World world) {
            int removed = 0;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                        if (takes(block.getType())) {
                            block.setType(Material.AIR, false);
                            removed++;
                        }
                    }
                }
            }
            return removed;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("de", corner(minX, minY, minZ));
            map.put("a", corner(maxX, maxY, maxZ));
            if (!materials.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (Material material : materials) {
                    names.add(material.name());
                }
                map.put("blocs", names);
            }
            return map;
        }

        static Map<String, Object> corner(int x, int y, int z) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("x", x);
            map.put("y", y);
            map.put("z", z);
            return map;
        }

        static Removal fromMap(Map<?, ?> map, String where) {
            int[] from = corner(map.get("de"), where + ".de");
            int[] to = corner(map.get("a"), where + ".a");
            List<Material> materials = new ArrayList<>();
            Object blocks = map.get("blocs");
            if (blocks != null) {
                if (!(blocks instanceof List)) {
                    throw new IllegalArgumentException(where + ".blocs: a list of blocks, for example [WOOL]");
                }
                for (Object name : (List<?>) blocks) {
                    Material material = Material.matchMaterial(String.valueOf(name));
                    if (material == null || !material.isBlock()) {
                        throw new IllegalArgumentException(where + ".blocs: \"" + name + "\" is not a block");
                    }
                    materials.add(material);
                }
            }
            return new Removal(from[0], from[1], from[2], to[0], to[1], to[2], materials);
        }

        static int[] corner(Object value, String where) {
            if (!(value instanceof Map)) {
                throw new IllegalArgumentException(where + ": x, y and z");
            }
            Map<?, ?> map = (Map<?, ?>) value;
            int[] xyz = new int[3];
            String[] keys = {"x", "y", "z"};
            for (int i = 0; i < 3; i++) {
                Object number = map.get(keys[i]);
                if (!(number instanceof Number)) {
                    throw new IllegalArgumentException(where + "." + keys[i] + ": a whole number");
                }
                xyz[i] = ((Number) number).intValue();
            }
            return xyz;
        }
    }

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

    /**
     * The complete teams a game of this many teams is played with: the first one, then each time
     * the one whose spawn is farthest from those already taken, as on Hypixel (a duel on a map of
     * four is played on two opposite islands). In the order of the colours.
     */
    List<TeamColor> playingTeams(int count) {
        List<TeamColor> complete = completeTeams();
        if (count >= complete.size()) {
            return complete;
        }
        List<TeamColor> chosen = new ArrayList<>();
        if (count <= 0) {
            return chosen;
        }
        chosen.add(complete.get(0));
        while (chosen.size() < count) {
            TeamColor best = null;
            double bestDistance = -1;
            for (TeamColor color : complete) {
                if (chosen.contains(color)) {
                    continue;
                }
                MapPoint spawn = teams.get(color).spawn;
                double nearest = Double.MAX_VALUE;
                for (TeamColor taken : chosen) {
                    MapPoint other = teams.get(taken).spawn;
                    double dx = spawn.x() - other.x();
                    double dz = spawn.z() - other.z();
                    nearest = Math.min(nearest, dx * dx + dz * dz);
                }
                // a tie keeps the first colour
                if (nearest > bestDistance + 1e-6) {
                    best = color;
                    bestDistance = nearest;
                }
            }
            chosen.add(best);
        }
        List<TeamColor> ordered = new ArrayList<>(complete);
        ordered.retainAll(chosen);
        return ordered;
    }

    /**
     * Two islands this close in distance (5 % apart at most) are as near as each other for
     * {@link #neighbourTeams}: the points found by the map tool are a block or two off, and on a map
     * of eight red's neighbours are blue and gray, a few tenths of a block apart.
     */
    static final double NEIGHBOUR_TIE = 0.05;

    /**
     * The complete teams a game of this many teams is played with on neighbouring islands (First
     * Rush): the first one, then each time the one whose spawn is nearest to those already taken; of
     * islands about as near ({@link #NEIGHBOUR_TIE}), the first colour, so that red plays blue as on
     * Hypixel. In the order of the colours.
     */
    List<TeamColor> neighbourTeams(int count) {
        List<TeamColor> complete = completeTeams();
        if (count >= complete.size()) {
            return complete;
        }
        List<TeamColor> chosen = new ArrayList<>();
        if (count <= 0) {
            return chosen;
        }
        chosen.add(complete.get(0));
        while (chosen.size() < count) {
            TeamColor best = null;
            double bestDistance = Double.MAX_VALUE;
            for (TeamColor color : complete) {
                if (chosen.contains(color)) {
                    continue;
                }
                MapPoint spawn = teams.get(color).spawn;
                double nearest = Double.MAX_VALUE;
                for (TeamColor taken : chosen) {
                    MapPoint other = teams.get(taken).spawn;
                    double dx = spawn.x() - other.x();
                    double dz = spawn.z() - other.z();
                    nearest = Math.min(nearest, dx * dx + dz * dz);
                }
                if (Math.sqrt(nearest) < Math.sqrt(bestDistance) * (1 - NEIGHBOUR_TIE)) {
                    best = color;
                    bestDistance = nearest;
                }
            }
            chosen.add(best);
        }
        List<TeamColor> ordered = new ArrayList<>(complete);
        ordered.retainAll(chosen);
        return ordered;
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
        if (sides() < MIN_TEAMS) {
            missing.add("at least " + MIN_TEAMS + " complete teams (spawn, bed, generator and shop each), or one and"
                    + " the attackers (attaquants:)");
        }
        return missing;
    }

    /** True when it is enabled and has two complete teams or more. */
    boolean playable() {
        return enabled && sides() >= MIN_TEAMS;
    }

    /** How many sides a game can have on this map: its complete teams, and the attackers if it has them. */
    int sides() {
        return completeTeams().size() + (hasAttackers() ? 1 : 0);
    }

    /** True for a map of attack and defense: the attackers have their colour and a start at least. */
    boolean hasAttackers() {
        return attackerColor != null && !attackerStarts.isEmpty();
    }

    /** The colour of the attackers (null if the map has none). */
    TeamColor attackerColor() {
        return attackerColor;
    }

    /** Where the attackers start, the first one first. */
    List<MapPoint> attackerStarts() {
        return attackerStarts;
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

    /** Where the boards of the most kills float in the FFA (the top line of each); empty: one above the middle. */
    List<MapPoint> killBoards() {
        return Collections.unmodifiableList(killBoards);
    }

    /** The islands where blocks may be placed (iles:); empty: everywhere. */
    List<Island> islands() {
        return Collections.unmodifiableList(islands);
    }

    /** The waiting lobby, where Lobby Fight is played (salle-attente:); null if the map has none. */
    WaitingLobby waitingLobby() {
        return waitingLobby;
    }

    void setWaitingLobby(WaitingLobby waitingLobby) {
        this.waitingLobby = waitingLobby;
    }

    /**
     * Why the islands of the map (iles:) keep a block from going there, null when they let it: off
     * every island (on a bridge, over the void), or above the top of the island under it.
     */
    String outsideIslands(int x, int y, int z) {
        if (islands.isEmpty()) {
            return null;
        }
        boolean under = false;
        for (Island island : islands) {
            if (island.covers(x, z)) {
                if (island.holds(y)) {
                    return null;
                }
                under |= y > island.maxY;
            }
        }
        return under ? "Build height limit reached!" : "You can only place blocks on the islands!";
    }

    /** The spawns of the FFA to choose from, by name, in their order; empty if none. */
    Map<String, List<MapPoint>> spawnChoices() {
        return Collections.unmodifiableMap(spawnChoices);
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

    /**
     * The lowest block players may place: hauteur-min, else none (they clutch down to the void).
     * Integer.MIN_VALUE when there is none.
     */
    int floorY() {
        return floorY;
    }

    void setFloorY(int floorY) {
        this.floorY = floorY;
    }

    boolean hasFloorY() {
        return floorY != Integer.MIN_VALUE;
    }

    boolean hasBuildLimit() {
        return buildLimit > 0;
    }

    boolean hasVoidY() {
        return voidY != Integer.MIN_VALUE;
    }

    /** What each game's copy leaves out (retirer:), often nothing. */
    List<Removal> removals() {
        return Collections.unmodifiableList(removals);
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
        if (floorY != Integer.MIN_VALUE) {
            yaml.set("hauteur-min", floorY);
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
        if (attackerColor != null) {
            yaml.set("attaquants.equipe", attackerColor.id());
            yaml.set("attaquants.departs", maps(attackerStarts));
        }
        for (Map.Entry<String, List<MapPoint>> choice : spawnChoices.entrySet()) {
            yaml.set("apparitions." + choice.getKey(), maps(choice.getValue()));
        }
        if (killBoards.size() == 1) {
            write(yaml, "classement", killBoards.get(0));
        } else if (!killBoards.isEmpty()) {
            yaml.set("classement", maps(killBoards));
        }
        if (!islands.isEmpty()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Island island : islands) {
                list.add(island.toMap());
            }
            yaml.set("iles", list);
        }
        if (!removals.isEmpty()) {
            List<Map<String, Object>> boxes = new ArrayList<>();
            for (Removal removal : removals) {
                boxes.add(removal.toMap());
            }
            yaml.set("retirer", boxes);
        }
        if (waitingLobby != null) {
            yaml.createSection("salle-attente", waitingLobby.toMap());
        }
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
        if (yaml.contains("hauteur-min")) {
            int floor = yaml.getInt("hauteur-min");
            if (floor < 0 || floor > 255) {
                throw new IllegalArgumentException("hauteur-min: from 0 to 255");
            }
            map.floorY = floor;
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
        ConfigurationSection attackers = yaml.getConfigurationSection("attaquants");
        if (attackers != null) {
            String id = attackers.getString("equipe", "");
            TeamColor color = TeamColor.byId(id);
            if (color == null) {
                throw new IllegalArgumentException("attaquants.equipe: not a team (" + TeamColor.ids() + ")");
            }
            if (map.teams.containsKey(color)) {
                throw new IllegalArgumentException("attaquants.equipe: " + id + " is a team of the map already");
            }
            map.attackerColor = color;
            map.attackerStarts.addAll(points(attackers.getMapList("departs"), "attaquants.departs"));
        }
        ConfigurationSection choices = yaml.getConfigurationSection("apparitions");
        if (choices != null) {
            for (String name : choices.getKeys(false)) {
                List<MapPoint> points = points(choices.getMapList(name), "apparitions." + name);
                if (name.trim().isEmpty() || points.isEmpty()) {
                    throw new IllegalArgumentException("apparitions." + name + ": a name and a list of points (x, y, z, yaw)");
                }
                if (map.spawnChoices.size() == MAX_SPAWN_CHOICES) {
                    throw new IllegalArgumentException("apparitions: " + MAX_SPAWN_CHOICES + " spawns at most to choose from");
                }
                map.spawnChoices.put(name, points);
            }
        }
        if (yaml.isList("classement")) {
            map.killBoards.addAll(points(yaml.getMapList("classement"), "classement"));
        } else {
            MapPoint board = point(yaml, "classement", null);
            if (board != null) {
                map.killBoards.add(board);
            }
        }
        List<Map<?, ?>> islands = yaml.getMapList("iles");
        for (int i = 0; i < islands.size(); i++) {
            map.islands.add(Island.fromMap(islands.get(i), "iles[" + i + "]"));
        }
        List<Map<?, ?>> boxes = yaml.getMapList("retirer");
        for (int i = 0; i < boxes.size(); i++) {
            map.removals.add(Removal.fromMap(boxes.get(i), "retirer[" + i + "]"));
        }
        if (yaml.contains("salle-attente")) {
            ConfigurationSection lobby = yaml.getConfigurationSection("salle-attente");
            if (lobby == null) {
                throw new IllegalArgumentException("salle-attente: centre (x and z), y and rayon");
            }
            map.waitingLobby = WaitingLobby.fromSection(lobby, "salle-attente");
        }
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
