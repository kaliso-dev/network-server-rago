package gg.rago.practice;

import gg.rago.lobby.api.EndOfGame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Every practice game running, by player and by entity, and the rules every mode shares: a game's
 * players stand still during the countdown, fall into the void under its floor, never hurt the
 * players of other games, only place and break blocks as their mode says, and never drop items;
 * the deadly blows become deaths of the mode. Main thread only.
 */
final class PracticeGames implements Listener {

    /** Spectators of a game stay this close to it. */
    static final double SPECTATOR_RANGE = 70;
    /** Blocks the blasts never break, as in Bed Wars (and end stone resists the fireballs). */
    private static final Material[] BLAST_PROOF = {Material.GLASS, Material.STAINED_GLASS, Material.OBSIDIAN,
        Material.BED_BLOCK};

    private final RagoPractice plugin;
    private final World world;
    private final Map<UUID, PracticeGame> byPlayer = new HashMap<>();
    private final Map<UUID, PracticeGame> byEntity = new HashMap<>();
    private final List<PracticeGame> running = new ArrayList<>();
    /** Who lit each TNT: its blast is theirs (1.8 cannot give a TNT its source). */
    private final Map<Entity, UUID> tntOwners = new WeakHashMap<>();

    PracticeGames(RagoPractice plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    World world() {
        return world;
    }

    PracticeGame gameOf(UUID player) {
        return byPlayer.get(player);
    }

    void add(PracticeGame game) {
        running.add(game);
        for (UUID id : game.players()) {
            byPlayer.put(id, game);
        }
    }

    void join(UUID player, PracticeGame game) {
        byPlayer.put(player, game);
    }

    void release(UUID player) {
        byPlayer.remove(player);
    }

    /** The game is over: nothing is its own any more. False if it was not running. */
    boolean finished(PracticeGame game) {
        byPlayer.values().removeIf(g -> g == game);
        byEntity.values().removeIf(g -> g == game);
        return running.remove(game);
    }

    void own(Entity entity, PracticeGame game) {
        byEntity.put(entity.getUniqueId(), game);
    }

    void disown(Entity entity) {
        byEntity.remove(entity.getUniqueId());
    }

    void litBy(Entity tnt, Player player) {
        tntOwners.put(tnt, player.getUniqueId());
    }

    /** The plugin is being disabled: every game stops, its players go back to the lobby. */
    void abortAll() {
        for (PracticeGame game : new ArrayList<>(running)) {
            game.abort();
            game.host.finished(game);
        }
    }

    int running() {
        return running.size();
    }

    /** Every game running, of every mode. */
    List<PracticeGame> games() {
        return Collections.unmodifiableList(running);
    }

    /** The game whose zone holds this point, or null. */
    private PracticeGame gameAt(Location location) {
        for (PracticeGame game : running) {
            if (game.arena.near(location, 100)) {
                return game;
            }
        }
        return null;
    }

    /** The player behind this damage: the one who hit, shot, threw the fireball or lit the TNT. */
    Player playerBehind(Entity damager) {
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

    private static boolean spectating(Player player) {
        return player.getGameMode() == org.bukkit.GameMode.SPECTATOR;
    }

    // --- Moves ----------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PracticeGame game = byPlayer.get(player.getUniqueId());
        if (game == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (spectating(player)) {
            if (!game.holds(to)) {
                event.setTo(game.viewPoint());
            }
            return;
        }
        Location unstuck = game.unstuck(player, to);
        if (unstuck != null) {
            player.setFallDistance(0f);
            event.setTo(unstuck);
            return;
        }
        if (game.phase == PracticeGame.Phase.COUNTDOWN && game.freezes()
                && (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY())) {
            Location still = from.clone();
            still.setYaw(to.getYaw());
            still.setPitch(to.getPitch());
            event.setTo(still);
            return;
        }
        Location held = game.heldAt(player);
        if (held != null) {
            if (!BlockExtendTraining.still(to, held)) {
                event.setTo(held);
            }
            return;
        }
        if (to.getY() < game.fallY()) {
            game.fellIntoVoid(player);
            return;
        }
        game.moved(player, from, to);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        PracticeGame game = byPlayer.get(player.getUniqueId());
        if (game != null && event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && !game.holds(event.getTo())) {
            // the spectator menu of a player out of the round: only to the players near the game
            event.setCancelled(true);
            return;
        }
        if (game == null || event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        if (game.phase != PracticeGame.Phase.PLAYING || !game.arena.near(event.getTo(), 100)) {
            event.setCancelled(true);
            return;
        }
        game.pearled(player, event);
        if (!event.isCancelled()) {
            // the teleport where the pearl lands: every player of the game hears it, wherever they are
            Location to = event.getTo();
            for (Player listener : game.online()) {
                listener.playSound(heardAt(listener.getLocation(), to), Sound.ENDERMAN_TELEPORT, 1f, 1f);
            }
        }
    }

    /** How far from a listener a sound from afar is played: within hearing, at full volume. */
    static final double HEARD_WITHIN = 8;

    /** Where a listener hears a sound from this source: there if near, else {@value #HEARD_WITHIN} blocks off towards it. */
    static Location heardAt(Location listener, Location source) {
        double dx = source.getX() - listener.getX();
        double dy = source.getY() - listener.getY();
        double dz = source.getZ() - listener.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= HEARD_WITHIN) {
            return source;
        }
        double k = HEARD_WITHIN / distance;
        return new Location(listener.getWorld(), listener.getX() + dx * k, listener.getY() + dy * k, listener.getZ() + dz * k);
    }

    // --- Damage ---------------------------------------------------------------------------------

    /** Who may hurt whom, and how much: the mode says. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        Entity damager = PracticeGame.damager(event);
        Player attacker = damager == null ? null : playerBehind(damager);
        if (!(entity instanceof Player)) {
            PracticeGame owner = byEntity.get(entity.getUniqueId());
            if (owner != null) {
                if (attacker != null && byPlayer.get(attacker.getUniqueId()) == owner && owner.phase == PracticeGame.Phase.PLAYING
                        && !spectating(attacker)) {
                    owner.hitEntity(attacker, entity, event);
                } else if (attacker == null && owner.phase == PracticeGame.Phase.PLAYING) {
                    owner.entityHurt(entity, event);
                } else {
                    event.setCancelled(true);
                }
            } else if (attacker != null && byPlayer.containsKey(attacker.getUniqueId()) && !(entity instanceof Fireball)) {
                // nothing else in the arenas is theirs to hit (a fireball can be sent back)
                event.setCancelled(true);
            }
            return;
        }
        Player victim = (Player) entity;
        PracticeGame game = byPlayer.get(victim.getUniqueId());
        if (game == null) {
            return;
        }
        if (damager instanceof EnderPearl) {
            // an ender pearl lands without hurting its thrower
            event.setCancelled(true);
            return;
        }
        if (game.phase != PracticeGame.Phase.PLAYING || spectating(victim)) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            game.fellIntoVoid(victim);
            return;
        }
        if (damager instanceof Fireball) {
            // its own push and damage come when it lands (onLand), as on Hypixel
            event.setCancelled(true);
            return;
        }
        if (damager instanceof TNTPrimed && !game.tntHurts()) {
            // its push comes with the blast (onExplode), without damage
            event.setCancelled(true);
            return;
        }
        if (attacker != null && attacker.equals(victim)) {
            attacker = null;
        }
        if (attacker != null && (byPlayer.get(attacker.getUniqueId()) != game || spectating(attacker)
                || (!game.friendlyFire() && game.sameSide(attacker.getUniqueId(), victim.getUniqueId())))) {
            event.setCancelled(true);
            return;
        }
        if (!game.damage(victim, attacker, event)) {
            event.setCancelled(true);
        }
    }

    /** A deadly blow: no death screen, the mode's own death instead (a point, a respawn...). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        PracticeGame game = byPlayer.get(victim.getUniqueId());
        if (game == null || event.getFinalDamage() < victim.getHealth()) {
            return;
        }
        event.setCancelled(true);
        Entity damager = PracticeGame.damager(event);
        Player attacker = damager == null ? null : playerBehind(damager);
        if (attacker != null && !attacker.equals(victim) && byPlayer.get(attacker.getUniqueId()) == game) {
            game.hit(victim, attacker);
        }
        game.died(victim);
    }

    /** A hit that went through: it counts for the kill of a fall, and for the mode (boxing...). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        PracticeGame game = byPlayer.get(victim.getUniqueId());
        if (game == null) {
            return;
        }
        Entity damager = PracticeGame.damager(event);
        Player attacker = damager == null ? null : playerBehind(damager);
        if (event.getFinalDamage() > 0 && game.phase == PracticeGame.Phase.PLAYING) {
            // the health left is worth telling in the recap
            game.recap.hurt();
        }
        if (attacker != null && !attacker.equals(victim) && byPlayer.get(attacker.getUniqueId()) == game) {
            game.hit(victim, attacker);
            game.landed(victim, attacker, event);
        } else {
            game.landed(victim, null, event);
        }
    }

    /** A golden apple eaten in the fight: counted for the recap. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        PracticeGame game = byPlayer.get(event.getPlayer().getUniqueId());
        if (game != null && game.phase == PracticeGame.Phase.PLAYING && event.getItem() != null
                && event.getItem().getType() == Material.GOLDEN_APPLE) {
            game.recap.ate(event.getPlayer().getUniqueId());
        }
    }

    /** A death the damage rule did not catch (a /kill): the same death, once respawned. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        final Player victim = event.getEntity();
        final PracticeGame game = byPlayer.get(victim.getUniqueId());
        if (game == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setDeathMessage(null);
        event.setKeepInventory(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!victim.isOnline() || byPlayer.get(victim.getUniqueId()) != game) {
                return;
            }
            if (victim.isDead()) {
                victim.spigot().respawn();
            }
            game.died(victim);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        PracticeGame game = byPlayer.get(event.getPlayer().getUniqueId());
        if (game != null) {
            event.setRespawnLocation(game.spawnOf(event.getPlayer().getUniqueId()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (byPlayer.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** No health comes back by itself in a mode where it tells the score (the block placement). */
    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        PracticeGame game = byPlayer.get(event.getEntity().getUniqueId());
        if (game != null && !game.regains()) {
            event.setCancelled(true);
        }
    }

    // --- Blocks ---------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PracticeGame game = byPlayer.get(player.getUniqueId());
        if (game == null) {
            return;
        }
        if (game.phase != PracticeGame.Phase.PLAYING || spectating(player)
                || !game.canPlace(player, event.getBlockPlaced(), event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaced(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PracticeGame game = byPlayer.get(player.getUniqueId());
        if (game == null) {
            return;
        }
        Block block = event.getBlockPlaced();
        game.arena.placed(block, event.getBlockReplacedState());
        if (game.phase == PracticeGame.Phase.PLAYING && !spectating(player)) {
            game.recap.placed(player.getUniqueId());
        }
        if (game.refills()) {
            refill(player, event.getItemInHand());
        }
        game.placed(player, block);
    }

    /** The stack in the hand is full again on the next tick: the blocks never run out. */
    private void refill(Player player, ItemStack hand) {
        if (hand == null || hand.getType() == Material.AIR) {
            return;
        }
        final int slot = player.getInventory().getHeldItemSlot();
        final ItemStack full = hand.clone();
        full.setAmount(Math.max(hand.getAmount(), 64));
        final PracticeGame game = byPlayer.get(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || byPlayer.get(player.getUniqueId()) != game || game.phase == PracticeGame.Phase.ENDED) {
                return;
            }
            ItemStack now = player.getInventory().getItem(slot);
            if (now == null || now.getType() == Material.AIR || now.isSimilar(full)) {
                player.getInventory().setItem(slot, full);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PracticeGame game = byPlayer.get(player.getUniqueId());
        if (game == null) {
            return;
        }
        if (game.phase != PracticeGame.Phase.PLAYING || spectating(player) || !game.canBreak(player, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBroken(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PracticeGame game = byPlayer.get(player.getUniqueId());
        if (game == null) {
            return;
        }
        ItemStack drop = game.dropOf(event.getBlock());
        game.arena.removed(event.getBlock());
        game.broke(player, event.getBlock());
        if (drop != null) {
            // on the ground, as a block broken anywhere: the only items the arenas let lie there
            dropping = true;
            try {
                game.dropped(world.dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), drop));
            } finally {
                dropping = false;
            }
        }
    }

    /** True while a game drops what a broken block leaves (PracticeGame#dropOf): that item may lie there. */
    private boolean dropping;

    /** Blasts only take the blocks placed in the round (and the defense of a map's beds, freed as if placed), never the blast-proof ones; a TNT pushes as its mode says. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!event.getLocation().getWorld().equals(world)) {
            return;
        }
        event.setYield(0f);
        PracticeGame game = gameAt(event.getLocation());
        boolean fireball = event.getEntity() instanceof Fireball;
        if (game != null && event.getEntity() instanceof TNTPrimed) {
            Player owner = playerBehind(event.getEntity());
            game.tntBlast(event.getLocation(), owner != null && byPlayer.get(owner.getUniqueId()) == game ? owner : null);
        }
        for (Iterator<Block> it = event.blockList().iterator(); it.hasNext();) {
            Block block = it.next();
            if (game == null || !game.arena.isPlaced(block) || blastProof(block.getType())
                    || (fireball && block.getType() == Material.ENDER_STONE)) {
                it.remove();
            } else {
                game.arena.removed(block);
            }
        }
    }

    private static boolean blastProof(Material type) {
        for (Material proof : BLAST_PROOF) {
            if (proof == type) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (byPlayer.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // --- Items and entities ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (byPlayer.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        PracticeGame game = byPlayer.get(event.getPlayer().getUniqueId());
        if (game == null) {
            return;
        }
        // only what the game's broken blocks left, by its players in the fight
        if (!game.ownsDrop(event.getItem()) || game.phase != PracticeGame.Phase.PLAYING || spectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Nothing wears out in a practice game: the swords, the armour, the shears, the bows... of the
     * kits and of the lobby's kit editor never lose durability, so they never break.
     */
    @EventHandler(ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent event) {
        if (byPlayer.containsKey(event.getPlayer().getUniqueId()) || event.getPlayer().getWorld().equals(world)) {
            event.setCancelled(true);
        }
    }

    /** Every few ticks: the items of the players in a game unbreakable (see Unbreakable). */
    void keepUnbreakable() {
        for (UUID id : byPlayer.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                Unbreakable.apply(player);
            }
        }
    }

    /** Nothing lies on the ground in the arenas (broken blocks, beds...) but what a game drops (Fireball Fight). */
    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (event.getEntity().getWorld().equals(world) && !dropping) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PracticeGame game = byPlayer.get(player.getUniqueId());
        if (game == null || event.getAction() == Action.PHYSICAL) {
            return;
        }
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && EndOfGame.clicked(plugin, player, event.getItem())) {
            // the end of a duel: the Play again paper or the Leave bed
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.BED_BLOCK) {
            // no sleeping, no "you can only sleep at night"
            event.setUseInteractedBlock(Event.Result.DENY);
            ItemStack item = event.getItem();
            if (item != null && item.getType().isBlock()) {
                // denying the bed alone also stops the block in hand from being placed against it; and only
                // with shift held, as on Hypixel: a click alone on the bed places nothing
                event.setUseItemInHand(player.isSneaking() ? Event.Result.ALLOW : Event.Result.DENY);
            }
        }
        if (spectating(player)) {
            return;
        }
        game.inputFrom(player);
        game.interact(player, event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (byPlayer.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** The armour of a target (an armour stand) is not the players' to take: its own event. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmourStand(PlayerArmorStandManipulateEvent event) {
        if (byPlayer.containsKey(event.getPlayer().getUniqueId()) || byEntity.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** No mob comes into the arenas but the games' own (no zombie reinforcements...). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity().getWorld().equals(world) && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        PracticeGame game = byPlayer.get(event.getPlayer().getUniqueId());
        if (game != null && game.phase == PracticeGame.Phase.PLAYING) {
            game.inputFrom(event.getPlayer());
            game.swung(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player)) {
            return;
        }
        Player shooter = (Player) projectile.getShooter();
        PracticeGame game = byPlayer.get(shooter.getUniqueId());
        if (game == null) {
            return;
        }
        if (game.phase != PracticeGame.Phase.PLAYING || spectating(shooter)) {
            event.setCancelled(true);
            if (projectile instanceof EnderPearl) {
                // the server took the pearl from the hand before this event: thrown during the countdown, it comes back
                shooter.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
            }
            return;
        }
        game.launched(shooter, projectile, event);
    }

    /** A fireball lands: it pushes and hurts the players around it, as on Hypixel. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLand(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!projectile.getWorld().equals(world)) {
            return;
        }
        Player shooter = projectile.getShooter() instanceof Player ? (Player) projectile.getShooter() : null;
        PracticeGame game = shooter == null ? byEntity.get(projectile.getUniqueId()) : byPlayer.get(shooter.getUniqueId());
        if (game == null) {
            game = gameAt(projectile.getLocation());
        }
        if (game == null) {
            return;
        }
        if (projectile instanceof Fireball) {
            game.blast((Fireball) projectile, shooter != null && byPlayer.get(shooter.getUniqueId()) == game ? shooter : null);
        }
        if (shooter != null && byPlayer.get(shooter.getUniqueId()) == game) {
            game.projectileHit(shooter, projectile);
        } else if (shooter == null && byEntity.get(projectile.getUniqueId()) == game) {
            game.ownLanded(projectile);
        }
    }

    /** The maps copied into the arenas stay as they are: no sand or gravel falls (with a bot on it)... */
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock && event.getBlock().getWorld().equals(world)) {
            event.setCancelled(true);
        }
    }

    /** ...and no water or lava runs out of them. */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (event.getBlock().getWorld().equals(world)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        Entity entity = event.getEntity();
        if (byEntity.containsKey(entity.getUniqueId()) || (entity instanceof Player && byPlayer.containsKey(entity.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        PracticeGame game = byEntity.get(event.getEntity().getUniqueId());
        if (game != null && (event.getTarget() == null || !game.mayTarget(event.getEntity(), event.getTarget()))) {
            if (event.getTarget() != null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        PracticeGame game = byEntity.remove(event.getEntity().getUniqueId());
        if (game != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            game.entityDied(event.getEntity());
        }
    }

    /** The menus of the games: nothing in them moves, a click goes to the game. */
    @EventHandler(priority = EventPriority.LOW)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GameMenu)) {
            return;
        }
        event.setCancelled(true);
        GameMenu menu = (GameMenu) event.getView().getTopInventory().getHolder();
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (byPlayer.get(player.getUniqueId()) == menu.game && menu.game.phase != PracticeGame.Phase.ENDED
                && slot >= 0 && slot < event.getView().getTopInventory().getSize()) {
            // the menu closes and the player moves after the click is over, not inside it
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (byPlayer.get(player.getUniqueId()) == menu.game && menu.game.phase != PracticeGame.Phase.ENDED) {
                    menu.game.menuClick(player, slot);
                }
            });
        }
    }

    /** A menu of a game closed (by the player, or by a teleport): the game hears it at the next tick. */
    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GameMenu) || !(event.getPlayer() instanceof Player)) {
            return;
        }
        GameMenu menu = (GameMenu) event.getInventory().getHolder();
        Player player = (Player) event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && byPlayer.get(player.getUniqueId()) == menu.game && menu.game.phase != PracticeGame.Phase.ENDED) {
                menu.game.menuClosed(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GameMenu) {
            event.setCancelled(true);
        }
    }

    /**
     * A click in the player's own inventory during a training: the training waits (no hit, no new
     * attempt that would give the kit back) until they close it.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onArrange(InventoryClickEvent event) {
        if (event.getAction() != InventoryAction.NOTHING) {
            arranging(event.getView().getType(), event.getWhoClicked().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArrangeDrag(InventoryDragEvent event) {
        arranging(event.getView().getType(), event.getWhoClicked().getUniqueId());
    }

    private void arranging(InventoryType view, UUID player) {
        PracticeGame game = byPlayer.get(player);
        if (view == InventoryType.CRAFTING && game instanceof Training && game.phase != PracticeGame.Phase.ENDED) {
            ((Training) game).arranging();
        }
    }

    /** The player closed their own inventory in a training: where they put the controls is kept. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onArranged(InventoryCloseEvent event) {
        if (event.getView().getType() != InventoryType.CRAFTING || !(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        PracticeGame game = byPlayer.get(player.getUniqueId());
        if (game instanceof Training && game.phase != PracticeGame.Phase.ENDED) {
            ((Training) game).inventoryClosed(player);
        }
    }

    /** Leaving the server in the middle of a game loses it. Before RagoLobby forgets the player. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PracticeGame game = byPlayer.get(event.getPlayer().getUniqueId());
        if (game != null) {
            game.leave(event.getPlayer(), false);
        }
    }
}
