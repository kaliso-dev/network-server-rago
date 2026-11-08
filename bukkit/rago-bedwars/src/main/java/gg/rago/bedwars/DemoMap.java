package gg.rago.bedwars;

import gg.rago.lobby.api.VoidGenerator;
import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/**
 * A small map of our own, made by the plugin the first time it starts, so that a game can be
 * played before any real map is set up: two islands facing each other (red in the west, blue in
 * the east), each with its spawn, bed, generator and villagers, a middle island with an emerald
 * generator, and two diamond islands on the sides. Turned off with carte-demo: false.
 */
final class DemoMap {

    static final String WORLD = "bw-demo";
    /** The floor of the islands; players stand one block above. */
    static final int FLOOR = 64;
    static final int Y = FLOOR + 1;

    private DemoMap() {
    }

    /**
     * Builds the demo world and returns true if its folder does not exist yet; the world is saved
     * and unloaded (game copies are made from its files). Its points are given by {@link #layout}.
     */
    static boolean buildIfMissing() {
        if (new File(Bukkit.getWorldContainer(), WORLD).exists()) {
            return false;
        }
        World world = new WorldCreator(WORLD).generator(new VoidGenerator()).generateStructures(false).createWorld();
        if (world == null) {
            return false;
        }
        world.setSpawnLocation(0, Y + 10, 0);
        build(world);
        world.save();
        Bukkit.unloadWorld(world, true);
        return true;
    }

    @SuppressWarnings("deprecation") // Block#setTypeIdAndData: the 1.8 way to set a block with its data
    static void build(World world) {
        // The team islands, in the colours of their teams, and the middle.
        floor(world, -37, -24, -6, 6, Material.STAINED_CLAY, 14);
        floor(world, 24, 37, -6, 6, Material.STAINED_CLAY, 11);
        floor(world, -5, 5, -5, 5, Material.STONE, 0);
        floor(world, -2, 2, 18, 22, Material.STONE, 0);
        floor(world, -2, 2, -22, -18, Material.STONE, 0);
        // What marks the generators.
        world.getBlockAt(-34, FLOOR, 0).setType(Material.IRON_BLOCK);
        world.getBlockAt(34, FLOOR, 0).setType(Material.IRON_BLOCK);
        world.getBlockAt(0, FLOOR, 0).setType(Material.EMERALD_BLOCK);
        world.getBlockAt(0, FLOOR, 20).setType(Material.DIAMOND_BLOCK);
        world.getBlockAt(0, FLOOR, -20).setType(Material.DIAMOND_BLOCK);
        // The beds, their head towards the spawn: data 1 points west, 3 east, +8 for the head.
        int bed = Material.BED_BLOCK.getId();
        world.getBlockAt(-27, Y, 0).setTypeIdAndData(bed, (byte) 1, false);
        world.getBlockAt(-28, Y, 0).setTypeIdAndData(bed, (byte) 9, false);
        world.getBlockAt(27, Y, 0).setTypeIdAndData(bed, (byte) 3, false);
        world.getBlockAt(28, Y, 0).setTypeIdAndData(bed, (byte) 11, false);
    }

    @SuppressWarnings("deprecation") // Block#setTypeIdAndData
    private static void floor(World world, int x1, int x2, int z1, int z2, Material material, int data) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                world.getBlockAt(x, FLOOR, z).setTypeIdAndData(material.getId(), (byte) data, false);
            }
        }
    }

    /** The points of the demo map: the red and blue teams, the generators, the spectators. */
    static void layout(BedwarsMap map) {
        BedwarsMap.Team red = map.team(TeamColor.RED);
        red.spawn = new MapPoint(-32.5, Y, 0.5, -90f, 0f);
        red.generator = new MapPoint(-33.5, Y, 0.5, 0f, 0f);
        red.bed = new MapPoint(-27, Y, 0, 0f, 0f);
        red.shop = new MapPoint(-32.5, Y, 4.5, 180f, 0f);
        red.upgrades = new MapPoint(-32.5, Y, -3.5, 0f, 0f);
        BedwarsMap.Team blue = map.team(TeamColor.BLUE);
        blue.spawn = new MapPoint(33.5, Y, 0.5, 90f, 0f);
        blue.generator = new MapPoint(34.5, Y, 0.5, 0f, 0f);
        blue.bed = new MapPoint(27, Y, 0, 0f, 0f);
        blue.shop = new MapPoint(33.5, Y, 4.5, 180f, 0f);
        blue.upgrades = new MapPoint(33.5, Y, -3.5, 0f, 0f);
        map.diamonds().clear();
        map.diamonds().add(new MapPoint(0.5, Y, 20.5, 0f, 0f));
        map.diamonds().add(new MapPoint(0.5, Y, -19.5, 0f, 0f));
        map.emeralds().clear();
        map.emeralds().add(new MapPoint(0.5, Y, 0.5, 0f, 0f));
        map.setSpectators(new MapPoint(0.5, Y + 20, 0.5, 0f, 90f));
        map.setBuildLimit(Y + 25);
        map.setVoidY(FLOOR - 24);
        map.setEnabled(true);
    }
}
