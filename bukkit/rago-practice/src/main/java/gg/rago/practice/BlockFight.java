package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Block placement fight, 1v1 or 2v2, on the owner's block placement maps (block-fight.cartes in
 * config.yml: each side starts on its gold block) or, without them, two small platforms over the
 * void. The kit of the maps: a wooden sword (Sharpness I), a pickaxe and an axe (Efficiency I),
 * shears, two stacks of wool, end stone and ladders; iron boots and leggings, the helmet and
 * chestplate of the side, all Protection IV. As on Minemen, the points are the hits: each hit
 * landed on the other side is a point (a hit does no damage, only its knockback), and a fall into
 * the void gives the other side {@value #VOID_BONUS} points and sends the one who fell back on their
 * gold block at once (the others fight on, the blocks stay). First to 100 by default
 * (points-pour-gagner.block-fight).
 */
final class BlockFight extends Duel {

    /** The points the other side gets for a fall into the void. */
    static final int VOID_BONUS = 10;

    BlockFight(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target) {
        super(host, mode, arena, teams, target);
    }

    /** The score of the other side after a fall of a player: the bonus of the void. */
    static int afterFall(int points) {
        return points + VOID_BONUS;
    }

    @Override
    String title() {
        return "Block fight";
    }

    /** Half the width of a platform: wider for two players a side. */
    private int half() {
        return mode.playersPerTeam() > 1 ? 2 : 1;
    }

    @Override
    void build() {
        if (arena.map() != null) {
            return;
        }
        int half = half();
        Shapes.floor(arena, -9, -half, -7, half, 0, Material.SANDSTONE, 2);
        Shapes.floor(arena, 7, -half, 9, half, 0, Material.SANDSTONE, 2);
        arena.build(-8, -1, 0, Material.STAINED_CLAY, Shapes.RED);
        arena.build(8, -1, 0, Material.STAINED_CLAY, Shapes.BLUE);
    }

    @Override
    Location spawnOf(UUID player) {
        int side = sideOf(player);
        Location start = arena.mapStart(Math.max(0, side));
        if (start != null) {
            // teammates side by side, across the way they look
            double across = across(player) * 2;
            double yaw = Math.toRadians(start.getYaw());
            return start.add(-Math.cos(yaw) * across, 0, -Math.sin(yaw) * across);
        }
        double z = across(player) * 2 + 0.5;
        return side == 0 ? arena.at(-7.5, 1, z, -90f) : arena.at(8.5, 1, z, 90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 10;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.WOOD_SWORD), new ItemStack(Material.WOOD_PICKAXE),
            new ItemStack(Material.WOOD_AXE), new ItemStack(Material.SHEARS), new ItemStack(Material.WOOL, 64),
            new ItemStack(Material.ENDER_STONE, 12), new ItemStack(Material.WOOL, 64), new ItemStack(Material.LADDER, 16)};
    }

    /** The enchantments of the kit of the maps, on the tools wherever the player put them. */
    @Override
    void afterKit(Player player) {
        ItemStack[] items = player.getInventory().getContents();
        enchantTools(items);
        player.getInventory().setContents(items);
    }

    static void enchantTools(ItemStack[] items) {
        for (ItemStack item : items) {
            if (item == null) {
                continue;
            }
            if (item.getType() == Material.WOOD_SWORD) {
                item.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 1);
            } else if (item.getType() == Material.WOOD_PICKAXE || item.getType() == Material.WOOD_AXE) {
                item.addUnsafeEnchantment(Enchantment.DIG_SPEED, 1);
            }
        }
    }

    @Override
    void armour(Player player) {
        ironArmour(player, COLOURS[Math.max(0, sideOf(player.getUniqueId()))]);
        ItemStack[] pieces = player.getInventory().getArmorContents();
        for (ItemStack piece : pieces) {
            if (piece != null && piece.getType() != Material.AIR) {
                piece.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
            }
        }
        player.getInventory().setArmorContents(pieces);
    }

    /** A hit of the other side: its knockback, no damage; nothing else hurts. */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        if (attacker == null) {
            return false;
        }
        event.setDamage(0.0);
        return true;
    }

    /** A hit landed: a point for the side of the one who landed it. */
    @Override
    void landed(Player victim, Player attacker, EntityDamageEvent event) {
        if (attacker == null || phase != Phase.PLAYING || sameSide(victim.getUniqueId(), attacker.getUniqueId())) {
            return;
        }
        int side = sideOf(attacker.getUniqueId());
        if (side < 0) {
            return;
        }
        points[side]++;
        if (points[side] >= target) {
            finish(side, null);
        }
    }

    /** A fall into the void: +10 for the other side (the one who knocked them off), and the one who fell back on their start at once. */
    @Override
    void fell(Player player) {
        UUID id = player.getUniqueId();
        int side = sideOf(id);
        if (side < 0 || phase != Phase.PLAYING) {
            player.teleport(spawnOf(id));
            player.setFallDistance(0f);
            return;
        }
        UUID by = killerOf(id);
        deathMessage(player, by, true);
        if (by != null && counts()) {
            plugin.lobby().recordKill(mode.id(), by, id);
        }
        int other = 1 - side;
        points[other] = afterFall(points[other]);
        for (Player each : online()) {
            int mine = sideOf(each.getUniqueId());
            each.sendMessage((mine == other ? ChatColor.GREEN + "+" + VOID_BONUS + " for your team" : ChatColor.RED + "+"
                    + VOID_BONUS + " for " + TEAMS[other]) + ChatColor.GRAY + " (" + points[Math.max(0, mine)] + " - "
                    + points[1 - Math.max(0, mine)] + ")");
            each.playSound(each.getLocation(), mine == other ? Sound.ORB_PICKUP : Sound.NOTE_BASS, 1f, 1f);
        }
        if (points[other] >= target) {
            finish(other, null);
            return;
        }
        // only the one who fell goes back, at once, to their start: the others fight on, the blocks stay
        forgetHits(id);
        player.setFallDistance(0f);
        player.teleport(spawnOf(id));
    }

    @Override
    void extraLines(UUID viewer, List<String> lines) {
        lines.add(ChatColor.GRAY + "A hit: +1, a knock into the void: +" + VOID_BONUS);
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        if (arena.map() != null) {
            return arena.contains(block, 45, 12, 25);
        }
        return arena.contains(block, 30, 12, 16);
    }
}
