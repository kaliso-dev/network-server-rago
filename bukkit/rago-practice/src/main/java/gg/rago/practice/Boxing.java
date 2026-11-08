package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Boxing, as on Minemen: in a closed ring, with Speed II and a sword that does no damage, the first
 * to land 100 hits wins. Every hit gives the knockback of a real one; the sidebar counts the hits
 * and the combo.
 */
final class Boxing extends Duel {

    private final int[] hits = new int[2];
    private final int[] combo = new int[2];
    private final int[] bestCombo = new int[2];

    Boxing(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target) {
        super(host, mode, arena, teams, target);
    }

    @Override
    String title() {
        return "Boxing";
    }

    @Override
    String goal() {
        return target + " hits";
    }

    @Override
    boolean colouredBlocks() {
        return false;
    }

    @Override
    void build() {
        Shapes.floor(arena, -12, -12, 12, 12, -1, Material.STAINED_CLAY, Shapes.GRAY);
        Shapes.floor(arena, -11, -11, 11, 11, 0, Material.WOOL, Shapes.WHITE);
        Shapes.walls(arena, -12, -12, 12, 12, 0, 1, Material.STAINED_CLAY, Shapes.GRAY);
        // the ropes, the corner posts of each side, and an invisible wall above
        Shapes.walls(arena, -12, -12, 12, 12, 1, 2, Material.IRON_FENCE, 0);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int colour = sx < 0 ? Shapes.RED : Shapes.BLUE;
                arena.fill(12 * sx, 1, 12 * sz, 12 * sx, 3, 12 * sz, Material.STAINED_CLAY, colour);
            }
        }
        Shapes.walls(arena, -12, -12, 12, 12, 3, 5, Material.BARRIER, 0);
    }

    @Override
    Location spawnOf(UUID player) {
        int side = sideOf(player);
        return side == 0 ? arena.at(-4.5, 1, 0.5, -90f) : arena.at(5.5, 1, 0.5, 90f);
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.DIAMOND_SWORD)};
    }

    @Override
    void effects(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false), true);
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
    void landed(Player victim, Player attacker, EntityDamageEvent event) {
        if (attacker == null || phase != Phase.PLAYING) {
            return;
        }
        int side = sideOf(attacker.getUniqueId());
        hits[side]++;
        combo[side]++;
        combo[1 - side] = 0;
        bestCombo[side] = Math.max(bestCombo[side], combo[side]);
        if (combo[side] > 0 && combo[side] % 10 == 0) {
            attacker.playSound(attacker.getLocation(), Sound.ORB_PICKUP, 1f, 1.5f);
            attacker.sendMessage(ChatColor.YELLOW + "" + combo[side] + " hit combo!");
        }
        if (hits[side] >= target) {
            finish(side, null);
        }
    }

    @Override
    void fell(Player player) {
        player.teleport(spawnOf(player.getUniqueId()));
        player.setFallDistance(0f);
    }

    @Override
    String score(int side) {
        return hits[side] + ChatColor.GRAY.toString() + " hits";
    }

    @Override
    void extraLines(UUID viewer, List<String> lines) {
        int side = Math.max(0, sideOf(viewer));
        lines.add(ChatColor.WHITE + "Combo: " + ChatColor.YELLOW + combo[side] + ChatColor.GRAY + " (best " + bestCombo[side] + ")");
    }

    @Override
    String result(int winner) {
        int side = Math.max(0, winner);
        return "(" + hits[side] + " - " + hits[1 - side] + " hits, best combo " + bestCombo[side] + ")";
    }
}
