package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The bow 1v1, on the place of the bow training (the red base of Archway and the north of its
 * middle): red on the front of the base, blue on the edge of the middle, facing each other. A bow
 * (Infinity) and wool: each arrow that hits the other is a point (its knockback, no damage), a fall
 * into the void is a point for the other side and the round starts again. Swords and fists do
 * nothing. First to 10 by default (points-pour-gagner.bow).
 */
final class BowDuel extends Duel {

    private final BowTraining.Course course;

    BowDuel(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target, BowTraining.Course course) {
        super(host, mode, arena, teams, target);
        this.course = course;
    }

    @Override
    String title() {
        return "Bow duel";
    }

    @Override
    String goal() {
        return target + (target == 1 ? " hit" : " hits");
    }

    @Override
    void build() {
        if (course.copy != null) {
            course.copy.build(arena);
            return;
        }
        int y = course.bridgeY;
        arena.fill(-7, y + 1, -27, 7, y + 1, -19, Material.STAINED_CLAY, Shapes.RED);
        arena.fill(-8, y, 17, 8, y, 26, Material.STAINED_CLAY, Shapes.WHITE);
    }

    /** Red on the base, as in the training; blue at the start of the middle bridge, facing the base. */
    @Override
    Location spawnOf(UUID player) {
        double[] start = startOf(course, Math.max(0, sideOf(player)));
        return arena.at(start[0], start[1], start[2], (float) start[3]);
    }

    /** The start of a side: x, y, z, yaw from the origin. */
    static double[] startOf(BowTraining.Course course, int side) {
        if (side == 0) {
            return course.player.clone();
        }
        int[] line = course.lines[0];
        double[] way = BowTraining.way(line);
        float yaw = (float) Math.toDegrees(Math.atan2(-way[0], way[1]));
        return new double[] {line[0] + 0.5, course.bridgeY + 1, line[1] + 0.5, yaw};
    }

    @Override
    double fallY() {
        return arena.floorY() + course.bridgeY - 20;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.BOW), new ItemStack(Material.WOOL, 64), new ItemStack(Material.ARROW)};
    }

    @Override
    void afterKit(Player player) {
        BowTraining.enchantBows(player, false);
    }

    @Override
    boolean refills() {
        return true;
    }

    /** An arrow of the other side: its knockback, no damage. Nothing else hurts. */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        if (attacker == null || event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) {
            return false;
        }
        event.setDamage(0.0);
        return true;
    }

    /** An arrow landed: a point for the side of the archer. */
    @Override
    void landed(Player victim, Player attacker, EntityDamageEvent event) {
        if (attacker == null || phase != Phase.PLAYING || event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE
                || sameSide(victim.getUniqueId(), attacker.getUniqueId())) {
            return;
        }
        int side = sideOf(attacker.getUniqueId());
        if (side < 0) {
            return;
        }
        points[side]++;
        sound(attacker, Sound.SUCCESSFUL_HIT, 1f);
        sidebars();
        if (points[side] >= target) {
            finish(side, null);
        }
    }

    @Override
    void extraLines(UUID viewer, List<String> lines) {
        lines.add(ChatColor.GRAY + "An arrow: +1, a fall: +1 to the other");
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return arena.contains(block, 60, 25, 25);
    }
}
