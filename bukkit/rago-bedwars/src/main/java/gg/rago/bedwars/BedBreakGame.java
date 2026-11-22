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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

/**
 * Bed Break Simulator, a situation of Ranked Bedwars: 2 to 5 players on the map chosen (Archway or
 * Invasion), in rounds of three minutes, on two opposite islands. Each round, one player attacks
 * and the others defend (four at most):
 *
 * <ul>
 *   <li>The way of the attacker, on a map with its way up (bed-break: in its bedwars.yml, see
 *       {@link BedwarsMap.BedBreakTop}): the owner's stairway from the defended island up to the
 *       top, in the defenders' wool, placed as by a player (the defenders may break it), and the
 *       Top Bridge platform at its top, part of the map for the round, each half in the colour of
 *       the team on its side and a white block in the middle; the attacker starts in its middle,
 *       facing the defenders' half. On a map without, their bridge is built as a player builds it:
 *       one block wide, in wool of their colour, from their island to the bed, straight along an
 *       axis, or along the side of the map then the other with one turn, not a diagonal (see
 *       {@link Bridges}); the defenders may break it, and the attacker starts in its middle, facing
 *       the way to the base. The attacker has one life and the kit they chose (bed-break.kits: in config.yml): Classic (the Golden
 *       Pickaxe, tier III, and the Iron Axe, two stacks of wool), Fireball (a fireball, the Iron
 *       Pickaxe, tier II, and the Stone Axe, two stacks) or Fighter (the Wooden Pickaxe and Axe,
 *       tier I, a golden apple, ladders, the knockback stick), each with a stone sword and shears.
 *       Only the bed counts: they win the round by breaking it. No block over the map's build
 *       limit (hauteur-max: in its bedwars.yml).
 *   <li>The defenders have no kit: a game's start (a wooden sword), their forge full of iron and
 *       gold and their item shop, no upgrades, no diamond nor emerald; the bed under the Ranked
 *       Bedwars defense, end stone under glass ({@link BedDefense#RANKED}). They come back after a
 *       death, and win the round by killing the attacker or by holding three minutes.
 * </ul>
 *
 * Everyone wears iron armour with Protection II (bed-break.protection), no Sharpness. Each player
 * attacks in turn, twice each with two or three players. Breaking the bed, or killing the
 * attacker, gives a point; the most points win, the quickest bed between two equal.
 *
 * <p>The game waits for its players on its map, who look at it meanwhile: its size chosen in the
 * lobby (bed-break-simulator-1v1 to -1v4), they come in together and it starts 3 seconds later;
 * open (a mode queued alone), it starts 10 seconds after the second one joins, 3 seconds once five
 * are in (see {@link Lineup}). Before each round, the menu of the kits opens for the attacker (/kit
 * opens it again, also to choose the kit of a next attack while defending): the round starts
 * {@value #ROUND_COUNTDOWN} seconds after it is set, {@value #READY_COUNTDOWN} once the attacker
 * has chosen. Main thread only.
 */
final class BedBreakGame extends BedwarsGame implements KitMenu.Chooser {

    static final int MIN_PLAYERS = Lineup.MIN_PLAYERS;
    /** At most one attacker and four defenders: 1v4. */
    static final int MAX_PLAYERS = 5;
    static final int ROUND_SECONDS = 180;
    /** The countdown of a round: the time for the attacker to choose their kit. */
    static final int ROUND_COUNTDOWN = 5;
    /** The countdown once the attacker has chosen. */
    static final int READY_COUNTDOWN = 2;
    /** Between the end of a round and the next one. */
    private static final long BETWEEN_TICKS = 20L;
    /** Iron armour for everyone (the tier of {@link Gamer#armor}). */
    static final int ARMOUR_TIER = 2;
    /** The defense on the bed: the Ranked Bedwars one, end stone under glass, not the duo one. */
    static final List<String[]> DEFENSE = BedDefense.RANKED;
    private static final String CHECK = "✔";
    private static final String CROSS = "✘";

    private final Team defenders;
    private final Team attackers;
    /** Its size (1v1 to 1v4), or open. */
    private final Lineup lineup;
    /** The attacker of each round, in turn. */
    private final List<UUID> order = new ArrayList<>();
    private final Map<UUID, Integer> points = new HashMap<>();
    /** Each attacker's quickest bed, in seconds. */
    private final Map<UUID, Integer> quickest = new HashMap<>();
    private UUID attacker;
    /** The attacker's bridge of this round, its blocks from their island's side; empty if none fits. */
    private List<int[]> bridge = Collections.emptyList();
    /** Where the attacker starts on the platform at the top, {x, y, z, yaw}; null on a map without (see {@link #buildTop}). */
    private double[] topStart;
    private int round = -1;
    private boolean roundOver = true;
    private int waitLeft = -1;
    private BukkitTask waiting;
    private BukkitTask roundCountdown;

    /** A game for so many players: the size of its lobby mode (2 to 5), open for 1 (see {@link Lineup}). */
    BedBreakGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides, int playersPerMatch) {
        super(plugin, host, instance, modeId, modeName, sides);
        lineup = new Lineup(playersPerMatch, MAX_PLAYERS);
        // two opposite islands, as in a game: the bed defended, and the one the attacker's bridge comes from
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

    /** Open, while it waits with room; a game of a size gets its players together. */
    @Override
    boolean joinable() {
        return phase == Phase.WAITING && round < 0 && lineup.joinable(present().size());
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
                + "/" + lineup.capacity() + ChatColor.GRAY + ").", Sound.CLICK);
        if (count >= lineup.capacity() && waitLeft > Lineup.FULL_WAIT_SECONDS) {
            waitLeft = Lineup.FULL_WAIT_SECONDS;
        }
        sidebars();
    }

    private void waitHere(Player player) {
        prepare(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(instance.map().spectatorPoint().in(instance.world()));
        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Bed Break Simulator " + lineup.format() + ChatColor.RED
                + " on " + ChatColor.WHITE + instance.map().world() + ChatColor.RED + ": " + lineup.players()
                + ", rounds of 3 minutes. Each player in turn attacks from " + (fromTheTop()
                ? "the platform at the top" : "the middle of their bridge") + ", with one life,"
                + " and has to break the bed; the others defend it with their forge full.");
        player.sendMessage(ChatColor.GRAY + lineup.when() + "; the menu of the attack kits opens before each of your"
                + " attacks. /spawn takes you back to the lobby.");
    }

    private void waitingSecond() {
        if (phase != Phase.WAITING || round >= 0) {
            return;
        }
        int count = present().size();
        int startIn = lineup.startIn(count);
        if (startIn < 0) {
            if (!lineup.open()) {
                // its players came together: nobody else comes
                broadcast(ChatColor.YELLOW + "Not enough players left: the Bed Break Simulator ends.", null);
                close();
                return;
            }
            if (waitLeft >= 0) {
                waitLeft = -1;
                broadcast(ChatColor.YELLOW + "Not enough players any more: waiting for another one.", null);
            }
            sidebars();
            return;
        }
        if (waitLeft < 0) {
            waitLeft = startIn;
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
        // the game itself: the forge and the shop, the bridge and the defense (setUp), everyone in
        // place, the countdown, then the round
        super.begin();
    }

    // --- The map --------------------------------------------------------------------------------

    /** No top bridge: the attacker's own bridge, at the height of the islands (see {@link #buildBridge}). */
    @Override
    boolean hasTopBridge() {
        return false;
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

    /**
     * The attacker's way (the platform at the top and the stairway, else their bridge), the Ranked
     * defense on the bed, and the forge full of iron and gold: made again for each round.
     */
    @Override
    void setUp() {
        BedwarsMap.BedBreakTop top = instance.map().bedBreakTop();
        topStart = null;
        bridge = Collections.emptyList();
        if (fromTheTop()) {
            buildTop(top);
        } else {
            buildBridge();
        }
        if (defenders.points.bed != null) {
            Defense.build(instance, defenders.points.bed.blockIn(instance.world()), DEFENSE, defenders.color.woolData());
        }
        for (Generator forge : forges) {
            forge.fill();
        }
    }

    /**
     * The way up of this map, as the owner built it (bed-break: in its bedwars.yml): the platform at
     * the top, each half in the colour of the team on its side and a white block in the middle, part
     * of the map for the round (no one breaks it, the rollback takes it away); then the stairway
     * from the defended island up to it, in the defenders' wool, placed as by a player: the
     * defenders may break it, the rollback takes it away after the round. Where the map has a block
     * already, it stays.
     */
    @SuppressWarnings("deprecation") // Block#setTypeIdAndData: the 1.8 way to set a block with its data
    private void buildTop(BedwarsMap.BedBreakTop top) {
        World world = instance.world();
        MapPoint bed = defenders.points.bed;
        for (int[] cell : platform(top, bed.x() + 0.5, bed.z() + 0.5)) {
            Block block = world.getBlockAt(cell[0], cell[1], cell[2]);
            if (block.getType() != Material.AIR) {
                continue;
            }
            short wool = cell[3] == WHITE ? TeamColor.WHITE.woolData()
                    : (cell[3] == DEFENDERS ? defenders : attackers).color.woolData();
            instance.build(block, Material.WOOL, (byte) wool);
        }
        for (int[] place : top.stairs) {
            Block block = world.getBlockAt(place[0], place[1], place[2]);
            if (block.getType() != Material.AIR || instance.isPlaced(block)) {
                continue;
            }
            instance.markPlaced(block);
            block.setTypeIdAndData(Material.WOOL.getId(), (byte) defenders.color.woolData(), false);
        }
        topStart = platformStart(top, bed.x() + 0.5, bed.z() + 0.5);
    }

    /** True if the attacker comes from the top: the map has its way up (bed-break:) and the defended bed. */
    private boolean fromTheTop() {
        return instance.map().bedBreakTop() != null && defenders.points.bed != null;
    }

    /** A block of the platform: white, in the middle. */
    static final int WHITE = 0;
    /** A block of the platform on the defenders' half. */
    static final int DEFENDERS = 1;
    /** A block of the platform on the attacker's half. */
    static final int ATTACKERS = 2;

    /**
     * The blocks of the platform at the top, {x, y, z, half}: along its length (its longer side),
     * the half nearer the defended bed (centre bedX, bedZ) is {@link #DEFENDERS}, the other
     * {@link #ATTACKERS}; on the middle row (an odd length), one side of its middle block is the
     * defenders', the other the attacker's, and the middle block is {@link #WHITE}: the colours of
     * {@link TopBridge}.
     */
    static List<int[]> platform(BedwarsMap.BedBreakTop top, double bedX, double bedZ) {
        boolean alongZ = top.maxZ - top.minZ >= top.maxX - top.minX;
        double middle = alongZ ? (top.minZ + top.maxZ + 1) / 2.0 : (top.minX + top.maxX + 1) / 2.0;
        double centre = alongZ ? (top.minX + top.maxX + 1) / 2.0 : (top.minZ + top.maxZ + 1) / 2.0;
        // the defenders' end: -1 at the low x or z, 1 at the high one
        int defendersEnd = (alongZ ? bedZ : bedX) < middle ? -1 : 1;
        List<int[]> blocks = new ArrayList<>();
        for (int x = top.minX; x <= top.maxX; x++) {
            for (int z = top.minZ; z <= top.maxZ; z++) {
                double along = (alongZ ? z : x) + 0.5 - middle;
                double across = (alongZ ? x : z) + 0.5 - centre;
                int half;
                if (Math.abs(along) > 1e-9) {
                    half = along * defendersEnd > 0 ? DEFENDERS : ATTACKERS;
                } else if (Math.abs(across) > 1e-9) {
                    // as on the Top Bridge: the defenders' side of the middle row, to their left
                    // looking at the attacker's end
                    half = (alongZ ? across * defendersEnd : -across * defendersEnd) < 0 ? DEFENDERS : ATTACKERS;
                } else {
                    half = WHITE;
                }
                blocks.add(new int[] {x, top.platformY, z, half});
            }
        }
        return blocks;
    }

    /**
     * Where the attacker starts on the platform at the top, {x, y, z, yaw}: standing on its middle,
     * facing the defenders' half (the end nearer the defended bed, centre bedX, bedZ).
     */
    static double[] platformStart(BedwarsMap.BedBreakTop top, double bedX, double bedZ) {
        boolean alongZ = top.maxZ - top.minZ >= top.maxX - top.minX;
        double x = (top.minX + top.maxX + 1) / 2.0;
        double z = (top.minZ + top.maxZ + 1) / 2.0;
        int defendersEnd = (alongZ ? bedZ - z : bedX - x) < 0 ? -1 : 1;
        float yaw = alongZ ? InvisGame.yawTowards(0, defendersEnd) : InvisGame.yawTowards(defendersEnd, 0);
        return new double[] {x, top.platformY + 1, z, yaw};
    }

    /**
     * The bridge the attacker came by, as in Ranked Bedwars: from the front of their island, where
     * their own bed is (else their spawn), to the defended bed, one block wide, straight, at the
     * height of the islands ({@link Bridges}), in wool of their colour; placed as by a player: the
     * defenders break it, and the rollback takes it away after the round. Not from the spawn: on
     * Invasion it is in the middle of a dome, whose glass walls a bridge cannot go through.
     */
    @SuppressWarnings("deprecation") // Block#setTypeIdAndData: the 1.8 way to set a block with its data
    private void buildBridge() {
        bridge = Collections.emptyList();
        MapPoint own = attackers.points.bed;
        MapPoint spawn = attackers.points.spawn;
        MapPoint bed = defenders.points.bed;
        if (own == null && spawn == null || bed == null) {
            return;
        }
        double[] from = own != null ? new double[] {own.x() + 0.5, own.y(), own.z() + 0.5}
                : new double[] {spawn.x(), spawn.y(), spawn.z()};
        World world = instance.world();
        List<int[]> blocks = Bridges.plan((x, y, z) -> world.getBlockAt(x, y, z).getType().isSolid(),
                from, new double[] {bed.x() + 0.5, bed.y(), bed.z() + 0.5});
        if (blocks.isEmpty()) {
            plugin.getLogger().warning("No room for the attacker's bridge on " + instance.map().world() + " from the "
                    + attackers.color.displayName() + " island to the " + defenders.color.displayName()
                    + " bed: the attacker starts on their island");
            return;
        }
        for (int[] place : blocks) {
            Block block = world.getBlockAt(place[0], place[1], place[2]);
            if (instance.isPlaced(block)) {
                continue;
            }
            instance.markPlaced(block);
            block.setTypeIdAndData(Material.WOOL.getId(), (byte) attackers.color.woolData(), false);
        }
        bridge = blocks;
    }

    /**
     * Where the attacker starts on their bridge ({x, y, z, yaw}): on its middle block, facing the
     * next one, the way to the base; null without a bridge.
     */
    static double[] attackerStart(List<int[]> bridge) {
        if (bridge.isEmpty()) {
            return null;
        }
        int middle = bridge.size() / 2;
        int[] here = bridge.get(middle);
        int[] next = bridge.get(Math.min(middle + 1, bridge.size() - 1));
        int[] last = bridge.get(Math.max(middle - 1, 0));
        // the last block has no next one: the way it came from, turned round
        double dx = next != here ? next[0] - here[0] : here[0] - last[0];
        double dz = next != here ? next[2] - here[2] : here[2] - last[2];
        return new double[] {here[0] + 0.5, here[1] + 1, here[2] + 0.5, InvisGame.yawTowards(dx, dz)};
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

    /**
     * The attacker in the middle of the platform at the top, facing the defenders' half (else in the
     * middle of their bridge, facing the way to the base); the defenders on their island.
     */
    @Override
    Location spawnOf(Gamer gamer) {
        double[] start = topStart != null ? topStart : attackerStart(bridge);
        if (gamer.id.equals(attacker) && start != null) {
            return at(start);
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
            player.sendMessage(head + "you " + ChatColor.WHITE + "attack" + ChatColor.RED + " from "
                    + (fromTheTop() ? "the platform at the top" : "your bridge") + ". Break the "
                    + defenders.color.coloured() + ChatColor.RED + " bed within three minutes: only the bed counts."
                    + " One life.");
            player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "ATTACK",
                    ChatColor.WHITE + "Break the " + defenders.color.coloured() + ChatColor.WHITE + " bed");
            offerKits(player);
        } else {
            player.sendMessage(head + "defend your bed against " + who + ChatColor.RED + ", who comes "
                    + (fromTheTop() ? "from the platform at the top." : "by their bridge.")
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
                if (won) {
                    ding(player);
                }
            }
        }
        later(BETWEEN_TICKS, this::nextRound);
        sidebars();
    }

    /** The map as it was, the next attacker, their bridge again, a countdown, and the round. */
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
        List<UUID> winners = new ArrayList<>();
        List<UUID> losers = new ArrayList<>();
        List<UUID> hereIds = new ArrayList<>();
        for (Gamer gamer : here) {
            hereIds.add(gamer.id);
        }
        // the players of the first round: one who left before the end loses (a forfeit)
        Lineup.result(points.keySet(), hereIds, points, winners, losers);
        if (!winners.isEmpty()) {
            plugin.lobby().recordMatch(modeId, winners, losers);
        } else if (!hereIds.isEmpty()) {
            // nobody scored: a draw, a game played for everyone of the first round
            List<UUID> players = new ArrayList<>(points.keySet());
            for (UUID id : hereIds) {
                if (!players.contains(id)) {
                    players.add(id);
                }
            }
            plugin.lobby().recordDraw(modeId, players);
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
                ding(player);
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
            lines.add(ChatColor.WHITE + "Players: " + ChatColor.GREEN + present().size() + ChatColor.GRAY + "/" + lineup.capacity());
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
