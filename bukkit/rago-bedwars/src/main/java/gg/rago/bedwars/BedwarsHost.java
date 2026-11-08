package gg.rago.bedwars;

import gg.rago.bedwars.BedwarsGame.Gamer;
import gg.rago.bedwars.BedwarsGame.Phase;
import gg.rago.game.GameMode;
import gg.rago.game.MatchProposal;
import gg.rago.game.QueueEntry;
import gg.rago.lobby.api.MatchHost;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Plays the Bed Wars modes of the lobby: takes a free copy of a map for each match (or has one
 * made, and the match waits in the queue for a second), starts the game, and applies the Bed Wars
 * rules in the game worlds: blocks placed only under the build limit and away from the spawns and
 * generators, only placed blocks and enemy beds broken, explosions that only take placed blocks,
 * no friendly fire, the void that kills, the shop villagers. Main thread only.
 */
final class BedwarsHost implements MatchHost, Listener {

    /** What players cannot use in a game: the map's chests stay as they are, nobody sleeps. */
    private static final Set<Material> NOT_USED = EnumSet.of(Material.BED_BLOCK, Material.CHEST,
            Material.TRAPPED_CHEST, Material.ENDER_CHEST, Material.FURNACE, Material.BURNING_FURNACE,
            Material.WORKBENCH, Material.ANVIL, Material.ENCHANTMENT_TABLE, Material.BREWING_STAND, Material.HOPPER,
            Material.DISPENSER, Material.DROPPER, Material.BEACON, Material.JUKEBOX, Material.NOTE_BLOCK,
            Material.CAULDRON, Material.ITEM_FRAME, Material.FLOWER_POT);
    /** Placed blocks that explosions do not break, as on Hypixel. */
    private static final Set<Material> BLAST_PROOF = EnumSet.of(Material.STAINED_GLASS, Material.OBSIDIAN,
            Material.BED_BLOCK);

    private final RagoBedwars plugin;
    private final Map<UUID, BedwarsGame> games = new HashMap<>();
    private final List<BedwarsGame> running = new ArrayList<>();
    /** Who lit each TNT: the kill goes to them (1.8 cannot give a TNT its source). */
    private final Map<Entity, UUID> tntOwners = new WeakHashMap<>();
    /** Players already told that no map is ready, so that they are not told every second. */
    private final Set<UUID> toldNoMap = new HashSet<>();
    private boolean warnedNoMap;

    BedwarsHost(RagoBedwars plugin) {
        this.plugin = plugin;
    }

    // --- MatchHost ------------------------------------------------------------------------------

    @Override
    public boolean start(MatchProposal match) {
        GameMode mode = match.mode();
        List<List<Player>> sides = new ArrayList<>();
        for (List<QueueEntry> team : match.teams()) {
            List<Player> side = new ArrayList<>();
            for (QueueEntry entry : team) {
                Player player = Bukkit.getPlayer(entry.player());
                if (player == null || games.containsKey(entry.player())) {
                    return false;
                }
                side.add(player);
            }
            sides.add(side);
        }
        List<BedwarsMap> maps = mapsFor(mode);
        if (maps.isEmpty()) {
            noMap(mode, sides);
            return false;
        }
        // the map the players chose, while it can still be played; else any map of the mode
        List<BedwarsMap> chosen = new ArrayList<>();
        for (BedwarsMap map : maps) {
            if (map.world().equalsIgnoreCase(match.map())) {
                chosen.add(map);
            }
        }
        if (!chosen.isEmpty()) {
            maps = chosen;
        }
        Instance instance = plugin.instances().acquire(maps);
        if (instance == null) {
            // a match waits: a copy of one of its maps is made, room made for it if need be
            plugin.instances().prepare(maps, true);
            return false;
        }
        if (instance.map().completeTeams().size() < islands(mode)) {
            plugin.instances().release(instance);
            return false;
        }
        BedwarsGame game = new BedwarsGame(plugin, this, instance, mode.id(), ChatColor.stripColor(mode.name()), sides);
        for (List<Player> side : sides) {
            for (Player player : side) {
                games.put(player.getUniqueId(), game);
                toldNoMap.remove(player.getUniqueId());
            }
        }
        running.add(game);
        game.begin();
        // The next match should not wait for a copy: one is made now if none is left and there is room.
        if (!plugin.instances().hasFree(maps)) {
            plugin.instances().prepare(maps, false);
        }
        return true;
    }

    /** The islands a game of this mode needs: one per team, two at least. */
    static int islands(GameMode mode) {
        return Math.max(2, mode.teams());
    }

    /** The maps this mode can be played on now (config.yml: modes), the demo map when none can. */
    List<BedwarsMap> mapsFor(GameMode mode) {
        return plugin.maps().playable(islands(mode), plugin.settings().mapsOf(mode.id()));
    }

    /**
     * The maps a player may choose for this mode in the lobby: its maps that can be played now. The
     * ranked modes draw the map at random: no choice.
     */
    @Override
    public List<String> maps(GameMode mode) {
        List<String> names = new ArrayList<>();
        if (mode.ranked()) {
            return names;
        }
        for (BedwarsMap map : mapsFor(mode)) {
            names.add(map.world());
        }
        return names;
    }

    private void noMap(GameMode mode, List<List<Player>> sides) {
        if (!warnedNoMap) {
            warnedNoMap = true;
            plugin.getLogger().warning("A " + mode.id() + " match is waiting, but none of its Bed Wars maps "
                    + plugin.settings().mapsOf(mode.id()) + " with " + islands(mode)
                    + " teams is ready: install it, or set one up with /bw (see the README of RagoBedwars).");
        }
        for (List<Player> side : sides) {
            for (Player player : side) {
                if (toldNoMap.add(player.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + "No Bed Wars map is ready for this mode yet: the staff sets "
                            + "one up with /bw. You keep your place in the queue.");
                }
            }
        }
    }

    @Override
    public void abandon(Player player) {
        BedwarsGame game = games.get(player.getUniqueId());
        if (game == null) {
            plugin.lobby().sendToLobby(player);
        } else {
            game.leave(player, true);
        }
    }

    // --- Games ----------------------------------------------------------------------------------

    /** The game of this player, or null. */
    BedwarsGame gameOf(UUID player) {
        return games.get(player);
    }

    /** The player no longer belongs to their game. */
    void release(UUID player) {
        games.remove(player);
    }

    /** The game is over and its players are gone: its world is made ready for the next one. */
    void finished(BedwarsGame game) {
        for (UUID id : game.players()) {
            if (games.get(id) == game) {
                games.remove(id);
            }
        }
        if (running.remove(game)) {
            plugin.instances().release(game.instance());
        }
    }

    /** The plugin is being disabled: every game stops, its players go back to the lobby. */
    void abortAll() {
        for (BedwarsGame game : new ArrayList<>(running)) {
            game.abort();
            finished(game);
        }
    }

    int running() {
        return running.size();
    }

    /** The game played in this world, or null. */
    private BedwarsGame gameIn(World world) {
        for (BedwarsGame game : running) {
            if (game.instance().world().equals(world)) {
                return game;
            }
        }
        return null;
    }

    private boolean isGameWorld(World world) {
        return plugin.instances().isGameWorld(world);
    }

    /** The player's game if they play in it now (alive, not waiting to respawn), else null. */
    private BedwarsGame playing(Player player) {
        BedwarsGame game = games.get(player.getUniqueId());
        if (game == null || game.phase() != Phase.PLAYING) {
            return null;
        }
        Gamer gamer = game.gamer(player.getUniqueId());
        return gamer == null || gamer.respawning || gamer.eliminated ? null : game;
    }

    // --- Moving, the void -----------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        BedwarsGame game = games.get(event.getPlayer().getUniqueId());
        if (game == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (game.phase() == Phase.STARTING
                && (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY())) {
            Location still = from.clone();
            still.setYaw(to.getYaw());
            still.setPitch(to.getPitch());
            event.setTo(still);
            return;
        }
        if (to.getY() < game.instance().map().voidY()) {
            game.kill(event.getPlayer(), null, true);
        }
    }

    /** Out of the game world by other means than the game (a command): out of the game. */
    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        final Player player = event.getPlayer();
        final BedwarsGame game = games.get(player.getUniqueId());
        if (game != null && event.getFrom().equals(game.instance().world())
                && !player.getWorld().equals(game.instance().world())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && games.get(player.getUniqueId()) == game) {
                    game.leave(player, true);
                }
            });
        }
    }

    // --- Damage, deaths -------------------------------------------------------------------------

    /**
     * In a game world, only players take damage. Between players: no friendly fire, the hit is
     * remembered for the kill, and a hit that would kill is the death of Bed Wars (respawn or out).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            if (isGameWorld(entity.getWorld())) {
                event.setCancelled(true);
            }
            return;
        }
        Player victim = (Player) entity;
        BedwarsGame game = games.get(victim.getUniqueId());
        if (game == null) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            game.kill(victim, null, true);
            return;
        }
        if (playing(victim) != game) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent
                && ((EntityDamageByEntityEvent) event).getDamager() instanceof Fireball) {
            // A fireball's hit and blast: neither its damage nor its push, which the server would
            // give with them; its own push and damage, as on Hypixel, come when it lands (onLand).
            event.setCancelled(true);
            return;
        }
        Gamer target = game.gamer(victim.getUniqueId());
        Player attacker = null;
        if (event instanceof EntityDamageByEntityEvent) {
            attacker = attacker(((EntityDamageByEntityEvent) event).getDamager());
            if (attacker != null && !attacker.equals(victim)) {
                Gamer author = game.gamer(attacker.getUniqueId());
                if (author == null || games.get(attacker.getUniqueId()) != game || author.team == target.team
                        || author.respawning || author.eliminated) {
                    event.setCancelled(true);
                    return;
                }
                game.hit(target, author);
            } else {
                // Their own fireball or TNT: the damage and the jump, nobody to credit.
                attacker = null;
            }
        }
        if (event.getFinalDamage() >= victim.getHealth()) {
            event.setCancelled(true);
            game.kill(victim, attacker, false);
        }
    }

    /**
     * A fireball lands (it has just exploded): it pushes the players around and hurts them, as on
     * Hypixel. The blast itself, which only breaks placed blocks, hurts and pushes nobody (onDamage).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLand(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball) || !isGameWorld(event.getEntity().getWorld())) {
            return;
        }
        BedwarsGame game = gameIn(event.getEntity().getWorld());
        if (game != null) {
            game.blast((Fireball) event.getEntity(), attacker(event.getEntity()));
        }
    }

    /** The player behind this damage: the one who hit, shot, threw the fireball or lit the TNT. */
    private Player attacker(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            Object shooter = ((Projectile) damager).getShooter();
            return shooter instanceof Player ? (Player) shooter : null;
        }
        if (damager instanceof TNTPrimed) {
            UUID owner = tntOwners.get(damager);
            if (owner != null) {
                return Bukkit.getPlayer(owner);
            }
            Entity source = ((TNTPrimed) damager).getSource();
            return source instanceof Player ? (Player) source : null;
        }
        return null;
    }

    /** A death the damage rule did not catch (a /kill): the same death, once respawned. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        final Player victim = event.getEntity();
        final BedwarsGame game = games.get(victim.getUniqueId());
        if (game == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setDeathMessage(null);
        event.setKeepInventory(true);
        final Player killer = victim.getKiller();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!victim.isOnline() || games.get(victim.getUniqueId()) != game) {
                return;
            }
            if (victim.isDead()) {
                victim.spigot().respawn();
            }
            game.kill(victim, killer, false);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        BedwarsGame game = games.get(event.getPlayer().getUniqueId());
        if (game != null) {
            event.setRespawnLocation(game.instance().map().spectatorPoint().in(game.instance().world()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (games.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Player) {
                ((Player) event.getEntity()).setFoodLevel(20);
            }
        }
    }

    // --- Blocks ---------------------------------------------------------------------------------

    @SuppressWarnings("deprecation") // BlockState#getTypeId and getRawData: the 1.8 block id and data
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!games.containsKey(player.getUniqueId())) {
            if (isGameWorld(block.getWorld())) {
                event.setCancelled(true);
            }
            return;
        }
        BedwarsGame game = playing(player);
        String refused = game == null ? "" : game.cannotBuildAt(block);
        if (refused != null) {
            event.setCancelled(true);
            if (!refused.isEmpty()) {
                player.sendMessage(ChatColor.RED + refused);
            }
            return;
        }
        if (block.getType() == Material.TNT) {
            // Lit at once, as on Hypixel.
            event.setCancelled(true);
            ItemStack hand = player.getItemInHand();
            if (hand.getAmount() > 1) {
                hand.setAmount(hand.getAmount() - 1);
            } else {
                player.setItemInHand(null);
            }
            TNTPrimed tnt = block.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5), TNTPrimed.class);
            tnt.setFuseTicks(BedwarsGame.TNT_FUSE_TICKS);
            tntOwners.put(tnt, player.getUniqueId());
            return;
        }
        game.instance().markPlaced(block, event.getBlockReplacedState().getTypeId(),
                event.getBlockReplacedState().getRawData());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!games.containsKey(player.getUniqueId())) {
            if (isGameWorld(block.getWorld())) {
                event.setCancelled(true);
            }
            return;
        }
        BedwarsGame game = playing(player);
        if (game == null) {
            event.setCancelled(true);
            return;
        }
        BedwarsGame.Team bed = game.bedTeam(block);
        if (bed != null) {
            event.setCancelled(true);
            if (bed == game.gamer(player.getUniqueId()).team) {
                player.sendMessage(ChatColor.RED + "You can't destroy your own bed!");
            } else {
                game.breakBed(player, bed);
            }
            return;
        }
        if (!game.instance().isPlaced(block)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You can only break blocks placed by a player!");
            return;
        }
        game.instance().unmark(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        final Player player = event.getPlayer();
        if (!games.containsKey(player.getUniqueId())) {
            if (isGameWorld(player.getWorld())) {
                event.setCancelled(true);
            }
            return;
        }
        BedwarsGame game = playing(player);
        Block block = event.getBlockClicked().getRelative(event.getBlockFace());
        String refused = game == null ? "" : game.cannotBuildAt(block);
        if (refused != null) {
            event.setCancelled(true);
            if (!refused.isEmpty()) {
                player.sendMessage(ChatColor.RED + refused);
            }
            return;
        }
        game.instance().markPlaced(block);
        // The bucket is used up, as on Hypixel.
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack hand = player.getItemInHand();
            if (hand != null && hand.getType() == Material.BUCKET) {
                player.setItemInHand(null);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (games.containsKey(event.getPlayer().getUniqueId()) || isGameWorld(event.getPlayer().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Water and lava flowing: where they go is taken back after the game. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        BedwarsGame game = gameIn(event.getBlock().getWorld());
        if (game != null) {
            game.instance().markPlaced(event.getToBlock());
        } else if (isGameWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Explosions only break placed blocks, never the blast-proof glass, obsidian or beds. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        World world = event.getLocation().getWorld();
        if (!isGameWorld(world)) {
            return;
        }
        event.setYield(0f);
        BedwarsGame game = gameIn(world);
        for (Iterator<Block> it = event.blockList().iterator(); it.hasNext();) {
            Block block = it.next();
            if (game == null || !game.instance().isPlaced(block) || BLAST_PROOF.contains(block.getType())) {
                it.remove();
            } else {
                game.instance().unmark(block);
            }
        }
    }

    // --- Items ----------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        BedwarsGame game = games.get(player.getUniqueId());
        if (game == null) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            return;
        }
        // A right click only: denying the block on a left click would also stop it from being broken (the beds).
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && NOT_USED.contains(event.getClickedBlock().getType())) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.FIREBALL
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            if (playing(player) == game) {
                game.throwFireball(player);
            }
        }
    }

    /** The villagers: the item shop, the team upgrades. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Villager) || !isGameWorld(clicked.getWorld())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        BedwarsGame game = playing(player);
        if (game == null || !game.instance().world().equals(clicked.getWorld())) {
            return;
        }
        if (game.isUpgrades(clicked)) {
            game.upgradesClicked(player);
        } else if (game.isShop(clicked)) {
            ShopMenu.open(player, game, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (isGameWorld(event.getRightClicked().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** The empty bottle of a potion goes, as on Hypixel. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        final Player player = event.getPlayer();
        if (games.containsKey(player.getUniqueId()) && event.getItem().getType() == Material.POTION) {
            Bukkit.getScheduler().runTask(plugin, () -> player.getInventory().remove(Material.GLASS_BOTTLE));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (event.getView().getTopInventory().getHolder() instanceof ShopMenu.Holder) {
            event.setCancelled(true);
            if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                ShopMenu.click(player, (ShopMenu.Holder) event.getView().getTopInventory().getHolder(), event.getRawSlot());
            }
            return;
        }
        // The armour is kept until the end: it stays on.
        if (games.containsKey(player.getUniqueId()) && event.getSlotType() == InventoryType.SlotType.ARMOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopMenu.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (games.containsKey(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** The wooden sword and the tools are kept: they cannot be dropped. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!games.containsKey(player.getUniqueId())) {
            return;
        }
        String type = event.getItemDrop().getItemStack().getType().name();
        if (playing(player) == null || type.equals("WOOD_SWORD") || type.endsWith("_PICKAXE") || type.endsWith("_AXE")
                || type.equals("SHEARS")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        if (games.containsKey(player.getUniqueId()) && playing(player) == null) {
            event.setCancelled(true);
        }
    }

    // --- Leaving --------------------------------------------------------------------------------

    /** Leaving the server in the middle of a game: out of it. Before RagoLobby forgets the player. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        toldNoMap.remove(event.getPlayer().getUniqueId());
        BedwarsGame game = games.get(event.getPlayer().getUniqueId());
        if (game != null) {
            game.leave(event.getPlayer(), false);
        }
    }

    // --- The game worlds stay as they are -------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState() && isGameWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (isGameWorld(event.getLocation().getWorld())
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (isGameWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (isGameWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (isGameWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (isGameWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (isGameWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (isGameWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }
}
