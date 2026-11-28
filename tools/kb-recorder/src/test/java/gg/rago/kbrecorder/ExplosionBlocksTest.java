package gg.rago.kbrecorder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExplosionBlocksTest {

    @Test
    void theCubeCoversTheFarthestBlockAVanillaExplosionCanBreak() {
        // A fireball (strength 1) reaches 1.73 blocks, a TNT (strength 4) 6.93: one more block
        // for the block a ray ends in.
        assertEquals(3, ExplosionBlocks.reach(1.0f));
        assertEquals(7, ExplosionBlocks.reach(3.0f));
        assertEquals(8, ExplosionBlocks.reach(4.0f));
        assertEquals(ExplosionBlocks.MAX_REACH, ExplosionBlocks.reach(100.0f));
        assertEquals(1, ExplosionBlocks.reach(0.0f));
        assertEquals(1, ExplosionBlocks.reach(Float.NaN));
    }
}
