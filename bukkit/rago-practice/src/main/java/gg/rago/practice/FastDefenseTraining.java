package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Fast defense: a bed on an island, and the player covers it as fast as they can, as in a game.
 * Two defenses, as the owner builds them: the Ranked Bedwars one, end stone (the top of the bed,
 * two rows on each side, one block at each end) under a layer of glass, its side rows left bare;
 * and the solo one, end stone over the bed and two more in front, then wool over all of it. Each
 * place takes its block; the time runs from the first block to the last place filled.
 */
final class FastDefenseTraining extends Training {

    private static final int BED_Y = 1;
    /** The side of the player, in front of the bed: z below 0. The bed lies along x, foot at x 0. */
    private static final int FRONT = -1;

    /** Each place of the defense ("x,y,z" offsets) and its block, for each variant. */
    private final List<Map<String, Material>> places = new ArrayList<>();
    private long started;
    private int filled;
    private boolean done;

    FastDefenseTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams,
                new Variant("ranked", "Ranked (end stone, glass)", true, ""),
                new Variant("solo", "Solo (end stone, wool)", true, ""));
        places.add(defense(true));
        places.add(defense(false));
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /**
     * The places of a defense and their blocks: the Ranked one (end stone and glass) or the solo
     * one (end stone and wool). The bed lies from (0, 1, 0) to (1, 1, 0).
     */
    static Map<String, Material> defense(boolean ranked) {
        List<List<int[]>> layers = Shapes.defense(0, BED_Y, 0, 1, 0, 2);
        Map<String, Material> defense = new LinkedHashMap<>();
        for (int[] place : layers.get(0)) {
            defense.put(key(place[0], place[1], place[2]), Material.ENDER_STONE);
        }
        // the second row of end stone on the sides: both sides in Ranked, the front one in solo
        for (int x = 0; x <= 1; x++) {
            defense.put(key(x, BED_Y, 2 * FRONT), Material.ENDER_STONE);
            if (ranked) {
                defense.put(key(x, BED_Y, -2 * FRONT), Material.ENDER_STONE);
            }
        }
        if (ranked) {
            for (int[] place : layers.get(1)) {
                defense.putIfAbsent(key(place[0], place[1], place[2]), Material.STAINED_GLASS);
            }
            return defense;
        }
        // the wool: every free place next to the bed or the end stone, over the ground
        Set<String> solid = new HashSet<>(defense.keySet());
        solid.add(key(0, BED_Y, 0));
        solid.add(key(1, BED_Y, 0));
        int[][] sides = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (String each : solid) {
            String[] xyz = each.split(",");
            for (int[] side : sides) {
                int x = Integer.parseInt(xyz[0]) + side[0];
                int y = Integer.parseInt(xyz[1]) + side[1];
                int z = Integer.parseInt(xyz[2]) + side[2];
                if (y >= BED_Y && !solid.contains(key(x, y, z))) {
                    defense.put(key(x, y, z), Material.WOOL);
                }
            }
        }
        return defense;
    }

    private boolean ranked() {
        return variant == 0;
    }

    @Override
    String describe() {
        return ranked() ? "end stone around the bed (two rows on each side), then glass over it (26 blocks)."
                : "end stone around the bed and two more in front, then wool over all of it (32 blocks).";
    }

    @Override
    void build() {
        Shapes.island(arena, -7, -6, 8, 6, Material.WOOD, 0, Material.LOG, 0);
        Shapes.bed(arena, 0, BED_Y, 0, 1, 0);
    }

    /** In front of the bed, facing it, as the owner stands to build the defense. */
    @Override
    Location spawnOf(UUID player) {
        return arena.at(1.0, 1, 5.5 * FRONT, 0f);
    }

    @Override
    ItemStack[] kit() {
        ItemStack outside = ranked() ? new ItemStack(Material.STAINED_GLASS, 64, (short) Shapes.BLUE)
                : new ItemStack(Material.WOOL, 64, (short) Shapes.BLUE);
        return new ItemStack[] {stack(Material.ENDER_STONE), outside};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        started = 0;
        filled = 0;
        done = false;
    }

    /** The block a place of the defense takes, or null if the block is not on one. */
    private Material wantedAt(Block block) {
        return places.get(variant).get(key(block.getX() - arena.centreX(), block.getY() - arena.floorY(),
                block.getZ() - arena.centreZ()));
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        if (done) {
            return false;
        }
        Material wanted = wantedAt(block);
        if (wanted == null) {
            // a block to stand on is fine, near the bed
            return near(block, 0, 0, 7, 0, 6);
        }
        if (block.getType() != wanted) {
            player.sendMessage(ChatColor.RED + "This place takes " + nameOf(wanted) + ".");
            return false;
        }
        return true;
    }

    private static String nameOf(Material type) {
        return type == Material.ENDER_STONE ? "end stone" : type == Material.STAINED_GLASS ? "glass" : "wool";
    }

    @Override
    void placed(Player player, Block block) {
        if (started == 0) {
            started = now();
        }
        if (wantedAt(block) != null) {
            filled++;
        }
    }

    @Override
    void broke(Player player, Block block) {
        if (wantedAt(block) != null) {
            filled--;
        }
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || done || started == 0) {
            return;
        }
        long elapsed = now() - started;
        if (filled >= places.get(variant).size() && complete()) {
            done = true;
            score(elapsed);
            sound(player, Sound.ANVIL_LAND, 1.6f);
            again(40L);
            return;
        }
        if (elapsed > limit()) {
            failed("Too slow: " + limit() / 1000 + " seconds and the defense is not finished (" + filled + "/"
                    + places.get(variant).size() + ").");
            return;
        }
        refresh();
    }

    /** Ten seconds, and half a second more for each place: then the attempt is over. */
    private long limit() {
        return 10000L + 500L * places.get(variant).size();
    }

    /** Every place holds its block. */
    private boolean complete() {
        for (Map.Entry<String, Material> place : places.get(variant).entrySet()) {
            String[] xyz = place.getKey().split(",");
            Block block = arena.block(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
            if (block.getType() != place.getValue()) {
                return false;
            }
        }
        return true;
    }

    @Override
    void trainingLines(List<String> lines) {
        long elapsed = started == 0 ? 0 : now() - started;
        lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + time(elapsed) + " s");
        lines.add(ChatColor.WHITE + "Blocks: " + ChatColor.GREEN + filled + ChatColor.GRAY + "/" + places.get(variant).size());
        lines.add(ChatColor.GRAY + (started == 0 ? "Place a block to start" : done ? "Defense done!" : "Cover the bed!"));
    }
}
