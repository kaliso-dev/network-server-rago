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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Top fight, 2v2 or a party's private one: a Bed Wars island whose bed has a three layer defense,
 * end stone, wood and wool, ready made. Both sides start on the top of the defense, face to face
 * (in 2v2 the second of each side one step down behind the first), in full diamond armour with
 * Protection IV, Absorption II (eight hearts more) and golden apples: whoever holds the top wins
 * the fights. A death or a fall into the void is a point for the other side. First to 3.
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

    /** The golden apples of the kit. */
    static final int APPLES = 5;
    /** The Absorption of each start: II, eight hearts more (a golden apple's is I, four). */
    static final int ABSORPTION_LEVEL = 1;

    /**
     * On the top of the defense, face to face: the bed is at x 0 and 1, its defense rises to y 4
     * over it; the first of the west side on x 0, of the east side on x 1, the second of each one
     * step down behind them (x -1 and 2, the third layer at y 3).
     */
    @Override
    Location spawnOf(UUID player) {
        return start(sideOf(player), indexOf(player));
    }

    /** Where the player of this side and place in it starts: x, y of the feet, z, yaw. */
    static double[] startOf(int side, int index) {
        boolean behind = index % 2 == 1;
        double z = 0.5 + (index / 2) * (side == 0 ? -1 : 1);
        if (side <= 0) {
            return new double[] {behind ? -0.5 : 0.5, behind ? 4 : 5, z, -90};
        }
        return new double[] {behind ? 2.5 : 1.5, behind ? 4 : 5, z, 90};
    }

    private Location start(int side, int index) {
        double[] at = startOf(side, index);
        return arena.at(at[0], at[1], at[2], (float) at[3]);
    }

    @Override
    double fallY() {
        return arena.floorY() - 8;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.IRON_SWORD), new ItemStack(Material.WOOL, 64),
            new ItemStack(Material.GOLDEN_APPLE, APPLES), new ItemStack(Material.ENDER_PEARL)};
    }

    /** Full diamond armour, Protection IV on each piece: the sides know each other by the colour of their names. */
    @Override
    void armour(Player player) {
        ItemStack[] armour = {new ItemStack(Material.DIAMOND_BOOTS), new ItemStack(Material.DIAMOND_LEGGINGS),
            new ItemStack(Material.DIAMOND_CHESTPLATE), new ItemStack(Material.DIAMOND_HELMET)};
        for (ItemStack piece : armour) {
            piece.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
        }
        player.getInventory().setArmorContents(armour);
    }

    @Override
    void effects(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, Integer.MAX_VALUE, ABSORPTION_LEVEL), true);
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
