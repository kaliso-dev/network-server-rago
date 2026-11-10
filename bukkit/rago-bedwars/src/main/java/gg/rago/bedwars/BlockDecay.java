package gg.rago.bedwars;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The blocks placed in an open game go after a while, oldest first, so that the map never fills
 * up: what the map had there comes back. A block placed again counts from its last placing. Main
 * thread only.
 */
final class BlockDecay {

    private final int seconds;
    /** The blocks placed, oldest first: their key and the second of the game they were placed at. */
    private final Deque<long[]> decaying = new ArrayDeque<>();
    /** The second each placed block was last placed at, to forget the older entries of a block placed again. */
    private final Map<Long, Long> placedAt = new HashMap<>();

    BlockDecay(int seconds) {
        this.seconds = seconds;
    }

    int seconds() {
        return seconds;
    }

    /** A block was placed at this second of the game. */
    void placed(Block block, int now) {
        long key = Instance.key(block.getX(), block.getY(), block.getZ());
        decaying.addLast(new long[] {key, now});
        placedAt.put(key, (long) now);
    }

    /** At this second of the game: the blocks placed long enough ago go. */
    void tick(Instance instance, World world, int now) {
        while (!decaying.isEmpty() && now - decaying.peekFirst()[1] >= seconds) {
            long[] entry = decaying.pollFirst();
            Long last = placedAt.get(entry[0]);
            if (last == null || last != entry[1]) {
                continue;
            }
            placedAt.remove(entry[0]);
            Block block = world.getBlockAt(Instance.xOf(entry[0]), Instance.yOf(entry[0]), Instance.zOf(entry[0]));
            if (instance.isPlaced(block)) {
                instance.restore(block);
            }
        }
    }

    /** The map was made as it was: nothing left to take away. */
    void clear() {
        decaying.clear();
        placedAt.clear();
    }
}
