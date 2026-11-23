package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Block placement fight, 1v1 or 2v2, on the owner's block placement maps (block-fight.cartes in
 * config.yml: each side starts on its gold block) or, without them, two small platforms over the
 * void. The kit of the maps: a wooden sword (Sharpness I), a pickaxe and an axe (Efficiency I),
 * shears, two stacks of wool, end stone and ladders; iron boots and leggings, the helmet and
 * chestplate of the side, all Protection IV. As on Minemen, the points are the hits: each hit
 * landed on the other side is a point (a hit does no damage, only its knockback), and a fall into
 * the void gives the other side {@value #VOID_BONUS} points and sends the one who fell back on their
 * gold block at once, their kit given back whole (two stacks of wool again; the others fight on,
 * the blocks stay, but those where they come back go first, as in Bed Fight). A block broken drops on the ground, for anyone to pick up, and no block may
 * close a player in completely (the anti full box, {@link FullBox}). First to 100 by default (points-pour-gagner.block-fight). The hearts tell the
 * score: a player has all their health while the other side has no point, none when it reaches
 * the target (see {@link #healthFor}), and none comes back by itself. As a Red Rover
 * (red-rover-block-placement), the first to red-rover.points-par-1v1-block-placement wins the
 * 1v1, and the next one starts on the map as built.
 */
final class BlockFight extends Duel {

    /** The points the other side gets for a fall into the void. */
    static final int VOID_BONUS = 10;
    /** The health of a player whose other side has no point. */
    static final double FULL_HEALTH = 20.0;
    /** The least health shown, half a heart: the score never kills. */
    static final double LEAST_HEALTH = 1.0;

    BlockFight(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target) {
        super(host, mode, arena, teams, target);
    }

    /** The score of the other side after a fall of a player: the bonus of the void. */
    static int afterFall(int points) {
        return points + VOID_BONUS;
    }

    /**
     * The health of a player whose other side has these points: full at 0, none left at the
     * target (100 hits by default), so that the hearts fall as the other side nears its win; half
     * a heart at least.
     */
    static double healthFor(int otherPoints, int target) {
        if (target <= 0) {
            return FULL_HEALTH;
        }
        double health = FULL_HEALTH * (target - otherPoints) / target;
        return Math.max(LEAST_HEALTH, Math.min(FULL_HEALTH, health));
    }

    /** The hearts of every player brought to the score. */
    private void showHealth() {
        for (Player player : online()) {
            int side = slotOf(player.getUniqueId());
            if (side >= 0 && side < points.length && !player.isDead()) {
                player.setHealth(Math.min(player.getMaxHealth(), healthFor(points[1 - side], target)));
            }
        }
    }

    /** At the start, the hearts the score leaves (all of them at 0 - 0); the start cleared first. */
    @Override
    void placeAtStart(Player player) {
        clearStart(player.getUniqueId());
        super.placeAtStart(player);
        showHealth();
    }

    /**
     * The two blocks where the player comes back (feet and head) go first, as in Bed Fight (the
     * owner's ask of 23/11): nobody comes back walled in by the blocks placed on their start.
     */
    void clearStart(UUID player) {
        Block feet = spawnOf(player).getBlock();
        for (Block block : new Block[] {feet, feet.getRelative(BlockFace.UP)}) {
            if (block.getType() != Material.AIR) {
                block.setType(Material.AIR);
            }
        }
    }

    /** The hearts are the score: none comes back by itself. */
    @Override
    boolean regains() {
        return false;
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
        if (arena.map() != null) {
            return;
        }
        int half = half();
        Shapes.floor(arena, -9, -half, -7, half, 0, Material.SANDSTONE, 2);
        Shapes.floor(arena, 7, -half, 9, half, 0, Material.SANDSTONE, 2);
        arena.build(-8, -1, 0, Material.STAINED_CLAY, Shapes.RED);
        arena.build(8, -1, 0, Material.STAINED_CLAY, Shapes.BLUE);
    }

    @Override
    Location spawnOf(UUID player) {
        int side = slotOf(player);
        Location start = arena.mapStart(Math.max(0, side));
        if (start != null) {
            // teammates side by side, across the way they look
            double across = across(player) * 2;
            double yaw = Math.toRadians(start.getYaw());
            return start.add(-Math.cos(yaw) * across, 0, -Math.sin(yaw) * across);
        }
        double z = across(player) * 2 + 0.5;
        return side == 0 ? arena.at(-7.5, 1, z, -90f) : arena.at(8.5, 1, z, 90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 10;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.WOOD_SWORD), new ItemStack(Material.WOOD_PICKAXE),
            new ItemStack(Material.WOOD_AXE), new ItemStack(Material.SHEARS), new ItemStack(Material.WOOL, 64),
            new ItemStack(Material.ENDER_STONE, 12), new ItemStack(Material.WOOL, 64), new ItemStack(Material.LADDER, 16)};
    }

    /** The enchantments of the kit of the maps, on the tools wherever the player put them. */
    @Override
    void afterKit(Player player) {
        ItemStack[] items = player.getInventory().getContents();
        enchantTools(items);
        player.getInventory().setContents(items);
    }

    static void enchantTools(ItemStack[] items) {
        for (ItemStack item : items) {
            if (item == null) {
                continue;
            }
            if (item.getType() == Material.WOOD_SWORD) {
                item.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 1);
            } else if (item.getType() == Material.WOOD_PICKAXE || item.getType() == Material.WOOD_AXE) {
                item.addUnsafeEnchantment(Enchantment.DIG_SPEED, 1);
            }
        }
    }

    @Override
    void armour(Player player) {
        ironArmour(player, COLOURS[Math.max(0, slotOf(player.getUniqueId()))]);
        ItemStack[] pieces = player.getInventory().getArmorContents();
        for (ItemStack piece : pieces) {
            if (piece != null && piece.getType() != Material.AIR) {
                piece.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
            }
        }
        player.getInventory().setArmorContents(pieces);
    }

    /** A hit of the other side: its knockback, no damage; nothing else hurts. */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        if (attacker == null) {
            return false;
        }
        // none at all: the hearts are the score
        noDamage(event);
        return true;
    }

    /** A hit landed: a point for the side of the one who landed it. */
    @Override
    void landed(Player victim, Player attacker, EntityDamageEvent event) {
        if (attacker == null || phase != Phase.PLAYING || sameSide(victim.getUniqueId(), attacker.getUniqueId())) {
            return;
        }
        int side = slotOf(attacker.getUniqueId());
        if (side < 0 || slotOf(victim.getUniqueId()) < 0) {
            return;
        }
        points[side]++;
        showHealth();
        if (points[side] >= target) {
            wonBy(side);
        }
    }

    /**
     * A fall into the void: +10 for the other side (the one who knocked them off), and the one who
     * fell back on their start at once, with their whole kit again (two stacks of wool).
     */
    @Override
    void fell(Player player) {
        UUID id = player.getUniqueId();
        int side = slotOf(id);
        if (side < 0 || phase != Phase.PLAYING) {
            player.teleport(spawnOf(id));
            player.setFallDistance(0f);
            return;
        }
        UUID by = killerOf(id);
        deathMessage(player, by, true);
        if (counts()) {
            // by null: a death nobody gets the kill of (the void, a fall), a death all the same
            plugin.lobby().recordKill(mode.id(), by, id);
        }
        int other = 1 - side;
        points[other] = afterFall(points[other]);
        showHealth();
        for (Player each : online()) {
            int mine = slotOf(each.getUniqueId());
            each.sendMessage((mine == other ? ChatColor.GREEN + "+" + VOID_BONUS + " for your team" : ChatColor.RED + "+"
                    + VOID_BONUS + " for " + slotName(other)) + ChatColor.GRAY + " (" + points[Math.max(0, mine)] + " - "
                    + points[1 - Math.max(0, mine)] + ")");
            each.playSound(each.getLocation(), mine == other ? Sound.ORB_PICKUP : Sound.NOTE_BASS, 1f, 1f);
        }
        if (points[other] >= target) {
            wonBy(other);
            return;
        }
        // only the one who fell goes back, at once, to their start, with their kit whole again: the
        // others fight on, the blocks stay
        forgetHits(id);
        player.setFallDistance(0f);
        clearStart(id);
        player.teleport(spawnOf(id));
        player.setItemOnCursor(null);
        giveKit(player);
    }

    @Override
    boolean pointsIn1v1() {
        return true;
    }

    @Override
    void extraLines(UUID viewer, List<String> lines) {
        lines.add(ChatColor.GRAY + "A hit: +1, a knock into the void: +" + VOID_BONUS);
    }

    /**
     * In the zone of the map; never a block that closes a player in completely (the anti full box,
     * the owner's choice of 22/11: see {@link FullBox}), the one who places it or another.
     */
    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        boolean inZone = arena.map() != null ? arena.contains(block, 45, 12, 25) : arena.contains(block, 30, 12, 16);
        if (!inZone) {
            return false;
        }
        World world = block.getWorld();
        for (Player inGame : online()) {
            Location feet = inGame.getLocation();
            if (inGame.getGameMode() == org.bukkit.GameMode.SPECTATOR || !world.equals(feet.getWorld())) {
                continue;
            }
            if (FullBox.closes(feet.getBlockX(), feet.getBlockY(), feet.getBlockZ(), block.getX(), block.getY(), block.getZ(),
                    (x, y, z) -> world.getBlockAt(x, y, z).getType().isSolid())) {
                player.sendMessage(ChatColor.RED + (inGame == player ? "You can't box yourself in!" : "You can't box a player in!"));
                return false;
            }
        }
        return true;
    }

    /**
     * A block broken (one placed in the round) drops on the ground, as a block broken anywhere
     * (the owner's ask of 22/11): the wool of its colour, the end stone, the ladders of the kit.
     */
    @Override
    @SuppressWarnings("deprecation") // Block#getData: the colour of the wool
    ItemStack dropOf(Block block) {
        return kept(block.getType(), block.getData());
    }

    /** The item a broken block of this type gives back: wool (of its colour), end stone, a ladder; null for another. */
    static ItemStack kept(Material type, byte data) {
        if (type == Material.WOOL) {
            return new ItemStack(Material.WOOL, 1, data);
        }
        return type == Material.ENDER_STONE || type == Material.LADDER ? new ItemStack(type) : null;
    }
}
