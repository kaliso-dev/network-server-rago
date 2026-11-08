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
 * KB displace: two players on a one-block bridge over the void, hits with no damage, only their
 * knockback. Knock the other one off; clutch with your wool when you are hit. First to 3.
 */
final class KbDisplace extends Duel {

    /** Half the length of the bridge. */
    static final int HALF = 14;

    KbDisplace(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target) {
        super(host, mode, arena, teams, target);
    }

    @Override
    String title() {
        return "KB displace";
    }

    @Override
    void build() {
        bridge(arena);
    }

    /** The bridge, and a platform at each end. */
    static void bridge(Arena arena) {
        Shapes.floor(arena, -HALF, 0, HALF, 0, 0, Material.SANDSTONE, 0);
        Shapes.floor(arena, -HALF - 3, -1, -HALF - 1, 1, 0, Material.STAINED_CLAY, Shapes.RED);
        Shapes.floor(arena, HALF + 1, -1, HALF + 3, 1, 0, Material.STAINED_CLAY, Shapes.BLUE);
    }

    @Override
    Location spawnOf(UUID player) {
        int side = sideOf(player);
        double along = 4 + indexOf(player) * 2;
        return side == 0 ? arena.at(-along + 0.5, 1, 0.5, -90f) : arena.at(along + 0.5, 1, 0.5, 90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 6;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.STONE_SWORD), new ItemStack(Material.WOOL, 64)};
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        if (attacker == null) {
            return false;
        }
        event.setDamage(0.0);
        return true;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return arena.contains(block, HALF + 8, 8, 6);
    }
}
