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
import java.util.logging.Level;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RagoPractice: the practice modes played from the lobby. Duels (sumo, boxing, KB displace, bridge
 * fight, block fight, top fight, attack and defense, fireball fight), the trainings (clutch, block
 * in, fast defense, timed bridge, ladders, pearls, fireballs, aim), the bots, and the open block
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

        sumo = new SumoHost(this, world, new ArenaGrid(slots, spacing), radius, points);
        getServer().getPluginManager().registerEvents(sumo, this);
        if (!lobby.registerHost(this, SumoHost.MODE_ID, sumo)) {
            getLogger().warning("The lobby has no mode \"" + SumoHost.MODE_ID + "\" in its config.yml: the sumo duel stays closed.");
        }

        games = new PracticeGames(this, world);
        getServer().getPluginManager().registerEvents(games, this);
        registerModes(config, world, slots, spacing);
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

        register("boxing", (h, m, a, t) -> new Boxing(h, m, a, t, boxing), slots, spacing, false);
        register("kb-displace", (h, m, a, t) -> new KbDisplace(h, m, a, t, kb), slots, spacing, false);
        register("bridge-fight", (h, m, a, t) -> new BridgeFight(h, m, a, t, bridge), slots, spacing, false);
        register("block-fight-1v1", (h, m, a, t) -> new BlockFight(h, m, a, t, blocks), slots, spacing, false);
        register("block-fight-2v2", (h, m, a, t) -> new BlockFight(h, m, a, t, blocks), slots, spacing, false);
        register("top-fight-1v1", (h, m, a, t) -> new TopFight(h, m, a, t, top), slots, spacing, false);
        register("top-fight-2v2", (h, m, a, t) -> new TopFight(h, m, a, t, top), slots, spacing, false);
        register("top-fight-prive", (h, m, a, t) -> new TopFight(h, m, a, t, top), slots, spacing, false);
        register("attack-defense-1v1", (h, m, a, t) -> new AttackDefense(h, m, a, t, build, attack, rounds), slots, spacing, false);
        register("attack-defense-2v2", (h, m, a, t) -> new AttackDefense(h, m, a, t, build, attack, rounds), slots, spacing, false);
        register("fireball", FireballFight::new, slots, spacing, false);
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
