package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * One game of a practice mode in its arena: its players by side, the 3-2-1 countdown, the rounds,
 * the sidebar, and the way back to the lobby. A mode says what its arena looks like, where its
 * players start and what its rules are, through the hooks below; {@link PracticeGames} hands it
 * the events of its players. Main thread only.
 */
abstract class PracticeGame {

    enum Phase { COUNTDOWN, PLAYING, ENDED }

    /** A hit gives the kill to its author if the player falls or dies within this time. */
    static final long CREDIT_MS = 10_000L;
    private static final ChatColor[] COUNTDOWN_COLOURS = {ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW};
    /** After the first round, the countdown before a round is this short at most: the next point at once. */
    static final int NEXT_ROUND_COUNTDOWN = 1;

    final RagoPractice plugin;
    final PracticeHost host;
    final GameMode mode;
    final Arena arena;
    final List<List<UUID>> sides = new ArrayList<>();
    final Map<UUID, String> names = new LinkedHashMap<>();
    private final Map<UUID, Integer> sideOf = new HashMap<>();
    private final Map<UUID, UUID> lastHitBy = new HashMap<>();
    private final Map<UUID, Long> lastHitAt = new HashMap<>();
    /** Where each player was last put at their start (placeAtStart), for {@link #unstick}. */
    private final Map<UUID, Location> startOf = new HashMap<>();
    /** The hits and combos of each player, told at the end of a duel. */
    final MatchRecap recap = new MatchRecap();
    private final List<BukkitTask> later = new ArrayList<>();
    Phase phase = Phase.COUNTDOWN;
    /** Seconds since the fight started, in this round. */
    int seconds;
    /** Ticks since the fight started, in this round (only counted by a mode that {@link #ticks()}). */
    int tickCount;
    /** Rounds started, from 1. */
    int round;
    /** Seconds played in this game, all rounds together (the XP of each minute). */
    private int playedSeconds;
    private BukkitTask clock;
    private BukkitTask countdown;
    private BukkitTask ticker;

    PracticeGame(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        this.plugin = host.plugin();
        this.host = host;
        this.mode = mode;
        this.arena = arena;
        for (List<Player> team : teams) {
            List<UUID> side = new ArrayList<>();
            for (Player player : team) {
                side.add(player.getUniqueId());
                names.put(player.getUniqueId(), player.getDisplayName());
                sideOf.put(player.getUniqueId(), sides.size());
            }
            sides.add(side);
        }
    }

    // --- What a mode says -----------------------------------------------------------------------

    /** Builds the arena's own blocks, the first time its zone is used. */
    abstract void build();

    /** Where this player starts each round. */
    abstract Location spawnOf(UUID player);

    /** The lines of this player's sidebar. */
    abstract List<String> sidebar(UUID viewer);

    /** What the player reads when the game starts. */
    abstract void intro(Player player);

    /** The kit when the lobby's config.yml gives the mode none. */
    ItemStack[] defaultKit(Player player) {
        return null;
    }

    /**
     * Seconds of countdown before the first round, during which the players stand still; 0 for
     * none. The next rounds: {@value #NEXT_ROUND_COUNTDOWN} at most.
     */
    int countdownSeconds() {
        return 3;
    }

    /** The countdown before this round (1 for the first). */
    static int countdownOf(int round, int first) {
        return round > 1 ? Math.min(first, NEXT_ROUND_COUNTDOWN) : first;
    }

    /** True if the players stand still during the countdown. */
    boolean freezes() {
        return true;
    }

    /** True to be called every tick ({@link #tick}) while the fight is on. */
    boolean ticks() {
        return false;
    }

    void tick() {
    }

    void onFight() {
    }

    void onSecond() {
    }

    /** At the start of each round, the arena restored, before the players are put back. */
    void roundReset() {
    }

    /** Under this height a player has fallen into the void. */
    double fallY() {
        return arena.floorY() - 8;
    }

    /** Where the players out of a round watch from. */
    Location viewPoint() {
        Location view = arena.at(0.5, 10, -14.5, 0f);
        view.setPitch(30f);
        return view;
    }

    /** The player fell into the void during the fight. */
    void fell(Player player) {
        player.teleport(spawnOf(player.getUniqueId()));
        player.setFallDistance(0f);
    }

    /**
     * Where this player is held, look included, while they may neither move nor turn (block
     * extend, before the hit); null when they are free.
     */
    Location heldAt(Player player) {
        return null;
    }

    /** The player moved (in the fight or not, not in the void). */
    void moved(Player player, Location from, Location to) {
    }

    /**
     * True to let this damage happen; false cancels it. Attacker: the player of the game who hit or
     * shot, or null (a fall, a bot...). A mode can change the damage (0: only the knockback).
     */
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        return false;
    }

    /** A hit of a player on another one went through (after every plugin had its say). */
    void landed(Player victim, Player attacker, EntityDamageEvent event) {
    }

    /** The damage would kill the player: it is cancelled, and this says what happens instead. */
    void lethal(Player victim, Player killer) {
        fell(victim);
    }

    /** True if the players of a side can hurt each other. */
    boolean friendlyFire() {
        return false;
    }

    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return false;
    }

    /** A block the player placed stayed. */
    void placed(Player player, Block block) {
    }

    /** Default: a block placed in this round can be broken again. */
    boolean canBreak(Player player, Block block) {
        return arena.isPlaced(block);
    }

    /** A block the player broke went. */
    void broke(Player player, Block block) {
    }

    /** The item a block a player broke leaves on the ground (Fireball Fight's wood and end stone); null: nothing. */
    ItemStack dropOf(Block block) {
        return null;
    }

    /** The items the game's broken blocks left on the ground: its players pick them up; gone with the arena's restore. */
    private final Set<Item> drops = new HashSet<>();

    void dropped(Item item) {
        drops.removeIf(lying -> !lying.isValid());
        drops.add(item);
    }

    boolean ownsDrop(Item item) {
        return drops.contains(item);
    }

    /** The items left on the ground go, then the arena is as it was. */
    private void restoreArena() {
        for (Item item : drops) {
            item.remove();
        }
        drops.clear();
        arena.restore();
    }

    /** True if the blocks a player places come back in the hand: the kit never runs out of blocks. */
    boolean refills() {
        return false;
    }

    /** True if the result counts in the lobby's stats (a duel); false for a training. */
    boolean counts() {
        return sides.size() > 1;
    }

    void interact(Player player, PlayerInteractEvent event) {
    }

    /** The player clicked this slot of a {@link GameMenu} the game opened for them. */
    void menuClick(Player player, int slot) {
    }

    /** A player hit an entity of the game (a target, a bot...). Default: nothing happens. */
    void hitEntity(Player attacker, Entity target, EntityDamageEvent event) {
        event.setCancelled(true);
    }

    /** Something else hurts an entity of the game (a fall...). Default: nothing happens. */
    void entityHurt(Entity entity, EntityDamageEvent event) {
        event.setCancelled(true);
    }

    /** An entity of the game wants to attack this one: true lets it. Default: the game's players only. */
    boolean mayTarget(Entity bot, Entity target) {
        return target instanceof Player && host.gameOf(target.getUniqueId()) == this
                && ((Player) target).getGameMode() != org.bukkit.GameMode.SPECTATOR;
    }

    /** An entity of the game died. */
    void entityDied(Entity entity) {
    }

    /** A player of the game threw or shot something, while the fight is on. */
    void launched(Player player, Projectile projectile, ProjectileLaunchEvent event) {
    }

    /** Something a player of the game threw or shot landed. */
    void projectileHit(Player shooter, Projectile projectile) {
    }

    /** A projectile of the game itself (no player threw it: a cannon's fireball) landed. */
    void ownLanded(Projectile projectile) {
    }

    /** A pearl of this player lands: the event can be cancelled. */
    void pearled(Player player, PlayerTeleportEvent event) {
    }

    /** The player swung their arm (hit or missed). */
    void swung(Player player) {
    }

    /** The player gave up (/spawn) or left the server: in a duel, the other side wins. */
    void leave(Player player, boolean toLobby) {
        UUID id = player.getUniqueId();
        if (phase != Phase.ENDED) {
            host.release(id);
            if (sides.size() > 1) {
                int side = sideOf(id);
                if (online(side).isEmpty()) {
                    finish(side == 0 ? 1 : 0, ChatColor.GRAY + nameOf(id) + " gave up.");
                } else {
                    broadcast(ChatColor.GRAY + nameOf(id) + " left the game.", null);
                    left(player);
                }
            } else {
                finish(-1, null);
            }
        }
        host.release(id);
        if (toLobby) {
            plugin.lobby().sendToLobby(player);
        }
    }

    /** A player of a side that plays on left the game (already released). */
    void left(Player player) {
    }

    // --- The game -------------------------------------------------------------------------------

    final void begin() {
        if (arena.fresh()) {
            build();
        }
        for (Player player : online()) {
            prepare(player);
            intro(player);
        }
        clock = Bukkit.getScheduler().runTaskTimer(plugin, this::everySecond, 20L, 20L);
        if (ticks()) {
            ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (phase == Phase.PLAYING) {
                    tickCount++;
                    tick();
                }
            }, 1L, 1L);
        }
        startRound();
        sidebars();
    }

    /** A new round: the arena as built, everyone at their place with their kit, the countdown. */
    void startRound() {
        cancel(countdown);
        countdown = null;
        phase = Phase.COUNTDOWN;
        seconds = 0;
        tickCount = 0;
        round++;
        restoreArena();
        lastHitBy.clear();
        lastHitAt.clear();
        roundReset();
        for (Player player : online()) {
            placeAtStart(player);
        }
        int count = countdownOf(round, countdownSeconds());
        if (round == 1) {
            // just come from the lobby's world: a client sinks into the map while its chunks have
            // not come yet (down through the block under its start); during the countdown and a
            // little after it, one who sinks under their start goes back on it
            unstickUntil = System.currentTimeMillis() + Math.max(0, count) * 1000L + UNSTICK_AFTER_MS;
        }
        if (count <= 0) {
            fight();
            return;
        }
        countdown = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int left = count;

            @Override
            public void run() {
                if (phase == Phase.ENDED) {
                    cancel(countdown);
                    return;
                }
                if (left > 0) {
                    broadcast(ChatColor.YELLOW + "" + left + "...", Sound.NOTE_STICKS);
                    title(COUNTDOWN_COLOURS[Math.min(left, 3) - 1] + "" + ChatColor.BOLD + left, "");
                    left--;
                    return;
                }
                cancel(countdown);
                countdown = null;
                fight();
            }
        }, 0L, 20L);
    }

    /** How long after the countdown of the first round a player who sinks under their start still goes back on it. */
    static final long UNSTICK_AFTER_MS = 5000L;
    /** Until when (System.currentTimeMillis) a player who sinks under their start goes back on it. */
    private long unstickUntil;

    /**
     * Where a player who moves there goes back (their start), if they are sinking under it at the
     * start of the game: just come from the lobby's world, a client whose chunks have not come yet
     * goes down through the block under its start. Null otherwise.
     */
    Location unstuck(Player player, Location to) {
        if (phase == Phase.ENDED || System.currentTimeMillis() > unstickUntil) {
            return null;
        }
        Location start = startOf.get(player.getUniqueId());
        if (start == null || !sunk(to, start)) {
            return null;
        }
        Location back = start.clone();
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        return back;
    }

    /** The same for everyone, on the spot (when the fight starts). */
    private void unstick() {
        for (Player player : online()) {
            Location back = unstuck(player, player.getLocation());
            if (back != null) {
                player.teleport(back);
                player.setFallDistance(0f);
            }
        }
    }

    /**
     * True if this place is straight under the start (less than half a block from it across: only
     * through the block under the start, as walking off it takes more), more than a block lower.
     */
    static boolean sunk(Location at, Location start) {
        if (at.getWorld() != null && start.getWorld() != null && !at.getWorld().equals(start.getWorld())) {
            return false;
        }
        return Math.abs(at.getX() - start.getX()) < SUNK_ACROSS && Math.abs(at.getZ() - start.getZ()) < SUNK_ACROSS
                && at.getY() < start.getY() - 1;
    }

    /** How far across from the start a place may be to be straight under it (see sunk). */
    static final double SUNK_ACROSS = 0.5;

    private void fight() {
        unstick();
        phase = Phase.PLAYING;
        if (countdownSeconds() > 0) {
            broadcast(ChatColor.GREEN + "Fight!", Sound.NOTE_PLING);
            title(ChatColor.GREEN + "" + ChatColor.BOLD + "Fight!", "");
        }
        onFight();
        sidebars();
    }

    /** The player back at their start, healed, with their kit. */
    void placeAtStart(Player player) {
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        heal(player);
        Location start = spawnOf(player.getUniqueId());
        startOf.put(player.getUniqueId(), start.clone());
        player.teleport(start);
        player.setFallDistance(0f);
        giveKit(player);
        effects(player);
    }

    /**
     * True if the players earn the XP of the trainings here (a minute played, an attempt, a
     * success): where no game is won, so not in a duel, which gives its own at the end.
     */
    boolean earnsXp() {
        return !counts();
    }

    /** An attempt of this player that worked: a little Network XP (given every two minutes). */
    void succeeded(UUID player) {
        if (earnsXp()) {
            plugin.xp().success(player, mode.id(), System.currentTimeMillis());
        }
    }

    /** An attempt of this player that failed: a little less XP. */
    void tried(UUID player) {
        if (earnsXp()) {
            plugin.xp().attempt(player, mode.id(), System.currentTimeMillis());
        }
    }

    /** The potion effects of the mode, given at each start (Speed in boxing...). */
    void effects(Player player) {
    }

    private void everySecond() {
        if (phase == Phase.ENDED) {
            return;
        }
        if (phase == Phase.PLAYING) {
            seconds++;
            playedSeconds++;
            if (playedSeconds % 60 == 0 && earnsXp()) {
                long now = System.currentTimeMillis();
                for (Player player : online()) {
                    plugin.xp().minute(player.getUniqueId(), mode.id(), now);
                }
            }
            onSecond();
        }
        if (phase != Phase.ENDED) {
            sidebars();
        }
    }

    void sidebars() {
        for (Player player : online()) {
            plugin.lobby().setGameSidebar(player, sidebar(player.getUniqueId()));
        }
    }

    /**
     * The game is over: the winning side (-1: none, a training or a draw) is told, the result
     * counts in the stats for a duel, and everyone is back in the lobby at once.
     */
    void finish(int winner, String why) {
        if (phase == Phase.ENDED) {
            return;
        }
        phase = Phase.ENDED;
        stopTasks();
        if (winner >= 0 && sides.size() > 1 && counts()) {
            List<UUID> winners = new ArrayList<>(sides.get(winner));
            List<UUID> losers = new ArrayList<>();
            for (int side = 0; side < sides.size(); side++) {
                if (side != winner) {
                    losers.addAll(sides.get(side));
                }
            }
            plugin.lobby().recordMatch(mode.id(), winners, losers);
        }
        for (Player player : online()) {
            if (winner < 0) {
                if (sides.size() > 1) {
                    player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "Draw." + ChatColor.GRAY + " " + result(-1)
                            + (why == null ? "" : " " + why));
                }
            } else if (sideOf(player.getUniqueId()) == winner) {
                player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Victory!" + ChatColor.GRAY + " " + result(winner)
                        + (why == null ? "" : " " + why));
                player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);
            } else {
                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Defeat." + ChatColor.GRAY + " " + result(winner));
            }
        }
        if (showsRecap()) {
            List<String> lines = recap.lines(sides, this::nameOf, Duel.CHAT);
            for (Player player : online()) {
                for (String line : lines) {
                    player.sendMessage(line);
                }
            }
        }
        ended();
        Bukkit.getScheduler().runTaskLater(plugin, this::backToLobby, 1L);
    }

    /** The score at the end, for the victory and defeat lines ("(3 - 1)"); "" for none. -1: a draw. */
    String result(int winner) {
        return "";
    }

    /** The game just ended: a mode removes what it spawned (targets, bots...). */
    void ended() {
    }

    private void backToLobby() {
        for (UUID id : players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && host.gameOf(id) == this) {
                host.release(id);
                plugin.lobby().sendToLobby(player);
            }
        }
        restoreArena();
        host.finished(this);
    }

    /** Stops the game at once: the plugin is being disabled. */
    void abort() {
        phase = Phase.ENDED;
        stopTasks();
        ended();
        for (UUID id : players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && host.gameOf(id) == this) {
                player.sendMessage(ChatColor.RED + "The game is stopped: the server is restarting its modes.");
                host.release(id);
                plugin.lobby().sendToLobby(player);
            }
        }
        restoreArena();
    }

    /** The game could not start: everything it started stops; the lobby takes its players back. */
    void cancelStart() {
        phase = Phase.ENDED;
        stopTasks();
        ended();
        restoreArena();
    }

    private void stopTasks() {
        cancel(countdown);
        cancel(clock);
        cancel(ticker);
        for (BukkitTask task : later) {
            task.cancel();
        }
        later.clear();
    }

    /** Runs this later, unless the game is over by then. */
    void later(long ticks, Runnable task) {
        later.removeIf(t -> !Bukkit.getScheduler().isQueued(t.getTaskId()));
        later.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (phase != Phase.ENDED) {
                task.run();
            }
        }, ticks));
    }

    // --- Called by the events -------------------------------------------------------------------

    /** The player fell under {@link #fallY}. */
    final void fellIntoVoid(Player player) {
        if (phase == Phase.PLAYING) {
            fell(player);
        } else if (phase == Phase.COUNTDOWN) {
            player.teleport(spawnOf(player.getUniqueId()));
            player.setFallDistance(0f);
        }
    }

    /** The player took a deadly blow: the kill goes to the last one who hit them lately. */
    final void died(Player victim) {
        if (phase != Phase.PLAYING) {
            heal(victim);
            return;
        }
        UUID by = killerOf(victim.getUniqueId());
        Player killer = by == null ? null : Bukkit.getPlayer(by);
        lethal(victim, killer != null && host.gameOf(by) == this ? killer : null);
    }

    // --- Players --------------------------------------------------------------------------------

    List<UUID> players() {
        return new ArrayList<>(names.keySet());
    }

    /** The players still in this game, online. */
    List<Player> online() {
        List<Player> online = new ArrayList<>();
        for (UUID id : names.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && host.gameOf(id) == this) {
                online.add(player);
            }
        }
        return online;
    }

    /** The players of a side still in this game, online. */
    List<Player> online(int side) {
        List<Player> online = new ArrayList<>();
        for (UUID id : sides.get(side)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && host.gameOf(id) == this) {
                online.add(player);
            }
        }
        return online;
    }

    /** The side of this player, from 0; -1 if not in the game. */
    int sideOf(UUID player) {
        Integer side = sideOf.get(player);
        return side == null ? -1 : side;
    }

    /** The place of the player in their side, from 0. */
    int indexOf(UUID player) {
        int side = sideOf(player);
        return side < 0 ? 0 : Math.max(0, sides.get(side).indexOf(player));
    }

    boolean sameSide(UUID one, UUID other) {
        return sideOf(one) >= 0 && sideOf(one) == sideOf(other);
    }

    /** A player joins this game while it runs (an open arena), on a side of their own. */
    void join(Player player) {
        UUID id = player.getUniqueId();
        List<UUID> side = new ArrayList<>();
        side.add(id);
        sideOf.put(id, sides.size());
        sides.add(side);
        names.put(id, player.getDisplayName());
        host.joined(id, this);
        prepare(player);
        intro(player);
        placeAtStart(player);
        sidebars();
    }

    /** Remembers who hit whom last, for the kill of a fall, and counts the hit for the recap. */
    void hit(Player victim, Player attacker) {
        lastHitBy.put(victim.getUniqueId(), attacker.getUniqueId());
        lastHitAt.put(victim.getUniqueId(), System.currentTimeMillis());
        if (phase == Phase.PLAYING) {
            recap.hit(attacker.getUniqueId(), victim.getUniqueId());
        }
    }

    /**
     * Whether the end of the game tells the recap of the hits: a duel of two sides, four players
     * at most, where someone hit someone. An event or a training does not.
     */
    boolean showsRecap() {
        return sides.size() == 2 && names.size() <= 4 && !recap.isEmpty();
    }

    /** Who gets the kill if this player dies now: the last one to hit them lately, or null. */
    UUID killerOf(UUID victim) {
        UUID by = lastHitBy.get(victim);
        Long at = lastHitAt.get(victim);
        if (by != null && at != null && System.currentTimeMillis() - at <= CREDIT_MS) {
            return by;
        }
        return null;
    }

    /** Forgets the hits on this player (they came back to life). */
    void forgetHits(UUID victim) {
        lastHitBy.remove(victim);
        lastHitAt.remove(victim);
    }

    /** The player's own kit as they arranged it (kit editor, or an earlier game), or the mode's default. */
    void giveKit(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        ItemStack[] kit = plugin.lobby().kit(player, mode.id());
        if (kit == null) {
            // the mode's own kit, in the order the player arranged it in an earlier game
            kit = plugin.lobby().arrange(player, mode.id(), defaultKit(player));
        }
        if (kit != null) {
            inventory.setContents(kit);
        }
        armour(player);
        afterKit(player);
        player.updateInventory();
    }

    /** The armour of the kit: none by default. */
    void armour(Player player) {
    }

    /** The kit is in the inventory: a mode adds its own items (a menu item...). */
    void afterKit(Player player) {
    }

    /** Leather armour of this colour, as the teams wear it in Bed Wars. */
    static void leather(Player player, Color color) {
        ItemStack[] armour = {leatherPiece(Material.LEATHER_BOOTS, color), leatherPiece(Material.LEATHER_LEGGINGS, color),
            leatherPiece(Material.LEATHER_CHESTPLATE, color), leatherPiece(Material.LEATHER_HELMET, color)};
        player.getInventory().setArmorContents(armour);
    }

    /** The Ranked Bedwars armour: iron boots and leggings, the helmet and chestplate of the team. */
    static void ironArmour(Player player, Color color) {
        ItemStack[] armour = {new ItemStack(Material.IRON_BOOTS), new ItemStack(Material.IRON_LEGGINGS),
            leatherPiece(Material.LEATHER_CHESTPLATE, color), leatherPiece(Material.LEATHER_HELMET, color)};
        player.getInventory().setArmorContents(armour);
    }

    /** Protection of this level on each piece of armour the player wears (0: none). */
    static void protect(Player player, int level) {
        if (level <= 0) {
            return;
        }
        ItemStack[] armour = player.getInventory().getArmorContents();
        for (ItemStack piece : armour) {
            if (piece != null && piece.getType() != Material.AIR) {
                piece.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION_ENVIRONMENTAL, level);
            }
        }
        player.getInventory().setArmorContents(armour);
    }

    static ItemStack leatherPiece(Material type, Color color) {
        ItemStack piece = new ItemStack(type);
        LeatherArmorMeta meta = (LeatherArmorMeta) piece.getItemMeta();
        meta.setColor(color);
        piece.setItemMeta(meta);
        return piece;
    }

    /** An item with a name, and lines under it. */
    static ItemStack named(Material type, int amount, int data, String name, String... lore) {
        ItemStack item = new ItemStack(type, amount, (short) data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) {
            List<String> lines = new ArrayList<>();
            Collections.addAll(lines, lore);
            meta.setLore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** True if the item is of this type and has this name. */
    static boolean isNamed(ItemStack item, Material type, String name) {
        return item != null && item.getType() == type && item.hasItemMeta() && name.equals(item.getItemMeta().getDisplayName());
    }

    void prepare(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        heal(player);
        player.setLevel(0);
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

    /** Out of the round until the next one: watching from above. */
    void spectate(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        heal(player);
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.teleport(viewPoint());
    }

    /**
     * A fireball thrown by a player of this game (or by the game itself, thrower null) exploded:
     * the players around it are pushed away and up as on Hypixel, and take its fixed damage (none
     * on a teammate); a lit TNT is pushed as they are. A hit on an enemy counts for the kill.
     */
    void blast(Fireball fireball, Player thrower) {
        if (phase != Phase.PLAYING) {
            return;
        }
        Location centre = fireball.getLocation();
        for (Player player : online()) {
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR || !player.getWorld().equals(centre.getWorld())) {
                continue;
            }
            Location at = player.getLocation();
            if (!Fireballs.reaches(at.getX() - centre.getX(), at.getY() - centre.getY(), at.getZ() - centre.getZ())) {
                continue;
            }
            double[] push = Fireballs.push(centre.getX(), centre.getY(), centre.getZ(), at.getX(), at.getY(), at.getZ());
            player.setVelocity(new Vector(push[0], push[1], push[2]));
            double damage;
            if (thrower == null || thrower.equals(player)) {
                damage = Fireballs.SELF_DAMAGE;
            } else if (sameSide(thrower.getUniqueId(), player.getUniqueId()) && !friendlyFire()) {
                damage = 0;
            } else {
                damage = Fireballs.ENEMY_DAMAGE;
                hit(player, thrower);
            }
            blasted(player, thrower, damage);
        }
        // the lit TNT around it flies off too, as on Hypixel: a fireball sends a TNT away
        for (Entity entity : centre.getWorld().getNearbyEntities(centre, Fireballs.RADIUS, Fireballs.RADIUS, Fireballs.RADIUS)) {
            if (entity instanceof TNTPrimed) {
                Location at = entity.getLocation();
                double[] push = Fireballs.push(centre.getX(), centre.getY(), centre.getZ(), at.getX(), at.getY(), at.getZ());
                entity.setVelocity(new Vector(push[0], push[1], push[2]));
            }
        }
    }

    /** True if the TNT of this mode hurts as the server's own does; false: it only pushes ({@link #tntBlast}). */
    boolean tntHurts() {
        return true;
    }

    /**
     * A TNT blew up in this game's zone, lit by owner (null if nobody of the game). Default:
     * nothing more than the server's own blast.
     */
    void tntBlast(Location centre, Player owner) {
    }

    /**
     * The push of a TNT's blast, as the server's own (power 4) but without its damage: the players
     * around it go away from it and up, the closer the stronger. A push on an enemy counts for the
     * kill.
     */
    void tntPush(Location centre, Player owner) {
        if (phase != Phase.PLAYING) {
            return;
        }
        for (Player player : online()) {
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR || !player.getWorld().equals(centre.getWorld())) {
                continue;
            }
            Location at = player.getLocation();
            double[] push = Fireballs.tntPush(centre.getX(), centre.getY(), centre.getZ(), at.getX(), at.getY(), at.getZ());
            if (push == null) {
                continue;
            }
            player.setVelocity(player.getVelocity().add(new Vector(push[0], push[1], push[2])));
            if (owner != null && !owner.equals(player) && !sameSide(owner.getUniqueId(), player.getUniqueId())) {
                hit(player, owner);
            }
        }
    }

    /** The fireball's damage on this player. Default: as on Hypixel, through death. */
    void blasted(Player player, Player thrower, double damage) {
        if (damage <= 0 || phase != Phase.PLAYING) {
            return;
        }
        if (player.getHealth() - damage <= 0) {
            died(player);
        } else {
            player.setHealth(player.getHealth() - damage);
            player.playEffect(EntityEffect.HURT);
        }
    }

    // --- Messages -------------------------------------------------------------------------------

    void broadcast(String message, Sound sound) {
        for (Player player : online()) {
            player.sendMessage(message);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1f, 1f);
            }
        }
    }

    static void sound(Player player, Sound sound, float pitch) {
        player.playSound(player.getLocation(), sound, 1f, pitch);
    }

    @SuppressWarnings("deprecation") // Player#sendTitle is marked "API subject to change" in 1.8.8
    void title(String title, String subtitle) {
        for (Player player : online()) {
            Titles.times(plugin.getLogger(), player, 0, 22, 6);
            player.sendTitle(title, subtitle);
        }
    }

    @SuppressWarnings("deprecation") // Player#sendTitle
    void title(Player player, String title, String subtitle) {
        Titles.times(plugin.getLogger(), player, 0, 30, 10);
        player.sendTitle(title, subtitle);
    }

    /** "1:05" for 65 seconds. */
    static String clock(int seconds) {
        int s = Math.max(0, seconds) % 60;
        return (Math.max(0, seconds) / 60) + ":" + (s < 10 ? "0" : "") + s;
    }

    /** "12.345" seconds for 12345 milliseconds. */
    static String time(long millis) {
        long ms = Math.max(0, millis);
        long thousandths = ms % 1000;
        return (ms / 1000) + "." + (thousandths < 100 ? "0" : "") + (thousandths < 10 ? "0" : "") + thousandths;
    }

    String nameOf(UUID player) {
        String name = names.get(player);
        return name == null ? "?" : name;
    }

    /** The names of a side, "A, B". */
    String namesOf(int side) {
        StringBuilder text = new StringBuilder();
        for (UUID id : sides.get(side)) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(nameOf(id));
        }
        return text.toString();
    }

    static void cancel(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    /** The entity behind this damage, or null. */
    static Entity damager(EntityDamageEvent event) {
        return event instanceof EntityDamageByEntityEvent ? ((EntityDamageByEntityEvent) event).getDamager() : null;
    }

    /** True if the player stands on the top of a full block (or of a slab). */
    static boolean onGround(Player player) {
        Location at = player.getLocation();
        double above = at.getY() - Math.floor(at.getY());
        if (above > 0.01 && above < 0.99) {
            return Math.abs(above - 0.5) < 0.01 && at.getBlock().getType().isSolid();
        }
        for (double dx = -0.3; dx <= 0.31; dx += 0.3) {
            for (double dz = -0.3; dz <= 0.31; dz += 0.3) {
                if (at.clone().add(dx, -0.05, dz).getBlock().getType().isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** An unmodifiable empty list, for sidebars with nothing. */
    static List<String> none() {
        return Collections.emptyList();
    }
}
