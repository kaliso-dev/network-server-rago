package gg.rago.bedwars;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * for themselves, and come back three seconds after a death, on the island farthest from the
 * others. The beds stay as the map has them, unbreakable; the blocks placed go after a while, so
 * that the map never fills up. The kills count in the stats of the mode.
 *
 * <p>FFA: the mode's kit as each player arranged it (the lobby's kit editor), with iron armour,
 * given back at each life; a kill heals the killer. Arena (Free FFA): the island forges, the
 * diamonds and emeralds and a shop of its own (the diamonds buy too), the armour and tools kept
 * as in Bed Wars, and on each bed a defense of three layers ready for top fights, put back when
 * nobody is near it. Main thread only.
 */
final class FfaGame extends BedwarsGame {

    /** At most this many players in one copy of the map: the next ones get another copy. */
    static final int MAX_PLAYERS = 16;
    private static final int RESPAWN_SECONDS = 3;
    /** The blocks placed go after this long. */
    private static final int DECAY_SECONDS = 45;
    /** How often the broken defenses come back, when nobody stands near them. */
    private static final int REPAIR_SECONDS = 20;
    private static final double REPAIR_DISTANCE = 8;
    private static final Material[] TOP_FIGHT = {Material.ENDER_STONE, Material.WOOD, Material.WOOL};

    private final boolean arena;
    /** Every island of the map: where the players come back, where the forges and shops stand. */
    private final List<Team> islands = new ArrayList<>();
    /** The blocks placed, oldest first: their key and the second of the game they were placed at. */
    private final Deque<long[]> decaying = new ArrayDeque<>();
    /** The second each placed block was last placed at, to forget the older entries of a block placed again. */
    private final Map<Long, Long> placedAt = new HashMap<>();
    private final List<Defense> defenses = new ArrayList<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();
    private final Map<UUID, Integer> deaths = new HashMap<>();
    private int colours;

    FfaGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides, boolean arena) {
        super(plugin, host, instance, modeId, modeName, sides, arena ? plugin.arenaCatalog() : plugin.catalog());
        this.arena = arena;
        for (TeamColor color : instance.map().completeTeams()) {
            islands.add(new Team(color, instance.map().teams().get(color)));
        }
        colours = teams.size();
    }

    /** True while one more player can join this game. */
    @Override
    boolean joinable() {
        return phase != Phase.ENDED && present().size() < MAX_PLAYERS;
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
        broadcast(gamer.coloured() + ChatColor.GRAY + " joined the " + (arena ? "arena" : "FFA") + ".", null);
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
        return RESPAWN_SECONDS;
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

    /** The island farthest from the other players, so that nobody comes back in the middle of a fight. */
    @Override
    Location spawnOf(Gamer gamer) {
        World world = instance.world();
        List<Location> spawns = new ArrayList<>();
        for (Team island : islands) {
            spawns.add(island.points.spawn.in(world));
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

    @Override
    void welcome(Player player, Gamer gamer) {
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + modeName + ChatColor.GREEN + " on " + ChatColor.WHITE
                + instance.map().world() + ChatColor.GREEN + ": everyone for themselves.");
        if (arena) {
            player.sendMessage(ChatColor.GRAY + "Iron and gold at the forges, diamonds and emeralds in the middle, and a "
                    + "shop of its own: the diamonds buy Sharpness, Power II, Strength... Your armour stays; the beds have "
                    + "a defense ready for top fights.");
        } else {
            player.sendMessage(ChatColor.GRAY + "Your kit (arranged in the lobby's kit editor) comes back at each life, "
                    + "and a kill heals you.");
        }
        player.sendMessage(ChatColor.GRAY + "Your blocks go after " + DECAY_SECONDS + " seconds. /spawn takes you back "
                + "to the lobby.");
    }

    /** FFA: the mode's kit and iron armour at each life. Arena: the start of a Bed Wars life. */
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
            for (ItemStack item : new ItemStack[] {new ItemStack(Material.IRON_SWORD), new ItemStack(Material.WOOL, 64),
                new ItemStack(Material.SHEARS), new ItemStack(Material.GOLDEN_APPLE, 2)}) {
                inventory.addItem(kitItem(item, gamer));
            }
        }
        gamer.armor = 2;
        wearArmor(player, gamer);
        player.updateInventory();
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
        }
    }

    @Override
    void placed(Player player, Block block) {
        long key = Instance.key(block.getX(), block.getY(), block.getZ());
        decaying.addLast(new long[] {key, seconds});
        placedAt.put(key, (long) seconds);
    }

    @Override
    void everySecond() {
        World world = instance.world();
        while (!decaying.isEmpty() && seconds - decaying.peekFirst()[1] >= DECAY_SECONDS) {
            long[] entry = decaying.pollFirst();
            Long last = placedAt.get(entry[0]);
            if (last == null || last != entry[1]) {
                continue;
            }
            placedAt.remove(entry[0]);
            Block block = world.getBlockAt(Instance.xOf(entry[0]), Instance.yOf(entry[0]), Instance.zOf(entry[0]));
            if (instance.isPlaced(block)) {
                instance.restore(block);
            }
        }
        if (arena && seconds % REPAIR_SECONDS == 0) {
            for (Defense defense : defenses) {
                if (!anyoneNear(defense.bed().get(0).getLocation().add(0.5, 0, 0.5))) {
                    defense.repair();
                }
            }
        }
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
        lines.add(ChatColor.WHITE + (arena ? "Arena on " : "FFA on ") + ChatColor.GREEN + instance.map().world());
        lines.add("");
        List<Gamer> here = present();
        lines.add(ChatColor.WHITE + "Players: " + ChatColor.GREEN + here.size());
        lines.add(ChatColor.WHITE + "Kills: " + ChatColor.GREEN + viewer.kills);
        lines.add(ChatColor.WHITE + "Deaths: " + ChatColor.GREEN + deaths.getOrDefault(viewer.id, 0));
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streaks.getOrDefault(viewer.id, 0));
        Gamer top = null;
        for (Gamer gamer : here) {
            if (gamer.kills > 0 && (top == null || gamer.kills > top.kills)) {
                top = gamer;
            }
        }
        if (top != null) {
            lines.add("");
            String name = top.name.length() > 16 ? top.name.substring(0, 16) : top.name;
            lines.add(ChatColor.WHITE + "Top: " + ChatColor.GOLD + name + ChatColor.GRAY + " " + top.kills);
        }
        lines.add("");
        lines.add(ChatColor.GRAY + (arena ? "Top fights on the beds" : "Blocks go after " + DECAY_SECONDS + " s"));
        return lines;
    }
}
