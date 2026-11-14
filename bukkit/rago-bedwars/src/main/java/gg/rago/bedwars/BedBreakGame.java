package gg.rago.bedwars;

import gg.rago.game.BedDefense;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

/**
 * Bed Break Simulator, a situation of Ranked Bedwars: 2 to 5 players on the map chosen (Archway or
 * Invasion), in rounds of three minutes, the two opposite islands linked by a top bridge (see
 * {@link TopBridge}). Each round, one player attacks and the others defend (four at most):
 *
 * <ul>
 *   <li>The attacker starts in the middle of the top bridge, facing the base to attack, with one
 *       life and the kit they chose (bed-break.kits: in config.yml): Classic (the Golden Pickaxe,
 *       tier III, two stacks of wool), Fireball (a fireball, the Iron Pickaxe, tier II, two stacks)
 *       or Fighter (the Wooden Pickaxe, tier I, a golden apple, ladders, the knockback stick), each
 *       with a stone sword. Only the bed counts: they win the round by breaking it.
 *   <li>The defenders have no kit: a game's start (a wooden sword), their forge full of iron and
 *       gold and their item shop, no upgrades, no diamond nor emerald; the bed under the owner's duo
 *       defense, end stone then wool ({@link BedDefense#DUO}). They come back after a death, and win
 *       the round by killing the attacker or by holding three minutes.
 * </ul>
 *
 * Everyone wears iron armour with Protection II (bed-break.protection), no Sharpness. Each player
 * attacks in turn, twice each with two or three players. Breaking the bed, or killing the
 * attacker, gives a point; the most points win, the quickest bed between two equal.
 *
 * <p>The game waits for its players on its map: it starts 10 seconds after the second one joins,
 * 3 seconds once five are in. Before each round, the menu of the kits opens for the attacker (/kit
 * opens it again, also to choose the kit of a next attack while defending): the round starts
 * {@value #ROUND_COUNTDOWN} seconds after it is set, {@value #READY_COUNTDOWN} once the attacker
 * has chosen. Main thread only.
 */
final class BedBreakGame extends BedwarsGame implements KitMenu.Chooser {

    static final int MIN_PLAYERS = 2;
    static final int MAX_PLAYERS = 5;
    static final int ROUND_SECONDS = 180;
    private static final int WAIT_SECONDS = 10;
    private static final int FULL_WAIT_SECONDS = 3;
    /** The countdown of a round: the time for the attacker to choose their kit. */
    static final int ROUND_COUNTDOWN = 5;
    /** The countdown once the attacker has chosen. */
    static final int READY_COUNTDOWN = 2;
    /** Between the end of a round and the next one. */
    private static final long BETWEEN_TICKS = 20L;
    /** Iron armour for everyone (the tier of {@link Gamer#armor}). */
    static final int ARMOUR_TIER = 2;
    /** The defense on the bed: the owner's duo one, end stone then wool. */
    static final List<String[]> DEFENSE = BedDefense.DUO;
    private static final String CHECK = "✔";
    private static final String CROSS = "✘";

    private final Team defenders;
    private final Team attackers;
    /** The attacker of each round, in turn. */
    private final List<UUID> order = new ArrayList<>();
    private final Map<UUID, Integer> points = new HashMap<>();
    /** Each attacker's quickest bed, in seconds. */
    private final Map<UUID, Integer> quickest = new HashMap<>();
    private UUID attacker;
    private int round = -1;
    private boolean roundOver = true;
    private int waitLeft = -1;
    private BukkitTask waiting;
    private BukkitTask roundCountdown;

    BedBreakGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides) {
        super(plugin, host, instance, modeId, modeName, sides);
        // two opposite islands, as in a game: the bed defended, and the one the top bridge comes from
        List<TeamColor> two = instance.map().playingTeams(2);
        BedwarsMap map = instance.map();
        teams.clear();
        defenders = new Team(two.get(0), map.teams().get(two.get(0)));
        attackers = new Team(two.get(1), map.teams().get(two.get(1)));
        attackers.bed = false;
        teams.add(defenders);
        teams.add(attackers);
        for (Gamer gamer : gamers.values()) {
            gamer.team = defenders;
            defenders.members.add(gamer.id);
        }
    }

    /** The rounds each player attacks: twice with two or three players, once with more. */
    static int laps(int players) {
        return players <= 3 ? 2 : 1;
    }

    /** The players by their points, the most first; between two equal, the quicker bed first. */
    static List<UUID> ranking(Collection<UUID> players, Map<UUID, Integer> points, Map<UUID, Integer> quickest) {
        List<UUID> ranking = new ArrayList<>(players);
        ranking.sort((a, b) -> {
            int byPoints = Integer.compare(points.getOrDefault(b, 0), points.getOrDefault(a, 0));
            if (byPoints != 0) {
                return byPoints;
            }
            return Integer.compare(quickest.getOrDefault(a, Integer.MAX_VALUE), quickest.getOrDefault(b, Integer.MAX_VALUE));
        });
        return ranking;
    }

    /** The kit of this index among so many, the first when it is no longer one of them. */
    static int kitIndex(int chosen, int count) {
        return chosen < 0 || chosen >= count ? 0 : chosen;
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

    /** Not kept for /replay: a practice in rounds whose players come and go. */
    @Override
    boolean recorded() {
        return false;
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
        broadcast(ChatColor.GRAY + player.getDisplayName() + " joined the Bed Break Simulator (" + ChatColor.WHITE + count
                + "/" + MAX_PLAYERS + ChatColor.GRAY + ").", Sound.CLICK);
        if (count >= MAX_PLAYERS && waitLeft > FULL_WAIT_SECONDS) {
            waitLeft = FULL_WAIT_SECONDS;
        }
        sidebars();
    }

    private void waitHere(Player player) {
        prepare(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(instance.map().spectatorPoint().in(instance.world()));
        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Bed Break Simulator" + ChatColor.RED + " on "
                + ChatColor.WHITE + instance.map().world() + ChatColor.RED + ": " + MIN_PLAYERS + " to " + MAX_PLAYERS
                + " players, rounds of 3 minutes. Each player in turn attacks from the top bridge, with one life, and"
                + " has to break the bed; the others defend it with their forge full.");
        player.sendMessage(ChatColor.GRAY + "It starts " + WAIT_SECONDS + " seconds after the second player joins; the"
                + " menu of the attack kits opens before each of your attacks. /spawn takes you back to the lobby.");
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
            broadcast(ChatColor.YELLOW + "The Bed Break Simulator starts in " + ChatColor.RED + waitLeft + ChatColor.YELLOW
                    + " second" + (waitLeft > 1 ? "s" : "") + "!", Sound.NOTE_STICKS);
        }
        waitLeft--;
        sidebars();
    }

    /** Everyone is in: the order of the attackers, and the first round. */
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
        for (int lap = 0; lap < laps(ids.size()); lap++) {
            order.addAll(ids);
        }
        round = 0;
        assignRoles();
        // the game itself: the top bridge, the forge and the shop, the defense (setUp), everyone in
        // place, the countdown, then the round
        super.begin();
    }

    // --- The map --------------------------------------------------------------------------------

    /** The top bridge over the middle, from the island of the attacker to the bed: on every map. */
    @Override
    boolean hasTopBridge() {
        return true;
    }

    /** Only the defenders' island has its forge and its item shop. */
    @Override
    List<Team> islands() {
        return Collections.singletonList(defenders);
    }

    @Override
    boolean upgrades() {
        return false;
    }

    /** No diamond, no emerald: the forge only. */
    @Override
    boolean midGenerators() {
        return false;
    }

    /** No timed event (no generator going up, no end of the beds): the round's own clock, see {@link #everySecond}. */
    @Override
    void events() {
    }

    /** The armour of everyone has the Protection of bed-break.protection (II by default); no Sharpness. */
    @Override
    int armourProtection(Gamer gamer) {
        return settings.bedBreakProtection();
    }

    /** The owner's duo defense on the bed, and the forge full of iron and gold: made again for each round. */
    @Override
    void setUp() {
        if (defenders.points.bed != null) {
            Defense.build(instance, defenders.points.bed.blockIn(instance.world()), DEFENSE, defenders.color.woolData());
        }
        for (Generator forge : forges) {
            forge.fill();
        }
    }

    /** Only the defended bed counts: the others are part of the map. */
    @Override
    Team bedTeam(Block block) {
        if (defenders.points.bed == null) {
            return null;
        }
        for (Block half : Instance.bedOf(defenders.points.bed.blockIn(instance.world()))) {
            if (half.equals(block)) {
                return defenders;
            }
        }
        return null;
    }

    /** Everyone in the game hears everyone. */
    @Override
    boolean chatTeams() {
        return false;
    }

    /** The death of the attacker ends a round, not the game: no final kill. */
    @Override
    boolean finalKillsCounted() {
        return false;
    }

    @Override
    int countdownSeconds() {
        return ROUND_COUNTDOWN;
    }

    /** The attacker in the middle of the top bridge, facing the base; the defenders on their island. */
    @Override
    Location spawnOf(Gamer gamer) {
        if (gamer.id.equals(attacker) && topBridge != null) {
            return at(topBridge.centre(true));
        }
        return super.spawnOf(gamer);
    }

    // --- The kits -------------------------------------------------------------------------------

    /** The attack kit this player chose. */
    RoundKit kitOf(UUID player) {
        List<RoundKit> kits = settings.bedBreakKits();
        return kits.get(kitIndex(host.bedBreakKit(player), kits.size()));
    }

    /**
     * The attacker: the kit they chose, where they put its items in the lobby's kit editor (else
     * where their Hypixel hotbar wants them), in iron armour; a wooden sword with a kit without
     * sword. The defenders: a game's start, the wooden sword and what they bought this round.
     */
    @Override
    void giveKit(Player player, Gamer gamer) {
        gamer.armor = Math.max(gamer.armor, ARMOUR_TIER);
        if (!gamer.id.equals(attacker)) {
            super.giveKit(player, gamer);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        wearArmor(player, gamer);
        ItemStack[] layout = lobbyKit(player, gamer);
        boolean sword = false;
        for (RoundKit.Entry entry : kitOf(gamer.id).entries(catalog())) {
            sword |= entry.item.kind() == ShopItem.Kind.SWORD;
            ItemStack stack = stack(entry.item, gamer);
            stack.setAmount(entry.amount);
            AttackDefenseGame.putKitItem(player, gamer, layout, stack, entry.item.category());
        }
        if (!sword) {
            ItemStack wooden = new ItemStack(Material.WOOD_SWORD);
            ItemMeta meta = wooden.getItemMeta();
            meta.spigot().setUnbreakable(true);
            if (gamer.team.upgrades.sharpness()) {
                meta.addEnchant(Enchantment.DAMAGE_ALL, 1, true);
            }
            wooden.setItemMeta(meta);
            AttackDefenseGame.putKitItem(player, gamer, layout, wooden, ShopItem.Category.MELEE);
        }
        player.updateInventory();
    }

    /** Opens the menu of the attack kits; false if the player does not play. */
    @Override
    public boolean openKitMenu(Player player) {
        Gamer gamer = gamers.get(player.getUniqueId());
        if (gamer == null || host.gameOf(gamer.id) != this || phase == Phase.ENDED) {
            return false;
        }
        List<RoundKit> kits = settings.bedBreakKits();
        KitMenu.open(player, this, true, kits, kitIndex(host.bedBreakKit(gamer.id), kits.size()), catalog());
        return true;
    }

    /**
     * The player chose an attack kit: the attacker before the round has it at once, and the
     * countdown goes on from {@value #READY_COUNTDOWN} seconds; otherwise it is the kit of their
     * next attack.
     */
    @Override
    public void chooseKit(Player player, boolean attack, int index) {
        Gamer gamer = gamers.get(player.getUniqueId());
        List<RoundKit> kits = settings.bedBreakKits();
        if (gamer == null || host.gameOf(gamer.id) != this || phase == Phase.ENDED || index < 0 || index >= kits.size()) {
            return;
        }
        host.chooseBedBreakKit(gamer.id, index);
        String name = ChatColor.stripColor(kits.get(index).name);
        if (gamer.id.equals(attacker) && phase == Phase.STARTING) {
            giveKit(player, gamer);
            player.sendMessage(ChatColor.YELLOW + "Your kit: " + ChatColor.GREEN + name + ChatColor.YELLOW + ".");
            player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1f, 1f);
            shortenCountdown(READY_COUNTDOWN);
        } else {
            boolean attacking = gamer.id.equals(attacker) && !roundOver;
            player.sendMessage(ChatColor.YELLOW + "Your attack kit: " + ChatColor.GREEN + name + ChatColor.YELLOW
                    + (attacking ? ", from your next attack." : ", for your next attack."));
            player.playSound(player.getLocation(), Sound.CLICK, 1f, 1f);
        }
        sidebars();
    }

    /** The menu of the kits opens by itself for the attacker before the round, and a line of the chat opens it again. */
    private void offerKits(final Player player) {
        sendButton(player, ChatColor.GRAY + "Your kit: " + ChatColor.WHITE + ChatColor.stripColor(kitOf(player.getUniqueId()).name)
                + ChatColor.GRAY + ". ", ChatColor.GREEN + "" + ChatColor.BOLD + "[CHANGE KIT]", "/kit");
        later(1L, () -> {
            if (player.isOnline() && host.gameOf(player.getUniqueId()) == this && phase == Phase.STARTING
                    && player.getUniqueId().equals(attacker)) {
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

    // --- Rounds ---------------------------------------------------------------------------------

    /** The attacker of the round, the defenders: everyone fresh, with nothing bought. */
    private void assignRoles() {
        attacker = order.get(round);
        defenders.members.clear();
        attackers.members.clear();
        defenders.bed = true;
        attackers.bed = false;
        for (Gamer gamer : present()) {
            cancel(gamer.respawn);
            gamer.respawn = null;
            gamer.respawning = false;
            gamer.eliminated = false;
            gamer.lastDamager = null;
            gamer.armor = ARMOUR_TIER;
            gamer.pickaxe = -1;
            gamer.axe = -1;
            gamer.shears = false;
            gamer.enderChest = null;
            gamer.milkUntil = 0L;
            gamer.team = gamer.id.equals(attacker) ? attackers : defenders;
            gamer.team.members.add(gamer.id);
        }
    }

    @Override
    void welcome(Player player, Gamer gamer) {
        String head = ChatColor.RED + "" + ChatColor.BOLD + "Round " + (round + 1) + "/" + order.size() + ChatColor.RED + ": ";
        Gamer attacking = gamers.get(attacker);
        String who = attacking == null ? "the attacker" : attacking.coloured();
        if (gamer.id.equals(attacker)) {
            player.sendMessage(head + "you " + ChatColor.WHITE + "attack" + ChatColor.RED + " from the top bridge. Break the "
                    + defenders.color.coloured() + ChatColor.RED + " bed within three minutes: only the bed counts."
                    + " One life.");
            player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "ATTACK",
                    ChatColor.WHITE + "Break the " + defenders.color.coloured() + ChatColor.WHITE + " bed");
            offerKits(player);
        } else {
            player.sendMessage(head + "defend your bed against " + who + ChatColor.RED + ", who comes by the top bridge."
                    + " No kit: your forge is full, buy what you need at the item shop. Kill them, or hold three minutes;"
                    + " you come back " + respawnSeconds() + " seconds after a death.");
            player.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "DEFEND", who + ChatColor.WHITE + " attacks");
        }
        if (round == 0) {
            player.sendMessage(ChatColor.GRAY + "Iron armour, Protection " + settings.bedBreakProtection()
                    + ", no Sharpness. A bed broken or the attacker killed: a point. /kit chooses your attack kit,"
                    + " /spawn takes you back to the lobby.");
        }
    }

    /** The first round's countdown is over. */
    @Override
    void started() {
        roundStarted();
    }

    private void roundStarted() {
        roundOver = false;
        closeKitMenus();
        Gamer gamer = gamers.get(attacker);
        broadcast(ChatColor.RED + "Round " + (round + 1) + "/" + order.size() + ": "
                + (gamer == null ? "the attacker" : gamer.coloured()) + ChatColor.RED + " attacks with the "
                + ChatColor.WHITE + ChatColor.stripColor(kitOf(attacker).name) + ChatColor.RED + " kit. Three minutes!",
                Sound.NOTE_PLING);
        sidebars();
    }

    /** The attacker has one life; the defenders come back while the round lasts. */
    @Override
    boolean finalDeath(Gamer gamer) {
        return gamer.id.equals(attacker);
    }

    @Override
    void outOfGame(Player victim, Gamer gamer) {
        victim.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "YOU DIED!", ChatColor.GRAY + "The defenders win the round.");
    }

    @Override
    void breakBed(Player breaker, Team team) {
        if (roundOver || phase != Phase.PLAYING || team != defenders || !defenders.bed) {
            return;
        }
        super.breakBed(breaker, team);
        Gamer gamer = gamers.get(breaker.getUniqueId());
        if (gamer != null && gamer.id.equals(attacker)) {
            points.merge(gamer.id, 1, Integer::sum);
            Integer best = quickest.get(gamer.id);
            if (best == null || seconds < best) {
                quickest.put(gamer.id, seconds);
            }
            roundEnd(true, gamer.coloured() + ChatColor.GREEN + " broke the bed in " + seconds + " seconds!");
        }
    }

    @Override
    void died(Gamer victim, Gamer author, boolean finalKill) {
        if (roundOver || !victim.id.equals(attacker)) {
            return;
        }
        if (author != null) {
            points.merge(author.id, 1, Integer::sum);
            roundEnd(false, author.coloured() + ChatColor.GREEN + " killed the attacker!");
        } else {
            roundEnd(false, victim.coloured() + ChatColor.GREEN + " died: the defenders win the round.");
        }
    }

    @Override
    void everySecond() {
        if (roundOver) {
            return;
        }
        int left = ROUND_SECONDS - seconds;
        if (left <= 0) {
            roundEnd(false, ChatColor.GREEN + "The bed held for three minutes: the defenders win the round.");
        } else if (left == 60 || left == 30 || left == 10) {
            broadcast(ChatColor.YELLOW + "" + left + " seconds left in the round.", Sound.NOTE_STICKS);
        }
    }

    /** The round is over: the result, then the next round a moment later. */
    private void roundEnd(boolean attackerWon, String message) {
        if (roundOver) {
            return;
        }
        roundOver = true;
        // nobody fights or builds any more, but everyone moves as they like
        phase = Phase.WAITING;
        broadcast(message, attackerWon ? Sound.ENDERDRAGON_GROWL : Sound.LEVEL_UP);
        for (Gamer gamer : present()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null) {
                boolean won = gamer.id.equals(attacker) == attackerWon;
                player.sendTitle(won ? ChatColor.GOLD + "" + ChatColor.BOLD + "ROUND WON"
                        : ChatColor.RED + "" + ChatColor.BOLD + "ROUND LOST", "");
            }
        }
        later(BETWEEN_TICKS, this::nextRound);
        sidebars();
    }

    /** The map as it was, the top bridge again, the next attacker, a countdown, and the round. */
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
        seconds = 0;
        // the map as it was: no block placed, the bed back, nothing on the ground
        instance.rollback();
        utilities.clear();
        removeUnusedBeds();
        assignRoles();
        entitiesAgain(islands());
        buildTopBridge();
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
        countdownLeft = ROUND_COUNTDOWN;
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
                    broadcast(ChatColor.YELLOW + "The round starts in " + ChatColor.RED + left + ChatColor.YELLOW + "...",
                            Sound.NOTE_STICKS);
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

    @Override
    void left(Gamer gamer) {
        defenders.members.remove(gamer.id);
        attackers.members.remove(gamer.id);
        if (phase != Phase.PLAYING || roundOver) {
            return;
        }
        if (gamer.id.equals(attacker)) {
            roundEnd(false, ChatColor.YELLOW + "The attacker left: next round.");
            return;
        }
        for (UUID id : defenders.members) {
            Gamer defender = gamers.get(id);
            if (defender != null && present(defender)) {
                return;
            }
        }
        roundEnd(true, ChatColor.YELLOW + "No defender left: next round.");
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
            broadcast(ChatColor.YELLOW + "Not enough players left: the Bed Break Simulator ends.", null);
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
        for (UUID id : ranking(points.keySet(), points, quickest)) {
            Gamer gamer = gamers.get(id);
            if (gamer != null) {
                ranking.add(gamer);
            }
        }
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
        summary.add(ChatColor.WHITE + "" + ChatColor.BOLD + "  Bed Break Simulator" + ChatColor.GRAY + " - " + instance.map().world());
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
            for (String line : summary) {
                player.sendMessage(line);
            }
            player.sendMessage(ChatColor.GRAY + "  You: " + gamer.beds + " beds broken, " + gamer.kills + " kills");
            if (winners.contains(gamer.id)) {
                player.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "VICTORY!", "");
                player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);
            } else {
                player.sendTitle(winners.isEmpty() ? ChatColor.YELLOW + "" + ChatColor.BOLD + "DRAW"
                        : ChatColor.RED + "" + ChatColor.BOLD + "GAME OVER!", "");
            }
        }
        afterTheEnd(!winners.isEmpty());
    }

    @Override
    List<String> lines(Gamer viewer) {
        List<String> lines = new ArrayList<>();
        lines.add((modeLine != null ? modeLine : ChatColor.RED + "Bed Break Simulator") + " " + ChatColor.GRAY
                + instance.map().world());
        lines.add("");
        if (round < 0) {
            lines.add(ChatColor.WHITE + "Players: " + ChatColor.GREEN + present().size() + ChatColor.GRAY + "/" + MAX_PLAYERS);
            lines.add(waitLeft >= 0 ? ChatColor.WHITE + "Starts in " + ChatColor.GREEN + waitLeft + " s"
                    : ChatColor.GRAY + "Waiting for players");
            lines.add("");
            lines.add(ChatColor.WHITE + "Attack kit: " + ChatColor.YELLOW + ChatColor.stripColor(kitOf(viewer.id).name));
            return lines;
        }
        lines.add(ChatColor.WHITE + "Round " + ChatColor.GREEN + (round + 1) + ChatColor.GRAY + "/" + order.size()
                + ChatColor.WHITE + " - " + (phase == Phase.STARTING ? "Starting..."
                : ChatColor.GREEN + clock(Math.max(0, ROUND_SECONDS - seconds))));
        lines.add(defenders.color.letter() + " " + ChatColor.WHITE + "Bed: " + (defenders.bed ? ChatColor.GREEN + CHECK
                : ChatColor.RED + CROSS));
        Gamer attacking = gamers.get(attacker);
        if (attacking != null) {
            lines.add(ChatColor.WHITE + "Attacker: " + attacking.coloured());
            lines.add(ChatColor.WHITE + "Kit: " + ChatColor.YELLOW + ChatColor.stripColor(kitOf(attacker).name));
        }
        lines.add("");
        // ten lines at most: the lobby adds the pings below
        List<UUID> ids = new ArrayList<>();
        for (Gamer gamer : present()) {
            ids.add(gamer.id);
        }
        List<UUID> ranking = ranking(ids, points, quickest);
        for (int i = 0; i < ranking.size() && lines.size() < 10; i++) {
            Gamer gamer = gamers.get(ranking.get(i));
            String name = gamer.name.length() > 16 ? gamer.name.substring(0, 16) : gamer.name;
            lines.add(ChatColor.WHITE + name + ": " + ChatColor.GREEN + points.getOrDefault(gamer.id, 0)
                    + (gamer == viewer ? ChatColor.GRAY + " YOU" : ""));
        }
        return lines;
    }
}
