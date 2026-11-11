package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Fireball fight: each side has an island and a bed under a defense of wood and wool. The kit of
 * the owner's maps: a stone sword, a pickaxe and an axe (Efficiency I), 6 fireballs, wool, end
 * stone, 2 TNT, shears and ladders. While its bed stands, a dead player comes back in 3 seconds;
 * once it is broken, a death is final. The last side standing wins. Fireballs and TNT as in Bed
 * Wars: they push, and they only blow up the blocks the players placed and the defense of the
 * beds (never end stone for a fireball); neither does damage, and nobody takes fall damage. On a
 * map of fireball.cartes (config.yml), each side starts on its start and the beds are the map's
 * own: a bed is the side's whose start is nearer, and its defense on the map (wood, end stone) is
 * broken and blown up as if placed. The wood and end stone a player breaks go into their inventory.
 */
final class FireballFight extends Duel {

    private static final int RESPAWN_SECONDS = 3;
    private static final int TNT_FUSE_TICKS = 52;
    /** Each side's bed: its foot, its head one block further from the middle. */
    private static final int BED_X = 15;

    private final boolean[] beds = {true, true};
    private final Set<UUID> respawning = new HashSet<>();
    private final Map<UUID, Long> lastFireball = new HashMap<>();
    /** The players told once that, their hotbar full, the blocks given back go into their inventory. */
    private final Set<UUID> toldFull = new HashSet<>();

    FireballFight(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams, 1);
    }

    @Override
    String title() {
        return "Fireball fight";
    }

    @Override
    void intro(Player player) {
        super.intro(player);
        player.sendMessage(ChatColor.GRAY + "Break their bed with your fireballs and TNT, then finish them: the last side standing wins.");
    }

    @Override
    String goal() {
        return "break the bed";
    }

    /** True on a map of the owner's, with a start for each side. */
    private boolean onMap() {
        return arena.map() != null && arena.map().starts.length >= 2;
    }

    @Override
    void build() {
        if (arena.map() != null) {
            return;
        }
        Shapes.island(arena, -19, -5, -11, 5, Material.STAINED_CLAY, Shapes.RED, Material.HARD_CLAY, 0);
        Shapes.island(arena, 11, -5, 19, 5, Material.STAINED_CLAY, Shapes.BLUE, Material.HARD_CLAY, 0);
        Shapes.island(arena, -2, -2, 2, 2, Material.WOOL, Shapes.WHITE, Material.HARD_CLAY, 0);
    }

    @Override
    @SuppressWarnings("deprecation") // Material#getId: the block id of the beds in the copy of the map
    void roundReset() {
        super.roundReset();
        respawning.clear();
        beds[0] = true;
        beds[1] = true;
        if (onMap()) {
            // the map's own beds, put back with the rest of the map at each restore
            beds[0] = false;
            beds[1] = false;
            List<int[]> mapBeds = arena.map().copy.find(Material.BED_BLOCK.getId());
            for (int[] bed : mapBeds) {
                beds[sideAt(bed[0], bed[2])] = true;
            }
            // the map's defense on the beds (wood, end stone): broken and blown up as if placed
            for (int[] at : MapDefense.of(mapBeds, (x, y, z) -> arena.block(x, y, z).getType())) {
                arena.free(arena.block(at[0], at[1], at[2]));
            }
            return;
        }
        for (int side = 0; side < 2; side++) {
            int sign = side == 0 ? -1 : 1;
            Shapes.putBed(arena, sign * BED_X, 1, 0, sign, 0);
            Shapes.putDefense(arena, Shapes.defense(sign * BED_X, 1, 0, sign, 0, 2),
                    new Material[] {Material.WOOD, Material.WOOL}, new int[] {0, WOOL[side]});
        }
    }

    @Override
    Location spawnOf(UUID player) {
        int side = sideOf(player);
        Location start = arena.mapStart(Math.max(0, side));
        if (start != null && onMap()) {
            // teammates side by side, across the way they look
            double across = across(player) * 2;
            double yaw = Math.toRadians(start.getYaw());
            return start.add(-Math.cos(yaw) * across, 0, -Math.sin(yaw) * across);
        }
        double z = across(player) * 2 + 0.5;
        return side == 0 ? arena.at(-11.5, 1, z, -90f) : arena.at(12.5, 1, z, 90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - (arena.map() != null ? 20 : 10);
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.STONE_SWORD), new ItemStack(Material.WOOD_PICKAXE),
            new ItemStack(Material.WOOD_AXE), new ItemStack(Material.FIREBALL, 6), new ItemStack(Material.WOOL, 64),
            new ItemStack(Material.ENDER_STONE, 8), new ItemStack(Material.TNT, 2), new ItemStack(Material.SHEARS),
            new ItemStack(Material.LADDER, 8)};
    }

    /** Efficiency I on the pickaxe and the axe, wherever the player put them. */
    @Override
    void afterKit(Player player) {
        ItemStack[] items = player.getInventory().getContents();
        BlockFight.enchantTools(items);
        player.getInventory().setContents(items);
    }

    @Override
    void armour(Player player) {
        leather(player, COLOURS[Math.max(0, sideOf(player.getUniqueId()))]);
    }

    /** No fall damage: the fights are about the push of the fireballs. */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        return !respawning.contains(victim.getUniqueId()) && event.getCause() != EntityDamageEvent.DamageCause.FALL;
    }

    /** The fireballs only push (their hit still counts for the kill): no damage. */
    @Override
    void blasted(Player player, Player thrower, double damage) {
    }

    /** The TNT only pushes too, as a TNT would (its hit counts for the kill): no damage. */
    @Override
    boolean tntHurts() {
        return false;
    }

    @Override
    void tntBlast(Location centre, Player owner) {
        tntPush(centre, owner);
    }

    @Override
    void interact(Player player, PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FIREBALL
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = lastFireball.get(player.getUniqueId());
        if (phase != Phase.PLAYING || (last != null && now - last < Fireballs.COOLDOWN_MS)) {
            return;
        }
        lastFireball.put(player.getUniqueId(), now);
        Fireballs.launch(player);
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        if (block.getType() == Material.TNT) {
            // lit at once, as in Bed Wars
            ItemStack hand = player.getItemInHand();
            if (hand.getAmount() > 1) {
                hand.setAmount(hand.getAmount() - 1);
            } else {
                player.setItemInHand(null);
            }
            TNTPrimed tnt = block.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5), TNTPrimed.class);
            tnt.setFuseTicks(TNT_FUSE_TICKS);
            host.litBy(tnt, player);
            return false;
        }
        if (arena.map() != null) {
            return arena.contains(block, 95, 20, 30);
        }
        return arena.contains(block, 32, 12, 16);
    }

    /** The side whose bed this block is, from where it stands. */
    private int bedSide(Block block) {
        return sideAt(block.getX() - arena.centreX(), block.getZ() - arena.centreZ());
    }

    /**
     * The side of this place (x, z from the centre): on a map, the side whose start is nearer;
     * on the plugin's islands, the red side west of the middle.
     */
    private int sideAt(double x, double z) {
        if (!onMap()) {
            return x < 0 ? 0 : 1;
        }
        return nearer(arena.map().starts[0], arena.map().starts[1], x + 0.5, z + 0.5);
    }

    /** 0 if the point (x, z) is nearer the first start than the second one, else 1. */
    static int nearer(double[] first, double[] second, double x, double z) {
        double d0 = (first[0] - x) * (first[0] - x) + (first[2] - z) * (first[2] - z);
        double d1 = (second[0] - x) * (second[0] - x) + (second[2] - z) * (second[2] - z);
        return d0 <= d1 ? 0 : 1;
    }

    @Override
    boolean canBreak(Player player, Block block) {
        if (block.getType() == Material.BED_BLOCK) {
            if (bedSide(block) == sideOf(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You can't break your own bed!");
                return false;
            }
            return true;
        }
        return arena.isPlaced(block);
    }

    @Override
    void broke(Player player, Block block) {
        if (block.getType() != Material.BED_BLOCK) {
            given(player, giveBack(player, block));
            return;
        }
        int side = bedSide(block);
        if (!beds[side]) {
            return;
        }
        beds[side] = false;
        broadcast(ChatColor.BOLD + "BED DESTRUCTION > " + ChatColor.RESET + CHAT[side] + TEAMS[side] + " bed"
                + ChatColor.GRAY + " was destroyed by " + CHAT[1 - side] + player.getDisplayName() + ChatColor.GRAY + "!", Sound.ENDERDRAGON_GROWL);
        for (Player victim : online(side)) {
            title(victim, ChatColor.RED + "BED DESTROYED!", ChatColor.GRAY + "You will no longer respawn!");
        }
        sidebars();
    }

    /** The wood and the end stone the blast of a player's TNT or fireball takes go to them too. */
    @Override
    void blasted(Player owner, Block block) {
        if (block.getType() != Material.BED_BLOCK) {
            given(owner, giveBack(owner, block));
        }
    }

    /** Once a game, a player whose block given back went into the inventory (their hotbar full) is told where. */
    private void given(Player player, boolean inHotbar) {
        if (!inHotbar && toldFull.add(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Your hotbar is full: the wood and end stone you break go into your inventory (E).");
        }
    }

    /**
     * The wood and the end stone a player breaks (theirs, the other side's, the defense of a bed)
     * go to them, in their hotbar where it has room (on a stack of the same block, or an empty
     * slot): nothing lies on the ground in the arenas. The hotbar full (the kit takes its nine
     * slots), the block goes into the inventory; that full too, it is lost. False when the block
     * did not go into the hotbar.
     */
    @SuppressWarnings("deprecation") // Block#getData: the kind of the wood
    static boolean giveBack(Player player, Block block) {
        ItemStack item = kept(block.getType(), block.getData());
        if (item == null) {
            return true;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] hotbar = new ItemStack[HOTBAR];
        for (int slot = 0; slot < HOTBAR; slot++) {
            hotbar[slot] = inventory.getItem(slot);
        }
        int slot = hotbarSlot(hotbar, item);
        ItemStack there = slot < 0 ? null : hotbar[slot];
        if (slot >= 0 && (there == null || there.getType() == Material.AIR)) {
            inventory.setItem(slot, item);
            return true;
        }
        if (there != null && there.isSimilar(item)) {
            there.setAmount(there.getAmount() + 1);
            inventory.setItem(slot, there);
            return true;
        }
        inventory.addItem(item);
        return false;
    }

    private static final int HOTBAR = 9;

    /**
     * The hotbar slot a block given back goes to: the first stack of the same block (and kind of
     * wood) with room, else the first empty slot; -1 when there is neither.
     */
    static int hotbarSlot(ItemStack[] hotbar, ItemStack item) {
        int empty = -1;
        for (int slot = 0; slot < hotbar.length; slot++) {
            ItemStack there = hotbar[slot];
            if (there == null || there.getType() == Material.AIR) {
                if (empty < 0) {
                    empty = slot;
                }
            } else if (there.getType() == item.getType() && there.getDurability() == item.getDurability()
                    && there.getAmount() < there.getType().getMaxStackSize()) {
                return slot;
            }
        }
        return empty;
    }

    /** The item a broken block of this type gives back: wood (of its kind) and end stone; null for another. */
    static ItemStack kept(Material type, byte data) {
        if (type == Material.WOOD) {
            return new ItemStack(Material.WOOD, 1, data);
        }
        return type == Material.ENDER_STONE ? new ItemStack(Material.ENDER_STONE) : null;
    }

    @Override
    void fell(Player player) {
        knockedOut(player, true);
    }

    @Override
    void lethal(Player victim, Player killer) {
        knockedOut(victim, false);
    }

    private void knockedOut(Player player, boolean fell) {
        UUID id = player.getUniqueId();
        int side = sideOf(id);
        if (side < 0 || respawning.contains(id) || out.contains(id) || phase != Phase.PLAYING) {
            player.teleport(spawnOf(id));
            return;
        }
        UUID by = killerOf(id);
        deathMessage(player, by, fell);
        if (by != null && counts()) {
            plugin.lobby().recordKill(mode.id(), by, id);
        }
        forgetHits(id);
        if (!beds[side]) {
            out.add(id);
            broadcast(CHAT[side] + player.getDisplayName() + ChatColor.GRAY + " is " + ChatColor.RED + "eliminated" + ChatColor.GRAY + "!", null);
            if (!alive(side)) {
                points[1 - side]++;
                finish(1 - side, null);
                return;
            }
            spectate(player);
            return;
        }
        respawning.add(id);
        spectate(player);
        for (int s = 0; s < RESPAWN_SECONDS; s++) {
            final int left = RESPAWN_SECONDS - s;
            later(20L * s, () -> {
                if (respawning.contains(id) && host.gameOf(id) == this) {
                    title(player, ChatColor.RED + "You died!", ChatColor.YELLOW + "Respawning in " + left + " s");
                }
            });
        }
        later(20L * RESPAWN_SECONDS, () -> {
            if (respawning.remove(id) && host.gameOf(id) == this && player.isOnline()) {
                placeAtStart(player);
            }
        });
    }

    @Override
    boolean alive(int side) {
        for (Player player : online(side)) {
            if (!out.contains(player.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    void left(Player player) {
        respawning.remove(player.getUniqueId());
        int side = sideOf(player.getUniqueId());
        if (phase == Phase.PLAYING && !alive(side)) {
            finish(1 - side, null);
        }
    }

    @Override
    String score(int side) {
        if (beds[side]) {
            return ChatColor.GREEN + "✔ bed";
        }
        int alive = 0;
        for (Player player : online(side)) {
            if (!out.contains(player.getUniqueId())) {
                alive++;
            }
        }
        return alive > 0 ? ChatColor.YELLOW + "" + alive + " left" : ChatColor.RED + "✘";
    }

    @Override
    String result(int winner) {
        return "";
    }
}
