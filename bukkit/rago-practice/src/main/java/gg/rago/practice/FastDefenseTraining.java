package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Fast defense: a bed on an island, and the player covers it as fast as they can, layer by layer
 * as in a game: one layer of wool (solo), two (end stone then wool) or three (end stone, wood and
 * wool, as in Ranked Bedwars). Each place takes the block of its layer; the time runs from the
 * first block to the last place filled.
 */
final class FastDefenseTraining extends Training {

    private static final int BED_X = 0;
    private static final int BED_Y = 1;

    /** Each place of the defense ("x,y,z" offsets) and the layer it belongs to, for each variant. */
    private final List<Map<String, Integer>> places = new ArrayList<>();
    private long started;
    private int filled;
    private boolean done;

    FastDefenseTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams,
                new Variant("one", "1 layer (wool)", true, ""),
                new Variant("two", "2 layers (end stone, wool)", true, ""),
                new Variant("three", "3 layers (RBW)", true, ""));
        for (int layers = 1; layers <= 3; layers++) {
            Map<String, Integer> map = new HashMap<>();
            List<List<int[]>> defense = Shapes.defense(BED_X, BED_Y, 0, 1, 0, layers);
            for (int layer = 0; layer < defense.size(); layer++) {
                for (int[] place : defense.get(layer)) {
                    map.put(key(place[0], place[1], place[2]), layer);
                }
            }
            places.add(map);
        }
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private int layers() {
        return variant + 1;
    }

    /** The block of this layer (0: touches the bed) of a defense of so many layers. */
    static Material typeOf(int layer, int layers) {
        if (layer == layers - 1) {
            return Material.WOOL;
        }
        return layer == 0 ? Material.ENDER_STONE : Material.WOOD;
    }

    @Override
    String describe() {
        switch (current().id) {
            case "two":
                return "end stone against the bed, then wool over it (26 blocks).";
            case "three":
                return "end stone against the bed, then wood, then wool (58 blocks).";
            default:
                return "cover your bed with one layer of wool (8 blocks).";
        }
    }

    @Override
    void build() {
        Shapes.island(arena, -7, -6, 8, 6, Material.WOOD, 0, Material.LOG, 0);
        Shapes.bed(arena, BED_X, BED_Y, 0, 1, 0);
    }

    @Override
    Location spawnOf(UUID player) {
        return arena.at(-layers() - 2.5, 1, 0.5, -90f);
    }

    @Override
    ItemStack[] kit() {
        switch (layers()) {
            case 1:
                return new ItemStack[] {stack(Material.WOOL)};
            case 2:
                return new ItemStack[] {stack(Material.ENDER_STONE), stack(Material.WOOL)};
            default:
                return new ItemStack[] {stack(Material.ENDER_STONE), stack(Material.WOOD), stack(Material.WOOL)};
        }
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

    private Integer layerOf(Block block) {
        return places.get(variant).get(key(block.getX() - arena.centreX(), block.getY() - arena.floorY(),
                block.getZ() - arena.centreZ()));
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        if (done) {
            return false;
        }
        Integer layer = layerOf(block);
        if (layer == null) {
            // a block to stand on is fine, near the bed
            return near(block, 0, 0, 7, 0, 6);
        }
        Material wanted = typeOf(layer, layers());
        if (block.getType() != wanted) {
            player.sendMessage(ChatColor.RED + "Layer " + (layer + 1) + " is " + nameOf(wanted) + ".");
            return false;
        }
        return true;
    }

    private static String nameOf(Material type) {
        return type == Material.ENDER_STONE ? "end stone" : type == Material.WOOD ? "wood" : "wool";
    }

    @Override
    void placed(Player player, Block block) {
        if (started == 0) {
            started = now();
        }
        if (layerOf(block) != null) {
            filled++;
        }
    }

    @Override
    void broke(Player player, Block block) {
        if (layerOf(block) != null) {
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

    /** Every place holds the block of its layer. */
    private boolean complete() {
        for (Map.Entry<String, Integer> place : places.get(variant).entrySet()) {
            String[] xyz = place.getKey().split(",");
            Block block = arena.block(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
            if (block.getType() != typeOf(place.getValue(), layers())) {
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
