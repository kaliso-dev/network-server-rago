package gg.rago.bedwars;

import gg.rago.lobby.api.LobbyService;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RagoBedwars: the Bed Wars modes played from the lobby (Ranked Bedwars 1v1 to 4v4, RBW on Sky
 * Rise, First Rush), each on its own maps, set up with /bw or the map tool, each game in a copy of
 * its map. See the module README.
 */
public final class RagoBedwars extends JavaPlugin {

    /** After the start, the time before the first copy of a map is made, so that the first match does not wait. */
    private static final long PREWARM_TICKS = 100L;

    private LobbyService lobby;
    private BedwarsSettings settings;
    private ShopCatalog catalog;
    private ShopCatalog arenaCatalog;
    private FastShopRecords fastShopRecords;
    private ExecutorService io;
    private MapStore maps;
    private Instances instances;
    private BedwarsHost host;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lobby = getServer().getServicesManager().load(LobbyService.class);
        if (lobby == null) {
            getLogger().severe("RagoLobby is not running: RagoBedwars is disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            settings = BedwarsSettings.read(getConfig());
        } catch (IllegalArgumentException e) {
            getLogger().severe("config.yml is invalid, RagoBedwars is disabled: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        catalog = ShopCatalog.standard();
        arenaCatalog = ShopCatalog.arena();
        io = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "RagoBedwars-io");
            thread.setDaemon(true);
            return thread;
        });
        File container = getServer().getWorldContainer();
        maps = new MapStore(container, io, getLogger());
        fastShopRecords = new FastShopRecords(new File(getDataFolder(), "fastshop.yml"), io, getLogger());
        fastShopRecords.load();
        instances = new Instances(this, container, io, settings.maxGames());
        instances.deleteLeftovers();
        maps.loadAll();
        if (settings.demo() && DemoMap.buildIfMissing()) {
            BedwarsMap demo = maps.getOrCreate(DemoMap.WORLD);
            DemoMap.layout(demo);
            maps.save(demo);
            getLogger().info("Demo map " + DemoMap.WORLD + " created (carte-demo: false in config.yml turns it off)");
        }

        host = new BedwarsHost(this);
        getServer().getPluginManager().registerEvents(host, this);
        MapCommands commands = new MapCommands(this);
        PluginCommand bw = getCommand("bw");
        bw.setExecutor(commands);
        bw.setTabCompleter(commands);
        // the FFA: where one spawns (Archway FFA: Top bridge, Low, Mid or Random)
        getCommand("ffaspawn").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                return true;
            }
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
            BedwarsGame game = host.gameOf(player.getUniqueId());
            if (!(game instanceof FfaGame) || !((FfaGame) game).openSpawnMenu(player)) {
                player.sendMessage(org.bukkit.ChatColor.RED + "Only in the FFA, on a map with spawns to choose (Archway FFA).");
            }
            return true;
        });

        List<String> hosted = new ArrayList<>();
        for (String mode : settings.modes()) {
            if (lobby.registerHost(this, mode, host)) {
                hosted.add(mode);
            } else {
                getLogger().warning("The lobby has no mode \"" + mode + "\" in its config.yml: it stays closed.");
            }
        }
        List<BedwarsMap> playable = playableMaps();
        List<String> names = new ArrayList<>();
        for (BedwarsMap map : playable) {
            names.add(map.world());
        }
        getLogger().info("Bed Wars maps ready: " + (names.isEmpty() ? "none (set one up with /bw)" : String.join(", ", names))
                + "; modes " + hosted + "; " + settings.maxGames() + " games at once at most");
        for (String mode : hosted) {
            List<String> missing = new ArrayList<>();
            for (String map : settings.mapsOf(mode)) {
                if (maps.get(map) == null || !maps.get(map).playable()) {
                    missing.add(map);
                }
            }
            if (!missing.isEmpty()) {
                getLogger().warning("Mode " + mode + ": " + String.join(", ", missing) + " not ready (no folder with a "
                        + BedwarsMap.FILE_NAME + " in the worlds folder, or not complete): skipped");
            }
        }
        // the first copy is of a real map when there is one: the demo map only serves without them
        getServer().getScheduler().runTaskLater(this,
                () -> instances.prepare(MapStore.inAdvance(playableMaps()), false), PREWARM_TICKS);
    }

    @Override
    public void onDisable() {
        if (host != null) {
            host.abortAll();
            host = null;
        }
        // On the server's own thread (the stop command), the game worlds are unloaded and deleted.
        // Stopped by a signal, the server disables its plugins from another thread while its worlds
        // still tick: they are left as they are, and deleted at the next start.
        if (instances != null && getServer().isPrimaryThread()) {
            instances.unloadAll();
        }
        if (lobby != null) {
            lobby.unregisterHosts(this);
        }
        if (io != null) {
            // The map files being written are finished; the copies being made are deleted at the next start.
            io.shutdown();
            try {
                io.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Every map that can be played now, whatever its number of teams. */
    List<BedwarsMap> playableMaps() {
        List<BedwarsMap> playable = new ArrayList<>();
        for (BedwarsMap map : maps.all()) {
            if (map.playable()) {
                playable.add(map);
            }
        }
        return playable;
    }

    LobbyService lobby() {
        return lobby;
    }

    BedwarsSettings settings() {
        return settings;
    }

    ShopCatalog catalog() {
        return catalog;
    }

    /** The best times of Fast Shop. */
    FastShopRecords fastShopRecords() {
        return fastShopRecords;
    }

    /** The shop of the arena (Free FFA). */
    ShopCatalog arenaCatalog() {
        return arenaCatalog;
    }

    MapStore maps() {
        return maps;
    }

    Instances instances() {
        return instances;
    }

    BedwarsHost host() {
        return host;
    }
}
