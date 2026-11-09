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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

/**
 * RagoLobby: the main lobby of Rago Practice. The spawn and its protection, the hotbar, the mode
 * selector and its local queues, the sidebar, and hiding players; the network around them: the
 * Network level and the ranks, the Rago Points and the shop, the ranked divisions, friends and
 * private messages, the staff's tools and sanctions. The game plugins plug into it through
 * {@link LobbyService}. See the module README.
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
    private DailyChest daily;
    private LobbyGadgets gadgets;
    private FakeEntities fakes;
    private DuelRequests duels;
    private StatsService stats;
    private StatsDisplays statsDisplays;
    private NetworkRanks ranks;
    private KitLayouts kits;
    private KitEditor kitEditor;
    private HypixelProfiles hypixel;
    private Parties parties;
    private ServerSettings server;
    private Profiles profiles;
    private ShopMenu shop;
    private StyleMenu styleMenu;
    private LevelMenu levelMenu;
    private Nicks nicks;
    private HeadLines headLines;
    private ProfileMenu profileMenu;
    private Social social;
    private CosmeticEffects cosmetics;
    private Punishments punishments;
    private StaffTools staff;
    private ServerInfo info;
    /** The kills of each player in their current game, for the MVP of a ranked game. */
    private final Map<UUID, Integer> gameKills = new HashMap<>();
    /** The players who turned /fly on in the lobby. */
    private final Set<UUID> flying = new HashSet<>();
    /** The players whose arrival the lobby was already told since they joined. */
    private final Set<UUID> arrivedOnce = new HashSet<>();
    private int seconds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            settings = LobbySettings.load(getConfig());
            server = ServerSettings.load(getConfig());
        } catch (IllegalArgumentException e) {
            getLogger().severe("config.yml is invalid, RagoLobby is disabled: " + e.getMessage());
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
            getLogger().warning(e.getMessage() + ": the spawn of the world is used");
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
            getLogger().warning(e.getMessage() + ": the points of the lobby are ignored");
            points = new LobbyPoints();
        }

        writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "RagoLobby-writer");
            thread.setDaemon(true);
            return thread;
        });
        state = new LobbyState(world, spawn);
        visibility = new Visibility();
        PartyBook partyBook = new PartyBook();
        queues = new QueueController(this, settings, state, partyBook, this::queuesChanged);
        stats = new StatsService(this, settings.defaultRating(), settings.levels(), server, writer, this::write, this::xpChanged);
        queues.setBook(stats.book());
        NameLooks names = new NameLooks(stats.book(), server);
        stats.book().setBoost(names::xpBoost);
        ranks = new NetworkRanks(stats.book(), names);
        profiles = new Profiles(this, server, stats, ranks, names);
        social = new Social(this, stats, profiles);
        punishments = new Punishments(this, stats.book(), writer, this::write);
        punishments.load();
        staff = new StaffTools(this, server, punishments);
        visibility.setVanish(staff::vanished);
        info = new ServerInfo(this, server);
        cosmetics = new CosmeticEffects(this, profiles, state);
        parties = new Parties(this, partyBook, state, ranks);
        parties.setQueues(queues);
        parties.setGate(social::acceptsParty);
        kits = new KitLayouts(this, writer, this::write);
        hypixel = new HypixelProfiles(this, settings.hypixelKey(), writer, this::write);
        kitEditor = new KitEditor(this, settings, kits, state, this::giveHotbar, hypixel);
        menu = new ModeMenu(settings, queues, stats.book());
        scoreboard = new LobbyScoreboard(settings, queues, stats.book(), state, names);
        duels = new DuelRequests(settings, state, queues);
        duels.setGate(social::acceptsDuel);
        duels.setFrozen(this::isFrozen);
        menu.setDuels(duels);
        stats.load();
        fakes = new FakeEntities(this, world);
        fakes.start();
        getServer().getPluginManager().registerEvents(fakes, this);
        StatsMenu statsMenu = new StatsMenu(settings, stats.book());
        statsDisplays = new StatsDisplays(settings, stats.book(), statsMenu, fakes);
        shop = new ShopMenu(profiles, stats.book());
        profileMenu = new ProfileMenu(this, profiles, social, stats, statsMenu, shop);
        levelMenu = new LevelMenu(stats.book(), server);
        profileMenu.setLevels(levelMenu);
        styleMenu = new StyleMenu(profiles, stats.book());
        styleMenu.setRewardLevel(server.levelRewards()::levelOf);
        styleMenu.setBack((p, right) -> profileMenu.open(p));
        shop.setStyles(styleMenu);
        profileMenu.setStyles(styleMenu);
        npcs = new LobbyNpcs(settings, state, points, queues, menu, fakes, statsDisplays);
        getServer().getPluginManager().registerEvents(npcs, this);

        getServer().getPluginManager().registerEvents(new LobbyListener(this, state, visibility), this);
        getServer().getPluginManager().registerEvents(new PlayerMenu(this, statsMenu), this);
        getServer().getPluginManager().registerEvents(new HostWatcher(queues), this);
        getServer().getPluginManager().registerEvents(punishments, this);
        // before the parties: a muted player does not talk in the party channel either
        getServer().getPluginManager().registerEvents(staff, this);
        getServer().getPluginManager().registerEvents(ranks, this);
        getServer().getPluginManager().registerEvents(parties, this);
        getServer().getPluginManager().registerEvents(cosmetics, this);
        getServer().getPluginManager().registerEvents(info, this);
        getServer().getPluginManager().registerEvents(new PearlGuard(), this);
        gadgets = new LobbyGadgets(this);
        getServer().getPluginManager().registerEvents(gadgets, this);
        nicks = new Nicks();
        nicks.start();
        profiles.names().setNicks(nicks::nick);
        headLines = new HeadLines(world, this::headLine);
        headLines.start();
        getServer().getPluginManager().registerEvents(headLines, this);
        cosmetics.start();
        getServer().getPluginManager().registerEvents(kitEditor, this);
        LobbyCommands commands = new LobbyCommands(this);
        for (String name : new String[] {"lobby", "spawn", "queue", "duel", "hypixel", "party", "pc", "chat"}) {
            getCommand(name).setExecutor(commands);
            getCommand(name).setTabCompleter(commands);
        }
        NetworkCommands network = new NetworkCommands(this);
        for (String name : NetworkCommands.NAMES) {
            getCommand(name).setExecutor(network);
            getCommand(name).setTabCompleter(network);
        }
        getServer().getServicesManager().register(LobbyService.class, this, this, ServicePriority.Normal);

        getServer().getScheduler().runTaskTimer(this, () -> {
            seconds++;
            queues.tick();
            parties.tick();
            social.expire();
            staff.tick(seconds);
            info.tick(seconds);
            for (Player player : getServer().getOnlinePlayers()) {
                // The stats are read after the start: the ranks follow once they are there.
                ranks.refresh(player);
            }
            scoreboard.updateAll();
            headLines.tick();
            menu.refreshOpen();
            npcs.update();
        }, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, fakes::tick, 10L, 10L);
        daily = new DailyChest(profiles);
        int placed = placeDecor();
        List<String> missing = npcs.missingPoints();
        getLogger().info(placed + " NPCs and leaderboards placed" + (missing.isEmpty() ? ""
                : ", no point for: " + String.join(", ", missing) + " (/lobby point <name>)"));

        // After a /reload: everyone online is brought back to the lobby.
        for (Player player : getServer().getOnlinePlayers()) {
            kits.load(player.getUniqueId());
            hypixel.load(player.getUniqueId());
            profiles.apply(player);
            arrivedOnce.add(player.getUniqueId());
            sendToLobby(player);
        }
        getLogger().info("Lobby in the world " + world.getName() + ", " + settings.modes().size() + " modes: "
                + settings.catalog().all());
        getLogger().info(hypixel.enabled() ? "Hypixel import on: players can copy their Bed Wars shop (/hypixel import)"
                : "Hypixel import off: no API key in hypixel.cle-api of config.yml");
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
        if (kitEditor != null) {
            kitEditor.closeAll();
        }
        if (cosmetics != null) {
            cosmetics.stop();
        }
        if (gadgets != null) {
            gadgets.clear();
        }
        if (headLines != null) {
            headLines.stop();
        }
        if (nicks != null) {
            nicks.stop();
        }
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof ClickMenu) {
                player.closeInventory();
            }
        }
        if (hypixel != null) {
            hypixel.stop();
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
            getLogger().info("World " + name + " created: an empty world with a platform. Put your map instead"
                    + " (monde: in config.yml), then /lobby setspawn.");
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
        boolean fromGame = state.gameOf(player.getUniqueId()) != null;
        state.leaveGame(player.getUniqueId());
        scoreboard.setGameLines(player.getUniqueId(), null);
        scoreboard.setGamePrefix(player.getUniqueId(), null);
        if (state.isBuilding(player)) {
            state.toggleBuilding(player.getUniqueId());
        }
        player.teleport(state.spawn());
        resetForLobby(player);
        if (fromGame || !arrivedOnce.contains(player.getUniqueId())) {
            arrivedOnce.add(player.getUniqueId());
            info.arrived(player);
        }
    }

    /** Puts the player in the lobby state where they stand. */
    void resetForLobby(Player player) {
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        boolean fly = flying.contains(player.getUniqueId()) && player.hasPermission("rago.perk.fly");
        player.setAllowFlight(fly);
        player.setFlying(false);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setExhaustion(0f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        showLevel(player);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        giveHotbar(player);
        scoreboard.show(player);
        visibility.applyInLobby(player, state);
        visibility.hideFromHiders(player, state);
    }

    /** The XP bar shows the Network level, and how far the player is towards the next one. */
    private void showLevel(Player player) {
        PlayerStats mine = stats.book().get(player.getUniqueId());
        int xp = mine == null ? 0 : mine.xp();
        player.setLevel(settings.levels().level(xp));
        player.setExp(settings.levels().progress(xp));
    }

    /** /fly: flying in the lobby, for the ranks that have it. */
    void toggleFly(Player player) {
        if (!state.isInLobby(player) || state.isInGame(player)) {
            player.sendMessage(ChatColor.RED + "You can only fly in the lobby.");
            return;
        }
        if (flying.remove(player.getUniqueId())) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(ChatColor.RED + "Flight off.");
        } else {
            flying.add(player.getUniqueId());
            player.setAllowFlight(true);
            player.sendMessage(ChatColor.GREEN + "Flight on. /fly again to land.");
        }
    }

    /** A player's Network XP changed: their rank in the chat, their XP bar in the lobby, the sidebars. */
    private void xpChanged(UUID id) {
        Player player = getServer().getPlayer(id);
        if (player == null) {
            return;
        }
        ranks.refresh(player);
        if (state.isProtected(player)) {
            showLevel(player);
        }
        scoreboard.updateAll();
    }

    /** The player belongs to a game from now on: sees and is seen by everyone. */
    /** Frozen by the staff (a check in progress): no game until they are let go. */
    boolean isFrozen(UUID player) {
        return staff != null && staff.frozen(player);
    }

    void enterGame(Player player, String modeId) {
        state.enterGame(player.getUniqueId(), modeId);
        gameKills.remove(player.getUniqueId());
        staff.unvanishForGame(player);
        player.closeInventory();
        player.setAllowFlight(false);
        player.setFlying(false);
        visibility.showAll(player);
        visibility.revealToAll(player);
    }

    /** The game did not start after all. */
    void cancelGame(Player player) {
        state.leaveGame(player.getUniqueId());
        scoreboard.setGameLines(player.getUniqueId(), null);
        scoreboard.setGamePrefix(player.getUniqueId(), null);
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
        arrivedOnce.remove(player.getUniqueId());
        info.forget(player.getUniqueId());
        social.announce(player, false);
        staff.quit(player);
        social.forget(player.getUniqueId());
        profiles.forget(player.getUniqueId());
        cosmetics.forget(player.getUniqueId());
        gameKills.remove(player.getUniqueId());
        flying.remove(player.getUniqueId());
        kitEditor.forget(player.getUniqueId());
        kits.forget(player.getUniqueId());
        hypixel.forget(player.getUniqueId());
        statsDisplays.forget(player.getUniqueId());
        ranks.forget(player.getUniqueId());
        queues.forget(player.getUniqueId());
        parties.forget(player.getUniqueId());
        duels.forget(player.getUniqueId());
        visibility.forget(player.getUniqueId());
        gadgets.forget(player.getUniqueId());
        nicks.forget(player.getUniqueId());
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
            case KIT_EDITOR:
                kitEditor.openModes(player);
                break;
            case FIREBALL:
                gadgets.throwFireball(player);
                break;
            case TELEPORT:
                gadgets.teleport(player);
                break;
            case LEVEL:
                levelMenu.open(player);
                break;
            case SHOP:
                shop.open(player);
                break;
            case PROFILE:
                profileMenu.open(player);
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
        if (kitEditor.isEditing(player)) {
            // The inventory holds the kit being arranged: the hotbar comes back when the editor closes.
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItem(HotbarItem.SELECTOR.slot(), HotbarItem.SELECTOR.create());
        inventory.setItem(HotbarItem.KIT_EDITOR.slot(), HotbarItem.KIT_EDITOR.create());
        inventory.setItem(HotbarItem.FIREBALL.slot(), HotbarItem.FIREBALL.create());
        inventory.setItem(HotbarItem.TELEPORT.slot(), HotbarItem.TELEPORT.create());
        inventory.setItem(HotbarItem.LEVEL.slot(), HotbarItem.LEVEL.create());
        if (queues.queueOf(player) != null) {
            inventory.setItem(HotbarItem.LEAVE_QUEUE.slot(), HotbarItem.LEAVE_QUEUE.create());
        }
        inventory.setItem(HotbarItem.SHOP.slot(), HotbarItem.SHOP.create());
        inventory.setItem(HotbarItem.PROFILE.slot(), ownHead(player));
        HotbarItem eye = visibility.hides(player) ? HotbarItem.SHOW_PLAYERS : HotbarItem.HIDE_PLAYERS;
        inventory.setItem(eye.slot(), eye.create());
        player.updateInventory();
    }

    /** The profile item with the player's own head. */
    @SuppressWarnings("deprecation") // SkullMeta#setOwner(String) is the 1.8.8 way to show a player's head
    private static ItemStack ownHead(Player player) {
        ItemStack head = HotbarItem.PROFILE.create();
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwner(player.getName());
        head.setItemMeta(meta);
        return head;
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
                    throw new IOException("cannot create " + folder);
                }
                File part = new File(file.getPath() + ".part");
                Files.write(part.toPath(), text.getBytes(StandardCharsets.UTF_8));
                Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Cannot write " + file, e);
            }
        });
    }

    /** /lobby point <name>: places a named spot where the player stands. */
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
        placeDecor();
        player.sendMessage(ChatColor.GREEN + "Point " + name + (moved ? " moved here." : " placed here."));
    }

    /** /lobby point remove <name>. */
    void removePoint(Player player, String name) {
        if (!points.remove(name)) {
            player.sendMessage(ChatColor.RED + "There is no point " + name + ". /lobby points for the list.");
            return;
        }
        write(new File(state.world().getWorldFolder(), LobbyPoints.FILE_NAME), points.toYaml());
        placeDecor();
        player.sendMessage(ChatColor.GREEN + "Point " + name + " removed.");
    }

    /** /lobby point tp <name>. */
    void goToPoint(Player player, String name) {
        SpawnPoint point = points.get(name);
        if (point == null) {
            player.sendMessage(ChatColor.RED + "There is no point " + name + ". /lobby points for the list.");
            return;
        }
        player.teleport(point.in(state.world()));
    }

    /** The NPCs, the leaderboards, then the daily chest, on their points. The number of NPCs placed. */
    private int placeDecor() {
        int placed = npcs.spawnAll();
        daily.place(points, state.world(), fakes);
        return placed;
    }

    DailyChest daily() {
        return daily;
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
            getLogger().warning("Result of an unknown mode ignored: " + modeId);
            return;
        }
        if (isPrivate(winners) || isPrivate(losers)) {
            getLogger().info("Private game of a party in " + modeId + ": not counted in the stats");
            return;
        }
        stats.record(mode, winners, losers, mvps(winners, losers));
        for (UUID winner : winners) {
            cosmetics.win(winner);
        }
        for (UUID player : winners) {
            gameKills.remove(player);
        }
        for (UUID player : losers) {
            gameKills.remove(player);
        }
    }

    /** The MVPs of a game: the most kills, at least one. */
    private List<UUID> mvps(List<UUID> winners, List<UUID> losers) {
        List<UUID> all = new ArrayList<>(winners);
        all.addAll(losers);
        int best = 0;
        for (UUID player : all) {
            Integer kills = gameKills.get(player);
            best = Math.max(best, kills == null ? 0 : kills);
        }
        List<UUID> mvps = new ArrayList<>();
        if (best > 0) {
            for (UUID player : all) {
                Integer kills = gameKills.get(player);
                if (kills != null && kills == best && !mvps.contains(player)) {
                    mvps.add(player);
                }
            }
        }
        return mvps;
    }

    @Override
    public void recordKill(String modeId, UUID killer, UUID victim) {
        if (settings.catalog().get(modeId) == null) {
            getLogger().warning("Kill in an unknown mode ignored: " + modeId);
            return;
        }
        if ((killer != null && state.isInPrivateGame(killer)) || (victim != null && state.isInPrivateGame(victim))) {
            return;
        }
        stats.kill(modeId, killer, victim);
        if (killer != null && !killer.equals(victim)) {
            Integer kills = gameKills.get(killer);
            gameKills.put(killer, kills == null ? 1 : kills + 1);
            cosmetics.kill(killer, victim);
        }
    }

    @Override
    public void giveXp(String modeId, UUID player, int xp, String reason) {
        if (settings.catalog().get(modeId) == null) {
            getLogger().warning("XP of an unknown mode ignored: " + modeId);
            return;
        }
        if (player == null || xp <= 0 || state.isInPrivateGame(player)) {
            return;
        }
        stats.giveXp(player, xp, reason);
    }

    /** True if one of these players plays a party's private game. */
    private boolean isPrivate(List<UUID> players) {
        for (UUID player : players) {
            if (state.isInPrivateGame(player)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack[] kit(Player player, String modeId) {
        ModeDisplay display = settings.display(modeId);
        if (display == null || display.kit() == null) {
            return null;
        }
        KitItem[] arranged = display.kit().arrange(kits.get(player.getUniqueId(), modeId));
        ItemStack[] items = new ItemStack[Kit.SLOTS];
        for (int slot = 0; slot < Kit.SLOTS; slot++) {
            items[slot] = arranged[slot] == null ? null : arranged[slot].toItem();
        }
        return items;
    }

    @Override
    public ItemStack[] arrange(Player player, String modeId, ItemStack[] kit) {
        if (kit == null) {
            return null;
        }
        Kit known = Kit.of(kit);
        if (known.isEmpty()) {
            return kit;
        }
        kitEditor.learn(modeId, known);
        int[] to = known.placement(kits.get(player.getUniqueId(), modeId));
        ItemStack[] arranged = new ItemStack[Math.max(kit.length, Kit.SLOTS)];
        for (int slot = Kit.SLOTS; slot < kit.length; slot++) {
            arranged[slot] = kit[slot];
        }
        List<ItemStack> unread = new ArrayList<>();
        for (int slot = 0; slot < Kit.SLOTS && slot < kit.length; slot++) {
            if (kit[slot] != null && to[slot] >= 0) {
                arranged[to[slot]] = kit[slot];
            } else if (kit[slot] != null && kit[slot].getType() != Material.AIR) {
                unread.add(kit[slot]);
            }
        }
        // a stack the kit does not read (more than 64 items): the first free slot
        for (ItemStack stack : unread) {
            for (int slot = 0; slot < Kit.SLOTS; slot++) {
                if (arranged[slot] == null) {
                    arranged[slot] = stack;
                    break;
                }
            }
        }
        return arranged;
    }

    @Override
    public List<String> hypixelQuickBuy(Player player) {
        HypixelProfile profile = hypixel.get(player.getUniqueId());
        return profile == null ? null : profile.quickBuy();
    }

    @Override
    public void setGameSidebar(Player player, List<String> lines) {
        scoreboard.setGameLines(player.getUniqueId(), lines);
        scoreboard.update(player);
    }

    @Override
    public void setGameNameTag(Player player, String prefix) {
        // every scoreboard takes it at its next update, within a second
        scoreboard.setGamePrefix(player.getUniqueId(), prefix);
    }

    @Override
    public List<String> hypixelHotbar(Player player) {
        HypixelProfile profile = hypixel.get(player.getUniqueId());
        return profile == null ? null : profile.hotbar();
    }

    /** The line above the name of a lobby player (none while nicked): what they chose, the ELO by default. */
    private String headLine(Player player) {
        UUID id = player.getUniqueId();
        if (nicks.nick(id) != null) {
            return "";
        }
        PlayerStats profile = stats.book().get(id);
        HeadLines.Shown shown = profile == null || profile.headLine() == null ? HeadLines.Shown.ELO
                : HeadLines.Shown.of(profile.headLine());
        if (shown == null || shown == HeadLines.Shown.MESSAGE && (profile.headMessage() == null
                || !player.hasPermission("rago.perk.message"))) {
            shown = HeadLines.Shown.ELO;
        }
        int elo = -1;
        for (GameMode mode : settings.catalog().all()) {
            ModeStats played = profile == null ? null : profile.mode(mode.id());
            if (mode.ranked() && played != null && played.games() > 0) {
                elo = Math.max(elo, played.elo);
            }
        }
        long now = System.currentTimeMillis();
        String day = StatsPeriod.dayKey(now);
        String week = StatsPeriod.weekKey(now);
        return HeadLines.line(shown, elo, elo < 0 ? "" : stats.book().ladder().format(elo),
                stats.book().levels().tag(stats.book().level(id)),
                profile == null ? 0 : profile.wins(StatsPeriod.ALL, null, day, week),
                profile == null ? 0 : profile.kills(StatsPeriod.ALL, null, day, week),
                profile == null ? 0 : profile.best(StatsPeriod.ALL, null, day, week),
                profile == null ? null : profile.headMessage());
    }

    Nicks nicks() {
        return nicks;
    }

    /** The player joined: their name, for the leaderboards, their kit layouts and their Hypixel shop. */
    void joined(Player player) {
        kits.load(player.getUniqueId());
        hypixel.load(player.getUniqueId());
        stats.seen(player);
        profiles.apply(player);
        PlayerStats profile = stats.book().get(player.getUniqueId());
        if (profile != null && profile.nick() != null) {
            if (player.hasPermission("rago.perk.nick")) {
                nicks.restore(player, profile.nick());
                player.sendMessage(ChatColor.GRAY + "You play as " + ChatColor.WHITE + profile.nick() + ChatColor.GRAY
                        + " (/nick reset to stop).");
            } else {
                profile.setNick(null);
                stats.save(profile);
            }
        }
        visibility.applyVanish(player, state);
        social.announce(player, true);
        if (profiles.dailyReady(player.getUniqueId())) {
            player.sendMessage(ChatColor.AQUA + "Your daily reward is waiting: " + ChatColor.YELLOW + "/daily");
        }
    }

    // --- For the other classes of the plugin ----------------------------------------------------

    /** A line for every player in the lobby. */
    void announce(String line) {
        for (Player player : getServer().getOnlinePlayers()) {
            if (state.isInLobby(player)) {
                player.sendMessage(line);
            }
        }
    }

    CosmeticEffects cosmeticEffects() {
        return cosmetics;
    }

    /** The sidebars and name tags follow a change of rank or points, within a second anyway. */
    void refreshBoards() {
        scoreboard.updateAll();
    }

    boolean canSee(Player viewer, Player target) {
        return visibility.canSee(viewer, target);
    }

    /** False for a vanished member of the staff. */
    boolean canBeSeen(Player player) {
        return !staff.vanished(player.getUniqueId());
    }

    /** The staff vanished or came back. */
    void applyVanish(Player player) {
        visibility.applyVanish(player, state);
    }

    /** What a muted player reads when they try to talk, or null. */
    String mutedFor(Player player) {
        return punishments.mutedMessage(player.getUniqueId());
    }

    /** True if this player's rank, given by the staff, is a staff rank. */
    boolean isStaffRank(PlayerStats profile) {
        RankDef rank = profile.rank() == null ? null : server.ranks().get(profile.rank());
        return rank != null && rank.staff();
    }

    ServerSettings server() {
        return server;
    }

    Profiles profiles() {
        return profiles;
    }

    LevelMenu levelMenu() {
        return levelMenu;
    }

    StyleMenu styleMenu() {
        return styleMenu;
    }

    ShopMenu shop() {
        return shop;
    }

    ProfileMenu profileMenu() {
        return profileMenu;
    }

    Social social() {
        return social;
    }

    StaffTools staff() {
        return staff;
    }

    Punishments punishments() {
        return punishments;
    }

    StatsService stats() {
        return stats;
    }

    DuelRequests duels() {
        return duels;
    }

    Parties parties() {
        return parties;
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

    KitEditor kitEditor() {
        return kitEditor;
    }

    HypixelProfiles hypixel() {
        return hypixel;
    }

    LobbyState state() {
        return state;
    }

    /** The mode with this id, or null. */
    GameMode mode(String id) {
        return settings.catalog().get(id);
    }
}
