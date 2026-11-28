package gg.rago.bedwars;

import gg.rago.bedwars.BedwarsGame.Gamer;
import gg.rago.bedwars.BedwarsGame.Phase;
import gg.rago.game.GameMode;
import gg.rago.game.MatchProposal;
import gg.rago.game.QueueEntry;
import gg.rago.lobby.api.EndOfGame;
import gg.rago.lobby.api.MatchHost;
import gg.rago.lobby.api.PrivateSetting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Plays the Bed Wars modes of the lobby: takes a free copy of a map for each match (or has one
 * made, and the match waits in the queue for a second), starts the game, and applies the Bed Wars
 * rules in the game worlds: blocks placed only under the build limit and away from the spawns and
 * generators, only placed blocks and enemy beds broken, explosions that only take placed blocks,
 * no friendly fire, the void that kills, the shop villagers. Main thread only.
 */
final class BedwarsHost implements MatchHost, Listener {

    /** What players cannot use in a game: the map's chests stay as they are, nobody sleeps. */
    private static final Set<Material> NOT_USED = EnumSet.of(Material.BED_BLOCK, Material.CHEST,
            Material.TRAPPED_CHEST, Material.ENDER_CHEST, Material.FURNACE, Material.BURNING_FURNACE,
            Material.WORKBENCH, Material.ANVIL, Material.ENCHANTMENT_TABLE, Material.BREWING_STAND, Material.HOPPER,
            Material.DISPENSER, Material.DROPPER, Material.BEACON, Material.JUKEBOX, Material.NOTE_BLOCK,
            Material.CAULDRON, Material.ITEM_FRAME, Material.FLOWER_POT);
    /** The chests a game opens as its own: the island's chest, the ender chest. */
    private static final Set<Material> CHESTS = EnumSet.of(Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST);
    /** Placed blocks that explosions do not break, as on Hypixel. */
    private static final Set<Material> BLAST_PROOF = EnumSet.of(Material.STAINED_GLASS, Material.OBSIDIAN,
            Material.BED_BLOCK);

    private final RagoBedwars plugin;
    private final Map<UUID, BedwarsGame> games = new HashMap<>();
    private final List<BedwarsGame> running = new ArrayList<>();
    /** The players in the waiting lobby of their private game (Bedwars Normal), by its waiting lobby. */
    private final Map<UUID, PrivateWaitingLobby> waiting = new HashMap<>();
    /** The server has been told once that salle-attente-privee names no map with a waiting lobby. */
    private boolean warnedNoWaitingLobby;
    /** Who lit each TNT: the kill goes to them (1.8 cannot give a TNT its source). */
    private final Map<Entity, UUID> tntOwners = new WeakHashMap<>();
    /** Players already told that no map is ready, so that they are not told every second. */
    private final Set<UUID> toldNoMap = new HashSet<>();
    private final MapDraw draw = new MapDraw(new java.util.Random());
    /** The kit each player last chose in Attack and Defense, for the attack and for the defense. */
    private final Map<UUID, int[]> roundKits = new HashMap<>();
    /** The kit each player chose for the attacker of the Bed Break Simulator, by its index (the first by default). */
    private final Map<UUID, Integer> bedBreakKits = new HashMap<>();
    private boolean warnedNoMap;

    BedwarsHost(RagoBedwars plugin) {
        this.plugin = plugin;
    }

    // --- MatchHost ------------------------------------------------------------------------------

    @Override
    public boolean ready(MatchProposal match) {
        List<BedwarsMap> maps = mapsOf(match);
        if (maps.isEmpty()) {
            // start() tells the players that no map plays this mode
            return true;
        }
        if (plugin.settings().kind(match.mode().id()) != BedwarsSettings.Kind.CLASSIC
                && joinable(match.mode().id(), maps) != null) {
            return true;
        }
        if (waitingOn(sidesOf(match), maps) != null) {
            // a private game whose waiting lobby is over its map: played on that copy
            return true;
        }
        List<BedwarsMap> drawn = drawn(match.mode(), maps);
        if (plugin.instances().hasFree(drawn)) {
            return true;
        }
        plugin.instances().prepare(drawn, true);
        return false;
    }

    /** The online players of the match, by team; a team without a player online stays empty. */
    private static List<List<Player>> sidesOf(MatchProposal match) {
        List<List<Player>> sides = new ArrayList<>();
        for (List<QueueEntry> team : match.teams()) {
            List<Player> side = new ArrayList<>();
            for (QueueEntry entry : team) {
                Player player = Bukkit.getPlayer(entry.player());
                if (player != null) {
                    side.add(player);
                }
            }
            sides.add(side);
        }
        return sides;
    }

    /**
     * The map a new game of this match is played on, among its maps: the one the players chose, or
     * the mode's next map, drawn at random (see {@link MapDraw}).
     */
    private List<BedwarsMap> drawn(GameMode mode, List<BedwarsMap> maps) {
        if (maps.size() <= 1) {
            return maps;
        }
        String world = draw.next(mode.id(), worlds(maps));
        for (BedwarsMap map : maps) {
            if (map.world().equals(world)) {
                List<BedwarsMap> one = new ArrayList<>();
                one.add(map);
                return one;
            }
        }
        return maps;
    }

    private static List<String> worlds(List<BedwarsMap> maps) {
        List<String> worlds = new ArrayList<>();
        for (BedwarsMap map : maps) {
            worlds.add(map.world());
        }
        return worlds;
    }

    /** The maps of the match's mode; only the one the players chose, while it can still be played. */
    private List<BedwarsMap> mapsOf(MatchProposal match) {
        List<BedwarsMap> maps = mapsFor(match.mode());
        List<BedwarsMap> chosen = new ArrayList<>();
        for (BedwarsMap map : maps) {
            if (map.world().equalsIgnoreCase(match.map())) {
                chosen.add(map);
            }
        }
        return chosen.isEmpty() ? maps : chosen;
    }

    @Override
    public boolean start(MatchProposal match) {
        GameMode mode = match.mode();
        List<List<Player>> sides = new ArrayList<>();
        for (List<QueueEntry> team : match.teams()) {
            List<Player> side = new ArrayList<>();
            for (QueueEntry entry : team) {
                Player player = Bukkit.getPlayer(entry.player());
                if (player == null || games.containsKey(entry.player())) {
                    return false;
                }
                side.add(player);
            }
            sides.add(side);
        }
        List<BedwarsMap> maps = mapsOf(match);
        if (maps.isEmpty()) {
            noMap(mode, sides);
            return false;
        }
        BedwarsSettings.Kind kind = plugin.settings().kind(mode.id());
        // FFA Archway (the Private games, the owner's choice of 23/11): the party's own FFA, in a copy
        // of the map of its own, nobody else in it, until the end of its minutes
        boolean partyFfa = kind == BedwarsSettings.Kind.FFA && plugin.lobby().inPrivateGame(firstPlayer(sides));
        BedwarsGame joinable = kind == BedwarsSettings.Kind.CLASSIC || partyFfa ? null : joinable(mode.id(), maps);
        if (joinable != null) {
            // an open game with room, an invis practice still waiting for its players: in it at once
            for (List<Player> side : sides) {
                for (Player player : side) {
                    toldNoMap.remove(player.getUniqueId());
                    joinable.join(player);
                }
            }
            return true;
        }
        PrivateWaitingLobby waitedOn = waitingOn(sides, maps);
        List<BedwarsMap> drawn = waitedOn != null ? Collections.singletonList(waitedOn.instance().map()) : drawn(mode, maps);
        Instance instance;
        if (waitedOn != null) {
            // the private game waited over its map: played on that copy, which stays taken
            instance = waitedOn.instance();
            if (instance.map().sides() < islands(mode)) {
                // the players still stand in it: their copy is freed when they leave it
                return false;
            }
            for (UUID id : waitedOn.players()) {
                waiting.remove(id);
                waitedOn.remove(id);
            }
        } else {
            instance = plugin.instances().acquire(drawn);
        }
        if (instance == null) {
            // a match waits: a copy of its map is made, room made for it if need be
            plugin.instances().prepare(drawn, true);
            return false;
        }
        if (instance.map().sides() < islands(mode)) {
            plugin.instances().release(instance);
            return false;
        }
        // the party's FFA starts with its first player; the others join it at once, each in a colour of their own
        List<Player> joining = new ArrayList<>();
        if (partyFfa) {
            for (List<Player> side : sides) {
                joining.addAll(side);
            }
            sides = new ArrayList<>();
            sides.add(new ArrayList<>(Collections.singletonList(joining.remove(0))));
        }
        BedwarsGame game = partyFfa ? new FfaGame(plugin, this, instance, mode.id(), ChatColor.stripColor(mode.name()), sides,
                plugin.settings().privateFfaMinutes()) : create(kind, instance, mode, sides);
        game.modeLine = mode.name();
        if (plugin.settings().privateRules(mode.id())) {
            // Bedwars Normal: the settings its party leader chose in the lobby, if a private game
            game.rules = PrivateRules.of(plugin.lobby().privateSettings(firstPlayer(sides)));
        }
        for (List<Player> side : sides) {
            for (Player player : side) {
                games.put(player.getUniqueId(), game);
                toldNoMap.remove(player.getUniqueId());
            }
        }
        running.add(game);
        // recorded for /replay from before its start: the blocks the start changes are in the replay
        game.record();
        game.begin();
        for (Player player : joining) {
            toldNoMap.remove(player.getUniqueId());
            game.join(player);
        }
        // a private game of Bedwars Normal: its players came from its waiting lobby, now on the map
        for (UUID id : game.players()) {
            leaveWaiting(id);
        }
        // The next game of the mode is on another map, drawn now; the next match should not wait for
        // its copy: one is made now if none is free and there is room.
        if (maps.size() > 1) {
            draw.played(mode.id(), instance.map().world(), worlds(maps));
            List<BedwarsMap> nextMaps = drawn(mode, maps);
            if (!plugin.instances().hasFree(nextMaps)) {
                plugin.instances().prepare(nextMaps, false);
            }
        } else if (!plugin.instances().hasFree(maps)) {
            plugin.instances().prepare(maps, false);
        }
        return true;
    }

    /** A new game of this kind. */
    private BedwarsGame create(BedwarsSettings.Kind kind, Instance instance, GameMode mode, List<List<Player>> sides) {
        String name = ChatColor.stripColor(mode.name());
        switch (kind) {
            case FFA:
                return new FfaGame(plugin, this, instance, mode.id(), name, sides, false);
            case ARENA:
                return new FfaGame(plugin, this, instance, mode.id(), name, sides, true);
            case INVIS:
                return new InvisGame(plugin, this, instance, mode.id(), name, sides, mode.playersPerMatch());
            case FAST_SHOP:
                return new FastShopGame(plugin, this, instance, mode.id(), name, sides);
            case TOP_BRIDGE:
                return new TopBridgeGame(plugin, this, instance, mode.id(), name, sides, partyPractice(sides));
            case ATTACK_DEFENSE:
                return new AttackDefenseGame(plugin, this, instance, mode.id(), name, sides);
            case CLUTCH_SCENARIO:
                return new ClutchScenarioGame(plugin, this, instance, mode.id(), name, sides);
            case BED_FIGHT:
                return new BedFightGame(plugin, this, instance, mode.id(), name, sides);
            case BLOCK_METHOD:
                return new BlockMethodGame(plugin, this, instance, mode.id(), name, sides);
            case TOP_BED_FIGHT:
                return new TopBedFightGame(plugin, this, instance, mode.id(), name, sides);
            case LOBBY_FIGHT:
                return new LobbyFightGame(plugin, this, instance, mode.id(), name, sides);
            case LOBBY_FIGHT_FFA:
                return new FfaGame(plugin, this, instance, mode.id(), name, sides, false, true);
            case BED_BREAK:
                return new BedBreakGame(plugin, this, instance, mode.id(), name, sides, mode.playersPerMatch());
            case RED_ROVER_TOP_BRIDGE:
                return new RedRoverTopBridgeGame(plugin, this, instance, mode.id(), name, sides, plugin.redRover(),
                        plugin.redRoverRounds(), new Random());
            case CTF_BRIDGE:
                return new CaptureTheFlagGame(plugin, this, instance, mode.id(), name, sides, true);
            case CTF_CASTLE:
                return new CaptureTheFlagGame(plugin, this, instance, mode.id(), name, sides, false);
            case FAST_BEDWARS:
                return new FastBedwarsGame(plugin, this, instance, mode.id(), name, sides);
            case BEDWARS_2012:
                return new Bedwars2012Game(plugin, this, instance, mode.id(), name, sides, true);
            case BEDWARS_2012_CLASSIC:
                return new Bedwars2012Game(plugin, this, instance, mode.id(), name, sides, false);
            default:
                return new BedwarsGame(plugin, this, instance, mode.id(), name, sides);
        }
    }

    /** True if these players play the practice of the Private games (the lobby marked them before the start). */
    private boolean partyPractice(List<List<Player>> sides) {
        for (List<Player> side : sides) {
            for (Player player : side) {
                if (plugin.lobby().inPartyPractice(player.getUniqueId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A game of this mode a player can join now, on one of these maps; null if none. */
    private BedwarsGame joinable(String modeId, List<BedwarsMap> maps) {
        for (BedwarsGame game : running) {
            if (!game.modeId().equals(modeId) || !game.joinable()) {
                continue;
            }
            for (BedwarsMap map : maps) {
                if (map.world().equalsIgnoreCase(game.instance().map().world())) {
                    return game;
                }
            }
        }
        return null;
    }

    /**
     * The islands a game of this mode needs: one per team, two at least; a free for all, two (FFA
     * Archway, 16 teams of one: its players come back on any island, see {@link FfaGame}).
     */
    int islands(GameMode mode) {
        return plugin.settings().kind(mode.id()) == BedwarsSettings.Kind.FFA ? 2 : Math.max(2, mode.teams());
    }

    /**
     * The maps this mode can be played on now (config.yml: modes), the demo map when none can;
     * for Lobby Fight, only those with their waiting lobby (salle-attente:).
     */
    List<BedwarsMap> mapsFor(GameMode mode) {
        List<BedwarsMap> maps = plugin.maps().playable(islands(mode), plugin.settings().mapsOf(mode.id()));
        if (plugin.settings().kind(mode.id()).inWaitingLobby()) {
            maps = new ArrayList<>(maps);
            maps.removeIf(map -> map.waitingLobby() == null);
        }
        if (plugin.settings().exactTeams(mode.id())) {
            // Bedwars Normal: the 4s on the maps of 4 teams, the 2s on those of 8 (not the demo map)
            maps = new ArrayList<>(maps);
            maps.removeIf(map -> map.completeTeams().size() != mode.teams());
        }
        return maps;
    }

    /** The first player of the match, or null. */
    private static UUID firstPlayer(List<List<Player>> sides) {
        for (List<Player> side : sides) {
            if (!side.isEmpty()) {
                return side.get(0).getUniqueId();
            }
        }
        return null;
    }

    /**
     * The settings the party leader chooses in the lobby for a private game of Bedwars Normal
     * (reglages-prives): Speed, Max Diamond, Max Emerald, Break the Map ({@link PrivateRules}).
     */
    @Override
    public List<PrivateSetting> privateSettings(GameMode mode) {
        if (!plugin.settings().privateRules(mode.id())) {
            return Collections.emptyList();
        }
        List<PrivateSetting> offered = new ArrayList<>();
        for (PrivateRules.Setting setting : PrivateRules.SETTINGS) {
            offered.add(setting.toggles
                    ? PrivateSetting.toggles(setting.id, setting.name, iconOf(setting.id), setting.choices, WEAPON_ICONS,
                            setting.description)
                    : new PrivateSetting(setting.id, setting.name, iconOf(setting.id), setting.choices, setting.description));
        }
        return offered;
    }

    /** The icons of the weapons of One Hit One Kill in its menu, in the order of {@link PrivateRules#WEAPONS}. */
    private static final List<Material> WEAPON_ICONS = Arrays.asList(Material.WOOD_SWORD, Material.STONE_SWORD,
            Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.STICK, Material.BOW, Material.FIREBALL, Material.TNT);

    private static Material iconOf(String setting) {
        switch (setting) {
            case PrivateRules.SPEED:
                return Material.SUGAR;
            case PrivateRules.MAX_DIAMOND:
                return Material.DIAMOND;
            case PrivateRules.MAX_EMERALD:
                return Material.EMERALD;
            case PrivateRules.BREAK_MAP:
                return Material.IRON_PICKAXE;
            case PrivateRules.HEALTH_BUFF:
                return Material.GOLDEN_APPLE;
            case PrivateRules.LOW_GRAVITY:
                return Material.FEATHER;
            case PrivateRules.RESPAWN_TIME:
                return Material.WATCH;
            case PrivateRules.EVENT_TIMES:
                return Material.EXP_BOTTLE;
            case PrivateRules.BED_INSTABREAK:
                return Material.BED;
            case PrivateRules.ONE_HIT_ONE_KILL:
                return Material.DIAMOND_SWORD;
            default:
                return Material.PAPER;
        }
    }

    /**
     * The teams of this map in the order a game of this mode gives them to the sides of its match
     * (the map's complete teams, in the order of the colours): a wool of each colour, named in it,
     * for the team menu of Bedwars Normal in the lobby. Empty when the map does not play this mode.
     */
    @Override
    public List<ItemStack> teamIcons(GameMode mode, String map) {
        List<ItemStack> icons = new ArrayList<>();
        for (BedwarsMap playable : mapsFor(mode)) {
            if (!playable.world().equalsIgnoreCase(map)) {
                continue;
            }
            for (TeamColor color : playable.playingTeams(mode.teams())) {
                ItemStack wool = new ItemStack(Material.WOOL, 1, color.woolData());
                org.bukkit.inventory.meta.ItemMeta meta = wool.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.RESET + color.coloured());
                    wool.setItemMeta(meta);
                }
                icons.add(wool);
            }
        }
        return icons;
    }

    /**
     * The waiting lobby of a private game of Bedwars Normal on the map its leader chose (the owner's
     * ask of 25/11): that map's own (salle-attente:), else the map of salle-attente-privee (Archway
     * FFA). READY when a copy is free, else PREPARING (a copy is made); NONE when neither has one.
     */
    @Override
    public Gathering canGather(GameMode mode, String map) {
        BedwarsMap waitingMap = waitingMapFor(mode, map);
        if (waitingMap == null) {
            return Gathering.NONE;
        }
        List<BedwarsMap> maps = Collections.singletonList(waitingMap);
        if (plugin.instances().hasFree(maps)) {
            return Gathering.READY;
        }
        plugin.instances().prepare(maps, true);
        return Gathering.PREPARING;
    }

    /** The party in the waiting lobby of a copy of its own of the map it chose (or of salle-attente-privee). */
    @Override
    public boolean gather(GameMode mode, String map, List<Player> players) {
        BedwarsMap waitingMap = waitingMapFor(mode, map);
        if (waitingMap == null) {
            return false;
        }
        for (Player player : players) {
            if (games.containsKey(player.getUniqueId()) || waiting.containsKey(player.getUniqueId())) {
                return false;
            }
        }
        Instance instance = plugin.instances().acquire(Collections.singletonList(waitingMap));
        if (instance == null) {
            return false;
        }
        if (instance.map().waitingLobby() == null) {
            // a copy made before the map had its waiting lobby
            plugin.instances().release(instance);
            return false;
        }
        PrivateWaitingLobby lobby = new PrivateWaitingLobby(instance);
        for (Player player : players) {
            toldNoMap.remove(player.getUniqueId());
            waiting.put(player.getUniqueId(), lobby);
            lobby.welcome(player);
        }
        return true;
    }

    /**
     * Where a private game of this mode on this map waits: the chosen map if it has its waiting
     * lobby, else the map of salle-attente-privee; null if neither.
     */
    private BedwarsMap waitingMapFor(GameMode mode, String map) {
        if (map != null) {
            for (BedwarsMap candidate : mapsFor(mode)) {
                if (candidate.world().equalsIgnoreCase(map) && candidate.playable() && candidate.waitingLobby() != null) {
                    return candidate;
                }
            }
        }
        return privateWaitingMap();
    }

    /**
     * The waiting lobby all the players of these sides wait in, if it is a copy of one of these maps:
     * their game is played on that copy, under their feet (the owner's ask of 25/11); else null.
     */
    private PrivateWaitingLobby waitingOn(List<List<Player>> sides, List<BedwarsMap> maps) {
        PrivateWaitingLobby lobby = null;
        for (List<Player> side : sides) {
            for (Player player : side) {
                PrivateWaitingLobby of = waiting.get(player.getUniqueId());
                if (of == null || lobby != null && of != lobby) {
                    return null;
                }
                lobby = of;
            }
        }
        if (lobby == null || lobby.players().size() != count(sides)) {
            return null;
        }
        for (BedwarsMap map : maps) {
            if (map.world().equals(lobby.instance().map().world())) {
                return lobby;
            }
        }
        return null;
    }

    private static int count(List<List<Player>> sides) {
        int count = 0;
        for (List<Player> side : sides) {
            count += side.size();
        }
        return count;
    }

    /** The map of salle-attente-privee if it has a waiting lobby and can be played, else null (told once in the log). */
    private BedwarsMap privateWaitingMap() {
        String name = plugin.settings().privateWaitingMap();
        if (name.isEmpty()) {
            return null;
        }
        for (BedwarsMap map : plugin.maps().all()) {
            if (map.world().equalsIgnoreCase(name) && map.playable() && map.waitingLobby() != null) {
                return map;
            }
        }
        if (!warnedNoWaitingLobby) {
            warnedNoWaitingLobby = true;
            plugin.getLogger().warning("salle-attente-privee: " + name + " is no map with a waiting lobby (/bw lobbyfight on"
                    + " its floor): the private games of Bedwars Normal choose their map in the lobby.");
        }
        return null;
    }

    /** The player is no longer in the waiting lobby of their private game; its copy is free again once it is empty. */
    private void leaveWaiting(UUID player) {
        PrivateWaitingLobby lobby = waiting.remove(player);
        if (lobby != null) {
            lobby.remove(player);
            if (lobby.empty()) {
                plugin.instances().release(lobby.instance());
            }
        }
    }

    /**
     * The maps a player may choose for this mode in the lobby, ranked modes included: its maps that
     * can be played now. Without a choice, the map is drawn at random.
     */
    @Override
    public List<String> maps(GameMode mode) {
        List<String> names = new ArrayList<>();
        for (BedwarsMap map : mapsFor(mode)) {
            names.add(map.world());
        }
        return names;
    }

    private void noMap(GameMode mode, List<List<Player>> sides) {
        if (!warnedNoMap) {
            warnedNoMap = true;
            plugin.getLogger().warning("A " + mode.id() + " match is waiting, but none of its Bed Wars maps "
                    + plugin.settings().mapsOf(mode.id()) + " with " + islands(mode)
                    + " teams is ready: install it, or set one up with /bw (see the README of RagoBedwars).");
        }
        for (List<Player> side : sides) {
            for (Player player : side) {
                if (toldNoMap.add(player.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + "No Bed Wars map is ready for this mode yet: the staff sets "
                            + "one up with /bw. You keep your place in the queue.");
                }
            }
        }
    }

    /**
     * The Fast Shop, a training, lets its player go at once: for the match found in a queue they wait
     * in while they train, or for another training.
     */
    @Override
    public boolean letGo(Player player) {
        if (plugin.watchers().letGo(player)) {
            return true;
        }
        if (!canLetGo(player)) {
            return false;
        }
        player.closeInventory();
        games.get(player.getUniqueId()).leave(player, false);
        return true;
    }

    /** The Fast Shop, or a game they only watch. */
    @Override
    public boolean canLetGo(Player player) {
        if (plugin.watchers().isWatching(player.getUniqueId())) {
            return true;
        }
        BedwarsGame game = games.get(player.getUniqueId());
        return game instanceof FastShopGame && game.phase != BedwarsGame.Phase.ENDED;
    }

    /** /spectate: any game of the Bed Wars modes, the Fast Shop too. */
    @Override
    public boolean spectate(Player watcher, Player target) {
        BedwarsGame game = games.get(target.getUniqueId());
        if (game == null || game.phase() == Phase.ENDED) {
            return false;
        }
        plugin.watchers().watch(watcher, game, target);
        game.sidebars();
        return true;
    }

    @Override
    public void abandon(Player player) {
        if (plugin.watchers().leave(player)) {
            return;
        }
        BedwarsGame game = games.get(player.getUniqueId());
        if (game == null) {
            // in the waiting lobby of their private game, or nowhere: back to the lobby
            leaveWaiting(player.getUniqueId());
            plugin.lobby().sendToLobby(player);
        } else {
            game.leave(player, true);
        }
    }

    // --- Games ----------------------------------------------------------------------------------

    /** The game of this player, or null. */
    BedwarsGame gameOf(UUID player) {
        return games.get(player);
    }

    /** The kit this player last chose for the attack (or the defense) of Attack and Defense: 0, the first, at first. */
    int roundKit(UUID player, boolean attack) {
        int[] chosen = roundKits.get(player);
        return chosen == null ? 0 : chosen[attack ? 0 : 1];
    }

    void chooseRoundKit(UUID player, boolean attack, int index) {
        int[] chosen = roundKits.get(player);
        if (chosen == null) {
            chosen = new int[2];
            roundKits.put(player, chosen);
        }
        chosen[attack ? 0 : 1] = index;
    }

    /** The attack kit this player last chose in the Bed Break Simulator: 0, the first, at first. */
    int bedBreakKit(UUID player) {
        Integer chosen = bedBreakKits.get(player);
        return chosen == null ? 0 : chosen;
    }

    void chooseBedBreakKit(UUID player, int index) {
        bedBreakKits.put(player, index);
    }

    /** The player no longer belongs to their game. */
    void release(UUID player) {
        games.remove(player);
    }

    /** The player joined this game while it was on (an open game, a practice waiting for its players). */
    void joined(UUID player, BedwarsGame game) {
        games.put(player, game);
    }

    /** The maps a Block Method practice of this mode goes on on (its menu of maps). */
    List<BedwarsMap> blockMethodMaps(String modeId) {
        return plugin.maps().playable(2, plugin.settings().mapsOf(modeId));
    }

    /**
     * A Block Method practice goes on on another map: a new game on a copy of it, with the same
     * players, its round and counts, the old one handed over. False if no copy is free yet (one is
     * made: the practice asks again a second later); true once done, or if nobody is left.
     */
    boolean moveTo(BlockMethodGame old, BedwarsMap map) {
        List<BedwarsMap> one = Collections.singletonList(map);
        List<List<Player>> sides = old.sides();
        if (sides == null) {
            return true;
        }
        Instance instance = plugin.instances().acquire(one);
        if (instance == null) {
            plugin.instances().prepare(one, true);
            return false;
        }
        BlockMethodGame game = new BlockMethodGame(plugin, this, instance, old.modeId(), old.modeName, sides);
        game.modeLine = old.modeLine;
        game.carryFrom(old);
        old.handOver();
        for (List<Player> side : sides) {
            for (Player player : side) {
                games.put(player.getUniqueId(), game);
            }
        }
        running.add(game);
        finished(old);
        game.begin();
        return true;
    }

    /** The game is over and its players are gone: its world is made ready for the next one. */
    void finished(BedwarsGame game) {
        for (UUID id : game.players()) {
            if (games.get(id) == game) {
                games.remove(id);
            }
        }
        if (running.remove(game)) {
            plugin.instances().release(game.instance());
        }
    }

    /** The plugin is being disabled: every game stops, its players go back to the lobby. */
    void abortAll() {
        for (BedwarsGame game : new ArrayList<>(running)) {
            game.abort();
            finished(game);
        }
        for (UUID id : new ArrayList<>(waiting.keySet())) {
            leaveWaiting(id);
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                plugin.lobby().sendToLobby(player);
            }
        }
    }

    int running() {
        return running.size();
    }

    /** The game played in this world, or null. */
    private BedwarsGame gameIn(World world) {
        for (BedwarsGame game : running) {
            if (game.instance().world().equals(world)) {
                return game;
            }
        }
        return null;
    }

    private boolean isGameWorld(World world) {
        return plugin.instances().isGameWorld(world);
    }

    /** The player's game if they play in it now (alive, not waiting to respawn), else null. */
    private BedwarsGame playing(Player player) {
        BedwarsGame game = games.get(player.getUniqueId());
        if (game == null || game.phase() != Phase.PLAYING) {
            return null;
        }
        Gamer gamer = game.gamer(player.getUniqueId());
        return gamer == null || gamer.respawning || gamer.eliminated ? null : game;
    }

    // --- Moving, the void -----------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        BedwarsGame game = games.get(event.getPlayer().getUniqueId());
        if (game == null) {
            PrivateWaitingLobby lobby = waiting.get(event.getPlayer().getUniqueId());
            if (lobby != null && event.getTo().getY() < lobby.fallLine()) {
                // off the waiting lobby: back in its middle
                event.getPlayer().setFallDistance(0f);
                event.setTo(lobby.middle());
            }
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        Location unstuck = game.unstuck(event.getPlayer(), to);
        if (unstuck != null) {
            event.getPlayer().setFallDistance(0f);
            event.setTo(unstuck);
            return;
        }
        if (game.phase() == Phase.STARTING
                && (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY())) {
            Location still = from.clone();
            still.setYaw(to.getYaw());
            still.setPitch(to.getPitch());
            event.setTo(still);
            return;
        }
        Location kept = game.keepIn(event.getPlayer(), from, to);
        if (kept != null) {
            event.setTo(kept);
            return;
        }
        // the void; in a game played on the top bridge, a fall off its platform (not for a spectator)
        if (to.getY() < game.instance().map().voidY() || (to.getY() < game.fallLine()
                && event.getPlayer().getGameMode() != org.bukkit.GameMode.SPECTATOR)) {
            game.kill(event.getPlayer(), null, true);
        }
    }

    /** Out of the game world by other means than the game (a command): out of the game. */
    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        final Player player = event.getPlayer();
        PrivateWaitingLobby lobby = waiting.get(player.getUniqueId());
        if (lobby != null && event.getFrom().equals(lobby.instance().world())) {
            // out of the waiting lobby of their private game (to its map, the lobby, a command)
            leaveWaiting(player.getUniqueId());
        }
        final BedwarsGame game = games.get(player.getUniqueId());
        if (game != null && event.getFrom().equals(game.instance().world())
                && !player.getWorld().equals(game.instance().world())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && games.get(player.getUniqueId()) == game) {
                    game.leave(player, true);
                }
            });
        }
    }

    // --- Damage, deaths -------------------------------------------------------------------------

    /**
     * In a game world, only players take damage. Between players: no friendly fire, no hit on a
     * player still protected after a respawn (Bed Fight), the hit is remembered for the kill, and a
     * hit that would kill is the death of Bed Wars (respawn or out), as is the hit of an enemy with a
     * weapon of One Hit One Kill (a private game's setting).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            if (isGameWorld(entity.getWorld()) && !creatureHurt(event)) {
                event.setCancelled(true);
            }
            return;
        }
        Player victim = (Player) entity;
        BedwarsGame game = games.get(victim.getUniqueId());
        if (game == null) {
            if (waiting.containsKey(victim.getUniqueId())) {
                // nobody is hurt in the waiting lobby of a private game
                event.setCancelled(true);
            }
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            game.kill(victim, null, true);
            return;
        }
        if (playing(victim) != game) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && !game.fallDamage()) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent
                && ((EntityDamageByEntityEvent) event).getDamager() instanceof EnderPearl) {
            // an ender pearl lands without hurting its thrower
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent
                && ((EntityDamageByEntityEvent) event).getDamager() instanceof Fireball) {
            // A fireball's hit and blast: neither its damage nor its push, which the server would
            // give with them; its own push and damage, as on Hypixel, come when it lands (onLand).
            event.setCancelled(true);
            return;
        }
        Gamer target = game.gamer(victim.getUniqueId());
        Player attacker = null;
        Entity damager = event instanceof EntityDamageByEntityEvent ? ((EntityDamageByEntityEvent) event).getDamager() : null;
        Utilities.Summoned creature = damager == null ? null : game.utilities.summoned(damager);
        if (creature != null) {
            // a Bedbug or a Dream Defender: never on its own team; its kills are its owner's
            if (creature.team == target.team || !game.pvp()) {
                event.setCancelled(true);
                return;
            }
            if (damager instanceof IronGolem) {
                event.setDamage(Utilities.DEFENDER_DAMAGE);
            }
            attacker = game.utilities.owner(damager);
            Gamer owner = attacker == null ? null : game.gamer(attacker.getUniqueId());
            if (owner != null) {
                game.hit(target, owner);
            }
        } else if (damager != null) {
            attacker = attacker(damager);
            if (attacker != null && !attacker.equals(victim)) {
                Gamer author = game.gamer(attacker.getUniqueId());
                if (author == null || games.get(attacker.getUniqueId()) != game || author.team == target.team
                        || author.respawning || author.eliminated || !game.pvp()) {
                    event.setCancelled(true);
                    return;
                }
                // Hitting an enemy ends the author's own protection after a respawn at once; the
                // victim's, while it lasts, makes the hit do nothing: no damage, no knockback.
                author.protection.engaged();
                if (target.protection.on(System.currentTimeMillis())) {
                    event.setCancelled(true);
                    return;
                }
                game.hit(target, author);
                if (game.rules.oneHitKills(weaponOf(damager))) {
                    event.setCancelled(true);
                    game.kill(victim, attacker, false);
                    return;
                }
            } else {
                // Their own fireball or TNT: the damage and the jump, nobody to credit.
                attacker = null;
            }
        }
        if (event.getFinalDamage() >= victim.getHealth()) {
            event.setCancelled(true);
            game.kill(victim, attacker, false);
        }
    }

    /**
     * A fireball lands (it has just exploded): it pushes the players around and hurts them, as on
     * Hypixel. The blast itself, which only breaks placed blocks, hurts and pushes nobody (onDamage).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLand(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!isGameWorld(projectile.getWorld())) {
            return;
        }
        BedwarsGame game = gameIn(projectile.getWorld());
        if (game == null) {
            return;
        }
        if (projectile instanceof Fireball) {
            game.blast((Fireball) projectile, attacker(projectile));
        } else if (projectile instanceof Snowball && projectile.getShooter() instanceof Player) {
            // the Bedbug: a silverfish where it lands
            Player thrower = (Player) projectile.getShooter();
            if (playing(thrower) == game) {
                game.utilities.bedbug(thrower, projectile.getLocation());
            }
        }
    }

    /** A Bridge Egg thrown: its bridge follows it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile instanceof Egg && projectile.getShooter() instanceof Player) {
            Player thrower = (Player) projectile.getShooter();
            BedwarsGame game = playing(thrower);
            if (game != null) {
                game.utilities.bridgeEgg(thrower, (Egg) projectile);
            }
        }
    }

    /**
     * An ender pearl lands: every player of the game (its world), the thrower too, hears the
     * teleport wherever they are (the 1.8 server plays no sound there).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearl(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL || to == null || !isGameWorld(to.getWorld())) {
            return;
        }
        for (Player listener : to.getWorld().getPlayers()) {
            listener.playSound(heardAt(listener.getLocation(), to), Sound.ENDERMAN_TELEPORT, 1f, 1f);
        }
    }

    /** The spectator menu of a player out of their game takes them to any player: only to those of its world. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpectate(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return;
        }
        BedwarsGame game = games.get(event.getPlayer().getUniqueId());
        if (game != null && !game.instance().world().equals(event.getTo().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** How far from a listener a sound from afar is played: within hearing, at full volume. */
    static final double HEARD_WITHIN = 8;

    /**
     * Where a listener hears a sound from this source: there if it is near, else on the way to it,
     * {@value #HEARD_WITHIN} blocks off, so that it comes from its side and at full volume.
     */
    static Location heardAt(Location listener, Location source) {
        double dx = source.getX() - listener.getX();
        double dy = source.getY() - listener.getY();
        double dz = source.getZ() - listener.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= HEARD_WITHIN) {
            return source;
        }
        double k = HEARD_WITHIN / distance;
        return new Location(listener.getWorld(), listener.getX() + dx * k, listener.getY() + dy * k, listener.getZ() + dz * k);
    }

    @EventHandler
    public void onEggThrow(PlayerEggThrowEvent event) {
        if (games.containsKey(event.getPlayer().getUniqueId()) || isGameWorld(event.getPlayer().getWorld())) {
            event.setHatching(false);
        }
    }

    /**
     * True if this damage to a creature of a game stands (a Bedbug, a Dream Defender): from an
     * enemy, a fall, a fire, never from its own team. Every other creature of a game world is safe.
     */
    private boolean creatureHurt(EntityDamageEvent event) {
        BedwarsGame game = gameIn(event.getEntity().getWorld());
        Utilities.Summoned creature = game == null ? null : game.utilities.summoned(event.getEntity());
        if (creature == null || game.phase() != Phase.PLAYING) {
            return false;
        }
        if (!(event instanceof EntityDamageByEntityEvent)) {
            return true;
        }
        Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
        Utilities.Summoned other = game.utilities.summoned(damager);
        if (other != null) {
            return other.team != creature.team;
        }
        Player attacker = attacker(damager);
        if (attacker == null) {
            return true;
        }
        Gamer gamer = game.gamer(attacker.getUniqueId());
        return gamer != null && gamer.team != creature.team && playing(attacker) == game;
    }

    /** The creatures of a game go for their enemies only; no other creature goes for anyone. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        Entity entity = event.getEntity();
        if (!isGameWorld(entity.getWorld())) {
            return;
        }
        BedwarsGame game = gameIn(entity.getWorld());
        Utilities.Summoned creature = game == null ? null : game.utilities.summoned(entity);
        if (creature == null) {
            event.setCancelled(true);
        } else if (event.getTarget() != null && !game.utilities.enemy(creature, event.getTarget())) {
            event.setCancelled(true);
        }
    }

    /** A creature of a game dies: it leaves nothing. */
    @EventHandler
    public void onCreatureDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || !isGameWorld(entity.getWorld())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        BedwarsGame game = gameIn(entity.getWorld());
        if (game != null) {
            game.utilities.died(entity);
        }
    }

    /**
     * The weapon of One Hit One Kill of this damage (an index of {@link PrivateRules#WEAPONS}), -1
     * if none: the item in the hand of the one who hit, the arrow of a bow, the TNT. The fireball's
     * is in {@link BedwarsGame#blast}, its hit hurting nobody here.
     */
    private static int weaponOf(Entity damager) {
        if (damager instanceof Player) {
            ItemStack hand = ((Player) damager).getItemInHand();
            return hand == null ? -1 : PrivateRules.weaponInHand(hand.getType().name());
        }
        if (damager instanceof Arrow) {
            return PrivateRules.BOW;
        }
        return damager instanceof TNTPrimed ? PrivateRules.TNT : -1;
    }

    /** The player behind this damage: the one who hit, shot, threw the fireball or lit the TNT. */
    private Player attacker(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            Object shooter = ((Projectile) damager).getShooter();
            return shooter instanceof Player ? (Player) shooter : null;
        }
        if (damager instanceof TNTPrimed) {
            UUID owner = tntOwners.get(damager);
            if (owner != null) {
                return Bukkit.getPlayer(owner);
            }
            Entity source = ((TNTPrimed) damager).getSource();
            return source instanceof Player ? (Player) source : null;
        }
        return null;
    }

    /** A death the damage rule did not catch (a /kill): the same death, once respawned. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        final Player victim = event.getEntity();
        final BedwarsGame game = games.get(victim.getUniqueId());
        if (game == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setDeathMessage(null);
        event.setKeepInventory(true);
        final Player killer = victim.getKiller();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!victim.isOnline() || games.get(victim.getUniqueId()) != game) {
                return;
            }
            if (victim.isDead()) {
                victim.spigot().respawn();
            }
            game.kill(victim, killer, false);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        BedwarsGame game = games.get(event.getPlayer().getUniqueId());
        if (game != null) {
            event.setRespawnLocation(game.watchPoint());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (games.containsKey(event.getEntity().getUniqueId()) || waiting.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Player) {
                ((Player) event.getEntity()).setFoodLevel(20);
            }
        }
    }

    // --- Blocks ---------------------------------------------------------------------------------

    @SuppressWarnings("deprecation") // BlockState#getTypeId and getRawData: the 1.8 block id and data
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!games.containsKey(player.getUniqueId())) {
            if (isGameWorld(block.getWorld())) {
                event.setCancelled(true);
            }
            return;
        }
        BedwarsGame game = playing(player);
        String refused = game == null ? "" : game.cannotBuildAt(block);
        if (refused != null) {
            event.setCancelled(true);
            if (!refused.isEmpty()) {
                player.sendMessage(ChatColor.RED + refused);
            }
            return;
        }
        if (block.getType() == Material.TNT) {
            // Lit at once, as on Hypixel.
            event.setCancelled(true);
            ItemStack hand = player.getItemInHand();
            if (hand.getAmount() > 1) {
                hand.setAmount(hand.getAmount() - 1);
            } else {
                player.setItemInHand(null);
            }
            TNTPrimed tnt = block.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5), TNTPrimed.class);
            tnt.setFuseTicks(BedwarsGame.TNT_FUSE_TICKS);
            tntOwners.put(tnt, player.getUniqueId());
            return;
        }
        if (block.getType() == Material.CHEST) {
            // the Compact Pop-up Tower: no chest, the tower around it
            event.setCancelled(true);
            useOne(player);
            game.utilities.tower(player, block);
            return;
        }
        game.instance().markPlaced(block, event.getBlockReplacedState().getTypeId(),
                event.getBlockReplacedState().getRawData());
        game.placed(player, block);
    }

    /** Nothing wears out in a game: swords, tools, bows and armour stay whole (see Unbreakable). */
    @EventHandler(ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent event) {
        if (games.containsKey(event.getPlayer().getUniqueId()) || isGameWorld(event.getPlayer().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Every few ticks: the items of the players in a game unbreakable, the new ones too (bought, given). */
    void keepUnbreakable() {
        for (UUID id : games.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                Unbreakable.apply(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!games.containsKey(player.getUniqueId())) {
            if (isGameWorld(block.getWorld())) {
                event.setCancelled(true);
            }
            return;
        }
        BedwarsGame game = playing(player);
        if (game == null) {
            event.setCancelled(true);
            return;
        }
        BedwarsGame.Team bed = game.bedTeam(block);
        if (bed != null) {
            event.setCancelled(true);
            if (bed == game.gamer(player.getUniqueId()).team) {
                player.sendMessage(ChatColor.RED + "You can't destroy your own bed!");
            } else {
                game.breakBed(player, bed);
            }
            return;
        }
        if (!game.instance().isPlaced(block)) {
            if (game.breaksMap(block)) {
                game.instance().markBroken(block);
                return;
            }
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You can only break blocks placed by a player!");
            return;
        }
        game.instance().unmark(block);
    }

    /**
     * Bed Instabreak, a setting of the party leader: the first punch on the bed of another team breaks
     * it, then {@link #onBreak} takes it as any bed broken.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBedPunch(BlockDamageEvent event) {
        BedwarsGame game = playing(event.getPlayer());
        if (game == null || !game.rules.bedInstabreak() || event.getBlock().getType() != Material.BED_BLOCK) {
            return;
        }
        BedwarsGame.Team bed = game.bedTeam(event.getBlock());
        if (bed != null && bed != game.gamer(event.getPlayer().getUniqueId()).team) {
            event.setInstaBreak(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        final Player player = event.getPlayer();
        if (!games.containsKey(player.getUniqueId())) {
            if (isGameWorld(player.getWorld())) {
                event.setCancelled(true);
            }
            return;
        }
        BedwarsGame game = playing(player);
        Block block = event.getBlockClicked().getRelative(event.getBlockFace());
        String refused = game == null ? "" : game.cannotBuildAt(block);
        if (refused != null) {
            event.setCancelled(true);
            if (!refused.isEmpty()) {
                player.sendMessage(ChatColor.RED + refused);
            }
            return;
        }
        game.instance().markPlaced(block);
        game.placed(player, block);
        // The bucket is used up, as on Hypixel.
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack hand = player.getItemInHand();
            if (hand != null && hand.getType() == Material.BUCKET) {
                player.setItemInHand(null);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (games.containsKey(event.getPlayer().getUniqueId()) || isGameWorld(event.getPlayer().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Water and lava flowing: where they go is taken back after the game. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        BedwarsGame game = gameIn(event.getBlock().getWorld());
        if (game != null) {
            game.instance().markPlaced(event.getToBlock());
        } else if (isGameWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    /**
     * A fireball of a game explodes: the server's blast would break no block (mobGriefing is off in
     * the game worlds), so it is made again, with its blocks (FireballBlast); onExplode keeps those
     * a game lets go.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Fireball && isGameWorld(entity.getWorld())
                && FireballBlast.explode(entity, event.getRadius(), event.getFire(), plugin.getLogger())) {
            event.setCancelled(true);
        }
    }

    /**
     * Explosions only break placed blocks, never the blast-proof glass, obsidian or beds; nor end
     * stone for a fireball, as on Hypixel (TNT breaks it).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        World world = event.getLocation().getWorld();
        if (!isGameWorld(world)) {
            return;
        }
        event.setYield(0f);
        BedwarsGame game = gameIn(world);
        boolean fireball = event.getEntity() instanceof Fireball;
        for (Iterator<Block> it = event.blockList().iterator(); it.hasNext();) {
            Block block = it.next();
            if (game == null || !game.instance().isPlaced(block) || BLAST_PROOF.contains(block.getType())
                    || (fireball && block.getType() == Material.ENDER_STONE)) {
                it.remove();
            } else {
                game.instance().unmark(block);
            }
        }
    }

    // --- Items ----------------------------------------------------------------------------------

    /**
     * 2012 Bedwars: a right click on the top of a block with the team's bed hides it there (the game
     * puts it, not the client: the places it refuses, its message).
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onHideBed(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !Bedwars2012Game.isBedToHide(event.getItem())) {
            return;
        }
        BedwarsGame game = games.get(event.getPlayer().getUniqueId());
        if (game instanceof Bedwars2012Game) {
            event.setCancelled(true);
            ((Bedwars2012Game) game).hideBed(event.getPlayer(), event.getClickedBlock(), event.getBlockFace());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        BedwarsGame game = games.get(player.getUniqueId());
        if (game == null) {
            if (waiting.containsKey(player.getUniqueId())) {
                // the waiting lobby of a private game: its doors, chests and buttons are not used
                event.setUseInteractedBlock(Event.Result.DENY);
            }
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            return;
        }
        if (game instanceof BlockMethodGame
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && ((BlockMethodGame) game).use(player, event.getItem())) {
            // Block Method: the blaze rod (the round again), the map (the menu of the maps)
            event.setCancelled(true);
            return;
        }
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && EndOfGame.clicked(plugin, player, event.getItem())) {
            // the end of the game: the Play again paper or the Leave bed
            event.setCancelled(true);
            return;
        }
        if (!game.itemsUsable() && event.getItem() != null
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            // the shop practice: the items bought are only shown
            event.setCancelled(true);
            return;
        }
        // A right click only: denying the block on a left click would also stop it from being broken (the beds).
        ItemStack item = event.getItem();
        boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && CHESTS.contains(event.getClickedBlock().getType())
                && item != null && BedwarsGame.resourceName(item.getType()) != null) {
            // a chest hit with iron, gold, diamonds or emeralds in hand: they go straight into it
            event.setCancelled(true);
            if (playing(player) == game) {
                game.deposit(player, event.getClickedBlock());
            }
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && CHESTS.contains(event.getClickedBlock().getType())
                && !(player.isSneaking() && item != null && item.getType().isBlock())) {
            // the chests of the islands and the ender chests, those of this game
            event.setCancelled(true);
            if (playing(player) == game) {
                player.openInventory(game.chest(event.getClickedBlock(), game.gamer(player.getUniqueId())));
            }
            return;
        }
        if (right && item != null && (item.getType() == Material.SNOW_BALL || item.getType() == Material.EGG
                || item.getType() == Material.MONSTER_EGG)) {
            // the Bedbug, the Bridge Egg, the Dream Defender: only while playing
            if (playing(player) != game) {
                event.setCancelled(true);
                return;
            }
            if (item.getType() == Material.MONSTER_EGG) {
                event.setCancelled(true);
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    useOne(player);
                    game.utilities.defender(player, event.getClickedBlock().getRelative(event.getBlockFace()).getLocation()
                            .add(0.5, 0, 0.5));
                }
                return;
            }
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && NOT_USED.contains(event.getClickedBlock().getType())) {
            event.setUseInteractedBlock(Event.Result.DENY);
            if (item != null && item.getType().isBlock()) {
                // Denying the block alone also stops the block in hand from being placed against it: wool on a
                // bed. On a bed, only with shift held, as on Hypixel: a click alone places nothing.
                event.setUseItemInHand(placesOn(event.getClickedBlock().getType(), player.isSneaking())
                        ? Event.Result.ALLOW : Event.Result.DENY);
            }
        }
        if (item != null && item.getType() == Material.FIREBALL
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            if (playing(player) == game) {
                game.throwFireball(player);
            }
        }
    }

    /** The villagers: the item shop, the team upgrades. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Villager) || !isGameWorld(clicked.getWorld())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        BedwarsGame game = playing(player);
        if (game == null || !game.instance().world().equals(clicked.getWorld())) {
            return;
        }
        if (game.isUpgrades(clicked)) {
            game.upgradesClicked(player);
        } else if (game.isShop(clicked)) {
            game.shopOpened(player);
            ShopMenu.open(player, game, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (isGameWorld(event.getRightClicked().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** The Magic Milk: it keeps its drinker from the traps, and their potions stay. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMilk(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        BedwarsGame game = games.get(player.getUniqueId());
        if (game == null || event.getItem().getType() != Material.MILK_BUCKET) {
            return;
        }
        event.setCancelled(true);
        if (playing(player) == game) {
            useOne(player);
            game.utilities.milk(player, game.gamer(player.getUniqueId()));
        }
    }

    /** One of the item in hand is used up. */
    private static void useOne(Player player) {
        ItemStack hand = player.getItemInHand();
        if (hand != null && hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.setItemInHand(null);
        }
    }

    /** The empty bottle of a potion goes, as on Hypixel; the mode may add to what was eaten. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        final Player player = event.getPlayer();
        BedwarsGame game = games.get(player.getUniqueId());
        if (game == null) {
            return;
        }
        if (event.getItem().getType() == Material.POTION) {
            Bukkit.getScheduler().runTask(plugin, () -> player.getInventory().remove(Material.GLASS_BOTTLE));
        }
        if (playing(player) == game) {
            game.ate(player, event.getItem());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        InventoryHolder menu = event.getView().getTopInventory().getHolder();
        if (menu instanceof ShopMenu.Holder || menu instanceof UpgradeMenu.Holder || menu instanceof FfaSpawnMenu.Holder
                || menu instanceof KitMenu.Holder || menu instanceof BlockMethodMenu.Holder) {
            event.setCancelled(true);
            if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
                return;
            }
            if (menu instanceof BlockMethodMenu.Holder) {
                BlockMethodMenu.click(player, (BlockMethodMenu.Holder) menu, event.getRawSlot());
            } else if (menu instanceof KitMenu.Holder) {
                KitMenu.click(player, (KitMenu.Holder) menu, event.getRawSlot());
            } else if (menu instanceof FfaSpawnMenu.Holder) {
                FfaSpawnMenu.click(player, (FfaSpawnMenu.Holder) menu, event.getRawSlot());
            } else if (menu instanceof ShopMenu.Holder) {
                ShopMenu.click(player, (ShopMenu.Holder) menu, event.getRawSlot());
            } else {
                UpgradeMenu.click(player, (UpgradeMenu.Holder) menu, event.getRawSlot());
            }
            return;
        }
        // The armour is kept until the end: it stays on.
        if (games.containsKey(player.getUniqueId()) && event.getSlotType() == InventoryType.SlotType.ARMOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder menu = event.getView().getTopInventory().getHolder();
        if (menu instanceof ShopMenu.Holder || menu instanceof UpgradeMenu.Holder || menu instanceof FfaSpawnMenu.Holder
                || menu instanceof KitMenu.Holder || menu instanceof BlockMethodMenu.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (games.containsKey(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** The wooden sword and the tools are kept: they cannot be dropped. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (waiting.containsKey(player.getUniqueId())) {
            // the items of the waiting lobby stay
            event.setCancelled(true);
            return;
        }
        if (!games.containsKey(player.getUniqueId())) {
            return;
        }
        String type = event.getItemDrop().getItemStack().getType().name();
        if (games.get(player.getUniqueId()) instanceof BlockMethodGame
                && BlockMethodGame.isTool(event.getItemDrop().getItemStack().getType())) {
            // the blaze rod and the map of Block Method stay in the hotbar
            event.setCancelled(true);
            return;
        }
        if (playing(player) == null || type.equals("WOOD_SWORD") || type.endsWith("_PICKAXE") || type.endsWith("_AXE")
                || type.equals("SHEARS")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        if (games.containsKey(player.getUniqueId()) && playing(player) == null || waiting.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** A pickup that happens: the forge's iron and gold for the teammates standing at it too. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickedUp(PlayerPickupItemEvent event) {
        BedwarsGame game = games.get(event.getPlayer().getUniqueId());
        if (game != null) {
            // the stack holds, during the event, what the player picks up of it
            game.pickedUp(event.getPlayer(), event.getItem(), event.getItem().getItemStack().getAmount());
        }
    }

    // --- Chat -----------------------------------------------------------------------------------

    /**
     * Each game has its own chat, as on Hypixel: the lobby does not hear it, nor it the lobby. In a
     * team game, a player talks to their team, or to everyone with "!" first; the eliminated talk
     * among the spectators. The chat runs on its own threads: who hears is worked out on the main one.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(final AsyncPlayerChatEvent event) {
        if (!event.isAsynchronous()) {
            route(event);
            return;
        }
        try {
            Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                route(event);
                return null;
            }).get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | CancellationException e) {
            // the server is stopping or too busy: the message goes as it is
        }
    }

    /**
     * Main thread: who hears this message, and how it reads. Those who watch a game (/spectate) hear
     * what its spectators hear (all but the teams' own chat) and talk among them.
     */
    private void route(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        Watchers watchers = plugin.watchers();
        final BedwarsGame watched = watchers.gameOf(sender.getUniqueId());
        if (watched != null) {
            event.getRecipients().removeIf(player -> {
                if (watchers.gameOf(player.getUniqueId()) == watched) {
                    return false;
                }
                Gamer other = games.get(player.getUniqueId()) == watched ? watched.gamer(player.getUniqueId()) : null;
                return other == null || !other.eliminated;
            });
            event.setFormat(ChatColor.GRAY + "[SPECTATOR] " + event.getFormat());
            return;
        }
        final PrivateWaitingLobby lobby = waiting.get(sender.getUniqueId());
        if (lobby != null) {
            // the waiting lobby of a private game: its party only
            event.getRecipients().removeIf(player -> waiting.get(player.getUniqueId()) != lobby);
            return;
        }
        final BedwarsGame game = games.get(sender.getUniqueId());
        if (game == null) {
            event.getRecipients().removeIf(player -> games.containsKey(player.getUniqueId())
                    || watchers.isWatching(player.getUniqueId()) || waiting.containsKey(player.getUniqueId()));
            return;
        }
        event.getRecipients().removeIf(player -> games.get(player.getUniqueId()) != game
                && watchers.gameOf(player.getUniqueId()) != game);
        final Gamer gamer = game.gamer(sender.getUniqueId());
        if (gamer == null || !game.chatTeams()) {
            return;
        }
        String format = event.getFormat();
        if (gamer.eliminated) {
            event.getRecipients().removeIf(player -> {
                Gamer other = game.gamer(player.getUniqueId());
                return other != null && !other.eliminated;
            });
            event.setFormat(ChatColor.GRAY + "[SPECTATOR] " + format);
        } else if (event.getMessage().startsWith("!") && event.getMessage().length() > 1) {
            event.setMessage(event.getMessage().substring(1).trim());
            event.setFormat(ChatColor.GOLD + "[SHOUT] " + gamer.team.color.chat() + "[" + gamer.team.color.displayName()
                    .toUpperCase(java.util.Locale.ROOT) + "] " + format);
        } else {
            event.getRecipients().removeIf(player -> {
                Gamer other = game.gamer(player.getUniqueId());
                return other == null || other.team != gamer.team;
            });
            event.setFormat(ChatColor.GREEN + "[TEAM] " + format);
        }
    }

    // --- Leaving --------------------------------------------------------------------------------

    /**
     * Leaving the server in the middle of a game: out of it, or in a game that waits for them (a
     * classic game of Bed Wars, ranked or not: {@link BedwarsGame#mayComeBack}) gone for a while,
     * their place kept. Before RagoLobby forgets the player.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        toldNoMap.remove(event.getPlayer().getUniqueId());
        leaveWaiting(event.getPlayer().getUniqueId());
        BedwarsGame game = games.get(event.getPlayer().getUniqueId());
        if (game != null) {
            if (game.mayComeBack(event.getPlayer())) {
                game.away(event.getPlayer());
            } else {
                game.leave(event.getPlayer(), false);
            }
        }
    }

    /**
     * Back on the server while their game waits for them ({@link BedwarsGame#awaits}): after the
     * lobby (MONITOR, the lobby is HIGHEST), back in the game. A game that no longer waits for them
     * lets them go.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        BedwarsGame game = games.get(player.getUniqueId());
        if (game == null) {
            return;
        }
        if (game.awaits(player.getUniqueId()) && plugin.lobby().backInGame(player, game.modeId())) {
            game.returned(player);
        } else {
            games.remove(player.getUniqueId());
        }
    }

    // --- The game worlds stay as they are -------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState() && isGameWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    /** A chunk of a game world loads (once: its chunks stay loaded): what the map kept of a game played on it goes. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isGameWorld(event.getWorld())) {
            return;
        }
        for (Entity entity : event.getChunk().getEntities()) {
            if (MapLeftovers.leftover(entity)) {
                entity.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (isGameWorld(event.getLocation().getWorld())
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (isGameWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (isGameWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (isGameWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (isGameWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (isGameWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (isGameWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    /**
     * True if a block in hand goes against this block of the map that nobody uses (a chest, a
     * furnace, a bed): always, but against a bed only with shift held, as on Hypixel.
     */
    static boolean placesOn(Material clicked, boolean sneaking) {
        return clicked != Material.BED_BLOCK || sneaking;
    }
}
