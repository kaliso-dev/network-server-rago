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
 * Bridge fight: two Bed Wars islands 27 blocks apart, in Ranked Bedwars gear (iron armour, stone
 * sword, wool, shears, golden apples). Bridge to the other one and fight on the bridge, real damage:
 * a death or a fall is a point for the other side. First to 3.
 */
final class BridgeFight extends Duel {

    BridgeFight(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target) {
        super(host, mode, arena, teams, target);
    }

    @Override
    String title() {
        return "Bridge fight";
    }

    @Override
    void build() {
        Shapes.island(arena, -20, -3, -14, 3, Material.STAINED_CLAY, Shapes.RED, Material.HARD_CLAY, 0);
        Shapes.island(arena, 14, -3, 20, 3, Material.STAINED_CLAY, Shapes.BLUE, Material.HARD_CLAY, 0);
        // the edge each side bridges from
        Shapes.floor(arena, -14, -1, -14, 1, 0, Material.WOOL, Shapes.RED);
        Shapes.floor(arena, 14, -1, 14, 1, 0, Material.WOOL, Shapes.BLUE);
    }

    @Override
    Location spawnOf(UUID player) {
        int side = sideOf(player);
        double z = across(player) * 1.5 + 0.5;
        return side == 0 ? arena.at(-16.5, 1, z, -90f) : arena.at(17.5, 1, z, 90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 10;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.STONE_SWORD), new ItemStack(Material.WOOL, 64),
            new ItemStack(Material.WOOL, 64), new ItemStack(Material.SHEARS), new ItemStack(Material.GOLDEN_APPLE, 2)};
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
        return arena.contains(block, 35, 15, 20);
    }
}
