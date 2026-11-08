package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Block placement fight, 1v1 or 2v2: two small platforms over the void, a wooden sword and
 * blocks that never run out. Build, clutch, block in, knock the others off: a fall or a death is a
 * point for the other side. First to 5.
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
        int half = half();
        Shapes.floor(arena, -9, -half, -7, half, 0, Material.SANDSTONE, 2);
        Shapes.floor(arena, 7, -half, 9, half, 0, Material.SANDSTONE, 2);
        arena.build(-8, -1, 0, Material.STAINED_CLAY, Shapes.RED);
        arena.build(8, -1, 0, Material.STAINED_CLAY, Shapes.BLUE);
    }

    @Override
    Location spawnOf(UUID player) {
        int side = sideOf(player);
        double z = across(player) * 2 + 0.5;
        return side == 0 ? arena.at(-7.5, 1, z, -90f) : arena.at(8.5, 1, z, 90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 10;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.WOOD_SWORD), new ItemStack(Material.SANDSTONE, 64),
            new ItemStack(Material.SANDSTONE, 64)};
    }

    @Override
    void armour(Player player) {
        leather(player, COLOURS[Math.max(0, sideOf(player.getUniqueId()))]);
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        return event.getCause() != EntityDamageEvent.DamageCause.FALL;
    }

    @Override
    boolean refills() {
        return true;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return arena.contains(block, 30, 12, 16);
    }
}
