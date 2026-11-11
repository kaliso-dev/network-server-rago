package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The bridge fight 1v1 or 2v2, apart from the open arena: one platform of the bridge fight, red on
 * the west half and blue on the east one, with its pads and the bridges from the pads to its tips.
 * Each side starts on its pad (a team side by side), in iron armour with golden apples, a wooden
 * sword in 1v1 (as in the open arena), a stone one in 2v2, the armour with Protection II by default
 * (bridge-fight.protection-1v1, 0 for none), and nobody places blocks: in 1v1 a death or a fall
 * into the void is a point for the other side, in 2v2 the whole side out. First to 5 by default
 * (points-pour-gagner.bridge-fight).
 */
final class BridgeDuel extends Duel {

    /** The platform of the duel: the colours of the sides. */
    static final BridgeArena.Platform PLATFORM = new BridgeArena.Platform("Red & Blue", ChatColor.RED, Shapes.RED, Color.RED,
            Shapes.BLUE, Color.BLUE, 0);

    /** The level of Protection of the armour, 0 for none. */
    private final int protection;

    BridgeDuel(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target, int protection) {
        super(host, mode, arena, teams, target);
        this.protection = protection;
    }

    @Override
    String title() {
        return "Bridge fight";
    }

    @Override
    void build() {
        BridgeArena.buildPlatform(arena, 0, PLATFORM);
    }

    /** Red on the west pad, blue on the east one, facing the platform; a team side by side on its pad. */
    @Override
    Location spawnOf(UUID player) {
        int end = sideOf(player) == 0 ? -1 : 1;
        double z = Math.max(-1, Math.min(1, across(player))) + 0.5;
        return arena.at(end * BridgeArena.PAD + 0.5, 1, z, end < 0 ? -90f : 90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 12;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(mode.playersPerTeam() > 1 ? Material.STONE_SWORD : Material.WOOD_SWORD),
            new ItemStack(Material.GOLDEN_APPLE, 2)};
    }

    @Override
    boolean colouredBlocks() {
        return false;
    }

    @Override
    void armour(Player player) {
        ironArmour(player, COLOURS[Math.max(0, sideOf(player.getUniqueId()))]);
        protect(player, protection);
    }

    /**
     * A round won or lost, each with its sound, loud: a level up, or the thud of an anvil. A tick
     * later, where the player stands then (on their pad for the next round, not in the void).
     */
    @Override
    void pointSound(Player player, boolean ours) {
        // the last point too, the game over: the scheduler's own task, not later's
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), ours ? Sound.LEVEL_UP : Sound.ANVIL_LAND, 1f, ours ? 1f : 0.8f);
            }
        }, 1L);
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        return true;
    }
}
