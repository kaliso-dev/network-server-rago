package gg.rago.buildtools;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

/**
 * Generates nothing: every chunk is air. Used for the "vide" worlds where maps and lobbies are
 * built, and for imported maps, so that nothing grows around them when someone flies past the edge.
 * The block players spawn on is placed by {@link BuildWorlds} when the world is created.
 */
final class VoidGenerator extends ChunkGenerator {

    /** Height of the block placed under the spawn of a new empty world. */
    static final int PLATFORM_Y = 64;

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biomes) {
        return createChunkData(world);
    }

    /** The world is empty, so any column is as good as another to spawn in. */
    @Override
    public boolean canSpawn(World world, int x, int z) {
        return true;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, PLATFORM_Y + 1, 0.5);
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return Collections.emptyList();
    }
}
