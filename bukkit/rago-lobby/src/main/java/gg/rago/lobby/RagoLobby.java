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
    /** Writes files (spawn.yml) away from the main thread, one at a time. */
    private ExecutorService writer;

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
            spawn = SpawnPoint.read(new File(getDataFolder(), SpawnPoint.FILE_NAME));
        } catch (IOException e) {
            getLogger().warning(e.getMessage() + " : le spawn du monde est utilisé");
            spawn = null;
        }
        if (spawn == null) {
            Location worldSpawn = world.getSpawnLocation();
            spawn = new SpawnPoint(worldSpawn.getBlockX() + 0.5, worldSpawn.getBlockY(), worldSpawn.getBlockZ() + 0.5, 0f, 0f);
        }
        quietWorld(world);

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

        getServer().getPluginManager().registerEvents(new LobbyListener(this, state, visibility), this);
        getServer().getPluginManager().registerEvents(new HostWatcher(queues), this);
        LobbyCommands commands = new LobbyCommands(this);
        for (String name : new String[] {"lobby", "spawn", "file"}) {
            getCommand(name).setExecutor(commands);
            getCommand(name).setTabCompleter(commands);
        }
        getServer().getServicesManager().register(LobbyService.class, this, this, ServicePriority.Normal);

        getServer().getScheduler().runTaskTimer(this, () -> {
            queues.tick();
            scoreboard.updateAll();
            menu.refreshOpen();
        }, 20L, 20L);

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
        queues.forget(player.getUniqueId());
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
                    player.sendMessage(ChatColor.GRAY + "Attends un peu avant de changer à nouveau.");
                    return;
                }
                visibility.applyInLobby(player, state);
                giveHotbar(player);
                player.sendMessage(visibility.hides(player) ? ChatColor.GRAY + "Les autres joueurs sont cachés."
                        : ChatColor.GREEN + "Les autres joueurs sont visibles.");
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
            player.sendMessage(ChatColor.RED + "Tu n'es pas dans le monde du lobby (" + state.world().getName()
                    + "). Pour changer de monde : monde: dans plugins/RagoLobby/config.yml.");
            return;
        }
        SpawnPoint spawn = SpawnPoint.of(player.getLocation());
        state.setSpawn(spawn);
        player.getWorld().setSpawnLocation(player.getLocation().getBlockX(), player.getLocation().getBlockY(),
                player.getLocation().getBlockZ());
        final String yaml = spawn.toYaml();
        final File file = new File(getDataFolder(), SpawnPoint.FILE_NAME);
        writer.execute(() -> {
            try {
                File part = new File(file.getPath() + ".part");
                Files.write(part.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
                Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Impossible d'écrire " + file, e);
            }
        });
        player.sendMessage(ChatColor.GREEN + "Spawn du lobby placé ici.");
    }

    /** /lobby build: switches the build mode. */
    void toggleBuild(Player player) {
        if (!player.getWorld().equals(state.world())) {
            player.sendMessage(ChatColor.RED + "Tu n'es pas dans le lobby.");
            return;
        }
        if (state.toggleBuilding(player.getUniqueId())) {
            queues.leave(player, true);
            player.getInventory().clear();
            player.setGameMode(org.bukkit.GameMode.CREATIVE);
            player.sendMessage(ChatColor.GREEN + "Mode construction : tu peux modifier le lobby. /lobby build pour en sortir.");
        } else {
            resetForLobby(player);
            player.sendMessage(ChatColor.GREEN + "Mode construction terminé.");
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

    // --- For the other classes of the plugin ----------------------------------------------------

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
