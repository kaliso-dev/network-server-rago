package gg.rago.bedwars;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

/**
 * Attack and Defense, on the owner's maps of it (Bio Hazard first): one base and its bed, the
 * defenders' (gray on Bio Hazard), and the bridge the attackers come by (pink), nothing else. 1v1
 * or 2v2, in {@value #ROUNDS} rounds: each side attacks once, the roles swap at the second round,
 * the map made as it was.
 *
 * <ul>
 *   <li>The attackers start at the end of their bridge (attaquants: in the map's bedwars.yml),
 *       with one life, iron armour and the mode's kit of the lobby (a stone sword, a stack of
 *       wool, 2 fireballs, the Golden Pickaxe of the shop, Efficiency III and Sharpness II, and
 *       shears; three kits to choose from will come). They place blocks and throw fireballs as in a
 *       game.
 *   <li>The defenders start in their base with the kit of a game's start (a wooden sword) in iron
 *       armour, its forge and its item shop, without upgrades; they come back while their bed
 *       stands, and a death after it is a final kill, as in a game.
 * </ul>
 *
 * A defense the map has on the bed (Bio Hazard's end stone and wool, captured with the map) is
 * the defenders' own, as in a game: the players can break it, and it is whole again each round.
 *
 * <p>The attackers win the round by breaking the bed and killing every defender; the defenders by
 * killing every attacker, or by holding {@value #ROUND_SECONDS} seconds. The side with more rounds
 * wins; one each, the quicker of two successful attacks wins, else it is a draw. Main thread only.
 */
final class AttackDefenseGame extends BedwarsGame {

    static final int ROUNDS = 2;
    static final int ROUND_SECONDS = 300;
    private static final int COUNTDOWN = 5;
    /** Between the end of a round and the next one, and before the end of the game. */
    private static final long BETWEEN_TICKS = 80L;
    /** The attackers' kit when the lobby has none for the mode: shop items, with their amount. */
    static final String[] ATTACKER_KIT = {"stone_sword", "wool 64", "fireball 2", "golden_pickaxe", "shears"};
    /** The armour of both sides at the start: iron (the tier of Gamer#armor), as bought in a game. */
    static final int ARMOUR = 2;
    /** The attackers' pickaxe, whatever the kit holds: the Golden Pickaxe of the shop (its tier). */
    static final int ATTACKER_PICKAXE = 2;
    /** What a defense of the map on the bed is made of: the blocks of a game, which players may break. */
    private static final Set<Material> DEFENSE_BLOCKS = EnumSet.of(Material.WOOL, Material.ENDER_STONE, Material.WOOD,
            Material.STAINED_GLASS, Material.GLASS, Material.STAINED_CLAY, Material.HARD_CLAY, Material.OBSIDIAN,
            Material.LADDER);
    /** How far from the bed, on each axis, a defense of the map goes at most. */
    static final int DEFENSE_REACH = 4;
    private static final BlockFace[] FACES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
        BlockFace.UP, BlockFace.DOWN};
    private static final String CHECK = "✔";
    private static final String CROSS = "✘";

    private final Team defenders;
    private final Team attackers;
    /** The players of each side, the first side first: a side keeps its players, its role changes. */
    private final List<List<UUID>> sideMembers = new ArrayList<>();
    private final int[] wins = new int[2];
    /** How long each side's attack took, in seconds, when it won its round; -1 otherwise. */
    private final int[] attackSeconds = {-1, -1};
    /** What happened in each round, for the summary at the end. */
    private final List<String> results = new ArrayList<>();
    private int round = 1;
    private boolean roundOver;
    private BukkitTask roundCountdown;

    AttackDefenseGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides) {
        super(plugin, host, instance, modeId, modeName, sides);
        defenders = teams.get(0);
        attackers = teams.get(1);
        attackers.bed = false;
        for (Team team : teams) {
            sideMembers.add(new ArrayList<>(team.members));
        }
    }

    /**
     * The defenders' base first (the map's team), then the attackers' colour (attaquants:); on a
     * map of islands without attackers, two of its teams, the second one attacking from its island.
     */
    @Override
    List<TeamColor> teamColors(int count) {
        BedwarsMap map = instance.map();
        if (!map.hasAttackers() || map.completeTeams().isEmpty()) {
            return super.teamColors(count);
        }
        List<TeamColor> colors = new ArrayList<>();
        colors.add(map.completeTeams().get(0));
        colors.add(map.attackerColor());
        return colors;
    }

    /** The side attacking this round: the second one first, then the first one. */
    int attackingSide() {
        return attackingSide(round);
    }

    static int attackingSide(int round) {
        return round % 2 == 1 ? 1 : 0;
    }

    /**
     * The winning side at the end (0, 1), or -1 for a draw: the more rounds won; one each, the
     * quicker of two successful attacks (seconds; -1 for an attack that failed).
     */
    static int winner(int[] wins, int[] attackSeconds) {
        if (wins[0] != wins[1]) {
            return wins[0] > wins[1] ? 0 : 1;
        }
        if (attackSeconds[0] >= 0 && attackSeconds[1] >= 0 && attackSeconds[0] != attackSeconds[1]) {
            return attackSeconds[0] < attackSeconds[1] ? 0 : 1;
        }
        return -1;
    }

    // --- The map --------------------------------------------------------------------------------

    /** Only the defenders' base has its forge and its shop. */
    @Override
    List<Team> islands() {
        return Collections.singletonList(defenders);
    }

    @Override
    boolean upgrades() {
        return false;
    }

    /** No timed event (no diamond, no end of the beds): the round's own clock, see {@link #everySecond}. */
    @Override
    void events() {
    }

    @Override
    int countdownSeconds() {
        return COUNTDOWN;
    }

    /** The defense of the map on the bed is the defenders' own: breakable (after each rollback too). */
    @Override
    void setUp() {
        freeDefense();
    }

    /**
     * The blocks of a game (wool, end stone, wood, glass, clay, obsidian, ladders) joined to the
     * bed, from its height up and within {@value #DEFENSE_REACH} blocks of it, become as if placed
     * by a player: breakable, and the rollback puts them back. How many.
     */
    int freeDefense() {
        if (defenders.points == null || defenders.points.bed == null) {
            return 0;
        }
        List<Block> bed = Instance.bedOf(defenders.points.bed.blockIn(instance.world()));
        if (bed.isEmpty()) {
            return 0;
        }
        Deque<Block> todo = new ArrayDeque<>(bed);
        Set<Block> seen = new HashSet<>(bed);
        int freed = 0;
        while (!todo.isEmpty()) {
            Block block = todo.poll();
            for (BlockFace face : FACES) {
                Block next = block.getRelative(face);
                if (!DEFENSE_BLOCKS.contains(next.getType()) || !nearBed(next, bed) || !seen.add(next)) {
                    continue;
                }
                if (!instance.isPlaced(next)) {
                    instance.markPlaced(next);
                    freed++;
                }
                todo.add(next);
            }
        }
        return freed;
    }

    /** From the bed's height up, within {@value #DEFENSE_REACH} blocks of one of its halves on each axis. */
    private static boolean nearBed(Block block, List<Block> bed) {
        for (Block half : bed) {
            if (block.getY() >= half.getY() && block.getY() - half.getY() <= DEFENSE_REACH
                    && Math.abs(block.getX() - half.getX()) <= DEFENSE_REACH
                    && Math.abs(block.getZ() - half.getZ()) <= DEFENSE_REACH) {
                return true;
            }
        }
        return false;
    }

    /** Only the defended bed counts. */
    @Override
    Team bedTeam(Block block) {
        if (defenders.points == null || defenders.points.bed == null) {
            return null;
        }
        for (Block half : Instance.bedOf(defenders.points.bed.blockIn(instance.world()))) {
            if (half.equals(block)) {
                return defenders;
            }
        }
        return null;
    }

    /** The attackers at the end of their bridge, one behind the other; the defenders on their spawn. */
    @Override
    Location spawnOf(Gamer gamer) {
        List<MapPoint> starts = instance.map().attackerStarts();
        if (gamer.team == attackers && !starts.isEmpty()) {
            int index = Math.max(0, attackers.members.indexOf(gamer.id));
            return starts.get(index % starts.size()).in(instance.world());
        }
        return super.spawnOf(gamer);
    }

    /**
     * The defenders: a game's start in iron armour (and what they bought: diamond armour stays); the
     * attackers: the mode's kit and iron armour.
     */
    @Override
    void giveKit(Player player, Gamer gamer) {
        gamer.armor = Math.max(gamer.armor, ARMOUR);
        if (gamer.team != attackers) {
            super.giveKit(player, gamer);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        wearArmor(player, gamer);
        ItemStack[] kit = lobbyKit(player, gamer);
        if (kit != null) {
            inventory.setContents(kit);
        } else {
            for (String entry : ATTACKER_KIT) {
                String[] parts = entry.split(" ");
                ShopItem item = InvisGame.catalogItem(catalog(), parts[0]);
                if (item != null) {
                    ItemStack stack = stack(item, gamer);
                    stack.setAmount(parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
                    inventory.addItem(stack);
                }
            }
        }
        ItemStack pickaxe = stack(catalog().pickaxes().get(ATTACKER_PICKAXE), gamer);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType().name().endsWith("_PICKAXE")) {
                inventory.setItem(slot, pickaxe.clone());
            }
        }
        player.updateInventory();
    }

    @Override
    void welcome(Player player, Gamer gamer) {
        String head = ChatColor.RED + "" + ChatColor.BOLD + "Round " + round + "/" + ROUNDS + ChatColor.RED + ": ";
        String minutes = ROUND_SECONDS / 60 + " minutes";
        if (gamer.team == attackers) {
            player.sendMessage(head + ChatColor.WHITE + "you attack" + ChatColor.RED + ". Break the "
                    + defenders.color.coloured() + ChatColor.RED + " bed and kill the defenders within " + minutes
                    + ". One life; blocks and fireballs as in a game.");
            player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "ATTACK", ChatColor.WHITE + "Break the bed, kill the defenders");
        } else {
            player.sendMessage(head + ChatColor.WHITE + "you defend" + ChatColor.RED + " your bed: your forge and the item "
                    + "shop are in your base, you come back while your bed stands. Kill the attackers, or hold " + minutes
                    + ".");
            player.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "DEFEND", ChatColor.WHITE + "Hold " + minutes);
        }
        if (round == 1) {
            player.sendMessage(ChatColor.GRAY + "Two rounds: the roles swap at the second. /spawn takes you back to the lobby.");
        }
    }

    @Override
    void outOfGame(Player victim, Gamer gamer) {
        victim.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "YOU DIED!", ChatColor.GRAY + "You watch the end of the round.");
    }

    // --- Rounds ---------------------------------------------------------------------------------

    @Override
    void started() {
        roundStarted();
    }

    private void roundStarted() {
        broadcast(ChatColor.RED + "Round " + round + "/" + ROUNDS + ": " + sideName(attackingSide()) + ChatColor.RED
                + " attacks!", Sound.NOTE_PLING);
        sidebars();
    }

    @Override
    void breakBed(Player breaker, Team team) {
        if (roundOver || team != defenders) {
            return;
        }
        super.breakBed(breaker, team);
        checkEnd();
    }

    @Override
    void everySecond() {
        if (roundOver) {
            return;
        }
        int left = ROUND_SECONDS - seconds;
        if (left <= 0) {
            roundEnd(false, ChatColor.GREEN + "The defense held for " + ROUND_SECONDS / 60 + " minutes!");
        } else if (left == 60 || left == 30 || left == 10) {
            broadcast(ChatColor.YELLOW + "" + left + " seconds left in the round.", Sound.NOTE_STICKS);
        }
    }

    /**
     * A side gone wins the game for the other; in a round, the attackers win it when no defender
     * is left (their bed broken), the defenders when no attacker is left.
     */
    @Override
    void checkEnd() {
        if (phase == Phase.ENDED) {
            return;
        }
        boolean first = sideHere(0);
        boolean second = sideHere(1);
        if (!first && !second) {
            close();
            return;
        }
        if (!first || !second) {
            int side = first ? 0 : 1;
            broadcast(ChatColor.YELLOW + "The other side left: " + sideName(side) + ChatColor.YELLOW + " wins.", null);
            finish(side);
            return;
        }
        if (roundOver || phase != Phase.PLAYING) {
            return;
        }
        if (alive(attackers) == 0) {
            roundEnd(false, ChatColor.GREEN + "The attackers are dead: the defense held!");
        } else if (!defenders.bed && alive(defenders) == 0) {
            roundEnd(true, ChatColor.RED + "Bed broken and defenders dead in " + seconds + " seconds!");
        }
    }

    /** The round is over: the point, then the next round or the end. */
    private void roundEnd(boolean attackersWon, String message) {
        if (roundOver) {
            return;
        }
        roundOver = true;
        // nobody fights or builds any more, but everyone moves as they like
        phase = Phase.WAITING;
        int attacking = attackingSide();
        int winner = attackersWon ? attacking : 1 - attacking;
        wins[winner]++;
        if (attackersWon) {
            attackSeconds[attacking] = seconds;
        }
        results.add(ChatColor.YELLOW + "  Round " + round + ": " + sideName(attacking) + ChatColor.YELLOW + " attacked, "
                + (attackersWon ? ChatColor.GREEN + "won in " + seconds + " s" : ChatColor.RED + "the defense held"));
        broadcast(message + " " + score(), attackersWon ? Sound.ENDERDRAGON_GROWL : Sound.LEVEL_UP);
        for (Gamer gamer : present()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null) {
                boolean won = sideOf(gamer.id) == winner;
                player.sendTitle(won ? ChatColor.GOLD + "" + ChatColor.BOLD + "ROUND WON"
                        : ChatColor.RED + "" + ChatColor.BOLD + "ROUND LOST", score());
            }
        }
        later(BETWEEN_TICKS, round >= ROUNDS ? () -> finish(-2) : this::nextRound);
        sidebars();
    }

    /** The map as it was, the roles swapped, everyone in place, a countdown, and the attack. */
    private void nextRound() {
        if (phase == Phase.ENDED) {
            return;
        }
        round++;
        roundOver = false;
        seconds = 0;
        instance.rollback();
        utilities.clear();
        removeUnusedBeds();
        assignRoles();
        entitiesAgain(islands());
        setUp();
        phase = Phase.STARTING;
        for (Gamer gamer : present()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null) {
                place(player, gamer);
                welcome(player, gamer);
                if (teamNameTags()) {
                    plugin.lobby().setGameNameTag(player, gamer.team.color.letter() + " " + gamer.team.color.chat());
                }
            }
        }
        roundCountdown = repeat(0L, 20L, new Runnable() {
            private int left = COUNTDOWN;

            @Override
            public void run() {
                if (phase == Phase.ENDED) {
                    cancel(roundCountdown);
                    return;
                }
                if (left > 0) {
                    title(ChatColor.RED + "" + ChatColor.BOLD + left, "");
                    broadcast(ChatColor.YELLOW + "The round starts in " + ChatColor.RED + left + ChatColor.YELLOW + "...",
                            Sound.NOTE_STICKS);
                    left--;
                    return;
                }
                cancel(roundCountdown);
                roundCountdown = null;
                phase = Phase.PLAYING;
                title(ChatColor.GREEN + "" + ChatColor.BOLD + "Go!", "");
                roundStarted();
            }
        });
        sidebars();
    }

    /** The roles of the round: each side in its team, fresh, with nothing bought. */
    private void assignRoles() {
        int attacking = attackingSide();
        defenders.members.clear();
        attackers.members.clear();
        defenders.bed = true;
        attackers.bed = false;
        for (int side = 0; side < sideMembers.size(); side++) {
            Team team = side == attacking ? attackers : defenders;
            for (UUID id : sideMembers.get(side)) {
                Gamer gamer = gamers.get(id);
                if (gamer == null) {
                    continue;
                }
                team.members.add(id);
                gamer.team = team;
                cancel(gamer.respawn);
                gamer.respawn = null;
                gamer.respawning = false;
                gamer.eliminated = !present(gamer);
                gamer.lastDamager = null;
                gamer.armor = 0;
                gamer.pickaxe = -1;
                gamer.axe = -1;
                gamer.shears = false;
                gamer.enderChest = null;
                gamer.milkUntil = 0L;
            }
        }
    }

    @Override
    void left(Gamer gamer) {
        // checkEnd follows: a side gone loses, a round left without attacker or defender ends
    }

    /** The last round is over (decided: -2), or a side won by the other leaving: the result. */
    private void finish(int decided) {
        if (phase == Phase.ENDED) {
            return;
        }
        phase = Phase.ENDED;
        stopTasks();
        int winner = decided >= -1 ? decided : winner(wins, attackSeconds);
        List<UUID> winners = new ArrayList<>();
        List<UUID> losers = new ArrayList<>();
        if (winner >= 0) {
            winners.addAll(sideMembers.get(winner));
            losers.addAll(sideMembers.get(1 - winner));
            plugin.lobby().recordMatch(modeId, winners, losers);
        }
        List<String> summary = new ArrayList<>();
        summary.add(ChatColor.GREEN + "" + ChatColor.STRIKETHROUGH + "                                        ");
        summary.add(ChatColor.WHITE + "" + ChatColor.BOLD + "  " + modeName + ChatColor.GRAY + " - " + instance.map().world());
        summary.addAll(results);
        if (winner < 0) {
            summary.add(ChatColor.YELLOW + "  Draw: " + score());
        } else {
            boolean tieBreak = decided == -2 && wins[0] == wins[1];
            summary.add(ChatColor.YELLOW + "  Winner: " + sideName(winner) + ChatColor.YELLOW + " (" + score()
                    + ChatColor.YELLOW + (tieBreak ? ", the quicker attack" : "") + ")");
        }
        summary.add(ChatColor.GREEN + "" + ChatColor.STRIKETHROUGH + "                                        ");
        for (Gamer gamer : present()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player == null) {
                continue;
            }
            for (String line : summary) {
                player.sendMessage(line);
            }
            player.sendMessage(ChatColor.GRAY + "  You: " + gamer.kills + " kills, " + gamer.finalKills + " final kills, "
                    + gamer.beds + " beds broken");
            if (winners.contains(gamer.id)) {
                player.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "VICTORY!", "");
                player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);
            } else {
                player.sendTitle(winner < 0 ? ChatColor.YELLOW + "" + ChatColor.BOLD + "DRAW"
                        : ChatColor.RED + "" + ChatColor.BOLD + "GAME OVER!", "");
            }
        }
        Bukkit.getScheduler().runTask(plugin, this::backToLobby);
    }

    // --- The sides ------------------------------------------------------------------------------

    /** The side of this player (0, 1), or -1. */
    private int sideOf(UUID id) {
        for (int side = 0; side < sideMembers.size(); side++) {
            if (sideMembers.get(side).contains(id)) {
                return side;
            }
        }
        return -1;
    }

    /** True while a player of this side is still in the game. */
    private boolean sideHere(int side) {
        for (UUID id : sideMembers.get(side)) {
            Gamer gamer = gamers.get(id);
            if (gamer != null && present(gamer)) {
                return true;
            }
        }
        return false;
    }

    /** The players of this team still alive in the round (a defender waiting to come back too). */
    private int alive(Team team) {
        int alive = 0;
        for (UUID id : team.members) {
            Gamer gamer = gamers.get(id);
            if (gamer != null && !gamer.eliminated && present(gamer)) {
                alive++;
            }
        }
        return alive;
    }

    /** A side by its players, in the colour of its role this round: "qAbel", "qAbel & Steve". */
    private String sideName(int side) {
        List<String> names = new ArrayList<>();
        ChatColor color = ChatColor.WHITE;
        for (UUID id : sideMembers.get(side)) {
            Gamer gamer = gamers.get(id);
            if (gamer != null) {
                names.add(gamer.name);
                color = gamer.team.color.chat();
            }
        }
        return color + String.join(" & ", names);
    }

    /** "qAbel 1 - 0 Steve". */
    private String score() {
        return sideName(0) + " " + ChatColor.WHITE + wins[0] + ChatColor.GRAY + " - " + ChatColor.WHITE + wins[1] + " "
                + sideName(1);
    }

    @Override
    List<String> lines(Gamer viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.WHITE + "Round " + ChatColor.GREEN + round + ChatColor.GRAY + "/" + ROUNDS + ChatColor.WHITE + " - "
                + (phase == Phase.STARTING ? "Starting..." : ChatColor.GREEN + clock(Math.max(0, ROUND_SECONDS - seconds))));
        lines.add("");
        lines.add(defenders.color.letter() + " " + ChatColor.WHITE + "Bed: " + (defenders.bed ? ChatColor.GREEN + CHECK
                : ChatColor.RED + CROSS));
        lines.add(defenders.color.letter() + " " + ChatColor.WHITE + "Defenders: " + ChatColor.GREEN + alive(defenders));
        lines.add(attackers.color.letter() + " " + ChatColor.WHITE + "Attackers: " + ChatColor.GREEN + alive(attackers));
        lines.add("");
        int mine = sideOf(viewer.id);
        for (int side = 0; side < sideMembers.size(); side++) {
            String name = ChatColor.stripColor(sideName(side));
            if (name.length() > 16) {
                name = name.substring(0, 16);
            }
            lines.add(ChatColor.WHITE + name + ": " + ChatColor.GREEN + wins[side] + (side == mine ? ChatColor.GRAY + " YOU" : ""));
        }
        lines.add("");
        lines.add(ChatColor.WHITE + "You: " + (viewer.team == attackers ? ChatColor.RED + "Attacker" : ChatColor.GREEN + "Defender"));
        lines.add(ChatColor.WHITE + "Kills: " + ChatColor.GREEN + viewer.kills);
        lines.add(ChatColor.WHITE + "Final Kills: " + ChatColor.GREEN + viewer.finalKills);
        return lines;
    }
}
