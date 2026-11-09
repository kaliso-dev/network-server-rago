package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
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
 * chestplate of the side, all Protection IV. Build, clutch, break their blocks, knock them off: a
 * fall or a death is a point for the other side. First to 5.
 */
final class BlockFight extends Duel {

    BlockFight(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target) {
        super(host, mode, arena, teams, target);
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

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        return event.getCause() != EntityDamageEvent.DamageCause.FALL;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        if (arena.map() != null) {
            return arena.contains(block, 45, 12, 25);
        }
        return arena.contains(block, 30, 12, 16);
    }
}
