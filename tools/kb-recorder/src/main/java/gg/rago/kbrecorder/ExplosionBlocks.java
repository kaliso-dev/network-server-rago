package gg.rago.kbrecorder;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

/**
 * Writes the blocks around an explosion as the client sees them just before the explosion: which
 * blocks an explosion breaks, and which ones it leaves, is then known from the recording alone.
 */
final class ExplosionBlocks {

    /**
     * Blocks a vanilla explosion can reach, per unit of strength: its rays start with up to 1.3
     * times the strength and lose 0.225 every 0.3 block, even through air.
     */
    static final double REACH_PER_STRENGTH = 1.3 / 0.225 * 0.3;
    /** Largest reach written, in blocks from the centre, whatever the explosion says. */
    static final int MAX_REACH = 8;

    private ExplosionBlocks() {
    }

    /**
     * Called on a network thread with an explosion packet: reads the blocks on the game thread,
     * before the game applies the packet (its own handling is queued after this one).
     */
    static void readBefore(Minecraft mc, Recorder recorder, byte[] explosion) {
        float[] values = PacketBytes.explosion(explosion);
        if (values == null) {
            return;
        }
        mc.addScheduledTask(() -> {
            try {
                World world = mc.theWorld;
                if (world != null && recorder.isRecording()) {
                    recorder.submit(write(world, recorder.line("blocks"), values));
                }
            } catch (RuntimeException e) {
                recorder.error("explosion blocks", e);
            }
        });
    }

    /** Half the side of the cube written around an explosion of this strength. */
    static int reach(float strength) {
        if (!(strength > 0)) {
            return 1;
        }
        return Math.min(MAX_REACH, (int) Math.ceil(strength * REACH_PER_STRENGTH) + 1);
    }

    /**
     * Completes a blocks line: the explosion's centre and strength, then every block that is not
     * air in the cube around the centre, as {@code [x, y, z, block id, metadata]}.
     */
    private static JsonLine write(World world, JsonLine line, float[] explosion) {
        int reach = reach(explosion[3]);
        int centreX = (int) Math.floor(explosion[0]);
        int centreY = (int) Math.floor(explosion[1]);
        int centreZ = (int) Math.floor(explosion[2]);
        StringBuilder blocks = new StringBuilder("[");
        int count = 0;
        for (int x = centreX - reach; x <= centreX + reach; x++) {
            for (int y = Math.max(0, centreY - reach); y <= Math.min(255, centreY + reach); y++) {
                for (int z = centreZ - reach; z <= centreZ + reach; z++) {
                    IBlockState state = world.getBlockState(new BlockPos(x, y, z));
                    Block block = state.getBlock();
                    int id = Block.getIdFromBlock(block);
                    if (id == 0) {
                        continue;
                    }
                    if (count++ > 0) {
                        blocks.append(',');
                    }
                    blocks.append('[').append(x).append(',').append(y).append(',').append(z).append(',')
                            .append(id).append(',').append(block.getMetaFromState(state)).append(']');
                }
            }
        }
        blocks.append(']');
        return line.add("x", explosion[0])
                .add("y", explosion[1])
                .add("z", explosion[2])
                .add("strength", explosion[3])
                .add("reach", reach)
                .add("count", count)
                .raw("blocks", blocks.toString());
    }
}
