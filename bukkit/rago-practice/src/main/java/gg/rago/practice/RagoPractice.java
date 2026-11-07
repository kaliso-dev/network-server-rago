package gg.rago.practice;

import gg.rago.game.ArenaGrid;
import gg.rago.lobby.api.LobbyService;
import gg.rago.lobby.api.VoidGenerator;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RagoPractice: the practice modes played from the lobby. For now the sumo duel, in arena slots of
 * an empty world. See the module README.
 */
public final class RagoPractice extends JavaPlugin {

    /** Height of the arena platforms. */
    static final int ARENA_Y = VoidGenerator.PLATFORM_Y;

    private LobbyService lobby;
    private SumoHost sumo;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lobby = getServer().getServicesManager().load(LobbyService.class);
        if (lobby == null) {
            getLogger().severe("RagoLobby n'est pas actif : RagoPractice est désactivé.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        String worldName = getConfig().getString("monde-arenes", "arenes");
        int spacing = getConfig().getInt("espacement", 200);
        int slots = getConfig().getInt("arenes-max", 16);
        int radius = getConfig().getInt("sumo.rayon", 7);
        int points = getConfig().getInt("sumo.points-pour-gagner", 3);
        String problem = check(worldName, spacing, slots, radius, points);
        if (problem != null) {
            getLogger().severe("config.yml est invalide, RagoPractice est désactivé : " + problem);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        World world = getServer().getWorld(worldName);
        if (world == null) {
            world = new WorldCreator(worldName).generator(new VoidGenerator()).generateStructures(false).createWorld();
        }
        world.setPVP(true);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setTime(6000L);

        sumo = new SumoHost(this, world, new ArenaGrid(slots, spacing), radius, points);
        getServer().getPluginManager().registerEvents(sumo, this);
        if (!lobby.registerHost(this, SumoHost.MODE_ID, sumo)) {
            getLogger().warning("Le lobby n'a pas de mode « " + SumoHost.MODE_ID + " » dans sa config.yml : le duel sumo reste fermé.");
        }
        getLogger().info("Arènes dans le monde " + world.getName() + " : " + slots + " parties à la fois au plus");
    }

    @Override
    public void onDisable() {
        if (sumo != null) {
            sumo.abortAll();
            sumo = null;
        }
        if (lobby != null) {
            lobby.unregisterHosts(this);
        }
    }

    /** What is wrong with these settings, in French, or null. */
    static String check(String world, int spacing, int slots, int radius, int points) {
        if (world == null || !world.matches("[A-Za-z0-9_-]{1,32}")) {
            return "monde-arenes : « " + world + " » n'est pas un nom de monde valide";
        }
        if (spacing < 100) {
            return "espacement : 100 blocs au moins, pour que deux parties ne se voient pas";
        }
        if (slots < 1 || slots > 64) {
            return "arenes-max : entre 1 et 64";
        }
        if (radius < 4 || radius > 15) {
            return "sumo.rayon : entre 4 et 15";
        }
        if (spacing < 2 * radius + 100) {
            return "espacement : trop petit pour ce rayon";
        }
        if (points < 1 || points > 10) {
            return "sumo.points-pour-gagner : entre 1 et 10";
        }
        return null;
    }

    LobbyService lobby() {
        return lobby;
    }
}
