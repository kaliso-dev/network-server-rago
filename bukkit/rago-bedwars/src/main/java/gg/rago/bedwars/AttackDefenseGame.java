package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Collections;
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
 *       with one life, iron armour and the attack kit they chose (Rusher, Breaker or Ninja by
 *       default, see {@link RoundKit}). They place blocks and throw fireballs as in a game.
 *   <li>The defenders start in their base with the kit of a game's start (a wooden sword) in iron
 *       armour, its forge and its item shop, without upgrades, and the defense kit they chose
 *       (Guardian, Archer or Builder) at their first life of the round; they come back while their
 *       bed stands, as in a game (the tools of the kit stay), and a death after it is a final kill.
 * </ul>
 *
 * Before each round, a menu shows each player the kits of their side; the countdown is
 * {@value #COUNTDOWN} seconds, 3 once everyone has chosen. /kit opens the menu again.
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
    static final int COUNTDOWN = 10;
    /** The countdown once every player has chosen their kit. */
    static final int READY_COUNTDOWN = 3;
    /** Between the end of a round and the next one, and before the end of the game. */
    private static final long BETWEEN_TICKS = 80L;
    /** The armour of both sides at the start: iron (the tier of Gamer#armor), as bought in a game. */
    static final int ARMOUR = 2;
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
    /** The defenders who had the items of their kit this round (at their first life). */
    private final Set<UUID> stocked = new HashSet<>();
    /** The players who chose their kit in the menu this round. */
    private final Set<UUID> chose = new HashSet<>();

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

    /** The defense of the map on the defenders' bed becomes breakable (see {@link Defense#free}). How many blocks. */
    int freeDefense() {
        if (defenders.points == null || defenders.points.bed == null) {
            return 0;
        }
        return Defense.free(instance, defenders.points.bed.blockIn(instance.world()));
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

    /** The kit this player chose for their role this round. */
    RoundKit kitOf(Gamer gamer) {
        boolean attack = gamer.team == attackers;
        List<RoundKit> kits = settings.roundKits(attack);
        return kits.get(Math.max(0, Math.min(kits.size() - 1, host.roundKit(gamer.id, attack))));
    }

    /**
     * The attackers: their kit and iron armour (one life). The defenders: a game's start in iron
     * armour, with the tools of their kit (kept, as bought), and the rest of their kit at their
     * first life of the round; its sword takes the place of the wooden one.
     */
    @Override
    void giveKit(Player player, Gamer gamer) {
        gamer.armor = Math.max(gamer.armor, ARMOUR);
        List<RoundKit.Entry> kit = kitOf(gamer).entries(catalog());
        PlayerInventory inventory = player.getInventory();
        if (gamer.team != attackers) {
            boolean first = stocked.add(gamer.id);
            if (first) {
                for (RoundKit.Entry entry : kit) {
                    keepTool(gamer, entry.item);
                }
            }
            super.giveKit(player, gamer);
            if (first) {
                for (RoundKit.Entry entry : kit) {
                    if (isTool(entry.item)) {
                        continue;
                    }
                    if (entry.item.kind() == ShopItem.Kind.SWORD) {
                        inventory.remove(Material.WOOD_SWORD);
                    }
                    give(player, gamer, amount(stack(entry.item, gamer), entry.amount), entry.item.category());
                }
            }
            player.updateInventory();
            return;
        }
        inventory.clear();
        wearArmor(player, gamer);
        ItemStack[] layout = lobbyKit(player, gamer);
        for (RoundKit.Entry entry : kit) {
            putKitItem(player, gamer, layout, amount(stack(entry.item, gamer), entry.amount), entry.item.category());
        }
        player.updateInventory();
    }

    private static ItemStack amount(ItemStack stack, int amount) {
        stack.setAmount(amount);
        return stack;
    }

    private static boolean isTool(ShopItem item) {
        return item.kind() == ShopItem.Kind.PICKAXE || item.kind() == ShopItem.Kind.AXE || item.kind() == ShopItem.Kind.SHEARS;
    }

    /** A pickaxe, an axe or shears of the kit become the defender's, as if bought: back at each life. */
    private void keepTool(Gamer gamer, ShopItem item) {
        if (item.kind() == ShopItem.Kind.PICKAXE) {
            gamer.pickaxe = Math.max(gamer.pickaxe, catalog().pickaxes().indexOf(item));
        } else if (item.kind() == ShopItem.Kind.AXE) {
            gamer.axe = Math.max(gamer.axe, catalog().axes().indexOf(item));
        } else if (item.kind() == ShopItem.Kind.SHEARS) {
            gamer.shears = true;
        }
    }

    /**
     * An item of the kit where the player put it in the lobby's kit editor (the first free slot
     * holding the same item there, or a sword, a pickaxe, an axe of another tier), else where
     * their Hypixel hotbar wants it.
     */
    private static void putKitItem(Player player, Gamer gamer, ItemStack[] layout, ItemStack item,
            ShopItem.Category category) {
        PlayerInventory inventory = player.getInventory();
        if (layout != null) {
            for (int slot = 0; slot < layout.length && slot < inventory.getSize(); slot++) {
                ItemStack there = inventory.getItem(slot);
                if (layout[slot] != null && sameKind(layout[slot].getType(), item.getType())
                        && (there == null || there.getType() == Material.AIR)) {
                    inventory.setItem(slot, item);
                    return;
                }
            }
        }
        give(player, gamer, item, category);
    }

    /** The same item, or two swords, two pickaxes, two axes: the slot of one in the kit editor fits the other. */
    static boolean sameKind(Material a, Material b) {
        if (a == b) {
            return true;
        }
        for (String tool : new String[] {"_SWORD", "_PICKAXE", "_AXE"}) {
            if (a.name().endsWith(tool) && b.name().endsWith(tool)) {
                return !tool.equals("_AXE") || !a.name().endsWith("_PICKAXE") && !b.name().endsWith("_PICKAXE");
            }
        }
        return false;
    }

    // --- The kits menu --------------------------------------------------------------------------

    /** Opens the menu of the kits of the player's role this round; false if they do not play. */
    boolean openKitMenu(Player player) {
        Gamer gamer = gamers.get(player.getUniqueId());
        if (gamer == null || host.gameOf(gamer.id) != this || phase == Phase.ENDED) {
            return false;
        }
        boolean attack = gamer.team == attackers;
        KitMenu.open(player, this, attack, settings.roundKits(attack), host.roundKit(gamer.id, attack), catalog());
        return true;
    }

    /**
     * The player chose a kit (attack: in the menu of the attack): before the fight, they have it at
     * once; during the round, from their next round of that role. Everyone ready: the countdown
     * goes on from {@value #READY_COUNTDOWN} seconds.
     */
    void chooseKit(Player player, boolean attack, int index) {
        Gamer gamer = gamers.get(player.getUniqueId());
        if (gamer == null || host.gameOf(gamer.id) != this || phase == Phase.ENDED
                || index < 0 || index >= settings.roundKits(attack).size()) {
            return;
        }
        host.chooseRoundKit(gamer.id, attack, index);
        String name = ChatColor.stripColor(settings.roundKits(attack).get(index).name);
        if ((gamer.team == attackers) != attack) {
            player.sendMessage(ChatColor.YELLOW + "Your " + (attack ? "attack" : "defense") + " kit: " + ChatColor.GREEN
                    + name + ChatColor.YELLOW + ", for your next round " + (attack ? "in attack." : "in defense."));
            return;
        }
        if (phase == Phase.STARTING) {
            chose.add(gamer.id);
            stocked.remove(gamer.id);
            gamer.pickaxe = -1;
            gamer.axe = -1;
            gamer.shears = false;
            giveKit(player, gamer);
            player.sendMessage(ChatColor.YELLOW + "Your kit: " + ChatColor.GREEN + name + ChatColor.YELLOW + ".");
            player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1f, 1f);
            if (everyoneChose()) {
                shortenCountdown(READY_COUNTDOWN);
            }
        } else {
            player.sendMessage(ChatColor.YELLOW + "Your kit: " + ChatColor.GREEN + name + ChatColor.YELLOW
                    + ", from your next round " + (attack ? "in attack." : "in defense."));
        }
        sidebars();
    }

    /** True if every player still here chose their kit this round. */
    private boolean everyoneChose() {
        for (Gamer gamer : present()) {
            if (!chose.contains(gamer.id)) {
                return false;
            }
        }
        return true;
    }

    /** The menu of the kits opens by itself before the round, and a line of the chat opens it again. */
    private void offerKits(final Player player) {
        net.md_5.bungee.api.chat.TextComponent text = new net.md_5.bungee.api.chat.TextComponent(ChatColor.GRAY
                + "Your kit: " + ChatColor.WHITE + ChatColor.stripColor(kitOf(gamers.get(player.getUniqueId())).name)
                + ChatColor.GRAY + ". ");
        net.md_5.bungee.api.chat.TextComponent link = new net.md_5.bungee.api.chat.TextComponent(ChatColor.GREEN + ""
                + ChatColor.BOLD + "[CHANGE KIT]");
        link.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                "/kit"));
        player.spigot().sendMessage(text, link);
        later(1L, () -> {
            if (player.isOnline() && host.gameOf(player.getUniqueId()) == this && phase == Phase.STARTING) {
                openKitMenu(player);
            }
        });
    }

    /** The menus of the kits still open close when the fight starts. */
    private void closeKitMenus() {
        for (Gamer gamer : present()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null && player.getOpenInventory() != null
                    && player.getOpenInventory().getTopInventory().getHolder() instanceof KitMenu.Holder) {
                player.closeInventory();
            }
        }
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
        offerKits(player);
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
        closeKitMenus();
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
        stocked.clear();
        chose.clear();
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
        countdownLeft = COUNTDOWN;
        roundCountdown = repeat(0L, 20L, new Runnable() {
            @Override
            public void run() {
                if (phase == Phase.ENDED) {
                    cancel(roundCountdown);
                    return;
                }
                int left = countdownLeft;
                if (left > 0) {
                    title(ChatColor.RED + "" + ChatColor.BOLD + left, "");
                    if (left <= 5 || left == COUNTDOWN) {
                        broadcast(ChatColor.YELLOW + "The round starts in " + ChatColor.RED + left + ChatColor.YELLOW + "...",
                                Sound.NOTE_STICKS);
                    }
                    countdownLeft--;
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
        // nine lines at most: the lobby adds the pings below
        lines.add((viewer.team == attackers ? ChatColor.RED + "Attack" : ChatColor.GREEN + "Defense") + ChatColor.WHITE
                + " kit: " + ChatColor.YELLOW + ChatColor.stripColor(kitOf(viewer).name));
        return lines;
    }
}
