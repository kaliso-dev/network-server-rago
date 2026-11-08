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
 * Top fight, 1v1 or 2v2 (or a party's private one): a Bed Wars island whose bed has a three
 * layer defense, end stone, wood and wool, ready made. Both sides start at its foot, climb it and
 * fight on top, in Ranked Bedwars gear: whoever holds the top wins the fights. A death or a fall
 * into the void is a point for the other side. First to 3.
 */
final class TopFight extends Duel {

    TopFight(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target) {
        super(host, mode, arena, teams, target);
    }

    @Override
    String title() {
        return "Top fight";
    }

    @Override
    void build() {
        Shapes.island(arena, -6, -5, 7, 5, Material.WOOD, 0, Material.LOG, 0);
        Shapes.bed(arena, 0, 1, 0, 1, 0);
        Shapes.buildDefense(arena, Shapes.defense(0, 1, 0, 1, 0, 3),
                new Material[] {Material.ENDER_STONE, Material.WOOD, Material.WOOL},
                new int[] {0, 0, Shapes.WHITE});
    }

    @Override
    Location spawnOf(UUID player) {
        int side = sideOf(player);
        double z = across(player) * 2 + 0.5;
        return side == 0 ? arena.at(-4.5, 1, z, -90f) : arena.at(5.5, 1, z, 90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 8;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.IRON_SWORD), new ItemStack(Material.WOOL, 64),
            new ItemStack(Material.GOLDEN_APPLE, 3), new ItemStack(Material.ENDER_PEARL)};
    }

    @Override
    void armour(Player player) {
        ironArmour(player, COLOURS[Math.max(0, sideOf(player.getUniqueId()))]);
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        return true;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return arena.contains(block, 25, 10, 14);
    }
}
