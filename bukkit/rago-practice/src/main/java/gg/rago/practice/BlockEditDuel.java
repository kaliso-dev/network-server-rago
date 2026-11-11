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
 * Block edit, one against one: the station of the block edit with a room on each side of the wall
 * of wool. Break a block of the wall, hit the other player through the hole, and put a block back
 * in the wall before they hit you: that is a point. A hit back first takes the point away; then
 * it is theirs to close. The hits do no damage, only their knockback. First to the target.
 */
final class BlockEditDuel extends Duel {

    /** The side that hit last and must now close the wall to score, or -1. */
    private int pending = -1;

    BlockEditDuel(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target) {
        super(host, mode, arena, teams, target);
    }

    /** Who scores when this side puts a block in the wall: the side that hit last, if it is this one. */
    static boolean scores(int pending, int side) {
        return pending >= 0 && pending == side;
    }

    @Override
    String title() {
        return "Block edit";
    }

    @Override
    void intro(Player player) {
        super.intro(player);
        player.sendMessage(ChatColor.GRAY + "Break a block of the wall, hit them through the hole, and put a block back"
                + " before they hit you: a point.");
    }

    @Override
    void build() {
        EditStation.build(arena, true, Shapes.WHITE);
    }

    @Override
    Location spawnOf(UUID player) {
        return sideOf(player) == 0 ? arena.at(-2.5, 1, 0.5, -90f) : arena.at(3.5, 1, 0.5, 90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 5;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.WOOD_SWORD), new ItemStack(Material.SHEARS), new ItemStack(Material.WOOL, 64)};
    }

    @Override
    boolean refills() {
        return true;
    }

    @Override
    void roundReset() {
        super.roundReset();
        pending = -1;
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
        if (pending != side) {
            attacker.sendMessage(ChatColor.YELLOW + "Hit! Put a block back in the wall before they hit you.");
            sound(attacker, Sound.NOTE_PLING, 1.4f);
            if (pending >= 0) {
                victim.sendMessage(ChatColor.RED + "Hit back: your point is gone.");
            }
        }
        pending = side;
    }

    @Override
    boolean canBreak(Player player, Block block) {
        return EditStation.isWall(arena, block);
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return EditStation.isWall(arena, block);
    }

    @Override
    void placed(Player player, Block block) {
        int side = sideOf(player.getUniqueId());
        if (scores(pending, side)) {
            broadcast(CHAT[side] + player.getDisplayName() + ChatColor.GRAY + " made a clean edit.", null);
            point(side);
        }
    }

    @Override
    void fell(Player player) {
        player.teleport(spawnOf(player.getUniqueId()));
        player.setFallDistance(0f);
    }

    @Override
    void extraLines(UUID viewer, List<String> lines) {
        int side = Math.max(0, sideOf(viewer));
        lines.add(pending < 0 ? ChatColor.GRAY + "Open, hit, close."
                : pending == side ? ChatColor.YELLOW + "Close the wall!" : ChatColor.RED + "Hit them back!");
    }
}
