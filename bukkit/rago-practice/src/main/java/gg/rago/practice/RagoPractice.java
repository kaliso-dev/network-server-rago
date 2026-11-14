package gg.rago.practice;

import gg.rago.game.ArenaGrid;
import gg.rago.lobby.api.LobbyService;
import gg.rago.lobby.api.VoidGenerator;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RagoPractice: the practice modes played from the lobby. Duels (sumo, boxing, KB displace, block
 * fight, fireball fight, block edit, bow duel), the trainings (clutch, block
 * in, fast defense, extends, timed bridge, ladder clutch, pearls, fireballs, aim, block edit), the bots, and the
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
    /** Those who watch a game with the lobby's /spectate. */
    private Spectators spectators;
    /** What the players of the duels had at their end, for the [View] of the recap. */
    private EndInventories endInventories;
    /** The events PREMIUM players host (/host). */
    private Events events;
    private Records records;
    /** /records and the wall of the records, once the modes are known; null before. */
    private RecordsMenu recordsMenu;
    private TrainingPrefs prefs;
    /** The challenge of the day (/challenge). */
    private Challenges challenges;
    private final List<String> hosted = new ArrayList<>();
    /** The Network XP of the trainings and the free arenas, given every two minutes. */
    private PracticeXp xp;
    /** How often the XP of the trainings goes to the lobby: two minutes. */
    static final long XP_PAY_TICKS = 2400L;
    /** How often the items of the players in a game are made unbreakable, the new ones (see Unbreakable). */
    static final long UNBREAKABLE_TICKS = 5L;

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
        int points = config.getInt("sumo.points-pour-gagner", 1);
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
        xp = new PracticeXp(xpRules(config));
        getServer().getScheduler().runTaskTimer(this, this::payXp, XP_PAY_TICKS, XP_PAY_TICKS);
        records = new Records(this, new File(getDataFolder(), "records.yml"));
        records.load();
        RecordCatalog.define(records.book());
        prefs = new TrainingPrefs(this, new File(getDataFolder(), "reglages.yml"));
        prefs.load();
        challenges = new Challenges(this, challengeSettings(config), new File(getDataFolder(), "defi.yml"));

        spectators = new Spectators(this);
        getServer().getPluginManager().registerEvents(spectators, this);
        endInventories = new EndInventories();
        getServer().getPluginManager().registerEvents(endInventories, this);
        getCommand("matchinv").setExecutor(endInventories);
        sumo = new SumoHost(this, world, new ArenaGrid(slots, spacing), radius, points, zoneMaps(config, "sumo.cartes", spacing,
                "the sumo"));
        getServer().getPluginManager().registerEvents(sumo, this);
        if (!lobby.registerHost(this, SumoHost.MODE_ID, sumo)) {
            getLogger().warning("The lobby has no mode \"" + SumoHost.MODE_ID + "\" in its config.yml: the sumo duel stays closed.");
        }

        games = new PracticeGames(this, world);
        getServer().getPluginManager().registerEvents(games, this);
        getServer().getScheduler().runTaskTimer(this, games::keepUnbreakable, UNBREAKABLE_TICKS, UNBREAKABLE_TICKS);
        events = new Events(this, eventSettings(config));
        getServer().getPluginManager().registerEvents(events, this);
        events.start();
        registerModes(config, world, slots, spacing);
        getCommand("host").setExecutor(events);
        getCommand("event").setExecutor(events);
        getCommand("join").setExecutor(events);
        getServer().getPluginManager().registerEvents(challenges, this);
        challenges.start(hosted);
        getCommand("challenge").setExecutor(challenges);
        getCommand("aimtop").setExecutor(new AimTop(this));
        getCommand("speed").setExecutor(new SpeedCommand(this));
        getCommand("center").setExecutor(new CenterCommand(this));
        startRecords(config);
        getLogger().info("Arenas in the world " + world.getName() + ": " + slots + " games of each mode at once at most. Modes: "
                + String.join(", ", hosted));
    }

    /**
     * /records, the wall of the records on its point of the lobby (mur-des-records, "" for none)
     * and the boards of the biggest clutches of the week, rewarded with the lobby's on Monday.
     */
    private void startRecords(FileConfiguration config) {
        recordsMenu = new RecordsMenu(this, RecordCatalog.played(hosted));
        getServer().getPluginManager().registerEvents(recordsMenu, this);
        getCommand("records").setExecutor(recordsMenu);
        String wall = config.getString("mur-des-records", "records");
        if (wall != null && !wall.trim().isEmpty()) {
            lobby.setLobbyBoard(this, wall.trim(), RecordsMenu.WALL_LINES, recordsMenu::wall, recordsMenu::wallClick);
        }
        if (hosted.contains("clutch")) {
            String board = "clutch." + ClutchTraining.ISLAND.id;
            lobby.addWeeklyBoard(this, "Biggest clutches", week -> ClutchTraining.weekRanking(records.book(), board, week));
        }
        if (hosted.contains("clutch-top-fight")) {
            String board = "clutch-top-fight." + ClutchTraining.TOP.id;
            lobby.addWeeklyBoard(this, "Biggest top fight clutches", week -> ClutchTraining.weekRanking(records.book(), board, week));
        }
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
        int edit = config.getInt("points-pour-gagner.block-edit", 5);
        int bridge = config.getInt("points-pour-gagner.bridge-fight", 5);
        int bridgeProtection = config.getInt("bridge-fight.protection-1v1", 2);
        int aimXp = config.getInt("aim-trainer.xp-par-seconde", 1);
        int aimHits = config.getInt("aim-trainer.touches-par-xp", 10);
        BlockInTraining.Island island = blockInIsland(config);

        List<MapZones.ZoneMap> arenas = zoneMaps(config, "boxing.cartes", spacing, "the boxing and the combo bot");
        onMaps(register("boxing", (h, m, a, t) -> new Boxing(h, m, a, t, boxing), slots, spacing, false), arenas);
        KbDisplace.Place kbPlace = kbDisplacePlace(config, spacing);
        register("kb-displace", (h, m, a, t) -> new KbDisplace(h, m, a, t, kb, kbPlace), slots, spacing, false);
        List<MapZones.ZoneMap> blockMaps = zoneMaps(config, "block-fight.cartes", spacing, "the block placement");
        onMaps(register("block-fight-1v1", (h, m, a, t) -> new BlockFight(h, m, a, t, blocks), slots, spacing, false), blockMaps);
        onMaps(register("block-fight-2v2", (h, m, a, t) -> new BlockFight(h, m, a, t, blocks), slots, spacing, false), blockMaps);
        // the rows of the old Top Fight, and of Attack and Defense (on the owner's maps, in the Bed
        // Wars plugin), stay reserved, so that the other modes keep their zones
        hosted.add("(top-fight-1v1)");
        hosted.add("(top-fight-2v2)");
        hosted.add("(attack-defense-1v1)");
        hosted.add("(attack-defense-2v2)");
        onMaps(register("fireball", FireballFight::new, slots, spacing, false),
                zoneMaps(config, "fireball.cartes", spacing, "the fireball fight"));
        // the islands side by side, close enough to see the players of the next ones
        ClutchTraining.Island clutchIsland = clutchIsland(config);
        register("clutch", (h, m, a, t) -> new ClutchTraining(h, m, a, t, false, clutchIsland), slots, ClutchTraining.SPACING, false);
        register("block-in", (h, m, a, t) -> new BlockInTraining(h, m, a, t, island), slots, spacing, false);
        register("fast-defense", FastDefenseTraining::new, slots, spacing, false);
        register("extend-practice", ExtendTraining::new, slots, spacing, false);
        register("ladder-practice", LadderTraining::new, slots, spacing, false);
        PearlTraining.Places pearlPlaces = pearlPlaces(config);
        register("pearl-practice", (h, m, a, t) -> new PearlTraining(h, m, a, t, pearlPlaces), slots, spacing, false);
        register("fireball-practice", FireballTraining::new, slots, spacing, false);
        register("aim-trainer", (h, m, a, t) -> new AimTrainer(h, m, a, t, aimXp, aimHits), slots, spacing, false);
        onMaps(register("combo-bot", ComboBot::new, slots, spacing, false), arenas);
        register("kb-displace-bot", (h, m, a, t) -> new KbDisplaceBot(h, m, a, t, kbPlace), slots, spacing, false);
        List<MapZones.ZoneMap> arenaMaps = withoutBlocks(zoneMaps(config, "block-fight-arena.cartes", spacing,
                "the block placement arena"), GOLD_ID);
        onMaps(register("block-fight-arena", (h, m, a, t) -> new BlockArena(h, m, a, t, false), slots, spacing, true), arenaMaps);
        // Arena Free: the same arena of the owner, with its slime, for a free for all fight
        onMaps(register("free-ffa", (h, m, a, t) -> new BlockArena(h, m, a, t, true), slots, spacing, true), arenaMaps);
        onMaps(register("bridge-fight", BridgeArena::new, slots, spacing, true), bridgeMaps(config, spacing));
        // the row of the old Ranked scenarios stays reserved, so that the other modes keep their zones
        hosted.add("(scenarios-ranked)");
        register("clutch-top-fight", (h, m, a, t) -> new ClutchTraining(h, m, a, t, true, null), slots, ClutchTraining.SPACING, false);
        register("block-edit", BlockEditTraining::new, slots, spacing, false);
        register("block-edit-duel", (h, m, a, t) -> new BlockEditDuel(h, m, a, t, edit), slots, spacing, false);
        register("bridge-fight-1v1", (h, m, a, t) -> new BridgeDuel(h, m, a, t, bridge, bridgeProtection), slots, spacing, false);
        BlockExtendTraining.Course course = blockExtendCourse(config, spacing);
        register("block-extend", (h, m, a, t) -> new BlockExtendTraining(h, m, a, t, course), slots, spacing, false);
        BowTraining.Course bow = bowCourse(config, spacing);
        int bowHits = config.getInt("points-pour-gagner.bow", 10);
        register("bow-training", (h, m, a, t) -> new BowTraining(h, m, a, t, bow), slots, spacing, false);
        register("bow-duel", (h, m, a, t) -> new BowDuel(h, m, a, t, bowHits, bow), slots, spacing, false);
        // the events of /host: one game at a time each, on the sumo maps
        List<MapZones.ZoneMap> eventMaps = zoneMaps(config, "sumo.cartes", spacing, "the events");
        for (EventGame.Kind kind : EventGame.Kind.values()) {
            onMaps(register(kind.modeId(), (h, m, a, t) -> events.created(new EventGame(h, m, a, t, events)), slots, spacing, true),
                    eventMaps);
        }
        // the bridge fight in 2v2, as the 1v1 (each team side by side on its pad); last, so that
        // the other modes keep their zones
        register("bridge-fight-2v2", (h, m, a, t) -> new BridgeDuel(h, m, a, t, bridge, bridgeProtection), slots, spacing, false);
        // the timed bridge, its twelve courses side by side; last too
        register("bridge-chrono", BridgeTraining::new, slots, spacing, false);
    }

    /**
     * The place of the bow training and the bow duel (bow-training in config.yml): a part of a Bed
     * Wars map, the player's start and the bridges of the bots. With a mistake in the section, or
     * without the world, the plugin's own platforms at the same places.
     */
    private BowTraining.Course bowCourse(FileConfiguration config, int spacing) {
        ConfigurationSection section = config.getConfigurationSection("bow-training");
        String problem = section == null ? "the section is missing" : checkBow(section, spacing);
        if (problem != null) {
            getLogger().warning("bow-training in config.yml: " + problem + ". The bow training plays on the plugin's own platforms.");
            return BowTraining.plainCourse();
        }
        int[] origin = numbers(section.getList("centre"), 3);
        List<?> start = section.getList("joueur");
        double[] player = new double[4];
        for (int k = 0; k < 4; k++) {
            player[k] = number(start.get(k)) - (k < 3 ? origin[k] : 0);
        }
        List<?> bridges = section.getList("ponts");
        int[][] lines = new int[bridges.size()][];
        for (int i = 0; i < lines.length; i++) {
            int[] line = numbers(bridges.get(i), 4);
            lines[i] = new int[] {line[0] - origin[0], line[1] - origin[2], line[2] - origin[0], line[3] - origin[2]};
        }
        BowTraining.Course plain = new BowTraining.Course(null, player, section.getInt("pont-y") - origin[1], lines);
        MapCopy copy = MapCopy.read(section.getString("monde", ""), numbers(section.getList("zone"), 6), origin, getLogger(),
                "the bow training");
        return copy == null ? plain : new BowTraining.Course(copy, player, plain.bridgeY, lines);
    }

    /**
     * The island of the block clutch (clutch in config.yml): the owner's island copied once, and
     * where the player stands. With a mistake in the section, or without the world, the plugin's
     * own island.
     */
    private ClutchTraining.Island clutchIsland(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("clutch");
        String problem = section == null ? "the section is missing" : checkClutch(section);
        if (problem != null) {
            getLogger().warning("clutch in config.yml: " + problem + ". The block clutch plays on the plugin's own island.");
            return ClutchTraining.plainIsland();
        }
        int[] origin = numbers(section.getList("centre"), 3);
        List<?> start = section.getList("joueur");
        double[] player = new double[4];
        for (int k = 0; k < 4; k++) {
            player[k] = number(start.get(k)) - (k < 3 ? origin[k] : 0);
        }
        MapCopy copy = MapCopy.read(section.getString("monde", ""), numbers(section.getList("zone"), 6), origin, getLogger(),
                "the block clutch");
        return copy == null ? ClutchTraining.plainIsland() : new ClutchTraining.Island(copy, player);
    }

    /**
     * What is wrong with the clutch section, or null. The islands stand ClutchTraining.SPACING
     * apart, so the zone fits in that.
     */
    static String checkClutch(ConfigurationSection section) {
        String world = section.getString("monde", "");
        Map<String, Object> zoneMap = new HashMap<>();
        zoneMap.put("monde", world.trim().isEmpty() ? "-" : world);
        zoneMap.put("centre", section.getList("centre"));
        zoneMap.put("zone", section.getList("zone"));
        String zoneProblem = checkZoneMap(zoneMap, ClutchTraining.SPACING);
        if (zoneProblem != null) {
            return zoneProblem;
        }
        if (!numbersOf(section.getList("joueur"), 4)) {
            return "joueur: x, y, z, yaw of where the player stands";
        }
        int[] zone = numbers(section.getList("zone"), 6);
        List<?> start = section.getList("joueur");
        for (int i = 0; i < 3; i++) {
            double at = number(start.get(i));
            if (at < Math.min(zone[i], zone[i + 3]) || at > Math.max(zone[i], zone[i + 3]) + 1) {
                return "joueur: in the zone";
            }
        }
        return null;
    }

    /** What is wrong with the bow-training section, or null. */
    static String checkBow(ConfigurationSection section, int spacing) {
        String world = section.getString("monde", "");
        Map<String, Object> zoneMap = new HashMap<>();
        zoneMap.put("monde", world.trim().isEmpty() ? "-" : world);
        zoneMap.put("centre", section.getList("centre"));
        zoneMap.put("zone", section.getList("zone"));
        String zoneProblem = checkZoneMap(zoneMap, spacing);
        if (zoneProblem != null) {
            return zoneProblem;
        }
        if (!numbersOf(section.getList("joueur"), 4)) {
            return "joueur: x, y, z, yaw of the player's start";
        }
        if (!section.isInt("pont-y")) {
            return "pont-y: the height of the blocks of the bridges";
        }
        List<?> bridges = section.getList("ponts");
        if (bridges == null || bridges.isEmpty() || bridges.size() > 3) {
            return "ponts: one to three bridges";
        }
        for (Object bridge : bridges) {
            int[] line = numbers(bridge, 4);
            if (line == null || line[0] == line[2] && line[1] == line[3]) {
                return "ponts: x, z of the first block of a bridge then of its last one, whole numbers";
            }
        }
        return null;
    }

    /**
     * The course of the block extend (block-extend): the owner's map copied once, its gold, diamond
     * and emerald markers turned to wool, and its three levels (or, diamant, emeraude). With a
     * mistake in the section, the plugin's own levels on plain blocks; without the world, the
     * levels of the section on plain blocks.
     */
    /** The place of the KB displace: the owner's map of the section kb-displace, or the plugin's own bridge. */
    private KbDisplace.Place kbDisplacePlace(FileConfiguration config, int spacing) {
        ConfigurationSection section = config.getConfigurationSection("kb-displace");
        if (section == null) {
            return KbDisplace.Place.PLAIN;
        }
        String problem = checkKbDisplace(section, spacing);
        if (problem != null) {
            getLogger().warning("kb-displace in config.yml: " + problem + ". The KB displace plays on the plugin's own bridge.");
            return KbDisplace.Place.PLAIN;
        }
        int[] origin = numbers(section.getList("centre"), 3);
        MapCopy copy = MapCopy.read(section.getString("monde", ""), numbers(section.getList("zone"), 6), origin, getLogger(),
                "the KB displace");
        if (copy == null) {
            return KbDisplace.Place.PLAIN;
        }
        return new KbDisplace.Place(copy, offsets(section.getList("attaquant"), origin), offsets(section.getList("defenseur"), origin));
    }

    /** x, y, z, yaw of the config, x, y, z made offsets from the origin. */
    private static double[] offsets(List<?> place, int[] origin) {
        double[] at = new double[4];
        for (int k = 0; k < 4; k++) {
            at[k] = number(place.get(k)) - (k < 3 ? origin[k] : 0);
        }
        return at;
    }

    /** What is wrong with the kb-displace section, or null. */
    static String checkKbDisplace(ConfigurationSection section, int spacing) {
        Map<String, Object> zoneMap = new HashMap<>();
        String world = section.getString("monde", "");
        zoneMap.put("monde", world.trim().isEmpty() ? "-" : world);
        zoneMap.put("centre", section.getList("centre"));
        zoneMap.put("zone", section.getList("zone"));
        String zoneProblem = checkZoneMap(zoneMap, spacing);
        if (zoneProblem != null) {
            return zoneProblem;
        }
        int[] zone = numbers(section.getList("zone"), 6);
        for (String key : new String[] {"attaquant", "defenseur"}) {
            if (!numbersOf(section.getList(key), 4)) {
                return key + ": x, y, z, yaw, as F3 gives them";
            }
            List<?> place = section.getList(key);
            for (int i = 0; i < 3; i++) {
                double v = number(place.get(i));
                if (v < Math.min(zone[i], zone[i + 3]) || v > Math.max(zone[i], zone[i + 3]) + 1) {
                    return key + ": in the zone";
                }
            }
        }
        List<?> a = section.getList("attaquant");
        List<?> d = section.getList("defenseur");
        double run = Math.hypot(number(a.get(0)) - number(d.get(0)), number(a.get(2)) - number(d.get(2)));
        if (run < 3 || run > 30) {
            return "attaquant: from 3 to 30 blocks from the defender, for the run";
        }
        return null;
    }

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
     * The places of the pearl practice: a part of each map of pearl.cartes the server has (its
     * world in the folder of the worlds, with the bedwars.yml of the Bed Wars plugin), around the
     * base of a team drawn at each start, its diamond islands and the middle, each read once.
     * Without any of them, the plugin's own islands.
     */
    private PearlTraining.Places pearlPlaces(FileConfiguration config) {
        List<PearlMaps.Part> parts = new ArrayList<>();
        List<String> absent = new ArrayList<>();
        Random random = new Random();
        for (String name : config.getStringList("pearl.cartes")) {
            File folder = new File(getServer().getWorldContainer(), name);
            File points = new File(folder, "bedwars.yml");
            if (!new File(folder, "level.dat").isFile() || !points.isFile()) {
                absent.add(name);
                continue;
            }
            List<PearlMaps.Anchors> sides = pearlAnchors(YamlConfiguration.loadConfiguration(points));
            if (sides.isEmpty()) {
                getLogger().warning("The pearl practice: the bedwars.yml of \"" + name + "\" has no team with a spawn, a bed and"
                        + " a generator, the map is left out.");
                continue;
            }
            Collections.shuffle(sides, random);
            PearlMaps.Part part = null;
            // the side of a team drawn at random; another one if no situation holds on it
            for (int i = 0; i < Math.min(2, sides.size()) && part == null; i++) {
                int[][] box = PearlMaps.box(sides.get(i));
                MapCopy copy = MapCopy.read(name, box[0], box[1], getLogger(), "the pearl practice");
                if (copy == null) {
                    break;
                }
                part = PearlMaps.analyse(name, sides.get(i), copy, box[0], box[1]);
            }
            if (part == null) {
                getLogger().warning("The pearl practice: no situation found on \"" + name + "\" (no diamond island near a base,"
                        + " or gaps too long for a pearl), the map is left out.");
                continue;
            }
            getLogger().info("The pearl practice on \"" + name + "\", the side of " + part.team + ": "
                    + part.of(PearlMaps.Kind.PEARL).size() + " pearls, " + part.of(PearlMaps.Kind.SAVE).size() + " saves, "
                    + part.of(PearlMaps.Kind.CLUTCH).size() + " pearl clutches (" + part.copy.size() + " blocks).");
            parts.add(part);
        }
        if (!absent.isEmpty()) {
            getLogger().info("The pearl practice: no world with its bedwars.yml in the folder of the worlds for " + absent
                    + ", these maps are left out.");
        }
        if (parts.isEmpty()) {
            getLogger().info("The pearl practice plays on the plugin's own islands: none of the maps of pearl.cartes is here.");
        }
        return new PearlTraining.Places(parts);
    }

    /** The colours of the teams of the Bed Wars plugin, and the data of their wool. */
    private static final String[] COLOURS = {"red", "blue", "green", "yellow", "aqua", "white", "pink", "gray"};
    private static final int[] WOOL = {14, 11, 5, 4, 9, 0, 6, 7};

    /** The teams of a map's bedwars.yml with a spawn, a bed and a generator, with the diamonds and the emeralds. */
    static List<PearlMaps.Anchors> pearlAnchors(ConfigurationSection yaml) {
        List<double[]> diamonds = points(yaml.getMapList("diamants"));
        List<double[]> emeralds = points(yaml.getMapList("emeraudes"));
        List<PearlMaps.Anchors> sides = new ArrayList<>();
        ConfigurationSection teams = yaml.getConfigurationSection("equipes");
        if (teams == null) {
            return sides;
        }
        for (String colour : teams.getKeys(false)) {
            double[] spawn = point(teams.getConfigurationSection(colour + ".spawn"));
            double[] bed = point(teams.getConfigurationSection(colour + ".lit"));
            double[] generator = point(teams.getConfigurationSection(colour + ".generateur"));
            if (spawn == null || bed == null || generator == null) {
                continue;
            }
            int wool = 0;
            for (int i = 0; i < COLOURS.length; i++) {
                if (COLOURS[i].equals(colour)) {
                    wool = WOOL[i];
                }
            }
            sides.add(new PearlMaps.Anchors(colour, wool, spawn, bed, generator, diamonds, emeralds));
        }
        return sides;
    }

    private static double[] point(ConfigurationSection section) {
        if (section == null || !section.isSet("x") || !section.isSet("y") || !section.isSet("z")) {
            return null;
        }
        return new double[] {section.getDouble("x"), section.getDouble("y"), section.getDouble("z")};
    }

    private static List<double[]> points(List<Map<?, ?>> list) {
        List<double[]> points = new ArrayList<>();
        for (Map<?, ?> entry : list) {
            Object x = entry.get("x");
            Object y = entry.get("y");
            Object z = entry.get("z");
            if (x instanceof Number && y instanceof Number && z instanceof Number) {
                points.add(new double[] {((Number) x).doubleValue(), ((Number) y).doubleValue(), ((Number) z).doubleValue()});
            }
        }
        return points;
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
            List<?> addedBeds = entry.get("lits") instanceof List ? (List<?>) entry.get("lits") : new ArrayList<>();
            if (copy != null && !addedBeds.isEmpty()) {
                List<int[]> beds = new ArrayList<>();
                for (Object bed : addedBeds) {
                    int[] at = numbers(bed, 5);
                    beds.add(new int[] {at[0] - origin[0], at[1] - origin[1], at[2] - origin[2], at[3] - origin[0], at[4] - origin[2]});
                }
                copy = copy.withBeds(beds);
            }
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
        Object beds = entry.get("lits");
        if (beds != null) {
            if (!(beds instanceof List)) {
                return "lits: a list of beds, x, y, z of the foot and x, z of the head each";
            }
            for (Object bed : (List<?>) beds) {
                int[] at = numbers(bed, 5);
                if (at == null || Math.abs(at[3] - at[0]) + Math.abs(at[4] - at[2]) != 1) {
                    return "lits: x, y, z of the foot and x, z of the head (next to it) for each bed";
                }
                int[][] cells = {{at[0], at[1], at[2]}, {at[3], at[1], at[4]}};
                for (int[] cell : cells) {
                    for (int i = 0; i < 3; i++) {
                        if (cell[i] < Math.min(zone[i], zone[i + 3]) || cell[i] > Math.max(zone[i], zone[i + 3])) {
                            return "lits: each bed in the zone";
                        }
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

    /** The XP of the trainings, as the settings give it. */
    static PracticeXp.Rules xpRules(FileConfiguration config) {
        return new PracticeXp.Rules(config.getInt("xp-entrainement.par-minute", 1), config.getInt("xp-entrainement.essai", 1),
                config.getInt("xp-entrainement.reussite", 3), config.getInt("xp-entrainement.max-par-minute", 8));
    }

    PracticeXp xp() {
        return xp;
    }

    /** The settings of the events (section evenements); the defaults when they are wrong. */
    private Events.Settings eventSettings(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("evenements");
        String problem = Events.Settings.check(section);
        if (problem != null) {
            getLogger().warning("config.yml: " + problem + ". The events use their default settings.");
            return Events.Settings.of(null);
        }
        return Events.Settings.of(section);
    }

    private DailyChallenge.Settings challengeSettings(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("defi-du-jour");
        String problem = DailyChallenge.Settings.check(section);
        if (problem != null) {
            getLogger().warning("config.yml: " + problem + ". There is no challenge of the day.");
            return DailyChallenge.Settings.OFF;
        }
        return DailyChallenge.Settings.of(section);
    }

    /** The challenge of the day, or null while the plugin is off. */
    Challenges challenges() {
        return challenges;
    }

    /** The events of /host, or null while the plugin is off. */
    Events events() {
        return events;
    }

    /** Gives the lobby the XP waiting for every player. */
    void payXp() {
        if (xp == null) {
            return;
        }
        for (UUID player : xp.waiting()) {
            payXp(player);
        }
        xp.prune(System.currentTimeMillis());
    }

    /** Gives the lobby the XP waiting for this player (when they leave a training, an arena). */
    void payXp(UUID player) {
        if (xp == null || lobby == null) {
            return;
        }
        String modeId = xp.modeOf(player);
        int earned = xp.take(player);
        if (earned > 0 && modeId != null) {
            lobby.giveXp(modeId, player, earned, "practice");
        }
    }

    @Override
    public void onDisable() {
        payXp();
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
        events = null;
        if (lobby != null) {
            lobby.setDailyChallenge(null, null);
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
            {"points-pour-gagner.block-edit", "5", "1", "20"},
            {"aim-trainer.xp-par-seconde", "1", "0", "10"},
            {"aim-trainer.touches-par-xp", "10", "1", "100"},
            {"bridge-fight.protection-1v1", "2", "0", "4"},
            {"xp-entrainement.par-minute", "1", "0", "20"},
            {"xp-entrainement.essai", "1", "0", "20"},
            {"xp-entrainement.reussite", "3", "0", "50"},
            {"xp-entrainement.max-par-minute", "8", "0", "100"},
            {"points-pour-gagner.bow", "10", "1", "50"},
        };
        for (String[] range : ranges) {
            int value = config.getInt(range[0], Integer.parseInt(range[1]));
            if (value < Integer.parseInt(range[2]) || value > Integer.parseInt(range[3])) {
                return range[0] + ": from " + range[2] + " to " + range[3];
            }
        }
        return null;
    }

    LobbyService lobby() {
        return lobby;
    }

    Records records() {
        return records;
    }

    /** /records and the wall of the records, or null while the plugin starts. */
    RecordsMenu recordsMenu() {
        return recordsMenu;
    }

    TrainingPrefs prefs() {
        return prefs;
    }

    /** The running practice games, or null while the plugin is off. */
    PracticeGames games() {
        return games;
    }

    /** Those who watch a game (/spectate). */
    Spectators spectators() {
        return spectators;
    }

    /** What the players of the duels had at their end, or null while the plugin starts. */
    EndInventories endInventories() {
        return endInventories;
    }
}
