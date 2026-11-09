package gg.rago.practice;

import gg.rago.game.ArenaGrid;
import gg.rago.lobby.api.LobbyService;
import gg.rago.lobby.api.VoidGenerator;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RagoPractice: the practice modes played from the lobby. Duels (sumo, boxing, KB displace, bridge
 * fight, block fight, top fight, attack and defense, fireball fight), the trainings (clutch, block
 * in, fast defense, extends, ladder clutch, pearls, fireballs, aim), the bots, and the open block
 * placement arena, each in zones of an empty world. See the module README.
 */
public final class RagoPractice extends JavaPlugin {

    /** Height of the arena platforms. */
    static final int ARENA_Y = VoidGenerator.PLATFORM_Y;
    /** The z of the first row of zones of the practice modes (the sumo plays on z = 0). */
    static final int FIRST_ROW_Z = 1000;
    /** The rows of two modes are this far apart. */
    static final int ROW_SPACING = 400;
    /** The file that says the arena world is the plugin's own: it is made again at each start. */
    static final String MARKER = "rago-arenas.txt";

    private LobbyService lobby;
    private SumoHost sumo;
    private PracticeGames games;
    private Records records;
    private final List<String> hosted = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lobby = getServer().getServicesManager().load(LobbyService.class);
        if (lobby == null) {
            getLogger().severe("RagoLobby is not running: RagoPractice is disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        FileConfiguration config = getConfig();
        String worldName = config.getString("monde-arenes", "arenes");
        int spacing = config.getInt("espacement", 200);
        int slots = config.getInt("arenes-max", 16);
        int radius = config.getInt("sumo.rayon", 7);
        int points = config.getInt("sumo.points-pour-gagner", 3);
        String problem = check(worldName, spacing, slots, radius, points);
        if (problem == null) {
            problem = checkModes(config);
        }
        if (problem != null) {
            getLogger().severe("config.yml is invalid, RagoPractice is disabled: " + problem);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        World world = arenaWorld(worldName);
        records = new Records(this, new File(getDataFolder(), "records.yml"));
        records.load();

        sumo = new SumoHost(this, world, new ArenaGrid(slots, spacing), radius, points, zoneMaps(config, "sumo.cartes", spacing,
                "the sumo"));
        getServer().getPluginManager().registerEvents(sumo, this);
        if (!lobby.registerHost(this, SumoHost.MODE_ID, sumo)) {
            getLogger().warning("The lobby has no mode \"" + SumoHost.MODE_ID + "\" in its config.yml: the sumo duel stays closed.");
        }

        games = new PracticeGames(this, world);
        getServer().getPluginManager().registerEvents(games, this);
        registerModes(config, world, slots, spacing);
        getCommand("aimtop").setExecutor(new AimTop(this));
        getLogger().info("Arenas in the world " + world.getName() + ": " + slots + " games of each mode at once at most. Modes: "
                + String.join(", ", hosted));
    }

    /** The empty world of the arenas, made again at each start when it is the plugin's own. */
    private World arenaWorld(String worldName) {
        World world = getServer().getWorld(worldName);
        if (world == null) {
            File folder = new File(getServer().getWorldContainer(), worldName);
            if (new File(folder, MARKER).isFile()) {
                // Built again from nothing: what a crash left in a zone goes with it.
                deleteFolder(folder);
            }
            world = new WorldCreator(worldName).generator(new VoidGenerator()).generateStructures(false).createWorld();
            try {
                Files.write(new File(world.getWorldFolder(), MARKER).toPath(),
                        "This world is made again by RagoPractice at each start: build nothing in it.\n".getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "The arena world could not be marked as the plugin's own", e);
            }
        }
        world.setPVP(true);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setTime(6000L);
        return world;
    }

    private void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else if (!file.delete()) {
                    getLogger().warning("Could not delete " + file);
                }
            }
        }
        if (!folder.delete()) {
            getLogger().warning("Could not delete " + folder);
        }
    }

    /** Every practice mode, on its own row of zones. */
    private void registerModes(FileConfiguration config, World world, int slots, int spacing) {
        int boxing = config.getInt("points-pour-gagner.boxing", 100);
        int kb = config.getInt("points-pour-gagner.kb-displace", 3);
        int bridge = config.getInt("points-pour-gagner.bridge-fight", 3);
        int blocks = config.getInt("points-pour-gagner.block-fight", 5);
        int top = config.getInt("points-pour-gagner.top-fight", 3);
        int build = config.getInt("attaque-defense.construction-secondes", 30);
        int attack = config.getInt("attaque-defense.attaque-secondes", 120);
        int rounds = config.getInt("attaque-defense.manches", 2);
        int aimXp = config.getInt("aim-trainer.xp-par-seconde", 1);
        int aimHits = config.getInt("aim-trainer.touches-par-xp", 10);
        BlockInTraining.Island island = blockInIsland(config);

        register("boxing", (h, m, a, t) -> new Boxing(h, m, a, t, boxing), slots, spacing, false);
        register("kb-displace", (h, m, a, t) -> new KbDisplace(h, m, a, t, kb), slots, spacing, false);
        register("bridge-fight", (h, m, a, t) -> new BridgeFight(h, m, a, t, bridge), slots, spacing, false);
        register("block-fight-1v1", (h, m, a, t) -> new BlockFight(h, m, a, t, blocks), slots, spacing, false);
        register("block-fight-2v2", (h, m, a, t) -> new BlockFight(h, m, a, t, blocks), slots, spacing, false);
        register("top-fight-1v1", (h, m, a, t) -> new TopFight(h, m, a, t, top), slots, spacing, false);
        register("top-fight-2v2", (h, m, a, t) -> new TopFight(h, m, a, t, top), slots, spacing, false);
        register("attack-defense-1v1", (h, m, a, t) -> new AttackDefense(h, m, a, t, build, attack, rounds), slots, spacing, false);
        register("attack-defense-2v2", (h, m, a, t) -> new AttackDefense(h, m, a, t, build, attack, rounds), slots, spacing, false);
        register("fireball", FireballFight::new, slots, spacing, false);
        register("clutch", ClutchTraining::new, slots, spacing, false);
        register("block-in", (h, m, a, t) -> new BlockInTraining(h, m, a, t, island), slots, spacing, false);
        register("fast-defense", FastDefenseTraining::new, slots, spacing, false);
        register("extend-practice", ExtendTraining::new, slots, spacing, false);
        register("ladder-practice", LadderTraining::new, slots, spacing, false);
        register("pearl-practice", PearlTraining::new, slots, spacing, false);
        register("fireball-practice", FireballTraining::new, slots, spacing, false);
        register("aim-trainer", (h, m, a, t) -> new AimTrainer(h, m, a, t, aimXp, aimHits), slots, spacing, false);
        register("combo-bot", ComboBot::new, slots, spacing, false);
        register("kb-displace-bot", KbDisplaceBot::new, slots, spacing, false);
        register("block-fight-arena", BlockArena::new, slots, spacing, true);
        register("scenarios-ranked", RankedScenarios::new, slots, spacing, false);
    }

    /**
     * The island of the block in training: the owner's map (block-in.monde) copied without its
     * command blocks, the bed and its defense left out (each attempt puts them back); without that
     * world, the plugin's plain island. The starts are the map's, from the foot of the bed.
     */
    private BlockInTraining.Island blockInIsland(FileConfiguration config) {
        List<Integer> bed = config.getIntegerList("block-in.lit");
        List<Integer> head = config.getIntegerList("block-in.tete");
        List<Integer> zone = config.getIntegerList("block-in.zone");
        List<?> starts = config.getList("block-in.departs");
        String problem = checkBlockIn(bed, head, zone, starts);
        if (problem != null) {
            getLogger().warning("block-in in config.yml: " + problem + ". The block in training plays on the plugin's own island.");
            return new BlockInTraining.Island(null, -1, 0, new double[][] {{4.5, 11, 4.5, 135, 60}, {4.5, 11, -3.5, 45, 60}});
        }
        int hx = head.get(0) - bed.get(0);
        int hz = head.get(2) - bed.get(2);
        int[] origin = {bed.get(0), bed.get(1) - BlockInTraining.BED_Y, bed.get(2)};
        double[][] places = new double[starts.size()][];
        for (int i = 0; i < places.length; i++) {
            List<?> start = (List<?>) starts.get(i);
            places[i] = new double[] {number(start.get(0)) - origin[0] + 0.5, number(start.get(1)) - origin[1],
                number(start.get(2)) - origin[2] + 0.5, number(start.get(3)), number(start.get(4))};
        }
        int[] box = new int[6];
        for (int i = 0; i < 6; i++) {
            box[i] = zone.get(i);
        }
        MapCopy copy = MapCopy.read(config.getString("block-in.monde", ""), box, origin, getLogger(),
                "the block in training");
        if (copy != null) {
            copy = copy.without(BlockInTraining.defensePlaces(hx, hz));
        }
        return new BlockInTraining.Island(copy, hx, hz, places);
    }

    /**
     * The maps of a list of config.yml (monde, centre, zone each) the server has, each read once:
     * the middle of the top of its platform lands on the centre of the zones.
     */
    private List<MapZones.ZoneMap> zoneMaps(FileConfiguration config, String path, int spacing, String what) {
        List<MapZones.ZoneMap> maps = new ArrayList<>();
        for (Map<?, ?> entry : config.getMapList(path)) {
            String problem = checkZoneMap(entry, spacing);
            if (problem != null) {
                getLogger().warning(path + " in config.yml: " + problem + ". The map is left out.");
                continue;
            }
            String name = String.valueOf(entry.get("monde"));
            List<?> centre = (List<?>) entry.get("centre");
            List<?> zone = (List<?>) entry.get("zone");
            int[] origin = new int[3];
            int[] box = new int[6];
            for (int i = 0; i < 3; i++) {
                origin[i] = (int) number(centre.get(i));
            }
            for (int i = 0; i < 6; i++) {
                box[i] = (int) number(zone.get(i));
            }
            MapCopy copy = MapCopy.read(name, box, origin, getLogger(), what);
            if (copy != null) {
                maps.add(new MapZones.ZoneMap(name, copy));
            }
        }
        return maps;
    }

    /**
     * What is wrong with a map of a list (monde, centre, zone), or null. The zone holds the centre
     * and stays in its zone of the arena world: less than half the spacing from the centre.
     */
    static String checkZoneMap(Map<?, ?> entry, int spacing) {
        Object world = entry.get("monde");
        if (world == null || String.valueOf(world).trim().isEmpty()) {
            return "monde: the name of the world";
        }
        int[] centre = numbers(entry.get("centre"), 3);
        if (centre == null) {
            return "centre: x, y, z of the block in the middle of the top of the platform";
        }
        int[] zone = numbers(entry.get("zone"), 6);
        if (zone == null) {
            return "zone: the two corners of the box copied, x, y, z each";
        }
        for (int i = 0; i < 3; i++) {
            if (centre[i] < Math.min(zone[i], zone[i + 3]) || centre[i] > Math.max(zone[i], zone[i + 3])) {
                return "zone: the box holds the centre";
            }
        }
        int half = spacing / 2 - 1;
        for (int i : new int[] {0, 2}) {
            if (Math.abs(zone[i] - centre[i]) > half || Math.abs(zone[i + 3] - centre[i]) > half) {
                return "zone: at most " + half + " blocks from the centre on x and z (espacement / 2)";
            }
        }
        return null;
    }

    /** The whole numbers of a list of this size, or null. */
    private static int[] numbers(Object value, int size) {
        if (!(value instanceof List) || ((List<?>) value).size() != size) {
            return null;
        }
        int[] numbers = new int[size];
        for (int i = 0; i < size; i++) {
            Object each = ((List<?>) value).get(i);
            if (!(each instanceof Number)) {
                return null;
            }
            numbers[i] = ((Number) each).intValue();
        }
        return numbers;
    }

    private static double number(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    /** What is wrong with the block-in section, or null. */
    static String checkBlockIn(List<Integer> bed, List<Integer> head, List<Integer> zone, List<?> starts) {
        if (bed.size() != 3 || head.size() != 3) {
            return "lit and tete: x, y, z each";
        }
        if (!bed.get(1).equals(head.get(1)) || Math.abs(head.get(0) - bed.get(0)) + Math.abs(head.get(2) - bed.get(2)) != 1) {
            return "tete: the block next to lit, on the same level";
        }
        if (zone.size() != 6) {
            return "zone: the two corners of the island, x, y, z each";
        }
        if (starts == null || starts.isEmpty()) {
            return "departs: at least one start, x, y, z, yaw, pitch";
        }
        for (Object start : starts) {
            if (!(start instanceof List) || ((List<?>) start).size() != 5) {
                return "departs: x, y, z, yaw, pitch for each start";
            }
            for (Object value : (List<?>) start) {
                try {
                    number(value);
                } catch (NumberFormatException e) {
                    return "departs: \"" + value + "\" is not a number";
                }
            }
        }
        return null;
    }

    private void register(String modeId, PracticeHost.Factory factory, int slots, int spacing, boolean open) {
        int row = FIRST_ROW_Z + ROW_SPACING * hosted.size();
        PracticeHost host = new PracticeHost(this, games, modeId, factory, new ArenaGrid(open ? 1 : slots, spacing), row, open);
        if (lobby.registerHost(this, modeId, host)) {
            hosted.add(modeId);
        } else {
            // its row stays reserved, so that the other modes keep their zones
            hosted.add("(" + modeId + ")");
            getLogger().warning("The lobby has no mode \"" + modeId + "\" in its config.yml: it stays closed.");
        }
    }

    @Override
    public void onDisable() {
        if (sumo != null) {
            sumo.abortAll();
            sumo = null;
        }
        if (games != null) {
            games.abortAll();
            games = null;
        }
        if (lobby != null) {
            lobby.unregisterHosts(this);
        }
    }

    /** What is wrong with these settings, or null. */
    static String check(String world, int spacing, int slots, int radius, int points) {
        if (world == null || !world.matches("[A-Za-z0-9_-]{1,32}")) {
            return "monde-arenes: \"" + world + "\" is not a valid world name";
        }
        if (spacing < 100) {
            return "espacement: 100 blocks at least, so that two games do not see each other";
        }
        if (slots < 1 || slots > 64) {
            return "arenes-max: from 1 to 64";
        }
        if (radius < 4 || radius > 15) {
            return "sumo.rayon: from 4 to 15";
        }
        if (spacing < 2 * radius + 100) {
            return "espacement: too small for this radius";
        }
        if (points < 1 || points > 10) {
            return "sumo.points-pour-gagner: from 1 to 10";
        }
        return null;
    }

    /** What is wrong with the settings of the other modes, or null. */
    static String checkModes(FileConfiguration config) {
        String[][] ranges = {
            {"points-pour-gagner.boxing", "100", "10", "500"},
            {"points-pour-gagner.kb-displace", "3", "1", "10"},
            {"points-pour-gagner.bridge-fight", "3", "1", "10"},
            {"points-pour-gagner.block-fight", "5", "1", "20"},
            {"points-pour-gagner.top-fight", "3", "1", "10"},
            {"attaque-defense.construction-secondes", "30", "5", "120"},
            {"attaque-defense.attaque-secondes", "120", "30", "600"},
            {"attaque-defense.manches", "2", "2", "10"},
            {"aim-trainer.xp-par-seconde", "1", "0", "10"},
            {"aim-trainer.touches-par-xp", "10", "1", "100"},
        };
        for (String[] range : ranges) {
            int value = config.getInt(range[0], Integer.parseInt(range[1]));
            if (value < Integer.parseInt(range[2]) || value > Integer.parseInt(range[3])) {
                return range[0] + ": from " + range[2] + " to " + range[3];
            }
        }
        if (config.getInt("attaque-defense.manches", 2) % 2 != 0) {
            return "attaque-defense.manches: an even number, so that each side attacks as often";
        }
        return null;
    }

    LobbyService lobby() {
        return lobby;
    }

    Records records() {
        return records;
    }
}
