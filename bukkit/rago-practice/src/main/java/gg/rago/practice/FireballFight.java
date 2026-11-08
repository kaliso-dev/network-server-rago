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

/**
 * Fireball fight: each side has an island and a bed under a defense of wood and wool, fireballs,
 * TNT, wool and a sword. While its bed stands, a dead player comes back in 3 seconds; once it is
 * broken, a death is final. The last side standing wins. Fireballs and TNT as in Bed Wars: they
 * push, they hurt a little, and they only blow up wool, wood and the blocks the players placed.
 */
final class FireballFight extends Duel {

    private static final int RESPAWN_SECONDS = 3;
    private static final int TNT_FUSE_TICKS = 52;
    /** Each side's bed: its foot, its head one block further from the middle. */
    private static final int BED_X = 15;

    private final boolean[] beds = {true, true};
    private final Set<UUID> respawning = new HashSet<>();
    private final Map<UUID, Long> lastFireball = new HashMap<>();

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

    @Override
    void build() {
        Shapes.island(arena, -19, -5, -11, 5, Material.STAINED_CLAY, Shapes.RED, Material.HARD_CLAY, 0);
        Shapes.island(arena, 11, -5, 19, 5, Material.STAINED_CLAY, Shapes.BLUE, Material.HARD_CLAY, 0);
        Shapes.island(arena, -2, -2, 2, 2, Material.WOOL, Shapes.WHITE, Material.HARD_CLAY, 0);
    }

    @Override
    void roundReset() {
        super.roundReset();
        respawning.clear();
        beds[0] = true;
        beds[1] = true;
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
        double z = across(player) * 2 + 0.5;
        return side == 0 ? arena.at(-11.5, 1, z, -90f) : arena.at(12.5, 1, z, 90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 10;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.IRON_SWORD), new ItemStack(Material.FIREBALL, 16),
            new ItemStack(Material.TNT, 4), new ItemStack(Material.WOOL, 64), new ItemStack(Material.SHEARS)};
    }

    @Override
    void armour(Player player) {
        leather(player, COLOURS[Math.max(0, sideOf(player.getUniqueId()))]);
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        return !respawning.contains(victim.getUniqueId());
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
        return arena.contains(block, 32, 12, 16);
    }

    /** The side whose bed this block is, from where it stands. */
    private int bedSide(Block block) {
        return block.getX() < arena.centreX() ? 0 : 1;
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
            return;
        }
        int side = bedSide(block);
        if (!beds[side]) {
            return;
        }
        beds[side] = false;
        broadcast(ChatColor.BOLD + "BED DESTRUCTION > " + ChatColor.RESET + CHAT[side] + TEAMS[side] + " bed"
                + ChatColor.GRAY + " was destroyed by " + CHAT[1 - side] + player.getName() + ChatColor.GRAY + "!", Sound.ENDERDRAGON_GROWL);
        for (Player victim : online(side)) {
            title(victim, ChatColor.RED + "BED DESTROYED!", ChatColor.GRAY + "You will no longer respawn!");
        }
        sidebars();
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
            broadcast(CHAT[side] + player.getName() + ChatColor.GRAY + " is " + ChatColor.RED + "eliminated" + ChatColor.GRAY + "!", null);
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
