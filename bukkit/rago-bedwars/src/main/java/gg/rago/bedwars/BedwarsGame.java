package gg.rago.bedwars;

import gg.rago.bedwars.ShopItem.Category;
import gg.rago.bedwars.ShopItem.Currency;
import gg.rago.lobby.api.EndOfGame;
import gg.rago.lobby.api.FireballAim;
import gg.rago.lobby.api.ReplayRecording;
import gg.rago.lobby.api.ReplaySetup;
import gg.rago.lobby.api.Replays;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * One Bed Wars game in one game world: the teams on their islands, their beds, generators and
 * shops, the deaths, the beds broken and the final kills, the timed events (the diamond and emerald
 * generators going up a tier, the end of the beds, the end of the game), until one team is left.
 * The result goes to the lobby's stats; everyone goes back to the lobby at once. Main thread only.
 */
class BedwarsGame {

    /** WAITING: a practice that waits for its players; STARTING: the countdown, everyone frozen. */
    enum Phase { WAITING, STARTING, PLAYING, ENDED }

    /** A team in this game. */
    static final class Team {
        final TeamColor color;
        final BedwarsMap.Team points;
        final List<UUID> members = new ArrayList<>();
        boolean bed = true;
        /** Its upgrades and traps, bought at the upgrades NPC. */
        final Upgrades upgrades = new Upgrades();
        /** How far its base goes around its spawn: for its traps and its Heal Pool. */
        final double baseRadius;

        Team(TeamColor color, BedwarsMap.Team points) {
            this.color = color;
            this.points = points;
            this.baseRadius = Upgrades.baseRadius(points);
        }

        /** True if this place is in the team's base. */
        boolean inBase(Location location) {
            MapPoint spawn = points == null ? null : points.spawn;
            return spawn != null && Upgrades.inBase(location.getX() - spawn.x(), location.getY() - spawn.y(),
                    location.getZ() - spawn.z(), baseRadius);
        }
    }

    /** A player of this game. */
    static final class Gamer {
        final UUID id;
        final String name;
        /** The team: it changes between the rounds of a practice (invis practice). */
        Team team;
        final List<String> hotbar;
        final List<ShopItem> quickBuy;
        boolean eliminated;
        boolean respawning;
        /** Where the player was put for the start (place), and until when one sinking under it goes back on it. */
        Location placedAt;
        long unstickUntil;
        /** 0 leather, 1 chainmail, 2 iron, 3 diamond. */
        int armor;
        /** -1 none, else the tier in {@link ShopCatalog#pickaxes()}. */
        int pickaxe = -1;
        int axe = -1;
        boolean shears;
        UUID lastDamager;
        long lastDamageAt;
        int kills;
        int finalKills;
        int beds;
        long lastFireball;
        /** Until when the Magic Milk keeps the player from setting off the traps. */
        long milkUntil;
        /** Their ender chest in this game, made when first opened. */
        Inventory enderChest;
        BukkitTask respawn;

        Gamer(Player player, Team team, List<String> hotbar, List<ShopItem> quickBuy) {
            this.id = player.getUniqueId();
            this.name = player.getDisplayName();
            this.team = team;
            this.hotbar = hotbar;
            this.quickBuy = quickBuy;
        }

        String coloured() {
            return team.color.chat() + name;
        }
    }

    /** A hit gives the kill to its author if the player dies within this time (a fall in the void). */
    static final long CREDIT_MS = 10_000L;
    /** Around a spawn, no block can be placed: this many blocks, horizontally. */
    static final double SPAWN_PROTECTION = 3.0;
    /** Around a generator, no block can be placed. */
    static final double GENERATOR_PROTECTION = 2.0;
    static final long FIREBALL_COOLDOWN_MS = 500L;
    static final int FIREBALL_YIELD = 3;
    static final int TNT_FUSE_TICKS = 52;
    private static final String CHECK = "✔";
    private static final String CROSS = "✘";

    final RagoBedwars plugin;
    final BedwarsHost host;
    final Instance instance;
    final String modeId;
    final String modeName;
    final BedwarsSettings settings;
    final ShopCatalog catalog;
    final List<Team> teams = new ArrayList<>();
    /** The teams already told out of the game (TEAM ELIMINATED), by colour id. */
    private final Set<String> teamsOut = new HashSet<>();
    final Map<UUID, Gamer> gamers = new LinkedHashMap<>();
    final List<Generator> generators = new ArrayList<>();
    /** The island forges: iron and gold. */
    final List<Generator> forges = new ArrayList<>();
    final List<Generator> diamonds = new ArrayList<>();
    final List<Generator> emeralds = new ArrayList<>();
    /** The shop and upgrades NPCs, and where they stand. */
    private final Map<Entity, Location> npcs = new HashMap<>();
    /** The upgrades NPCs (each opens the upgrades of the team of whoever clicks it). */
    private final Set<Entity> upgradeNpcs = new HashSet<>();
    /** The names floating above them. */
    private final List<ArmorStand> labels = new ArrayList<>();
    Phase phase = Phase.STARTING;
    int seconds;
    private BukkitTask clock;
    private BukkitTask ticker;
    private BukkitTask countdown;
    /** The seconds left in the countdown before the game (or a round): see {@link #shortenCountdown}. */
    int countdownLeft;
    /** The tasks of a kind of game (its rounds, its waiting room): stopped with the game. */
    private final List<BukkitTask> tasks = new ArrayList<>();
    /** The Bedbugs, the Dream Defenders, the Bridge Eggs, the Pop-up Towers, the Magic Milk. */
    final Utilities utilities = new Utilities(this);
    /** The chests of the map as the players fill them in this game: the map's own stay empty. */
    private final Map<Block, Inventory> chests = new HashMap<>();
    /** The invisible players, whose armour the others do not see. */
    private final Set<UUID> hidden = new HashSet<>();
    /** Where each invisible player left their last footprint (x, z), and on which foot (1 or -1). */
    private final Map<UUID, double[]> lastStep = new HashMap<>();
    private int traceTicks;
    /** The build limit of this game when it is not the map's (above a top bridge), else 0. */
    private int buildLimit;
    /** The top bridge of this game, once built; null without one. */
    TopBridge topBridge;
    /** The recording of the game for /replay, or null. */
    private ReplayRecording replay;

    BedwarsGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides) {
        this(plugin, host, instance, modeId, modeName, sides, plugin.catalog());
    }

    /** A game whose shop sells this catalog. */
    BedwarsGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides, ShopCatalog catalog) {
        this.plugin = plugin;
        this.host = host;
        this.instance = instance;
        this.modeId = modeId;
        this.modeName = modeName;
        this.settings = plugin.settings();
        this.catalog = catalog;
        List<TeamColor> colors = teamColors(sides.size());
        for (int i = 0; i < sides.size(); i++) {
            TeamColor color = colors.get(i);
            Team team = new Team(color, instance.map().teams().get(color));
            teams.add(team);
            for (Player player : sides.get(i)) {
                team.members.add(player.getUniqueId());
                gamers.put(player.getUniqueId(), newGamer(player, team));
            }
        }
    }

    /**
     * The colours of the teams of a game of this many sides, the first side's first: First Rush,
     * neighbouring islands; the other modes, opposite ones, as on Hypixel. Called by the
     * constructor: only the fields set before it may be used.
     */
    List<TeamColor> teamColors(int count) {
        return settings.neighbours(modeId) ? instance.map().neighbourTeams(count) : instance.map().playingTeams(count);
    }

    /** A player of this game, in this team, with their Hypixel hotbar and quick buy. */
    Gamer newGamer(Player player, Team team) {
        return new Gamer(player, team, plugin.lobby().hypixelHotbar(player),
                catalog.quickBuy(plugin.lobby().hypixelQuickBuy(player)));
    }

    /** A task of this game, every period ticks after delay: stopped with the game. */
    BukkitTask repeat(long delay, long period, Runnable task) {
        BukkitTask scheduled = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        tasks.add(scheduled);
        return scheduled;
    }

    /** A task of this game, once, after delay ticks: not run if the game stops first. */
    BukkitTask later(long delay, Runnable task) {
        BukkitTask scheduled = Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        tasks.add(scheduled);
        return scheduled;
    }

    /**
     * A line of the chat ending with a button that runs a command, through the vanilla /tellraw, as
     * the lobby's buttons: the chat component API (bungeecord-chat) is not in the build (see
     * docs/compatibility.md). Player names are letters, digits and _.
     */
    static void sendButton(Player player, String text, String button, String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + buttonJson(text, button, command));
    }

    /** The JSON of a line and its button, the colour codes kept in the texts. */
    static String buttonJson(String text, String button, String command) {
        return "[\"\",{\"text\":\"" + jsonText(text) + "\"},{\"text\":\"" + jsonText(button)
                + "\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" + jsonText(command) + "\"}}]";
    }

    /** The text inside the quotes of a JSON string. */
    static String jsonText(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    Instance instance() {
        return instance;
    }

    Phase phase() {
        return phase;
    }

    String modeId() {
        return modeId;
    }

    Gamer gamer(UUID player) {
        return gamers.get(player);
    }

    List<UUID> players() {
        return new ArrayList<>(gamers.keySet());
    }

    ShopCatalog catalog() {
        return catalog;
    }

    // --- Replay ---------------------------------------------------------------------------------

    /**
     * The game is recorded for /replay from now on, if the lobby keeps replays and the game is one
     * that is kept ({@link #recorded}). Before {@link #begin}: what the start changes in the copy of
     * the map (the beds of the teams not playing...) is in the replay, played on a fresh copy.
     */
    void record() {
        Replays replays = plugin.lobby().replays();
        if (replays == null || !recorded()) {
            return;
        }
        String map = instance.map().world();
        replay = replays.record(ReplaySetup.ofStage(modeId, modeName, map, instance.world(), ReplayWorlds.KIND, map));
        if (replay == null) {
            return;
        }
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null) {
                replay.actor(player, gamer.team.color.chat());
            }
        }
    }

    /** True for a game kept for /replay: a game of teams; not an open game nor a training. */
    boolean recorded() {
        return true;
    }

    /** A line of the chat for the replay only. */
    /** A line of the game's chat for its replay and those who watch it (/spectate). */
    void replayChat(String line) {
        if (replay != null) {
            replay.chat(line);
        }
        plugin.watchers().tell(this, line);
    }

    /** The replay ends without being kept. */
    private void cancelReplay() {
        if (replay != null) {
            replay.cancel();
            replay = null;
        }
    }

    // --- Start ----------------------------------------------------------------------------------

    void begin() {
        phase = Phase.STARTING;
        removeUnusedBeds();
        if (hasTopBridge()) {
            buildTopBridge();
        }
        if (economy()) {
            economyUp(islands());
        }
        setUp();
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player == null) {
                continue;
            }
            place(player, gamer);
            welcome(player, gamer);
            if (teamNameTags()) {
                plugin.lobby().setGameNameTag(player, gamer.team.color.letter() + " " + gamer.team.color.chat());
            }
        }
        clock = Bukkit.getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
        repeat(2L, 2L, this::hideArmour);
        final int total = countdownSeconds();
        countdownLeft = total;
        countdown = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                int left = countdownLeft;
                if (left > 0) {
                    if (left <= 5 || left == total) {
                        broadcast(ChatColor.YELLOW + "The game starts in " + ChatColor.RED + left + ChatColor.YELLOW + "...",
                                Sound.NOTE_STICKS);
                    }
                    title(ChatColor.RED + "" + ChatColor.BOLD + left, "");
                    countdownLeft--;
                    return;
                }
                cancel(countdown);
                countdown = null;
                phase = Phase.PLAYING;
                if (total > 0) {
                    broadcast(ChatColor.GREEN + "Fight!", Sound.NOTE_PLING);
                    title(ChatColor.GREEN + "" + ChatColor.BOLD + "Fight!", "");
                }
                ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    // the forges go twice as fast at the start of the game, as on Hypixel
                    int forgeTicks = seconds < settings.forge().fastStartSeconds ? 10 : 5;
                    for (Generator generator : generators) {
                        generator.tick(forges.contains(generator) ? forgeTicks : 5);
                    }
                    if (phase == Phase.PLAYING) {
                        checkTraps();
                    }
                    if (phase == Phase.PLAYING) {
                        fallenOff();
                    }
                    if (phase == Phase.PLAYING) {
                        quarter();
                    }
                }, 5L, 5L);
                started();
            }
        }, 0L, 20L);
        sidebars();
    }

    /**
     * Top Bridge: the platform over the middle and the stairways up to it, between the beds of the
     * first two teams, in the copy of the map (the rollback takes them away). Players may then build
     * up to a little above the platform.
     */
    void buildTopBridge() {
        Team[] ends = topBridgeEnds();
        if (ends == null || ends[0].points.bed == null || ends[1].points.bed == null) {
            return;
        }
        final World world = instance.world();
        TopBridge bridge = TopBridge.plan(ends[0].color, ends[0].points.bed, ends[1].color,
                ends[1].points.bed, (x, y, z) -> world.getBlockAt(x, y, z).getType() != Material.AIR);
        if (bridge == null) {
            plugin.getLogger().warning("No room for a top bridge on " + instance.map().world() + ": the islands are too near");
            return;
        }
        for (TopBridge.Piece piece : bridge.pieces()) {
            instance.build(world.getBlockAt(piece.x, piece.y, piece.z), Material.WOOL, (byte) piece.woolData());
        }
        buildLimit = Math.max(instance.map().buildLimit(), Math.min(255, bridge.height() + TopBridge.BUILD_ABOVE));
        topBridge = bridge;
    }

    /** True if this game has a top bridge (top-bridge: in config.yml). */
    boolean hasTopBridge() {
        return settings.topBridge(modeId);
    }

    /**
     * True if the game is played on its top bridge alone (Top Bridge rounds and practice, Top
     * Bridge FFA): the players start and come back on it, and one who falls off it is dead.
     */
    boolean onTheBridge() {
        return false;
    }

    /**
     * Under this height a player (not a spectator) has fallen, dead as in the void: the map's void,
     * or a little under the platform in a game played on the top bridge.
     */
    double fallLine() {
        return onTheBridge() && topBridge != null ? Math.max(instance.map().voidY(), topBridge.fallLine())
                : instance.map().voidY();
    }

    /**
     * On the top bridge, the players under its fall line are dead: also
     * those who stand still down there (a move under it kills too, see the host).
     */
    private void fallenOff() {
        double line = fallLine();
        if (line <= instance.map().voidY()) {
            return;
        }
        for (Gamer gamer : new ArrayList<>(gamers.values())) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (phase != Phase.PLAYING) {
                return;
            }
            if (player != null && !gamer.eliminated && !gamer.respawning && present(gamer)
                    && player.getGameMode() != GameMode.SPECTATOR && player.getLocation().getY() < line) {
                kill(player, null, true);
            }
        }
    }

    /**
     * Where a player who moves from there to there is put instead, or null when they may go there:
     * a game with a play area keeps its players in it (Top Bed Fight: on its bridge).
     */
    Location keepIn(Player player, Location from, Location to) {
        return null;
    }

    /** Where the dead and those who wait watch the game: the map's spectator point, or above the top bridge. */
    Location watchPoint() {
        return onTheBridge() && topBridge != null ? at(topBridge.above()) : instance.map().spectatorPoint().in(instance.world());
    }

    /**
     * A place of the top bridge ({x, y, z, yaw}) where a player stands: on the platform, or just
     * above the block of the map that takes its place there.
     */
    Location at(double[] spot) {
        World world = instance.world();
        int x = (int) Math.floor(spot[0]);
        int z = (int) Math.floor(spot[2]);
        int y = (int) spot[1];
        for (int up = 0; up <= 6; up++) {
            if (world.getBlockAt(x, y + up - 1, z).getType().isSolid() && !world.getBlockAt(x, y + up, z).getType().isSolid()
                    && !world.getBlockAt(x, y + up + 1, z).getType().isSolid()) {
                y += up;
                break;
            }
        }
        return new Location(world, spot[0], y, spot[2], (float) spot[3], 0f);
    }

    /** The two islands a top bridge links: those of the first two teams; null if there are not two. */
    Team[] topBridgeEnds() {
        return teams.size() < 2 ? null : new Team[] {teams.get(0), teams.get(1)};
    }

    /** The islands nobody plays on have no bed, as on Hypixel; the copy puts them back after the game. */
    void removeUnusedBeds() {
        World world = instance.world();
        for (Map.Entry<TeamColor, BedwarsMap.Team> entry : instance.map().teams().entrySet()) {
            if (entry.getValue().bed != null && !playing(entry.getKey())) {
                for (Block half : Instance.bedOf(entry.getValue().bed.blockIn(world))) {
                    half.setType(Material.AIR, false);
                }
            }
        }
    }

    /** A player takes their place for the start: ready, on their spawn, with their kit. */
    void place(Player player, Gamer gamer) {
        prepare(player);
        Location start = spawnOf(gamer);
        gamer.placedAt = start.clone();
        gamer.unstickUntil = System.currentTimeMillis() + UNSTICK_MS;
        player.teleport(start);
        giveKit(player, gamer);
    }

    /**
     * How long after a placement a player sinking under their spawn goes back on it: just come from
     * the lobby's world (or between two rounds), a client whose chunks have not come yet goes down
     * through the floor under it, under the map.
     */
    static final long UNSTICK_MS = 10_000L;

    /**
     * Where a player who moves there goes back (their spawn of the start), if they are sinking
     * under it in the seconds after their placement; null otherwise.
     */
    Location unstuck(Player player, Location to) {
        Gamer gamer = gamers.get(player.getUniqueId());
        if (gamer == null || gamer.placedAt == null || phase == Phase.ENDED || System.currentTimeMillis() > gamer.unstickUntil
                || player.getGameMode() == GameMode.SPECTATOR || !sunk(to, gamer.placedAt)) {
            return null;
        }
        Location back = gamer.placedAt.clone();
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        return back;
    }

    /**
     * True if this place is straight under the start (less than half a block from it across: only
     * through the floor under it, as walking off it takes more), more than a block lower.
     */
    static boolean sunk(Location at, Location start) {
        if (at.getWorld() != null && start.getWorld() != null && !at.getWorld().equals(start.getWorld())) {
            return false;
        }
        return Math.abs(at.getX() - start.getX()) < 0.5 && Math.abs(at.getZ() - start.getZ()) < 0.5
                && at.getY() < start.getY() - 1;
    }

    /** The islands that get a forge and shops (the teams' own, in a classic game). */
    List<Team> islands() {
        return teams;
    }

    /** The forges, shops and upgrades of these teams' islands, and the diamond and emerald generators. */
    void economyUp(List<Team> islands) {
        World world = instance.world();
        BedwarsMap map = instance.map();
        BedwarsSettings.Forge forge = settings.forge().forMode(modeId);
        int teamSize = 0;
        for (Team team : islands) {
            teamSize = Math.max(teamSize, team.members.size());
        }
        int count = map.completeTeams().size();
        for (Team team : islands) {
            Location spot = team.points.generator.in(world);
            Upgrades upgrades = team.upgrades;
            upgrades.forge = spot;
            upgrades.ironRate = forge.iron(map.world(), teamSize, count);
            upgrades.goldRate = forge.gold(count);
            upgrades.iron = new Generator(spot, Material.IRON_INGOT, upgrades.ironRate, null);
            upgrades.gold = new Generator(spot, Material.GOLD_INGOT, upgrades.goldRate, null);
            forges.add(upgrades.iron);
            forges.add(upgrades.gold);
        }
        if (settings.allForges(modeId)) {
            // the forges of the islands nobody plays: their iron and gold for whoever comes to take it
            Set<TeamColor> played = EnumSet.noneOf(TeamColor.class);
            for (Team team : islands) {
                played.add(team.color);
            }
            for (TeamColor color : map.completeTeams()) {
                if (played.contains(color)) {
                    continue;
                }
                Location spot = map.teams().get(color).generator.in(world);
                forges.add(new Generator(spot, Material.IRON_INGOT, forge.iron(map.world(), teamSize, count), null));
                forges.add(new Generator(spot, Material.GOLD_INGOT, forge.gold(count), null));
            }
        }
        npcsUp(islands);
        for (MapPoint point : map.diamonds()) {
            Generator generator = new Generator(point.in(world), Material.DIAMOND, settings.diamond().get(0), "Diamond");
            diamonds.add(generator);
        }
        for (MapPoint point : map.emeralds()) {
            Generator generator = new Generator(point.in(world), Material.EMERALD, settings.emerald().get(0), "Emerald");
            emeralds.add(generator);
        }
        generators.addAll(forges);
        generators.addAll(diamonds);
        generators.addAll(emeralds);
        for (Generator generator : generators) {
            generator.showText();
        }
    }

    /**
     * A player picked up this many of this item. If their own island forge dropped it, each
     * teammate standing at the forge gets as many too, as on Hypixel (not a share of it: as many
     * each). Only what the forge made counts, never what a player threw on it.
     */
    void pickedUp(Player player, Item item, int amount) {
        Generator source = null;
        for (Generator generator : generators) {
            if (generator.made(item)) {
                source = generator;
                break;
            }
        }
        if (source == null) {
            return;
        }
        int own = source.taken(amount);
        double radius = settings.forgeShare();
        Gamer gamer = gamers.get(player.getUniqueId());
        if (own <= 0 || radius <= 0 || phase != Phase.PLAYING || gamer == null || gamer.team == null) {
            return;
        }
        Upgrades upgrades = gamer.team.upgrades;
        if (upgrades == null || upgrades.forge == null
                || (source != upgrades.iron && source != upgrades.gold && source != upgrades.emerald)) {
            return;
        }
        Material material = item.getItemStack().getType();
        for (UUID member : gamer.team.members) {
            Gamer mate = gamers.get(member);
            Player other = Bukkit.getPlayer(member);
            if (member.equals(player.getUniqueId()) || mate == null || other == null || mate.eliminated || mate.respawning
                    || host.gameOf(member) != this || !atForge(upgrades.forge, other.getLocation(), radius)) {
                continue;
            }
            // a full inventory takes none, as it would not have picked it up
            other.getInventory().addItem(new ItemStack(material, own));
            other.playSound(other.getLocation(), Sound.ITEM_PICKUP, 0.2F, 1.6F);
        }
    }

    /** True if a player standing here is at this forge: within radius of it across, and 2 blocks up or down. */
    static boolean atForge(Location forge, Location player, double radius) {
        if (forge.getWorld() != player.getWorld()) {
            return false;
        }
        double dx = player.getX() - forge.getX();
        double dz = player.getZ() - forge.getZ();
        return dx * dx + dz * dz <= radius * radius && Math.abs(player.getY() - forge.getY()) <= 2;
    }

    /**
     * The shop and upgrades NPCs of these teams' islands; in a mode with shops everywhere
     * (shops-partout:, First Rush full map), an item shop and upgrades on each island nobody plays too.
     */
    void npcsUp(List<Team> islands) {
        World world = instance.world();
        int teamSize = 0;
        for (Team team : islands) {
            teamSize = Math.max(teamSize, team.members.size());
        }
        Set<TeamColor> played = EnumSet.noneOf(TeamColor.class);
        for (Team team : islands) {
            played.add(team.color);
            Location spawn = team.points.spawn.in(world);
            spawnNpc(team.points.shop.in(world), spawn, ChatColor.AQUA + "ITEM SHOP", false);
            upgradesNpc(team.points, world, teamSize);
        }
        if (settings.allShops(modeId)) {
            BedwarsMap map = instance.map();
            for (TeamColor color : map.completeTeams()) {
                if (!played.contains(color)) {
                    BedwarsMap.Team points = map.teams().get(color);
                    spawnNpc(points.shop.in(world), points.spawn.in(world), ChatColor.AQUA + "ITEM SHOP", false);
                    // the upgrades too: they are the team's of whoever clicks, as on their own island
                    upgradesNpc(points, world, teamSize);
                }
            }
        }
    }

    /** The upgrades NPC of an island, if the map has its point and the game its upgrades. */
    private void upgradesNpc(BedwarsMap.Team points, World world, int teamSize) {
        if (points.upgrades != null && upgrades()) {
            spawnNpc(points.upgrades.in(world), points.spawn.in(world), ChatColor.AQUA + (teamSize <= 1 ? "SOLO UPGRADES"
                    : "TEAM UPGRADES"), true);
        }
    }

    /**
     * After a rollback, which takes every entity of the map away: the NPCs of these islands and
     * the texts of the generators again, the chests empty again.
     */
    void entitiesAgain(List<Team> islands) {
        removeNpcs();
        npcsUp(islands);
        for (Generator generator : generators) {
            generator.remove();
            generator.showText();
        }
        chests.clear();
    }

    // --- What a kind of game changes (a FFA, a practice) ----------------------------------------

    /** True if the islands have their forge and shops, and the map its diamonds and emeralds. */
    boolean economy() {
        return true;
    }

    /** True if the islands have their upgrades NPC. */
    boolean upgrades() {
        return true;
    }

    /** Before the players are placed: what the game builds or changes on the map. */
    void setUp() {
    }

    /** The seconds of the countdown before the game. */
    int countdownSeconds() {
        return 3;
    }

    /** The countdown goes on from this many seconds at most (everyone is ready): nothing if it is not on. */
    void shortenCountdown(int seconds) {
        if (phase == Phase.STARTING && countdownLeft > seconds) {
            countdownLeft = seconds;
        }
    }

    /** The countdown is over: the game is on. */
    void started() {
    }

    /** Where this player starts, and comes back to life. */
    Location spawnOf(Gamer gamer) {
        return gamer.team.points.spawn.in(instance.world());
    }

    /** The first message of the game. */
    void welcome(Player player, Gamer gamer) {
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + modeName + ChatColor.GREEN + " on "
                + ChatColor.WHITE + instance.map().world() + ChatColor.GREEN + ": you are in the " + gamer.team.color.coloured()
                + ChatColor.GREEN + " team. Protect your bed and destroy the others'!");
    }

    /** True if this death is the last of the player (their bed is gone). */
    boolean finalDeath(Gamer gamer) {
        return !gamer.team.bed;
    }

    /**
     * True if a last death is a final kill, counted in the final kills; false in a game of rounds,
     * where the player is only out until the next round.
     */
    boolean finalKillsCounted() {
        return true;
    }

    /** The seconds before a dead player comes back. */
    int respawnSeconds() {
        return settings.respawnSeconds();
    }

    /** A player died (author: who gets the kill, or null), after the game counted it. */
    void died(Gamer victim, Gamer author, boolean finalKill) {
    }

    /** Four times a second, while the game is on. */
    void quarter() {
    }

    /** Once a second, while the game is on, after the timed events. */
    void everySecond() {
    }

    /** True if a player can join this game while it is on (an open game with room): see {@link #join}. */
    boolean joinable() {
        return false;
    }

    /** A player joins this game while it is on. Only when {@link #joinable()}. */
    void join(Player player) {
        throw new IllegalStateException(modeId + " cannot be joined while it is on");
    }

    /** A player placed this block (it is marked as placed already). */
    void placed(Player player, Block block) {
    }

    /** A player opened the item shop. */
    void shopOpened(Player player) {
    }

    /** True if the players of different teams can hurt each other. */
    boolean pvp() {
        return true;
    }

    /**
     * True if the chat goes by team, as in a Bed Wars game of teams: a player talks to their team
     * (to everyone with "!" first), the eliminated to the spectators. False: everyone in the game hears.
     */
    boolean chatTeams() {
        return teamSize() > 1;
    }

    /** True if the names of the players show their team, as on Hypixel ("R Steve" in red): a classic game. */
    boolean teamNameTags() {
        return true;
    }

    /**
     * True if an invisible player leaves footprints and the swirls of the potion, to be spotted;
     * false (a classic game, as on Hypixel): wholly invisible.
     */
    boolean invisibleTraces() {
        return false;
    }

    /** True if the players can use their items (eat, drink, throw, shoot); false: the shop practice. */
    boolean itemsUsable() {
        return true;
    }

    /**
     * The kit of the mode as the player arranged it in the lobby's kit editor, ready to play: the
     * wool in the team's colour, the swords, tools and shears unbreakable. Null if the mode has none.
     */
    ItemStack[] lobbyKit(Player player, Gamer gamer) {
        ItemStack[] kit = plugin.lobby().kit(player, modeId);
        if (kit == null) {
            return null;
        }
        ItemStack[] ready = new ItemStack[kit.length];
        for (int i = 0; i < kit.length; i++) {
            ready[i] = kit[i] == null ? null : kitItem(kit[i].clone(), gamer);
        }
        return ready;
    }

    /** An item of a kit as a game gives it: wool, clay and glass in the team's colour; tools unbreakable. */
    static ItemStack kitItem(ItemStack item, Gamer gamer) {
        Material type = item.getType();
        if (type == Material.WOOL || type == Material.STAINED_CLAY || type == Material.STAINED_GLASS) {
            item.setDurability(gamer.team.color.woolData());
        }
        String name = type.name();
        if (name.endsWith("_SWORD") || name.endsWith("_PICKAXE") || name.endsWith("_AXE") || type == Material.SHEARS
                || type == Material.BOW) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.spigot().setUnbreakable(true);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    /**
     * A villager on its point, its name floating above it on two lines as on Hypixel ("ITEM SHOP",
     * "RIGHT CLICK"): the lines are invisible armour stands, which every client shows.
     */
    void spawnNpc(Location point, Location spawn, String name, boolean upgrades) {
        point.getChunk().load();
        // in its niche, straight out of it, as on Hypixel: not on the guard in front of it (NpcSpot)
        NpcSpot fitted = NpcSpot.fit(blocks(point.getWorld()), point.getX(), point.getY(), point.getZ(), point.getYaw(),
                spawn.getX(), spawn.getZ());
        Location spot = new Location(point.getWorld(), fitted.x, fitted.y, fitted.z, fitted.yaw, 0f);
        Villager villager = spot.getWorld().spawn(spot, Villager.class);
        villager.setProfession(Villager.Profession.FARMER);
        villager.setAdult();
        labels.add(label(spot.clone().add(0, 2.1, 0), name));
        labels.add(label(spot.clone().add(0, 1.8, 0), ChatColor.YELLOW + "" + ChatColor.BOLD + "RIGHT CLICK"));
        villager.setRemoveWhenFarAway(false);
        villager.setCanPickupItems(false);
        villager.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255, false, false), true);
        npcs.put(villager, spot);
        if (upgrades) {
            upgradeNpcs.add(villager);
        }
    }

    /** The blocks of this world as an NPC sees them: what it cannot stand in, and how high each floor is. */
    static NpcSpot.Blocks blocks(World world) {
        return new NpcSpot.Blocks() {
            @Override
            public boolean solid(int x, int y, int z) {
                return y >= 0 && y < 256 && world.getBlockAt(x, y, z).getType().isSolid();
            }

            @Override
            @SuppressWarnings("deprecation") // Block#getData: the 1.8 data value, the top bit of a slab's
            public double height(int x, int y, int z) {
                if (y < 0 || y >= 256) {
                    return 0;
                }
                org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                Material type = block.getType();
                if (!type.isSolid()) {
                    return 0;
                }
                boolean slab = type == Material.STEP || type == Material.WOOD_STEP || type == Material.STONE_SLAB2;
                return slab && (block.getData() & 8) == 0 ? 0.5 : 1.0;
            }
        };
    }

    /** A line of text floating here: an invisible armour stand that nothing can touch or click. */
    static ArmorStand label(Location where, String text) {
        ArmorStand stand = where.getWorld().spawn(where, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setBasePlate(false);
        try {
            // a marker has no box: the clicks go through it, to the villager
            ArmorStand.class.getMethod("setMarker", boolean.class).invoke(stand, true);
        } catch (ReflectiveOperationException e) {
            // an older server: the line is just a little higher
            stand.teleport(where.clone().add(0, 0.5, 0));
        }
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        return stand;
    }

    /** True for the shop NPCs of this game. */
    boolean isShop(Entity entity) {
        return npcs.containsKey(entity) && !upgradeNpcs.contains(entity);
    }

    boolean isNpc(Entity entity) {
        return npcs.containsKey(entity);
    }

    void prepare(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        heal(player);
        // a ranked mode shows the player's ELO in the experience bar, as the RBW servers do
        player.setLevel(Math.max(0, plugin.lobby().elo(player.getUniqueId(), modeId)));
        player.setExp(0f);
    }

    static void heal(Player player) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    // --- Every second ---------------------------------------------------------------------------

    private void second() {
        if (phase == Phase.ENDED) {
            return;
        }
        if (phase == Phase.PLAYING) {
            seconds++;
            events();
            if (phase == Phase.PLAYING) {
                for (Gamer gamer : gamers.values()) {
                    Player player = Bukkit.getPlayer(gamer.id);
                    if (player != null && !gamer.eliminated && !gamer.respawning && host.gameOf(gamer.id) == this) {
                        teamEffects(player, gamer);
                    }
                }
                utilities.second();
                everySecond();
            }
        }
        for (Generator generator : diamonds) {
            generator.refreshText();
        }
        for (Generator generator : emeralds) {
            generator.refreshText();
        }
        for (Map.Entry<Entity, Location> npc : npcs.entrySet()) {
            if (npc.getKey().isValid() && npc.getKey().getLocation().distanceSquared(npc.getValue()) > 0.04) {
                npc.getKey().teleport(npc.getValue());
            }
        }
        if (phase != Phase.ENDED) {
            sidebars();
        }
    }

    /** The timed events of a game: the generators going up a tier, the end of the beds, the end. */
    void events() {
        if (seconds == settings.diamondII()) {
            upgrade(diamonds, 2, settings.diamond().get(1), "Diamond");
        } else if (seconds == settings.emeraldII()) {
            upgrade(emeralds, 2, settings.emerald().get(1), "Emerald");
        } else if (seconds == settings.diamondIII()) {
            upgrade(diamonds, 3, settings.diamond().get(2), "Diamond");
        } else if (seconds == settings.emeraldIII()) {
            upgrade(emeralds, 3, settings.emerald().get(2), "Emerald");
        } else if (seconds == settings.bedsGone()) {
            for (Team team : teams) {
                if (team.bed) {
                    destroyBed(team);
                }
            }
            broadcast(ChatColor.RED + "" + ChatColor.BOLD + "All beds have been destroyed!", Sound.ENDERDRAGON_GROWL);
            title(ChatColor.RED + "" + ChatColor.BOLD + "BEDS DESTROYED", ChatColor.WHITE + "Nobody respawns any more!");
        } else if (seconds >= settings.gameEnd()) {
            broadcast(ChatColor.YELLOW + "Time is up: the game ends in a draw.", Sound.NOTE_BASS);
            end(null);
        }
    }

    private void upgrade(List<Generator> list, int tier, BedwarsSettings.Rate rate, String what) {
        for (Generator generator : list) {
            generator.upgrade(tier, rate);
        }
        if (!list.isEmpty()) {
            broadcast(ChatColor.YELLOW + what + " generators are now " + ChatColor.RED + "Tier " + Generator.roman(tier),
                    Sound.LEVEL_UP);
        }
    }

    /** The next timed event and when: "Diamond II", 180 seconds. */
    private String nextEvent() {
        int[] times = {settings.diamondII(), settings.emeraldII(), settings.diamondIII(), settings.emeraldIII(),
            settings.bedsGone(), settings.gameEnd()};
        String[] names = {"Diamond II", "Emerald II", "Diamond III", "Emerald III", "Bed Gone", "Game End"};
        for (int i = 0; i < times.length; i++) {
            if (times[i] > seconds) {
                return ChatColor.WHITE + names[i] + " in " + ChatColor.GREEN + clock(times[i] - seconds);
            }
        }
        return ChatColor.WHITE + "Game End";
    }

    static String clock(int seconds) {
        int s = seconds % 60;
        return (seconds / 60) + ":" + (s < 10 ? "0" : "") + s;
    }

    void sidebars() {
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null && host.gameOf(gamer.id) == this) {
                plugin.lobby().setGameSidebar(player, lines(gamer));
            }
        }
        for (Player watcher : plugin.watchers().of(this)) {
            Gamer watched = gamers.get(plugin.watchers().targetOf(watcher.getUniqueId()));
            if (watched == null && !gamers.isEmpty()) {
                watched = gamers.values().iterator().next();
            }
            if (watched != null) {
                // the sidebar of the player they came for, as that player sees it
                plugin.lobby().setGameSidebar(watcher, lines(watched));
            }
        }
    }

    List<String> lines(Gamer viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(phase == Phase.STARTING ? ChatColor.WHITE + "Starting..." : nextEvent());
        lines.add("");
        for (Team team : teams) {
            int alive = 0;
            for (UUID member : team.members) {
                if (!gamers.get(member).eliminated) {
                    alive++;
                }
            }
            String state = team.bed ? ChatColor.GREEN + CHECK : alive > 0 ? ChatColor.GREEN + "" + alive : ChatColor.RED + CROSS;
            lines.add(team.color.letter() + " " + ChatColor.WHITE + team.color.displayName() + ": " + state
                    + (team == viewer.team ? ChatColor.GRAY + " YOU" : ""));
        }
        lines.add("");
        lines.add(ChatColor.WHITE + "Kills: " + ChatColor.GREEN + viewer.kills);
        lines.add(ChatColor.WHITE + "Final Kills: " + ChatColor.GREEN + viewer.finalKills);
        lines.add(ChatColor.WHITE + "Beds Broken: " + ChatColor.GREEN + viewer.beds);
        return lines;
    }

    // --- Blocks ---------------------------------------------------------------------------------

    /**
     * True if this block of the map, not placed by a player, may be broken (the rollback puts it
     * back): never, but on the bridge of Top Bed Fight.
     */
    boolean breaksMap(Block block) {
        return false;
    }

    /**
     * Why no block can be placed there (null when one can, "" to say nothing): above the build
     * limit or under the map's lowest (hauteur-min), off the islands of a map that lists them
     * (iles:), or too close to a spawn or a generator.
     */
    String cannotBuildAt(Block block) {
        BedwarsMap map = instance.map();
        if (block.getY() > (buildLimit > 0 ? buildLimit : map.buildLimit())) {
            return "Build height limit reached!";
        }
        if (map.hasFloorY() && block.getY() < map.floorY()) {
            // hauteur-min: no clutch far under the islands
            return "You can't place blocks this low!";
        }
        String outside = map.outsideIslands(block.getX(), block.getY(), block.getZ());
        if (outside != null) {
            return outside;
        }
        if (!protectsSpawns()) {
            return null;
        }
        for (BedwarsMap.Team team : map.teams().values()) {
            if (near(team.spawn, block, SPAWN_PROTECTION, 3) || near(team.generator, block, GENERATOR_PROTECTION, 2)) {
                return "You can't place blocks here!";
            }
        }
        for (MapPoint point : map.diamonds()) {
            if (near(point, block, GENERATOR_PROTECTION, 2)) {
                return "You can't place blocks here!";
            }
        }
        for (MapPoint point : map.emeralds()) {
            if (near(point, block, GENERATOR_PROTECTION, 2)) {
                return "You can't place blocks here!";
            }
        }
        return null;
    }

    /** True if no block goes near a spawn or a generator; false: blocks go everywhere under the build limit. */
    boolean protectsSpawns() {
        return true;
    }

    /** The sound every player of the game hears at each death, or null: only the killer's ding. */
    Sound deathSound() {
        return null;
    }

    /** True if a fall hurts, as in Bed Wars. */
    boolean fallDamage() {
        return true;
    }

    /** True if the block is within this horizontal radius of the point, from just under it to this height above. */
    static boolean near(MapPoint point, Block block, double radius, int height) {
        return point != null && point.flatDistanceSquared(block.getX(), block.getZ()) < radius * radius
                && block.getY() >= point.blockY() - 1 && block.getY() <= point.blockY() + height;
    }

    // --- Beds -----------------------------------------------------------------------------------

    /** True if this colour plays in this game. */
    private boolean playing(TeamColor color) {
        for (Team team : teams) {
            if (team.color == color) {
                return true;
            }
        }
        return false;
    }

    /** The team whose bed this block is part of, or null. */
    Team bedTeam(Block block) {
        for (Team team : teams) {
            for (Block half : Instance.bedOf(team.points.bed.blockIn(instance.world()))) {
                if (half.equals(block)) {
                    return team;
                }
            }
        }
        return null;
    }

    /** A player broke the bed of another team. */
    void breakBed(Player breaker, Team team) {
        Gamer gamer = gamers.get(breaker.getUniqueId());
        if (gamer == null || !team.bed) {
            return;
        }
        gamer.beds++;
        destroyBed(team);
        // with the player's own count of beds: "Red Bed was destroyed by qAbel's bed #1,250!"
        String line = KillLines.bed(team.color.coloured() + " Bed", gamer.coloured(),
                plugin.lobby().recordBedBroken(modeId, gamer.id));
        broadcast(line != null ? line : ChatColor.WHITE + "" + ChatColor.BOLD + "BED DESTRUCTION > " + team.color.coloured()
                + " Bed" + ChatColor.GRAY + " was destroyed by " + gamer.coloured() + ChatColor.GRAY + "!", null);
        for (Gamer other : gamers.values()) {
            Player player = Bukkit.getPlayer(other.id);
            if (player == null || host.gameOf(other.id) != this) {
                continue;
            }
            player.playSound(player.getLocation(), Sound.ENDERDRAGON_GROWL, 1f, 1f);
            if (other.team == team) {
                player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "BED DESTROYED!",
                        ChatColor.WHITE + "You will no longer respawn!");
            }
        }
        sidebars();
    }

    void destroyBed(Team team) {
        team.bed = false;
        for (Block half : Instance.bedOf(team.points.bed.blockIn(instance.world()))) {
            half.setType(Material.AIR, false);
        }
    }

    // --- Deaths ---------------------------------------------------------------------------------

    /**
     * A hit by another player of the game: the kill goes to them if this player dies soon. An
     * invisible player (a potion of the shop, a kit's) hit loses their invisibility: seen, armour
     * and all, until they drink another; both hear it, nothing in the chat.
     */
    void hit(Gamer victim, Gamer attacker) {
        victim.lastDamager = attacker.id;
        victim.lastDamageAt = System.currentTimeMillis();
        if (!revealedByHits(victim)) {
            return;
        }
        Player player = Bukkit.getPlayer(victim.id);
        if (player == null || !player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return;
        }
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1f, 1f);
        Player by = Bukkit.getPlayer(attacker.id);
        if (by != null && host.gameOf(attacker.id) == this) {
            by.playSound(by.getLocation(), Sound.ORB_PICKUP, 1f, 1f);
        }
    }

    /** True if a hit takes this player's invisibility away: while the game is played. */
    boolean revealedByHits(Gamer victim) {
        return phase == Phase.PLAYING;
    }

    /**
     * The player died: killed by a player (or null), or fell into the void. They respawn after a
     * few seconds if their team still has its bed, else they are out of the game.
     */
    void kill(Player victim, Player killer, boolean voidDeath) {
        Gamer gamer = gamers.get(victim.getUniqueId());
        if (gamer == null || phase != Phase.PLAYING || gamer.respawning || gamer.eliminated) {
            if (gamer != null && voidDeath) {
                // before the game or between two rounds, back on the spawn; a spectator, back to the spectators
                victim.setFallDistance(0f);
                boolean spectating = gamer.eliminated || gamer.respawning || victim.getGameMode() == GameMode.SPECTATOR;
                victim.teleport(spectating || phase == Phase.ENDED ? watchPoint() : spawnOf(gamer));
            }
            return;
        }
        Gamer author = killer == null ? null : gamers.get(killer.getUniqueId());
        if (author == null && gamer.lastDamager != null
                && System.currentTimeMillis() - gamer.lastDamageAt < CREDIT_MS) {
            author = gamers.get(gamer.lastDamager);
        }
        if (author != null && (author.team == gamer.team || author == gamer)) {
            author = null;
        }
        gamer.lastDamager = null;
        boolean out = finalDeath(gamer);
        // the last death of a round is not a final kill: the player plays the next round
        boolean finalKill = out && finalKillsCounted();
        String how = voidDeath ? (author == null ? " fell into the void." : " was knocked into the void by ")
                : (author == null ? " died." : " was killed by ");
        // a final kill shows the killer's own count, as on Hypixel: "X was Y's final #41,000. FINAL KILL!"
        String line = finalKill && author != null
                ? KillLines.finalKill(gamer.coloured(), author.coloured(), plugin.lobby().recordFinalKill(modeId, author.id))
                : null;
        if (finalKill) {
            // out of the game, killed or not: a final death, for the FKDR
            plugin.lobby().recordFinalDeath(modeId, gamer.id);
        }
        broadcast(line != null ? line : gamer.coloured() + ChatColor.GRAY + how
                + (author == null ? "" : author.coloured() + ChatColor.GRAY + ".")
                + (finalKill ? ChatColor.AQUA + "" + ChatColor.BOLD + " FINAL KILL!" : ""), deathSound());
        if (author != null) {
            if (finalKill) {
                author.finalKills++;
            } else {
                author.kills++;
            }
            plugin.lobby().recordKill(modeId, author.id, gamer.id);
            Player authorPlayer = Bukkit.getPlayer(author.id);
            if (authorPlayer != null) {
                loot(victim, authorPlayer);
                if (deathSound() == null) {
                    authorPlayer.playSound(authorPlayer.getLocation(), Sound.ORB_PICKUP, 1f, 1f);
                }
            }
        }
        // Tools lose a tier at each death; armour and shears stay.
        if (gamer.pickaxe > 0) {
            gamer.pickaxe--;
        }
        if (gamer.axe > 0) {
            gamer.axe--;
        }
        victim.closeInventory();
        victim.getInventory().clear();
        victim.getInventory().setArmorContents(null);
        heal(victim);
        victim.setGameMode(GameMode.SPECTATOR);
        victim.teleport(watchPoint());
        if (out) {
            gamer.eliminated = true;
            outOfGame(victim, gamer);
            died(gamer, author, true);
            teamOut(gamer.team);
            sidebars();
            checkEnd();
            return;
        }
        gamer.respawning = true;
        died(gamer, author, false);
        if (phase == Phase.PLAYING && gamer.respawning) {
            respawnLater(victim, gamer);
        }
        sidebars();
    }

    /** A final death: what the player is told, with [PLAY AGAIN] and [LEAVE] to go on at once. */
    void outOfGame(Player victim, Gamer gamer) {
        victim.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "YOU DIED!", ChatColor.GRAY + "You are now a spectator.");
        victim.sendMessage(ChatColor.GRAY + "You are out of the game: play again now, or watch the end.");
        EndOfGame.buttons(victim);
    }

    /**
     * A player of this team is out for good: once none of it is left, everyone hears it, as on
     * Hypixel ("TEAM ELIMINATED > Red Team has been eliminated!"). Only in a game of final kills,
     * once per team.
     */
    void teamOut(Team team) {
        if (!finalKillsCounted() || teams.size() < 2 || !teamsOut.add(team.color.id())) {
            return;
        }
        for (UUID member : team.members) {
            if (!gamers.get(member).eliminated) {
                teamsOut.remove(team.color.id());
                return;
            }
        }
        broadcast(ChatColor.WHITE + "" + ChatColor.BOLD + "TEAM ELIMINATED > " + team.color.coloured() + " Team"
                + ChatColor.RED + " has been eliminated!", null);
    }

    /** The victim's iron, gold, diamonds and emeralds go to the killer. */
    void loot(Player victim, Player killer) {
        List<String> got = new ArrayList<>();
        for (Currency currency : Currency.values()) {
            int count = count(victim, currency);
            if (count > 0) {
                for (ItemStack left : killer.getInventory().addItem(new ItemStack(currency.material(), count)).values()) {
                    killer.getWorld().dropItem(killer.getLocation(), left);
                }
                got.add(currency.amount(count));
            }
        }
        if (!got.isEmpty()) {
            killer.sendMessage(ChatColor.GRAY + "+" + String.join(ChatColor.GRAY + ", +", got));
        }
    }

    private void respawnLater(final Player victim, final Gamer gamer) {
        final int total = respawnSeconds();
        if (total <= 0) {
            respawn(victim, gamer);
            return;
        }
        victim.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "YOU DIED!",
                ChatColor.YELLOW + "You will respawn in " + ChatColor.RED + total + ChatColor.YELLOW + " seconds!");
        gamer.respawn = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int left = total;

            @Override
            public void run() {
                left--;
                Player player = Bukkit.getPlayer(gamer.id);
                if (player == null || phase != Phase.PLAYING || host.gameOf(gamer.id) != BedwarsGame.this) {
                    cancel(gamer.respawn);
                    return;
                }
                if (left > 0) {
                    player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "YOU DIED!", ChatColor.YELLOW
                            + "You will respawn in " + ChatColor.RED + left + ChatColor.YELLOW + " seconds!");
                    return;
                }
                cancel(gamer.respawn);
                respawn(player, gamer);
            }
        }, 20L, 20L);
    }

    void respawn(Player player, Gamer gamer) {
        gamer.respawning = false;
        gamer.respawn = null;
        heal(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(spawnOf(gamer));
        giveKit(player, gamer);
        teamEffects(player, gamer);
        player.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "RESPAWNED!", "");
        player.sendMessage(ChatColor.YELLOW + "You have respawned!");
    }

    /** The player left the game: out of the server, or /spawn. Their team plays on without them. */
    void leave(Player player, boolean toLobby) {
        Gamer gamer = gamers.get(player.getUniqueId());
        if (gamer == null || host.gameOf(gamer.id) != this) {
            if (toLobby) {
                plugin.lobby().sendToLobby(player);
            }
            return;
        }
        plugin.watchers().playerLeft(this, player);
        host.release(gamer.id);
        if (phase != Phase.ENDED) {
            boolean wasPlaying = !gamer.eliminated;
            if (wasPlaying) {
                // a forfeit: after a ranked game, the ranked queues close for a while (the lobby says)
                plugin.lobby().forfeited(modeId, gamer.id);
            }
            gamer.eliminated = true;
            gamer.respawning = false;
            cancel(gamer.respawn);
            gamer.respawn = null;
            if (wasPlaying) {
                broadcast(gamer.coloured() + ChatColor.GRAY + " left the game.", null);
            }
            left(gamer);
            if (wasPlaying) {
                teamOut(gamer.team);
            }
            sidebars();
            checkEnd();
        }
        if (toLobby) {
            plugin.lobby().sendToLobby(player);
        }
    }

    /** A player left the game: no longer in it, already counted out. */
    void left(Gamer gamer) {
    }

    /** True while this player is still in this game (online, not gone back to the lobby). */
    boolean present(Gamer gamer) {
        return host.gameOf(gamer.id) == this && Bukkit.getPlayer(gamer.id) != null;
    }

    /** The players still in this game. */
    List<Gamer> present() {
        List<Gamer> here = new ArrayList<>();
        for (Gamer gamer : gamers.values()) {
            if (present(gamer)) {
                here.add(gamer);
            }
        }
        return here;
    }

    /** The game is over when one team is left (or none). */
    void checkEnd() {
        if (phase == Phase.ENDED) {
            return;
        }
        List<Team> alive = new ArrayList<>();
        for (Team team : teams) {
            for (UUID member : team.members) {
                if (!gamers.get(member).eliminated) {
                    alive.add(team);
                    break;
                }
            }
        }
        if (alive.size() <= 1) {
            end(alive.isEmpty() ? null : alive.get(0));
        }
    }

    // --- End ------------------------------------------------------------------------------------

    void end(Team winner) {
        phase = Phase.ENDED;
        stopTasks();
        if (winner != null) {
            List<UUID> winners = new ArrayList<>(winner.members);
            List<UUID> losers = new ArrayList<>();
            for (Team team : teams) {
                if (team != winner) {
                    losers.addAll(team.members);
                }
            }
            plugin.lobby().recordMatch(modeId, winners, losers);
        }
        List<Gamer> killers = topKillers(new ArrayList<>(gamers.values()));
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player == null || host.gameOf(gamer.id) != this) {
                continue;
            }
            if (winner == null) {
                player.sendTitle(ChatColor.YELLOW + "" + ChatColor.BOLD + "DRAW", "");
            } else if (gamer.team == winner) {
                player.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "VICTORY!", "");
                player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);
            } else {
                player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "GAME OVER!", "");
            }
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.STRIKETHROUGH + "                                        ");
            player.sendMessage(ChatColor.WHITE + "" + ChatColor.BOLD + "  " + modeName);
            player.sendMessage(winner == null ? ChatColor.YELLOW + "  Draw: no team won."
                    : ChatColor.YELLOW + "  Winner: " + winner.color.coloured() + ChatColor.YELLOW + " - " + names(winner));
            if (!killers.isEmpty()) {
                player.sendMessage("");
            }
            for (int i = 0; i < killers.size(); i++) {
                Gamer killer = killers.get(i);
                player.sendMessage(KILLER_PLACES[i] + ChatColor.GRAY + " - " + killer.coloured() + ChatColor.GRAY + " - "
                        + ChatColor.WHITE + (killer.kills + killer.finalKills));
            }
            player.sendMessage(ChatColor.GRAY + "  You: " + gamer.kills + " kills, " + gamer.finalKills
                    + " final kills, " + gamer.beds + " beds broken");
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.STRIKETHROUGH + "                                        ");
        }
        // the end for the replay and those who watch the game
        replayChat(winner == null ? ChatColor.YELLOW + "Draw: no team won."
                : ChatColor.YELLOW + "Winner: " + winner.color.coloured() + ChatColor.YELLOW + " - " + names(winner));
        for (int i = 0; i < killers.size(); i++) {
            Gamer killer = killers.get(i);
            replayChat(KILLER_PLACES[i] + ChatColor.GRAY + " - " + killer.coloured() + ChatColor.GRAY + " - "
                    + ChatColor.WHITE + (killer.kills + killer.finalKills));
        }
        if (replay != null) {
            replay.finish(winner == null ? "Draw" : winner.color.displayName() + " won (" + names(winner) + ")");
            replay = null;
        }
        afterTheEnd(winner != null);
    }

    /** The places of the end summary, as on Hypixel. */
    static final String[] KILLER_PLACES = {
        "  " + ChatColor.YELLOW + ChatColor.BOLD + "1st Killer",
        "  " + ChatColor.GOLD + ChatColor.BOLD + "2nd Killer",
        "  " + ChatColor.RED + ChatColor.BOLD + "3rd Killer",
    };

    /** The three players with the most kills (final kills included), the most first; none without a kill. */
    static List<Gamer> topKillers(List<Gamer> all) {
        List<Gamer> killers = new ArrayList<>();
        for (Gamer gamer : all) {
            if (gamer.kills + gamer.finalKills > 0) {
                killers.add(gamer);
            }
        }
        killers.sort((a, b) -> Integer.compare(b.kills + b.finalKills, a.kills + a.finalKills));
        return killers.size() > KILLER_PLACES.length ? new ArrayList<>(killers.subList(0, KILLER_PLACES.length)) : killers;
    }

    /**
     * Everyone goes back to the lobby: after the seconds of the end when a team won (the lobby's
     * fin-de-partie-secondes), to see the winners and their victory effect, with Play again and
     * Leave usable at once; at once after a draw. Out of the event that ended the game.
     */
    void afterTheEnd(boolean won) {
        int seconds = won ? plugin.lobby().endSeconds() : 0;
        if (seconds <= 0) {
            Bukkit.getScheduler().runTask(plugin, this::backToLobby);
            return;
        }
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null && host.gameOf(gamer.id) == this) {
                player.closeInventory();
                EndOfGame.give(player);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::backToLobby, seconds * 20L);
    }

    /** The game ends without a result (an open game left by its last player). */
    void close() {
        if (phase == Phase.ENDED) {
            return;
        }
        phase = Phase.ENDED;
        stopTasks();
        cancelReplay();
        Bukkit.getScheduler().runTask(plugin, this::backToLobby);
    }

    private String names(Team team) {
        List<String> names = new ArrayList<>();
        for (UUID member : team.members) {
            names.add(gamers.get(member).name);
        }
        return String.join(", ", names);
    }

    void backToLobby() {
        // those who watch first: none of them still sees through the eyes of a player who left
        plugin.watchers().ended(this);
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null && host.gameOf(gamer.id) == this) {
                host.release(gamer.id);
                plugin.lobby().sendToLobby(player);
            }
        }
        cleanUp();
        host.finished(this);
    }

    /** The plugin stops: the game ends without a result. */
    void abort() {
        phase = Phase.ENDED;
        stopTasks();
        cancelReplay();
        plugin.watchers().ended(this);
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null && host.gameOf(gamer.id) == this) {
                player.sendMessage(ChatColor.RED + "The game is stopped: the server is restarting its modes.");
                host.release(gamer.id);
                plugin.lobby().sendToLobby(player);
            }
        }
        cleanUp();
    }

    void stopTasks() {
        cancel(clock);
        cancel(ticker);
        cancel(countdown);
        clock = null;
        ticker = null;
        countdown = null;
        for (BukkitTask task : tasks) {
            cancel(task);
        }
        tasks.clear();
        for (Gamer gamer : gamers.values()) {
            cancel(gamer.respawn);
            gamer.respawn = null;
        }
    }

    void cleanUp() {
        utilities.clear();
        chests.clear();
        for (Generator generator : generators) {
            generator.remove();
        }
        removeNpcs();
    }

    private void removeNpcs() {
        for (Entity npc : npcs.keySet()) {
            npc.remove();
        }
        for (ArmorStand label : labels) {
            label.remove();
        }
        labels.clear();
        npcs.clear();
        upgradeNpcs.clear();
    }

    static void cancel(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    // --- Shop -----------------------------------------------------------------------------------

    /** What the shop shows in place of this item for the player: their next pickaxe or axe. */
    ShopItem shown(Gamer gamer, ShopItem item) {
        if (item.kind() == ShopItem.Kind.PICKAXE) {
            return catalog.pickaxes().get(Math.min(gamer.pickaxe + 1, catalog.pickaxes().size() - 1));
        }
        if (item.kind() == ShopItem.Kind.AXE) {
            return catalog.axes().get(Math.min(gamer.axe + 1, catalog.axes().size() - 1));
        }
        return item;
    }

    /** Why the player cannot buy it now (they have it already...), or null. */
    String cannotBuy(Gamer gamer, ShopItem item) {
        switch (item.kind()) {
            case COMING_SOON:
                return "Coming soon: not in this version yet.";
            case PICKAXE:
                return gamer.pickaxe >= catalog.pickaxes().size() - 1 ? "You already have the best pickaxe!" : null;
            case AXE:
                return gamer.axe >= catalog.axes().size() - 1 ? "You already have the best axe!" : null;
            case ARMOR:
                return gamer.armor >= item.tier() ? "You already have this armor or better!" : null;
            case SHEARS:
                return gamer.shears ? "You already have shears!" : null;
            default:
                return null;
        }
    }

    /** The player buys this item of the shop. True if they did. */
    boolean buy(Player player, ShopItem item) {
        Gamer gamer = gamers.get(player.getUniqueId());
        if (gamer == null || phase == Phase.ENDED || gamer.respawning || gamer.eliminated) {
            return false;
        }
        ShopItem bought = shown(gamer, item);
        String refused = cannotBuy(gamer, bought);
        if (refused != null) {
            player.sendMessage(ChatColor.RED + refused);
            player.playSound(player.getLocation(), Sound.VILLAGER_NO, 1f, 1f);
            return false;
        }
        int have = count(player, bought.currency());
        if (have < bought.price()) {
            player.sendMessage(ChatColor.RED + "You don't have enough " + bought.currency().displayName() + "! Need "
                    + (bought.price() - have) + " more!");
            player.playSound(player.getLocation(), Sound.VILLAGER_NO, 1f, 1f);
            return false;
        }
        player.getInventory().removeItem(new ItemStack(bought.currency().material(), bought.price()));
        PlayerInventory inventory = player.getInventory();
        switch (bought.kind()) {
            case SWORD: {
                // the new sword in the sword slot, instead of the sword there (the wooden one goes anyway)
                int was = swordSlot(inventory, bought.material());
                if (was >= 0) {
                    inventory.setItem(was, null);
                }
                inventory.remove(Material.WOOD_SWORD);
                give(player, gamer, stack(bought, gamer), bought.category(), was);
                break;
            }
            case ARMOR:
                gamer.armor = bought.tier();
                wearArmor(player, gamer);
                break;
            case PICKAXE: {
                gamer.pickaxe = bought.tier();
                int was = firstOf(inventory, "_PICKAXE");
                removeAll(inventory, "_PICKAXE");
                give(player, gamer, stack(bought, gamer), bought.category(), was);
                break;
            }
            case AXE: {
                gamer.axe = bought.tier();
                int was = -1;
                for (int slot = 0; slot < inventory.getSize(); slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack != null && stack.getType().name().endsWith("_AXE") && !stack.getType().name().endsWith("PICKAXE")) {
                        was = was < 0 ? slot : was;
                        inventory.setItem(slot, null);
                    }
                }
                give(player, gamer, stack(bought, gamer), bought.category(), was);
                break;
            }
            case SHEARS:
                gamer.shears = true;
                give(player, gamer, stack(bought, gamer), bought.category());
                break;
            default:
                give(player, gamer, stack(bought, gamer), bought.category());
                break;
        }
        player.sendMessage(ChatColor.GREEN + "You purchased " + ChatColor.GOLD + bought.name());
        player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 2f);
        return true;
    }

    /** The swords from the worst to the best. */
    private static final List<Material> SWORDS = Arrays.asList(Material.WOOD_SWORD, Material.GOLD_SWORD, Material.STONE_SWORD,
            Material.IRON_SWORD, Material.DIAMOND_SWORD);

    /**
     * The sword slot a new sword takes: the first slot (the hotbar first) holding a sword no better
     * than it, which it replaces; -1 when there is none.
     */
    static int swordSlot(PlayerInventory inventory, Material bought) {
        ItemStack[] contents = new ItemStack[inventory.getSize()];
        for (int slot = 0; slot < contents.length; slot++) {
            contents[slot] = inventory.getItem(slot);
        }
        return swordSlot(contents, bought);
    }

    static int swordSlot(ItemStack[] contents, Material bought) {
        int rank = SWORDS.indexOf(bought);
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack there = contents[slot];
            int was = there == null ? -1 : SWORDS.indexOf(there.getType());
            if (was >= 0 && was <= rank) {
                return slot;
            }
        }
        return -1;
    }

    /** The first slot holding an item whose type ends so (_PICKAXE), or -1. */
    private static int firstOf(PlayerInventory inventory, String suffix) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType().name().endsWith(suffix)) {
                return slot;
            }
        }
        return -1;
    }

    private static void removeAll(PlayerInventory inventory, String suffix) {
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.getType().name().endsWith(suffix)) {
                inventory.remove(stack);
            }
        }
    }

    /** How much of this currency the player carries. */
    static int count(Player player, Currency currency) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == currency.material()) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    /** The item the shop gives: in the team's colour, enchanted, unbreakable, with its potion effect. */
    ItemStack stack(ShopItem item, Gamer gamer) {
        short data = item.kind() == ShopItem.Kind.TEAM_BLOCK ? gamer.team.color.woolData() : item.data();
        ItemStack stack = new ItemStack(item.material(), item.amount(), data);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (item.enchantment() != null) {
            meta.addEnchant(item.enchantment(), item.enchantmentLevel(), true);
        }
        if (item.second() != null) {
            meta.addEnchant(item.second(), item.secondLevel(), true);
        }
        switch (item.kind()) {
            case SWORD:
            case AXE:
                if (gamer.team.upgrades.sharpness() && !meta.hasEnchant(Enchantment.DAMAGE_ALL)) {
                    meta.addEnchant(Enchantment.DAMAGE_ALL, 1, true);
                }
                meta.spigot().setUnbreakable(true);
                break;
            case PICKAXE:
            case SHEARS:
                meta.spigot().setUnbreakable(true);
                break;
            default:
                break;
        }
        if (item.kind() == ShopItem.Kind.POTION && meta instanceof PotionMeta) {
            PotionMeta potion = (PotionMeta) meta;
            potion.clearCustomEffects();
            potion.addCustomEffect(new PotionEffect(item.effect(), item.effectSeconds() * 20, item.effectLevel() - 1), true);
            potion.setDisplayName(ChatColor.WHITE + item.name());
        }
        if (item.kind() == ShopItem.Kind.UTILITY) {
            meta.setDisplayName(ChatColor.WHITE + item.name());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** At the start and after each death: the wooden sword, the armour, the tools and shears kept. */
    void giveKit(Player player, Gamer gamer) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        wearArmor(player, gamer);
        ItemStack sword = new ItemStack(Material.WOOD_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.spigot().setUnbreakable(true);
        if (gamer.team.upgrades.sharpness()) {
            meta.addEnchant(Enchantment.DAMAGE_ALL, 1, true);
        }
        sword.setItemMeta(meta);
        give(player, gamer, sword, Category.MELEE);
        if (gamer.pickaxe >= 0) {
            give(player, gamer, stack(catalog.pickaxes().get(gamer.pickaxe), gamer), Category.TOOLS);
        }
        if (gamer.axe >= 0) {
            give(player, gamer, stack(catalog.axes().get(gamer.axe), gamer), Category.TOOLS);
        }
        if (gamer.shears) {
            give(player, gamer, stack(catalog.byId("shears"), gamer), Category.TOOLS);
        }
        player.updateInventory();
    }

    /**
     * The armour of the fight kit (Top Bridge, the FFA), as in the top fights of the practice: full
     * diamond, Protection IV, unbreakable. No effect: the absorption is the golden apples'.
     */
    static void fightArmour(Player player) {
        Material[] pieces = {Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS, Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_HELMET};
        ItemStack[] armour = new ItemStack[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            ItemStack piece = new ItemStack(pieces[i]);
            ItemMeta meta = piece.getItemMeta();
            meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4, true);
            meta.spigot().setUnbreakable(true);
            piece.setItemMeta(meta);
            armour[i] = piece;
        }
        player.getInventory().setArmorContents(armour);
    }

    private static final Material[][] ARMOR = {
        {Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS},
        {Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS},
        {Material.IRON_LEGGINGS, Material.IRON_BOOTS},
        {Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS},
    };

    void wearArmor(Player player, Gamer gamer) {
        PlayerInventory inventory = player.getInventory();
        int protection = armourProtection(gamer);
        inventory.setHelmet(armor(Material.LEATHER_HELMET, gamer, protection));
        inventory.setChestplate(armor(Material.LEATHER_CHESTPLATE, gamer, protection));
        inventory.setLeggings(armor(ARMOR[gamer.armor][0], gamer, protection));
        inventory.setBoots(armor(ARMOR[gamer.armor][1], gamer, protection));
    }

    /** The Protection level on this player's armour: their team's Reinforced Armor upgrade. */
    int armourProtection(Gamer gamer) {
        return gamer.team.upgrades.protection();
    }

    private static ItemStack armor(Material material, Gamer gamer, int protection) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta) {
            ((LeatherArmorMeta) meta).setColor(gamer.team.color.leather());
        }
        if (protection > 0) {
            meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, protection, true);
        }
        meta.spigot().setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Puts the item in the hotbar slot the player's Hypixel hotbar manager gives its kind, if free
     * (or holding the same item, with room); else wherever there is room; else at their feet.
     */
    static void give(Player player, Gamer gamer, ItemStack item, Category category) {
        give(player, gamer, item, category, -1);
    }

    /**
     * The same, the slot of the item this one replaces (a better sword, pickaxe or axe; -1: none)
     * before any free slot: the player's hotbar manager first, else where the old one was.
     */
    static void give(Player player, Gamer gamer, ItemStack item, Category category, int replaced) {
        PlayerInventory inventory = player.getInventory();
        if (gamer.hotbar != null && category != null && category.hotbar() != null) {
            for (int slot = 0; slot < 9 && slot < gamer.hotbar.size(); slot++) {
                if (!category.hotbar().equalsIgnoreCase(gamer.hotbar.get(slot))) {
                    continue;
                }
                ItemStack there = inventory.getItem(slot);
                if (there == null || there.getType() == Material.AIR) {
                    inventory.setItem(slot, item);
                    return;
                }
                if (there.isSimilar(item) && there.getAmount() + item.getAmount() <= there.getMaxStackSize()) {
                    there.setAmount(there.getAmount() + item.getAmount());
                    return;
                }
            }
        }
        if (replaced >= 0) {
            ItemStack there = inventory.getItem(replaced);
            if (there == null || there.getType() == Material.AIR) {
                inventory.setItem(replaced, item);
                return;
            }
        }
        for (ItemStack left : inventory.addItem(item).values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }
    }

    /** Right click with a fireball: it flies where the player looks. */
    boolean throwFireball(Player player) {
        Gamer gamer = gamers.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (gamer == null || phase != Phase.PLAYING || now - gamer.lastFireball < FIREBALL_COOLDOWN_MS) {
            return false;
        }
        gamer.lastFireball = now;
        ItemStack hand = player.getItemInHand();
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.setItemInHand(null);
        }
        org.bukkit.entity.Fireball fireball = player.launchProjectile(org.bukkit.entity.Fireball.class);
        fireball.setIsIncendiary(false);
        fireball.setYield(FIREBALL_YIELD);
        Vector direction = player.getEyeLocation().getDirection();
        // straight where the player looks, 0.1 faster each tick, as on Hypixel (not Fireball#setDirection:
        // on 1.8 it makes the fireball ten times too fast)
        FireballAim.aim(fireball, direction);
        fireball.setVelocity(direction.clone().multiply(settings.fireball().speed));
        return true;
    }

    /**
     * A fireball thrown by this player (or null) has exploded: each player of the game around it is
     * pushed away and up, as on Hypixel, and takes its fixed damage (none to a teammate). Hurting an
     * enemy counts as a hit, for the kill if they fall.
     */
    void blast(org.bukkit.entity.Fireball fireball, Player thrower) {
        if (phase != Phase.PLAYING) {
            return;
        }
        BedwarsSettings.Fireball rules = settings.fireball();
        Location blast = fireball.getLocation();
        Gamer author = thrower == null ? null : gamers.get(thrower.getUniqueId());
        for (Gamer gamer : new ArrayList<>(gamers.values())) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (phase != Phase.PLAYING) {
                return;
            }
            if (player == null || gamer.eliminated || gamer.respawning || host.gameOf(gamer.id) != this
                    || !player.getWorld().equals(blast.getWorld())) {
                continue;
            }
            Location at = player.getLocation();
            if (!rules.reaches(at.getX() - blast.getX(), at.getY() - blast.getY(), at.getZ() - blast.getZ())) {
                continue;
            }
            double[] push = rules.push(blast.getX(), blast.getY(), blast.getZ(), at.getX(), at.getY(), at.getZ());
            player.setVelocity(new Vector(push[0], push[1], push[2]));
            double damage;
            if (author == null || author == gamer) {
                damage = rules.self;
            } else if (author.team == gamer.team) {
                damage = rules.teammates;
            } else {
                damage = rules.enemies;
                hit(gamer, author);
            }
            if (damage <= 0) {
                continue;
            }
            if (player.getHealth() - damage <= 0) {
                kill(player, author == null || author == gamer ? null : thrower, false);
            } else {
                player.setHealth(player.getHealth() - damage);
                player.playEffect(EntityEffect.HURT);
            }
        }
    }

    // --- Invisibility ---------------------------------------------------------------------------

    /**
     * Every other tick: an invisible player is wholly invisible, as on Hypixel. The others are told
     * again that they wear no armour (a player who comes in sight, or armour that changes, would
     * show it); the potion's swirls go. Their armour shows again when they can be seen.
     */
    private void hideArmour() {
        traceTicks += 2;
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            PotionEffect invisibility = player == null || !present(gamer) || gamer.eliminated || gamer.respawning ? null
                    : effect(player, PotionEffectType.INVISIBILITY);
            boolean invisible = invisibility != null;
            if (!invisible && !hidden.remove(gamer.id) || player == null) {
                continue;
            }
            if (invisible) {
                hidden.add(gamer.id);
                if (invisibleTraces()) {
                    traces(player);
                } else if (invisibility.hasParticles()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, invisibility.getDuration(),
                            invisibility.getAmplifier(), true, false), true);
                }
            } else {
                lastStep.remove(gamer.id);
            }
            for (Gamer other : gamers.values()) {
                Player viewer = other == gamer ? null : Bukkit.getPlayer(other.id);
                if (viewer != null && present(other) && viewer.getWorld().equals(player.getWorld())) {
                    ArmourHider.show(viewer, player, invisible, plugin.getLogger());
                }
            }
        }
    }

    /** A footprint every this many blocks walked. */
    static final double STEP = 0.8;
    /** A footprint is this far to the side of the middle of the player, left foot then right foot. */
    static final double FOOT = 0.15;
    /** The colour of the swirls of an invisibility potion (0x7F8392), as red, green, blue. */
    static final float[] SWIRL = {0x7F / 255f, 0x83 / 255f, 0x92 / 255f};

    /**
     * The traces of an invisible player, every other tick: a footprint on the ground each STEP
     * blocks, on one foot then the other, and a swirl of the potion now and then around them.
     */
    private void traces(Player player) {
        Location at = player.getLocation();
        World world = at.getWorld();
        double[] last = lastStep.get(player.getUniqueId());
        if (last == null) {
            lastStep.put(player.getUniqueId(), new double[] {at.getX(), at.getZ(), 1});
        } else if (player.isOnGround() && Math.hypot(at.getX() - last[0], at.getZ() - last[1]) >= STEP) {
            double[] foot = footprint(at.getX(), at.getZ(), at.getYaw(), last[2]);
            Location step = new Location(world, foot[0], Math.floor(at.getY() + 0.01) + 0.01, foot[1]);
            world.spigot().playEffect(step, Effect.FOOTSTEP, 0, 0, 0f, 0f, 0f, 0f, 1, 48);
            last[0] = at.getX();
            last[1] = at.getZ();
            last[2] = -last[2];
        }
        if (traceTicks % 6 == 0) {
            java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
            Location swirl = at.clone().add(random.nextDouble(-0.3, 0.3), random.nextDouble(0.2, 1.7), random.nextDouble(-0.3, 0.3));
            // no count: the offsets are the colour of the particle
            world.spigot().playEffect(swirl, Effect.POTION_SWIRL, 0, 0, SWIRL[0], SWIRL[1], SWIRL[2], 1f, 0, 48);
        }
    }

    /** Where a footprint goes (x, z): beside the middle of the player, on the side of this foot (1: right). */
    static double[] footprint(double x, double z, float yaw, double side) {
        double r = Math.toRadians(yaw);
        // the right hand of a player who looks along this yaw
        double rightX = -Math.cos(r);
        double rightZ = -Math.sin(r);
        return new double[] {x + rightX * FOOT * side, z + rightZ * FOOT * side};
    }

    // --- Team upgrades and traps ----------------------------------------------------------------

    /** The effects of the upgrades last the whole game: Haste, the Heal Pool's Regeneration. */
    private static final int FOREVER = Integer.MAX_VALUE;
    /** A Regeneration this long is the Heal Pool's (a golden apple's lasts seconds). */
    private static final int POOL_MARK = 20 * 60 * 60;

    /** The team upgrades NPC was clicked. */
    void upgradesClicked(Player player) {
        UpgradeMenu.open(player, this);
    }

    /** True if the NPC is the upgrades NPC of a team. */
    boolean isUpgrades(Entity entity) {
        return upgradeNpcs.contains(entity);
    }

    /**
     * What a chest of the map holds in this game, as on Hypixel: the same chest for everyone who
     * opens it (the island's chest, an enemy's too); an ender chest, each player's own.
     */
    Inventory chest(Block block, Gamer gamer) {
        if (block.getType() == Material.ENDER_CHEST) {
            if (gamer.enderChest == null) {
                gamer.enderChest = Bukkit.createInventory(null, 27, "Ender Chest");
            }
            return gamer.enderChest;
        }
        Inventory chest = chests.get(block);
        if (chest == null) {
            chest = Bukkit.createInventory(null, 27, "Chest");
            chests.put(block, chest);
        }
        return chest;
    }

    /** The name of a resource of the forges and generators (Iron, Gold, Diamond, Emerald), null for anything else. */
    static String resourceName(Material type) {
        switch (type) {
            case IRON_INGOT:
                return "Iron";
            case GOLD_INGOT:
                return "Gold";
            case DIAMOND:
                return "Diamond";
            case EMERALD:
                return "Emerald";
            default:
                return null;
        }
    }

    /**
     * The player hit this chest (or ender chest) with resources in hand: the whole stack goes into
     * it, as much as fits.
     */
    void deposit(Player player, Block block) {
        Gamer gamer = gamers.get(player.getUniqueId());
        ItemStack hand = player.getItemInHand();
        String name = hand == null ? null : resourceName(hand.getType());
        if (gamer == null || gamer.respawning || gamer.eliminated || name == null) {
            return;
        }
        int amount = hand.getAmount();
        int kept = 0;
        for (ItemStack left : chest(block, gamer).addItem(hand.clone()).values()) {
            kept += left.getAmount();
        }
        String where = block.getType() == Material.ENDER_CHEST ? "Ender Chest" : "Chest";
        if (kept == amount) {
            player.sendMessage(ChatColor.RED + "This " + where + " is full!");
            return;
        }
        if (kept == 0) {
            player.setItemInHand(null);
        } else {
            hand.setAmount(kept);
            player.setItemInHand(hand);
        }
        player.updateInventory();
        player.playSound(player.getLocation(), Sound.ITEM_PICKUP, 1f, 1.2f);
        player.sendMessage(ChatColor.GRAY + "You deposited " + ChatColor.WHITE + "x" + (amount - kept) + " " + name
                + ChatColor.GRAY + " into the " + ChatColor.AQUA + where + ChatColor.GRAY + "!");
    }

    /**
     * True if the upgrades cost as in solo and doubles: on a map of solo and doubles (eight
     * islands), whoever plays it; false on one of 3v3v3v3 and 4v4v4v4 or a ranked map (see
     * {@link Upgrades#doublesMap}).
     */
    boolean doublesPrices() {
        return Upgrades.doublesMap(instance.map().teams().size());
    }

    /** How many players the biggest team has. */
    int teamSize() {
        int size = 1;
        for (Team team : teams) {
            size = Math.max(size, team.members.size());
        }
        return size;
    }

    /** The player buys the next tier of this upgrade for their team. True if they did. */
    boolean buyUpgrade(Player player, Upgrades.Kind kind) {
        Gamer gamer = gamers.get(player.getUniqueId());
        if (gamer == null || phase != Phase.PLAYING || gamer.respawning || gamer.eliminated) {
            return false;
        }
        Team team = gamer.team;
        int tier = team.upgrades.tier(kind) + 1;
        if (tier > kind.tiers()) {
            refuse(player, "You already unlocked this upgrade!");
            return false;
        }
        if (!pay(player, kind.price(tier, doublesPrices()))) {
            return false;
        }
        team.upgrades.setTier(kind, tier);
        upgraded(team, kind, tier);
        purchased(team, gamer, kind.tierName(tier));
        return true;
    }

    /** The player buys this trap for their team: it waits its turn in the queue. True if they did. */
    boolean buyTrap(Player player, Upgrades.Trap trap) {
        Gamer gamer = gamers.get(player.getUniqueId());
        if (gamer == null || phase != Phase.PLAYING || gamer.respawning || gamer.eliminated) {
            return false;
        }
        Upgrades upgrades = gamer.team.upgrades;
        if (upgrades.traps.size() >= Upgrades.MAX_TRAPS) {
            refuse(player, "Trap queue full!");
            return false;
        }
        if (!pay(player, Upgrades.trapPrice(upgrades.traps.size()))) {
            return false;
        }
        upgrades.traps.add(trap);
        purchased(gamer.team, gamer, trap.displayName);
        return true;
    }

    private static void refuse(Player player, String why) {
        player.sendMessage(ChatColor.RED + why);
        player.playSound(player.getLocation(), Sound.VILLAGER_NO, 1f, 1f);
    }

    /** Takes this many diamonds from the player, if they have them. */
    private static boolean pay(Player player, int diamonds) {
        int have = count(player, Currency.DIAMOND);
        if (have < diamonds) {
            refuse(player, "You don't have enough Diamonds! Need " + (diamonds - have) + " more!");
            return false;
        }
        player.getInventory().removeItem(new ItemStack(Currency.DIAMOND.material(), diamonds));
        return true;
    }

    /** The whole team hears of an upgrade or a trap bought, as on Hypixel. */
    private void purchased(Team team, Gamer buyer, String what) {
        for (UUID member : team.members) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && host.gameOf(member) == this) {
                player.sendMessage(buyer.coloured() + ChatColor.GREEN + " purchased " + ChatColor.GOLD + what);
                if (member.equals(buyer.id)) {
                    player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 2f);
                }
            }
        }
    }

    /** A tier of an upgrade bought: what changes at once (the rest comes each second, and with each item). */
    private void upgraded(Team team, Upgrades.Kind kind, int tier) {
        Upgrades upgrades = team.upgrades;
        if (kind == Upgrades.Kind.FORGE) {
            if (upgrades.iron == null) {
                return;
            }
            double speed = Upgrades.forgeSpeed(tier);
            upgrades.iron.upgrade(1, Upgrades.faster(upgrades.ironRate, speed));
            upgrades.gold.upgrade(1, Upgrades.faster(upgrades.goldRate, speed));
            if (tier >= 3 && upgrades.emerald == null) {
                upgrades.emerald = new Generator(upgrades.forge, Material.EMERALD, new BedwarsSettings.Rate(
                        Upgrades.EMERALD_FORGE_TICKS, settings.emerald().get(0).max), null);
                generators.add(upgrades.emerald);
            }
            return;
        }
        for (UUID member : team.members) {
            Gamer gamer = gamers.get(member);
            Player player = Bukkit.getPlayer(member);
            if (gamer == null || player == null || gamer.eliminated || gamer.respawning || host.gameOf(member) != this) {
                continue;
            }
            if (kind == Upgrades.Kind.REINFORCED_ARMOR) {
                wearArmor(player, gamer);
            }
            teamEffects(player, gamer);
        }
    }

    /**
     * The upgrades of the player's team on them, each second and at each respawn: Haste, Sharpness on
     * the swords and axes they carry, the Heal Pool's Regeneration while they are in their base.
     */
    void teamEffects(Player player, Gamer gamer) {
        Upgrades upgrades = gamer.team.upgrades;
        if (upgrades.haste() > 0) {
            keep(player, PotionEffectType.FAST_DIGGING, upgrades.haste() - 1);
        }
        if (upgrades.sharpness()) {
            sharpen(player.getInventory());
        }
        if (upgrades.healPool()) {
            if (gamer.team.inBase(player.getLocation())) {
                keep(player, PotionEffectType.REGENERATION, 0);
            } else {
                PotionEffect pool = effect(player, PotionEffectType.REGENERATION);
                if (pool != null && pool.getAmplifier() == 0 && pool.getDuration() > POOL_MARK) {
                    player.removePotionEffect(PotionEffectType.REGENERATION);
                }
            }
        }
    }

    /**
     * Gives the effect for the rest of the game, unless the player has it already, as strong: given
     * again each second, it would never heal (Regeneration heals on its countdown).
     */
    private static void keep(Player player, PotionEffectType type, int amplifier) {
        PotionEffect now = effect(player, type);
        if (now == null || now.getAmplifier() < amplifier) {
            player.addPotionEffect(new PotionEffect(type, FOREVER, amplifier, true, false), true);
        }
    }

    /** Gives the effect, unless the player has it already, as strong and as long (a potion of the shop). */
    static void boost(Player player, PotionEffectType type, int ticks, int amplifier) {
        PotionEffect now = effect(player, type);
        if (now == null || now.getAmplifier() < amplifier || now.getAmplifier() == amplifier && now.getDuration() < ticks) {
            player.addPotionEffect(new PotionEffect(type, ticks, amplifier), true);
        }
    }

    static PotionEffect effect(Player player, PotionEffectType type) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(type)) {
                return effect;
            }
        }
        return null;
    }

    /** Sharpness I on the swords and axes of this inventory that have none. */
    static void sharpen(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !sharpened(item.getType()) || item.containsEnchantment(Enchantment.DAMAGE_ALL)) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            meta.addEnchant(Enchantment.DAMAGE_ALL, 1, true);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }
    }

    /** True for the items Sharpened Swords sharpens: the swords and the axes. */
    static boolean sharpened(Material type) {
        String name = type.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE");
    }

    /** Four times a second: an enemy in a base sets off its next trap, once the base has had its rest. */
    private void checkTraps() {
        long now = System.currentTimeMillis();
        for (Team team : teams) {
            Upgrades upgrades = team.upgrades;
            if (upgrades.traps.isEmpty() || now < upgrades.trapReadyAt || !standing(team)) {
                continue;
            }
            for (Gamer intruder : gamers.values()) {
                if (intruder.team == team || intruder.eliminated || intruder.respawning || now < intruder.milkUntil) {
                    continue;
                }
                Player player = Bukkit.getPlayer(intruder.id);
                if (player == null || host.gameOf(intruder.id) != this || player.getGameMode() == GameMode.SPECTATOR
                        || !team.inBase(player.getLocation())) {
                    continue;
                }
                upgrades.trapReadyAt = now + Upgrades.TRAP_COOLDOWN_MS;
                trigger(team, upgrades.traps.remove(0), intruder, player);
                break;
            }
        }
    }

    /** True while one of the team still plays. */
    private boolean standing(Team team) {
        for (UUID member : team.members) {
            Gamer gamer = gamers.get(member);
            if (gamer != null && !gamer.eliminated) {
                return true;
            }
        }
        return false;
    }

    /** The trap goes off on this intruder: its effect, and the team told, as on Hypixel. */
    private void trigger(Team team, Upgrades.Trap trap, Gamer intruder, Player player) {
        switch (trap) {
            case ITS_A_TRAP:
                boost(player, PotionEffectType.BLINDNESS, 8 * 20, 0);
                boost(player, PotionEffectType.SLOW, 8 * 20, 0);
                break;
            case COUNTER_OFFENSIVE:
                for (UUID member : team.members) {
                    Gamer ally = gamers.get(member);
                    Player allyPlayer = Bukkit.getPlayer(member);
                    if (ally != null && allyPlayer != null && !ally.eliminated && !ally.respawning
                            && host.gameOf(member) == this && team.inBase(allyPlayer.getLocation())) {
                        boost(allyPlayer, PotionEffectType.SPEED, 15 * 20, 1);
                        boost(allyPlayer, PotionEffectType.JUMP, 15 * 20, 1);
                    }
                }
                break;
            case ALARM:
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                break;
            case MINER_FATIGUE:
                boost(player, PotionEffectType.SLOW_DIGGING, 10 * 20, 0);
                break;
            default:
                break;
        }
        boolean alarm = trap == Upgrades.Trap.ALARM;
        String title = ChatColor.RED + "" + ChatColor.BOLD + (alarm ? "ALARM!!!" : "TRAP TRIGGERED!");
        String subtitle = alarm ? ChatColor.WHITE + "Enemy " + intruder.coloured() + ChatColor.WHITE + " entered your base!"
                : ChatColor.WHITE + "Your " + ChatColor.GREEN + trap.displayName + ChatColor.WHITE + " was set off!";
        String message = alarm ? ChatColor.RED + "" + ChatColor.BOLD + "ALARM!!! " + ChatColor.GRAY + "Your Alarm Trap was set off by "
                + intruder.coloured() + ChatColor.GRAY + " of the " + intruder.team.color.coloured() + ChatColor.GRAY + " team!"
                : ChatColor.RED + "" + ChatColor.BOLD + "TRAP TRIGGERED! " + ChatColor.GRAY + "Your " + ChatColor.GREEN
                + trap.displayName + ChatColor.GRAY + " was set off!";
        for (UUID member : team.members) {
            Player ally = Bukkit.getPlayer(member);
            if (ally != null && host.gameOf(member) == this) {
                ally.sendTitle(title, subtitle);
                ally.sendMessage(message);
                ally.playSound(ally.getLocation(), alarm ? Sound.NOTE_PLING : Sound.ENDERMAN_TELEPORT, 1f, alarm ? 0.5f : 1f);
            }
        }
    }

    // --- Messages -------------------------------------------------------------------------------

    void broadcast(String message, Sound sound) {
        replayChat(message);
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null && host.gameOf(gamer.id) == this) {
                player.sendMessage(message);
                if (sound != null) {
                    player.playSound(player.getLocation(), sound, 1f, 1f);
                }
            }
        }
    }

    void title(String title, String subtitle) {
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null && host.gameOf(gamer.id) == this) {
                player.sendTitle(title, subtitle);
            }
        }
    }

    /** The teams, for tests and the shop. */
    List<Team> teams() {
        return Collections.unmodifiableList(teams);
    }
}
