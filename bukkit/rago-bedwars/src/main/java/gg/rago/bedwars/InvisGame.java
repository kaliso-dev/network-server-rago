package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Invis practice: 2 to 6 players on a Bed Wars map, in rounds of two minutes. Each round, one
 * player is the invisible: on their island, invisible the whole round, with the mode's kit (the
 * lobby's kit editor) and no armour, which would show (Resistance II in its place, as the hidden
 * armour of Hypixel). The others defend the bed of the next island, under a defense of end stone
 * and wool, with iron armour; four at most: a sixth player watches that round. The invisible wins
 * the round by breaking the bed, the defenders by killing the invisible (one life) or by holding
 * two minutes; a defender comes back three seconds after a death. Each player is the invisible in
 * turn, twice each with two or three players. Breaking the bed, or killing the invisible, gives a
 * point; the most points win.
 *
 * <p>The game waits for its players on its map: it starts 20 seconds after the second one joins,
 * 5 seconds once six are in. Main thread only.
 */
final class InvisGame extends BedwarsGame {

    static final int MIN_PLAYERS = 2;
    static final int MAX_PLAYERS = 6;
    static final int MAX_DEFENDERS = 4;
    static final int ROUND_SECONDS = 120;
    private static final int WAIT_SECONDS = 20;
    private static final int FULL_WAIT_SECONDS = 5;
    private static final int ROUND_COUNTDOWN = 5;
    private static final int DEFENDER_RESPAWN = 3;
    /** Between the end of a round and the next one. */
    private static final long BETWEEN_TICKS = 80L;
    private static final Material[] DEFENSE = {Material.ENDER_STONE, Material.WOOL};

    private final Team defenders;
    private final Team attackers;
    /** The invisible of each round, in turn. */
    private final List<UUID> order = new ArrayList<>();
    private final Map<UUID, Integer> points = new HashMap<>();
    /** Each invisible's quickest bed, in seconds. */
    private final Map<UUID, Integer> quickest = new HashMap<>();
    /** Who sits this round out. */
    private final Set<UUID> watching = new HashSet<>();
    private UUID invisible;
    private int round = -1;
    private int roundSeconds;
    private boolean roundOver = true;
    private int waitLeft = -1;
    private BukkitTask waiting;
    private BukkitTask roundCountdown;

    InvisGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides) {
        super(plugin, host, instance, modeId, modeName, sides);
        // two islands side by side: the bed defended, and the island of the invisible
        List<TeamColor> two = instance.map().neighbourTeams(2);
        BedwarsMap map = instance.map();
        teams.clear();
        defenders = new Team(two.get(0), map.teams().get(two.get(0)));
        attackers = new Team(two.get(1), map.teams().get(two.get(1)));
        teams.add(defenders);
        teams.add(attackers);
        for (Gamer gamer : gamers.values()) {
            gamer.team = defenders;
            defenders.members.add(gamer.id);
        }
    }

    // --- Waiting for the players ----------------------------------------------------------------

    /** The game opens: it waits for its players, who look at the map meanwhile. */
    @Override
    void begin() {
        phase = Phase.WAITING;
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null) {
                waitHere(player);
            }
        }
        waiting = repeat(20L, 20L, this::waitingSecond);
        sidebars();
    }

    @Override
    boolean joinable() {
        return phase == Phase.WAITING && round < 0 && present().size() < MAX_PLAYERS;
    }

    @Override
    void join(Player player) {
        UUID id = player.getUniqueId();
        gamers.remove(id);
        defenders.members.remove(id);
        Gamer gamer = newGamer(player, defenders);
        defenders.members.add(id);
        gamers.put(id, gamer);
        host.joined(id, this);
        waitHere(player);
        int count = present().size();
        broadcast(ChatColor.GRAY + player.getName() + " joined the invis practice (" + ChatColor.WHITE + count + "/"
                + MAX_PLAYERS + ChatColor.GRAY + ").", Sound.CLICK);
        if (count >= MAX_PLAYERS && waitLeft > FULL_WAIT_SECONDS) {
            waitLeft = FULL_WAIT_SECONDS;
        }
        sidebars();
    }

    private void waitHere(Player player) {
        prepare(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(instance.map().spectatorPoint().in(instance.world()));
        player.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Invis practice" + ChatColor.LIGHT_PURPLE + " on "
                + ChatColor.WHITE + instance.map().world() + ChatColor.LIGHT_PURPLE + ": " + MIN_PLAYERS + " to " + MAX_PLAYERS
                + " players, rounds of 2 minutes, one invisible against the others, each player in turn.");
        player.sendMessage(ChatColor.GRAY + "It starts " + WAIT_SECONDS + " seconds after the second player joins. Your "
                + "invisible kit is the mode's kit of the lobby's kit editor. /spawn takes you back to the lobby.");
    }

    private void waitingSecond() {
        if (phase != Phase.WAITING || round >= 0) {
            return;
        }
        int count = present().size();
        if (count < MIN_PLAYERS) {
            if (waitLeft >= 0) {
                waitLeft = -1;
                broadcast(ChatColor.YELLOW + "Not enough players any more: waiting for another one.", null);
            }
            sidebars();
            return;
        }
        if (waitLeft < 0) {
            waitLeft = count >= MAX_PLAYERS ? FULL_WAIT_SECONDS : WAIT_SECONDS;
        }
        if (waitLeft == 0) {
            startRounds();
            return;
        }
        if (waitLeft <= 5 || waitLeft % 10 == 0) {
            broadcast(ChatColor.YELLOW + "The invis practice starts in " + ChatColor.RED + waitLeft + ChatColor.YELLOW
                    + " second" + (waitLeft > 1 ? "s" : "") + "!", Sound.NOTE_STICKS);
        }
        waitLeft--;
        sidebars();
    }

    /** Everyone is in: the order of the invisibles, and the first round. */
    private void startRounds() {
        cancel(waiting);
        waiting = null;
        List<UUID> ids = new ArrayList<>();
        for (Gamer gamer : new ArrayList<>(gamers.values())) {
            if (present(gamer)) {
                ids.add(gamer.id);
                points.put(gamer.id, 0);
            } else {
                gamers.remove(gamer.id);
            }
        }
        Collections.shuffle(ids);
        int laps = ids.size() <= 3 ? 2 : 1;
        for (int lap = 0; lap < laps; lap++) {
            order.addAll(ids);
        }
        round = 0;
        assignRoles();
        // the game itself: the defense (setUp), everyone in place, the countdown, then the round
        super.begin();
    }

    // --- Rounds ---------------------------------------------------------------------------------

    /** The invisible of the round, the defenders (four at most), and who watches. */
    private void assignRoles() {
        invisible = order.get(round);
        defenders.members.clear();
        attackers.members.clear();
        defenders.bed = true;
        watching.clear();
        List<Gamer> here = present();
        int extra = Math.max(0, here.size() - 1 - MAX_DEFENDERS);
        // the next invisibles watch, when there are too many defenders: they see how it is done
        for (int i = round + 1; i < order.size() && watching.size() < extra; i++) {
            UUID next = order.get(i);
            if (!next.equals(invisible) && gamers.containsKey(next) && present(gamers.get(next))) {
                watching.add(next);
            }
        }
        for (Gamer gamer : here) {
            gamer.eliminated = false;
            gamer.respawning = false;
            cancel(gamer.respawn);
            gamer.respawn = null;
            gamer.lastDamager = null;
            if (gamer.id.equals(invisible)) {
                gamer.team = attackers;
                attackers.members.add(gamer.id);
            } else if (watching.contains(gamer.id) || defenders.members.size() >= MAX_DEFENDERS) {
                watching.add(gamer.id);
                gamer.team = defenders;
                gamer.eliminated = true;
            } else {
                gamer.team = defenders;
                defenders.members.add(gamer.id);
            }
        }
    }

    /** The defense of end stone and wool on the bed, made again for each round. */
    @Override
    void setUp() {
        Defense.build(instance, defenders.points.bed.blockIn(instance.world()), DEFENSE, defenders.color.woolData());
    }

    @Override
    boolean economy() {
        return false;
    }

    @Override
    int countdownSeconds() {
        return ROUND_COUNTDOWN;
    }

    @Override
    int respawnSeconds() {
        return DEFENDER_RESPAWN;
    }

    /** The first round's countdown is over. */
    @Override
    void started() {
        roundStarted();
    }

    private void roundStarted() {
        roundSeconds = 0;
        roundOver = false;
        Gamer gamer = gamers.get(invisible);
        broadcast(ChatColor.LIGHT_PURPLE + "Round " + (round + 1) + "/" + order.size() + ": "
                + (gamer == null ? "the invisible" : gamer.coloured()) + ChatColor.LIGHT_PURPLE
                + " is invisible. Two minutes!", Sound.NOTE_PLING);
    }

    @Override
    void place(Player player, Gamer gamer) {
        if (watching.contains(gamer.id)) {
            prepare(player);
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(instance.map().spectatorPoint().in(instance.world()));
            return;
        }
        super.place(player, gamer);
        if (gamer.id.equals(invisible)) {
            invisible(player);
        }
    }

    /** Invisible the whole round, without particles; Resistance II for the armour that would show. */
    private static void invisible(Player player) {
        int ticks = (ROUND_SECONDS + ROUND_COUNTDOWN + 10) * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, ticks, 0, false, false), true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, ticks, 1, false, false), true);
    }

    @Override
    void giveKit(Player player, Gamer gamer) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        if (gamer.id.equals(invisible)) {
            ItemStack[] kit = lobbyKit(player, gamer);
            if (kit != null) {
                inventory.setContents(kit);
            } else {
                for (ItemStack item : new ItemStack[] {new ItemStack(Material.IRON_SWORD), new ItemStack(Material.WOOL, 64),
                    new ItemStack(Material.SHEARS), new ItemStack(Material.IRON_PICKAXE),
                    new ItemStack(Material.GOLDEN_APPLE, 2), new ItemStack(Material.FIREBALL, 2)}) {
                    inventory.addItem(kitItem(item, gamer));
                }
            }
            // no armour: it would show the invisible
            inventory.setArmorContents(null);
        } else {
            gamer.armor = 2;
            wearArmor(player, gamer);
            give(player, gamer, kitItem(new ItemStack(Material.IRON_SWORD), gamer), ShopItem.Category.MELEE);
            give(player, gamer, kitItem(new ItemStack(Material.WOOL, 64), gamer), ShopItem.Category.BLOCKS);
            give(player, gamer, kitItem(new ItemStack(Material.SHEARS), gamer), ShopItem.Category.TOOLS);
            give(player, gamer, new ItemStack(Material.GOLDEN_APPLE, 2), ShopItem.Category.UTILITY);
            give(player, gamer, new ItemStack(Material.FIREBALL, 1), ShopItem.Category.UTILITY);
        }
        player.updateInventory();
    }

    @Override
    void welcome(Player player, Gamer gamer) {
        String head = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Round " + (round + 1) + "/" + order.size()
                + ChatColor.LIGHT_PURPLE + ": ";
        Gamer invisibleGamer = gamers.get(invisible);
        String who = invisibleGamer == null ? "the invisible" : invisibleGamer.coloured();
        if (gamer.id.equals(invisible)) {
            player.sendMessage(head + "you are the " + ChatColor.WHITE + "invisible" + ChatColor.LIGHT_PURPLE
                    + ". Break the bed of the " + defenders.color.coloured() + ChatColor.LIGHT_PURPLE
                    + " island next to yours in two minutes. One life; no armour, it would show: Resistance II instead.");
            player.sendTitle(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "INVISIBLE",
                    ChatColor.WHITE + "Break the " + defenders.color.coloured() + ChatColor.WHITE + " bed in 2 minutes");
        } else if (watching.contains(gamer.id)) {
            player.sendMessage(head + "four defenders at most: you watch this round, where " + who + ChatColor.LIGHT_PURPLE
                    + " is invisible.");
            player.sendTitle(ChatColor.GRAY + "" + ChatColor.BOLD + "WATCHING", ChatColor.WHITE + "Your turn comes next");
        } else {
            player.sendMessage(head + "defend your bed against " + who + ChatColor.LIGHT_PURPLE
                    + ", invisible: kill them, or hold two minutes. You come back " + DEFENDER_RESPAWN
                    + " seconds after a death.");
            player.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "DEFEND", who + ChatColor.WHITE + " is invisible");
        }
    }

    /** The invisible has one life; the defenders come back while the round lasts. */
    @Override
    boolean finalDeath(Gamer gamer) {
        return gamer.id.equals(invisible);
    }

    @Override
    void outOfGame(Player victim, Gamer gamer) {
        victim.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "YOU DIED!", ChatColor.GRAY + "The defenders win the round.");
    }

    /** Only the defended bed counts: the others are part of the map. */
    @Override
    Team bedTeam(Block block) {
        for (Block half : Instance.bedOf(defenders.points.bed.blockIn(instance.world()))) {
            if (half.equals(block)) {
                return defenders;
            }
        }
        return null;
    }

    @Override
    void breakBed(Player breaker, Team team) {
        if (roundOver || team != defenders || !defenders.bed) {
            return;
        }
        super.breakBed(breaker, team);
        Gamer gamer = gamers.get(breaker.getUniqueId());
        if (gamer != null && gamer.id.equals(invisible)) {
            points.merge(gamer.id, 1, Integer::sum);
            Integer best = quickest.get(gamer.id);
            if (best == null || roundSeconds < best) {
                quickest.put(gamer.id, roundSeconds);
            }
            roundEnd(true, gamer.coloured() + ChatColor.GREEN + " broke the bed in " + roundSeconds + " seconds!");
        }
    }

    @Override
    void died(Gamer victim, Gamer author, boolean finalKill) {
        if (roundOver || !victim.id.equals(invisible)) {
            return;
        }
        if (author != null) {
            points.merge(author.id, 1, Integer::sum);
            roundEnd(false, author.coloured() + ChatColor.GREEN + " killed the invisible!");
        } else {
            roundEnd(false, victim.coloured() + ChatColor.GREEN + " fell: the defenders win the round.");
        }
    }

    @Override
    void events() {
    }

    @Override
    void everySecond() {
        if (roundOver) {
            return;
        }
        roundSeconds++;
        int left = ROUND_SECONDS - roundSeconds;
        if (left <= 0) {
            roundEnd(false, ChatColor.GREEN + "The bed held for two minutes: the defenders win the round.");
        } else if (left == 60 || left == 30 || left == 10) {
            broadcast(ChatColor.YELLOW + "" + left + " seconds left in the round.", Sound.NOTE_STICKS);
        }
    }

    /** The round is over: the result, then the next round a few seconds later. */
    private void roundEnd(boolean invisibleWon, String message) {
        if (roundOver) {
            return;
        }
        roundOver = true;
        // nobody fights or builds any more, but everyone moves as they like
        phase = Phase.WAITING;
        broadcast(message, invisibleWon ? Sound.ENDERDRAGON_GROWL : Sound.LEVEL_UP);
        for (Gamer gamer : present()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player == null) {
                continue;
            }
            boolean won = gamer.id.equals(invisible) == invisibleWon;
            if (!watching.contains(gamer.id)) {
                player.sendTitle(won ? ChatColor.GOLD + "" + ChatColor.BOLD + "ROUND WON" : ChatColor.RED + "" + ChatColor.BOLD
                        + "ROUND LOST", "");
            }
            if (gamer.id.equals(invisible)) {
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                player.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
            }
        }
        later(BETWEEN_TICKS, this::nextRound);
        sidebars();
    }

    private void nextRound() {
        if (phase == Phase.ENDED) {
            return;
        }
        round++;
        while (round < order.size() && !(gamers.containsKey(order.get(round)) && present(gamers.get(order.get(round))))) {
            round++;
        }
        if (round >= order.size() || present().size() < MIN_PLAYERS) {
            finish();
            return;
        }
        // the map as it was: no block placed, the bed back, nothing on the ground
        instance.rollback();
        removeUnusedBeds();
        assignRoles();
        setUp();
        phase = Phase.STARTING;
        for (Gamer gamer : present()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null) {
                place(player, gamer);
                welcome(player, gamer);
            }
        }
        roundCountdown = repeat(0L, 20L, new Runnable() {
            private int left = ROUND_COUNTDOWN;

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

    @Override
    void left(Gamer gamer) {
        watching.remove(gamer.id);
        defenders.members.remove(gamer.id);
        attackers.members.remove(gamer.id);
        if (phase != Phase.PLAYING || roundOver) {
            return;
        }
        if (gamer.id.equals(invisible)) {
            roundEnd(false, ChatColor.YELLOW + "The invisible left: next round.");
            return;
        }
        boolean defended = false;
        for (UUID id : defenders.members) {
            Gamer defender = gamers.get(id);
            if (defender != null && present(defender)) {
                defended = true;
            }
        }
        if (!defended) {
            roundEnd(true, ChatColor.YELLOW + "No defender left: next round.");
        }
    }

    @Override
    void checkEnd() {
        if (phase == Phase.ENDED) {
            return;
        }
        List<Gamer> here = present();
        if (here.isEmpty()) {
            close();
        } else if (round >= 0 && here.size() < MIN_PLAYERS) {
            broadcast(ChatColor.YELLOW + "Not enough players left: the invis practice ends.", null);
            finish();
        }
    }

    /** The last round is over: the points, the winners. */
    private void finish() {
        if (phase == Phase.ENDED) {
            return;
        }
        phase = Phase.ENDED;
        stopTasks();
        List<Gamer> here = present();
        List<Gamer> ranking = new ArrayList<>();
        for (UUID id : points.keySet()) {
            Gamer gamer = gamers.get(id);
            if (gamer != null) {
                ranking.add(gamer);
            }
        }
        ranking.sort((a, b) -> {
            int byPoints = Integer.compare(points.getOrDefault(b.id, 0), points.getOrDefault(a.id, 0));
            if (byPoints != 0) {
                return byPoints;
            }
            return Integer.compare(quickest.getOrDefault(a.id, Integer.MAX_VALUE), quickest.getOrDefault(b.id, Integer.MAX_VALUE));
        });
        int most = ranking.isEmpty() ? 0 : points.getOrDefault(ranking.get(0).id, 0);
        List<UUID> winners = new ArrayList<>();
        List<UUID> losers = new ArrayList<>();
        for (Gamer gamer : here) {
            if (most > 0 && points.getOrDefault(gamer.id, 0) == most) {
                winners.add(gamer.id);
            } else {
                losers.add(gamer.id);
            }
        }
        if (!winners.isEmpty()) {
            plugin.lobby().recordMatch(modeId, winners, losers);
        }
        List<String> summary = new ArrayList<>();
        summary.add(ChatColor.GREEN + "" + ChatColor.STRIKETHROUGH + "                                        ");
        summary.add(ChatColor.WHITE + "" + ChatColor.BOLD + "  Invis practice" + ChatColor.GRAY + " - " + instance.map().world());
        for (int i = 0; i < ranking.size(); i++) {
            Gamer gamer = ranking.get(i);
            int score = points.getOrDefault(gamer.id, 0);
            Integer bed = quickest.get(gamer.id);
            summary.add(ChatColor.YELLOW + "  " + (i + 1) + ". " + gamer.coloured() + ChatColor.GRAY + " - " + ChatColor.WHITE
                    + score + " point" + (score == 1 ? "" : "s") + (bed == null ? "" : ChatColor.GRAY + " (quickest bed: "
                    + bed + " s)"));
        }
        summary.add(ChatColor.GREEN + "" + ChatColor.STRIKETHROUGH + "                                        ");
        for (Gamer gamer : here) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player == null) {
                continue;
            }
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            for (String line : summary) {
                player.sendMessage(line);
            }
            if (winners.contains(gamer.id)) {
                player.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "VICTORY!", "");
                player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);
            } else {
                player.sendTitle(winners.isEmpty() ? ChatColor.YELLOW + "" + ChatColor.BOLD + "DRAW"
                        : ChatColor.RED + "" + ChatColor.BOLD + "GAME OVER!", "");
            }
        }
        Bukkit.getScheduler().runTask(plugin, this::backToLobby);
    }

    @Override
    List<String> lines(Gamer viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.LIGHT_PURPLE + "Invis practice " + ChatColor.GRAY + instance.map().world());
        lines.add("");
        if (round < 0) {
            lines.add(ChatColor.WHITE + "Players: " + ChatColor.GREEN + present().size() + ChatColor.GRAY + "/" + MAX_PLAYERS);
            lines.add(waitLeft >= 0 ? ChatColor.WHITE + "Starts in " + ChatColor.GREEN + waitLeft + " s"
                    : ChatColor.GRAY + "Waiting for players");
            return lines;
        }
        lines.add(ChatColor.WHITE + "Round: " + ChatColor.GREEN + (round + 1) + ChatColor.GRAY + "/" + order.size());
        lines.add(ChatColor.WHITE + "Time left: " + ChatColor.GREEN + clock(Math.max(0, ROUND_SECONDS - roundSeconds)));
        Gamer invisibleGamer = gamers.get(invisible);
        if (invisibleGamer != null) {
            lines.add(ChatColor.WHITE + "Invisible: " + invisibleGamer.coloured());
        }
        String role = viewer.id.equals(invisible) ? ChatColor.LIGHT_PURPLE + "Invisible"
                : watching.contains(viewer.id) ? ChatColor.GRAY + "Watching" : ChatColor.GREEN + "Defender";
        lines.add(ChatColor.WHITE + "You: " + role);
        lines.add("");
        List<Gamer> ranking = new ArrayList<>(present());
        ranking.sort((a, b) -> Integer.compare(points.getOrDefault(b.id, 0), points.getOrDefault(a.id, 0)));
        for (int i = 0; i < ranking.size() && i < 5; i++) {
            Gamer gamer = ranking.get(i);
            String name = gamer.name.length() > 16 ? gamer.name.substring(0, 16) : gamer.name;
            lines.add(ChatColor.WHITE + name + ": " + ChatColor.GREEN + points.getOrDefault(gamer.id, 0));
        }
        return lines;
    }
}
