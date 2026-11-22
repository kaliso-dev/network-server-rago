package gg.rago.bedwars;

import gg.rago.game.RedRoverRotation;
import gg.rago.lobby.api.LobbyService;
import gg.rago.lobby.api.ModeRecord;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RagoBedwars: the Bed Wars modes played from the lobby (Ranked Bedwars 1v1 to 4v4, RBW on Sky
 * Rise, First Rush), each on its own maps, set up with /bw or the map tool, each game in a copy of
 * its map. See the module README.
 */
public final class RagoBedwars extends JavaPlugin {

    /** How often the items of the players in a game are made unbreakable, the new ones (see Unbreakable). */
    static final long UNBREAKABLE_TICKS = 5L;

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
    /** Those who watch a game with the lobby's /spectate. */
    private Watchers watchers;
    /** The last problem told about the Red Rover settings, not told again. */
    private String redRoverWarned;

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
        // the maps of Capture the Flag, made the first time a mode of config.yml plays them
        ctfMap(CtfMaps.BRIDGE, CtfMaps::bridgePlan, CtfMaps::bridgeLayout);

        watchers = new Watchers(this);
        getServer().getPluginManager().registerEvents(watchers, this);
        host = new BedwarsHost(this);
        getServer().getPluginManager().registerEvents(host, this);
        if (lobby.replays() != null) {
            // the replays of the games (/replay) are shown in a free copy of their map
            lobby.replays().registerStages(this, ReplayWorlds.KIND, new ReplayWorlds(this));
        }
        getServer().getScheduler().runTaskTimer(this, host::keepUnbreakable, UNBREAKABLE_TICKS, UNBREAKABLE_TICKS);
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

        // Attack and Defense, the Bed Break Simulator: the kit of the round (the menu opens by itself before each round)
        getCommand("kit").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                return true;
            }
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
            BedwarsGame game = host.gameOf(player.getUniqueId());
            if (!(game instanceof KitMenu.Chooser) || !((KitMenu.Chooser) game).openKitMenu(player)) {
                player.sendMessage(org.bukkit.ChatColor.RED + "Only in Attack and Defense and the Bed Break Simulator: the kits"
                        + " of the round.");
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
        // under Fast Shop in the menus of the lobby: the record of its first list, in the place of the Daily Streak
        lobby.setModeRecords(this, this::modeRecord);
        // Once: the kills and deaths of the Bed Wars modes start again from 0, since a final kill
        // no longer counts as a kill too and a death in the void without a killer counts (21/11/2026)
        lobby.resetKillsOnce("kills-bedwars-2026-11", settings.modes());
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

    /**
     * A map of Capture the Flag ({@link CtfMaps}), built and set up the first time a mode of
     * config.yml lists it; left as it is once its world exists (its points can be changed with /bw).
     */
    private void ctfMap(String name, Supplier<CtfMaps.Plan> plan, Consumer<BedwarsMap> layout) {
        if (!settings.playsMap(name) || !CtfMaps.buildIfMissing(name, plan)) {
            return;
        }
        BedwarsMap map = maps.getOrCreate(name);
        layout.accept(map);
        maps.save(map);
        getLogger().info("Capture the Flag map " + name + " created");
    }

    @Override
    public void onDisable() {
        if (lobby != null && lobby.replays() != null) {
            // the replays watched in the game worlds end first, before the worlds go
            lobby.replays().unregisterStages(this);
        }
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

    /**
     * The settings of every Red Rover, for Red Rover Top Bridge: red-rover in the config.yml of
     * RagoPractice, where the owner sets those of all the Red Rovers; the defaults without it, or
     * when they are wrong (told once in the log).
     */
    RedRoverRotation.Settings redRover() {
        ConfigurationSection section = redRoverSection();
        RedRoverRotation.Settings rover = section == null ? null : RedRoverTopBridgeGame.settingsOf(section);
        if (rover == null) {
            warnRedRover(section == null ? "no red-rover in the config.yml of RagoPractice (is it installed?)"
                    : RedRoverTopBridgeGame.problemOf(section));
            return RedRoverRotation.Settings.DEFAULTS;
        }
        return rover;
    }

    /** The rounds that win a 1v1 of Red Rover Top Bridge (red-rover.points-par-1v1-top-bridge of RagoPractice). */
    int redRoverRounds() {
        ConfigurationSection section = redRoverSection();
        int rounds = section == null ? RedRoverTopBridgeGame.ROUNDS_PER_1V1 : RedRoverTopBridgeGame.roundsOf(section);
        if (rounds < 0) {
            warnRedRover("points-par-1v1-top-bridge must be from 1 to " + RedRoverTopBridgeGame.MAX_ROUNDS_PER_1V1);
            return RedRoverTopBridgeGame.ROUNDS_PER_1V1;
        }
        return rounds;
    }

    /** red-rover in the config.yml of RagoPractice, or null. */
    private ConfigurationSection redRoverSection() {
        Plugin practice = getServer().getPluginManager().getPlugin("RagoPractice");
        return practice == null ? null : practice.getConfig().getConfigurationSection("red-rover");
    }

    private void warnRedRover(String problem) {
        if (!problem.equals(redRoverWarned)) {
            redRoverWarned = problem;
            getLogger().warning("Red Rover Top Bridge: " + problem + ". It plays with the default settings of Red Rover.");
        }
    }

    ShopCatalog catalog() {
        return catalog;
    }

    /** The best times of Fast Shop. */
    FastShopRecords fastShopRecords() {
        return fastShopRecords;
    }

    /** Rows of the top under Fast Shop in the menus of the lobby (plan 3.4: a top 3). */
    static final int MODE_TOP = 3;

    /**
     * The record the menus of the lobby show under a Fast Shop mode, in the place of its Daily
     * Streak (plan 3.4), lifetime: its first list, the one each game starts on, as a training
     * shows the variant it starts on. Null for the other modes: they keep their Daily Streak.
     */
    ModeRecord modeRecord(String modeId, UUID viewer) {
        if (settings.kind(modeId) != BedwarsSettings.Kind.FAST_SHOP) {
            return null;
        }
        FastShopGame.Order first = FastShopGame.ORDERS.get(0);
        return fastShopRecords.modeRecord(first.id, first.name, viewer, MODE_TOP);
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

    /** Those who watch a game (/spectate). */
    Watchers watchers() {
        return watchers;
    }
}
