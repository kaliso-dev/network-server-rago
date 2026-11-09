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
import org.bukkit.inventory.PlayerInventory;

/**
 * KB displace, as in the top fight of a Minemen Bed Fight duel: a Bed Wars island whose bed has a
 * three layer defense. One player is boxed on top of it, a box of wool in front, behind and above
 * them, open on the sides; the other one starts at the foot of the defense, climbs it and must
 * knock them off the top with the knockback of their hits, in 20 seconds. Knocked off the top: a
 * point for the attacker; held: a point for the boxed player. The roles swap each round. No
 * damage, only knockback. First to the target.
 */
final class KbDisplace extends Duel {

    /** The feet of the boxed player, on top of the defense: this high above the floor. */
    static final int TOP = 5;
    /** The top of the defense runs along x, over the bed, from x = 0 to 1, at z = 0. */
    static final double TOP_X = 1.0;
    static final double TOP_Z = 0.5;
    /** The seconds of the attacker to knock the boxed player off. */
    static final int ROUND_SECONDS = 20;

    private boolean decided;

    KbDisplace(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target) {
        super(host, mode, arena, teams, target);
    }

    @Override
    String title() {
        return "KB displace";
    }

    @Override
    void intro(Player player) {
        super.intro(player);
        player.sendMessage(ChatColor.GRAY + "One of you is boxed on top of the defense, open on the sides; the other one"
                + " climbs and knocks them off in " + ROUND_SECONDS + " seconds. The roles swap each round.");
    }

    /** The island, its bed and the defense of three layers. */
    static void topFight(Arena arena) {
        Shapes.island(arena, -6, -5, 7, 5, Material.WOOD, 0, Material.LOG, 0);
        Shapes.bed(arena, 0, 1, 0, 1, 0);
        Shapes.buildDefense(arena, Shapes.defense(0, 1, 0, 1, 0, 3),
                new Material[] {Material.ENDER_STONE, Material.WOOD, Material.WOOL}, new int[] {0, 0, Shapes.WHITE});
    }

    /** The box on top of the defense, of this round only: in front, behind and above, open on the sides. */
    static void box(Arena arena, int colour) {
        for (int y = TOP; y <= TOP + 1; y++) {
            arena.put(-1, y, 0, Material.WOOL, colour);
            arena.put(2, y, 0, Material.WOOL, colour);
        }
        arena.put(0, TOP + 2, 0, Material.WOOL, colour);
        arena.put(1, TOP + 2, 0, Material.WOOL, colour);
    }

    /** True if the boxed player, standing at this height above the floor, has been knocked off the top. */
    static boolean offTheTop(double feet) {
        return feet < TOP - 0.9;
    }

    /** Where the attacker starts: at the foot of the defense, on one side of it. */
    static Location attackerStart(Arena arena) {
        return arena.at(TOP_X, 1, 4.5, 180f);
    }

    /** The side that attacks in this round: the first side in the first round, then each in turn. */
    static int attackerOf(int round) {
        return (round + 1) % 2;
    }

    private int attacker() {
        return attackerOf(round);
    }

    @Override
    void build() {
        topFight(arena);
    }

    @Override
    void roundReset() {
        super.roundReset();
        decided = false;
        box(arena, WOOL[1 - attacker()]);
    }

    @Override
    Location spawnOf(UUID player) {
        return sideOf(player) == attacker() ? attackerStart(arena) : arena.at(TOP_X, TOP, TOP_Z, 0f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 6;
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.STONE_SWORD), new ItemStack(Material.WOOL, 64)};
    }

    /** The boxed player has their sword only: no block to close the sides of their box. */
    @Override
    void afterKit(Player player) {
        if (sideOf(player.getUniqueId()) == attacker()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] != null && contents[slot].getType().isBlock()) {
                inventory.setItem(slot, null);
            }
        }
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
        return sideOf(player.getUniqueId()) == attacker() && arena.contains(block, 12, 6, TOP + 4);
    }

    @Override
    void tick() {
        if (decided) {
            return;
        }
        for (Player player : online(1 - attacker())) {
            if (offTheTop(player.getLocation().getY() - arena.floorY())) {
                decided = true;
                broadcast(CHAT[attacker()] + namesOf(attacker()) + ChatColor.GRAY + " knocked " + CHAT[1 - attacker()]
                        + player.getName() + ChatColor.GRAY + " off the top.", Sound.NOTE_PLING);
                point(attacker());
                return;
            }
        }
    }

    @Override
    void onSecond() {
        if (!decided && seconds >= ROUND_SECONDS) {
            decided = true;
            broadcast(CHAT[1 - attacker()] + namesOf(1 - attacker()) + ChatColor.GRAY + " held the top.", null);
            point(1 - attacker());
        }
    }

    @Override
    void extraLines(UUID viewer, List<String> lines) {
        boolean attacking = sideOf(viewer) == attacker();
        lines.add(attacking ? ChatColor.YELLOW + "Knock them off the top!" : ChatColor.AQUA + "Hold the top!");
        lines.add(ChatColor.WHITE + "Left: " + ChatColor.GREEN + Math.max(0, ROUND_SECONDS - seconds) + " s");
    }
}
