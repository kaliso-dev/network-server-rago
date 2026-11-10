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
 * and the solo one, the bed pointing at the player: end stone over it, at each end and along each
 * side one block further on the player's side, then wool over all of it. Each
 * place takes its block; the time runs from the first block to the last place filled. Before each
 * attempt, the defense to build shows for three seconds, with a countdown, then goes.
 */
final class FastDefenseTraining extends Training {

    private static final int BED_Y = 1;
    /**
     * The side of the player, in front of the bed: z below 0. The foot of the bed is at (0, 1, 0);
     * in Ranked it lies along x (the player sees its side), in solo along z (it points at them).
     */
    private static final int FRONT = -1;
    /** Where the head of the bed is from its foot, {x, z}: Ranked, solo. */
    private static final int[][] HEADS = {{1, 0}, {0, 1}};

    /** Each place of the defense ("x,y,z" offsets) and its block, for each variant. */
    private final List<Map<String, Material>> places = new ArrayList<>();
    /** How long the defense to build shows before each attempt. */
    static final int PREVIEW_SECONDS = 3;
    private long started;
    private int filled;
    private boolean done;
    /** The seconds left while the defense to build shows; 0 once the player builds. */
    private int previewLeft;
    private long refusedAt;

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
     * The places of a defense and their blocks: the Ranked one (end stone and glass, the bed from
     * (0, 1, 0) to (1, 1, 0)) or the solo one (end stone and wool, the bed from (0, 1, 0) to
     * (0, 1, 1), its foot towards the player).
     */
    static Map<String, Material> defense(boolean ranked) {
        int[] head = HEADS[ranked ? 0 : 1];
        List<List<int[]>> layers = Shapes.defense(0, BED_Y, 0, head[0], head[1], 2);
        Map<String, Material> defense = new LinkedHashMap<>();
        for (int[] place : layers.get(0)) {
            defense.put(key(place[0], place[1], place[2]), Material.ENDER_STONE);
        }
        if (ranked) {
            // the second row of end stone on each side
            for (int x = 0; x <= 1; x++) {
                defense.put(key(x, BED_Y, 2 * FRONT), Material.ENDER_STONE);
                defense.put(key(x, BED_Y, -2 * FRONT), Material.ENDER_STONE);
            }
            for (int[] place : layers.get(1)) {
                defense.putIfAbsent(key(place[0], place[1], place[2]), Material.STAINED_GLASS);
            }
            return defense;
        }
        // solo: each side row one block longer on the player's side, level with the foot's end
        defense.put(key(-1, BED_Y, FRONT), Material.ENDER_STONE);
        defense.put(key(1, BED_Y, FRONT), Material.ENDER_STONE);
        // the wool: every free place next to the bed or the end stone, over the ground
        Set<String> solid = new HashSet<>(defense.keySet());
        solid.add(key(0, BED_Y, 0));
        solid.add(key(head[0], BED_Y, head[1]));
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
                : "the bed points at you: end stone around it, its sides one block longer on your side, then wool"
                        + " over all of it (32 blocks).";
    }

    @Override
    void build() {
        Shapes.island(arena, -7, -6, 8, 6, Material.WOOD, 0, Material.LOG, 0);
    }

    /** In front of the bed, facing it, as the owner stands to build the defense. */
    @Override
    Location spawnOf(UUID player) {
        // the middle of the bed: across it in Ranked, in line with it in solo
        return arena.at(ranked() ? 1.0 : 0.5, 1, 5.5 * FRONT, 0f);
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
        // the bed of the mode, each round (the other mode lays it the other way)
        int[] head = HEADS[variant];
        Shapes.putBed(arena, 0, BED_Y, 0, head[0], head[1]);
        started = 0;
        filled = 0;
        done = false;
        previewLeft = PREVIEW_SECONDS;
        showDefense(true);
        final int roundNow = round;
        for (int second = 0; second <= PREVIEW_SECONDS; second++) {
            final int left = PREVIEW_SECONDS - second;
            later(1L + 20L * second, () -> {
                if (round == roundNow) {
                    preview(left);
                }
            });
        }
    }

    /** A second of the preview: the countdown, then the defense goes and the player builds it. */
    private void preview(int left) {
        Player player = player();
        previewLeft = left;
        if (left > 0) {
            if (player != null) {
                title(player, ChatColor.YELLOW + "" + ChatColor.BOLD + left, ChatColor.WHITE + "Look at the defense to build");
                sound(player, Sound.NOTE_STICKS, 1f);
            }
        } else {
            showDefense(false);
            if (player != null) {
                title(player, ChatColor.GREEN + "" + ChatColor.BOLD + "BUILD!", ChatColor.WHITE + describe());
                sound(player, Sound.NOTE_PLING, 1.5f);
            }
        }
        sidebars();
    }

    /** The defense to build, in place for the preview, or gone. */
    private void showDefense(boolean shown) {
        for (Map.Entry<String, Material> place : places.get(variant).entrySet()) {
            String[] xyz = place.getKey().split(",");
            int x = Integer.parseInt(xyz[0]);
            int y = Integer.parseInt(xyz[1]);
            int z = Integer.parseInt(xyz[2]);
            if (shown) {
                Material type = place.getValue();
                arena.put(x, y, z, type, type == Material.ENDER_STONE ? 0 : Shapes.BLUE);
            } else {
                arena.restore(arena.block(x, y, z));
            }
        }
    }

    /** The block a place of the defense takes, or null if the block is not on one. */
    private Material wantedAt(Block block) {
        return places.get(variant).get(key(block.getX() - arena.centreX(), block.getY() - arena.floorY(),
                block.getZ() - arena.centreZ()));
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        if (previewLeft > 0) {
            if (now() - refusedAt > 1000) {
                refusedAt = now();
                player.sendMessage(ChatColor.YELLOW + "Look at the defense first: you build it in " + previewLeft + " s.");
            }
            return false;
        }
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
    boolean canBreak(Player player, Block block) {
        return previewLeft == 0 && block.getType() != Material.BED_BLOCK && super.canBreak(player, block);
    }

    @Override
    void placed(Player player, Block block) {
        if (started == 0) {
            started = now();
        }
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || done || started == 0) {
            return;
        }
        long elapsed = now() - started;
        // read from the blocks themselves: a place filled any way counts, a place emptied does not
        filled = filledPlaces();
        if (filled >= places.get(variant).size()) {
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

    /** How many places hold their block. */
    private int filledPlaces() {
        int count = 0;
        for (Map.Entry<String, Material> place : places.get(variant).entrySet()) {
            String[] xyz = place.getKey().split(",");
            Block block = arena.block(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
            if (block.getType() == place.getValue()) {
                count++;
            }
        }
        return count;
    }

    @Override
    void trainingLines(List<String> lines) {
        long elapsed = started == 0 ? 0 : now() - started;
        lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + time(elapsed) + " s");
        lines.add(ChatColor.WHITE + "Blocks: " + ChatColor.GREEN + filled + ChatColor.GRAY + "/" + places.get(variant).size());
        lines.add(ChatColor.GRAY + (previewLeft > 0 ? "Look at the defense: " + ChatColor.YELLOW + previewLeft + " s"
                : started == 0 ? "Place a block to start" : done ? "Defense done!" : "Cover the bed!"));
    }
}
