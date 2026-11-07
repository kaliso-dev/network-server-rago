package gg.rago.lobby;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

/**
 * Generates nothing: every chunk of the lobby world is air, so nothing grows around the lobby map
 * when someone looks past its edge. The same generator as RagoBuildTools' empty worlds.
 */
final class VoidGenerator extends ChunkGenerator {

    /** Height of the platform of a new lobby world. */
    static final int PLATFORM_Y = 64;

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biomes) {
        return createChunkData(world);
    }

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
