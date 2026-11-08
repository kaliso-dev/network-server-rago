package gg.rago.lobby;

import gg.rago.game.GameMode;
import gg.rago.lobby.api.LobbyService;
import gg.rago.lobby.api.MatchHost;
import gg.rago.lobby.api.VoidGenerator;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

/**
 * RagoLobby: the main lobby of Rago Practice. The spawn and its protection, the hotbar, the mode
 * selector and its local queues, the sidebar, and hiding players. The game plugins plug into it
 * through {@link LobbyService}. See the module README.
 */
public final class RagoLobby extends JavaPlugin implements LobbyService {

    private LobbySettings settings;
    private LobbyState state;
    private Visibility visibility;
    private QueueController queues;
    private ModeMenu menu;
    private LobbyScoreboard scoreboard;
    /** Writes files (spawn.yml, points.yml) away from the main thread, one at a time. */
    private ExecutorService writer;
    private LobbyPoints points;
    private LobbyNpcs npcs;
    private FakeEntities fakes;
    private DuelRequests duels;
    private StatsService stats;
    private StatsDisplays statsDisplays;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            settings = LobbySettings.load(getConfig());
        } catch (IllegalArgumentException e) {
            getLogger().severe("config.yml est invalide, RagoLobby est désactivé : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        World world = loadWorld(settings.world());
        SpawnPoint spawn;
        try {
            spawn = SpawnPoint.read(new File(world.getWorldFolder(), SpawnPoint.FILE_NAME));
            if (spawn == null) {
                // Before the spawn moved into the map's folder, it was in the plugin folder.
                spawn = SpawnPoint.read(new File(getDataFolder(), SpawnPoint.FILE_NAME));
            }
        } catch (IOException e) {
            getLogger().warning(e.getMessage() + " : le spawn du monde est utilisé");
            spawn = null;
        }
        if (spawn == null) {
            Location worldSpawn = world.getSpawnLocation();
            spawn = new SpawnPoint(worldSpawn.getBlockX() + 0.5, worldSpawn.getBlockY(), worldSpawn.getBlockZ() + 0.5, 0f, 0f);
        }
        quietWorld(world);
        try {
            points = LobbyPoints.read(new File(world.getWorldFolder(), LobbyPoints.FILE_NAME));
        } catch (IOException e) {
            getLogger().warning(e.getMessage() + " : les points du lobby sont ignorés");
            points = new LobbyPoints();
        }

        writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "RagoLobby-writer");
            thread.setDaemon(true);
            return thread;
        });
        state = new LobbyState(world, spawn);
        visibility = new Visibility();
        queues = new QueueController(this, settings, state, this::queuesChanged);
        menu = new ModeMenu(settings, queues);
        scoreboard = new LobbyScoreboard(settings, queues);
        duels = new DuelRequests(settings, state, queues);
        menu.setDuels(duels);
        stats = new StatsService(this, settings.defaultRating(), writer, this::write);
        stats.load();
        fakes = new FakeEntities(this, world);
        fakes.start();
        getServer().getPluginManager().registerEvents(fakes, this);
        statsDisplays = new StatsDisplays(settings, stats.book(), new StatsMenu(settings, stats.book()), fakes);
        npcs = new LobbyNpcs(settings, state, points, queues, menu, fakes, statsDisplays);
        getServer().getPluginManager().registerEvents(npcs, this);

        getServer().getPluginManager().registerEvents(new LobbyListener(this, state, visibility), this);
        getServer().getPluginManager().registerEvents(new HostWatcher(queues), this);
        LobbyCommands commands = new LobbyCommands(this);
        for (String name : new String[] {"lobby", "spawn", "queue", "duel"}) {
            getCommand(name).setExecutor(commands);
            getCommand(name).setTabCompleter(commands);
        }
        getServer().getServicesManager().register(LobbyService.class, this, this, ServicePriority.Normal);

        getServer().getScheduler().runTaskTimer(this, () -> {
            queues.tick();
            scoreboard.updateAll();
            menu.refreshOpen();
            npcs.update();
        }, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, fakes::tick, 10L, 10L);
        int placed = npcs.spawnAll();
        List<String> missing = npcs.missingPoints();
        getLogger().info(placed + " PNJ et classements placés" + (missing.isEmpty() ? ""
                : ", sans point pour : " + String.join(", ", missing) + " (/lobby point <nom>)"));

        // After a /reload: everyone online is brought back to the lobby.
        for (Player player : getServer().getOnlinePlayers()) {
            sendToLobby(player);
        }
        getLogger().info("Lobby dans le monde " + world.getName() + ", " + settings.modes().size() + " modes : "
                + settings.catalog().all());
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (npcs != null) {
            npcs.removeAll();
        }
        if (fakes != null) {
            fakes.stop();
        }
        if (writer != null) {
            writer.shutdown();
            try {
                writer.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            writer = null;
        }
    }

    // --- The lobby world ------------------------------------------------------------------------

    /** The lobby world: loaded by RagoBuildTools, or by us; created empty if it does not exist. */
    private World loadWorld(String name) {
        World world = getServer().getWorld(name);
        if (world != null) {
            return world;
        }
        boolean exists = new File(new File(getServer().getWorldContainer(), name), "level.dat").isFile();
        world = new WorldCreator(name).generator(new VoidGenerator()).generateStructures(false).createWorld();
        if (!exists) {
            int y = VoidGenerator.PLATFORM_Y;
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    world.getBlockAt(x, y, z).setType(Material.QUARTZ_BLOCK);
                }
            }
            world.setSpawnLocation(0, y + 1, 0);
            world.save();
            getLogger().info("Monde " + name + " créé : un monde vide avec une plateforme. Mets ta map à la place"
                    + " (monde: dans config.yml) puis /lobby setspawn.");
        }
        return world;
    }

    /** No night, no rain, no mobs, no PvP in the lobby world. */
    private static void quietWorld(World world) {
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("mobGriefing", "false");
        world.setTime(6000L);
        world.setPVP(false);
    }

    // --- Players --------------------------------------------------------------------------------

    @Override
    public void sendToLobby(Player player) {
        state.leaveGame(player.getUniqueId());
        if (state.isBuilding(player)) {
            state.toggleBuilding(player.getUniqueId());
        }
        player.teleport(state.spawn());
        resetForLobby(player);
    }

    /** Puts the player in the lobby state where they stand. */
    void resetForLobby(Player player) {
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setExhaustion(0f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setLevel(0);
        player.setExp(0f);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        giveHotbar(player);
        scoreboard.show(player);
        visibility.applyInLobby(player, state);
        visibility.hideFromHiders(player, state);
    }

    /** The player belongs to a game from now on: sees and is seen by everyone. */
    void enterGame(Player player, String modeId) {
        state.enterGame(player.getUniqueId(), modeId);
        player.closeInventory();
        visibility.showAll(player);
        visibility.revealToAll(player);
    }

    /** The game did not start after all. */
    void cancelGame(Player player) {
        state.leaveGame(player.getUniqueId());
        if (player.getWorld().equals(state.world())) {
            giveHotbar(player);
            visibility.applyInLobby(player, state);
            visibility.hideFromHiders(player, state);
        } else {
            sendToLobby(player);
        }
    }

    /** /spawn: back to the lobby, through the game's host when in a game. */
    void goToSpawn(Player player) {
        String modeId = state.gameOf(player.getUniqueId());
        MatchHost host = modeId == null ? null : queues.hostOf(modeId);
        if (host != null) {
            host.abandon(player);
            if (state.isInGame(player)) {
                // The host did not give the player back: do it, the lobby must stay reachable.
                sendToLobby(player);
            }
        } else {
            sendToLobby(player);
        }
    }

    void forget(Player player) {
        statsDisplays.forget(player.getUniqueId());
        queues.forget(player.getUniqueId());
        duels.forget(player.getUniqueId());
        visibility.forget(player.getUniqueId());
        scoreboard.forget(player.getUniqueId());
        state.forget(player.getUniqueId());
    }

    void use(Player player, HotbarItem item) {
        switch (item) {
            case SELECTOR:
                menu.open(player);
                break;
            case LEAVE_QUEUE:
                queues.leave(player, true);
                break;
            case HIDE_PLAYERS:
            case SHOW_PLAYERS:
                if (!visibility.toggle(player, System.currentTimeMillis())) {
                    player.sendMessage(ChatColor.GRAY + "Wait a moment before switching again.");
                    return;
                }
                visibility.applyInLobby(player, state);
                giveHotbar(player);
                player.sendMessage(visibility.hides(player) ? ChatColor.GRAY + "The other players are hidden."
                        : ChatColor.GREEN + "The other players are shown.");
                break;
            default:
                break;
        }
    }

    private void giveHotbar(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItem(HotbarItem.SELECTOR.slot(), HotbarItem.SELECTOR.create());
        if (queues.queueOf(player) != null) {
            inventory.setItem(HotbarItem.LEAVE_QUEUE.slot(), HotbarItem.LEAVE_QUEUE.create());
        }
        HotbarItem eye = visibility.hides(player) ? HotbarItem.SHOW_PLAYERS : HotbarItem.HIDE_PLAYERS;
        inventory.setItem(eye.slot(), eye.create());
        player.updateInventory();
    }

    /** A queue changed: the hotbars (leave item), the sidebars and the open selectors follow. */
    private void queuesChanged() {
        if (scoreboard == null || menu == null) {
            return;
        }
        for (Player player : getServer().getOnlinePlayers()) {
            if (state.isProtected(player)) {
                giveHotbar(player);
            }
        }
        scoreboard.updateAll();
        menu.refreshOpen();
    }

    // --- Staff ----------------------------------------------------------------------------------

    /** /lobby setspawn. */
    void setSpawn(Player player) {
        if (!player.getWorld().equals(state.world())) {
            player.sendMessage(ChatColor.RED + "You are not in the lobby world (" + state.world().getName()
                    + "). To change the world: monde: in plugins/RagoLobby/config.yml.");
            return;
        }
        SpawnPoint spawn = SpawnPoint.of(player.getLocation());
        state.setSpawn(spawn);
        player.getWorld().setSpawnLocation(player.getLocation().getBlockX(), player.getLocation().getBlockY(),
                player.getLocation().getBlockZ());
        write(new File(state.world().getWorldFolder(), SpawnPoint.FILE_NAME), spawn.toYaml());
        player.sendMessage(ChatColor.GREEN + "Lobby spawn set here.");
    }

    /** Writes a file on the writer thread, through a .part file so that a crash leaves the old one. */
    private void write(final File file, final String text) {
        writer.execute(() -> {
            try {
                File folder = file.getParentFile();
                if (folder != null && !folder.isDirectory() && !folder.mkdirs()) {
                    throw new IOException("impossible de créer " + folder);
                }
                File part = new File(file.getPath() + ".part");
                Files.write(part.toPath(), text.getBytes(StandardCharsets.UTF_8));
                Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Impossible d'écrire " + file, e);
            }
        });
    }

    /** /lobby point <nom>: places a named spot where the player stands. */
    void setPoint(Player player, String name) {
        if (!player.getWorld().equals(state.world())) {
            player.sendMessage(ChatColor.RED + "You are not in the lobby.");
            return;
        }
        boolean moved = points.get(name) != null;
        if (!points.set(name, SpawnPoint.of(player.getLocation()))) {
            player.sendMessage(ChatColor.RED + (LobbyPoints.validName(name)
                    ? "At most " + LobbyPoints.MAX_POINTS + " points."
                    : "Invalid name: lowercase letters without accents, digits and dashes, 32 characters at most."));
            return;
        }
        write(new File(state.world().getWorldFolder(), LobbyPoints.FILE_NAME), points.toYaml());
        npcs.spawnAll();
        player.sendMessage(ChatColor.GREEN + "Point " + name + (moved ? " moved here." : " placed here."));
    }

    /** /lobby point retirer <nom>. */
    void removePoint(Player player, String name) {
        if (!points.remove(name)) {
            player.sendMessage(ChatColor.RED + "There is no point " + name + ". /lobby points for the list.");
            return;
        }
        write(new File(state.world().getWorldFolder(), LobbyPoints.FILE_NAME), points.toYaml());
        npcs.spawnAll();
        player.sendMessage(ChatColor.GREEN + "Point " + name + " removed.");
    }

    /** /lobby point tp <nom>. */
    void goToPoint(Player player, String name) {
        SpawnPoint point = points.get(name);
        if (point == null) {
            player.sendMessage(ChatColor.RED + "There is no point " + name + ". /lobby points for the list.");
            return;
        }
        player.teleport(point.in(state.world()));
    }

    LobbyPoints points() {
        return points;
    }

    /** /lobby build: switches the build mode. */
    void toggleBuild(Player player) {
        if (!player.getWorld().equals(state.world())) {
            player.sendMessage(ChatColor.RED + "You are not in the lobby.");
            return;
        }
        if (state.toggleBuilding(player.getUniqueId())) {
            queues.leave(player, true);
            player.getInventory().clear();
            player.setGameMode(org.bukkit.GameMode.CREATIVE);
            player.sendMessage(ChatColor.GREEN + "Build mode: you can edit the lobby. /lobby build to leave it.");
        } else {
            resetForLobby(player);
            player.sendMessage(ChatColor.GREEN + "Build mode off.");
        }
    }

    // --- LobbyService ---------------------------------------------------------------------------

    @Override
    public boolean registerHost(Plugin plugin, String modeId, MatchHost host) {
        return queues.register(plugin, modeId, host);
    }

    @Override
    public void unregisterHosts(Plugin plugin) {
        queues.unregister(plugin);
    }

    @Override
    public boolean isInGame(Player player) {
        return state.isInGame(player);
    }

    @Override
    public void recordMatch(String modeId, List<UUID> winners, List<UUID> losers) {
        GameMode mode = settings.catalog().get(modeId);
        if (mode == null) {
            getLogger().warning("Résultat d'un mode inconnu ignoré : " + modeId);
            return;
        }
        stats.record(mode, winners, losers);
    }

    /** The player joined: their name, for the leaderboards. */
    void joined(Player player) {
        stats.seen(player);
    }

    // --- For the other classes of the plugin ----------------------------------------------------

    DuelRequests duels() {
        return duels;
    }

    LobbySettings settings() {
        return settings;
    }

    QueueController queues() {
        return queues;
    }

    ModeMenu menu() {
        return menu;
    }

    LobbyState state() {
        return state;
    }

    /** The mode with this id, or null. */
    GameMode mode(String id) {
        return settings.catalog().get(id);
    }
}
