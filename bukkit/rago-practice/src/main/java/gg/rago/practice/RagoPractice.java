package gg.rago.practice;

import gg.rago.game.ArenaGrid;
import gg.rago.lobby.api.LobbyService;
import gg.rago.lobby.api.VoidGenerator;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RagoPractice: the practice modes played from the lobby. Duels (sumo, boxing, KB displace, block
 * fight, top fight, attack and defense, fireball fight, block edit), the trainings (clutch, block
 * in, fast defense, extends, ladder clutch, pearls, fireballs, aim, block edit), the bots, and the
 * open arenas (block placement, bridge fight), each in zones of an empty world. See the README.
 */
public final class RagoPractice extends JavaPlugin {

    /** Height of the arena platforms. */
    static final int ARENA_Y = VoidGenerator.PLATFORM_Y;
    /** The z of the first row of zones of the practice modes (the sumo plays on z = 0). */
    static final int FIRST_ROW_Z = 1000;
    /** The rows of two modes are this far apart. */
    static final int ROW_SPACING = 400;
    /** The id of the gold block: a marker on the owner's maps. */
    static final int GOLD_ID = 41;
    /** The file that says the arena world is the plugin's own: it is made again at each start. */
    static final String MARKER = "rago-arenas.txt";

    private LobbyService lobby;
    private SumoHost sumo;
    /** The maps being copied into the zones of the modes that play on maps. */
    private final List<MapZones> mapZones = new ArrayList<>();
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
        getCommand("speed").setExecutor(new SpeedCommand(this));
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
        int blocks = config.getInt("points-pour-gagner.block-fight", 100);
        int top = config.getInt("points-pour-gagner.top-fight", 3);
        int edit = config.getInt("points-pour-gagner.block-edit", 5);
        int bridge = config.getInt("points-pour-gagner.bridge-fight", 5);
        int bridgeProtection = config.getInt("bridge-fight.protection-1v1", 2);
        int attack = config.getInt("attaque-defense.attaque-secondes", 60);
        int rounds = config.getInt("attaque-defense.manches", 2);
        int aimXp = config.getInt("aim-trainer.xp-par-seconde", 1);
        int aimHits = config.getInt("aim-trainer.touches-par-xp", 10);
        BlockInTraining.Island island = blockInIsland(config);

        List<MapZones.ZoneMap> arenas = zoneMaps(config, "boxing.cartes", spacing, "the boxing and the combo bot");
        onMaps(register("boxing", (h, m, a, t) -> new Boxing(h, m, a, t, boxing), slots, spacing, false), arenas);
        register("kb-displace", (h, m, a, t) -> new KbDisplace(h, m, a, t, kb), slots, spacing, false);
        List<MapZones.ZoneMap> blockMaps = zoneMaps(config, "block-fight.cartes", spacing, "the block placement");
        onMaps(register("block-fight-1v1", (h, m, a, t) -> new BlockFight(h, m, a, t, blocks), slots, spacing, false), blockMaps);
        onMaps(register("block-fight-2v2", (h, m, a, t) -> new BlockFight(h, m, a, t, blocks), slots, spacing, false), blockMaps);
        register("top-fight-1v1", (h, m, a, t) -> new TopFight(h, m, a, t, top), slots, spacing, false);
        register("top-fight-2v2", (h, m, a, t) -> new TopFight(h, m, a, t, top), slots, spacing, false);
        AttackDefense.Base base = attackDefenseBase(config, spacing);
        register("attack-defense-1v1", (h, m, a, t) -> new AttackDefense(h, m, a, t, base, attack, rounds), slots, spacing, false);
        register("attack-defense-2v2", (h, m, a, t) -> new AttackDefense(h, m, a, t, base, attack, rounds), slots, spacing, false);
        register("fireball", FireballFight::new, slots, spacing, false);
        // the islands side by side, close enough to see the players of the next ones
        register("clutch", (h, m, a, t) -> new ClutchTraining(h, m, a, t, false), slots, ClutchTraining.SPACING, false);
        register("block-in", (h, m, a, t) -> new BlockInTraining(h, m, a, t, island), slots, spacing, false);
        register("fast-defense", FastDefenseTraining::new, slots, spacing, false);
        register("extend-practice", ExtendTraining::new, slots, spacing, false);
        register("ladder-practice", LadderTraining::new, slots, spacing, false);
        PearlTraining.Island skyRise = pearlIsland(config);
        register("pearl-practice", (h, m, a, t) -> new PearlTraining(h, m, a, t, skyRise), slots, spacing, false);
        register("fireball-practice", FireballTraining::new, slots, spacing, false);
        register("aim-trainer", (h, m, a, t) -> new AimTrainer(h, m, a, t, aimXp, aimHits), slots, spacing, false);
        onMaps(register("combo-bot", ComboBot::new, slots, spacing, false), arenas);
        register("kb-displace-bot", KbDisplaceBot::new, slots, spacing, false);
        List<MapZones.ZoneMap> arenaMaps = withoutBlocks(zoneMaps(config, "block-fight-arena.cartes", spacing,
                "the block placement arena"), GOLD_ID);
        onMaps(register("block-fight-arena", (h, m, a, t) -> new BlockArena(h, m, a, t, false), slots, spacing, true), arenaMaps);
        // Arena Free: the same arena of the owner, with its slime, for a free for all fight
        onMaps(register("free-ffa", (h, m, a, t) -> new BlockArena(h, m, a, t, true), slots, spacing, true), arenaMaps);
        onMaps(register("bridge-fight", BridgeArena::new, slots, spacing, true), bridgeMaps(config, spacing));
        register("scenarios-ranked", RankedScenarios::new, slots, spacing, false);
        register("clutch-top-fight", (h, m, a, t) -> new ClutchTraining(h, m, a, t, true), slots, ClutchTraining.SPACING, false);
        register("block-edit", BlockEditTraining::new, slots, spacing, false);
        register("block-edit-duel", (h, m, a, t) -> new BlockEditDuel(h, m, a, t, edit), slots, spacing, false);
        register("bridge-fight-1v1", (h, m, a, t) -> new BridgeDuel(h, m, a, t, bridge, bridgeProtection), slots, spacing, false);
        BlockExtendTraining.Course course = blockExtendCourse(config, spacing);
        register("block-extend", (h, m, a, t) -> new BlockExtendTraining(h, m, a, t, course), slots, spacing, false);
    }

    /**
     * The course of the block extend (block-extend): the owner's map copied once, its gold, diamond
     * and emerald markers turned to wool, and its three levels (or, diamant, emeraude). With a
     * mistake in the section, the plugin's own levels on plain blocks; without the world, the
     * levels of the section on plain blocks.
     */
    private BlockExtendTraining.Course blockExtendCourse(FileConfiguration config, int spacing) {
        ConfigurationSection section = config.getConfigurationSection("block-extend");
        String problem = section == null ? "the section is missing" : checkBlockExtend(section, spacing);
        if (problem != null) {
            getLogger().warning("block-extend in config.yml: " + problem + ". The block extend plays on the plugin's own blocks.");
            return new BlockExtendTraining.Course(null, BlockExtendTraining.plainLevels());
        }
        int[] origin = numbers(section.getList("centre"), 3);
        BlockExtendTraining.Level[] levels = new BlockExtendTraining.Level[BlockExtendTraining.IDS.length];
        for (int i = 0; i < levels.length; i++) {
            ConfigurationSection level = section.getConfigurationSection("niveaux." + BlockExtendTraining.IDS[i]);
            List<?> start = level.getList("depart");
            double[] at = new double[5];
            for (int k = 0; k < 5; k++) {
                at[k] = number(start.get(k)) - (k < 3 ? origin[k] : 0);
            }
            int[] target = numbers(level.getList("cible"), 3);
            for (int k = 0; k < 3; k++) {
                target[k] -= origin[k];
            }
            List<?> force = level.getList("force");
            double[] way;
            BlockExtendTraining.From from = null;
            if (level.isList("vers")) {
                List<?> towards = level.getList("vers");
                way = BlockExtendTraining.towards(at, number(towards.get(0)) - origin[0], number(towards.get(1)) - origin[2]);
            } else {
                from = BlockExtendTraining.From.of(level.getString("coup", "devant"));
                way = BlockExtendTraining.thrown((float) at[3], from);
            }
            levels[i] = new BlockExtendTraining.Level(BlockExtendTraining.IDS[i], BlockExtendTraining.LABELS[i], at, target, way,
                    from, number(force.get(0)), number(force.get(1)));
        }
        MapCopy copy = MapCopy.read(section.getString("monde", ""), numbers(section.getList("zone"), 6), origin, getLogger(),
                "the block extend");
        if (copy != null) {
            copy = copy.markersAsWool(BlockExtendTraining.MARKERS);
        }
        return new BlockExtendTraining.Course(copy, levels);
    }

    /** What is wrong with the block-extend section, or null. */
    static String checkBlockExtend(ConfigurationSection section, int spacing) {
        String world = section.getString("monde", "");
        Map<String, Object> zoneMap = new HashMap<>();
        zoneMap.put("monde", world.trim().isEmpty() ? "-" : world);
        zoneMap.put("centre", section.getList("centre"));
        zoneMap.put("zone", section.getList("zone"));
        String zoneProblem = checkZoneMap(zoneMap, spacing);
        if (zoneProblem != null) {
            return zoneProblem;
        }
        for (String id : BlockExtendTraining.IDS) {
            ConfigurationSection level = section.getConfigurationSection("niveaux." + id);
            String where = "niveaux." + id;
            if (level == null) {
                return where + ": the level is missing";
            }
            if (!numbersOf(level.getList("depart"), 5)) {
                return where + ".depart: x, y, z, yaw, pitch, as F3 gives them";
            }
            if (numbers(level.getList("cible"), 3) == null) {
                return where + ".cible: x, y, z of the block to land on, whole numbers";
            }
            if (!numbersOf(level.getList("force"), 2)) {
                return where + ".force: horizontal, vertical (a hit of a fight: 0.42, 0.36; a sprint hit: 0.80, 0.43)";
            }
            if (level.contains("vers")) {
                if (!numbersOf(level.getList("vers"), 2)) {
                    return where + ".vers: x, z of the point the hit throws towards";
                }
            } else if (BlockExtendTraining.From.of(level.getString("coup", "devant")) == null) {
                return where + ".coup: devant, droite, gauche or derriere";
            }
        }
        return null;
    }

    /** True if the value is a list of this many numbers. */
    private static boolean numbersOf(Object value, int size) {
        if (!(value instanceof List) || ((List<?>) value).size() != size) {
            return false;
        }
        for (Object each : (List<?>) value) {
            if (!(each instanceof Number)) {
                return false;
            }
        }
        return true;
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
     * The part of Sky Rise of the pearl practice (pearl.monde): the red island, the diamond island
     * and the middle, copied once; without that world, plain islands at the same places.
     */
    private PearlTraining.Island pearlIsland(FileConfiguration config) {
        int limit = config.getInt("pearl.limite-basse", 55);
        MapCopy copy = MapCopy.read(config.getString("pearl.monde", ""), PearlTraining.BOX.clone(), PearlTraining.ORIGIN.clone(),
                getLogger(), "the pearl practice");
        return new PearlTraining.Island(copy, limit);
    }

    /**
     * The defender's island of the attack and defense: a solo island of a Bed Wars map
     * (attaque-defense.monde) copied once, the foot of its bed one block above the centre of the
     * zones; the defender's start and the attacker's bridge from it. Without that world, or with a
     * mistake in the section, the plugin's plain island.
     */
    private AttackDefense.Base attackDefenseBase(FileConfiguration config, int spacing) {
        String world = config.getString("attaque-defense.monde", "");
        if (world.trim().isEmpty()) {
            return AttackDefense.Base.plain();
        }
        Object zone = config.getList("attaque-defense.zone");
        Object bed = config.getList("attaque-defense.lit");
        Object defender = config.getList("attaque-defense.defenseur");
        Object bridge = config.getList("attaque-defense.pont");
        String problem = checkAttackDefense(zone, bed, defender, bridge, spacing);
        if (problem != null) {
            getLogger().warning("attaque-defense in config.yml: " + problem + ". The attack and defense plays on the plugin's own island.");
            return AttackDefense.Base.plain();
        }
        int[] foot = numbers(bed, 3);
        int[] origin = {foot[0], foot[1] - 1, foot[2]};
        int[] box = numbers(zone, 6);
        int[] start = numbers(defender, 4);
        int[] ends = numbers(bridge, 6);
        MapCopy copy = MapCopy.read(world, box, origin, getLogger(), "the attack and defense");
        if (copy == null) {
            return AttackDefense.Base.plain();
        }
        double[] spot = {start[0] - origin[0] + 0.5, start[1] - origin[1], start[2] - origin[2] + 0.5, start[3]};
        int[] way = new int[6];
        for (int i = 0; i < 6; i++) {
            way[i] = ends[i] - origin[i % 3];
        }
        return new AttackDefense.Base(copy, null, spot, way);
    }

    /**
     * What is wrong with the attaque-defense section (zone, lit, defenseur, pont), or null. The
     * zone holds the bed and the defender's start, the bridge is a straight line at one height,
     * and all of it stays in its zone of the arena world: less than half the spacing from the bed.
     */
    static String checkAttackDefense(Object zone, Object bed, Object defender, Object bridge, int spacing) {
        int[] box = numbers(zone, 6);
        if (box == null) {
            return "zone: the two corners of the box copied, x, y, z each";
        }
        int[] foot = numbers(bed, 3);
        if (foot == null) {
            return "lit: x, y, z of the foot of the bed";
        }
        if (!inBox(foot, box)) {
            return "zone: the box holds the bed";
        }
        int[] start = numbers(defender, 4);
        if (start == null) {
            return "defenseur: x, y, z, yaw of the defender's start";
        }
        if (!inBox(start, box)) {
            return "defenseur: in the zone";
        }
        int[] ends = numbers(bridge, 6);
        if (ends == null) {
            return "pont: x, y, z of its far end, then of its near end";
        }
        if (ends[1] != ends[4] || (ends[0] != ends[3]) == (ends[2] != ends[5])) {
            return "pont: a straight line of blocks along x or z, at one height";
        }
        int half = spacing / 2 - 1;
        for (int i : new int[] {0, 2}) {
            for (int value : new int[] {box[i], box[i + 3], ends[i], ends[i + 3]}) {
                if (Math.abs(value - foot[i]) > half) {
                    return "zone and pont: at most " + half + " blocks from the bed on x and z (espacement / 2)";
                }
            }
        }
        return null;
    }

    private static boolean inBox(int[] place, int[] box) {
        for (int i = 0; i < 3; i++) {
            if (place[i] < Math.min(box[i], box[i + 3]) || place[i] > Math.max(box[i], box[i + 3])) {
                return false;
            }
        }
        return true;
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
            List<?> departs = entry.get("departs") instanceof List ? (List<?>) entry.get("departs") : new ArrayList<>();
            double[][] starts = new double[departs.size()][];
            for (int i = 0; i < starts.length; i++) {
                List<?> start = (List<?>) departs.get(i);
                starts[i] = new double[] {number(start.get(0)) - origin[0] + 0.5, number(start.get(1)) - origin[1],
                    number(start.get(2)) - origin[2] + 0.5, number(start.get(3))};
            }
            MapCopy copy = MapCopy.read(name, box, origin, getLogger(), what);
            if (copy != null) {
                maps.add(new MapZones.ZoneMap(name, copy, starts));
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
        Object departs = entry.get("departs");
        if (departs != null) {
            if (!(departs instanceof List)) {
                return "departs: a list of starts, x, y, z, yaw each";
            }
            for (Object start : (List<?>) departs) {
                int[] place = numbers(start, 4);
                if (place == null) {
                    return "departs: x, y, z, yaw for each start, whole numbers";
                }
                for (int i = 0; i < 3; i++) {
                    if (place[i] < Math.min(zone[i], zone[i + 3]) || place[i] > Math.max(zone[i], zone[i + 3])) {
                        return "departs: each start in the zone";
                    }
                }
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

    /** The host of the mode, or null when the lobby does not have the mode. */
    private PracticeHost register(String modeId, PracticeHost.Factory factory, int slots, int spacing, boolean open) {
        int row = FIRST_ROW_Z + ROW_SPACING * hosted.size();
        PracticeHost host = new PracticeHost(this, games, modeId, factory, new ArenaGrid(open ? 1 : slots, spacing), row, open);
        if (lobby.registerHost(this, modeId, host)) {
            hosted.add(modeId);
            return host;
        }
        // its row stays reserved, so that the other modes keep their zones
        hosted.add("(" + modeId + ")");
        getLogger().warning("The lobby has no mode \"" + modeId + "\" in its config.yml: it stays closed.");
        return null;
    }

    /**
     * The maps of the bridge fight (bridge-fight.cartes), without their barriers: a player knocked
     * off falls into the void, and no invisible wall stops the knockback. A map with no platform at
     * the height of its centre is left out.
     */
    private List<MapZones.ZoneMap> bridgeMaps(FileConfiguration config, int spacing) {
        List<MapZones.ZoneMap> kept = new ArrayList<>();
        for (MapZones.ZoneMap map : withoutBlocks(zoneMaps(config, "bridge-fight.cartes", spacing, "the bridge fight"),
                BridgeArena.BARRIER_ID)) {
            List<BridgeArena.Platform> platforms = BridgeArena.platformsOf(map.copy);
            if (platforms.isEmpty()) {
                getLogger().warning("bridge-fight.cartes in config.yml: \"" + map.name + "\" has no platform of wool or stained"
                        + " clay at the height of its centre. The map is left out.");
                continue;
            }
            getLogger().info("The bridge fight on \"" + map.name + "\": " + platforms.size() + " platforms, "
                    + BridgeArena.portalsOf(map.copy).size() + " portals.");
            kept.add(map);
        }
        return kept;
    }

    /** The maps without their blocks of this id (the gold that only marks the middle). */
    static List<MapZones.ZoneMap> withoutBlocks(List<MapZones.ZoneMap> maps, int id) {
        List<MapZones.ZoneMap> kept = new ArrayList<>();
        for (MapZones.ZoneMap map : maps) {
            kept.add(new MapZones.ZoneMap(map.name, map.copy.without(map.copy.find(id)), map.starts));
        }
        return kept;
    }

    /** The mode plays on these maps, if it is open and there are some. */
    private void onMaps(PracticeHost host, List<MapZones.ZoneMap> maps) {
        if (host != null) {
            MapZones zones = host.useMaps(maps);
            if (zones != null) {
                mapZones.add(zones);
            }
        }
    }

    @Override
    public void onDisable() {
        for (MapZones zones : mapZones) {
            zones.stop();
        }
        mapZones.clear();
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
            {"points-pour-gagner.block-fight", "100", "10", "500"},
            {"points-pour-gagner.top-fight", "3", "1", "10"},
            {"points-pour-gagner.block-edit", "5", "1", "20"},
            {"attaque-defense.attaque-secondes", "60", "15", "600"},
            {"attaque-defense.manches", "2", "2", "10"},
            {"aim-trainer.xp-par-seconde", "1", "0", "10"},
            {"aim-trainer.touches-par-xp", "10", "1", "100"},
            {"pearl.limite-basse", "55", "0", "70"},
            {"bridge-fight.protection-1v1", "2", "0", "4"},
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

    /** The running practice games, or null while the plugin is off. */
    PracticeGames games() {
        return games;
    }
}
