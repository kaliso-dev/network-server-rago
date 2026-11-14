package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * A free for all on a Bed Wars map, open to everyone: players join and leave when they like, each
 * for themselves, and come back at once after a death (in the arena, three seconds after it), on
 * the island farthest from the others. The beds stay as the map has them, unbreakable; the blocks placed go after a while (10
 * seconds in the FFA, 45 in the arena), so that the map never fills up, and only on the islands of
 * a map that lists them (iles:, Archway FFA): none on the bridges, nor up to the top bridge. No
 * block either near a place where the players spawn. The kills count in the stats of the mode.
 *
 * <p>FFA: the mode's kit as each player arranged it (the lobby's kit editor), with its armour,
 * given back at each life; a kill heals the killer. Arena (Free FFA): the island forges, the
 * diamonds and emeralds and a shop of its own (the diamonds buy too), the armour and tools kept
 * as in Bed Wars, and on each bed a defense of three layers ready for top fights, put back when
 * nobody is near it. Top Bridge FFA (a mode of top-bridge: in config.yml): the FFA on the top
 * bridge of Antenna, built between the two islands farthest apart: the players start and come
 * back on the platform, away from the others, and one who falls off it is dead. The FFA and Top
 * Bridge FFA play the owner's fight kit: a stone sword and a stack of golden apples (and in the FFA,
 * 3 fireballs, a pearl and 2 stacks of wool), diamond leggings and boots with a red leather helmet
 * and chestplate, Protection IV. In both, the
 * boards of the most kills ever in the mode float in the air beside the fight (see
 * {@link #boardPlaces}), and the sidebar shows the 3 players with the most kills in the game. Lobby
 * Fight FFA (lobby-fight-ffa) is the FFA in the waiting lobby of the map (see {@link LobbyFight}):
 * its own kit (in the lobby's config.yml: a stone sword, 5 golden apples and 3 fireballs), each
 * kill bringing the killer's golden apples back to the kit's, at most {@value #LOBBY_MAX_PLAYERS}
 * players, each life somewhere on its floor away from the others, dead a few blocks under it, the
 * blocks in the lobby only. Main thread only.
 */
final class FfaGame extends BedwarsGame {

    /** At most this many players in one copy of the map: the next ones get another copy. */
    static final int MAX_PLAYERS = 16;
    /** In the waiting lobby (Lobby Fight FFA), smaller: at most this many. */
    static final int LOBBY_MAX_PLAYERS = 12;
    /** The arena's wait before a new life; the FFA and Top Bridge FFA have none: the respawn is instant. */
    static final int ARENA_RESPAWN_SECONDS = 3;
    /** The blocks placed in the FFA go after this long... */
    static final int DECAY_SECONDS = 10;
    /** ...and in the arena, where they make the defenses of the top fights. */
    static final int ARENA_DECAY_SECONDS = 45;
    /** The players with the most kills in the game, in the sidebar. */
    static final int SIDEBAR_TOP = 3;
    /** How often the broken defenses come back, when nobody stands near them. */
    private static final int REPAIR_SECONDS = 20;
    private static final double REPAIR_DISTANCE = 8;
    private static final Material[] TOP_FIGHT = {Material.ENDER_STONE, Material.WOOD, Material.WOOL};

    private final boolean arena;
    /** Lobby Fight FFA: the fight in the map's waiting lobby; null for the others. */
    private final LobbyFight lobby;
    /** Every island of the map: where the players come back, where the forges and shops stand. */
    private final List<Team> islands = new ArrayList<>();
    private final BlockDecay decay;
    private final Random random = new Random();
    private final List<Defense> defenses = new ArrayList<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();
    private final Map<UUID, Integer> deaths = new HashMap<>();
    /** The spawn each player chose (FFA with apparitions: in its map); none: Random. */
    private final Map<UUID, String> spawnChoice = new HashMap<>();
    /** When each player last spawned: a choice made just after it moves them at once. */
    private final Map<UUID, Long> spawnedAt = new HashMap<>();
    /** A choice made this soon after spawning, at full health, moves the player at once. */
    static final long MOVE_AFTER_SPAWN_MS = 10_000L;
    /** A spawn with nobody this close is taken first, at random; else the one farthest from them. */
    static final double SAFE_DISTANCE = 12;
    /** How often the board of the most kills is read again from the lobby's stats. */
    static final int BOARD_SECONDS = 5;
    /** Top Bridge FFA: the board this far across the middle line of the platform (8 blocks past its edge)... */
    static final int BOARD_BESIDE_BRIDGE = TopBridge.HALF_WIDTH + 8;
    /** ...its top line this high above the platform. */
    static final int BOARD_ABOVE_BRIDGE = 7;
    /** Lobby Fight FFA: the board this far past the edge of the lobby... */
    static final int BOARD_BESIDE_LOBBY = 4;
    /** ...its top line this high above the floor. */
    static final int BOARD_ABOVE_LOBBY = 5;
    /** Without a place in the map (classement:), the top line this high above the highest a player can build. */
    static final int BOARD_ABOVE_LIMIT = 4;
    /** The boards of the most kills (FFA and Top Bridge FFA); none until the first second, nor in the arena. */
    private final List<KillBoard> boards = new ArrayList<>();
    /** The two islands of the top bridge, once it is planned; null without one. */
    private Team[] bridged;
    private int colours;

    FfaGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides, boolean arena) {
        this(plugin, host, instance, modeId, modeName, sides, arena, false);
    }

    /** The FFA, the arena, or Lobby Fight FFA (inLobby: in the map's waiting lobby, which it must have). */
    FfaGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides, boolean arena, boolean inLobby) {
        super(plugin, host, instance, modeId, modeName, sides, arena ? plugin.arenaCatalog() : plugin.catalog());
        this.arena = arena;
        this.lobby = inLobby && !arena && instance.map().waitingLobby() != null
                ? new LobbyFight(instance.map().waitingLobby(), instance.world()) : null;
        this.decay = new BlockDecay(arena ? ARENA_DECAY_SECONDS : DECAY_SECONDS);
        for (TeamColor color : instance.map().completeTeams()) {
            islands.add(new Team(color, instance.map().teams().get(color)));
        }
        colours = teams.size();
    }

    /** True while one more player can join this game. */
    /** Not kept for /replay: open to everyone, no teams. */
    @Override
    boolean recorded() {
        return false;
    }

    @Override
    boolean joinable() {
        return phase != Phase.ENDED && present().size() < (lobby != null ? LOBBY_MAX_PLAYERS : MAX_PLAYERS);
    }

    /** True for Lobby Fight FFA, in the map's waiting lobby. */
    @Override
    boolean playsInWaitingLobby() {
        return lobby != null;
    }

    /** A player joins the game that is on: their own team, in the next colour. */
    @Override
    void join(Player player) {
        UUID id = player.getUniqueId();
        forget(id);
        TeamColor[] palette = TeamColor.values();
        TeamColor color = palette[colours++ % palette.length];
        Team team = new Team(color, instance.map().teams().containsKey(color) ? instance.map().teams().get(color)
                : islands.get(0).points);
        team.members.add(id);
        teams.add(team);
        Gamer gamer = newGamer(player, team);
        gamers.put(id, gamer);
        host.joined(id, this);
        place(player, gamer);
        welcome(player, gamer);
        broadcast(gamer.coloured() + ChatColor.GRAY + " joined the " + (arena ? "arena" : lobby != null ? "lobby fight"
                : "FFA") + ".", null);
        sidebars();
    }

    /** A player who left: their team goes, and what the game remembered of them. */
    private void forget(UUID id) {
        Gamer old = gamers.remove(id);
        if (old != null) {
            teams.remove(old.team);
            cancel(old.respawn);
        }
        streaks.remove(id);
        spawnChoice.remove(id);
        spawnedAt.remove(id);
    }

    // --- What the free for all changes ----------------------------------------------------------

    @Override
    boolean economy() {
        return arena;
    }

    /** Everyone in the game hears everyone. */
    @Override
    boolean chatTeams() {
        return false;
    }

    /** The names keep the players' ranks: no teams to show. */
    @Override
    boolean teamNameTags() {
        return false;
    }

    @Override
    boolean upgrades() {
        return false;
    }

    @Override
    List<Team> islands() {
        return islands;
    }

    /** The beds stay as the map has them: they are part of the islands, and of the top fights. */
    @Override
    void removeUnusedBeds() {
    }

    @Override
    void setUp() {
        if (!arena) {
            return;
        }
        World world = instance.world();
        for (Team island : islands) {
            if (island.points.bed == null) {
                continue;
            }
            Defense defense = Defense.build(instance, island.points.bed.blockIn(world), TOP_FIGHT, island.color.woolData());
            if (defense != null) {
                defenses.add(defense);
            }
        }
    }

    @Override
    int countdownSeconds() {
        return 0;
    }

    @Override
    int respawnSeconds() {
        return arena ? ARENA_RESPAWN_SECONDS : 0;
    }

    /** Nobody is ever out: a death is followed by a new life. */
    @Override
    boolean finalDeath(Gamer gamer) {
        return false;
    }

    /** No bed belongs to a player: they stay as the map has them. */
    @Override
    Team bedTeam(Block block) {
        return null;
    }

    /** No timed event: the generators stay at their first tier, the game never ends on its own. */
    @Override
    void events() {
    }

    /** The top bridge links the two islands with a bed farthest apart. */
    @Override
    Team[] topBridgeEnds() {
        Team[] best = null;
        double farthest = -1;
        for (int i = 0; i < islands.size(); i++) {
            for (int j = i + 1; j < islands.size(); j++) {
                MapPoint a = islands.get(i).points.bed;
                MapPoint b = islands.get(j).points.bed;
                if (a == null || b == null) {
                    continue;
                }
                double distance = Math.hypot(a.x() - b.x(), a.z() - b.z());
                if (distance > farthest) {
                    farthest = distance;
                    best = new Team[] {islands.get(i), islands.get(j)};
                }
            }
        }
        bridged = best;
        return best;
    }

    /** Top Bridge FFA: everyone on the platform, and dead when they fall off it. */
    @Override
    boolean onTheBridge() {
        return !arena && lobby == null && hasTopBridge();
    }

    /** In the waiting lobby, a few blocks under its floor is the fall of a death. */
    @Override
    double fallLine() {
        return lobby != null ? lobby.fallLine() : super.fallLine();
    }

    @Override
    Location watchPoint() {
        return lobby != null ? lobby.watchPoint() : super.watchPoint();
    }

    /**
     * The island farthest from the other players, so that nobody comes back in the middle of a
     * fight; on the top bridge, the place of the platform farthest from them, among a few drawn.
     */
    @Override
    Location spawnOf(Gamer gamer) {
        if (choosesSpawn()) {
            spawnedAt.put(gamer.id, System.currentTimeMillis());
            return pick(gamer, spawnPool(spawnChoice.get(gamer.id)));
        }
        World world = instance.world();
        List<Location> spawns = new ArrayList<>();
        if (lobby != null) {
            for (int i = 0; i < 8; i++) {
                spawns.add(lobby.anywhere(random));
            }
        } else if (onTheBridge() && topBridge != null) {
            for (int i = 0; i < 8; i++) {
                spawns.add(at(topBridge.anywhere(random)));
            }
        } else {
            for (Team island : bridged != null ? java.util.Arrays.asList(bridged) : islands) {
                spawns.add(island.points.spawn.in(world));
            }
        }
        Collections.shuffle(spawns);
        Location best = spawns.get(0);
        double farthest = -1;
        for (Location spawn : spawns) {
            double nearest = Double.MAX_VALUE;
            for (Gamer other : gamers.values()) {
                Player player = Bukkit.getPlayer(other.id);
                if (other == gamer || other.respawning || player == null || !present(other)
                        || !player.getWorld().equals(world)) {
                    continue;
                }
                nearest = Math.min(nearest, player.getLocation().distanceSquared(spawn));
            }
            if (nearest > farthest) {
                farthest = nearest;
                best = spawn;
            }
        }
        return best;
    }

    // --- The spawn to choose (Archway FFA: Top bridge, Low, Mid or Random) ----------------------

    /** The FFA (not the arena, nor the top bridge, nor the waiting lobby) on a map with spawns to choose from. */
    boolean choosesSpawn() {
        return !arena && lobby == null && !onTheBridge() && !instance.map().spawnChoices().isEmpty();
    }

    /**
     * Where a choice spawns: its points; Random (null, or a name the map no longer has): every
     * one of them, the bases and the diamonds.
     */
    List<Location> spawnPool(String choice) {
        World world = instance.world();
        List<Location> pool = new ArrayList<>();
        Map<String, List<MapPoint>> choices = instance.map().spawnChoices();
        if (choice != null && choices.containsKey(choice)) {
            for (MapPoint point : choices.get(choice)) {
                pool.add(point.in(world));
            }
            return pool;
        }
        for (Team island : islands) {
            pool.add(island.points.spawn.in(world));
        }
        for (MapPoint diamond : instance.map().diamonds()) {
            pool.add(diamond.in(world));
        }
        for (List<MapPoint> points : choices.values()) {
            for (MapPoint point : points) {
                pool.add(point.in(world));
            }
        }
        return pool;
    }

    /** A spawn of the pool with nobody near it, at random; else the one farthest from the others. */
    private Location pick(Gamer gamer, List<Location> pool) {
        World world = instance.world();
        Collections.shuffle(pool, random);
        Location best = pool.get(0);
        double farthest = -1;
        for (Location spawn : pool) {
            double nearest = Double.MAX_VALUE;
            for (Gamer other : gamers.values()) {
                Player player = Bukkit.getPlayer(other.id);
                if (other == gamer || other.respawning || player == null || !present(other)
                        || !player.getWorld().equals(world)) {
                    continue;
                }
                nearest = Math.min(nearest, player.getLocation().distanceSquared(spawn));
            }
            if (nearest >= SAFE_DISTANCE * SAFE_DISTANCE) {
                return spawn;
            }
            if (nearest > farthest) {
                farthest = nearest;
                best = spawn;
            }
        }
        return best;
    }

    /** Opens the menu of the spawns; false if this game has none to choose. */
    boolean openSpawnMenu(Player player) {
        if (!choosesSpawn() || gamers.get(player.getUniqueId()) == null) {
            return false;
        }
        FfaSpawnMenu.open(player, this, new ArrayList<>(instance.map().spawnChoices().keySet()),
                spawnChoice.get(player.getUniqueId()));
        return true;
    }

    /**
     * The player chose a spawn (null: Random): from their next life on, and at once if they have
     * just spawned and are not hurt.
     */
    void choose(Player player, String choice) {
        Gamer gamer = gamers.get(player.getUniqueId());
        if (gamer == null || host.gameOf(gamer.id) != this || !choosesSpawn()) {
            return;
        }
        if (choice == null) {
            spawnChoice.remove(gamer.id);
        } else {
            spawnChoice.put(gamer.id, choice);
        }
        String name = choice == null ? "Random" : choice;
        Long at = spawnedAt.get(gamer.id);
        if (at != null && System.currentTimeMillis() - at < MOVE_AFTER_SPAWN_MS && !gamer.respawning
                && player.getHealth() >= player.getMaxHealth()) {
            player.teleport(spawnOf(gamer));
            player.sendMessage(ChatColor.GREEN + "Your spawn: " + ChatColor.WHITE + name + ChatColor.GREEN + ".");
        } else {
            player.sendMessage(ChatColor.GREEN + "Your spawn: " + ChatColor.WHITE + name + ChatColor.GREEN
                    + ", from your next life.");
        }
    }

    /** With spawns to choose, the menu opens again at each life: a click moves the player at once. */
    @Override
    void respawn(Player player, Gamer gamer) {
        super.respawn(player, gamer);
        if (choosesSpawn()) {
            openSpawnMenu(player);
        }
    }

    @Override
    void welcome(Player player, Gamer gamer) {
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + modeName + ChatColor.GREEN + " on " + ChatColor.WHITE
                + instance.map().world() + ChatColor.GREEN + ": everyone for themselves.");
        if (arena) {
            player.sendMessage(ChatColor.GRAY + "Iron and gold at the forges, diamonds and emeralds in the middle, and a "
                    + "shop of its own: the diamonds buy Sharpness, Power II, Strength... Your armour stays; the beds have "
                    + "a defense ready for top fights.");
        } else if (onTheBridge()) {
            player.sendMessage(ChatColor.GRAY + "Everyone fights on the platform of the top bridge, diamond leggings and boots on: fall "
                    + "off and you die, and come back on it. A kill heals you. /spawn takes you back to the lobby.");
            return;
        } else if (lobby != null) {
            ItemStack[] kit = lobbyKit(player, gamer);
            int apples = count(kit, Material.GOLDEN_APPLE);
            player.sendMessage(ChatColor.GRAY + "Everyone fights in the waiting lobby of the map with your kit (as you"
                    + " arranged it in the lobby's kit editor): fall out of it and you die, and come back in it. A kill heals"
                    + " you" + (apples > 0 ? " and brings your golden apples back to " + apples : "") + "."
                    + (hasBlocks(kit) ? " Your blocks go after " + decay.seconds() + " seconds." : "")
                    + " /spawn takes you back to the lobby.");
            return;
        } else {
            player.sendMessage(ChatColor.GRAY + "Your kit (arranged in the lobby's kit editor) comes back at each life, "
                    + "and a kill heals you.");
            if (choosesSpawn()) {
                sendButton(player, ChatColor.GRAY + "Where you spawn: "
                        + String.join(", ", instance.map().spawnChoices().keySet()) + " or Random. ",
                        ChatColor.GREEN + "" + ChatColor.BOLD + "[CHOOSE]", "/ffaspawn");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && host.gameOf(player.getUniqueId()) == this) {
                        openSpawnMenu(player);
                    }
                });
            }
        }
        player.sendMessage(ChatColor.GRAY + "Your blocks go after " + decay.seconds() + " seconds"
                + (instance.map().islands().isEmpty() ? "" : ", and only on the islands, not on the bridges")
                + ". /spawn takes you back to the lobby.");
    }

    /**
     * FFA: the mode's kit and the fight armour, its leather red for everyone, at each life. Arena:
     * the start of a Bed Wars life.
     */
    @Override
    void giveKit(Player player, Gamer gamer) {
        if (arena) {
            super.giveKit(player, gamer);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        ItemStack[] kit = lobbyKit(player, gamer);
        if (kit != null) {
            inventory.setContents(kit);
        } else {
            for (ItemStack item : fightKit(!onTheBridge())) {
                inventory.addItem(kitItem(item, gamer));
            }
        }
        fightArmour(player, TeamColor.RED.leather());
        player.updateInventory();
    }

    /** True if this kit holds blocks to place; a kit made by the game (null) has its wool. */
    static boolean hasBlocks(ItemStack[] kit) {
        if (kit == null) {
            return true;
        }
        for (ItemStack item : kit) {
            if (item != null && item.getType().isBlock()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The owner's fight kit, when the lobby has none for the mode: a stone sword and a stack of
     * golden apples; the FFA's (full) adds 3 fireballs, a pearl, 2 stacks of wool and shears.
     */
    static List<ItemStack> fightKit(boolean full) {
        List<ItemStack> items = new ArrayList<>(java.util.Arrays.asList(new ItemStack(Material.STONE_SWORD),
                new ItemStack(Material.GOLDEN_APPLE, 64)));
        if (full) {
            items.add(new ItemStack(Material.FIREBALL, 3));
            items.add(new ItemStack(Material.ENDER_PEARL));
            items.add(new ItemStack(Material.WOOL, 64));
            items.add(new ItemStack(Material.WOOL, 64));
            items.add(new ItemStack(Material.SHEARS));
        }
        return items;
    }

    @Override
    void died(Gamer victim, Gamer author, boolean finalKill) {
        deaths.merge(victim.id, 1, Integer::sum);
        Integer ended = streaks.remove(victim.id);
        if (ended != null && ended >= 5 && author != null) {
            broadcast(author.coloured() + ChatColor.GRAY + " ended the streak of " + ChatColor.GOLD + ended + ChatColor.GRAY
                    + " kills of " + victim.coloured() + ChatColor.GRAY + "!", null);
        }
        if (author == null) {
            return;
        }
        int streak = streaks.merge(author.id, 1, Integer::sum);
        if (streak % 5 == 0) {
            broadcast(author.coloured() + ChatColor.GOLD + " is on a streak of " + streak + " kills!", Sound.ENDERDRAGON_WINGS);
        }
        Player killer = Bukkit.getPlayer(author.id);
        if (!arena && killer != null && !author.respawning) {
            killer.setHealth(killer.getMaxHealth());
            if (lobby != null) {
                refillApples(killer, author);
            }
        }
    }

    /** Lobby Fight FFA: a kill brings the killer's golden apples back to as many as their kit gives. */
    private void refillApples(Player killer, Gamer author) {
        PlayerInventory inventory = killer.getInventory();
        ItemStack[] contents = inventory.getContents();
        List<Integer> filled = refill(contents, lobbyKit(killer, author), Material.GOLDEN_APPLE);
        // the stacks grown are the inventory's own (a stack being eaten goes on being eaten); set the new ones
        for (int slot : filled) {
            inventory.setItem(slot, contents[slot]);
        }
        killer.updateInventory();
    }

    /** How many items of this material these stacks hold (none without stacks). */
    static int count(ItemStack[] stacks, Material material) {
        int count = 0;
        if (stacks != null) {
            for (ItemStack item : stacks) {
                if (item != null && item.getType() == material) {
                    count += item.getAmount();
                }
            }
        }
        return count;
    }

    /**
     * Brings these contents back to as many items of this material as the kit gives, when they hold
     * fewer: onto the stacks of it they hold, the one in the kit's slot for it first; what is left
     * in the kit's slot for it if empty, else in the first empty slots. A stack held grows in place
     * (eating it goes on). Returns the slots that were empty and now hold a stack, for the inventory
     * to set; none without a kit or without that material in it.
     */
    static List<Integer> refill(ItemStack[] contents, ItemStack[] kit, Material material) {
        List<Integer> filled = new ArrayList<>();
        ItemStack model = null;
        List<Integer> order = new ArrayList<>();
        for (int slot = 0; kit != null && slot < kit.length; slot++) {
            if (kit[slot] != null && kit[slot].getType() == material) {
                if (model == null) {
                    model = kit[slot];
                }
                if (slot < contents.length) {
                    order.add(slot);
                }
            }
        }
        int missing = count(kit, material) - count(contents, material);
        if (model == null || missing <= 0) {
            return filled;
        }
        for (int slot = 0; slot < contents.length; slot++) {
            if (!order.contains(slot)) {
                order.add(slot);
            }
        }
        int max = material.getMaxStackSize();
        for (int slot : order) {
            ItemStack held = contents[slot];
            if (missing > 0 && held != null && held.getType() == material && held.getDurability() == model.getDurability()
                    && held.getAmount() < max) {
                int put = Math.min(missing, max - held.getAmount());
                held.setAmount(held.getAmount() + put);
                missing -= put;
            }
        }
        for (int slot : order) {
            if (missing > 0 && (contents[slot] == null || contents[slot].getType() == Material.AIR)) {
                ItemStack stack = model.clone();
                stack.setAmount(Math.min(missing, max));
                contents[slot] = stack;
                filled.add(slot);
                missing -= stack.getAmount();
            }
        }
        return filled;
    }

    /**
     * The FFA keeps the places where the players spawn free of blocks too, as the bases: the
     * spawns to choose (apparitions:) and, when Random draws among them, the diamonds.
     */
    @Override
    String cannotBuildAt(Block block) {
        if (lobby != null) {
            return lobby.cannotBuildAt(block);
        }
        String refused = super.cannotBuildAt(block);
        if (refused != null || !choosesSpawn()) {
            return refused;
        }
        for (List<MapPoint> points : instance.map().spawnChoices().values()) {
            for (MapPoint point : points) {
                if (near(point, block, SPAWN_PROTECTION, 3)) {
                    return "You can't place blocks here!";
                }
            }
        }
        for (MapPoint point : instance.map().diamonds()) {
            if (near(point, block, SPAWN_PROTECTION, 3)) {
                return "You can't place blocks here!";
            }
        }
        return null;
    }

    @Override
    void placed(Player player, Block block) {
        decay.placed(block, seconds);
    }

    @Override
    void everySecond() {
        decay.tick(instance, instance.world(), seconds);
        if (!arena && seconds % BOARD_SECONDS == 1) {
            updateBoard();
        }
        if (arena && seconds % REPAIR_SECONDS == 0) {
            for (Defense defense : defenses) {
                if (!anyoneNear(defense.bed().get(0).getLocation().add(0.5, 0, 0.5))) {
                    defense.repair();
                }
            }
        }
    }

    /** The boards of the most kills, put up the first time: the ten first of the mode, from the lobby's stats. */
    private void updateBoard() {
        if (boards.isEmpty()) {
            for (Location where : boardPlaces()) {
                boards.add(new KillBoard(where));
            }
            if (boards.isEmpty()) {
                return;
            }
        }
        List<String> lines = KillBoard.lines(plugin.lobby().topKills(modeId, KillBoard.ROWS));
        for (KillBoard board : boards) {
            board.show(lines);
        }
    }

    /**
     * Where the boards of the most kills float (the top line of each), in the air and away from
     * the fight: in Top Bridge FFA beside the middle of the platform, past its edge and above it; in
     * the FFA where the map puts them (classement: in its bedwars.yml, Archway FFA: beside the top
     * bridge, and over the low of the middle), else one high above the middle of the islands. None
     * while the top bridge is not built.
     */
    List<Location> boardPlaces() {
        World world = instance.world();
        List<Location> places = new ArrayList<>();
        if (lobby != null) {
            // in the air past the east edge of the lobby, its top line a little above the heads
            BedwarsMap.WaitingLobby room = lobby.lobby();
            places.add(new Location(world, room.centreX + room.radius + BOARD_BESIDE_LOBBY, room.floorY + BOARD_ABOVE_LOBBY,
                    room.centreZ));
            return places;
        }
        if (onTheBridge()) {
            if (topBridge != null) {
                double[] spot = topBridge.beside(BOARD_BESIDE_BRIDGE, BOARD_ABOVE_BRIDGE);
                places.add(new Location(world, spot[0], spot[1], spot[2]));
            }
            return places;
        }
        for (MapPoint place : instance.map().killBoards()) {
            places.add(place.in(world));
        }
        if (!places.isEmpty()) {
            return places;
        }
        double x = 0;
        double z = 0;
        for (Team island : islands) {
            x += island.points.spawn.x();
            z += island.points.spawn.z();
        }
        places.add(new Location(world, x / islands.size(), Math.min(world.getMaxHeight() - 2,
                instance.map().buildLimit() + BOARD_ABOVE_LIMIT), z / islands.size()));
        return places;
    }

    @Override
    void cleanUp() {
        super.cleanUp();
        for (KillBoard board : boards) {
            board.remove();
        }
        boards.clear();
    }

    private boolean anyoneNear(Location where) {
        for (Gamer gamer : present()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null && player.getWorld().equals(where.getWorld())
                    && player.getLocation().distanceSquared(where) < REPAIR_DISTANCE * REPAIR_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    @Override
    void left(Gamer gamer) {
        forget(gamer.id);
        deaths.remove(gamer.id);
    }

    /** The game goes on as long as someone plays it. */
    @Override
    void checkEnd() {
        if (present().isEmpty()) {
            close();
        }
    }

    @Override
    List<String> lines(Gamer viewer) {
        List<String> lines = new ArrayList<>();
        // the mode in the colour of its category, then the map
        lines.add(modeLine != null ? modeLine + " " + ChatColor.GRAY + instance.map().world()
                : ChatColor.WHITE + (arena ? "Arena on " : bridged != null ? "Top Bridge on " : "FFA on ") + ChatColor.GREEN
                        + instance.map().world());
        lines.add("");
        List<Gamer> here = present();
        lines.add(ChatColor.WHITE + "Players: " + ChatColor.GREEN + here.size());
        lines.add(ChatColor.WHITE + "Kills: " + ChatColor.GREEN + viewer.kills + ChatColor.WHITE + "  Deaths: "
                + ChatColor.GREEN + deaths.getOrDefault(viewer.id, 0));
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streaks.getOrDefault(viewer.id, 0));
        lines.add("");
        List<Map.Entry<String, Integer>> top = new ArrayList<>();
        for (Gamer gamer : here) {
            top.add(new java.util.AbstractMap.SimpleEntry<>(gamer.name, gamer.kills));
        }
        lines.addAll(topLines(top, SIDEBAR_TOP));
        return lines;
    }

    /**
     * The lines of the players with the most kills in the game, for the sidebar (ten lines of a
     * game at most there, with the lobby's ping): "Top kills", then "1. Sofiene 12" for each of
     * the first with a kill, or "Nobody yet". Ties keep the order given.
     */
    static List<String> topLines(List<Map.Entry<String, Integer>> players, int rows) {
        List<Map.Entry<String, Integer>> top = new ArrayList<>();
        for (Map.Entry<String, Integer> player : players) {
            if (player.getValue() > 0) {
                top.add(player);
            }
        }
        Collections.sort(top, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Top kills");
        if (top.isEmpty()) {
            lines.add(ChatColor.GRAY + "Nobody yet");
        }
        ChatColor[] places = {ChatColor.GOLD, ChatColor.WHITE, ChatColor.RED};
        for (int i = 0; i < Math.min(rows, top.size()); i++) {
            String name = top.get(i).getKey();
            lines.add(places[Math.min(i, places.length - 1)] + "" + (i + 1) + ". " + ChatColor.WHITE
                    + (name.length() > 16 ? name.substring(0, 16) : name) + ChatColor.GRAY + " " + top.get(i).getValue());
        }
        return lines;
    }
}
