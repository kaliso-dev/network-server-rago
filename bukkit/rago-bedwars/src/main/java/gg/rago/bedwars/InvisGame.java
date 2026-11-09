package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Invis practice: 2 to 6 players on a Bed Wars map (Invasion, Lectus or Archway), in rounds of
 * three minutes. Each round, one player is the attacker, at mid (the emerald generator nearest the
 * middle, facing the bed), with the owner's kit arranged in the lobby's kit editor: an invisibility
 * potion that lasts the whole round, without a name above the head, and a Jump V potion, 16 planks, a
 * golden pickaxe, a fireball, an iron axe, shears, a stone sword, 2 Bridge Eggs, 2 stacks of wool,
 * and Protection I armour (hidden while invisible, as on Hypixel). The others defend the bed of the
 * next island, under a defense of end stone and wool: shears, wooden tools and Haste, an iron sword
 * with Sharpness, Protection IV armour and 2 stacks of wool; four at most, a sixth player watches
 * that round. The attacker wins the round by breaking the bed; the defenders by killing the
 * attacker (one life) or by holding three minutes; a defender comes back three seconds after a
 * death. Each player attacks in turn, twice each with two or three players. Breaking the bed, or
 * killing the attacker, gives a point; the most points win.
 *
 * <p>The game waits for its players on its map: it starts 20 seconds after the second one joins,
 * 5 seconds once six are in. Main thread only.
 */
final class InvisGame extends BedwarsGame {

    static final int MIN_PLAYERS = 2;
    static final int MAX_PLAYERS = 6;
    static final int MAX_DEFENDERS = 4;
    static final int ROUND_SECONDS = 180;
    private static final int WAIT_SECONDS = 20;
    private static final int FULL_WAIT_SECONDS = 5;
    private static final int ROUND_COUNTDOWN = 5;
    private static final int DEFENDER_RESPAWN = 3;
    /** Between the end of a round and the next one. */
    private static final long BETWEEN_TICKS = 80L;
    private static final Material[] DEFENSE = {Material.ENDER_STONE, Material.WOOL};
    /** The attacker's kit when the lobby has none for the mode: the owner's, as in config.yml. */
    static final String[] ATTACKER_KIT = {"stone_sword", "wool 64", "wool 64", "plank 16", "golden_pickaxe", "iron_axe",
        "shears", "fireball 1", "bridge_egg 2", "invisibility", "jump"};
    /** Iron armour for everyone; Protection I for the attacker, IV for the defenders; Haste I. */
    static final int ARMOUR_TIER = 2;
    static final int ATTACKER_PROTECTION = 1;
    static final int DEFENDER_PROTECTION = 4;
    static final int DEFENDER_HASTE = 1;

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
        defenders.upgrades.setTier(Upgrades.Kind.REINFORCED_ARMOR, DEFENDER_PROTECTION);
        defenders.upgrades.setTier(Upgrades.Kind.MANIAC_MINER, DEFENDER_HASTE);
        defenders.upgrades.setTier(Upgrades.Kind.SHARPENED_SWORDS, 1);
        attackers.upgrades.setTier(Upgrades.Kind.REINFORCED_ARMOR, ATTACKER_PROTECTION);
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
        broadcast(ChatColor.GRAY + player.getDisplayName() + " joined the invis practice (" + ChatColor.WHITE + count + "/"
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
                + " players, rounds of 3 minutes, one attacker against the defenders, each player in turn.");
        player.sendMessage(ChatColor.GRAY + "It starts " + WAIT_SECONDS + " seconds after the second player joins. Your "
                + "attacker kit is the mode's kit of the lobby's kit editor. /spawn takes you back to the lobby.");
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
                + " attacks. Three minutes!", Sound.NOTE_PLING);
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
    }

    @Override
    void giveKit(Player player, Gamer gamer) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        gamer.armor = ARMOUR_TIER;
        wearArmor(player, gamer);
        if (gamer.id.equals(invisible)) {
            ItemStack[] kit = plugin.lobby().kit(player, modeId);
            if (kit != null) {
                for (int i = 0; i < kit.length; i++) {
                    inventory.setItem(i, kit[i] == null ? null : shopStack(kit[i], gamer));
                }
            } else {
                for (String entry : ATTACKER_KIT) {
                    String[] parts = entry.split(" ");
                    ShopItem item = catalogItem(parts[0]);
                    if (item != null) {
                        ItemStack stack = stack(item, gamer);
                        stack.setAmount(parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
                        inventory.addItem(stack);
                    }
                }
            }
        } else {
            give(player, gamer, stack(catalogItem("iron_sword"), gamer), ShopItem.Category.MELEE);
            ItemStack wool = stack(catalogItem("wool"), gamer);
            wool.setAmount(64);
            give(player, gamer, wool, ShopItem.Category.BLOCKS);
            give(player, gamer, wool.clone(), ShopItem.Category.BLOCKS);
            give(player, gamer, stack(catalogItem("shears"), gamer), ShopItem.Category.TOOLS);
            give(player, gamer, stack(catalog().pickaxes().get(0), gamer), ShopItem.Category.TOOLS);
            give(player, gamer, stack(catalog().axes().get(0), gamer), ShopItem.Category.TOOLS);
        }
        if (gamer.id.equals(invisible)) {
            longInvisibility(inventory);
        }
        player.updateInventory();
    }

    /** The attacker's invisibility lasts the whole round, three minutes, not the shop's 30 seconds. */
    private static void longInvisibility(PlayerInventory inventory) {
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || stack.getType() != Material.POTION || !(stack.getItemMeta() instanceof PotionMeta)) {
                continue;
            }
            PotionMeta meta = (PotionMeta) stack.getItemMeta();
            if (!meta.hasCustomEffect(PotionEffectType.INVISIBILITY)) {
                continue;
            }
            meta.removeCustomEffect(PotionEffectType.INVISIBILITY);
            meta.addCustomEffect(new PotionEffect(PotionEffectType.INVISIBILITY, ROUND_SECONDS * 20, 0), true);
            meta.setDisplayName(ChatColor.WHITE + "Invisibility Potion (3 minutes)");
            stack.setItemMeta(meta);
        }
    }

    /** The attacker starts at mid, as in a game, looking at the bed to break; the defenders on their island. */
    @Override
    Location spawnOf(Gamer gamer) {
        Location mid = gamer.id.equals(invisible) ? mid() : null;
        return mid != null ? mid : super.spawnOf(gamer);
    }

    /** The emerald generator nearest the middle of the map, facing the defended bed; null without emeralds. */
    private Location mid() {
        List<MapPoint> emeralds = instance.map().emeralds();
        if (emeralds.isEmpty()) {
            return null;
        }
        double cx = 0;
        double cz = 0;
        for (MapPoint point : emeralds) {
            cx += point.x();
            cz += point.z();
        }
        cx /= emeralds.size();
        cz /= emeralds.size();
        MapPoint nearest = emeralds.get(0);
        for (MapPoint point : emeralds) {
            if (square(point.x() - cx) + square(point.z() - cz) < square(nearest.x() - cx) + square(nearest.z() - cz)) {
                nearest = point;
            }
        }
        Location at = nearest.in(instance.world());
        MapPoint bed = defenders.points.bed;
        at.setYaw(yawTowards(bed.x() + 0.5 - at.getX(), bed.z() + 0.5 - at.getZ()));
        at.setPitch(0f);
        return at;
    }

    private static double square(double value) {
        return value * value;
    }

    /** The yaw that looks along (dx, dz): 0 to the south (+z), 90 to the west (-x), as Minecraft counts. */
    static float yawTowards(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private ShopItem catalogItem(String id) {
        return catalogItem(catalog(), id);
    }

    /** The shop's item of this id, the pickaxes and axes by their tier name ("golden_pickaxe"); null if none. */
    static ShopItem catalogItem(ShopCatalog catalog, String id) {
        for (List<ShopItem> tools : Arrays.asList(catalog.pickaxes(), catalog.axes())) {
            for (ShopItem tool : tools) {
                if (shopId(tool).equals(id)) {
                    return tool;
                }
            }
        }
        return catalog.byId(id);
    }

    /** "Golden Pickaxe": golden_pickaxe. */
    static String shopId(ShopItem item) {
        return item.name().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /**
     * A stack of the kit as the shop sells it: the potions with their effect, the Bridge Egg with
     * its name, the tools with their Efficiency, in the team's colour. Anything else stays as it is.
     */
    @SuppressWarnings("deprecation") // ItemStack#getDurability is the 1.8 data value
    private ItemStack shopStack(ItemStack kit, Gamer gamer) {
        ShopItem match = null;
        for (List<ShopItem> tools : Arrays.asList(catalog().pickaxes(), catalog().axes())) {
            for (ShopItem tool : tools) {
                if (tool.material() == kit.getType()) {
                    match = tool;
                }
            }
        }
        if (match == null) {
            for (ShopItem item : catalog().all()) {
                boolean sameData = item.kind() == ShopItem.Kind.TEAM_BLOCK || item.data() == kit.getDurability();
                if (item.material() == kit.getType() && sameData) {
                    match = item;
                    break;
                }
            }
        }
        if (match == null) {
            return kitItem(kit.clone(), gamer);
        }
        ItemStack stack = stack(match, gamer);
        stack.setAmount(kit.getAmount());
        return stack;
    }

    @Override
    void welcome(Player player, Gamer gamer) {
        String head = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Round " + (round + 1) + "/" + order.size()
                + ChatColor.LIGHT_PURPLE + ": ";
        Gamer invisibleGamer = gamers.get(invisible);
        String who = invisibleGamer == null ? "the invisible" : invisibleGamer.coloured();
        if (gamer.id.equals(invisible)) {
            player.sendMessage(head + "you " + ChatColor.WHITE + "attack" + ChatColor.LIGHT_PURPLE
                    + ". Drink your invisibility potion (it lasts the round) and break the bed of the "
                    + defenders.color.coloured() + ChatColor.LIGHT_PURPLE + " island in three minutes. One life.");
            player.sendTitle(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "ATTACK",
                    ChatColor.WHITE + "Break the " + defenders.color.coloured() + ChatColor.WHITE + " bed in 3 minutes");
        } else if (watching.contains(gamer.id)) {
            player.sendMessage(head + "four defenders at most: you watch this round, where " + who + ChatColor.LIGHT_PURPLE
                    + " attacks.");
            player.sendTitle(ChatColor.GRAY + "" + ChatColor.BOLD + "WATCHING", ChatColor.WHITE + "Your turn comes next");
        } else {
            player.sendMessage(head + "defend your bed against " + who + ChatColor.LIGHT_PURPLE
                    + ", who will go invisible: kill them, or hold three minutes. You come back " + DEFENDER_RESPAWN
                    + " seconds after a death.");
            player.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "DEFEND", who + ChatColor.WHITE + " attacks");
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
            roundEnd(false, author.coloured() + ChatColor.GREEN + " killed the attacker!");
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
            roundEnd(false, ChatColor.GREEN + "The bed held for three minutes: the defenders win the round.");
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
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.removePotionEffect(PotionEffectType.JUMP);
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
            roundEnd(false, ChatColor.YELLOW + "The attacker left: next round.");
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
            lines.add(ChatColor.WHITE + "Attacker: " + invisibleGamer.coloured());
        }
        String role = viewer.id.equals(invisible) ? ChatColor.LIGHT_PURPLE + "Attacker"
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
